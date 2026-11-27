#!/usr/bin/env python
"""Phase 0b — the economy layer over the Phase 0a cache.

Everything here is induced from structure.  There is no mod / material /
colour / form / system word list anywhere: the only inputs are the export
schema's own fields, the induced identity quadruple of Phase 0a
(``out/<slug>/identity.parquet``) and *pure shape* facts (how many slots a
recipe has, whether its material multiset is preserved, whether the item graph
maps its outputs back onto its inputs).

Pipeline per pack
-----------------
1. **Graph build.**  Every content row is resolved to a *node*: a stack to the
   item it names, a tag / any / typedAny to an OR node over its members
   (de-duplicated, so one tag is one node no matter how often it is used), a
   complex or member-less group to nothing.  Inputs are folded per
   (recipe, node) and outputs per (recipe, item); ``amount`` is multiplied by
   ``chance`` (the *chance hook* — inert on this corpus, where chance is a
   constant 1.0); items that appear on both sides of one recipe are netted
   (the *catalyst hook*).
2. **Material conservation -> shape roles.**  Inputs and outputs are aggregated
   by material key; the round-trip signal comes from the strongly connected
   components of the same-material item graph.  The conversion tolerance band
   for the count ratio is *learned* from the ratio distribution of round-trip
   confirmed, material-preserving recipes (central 90%), never assumed.
3. **Pools.**  SCCs and weak components of the item graph restricted to
   conversion-role recipes.
4. **Tiers.**  A monotone min-max fixpoint: leaves are 0, a recipe is
   ``max(machine tiers, input tiers) + 1``, an item is the min over producers.
   A machine's tier is the tier of the machine item itself, so machines and
   items are solved in one joint fixpoint.
5. **Values.**  Knuth's generalisation of Dijkstra over the hypergraph:
   ``value(item) = min over producers of (sum input value * amount) / amount``,
   OR nodes take the min over members, leaves start at the acquirability seed.
   Solved twice — once with a uniform seed, once with the fitted one.
6. **Leaf acquirability.**  Unsupervised: rank-normalised structural features
   -> first principal component, oriented so that broadly consumed is cheap.
7. **World resource vs data gap.**  A leaf that most *other* packs can produce
   is a data gap; one nobody produces is a world resource.
8. **Reliability.**  Per recipe, a product of learned flag penalties and a
   system shape-atypicality factor.
"""
from __future__ import annotations

import argparse
import heapq
import json
import math
import sys
import time
from collections import defaultdict
from pathlib import Path

import numpy as np
import pyarrow as pa
import pyarrow.compute as pc
import pyarrow.parquet as pq

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import (  # noqa: E402
    path_tokens,
    peak_rss_mb,
    read_json,
    split_id,
    stable_hash,
    write_json,
    write_parquet,
)

# ------------------------------------------------------------- default knobs

#: Central mass of the round-trip ratio distribution that defines "the counts
#: are preserved".  Everything about the band itself is learned; this is the
#: quantile pair, i.e. how much of the confirmed population must fit inside.
BAND_QUANTILE = 0.05          # -> central 90 %

#: Hard floor/ceiling for the learned band, used only when there is not enough
#: round-trip evidence in a pack to estimate one (< MIN_BAND_SAMPLES recipes).
MIN_BAND_SAMPLES = 30
FALLBACK_BAND = (1.0 / 3.0, 3.0)

#: The acquirability -> seed map.  A leaf is by definition hand-gatherable, so
#: the whole leaf population lives in the multiplicative band ``[1, K]``:
#: ``seed = K ** (1 - acquirability)``.  The cheapest leaf is exactly 1.0 and
#: the dearest is K.  K = 1 reproduces the uniform solve.  The unsupervised
#: score only breaks ties between gatherables; it must never outweigh a
#: crafting step, which is what v1 let it do (a 119x spread on fom).
SEED_SPREAD = 2.0

#: Every recipe costs something to run: ``step_cost * (1 + recipe tier)``,
#: with ``step_cost`` a fraction of the pack's median leaf seed.  0.25 means
#: four hand steps are worth about one gathered item, and it is what stops a
#: smelted ore from costing exactly as much as the ore.
STEP_COST = 0.25

#: Price of one unit of a ``value`` content (time / energy).  No pack in this
#: corpus exports any, so this only ever runs in the synthetic test.
VALUE_UNIT_PRICE = 1.0

#: Cap on a single reliability penalty, so no one flag can zero a recipe out.
MAX_PENALTY = 0.5
#: Robust-z scale at which a system counts as fully atypical.
ATYPICAL_SCALE = 3.0

MIXED = "mixed"

RECIPE_SHAPE_SCHEMA = pa.schema([
    ("recipe_idx", pa.int32()),
    ("recipe_id", pa.string()),
    ("system", pa.string()),
    ("shape_role", pa.string()),
    ("material_set_preserved", pa.bool_()),
    ("out_subset_in", pa.bool_()),
    ("count_ratio", pa.float64()),
    ("mat_log_ratio_max", pa.float64()),
    ("is_round_trip", pa.bool_()),
    ("zero_concrete_inputs", pa.bool_()),
    ("inputs_equal_outputs", pa.bool_()),
    ("self_dependent", pa.bool_()),
    ("n_input_slots", pa.int32()),
    ("n_output_slots", pa.int32()),
    ("n_in_materials", pa.int32()),
    ("n_out_materials", pa.int32()),
    ("tier", pa.float64()),
    ("machine_tier", pa.float64()),
    ("max_input_tier", pa.float64()),
    ("reverse_tier", pa.bool_()),
    ("value_in", pa.float64()),
    ("value_out", pa.float64()),
    ("value_ratio", pa.float64()),
    ("unique_producer", pa.bool_()),
    ("reliability", pa.float64()),
])

ITEM_VALUE_SCHEMA = pa.schema([
    ("base_id", pa.string()),
    ("material_key", pa.string()),
    ("form_class", pa.string()),
    ("value_uniform", pa.float64()),
    ("value_fitted", pa.float64()),
    ("tier", pa.float64()),
    ("is_leaf", pa.bool_()),
    ("pool_id", pa.string()),
    ("weak_pool_id", pa.string()),
    ("acquirability", pa.float64()),
    ("data_gap", pa.bool_()),
    ("producer_packs", pa.int32()),
    ("n_producers", pa.int32()),
    ("n_consumers", pa.int32()),
    ("value_source", pa.string()),
])

POOL_SCHEMA = pa.schema([
    ("pool_id", pa.string()),
    ("pool_kind", pa.string()),
    ("size", pa.int32()),
    ("members", pa.list_(pa.string())),
    ("material_keys", pa.list_(pa.string())),
    ("representative", pa.string()),
    ("n_edges", pa.int32()),
    ("n_recipes", pa.int32()),
])

POOL_EDGE_SCHEMA = pa.schema([
    ("pool_id", pa.string()),
    ("src", pa.string()),
    ("dst", pa.string()),
    ("unit_cost", pa.float64()),
    ("recipe_idx", pa.int32()),
])

MACHINE_SCHEMA = pa.schema([
    ("machine_id", pa.string()),
    ("tier", pa.float64()),
    ("n_recipes", pa.int32()),
    ("n_producers", pa.int32()),
    ("machine_unbuildable", pa.bool_()),
    ("in_content_universe", pa.bool_()),
])

LEAF_SCHEMA = pa.schema([
    ("base_id", pa.string()),
    ("material_key", pa.string()),
    ("form_class", pa.string()),
    ("n_consumers", pa.float64()),
    ("n_consumer_ns", pa.float64()),
    ("n_consumer_systems", pa.float64()),
    ("n_tags", pa.float64()),
    ("n_forms_of_material", pa.float64()),
    ("min_consumer_tier", pa.float64()),
    ("renewable", pa.float64()),
    ("leaf_pack_fraction", pa.float64()),
    ("producer_packs", pa.float64()),
    ("n_variant_keys", pa.float64()),
    ("containing_packs", pa.int32()),
    ("acquirability", pa.float64()),
    ("seed_value", pa.float64()),
    ("data_gap", pa.bool_()),
    ("world_resource", pa.bool_()),
])

SYSTEM_SCHEMA = pa.schema([
    ("system", pa.string()),
    ("n_recipes", pa.int32()),
    ("zero_input_share", pa.float64()),
    ("noop_share", pa.float64()),
    ("mean_inputs", pa.float64()),
    ("mean_outputs", pa.float64()),
    ("cross_system_output_share", pa.float64()),
    ("empty_id_share", pa.float64()),
    ("no_machine_share", pa.float64()),
    ("atypicality", pa.float64()),
    ("system_factor", pa.float64()),
    ("mean_reliability", pa.float64()),
])

LEAF_STATS_SCHEMA = pa.schema([
    ("base_id", pa.string()),
    ("containing_packs", pa.int32()),
    ("producer_packs", pa.int32()),
    ("leaf_packs", pa.int32()),
    ("leaf_fraction", pa.float64()),
    ("renewable", pa.bool_()),
    ("every_pack_leaf", pa.bool_()),
])


# ============================================================ numpy utilities


def fold_sums(keys: np.ndarray, values: np.ndarray):
    """Sum ``values`` per distinct key.  Returns ``(keys, sums)``, key-sorted."""
    if keys.size == 0:
        return keys.astype(np.int64), np.zeros(0, dtype=np.float64)
    order = np.argsort(keys, kind="stable")
    k = keys[order]
    v = np.asarray(values, dtype=np.float64)[order]
    new = np.empty(k.size, dtype=bool)
    new[0] = True
    np.not_equal(k[1:], k[:-1], out=new[1:])
    starts = np.flatnonzero(new)
    ends = np.append(starts[1:], k.size)
    csum = np.concatenate(([0.0], np.cumsum(v)))
    return k[starts], csum[ends] - csum[starts]


def segment_hash(codes: np.ndarray, starts: np.ndarray, ends: np.ndarray,
                 salt: int) -> np.ndarray:
    """Order-independent 64-bit hash of each ``codes[start:end]`` segment."""
    with np.errstate(over="ignore"):
        scr = (codes.astype(np.uint64) + np.uint64(salt)) * np.uint64(0x9E3779B97F4A7C15)
        scr ^= scr >> np.uint64(29)
        csum = np.concatenate((np.zeros(1, dtype=np.uint64),
                               np.cumsum(scr, dtype=np.uint64)))
        return csum[ends] - csum[starts]


def build_csr(parent: np.ndarray, n_parents: int):
    """Group row indices by parent.  Returns ``(ptr, idx)``."""
    counts = np.bincount(parent, minlength=n_parents) if parent.size else \
        np.zeros(n_parents, dtype=np.int64)
    ptr = np.concatenate(([0], np.cumsum(counts))).astype(np.int64)
    idx = np.argsort(parent, kind="stable").astype(np.int64)
    return ptr, idx


def rank_norm(values: np.ndarray) -> np.ndarray:
    """Rank-normalise to [0, 1]; ties share the mid-rank.  Constant -> 0.5."""
    n = values.size
    if n == 0:
        return values.astype(np.float64)
    order = np.argsort(values, kind="stable")
    ranks = np.empty(n, dtype=np.float64)
    sorted_values = values[order]
    i = 0
    while i < n:
        j = i
        while j + 1 < n and sorted_values[j + 1] == sorted_values[i]:
            j += 1
        ranks[order[i:j + 1]] = 0.5 * (i + j)
        i = j + 1
    if n == 1:
        return np.full(1, 0.5)
    return ranks / (n - 1.0)


def strong_components(n: int, ptr: np.ndarray, idx: np.ndarray) -> np.ndarray:
    """Iterative Tarjan over a CSR adjacency.  Returns a component id per node."""
    index = np.zeros(n, dtype=np.int64)
    low = np.zeros(n, dtype=np.int64)
    comp = np.full(n, -1, dtype=np.int64)
    seen = np.zeros(n, dtype=bool)
    on_stack = np.zeros(n, dtype=bool)
    stack: list = []
    counter = 0
    n_comp = 0
    for root in range(n):
        if seen[root]:
            continue
        work = [(root, int(ptr[root]))]
        seen[root] = True
        counter += 1
        index[root] = low[root] = counter
        stack.append(root)
        on_stack[root] = True
        while work:
            node, cursor = work[-1]
            advanced = False
            end = int(ptr[node + 1])
            while cursor < end:
                nxt = int(idx[cursor])
                cursor += 1
                if not seen[nxt]:
                    work[-1] = (node, cursor)
                    seen[nxt] = True
                    counter += 1
                    index[nxt] = low[nxt] = counter
                    stack.append(nxt)
                    on_stack[nxt] = True
                    work.append((nxt, int(ptr[nxt])))
                    advanced = True
                    break
                if on_stack[nxt] and index[nxt] < low[node]:
                    low[node] = index[nxt]
            else:
                work[-1] = (node, cursor)
            if advanced:
                continue
            if low[node] == index[node]:
                while True:
                    top = stack.pop()
                    on_stack[top] = False
                    comp[top] = n_comp
                    if top == node:
                        break
                n_comp += 1
            work.pop()
            if work:
                parent = work[-1][0]
                if low[node] < low[parent]:
                    low[parent] = low[node]
    return comp


