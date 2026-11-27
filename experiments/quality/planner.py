#!/usr/bin/env python
"""Phase 3 — the HierarchicalPlanner prototype.

    ./.venv/bin/python planner.py --stage index --packs fom
    ./.venv/bin/python planner.py --stage solve --packs fom --targets 20

This module is the *design reference* for the Java port (see DESIGN.md
"Phase 3"), so it is written in the shape the Java planner will have:

  PackIndex     build-time, cached per pack   -> RecipeGraph.Node caches
  CandidatePolicy  which producers may fire   -> SourcePolicyDecision bitmap
  CostSolve     the material layer            -> the cost heuristic
  FormLayer     producer choice               -> ProducerRanker / FeatureType
  Extraction    the ledger walk               -> ResourceLedger / PlanBuilder

Hard rules inherited from Phase 0 and kept here:

* **No hardcoded mod / item / material / colour / system vocabulary.**  Every
  role word this module uses is imported from ``labels.py``'s single sanctioned
  label-space table; every other name is an export-schema field name or a
  Phase 0a/0b column name.  ``tests/test_phase0a.py`` asserts it.
* **Never join on recipe ids** (28.8 % are empty).  ``recipe_idx``,
  ``template_id`` and ``base_id`` are the only keys.
* **Deterministic.**  Stable sorts, seeded sampling, tie-breaks by stable keys,
  ``craftTimes`` in ``int64``.
"""
from __future__ import annotations

import argparse
import math
import sys
import time
from pathlib import Path

import numpy as np
import pyarrow as pa
import pyarrow.parquet as pq

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from common import read_json, stable_hash, write_json  # noqa: E402
from labels import (ROLE_CONVERSION, ROLE_DISPLAY, ROLE_GENERATION,  # noqa: E402
                    ROLE_PRODUCTION, ROLE_RECYCLING)
from predict_reference import Reference  # noqa: E402

SIDE_IN, SIDE_OUT = "in", "out"

#: Bootstrap rounds the cost solve may spend entering rings the candidate
#: policy created.  ``economy.py`` converges in 2; the cap only bounds a
#: pathological pack.
MAX_BOOT_ROUNDS = 64
OR_KINDS = ("tag", "any", "typedAny")

#: Phase 0b shape-role names (`economy.py`), mapped onto the Phase 0c role set.
#: This is a rename between two *shape* vocabularies, not pack data.
SHAPE_TO_ROLE = {
    "production": ROLE_PRODUCTION,
    "conversion": ROLE_CONVERSION,
    "recycling_shaped": ROLE_RECYCLING,
    "generation": ROLE_GENERATION,
    "noop": ROLE_DISPLAY,
}

#: Role-head classes the Phase 0c-2 report says are usable (REPORT issue 6).
#: For every other argmax the Phase 0b shape role decides.
TRUSTED_ROLES = (ROLE_PRODUCTION, ROLE_DISPLAY)


# =============================================================== cost defaults

class Knobs:
    """Every tunable of the planner, in one place, all CLI-overridable."""

    __slots__ = ("tau_junk", "tau_display", "gamma", "step_cost",
                 "machine_cost", "tier_penalty", "recycling_mult",
                 "generation_mult", "budget_base", "budget_per_producer",
                 "budget_cap", "arm_junk", "arm_role", "arm_rank",
                 "use_ranker", "conversion_needs_stock")

    def __init__(self, **kw):
        self.tau_junk = 0.8707099294249354     # metrics-recipe, lgb, P >= 0.95
        self.tau_display = 0.5
        self.gamma = 2.0
        self.step_cost = 0.25
        self.machine_cost = 0.25
        self.tier_penalty = 1.0
        self.recycling_mult = 8.0
        self.generation_mult = 1.0
        self.budget_base = 512
        self.budget_per_producer = 4
        self.budget_cap = 200000
        self.arm_junk = None
        self.arm_role = None
        self.arm_rank = None
        self.use_ranker = True
        self.conversion_needs_stock = True
        for k, v in kw.items():
            if v is not None:
                setattr(self, k, v)

    def signature(self) -> str:
        return stable_hash({k: getattr(self, k) for k in self.__slots__}, 12)

    def as_dict(self) -> dict:
        return {k: getattr(self, k) for k in self.__slots__}


# ================================================ additive options (Phase 4/5)


class PlanOptions:
    """Everything Phases 4 and 5 add, in one optional object.

    ``opt=None`` is the default at every call site and is the Phase 3 code
    path byte for byte — ``tests/test_phase4.py`` asserts a 50-target fom
    snapshot taken before these hooks were added.  The fields live here rather
    than in ``Knobs`` on purpose: ``Knobs`` is serialised into every plan, so a
    new field there would change the JSON of a run that does not use it.

    ``feedback`` is a *view* (``feedback.FeedbackView``): dense arrays the
    planner reads exactly where it reads the model's own arrays.  The planner
    knows nothing about how they were derived.
    """

    __slots__ = ("feedback", "share_memo", "fold_templates", "budget_shape",
                 "budget_seconds")

    #: Budget shapes measured in Phase 5.  ``cone`` is the Phase 3 default.
    SHAPES = ("cone", "depth", "time")

    def __init__(self, feedback=None, share_memo=False, fold_templates=False,
                 budget_shape="cone", budget_seconds=0.25):
        self.feedback = feedback
        self.share_memo = bool(share_memo)
        self.fold_templates = bool(fold_templates)
        if budget_shape not in self.SHAPES:
            raise SystemExit(f"unknown budget shape {budget_shape!r}")
        self.budget_shape = budget_shape
        self.budget_seconds = float(budget_seconds)

    def is_default(self) -> bool:
        return (self.feedback is None and not self.share_memo
                and not self.fold_templates and self.budget_shape == "cone")

    def as_dict(self) -> dict:
        d = {"share_memo": self.share_memo,
             "fold_templates": self.fold_templates,
             "budget_shape": self.budget_shape}
        if self.budget_shape == "time":
            d["budget_seconds"] = self.budget_seconds
        if self.feedback is not None:
            d["feedback"] = self.feedback.signature()
        return d

    def signature(self) -> str:
        return stable_hash(self.as_dict(), 12)


def _fb(opt):
    """The feedback view of ``opt``, or ``None``."""
    return None if opt is None else opt.feedback


def eff_score(ix: "PackIndex", opt=None) -> np.ndarray:
    """Producer scores the form layer ranks by: the exported ones, plus the
    feedback delta when a view is attached."""
    v = _fb(opt)
    return ix.score if v is None else v.score


# ============================================================ small utilities


def build_csr(parent: np.ndarray, n_parents: int):
    """``(ptr, order)`` grouping ``parent`` — the same helper ``economy.py`` uses."""
    order = np.argsort(parent, kind="stable")
    counts = np.bincount(parent, minlength=n_parents)
    ptr = np.zeros(n_parents + 1, dtype=np.int64)
    np.cumsum(counts, out=ptr[1:])
    return ptr, order


def _codes(arr):
    if isinstance(arr, pa.ChunkedArray):
        arr = arr.combine_chunks()
    enc = arr.dictionary_encode()
    if isinstance(enc, pa.ChunkedArray):
        enc = enc.combine_chunks()
    idx = enc.indices.to_numpy(zero_copy_only=False)
    return idx.astype(np.int64), enc.dictionary.to_numpy(zero_copy_only=False)


def lookup(sorted_keys: np.ndarray, probe: np.ndarray) -> np.ndarray:
    """Index of every ``probe`` in ``sorted_keys``; ``-1`` when absent."""
    if sorted_keys.size == 0:
        return np.full(len(probe), -1, dtype=np.int64)
    pos = np.clip(np.searchsorted(sorted_keys, probe), 0, sorted_keys.size - 1)
    return np.where(sorted_keys[pos] == probe, pos, -1).astype(np.int64)


# ================================================================== PackIndex


class PackIndex:
    """Everything the planner needs about one pack, built once and cached.

    Java mapping: the per-node caches of ``RecipeGraph.Node`` plus
    ``GraphTopology``'s derived structures.
    """

    __slots__ = ("slug", "pack", "n_recipes", "keep", "ridx",
                 "items", "item_map", "n_items", "item_value", "item_tier", "item_leaf",
                 "item_pool", "item_acq", "item_gap", "item_nprod", "item_cycle",
                 "item_ncons",
                 "n_or", "or_members", "or_rep", "or_index", "or_items", "or_set",
                 "in_ptr", "in_node", "in_amt",
                 "out_ptr", "out_item", "out_amt", "out_chance",
                 "prod_ptr", "prod_rec", "prod_amt",
                 "mach_ptr", "mach_item", "mach_tier", "rec_mach_tier",
                 "machine_ids", "machine_tier", "machine_unbuildable",
                 "template_id", "system", "recipe_id",
                 "shape_role", "tier", "max_input_tier", "reverse_tier",
                 "reliability", "weight", "unique_producer", "self_dependent",
                 "junk_p", "role_p", "role_classes", "role_argmax",
                 "eff_role", "score", "pool_members", "pool_edges",
                 "meta")

    def item_of(self, base_id: str) -> int:
        """Item index of a ``base_id``, or ``-1``.

        A dict, not a ``searchsorted``: the metric and guard passes ask this
        question once per content row of every action, which on the largest
        pack is millions of calls per evaluation run.
        """
        return self.item_map.get(base_id, -1)

    def producers_of(self, item: int) -> np.ndarray:
        return self.prod_rec[self.prod_ptr[item]:self.prod_ptr[item + 1]]

    def out_amount(self, r: int, item: int) -> float:
        lo, hi = self.out_ptr[r], self.out_ptr[r + 1]
        sel = self.out_item[lo:hi] == item
        if not sel.any():
            return 0.0
        return float((self.out_amt[lo:hi][sel] * self.out_chance[lo:hi][sel]).sum())


def _or_representative(members: list, items: np.ndarray, value: np.ndarray,
                       ncons: np.ndarray) -> str:
    """Cheapest member of an OR group; ties to the most-consumed, then the id.

    "Cheapest in pool" of the plan text: the pool price *is* the fitted value,
    so the rule is one sort key over three stable columns.
    """
    best, best_key = None, None
    for m in members:
        i = int(np.searchsorted(items, m))
        if i < items.size and items[i] == m:
            key = (float(value[i]), -int(ncons[i]), m)
        else:
            key = (math.inf, 0, m)
        if best_key is None or key < best_key:
            best, best_key = m, key
    return best


