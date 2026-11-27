#!/usr/bin/env python
"""Phase 0c-2 — the one aggregation Phase 0c-1 did not write.

    ./.venv/bin/python pair_features.py --packs fom,atm10-lite
    ./.venv/bin/python pair_features.py --packs all

``features.py`` reduced the Phase 0b leaf table to three numbers per recipe
(`l4_leaf_acq_mean`, `l4_leaf_acq_min`, `l4_data_gap_input_share`), and two of
those are the *unsupervised* composite the Phase 0c-2 ranker is supposed to
replace.  The pairwise ranker needs the **raw** leaf columns instead, so that
the weights it learns on them ARE the supervised leaf-acquirability model
(Stage C).

So this module recomputes, per ``(pack, recipe_idx)``, the **mean and min over
the recipe's leaf inputs** of every structural column of
``out/<slug>/economy/leaf_features.parquet`` except three:

* ``acquirability`` and ``seed_value`` — the unsupervised score itself and its
  exponential map; using them as features would be fitting the thing we are
  trying to replace onto itself;
* ``n_forms_of_material`` — Phase 0b deliberately excluded it from its own
  composite (it measures how *core* a material is, not how cheap it is), and
  the same argument applies here.

Everything is read from existing parquet; nothing upstream is modified and
nothing upstream is re-run.  The leaf-input resolution is a copy of
``features.py::stage_resources``: input-side content rows, a ``tag`` row
represented by its lexicographically smallest member, matched against
``item_values.is_leaf``.

Output: ``out/models/recipe_leaf_agg.parquet``
(``pack``, ``recipe_idx``, ``leaf_agg_n``, then ``<col>_mean`` / ``<col>_min``).
"""
from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

import numpy as np
import pyarrow as pa
import pyarrow.parquet as pq

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from common import read_json, write_json  # noqa: E402

SIDE_IN = "in"

#: Columns of ``leaf_features.parquet`` that are *unsupervised scores*, not
#: structural observations.  See the module docstring.
EXCLUDED = ("acquirability", "seed_value", "n_forms_of_material")

#: Non-feature columns of that table.
KEYS = ("base_id", "material_key", "form_class", "data_gap", "world_resource")


def leaf_feature_columns(out_dir: Path, slugs) -> list[str]:
    """Structural leaf columns, in a stable order, present in every pack."""
    common: list[str] | None = None
    for slug in slugs:
        path = out_dir / slug / "economy" / "leaf_features.parquet"
        names = [n for n in pq.ParquetFile(path).schema_arrow.names
                 if n not in KEYS and n not in EXCLUDED]
        common = names if common is None else [n for n in common if n in names]
    return sorted(common or [])


def or_representatives(table: pa.Table, kinds: np.ndarray,
                       base: np.ndarray) -> dict:
    """``{tag id: smallest member id}`` — same rule as ``features.py``."""
    is_tag = kinds == "tag"
    if not is_tag.any():
        return {}
    idx = np.flatnonzero(is_tag)
    _, first = np.unique(base[idx], return_index=True)
    rows = idx[first]
    members = table.column("members").take(pa.array(rows)).to_pylist()
    out = {}
    for row, mem in zip(rows, members):
        if mem:
            out[base[row]] = min(mem)
    return out


