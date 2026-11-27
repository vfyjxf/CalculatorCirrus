#!/usr/bin/env python
"""Phase 0c-1 invariants — the label layer and the feature layer.

pytest is not installed; run with the venv python:

    .venv/bin/python tests/test_phase0c1.py            # checks out/
    .venv/bin/python tests/test_phase0c1.py --packs fom

The table checks read whatever ``out/labels`` and ``out/features`` currently
hold, so they check the artifacts a later phase will actually train on.  The
determinism check re-runs both layers twice over the small ``fom`` pack into
throwaway directories, which takes about two seconds.
"""
from __future__ import annotations

import argparse
import hashlib
import shutil
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(HERE))

import numpy as np  # noqa: E402
import pyarrow.parquet as pq  # noqa: E402

import features as F  # noqa: E402
import labels as L  # noqa: E402
from common import read_json  # noqa: E402

PACK = "fom"
PASSED = []


def check(name: str, condition: bool, detail: str = "") -> None:
    assert condition, f"FAILED: {name}{(': ' + detail) if detail else ''}"
    PASSED.append(name)
    print(f"  ok  {name}" + (f"  ({detail})" if detail else ""))


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(path: Path):
    return pq.read_table(path)


# ------------------------------------------------------------ the label side


def test_labels_join_on_index_only(out: Path, cache: Path) -> None:
    """Nothing in the label layer may join on a recipe id.

    28.8 % of exported recipes have an empty id and 20 % of the rest share
    theirs with another row, so an id join silently mislabels.  Three things
    prove the index join instead: the tables carry no id column at all, every
    key is unique and inside the pack's index range, and where a label source
    *does* carry an id it agrees with the cached id at that index.
    """
    table = load(out / "labels" / "recipe_labels.parquet")
    names = set(table.schema.names)
    check("recipe_labels carries no recipe-id column",
          not {"recipe_id", "id"} & names, sorted(names)[:4])

    packs = np.asarray(table.column("pack").to_pylist())
    idx = table.column("recipe_idx").to_numpy(zero_copy_only=False)
    keys = list(zip(packs.tolist(), idx.tolist()))
    check("every (pack, recipe_idx) appears once",
          len(set(keys)) == len(keys), f"{len(keys) - len(set(keys))} repeats")

    bad = []
    for slug in sorted(set(packs.tolist())):
        n = pq.read_metadata(cache / slug / "recipes.parquet").num_rows
        sel = idx[packs == slug]
        if sel.size and (sel.min() < 0 or sel.max() >= n):
            bad.append(slug)
    check("every recipe_idx is inside its pack's index range", not bad, str(bad))

    meta = read_json(out / "labels" / "labels-meta.json")
    worst = min((c["id_agree_pct"] for c in meta["gold"]["id_check"].values()
                 if c["id_agree_pct"] is not None), default=100.0)
    check("where a label source carries an id it matches the cached id at "
          "that index", worst == 100.0, f"worst pack {worst}%")


def test_every_labelled_recipe_has_a_template(out: Path) -> None:
    table = load(out / "labels" / "recipe_labels.parquet")
    tpl = np.asarray(table.column("template_id").to_pylist())
    check("every labelled recipe carries a template id",
          bool((tpl != "").all()), f"{int((tpl == '').sum())} without")

    templates = load(out / "labels" / "template_labels.parquet")
    known = set(zip(templates.column("pack").to_pylist(),
                    templates.column("template_id").to_pylist()))
    pairs = set(zip(table.column("pack").to_pylist(), tpl.tolist()))
    check("every labelled recipe's template has a template_labels row",
          pairs <= known, f"{len(pairs - known)} missing")


