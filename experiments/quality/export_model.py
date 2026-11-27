#!/usr/bin/env python
"""Phase 0c-2 — export the trained models as the Java port's contract.

    ./.venv/bin/python export_model.py

Refits the three models over **all** labelled pack groups (the LOPO folds in
``train_eval.py`` measure generalisation; the exported model is the one that
has seen everything), and writes ``out/models/``:

| file | what |
|---|---|
| ``feature-spec.json`` | ordered feature names, layer, fill/mean/std, hash spec |
| ``template-role.json`` | junk head + role head, LR and LightGBM arms |
| ``producer-ranker.json`` | the pairwise ranker, LR and LightGBM arms |
| ``leaf-acquirability.json`` | the supervised leaf scorer |
| ``token-classes.json`` | the Phase 0a prior, version-stamped |
| ``golden-sample.json`` | rows + expected outputs for the Java parity test |
| ``manifest.json`` | versions, date, data hash, metrics, gates, choices |

``predict_reference.py`` reads **only** these files and reproduces every
number in ``golden-sample.json`` with numpy alone.  That round trip is the
contract the Java inference has to match.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
import time
from datetime import date
from pathlib import Path

import numpy as np
import pyarrow.parquet as pq

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import train_eval as te  # noqa: E402
from common import write_json  # noqa: E402
from labels import COL_JUNK_TARGET  # noqa: E402

MODEL_VERSION = "0c2.1"

#: A LightGBM arm is exported as the default only when it beats the linear one
#: by more than this on the fold-averaged headline metric; below it the linear
#: model wins on the tie-break that matters here — a Java port of a dot product
#: is a dot product, a Java port of 300 trees is a tree walker.
LR_TOLERANCE = 0.02

#: Hard cap per exported JSON.
MAX_JSON_BYTES = 5 * 1024 * 1024
MAX_GOLDEN_BYTES = 3 * 1024 * 1024


# ------------------------------------------------------------------ dumping


def dump_lr(model: dict, names: list[str], *, standardised: bool = True) -> dict:
    clf = model["clf"]
    coef = np.atleast_2d(clf.coef_)
    out = {"kind": "lr", "features": names,
           "coef": [[float(v) for v in row] for row in coef],
           "intercept": [float(v) for v in np.atleast_1d(clf.intercept_)]
           if getattr(clf, "fit_intercept", True) else [0.0] * coef.shape[0]}
    if standardised:
        out["fill"] = [float(v) for v in model["fill"]]
        out["mean"] = [float(v) for v in model["mean"]]
        out["std"] = [float(v) for v in model["std"]]
    else:
        out["scale"] = [float(v) for v in model["scale"]]
    return out


def flatten_tree(node: dict) -> dict:
    """LightGBM's nested tree -> flat arrays a Java loop can walk.

    ``left``/``right`` hold a child node index when >= 0 and ``-(leaf+1)``
    when the child is a leaf.  Only numerical ``<=`` splits occur (there are no
    categorical features in this matrix); anything else raises here rather than
    silently exporting a model the reference cannot reproduce.
    """
    split_feature, threshold, default_left = [], [], []
    left, right, leaf_value = [], [], []

    def walk(n) -> int:
        if "leaf_value" in n:
            leaf_value.append(float(n["leaf_value"]))
            return -len(leaf_value)
        if n.get("decision_type", "<=") != "<=":
            raise ValueError(f"unsupported split {n.get('decision_type')!r}")
        idx = len(split_feature)
        split_feature.append(int(n["split_feature"]))
        threshold.append(float(n["threshold"]))
        default_left.append(bool(n.get("default_left", False)))
        left.append(0)
        right.append(0)
        left[idx] = walk(n["left_child"])
        right[idx] = walk(n["right_child"])
        return idx

    root = walk(node)
    return {"root": root, "split_feature": split_feature, "threshold": threshold,
            "default_left": [int(v) for v in default_left],
            "left": left, "right": right, "leaf_value": leaf_value}


def dump_lgb(model: dict, names: list[str], *, standardised: bool = True,
             n_classes: int = 1) -> dict:
    booster = model["clf"].booster_
    raw = booster.dump_model(num_iteration=model.get("best_iteration"))
    trees = [flatten_tree(t["tree_structure"]) for t in raw["tree_info"]]
    out = {"kind": "lgb", "features": names, "n_trees": len(trees),
           "n_classes": int(n_classes), "trees": trees,
           "objective": raw.get("objective", "")}
    if standardised:
        out["fill"] = [float(v) for v in model["fill"]]
    else:
        out["scale"] = [float(v) for v in model["scale"]]
    return out


def sized(obj) -> int:
    return len(json.dumps(obj, separators=(",", ":")).encode("utf-8"))


def trim_to_budget(dump: dict, budget: int, label: str) -> dict:
    """Drop trailing boosting rounds until the dump fits.  Reported, not silent."""
    dropped = 0
    n_classes = max(int(dump.get("n_classes", 1)), 1)
    while sized(dump) > budget and len(dump["trees"]) > n_classes:
        del dump["trees"][-n_classes:]
        dump["n_trees"] = len(dump["trees"])
        dropped += n_classes
    if dropped:
        dump["trimmed_trees"] = dropped
        print(f"    ! {label}: dropped {dropped} trailing trees to fit "
              f"{budget / 1e6:.0f} MB")
    return dump


# ------------------------------------------------------------------- fitting


def fit_heads(df, names):
    """Final junk and role heads over every labelled pack group."""
    out = {}
    mask = df[COL_JUNK_TARGET].notna().to_numpy()
    x = df.loc[mask, names].to_numpy(dtype=np.float64)
    y = df.loc[mask, COL_JUNK_TARGET].to_numpy(dtype=np.float64)
    g = df.loc[mask, "pack_group"].to_numpy()
    w = te.group_capped_weights(df.loc[mask, "label_weight"].to_numpy(float), g)
    with te.Timer("final junk head — lr"):
        out["junk_lr"] = te.fit_lr(x, y, w)
    with te.Timer("final junk head — lgb"):
        out["junk_lgb"] = te.fit_lgb(x, y, w, g)

    role = df["role_target"].fillna("").to_numpy()
    rmask = np.isin(role, te.ROLE_CLASSES)
    code = {c: i for i, c in enumerate(te.ROLE_CLASSES)}
    xr = df.loc[rmask, names].to_numpy(dtype=np.float64)
    yr = np.array([code[r] for r in role[rmask]])
    gr = df.loc[rmask, "pack_group"].to_numpy()
    wr = te.group_capped_weights(df.loc[rmask, "role_weight"].to_numpy(float), gr)
    with te.Timer("final role head — lr"):
        out["role_lr"] = te.fit_lr(xr, yr, wr, multiclass=True)
    with te.Timer("final role head — lgb"):
        out["role_lgb"] = te.fit_lgb(xr, yr, wr, gr, multiclass=True,
                                     n_classes=len(te.ROLE_CLASSES),
                                     n_estimators=150)
    out["role_classes"] = [te.ROLE_CLASSES[i] for i in out["role_lr"]["clf"].classes_]
    out["role_classes_lgb"] = [te.ROLE_CLASSES[i]
                               for i in out["role_lgb"]["clf"].classes_]
    return out


def data_hash(out_dir: Path) -> str:
    """sha1 over the sizes + sha1 of every input table this phase consumed."""
    parts = []
    for rel in ("features/template_features.parquet",
                "features/recipe_features.parquet",
                "features/feature-catalog.json",
                "labels/template_labels.parquet",
                "labels/recipe_labels.parquet",
                "labels/pairs.parquet",
                "labels/pairwise_gold.parquet",
                "models/recipe_leaf_agg.parquet",
                "token-classes.json"):
        path = out_dir / rel
        h = hashlib.sha1(path.read_bytes()).hexdigest()
        parts.append(f"{rel}:{path.stat().st_size}:{h}")
    return hashlib.sha1("\n".join(parts).encode()).hexdigest()


# -------------------------------------------------------------- golden sample


def golden_sample(out_dir: Path, df, names, heads, pairs, leaf, seed=te.SEED):
    """Rows + expected outputs for the Java parity test.

    Every feature value is rounded to 6 decimals *before* the expected outputs
    are computed, so the file is self-contained: a port that reads these exact
    numbers must reproduce these exact outputs.
    """
    rng = np.random.default_rng(seed)

    groups = df["pack_group"].to_numpy()
    uniq = sorted(set(groups.tolist()))
    per = max(1, 1000 // len(uniq))
    picks = []
    for g in uniq:
        idx = np.flatnonzero(groups == g)
        picks.append(rng.choice(idx, min(per, len(idx)), replace=False))
    picks = np.concatenate(picks)
    if len(picks) < 1000:
        rest = np.setdiff1d(np.arange(len(df)), picks)
        picks = np.concatenate([picks, rng.choice(rest, 1000 - len(picks),
                                                  replace=False)])
    picks = np.sort(picks[:1000])

    raw = df.iloc[picks][names].to_numpy(dtype=np.float64)
    raw = np.where(np.isfinite(raw), np.round(raw, 6), np.nan)
    rows = []
    p_junk_lr = te.predict(heads["junk_lr"], raw)[:, 1]
    p_junk_gb = te.predict(heads["junk_lgb"], raw)[:, 1]
    p_role_lr = te.predict(heads["role_lr"], raw)
    p_role_gb = te.predict(heads["role_lgb"], raw)
    packs = df["pack"].to_numpy()[picks]
    tids = df["template_id"].to_numpy()[picks]
    for i in range(len(picks)):
        rows.append({
            "pack": str(packs[i]), "template_id": str(tids[i]),
            "x": [None if not np.isfinite(v) else float(v) for v in raw[i]],
            "junk_lr": float(p_junk_lr[i]), "junk_lgb": float(p_junk_gb[i]),
            "role_lr": [float(v) for v in p_role_lr[i]],
            "role_lgb": [float(v) for v in p_role_gb[i]]})

    # ---- 500 pairs
    n_pairs = min(500, len(pairs["_x"]))
    sel = np.sort(rng.choice(len(pairs["_x"]), n_pairs, replace=False))
    px = np.round(pairs["_x"][sel], 6)
    m_lr = te.ranker_margin(pairs["_models"]["lr"], px, pairs["_n_base"])
    m_gb = te.ranker_margin(pairs["_models"]["lgb"], px, pairs["_n_base"])
    train = pairs["_train"]
    pair_rows = [{
        "pack": str(train["pack"].iloc[int(j)]),
        "a": int(train["a_recipe_idx"].iloc[int(j)]),
        "b": int(train["b_recipe_idx"].iloc[int(j)]),
        "x": [float(v) for v in px[k]],
        "margin_lr": float(m_lr[k]), "margin_lgb": float(m_gb[k])}
        for k, j in enumerate(sel)]

    # ---- 300 leaves
    leaf_cols = leaf["leaf_columns"]
    w_mean = np.array([leaf["weights_mean"][c] for c in leaf_cols])
    packs_json = json.loads((HERE / "cache" / "packs.json").read_text())
    leaf_rows = []
    slugs = sorted(packs_json)
    for slug in slugs:
        t = pq.read_table(out_dir / slug / "economy" / "leaf_features.parquet",
                          columns=["base_id"] + leaf_cols)
        z = np.round(np.column_stack(
            [np.asarray(t.column(c).to_pylist(), dtype=np.float64)
             for c in leaf_cols]), 6)
        s = z @ w_mean
        mu, sd = float(s.mean()), float(s.std())
        sd = sd if sd > 1e-12 else 1.0
        acq = 1.0 / (1.0 + np.exp(-(s - mu) / sd))
        seed_v = leaf["spread"] ** (1.0 - acq)
        want = -(-300 // len(slugs))      # ceil, so the 300 target is reached
        take = rng.choice(len(s), min(max(1, want), len(s)), replace=False)
        for i in take:
            leaf_rows.append({"pack": packs_json[slug], "slug": slug,
                              "z": [float(v) for v in z[i]],
                              "pack_mean": mu, "pack_std": sd,
                              "acquirability": float(acq[i]),
                              "seed": float(seed_v[i])})
    leaf_rows = leaf_rows[:300]

    return {"version": MODEL_VERSION, "seed": int(seed),
            "n_pack_groups": len(uniq),
            "template_features": names,
            "pair_features": pairs["_pair_names"],
            "leaf_features": leaf_cols,
            "role_classes": heads["role_classes"],
            "rows": rows, "pairs": pair_rows, "leaves": leaf_rows}


# ------------------------------------------------------------------- driver


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out-dir", default=str(HERE / "out"))
    ap.add_argument("--cache-dir", default=str(HERE / "cache"))
    args = ap.parse_args(argv)
    out_dir, cache_dir = Path(args.out_dir), Path(args.cache_dir)
    dest = out_dir / "models"
    dest.mkdir(parents=True, exist_ok=True)
    t0 = time.time()

    df, names = te.load_templates(out_dir)
    layers = te.feature_layers(out_dir)
    heads = fit_heads(df, names)
    pairs = te.stage_pairs(out_dir, cache_dir, lopo=False)
    leaf = te.stage_leaf(out_dir, cache_dir, pairs)
    leaf["spread"] = 2.0

    catalog = json.loads((out_dir / "features" /
                          "feature-catalog.json").read_text())
    spec = {
        "version": MODEL_VERSION,
        "template_features": {
            "names": names,
            "layers": [layers[n] for n in names],
            "fill": [float(v) for v in heads["junk_lr"]["fill"]],
            "mean": [float(v) for v in heads["junk_lr"]["mean"]],
            "std": [float(v) for v in heads["junk_lr"]["std"]],
            "nan_policy": "replace with fill[i], then (x - mean[i]) / std[i] "
                          "for the linear arm; LightGBM reads the filled value"},
        "pair_features": {
            "names": pairs["_pair_names"],
            "n_base": int(pairs["_n_base"]),
            "flags": list(te.PAIR_FLAGS),
            "symmetric_flags": list(te.SYMMETRIC_FLAGS),
            "recipe_fill": [float(v) for v in pairs["_fill"]],
            "construction": "x = fill(f(a)) - fill(f(b)) over the base block, "
                            "then the 8 pair flags; scale by scale[i]; no "
                            "intercept.  The LightGBM arm is antisymmetrised at "
                            "inference: 0.5 * (raw(a,b) - raw(b,a)), where the "
                            "swap negates every column except symmetric_flags."},
        "leaf_features": {
            "names": leaf["leaf_columns"],
            "excluded": ["acquirability", "seed_value", "n_forms_of_material"],
            "construction": "s = w . z on the raw columns; standardise s over "
                            "the pack's own leaves; acquirability = sigmoid; "
                            "seed = spread ** (1 - acquirability)"},
        "hash": catalog["hash"],
        "match_strategies": catalog["match_strategies"],
        "shape_roles": catalog["shape_roles"],
    }
    write_json(dest / "feature-spec.json", spec)

    role_dump_lr = dump_lr(heads["role_lr"], names)
    role_dump_lr["classes"] = heads["role_classes"]
    role_dump_gb = trim_to_budget(
        dump_lgb(heads["role_lgb"], names, n_classes=len(heads["role_classes_lgb"])),
        MAX_JSON_BYTES // 2, "role lgb")
    role_dump_gb["classes"] = heads["role_classes_lgb"]

    metrics = {}
    for path in sorted(dest.glob("metrics-*.json")):
        metrics[path.stem] = json.loads(path.read_text())

    def pooled(stage, key, kind, sub=None):
        """Look the metric up in ``metrics-all`` first, then the per-stage file."""
        for name in (f"metrics-{stage}", "metrics-all"):
            try:
                node = metrics[name][key]["pooled"][kind]
                return node[sub] if sub else node
            except (KeyError, TypeError):
                continue
        return None

    junk_lr_auc = pooled("junk", "junk", "lr", "auc")
    junk_gb_auc = pooled("junk", "junk", "lgb", "auc")
    role_lr_f1 = pooled("role", "role", "lr", "macro_f1")
    role_gb_f1 = pooled("role", "role", "lgb", "macro_f1")
    rank_lr = (pooled("pairs", "pairs", "lr") or {}).get(te.TIER_SILVER, {}).get("acc")
    rank_gb = (pooled("pairs", "pairs", "lgb") or {}).get(te.TIER_SILVER, {}).get("acc")

    def choose(lr_score, gb_score):
        if lr_score is None or gb_score is None:
            return "lr", "no LOPO metrics on disk — linear arm is the safe default"
        if gb_score - lr_score > LR_TOLERANCE:
            return "lgb", (f"LightGBM is {gb_score - lr_score:+.3f} ahead, more "
                           f"than the {LR_TOLERANCE} tolerance")
        return "lr", (f"LightGBM is only {gb_score - lr_score:+.3f} ahead "
                      f"(<= {LR_TOLERANCE}); the linear arm ports to Java as a "
                      f"dot product")

    junk_choice, junk_why = choose(junk_lr_auc, junk_gb_auc)
    role_choice, role_why = choose(role_lr_f1, role_gb_f1)
    rank_choice, rank_why = choose(rank_lr, rank_gb)

    write_json(dest / "template-role.json", {
        "version": MODEL_VERSION,
        "junk": {"default": junk_choice, "why": junk_why,
                 "lr": dump_lr(heads["junk_lr"], names),
                 "lgb": trim_to_budget(dump_lgb(heads["junk_lgb"], names),
                                       MAX_JSON_BYTES // 2, "junk lgb")},
        "role": {"default": role_choice, "why": role_why,
                 "classes": heads["role_classes"],
                 "lr": role_dump_lr, "lgb": role_dump_gb}})

    write_json(dest / "producer-ranker.json", {
        "version": MODEL_VERSION, "default": rank_choice, "why": rank_why,
        "n_base": int(pairs["_n_base"]),
        "symmetric_flags": list(te.SYMMETRIC_FLAGS),
        "lr": dump_lr(pairs["_models"]["lr"], pairs["_pair_names"],
                      standardised=False),
        "lgb": trim_to_budget(
            dump_lgb(pairs["_models"]["lgb"], pairs["_pair_names"],
                     standardised=False), MAX_JSON_BYTES, "ranker lgb")})

    write_json(dest / "leaf-acquirability.json", {
        "version": MODEL_VERSION,
        "features": leaf["leaf_columns"],
        "weights": [leaf["weights_mean"][c] for c in leaf["leaf_columns"]],
        "spread": leaf["spread"],
        "standardisation": "over the pack's own leaves",
        "source": "weights of the producer-ranker LR arm on the per-recipe "
                  "mean leaf aggregates, at raw-column scale (coef / scale)",
        "spearman_vs_unsupervised": leaf["spearman"],
        "spearman_summary": leaf["spearman_summary"]})

    prior = json.loads((out_dir / "token-classes.json").read_text())
    write_json(dest / "token-classes.json",
               {"version": MODEL_VERSION, "source": "out/token-classes.json",
                "prior": prior})

    golden = golden_sample(out_dir, df, names, heads, pairs, leaf)
    # compact: the golden sample is machine input, and indent(2) on 1,000 x 129
    # feature vectors costs more than the payload itself.
    (dest / "golden-sample.json").write_text(
        json.dumps(golden, separators=(",", ":")) + "\n", encoding="utf-8")

    sizes = {p.name: p.stat().st_size for p in sorted(dest.glob("*.json"))}
    write_json(dest / "manifest.json", {
        "version": MODEL_VERSION,
        "trained": date.today().isoformat(),
        "data_hash": data_hash(out_dir),
        "libraries": {"python": sys.version.split()[0],
                      "numpy": np.__version__,
                      "sklearn": __import__("sklearn").__version__,
                      "lightgbm": __import__("lightgbm").__version__},
        "rows": {"templates": int(len(df)),
                 "labelled_templates": int(df[COL_JUNK_TARGET].notna().sum()),
                 "role_templates": int(np.isin(
                     df["role_target"].fillna("").to_numpy(),
                     te.ROLE_CLASSES).sum()),
                 "training_pairs": int(pairs["n_train_pairs"]),
                 "gold_pairs": int(pairs["gold"]["lr"]["n"])},
        "choice": {"junk": {"default": junk_choice, "why": junk_why,
                            "lopo_auc_lr": junk_lr_auc, "lopo_auc_lgb": junk_gb_auc},
                   "role": {"default": role_choice, "why": role_why,
                            "lopo_macro_f1_lr": role_lr_f1,
                            "lopo_macro_f1_lgb": role_gb_f1},
                   "ranker": {"default": rank_choice, "why": rank_why,
                              "lopo_silver_acc_lr": rank_lr,
                              "lopo_silver_acc_lgb": rank_gb}},
        "metrics_files": sorted(metrics),
        "sizes_bytes": sizes,
        "export_seconds": round(time.time() - t0, 2)})

    print("\n  exported:")
    for name, size in sorted(sizes.items()):
        flag = ""
        if name == "golden-sample.json" and size > MAX_GOLDEN_BYTES:
            flag = "  ** OVER 3 MB **"
        elif size > MAX_JSON_BYTES:
            flag = "  ** OVER 5 MB **"
        print(f"    {name:<28} {size / 1e6:>7.2f} MB{flag}")
    print(f"  defaults: junk={junk_choice}  role={role_choice}  "
          f"ranker={rank_choice}")
    print(f"  total {time.time() - t0:.1f}s")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