def weak_components(n: int, src: np.ndarray, dst: np.ndarray) -> np.ndarray:
    """Union-find over undirected edges.  Returns a component id per node."""
    parent = np.arange(n, dtype=np.int64)

    def find(x: int) -> int:
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = int(parent[x])
        return x

    for a, b in zip(src.tolist(), dst.tolist()):
        ra, rb = find(a), find(b)
        if ra != rb:
            if ra < rb:
                parent[rb] = ra
            else:
                parent[ra] = rb
    return np.array([find(i) for i in range(n)], dtype=np.int64)


# ============================================================== graph building

OR_KINDS = ("tag", "any", "typedAny")
OR_SALT = 0x5EED


def effective_amounts(amount: np.ndarray, chance: np.ndarray) -> np.ndarray:
    """*Chance hook*: the amount actually moved by a content entry.

    ``chance`` is a constant 1.0 everywhere in this corpus, so this is the
    identity in practice; it exists so a fixed exporter changes one function.
    """
    return np.asarray(amount, dtype=np.float64) * np.asarray(chance, dtype=np.float64)


def parse_value_scalars(raw: np.ndarray) -> np.ndarray:
    """*Value hook*: the numeric magnitude of a ``value`` content entry.

    No pack in this corpus exports one (0 rows of kind ``value``); the synthetic
    test in ``tests/test_phase0b.py`` is the only caller that sees data.
    """
    out = np.zeros(raw.shape[0], dtype=np.float64)
    for i, text in enumerate(raw.tolist()):
        if text is None:
            continue
        try:
            out[i] = float(text)
        except (TypeError, ValueError):
            out[i] = 1.0
    return out


def net_catalysts(rec_a: np.ndarray, node_a: np.ndarray, amt_a: np.ndarray,
                  rec_b: np.ndarray, node_b: np.ndarray, amt_b: np.ndarray):
    """*Catalyst hook*: remove the part of a stack that a recipe hands back.

    An entry that appears on both sides of the same recipe is only *borrowed*:
    ``min(in, out)`` of it is netted off both sides.  A chance-0 output is the
    canonical case; this corpus has none (chance is a constant 1.0), so the
    hook is inert on real data and is exercised only by the synthetic tests.
    Returns the two sides with the shared part removed.
    """
    key_a = rec_a.astype(np.int64) * (1 << 32) + node_a
    key_b = rec_b.astype(np.int64) * (1 << 32) + node_b
    pos = np.searchsorted(key_a, key_b)
    pos = np.clip(pos, 0, key_a.size - 1) if key_a.size else pos
    if key_a.size == 0 or key_b.size == 0:
        return amt_a, amt_b
    hit = key_a[pos] == key_b
    shared = np.minimum(amt_a[pos[hit]], amt_b[hit])
    out_a = amt_a.copy()
    out_b = amt_b.copy()
    np.subtract.at(out_a, pos[hit], shared)
    out_b[hit] -= shared
    return out_a, out_b


class PackGraph:
    """The resolved hypergraph of one pack.  Nodes are items then OR nodes."""

    __slots__ = ("slug", "n_recipes", "kept", "keep_idx", "system_c", "system_v",
                 "source_c", "source_v", "recipe_id",
                 "machines_ptr", "machines_item", "flags_ptr", "flags_c", "flags_v",
                 "n_inputs", "n_outputs",
                 "items", "n_items", "n_or", "or_ptr", "or_mem",
                 "in_rec", "in_node", "in_amt",
                 "out_rec", "out_item", "out_amt",
                 "n_unres_in", "n_unres_out", "value_cost", "stats")

    @property
    def n_nodes(self) -> int:
        return self.n_items + self.n_or

    def item_of(self, base_id: str) -> int:
        pos = int(np.searchsorted(self.items, base_id))
        if pos < self.items.size and self.items[pos] == base_id:
            return pos
        return -1


def _dict_codes(arr):
    """``(codes, distinct values)`` for a string column.

    Every string comparison in this module goes through arrow's dictionary
    encoder: numpy object arrays compare at Python speed, which on the 1.58 M
    content rows of the largest pack costs ~15 s per pass.
    """
    if isinstance(arr, pa.ChunkedArray):
        arr = arr.combine_chunks()
    enc = arr.dictionary_encode()
    if isinstance(enc, pa.ChunkedArray):
        enc = enc.combine_chunks()
    codes = enc.indices.to_numpy(zero_copy_only=False)
    codes = np.where(np.equal(codes, None), -1, codes).astype(np.int64) \
        if codes.dtype == object else codes.astype(np.int64)
    return codes, enc.dictionary.to_numpy(zero_copy_only=False)


def _mask_of(codes: np.ndarray, values: np.ndarray, wanted) -> np.ndarray:
    """Row mask for "this string column is one of ``wanted``"."""
    if values.size == 0:
        return np.zeros(codes.size, dtype=bool)
    hit = np.isin(values, np.asarray(wanted, dtype=object))
    return np.where(codes >= 0, hit[np.clip(codes, 0, values.size - 1)], False)


def _remap(codes: np.ndarray, values: np.ndarray, items: np.ndarray) -> np.ndarray:
    """Translate local dictionary codes into global item indices (-1 = absent)."""
    if values.size == 0:
        return np.full(codes.size, -1, dtype=np.int64)
    pos = np.searchsorted(items, values)
    pos = np.clip(pos, 0, max(items.size - 1, 0))
    ok = (items.size > 0) & (items[pos] == values)
    table = np.where(ok, pos, -1).astype(np.int64)
    return np.where(codes >= 0, table[np.clip(codes, 0, values.size - 1)], -1)


def build_graph(cache_dir: Path, out_dir: Path, slug: str,
                max_or_expand: int = 256) -> PackGraph:
    """Resolve one pack's recipes into the hypergraph every later stage walks."""
    cdir = cache_dir / slug
    rec = pq.read_table(cdir / "recipes.parquet", columns=[
        "recipe_idx", "system", "source", "recipe_id", "machines",
        "n_inputs", "n_outputs", "flags", "dropped"])
    g = PackGraph()
    g.slug = slug
    g.n_recipes = rec.num_rows
    g.kept = ~rec.column("dropped").to_numpy(zero_copy_only=False).astype(bool)
    g.keep_idx = np.flatnonzero(g.kept)
    g.system_c, g.system_v = _dict_codes(rec.column("system"))
    g.source_c, g.source_v = _dict_codes(rec.column("source"))
    g.recipe_id = rec.column("recipe_id").combine_chunks()
    g.n_inputs = rec.column("n_inputs").to_numpy().astype(np.int64)
    g.n_outputs = rec.column("n_outputs").to_numpy().astype(np.int64)

    con = pq.read_table(cdir / "contents.parquet", columns=[
        "recipe_idx", "side", "kind", "base_id", "members", "n_members",
        "amount", "chance", "value"])
    ridx_all = con.column("recipe_idx").to_numpy().astype(np.int64)
    if not g.kept.all():
        con = con.filter(pa.array(g.kept[ridx_all]))

    ridx = con.column("recipe_idx").to_numpy().astype(np.int64)
    kind_c, kind_v = _dict_codes(con.column("kind"))
    side_c, side_v = _dict_codes(con.column("side"))
    base_c, base_v = _dict_codes(con.column("base_id"))
    n_mem = con.column("n_members").to_numpy().astype(np.int64)
    amount = pc.fill_null(con.column("amount"), 1).to_numpy().astype(np.float64)
    chance = con.column("chance").to_numpy().astype(np.float64)
    amt = effective_amounts(amount, chance)

    is_or = _mask_of(kind_c, kind_v, OR_KINDS) & (n_mem > 0)
    # Outputs are `stack` in every pack of this corpus; an OR output would fall
    # through to the unresolved-output counter, never silently multiply items.
    is_plain = _mask_of(kind_c, kind_v, ("stack",))
    is_value = _mask_of(kind_c, kind_v, ("value",))
    is_out = _mask_of(side_c, side_v, ("out",))

    # ---- item universe: concrete ids + every OR member + every machine id
    mem_col = con.column("members").combine_chunks()
    mem_offs = mem_col.offsets.to_numpy().astype(np.int64)
    mem_c, mem_v = _dict_codes(mem_col.flatten())
    mach_col = rec.column("machines").combine_chunks()
    mach_c, mach_v = _dict_codes(mach_col.flatten())
    keep_base = base_v[np.unique(base_c[is_plain])] if is_plain.any() \
        else np.zeros(0, dtype=object)
    pieces = [np.asarray(p, dtype=object) for p in (keep_base, mem_v, mach_v)
              if len(p)]
    g.items = np.unique(np.concatenate(pieces)) if pieces else \
        np.zeros(0, dtype=object)
    g.n_items = int(g.items.size)
    mem_codes = _remap(mem_c, mem_v, g.items)

    # ---- OR nodes: one node per distinct member *set*, order-independent
    or_rows = np.flatnonzero(is_or)
    or_of_row = np.full(ridx.size, -1, dtype=np.int64)
    if or_rows.size:
        row_hash = segment_hash(mem_codes, mem_offs[:-1], mem_offs[1:], OR_SALT)
        uniq, first, inv = np.unique(row_hash[or_rows], return_index=True,
                                     return_inverse=True)
        rep = or_rows[first]
        counts = (mem_offs[rep + 1] - mem_offs[rep]).astype(np.int64)
        g.or_ptr = np.concatenate(([0], np.cumsum(counts))).astype(np.int64)
        parts = [np.sort(mem_codes[mem_offs[r]:mem_offs[r + 1]]) for r in rep]
        g.or_mem = np.concatenate(parts) if parts else np.zeros(0, dtype=np.int64)
        g.n_or = int(uniq.size)
        or_of_row[or_rows] = inv.reshape(-1)
    else:
        g.or_ptr = np.zeros(1, dtype=np.int64)
        g.or_mem = np.zeros(0, dtype=np.int64)
        g.n_or = 0

    node = np.full(ridx.size, -1, dtype=np.int64)
    item_of_row = _remap(base_c, base_v, g.items)
    node[is_plain] = item_of_row[is_plain]
    node[is_or] = g.n_items + or_of_row[is_or]

    # ---- fold per (recipe, node) on the way in, (recipe, item) on the way out
    resolved = node >= 0
    in_sel = resolved & ~is_out
    out_sel = resolved & is_out & is_plain
    g.in_rec, g.in_node, g.in_amt = _fold_pairs(ridx[in_sel], node[in_sel],
                                                amt[in_sel])
    g.out_rec, g.out_item, g.out_amt = _fold_pairs(ridx[out_sel], node[out_sel],
                                                   amt[out_sel])

    unres = ~resolved & ~is_value
    g.n_unres_in = np.bincount(ridx[unres & ~is_out], minlength=g.n_recipes)
    g.n_unres_out = np.bincount(ridx[unres & is_out], minlength=g.n_recipes)

    # ---- value hook: a per-recipe scalar cost, zero on every real pack
    g.value_cost = np.zeros(g.n_recipes, dtype=np.float64)
    if is_value.any():
        vsel = is_value & ~is_out
        raw = con.column("value").combine_chunks().to_numpy(zero_copy_only=False)
        g.value_cost = np.zeros(g.n_recipes, dtype=np.float64)
        np.add.at(g.value_cost, ridx[vsel],
                  parse_value_scalars(raw[vsel]) * amt[vsel] * VALUE_UNIT_PRICE)

    g.machines_ptr = mach_col.offsets.to_numpy().astype(np.int64)
    g.machines_item = _remap(mach_c, mach_v, g.items)
    flag_col = rec.column("flags").combine_chunks()
    g.flags_ptr = flag_col.offsets.to_numpy().astype(np.int64)
    g.flags_c, g.flags_v = _dict_codes(flag_col.flatten())

    g.stats = {
        "recipes": int(g.n_recipes),
        "kept": int(g.kept.sum()),
        "content_rows": int(ridx.size),
        "items": g.n_items,
        "or_nodes": g.n_or,
        "or_member_slots": int(g.or_mem.size),
        "or_rows": int(is_or.sum()),
        "or_oversize": int((is_or & (n_mem > max_or_expand)).sum()),
        "or_output_rows": int((is_or & is_out).sum()),
        "unresolved_rows": int(unres.sum()),
        "value_rows": int(is_value.sum()),
        "in_pairs": int(g.in_rec.size),
        "out_pairs": int(g.out_rec.size),
    }
    return g


def _fold_pairs(rec: np.ndarray, node: np.ndarray, amt: np.ndarray):
    """Fold amounts per ``(recipe, node)``; returns three key-sorted arrays."""
    if rec.size == 0:
        z64 = np.zeros(0, dtype=np.int64)
        return z64, z64, np.zeros(0, dtype=np.float64)
    key = rec * (1 << 32) + node
    keys, sums = fold_sums(key, amt)
    return (keys >> 32).astype(np.int64), (keys & 0xFFFFFFFF).astype(np.int64), sums


# ================================================ stage 1 — material conservation