def build_index(cache_dir: Path, out_dir: Path, slug: str, pack: str,
                knobs: Knobs) -> PackIndex:
    """Resolve one pack into the planner's working index."""
    t0 = time.perf_counter()
    ix = PackIndex()
    ix.slug, ix.pack = slug, pack
    cdir, odir = cache_dir / slug, out_dir / slug
    econ = odir / "economy"

    rec = pq.read_table(cdir / "recipes.parquet", columns=[
        "recipe_idx", "system", "recipe_id", "machines", "dropped"])
    dropped = rec.column("dropped").to_numpy(zero_copy_only=False).astype(bool)
    ix.n_recipes = rec.num_rows
    ix.keep = ~dropped
    ix.ridx = np.arange(ix.n_recipes, dtype=np.int64)
    ix.system = np.asarray(rec.column("system").to_pylist())
    ix.recipe_id = np.asarray(rec.column("recipe_id").to_pylist())

    # -- items -------------------------------------------------------------
    iv = pq.read_table(econ / "item_values.parquet")
    ix.items = np.asarray(iv.column("base_id").to_pylist())
    order = np.argsort(ix.items, kind="stable")
    ix.items = ix.items[order]
    ix.n_items = ix.items.size
    ix.item_map = {str(v): i for i, v in enumerate(ix.items)}
    ix.item_value = iv.column("value_fitted").to_numpy()[order]
    ix.item_tier = iv.column("tier").to_numpy()[order]
    ix.item_leaf = iv.column("is_leaf").to_numpy(zero_copy_only=False)[order]
    ix.item_pool = np.asarray(iv.column("pool_id").to_pylist())[order]
    ix.item_acq = iv.column("acquirability").to_numpy()[order]
    ix.item_gap = iv.column("data_gap").to_numpy(zero_copy_only=False)[order]
    ix.item_nprod = iv.column("n_producers").to_numpy()[order].astype(np.int64)
    ix.item_ncons = iv.column("n_consumers").to_numpy()[order].astype(np.int64)
    # Phase 0b's own verdict on which items nothing in the pack can make: the
    # closed rings it had to bootstrap.  Re-deriving them here would be a
    # second implementation of the same decision, so the planner reads it.
    ix.item_cycle = (np.asarray(iv.column("value_source").to_pylist())[order]
                     == "cycle")

    # -- contents ----------------------------------------------------------
    con = pq.read_table(cdir / "contents.parquet", columns=[
        "recipe_idx", "side", "kind", "base_id", "members", "n_members",
        "amount", "chance"])
    cr = con.column("recipe_idx").to_numpy().astype(np.int64)
    con = con.filter(pa.array(ix.keep[cr]))
    cr = con.column("recipe_idx").to_numpy().astype(np.int64)
    # Dictionary codes, not object arrays: comparing 1.58 M python strings per
    # pass costs ~15 s on the largest pack, the codes cost 0.5 s.  Same trick
    # ``economy.py`` uses, same reason.
    side_c, side_v = _codes(con.column("side"))
    kind_c, kind_v = _codes(con.column("kind"))
    base_c, base_v = _codes(con.column("base_id"))
    nmem = con.column("n_members").to_numpy().astype(np.int64)
    amount = np.nan_to_num(con.column("amount").to_numpy(
        zero_copy_only=False).astype(np.float64), nan=1.0)
    chance = con.column("chance").to_numpy().astype(np.float64)

    is_or = np.isin(kind_v, OR_KINDS)[kind_c] & (nmem > 0)
    is_stack = (kind_v == "stack")[kind_c]

    # OR groups, keyed by the group's own base id (the exporter's display id).
    or_keys = (np.unique(base_v[base_c[is_or]]) if is_or.any()
               else np.zeros(0, dtype=object))
    ix.n_or = or_keys.size
    ix.or_index = {k: i for i, k in enumerate(or_keys)}
    ix.or_members = []
    ix.or_items, ix.or_set = [], []
    ix.or_rep = np.zeros(ix.n_or, dtype=np.int64)
    if ix.n_or:
        mem_col = con.column("members").combine_chunks()
        or_rows = np.flatnonzero(is_or)
        gcode = lookup(or_keys, base_v[base_c[or_rows]])
        _, firsts = np.unique(gcode, return_index=True)
        first = {int(gcode[f]): int(or_rows[f]) for f in firsts}
        for gi, key in enumerate(or_keys):
            members = sorted(set(mem_col[first[gi]].as_py() or []))
            ix.or_members.append(members)
            idx = [i for i in lookup(ix.items, np.asarray(members, dtype=object)).tolist()
                   if i >= 0] if members else []
            ix.or_items.append(idx)
            ix.or_set.append(set(idx))
            rep = _or_representative(members, ix.items, ix.item_value,
                                     ix.item_ncons)
            ix.or_rep[gi] = ix.item_of(rep) if rep is not None else -1

    # node id: 0..n_items-1 are items, n_items.. are OR groups
    item_of_code = lookup(ix.items, base_v)
    node = np.full(base_c.size, -1, dtype=np.int64)
    node[is_stack] = item_of_code[base_c[is_stack]]
    if ix.n_or:
        gi = lookup(or_keys, base_v[base_c[is_or]])
        node[is_or] = np.where(gi >= 0, gi + ix.n_items, -1)
    side_in = (side_v == SIDE_IN)[side_c]
    side_out = (side_v == SIDE_OUT)[side_c]

    in_sel = side_in & (node >= 0)
    ix.in_ptr, o = build_csr(cr[in_sel], ix.n_recipes)
    ix.in_node = node[in_sel][o]
    ix.in_amt = amount[in_sel][o]

    out_sel = side_out & is_stack & (node >= 0)
    ix.out_ptr, o = build_csr(cr[out_sel], ix.n_recipes)
    ix.out_item = node[out_sel][o]
    ix.out_amt = amount[out_sel][o]
    ix.out_chance = chance[out_sel][o]

    ix.prod_ptr, o = build_csr(ix.out_item, ix.n_items)
    prod_rec = np.repeat(np.arange(ix.n_recipes, dtype=np.int64),
                         np.diff(ix.out_ptr))
    ix.prod_rec = prod_rec[o]
    ix.prod_amt = (ix.out_amt * ix.out_chance)[o]

    # -- machines ----------------------------------------------------------
    mt = pq.read_table(econ / "machines.parquet")
    ix.machine_ids = np.asarray(mt.column("machine_id").to_pylist())
    mo = np.argsort(ix.machine_ids, kind="stable")
    ix.machine_ids = ix.machine_ids[mo]
    ix.machine_tier = mt.column("tier").to_numpy()[mo]
    ix.machine_unbuildable = mt.column(
        "machine_unbuildable").to_numpy(zero_copy_only=False)[mo]
    mach_col = rec.column("machines").combine_chunks()
    offsets = mach_col.offsets.to_numpy().astype(np.int64)
    ix.mach_ptr = offsets - offsets[0]
    mflat_c, mflat_v = _codes(mach_col.values)
    ix.mach_item = (lookup(ix.machine_ids, mflat_v)[mflat_c]
                    if mflat_c.size else np.zeros(0, dtype=np.int64))
    ix.mach_tier = np.where(ix.mach_item >= 0,
                            ix.machine_tier[np.clip(ix.mach_item, 0, None)], 0.0)

    # -- shape / economy per recipe ---------------------------------------
    sh = pq.read_table(econ / "recipe_shape.parquet")
    srec = sh.column("recipe_idx").to_numpy().astype(np.int64)
    full = lambda col, dtype, default: _scatter(  # noqa: E731
        srec, sh.column(col).to_numpy(zero_copy_only=False), ix.n_recipes,
        dtype, default)
    ix.shape_role = _scatter(srec, np.asarray(sh.column("shape_role").to_pylist()),
                             ix.n_recipes, object, "production")
    ix.tier = full("tier", np.float64, 0.0)
    ix.max_input_tier = full("max_input_tier", np.float64, 0.0)
    ix.reverse_tier = full("reverse_tier", bool, False)
    ix.reliability = full("reliability", np.float64, 1.0)
    ix.unique_producer = full("unique_producer", bool, False)
    ix.self_dependent = full("self_dependent", bool, False)
    ix.rec_mach_tier = full("machine_tier", np.float64, 0.0)
    rmax = float(ix.reliability[ix.keep].max()) if ix.keep.any() else 1.0
    ix.weight = np.where(ix.reliability > 0, rmax / np.maximum(ix.reliability, 1e-9), 1.0)

    # -- pools -------------------------------------------------------------
    pl = pq.read_table(econ / "pools.parquet", columns=["pool_id", "pool_kind",
                                                        "members"])
    ix.pool_members = {}
    pids = pl.column("pool_id").to_pylist()
    pkind = pl.column("pool_kind").to_pylist()
    pmem = pl.column("members").to_pylist()
    for pid, kindv, mem in zip(pids, pkind, pmem):
        if kindv == "strong":
            ix.pool_members[pid] = sorted(mem)
    pe = pq.read_table(econ / "pool_edges.parquet")
    ix.pool_edges = {
        "pool_id": np.asarray(pe.column("pool_id").to_pylist()),
        "src": lookup(ix.items, np.asarray(pe.column("src").to_pylist())),
        "dst": lookup(ix.items, np.asarray(pe.column("dst").to_pylist())),
        "unit_cost": pe.column("unit_cost").to_numpy(),
        "recipe_idx": pe.column("recipe_idx").to_numpy().astype(np.int64),
    }

    ix.template_id = np.asarray(pq.read_table(
        odir / "recipe_template.parquet",
        columns=["template_id"]).column("template_id").to_pylist())
    ix.meta = {"build_seconds": round(time.perf_counter() - t0, 3),
               "n_recipes": int(ix.n_recipes), "n_kept": int(ix.keep.sum()),
               "n_items": int(ix.n_items), "n_or": int(ix.n_or),
               "n_machines": int(ix.machine_ids.size),
               "n_strong_pools": len(ix.pool_members)}
    return ix


def _scatter(rows: np.ndarray, values: np.ndarray, n: int, dtype, default):
    out = np.full(n, default, dtype=dtype)
    out[rows] = values
    return out


# ============================================== model scores, cached per pack


def score_models(out_dir: Path, ix: PackIndex, knobs: Knobs,
                 force: bool = False) -> dict:
    """Template posteriors and per-producer ranker scores, computed once.

    ``out/<slug>/planner/template_posteriors.parquet`` — one row per template
    of this pack: ``junk_p`` and one column per role class.
    ``out/<slug>/planner/producer_scores.parquet`` — one row per kept recipe:
    the Bradley-Terry aggregate of the pairwise ranker margins among the
    producers of each of its outputs (see DESIGN.md Phase 3 §2.3).
    """
    pdir = out_dir / ix.slug / "planner"
    tpath, spath = (pdir / "template_posteriors.parquet",
                    pdir / "producer_scores.parquet")
    stamp = stable_hash({"arms": [knobs.arm_junk, knobs.arm_role, knobs.arm_rank],
                         "n": int(ix.n_recipes)}, 12)
    if not force and tpath.exists() and spath.exists():
        meta = read_json(pdir / "planner-index-meta.json")
        if meta.get("stamp") == stamp:
            return _load_scores(ix, tpath, spath, meta)

    t0 = time.perf_counter()
    ref = Reference(out_dir / "models")
    spec = ref.spec
    tnames = spec["template_features"]["names"]

    tf = pack_rows(pq.read_table(
        out_dir / "features" / "template_features.parquet",
        columns=["pack", "template_id"] + tnames), ix)
    tids = np.asarray(tf.column("template_id").to_pylist())
    x = np.column_stack([tf.column(n).to_numpy(zero_copy_only=False)
                         .astype(np.float64) for n in tnames])
    junk = ref.junk_probability(x, knobs.arm_junk)
    roles = ref.role_probabilities(x, knobs.arm_role)
    classes = ref.role_classes(knobs.arm_role)
    t_model = time.perf_counter() - t0

    t1 = time.perf_counter()
    score, sinfo = producer_scores(out_dir, ix, ref, knobs)
    t_rank = time.perf_counter() - t1

    pdir.mkdir(parents=True, exist_ok=True)
    cols = {"template_id": pa.array(tids, pa.string()),
            "junk_p": pa.array(junk, pa.float64())}
    for j, c in enumerate(classes):
        cols[f"role_{c}"] = pa.array(roles[:, j], pa.float64())
    pq.write_table(pa.table(cols), tpath, compression="snappy")
    pq.write_table(pa.table({
        "recipe_idx": pa.array(np.arange(ix.n_recipes, dtype=np.int32)),
        "producer_score": pa.array(score, pa.float64())}), spath,
        compression="snappy")
    meta = {"stamp": stamp, "classes": list(classes),
            "template_seconds": round(t_model, 3),
            "ranker_seconds": round(t_rank, 3), **sinfo, **ix.meta}
    write_json(pdir / "planner-index-meta.json", meta)
    return _load_scores(ix, tpath, spath, meta)