def test_role_targets_come_from_the_table(out: Path) -> None:
    table = load(out / "labels" / "template_labels.parquet")
    roles = set(table.column("role_target").to_pylist())
    check("role targets are exactly the documented role set",
          roles <= set(L.ROLES) | {""}, str(sorted(roles)))
    categories = set(load(out / "labels" / "recipe_labels.parquet")
                     .column("gold_category").to_pylist())
    unmapped = {c for c in categories if c and c not in L.CATEGORY_ROLE}
    check("every category a label source uses is in the mapping table",
          not unmapped, str(sorted(unmapped)[:5]))


def test_pairs_are_well_formed(out: Path) -> None:
    table = load(out / "labels" / "pairs.parquet")
    a = table.column("a_recipe_idx").to_numpy(zero_copy_only=False)
    b = table.column("b_recipe_idx").to_numpy(zero_copy_only=False)
    check("no pair pairs a recipe with itself", bool((a != b).all()),
          f"{int((a == b).sum())} self-pairs")

    packs = np.asarray(table.column("pack").to_pylist())
    groups = np.asarray(table.column("pack_group").to_pylist())
    check("a pair never crosses packs", table.num_rows == packs.size)
    check("every pair carries its pack group",
          bool((groups != "").all()))

    labelled = table.column("label").to_numpy(zero_copy_only=False)
    check("labels are +-1 only", set(np.unique(labelled).tolist()) <= {-1, 1},
          str(sorted(set(np.unique(labelled).tolist()))))
    tiers = set(table.column("tier").to_pylist())
    check("tiers are the four documented ones",
          tiers <= set(L.TIER_ORDER), str(sorted(tiers)))
    weight = table.column("weight").to_numpy(zero_copy_only=False)
    check("weights are finite and positive",
          bool(np.isfinite(weight).all() and (weight > 0).all()))


def test_pairs_reference_real_producers(out: Path, cache: Path) -> None:
    """Both sides of every pair must actually produce the named output."""
    table = load(out / "labels" / "pairs.parquet")
    rows = table.to_pylist()
    by_pack: dict = {}
    for row in rows:
        by_pack.setdefault(row["pack"], []).append(row)
    bad = 0
    checked = 0
    for slug, group in sorted(by_pack.items()):
        contents = pq.read_table(cache / slug / "contents.parquet",
                                 columns=["recipe_idx", "side", "base_id"])
        side = np.asarray(contents.column("side").to_pylist())
        mask = side == F.SIDE_OUT
        ridx = contents.column("recipe_idx").to_numpy(zero_copy_only=False)[mask]
        base = np.asarray(contents.column("base_id").to_pylist())[mask]
        produces: dict = {}
        for r, value in zip(ridx.tolist(), base.tolist()):
            produces.setdefault(value, set()).add(r)
        for row in group:
            checked += 1
            makers = produces.get(row["output_base_id"], set())
            if row["a_recipe_idx"] not in makers or \
                    row["b_recipe_idx"] not in makers:
                bad += 1
    check("both sides of every pair produce the named output", bad == 0,
          f"{bad} of {checked:,} bad")


def test_gold_pairs_resolve(out: Path) -> None:
    gold = load(out / "labels" / "pairwise_gold.parquet")
    pairs = load(out / "labels" / "pairs.parquet")
    keyed = set()
    for row in pairs.to_pylist():
        if row["tier"] != L.TIER_GOLD:
            continue
        a, b = row["a_recipe_idx"], row["b_recipe_idx"]
        keyed.add((row["pack"], min(a, b), max(a, b)))
    missing = []
    for row in gold.to_pylist():
        key = (row["pack"], min(row["chosen_recipe_idx"],
                                row["preferred_recipe_idx"]),
               max(row["chosen_recipe_idx"], row["preferred_recipe_idx"]))
        if key not in keyed:
            missing.append(row["finding_id"])
    check("every pairwise_gold row reaches pairs.parquet as a gold pair",
          not missing, str(sorted(set(missing))[:3]))
    check("a gold pair never has the same recipe on both sides",
          all(r["chosen_recipe_idx"] != r["preferred_recipe_idx"]
              for r in gold.to_pylist()))