def material_keys(out_dir: Path, g: PackGraph):
    """Material key per item, then per node.  Returns ``(names, item_mat, node_mat)``.

    The key is the induced ``material_class`` of Phase 0a.  An id whose path is
    a single token has no material slot at all (its one token *is* its
    identity), so the form slot stands in; an id that never reached the
    ontology falls back to the same positional rule applied to its raw path
    tokens, which is exactly how Phase 0a mints ``t:<token>``.
    """
    ident = pq.read_table(out_dir / g.slug / "identity.parquet",
                          columns=["base_id", "material_class", "form_class"])
    ident = ident.sort_by("base_id")
    ids = ident.column("base_id").to_numpy(zero_copy_only=False)
    mat = ident.column("material_class").to_numpy(zero_copy_only=False)
    form = ident.column("form_class").to_numpy(zero_copy_only=False)
    key = np.where(np.equal(mat, None), form, mat)

    pos = np.searchsorted(ids, g.items)
    pos_c = np.clip(pos, 0, max(ids.size - 1, 0))
    known = (ids.size > 0) & (ids[pos_c] == g.items)
    out = np.empty(g.n_items, dtype=object)
    out[known] = key[pos_c[known]]
    for i in np.flatnonzero(~known | np.equal(out, None)):
        toks = path_tokens(split_id(str(g.items[i]))[1])
        out[i] = f"t:{toks[-2]}" if len(toks) >= 2 else (
            f"t:{toks[-1]}" if toks else f"id:{g.items[i]}")
    names, item_mat = np.unique(out, return_inverse=True)
    item_mat = item_mat.reshape(-1).astype(np.int64)

    mixed = int(np.searchsorted(names, MIXED))
    if mixed >= names.size or names[mixed] != MIXED:
        names = np.insert(names, mixed, MIXED)
        item_mat = np.where(item_mat >= mixed, item_mat + 1, item_mat)
    node_mat = np.empty(g.n_nodes, dtype=np.int64)
    node_mat[:g.n_items] = item_mat
    if g.n_or:
        mem_mat = item_mat[g.or_mem]
        lo = np.minimum.reduceat(mem_mat, g.or_ptr[:-1])
        hi = np.maximum.reduceat(mem_mat, g.or_ptr[:-1])
        node_mat[g.n_items:] = np.where(lo == hi, lo, mixed)
    return names, item_mat, node_mat


def expand_or(rec: np.ndarray, node: np.ndarray, g: PackGraph, cap: int):
    """OR nodes -> their members.  Returns ``(rec, item, n_capped)``."""
    is_or = node >= g.n_items
    plain_rec, plain_item = rec[~is_or], node[~is_or]
    if not is_or.any():
        return plain_rec, plain_item, 0
    ors = node[is_or] - g.n_items
    sizes = g.or_ptr[ors + 1] - g.or_ptr[ors]
    small = sizes <= cap
    ors, keep_rec = ors[small], rec[is_or][small]
    if ors.size == 0:
        return plain_rec, plain_item, int((~small).sum())
    sizes = g.or_ptr[ors + 1] - g.or_ptr[ors]
    starts = np.repeat(g.or_ptr[ors], sizes)
    within = np.arange(sizes.sum()) - np.repeat(np.concatenate(
        ([0], np.cumsum(sizes)[:-1])), sizes)
    exp_item = g.or_mem[starts + within]
    exp_rec = np.repeat(keep_rec, sizes)
    return (np.concatenate([plain_rec, exp_rec]),
            np.concatenate([plain_item, exp_item]),
            int((~small).sum()))


def _per_recipe_counts(rec: np.ndarray, n_recipes: int) -> np.ndarray:
    return np.bincount(rec, minlength=n_recipes).astype(np.int64)


def _sequences_equal(rec_a, node_a, amt_a, rec_b, node_b, amt_b, n_recipes):
    """True per recipe when both key-sorted (node, amount) runs are identical."""
    cnt_a = _per_recipe_counts(rec_a, n_recipes)
    cnt_b = _per_recipe_counts(rec_b, n_recipes)
    same_len = (cnt_a == cnt_b) & (cnt_a > 0)
    sel_a = same_len[rec_a] if rec_a.size else np.zeros(0, dtype=bool)
    sel_b = same_len[rec_b] if rec_b.size else np.zeros(0, dtype=bool)
    ra, na, aa = rec_a[sel_a], node_a[sel_a], amt_a[sel_a]
    nb, ab = node_b[sel_b], amt_b[sel_b]
    out = np.zeros(n_recipes, dtype=bool)
    if ra.size == 0:
        return out
    bad = (na != nb) | (np.abs(aa - ab) > 1e-9)
    out[same_len] = True
    hit = np.unique(ra[bad])
    out[hit] = False
    return out


def round_trip_flags(g: PackGraph, item_mat: np.ndarray, node_mat: np.ndarray,
                     in_item_rec, in_item_id, cap: int):
    """SCCs of the same-material item graph -> a round-trip flag per recipe.

    Instead of the O(in x out) product of same-material pairs, each
    ``(recipe, material)`` that occurs on both sides becomes a hub node
    (``item -> hub -> item``).  Reachability between item nodes, and therefore
    their strong components, are exactly the same, and the graph stays linear.
    """
    in_mat = item_mat[in_item_id]
    out_mat = item_mat[g.out_item]
    key_in = in_item_rec * (1 << 32) + in_mat
    key_out = g.out_rec * (1 << 32) + out_mat
    hubs = np.intersect1d(np.unique(key_in), np.unique(key_out))
    comp = np.zeros(g.n_items, dtype=np.int64)
    if hubs.size:
        hi = np.searchsorted(hubs, key_in)
        ok_i = (hi < hubs.size) & (hubs[np.clip(hi, 0, hubs.size - 1)] == key_in)
        ho = np.searchsorted(hubs, key_out)
        ok_o = (ho < hubs.size) & (hubs[np.clip(ho, 0, hubs.size - 1)] == key_out)
        n_nodes = g.n_items + hubs.size
        src = np.concatenate([in_item_id[ok_i], g.n_items + ho[ok_o]])
        dst = np.concatenate([g.n_items + hi[ok_i], g.out_item[ok_o]])
        ptr, order = build_csr(src, n_nodes)
        comp_all = strong_components(n_nodes, ptr, dst[order])
        comp = comp_all[:g.n_items]

    # An input that the recipe also outputs would pair with itself; drop it, so
    # a surviving shared component always means two *different* items.
    key_ri = in_item_rec * (1 << 32) + in_item_id
    key_ro = np.unique(g.out_rec * (1 << 32) + g.out_item)
    self_pos = np.searchsorted(key_ro, key_ri)
    is_self = (key_ro.size > 0) & (key_ro[np.clip(self_pos, 0, max(key_ro.size - 1, 0))]
                                   == key_ri)
    self_dependent = np.zeros(g.n_recipes, dtype=bool)
    self_dependent[in_item_rec[is_self]] = True

    left = np.unique(in_item_rec[~is_self] * (1 << 32) + comp[in_item_id[~is_self]])
    right = np.unique(g.out_rec * (1 << 32) + comp[g.out_item])
    shared = np.intersect1d(left, right)
    round_trip = np.zeros(g.n_recipes, dtype=bool)
    if shared.size:
        round_trip[(shared >> 32).astype(np.int64)] = True
    return comp, round_trip, self_dependent


class Shape:
    """Stage 1 result: one row of pure-shape signals per kept recipe."""
    __slots__ = ("mat_names", "item_mat", "node_mat", "comp", "band",
                 "role", "role_names", "material_set_preserved", "count_ratio",
                 "mat_log_ratio_max", "is_round_trip", "zero_concrete_inputs",
                 "inputs_equal_outputs", "self_dependent", "n_in_materials",
                 "n_out_materials", "in_total", "out_total", "out_subset_in",
                 "in_item_rec", "in_item_id", "stats")


ROLE_NAMES = ("noop", "generation", "conversion", "recycling_shaped", "production")


def compute_shape(g: PackGraph, out_dir: Path, cap: int) -> Shape:
    sh = Shape()
    sh.mat_names, sh.item_mat, sh.node_mat = material_keys(out_dir, g)
    n = g.n_recipes

    in_rec, in_item, n_capped = expand_or(g.in_rec, g.in_node, g, cap)
    order = np.argsort(in_rec * (1 << 32) + in_item, kind="stable")
    sh.in_item_rec, sh.in_item_id = in_rec[order], in_item[order]

    sh.comp, sh.is_round_trip, sh.self_dependent = round_trip_flags(
        g, sh.item_mat, sh.node_mat, sh.in_item_rec, sh.in_item_id, cap)

    # ---- material aggregation, per side
    mi_rec, mi_key, mi_amt = _fold_pairs(g.in_rec, sh.node_mat[g.in_node], g.in_amt)
    mo_rec, mo_key, mo_amt = _fold_pairs(g.out_rec, sh.item_mat[g.out_item], g.out_amt)
    sh.n_in_materials = _per_recipe_counts(mi_rec, n)
    sh.n_out_materials = _per_recipe_counts(mo_rec, n)
    sh.in_total = np.bincount(mi_rec, weights=mi_amt, minlength=n)
    sh.out_total = np.bincount(mo_rec, weights=mo_amt, minlength=n)

    ki = mi_rec * (1 << 32) + mi_key
    ko = mo_rec * (1 << 32) + mo_key
    both = np.intersect1d(ki, ko)
    pi = np.searchsorted(ki, both)
    po = np.searchsorted(ko, both)
    log_ratio = np.log(np.maximum(mo_amt[po], 1e-12) / np.maximum(mi_amt[pi], 1e-12))
    shared_rec = (both >> 32).astype(np.int64)
    sh.mat_log_ratio_max = np.zeros(n)
    np.maximum.at(sh.mat_log_ratio_max, shared_rec, np.abs(log_ratio))

    n_shared = np.bincount(shared_rec, minlength=n)
    sh.material_set_preserved = (n_shared == sh.n_in_materials) & \
        (n_shared == sh.n_out_materials) & (n_shared > 0)
    sh.out_subset_in = (n_shared == sh.n_out_materials) & (sh.n_out_materials > 0)
    with np.errstate(divide="ignore", invalid="ignore"):
        sh.count_ratio = np.where(sh.in_total > 0, sh.out_total / sh.in_total, np.inf)

    sh.zero_concrete_inputs = _per_recipe_counts(g.in_rec, n) == 0
    sh.inputs_equal_outputs = _sequences_equal(
        g.in_rec, g.in_node, g.in_amt, g.out_rec, g.out_item, g.out_amt, n)

    # ---- learn the conversion band from round-trip-confirmed conversions
    confirmed = sh.is_round_trip & sh.material_set_preserved
    sample = np.abs(log_ratio[confirmed[shared_rec]])
    if sample.size >= MIN_BAND_SAMPLES:
        hi = float(np.quantile(sample, 1.0 - 2 * BAND_QUANTILE))
        band = (math.exp(-hi), math.exp(hi))
    else:
        band = FALLBACK_BAND
    sh.band = band

    inside = sh.mat_log_ratio_max <= math.log(band[1]) + 1e-12
    role = np.full(n, ROLE_NAMES.index("production"), dtype=np.int64)
    is_conv = sh.material_set_preserved & inside
    # Recycling-shaped, defined purely by shape: nothing materially new appears
    # (every output material was already an input material) *and* the recipe
    # expands -- strictly more items come out than went in.  "Out materials are
    # a subset of in materials" alone is not enough: a colour or a wood shared
    # between one ingredient and the product satisfies it for plain production
    # (blue_wool + glass + leather -> blue_harness), which is why the expansion
    # term is required.  A dismantle recipe that returns *foreign* materials
    # (bed -> wool + planks) fails the subset test and stays `production`;
    # value_ratio << 1 (stage 3) and reverse_tier (stage 4) are the signals that
    # catch those, and Phase 0c is where the supervised role model settles it.
    is_recyc = (~is_conv) & sh.out_subset_in & (sh.out_total > sh.in_total)
    role[is_recyc] = ROLE_NAMES.index("recycling_shaped")
    role[is_conv] = ROLE_NAMES.index("conversion")
    role[sh.zero_concrete_inputs] = ROLE_NAMES.index("generation")
    role[sh.inputs_equal_outputs] = ROLE_NAMES.index("noop")
    role[~g.kept] = -1
    sh.role = role
    sh.role_names = ROLE_NAMES
    sh.stats = {
        "materials": int(sh.mat_names.size),
        "or_capped": n_capped,
        "band_lo": round(band[0], 4), "band_hi": round(band[1], 4),
        "band_sample": int(sample.size),
        "round_trip": int(sh.is_round_trip.sum()),
        "self_dependent": int(sh.self_dependent.sum()),
        "zero_concrete_inputs": int(sh.zero_concrete_inputs.sum()),
        "inputs_equal_outputs": int(sh.inputs_equal_outputs.sum()),
        "preserved": int(sh.material_set_preserved.sum()),
    }
    for i, name in enumerate(ROLE_NAMES):
        sh.stats[name] = int((role == i).sum())
    return sh


# ========================================================= stage 2 — pools