def _load_scores(ix: PackIndex, tpath: Path, spath: Path, meta: dict) -> dict:
    t = pq.read_table(tpath)
    tids = np.asarray(t.column("template_id").to_pylist())
    order = np.argsort(tids, kind="stable")
    tids = tids[order]
    classes = meta["classes"]
    junk = t.column("junk_p").to_numpy()[order]
    roles = np.column_stack([t.column(f"role_{c}").to_numpy()[order]
                             for c in classes])
    pos = lookup(tids, ix.template_id)
    ix.junk_p = np.where(pos >= 0, junk[np.clip(pos, 0, None)], 0.0)
    ix.role_p = np.where((pos >= 0)[:, None],
                         roles[np.clip(pos, 0, None)], 0.0)
    ix.role_classes = classes
    ix.role_argmax = np.asarray(classes, dtype=object)[ix.role_p.argmax(axis=1)]
    ix.score = pq.read_table(spath).column("producer_score").to_numpy()
    ix.eff_role = effective_roles(ix)
    return meta


def effective_roles(ix: PackIndex, role_argmax=None) -> np.ndarray:
    """Role head where it is trustworthy, Phase 0b shape role everywhere else.

    REPORT.md Phase 0c-2 issue 6: only ``production`` and ``display`` have
    usable recall; for any other argmax the shape role decides.  ``role_argmax``
    overrides the model's own argmax — the Phase 4 hook, so a role posterior
    updated by feedback goes through exactly this rule and not a second one.
    """
    shape = np.asarray([SHAPE_TO_ROLE.get(s, ROLE_PRODUCTION)
                        for s in ix.shape_role], dtype=object)
    argmax = ix.role_argmax if role_argmax is None else role_argmax
    trusted = np.isin(argmax, TRUSTED_ROLES)
    return np.where(trusted, argmax, shape)


def producer_scores(out_dir: Path, ix: PackIndex, ref: Reference,
                    knobs: Knobs):
    """Bradley-Terry aggregation of pairwise ranker margins, per output.

    The ranker answers *pairs*; the planner needs one number per producer.  For
    an output with producers ``P`` the pairwise margins form an antisymmetric
    matrix ``M``; the Bradley-Terry strengths ``theta`` that best explain it in
    least squares are the **row means** ``theta_a = mean_b M[a][b]`` (the
    classical closed form for a complete antisymmetric comparison matrix).  A
    recipe producing several outputs takes the mean of its per-output scores.

    The linear arm makes that cheap and exact.  Its margin is
    ``((f(a)-f(b))/scale) . w``, which for the 180 base columns is
    ``S(a) - S(b)`` with ``S(r) = (fill(f(r))/scale) . w`` — so the O(n^2)
    matrix never has to be formed.  The eight pair flags are handled exactly:
    five are differences of per-recipe scalars *zeroed when either side is
    NaN*, three are symmetric equality flags.  Both reduce to per-group sums.
    """
    arm = knobs.arm_rank or ref.ranker["default"]
    spec = ref.ranker[arm]
    if spec["kind"] != "lr":
        raise SystemExit("producer scoring implements the linear ranker arm; "
                         f"arm {arm!r} is {spec['kind']!r}")
    names = ref.spec["pair_features"]["names"]
    n_base = int(ref.spec["pair_features"]["n_base"])
    fill = np.asarray(ref.spec["pair_features"]["recipe_fill"], dtype=np.float64)
    w = np.asarray(spec["coef"])[0] / np.asarray(spec["scale"])

    base, flagv = _pair_inputs(out_dir, ix, names, n_base)
    z = np.where(np.isfinite(base), base, fill)
    s_base = z @ w[:n_base]

    flags = list(ref.spec["pair_features"]["flags"])
    sym = set(ref.spec["pair_features"]["symmetric_flags"])
    score = np.zeros(ix.n_recipes, dtype=np.float64)
    counted = np.zeros(ix.n_recipes, dtype=np.float64)
    groups = 0
    for item in range(ix.n_items):
        lo, hi = ix.prod_ptr[item], ix.prod_ptr[item + 1]
        if hi - lo < 2:
            if hi > lo:
                counted[ix.prod_rec[lo]] += 1.0
            continue
        members = np.unique(ix.prod_rec[lo:hi])
        if members.size < 2:
            counted[members] += 1.0
            continue
        groups += 1
        theta = _group_rowmeans(s_base[members], members, flagv, flags, sym,
                                w, n_base, ix)
        score[members] += theta
        counted[members] += 1.0
    out = np.where(counted > 0, score / np.maximum(counted, 1.0), 0.0)
    return out, {"ranker_arm": arm, "ranker_groups": int(groups)}


def _group_rowmeans(s: np.ndarray, members: np.ndarray, flagv: dict,
                    flags: list, sym: set, w: np.ndarray, n_base: int,
                    ix: PackIndex) -> np.ndarray:
    """Row means of the exact pairwise margin matrix, in O(n) per group."""
    n = members.size
    denom = float(n - 1)
    theta = s - (s.sum() - s) / denom  # mean_b (S(a) - S(b)), b != a
    for j, name in enumerate(flags):
        weight = float(w[n_base + j])
        if abs(weight) < 1e-12:
            continue
        if name in sym:
            key = flagv[name][members]
            _, inv, cnt = np.unique(key, return_inverse=True, return_counts=True)
            theta += weight * (cnt[inv] - 1) / denom
            continue
        v = flagv[name][members]
        ok = np.isfinite(v)
        vv = np.where(ok, v, 0.0)
        # b ranges over the finite entries other than a; a itself must be finite
        c = float(ok.sum()) - ok.astype(np.float64)
        tot = vv.sum() - vv
        theta += weight * np.where(ok, (c * v - tot) / denom, 0.0)
    return theta


def pack_rows(table, ix: PackIndex):
    """Rows of ``table`` belonging to this pack.

    The Phase 0c tables do not agree on what the ``pack`` column holds:
    ``features.py`` writes the **slug**, ``pair_features.py`` writes the
    **pack name** (``packs.json``'s value).  They coincide for 17 of the 22
    packs, which is why nothing upstream tripped over it.  Resolving both
    spellings here keeps this module correct without touching Phase 0c; the
    consequence for the trained models is recorded in REPORT.md Phase 3.
    """
    col = table.column("pack")
    for key in (ix.slug, ix.pack):
        sub = table.filter(pa.compute.equal(col, key))
        if sub.num_rows:
            return sub
    return table.slice(0, 0)


def _pair_inputs(out_dir: Path, ix: PackIndex, names: list, n_base: int):
    """The 180 base pair columns and the 8 flag sources, per kept recipe."""
    rf = pack_rows(pq.read_table(
        out_dir / "features" / "recipe_features.parquet"), ix)
    agg = pack_rows(pq.read_table(
        out_dir / "models" / "recipe_leaf_agg.parquet"), ix)
    tfa = pack_rows(pq.read_table(
        out_dir / "features" / "template_features.parquet"), ix)

    ridx = rf.column("recipe_idx").to_numpy().astype(np.int64)
    aidx = agg.column("recipe_idx").to_numpy().astype(np.int64)
    tids = np.asarray(tfa.column("template_id").to_pylist())
    torder = np.argsort(tids, kind="stable")
    tids = tids[torder]
    tpos = lookup(tids, ix.template_id)

    def rec_col(name):
        if name in rf.column_names:
            return _scatter(ridx, rf.column(name).to_numpy(
                zero_copy_only=False).astype(np.float64), ix.n_recipes,
                np.float64, np.nan)
        return _scatter(aidx, agg.column(name).to_numpy(
            zero_copy_only=False).astype(np.float64), ix.n_recipes,
            np.float64, np.nan)

    def tpl_col(name):
        v = tfa.column(name).to_numpy(zero_copy_only=False).astype(
            np.float64)[torder]
        return np.where(tpos >= 0, v[np.clip(tpos, 0, None)], np.nan)

    cols = []
    for name in names[:n_base]:
        cols.append(tpl_col(name[4:]) if name.startswith("tpl_")
                    else rec_col(name[4:]))
    base = np.column_stack(cols)
    flagv = {"flag_same_template": ix.template_id,
             "flag_same_system": ix.system,
             "flag_same_ns": np.asarray(
                 [r.split(":", 1)[0] if ":" in r else "" for r in ix.recipe_id],
                 dtype=object),
             "flag_d_tier": rec_col("tier"),
             "flag_d_n_inputs": rec_col("n_input_slots"),
             "flag_d_log_value_in": rec_col("log_value_in"),
             "flag_d_n_leaf_inputs": rec_col("leaf_agg_n"),
             "flag_d_leaf_acq_min": rec_col("l4_leaf_acq_min")}
    return base, flagv


# =========================================================== candidate policy