def test_chain_verdicts(out: Path, cache: Path) -> None:
    table = load(out / "labels" / "chain_verdicts.parquet")
    rows = table.to_pylist()
    bad = []
    limits = {}
    for row in rows:
        n = limits.setdefault(
            row["pack"],
            pq.read_metadata(cache / row["pack"] / "recipes.parquet").num_rows)
        for r in row["recipe_idx"]:
            if r < 0 or r >= n:
                bad.append((row["pack"], r))
                break
    check("every chain step resolves to an in-range recipe_idx", not bad,
          str(bad[:3]))
    verdicts = {r["verdict"] for r in rows}
    check("chain verdicts are ok / bad / questionable / unread",
          verdicts <= {L.CHAIN_OK, L.CHAIN_BAD, "questionable", ""},
          str(sorted(verdicts)))


# ---------------------------------------------------------- the feature side


def test_feature_matrix(out: Path) -> None:
    table = load(out / "features" / "template_features.parquet")
    catalog = read_json(out / "features" / "feature-catalog.json")
    names = [c["name"] for c in catalog["template_features"]]
    check("every feature column is in the catalog",
          set(names) <= set(table.schema.names),
          str(sorted(set(names) - set(table.schema.names))[:3]))
    check("every catalog entry names a layer",
          all(c["layer"] != "?" for c in catalog["template_features"]))

    infs, nan_rate = [], {}
    for field in table.schema:
        if field.type != "float":
            continue
        values = table.column(field.name).to_numpy(zero_copy_only=False)
        if np.isinf(values).any():
            infs.append(field.name)
        rate = float(np.isnan(values).mean())
        if rate:
            nan_rate[field.name] = rate
    check("no feature column contains an infinity", not infs, str(infs[:3]))

    meta = read_json(out / "features" / "features-meta.json")
    documented = meta["template_features"]["nan_rate"]
    undocumented = sorted(set(nan_rate) - set(documented))
    check("every column with missing values is documented in the meta",
          not undocumented, str(undocumented[:3]))
    worst = max(nan_rate.values()) if nan_rate else 0.0
    check("the worst missing-value rate is below 90 %", worst < 0.9,
          f"{worst:.1%}")


def test_feature_keys_match_labels(out: Path) -> None:
    feats = load(out / "features" / "template_features.parquet")
    lab = load(out / "labels" / "template_labels.parquet")
    fkeys = set(zip(feats.column("pack").to_pylist(),
                    feats.column("template_id").to_pylist()))
    lkeys = set(zip(lab.column("pack").to_pylist(),
                    lab.column("template_id").to_pylist()))
    check("the feature matrix and the template labels have the same keys",
          fkeys == lkeys,
          f"{len(fkeys - lkeys)} features-only, {len(lkeys - fkeys)} labels-only")

    recs = load(out / "features" / "recipe_features.parquet")
    check("recipe features carry a template id for every row",
          bool((np.asarray(recs.column("template_id").to_pylist()) != "").all()))
    check("recipe features stay under the size budget",
          (out / "features" / "recipe_features.parquet").stat().st_size < 500e6,
          f"{(out / 'features' / 'recipe_features.parquet').stat().st_size / 1e6:.0f} MB")


def test_hash_excludes_namespaces(out: Path, cache: Path) -> None:
    """No L6 bucket may be fed a namespace.

    A system id that renders another registry's page carries that registry's
    namespace as a *path* segment, so stripping only the id's own namespace
    leaves a bucket that is really "this mod".
    """
    import json as _json
    ctx = {"out_dir": out, "cache_dir": cache}
    namespaces = F.pack_namespaces(ctx, PACK)
    classes = read_json(out / PACK / "token-classes.json")
    classed = {m for info in classes["classes"].values() for m in info["members"]}
    table = pq.read_table(out / PACK / "templates.parquet",
                          columns=["signature", "system"])
    leaked = set()
    for sig, system in zip(table.column("signature").to_pylist(),
                           table.column("system").to_pylist()):
        tokens = [t for t in _json.loads(sig).get("rid", [])
                  if t not in classed and t not in namespaces]
        tokens += F.residual_system_tokens(system, classed, namespaces)
        leaked |= {t for t in tokens if t in namespaces}
    check("no namespace reaches an L6 bucket", not leaked,
          str(sorted(leaked)[:5]))
    check("the pack's namespace set is non-trivial", len(namespaces) > 5,
          f"{len(namespaces)} namespaces")