def cross_pairs(rec_a, val_a, amt_a, rec_b, val_b, amt_b, keep, n_recipes,
                max_pairs: int = 4096):
    """Per-recipe cartesian product of two key-sorted (recipe, value) runs."""
    sel_a = keep[rec_a] if rec_a.size else np.zeros(0, dtype=bool)
    sel_b = keep[rec_b] if rec_b.size else np.zeros(0, dtype=bool)
    ra, va, aa = rec_a[sel_a], val_a[sel_a], amt_a[sel_a]
    rb, vb, ab = rec_b[sel_b], val_b[sel_b], amt_b[sel_b]
    ca = np.bincount(ra, minlength=n_recipes)
    cb = np.bincount(rb, minlength=n_recipes)
    n_pairs = ca * cb
    over = n_pairs > max_pairs
    n_pairs = np.where(over, 0, n_pairs)
    live = np.flatnonzero(n_pairs > 0)
    if live.size == 0:
        z = np.zeros(0, dtype=np.int64)
        return z, z, z, np.zeros(0, dtype=np.float64), np.zeros(0, dtype=np.float64), int(over.sum())
    start_a = np.concatenate(([0], np.cumsum(ca)))[live]
    start_b = np.concatenate(([0], np.cumsum(cb)))[live]
    sizes = n_pairs[live]
    rep = np.repeat(np.arange(live.size), sizes)
    within = np.arange(int(sizes.sum())) - np.repeat(
        np.concatenate(([0], np.cumsum(sizes)[:-1])), sizes)
    wide = cb[live][rep]
    ia = start_a[rep] + within // wide
    ib = start_b[rep] + within % wide
    return live[rep], va[ia], vb[ib], aa[ia], ab[ib], int(over.sum())


class Pools:
    __slots__ = ("strong", "weak", "src", "dst", "unit_cost", "edge_rec",
                 "n_producers", "n_consumers", "rep_of_strong", "stats")


def compute_pools(g: PackGraph, sh: Shape, max_pairs: int = 4096) -> Pools:
    """Item pools: strong and weak components of the conversion-only item graph."""
    p = Pools()
    conv = sh.role == ROLE_NAMES.index("conversion")
    p.edge_rec, p.src, p.dst, amt_in, amt_out, skipped = cross_pairs(
        sh.in_item_rec, sh.in_item_id, np.ones(sh.in_item_id.size),
        g.out_rec, g.out_item, g.out_amt, conv, g.n_recipes, max_pairs)
    # unit cost = input count per unit of output, taken from the folded amounts
    in_amt_lookup = _amount_lookup(g.in_rec, g.in_node, g.in_amt, sh, p.edge_rec, p.src)
    with np.errstate(divide="ignore", invalid="ignore"):
        p.unit_cost = np.where(amt_out > 0, in_amt_lookup / amt_out, np.inf)
    keep = p.src != p.dst
    p.edge_rec, p.src, p.dst = p.edge_rec[keep], p.src[keep], p.dst[keep]
    p.unit_cost = p.unit_cost[keep]

    ptr, order = build_csr(p.src, g.n_items)
    p.strong = strong_components(g.n_items, ptr, p.dst[order])
    p.weak = weak_components(g.n_items, p.src, p.dst)

    uniq_cons = np.unique(sh.in_item_rec * (1 << 32) + sh.in_item_id)
    p.n_consumers = np.bincount((uniq_cons & 0xFFFFFFFF).astype(np.int64),
                                minlength=g.n_items)
    uniq_prod = np.unique(g.out_rec * (1 << 32) + g.out_item)
    p.n_producers = np.bincount((uniq_prod & 0xFFFFFFFF).astype(np.int64),
                                minlength=g.n_items)

    p.stats = {
        "conv_recipes": int(conv.sum()),
        "pool_edges": int(p.src.size),
        "cross_skipped": skipped,
        "strong_pools": int(_multi_count(p.strong)),
        "weak_pools": int(_multi_count(p.weak)),
        "pooled_items": int(_pooled_items(p.strong)),
        "largest_strong": int(np.max(np.bincount(p.strong))) if g.n_items else 0,
        "largest_weak": int(np.max(np.bincount(p.weak))) if g.n_items else 0,
    }
    return p


def _amount_lookup(in_rec, in_node, in_amt, sh: Shape, edge_rec, edge_item):
    """Input amount for an (recipe, item) edge; OR members inherit the OR amount."""
    key = in_rec * (1 << 32) + in_node
    want = edge_rec * (1 << 32) + edge_item
    pos = np.searchsorted(key, want)
    pos_c = np.clip(pos, 0, max(key.size - 1, 0))
    hit = (key.size > 0) & (key[pos_c] == want)
    out = np.ones(want.size, dtype=np.float64)
    out[hit] = in_amt[pos_c[hit]]
    return out


def _multi_count(comp: np.ndarray) -> int:
    if comp.size == 0:
        return 0
    return int((np.bincount(comp) >= 2).sum())


def _pooled_items(comp: np.ndarray) -> int:
    if comp.size == 0:
        return 0
    sizes = np.bincount(comp)
    return int((sizes[comp] >= 2).sum())


def pool_rows(g: PackGraph, sh: Shape, p: Pools):
    """Materialise both pool families as rows for ``pools.parquet``."""
    rows = []
    strong_id = np.full(g.n_items, "", dtype=object)
    weak_id = np.full(g.n_items, "", dtype=object)
    for kind, comp, target in (("strong", p.strong, strong_id),
                               ("weak", p.weak, weak_id)):
        sizes = np.bincount(comp, minlength=1)
        order = np.argsort(comp, kind="stable")
        starts = np.concatenate(([0], np.cumsum(sizes)))
        edge_comp = comp[p.src] if p.src.size else np.zeros(0, dtype=np.int64)
        same = (comp[p.src] == comp[p.dst]) if p.src.size else np.zeros(0, dtype=bool)
        edge_counts = np.bincount(edge_comp[same], minlength=sizes.size) \
            if p.src.size else np.zeros(sizes.size, dtype=np.int64)
        for cid in np.flatnonzero(sizes >= 2):
            members = order[starts[cid]:starts[cid + 1]]
            names = sorted(str(x) for x in g.items[members])
            pid = f"{kind[0]}pool:{stable_hash(names, 12)}"
            for m in members:
                target[m] = pid
            best = max(members.tolist(),
                       key=lambda m: (int(p.n_consumers[m]), int(p.n_producers[m]),
                                      [-ord(ch) for ch in str(g.items[m])]))
            mats = sorted({str(sh.mat_names[sh.item_mat[m]]) for m in members})
            rec_ids = np.unique(p.edge_rec[same & (edge_comp == cid)]) \
                if p.src.size else np.zeros(0, dtype=np.int64)
            rows.append({
                "pool_id": pid, "pool_kind": kind, "size": int(members.size),
                "members": names, "material_keys": mats,
                "representative": str(g.items[best]),
                "n_edges": int(edge_counts[cid]), "n_recipes": int(rec_ids.size),
            })
    rows.sort(key=lambda r: (r["pool_kind"], -r["size"], r["pool_id"]))
    return rows, strong_id, weak_id


# ============================== stages 3 & 4 — the tier and value fixpoints


class Incidence:
    """CSR views of the hypergraph, shared by the tier and value solvers."""
    __slots__ = ("cons_ptr", "cons_rec", "cons_amt", "orm_ptr", "orm_of",
                 "mach_ptr", "mach_rec", "prod_ptr", "prod_item", "prod_amt",
                 "n_producers", "n_machines", "mach_items",
                 "pr_ptr", "pr_rec", "pr_amt")


def incidence(g: PackGraph) -> Incidence:
    inc = Incidence()
    ptr, order = build_csr(g.in_node, g.n_nodes)
    inc.cons_ptr, inc.cons_rec = ptr, g.in_rec[order]
    inc.cons_amt = g.in_amt[order]

    sizes = np.diff(g.or_ptr)
    or_idx = np.repeat(np.arange(g.n_or, dtype=np.int64), sizes) if g.n_or else \
        np.zeros(0, dtype=np.int64)
    ptr, order = build_csr(g.or_mem, g.n_items) if g.or_mem.size else \
        (np.zeros(g.n_items + 1, dtype=np.int64), np.zeros(0, dtype=np.int64))
    inc.orm_ptr, inc.orm_of = ptr, (or_idx[order] if g.or_mem.size else order)

    msizes = np.diff(g.machines_ptr)
    mrec = np.repeat(np.arange(g.n_recipes, dtype=np.int64), msizes)
    ok = (g.machines_item >= 0) & g.kept[mrec]
    pair = np.unique(mrec[ok] * (1 << 32) + g.machines_item[ok])
    mitem = (pair & 0xFFFFFFFF).astype(np.int64)
    mrec = (pair >> 32).astype(np.int64)
    ptr, order = build_csr(mitem, g.n_items)
    inc.mach_ptr, inc.mach_rec = ptr, mrec[order]
    inc.n_machines = np.bincount(mrec, minlength=g.n_recipes)
    inc.mach_items = np.unique(mitem)

    ptr, order = build_csr(g.out_rec, g.n_recipes)
    inc.prod_ptr = ptr
    inc.prod_item, inc.prod_amt = g.out_item[order], g.out_amt[order]
    inc.n_producers = np.bincount(g.out_item, minlength=g.n_items)

    ptr, order = build_csr(g.out_item, g.n_items)
    inc.pr_ptr, inc.pr_rec, inc.pr_amt = ptr, g.out_rec[order], g.out_amt[order]
    return inc


def solve_hypergraph(g: PackGraph, inc: Incidence, seed: np.ndarray,
                     mode: str, process_cost: np.ndarray = None,
                     acc_init: np.ndarray = None, pre_seed: np.ndarray = None,
                     ring_seed: np.ndarray = None,
                     recipe_weight: np.ndarray = None,
                     exclude: np.ndarray = None):
    """Knuth's generalisation of Dijkstra over the recipe hypergraph.

    ``mode="value"``  recipe cost = sum(input value x amount) + process cost,
                      each output claims ``cost / its own amount``.
    ``mode="tier"``   recipe tier = max(input tier, machine tier) + 1, where
                      the machine term arrives as ``acc_init`` -- a constant
                      solved in an earlier machine-free pass, because a machine
                      that is built *in itself* (the crafting table) would
                      otherwise make its whole pack one big cycle.

    A node settles once, in non-decreasing order, so a recipe that consumes its
    own output cannot spiral: it only fires after that item has settled.  The
    tier function is strictly superior (``max + 1``) so tiers are exact; the
    value function is not (dividing by an output amount > 1 can undercut an
    input), so values are the well-defined Dijkstra upper bound -- the cheapest
    chain a planner can actually commit to.
    """
    n_nodes, n_items, n_rec = g.n_nodes, g.n_items, g.n_recipes
    is_tier = mode == "tier"
    inf = float("inf")
    settled = np.zeros(n_nodes, dtype=bool)
    bootstrapped = np.zeros(n_items, dtype=bool)
    boot_value = np.full(n_items, inf)
    fired = np.zeros(n_rec, dtype=bool)
    source = np.full(n_items, -1, dtype=np.int64)
    pending = np.bincount(g.in_rec, minlength=n_rec).astype(np.int64)
    pending[~g.kept] = -1
    if exclude is not None:
        pending[np.asarray(exclude, dtype=bool)] = -1
    weight = np.ones(n_rec) if recipe_weight is None else \
        np.asarray(recipe_weight, dtype=np.float64)
    if process_cost is None:
        process_cost = np.zeros(n_rec)
    if not is_tier and seed.size and not (np.min(seed) > 0):
        raise ValueError("seed values must be strictly positive")
    if ring_seed is None:
        ring_seed = np.ones(n_items) if not is_tier else np.zeros(n_items)

    cons_ptr = inc.cons_ptr.tolist(); cons_rec = inc.cons_rec.tolist()
    cons_amt = inc.cons_amt.tolist()
    orm_ptr = inc.orm_ptr.tolist(); orm_of = inc.orm_of.tolist()
    mach_ptr = inc.mach_ptr.tolist(); mach_rec = inc.mach_rec.tolist()
    prod_ptr = inc.prod_ptr.tolist(); prod_item = inc.prod_item.tolist()
    prod_amt = inc.prod_amt.tolist()
    pend = pending.tolist(); vals = [inf] * n_nodes
    accl = [0.0] * n_rec if acc_init is None else \
        np.asarray(acc_init, dtype=np.float64).tolist()
    pcost = process_cost.tolist(); vcost = g.value_cost.tolist()
    wl = weight.tolist()
    args = (accl, vcost, pcost, wl, prod_ptr, prod_item, prod_amt, vals,
            settled, fired, source, is_tier)

    heap: list = []
    for i in np.flatnonzero(inc.n_producers == 0).tolist():
        vals[i] = 0.0 if is_tier else float(seed[i])
        heapq.heappush(heap, (vals[i], i))
    # Items a previous pass had to bootstrap out of a ring enter as leaves, so
    # that everything downstream of them settles in the right order.
    if pre_seed is not None:
        for i in np.flatnonzero(np.isfinite(pre_seed)).tolist():
            if pre_seed[i] < vals[i]:
                vals[i] = float(pre_seed[i])
                bootstrapped[i] = True
                boot_value[i] = vals[i]
                heapq.heappush(heap, (vals[i], i))
    for r in np.flatnonzero(pending == 0).tolist():   # nothing gates it at all
        _fire(r, heap, *args)

    cycle_seeds, rounds = 0, 0
    while True:
        while heap:
            v, node = heapq.heappop(heap)
            if settled[node] or v > vals[node]:
                continue
            settled[node] = True
            if node < n_items:
                for k in range(orm_ptr[node], orm_ptr[node + 1]):
                    o = n_items + orm_of[k]
                    if not settled[o] and v < vals[o]:
                        vals[o] = v
                        heapq.heappush(heap, (v, o))
            for k in range(cons_ptr[node], cons_ptr[node + 1]):
                r = cons_rec[k]
                if is_tier:
                    if v > accl[r]:
                        accl[r] = v
                else:
                    accl[r] += v * cons_amt[k]
                pend[r] -= 1
                if pend[r] == 0:
                    _fire(r, heap, *args)
        stuck = np.flatnonzero(~settled[:n_items])
        if stuck.size == 0:
            break
        rounds += 1
        # A ring the queue cannot enter.  Decompose the unsettled sub-graph
        # into strong components and find the ones with *no entry from
        # outside*: every producer of every member depends on another member.
        # Those are generation rings -- nothing in the pack can make them --
        # so they are priced like leaves, at their cheapest member's own
        # acquirability seed, and flagged.  A ring that does have an outside
        # entry is never priced here: it settles by ordinary relaxation as
        # soon as the ring it depends on has settled.  Because the
        # ring-dependency graph is a DAG (a cycle among rings would be one
        # ring), at least one closed ring always exists, so this terminates.
        pend_arr = np.asarray(pend)
        # An item every one of whose producers is gone -- excluded by a caller,
        # or never able to fire -- is orphaned: nothing makes it, so it is
        # priced like a leaf, at its own seed, and flagged.
        live_rec = (pend_arr > 0) & g.kept
        has_live = np.zeros(n_items, dtype=bool)
        if g.out_rec.size:
            has_live[g.out_item[live_rec[g.out_rec]]] = True
        orphans = stuck[~has_live[stuck]]
        groups = [np.array([i], dtype=np.int64) for i in orphans.tolist()]
        rest = stuck[has_live[stuck]]
        if rest.size:
            closed, comp = _closed_rings(g, inc, settled, pend_arr, rest)
            groups.extend(rest[comp[rest] == cid] for cid in closed.tolist())
        priced = 0
        for members in groups:
            if members.size == 0:
                continue
            pick = members[int(np.argmin(ring_seed[members]))]
            value = 0.0 if is_tier else float(ring_seed[pick])
            if settled[pick] or not (value < vals[pick]):
                continue
            vals[pick] = value
            bootstrapped[pick] = True
            boot_value[pick] = value
            cycle_seeds += 1
            priced += 1
            heapq.heappush(heap, (value, int(pick)))
        if priced == 0:
            break

    return (np.asarray(vals), np.asarray(accl), source, settled, bootstrapped,
            boot_value,
            {"cycle_rounds": rounds, "cycle_seeds": cycle_seeds,
             "unsettled_items": int((~settled[:n_items]).sum()),
             "fired": int(fired.sum())})