class Policy:
    """Which producers may fire, and under which role.

    Java mapping: the ``SourcePolicyDecision`` bitmap that ``StandardPlanner``
    reads at ``scanFilteredProducers``.  One bit per recipe, decided once.
    """

    __slots__ = ("excluded", "protected_", "role", "flagged", "stats",
                 "junk_p", "sig")

    def __init__(self, ix: PackIndex, knobs: Knobs, opt=None):
        # The Phase 4 hook: the feedback view replaces the model's posteriors,
        # its roles and its scores with the ones the closed-form updates
        # produced.  With no view the three arrays *are* the model's.
        view = _fb(opt)
        junk_p = ix.junk_p if view is None else view.junk_p
        role_p = ix.role_p if view is None else view.role_p
        eff_role = ix.eff_role if view is None else view.eff_role
        display_i = (ix.role_classes.index(ROLE_DISPLAY)
                     if ROLE_DISPLAY in ix.role_classes else -1)
        p_display = (role_p[:, display_i] if display_i >= 0
                     else np.zeros(ix.n_recipes))
        # The display role is a rejection like the two posteriors, so that the
        # unique-producer protection below covers all three: an output whose
        # only producer the role head calls a display page still has to be
        # reachable (REPORT Phase 0c-2 gate "唯一生产者永不被排除").
        flagged = ((junk_p > knobs.tau_junk) |
                   (p_display > knobs.tau_display) |
                   (eff_role == ROLE_DISPLAY)) & ix.keep
        hard = None
        if view is not None:
            # An explicit ban is a statement, not a posterior: it flags
            # unconditionally and the sole-producer protection below may not
            # rescue it.  An explicit preference is the same in reverse.
            hard = view.banned
            flagged = ((flagged | hard) & ~view.preferred) & ix.keep
        # Unique-producer protection: an output whose every producer is
        # flagged keeps the least-suspect one.  A recipe protected for ANY of
        # its outputs survives.
        keepers = np.zeros(ix.n_recipes, dtype=bool)
        for item in range(ix.n_items):
            lo, hi = ix.prod_ptr[item], ix.prod_ptr[item + 1]
            if hi <= lo:
                continue
            members = np.unique(ix.prod_rec[lo:hi])
            live = members[~flagged[members]]
            if live.size:
                continue
            cand = members if hard is None else members[~hard[members]]
            if not cand.size:
                continue
            pick = cand[np.lexsort((cand, junk_p[cand]))[0]]
            keepers[pick] = True
        self.flagged = flagged
        self.junk_p = junk_p
        self.protected_ = keepers & flagged
        self.excluded = (flagged & ~keepers) | ~ix.keep
        self.role = eff_role
        self.stats = {
            "flagged": int(flagged.sum()),
            "protected": int(self.protected_.sum()),
            "excluded": int((flagged & ~keepers).sum()),
            "role_counts": {c: int((self.role[ix.keep] == c).sum())
                            for c in sorted(set(self.role[ix.keep].tolist()))},
        }

    def signature(self) -> str:
        """Stable fingerprint of the exclusion bitmap, computed once.

        The cross-request memo key needs it on *every* request; hashing the
        bitmap each time costs 9 ms on the largest pack, which is most of what
        the memo saves.  Java's ``SourcePolicyDecision`` would carry the same
        fingerprint as a field.
        """
        got = getattr(self, "sig", None)
        if got is None:
            got = self.sig = stable_hash(
                [int(x) for x in np.flatnonzero(self.excluded)], 16)
        return got

    def reason(self, ix: PackIndex, r: int, knobs: Knobs) -> str:
        bits = []
        if self.junk_p[r] > knobs.tau_junk:
            bits.append(f"junk_p={self.junk_p[r]:.3f}>tau")
        di = (ix.role_classes.index(ROLE_DISPLAY)
              if ROLE_DISPLAY in ix.role_classes else -1)
        if di >= 0 and ix.role_p[r, di] > knobs.tau_display:
            bits.append(f"p_{ROLE_DISPLAY}={ix.role_p[r, di]:.3f}>tau")
        if self.protected_[r]:
            bits.append("protected(sole producer)")
        return ",".join(bits) or "-"


# ============================================== material layer — the cost solve


class CostSolve:
    """One request-scoped fixpoint over the recipe hypergraph.

    Memo key (DESIGN.md Phase 3 §3): ``(pack fingerprint, policy signature,
    knob signature, stock signature)``.  The *item* is the array index into
    ``cost`` — so the memo is "per (item, remaining-stock-signature)" with the
    per-item dimension materialised as a dense array, which is what makes the
    whole solve reusable across the targets of one batch.
    """

    __slots__ = ("cost", "source", "settled", "or_best", "acc", "proc",
                 "machines_paid", "step_unit", "stats", "key", "seq",
                 "gathered")


def step_unit(ix: PackIndex, knobs: Knobs) -> float:
    leaf = ix.item_value[ix.item_leaf]
    med = float(np.median(leaf)) if leaf.size else 1.0
    return knobs.step_cost * (med if med > 0 else 1.0)


def solve_cost(ix: PackIndex, pol: Policy, knobs: Knobs,
               stock: dict = None, opt=None) -> CostSolve:
    """Knuth's generalisation of Dijkstra, with stock, machines and tiers.

        inputs(r)   = sum over input nodes of cost[node] * amount
        steps(r)    = step_unit * (1 + tier[r])
        machines(r) = machine_cost * step_unit * sum of the machine tiers this
                      request has not paid for yet (greedy per-solve set)
        tierpen(r)  = tier_penalty * step_unit * max(0, max_input_tier - out
                      tier) , doubled when Phase 0b flagged ``reverse_tier``
        weight(r)   = max reliability in pack / this recipe's reliability
        cost(r)     = (inputs + steps + machines + tierpen) * weight * rolemult
        cost[item]  = min over producers of cost(r) / effective output amount
        cost[leaf]  = the Phase 0b acquirability seed
        cost[stock] = 0

    Settling once in non-decreasing order is what makes a storage ladder
    impossible: a conversion edge can only fire *after* its input settled, so
    the cheaper form of a pool is always reached first and the ladder has no
    downhill direction to run in.
    """
    import heapq

    inf = float("inf")
    n_items, n_nodes = ix.n_items, ix.n_items + ix.n_or
    sv = CostSolve()
    sv.step_unit = su = step_unit(ix, knobs)
    cost = [inf] * n_nodes
    source = [-1] * n_items
    settled = [False] * n_nodes
    or_best = [-1] * ix.n_or
    # Settle order.  0 means "never settled" — nothing in the pack makes it
    # from leaves, so the planner gathers it and it is available from the
    # start, exactly like a leaf.  Everything else settles at 1, 2, 3, ...
    # in non-decreasing cost.  This total order is the acyclicity certificate
    # the form layer checks against (DESIGN.md Phase 3 §4).
    seq = [0] * n_nodes
    clock = [0]
    acc = [0.0] * ix.n_recipes
    proc = [0.0] * ix.n_recipes
    paid = set()

    in_ptr, in_node, in_amt = (ix.in_ptr.tolist(), ix.in_node.tolist(),
                               ix.in_amt.tolist())
    out_ptr, out_item = ix.out_ptr.tolist(), ix.out_item.tolist()
    out_eff = (ix.out_amt * ix.out_chance).tolist()
    mach_ptr, mach_item = ix.mach_ptr.tolist(), ix.mach_item.tolist()
    mach_tier = ix.mach_tier.tolist()
    tier, mit = ix.tier.tolist(), ix.max_input_tier.tolist()
    rev, wgt = ix.reverse_tier.tolist(), ix.weight.tolist()
    itier = ix.item_tier.tolist()
    role = pol.role
    excluded = pol.excluded
    pend = _pending(ix).tolist()
    for r in np.flatnonzero(excluded).tolist():
        pend[r] = -1

    view = _fb(opt)
    # Item-level ban: "this item is not available as an input source".  Every
    # recipe that consumes it directly dies; an any-of input simply loses that
    # member (``_or_membership`` drops it, so the OR node prices from the
    # members that are left).
    ibanned = None if view is None else view.item_banned
    # Item-level preference: a documented discount on the price this item
    # contributes to whoever consumes it.  It is applied where the price is
    # *consumed*, not where it settles, so the settle order — the acyclicity
    # certificate — is untouched.
    pf = None if view is None else view.item_factor
    if pf is not None and not (pf != 1.0).any():
        pf = None
    if ibanned is not None:
        for r in range(ix.n_recipes):
            if pend[r] < 0:
                continue
            for k in range(in_ptr[r], in_ptr[r + 1]):
                node = in_node[k]
                if node < n_items and ibanned[node]:
                    pend[r] = -1
                    break

    cons_ptr, cons_rec, cons_amt = _consumers(ix)
    orm_ptr, orm_of = _or_membership(ix, ibanned)

    heap: list = []
    stock = stock or {}
    if view is not None and view.stock:
        stock = {**view.stock, **stock}
    for base, qty in sorted(stock.items()):
        i = ix.item_of(base)
        if i >= 0 and qty > 0:
            cost[i] = 0.0
            heapq.heappush(heap, (0.0, i))
    # Leaves, producerless items, and the closed rings Phase 0b bootstrapped:
    # all three are "nothing in this pack makes it", so all three enter at the
    # acquirability seed Phase 0b priced them at.
    gatherable = ix.item_leaf | (ix.item_nprod == 0) | ix.item_cycle
    if ibanned is not None:
        gatherable = gatherable & ~ibanned
    gathered = [False] * n_items
    for i in np.flatnonzero(gatherable).tolist():
        gathered[i] = True
        seed = float(ix.item_value[i])
        if seed < cost[i]:
            cost[i] = seed
            heapq.heappush(heap, (seed, i))

    def fire(r):
        mach = 0.0
        for k in range(mach_ptr[r], mach_ptr[r + 1]):
            m = mach_item[k]
            if m >= 0 and m not in paid:
                mach += mach_tier[k]
        lo, hi = out_ptr[r], out_ptr[r + 1]
        if hi <= lo:
            return
        out_tier = min(itier[out_item[k]] for k in range(lo, hi))
        gap = mit[r] - out_tier
        pen = (knobs.tier_penalty * su * gap * (2.0 if rev[r] else 1.0)
               if gap > 0 else 0.0)
        p = su * (1.0 + tier[r]) + knobs.machine_cost * su * mach + pen
        proc[r] = p
        mult = (knobs.recycling_mult if role[r] == ROLE_RECYCLING else
                knobs.generation_mult if role[r] == ROLE_GENERATION else 1.0)
        total = (acc[r] + p) * wgt[r] * mult
        touched = False
        for k in range(lo, hi):
            item, amt = out_item[k], out_eff[k]
            if amt <= 0:
                continue
            c = total / amt
            if not settled[item] and c < cost[item]:
                cost[item] = c
                source[item] = r
                heapq.heappush(heap, (c, item))
                touched = True
        if touched:
            for k in range(mach_ptr[r], mach_ptr[r + 1]):
                if mach_item[k] >= 0:
                    paid.add(mach_item[k])

    for r in np.flatnonzero(np.asarray(pend) == 0).tolist():
        fire(r)
    fired = [0]

    def drain():
        while heap:
            v, node = heapq.heappop(heap)
            if settled[node] or v > cost[node]:
                continue
            settled[node] = True
            clock[0] += 1
            seq[node] = clock[0]
            if node < n_items:
                for k in range(orm_ptr[node], orm_ptr[node + 1]):
                    o = n_items + orm_of[k]
                    if not settled[o] and v < cost[o]:
                        cost[o] = v
                        or_best[orm_of[k]] = node
                        heapq.heappush(heap, (v, o))
            v_eff = v
            if pf is not None:
                if node < n_items:
                    v_eff = v * pf[node]
                else:
                    b = or_best[node - n_items]
                    v_eff = v * pf[b] if b >= 0 else v
            for k in range(cons_ptr[node], cons_ptr[node + 1]):
                r = cons_rec[k]
                if pend[r] < 0:
                    continue
                acc[r] += v_eff * cons_amt[k]
                pend[r] -= 1
                if pend[r] == 0:
                    fired[0] += 1
                    fire(r)

    drain()
    # Rings the queue cannot enter, and items the candidate policy orphaned.
    # Same rule as ``economy.py``: an item no live producer can ever reach is
    # priced at its own Phase 0b seed and gathered; a ring with no way in is
    # entered at its cheapest member.  Phase 0b's own rings are already seeded
    # above, so these rounds only clean up what *this* layer's exclusions cost.
    seeded, rounds = 0, 0
    while rounds < MAX_BOOT_ROUNDS:
        stuck = [i for i in range(n_items) if not settled[i]]
        if not stuck:
            break
        rounds += 1
        live, blockers = set(), {}
        for r in range(ix.n_recipes):
            if pend[r] <= 0:
                continue
            waiting = [int(ix.in_node[k]) for k in range(in_ptr[r], in_ptr[r + 1])
                       if not settled[int(ix.in_node[k])]]
            for k in range(out_ptr[r], out_ptr[r + 1]):
                live.add(out_item[k])
                blockers.setdefault(out_item[k], []).append(waiting)
        orphans = [i for i in stuck if i not in live]
        # Ring entries, batched: an unsettled item is the way *into* its ring
        # when nothing it still waits on is cheaper than itself.  That is
        # ``economy.py``'s "cheapest member of a closed ring", evaluated
        # locally so every independent ring is entered in the same round.
        entries = []
        if not orphans:
            for i in stuck:
                mine = float(ix.item_value[i])
                if all(float(ix.item_value[u if u < n_items else i]) >= mine
                       for w in blockers.get(i, ()) for u in w):
                    entries.append(i)
        picks = orphans or entries or [
            min(stuck, key=lambda i: (float(ix.item_value[i]), str(ix.items[i])))]
        if ibanned is not None:
            picks = [i for i in picks if not ibanned[i]]
        progress = False
        for i in picks:
            seed = float(ix.item_value[i])
            if seed < cost[i]:
                cost[i] = seed
                gathered[i] = True
                heapq.heappush(heap, (seed, i))
                seeded += 1
                progress = True
        if not progress:
            break
        drain()

    sv.cost = cost
    sv.gathered = gathered
    sv.seq = seq
    sv.source = source
    sv.settled = settled
    sv.or_best = or_best
    sv.acc, sv.proc = acc, proc
    sv.machines_paid = paid
    sv.stats = {"fired": fired[0], "boot_rounds": rounds,
                "bootstrapped": seeded, "unreached": int(sum(
        1 for i in range(n_items) if cost[i] == inf)),
        "machines_paid": len(paid)}
    return sv