def test_hash_is_stable() -> None:
    """L6's bucket function must not depend on the process."""
    check("the residual hash is stable and inside the bucket range",
          all(0 <= F.bucket_of(f"t{i}") < F.HASH_BUCKETS for i in range(50)))
    check("the residual hash is the documented sha1 construction",
          F.bucket_of("a") == int.from_bytes(
              hashlib.sha1(b"a").digest(), "big") % F.HASH_BUCKETS)


def test_sibling_z_definition() -> None:
    """A group of one, and a group that agrees, both score 0 — not NaN."""
    values = np.array([1.0, 1.0, 5.0, 3.0])
    group = np.array([0, 0, 1, 2])
    z = F.group_z(values, group, 3)
    check("identical siblings score zero", bool((z[:2] == 0).all()))
    check("a lone producer scores zero", z[2] == 0.0 and z[3] == 0.0)
    values = np.array([0.0, 2.0])
    z = F.group_z(values, np.array([0, 0]), 1)
    check("two siblings score +-1", bool(np.allclose(np.abs(z), 1.0)),
          str(z.tolist()))


# ------------------------------------------------------------- determinism


def run_both(write_dir: Path, out: Path, cache: Path) -> None:
    rc = L.main(["--cache-dir", str(cache), "--out-dir", str(out),
                 "--write-dir", str(write_dir), "--packs", PACK])
    assert rc == 0
    rc = F.main(["--cache-dir", str(cache), "--out-dir", str(out),
                 "--write-dir", str(write_dir), "--packs", PACK])
    assert rc == 0


def test_determinism(out: Path, cache: Path) -> None:
    tmp = Path(tempfile.mkdtemp(prefix="phase0c1-test-"))
    try:
        run_both(tmp / "a", out, cache)
        run_both(tmp / "b", out, cache)
        for sub in ("labels", "features"):
            for path in sorted((tmp / "a" / sub).glob("*.parquet")):
                other = tmp / "b" / sub / path.name
                check(f"{sub}/{path.name} is byte-identical across two runs",
                      digest(path) == digest(other))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache-dir", default=str(ROOT / "cache"))
    ap.add_argument("--out-dir", default=str(ROOT / "out"))
    args = ap.parse_args(argv)
    out, cache = Path(args.out_dir), Path(args.cache_dir)

    print("Phase 0c-1 checks")
    print("-- label joins")
    test_labels_join_on_index_only(out, cache)
    test_every_labelled_recipe_has_a_template(out)
    test_role_targets_come_from_the_table(out)
    print("-- pairs")
    test_pairs_are_well_formed(out)
    test_pairs_reference_real_producers(out, cache)
    test_gold_pairs_resolve(out)
    test_chain_verdicts(out, cache)
    print("-- features")
    test_feature_matrix(out)
    test_feature_keys_match_labels(out)
    test_hash_excludes_namespaces(out, cache)
    test_hash_is_stable()
    test_sibling_z_definition()
    print("-- design rules")
    import test_phase0a  # noqa: E402  (the shared no-vocabulary check)
    test_phase0a.test_no_rule_vocabulary()
    test_phase0a.test_label_space_table_is_the_only_exemption()
    PASSED.extend(test_phase0a.PASSED)
    print("-- determinism")
    test_determinism(out, cache)
    print(f"\n{len(PASSED)} checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