def solve_to_fixpoint(g: PackGraph, inc: Incidence, seed: np.ndarray, mode: str,
                      process_cost: np.ndarray = None,
                      acc_init: np.ndarray = None, max_passes: int = 4,
                      ring_seed: np.ndarray = None,
                      recipe_weight: np.ndarray = None,
                      exclude: np.ndarray = None):
    """``solve_hypergraph`` until the bootstrapped ring set stops growing.

    One pass settles ring items only after the queue has drained, which is too
    late: anything they feed has already settled at the price of some other
    chain.  Re-running with the ring values injected as leaves puts them back
    in queue order.  Only the ring set is carried over, so the settle-once rule
    -- the thing that stops a recipe that doubles its own input from driving
    values to zero -- still holds inside every pass.
    """
    pre = np.full(g.n_items, np.inf)
    last = None
    for passes in range(1, max_passes + 1):
        out = solve_hypergraph(g, inc, seed, mode, process_cost, acc_init,
                               pre_seed=pre, ring_seed=ring_seed,
                               recipe_weight=recipe_weight, exclude=exclude)
        vals, acc, source, settled, boot, boot_value, st = out
        st["passes"] = passes
        # Carry the value the ring was *seeded* at, never the value it was
        # later relaxed to: re-feeding a relaxed ring value would let the ring
        # get cheaper on every pass, which is the free lunch all over again.
        nxt = np.where(np.isfinite(pre), pre, boot_value)
        if last is not None and np.array_equal(np.isfinite(nxt), np.isfinite(pre)):
            return out[:5] + out[6:]
        last, pre = out, nxt
    return last[:5] + last[6:]


def _closed_rings(g: PackGraph, inc: Incidence, settled: np.ndarray,
                  pend: np.ndarray, stuck: np.ndarray):
    """Strong components of the unsettled sub-graph that have no way in.

    An edge is drawn from every *unsettled* input of an unfired recipe to every
    *unsettled* output of it; an unsettled OR input contributes its unsettled
    members, because taking any one of them is a way through.  A component is
    **closed** when every producer of every member has at least one unsettled
    input inside that same component -- i.e. there is no entry from outside.
    Returns ``(closed component ids, component id per item)``.
    """
    n_items = g.n_items
    comp = np.full(n_items, -1, dtype=np.int64)
    if stuck.size == 0:
        return np.zeros(0, dtype=np.int64), comp

    live = (pend > 0) & g.kept
    out_sel = live[g.out_rec] & ~settled[g.out_item]
    in_sel = live[g.in_rec] & ~settled[g.in_node]
    if not out_sel.any() or not in_sel.any():
        return np.zeros(0, dtype=np.int64), comp
    in_rec, in_item, _ = expand_or(g.in_rec[in_sel], g.in_node[in_sel], g,
                                   1 << 30)
    keep = ~settled[in_item]
    in_rec, in_item = in_rec[keep], in_item[keep]
    out_rec, out_item = g.out_rec[out_sel], g.out_item[out_sel]

    # item -> item edges through the recipes that still cannot fire
    order_i = np.argsort(in_rec, kind="stable")
    order_o = np.argsort(out_rec, kind="stable")
    _, e_src, e_dst, _, _, _ = cross_pairs(
        in_rec[order_i], in_item[order_i], np.ones(in_item.size),
        out_rec[order_o], out_item[order_o], np.ones(out_item.size),
        np.ones(g.n_recipes, dtype=bool), g.n_recipes, 1 << 30)
    return _closed_from_edges(g, comp, stuck, in_rec, in_item, out_rec,
                              out_item, e_src, e_dst)


def _closed_from_edges(g, comp, stuck, in_rec, in_item, out_rec, out_item,
                       e_src, e_dst):
    ptr, order = build_csr(e_src, g.n_items)
    part = strong_components(g.n_items, ptr, e_dst[order])
    comp[:] = part
    # a component is open as soon as one producer of one member depends on
    # nothing inside it
    in_keys = np.unique(in_rec * (1 << 32) + comp[in_item])
    want = out_rec * (1 << 32) + comp[out_item]
    pos = np.searchsorted(in_keys, want)
    pos_c = np.clip(pos, 0, max(in_keys.size - 1, 0))
    inside = (in_keys.size > 0) & (in_keys[pos_c] == want)
    open_comps = np.unique(comp[out_item[~inside]]) if (~inside).any() else \
        np.zeros(0, dtype=np.int64)
    stuck_comps = np.unique(comp[stuck])
    closed = np.setdiff1d(stuck_comps, open_comps, assume_unique=True)
    return closed, comp


def _fire(r, heap, accl, vcost, pcost, weight, prod_ptr, prod_item, prod_amt,
          vals, settled, fired, source, is_tier):
    fired[r] = True
    if is_tier:
        base = accl[r] + 1.0
        for k in range(prod_ptr[r], prod_ptr[r + 1]):
            o = prod_item[k]
            if not settled[o] and base < vals[o]:
                vals[o] = base
                source[o] = r
                heapq.heappush(heap, (base, o))
    else:
        cost = (accl[r] + vcost[r] + pcost[r]) * weight[r]
        for k in range(prod_ptr[r], prod_ptr[r + 1]):
            o = prod_item[k]
            cand = cost / prod_amt[k]
            if not settled[o] and cand < vals[o]:
                vals[o] = cand
                source[o] = r
                heapq.heappush(heap, (cand, o))


class Tiers:
    __slots__ = ("item_tier", "recipe_tier", "machine_tier", "machine_id",
                 "machine_unbuildable", "machine_in_universe", "machine_recipes",
                 "machine_producers", "max_input_tier", "recipe_machine_tier",
                 "reverse_tier", "reverse_margin", "out_tier", "stats")


def _recipe_machine_floor(g: PackGraph, inc: Incidence, machine_tier: np.ndarray):
    """Per-recipe ``max(machine tier)`` -- 0 when the recipe lists no machine."""
    msizes = np.diff(g.machines_ptr)
    mrec = np.repeat(np.arange(g.n_recipes, dtype=np.int64), msizes)
    ok = g.machines_item >= 0
    floor = np.zeros(g.n_recipes)
    if ok.any():
        np.maximum.at(floor, mrec[ok], machine_tier[g.machines_item[ok]])
    return floor


def solve_tiers(g: PackGraph, inc: Incidence) -> Tiers:
    """Two passes: machine tiers from their own chains, then the joint solve."""
    t = Tiers()
    seed = np.zeros(g.n_items)
    bare, _, _, settled0, boot0, st0 = solve_to_fixpoint(g, inc, seed, "tier")
    bare_item = bare[:g.n_items]

    t.machine_id = inc.mach_items
    # A machine with no in-pack producer -- or one that only exists because the
    # cycle break bootstrapped it (the crafting table, built in itself) -- is
    # hand/unknown: tier 0, flagged unbuildable.
    buildable = (settled0[:g.n_items] & np.isfinite(bare_item) & ~boot0
                 & (inc.n_producers > 0))
    machine_tier_all = np.where(buildable, bare_item, 0.0)
    unbuildable_all = ~buildable
    floor = _recipe_machine_floor(g, inc, machine_tier_all)

    vals, acc, source, settled, boot, st = solve_to_fixpoint(g, inc, seed,
                                                             "tier", acc_init=floor)
    t.item_tier = vals[:g.n_items]
    t.recipe_machine_tier = floor
    t.max_input_tier = np.zeros(g.n_recipes)
    if g.in_rec.size:
        np.fmax.at(t.max_input_tier, g.in_rec, vals[g.in_node])
    t.recipe_tier = np.asarray(acc) + 1.0
    t.machine_tier = machine_tier_all[inc.mach_items]
    t.machine_unbuildable = unbuildable_all[inc.mach_items]
    t.machine_producers = inc.n_producers[inc.mach_items]
    t.machine_recipes = np.diff(inc.mach_ptr)[inc.mach_items]

    with np.errstate(invalid="ignore"):
        out_tier = np.full(g.n_recipes, np.nan)
        finite = np.isfinite(t.item_tier[g.out_item])
        np.fmin.at(out_tier, g.out_rec[finite], t.item_tier[g.out_item][finite])
    t.out_tier = out_tier
    gap = np.where(np.isfinite(out_tier) & np.isfinite(t.max_input_tier),
                   t.max_input_tier - out_tier, np.nan)
    good = gap[np.isfinite(gap)]
    # Tukey upper fence on the tier-gap distribution: a learned outlier
    # threshold that follows the pack's own spread instead of flagging a fixed
    # share.  Forward recipes pile up at gap == -1 (they *are* their output's
    # cheapest chain), so the fence sits just above the pack's normal slack.
    if good.size >= MIN_BAND_SAMPLES:
        q25, q75 = np.quantile(good, 0.25), np.quantile(good, 0.75)
        margin = max(1.0, float(q75 + 1.5 * (q75 - q25)))
    else:
        margin = 1.0
    t.reverse_margin = margin
    t.reverse_tier = np.isfinite(gap) & (gap >= margin)

    finite_tier = t.item_tier[np.isfinite(t.item_tier)]
    t.stats = {
        "max_item_tier": int(finite_tier.max()) if finite_tier.size else 0,
        "unreached_items": int((~np.isfinite(t.item_tier)).sum()),
        "machines": int(inc.mach_items.size),
        "machines_unbuildable": int(t.machine_unbuildable.sum()),
        "reverse_margin": margin,
        "reverse_tier": int(t.reverse_tier.sum()),
        "bootstrapped_items": int(boot.sum()),
        "bare_bootstrapped": int(boot0.sum()),
        "cycle_seeds": st["cycle_seeds"], "passes": st["passes"],
        "hand_recipes": int((inc.n_machines == 0).sum()),
    }
    return t


class Values:
    __slots__ = ("item_value", "node_value", "source_recipe", "bootstrapped",
                 "value_ratio", "in_value", "out_value", "unique_producer",
                 "value_source", "step_cost", "stats")


def netted_graph(g: PackGraph) -> PackGraph:
    """Apply the catalyst hook to a copy of the graph, for the value solve.

    A recipe whose outputs would vanish entirely keeps its original amounts,
    so netting can never delete the only producer of an item.
    """
    net = PackGraph()
    for slot in PackGraph.__slots__:
        setattr(net, slot, getattr(g, slot))
    in_amt, out_amt = net_catalysts(g.in_rec, g.in_node, g.in_amt,
                                    g.out_rec, g.out_item, g.out_amt)
    survives = np.zeros(g.n_recipes, dtype=bool)
    if g.out_rec.size:
        np.logical_or.at(survives, g.out_rec, out_amt > 0)
    keep_in = survives[g.in_rec] if g.in_rec.size else np.zeros(0, dtype=bool)
    keep_out = survives[g.out_rec] if g.out_rec.size else np.zeros(0, dtype=bool)
    net.in_amt = np.where(keep_in, in_amt, g.in_amt)
    net.out_amt = np.where(keep_out, out_amt, g.out_amt)
    live_in = net.in_amt > 0
    net.in_rec, net.in_node = g.in_rec[live_in], g.in_node[live_in]
    net.in_amt = net.in_amt[live_in]
    live_out = net.out_amt > 0
    net.out_rec, net.out_item = g.out_rec[live_out], g.out_item[live_out]
    net.out_amt = net.out_amt[live_out]
    return net