def _pending(ix: PackIndex) -> np.ndarray:
    sizes = np.diff(ix.in_ptr)
    pend = sizes.astype(np.int64).copy()
    pend[~ix.keep] = -1
    return pend


def _consumers(ix: PackIndex):
    n_nodes = ix.n_items + ix.n_or
    rec = np.repeat(np.arange(ix.n_recipes, dtype=np.int64), np.diff(ix.in_ptr))
    ptr, order = build_csr(ix.in_node, n_nodes)
    return ptr.tolist(), rec[order].tolist(), ix.in_amt[order].tolist()


def _or_membership(ix: PackIndex, ibanned=None):
    if not ix.n_or:
        return [0] * (ix.n_items + 1), []
    mem, grp = [], []
    for gi, members in enumerate(ix.or_members):
        for m in members:
            i = ix.item_of(m)
            if i >= 0 and (ibanned is None or not ibanned[i]):
                mem.append(i)
                grp.append(gi)
    mem = np.asarray(mem, dtype=np.int64)
    grp = np.asarray(grp, dtype=np.int64)
    ptr, order = build_csr(mem, ix.n_items)
    return ptr.tolist(), grp[order].tolist()


# ============================================================ pool unit weights


def pool_units(ix: PackIndex) -> dict:
    """``item -> units of its pool's representative`` for every pooled item.

    Propagated over ``pool_edges``: an edge ``src -> dst`` with
    ``unit_cost = a/b`` says one ``dst`` costs ``a/b`` of ``src``, so
    ``u[dst] = u[src] * unit_cost``.  A conversion that conserves material has
    ``sum u*amount`` equal on both sides; one that *creates* material does not,
    and that difference is the amplification guard.
    """
    adj: dict = {}
    for s, d, c in zip(ix.pool_edges["src"], ix.pool_edges["dst"],
                       ix.pool_edges["unit_cost"]):
        if s < 0 or d < 0 or not (c > 0):
            continue
        adj.setdefault(int(s), []).append((int(d), float(c)))
        adj.setdefault(int(d), []).append((int(s), 1.0 / float(c)))
    units, seen = {}, set()
    for pid, members in sorted(ix.pool_members.items()):
        idx = [ix.item_of(m) for m in members]
        idx = [i for i in idx if i >= 0 and i not in seen]
        if not idx:
            continue
        root = min(idx, key=lambda i: (float(ix.item_value[i]), str(ix.items[i])))
        units[root], queue = 1.0, [root]
        seen.add(root)
        while queue:
            cur = queue.pop()
            for nxt, mult in adj.get(cur, ()):  # deterministic: insertion order
                if nxt in seen:
                    continue
                seen.add(nxt)
                units[nxt] = units[cur] * mult
                queue.append(nxt)
    return units


# ===================================================== form layer + extraction


class Choice:
    __slots__ = ("recipe", "cost", "runner_up", "runner_cost", "shortlist",
                 "rejected", "reason")


class Plan:
    """The emitted plan.  Java mapping: ``RoutingPlan`` + ``PlanBuilder``."""

    __slots__ = ("pack", "slug", "target", "amount", "status", "actions",
                 "raw", "unresolved", "catalysts", "stock", "profile",
                 "metrics", "guards", "knobs", "seconds", "opt")

    def __init__(self):
        self.actions, self.raw, self.unresolved = [], {}, []
        self.catalysts, self.guards = [], []
        self.profile, self.metrics = {}, {}
        self.status = "ok"
        self.opt = None


class Ledger:
    """Stock, byproducts and reservations.  Java mapping: ``ResourceLedger``.

    Keyed by **item index**, not by id string: an any-of input asks "do I hold
    any member of this group", and one set intersection answers it whatever
    the group's size.
    """

    __slots__ = ("have", "origin")

    def __init__(self, stock: dict, ix: "PackIndex"):
        self.have, self.origin = {}, {}
        for base, qty in sorted(stock.items()):
            i = ix.item_of(base)
            if i >= 0 and qty > 0:
                self.have[i] = float(qty)
                self.origin[i] = "stock"

    def take(self, item: int, qty: float) -> float:
        got = min(self.have.get(item, 0.0), qty)
        if got > 0:
            self.have[item] -= got
            if self.have[item] <= 1e-9:
                self.have.pop(item, None)
        return got

    def give(self, item: int, qty: float, origin: str) -> None:
        if qty <= 0:
            return
        self.have[item] = self.have.get(item, 0.0) + qty
        self.origin.setdefault(item, origin)


def producer_cost(ix: PackIndex, sv: CostSolve, knobs: Knobs, pol: Policy,
                  r: int, item: int, used_machines: set, opt=None) -> tuple:
    """Effective cost of routing one unit of ``item`` through recipe ``r``.

    Returns ``(cost per unit, breakdown)``.  The machine term is **exact** here
    (the plan's own machine set is known), which is the promised refinement of
    the greedy per-solve approximation.
    """
    su = sv.step_unit
    view = _fb(opt)
    pf = None if view is None else view.item_factor
    inputs = 0.0
    for k in range(ix.in_ptr[r], ix.in_ptr[r + 1]):
        node, amt = int(ix.in_node[k]), float(ix.in_amt[k])
        c = sv.cost[node]
        if c == float("inf"):
            return float("inf"), {}
        if pf is not None:
            pick = _resolve_or(ix, sv, node, _EMPTY_LEDGER, opt)
            if pick >= 0:
                c *= float(pf[pick])
        inputs += c * amt
    mach = sum(float(ix.mach_tier[k])
               for k in range(ix.mach_ptr[r], ix.mach_ptr[r + 1])
               if int(ix.mach_item[k]) >= 0
               and int(ix.mach_item[k]) not in used_machines)
    lo, hi = ix.out_ptr[r], ix.out_ptr[r + 1]
    out_tier = min(float(ix.item_tier[ix.out_item[k]]) for k in range(lo, hi))
    gap = float(ix.max_input_tier[r]) - out_tier
    pen = (knobs.tier_penalty * su * gap * (2.0 if ix.reverse_tier[r] else 1.0)
           if gap > 0 else 0.0)
    steps = su * (1.0 + float(ix.tier[r]))
    machines = knobs.machine_cost * su * mach
    mult = (knobs.recycling_mult if pol.role[r] == ROLE_RECYCLING else
            knobs.generation_mult if pol.role[r] == ROLE_GENERATION else 1.0)
    total = (inputs + steps + machines + pen) * float(ix.weight[r]) * mult
    amount = ix.out_amount(r, item)
    if amount <= 0:
        return float("inf"), {}
    return total / amount, {
        "inputs": round(inputs, 6), "steps": round(steps, 6),
        "machines": round(machines, 6), "tier_penalty": round(pen, 6),
        "reliability_weight": round(float(ix.weight[r]), 6),
        "role_multiplier": round(mult, 6), "out_amount": round(amount, 6),
        "unit_cost": round(total / amount, 6)}


def choose_producer(ix: PackIndex, sv: CostSolve, pol: Policy, knobs: Knobs,
                    item: int, ledger: Ledger, used_machines: set,
                    banned: set, plan_edges: set = None, opt=None) -> Choice:
    """Cost shortlist within factor gamma, then the ranker decides inside it."""
    score = eff_score(ix, opt)
    ch = Choice()
    ch.rejected, ch.shortlist = [], []
    cands = []
    target_seq = sv.seq[item]
    for r in sorted(set(int(x) for x in ix.producers_of(item))):
        if pol.excluded[r] or r in banned:
            ch.rejected.append((r, "excluded" if pol.excluded[r] else "loop"))
            continue
        # Acyclicity: every input must have settled strictly before ``item``
        # did, unless the ledger already holds it.  Because the settle order
        # is total, a plan built under this rule cannot contain A -> B and
        # B -> A, so round trips are impossible *by construction* rather than
        # detected afterwards.
        late = [k for k in range(ix.in_ptr[r], ix.in_ptr[r + 1])
                if sv.seq[int(ix.in_node[k])] >= target_seq
                and not _input_held(ix, int(ix.in_node[k]), ledger, opt)]
        if late:
            ch.rejected.append((r, "re-enters the demand"))
            continue
        # The ledger exception above is what lets a conversion fire off a
        # byproduct.  It must not be able to re-open an edge the plan has
        # already taken the other way round: that IS the round trip.
        if plan_edges and pol.role[r] == ROLE_CONVERSION and any(
                (item, _resolve_or(ix, sv, int(ix.in_node[k]), ledger, opt))
                in plan_edges
                for k in range(ix.in_ptr[r], ix.in_ptr[r + 1])):
            ch.rejected.append((r, "would reverse an edge already in the plan"))
            continue
        c, brk = producer_cost(ix, sv, knobs, pol, r, item, used_machines,
                               opt)
        if c == float("inf"):
            ch.rejected.append((r, "unreachable input"))
            continue
        cands.append((r, c, brk))
    if not cands:
        ch.recipe = -1
        ch.reason = "no admissible producer"
        return ch

    # role gates: recycling is a last resort, and a conversion edge may only be
    # taken when the ledger already holds one of its inputs (plan §二 conservation)
    def held(r):
        for k in range(ix.in_ptr[r], ix.in_ptr[r + 1]):
            if _input_held(ix, int(ix.in_node[k]), ledger, opt):
                return True
        return False

    primary = [c for c in cands if pol.role[c[0]] != ROLE_RECYCLING]
    if primary:
        for c in cands:
            if pol.role[c[0]] == ROLE_RECYCLING:
                ch.rejected.append((c[0], "last resort (" + ROLE_RECYCLING + ")"))
        cands = primary
    if knobs.conversion_needs_stock:
        keepc = [c for c in cands
                 if pol.role[c[0]] != ROLE_CONVERSION or held(c[0])]
        if keepc and len(keepc) < len(cands):
            for c in cands:
                if c not in keepc:
                    ch.rejected.append((c[0], ROLE_CONVERSION + ", input not held"))
            cands = keepc

    if opt is not None and opt.fold_templates:
        # Phase 5 fold pruning: siblings that share a ``template_id`` are the
        # same recipe as far as the model is concerned, so only the best-scored
        # representative reaches the shortlist (ties by cost, then recipe_idx).
        rep: dict = {}
        for c in cands:
            key = str(ix.template_id[c[0]])
            cur = rep.get(key)
            if cur is None or (-float(score[c[0]]), c[1], c[0]) < \
                    (-float(score[cur[0]]), cur[1], cur[0]):
                rep[key] = c
        kept = set(id(c) for c in rep.values())
        if len(rep) < len(cands):
            for c in cands:
                if id(c) not in kept:
                    ch.rejected.append((c[0], "folded into its template"))
            cands = [c for c in cands if id(c) in kept]
    best = min(c[1] for c in cands)
    short = [c for c in cands if c[1] <= best * knobs.gamma + 1e-12]
    ch.shortlist = [(r, c) for r, c, _ in short]
    if knobs.use_ranker:
        short.sort(key=lambda c: (-float(score[c[0]]), c[1], c[0]))
    else:
        short.sort(key=lambda c: (c[1], c[0]))
    ch.recipe, ch.cost = short[0][0], short[0][1]
    if len(short) > 1:
        ch.runner_up, ch.runner_cost = short[1][0], short[1][1]
    else:
        rest = sorted((c for c in cands if c[0] != ch.recipe),
                      key=lambda c: (c[1], c[0]))
        ch.runner_up = rest[0][0] if rest else -1
        ch.runner_cost = rest[0][1] if rest else float("inf")
    ch.reason = "ranker" if (knobs.use_ranker and len(short) > 1
                             and ch.cost > best + 1e-12) else "cost"
    return ch


