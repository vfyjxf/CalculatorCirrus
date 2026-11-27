#!/usr/bin/env python
"""Phase 0c-2 invariants.  pytest is not installed — run with the venv python:

    .venv/bin/python tests/test_phase0c2.py

Reads the artifacts ``train_eval.py`` / ``pair_features.py`` / ``export_model.py``
left on disk; it does not retrain.  Run the exporter first (``run_phase0c2.sh``
does).
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
import pyarrow.parquet as pq

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(HERE))

import predict_reference  # noqa: E402
import train_eval as te  # noqa: E402
from labels import COL_JUNK_TARGET, TIER_GOLD  # noqa: E402

OUT = ROOT / "out"
MODELS = OUT / "models"
PASSED = []


def check(name: str, condition: bool, detail: str = "") -> None:
    assert condition, f"FAILED: {name}{(': ' + detail) if detail else ''}"
    PASSED.append(name)
    print(f"  ok  {name}" + (f"  ({detail})" if detail else ""))


# ------------------------------------------------------------------- splits


def test_group_weighting() -> None:
    """The cap is a cap, it never promotes a small group, and it stays feasible."""
    base = np.concatenate([np.full(100, 10.0), np.ones(5), np.full(5, 2.0)])
    groups = np.array(["a"] * 100 + ["b"] * 5 + ["c"] * 5)
    w = te.group_capped_weights(base, groups)
    mass = {g: w[groups == g].sum() for g in ("a", "b", "c")}
    total = sum(mass.values())
    check("no pack group exceeds the weight cap",
          max(mass.values()) / total <= te.GROUP_WEIGHT_CAP + 1e-9,
          f"max share {max(mass.values()) / total:.3f} "
          f"<= {te.GROUP_WEIGHT_CAP}")
    for small in ("b", "c"):
        check(f"group {small} (under the cap) is never scaled up",
              np.allclose(w[groups == small], base[groups == small]))
    one = te.group_capped_weights(np.ones(10), np.array(["a"] * 10))
    check("a single group is left alone", np.allclose(one, 1.0))
    # Two groups cannot both sit under 0.40; the cap clamps to 1/G rather than
    # oscillating between the two largest.
    two = te.group_capped_weights(np.array([10.0] * 100 + [1.0] * 5),
                                  np.array(["a"] * 100 + ["b"] * 5))
    g2 = np.array(["a"] * 100 + ["b"] * 5)
    share = {g: two[g2 == g].sum() for g in ("a", "b")}
    check("with two groups the cap clamps to the feasible 1/G",
          abs(share["a"] / sum(share.values()) - 0.5) < 1e-9,
          "0.500 / 0.500")


def test_lopo_never_leaks_a_group() -> None:
    """Every recorded fold partitions the labelled rows exactly."""
    lab = pq.read_table(OUT / "labels" / "template_labels.parquet",
                        columns=["pack_group", COL_JUNK_TARGET]).to_pandas()
    lab = lab[lab[COL_JUNK_TARGET].notna()]
    total = len(lab)
    metrics = load_metrics("junk")["junk"]
    for fold in metrics["folds"]:
        n_group = int((lab.pack_group == fold["group"]).sum())
        check(f"fold {fold['group']}: train + held-out == all labelled rows",
              fold["n_train"] + n_group == total,
              f"{fold['n_train']} + {n_group} == {total}")
        check(f"fold {fold['group']}: the held-out group is fully held out",
              fold["n_test_labelled"] == n_group)
    groups = sorted(set(lab.pack_group))
    check("every labelled pack group gets a fold",
          sorted(f["group"] for f in metrics["folds"]) == groups,
          f"{len(groups)} groups")


def test_gold_is_never_trained_on() -> None:
    train, gold, dropped = te.load_pairs(OUT)
    check("no gold-tier row reaches the training pairs",
          int((train.tier == TIER_GOLD).sum()) == 0, f"{len(train):,} pairs")
    raw = pq.read_table(OUT / "labels" / "pairwise_gold.parquet").to_pandas()
    gold_keys = {(r.pack, *sorted((int(r.chosen_recipe_idx),
                                   int(r.preferred_recipe_idx))))
                 for r in raw.itertuples()}
    train_keys = {(p, *sorted((int(a), int(b)))) for p, a, b in
                  zip(train["pack"], train["a_recipe_idx"], train["b_recipe_idx"])}
    check("no gold recipe pair appears under another tier",
          not (gold_keys & train_keys),
          f"{len(gold_keys)} gold pairs, {len(train_keys):,} training pairs")
    check("exactly one gold reading is dropped, and it is documented",
          len(dropped) == sum(len(v) for v in te.DROPPED_GOLD_READINGS.values()),
          f"{len(dropped)} dropped, {len(gold)} kept")
    check("the dropped reading belongs to the documented finding",
          set(dropped.finding_id) <= set(te.DROPPED_GOLD_READINGS))


# ----------------------------------------------------------------- artifacts


def load_metrics(stage: str) -> dict:
    for name in (f"metrics-{stage}.json", "metrics-all.json"):
        path = MODELS / name
        if path.exists():
            data = json.loads(path.read_text())
            if stage in data or stage == "all":
                return data
    raise AssertionError(f"no metrics on disk for stage {stage}")


def test_json_round_trips() -> None:
    names = ("feature-spec.json", "template-role.json", "producer-ranker.json",
             "leaf-acquirability.json", "token-classes.json", "manifest.json",
             "golden-sample.json")
    for name in names:
        path = MODELS / name
        check(f"{name} exists", path.exists())
        obj = json.loads(path.read_text())
        again = json.loads(json.dumps(obj, separators=(",", ":")))
        check(f"{name} round-trips", again == obj,
              f"{path.stat().st_size / 1e6:.2f} MB")
        cap = 3 if name == "golden-sample.json" else 5
        check(f"{name} is under {cap} MB",
              path.stat().st_size <= cap * 1024 * 1024)


def test_spec_matches_the_models() -> None:
    spec = json.loads((MODELS / "feature-spec.json").read_text())
    catalog = json.loads((OUT / "features" / "feature-catalog.json").read_text())
    names = [e["name"] for e in catalog["template_features"]]
    check("feature-spec keeps the catalog's feature order",
          spec["template_features"]["names"] == names, f"{len(names)} features")
    for key in ("fill", "mean", "std", "layers"):
        check(f"feature-spec {key} is one value per feature",
              len(spec["template_features"][key]) == len(names))
    check("the L6 hash spec is copied verbatim from the catalog",
          spec["hash"] == catalog["hash"])
    role = json.loads((MODELS / "template-role.json").read_text())
    check("the junk LR arm has one weight per feature",
          len(role["junk"]["lr"]["coef"][0]) == len(names))
    check("the role LR arm has one weight row per class",
          len(role["role"]["lr"]["coef"]) == len(role["role"]["lr"]["classes"]))
    ranker = json.loads((MODELS / "producer-ranker.json").read_text())
    check("the ranker has no intercept",
          all(v == 0.0 for v in ranker["lr"]["intercept"]))
    check("the ranker LR arm has one weight per pair feature",
          len(ranker["lr"]["coef"][0]) == len(spec["pair_features"]["names"]))
    leaf = json.loads((MODELS / "leaf-acquirability.json").read_text())
    check("the leaf scorer has one weight per leaf column",
          len(leaf["weights"]) == len(leaf["features"]))
    agg = json.loads((MODELS / "recipe-leaf-agg-meta.json").read_text())
    check("the leaf scorer uses the aggregated columns and no score",
          leaf["features"] == agg["columns"]
          and not (set(agg["excluded"]) & set(leaf["features"])),
          f"{len(leaf['features'])} columns, {agg['excluded']} excluded")


def test_reference_inference() -> None:
    rc = predict_reference.verify(MODELS, tol=1e-6)
    check("pure-numpy inference reproduces the library predictions", rc == 0)


def test_reference_is_library_free() -> None:
    text = (ROOT / "predict_reference.py").read_text()
    for banned in ("sklearn", "lightgbm", "pyarrow", "pandas", "scipy"):
        check(f"predict_reference.py does not import {banned}",
              f"import {banned}" not in text)


def test_ranker_is_antisymmetric() -> None:
    """``m(a,b) = -m(b,a)``, up to the residual the symmetric flags leave.

    Antisymmetrising the training set drives the three symmetric flags' linear
    weights to zero but L2 regularisation leaves them at ~1e-6, so the linear
    arm's residual is twice that, not exactly zero.  The tree arm is averaged
    explicitly at inference and is exact.  Both are asserted, and the symmetric
    weights themselves are asserted to be negligible next to the largest one.
    """
    ref = predict_reference.Reference(MODELS)
    sample = json.loads((MODELS / "golden-sample.json").read_text())
    x = np.array([p["x"] for p in sample["pairs"]])
    limits = {"lr": 1e-4, "lgb": 1e-9}
    for arm, limit in limits.items():
        m = ref.margin(x, arm)
        back = ref.margin(ref.swap(x), arm)
        err = float(np.abs(m + back).max())
        check(f"the {arm} ranker margin flips sign with the pair", err < limit,
              f"max |m(a,b) + m(b,a)| = {err:.2e} < {limit:.0e}")
    spec = ref.spec["pair_features"]
    coef = np.asarray(ref.ranker["lr"]["coef"][0])
    sym = np.array([abs(coef[spec["names"].index(f)])
                    for f in spec["symmetric_flags"]])
    check("antisymmetrisation drives the symmetric flags' linear weights to ~0",
          float(sym.max()) < 1e-4 * float(np.abs(coef).max()),
          f"max symmetric |coef| {sym.max():.2e} vs max |coef| "
          f"{np.abs(coef).max():.3f}")


def test_golden_sample_shape() -> None:
    sample = json.loads((MODELS / "golden-sample.json").read_text())
    check("the golden sample has 1,000 template rows",
          len(sample["rows"]) == 1000)
    check("the golden sample spans at least 6 pack groups",
          sample["n_pack_groups"] >= 6, f"{sample['n_pack_groups']} groups")
    check("the golden sample carries 500 pairs and 300 leaves",
          len(sample["pairs"]) == 500 and len(sample["leaves"]) == 300,
          f"{len(sample['pairs'])} pairs, {len(sample['leaves'])} leaves")
    width = len(sample["template_features"])
    check("every sampled row carries the full feature vector",
          all(len(r["x"]) == width for r in sample["rows"]), f"{width} features")
    pw = len(sample["pair_features"])
    check("every sampled pair carries the full pair vector",
          all(len(p["x"]) == pw for p in sample["pairs"]), f"{pw} features")


def test_leaf_seed_band() -> None:
    ref = predict_reference.Reference(MODELS)
    sample = json.loads((MODELS / "golden-sample.json").read_text())
    spread = float(ref.leaf["spread"])
    seeds = np.array([l["seed"] for l in sample["leaves"]])
    check("every leaf seed lies in the [1, K] band economy.py defines",
          bool(((seeds >= 1.0 - 1e-9) & (seeds <= spread + 1e-9)).all()),
          f"K={spread}, seeds in [{seeds.min():.3f}, {seeds.max():.3f}]")


def test_leaf_aggregation_covers_every_recipe() -> None:
    agg = pq.ParquetFile(MODELS / "recipe_leaf_agg.parquet").metadata.num_rows
    rf = pq.ParquetFile(OUT / "features" /
                        "recipe_features.parquet").metadata.num_rows
    check("the leaf aggregation has one row per recipe", agg == rf,
          f"{agg:,} == {rf:,}")


def test_no_vocabulary() -> None:
    import test_phase0a
    test_phase0a.test_no_rule_vocabulary()
    test_phase0a.test_label_space_table_is_the_only_exemption()
    check("no mod / item / material vocabulary in the Phase 0c-2 modules",
          all(m in test_phase0a.MODULES for m in
              ("train_eval.py", "export_model.py", "predict_reference.py",
               "pair_features.py")))


def main() -> int:
    print("Phase 0c-2 checks")
    print("-- splits")
    test_group_weighting()
    test_lopo_never_leaks_a_group()
    test_gold_is_never_trained_on()
    print("-- exported artifacts")
    test_json_round_trips()
    test_spec_matches_the_models()
    test_leaf_aggregation_covers_every_recipe()
    print("-- reference inference")
    test_reference_inference()
    test_reference_is_library_free()
    test_ranker_is_antisymmetric()
    test_golden_sample_shape()
    test_leaf_seed_band()
    print("-- design rules")
    test_no_vocabulary()
    print(f"\n{len(PASSED)} checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