def step_costs(g: PackGraph, ti: Tiers, seed: np.ndarray,
               step_cost: float = STEP_COST) -> np.ndarray:
    """Per-recipe process cost: ``step_cost x median leaf seed x (1 + tier)``.

    Running a recipe is work.  Without this a smelted ore costs exactly what
    the ore costs, a nine-step chain costs what its leaves cost, and the whole
    ladder is flat -- which is what v1 did.  Scaling with the tier makes the
    cost of a step grow with how deep into the pack it sits, so the value
    ladder and the tier ladder point the same way.
    """
    unit = float(np.median(seed[np.isfinite(seed)])) if seed.size else 1.0
    tier = ti.recipe_tier
    finite = tier[np.isfinite(tier)]
    tier = np.where(np.isfinite(tier), tier,
                    float(finite.max()) if finite.size else 0.0)
    return step_cost * unit * (1.0 + np.maximum(tier, 0.0))


def compute_values(g: PackGraph, inc: Incidence, sh: Shape, ti: Tiers,
                   seed: np.ndarray, ring_seed: np.ndarray = None,
                   reliability: np.ndarray = None, net: bool = False,
                   step_cost: float = STEP_COST,
                   exclude: np.ndarray = None) -> Values:
    """Item values by the hypergraph Dijkstra, plus the per-recipe value ratio."""
    v = Values()
    process = step_costs(g, ti, seed, step_cost)
    solved = netted_graph(g) if net else g
    if net:
        inc = incidence(solved)
    # A producer you cannot trust is a more expensive producer:
    # ``cost / reliability``, normalised so the pack's most trusted recipe is
    # unpenalised.  The normalisation matters.  Reliability carries a
    # corpus-wide constant (the field ceiling: chance and value are degenerate
    # everywhere, so nothing scores above 0.5), and a raw ``1 / reliability``
    # turns that constant into a >=2x multiplier on *every* step, compounding
    # to ~1000x over a ten-deep chain for a fact that says nothing about any
    # individual recipe.  Dividing it out keeps exactly the informative part --
    # how much less trustworthy this recipe is than the best one in its pack.
    # The range that survives (about 1x-2x here) is far too flat to gate quest
    # or display systems on its own; that is Phase 0c's job.
    weight = None
    if reliability is not None:
        rel = np.clip(np.asarray(reliability, dtype=np.float64), 1e-6, 1.0)
        live = rel[g.kept] if g.kept.any() else rel
        weight = float(live.max()) / rel
    vals, _, source, settled, boot, st = solve_to_fixpoint(
        solved, inc, seed, "value", process_cost=process,
        ring_seed=ring_seed, recipe_weight=weight, exclude=exclude)
    v.node_value = vals
    v.item_value = vals[:g.n_items]
    v.source_recipe = source
    v.bootstrapped = boot

    v.step_cost = process
    v.in_value = np.zeros(g.n_recipes)
    if g.in_rec.size:
        np.add.at(v.in_value, g.in_rec, vals[g.in_node] * g.in_amt)
    v.out_value = np.zeros(g.n_recipes)
    if g.out_rec.size:
        np.add.at(v.out_value, g.out_rec, v.item_value[g.out_item] * g.out_amt)
    with np.errstate(divide="ignore", invalid="ignore"):
        v.value_ratio = np.where(v.in_value > 0, v.out_value / v.in_value, np.inf)

    min_prod = np.full(g.n_recipes, 0, dtype=np.int64)
    if g.out_rec.size:
        np.fmax.at(min_prod, g.out_rec, inc.n_producers[g.out_item])
        uniq = np.full(g.n_recipes, 1 << 30, dtype=np.int64)
        np.fmin.at(uniq, g.out_rec, inc.n_producers[g.out_item])
        v.unique_producer = uniq == 1
    else:
        v.unique_producer = np.zeros(g.n_recipes, dtype=bool)

    src = np.where(inc.n_producers == 0, "leaf", "recipe")
    src = np.where(boot, "cycle", src)
    src = np.where(~settled[:g.n_items], "unreached", src)
    v.value_source = src

    finite = v.item_value[np.isfinite(v.item_value)]
    ratio = v.value_ratio[np.isfinite(v.value_ratio) & (v.value_ratio > 0)]
    v.stats = {
        "netted": bool(net),
        "weight_max": round(float(weight.max()), 3) if weight is not None else 1.0,
        "step_cost": round(float(process.min()), 4),
        "generation_rings": int(boot.sum()),
        "unreached": int((~np.isfinite(v.item_value)).sum()),
        "passes": st["passes"],
        "min_value": float(f"{finite.min():.3g}") if finite.size else 0.0,
        "median_value": round(float(np.median(finite)), 4) if finite.size else 0.0,
        "max_value": float(f"{finite.max():.4g}") if finite.size else 0.0,
        "ratio_p05": round(float(np.quantile(ratio, 0.05)), 4) if ratio.size else 0.0,
        "ratio_median": round(float(np.median(ratio)), 4) if ratio.size else 0.0,
        "ratio_p95": round(float(np.quantile(ratio, 0.95)), 3) if ratio.size else 0.0,
        "ratio_gt1_unique": int(((v.value_ratio > 1.5) & v.unique_producer).sum()),
        "ratio_gt1_alt": int(((v.value_ratio > 1.5) & ~v.unique_producer).sum()),
        "ratio_lt1": int((v.value_ratio < 0.5).sum()),
    }
    return v


# ================================ stages 5 & 6 — leaves, acquirability, data gap


def pack_fingerprint(out_dir: Path, slug: str) -> str:
    """Content hash of a pack's template multiset.

    Two exports of the same pack differ in file name, directory name and member
    order but fold to exactly the same templates, so this identifies duplicate
    packs without ever looking at a pack *name*.
    """
    tbl = pq.read_table(out_dir / slug / "recipe_template.parquet",
                        columns=["template_id"])
    ids, counts = np.unique(tbl.column("template_id").to_numpy(zero_copy_only=False),
                            return_counts=True)
    return stable_hash([[str(i), int(c)] for i, c in zip(ids, counts)], 16)


class PackScan:
    """The cross-pack slice of one pack: which ids exist, and who can make them."""
    __slots__ = ("slug", "fingerprint", "items", "has_producer", "renewable")


def scan_pack(cache_dir: Path, out_dir: Path, slug: str, cap: int) -> PackScan:
    g = build_graph(cache_dir, out_dir, slug, cap)
    sc = PackScan()
    sc.slug = slug
    sc.fingerprint = pack_fingerprint(out_dir, slug)
    sc.items = g.items
    n_prod = np.bincount(g.out_item, minlength=g.n_items)
    sc.has_producer = n_prod > 0
    zero_in = np.bincount(g.in_rec, minlength=g.n_recipes) == 0
    sc.renewable = np.zeros(g.n_items, dtype=bool)
    if g.out_rec.size:
        sc.renewable[g.out_item[zero_in[g.out_rec]]] = True
    return sc


class CrossPack:
    __slots__ = ("ids", "containing", "producing", "leafing", "renewable",
                 "groups", "members", "stats")


def cross_pack(scans: list) -> CrossPack:
    """Fold per-pack scans into per-id pack counts, duplicate packs counted once."""
    cp = CrossPack()
    groups = {}
    for sc in scans:
        groups.setdefault(sc.fingerprint, []).append(sc)
    cp.groups = groups
    cp.members = {sc.slug: sorted(s.slug for s in groups[sc.fingerprint])
                  for sc in scans}
    reps = [sorted(v, key=lambda s: s.slug)[0] for _, v in sorted(groups.items())]

    all_ids = np.unique(np.concatenate([r.items for r in reps])) if reps else \
        np.zeros(0, dtype=object)
    cp.ids = all_ids
    cp.containing = np.zeros(all_ids.size, dtype=np.int64)
    cp.producing = np.zeros(all_ids.size, dtype=np.int64)
    cp.leafing = np.zeros(all_ids.size, dtype=np.int64)
    cp.renewable = np.zeros(all_ids.size, dtype=bool)
    for r in reps:
        pos = np.searchsorted(all_ids, r.items)
        cp.containing[pos] += 1
        cp.producing[pos] += r.has_producer
        cp.leafing[pos] += ~r.has_producer
        cp.renewable[pos[r.renewable]] = True
    cp.stats = {
        "packs": len(scans), "dedup_groups": len(groups),
        "repeated_packs": len(scans) - len(groups),
        "ids": int(all_ids.size),
        "ids_renewable": int(cp.renewable.sum()),
        "ids_every_pack_leaf": int((cp.producing == 0).sum()),
    }
    return cp


def data_gap_rule(containing_other: np.ndarray, producing_other: np.ndarray):
    """A leaf is a *data gap* when a majority of the other packs that ship the
    same id can already make it.  ``k`` is therefore not a constant: it is the
    majority of the packs that carry the id, which is the only threshold that
    means the same thing for an id in 2 packs and an id in 21."""
    k = np.maximum(1, np.ceil(containing_other / 2.0)).astype(np.int64)
    return (producing_other >= k) & (containing_other >= 1), k


LEAF_FEATURES = ("n_consumers", "n_consumer_ns", "n_consumer_systems", "n_tags",
                 "n_forms_of_material", "min_consumer_tier", "renewable",
                 "leaf_pack_fraction", "producer_packs", "n_variant_keys")

#: Features the *unsupervised* composite is fitted on.  ``n_forms_of_material``
#: is kept in the table for Phase 0c but excluded here: it measures how
#: *core* a material is, not how cheap.  On fom it seeded diamond_ore cheap
#: (cls:diamond has 14 forms) and inflated nether_star (whose material class
#: lumps it with nether_brick, so it inherits 6 "forms").
COMPOSITE_FEATURES = tuple(f for f in LEAF_FEATURES if f != "n_forms_of_material")


class Leaves:
    __slots__ = ("idx", "matrix", "acquirability", "seed_value", "data_gap",
                 "world_resource", "producer_packs", "containing_packs",
                 "leaf_fraction", "renewable", "loadings", "columns", "stats",
                 "seed_all", "n_variant_keys", "form_class", "gap_k", "raw",
                 "spread")