def cone_size(ix: PackIndex, pol: Policy, root: int, cap: int) -> int:
    """Distinct admissible producers reachable from ``root`` — Java's
    ``RoutingBudget.fromConeStats`` input."""
    return cone_stats(ix, pol, root, cap)[0]


def cone_stats(ix: PackIndex, pol: Policy, root: int, cap: int) -> tuple:
    """``(producers, depth)`` of the demand cone, in one walk.

    The producer count is Phase 3's budget input; the hop depth is the Phase 5
    depth-scaled variant's.  Breadth-first so the depth is the true hop
    distance, and capped the same way (a pathological cone must not hang).
    """
    seen_i, seen_r = {root}, set()
    frontier, depth = [root], 0
    while frontier and len(seen_r) < cap:
        nxt = []
        for item in frontier:
            for r in ix.producers_of(item):
                r = int(r)
                if pol.excluded[r] or r in seen_r:
                    continue
                seen_r.add(r)
                for k in range(ix.in_ptr[r], ix.in_ptr[r + 1]):
                    node = int(ix.in_node[k])
                    members = ([node] if node < ix.n_items
                               else ix.or_items[node - ix.n_items])
                    for m in members:
                        if m >= 0 and m not in seen_i:
                            seen_i.add(m)
                            nxt.append(m)
        if nxt:
            depth += 1
        frontier = nxt
    return len(seen_r), depth


def _raised_budget(knobs: Knobs, opt, state: dict) -> int:
    """The one-shot budget raise, in whichever shape ``opt`` asks for.

    ``cone``  base + per_producer x |cone|                      (Phase 3)
    ``depth`` base x (1 + cone depth)^2 — the same lazy walk, read as hops
    ``time``  no expansion ceiling at all; ``budget_seconds`` is the stop
    """
    shape = "cone" if opt is None else opt.budget_shape
    if shape == "depth":
        return int(min(knobs.budget_cap,
                       knobs.budget_base * (1 + state["cone_depth"]) ** 2))
    if shape == "time":
        return int(knobs.budget_cap)
    return int(min(knobs.budget_cap,
                   knobs.budget_base
                   + knobs.budget_per_producer * state["cone"]))


# ------------------------------------------- the cross-request cost-solve memo

#: Phase 5 "定点按锥增量并跨请求缓存": one solve serves every request of a pack
#: that shares its policy, knobs and stock.  Opt-in (``PlanOptions.share_memo``)
#: because the memo holds dense arrays over every item of the pack.
_SOLVE_MEMO: dict = {}
SOLVE_MEMO_STATS = {"hits": 0, "misses": 0, "stored": 0}


def memo_key(ix: PackIndex, pol: Policy, knobs: Knobs, stock: dict,
             opt=None) -> tuple:
    """``(pack, policy, knobs, feedback, stock)`` — DESIGN.md Phase 3 §3."""
    view = _fb(opt)
    return (ix.slug, int(ix.n_recipes), int(ix.n_items),
            pol.signature(), knobs.signature(),
            "" if view is None else view.signature(),
            stable_hash(sorted((str(k), float(v))
                               for k, v in (stock or {}).items()), 16))


def solve_cached(ix: PackIndex, pol: Policy, knobs: Knobs,
                 stock: dict = None, opt=None) -> CostSolve:
    """``solve_cost``, memoised across requests when ``share_memo`` is on."""
    if opt is None or not opt.share_memo:
        SOLVE_MEMO_STATS["misses"] += 1
        return solve_cost(ix, pol, knobs, stock, opt)
    key = memo_key(ix, pol, knobs, stock, opt)
    hit = _SOLVE_MEMO.get(key)
    if hit is not None:
        SOLVE_MEMO_STATS["hits"] += 1
        return hit
    SOLVE_MEMO_STATS["misses"] += 1
    sv = solve_cost(ix, pol, knobs, stock, opt)
    sv.key = key
    _SOLVE_MEMO[key] = sv
    SOLVE_MEMO_STATS["stored"] = len(_SOLVE_MEMO)
    return sv


#: Per-request search counters, overwritten by every ``plan_target`` call.
#: Read by ``bench_planner.py``; never serialised into a plan.
PLAN_STATS = {"expansions": 0, "budget": 0, "cone": -1, "cone_depth": 0,
              "actions": 0}


def clear_solve_memo() -> None:
    _SOLVE_MEMO.clear()
    SOLVE_MEMO_STATS.update({"hits": 0, "misses": 0, "stored": 0})


def plan_target(ix: PackIndex, pol: Policy, knobs: Knobs, target: str,
                amount: int = 1, stock: dict = None,
                sv: CostSolve = None, units: dict = None, opt=None) -> Plan:
    """Solve one target.  Material layer, then form layer, then extraction."""
    t0 = time.perf_counter()
    pl = Plan()
    pl.pack, pl.slug = ix.pack, ix.slug
    pl.target, pl.amount = target, int(amount)
    view = _fb(opt)
    if view is not None and view.stock:
        # The feedback layer's ``have`` is standing stock; the request's own
        # stock wins where the two name the same item.
        stock = {**view.stock, **(stock or {})}
    pl.stock = dict(stock or {})
    pl.opt = opt
    pl.knobs = knobs.as_dict()
    root = ix.item_of(target)
    if sv is None:
        sv = solve_cached(ix, pol, knobs, stock, opt)
    if units is None:
        units = pool_units(ix)
    if root < 0:
        pl.status = "unknown-target"
        pl.seconds = time.perf_counter() - t0
        return pl

    # The budget is raised from the cone only when the base allowance runs
    # out: counting the cone of a two-action target costs more than solving it
    # (measured: 8 ms of a 8.1 ms target on lost-world).
    ledger = Ledger(pl.stock, ix)
    shape = "cone" if opt is None else opt.budget_shape
    state = {"n": 0, "budget": knobs.budget_base, "expansions": 0,
             "cone": -1, "cone_depth": 0}
    used_machines: set = set()
    plan_edges: set = set()
    expanding: list = []
    produced_by: dict = {}          # base_id -> action id that last made it

    def emit_unresolved(base, qty, reason, cands=0):
        pl.unresolved.append({"base_id": base, "amount": round(qty, 6),
                              "reason": reason, "candidates": cands})
        pl.status = "incomplete"

    def demand(item: int, qty: float, depth: int):
        base = str(ix.items[item])
        took = ledger.take(item, qty)
        sources = []
        if took > 0:
            sources.append(ledger.origin.get(item, "stock"))
            qty -= took
        if qty <= 1e-9:
            return sources, took, 0.0
        # Raw: no producer at all, or none the *cost solve* could ever reach
        # from leaves (a closed ring, or an item whose only producer consumes
        # it).  Nothing in the pack makes it, so gathering it is the truth.
        if (bool(ix.item_leaf[item])
                or ix.prod_ptr[item] == ix.prod_ptr[item + 1]
                or sv.gathered[item]
                or sv.cost[item] == float("inf")):
            pl.raw[base] = pl.raw.get(base, 0.0) + qty
            return sources + ["raw"], took, qty
        if base in expanding:
            emit_unresolved(base, qty, "SELF_DEPENDENT")
            return sources + ["unresolved"], took, 0.0
        if state["expansions"] >= state["budget"]:
            if state["cone"] < 0:
                state["cone"], state["cone_depth"] = cone_stats(
                    ix, pol, root, knobs.budget_cap)
                state["budget"] = _raised_budget(knobs, opt, state)
            if shape == "time":
                if time.perf_counter() - t0 > opt.budget_seconds:
                    emit_unresolved(base, qty, "BUDGET_TRUNCATED")
                    return sources + ["unresolved"], took, 0.0
            elif state["expansions"] >= state["budget"]:
                emit_unresolved(base, qty, "BUDGET_TRUNCATED")
                return sources + ["unresolved"], took, 0.0
        state["expansions"] += 1

        banned = set()
        for r0 in sorted(set(int(x) for x in ix.producers_of(item))):
            eaten = _consumed_amount(ix, sv, ledger, r0, item, opt)
            if eaten > 0 and ix.out_amount(r0, item) <= eaten + 1e-9:
                banned.add(r0)
        ch = choose_producer(ix, sv, pol, knobs, item, ledger, used_machines,
                             banned, plan_edges, opt)
        if ch.recipe < 0:
            emit_unresolved(base, qty, "NO_PRODUCER",
                            int(ix.prod_ptr[item + 1] - ix.prod_ptr[item]))
            return sources + ["unresolved"], took, 0.0
        r = ch.recipe
        per = ix.out_amount(r, item)
        times = np.int64(math.ceil(qty / per - 1e-9))
        if times < 1:
            times = np.int64(1)

        expanding.append(base)
        # Resolve every slot first, then demand each distinct item **once**:
        # four glass slots are one demand for four glass, not four demands for
        # one.  The printed rows stay per slot, like the baseline's.
        slots, folded = [], {}
        for k in range(ix.in_ptr[r], ix.in_ptr[r + 1]):
            node, amt = int(ix.in_node[k]), float(ix.in_amt[k]) * float(times)
            pick = _resolve_or(ix, sv, node, ledger, opt)
            slots.append((pick, amt, node))
            if pick >= 0:
                folded[pick] = folded.get(pick, 0.0) + amt
        origins: dict = {}
        for pick in sorted(folded, key=lambda p: (sv.cost[p], str(ix.items[p]))):
            src, _, _ = demand(pick, folded[pick], depth + 1)
            origins[pick] = sorted(set(src)) or ["raw"]
        in_rows = []
        for pick, amt, node in slots:
            if pick < 0:
                emit_unresolved(f"or:{node - ix.n_items}", amt,
                                "UNRESOLVED_INPUT")
                in_rows.append({"base_id": "", "amount": round(amt, 6),
                                "from": ["unresolved"]})
                continue
            in_rows.append({"base_id": str(ix.items[pick]),
                            "amount": round(amt, 6),
                            "from": origins.get(pick, ["raw"])})
        expanding.pop()

        state["n"] += 1
        aid = f"A{state['n']}"
        if pol.role[r] == ROLE_CONVERSION:
            for pick, _, _ in slots:
                if pick < 0:
                    continue
                for k in range(ix.out_ptr[r], ix.out_ptr[r + 1]):
                    oi = int(ix.out_item[k])
                    if oi != pick:
                        plan_edges.add((pick, oi))
        for k in range(ix.mach_ptr[r], ix.mach_ptr[r + 1]):
            if int(ix.mach_item[k]) >= 0:
                used_machines.add(int(ix.mach_item[k]))
        out_rows = []
        for k in range(ix.out_ptr[r], ix.out_ptr[r + 1]):
            oi = int(ix.out_item[k])
            got = float(ix.out_amt[k]) * float(ix.out_chance[k]) * float(times)
            out_rows.append({"base_id": str(ix.items[oi]),
                             "amount": round(got, 6)})
            if oi == item:
                surplus = got - qty
                if surplus > 1e-9:
                    ledger.give(oi, surplus, aid)
            else:
                ledger.give(oi, got, aid)
            produced_by[oi] = aid
        pl.actions.append(_action_row(ix, pol, knobs, sv, aid, r, times, depth,
                                      in_rows, out_rows, ch, item, opt))
        return sources + [aid], took, 0.0

    demand(root, float(amount), 0)
    pl.seconds = time.perf_counter() - t0
    # Search counters for the Phase 5 benchmark.  Deliberately *not* in the
    # plan JSON: that would change the bytes of a default run.
    PLAN_STATS.update({"expansions": state["expansions"],
                       "budget": state["budget"], "cone": state["cone"],
                       "cone_depth": state["cone_depth"],
                       "actions": state["n"]})
    finalise(ix, pol, knobs, pl, units, sv)
    return pl


