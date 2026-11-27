#!/usr/bin/env python
"""Phase 0c-2 — train and evaluate the three planner models.

    ./.venv/bin/python train_eval.py --stage junk
    ./.venv/bin/python train_eval.py --stage all
    ./.venv/bin/python train_eval.py --stage junk --quick     # 3 groups, fast

Reads only Phase 0c-1 outputs (``out/features/*.parquet``,
``out/labels/*.parquet``) plus the Phase 0b per-pack economy tables, and
writes only under ``out/models/``.  Nothing upstream is touched.

Design rules inherited from the rest of the package:

* **No hardcoded mod / item / material / colour / system vocabulary.**  Every
  label-space word this module uses is *imported* from ``labels.py``'s single
  sanctioned table; no new word is introduced here.
* **Group-aware evaluation only.**  Every split is on ``pack_group``; a pack
  group is never on both sides of a fit.
* **Gold is evaluation-only.**  The 19 pairwise-gold readings never enter a
  training set (:func:`load_pairs` drops them and the tests assert it).
* **Determinism.**  Fixed seeds, sorted group order, stable sorts.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

import numpy as np
import pandas as pd
import pyarrow.parquet as pq

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from common import canon_json, write_json  # noqa: E402
# The label-space vocabulary lives in exactly one place in this package.
from labels import (  # noqa: E402
    COL_JUNK_TARGET, ROLES, ROLE_UNKNOWN, VERDICT_JUNK, VERDICT_USEFUL,
    TIER_GOLD, TIER_SILVER, TIER_CHAIN, TIER_BRONZE,
)

#: Roles the multiclass head predicts: the plan's role set minus the one that
#: says "labelled, but not about the role".
ROLE_CLASSES = tuple(r for r in ROLES if r != ROLE_UNKNOWN)

#: Share of the total sample weight a single pack group may carry.
GROUP_WEIGHT_CAP = 0.40

SEED = 20260919


# --------------------------------------------------------------- bookkeeping

TIMINGS: list[dict] = []


class Timer:
    """Context manager that appends ``{'what': ..., 'seconds': ...}``."""

    def __init__(self, what: str, quiet: bool = False):
        self.what = what
        self.quiet = quiet

    def __enter__(self):
        self.t0 = time.perf_counter()
        return self

    def __exit__(self, *exc):
        self.seconds = time.perf_counter() - self.t0
        TIMINGS.append({"what": self.what, "seconds": round(self.seconds, 3)})
        if not self.quiet:
            print(f"    [{self.seconds:7.2f}s] {self.what}")
        return False


# -------------------------------------------------------------------- inputs


def feature_names(out_dir: Path) -> list[str]:
    """Ordered template feature names, exactly as ``features.py`` wrote them."""
    catalog = json.loads((out_dir / "features" / "feature-catalog.json").read_text())
    return [entry["name"] for entry in catalog["template_features"]]


def feature_layers(out_dir: Path) -> dict:
    catalog = json.loads((out_dir / "features" / "feature-catalog.json").read_text())
    return {e["name"]: e["layer"] for e in catalog["template_features"]}


def load_templates(out_dir: Path) -> tuple[pd.DataFrame, list[str]]:
    """``template_features`` joined 1:1 with ``template_labels``."""
    names = feature_names(out_dir)
    feats = pq.read_table(out_dir / "features" / "template_features.parquet",
                          columns=["pack", "pack_group", "template_id"] + names
                          ).to_pandas()
    lab = pq.read_table(out_dir / "labels" / "template_labels.parquet",
                        columns=["pack", "template_id", COL_JUNK_TARGET,
                                 "role_target", "label_weight", "role_weight",
                                 "best_tier", "n_members"]).to_pandas()
    df = feats.merge(lab, on=["pack", "template_id"], how="inner",
                     validate="one_to_one")
    assert len(df) == len(feats), "feature/label key sets must be identical"
    return df, names


# ------------------------------------------------------------------ weighting


def group_capped_weights(base: np.ndarray, groups: np.ndarray,
                         cap: float = GROUP_WEIGHT_CAP) -> np.ndarray:
    """Per-row weight = ``base`` x a per-group scale, capped at ``cap`` share.

    atm10 carries 69 % of the gold verdicts and 94 % of the pairs, so an
    unweighted fit is an atm10 fit.  The scale is found by water-filling: any
    group whose share of the total weight exceeds ``cap`` is scaled down to
    exactly ``cap`` *given the other groups' current mass*,

        scale_g <- cap / (1 - cap) * (T - W_g) / W_g,

    and the pass is repeated until every share is under the cap (it converges
    in a handful of passes because scaling one group only lowers the others'
    denominators).  Groups under the cap are never scaled **up** — a group
    with 78 labelled templates does not get the same say as one with 15,533;
    it only stops being drowned.
    """
    base = np.asarray(base, dtype=np.float64)
    scale = {g: 1.0 for g in np.unique(groups)}
    # With G groups no assignment can push every share below 1/G, and asking
    # for less makes the water-filling oscillate between the two largest.
    # Clamping is a no-op on real folds (>= 9 groups, 1/9 < 0.40).
    cap = max(float(cap), 1.0 / max(len(scale), 1))
    idx = {g: np.flatnonzero(groups == g) for g in scale}
    for _ in range(50):
        mass = {g: float(base[i].sum() * scale[g]) for g, i in idx.items()}
        total = sum(mass.values())
        if total <= 0:
            break
        hot = [g for g, m in mass.items() if m / total > cap + 1e-12]
        if not hot:
            break
        for g in hot:
            rest = total - mass[g]
            target = cap / (1.0 - cap) * rest
            scale[g] *= target / mass[g]
    out = base.copy()
    for g, i in idx.items():
        out[i] *= scale[g]
    return out


# -------------------------------------------------------------------- models


def standardiser(x: np.ndarray) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """``(fill, mean, std)`` from a training matrix.  Fill = column median."""
    fill = np.nanmedian(x, axis=0)
    fill = np.where(np.isfinite(fill), fill, 0.0)
    filled = np.where(np.isfinite(x), x, fill)
    mean = filled.mean(axis=0)
    std = filled.std(axis=0)
    std = np.where(std > 1e-12, std, 1.0)
    return fill, mean, std


def apply_fill(x: np.ndarray, fill: np.ndarray) -> np.ndarray:
    return np.where(np.isfinite(x), x, fill)


def fit_lr(x: np.ndarray, y: np.ndarray, w: np.ndarray, *, multiclass: bool = False):
    """Standardised, class-balanced logistic regression."""
    from sklearn.linear_model import LogisticRegression

    fill, mean, std = standardiser(x)
    z = (apply_fill(x, fill) - mean) / std
    clf = LogisticRegression(max_iter=2000, C=1.0, class_weight="balanced",
                             solver="lbfgs", n_jobs=-1 if multiclass else None,
                             random_state=SEED)
    clf.fit(z, y, sample_weight=w)
    return {"kind": "lr", "fill": fill, "mean": mean, "std": std, "clf": clf}


def fit_lgb(x: np.ndarray, y: np.ndarray, w: np.ndarray, groups: np.ndarray,
            *, multiclass: bool = False, n_classes: int = 2,
            n_estimators: int = 300):
    """LightGBM with early stopping on an *inner* left-out pack group."""
    import lightgbm as lgb

    fill, mean, std = standardiser(x)
    xf = apply_fill(x, fill)
    tr, va, inner = inner_split(y, groups)
    params = dict(n_estimators=n_estimators, num_leaves=31, learning_rate=0.05,
                  min_child_samples=20, subsample=0.9, subsample_freq=1,
                  colsample_bytree=0.9, random_state=SEED, n_jobs=10, verbose=-1)
    if multiclass:
        model = lgb.LGBMClassifier(objective="multiclass", num_class=n_classes,
                                   **params)
    else:
        model = lgb.LGBMClassifier(objective="binary", **params)
    ok = va is not None and len(np.unique(y[va])) > 1 and va.sum() >= 20
    if ok:
        model.fit(xf[tr], y[tr], sample_weight=w[tr],
                  eval_X=xf[va], eval_y=y[va], eval_sample_weight=[w[va]],
                  callbacks=[lgb.early_stopping(30, verbose=False)])
    best = getattr(model, "best_iteration_", None) or 0
    if not ok or best <= 0:
        # Early stopping never found an improving round on the inner split
        # (it happens when the inner group is the only other big one).  Fall
        # back to a fixed, documented budget rather than to a constant model.
        model.fit(xf, y, sample_weight=w)
        best, inner = n_estimators, None
    return {"kind": "lgb", "fill": fill, "mean": mean, "std": std, "clf": model,
            "inner_group": inner, "best_iteration": int(best)}


def inner_split(y: np.ndarray, groups: np.ndarray):
    """Train / early-stopping split *inside* a training set.

    With three or more pack groups available the validation fold is a whole
    group — the one whose size is closest to 15 % of the training rows — so
    early stopping sees the same transfer problem the outer fold measures.
    With fewer, a group split would hand the model a single group to learn
    from, so a seeded 80/20 row split is used instead and the fold is marked
    ``inner_group = None``.  The outer held-out group is untouched either way.
    """
    uniq = sorted(np.unique(groups).tolist())
    if len(uniq) >= 3:
        sizes = {g: int((groups == g).sum()) for g in uniq}
        want = 0.15 * len(y)
        inner = min(uniq, key=lambda g: (abs(sizes[g] - want), g))
        tr = groups != inner
        if tr.sum() >= 50 and len(np.unique(y[~tr])) > 1:
            return tr, ~tr, inner
    rng = np.random.default_rng(SEED)
    order = rng.permutation(len(y))
    tr = np.zeros(len(y), bool)
    tr[order[: int(len(y) * 0.8)]] = True
    return tr, ~tr, None


def predict(model: dict, x: np.ndarray) -> np.ndarray:
    xf = apply_fill(x, model["fill"])
    if model["kind"] == "lr":
        z = (xf - model["mean"]) / model["std"]
        return model["clf"].predict_proba(z)
    return model["clf"].predict_proba(xf)


# ------------------------------------------------------------------- metrics


def auc_pr(y: np.ndarray, p: np.ndarray, w: np.ndarray | None = None) -> dict:
    from sklearn.metrics import roc_auc_score, average_precision_score
    if len(np.unique(y)) < 2:
        return {"auc": float("nan"), "pr_auc": float("nan"), "n": int(len(y)),
                "pos": int(y.sum())}
    return {"auc": float(roc_auc_score(y, p, sample_weight=w)),
            "pr_auc": float(average_precision_score(y, p, sample_weight=w)),
            "n": int(len(y)), "pos": int(y.sum())}


def operating_points(y: np.ndarray, p: np.ndarray) -> dict:
    """Recall at P>=0.95, precision at R>=0.90, and the best-F1 point."""
    from sklearn.metrics import precision_recall_curve
    out = {"recall_at_p95": 0.0, "thr_at_p95": float("nan"),
           "precision_at_r90": 0.0, "thr_at_r90": float("nan"),
           "best_f1": 0.0, "f1_precision": 0.0, "f1_recall": 0.0,
           "thr_at_best_f1": float("nan")}
    if len(np.unique(y)) < 2:
        return out
    prec, rec, thr = precision_recall_curve(y, p)
    prec, rec = prec[:-1], rec[:-1]          # drop the trivial endpoint
    hit = prec >= 0.95
    if hit.any():
        j = int(np.argmax(np.where(hit, rec, -1.0)))
        out["recall_at_p95"] = float(rec[j])
        out["thr_at_p95"] = float(thr[j])
    hit = rec >= 0.90
    if hit.any():
        j = int(np.argmax(np.where(hit, prec, -1.0)))
        out["precision_at_r90"] = float(prec[j])
        out["thr_at_r90"] = float(thr[j])
    f1 = np.where(prec + rec > 0, 2 * prec * rec / np.maximum(prec + rec, 1e-12), 0.0)
    j = int(np.argmax(f1))
    out.update(best_f1=float(f1[j]), f1_precision=float(prec[j]),
               f1_recall=float(rec[j]), thr_at_best_f1=float(thr[j]))
    return out


# ------------------------------------------------------------- the junk head


def lopo_binary(df: pd.DataFrame, names: list[str], target: str,
                weight_col: str, kinds=("lr", "lgb"), quiet: bool = False,
                subset: np.ndarray | None = None) -> dict:
    """Leave-one-pack-group-out over the groups that carry ``target``.

    Trains on the labelled templates of every *other* group and predicts the
    **whole** held-out group (labelled or not), so the recipe-level pass has a
    prediction for every member recipe.
    """
    mask = df[target].notna().to_numpy()
    if subset is not None:
        mask = mask & subset
    groups = df["pack_group"].to_numpy()
    labelled_groups = sorted(set(groups[mask].tolist()))
    x_all = df[names].to_numpy(dtype=np.float64)
    y_all = df[target].to_numpy(dtype=np.float64)
    w_all = df[weight_col].to_numpy(dtype=np.float64)

    oof = {k: np.full(len(df), np.nan) for k in kinds}
    folds = []
    for held in labelled_groups:
        tr = mask & (groups != held)
        te = groups == held
        if len(np.unique(y_all[tr])) < 2:
            continue
        w = group_capped_weights(w_all[tr], groups[tr])
        fold = {"group": held, "n_train": int(tr.sum()),
                "n_test_labelled": int((te & mask).sum())}
        for kind in kinds:
            with Timer(f"{target} LOPO fit {kind} (hold out {held})", quiet) as t:
                model = (fit_lr(x_all[tr], y_all[tr], w)
                         if kind == "lr"
                         else fit_lgb(x_all[tr], y_all[tr], w, groups[tr]))
            p = predict(model, x_all[te])[:, 1]
            oof[kind][te] = p
            sel = (te & mask)[te]
            fold[kind] = auc_pr(y_all[te][sel], p[sel])
            fold[kind].update(operating_points(y_all[te][sel], p[sel]))
            fold[kind]["fit_seconds"] = round(t.seconds, 2)
            if kind == "lgb":
                fold[kind]["best_iteration"] = model["best_iteration"]
            fold[f"pred_{kind}"] = p
        folds.append(fold)

    result = {"target": target, "folds": [], "pooled": {}}
    for kind in kinds:
        pool_p, pool_y = [], []
        for f in folds:
            te = groups == f["group"]
            sel = (te & mask)[te]
            pool_p.append(f[f"pred_{kind}"][sel])
            pool_y.append(y_all[te][sel])
        py = np.concatenate(pool_y)
        pp = np.concatenate(pool_p)
        pooled = auc_pr(py, pp)
        pooled.update(operating_points(py, pp))
        pooled["mean_fold_auc"] = float(np.nanmean([f[kind]["auc"] for f in folds]))
        result["pooled"][kind] = pooled
    for f in folds:
        result["folds"].append({k: v for k, v in f.items()
                                if not k.startswith("pred_")})
    result["oof"] = oof
    result["mask"] = mask
    return result


def print_binary(result: dict, kinds=("lr", "lgb")) -> None:
    print(f"\n  {result['target']} — leave-one-pack-group-out")
    head = "  {:<26} {:>7} {:>7}".format("group", "n", "pos")
    for k in kinds:
        head += " {:>7} {:>7} {:>7} {:>7}".format(
            f"{k} AUC", f"{k} PR", f"{k} R@P95", f"{k} P@R90")
    print(head)
    for f in result["folds"]:
        row = "  {:<26} {:>7} {:>7}".format(
            f["group"][:26], f[kinds[0]]["n"], f[kinds[0]]["pos"])
        for k in kinds:
            row += " {:>7.3f} {:>7.3f} {:>7.3f} {:>7.3f}".format(
                f[k]["auc"], f[k]["pr_auc"], f[k]["recall_at_p95"],
                f[k]["precision_at_r90"])
        print(row)
    row = "  {:<26} {:>7} {:>7}".format("POOLED", result["pooled"][kinds[0]]["n"],
                                        result["pooled"][kinds[0]]["pos"])
    for k in kinds:
        p = result["pooled"][k]
        row += " {:>7.3f} {:>7.3f} {:>7.3f} {:>7.3f}".format(
            p["auc"], p["pr_auc"], p["recall_at_p95"], p["precision_at_r90"])
    print(row)
    for k in kinds:
        p = result["pooled"][k]
        print(f"    {k}: best-F1 {p['best_f1']:.3f} at P={p['f1_precision']:.3f} "
              f"R={p['f1_recall']:.3f}; mean fold AUC {p['mean_fold_auc']:.3f}")


# ------------------------------------------------------------- the role head


def lopo_multiclass(df: pd.DataFrame, names: list[str],
                    kinds=("lr", "lgb")) -> dict:
    """Leave-one-pack-group-out over the five role classes.

    ``unknown`` and the empty role are dropped: the first says "labelled, but
    not about the role", the second says "no member carried a category".
    """
    from sklearn.metrics import f1_score, recall_score
    role = df["role_target"].fillna("").to_numpy()
    mask = np.isin(role, ROLE_CLASSES)
    groups = df["pack_group"].to_numpy()
    classes = list(ROLE_CLASSES)
    code = {c: i for i, c in enumerate(classes)}
    x_all = df[names].to_numpy(dtype=np.float64)
    y_all = np.array([code.get(r, -1) for r in role])
    w_all = df["role_weight"].to_numpy(dtype=np.float64)

    labelled_groups = sorted(set(groups[mask].tolist()))
    folds, pooled_true, pooled_pred = [], {k: [] for k in kinds}, {k: [] for k in kinds}
    for held in labelled_groups:
        tr = mask & (groups != held)
        te = mask & (groups == held)
        if te.sum() < 5 or len(np.unique(y_all[tr])) < 2:
            continue
        w = group_capped_weights(w_all[tr], groups[tr])
        fold = {"group": held, "n_train": int(tr.sum()), "n_test": int(te.sum()),
                "classes_present": sorted({classes[i] for i in np.unique(y_all[te])})}
        for kind in kinds:
            with Timer(f"role LOPO fit {kind} (hold out {held})") as t:
                model = (fit_lr(x_all[tr], y_all[tr], w, multiclass=True)
                         if kind == "lr"
                         else fit_lgb(x_all[tr], y_all[tr], w, groups[tr],
                                      multiclass=True,
                                      n_classes=len(np.unique(y_all[tr]))))
            proba = predict(model, x_all[te])
            seen = list(model["clf"].classes_)
            pred = np.array([seen[i] for i in proba.argmax(axis=1)])
            fold[kind] = {
                "macro_f1": float(f1_score(y_all[te], pred, average="macro",
                                           zero_division=0)),
                "accuracy": float((pred == y_all[te]).mean()),
                "fit_seconds": round(t.seconds, 2)}
            pooled_true[kind].append(y_all[te])
            pooled_pred[kind].append(pred)
        folds.append(fold)

    result = {"target": "role_target", "classes": classes, "folds": folds,
              "pooled": {}}
    for kind in kinds:
        yt = np.concatenate(pooled_true[kind])
        yp = np.concatenate(pooled_pred[kind])
        rec = recall_score(yt, yp, average=None, labels=range(len(classes)),
                           zero_division=0)
        result["pooled"][kind] = {
            "macro_f1": float(f1_score(yt, yp, average="macro", zero_division=0)),
            "weighted_f1": float(f1_score(yt, yp, average="weighted",
                                          zero_division=0)),
            "accuracy": float((yt == yp).mean()),
            "n": int(len(yt)),
            "per_class_recall": {c: float(r) for c, r in zip(classes, rec)},
            "support": {c: int((yt == i).sum()) for i, c in enumerate(classes)},
            "mean_fold_macro_f1": float(np.mean([f[kind]["macro_f1"]
                                                 for f in folds]))}
    return result


def print_multiclass(result: dict, kinds=("lr", "lgb")) -> None:
    print("\n  role_target — leave-one-pack-group-out (macro-F1 / accuracy)")
    print("  {:<26} {:>6} {:>15} {:>15}".format("group", "n", "lr F1 / acc",
                                                "lgb F1 / acc"))
    for f in result["folds"]:
        print("  {:<26} {:>6} {:>7.3f}/{:<7.3f} {:>7.3f}/{:<7.3f}".format(
            f["group"][:26], f["n_test"], f["lr"]["macro_f1"], f["lr"]["accuracy"],
            f["lgb"]["macro_f1"], f["lgb"]["accuracy"]))
    for k in kinds:
        p = result["pooled"][k]
        print(f"    POOLED {k}: macro-F1 {p['macro_f1']:.3f}  weighted-F1 "
              f"{p['weighted_f1']:.3f}  acc {p['accuracy']:.3f}  n={p['n']}")
        print("      per-class recall: " + "  ".join(
            f"{c} {p['per_class_recall'][c]:.3f} (n={p['support'][c]})"
            for c in result["classes"]))


# -------------------------------------------------- template -> recipe level


def recipe_level(out_dir: Path, df: pd.DataFrame, oof: dict) -> dict:
    """Score the template predictions where the planner consumes them.

    Each template's out-of-fold prediction is propagated to every member
    recipe and scored against ``recipe_labels.gold_verdict`` — the recipe-level
    verdict the planner actually acts on.
    """
    rl = pq.read_table(out_dir / "labels" / "recipe_labels.parquet",
                       columns=["pack", "recipe_idx", "template_id",
                                "gold_verdict"]).to_pandas()
    rl = rl[rl.gold_verdict.isin([VERDICT_JUNK, VERDICT_USEFUL])]
    out = {"n_gold_recipes": int(len(rl)), "by_model": {}}
    for kind, pred in oof.items():
        side = pd.DataFrame({"pack": df["pack"], "template_id": df["template_id"],
                             "p": pred})
        j = rl.merge(side, on=["pack", "template_id"], how="inner")
        j = j[j.p.notna()]
        y = (j.gold_verdict == VERDICT_JUNK).to_numpy().astype(float)
        p = j.p.to_numpy()
        m = auc_pr(y, p)
        m.update(operating_points(y, p))
        m["n_scored"] = int(len(j))
        m["coverage"] = float(len(j) / max(len(rl), 1))
        out["by_model"][kind] = m
    return out


def print_recipe_level(res: dict) -> None:
    print(f"\n  recipe level ({res['n_gold_recipes']:,} gold-verdict recipes)")
    for kind, m in res["by_model"].items():
        print(f"    {kind}: n={m['n_scored']:,} (cov {m['coverage']:.3f})  "
              f"AUC {m['auc']:.3f}  PR {m['pr_auc']:.3f}  "
              f"R@P95 {m['recall_at_p95']:.3f}  P@R90 {m['precision_at_r90']:.3f}  "
              f"bestF1 {m['best_f1']:.3f} (P={m['f1_precision']:.3f})")


# ---------------------------------------------------- new-mod zero-shot


def template_namespace(out_dir: Path) -> pd.DataFrame:
    """``(pack, template_id) -> majority recipe namespace`` of its members.

    The namespace is not a name this module knows: it is read off the data and
    only ever used as a *grouping key* for the hold-out.
    """
    rf = pq.read_table(out_dir / "features" / "recipe_features.parquet",
                       columns=["pack", "template_id", "recipe_ns"]).to_pandas()
    counts = rf.groupby(["pack", "template_id", "recipe_ns"]).size()
    counts = counts.reset_index(name="n").sort_values(
        ["pack", "template_id", "n", "recipe_ns"],
        ascending=[True, True, False, True], kind="stable")
    top = counts.drop_duplicates(["pack", "template_id"], keep="first")
    return top[["pack", "template_id", "recipe_ns"]].rename(
        columns={"recipe_ns": "ns"})


def zero_shot(out_dir: Path, df: pd.DataFrame, names: list[str],
              top_k: int = 5, kinds=("lr", "lgb")) -> dict:
    """Hold out one whole namespace at a time — the "new mod" case.

    Unlike the LOPO folds this one trains on **every** pack group; what the
    model has never seen is the mod, not the pack.
    """
    ns = template_namespace(out_dir)
    d = df.merge(ns, on=["pack", "template_id"], how="left")
    d["ns"] = d["ns"].fillna("")
    mask = d[COL_JUNK_TARGET].notna().to_numpy()
    counts = d.loc[mask & (d.ns != ""), "ns"].value_counts()
    chosen = list(counts.index[:top_k])
    x_all = d[names].to_numpy(dtype=np.float64)
    y_all = d[COL_JUNK_TARGET].to_numpy(dtype=np.float64)
    w_all = d["label_weight"].to_numpy(dtype=np.float64)
    groups = d["pack_group"].to_numpy()
    nsv = d["ns"].to_numpy()

    out = {"namespaces": [], "n_namespaces_seen": int(len(counts))}
    for name in chosen:
        held = nsv == name
        tr = mask & ~held
        te = mask & held
        if len(np.unique(y_all[te])) < 2:
            out["namespaces"].append({"rank": len(out["namespaces"]) + 1,
                                      "n": int(te.sum()),
                                      "skipped": "one class only"})
            continue
        w = group_capped_weights(w_all[tr], groups[tr])
        row = {"rank": len(out["namespaces"]) + 1, "n_train": int(tr.sum()),
               "n_test": int(te.sum()), "pos": int(y_all[te].sum()),
               "n_groups": int(len(set(groups[te].tolist())))}
        for kind in kinds:
            with Timer(f"zero-shot fit {kind} (hold out namespace #{row['rank']})") as t:
                model = (fit_lr(x_all[tr], y_all[tr], w) if kind == "lr"
                         else fit_lgb(x_all[tr], y_all[tr], w, groups[tr]))
            p = predict(model, x_all[te])[:, 1]
            row[kind] = auc_pr(y_all[te], p)
            row[kind].update(operating_points(y_all[te], p))
            row[kind]["fit_seconds"] = round(t.seconds, 2)
        out["namespaces"].append(row)
    return out


def print_zero_shot(res: dict, kinds=("lr", "lgb")) -> None:
    print("\n  new-mod zero-shot — hold out every template of one namespace")
    print("  (namespaces are ranked by labelled-template count and never named:"
          " this module writes down no registry word)")
    print("  {:<6} {:>7} {:>6} {:>7} {:>8} {:>8} {:>8} {:>8}".format(
        "rank", "n", "pos", "groups", "lr AUC", "lr R@P95", "lgb AUC", "lgb R@P95"))
    for r in res["namespaces"]:
        if "skipped" in r:
            print("  {:<6} {:>7} {:>6}  {}".format(r["rank"], r["n"], "-",
                                                   r["skipped"]))
            continue
        print("  {:<6} {:>7} {:>6} {:>7} {:>8.3f} {:>8.3f} {:>8.3f} {:>8.3f}".format(
            r["rank"], r["n_test"], r["pos"], r["n_groups"], r["lr"]["auc"],
            r["lr"]["recall_at_p95"], r["lgb"]["auc"], r["lgb"]["recall_at_p95"]))


# ------------------------------------------------------------------ ablations


def ablations(df: pd.DataFrame, names: list[str], layers: dict,
              drop=("L1", "L3", "L6")) -> dict:
    """LOPO pooled AUC with one whole feature layer removed."""
    out = {}
    for tag in drop:
        keep = [n for n in names if not layers[n].startswith(tag)]
        with Timer(f"ablation drop {tag} ({len(names) - len(keep)} features)"):
            res = lopo_binary(df, keep, COL_JUNK_TARGET, "label_weight",
                              quiet=True)
        out[tag] = {"n_features": len(keep),
                    "dropped": len(names) - len(keep),
                    "pooled": {k: {kk: res["pooled"][k][kk]
                                   for kk in ("auc", "pr_auc", "recall_at_p95",
                                              "best_f1")}
                               for k in res["pooled"]}}
    return out


def importances(df: pd.DataFrame, names: list[str], top: int = 20) -> dict:
    """Top-20 LightGBM gain and |LR coef| on a fit over *all* labelled groups."""
    mask = df[COL_JUNK_TARGET].notna().to_numpy()
    x = df.loc[mask, names].to_numpy(dtype=np.float64)
    y = df.loc[mask, COL_JUNK_TARGET].to_numpy(dtype=np.float64)
    groups = df.loc[mask, "pack_group"].to_numpy()
    w = group_capped_weights(df.loc[mask, "label_weight"].to_numpy(float), groups)
    with Timer("importance fit lr (all groups)"):
        lr = fit_lr(x, y, w)
    with Timer("importance fit lgb (all groups)"):
        gb = fit_lgb(x, y, w, groups)
    coef = np.abs(lr["clf"].coef_[0])
    gain = gb["clf"].booster_.feature_importance(importance_type="gain")
    order_lr = np.argsort(-coef)[:top]
    order_gb = np.argsort(-gain)[:top]
    return {
        "lr_abs_coef": [{"feature": names[i], "value": float(coef[i]),
                         "sign": float(np.sign(lr["clf"].coef_[0][i]))}
                        for i in order_lr],
        "lgb_gain": [{"feature": names[i], "value": float(gain[i])}
                     for i in order_gb]}


def print_importances(imp: dict) -> None:
    print("\n  top-20 features (junk head, fit over every labelled group)")
    print("  {:<38} {:>10}   {:<38} {:>12}".format(
        "LR |coef| (sign)", "value", "LightGBM gain", "gain"))
    for a, b in zip(imp["lr_abs_coef"], imp["lgb_gain"]):
        sign = "+" if a["sign"] > 0 else "-"
        print("  {:<38} {:>10.3f}   {:<38} {:>12.1f}".format(
            sign + a["feature"], a["value"], b["feature"], b["value"]))


# ================================================== Stage B — ProducerRanker

#: Per-recipe columns that enter the pair matrix only as a *named pair flag*
#: (their difference IS the flag, so keeping both would split one weight over
#: two collinear columns).
FLAG_SOURCE_COLS = ("tier", "n_input_slots", "log_value_in")

#: The unsupervised leaf composite.  It enters as exactly one pair flag
#: (``flag_d_leaf_acq_min``) so Stage C can read the supervised weights off the
#: *raw* leaf columns without them being absorbed by the score they replace.
ACQ_COLS = ("l4_leaf_acq_mean", "l4_leaf_acq_min")

#: Relative say of one pair of each provenance tier.  The pair's own
#: ``weight`` column is normalised to mean 1 inside its tier first, so these
#: multipliers are exactly the mean weight of a pair of that tier: an audited
#: chain is worth half a two-verdict comparison, and the rule replay — the
#: thing this project replaces — is worth a tenth and may only break ties.
TIER_MULT = {TIER_SILVER: 1.0, TIER_CHAIN: 0.5, TIER_BRONZE: 0.1}

#: The one gold reading dropped by hand, addressed as
#: ``finding id -> reading indices`` in the deterministic order ``labels.py``
#: wrote them.  Reading #1 of this two-reading ``silly-detour`` finding pairs
#: two recipes the prose never compares; the finding is about the *other*
#: reading's detour (see REPORT.md, Stage B).  A finding id is audit
#: bookkeeping, not a registry name, and the count is asserted before the drop
#: so an upstream change trips instead of silently dropping the wrong row.
DROPPED_GOLD_READINGS = {"F-lostworld-2": (1,)}


def recipe_matrix(out_dir: Path):
    """Per-recipe feature matrix + the row index for ``(pack, recipe_idx)``.

    Three blocks, prefixed so the two layers' identically-named columns stay
    apart: ``rec_`` (the Phase 0c-1 per-recipe matrix plus the Stage-B leaf
    aggregates) and ``tpl_`` (the template matrix joined on ``template_id``).
    """
    rf = pq.read_table(out_dir / "features" / "recipe_features.parquet").to_pandas()
    agg = pq.read_table(out_dir / "models" / "recipe_leaf_agg.parquet").to_pandas()
    rf = rf.merge(agg, on=["pack", "recipe_idx"], how="left",
                  validate="one_to_one")
    tnames = feature_names(out_dir)
    tf = pq.read_table(out_dir / "features" / "template_features.parquet",
                       columns=["pack", "template_id"] + tnames).to_pandas()
    tf = tf.rename(columns={n: "tpl_" + n for n in tnames})
    tnames = ["tpl_" + n for n in tnames]
    rf = rf.merge(tf, on=["pack", "template_id"], how="left", validate="many_to_one")

    skip = {"pack", "pack_group", "recipe_idx", "template_id", "system",
            "recipe_ns", "shape_role"} | set(FLAG_SOURCE_COLS) | set(ACQ_COLS)
    rnames = [c for c in rf.columns if c not in skip and c not in tnames]
    mat = rf[rnames + tnames].to_numpy(dtype=np.float32)
    names = ["rec_" + n for n in rnames] + tnames
    flags = {c: rf[c].to_numpy(dtype=np.float64) for c in
             FLAG_SOURCE_COLS + ACQ_COLS + ("leaf_agg_n",)}
    packs = rf["pack"].to_numpy()
    ridx = rf["recipe_idx"].to_numpy()
    key = {(p, int(r)): i for i, (p, r) in enumerate(zip(packs, ridx))}
    meta = {"system": rf["system"].to_numpy(),
            "ns": rf["recipe_ns"].to_numpy(),
            "template_id": rf["template_id"].to_numpy()}
    return mat, names, flags, key, meta, len(rnames) + len(tnames)


PAIR_FLAGS = ("flag_same_template", "flag_same_system", "flag_same_ns",
              "flag_d_tier", "flag_d_n_inputs", "flag_d_log_value_in",
              "flag_d_n_leaf_inputs", "flag_d_leaf_acq_min")

#: Flags that do not change sign when a and b are swapped.  An antisymmetric
#: linear model cannot use them (both orientations carry the same value with
#: opposite labels, so the weight is driven to zero); LightGBM can, because a
#: tree is not constrained to be antisymmetric.  Reported, not hidden.
SYMMETRIC_FLAGS = ("flag_same_template", "flag_same_system", "flag_same_ns")


def pair_flags(flags: dict, meta: dict, ia: np.ndarray, ib: np.ndarray) -> np.ndarray:
    def d(col):
        v = flags[col]
        return np.nan_to_num(v[ia] - v[ib], nan=0.0)
    out = np.column_stack([
        (meta["template_id"][ia] == meta["template_id"][ib]).astype(np.float64),
        (meta["system"][ia] == meta["system"][ib]).astype(np.float64),
        (meta["ns"][ia] == meta["ns"][ib]).astype(np.float64),
        d("tier"), d("n_input_slots"), d("log_value_in"),
        d("leaf_agg_n"), d("l4_leaf_acq_min")])
    return out


def load_pairs(out_dir: Path):
    """Training pairs (silver/chain/bronze) and the gold evaluation pairs.

    ``gold`` never reaches the training set — it is filtered here, once, and
    ``tests/test_phase0c2.py`` asserts the training tiers contain no gold row.
    """
    p = pq.read_table(out_dir / "labels" / "pairs.parquet").to_pandas()
    train = p[p.tier != TIER_GOLD].reset_index(drop=True)
    gold = pq.read_table(out_dir / "labels" / "pairwise_gold.parquet").to_pandas()
    gold = gold.reset_index(drop=True)
    gold["reading"] = gold.groupby("finding_id").cumcount()
    drop = np.zeros(len(gold), bool)
    for finding, readings in DROPPED_GOLD_READINGS.items():
        sel = gold.finding_id == finding
        assert sel.sum() == int(gold.loc[sel, "n_readings"].iloc[0]), \
            f"{finding}: reading count changed upstream"
        drop |= sel & gold.reading.isin(readings)
    return train, gold[~drop].reset_index(drop=True), gold[drop]


def build_pair_matrix(mat, flags, meta, key, pairs):
    ia = np.array([key[(p, int(a))] for p, a in
                   zip(pairs["pack"], pairs["a_recipe_idx"])])
    ib = np.array([key[(p, int(b))] for p, b in
                   zip(pairs["pack"], pairs["b_recipe_idx"])])
    return ia, ib


def diff_matrix(mat, flags, meta, ia, ib, fill: np.ndarray) -> np.ndarray:
    a = np.where(np.isfinite(mat[ia]), mat[ia], fill)
    b = np.where(np.isfinite(mat[ib]), mat[ib], fill)
    return np.hstack([(a - b).astype(np.float64), pair_flags(flags, meta, ia, ib)])


def antisymmetrise(x: np.ndarray, y: np.ndarray, w: np.ndarray, n_base: int):
    """Both orientations, labels flipped; symmetric flags keep their value."""
    flip = -x.copy()
    for j, name in enumerate(PAIR_FLAGS):
        if name in SYMMETRIC_FLAGS:
            flip[:, n_base + j] = x[:, n_base + j]
    return (np.vstack([x, flip]), np.concatenate([y, -y]),
            np.concatenate([w, w]))


def tier_weights(pairs) -> np.ndarray:
    w = pairs["weight"].to_numpy(dtype=np.float64)
    tiers = pairs["tier"].to_numpy()
    out = np.zeros(len(w))
    for tier, mult in TIER_MULT.items():
        sel = tiers == tier
        if sel.any():
            out[sel] = mult * w[sel] / max(w[sel].mean(), 1e-12)
    return out


def fit_ranker(x, y, w, groups, kind: str):
    """Antisymmetric pairwise model.  ``y`` is +-1, no intercept."""
    scale = np.sqrt((x ** 2).mean(axis=0))
    scale = np.where(scale > 1e-12, scale, 1.0)
    z = x / scale
    if kind == "lr":
        from sklearn.linear_model import LogisticRegression
        clf = LogisticRegression(max_iter=3000, C=1.0, fit_intercept=False,
                                 solver="lbfgs", random_state=SEED)
        clf.fit(z, (y > 0).astype(int), sample_weight=w)
        return {"kind": "lr", "scale": scale, "clf": clf}
    import lightgbm as lgb
    tr, va, inner = inner_split(y, groups)
    model = lgb.LGBMClassifier(objective="binary", n_estimators=300,
                               num_leaves=31, learning_rate=0.05,
                               min_child_samples=40, subsample=0.9,
                               subsample_freq=1, colsample_bytree=0.9,
                               random_state=SEED, n_jobs=10, verbose=-1)
    yy = (y > 0).astype(int)
    ok = va is not None and len(np.unique(yy[va])) > 1 and va.sum() >= 50
    if ok:
        model.fit(z[tr], yy[tr], sample_weight=w[tr], eval_X=z[va], eval_y=yy[va],
                  eval_sample_weight=[w[va]],
                  callbacks=[lgb.early_stopping(30, verbose=False)])
    best = getattr(model, "best_iteration_", None) or 0
    if not ok or best <= 0:
        model.fit(z, yy, sample_weight=w)
        best = 300
    return {"kind": "lgb", "scale": scale, "clf": model, "best_iteration": int(best)}


def ranker_margin(model, x: np.ndarray, n_base: int) -> np.ndarray:
    """Signed preference for a over b.  LightGBM is explicitly antisymmetrised."""
    z = x / model["scale"]
    if model["kind"] == "lr":
        return z @ model["clf"].coef_[0]
    raw = model["clf"].predict_proba(z, raw_score=True)
    flip = -x.copy()
    for j, name in enumerate(PAIR_FLAGS):
        if name in SYMMETRIC_FLAGS:
            flip[:, n_base + j] = x[:, n_base + j]
    raw_b = model["clf"].predict_proba(flip / model["scale"], raw_score=True)
    return 0.5 * (raw - raw_b)


def cost_baselines(out_dir: Path, cache_dir: Path, key: dict) -> dict:
    """Phase 0b cost, per recipe: ``value_in`` and ``value_in + step``.

    The baseline the learned ranker has to beat: rank a producer by what the
    economy layer already says it costs.  ``step`` is the pack's own
    ``step_cost`` (``--step-cost`` x median leaf seed) times ``1 + tier``,
    exactly the charge ``economy.py`` puts on running a recipe.
    """
    packs = json.loads((cache_dir / "packs.json").read_text())
    n = len(key)
    value_in = np.full(n, np.nan)
    with_step = np.full(n, np.nan)
    for slug, pack in sorted(packs.items()):
        shape = pq.read_table(out_dir / slug / "economy" / "recipe_shape.parquet",
                              columns=["recipe_idx", "value_in", "tier"])
        meta = json.loads((out_dir / slug / "economy" /
                           "economy-meta.json").read_text())
        step = float(meta["values_fitted"]["step_cost"])
        ridx = np.asarray(shape.column("recipe_idx").to_pylist())
        vin = np.asarray(shape.column("value_in").to_pylist(), dtype=np.float64)
        tier = np.asarray(shape.column("tier").to_pylist(), dtype=np.float64)
        rows = np.array([key.get((pack, int(r)), -1) for r in ridx])
        ok = rows >= 0
        value_in[rows[ok]] = vin[ok]
        with_step[rows[ok]] = vin[ok] + step * (1.0 + tier[ok])
    return {"value_in": value_in, "value_in_plus_step": with_step}


def baseline_margin(base: np.ndarray, ia: np.ndarray, ib: np.ndarray) -> np.ndarray:
    """Cheaper is better, so the margin for ``a`` is ``cost(b) - cost(a)``."""
    return np.nan_to_num(base[ib] - base[ia], nan=0.0)


def accuracy(margin: np.ndarray, label: np.ndarray) -> float:
    if len(margin) == 0:
        return float("nan")
    return float((np.sign(margin) == np.sign(label)).mean())


def stage_pairs(out_dir: Path, cache_dir: Path, quick: bool = False,
                lopo: bool = True) -> dict:
    with Timer("build the per-recipe pair matrix"):
        mat, names, flags, key, meta_arr, n_base = recipe_matrix(out_dir)
    print(f"  per-recipe matrix {mat.shape[0]:,} x {mat.shape[1]} "
          f"(+{len(PAIR_FLAGS)} pair flags)")
    train, gold, dropped = load_pairs(out_dir)
    print(f"  {len(train):,} training pairs "
          f"({', '.join(f'{t} {int((train.tier == t).sum()):,}' for t in TIER_MULT)}), "
          f"{len(gold)} gold readings kept, {len(dropped)} dropped")
    if quick:
        rng = np.random.default_rng(SEED)
        train = train.iloc[np.sort(rng.choice(len(train), 20000, replace=False))]
        train = train.reset_index(drop=True)
        print(f"  --quick: subsampled to {len(train):,} pairs")

    ia, ib = build_pair_matrix(mat, flags, meta_arr, key, train)
    fill = np.nanmedian(mat, axis=0)
    fill = np.where(np.isfinite(fill), fill, 0.0).astype(np.float32)
    with Timer("difference the pair matrix"):
        x = diff_matrix(mat, flags, meta_arr, ia, ib, fill)
    y = train["label"].to_numpy(dtype=np.float64)
    w = tier_weights(train)
    groups = train["pack_group"].to_numpy()
    tiers = train["tier"].to_numpy()
    print(f"  pair matrix {x.shape[0]:,} x {x.shape[1]}")

    base = cost_baselines(out_dir, cache_dir, key)
    result = {"n_train_pairs": int(len(train)), "n_features": int(x.shape[1]),
              "folds": [], "pooled": {}, "baselines": {}, "gold": {}}

    for bname, bvec in base.items():
        for tier in (TIER_SILVER, TIER_CHAIN, TIER_BRONZE):
            sel = tiers == tier
            m = baseline_margin(bvec, ia[sel], ib[sel])
            result["baselines"].setdefault(bname, {})[tier] = accuracy(m, y[sel])

    labelled_groups = sorted(set(groups.tolist()))
    models_full = {}
    for kind in ("lr", "lgb"):
        xa, ya, wa = antisymmetrise(x, y, group_capped_weights(w, groups), n_base)
        ga = np.concatenate([groups, groups])
        with Timer(f"ranker fit {kind} (all groups, for the gold pass + export)"):
            models_full[kind] = fit_ranker(xa, ya, wa, ga, kind)

    oof_margin = {k: np.full(len(train), np.nan) for k in ("lr", "lgb")}
    fold_models: dict = {}
    for held in (labelled_groups if lopo else []):
        tr = groups != held
        te = ~tr
        if te.sum() < 20 or tr.sum() < 100:
            continue
        fold = {"group": held, "n_train": int(tr.sum()), "n_test": int(te.sum())}
        for kind in ("lr", "lgb"):
            xa, ya, wa = antisymmetrise(x[tr], y[tr],
                                        group_capped_weights(w[tr], groups[tr]),
                                        n_base)
            ga = np.concatenate([groups[tr], groups[tr]])
            with Timer(f"ranker LOPO fit {kind} (hold out {held})", quiet=True) as t:
                model = fit_ranker(xa, ya, wa, ga, kind)
            fold_models[(kind, held)] = model
            m = ranker_margin(model, x[te], n_base)
            oof_margin[kind][te] = m
            fold[kind] = {"fit_seconds": round(t.seconds, 2)}
            for tier in (TIER_SILVER, TIER_CHAIN, TIER_BRONZE):
                sel = tiers[te] == tier
                fold[kind][tier] = {"n": int(sel.sum()),
                                    "acc": accuracy(m[sel], y[te][sel])}
        result["folds"].append(fold)

    for kind in ("lr", "lgb"):
        pooled = {}
        for tier in (TIER_SILVER, TIER_CHAIN, TIER_BRONZE):
            sel = (tiers == tier) & np.isfinite(oof_margin[kind])
            pooled[tier] = {"n": int(sel.sum()),
                            "acc": accuracy(oof_margin[kind][sel], y[sel])}
        result["pooled"][kind] = pooled

    # ---- gold: evaluation only, both under the all-groups model and under the
    # LOPO model that never saw the gold pair's own pack group.
    gia = np.array([key[(p, int(a))] for p, a in
                    zip(gold["pack"], gold["preferred_recipe_idx"])])
    gib = np.array([key[(p, int(b))] for p, b in
                    zip(gold["pack"], gold["chosen_recipe_idx"])])
    gx = diff_matrix(mat, flags, meta_arr, gia, gib, fill)
    gw = gold["weight"].to_numpy(dtype=np.float64)
    rows = []
    for kind in ("lr", "lgb"):
        m_full = ranker_margin(models_full[kind], gx, n_base)
        m_lopo = np.full(len(gold), np.nan)
        for held in sorted(set(gold.pack_group.tolist())):
            sel = (gold.pack_group == held).to_numpy()
            model = fold_models.get((kind, held))
            if model is None:
                tr = groups != held
                if tr.sum() < 100:
                    continue
                xa, ya, wa = antisymmetrise(
                    x[tr], y[tr], group_capped_weights(w[tr], groups[tr]), n_base)
                ga = np.concatenate([groups[tr], groups[tr]])
                with Timer(f"gold LOPO fit {kind} ({held})", quiet=True):
                    model = fit_ranker(xa, ya, wa, ga, kind)
            m_lopo[sel] = ranker_margin(model, gx[sel], n_base)
        result["gold"][kind] = {
            "n": int(len(gold)),
            "acc": accuracy(m_full, np.ones(len(gold))),
            "weighted_acc": float((gw * (m_full > 0)).sum() / gw.sum()),
            "acc_lopo": accuracy(m_lopo[np.isfinite(m_lopo)],
                                 np.ones(int(np.isfinite(m_lopo).sum()))),
            "weighted_acc_lopo": float(
                (gw[np.isfinite(m_lopo)] * (m_lopo[np.isfinite(m_lopo)] > 0)).sum()
                / max(gw[np.isfinite(m_lopo)].sum(), 1e-12)),
            "margins": [float(v) for v in m_full],
            "margins_lopo": [float(v) for v in m_lopo]}
    for bname, bvec in base.items():
        m = baseline_margin(bvec, gia, gib)
        result["gold"][bname] = {
            "n": int(len(gold)), "acc": accuracy(m, np.ones(len(gold))),
            "weighted_acc": float((gw * (m > 0)).sum() / gw.sum()),
            "margins": [float(v) for v in m]}
    result["gold_index"] = [
        {"finding_id": r.finding_id, "reading": int(r.reading),
         "pack_group": r.pack_group, "category": r.category,
         "severity": r.severity, "weight": float(r.weight),
         "preferred": int(r.preferred_recipe_idx), "chosen": int(r.chosen_recipe_idx)}
        for r in gold.itertuples()]
    result["dropped_gold"] = [
        {"finding_id": r.finding_id, "reading": int(r.reading)}
        for r in dropped.itertuples()]
    result["_models"] = models_full
    result["_pair_names"] = names + list(PAIR_FLAGS)
    result["_n_base"] = n_base
    result["_fill"] = fill
    result["_mat"] = mat
    result["_flags"] = flags
    result["_meta"] = meta_arr
    result["_key"] = key
    result["_train"] = train
    result["_ia"], result["_ib"] = ia, ib
    result["_x"] = x
    return result


def print_pairs(res: dict) -> None:
    print("\n  pairwise accuracy — leave-one-pack-group-out")
    print("  {:<26} {:>16} {:>16} {:>16}".format(
        "group", "silver lr/lgb", "chain lr/lgb", "bronze lr/lgb"))
    for f in res["folds"]:
        cells = []
        for tier in (TIER_SILVER, TIER_CHAIN, TIER_BRONZE):
            a, b = f["lr"][tier], f["lgb"][tier]
            cells.append("{:>6} {:.3f}/{:.3f}".format(
                a["n"], a["acc"] if a["n"] else float("nan"),
                b["acc"] if b["n"] else float("nan")))
        print("  {:<26} {}".format(f["group"][:26], " ".join(cells)))
    for kind in ("lr", "lgb"):
        p = res["pooled"][kind]
        print("    POOLED {:<4} ".format(kind) + "  ".join(
            f"{t} {p[t]['acc']:.3f} (n={p[t]['n']:,})"
            for t in (TIER_SILVER, TIER_CHAIN, TIER_BRONZE)))
    print("\n  cost-only baselines (no learning, whole corpus)")
    for bname, per in res["baselines"].items():
        print("    {:<22} ".format(bname) + "  ".join(
            f"{t} {per[t]:.3f}" for t in (TIER_SILVER, TIER_CHAIN, TIER_BRONZE)))
    print("\n  gold pairs (evaluation only, n={})".format(res["gold"]["lr"]["n"]))
    for kind, g in res["gold"].items():
        extra = ("  LOPO {:.3f} (w {:.3f})".format(g["acc_lopo"], g["weighted_acc_lopo"])
                 if "acc_lopo" in g else "")
        print(f"    {kind:<22} acc {g['acc']:.3f}  weighted {g['weighted_acc']:.3f}{extra}")


# ============================================ Stage C — LeafAcquirability

def leaf_weight_vector(model: dict, names: list[str], leaf_cols: list[str]):
    """Weights the ranker put on the aggregated leaf columns.

    The LR arm is linear in the *scaled* pair differences, so its weight on a
    raw column is ``coef / scale``.  Only the ``_mean`` aggregates are used:
    ``mean_c(a) - mean_c(b)`` is the per-leaf property differenced, whereas the
    ``_min`` aggregate is an order statistic of a whole recipe and has no
    per-leaf reading.  The ``_min`` weights are reported alongside, so the
    reader can see how much of the ranker's leaf signal is left on the table.
    """
    coef = model["clf"].coef_[0] / model["scale"]
    idx = {n: i for i, n in enumerate(names)}
    w_mean = np.array([coef[idx["rec_" + c + "_mean"]] for c in leaf_cols])
    w_min = np.array([coef[idx["rec_" + c + "_min"]] for c in leaf_cols])
    acq_flag = float(coef[idx["flag_d_leaf_acq_min"]]) \
        if "flag_d_leaf_acq_min" in idx else float("nan")
    return w_mean, w_min, acq_flag


def supervised_seeds(out_dir: Path, slugs, leaf_cols: list[str],
                     w_mean: np.ndarray, spread: float = 2.0) -> dict:
    """Score every leaf in every pack with the supervised weights.

    ``s = w . z`` on the raw columns, standardised over the pack's own leaves
    (the per-pack convention the whole economy layer uses, and the analogue of
    Phase 0b's per-pack rank normalisation), then

        acquirability = sigma(s_std),   seed = K ** (1 - acquirability)

    so the band is the same ``[1, K]`` ``economy.py --seed-spread`` defines and
    the two seeds are directly comparable.
    """
    from scipy.stats import spearmanr
    out = {}
    for slug in slugs:
        path = out_dir / slug / "economy" / "leaf_features.parquet"
        t = pq.read_table(path, columns=["base_id", "acquirability",
                                         "seed_value"] + leaf_cols)
        ids = np.asarray(t.column("base_id").to_pylist())
        z = np.column_stack([np.asarray(t.column(c).to_pylist(), dtype=np.float64)
                             for c in leaf_cols])
        s = z @ w_mean
        mu, sd = float(s.mean()), float(s.std())
        sd = sd if sd > 1e-12 else 1.0
        acq = 1.0 / (1.0 + np.exp(-(s - mu) / sd))
        seed = spread ** (1.0 - acq)
        unsup = np.asarray(t.column("acquirability").to_pylist(), dtype=np.float64)
        rho = float(spearmanr(acq, unsup).statistic) if len(acq) > 2 else float("nan")
        out[slug] = {"ids": ids, "acq": acq, "seed": seed, "unsup_acq": unsup,
                     "unsup_seed": np.asarray(t.column("seed_value").to_pylist(),
                                              dtype=np.float64),
                     "spearman": rho, "n": int(len(ids)), "mean": mu, "std": sd}
    return out


def producer_costs(cache_dir: Path, out_dir: Path, slug: str,
                   sup: dict, spread: float = 2.0):
    """Re-solve the pack's economy with the supervised leaf seed.

    ``economy.py`` is called as a library — ``build_graph`` / ``compute_shape``
    / ``incidence`` / ``solve_tiers`` / ``compute_reliability`` /
    ``compute_values`` — with only the seed array swapped, so the number
    reported is a real producer cost out of the same solver Phase 0b used, not
    a re-implementation.  Returns ``(cost_unsup, cost_sup)`` per recipe, each
    ``sum(input value x amount) + step cost`` = what committing to that
    producer costs once its inputs are priced.
    """
    import economy

    g = economy.build_graph(cache_dir, out_dir, slug)
    sh = economy.compute_shape(g, out_dir, 256)
    inc = economy.incidence(g)
    ti = economy.solve_tiers(g, inc)
    rl = economy.compute_reliability(g, inc, sh, cache_dir)
    meta = json.loads((out_dir / slug / "economy" /
                       "economy-meta.json").read_text())
    step = float(meta["values_fitted"]["step_cost"])
    step_arg = step / max(float(np.median(
        np.asarray(sup["unsup_seed"], dtype=np.float64))), 1e-12)

    values = pq.read_table(out_dir / slug / "economy" / "item_values.parquet",
                           columns=["base_id", "acquirability"])
    vid = np.asarray(values.column("base_id").to_pylist())
    vacq = np.asarray(values.column("acquirability").to_pylist(), dtype=np.float64)
    pos = np.searchsorted(vid, g.items)
    pos = np.clip(pos, 0, max(vid.size - 1, 0))
    hit = (vid.size > 0) & (vid[pos] == g.items)
    base = np.ones(g.n_items)
    base[hit] = spread ** (1.0 - np.nan_to_num(vacq[pos][hit], nan=0.5))

    def seeded(leaf_seed: np.ndarray, leaf_ids: np.ndarray) -> np.ndarray:
        seed = base.copy()
        p = np.searchsorted(g.items, leaf_ids)
        p = np.clip(p, 0, max(g.n_items - 1, 0))
        ok = (g.n_items > 0) & (g.items[p] == leaf_ids)
        seed[p[ok]] = leaf_seed[ok]
        return seed

    out = {}
    for tag, leaf_seed in (("unsup", sup["unsup_seed"]), ("sup", sup["seed"])):
        seed = seeded(np.asarray(leaf_seed, dtype=np.float64), sup["ids"])
        v = economy.compute_values(g, inc, sh, ti, seed, ring_seed=seed,
                                   reliability=rl.recipe, step_cost=step_arg)
        out[tag] = v.in_value + v.step_cost
    return out


def stage_leaf(out_dir: Path, cache_dir: Path, pairs: dict) -> dict:
    """Supervised LeafAcquirability + the round-006 acceptance cases."""
    agg_meta = json.loads((out_dir / "models" /
                           "recipe-leaf-agg-meta.json").read_text())
    leaf_cols = agg_meta["columns"]
    names = pairs["_pair_names"]
    w_mean, w_min, acq_flag = leaf_weight_vector(pairs["_models"]["lr"], names,
                                                 leaf_cols)
    packs = json.loads((cache_dir / "packs.json").read_text())
    slugs = sorted(packs)
    with Timer("score every leaf in every pack"):
        sup = supervised_seeds(out_dir, slugs, leaf_cols, w_mean)

    res = {"leaf_columns": leaf_cols,
           "weights_mean": {c: float(v) for c, v in zip(leaf_cols, w_mean)},
           "weights_min": {c: float(v) for c, v in zip(leaf_cols, w_min)},
           "weight_on_unsupervised_score": acq_flag,
           "spearman": {s: round(sup[s]["spearman"], 4) for s in slugs},
           "n_leaves": {s: sup[s]["n"] for s in slugs},
           "cases": []}
    rho = np.array([sup[s]["spearman"] for s in slugs if sup[s]["n"] > 2])
    res["spearman_summary"] = {"mean": float(np.nanmean(rho)),
                               "min": float(np.nanmin(rho)),
                               "max": float(np.nanmax(rho))}

    gold = pq.read_table(out_dir / "labels" / "pairwise_gold.parquet").to_pandas()
    gold = gold.reset_index(drop=True)
    gold["reading"] = gold.groupby("finding_id").cumcount()
    drop = np.zeros(len(gold), bool)
    for finding, readings in DROPPED_GOLD_READINGS.items():
        drop |= (gold.finding_id == finding) & gold.reading.isin(readings)
    gold = gold[~drop]
    slug_of = {v: k for k, v in packs.items()}
    for pack, rows in gold.groupby("pack", sort=True):
        slug = slug_of[pack]
        with Timer(f"re-solve {slug} with the supervised seed"):
            cost = producer_costs(cache_dir, out_dir, slug, sup[slug])
        for r in rows.itertuples():
            entry = {"finding_id": r.finding_id, "reading": int(r.reading),
                     "pack_group": r.pack_group,
                     "chosen": int(r.chosen_recipe_idx),
                     "preferred": int(r.preferred_recipe_idx)}
            for tag in ("unsup", "sup"):
                c = float(cost[tag][r.chosen_recipe_idx])
                p = float(cost[tag][r.preferred_recipe_idx])
                entry[tag] = {"chosen_cost": c, "preferred_cost": p,
                              "ratio": float(c / p) if p > 0 else float("inf"),
                              "preferred_wins": bool(p < c)}
            res["cases"].append(entry)
    for tag in ("unsup", "sup"):
        wins = sum(1 for c in res["cases"] if c[tag]["preferred_wins"])
        res.setdefault("case_summary", {})[tag] = {
            "n": len(res["cases"]), "preferred_wins": wins}
    return res


def print_leaf(res: dict) -> None:
    print("\n  supervised leaf weights (LR ranker arm, raw-column scale)")
    for c in res["leaf_columns"]:
        print("    {:<22} mean {:>+10.4f}   min {:>+10.4f}".format(
            c, res["weights_mean"][c], res["weights_min"][c]))
    print("    {:<22} {:>+10.4f}".format("(unsupervised score)",
                                         res["weight_on_unsupervised_score"]))
    s = res["spearman_summary"]
    print(f"\n  Spearman(supervised acquirability, Phase 0b acquirability) over "
          f"{len(res['spearman'])} packs: mean {s['mean']:+.3f}  "
          f"min {s['min']:+.3f}  max {s['max']:+.3f}")
    print("\n  round-006 acceptance cases — producer cost (input value + step)")
    print("  {:<26} {:>5} {:>11} {:>11} {:>7}  {:>11} {:>11} {:>7}".format(
        "finding", "read", "unsup chos", "unsup pref", "wins?",
        "sup chosen", "sup pref", "wins?"))
    for c in res["cases"]:
        print("  {:<26} {:>5} {:>11.4g} {:>11.4g} {:>7}  {:>11.4g} {:>11.4g} {:>7}".format(
            c["finding_id"][:26], c["reading"],
            c["unsup"]["chosen_cost"], c["unsup"]["preferred_cost"],
            "yes" if c["unsup"]["preferred_wins"] else "NO",
            c["sup"]["chosen_cost"], c["sup"]["preferred_cost"],
            "yes" if c["sup"]["preferred_wins"] else "NO"))
    for tag in ("unsup", "sup"):
        s = res["case_summary"][tag]
        print(f"    {tag}: preferred cheaper in {s['preferred_wins']}/{s['n']} cases")


# ----------------------------------------------------------------------- main


def stage_junk(args, df: pd.DataFrame, names: list[str]) -> dict:
    print("\n== Stage A1 — TemplateRoleModel, junk head")
    res = lopo_binary(df, names, COL_JUNK_TARGET, "label_weight")
    print_binary(res)
    return res


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out-dir", default=str(HERE / "out"))
    ap.add_argument("--cache-dir", default=str(HERE / "cache"))
    ap.add_argument("--stage", default="junk")
    ap.add_argument("--quick", action="store_true",
                    help="3 pack groups only — smoke test, not a result")
    args = ap.parse_args(argv)
    out_dir = Path(args.out_dir)
    (out_dir / "models").mkdir(parents=True, exist_ok=True)

    with Timer("load template matrix"):
        df, names = load_templates(out_dir)
    print(f"  {len(df):,} templates x {len(names)} features, "
          f"{df[COL_JUNK_TARGET].notna().sum():,} verdict-bearing, "
          f"{(df.role_target.fillna('') != '').sum():,} role-bearing")
    if args.quick:
        keep = sorted(set(df.loc[df[COL_JUNK_TARGET].notna(), "pack_group"]))[:3]
        df = df[df.pack_group.isin(keep)].reset_index(drop=True)
        print(f"  --quick: {keep} -> {len(df):,} rows")

    results = {}
    if args.stage in ("junk", "recipe", "all"):
        results["junk"] = stage_junk(args, df, names)
    if args.stage in ("recipe", "all"):
        print("\n== Stage A3 — template predictions at recipe level")
        results["recipe_level"] = recipe_level(out_dir, df, results["junk"]["oof"])
        print_recipe_level(results["recipe_level"])
    if args.stage in ("pairs", "all"):
        print("\n== Stage B — ProducerRanker (pairwise)")
        results["pairs"] = stage_pairs(out_dir, Path(args.cache_dir), args.quick)
        print_pairs(results["pairs"])
    if args.stage in ("leaf", "all"):
        print("\n== Stage C — LeafAcquirability (supervised)")
        if "pairs" not in results:
            results["pairs"] = stage_pairs(out_dir, Path(args.cache_dir),
                                           args.quick, lopo=False)
        results["leaf"] = stage_leaf(out_dir, Path(args.cache_dir),
                                     results["pairs"])
        print_leaf(results["leaf"])
    if args.stage in ("zeroshot", "all"):
        print("\n== Stage A4 — new-mod zero-shot")
        results["zero_shot"] = zero_shot(out_dir, df, names)
        print_zero_shot(results["zero_shot"])
    if args.stage in ("ablation", "all"):
        print("\n== Stage A5 — layer ablations and importances")
        layers = feature_layers(out_dir)
        results["ablations"] = ablations(df, names, layers)
        base = results.get("junk") or lopo_binary(df, names, COL_JUNK_TARGET,
                                                  "label_weight", quiet=True)
        print("\n  LOPO pooled AUC, one layer removed")
        print("  {:<22} {:>9} {:>9} {:>9} {:>9}".format(
            "variant", "lr AUC", "d(lr)", "lgb AUC", "d(lgb)"))
        b_lr = base["pooled"]["lr"]["auc"]
        b_gb = base["pooled"]["lgb"]["auc"]
        print("  {:<22} {:>9.3f} {:>9} {:>9.3f} {:>9}".format(
            "all 129 features", b_lr, "-", b_gb, "-"))
        for tag, v in results["ablations"].items():
            a_lr = v["pooled"]["lr"]["auc"]
            a_gb = v["pooled"]["lgb"]["auc"]
            print("  {:<22} {:>9.3f} {:>+9.3f} {:>9.3f} {:>+9.3f}".format(
                f"drop {tag} (-{v['dropped']})", a_lr, a_lr - b_lr,
                a_gb, a_gb - b_gb))
        results["importances"] = importances(df, names)
        print_importances(results["importances"])
    if args.stage in ("role", "all"):
        print("\n== Stage A2 — TemplateRoleModel, role head")
        results["role"] = lopo_multiclass(df, names)
        print_multiclass(results["role"])

    metrics = {k: {kk: vv for kk, vv in v.items()
                   if kk not in ("oof", "mask") and not kk.startswith("_")}
               for k, v in results.items()}
    metrics["timings"] = TIMINGS
    # A --quick run is a smoke test, not a result: it writes under its own
    # prefix so it can never overwrite the metrics the report and the export
    # read.
    prefix = "metrics-quick" if args.quick else "metrics"
    write_json(out_dir / "models" / f"{prefix}-{args.stage}.json", metrics)
    print(f"\n  total {sum(t['seconds'] for t in TIMINGS):.1f}s")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