def compute_leaves(g: PackGraph, inc: Incidence, sh: Shape, pl: Pools,
                   ti: Tiers, cp: CrossPack, out_dir: Path,
                   seed_spread: float = SEED_SPREAD) -> Leaves:
    lf = Leaves()
    lf.idx = np.flatnonzero(inc.n_producers == 0)
    n = lf.idx.size

    ident = pq.read_table(out_dir / g.slug / "identity.parquet",
                          columns=["base_id", "form_class", "n_variant_keys"])
    ident = ident.sort_by("base_id")
    iid = ident.column("base_id").to_numpy(zero_copy_only=False)
    iform = ident.column("form_class").to_numpy(zero_copy_only=False)
    ivar = ident.column("n_variant_keys").to_numpy()
    pos = np.searchsorted(iid, g.items)
    pos_c = np.clip(pos, 0, max(iid.size - 1, 0))
    known = (iid.size > 0) & (iid[pos_c] == g.items)
    form = np.where(known, iform[pos_c], None)
    lf.form_class = form
    lf.n_variant_keys = np.where(known, ivar[pos_c], 0).astype(np.int64)

    # forms per material: "a material with many forms is a basic material"
    ok = ~np.equal(form, None)
    pair = np.unique(sh.item_mat[ok] * (1 << 32) +
                     np.unique(form[ok], return_inverse=True)[1].reshape(-1))
    forms_per_mat = np.bincount((pair >> 32).astype(np.int64),
                                minlength=sh.mat_names.size)

    # consumer breadth
    rid_ns = np.array([split_id(str(x))[0] for x in
                       g.recipe_id.to_numpy(zero_copy_only=False)], dtype=object)
    out_ns_of_rec = np.empty(g.n_recipes, dtype=object)
    out_ns_of_rec[:] = ""
    if g.out_rec.size:
        first = np.unique(g.out_rec, return_index=True)[1]
        out_ns_of_rec[g.out_rec[first]] = [split_id(str(x))[0]
                                           for x in g.items[g.out_item[first]]]
    rec_ns = np.where(rid_ns != "", rid_ns, out_ns_of_rec)
    ns_code = np.unique(rec_ns, return_inverse=True)[1].reshape(-1)

    cons_item, cons_rec = sh.in_item_id, sh.in_item_rec
    n_cons_ns = _distinct_per_item(cons_item, ns_code[cons_rec], g.n_items)
    n_cons_sys = _distinct_per_item(cons_item, g.system_c[cons_rec], g.n_items)
    min_tier = np.full(g.n_items, np.inf)
    if cons_item.size:
        np.fmin.at(min_tier, cons_item, ti.recipe_tier[cons_rec])
    n_tags = np.diff(inc.orm_ptr)

    pos = np.searchsorted(cp.ids, g.items)
    pos_c = np.clip(pos, 0, max(cp.ids.size - 1, 0))
    seen = (cp.ids.size > 0) & (cp.ids[pos_c] == g.items)
    mine = np.ones(g.n_items, dtype=np.int64)
    containing_other = np.where(seen, cp.containing[pos_c], 1) - mine
    producing_other = np.where(seen, cp.producing[pos_c], 0)
    producing_other = producing_other - (inc.n_producers > 0).astype(np.int64)
    producing_other = np.maximum(producing_other, 0)
    leafing = np.where(seen, cp.leafing[pos_c], 1)
    containing = np.where(seen, cp.containing[pos_c], 1)
    lf.leaf_fraction = leafing / np.maximum(containing, 1)
    lf.renewable = np.where(seen, cp.renewable[pos_c], False)
    lf.producer_packs = producing_other
    lf.containing_packs = containing_other

    gap, k = data_gap_rule(containing_other, producing_other)
    lf.gap_k = k
    is_leaf = inc.n_producers == 0
    lf.data_gap = gap & is_leaf
    lf.world_resource = is_leaf & ~lf.data_gap

    columns = {
        "n_consumers": pl.n_consumers.astype(np.float64),
        "n_consumer_ns": n_cons_ns.astype(np.float64),
        "n_consumer_systems": n_cons_sys.astype(np.float64),
        "n_tags": n_tags.astype(np.float64),
        "n_forms_of_material": forms_per_mat[sh.item_mat].astype(np.float64),
        "min_consumer_tier": np.where(np.isfinite(min_tier), min_tier,
                                      np.nanmax(ti.item_tier[np.isfinite(
                                          ti.item_tier)], initial=0.0) + 1.0),
        "renewable": lf.renewable.astype(np.float64),
        "leaf_pack_fraction": lf.leaf_fraction,
        "producer_packs": producing_other.astype(np.float64),
        "n_variant_keys": lf.n_variant_keys.astype(np.float64),
    }
    lf.columns = COMPOSITE_FEATURES
    lf.raw = columns
    mat = np.column_stack([rank_norm(columns[c][lf.idx])
                           for c in COMPOSITE_FEATURES]) \
        if n else np.zeros((0, len(COMPOSITE_FEATURES)))
    lf.matrix = mat

    # Unsupervised score: first principal component of the rank-normalised
    # features, oriented so that "consumed by many things" reads as *cheap*.
    if n >= 2:
        centred = mat - mat.mean(axis=0, keepdims=True)
        _, sv, vt = np.linalg.svd(centred, full_matrices=False)
        comp = vt[0]
        score = centred @ comp
        breadth = rank_norm(columns["n_consumers"][lf.idx])
        if np.corrcoef(score, breadth)[0, 1] < 0:
            comp, score = -comp, -score
        lf.loadings = dict(zip(COMPOSITE_FEATURES, [float(x) for x in comp]))
        explained = float(sv[0] ** 2 / max((sv ** 2).sum(), 1e-12))
    else:
        comp = np.zeros(len(COMPOSITE_FEATURES))
        score = np.zeros(n)
        lf.loadings = dict.fromkeys(COMPOSITE_FEATURES, 0.0)
        explained = 0.0
    lf.acquirability = rank_norm(score) if n else score

    # acquirability -> seed: the whole leaf population inside [1, K].
    spread = max(float(seed_spread), 1.0)
    seeds = np.power(spread, 1.0 - lf.acquirability) if n else lf.acquirability
    lf.seed_value = seeds
    lf.spread = spread

    # The same map, projected onto *every* item: a non-leaf's features are
    # ranked against the leaf distribution and pushed through the same
    # component.  Leaves keep their exact seed.  Only the closed-ring pricing
    # in the value solve reads the non-leaf entries.
    lf.seed_all = np.ones(g.n_items)
    if n >= 2:
        proj = np.column_stack([_rank_against(columns[c][lf.idx], columns[c])
                                for c in COMPOSITE_FEATURES])
        all_score = (proj - mat.mean(axis=0, keepdims=True)) @ comp
        all_acq = _rank_against(score, all_score)
        lf.seed_all = np.power(spread, 1.0 - all_acq)
    lf.seed_all[lf.idx] = seeds

    lf.stats = {
        "leaves": int(n),
        "data_gap": int(lf.data_gap.sum()),
        "world_resource": int(lf.world_resource.sum()),
        "renewable_leaves": int(lf.renewable[lf.idx].sum()),
        "pc1_explained": round(explained, 3),
        "seed_spread": spread,
        "seed_min": float(f"{seeds.min():.3g}") if n else 0.0,
        "seed_max": float(f"{seeds.max():.3g}") if n else 0.0,
    }
    return lf


def _rank_against(reference: np.ndarray, values: np.ndarray) -> np.ndarray:
    """Where each of ``values`` falls in ``reference``'s distribution, in [0, 1]."""
    if reference.size < 2:
        return np.full(values.shape, 0.5)
    ref = np.sort(reference)
    pos = (np.searchsorted(ref, values, side="left") +
           np.searchsorted(ref, values, side="right")) / 2.0
    return np.clip(pos / (ref.size - 1.0), 0.0, 1.0)


def _distinct_per_item(item: np.ndarray, code: np.ndarray, n_items: int):
    if item.size == 0:
        return np.zeros(n_items, dtype=np.int64)
    pair = np.unique(item * (1 << 32) + code)
    return np.bincount((pair >> 32).astype(np.int64), minlength=n_items)


# ==================================================== stage 7 — data reliability

SYSTEM_SHAPE_FEATURES = ("zero_input_share", "noop_share", "mean_inputs",
                         "mean_outputs", "cross_system_output_share",
                         "empty_id_share", "no_machine_share")


def _weighted_median(x: np.ndarray, w: np.ndarray) -> float:
    if x.size == 0:
        return 0.0
    order = np.argsort(x, kind="stable")
    xs, ws = x[order], w[order]
    cum = np.cumsum(ws)
    if cum[-1] <= 0:
        return float(np.median(xs))
    return float(xs[int(np.searchsorted(cum, cum[-1] / 2.0))])


def field_ceiling(g: PackGraph, con_stats: dict) -> tuple:
    """How much of the exported economy is actually *there*.

    Two schema fields carry economic information that this layer would use if
    it existed: ``chance`` (byproducts and returned catalysts) and ``value``
    (time / energy).  A field whose corpus distribution is degenerate -- one
    distinct value, or no rows at all -- has been measured, not assumed, and
    costs every recipe the same fraction of its trust.
    """
    checked = {"chance": con_stats["chance_distinct"] > 1,
               "value": con_stats["value_rows"] > 0}
    degenerate = sum(1 for ok in checked.values() if not ok)
    return 1.0 - MAX_PENALTY * degenerate / len(checked), checked


class Reliability:
    __slots__ = ("recipe", "flag_factor", "system_factor", "ceiling",
                 "flag_weight", "system_rows", "checked", "stats")


def compute_reliability(g: PackGraph, inc: Incidence, sh: Shape,
                        cache_dir: Path) -> Reliability:
    rel = Reliability()
    con_stats = _content_field_stats(cache_dir, g.slug)
    rel.ceiling, rel.checked = field_ceiling(g, con_stats)

    # ---- flag weights: rarer flag == more surprising == heavier penalty
    fl_sizes = np.diff(g.flags_ptr)
    frec = np.repeat(np.arange(g.n_recipes, dtype=np.int64), fl_sizes)
    kept_flag = g.kept[frec] if frec.size else np.zeros(0, dtype=bool)
    n_kept = max(int(g.kept.sum()), 1)
    penalty = np.zeros(g.n_recipes)
    rel.flag_weight = {}
    if g.flags_v.size and kept_flag.any():
        counts = np.bincount(g.flags_c[kept_flag], minlength=g.flags_v.size)
        share = np.where(counts > 0, counts / n_kept, 1.0)
        surprisal = np.where(counts > 0, -np.log2(np.clip(share, 1e-12, 1.0)), 0.0)
        total = surprisal.sum()
        weight = surprisal / total if total > 0 else surprisal
        rel.flag_weight = {str(g.flags_v[i]): float(weight[i])
                           for i in range(g.flags_v.size) if counts[i] > 0}
        np.add.at(penalty, frec[kept_flag], weight[g.flags_c[kept_flag]])
    rel.flag_factor = 1.0 - np.minimum(penalty, 1.0) * MAX_PENALTY

    # ---- per-system shape
    n_sys = g.system_v.size
    sysc = g.system_c
    counts = np.bincount(sysc[g.kept], minlength=n_sys).astype(np.float64)
    safe = np.maximum(counts, 1.0)
    empty_id = np.array([len(str(x)) == 0 for x in
                         g.recipe_id.to_numpy(zero_copy_only=False)])
    no_machine = inc.n_machines == 0
    feats = {
        "zero_input_share": np.bincount(sysc[g.kept & sh.zero_concrete_inputs],
                                        minlength=n_sys) / safe,
        "noop_share": np.bincount(sysc[g.kept & sh.inputs_equal_outputs],
                                  minlength=n_sys) / safe,
        "mean_inputs": np.bincount(sysc[g.kept], weights=g.n_inputs[g.kept].astype(float),
                                   minlength=n_sys) / safe,
        "mean_outputs": np.bincount(sysc[g.kept], weights=g.n_outputs[g.kept].astype(float),
                                    minlength=n_sys) / safe,
        "cross_system_output_share": _cross_system_share(g, sysc, n_sys),
        "empty_id_share": np.bincount(sysc[g.kept & empty_id], minlength=n_sys) / safe,
        "no_machine_share": np.bincount(sysc[g.kept & no_machine], minlength=n_sys) / safe,
    }
    live = counts > 0
    atyp = np.zeros(n_sys)
    for name in SYSTEM_SHAPE_FEATURES:
        col = feats[name]
        med = _weighted_median(col[live], counts[live])
        mad = _weighted_median(np.abs(col[live] - med), counts[live])
        scale = mad if mad > 1e-9 else (np.std(col[live]) or 1.0)
        z = np.abs(col - med) / scale
        atyp += np.clip(z / ATYPICAL_SCALE, 0.0, 1.0)
    atyp /= len(SYSTEM_SHAPE_FEATURES)
    sys_factor = 1.0 - MAX_PENALTY * atyp

    rel.system_factor = sys_factor[sysc]
    rel.recipe = rel.ceiling * rel.flag_factor * rel.system_factor
    rel.recipe[~g.kept] = 0.0

    mean_rel = np.bincount(sysc[g.kept], weights=rel.recipe[g.kept],
                           minlength=n_sys) / safe
    rel.system_rows = []
    for i in np.flatnonzero(live).tolist():
        row = {"system": str(g.system_v[i]), "n_recipes": int(counts[i]),
               "atypicality": float(atyp[i]), "system_factor": float(sys_factor[i]),
               "mean_reliability": float(mean_rel[i])}
        row.update({k: float(feats[k][i]) for k in SYSTEM_SHAPE_FEATURES})
        rel.system_rows.append(row)
    rel.system_rows.sort(key=lambda r: (-r["n_recipes"], r["system"]))

    live_rel = rel.recipe[g.kept]
    rel.stats = {
        "ceiling": round(rel.ceiling, 3),
        "degenerate_fields": sorted(k for k, ok in rel.checked.items() if not ok),
        "systems": int(live.sum()),
        "min_reliability": round(float(live_rel.min()), 4) if live_rel.size else 0.0,
        "mean_reliability": round(float(live_rel.mean()), 4) if live_rel.size else 0.0,
        "max_reliability": round(float(live_rel.max()), 4) if live_rel.size else 0.0,
        "flags_seen": len(rel.flag_weight),
    }
    return rel


def _cross_system_share(g: PackGraph, sysc: np.ndarray, n_sys: int):
    """Share of a system's produced items that some other system also makes."""
    if g.out_rec.size == 0:
        return np.zeros(n_sys)
    pair = np.unique(g.out_item * (1 << 32) + sysc[g.out_rec])
    item = (pair >> 32).astype(np.int64)
    n_sys_of_item = np.bincount(item, minlength=g.n_items)
    shared = n_sys_of_item[g.out_item] > 1
    per_sys = np.bincount(sysc[g.out_rec][shared], minlength=n_sys)
    total = np.bincount(sysc[g.out_rec], minlength=n_sys)
    return per_sys / np.maximum(total, 1)


def _content_field_stats(cache_dir: Path, slug: str) -> dict:
    tbl = pq.read_table(cache_dir / slug / "contents.parquet",
                        columns=["kind", "chance", "value"])
    chance = tbl.column("chance").to_numpy()
    kind_c, kind_v = _dict_codes(tbl.column("kind"))
    return {"chance_distinct": int(np.unique(chance).size),
            "value_rows": int(_mask_of(kind_c, kind_v, ("value",)).sum()),
            "rows": tbl.num_rows}


# ============================================================ stage 8 — writing


def write_table(path: Path, schema, columns: dict) -> int:
    """Deterministic parquet from column arrays (one row group, snappy)."""
    path.parent.mkdir(parents=True, exist_ok=True)
    arrays = []
    for field in schema:
        col = columns[field.name]
        if isinstance(col, np.ndarray) and col.dtype == object:
            col = [None if v is None else str(v) for v in col.tolist()]
        elif isinstance(col, np.ndarray):
            col = col.tolist()
        arrays.append(pa.array(col, type=field.type))
    table = pa.Table.from_arrays(arrays, schema=schema)
    pq.write_table(table, path, compression="snappy")
    return table.num_rows


class PackResult:
    __slots__ = ("slug", "g", "sh", "pl", "ti", "inc", "vu", "vf", "lf", "rl",
                 "timings", "meta")