def _input_held(ix: PackIndex, node: int, ledger: Ledger, opt=None) -> bool:
    view = _fb(opt)
    ban = None if view is None else view.item_banned
    if node < ix.n_items:
        return (ledger.have.get(node, 0.0) > 0
                and (ban is None or not ban[node]))
    held = ix.or_set[node - ix.n_items] & ledger.have.keys()
    if ban is not None:
        held = {i for i in held if not ban[i]}
    return bool(held)


def _consumed_amount(ix: PackIndex, sv: CostSolve, ledger: Ledger, r: int,
                     item: int, opt=None) -> float:
    """How much of ``item`` recipe ``r`` really consumes, once every any-of
    input has been resolved the way extraction will resolve it.

    Counting bare tag *membership* instead was wrong in both directions: it let
    a same-in-same-out stonecutting recipe through (its input is a tag whose
    only member is the output) and it banned every recolour recipe (whose
    input tag contains the output among a dozen siblings, but resolves to a
    different one).
    """
    total = 0.0
    for k in range(ix.in_ptr[r], ix.in_ptr[r + 1]):
        node = int(ix.in_node[k])
        if _resolve_or(ix, sv, node, ledger, opt) == item:
            total += float(ix.in_amt[k])
    return total


def _resolve_or(ix: PackIndex, sv: CostSolve, node: int, ledger: Ledger,
                opt=None) -> int:
    """An any-of input is served by whatever the ledger holds, else by the
    representative (request-cheapest member, build-time tie-breaks).

    A feedback view's item bans take a member out of the group: the solve
    already priced the node without it, and the fallback representative is
    re-picked among the members that are left.
    """
    if node < ix.n_items:
        return node
    gi = node - ix.n_items
    view = _fb(opt)
    ban = None if view is None else view.item_banned
    held = ix.or_set[gi] & ledger.have.keys()
    if ban is not None:
        held = {i for i in held if not ban[i]}
    if held:
        return min(held, key=lambda i: (sv.cost[i], str(ix.items[i])))
    best = sv.or_best[gi]
    if best >= 0 and (ban is None or not ban[int(best)]):
        return int(best)
    rep = int(ix.or_rep[gi])
    if ban is None or rep < 0 or not ban[rep]:
        return rep
    live = [i for i in ix.or_items[gi] if not ban[i]]
    if not live:
        return -1
    return min(live, key=lambda i: (sv.cost[i], str(ix.items[i])))


class _EmptyLedger:
    """A ledger holding nothing — the price-only path of ``producer_cost``."""

    __slots__ = ("have", "origin")

    def __init__(self):
        self.have, self.origin = {}, {}


_EMPTY_LEDGER = _EmptyLedger()


def _action_row(ix, pol, knobs, sv, aid, r, times, depth, in_rows, out_rows,
                ch, item, opt=None) -> dict:
    score = eff_score(ix, opt)
    _, brk = producer_cost(ix, sv, knobs, pol, r, item, set(), opt)
    runner = {}
    if ch.runner_up is not None and ch.runner_up >= 0:
        runner = {"recipe_idx": int(ch.runner_up),
                  "recipe_id": str(ix.recipe_id[ch.runner_up]),
                  "unit_cost": round(float(ch.runner_cost), 6),
                  "producer_score": round(float(score[ch.runner_up]), 6),
                  "gap": round(float(ch.runner_cost) - float(ch.cost), 6)}
    return {
        "id": aid, "recipe_idx": int(r), "recipe_id": str(ix.recipe_id[r]),
        "template_id": str(ix.template_id[r]), "system": str(ix.system[r]),
        "machines": [str(ix.machine_ids[int(ix.mach_item[k])])
                     for k in range(ix.mach_ptr[r], ix.mach_ptr[r + 1])
                     if int(ix.mach_item[k]) >= 0],
        "times": int(times), "depth": int(depth),
        "for": str(ix.items[item]),
        "inputs": in_rows, "outputs": out_rows,
        "why": {
            "decided_by": ch.reason,
            "cost": brk,
            "runner_up": runner,
            "judgements": {
                "junk_p": round(float(pol.junk_p[r]), 6),
                "role_argmax": str(ix.role_argmax[r]),
                "effective_role": str(pol.role[r]),
                "shape_role": str(ix.shape_role[r]),
                "producer_score": round(float(score[r]), 6),
                "reliability": round(float(ix.reliability[r]), 6),
                "tier": float(ix.tier[r]),
                "reverse_tier": bool(ix.reverse_tier[r]),
            },
            "excluded": [{"recipe_idx": int(x), "reason": why,
                          "junk_p": round(float(pol.junk_p[x]), 6)}
                         for x, why in ch.rejected[:8]],
            "shortlist": [{"recipe_idx": int(x), "unit_cost": round(c, 6),
                           "producer_score": round(float(score[x]), 6)}
                          for x, c in ch.shortlist[:8]],
        }}


# ================================================= guards, profile and metrics


def finalise(ix: PackIndex, pol: Policy, knobs: Knobs, pl: Plan, units: dict,
             sv: CostSolve) -> None:
    """Guards, the explanation profile and the comparable metric block."""
    acts = pl.actions
    root_pool = str(ix.item_pool[ix.item_of(pl.target)]) \
        if ix.item_of(pl.target) >= 0 else ""

    # -- depth: longest dependency path from a raw/stock input --------------
    depth_of: dict = {}
    for a in acts:
        d = 0
        for row in a["inputs"]:
            for src in row["from"]:
                if src in depth_of:
                    d = max(d, depth_of[src] + 1)
        depth_of[a["id"]] = d
        a["chain_depth"] = d

    # -- guard 1: per-pool net gain of the conversion actions --------------
    pool_net: dict = {}
    pools_touched = set()
    for a in acts:
        r = a["recipe_idx"]
        for row in a["outputs"] + a["inputs"]:
            i = ix.item_of(row["base_id"]) if row["base_id"] else -1
            if i >= 0 and str(ix.item_pool[i]):
                pools_touched.add(str(ix.item_pool[i]))
        if pol.role[r] != ROLE_CONVERSION:
            continue
        for sign, rows in ((+1.0, a["outputs"]), (-1.0, a["inputs"])):
            for row in rows:
                i = ix.item_of(row["base_id"]) if row["base_id"] else -1
                if i < 0 or i not in units:
                    continue
                pid = str(ix.item_pool[i])
                pool_net[pid] = pool_net.get(pid, 0.0) + \
                    sign * units[i] * float(row["amount"])
    amp = 0.0
    for pid, net in sorted(pool_net.items()):
        if pid == root_pool:
            continue
        if net > 1e-6:
            amp = max(amp, net)
            pl.guards.append({"guard": "pool_amplification", "pool_id": pid,
                              "net": round(net, 6)})

    # -- guard 2: round trips.  Impossible by construction; asserted. -------
    seen_edges, round_trips = set(), 0
    for a in acts:
        r = a["recipe_idx"]
        if pol.role[r] != ROLE_CONVERSION:
            continue
        ins = {row["base_id"] for row in a["inputs"] if row["base_id"]}
        outs = {row["base_id"] for row in a["outputs"]}
        # Edges of *this* action are checked against earlier actions only: a
        # single recipe that both consumes and produces two members of one
        # family emits (x,y) and (y,x) itself, which is not a round trip.
        edges = {(s, d) for s in ins for d in outs if s != d}
        for s, d in sorted(edges):
            if (d, s) in seen_edges:
                round_trips += 1
                pl.guards.append({"guard": "round_trip", "a": s, "b": d})
        seen_edges |= edges

    # -- guard 3: tier reversals -------------------------------------------
    reversals = sum(1 for a in acts if a["why"]["judgements"]["reverse_tier"])

    # -- profile -----------------------------------------------------------
    buckets = {"low": 0.0, "mid": 0.0, "high": 0.0}
    for base, qty in sorted(pl.raw.items()):
        i = ix.item_of(base)
        acq = float(ix.item_acq[i]) if i >= 0 else 0.5
        key = "low" if acq < 1 / 3 else "mid" if acq < 2 / 3 else "high"
        buckets[key] += qty
    machines = sorted({m for a in acts for m in a["machines"]})
    mods = sorted({str(ix.items[ix.item_of(row["base_id"])]).split(":", 1)[0]
                   for a in acts for row in a["outputs"]
                   if ":" in row["base_id"]})
    pl.profile = {
        "leaves_by_acquirability": {k: round(v, 3) for k, v in buckets.items()},
        "machine_footprint": [{"machine_id": m,
                               "tier": float(ix.machine_tier[
                                   ix.machine_ids.tolist().index(m)])
                               if m in ix.machine_ids else 0.0}
                              for m in machines],
        "mod_footprint": mods,
        "pools_touched": sorted(pools_touched),
        "guards_fired": sorted({g["guard"] for g in pl.guards}),
        "policy": pol.stats,
    }
    wl_junk = sum(1 for a in acts
                  if float(a["why"]["judgements"]["junk_p"]) > knobs.tau_junk)
    disp = sum(1 for a in acts
               if a["why"]["judgements"]["effective_role"] == ROLE_DISPLAY)
    pl.metrics = {
        "actions": len(acts),
        "distinct_recipes": len({a["recipe_idx"] for a in acts}),
        "crafts": int(sum(int(a["times"]) for a in acts)),
        "raw_kinds": len(pl.raw),
        "machines": len(machines),
        "systems": len({a["system"] for a in acts}),
        "mods": len(mods),
        "depth": (max(depth_of.values()) + 1) if depth_of else 0,
        "junk_in_path_model": round(wl_junk / len(acts), 6) if acts else 0.0,
        "display_in_path": round(disp / len(acts), 6) if acts else 0.0,
        "round_trips": round_trips,
        "pool_amplification": round(amp, 6),
        "tier_reversals": reversals,
        "incomplete": int(pl.status != "ok"),
        "unresolved": len(pl.unresolved),
        "wall_ms": round(pl.seconds * 1000.0, 3),
    }
    assert round_trips == 0, (
        f"round trip in {pl.slug}/{pl.target}: {pl.guards[:2]}")