def aggregate_pack(cache_dir: Path, out_dir: Path, slug: str, pack: str,
                   cols: list[str]) -> dict:
    econ = out_dir / slug / "economy"
    values = pq.read_table(econ / "item_values.parquet",
                           columns=["base_id", "is_leaf"])
    n_recipes = pq.ParquetFile(cache_dir / slug / "recipes.parquet").metadata.num_rows

    leaf = pq.read_table(econ / "leaf_features.parquet",
                         columns=["base_id"] + cols)
    lids = np.asarray(leaf.column("base_id").to_pylist())
    order = np.argsort(lids, kind="stable")
    lids = lids[order]
    lmat = np.column_stack([np.asarray(leaf.column(c).to_pylist(),
                                       dtype=np.float64)[order] for c in cols])

    contents = pq.read_table(cache_dir / slug / "contents.parquet",
                             columns=["recipe_idx", "side", "kind", "base_id",
                                      "members"])
    side = np.asarray(contents.column("side").to_pylist())
    sub = contents.take(pa.array(np.flatnonzero(side == SIDE_IN)))
    kinds = np.asarray(sub.column("kind").to_pylist())
    base = np.asarray(sub.column("base_id").to_pylist())
    rep = or_representatives(sub, kinds, base)
    if rep:
        base = np.asarray([rep.get(b, b) for b in base])
    ridx = np.asarray(sub.column("recipe_idx").to_pylist(), dtype=np.int64)

    # is_leaf is carried by item_values; leaf_features has exactly the leaves,
    # so membership in ``lids`` *is* leafhood.  Assert that once per pack.
    vid = np.asarray(values.column("base_id").to_pylist())
    vleaf = np.asarray(values.column("is_leaf").to_pylist())
    n_leaf_items = int(np.asarray(vleaf, dtype=bool).sum())
    assert n_leaf_items == len(lids), (slug, n_leaf_items, len(lids))
    del vid, vleaf

    slot = np.searchsorted(lids, base)
    slot = np.clip(slot, 0, max(lids.size - 1, 0))
    hit = (lids.size > 0) & (lids[slot] == base)

    n = np.bincount(ridx[hit], minlength=n_recipes).astype(np.float64)
    out = {"leaf_agg_n": n}
    for j, name in enumerate(cols):
        v = lmat[slot, j]
        total = np.bincount(ridx[hit], weights=v[hit], minlength=n_recipes)
        with np.errstate(invalid="ignore", divide="ignore"):
            out[f"{name}_mean"] = np.where(n > 0, total / np.maximum(n, 1), np.nan)
        mn = np.full(n_recipes, np.inf)
        np.minimum.at(mn, ridx[hit], v[hit])
        mn[np.isposinf(mn)] = np.nan
        out[f"{name}_min"] = mn
    out["_pack"] = pack
    out["_n_recipes"] = n_recipes
    return out


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--cache-dir", default=str(HERE / "cache"))
    ap.add_argument("--out-dir", default=str(HERE / "out"))
    ap.add_argument("--packs", default="all")
    args = ap.parse_args(argv)
    cache_dir, out_dir = Path(args.cache_dir), Path(args.out_dir)
    packs = read_json(cache_dir / "packs.json")
    slugs = (sorted(packs) if args.packs == "all"
             else [s.strip() for s in args.packs.split(",") if s.strip()])

    t0 = time.time()
    cols = leaf_feature_columns(out_dir, slugs)
    print(f"  {len(cols)} structural leaf columns: {', '.join(cols)}")
    frames, rows = [], 0
    for slug in slugs:
        # label rows by SLUG: out/features/*.parquet key `pack` on the slug, not the
        # export directory name (they differ for 5 packs; passing packs[slug] here
        # silently NaN-ed every leaf aggregate for those packs in train_eval).
        agg = aggregate_pack(cache_dir, out_dir, slug, slug, cols)
        n = agg.pop("_n_recipes")
        pack = agg.pop("_pack")
        arrays = {"pack": pa.array([pack] * n, pa.string()),
                  "recipe_idx": pa.array(np.arange(n, dtype=np.int32))}
        for k, v in agg.items():
            arrays[k] = pa.array(v.astype(np.float32), pa.float32())
        frames.append(pa.table(arrays))
        rows += n
        print(f"    {slug:<40} {n:>7,} recipes, "
              f"{int((agg['leaf_agg_n'] > 0).sum()):>7,} with a leaf input")
    table = pa.concat_tables(frames)
    dest = out_dir / "models" / "recipe_leaf_agg.parquet"
    dest.parent.mkdir(parents=True, exist_ok=True)
    pq.write_table(table, dest, compression="snappy")
    write_json(out_dir / "models" / "recipe-leaf-agg-meta.json",
               {"columns": cols, "rows": rows, "packs": len(slugs),
                "excluded": list(EXCLUDED),
                "seconds": round(time.time() - t0, 2)})
    print(f"  wrote {dest} — {rows:,} rows x {len(table.column_names)} cols, "
          f"{dest.stat().st_size / 1e6:.1f} MB, {time.time() - t0:.1f}s")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