def write_pack(res: PackResult, out_dir: Path) -> dict:
    g, sh, pl, ti, inc = res.g, res.sh, res.pl, res.ti, res.inc
    vu, vf, lf, rl = res.vu, res.vf, res.lf, res.rl
    odir = out_dir / g.slug / "economy"
    counts = {}

    kept = g.keep_idx
    role_names = np.array(ROLE_NAMES, dtype=object)
    counts["recipe_shape"] = write_table(odir / "recipe_shape.parquet",
                                          RECIPE_SHAPE_SCHEMA, {
        "recipe_idx": kept.astype(np.int32),
        "recipe_id": g.recipe_id.to_numpy(zero_copy_only=False)[kept],
        "system": g.system_v[g.system_c[kept]],
        "shape_role": role_names[sh.role[kept]],
        "material_set_preserved": sh.material_set_preserved[kept],
        "out_subset_in": sh.out_subset_in[kept],
        "count_ratio": sh.count_ratio[kept],
        "mat_log_ratio_max": sh.mat_log_ratio_max[kept],
        "is_round_trip": sh.is_round_trip[kept],
        "zero_concrete_inputs": sh.zero_concrete_inputs[kept],
        "inputs_equal_outputs": sh.inputs_equal_outputs[kept],
        "self_dependent": sh.self_dependent[kept],
        "n_input_slots": g.n_inputs[kept].astype(np.int32),
        "n_output_slots": g.n_outputs[kept].astype(np.int32),
        "n_in_materials": sh.n_in_materials[kept].astype(np.int32),
        "n_out_materials": sh.n_out_materials[kept].astype(np.int32),
        "tier": ti.recipe_tier[kept],
        "machine_tier": ti.recipe_machine_tier[kept],
        "max_input_tier": ti.max_input_tier[kept],
        "reverse_tier": ti.reverse_tier[kept],
        "value_in": vf.in_value[kept],
        "value_out": vf.out_value[kept],
        "value_ratio": vf.value_ratio[kept],
        "unique_producer": vf.unique_producer[kept],
        "reliability": rl.recipe[kept],
    })

    pools, strong_id, weak_id = pool_rows(g, sh, pl)
    is_leaf = inc.n_producers == 0
    acq = np.full(g.n_items, np.nan)
    acq[lf.idx] = lf.acquirability
    counts["item_values"] = write_table(odir / "item_values.parquet",
                                        ITEM_VALUE_SCHEMA, {
        "base_id": g.items,
        "material_key": sh.mat_names[sh.item_mat],
        "form_class": lf.form_class,
        "value_uniform": vu.item_value,
        "value_fitted": vf.item_value,
        "tier": ti.item_tier,
        "is_leaf": is_leaf,
        "pool_id": strong_id,
        "weak_pool_id": weak_id,
        "acquirability": acq,
        "data_gap": lf.data_gap,
        "producer_packs": lf.producer_packs.astype(np.int32),
        "n_producers": inc.n_producers.astype(np.int32),
        "n_consumers": pl.n_consumers.astype(np.int32),
        "value_source": vf.value_source,
    })

    cols = {f.name: [r[f.name] for r in pools] for f in POOL_SCHEMA}
    counts["pools"] = write_table(odir / "pools.parquet", POOL_SCHEMA, cols)

    same = (pl.strong[pl.src] == pl.strong[pl.dst]) if pl.src.size else \
        np.zeros(0, dtype=bool)
    order = np.lexsort((pl.edge_rec[same], pl.dst[same], pl.src[same])) \
        if same.any() else np.zeros(0, dtype=np.int64)
    counts["pool_edges"] = write_table(odir / "pool_edges.parquet",
                                       POOL_EDGE_SCHEMA, {
        "pool_id": strong_id[pl.src[same][order]] if same.any() else [],
        "src": g.items[pl.src[same][order]] if same.any() else [],
        "dst": g.items[pl.dst[same][order]] if same.any() else [],
        "unit_cost": pl.unit_cost[same][order] if same.any() else [],
        "recipe_idx": pl.edge_rec[same][order].astype(np.int32) if same.any() else [],
    })

    in_universe = np.zeros(g.n_items, dtype=bool)
    if g.out_item.size:
        in_universe[g.out_item] = True
    plain_in = g.in_node[g.in_node < g.n_items]
    if plain_in.size:
        in_universe[plain_in] = True
    if g.or_mem.size:
        in_universe[g.or_mem] = True
    counts["machines"] = write_table(odir / "machines.parquet", MACHINE_SCHEMA, {
        "machine_id": g.items[inc.mach_items],
        "tier": ti.machine_tier,
        "n_recipes": ti.machine_recipes.astype(np.int32),
        "n_producers": ti.machine_producers.astype(np.int32),
        "machine_unbuildable": ti.machine_unbuildable,
        "in_content_universe": in_universe[inc.mach_items],
    })

    leaf_cols = {"base_id": g.items[lf.idx],
                 "material_key": sh.mat_names[sh.item_mat[lf.idx]],
                 "form_class": lf.form_class[lf.idx],
                 "containing_packs": lf.containing_packs[lf.idx].astype(np.int32),
                 "acquirability": lf.acquirability,
                 "seed_value": lf.seed_value,
                 "data_gap": lf.data_gap[lf.idx],
                 "world_resource": lf.world_resource[lf.idx]}
    for name in LEAF_FEATURES:
        leaf_cols[name] = lf.raw[name][lf.idx]
    counts["leaf_features"] = write_table(odir / "leaf_features.parquet",
                                          LEAF_SCHEMA, leaf_cols)

    counts["system_reliability"] = write_table(
        odir / "system_reliability.parquet", SYSTEM_SCHEMA,
        {f.name: [r[f.name] for r in rl.system_rows] for f in SYSTEM_SCHEMA})

    res.meta = {
        "pack": g.slug,
        "rows": counts,
        "timings_seconds": {k: round(v, 3) for k, v in res.timings.items()},
        "peak_rss_mb": round(peak_rss_mb(), 1),
        "graph": g.stats, "shape": sh.stats, "pools": pl.stats,
        "tiers": ti.stats, "values_uniform": vu.stats, "values_fitted": vf.stats,
        "leaves": lf.stats, "reliability": rl.stats,
        "conversion_band": [sh.band[0], sh.band[1]],
        "reverse_tier_margin": ti.reverse_margin,
        "acquirability_loadings": lf.loadings,
        "flag_weights": rl.flag_weight,
        "field_checks": rl.checked,
    }
    return counts


def write_cross_pack(cp: CrossPack, results: list, out_dir: Path,
                     wall: float) -> dict:
    odir = out_dir / "economy"
    gap, k = data_gap_rule(cp.containing, cp.producing)
    write_table(odir / "leaf_stats.parquet", LEAF_STATS_SCHEMA, {
        "base_id": cp.ids,
        "containing_packs": cp.containing.astype(np.int32),
        "producer_packs": cp.producing.astype(np.int32),
        "leaf_packs": cp.leafing.astype(np.int32),
        "leaf_fraction": cp.leafing / np.maximum(cp.containing, 1),
        "renewable": cp.renewable,
        "every_pack_leaf": cp.producing == 0,
    })
    summary = {
        "packs": [r.meta for r in results],
        "cross_pack": cp.stats,
        "repeated_exports": {h: sorted(s.slug for s in v)
                             for h, v in sorted(cp.groups.items()) if len(v) > 1},
        "wall_seconds": round(wall, 2),
        "peak_rss_mb": round(peak_rss_mb(), 1),
    }
    write_json(odir / "summary.json", summary)
    return summary


# ==================================================================== driver

STAGES = ("graph", "shape", "pools", "tiers", "reliability", "values",
          "leaves", "gap", "write")


def pack_slugs(cache_dir: Path, spec: str) -> list[str]:
    known = sorted(read_json(cache_dir / "packs.json").keys())
    if spec == "all":
        return known
    wanted = [s.strip() for s in spec.split(",") if s.strip()]
    missing = [s for s in wanted if s not in known]
    if missing:
        raise SystemExit(f"unknown pack slug(s): {missing}")
    return wanted


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Phase 0b — the economy layer")
    ap.add_argument("--cache-dir", default="cache", type=Path)
    ap.add_argument("--out-dir", default="out", type=Path)
    ap.add_argument("--packs", default="fom")
    ap.add_argument("--stage", default="all", choices=("all",) + STAGES)
    ap.add_argument("--max-or-expand", type=int, default=256,
                    help="performance guard on OR-node pair expansion")
    ap.add_argument("--net-catalysts", action="store_true",
                    help="catalyst hook: net an entry that appears on both "
                         "sides of one recipe off both sides before solving "
                         "values.  Off by default -- this corpus exports no "
                         "chance-0 catalyst, so it would only fire on shapes "
                         "(the anvil rows) that are export artifacts")
    ap.add_argument("--seed-spread", type=float, default=SEED_SPREAD,
                    help="K: leaf seeds live in [1, K]; 1 = uniform seeds")
    ap.add_argument("--step-cost", type=float, default=STEP_COST,
                    help="cost of one recipe step, as a fraction of the "
                         "median leaf seed (scaled by 1 + recipe tier)")
    ap.add_argument("--cross-all", action="store_true",
                    help="scan every cached pack for the cross-pack stats even "
                         "when only a few packs are being processed")
    ap.add_argument("--quiet", action="store_true")
    args = ap.parse_args(argv)

    slugs = pack_slugs(args.cache_dir, args.packs)
    upto = len(STAGES) - 1 if args.stage == "all" else STAGES.index(args.stage)
    wall0 = time.time()

    cp = None
    if upto >= STAGES.index("leaves"):
        t = time.time()
        scan_slugs = pack_slugs(args.cache_dir, "all") if args.cross_all else slugs
        cp = cross_pack([scan_pack(args.cache_dir, args.out_dir, s_,
                                   args.max_or_expand) for s_ in scan_slugs])
        print(f"[cross] scan        {time.time() - t:6.2f}s  " +
              "  ".join(f"{k}={v}" for k, v in cp.stats.items()))

    results = []
    for slug in slugs:
        res = PackResult()
        res.slug = slug
        res.timings = {}
        report = []

        def step(name, fn):
            t = time.time()
            value = fn()
            res.timings[name] = time.time() - t
            return value

        res.g = step("graph", lambda: build_graph(args.cache_dir, args.out_dir,
                                                  slug, args.max_or_expand))
        report.append(("graph", res.g.stats))
        if upto >= STAGES.index("shape"):
            res.sh = step("shape", lambda: compute_shape(res.g, args.out_dir,
                                                         args.max_or_expand))
            report.append(("shape", res.sh.stats))
        if upto >= STAGES.index("pools"):
            res.pl = step("pools", lambda: compute_pools(res.g, res.sh))
            report.append(("pools", res.pl.stats))
        if upto >= STAGES.index("tiers"):
            res.inc = step("incidence", lambda: incidence(res.g))
            res.ti = step("tiers", lambda: solve_tiers(res.g, res.inc))
            report.append(("tiers", res.ti.stats))
        if upto >= STAGES.index("reliability"):
            res.rl = step("reliability", lambda: compute_reliability(
                res.g, res.inc, res.sh, args.cache_dir))
            report.append(("reliability", res.rl.stats))
        if upto >= STAGES.index("values"):
            res.vu = step("values", lambda: compute_values(
                res.g, res.inc, res.sh, res.ti, np.ones(res.g.n_items),
                reliability=res.rl.recipe, net=args.net_catalysts,
                step_cost=args.step_cost))
            report.append(("values", res.vu.stats))
        if upto >= STAGES.index("leaves"):
            res.lf = step("leaves", lambda: compute_leaves(
                res.g, res.inc, res.sh, res.pl, res.ti, cp, args.out_dir,
                args.seed_spread))
            report.append(("leaves", res.lf.stats))
            # second solve: the same fixpoint, seeded from the fitted
            # acquirability instead of a uniform 1.0
            res.vf = step("values_fitted", lambda: compute_values(
                res.g, res.inc, res.sh, res.ti, res.lf.seed_all,
                ring_seed=res.lf.seed_all, reliability=res.rl.recipe,
                net=args.net_catalysts, step_cost=args.step_cost))
            report.append(("values_fitted", res.vf.stats))
        if upto >= STAGES.index("write"):
            counts = step("write", lambda: write_pack(res, args.out_dir))
            # the meta carries the write timing, so it is dumped once the
            # timer around write_pack has closed
            res.meta["timings_seconds"] = {k: round(v, 3)
                                           for k, v in res.timings.items()}
            write_json(args.out_dir / slug / "economy" / "economy-meta.json",
                       res.meta)
            report.append(("write", counts))
        results.append(res)

        if not args.quiet:
            for name, stats in report:
                secs = res.timings.get(name, 0.0)
                print(f"[{slug}] {name:<13} {secs:6.2f}s  " +
                      "  ".join(f"{k}={v}" for k, v in stats.items()))
        total = sum(res.timings.values())
        print(f"[{slug}] TOTAL         {total:6.2f}s  "
              f"rss={peak_rss_mb():.0f}MB")

    if upto >= STAGES.index("write") and cp is not None:
        summary = write_cross_pack(cp, results, args.out_dir,
                                   time.time() - wall0)
        print(f"[cross] wrote out/economy/  packs={len(results)}  "
              f"wall={summary['wall_seconds']}s  rss={summary['peak_rss_mb']}MB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