# ==================================================== the `### CHAIN` emitter


def chain_text(pl: Plan, chain_id: str = "0000") -> str:
    """The baseline's own text format, so a human can diff side by side."""
    m = pl.metrics
    cat = _category(m.get("depth", 0), m.get("actions", 0),
                    pl.status != "ok")
    out = [f"### CHAIN {chain_id} | category={cat} | depthBucket={cat} | "
           f"flags={'incomplete' if pl.status != 'ok' else 'complete'}"
           f"+raw{m.get('raw_kinds', 0)}",
           f"target: {pl.target} x{pl.amount} | recipe: "
           f"{pl.actions[-1]['recipe_id'] if pl.actions else '-'} | system: "
           f"{pl.actions[-1]['system'] if pl.actions else '-'} | tier: "
           f"prototype | coneScore: 0",
           f"status: {'success' if pl.status == 'ok' else pl.status} | "
           f"depth={m.get('depth', 0)} actions={m.get('actions', 0)} "
           f"recipes={m.get('distinct_recipes', 0)} crafts={m.get('crafts', 0)} "
           f"raw_kinds={m.get('raw_kinds', 0)} "
           f"pending={m.get('unresolved', 0)} catalysts={len(pl.catalysts)} "
           f"machines={m.get('machines', 0)} systems={m.get('systems', 0)} "
           f"elapsed_ms={int(m.get('wall_ms', 0))}",
           f"search: expanded={m.get('actions', 0)} "
           f"candidates={m.get('distinct_recipes', 0)} "
           f"budgetStop={'budget' if pl.status != 'ok' else 'none'}",
           "rule: cirrus:routing/rule/hierarchical | planner=hierarchical | "
           "loop=conversionFromStockOnly",
           "machines: " + ", ".join(sorted({mm for a in pl.actions
                                            for mm in a["machines"]}))]
    for a in pl.actions:
        pad = " " * (2 * int(a.get("chain_depth", a["depth"])))
        mach = a["machines"][0] if a["machines"] else "-"
        out.append(f"{pad}[{a['id']}] EXECUTE {a['recipe_id'] or '-'} "
                   f"x{a['times']} @ {mach}")
        ipad = pad + " " * 7
        out.append(ipad + "in : " + ", ".join(
            f"{r['base_id']} x{_fmt(r['amount'])}" for r in a["inputs"]) or
            ipad + "in : -")
        out.append(ipad + "out: " + ", ".join(
            f"{r['base_id']} x{_fmt(r['amount'])}" for r in a["outputs"]))
        froms = sorted({s for r in a["inputs"] for s in r["from"]
                        if s.startswith("A")}, key=lambda s: int(s[1:]))
        if froms:
            out.append(ipad + "<= from " + ",".join(froms))
    if pl.raw:
        out.append("[RAW] " + ", ".join(f"{k} x{_fmt(v)}"
                                        for k, v in sorted(pl.raw.items())))
    for u in pl.unresolved:
        out.append(f"[UNRESOLVED] {u['base_id']} x{_fmt(u['amount'])} "
                   f"(candidates={u['candidates']}, reason={u['reason']})")
    for c in pl.catalysts:
        out.append(f"[CATALYST] {c['base_id']} x{_fmt(c['amount'])} "
                   f"for {c['recipe_id']}")
    return "\n".join(out) + "\n"


def _fmt(v) -> str:
    f = float(v)
    return str(int(round(f))) if abs(f - round(f)) < 1e-6 else f"{f:.3f}"


def plan_json(pl: Plan) -> dict:
    """The plan as plain JSON — the schema in DESIGN.md Phase 3 §9.

    The ``options`` block appears only when a non-default ``PlanOptions`` was
    used, so a default run's JSON is byte for byte the Phase 3 one.
    """
    extra = ({"options": pl.opt.as_dict()}
             if pl.opt is not None and not pl.opt.is_default() else {})
    return {"pack": pl.pack, "slug": pl.slug, "target": pl.target,
            "amount": int(pl.amount), "status": pl.status,
            "stock": pl.stock, "knobs": pl.knobs, **extra,
            "seconds": round(pl.seconds, 6), "actions": pl.actions,
            "raw": {k: round(v, 6) for k, v in sorted(pl.raw.items())},
            "unresolved": pl.unresolved, "catalysts": pl.catalysts,
            "profile": pl.profile, "guards": pl.guards, "metrics": pl.metrics}


def _category(depth: int, actions: int, incomplete: bool) -> str:
    if incomplete:
        return "incomplete"
    if actions >= 300:
        return "mega-wide"
    return ("trivial" if depth <= 1 else "short" if depth <= 3 else
            "medium" if depth <= 6 else "long" if depth <= 11 else
            "giant" if depth <= 19 else "colossal")


# ===================================================================== driver


def pack_slugs(cache_dir: Path, spec: str) -> list:
    packs = read_json(cache_dir / "packs.json")
    if spec == "all":
        return sorted(packs)
    return [s.strip() for s in spec.split(",") if s.strip()]


def load_pack(cache_dir: Path, out_dir: Path, slug: str, knobs: Knobs,
              force: bool = False) -> PackIndex:
    packs = read_json(cache_dir / "packs.json")
    ix = build_index(cache_dir, out_dir, slug, packs[slug], knobs)
    meta = score_models(out_dir, ix, knobs, force=force)
    ix.meta.update(meta)
    return ix


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--cache-dir", default=str(HERE / "cache"))
    ap.add_argument("--out-dir", default=str(HERE / "out"))
    ap.add_argument("--packs", default="fom")
    ap.add_argument("--stage", default="index", choices=("index", "solve"))
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--targets", type=int, default=20)
    ap.add_argument("--target", default=None)
    ap.add_argument("--seed", type=int, default=20260919)
    ap.add_argument("--chain", action="store_true",
                    help="print the plan in the baseline's ### CHAIN format")
    ap.add_argument("--gamma", type=float), ap.add_argument("--tau-junk", type=float)
    ap.add_argument("--tau-display", type=float)
    ap.add_argument("--step-cost", type=float)
    ap.add_argument("--machine-cost", type=float)
    ap.add_argument("--tier-penalty", type=float)
    ap.add_argument("--recycling-mult", type=float)
    ap.add_argument("--generation-mult", type=float)
    ap.add_argument("--budget-base", type=int)
    ap.add_argument("--budget-per-producer", type=int)
    ap.add_argument("--budget-cap", type=int)
    ap.add_argument("--no-ranker", action="store_true")
    ap.add_argument("--no-conversion-gate", action="store_true")
    ap.add_argument("--stock", default=None,
                    help="<base_id>=<qty>,... starting inventory")
    ap.add_argument("--dump", default=None,
                    help="write every solved plan as JSON to this directory")
    ap.add_argument("--arm-junk"), ap.add_argument("--arm-role")
    ap.add_argument("--arm-rank")
    args = ap.parse_args(argv)
    knobs = Knobs(arm_junk=args.arm_junk, arm_role=args.arm_role,
                  arm_rank=args.arm_rank, gamma=args.gamma,
                  tau_junk=args.tau_junk, tau_display=args.tau_display,
                  step_cost=args.step_cost, machine_cost=args.machine_cost,
                  tier_penalty=args.tier_penalty,
                  recycling_mult=args.recycling_mult,
                  generation_mult=args.generation_mult,
                  budget_base=args.budget_base,
                  budget_per_producer=args.budget_per_producer,
                  budget_cap=args.budget_cap,
                  use_ranker=False if args.no_ranker else None,
                  conversion_needs_stock=(False if args.no_conversion_gate
                                          else None))
    stock = {}
    for part in (args.stock or "").split(","):
        if "=" in part:
            base, _, qty = part.partition("=")
            stock[base.strip()] = float(qty)
    cache_dir, out_dir = Path(args.cache_dir), Path(args.out_dir)
    for slug in pack_slugs(cache_dir, args.packs):
        t0 = time.perf_counter()
        ix = load_pack(cache_dir, out_dir, slug, knobs, force=args.force)
        pol = Policy(ix, knobs)
        el = time.perf_counter() - t0
        print(f"  {slug:<38} {ix.n_recipes:>7,} recipes  {ix.n_items:>7,} items  "
              f"{ix.n_or:>5,} OR  build {el:>6.2f}s")
        print("      policy: " + ", ".join(f"{k}={v}" for k, v in
                                           pol.stats.items() if k != "role_counts"))
        print("      roles:  " + ", ".join(
            f"{k}={v:,}" for k, v in sorted(pol.stats["role_counts"].items())))
        if args.stage == "index":
            continue
        t1 = time.perf_counter()
        sv = solve_cost(ix, pol, knobs, stock)
        units = pool_units(ix)
        print(f"      solve:  {time.perf_counter() - t1:.3f}s  {sv.stats}")
        if args.target:
            targets = [args.target]
        else:
            rng = np.random.default_rng(args.seed)
            pool = np.flatnonzero(ix.item_nprod > 0)
            pick = rng.choice(pool, size=min(args.targets, pool.size),
                              replace=False)
            targets = sorted(str(ix.items[i]) for i in np.sort(pick))
        times = []
        for tgt in targets:
            pl = plan_target(ix, pol, knobs, tgt, 1, stock, sv, units)
            times.append(pl.metrics.get("wall_ms", 0.0))
            print(f"      {tgt:<52} {pl.status:<10} "
                  f"a={pl.metrics.get('actions', 0):>4} "
                  f"crafts={pl.metrics.get('crafts', 0):>6} "
                  f"raw={pl.metrics.get('raw_kinds', 0):>3} "
                  f"d={pl.metrics.get('depth', 0):>3} "
                  f"{pl.metrics.get('wall_ms', 0):>7.2f} ms")
            if args.chain:
                print(chain_text(pl))
            if args.dump:
                write_json(Path(args.dump) / f"{slug}--"
                           f"{stable_hash(tgt, 10)}.json", plan_json(pl))
        if times:
            print(f"      per-target ms: median {np.median(times):.2f} "
                  f"p95 {np.percentile(times, 95):.2f} max {max(times):.2f}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
