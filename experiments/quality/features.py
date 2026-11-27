"""Phase 0c-1 — the feature layer.

One row per ``(pack, template_id)``, float32, grouped by the plan's six
layers, plus the per-recipe subset the pair model needs:

    out/features/template_features.parquet
    out/features/recipe_features.parquet
    out/features/feature-catalog.json
    features-meta.json

Hard rules, unchanged from Phase 0a/0b:

1. **No hardcoded mod / item / material / colour / system-name vocabulary.**
   Nothing here branches on a registry name.  The only names written down are
   the export schema's own field names and field values (``kind``, ``stack``,
   ``tag``, ``any``, ``typedAny``, ``value``, ``complex``, ``item``, ``in``,
   ``out``, ``matchStrategy``, …) and the Phase 0a/0b column names.  Every
   *value* vocabulary that turns into a one-hot — shape roles, match
   strategies — is read off the data at run time and recorded in
   ``feature-catalog.json``, so the module never states what those values are.
   ``tests/test_phase0c1.py::test_no_rule_vocabulary`` enforces it.
2. **No feature is a rule.**  L6 hashes the *residual* lexical tokens — the id
   tokens the ontology could not abstract — into anonymous buckets.  That is a
   feature the model weighs, not a pattern this layer asserts; the hash is
   documented and the namespace is deliberately excluded from it.
3. **Determinism.**  Same inputs => byte-identical parquet.  Fixed schema
   order, fixed hash, stable sorts, no timestamps in the payload.
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
import math
import time
from pathlib import Path

import numpy as np
import pyarrow as pa
import pyarrow.parquet as pq

from common import peak_rss_mb, read_json, write_json, pack_group_map, split_id

# --------------------------------------------------------------- the schema
#
# Export-schema field names and field values (Phase 0a contract), plus the
# Phase 0a/0b column names.  These are the data's own words, not a vocabulary.
SIDE_IN, SIDE_OUT = "in", "out"
KINDS = ("stack", "tag", "any", "typedAny", "value", "complex")
#: the eight ``|``-separated fields of an abstracted content in a template
#: signature (ontology.py::abstract_content).
SIG_FIELDS = ("kind", "contentType", "material", "form", "modifiers",
              "variant", "amount", "chance")
#: class-id prefixes the ontology mints.  ``t:`` marks a token that belongs to
#: no induced class, i.e. a *raw* slot; everything else is abstracted.
RAW_PREFIX = "t:"
STAGES = ("l1", "economy", "resources", "intent", "lexical", "recipes", "write")

#: L6 bucket count.  32 is enough to separate the frequent residual tokens
#: without giving the model one column per rare token.
HASH_BUCKETS = 32


def log(msg: str) -> None:
    print(msg, flush=True)


def catalog_add(catalog: list, name: str, layer: str, description: str,
                aggregation: str) -> str:
    catalog.append({"name": name, "layer": layer, "description": description,
                    "aggregation": aggregation})
    return name


# =================================================================== stage L1


def parse_content(field: str) -> dict:
    """Split one abstracted content of a template signature into its slots."""
    parts = field.split("|")
    parts += [""] * (len(SIG_FIELDS) - len(parts))
    out = dict(zip(SIG_FIELDS, parts))
    out["modifiers"] = [m for m in out["modifiers"].split(",") if m]
    return out


def as_float(text: str) -> float:
    try:
        return float(text)
    except (TypeError, ValueError):
        return float("nan")


def abstract_share(slots: list) -> float:
    """Share of filled material/form slots that resolved to an induced class.

    A slot rendered ``t:<token>`` is a raw token the ontology could not place;
    anything else (``cls:``/``var:``/``impl:``) is abstracted.  This is the
    single most direct measure of how much of a template the ontology
    understood.
    """
    filled = [s for s in slots if s]
    if not filled:
        return float("nan")
    return sum(1.0 for s in filled if not s.startswith(RAW_PREFIX)) / len(filled)


def l1_row(signature: str) -> dict:
    """L1 — ontology shape, read entirely off the template signature."""
    sig = json.loads(signature)
    ins = [parse_content(f) for f in sig.get(SIDE_IN, [])]
    outs = [parse_content(f) for f in sig.get(SIDE_OUT, [])]
    machines = [m.split("|") for m in sig.get("mach", [])]
    rid = sig.get("rid", [])
    pat = sig.get("pat", [])

    def materials(rows):
        return {r["material"] for r in rows if r["material"]}

    in_mats, out_mats = materials(ins), materials(outs)
    union = in_mats | out_mats
    variants = {r["variant"] for r in ins + outs if r["variant"]}
    amounts_in = [as_float(r["amount"]) for r in ins]
    amounts_out = [as_float(r["amount"]) for r in outs]
    chances_out = [as_float(r["chance"]) for r in outs]

    row = {
        "l1_n_inputs": float(len(ins)),
        "l1_n_outputs": float(len(outs)),
        "l1_n_in_materials": float(len(in_mats)),
        "l1_n_out_materials": float(len(out_mats)),
        "l1_material_overlap": (len(in_mats & out_mats) / len(union)
                                if union else float("nan")),
        "l1_in_abstract_share": abstract_share(
            [r["material"] for r in ins] + [r["form"] for r in ins]),
        "l1_out_abstract_share": abstract_share(
            [r["material"] for r in outs] + [r["form"] for r in outs]),
        "l1_n_variant_classes": float(len(variants)),
        "l1_has_variant_class": float(bool(variants)),
        "l1_n_modifier_slots": float(sum(len(r["modifiers"]) for r in ins + outs)),
        "l1_n_rid_tokens": float(len(rid)),
        "l1_in_slot_repetition": (1.0 - len({f for f in sig.get(SIDE_IN, [])})
                                  / len(ins)) if ins else float("nan"),
        "l1_amount_in_sum": float(np.nansum(amounts_in)) if ins else float("nan"),
        "l1_amount_out_sum": float(np.nansum(amounts_out)) if outs else float("nan"),
        "l1_amount_out_max": float(np.nanmax(amounts_out)) if outs else float("nan"),
        "l1_chance_out_min": float(np.nanmin(chances_out)) if outs else float("nan"),
        "l1_n_machines": float(len(machines)),
        "l1_machine_present": float(bool(machines)),
        "l1_machine_abstract_share": abstract_share(
            [part for m in machines for part in m[:2]]),
    }
    for kind in KINDS:
        row[f"l1_in_kind_{kind}_share"] = (
            sum(1.0 for r in ins if r["kind"] == kind) / len(ins)
            if ins else float("nan"))
        row[f"l1_out_kind_{kind}_present"] = float(
            any(r["kind"] == kind for r in outs))
    row["_rid"] = list(rid)
    row["_strategies"] = collections.Counter(
        p.split(":", 2)[2] if p.count(":") >= 2 else "" for p in pat)
    row["_n_pat"] = float(len(pat))
    return row


def stage_l1(ctx: dict) -> None:
    t0 = time.time()
    for slug in ctx["packs"]:
        table = pq.read_table(ctx["out_dir"] / slug / "templates.parquet")
        tids = table.column("template_id").to_pylist()
        rows = [l1_row(s) for s in table.column("signature").to_pylist()]
        ctx["templates"][slug] = tids
        ctx["systems"][slug] = table.column("system").to_pylist()
        ctx["sizes"][slug] = np.asarray(table.column("size").to_pylist(),
                                        dtype=np.float64)
        names = [k for k in rows[0] if not k.startswith("_")] if rows else []
        feat = ctx["features"].setdefault(slug, {})
        for name in names:
            feat[name] = np.asarray([r[name] for r in rows], dtype=np.float64)
        feat["l1_n_members"] = ctx["sizes"][slug].copy()
        ctx["strategies"][slug] = ([r["_strategies"] for r in rows],
                                   np.asarray([r["_n_pat"] for r in rows],
                                              dtype=np.float64))
        ctx["residual"][slug] = [r["_rid"] for r in rows]
    n = sum(len(v) for v in ctx["templates"].values())
    log(f"[l1] {n:,} templates; "
        f"{len(ctx['features'][ctx['packs'][0]])} L1 columns; "
        f"{time.time() - t0:.1f}s")


# ================================================== aggregation helpers


def group_mean(values: np.ndarray, pos: np.ndarray, n: int) -> np.ndarray:
    """Mean of ``values`` per group, NaN for an empty group."""
    ok = np.isfinite(values)
    total = np.bincount(pos[ok], weights=values[ok].astype(np.float64),
                        minlength=n)
    count = np.bincount(pos[ok], minlength=n)
    out = np.full(n, np.nan)
    np.divide(total, count, out=out, where=count > 0)
    return out


def group_max(values: np.ndarray, pos: np.ndarray, n: int) -> np.ndarray:
    ok = np.isfinite(values)
    out = np.full(n, -np.inf)
    np.maximum.at(out, pos[ok], values[ok].astype(np.float64))
    out[np.isneginf(out)] = np.nan
    return out


def group_min(values: np.ndarray, pos: np.ndarray, n: int) -> np.ndarray:
    ok = np.isfinite(values)
    out = np.full(n, np.inf)
    np.minimum.at(out, pos[ok], values[ok].astype(np.float64))
    out[np.isposinf(out)] = np.nan
    return out


def centred_log(values: np.ndarray) -> np.ndarray:
    """``log(v) - median(log(v))`` over the pack.

    Phase 0b values are comparable *within* a pack only (the step cost
    compounds through chain depth, so one pack reaches 10^17 and another
    10^4).  Centring on the pack's own median median is what makes the column
    mean the same thing in every pack.
    """
    out = np.full(values.shape, np.nan)
    ok = np.isfinite(values) & (values > 0)
    if not ok.any():
        return out
    logs = np.log(values[ok])
    out[ok] = logs - np.median(logs)
    return out


def column(table: pa.Table, name: str, dtype=np.float64) -> np.ndarray:
    return np.asarray(table.column(name).to_numpy(zero_copy_only=False),
                      dtype=dtype)


# ============================================================ stages L2 + L3


def template_positions(ctx: dict, slug: str):
    """``(tpos_of_recipe, n_templates)``; ``-1`` for a recipe with no template."""
    tids = ctx["templates"][slug]
    pos_of = {t: i for i, t in enumerate(tids)}
    table = pq.read_table(ctx["out_dir"] / slug / "recipe_template.parquet")
    idx = column(table, "recipe_idx", np.int64)
    tpl = table.column("template_id").to_pylist()
    n_recipes = ctx["n_recipes"][slug]
    out = np.full(n_recipes, -1, dtype=np.int64)
    out[idx] = np.asarray([pos_of.get(t, -1) for t in tpl], dtype=np.int64)
    return out, len(tids)


def machine_unbuildable(ctx: dict, slug: str) -> np.ndarray:
    """Per recipe: does any of its machines have no in-pack producer?"""
    machines = pq.read_table(ctx["out_dir"] / slug / "economy" /
                             "machines.parquet")
    flag = dict(zip(machines.column("machine_id").to_pylist(),
                    machines.column("machine_unbuildable").to_pylist()))
    lists = pq.read_table(ctx["cache_dir"] / slug / "recipes.parquet",
                          columns=["machines"]).column("machines").to_pylist()
    return np.asarray([float(any(flag.get(m, True) for m in ms)) if ms else 0.0
                       for ms in lists])


def stage_economy(ctx: dict) -> None:
    t0 = time.time()
    roles_seen = set()
    for slug in ctx["packs"]:
        shape = pq.read_table(ctx["out_dir"] / slug / "economy" /
                              "recipe_shape.parquet")
        tpos_all, n = template_positions(ctx, slug)
        ridx = column(shape, "recipe_idx", np.int64)
        pos = tpos_all[ridx]
        keep = pos >= 0
        pos = pos[keep]
        feat = ctx["features"][slug]

        def add(name, values, how="mean"):
            fn = {"mean": group_mean, "max": group_max, "min": group_min}[how]
            feat[name] = fn(np.asarray(values, dtype=np.float64)[keep], pos, n)

        roles = np.asarray(shape.column("shape_role").to_pylist())[keep]
        for role in sorted(set(roles.tolist())):
            roles_seen.add(role)
            feat[f"l2_role_{role}_share"] = group_mean(
                (roles == role).astype(np.float64), pos, n)

        for name in ("material_set_preserved", "out_subset_in", "is_round_trip",
                     "zero_concrete_inputs", "inputs_equal_outputs",
                     "self_dependent", "unique_producer"):
            add(f"l2_{name}_share", column(shape, name))
        add("l2_count_ratio_mean", column(shape, "count_ratio"))
        add("l2_count_ratio_max", column(shape, "count_ratio"), "max")
        add("l2_mat_log_ratio_max_mean", column(shape, "mat_log_ratio_max"))
        add("l2_reliability_mean", column(shape, "reliability"))
        add("l2_reliability_min", column(shape, "reliability"), "min")

        value_in = centred_log(column(shape, "value_in"))
        value_out = centred_log(column(shape, "value_out"))
        ratio = column(shape, "value_ratio")
        log_ratio = np.where(np.isfinite(ratio) & (ratio > 0),
                             np.log(np.maximum(ratio, 1e-30)), np.nan)
        add("l2_log_value_in_mean", value_in)
        add("l2_log_value_out_mean", value_out)
        add("l2_log_value_ratio_mean", log_ratio)
        add("l2_log_value_ratio_min", log_ratio, "min")

        tier = column(shape, "tier")
        max_in = column(shape, "max_input_tier")
        add("l3_tier_mean", tier)
        add("l3_tier_max", tier, "max")
        add("l3_machine_tier_mean", column(shape, "machine_tier"))
        add("l3_max_input_tier_mean", max_in)
        add("l3_reverse_tier_share", column(shape, "reverse_tier"))
        add("l3_tier_jump_mean", tier - max_in)
        add("l3_tier_jump_max", tier - max_in, "max")
        unbuildable = machine_unbuildable(ctx, slug)
        add("l3_machine_unbuildable_share", unbuildable[ridx])

        ctx["recipes"].setdefault(slug, {}).update({
            "recipe_idx": ridx, "tpos": tpos_all[ridx],
            "tier": tier, "machine_tier": column(shape, "machine_tier"),
            "max_input_tier": max_in,
            "value_in": column(shape, "value_in"),
            "log_value_in": centred_log(column(shape, "value_in")),
            "log_value_ratio": log_ratio,
            "reliability": column(shape, "reliability"),
            "n_input_slots": column(shape, "n_input_slots"),
            "n_output_slots": column(shape, "n_output_slots"),
            "shape_role": shape.column("shape_role").to_pylist(),
            "system": shape.column("system").to_pylist(),
        })
    ctx["meta"]["shape_roles"] = sorted(roles_seen)
    n_cols = len(ctx["features"][ctx["packs"][0]])
    log(f"[economy] L2+L3 done; roles {sorted(roles_seen)}; "
        f"{n_cols} columns so far; {time.time() - t0:.1f}s")


# ==================================================================== stage L4


def item_table(ctx: dict, slug: str) -> dict:
    """Per-item economy columns, keyed by a sorted id array for searchsorted."""
    values = pq.read_table(ctx["out_dir"] / slug / "economy" /
                           "item_values.parquet")
    ids = np.asarray(values.column("base_id").to_pylist())
    order = np.argsort(ids, kind="stable")
    pools = pq.read_table(ctx["out_dir"] / slug / "economy" / "pools.parquet")
    size_of = dict(zip(pools.column("pool_id").to_pylist(),
                       pools.column("size").to_pylist()))
    pool_ids = values.column("pool_id").to_pylist()
    return {
        "ids": ids[order],
        "is_leaf": column(values, "is_leaf")[order],
        "acquirability": column(values, "acquirability")[order],
        "data_gap": column(values, "data_gap")[order],
        "tier": column(values, "tier")[order],
        "in_pool": np.asarray([1.0 if p else 0.0 for p in pool_ids])[order],
        "pool_size": np.asarray([float(size_of.get(p, 0)) for p in pool_ids],
                                dtype=np.float64)[order],
    }


def or_representatives(table: pa.Table, kinds: np.ndarray,
                       base: np.ndarray) -> dict:
    """``{tag base_id: a member id}`` for content rows whose base id is a tag.

    A ``tag`` row's ``base_id`` is the tag's own id, which is not an item and
    has no economy row; its members are.  The map is built from the *first*
    row of each distinct tag, and the representative is the lexicographically
    smallest member — the same tie-break Phase 0a uses for ``any`` — so it is
    stable across exports whose member order differs.
    """
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


def stage_resources(ctx: dict) -> None:
    t0 = time.time()
    for slug in ctx["packs"]:
        items = item_table(ctx, slug)
        contents = pq.read_table(ctx["cache_dir"] / slug / "contents.parquet",
                                 columns=["recipe_idx", "side", "kind",
                                          "base_id", "members"])
        side = np.asarray(contents.column("side").to_pylist())
        mask = side == SIDE_IN
        rows = np.flatnonzero(mask)
        sub = contents.take(pa.array(rows))
        kinds = np.asarray(sub.column("kind").to_pylist())
        base = np.asarray(sub.column("base_id").to_pylist())
        rep = or_representatives(sub, kinds, base)
        if rep:
            base = np.asarray([rep.get(b, b) for b in base])
        ridx = column(sub, "recipe_idx", np.int64)

        slot = np.searchsorted(items["ids"], base)
        slot = np.clip(slot, 0, max(items["ids"].size - 1, 0))
        known = (items["ids"].size > 0) & (items["ids"][slot] == base)
        n_recipes = ctx["n_recipes"][slug]

        def per_recipe(values, where=None):
            sel = known if where is None else (known & where)
            total = np.bincount(ridx[sel], weights=values[sel],
                                minlength=n_recipes)
            count = np.bincount(ridx[sel], minlength=n_recipes)
            return total, count

        is_leaf = items["is_leaf"][slot]
        leafish = is_leaf > 0
        n_inputs = np.bincount(ridx, minlength=n_recipes).astype(np.float64)
        leaf_total, leaf_count = per_recipe(np.ones(ridx.size), leafish)
        acq = items["acquirability"][slot]
        acq_sum, _ = per_recipe(np.nan_to_num(acq), leafish)
        acq_min = np.full(n_recipes, np.inf)
        sel = known & leafish & np.isfinite(acq)
        np.minimum.at(acq_min, ridx[sel], acq[sel])
        acq_min[np.isposinf(acq_min)] = np.nan
        gap_sum, _ = per_recipe(items["data_gap"][slot], leafish)
        pool_sum, pool_n = per_recipe(items["in_pool"][slot])
        pool_size = items["pool_size"][slot]
        pool_size_sum, _ = per_recipe(pool_size, pool_size > 0)
        pool_size_n = np.bincount(ridx[known & (pool_size > 0)],
                                  minlength=n_recipes)
        pool_size_max = np.zeros(n_recipes)
        np.maximum.at(pool_size_max, ridx[known], pool_size[known])
        in_tier_max = np.zeros(n_recipes)
        np.maximum.at(in_tier_max, ridx[known], items["tier"][slot][known])

        with np.errstate(invalid="ignore", divide="ignore"):
            per = {
                "l4_n_leaf_inputs": leaf_total,
                "l4_leaf_input_share": np.where(n_inputs > 0,
                                                leaf_total / n_inputs, np.nan),
                "l4_leaf_acq_mean": np.where(leaf_count > 0,
                                             acq_sum / leaf_count, np.nan),
                "l4_leaf_acq_min": acq_min,
                "l4_data_gap_input_share": np.where(leaf_count > 0,
                                                    gap_sum / leaf_count, np.nan),
                "l4_pool_input_share": np.where(n_inputs > 0,
                                                pool_sum / n_inputs, np.nan),
                "l4_pool_size_mean": np.where(pool_size_n > 0,
                                              pool_size_sum / pool_size_n, np.nan),
                "l4_pool_size_max": pool_size_max,
                "l4_unknown_input_share": np.where(
                    n_inputs > 0,
                    np.bincount(ridx[~known], minlength=n_recipes) / n_inputs,
                    np.nan),
            }
        rec = ctx["recipes"][slug]
        keep = rec["tpos"] >= 0
        pos = rec["tpos"][keep]
        n = len(ctx["templates"][slug])
        feat = ctx["features"][slug]
        for name, values in per.items():
            take = values[rec["recipe_idx"]]
            how = group_max if name.endswith("_max") else group_mean
            feat[f"{name}_mean" if how is group_mean else name] = \
                how(take[keep], pos, n)
            rec[name] = take
    n_cols = len(ctx["features"][ctx["packs"][0]])
    log(f"[resources] L4 done; {n_cols} columns so far; "
        f"{time.time() - t0:.1f}s")


# ==================================================================== stage L5
#
# "Intent" is the layer that asks whether a recipe looks like something its
# own mod meant to ship, or like a page some other system printed about it.
# All of it is relational: namespace agreement, whether the system is the one
# that carries the mod's own production, and how this producer compares with
# its *siblings* — the other producers of the same output.


def namespaces(ids: np.ndarray) -> np.ndarray:
    return np.asarray([split_id(str(i))[0] for i in ids])


def group_z(values: np.ndarray, group: np.ndarray, n_groups: int) -> np.ndarray:
    """Z-score of each row against its own group; 0 when the group is constant.

    This is the sibling-outlier definition: for one output, every producer is
    scored against the mean and population standard deviation of the other
    producers *including itself* (the unbiased leave-one-out version differs
    by a factor of n/(n-1) and is undefined at n = 2).  A group of one, or a
    group where every producer agrees, scores 0 — not NaN — because "no
    sibling disagrees with me" is information, not a missing value.
    """
    ok = np.isfinite(values)
    count = np.bincount(group[ok], minlength=n_groups)
    total = np.bincount(group[ok], weights=values[ok], minlength=n_groups)
    sq = np.bincount(group[ok], weights=values[ok] ** 2, minlength=n_groups)
    mean = np.divide(total, count, out=np.zeros(n_groups), where=count > 0)
    var = np.divide(sq, count, out=np.zeros(n_groups), where=count > 0) - mean ** 2
    std = np.sqrt(np.maximum(var, 0.0))
    out = np.zeros(values.shape)
    good = ok & (std[group] > 0)
    out[good] = (values[good] - mean[group][good]) / std[group][good]
    return out


def stage_intent(ctx: dict) -> None:
    t0 = time.time()
    for slug in ctx["packs"]:
        n_recipes = ctx["n_recipes"][slug]
        recipes = pq.read_table(ctx["cache_dir"] / slug / "recipes.parquet",
                                columns=["recipe_idx", "recipe_ns", "system",
                                         "flags"])
        recipe_ns = np.asarray(recipes.column("recipe_ns").to_pylist())
        systems = np.asarray(recipes.column("system").to_pylist())

        contents = pq.read_table(ctx["cache_dir"] / slug / "contents.parquet",
                                 columns=["recipe_idx", "side", "slot",
                                          "base_id"])
        side = np.asarray(contents.column("side").to_pylist())
        ridx = column(contents, "recipe_idx", np.int64)
        base = np.asarray(contents.column("base_id").to_pylist())
        slot = column(contents, "slot", np.int64)
        ns = namespaces(base)
        out_mask = side == SIDE_OUT
        in_mask = ~out_mask

        # primary output namespace: the first output slot of the recipe.
        first_out = np.full(n_recipes, -1, dtype=np.int64)
        order = np.flatnonzero(out_mask)[np.argsort(slot[out_mask], kind="stable")]
        first_out[ridx[order[::-1]]] = order[::-1]
        out_ns = np.where(first_out >= 0, ns[np.maximum(first_out, 0)], "")

        # a recipe with no id inherits its first output's namespace, exactly
        # as the Phase 0b leaf stage does for consumer namespaces.
        effective_ns = np.where(recipe_ns != "", recipe_ns, out_ns)

        in_ns_match = (ns == effective_ns[ridx]) & in_mask
        n_in = np.bincount(ridx[in_mask], minlength=n_recipes).astype(np.float64)
        in_same = np.bincount(ridx[in_ns_match], minlength=n_recipes).astype(np.float64)
        with np.errstate(invalid="ignore"):
            in_share = np.where(n_in > 0, in_same / n_in, np.nan)

        # majority input namespace, by count
        maj_in_ns = np.full(n_recipes, "", dtype=object)
        counter: dict = {}
        for r, value in zip(ridx[in_mask], ns[in_mask]):
            per = counter.setdefault(int(r), collections.Counter())
            per[value] += 1
        for r, per in counter.items():
            maj_in_ns[r] = max(sorted(per), key=lambda k: per[k])
        maj_in_ns = np.asarray([str(v) for v in maj_in_ns])

        # the mod's primary production system: within a namespace, the system
        # that produces the most *distinct* outputs of that same namespace.
        own = out_mask & (ns == effective_ns[ridx])
        pairs = collections.Counter()
        for r, value in zip(ridx[own], base[own]):
            pairs[(effective_ns[r], systems[r], value)] = 1
        per_system = collections.Counter()
        for (pack_ns, system, _), _ in pairs.items():
            per_system[(pack_ns, system)] += 1
        primary: dict = {}
        for (pack_ns, system), count in sorted(per_system.items()):
            best = primary.get(pack_ns)
            if best is None or count > best[1]:
                primary[pack_ns] = (system, count)
        is_primary = np.asarray(
            [1.0 if primary.get(effective_ns[r], ("", 0))[0] == systems[r] else 0.0
             for r in range(n_recipes)])

        # ------------------------------------------- sibling outlier z-scores
        rec = ctx["recipes"][slug]
        by_recipe = np.full(n_recipes, -1, dtype=np.int64)
        by_recipe[rec["recipe_idx"]] = np.arange(rec["recipe_idx"].size)
        prod_rows = np.flatnonzero(out_mask & (by_recipe[ridx] >= 0))
        groups, group_idx = np.unique(base[prod_rows], return_inverse=True)
        pos = by_recipe[ridx[prod_rows]]
        z_columns = {}
        for name, values in (("n_inputs", rec["n_input_slots"]),
                             ("value_in", rec["log_value_in"]),
                             ("tier", rec["tier"]),
                             ("n_outputs", rec["n_output_slots"])):
            z = group_z(values[pos], group_idx, groups.size)
            total = np.bincount(pos, weights=z, minlength=rec["recipe_idx"].size)
            count = np.bincount(pos, minlength=rec["recipe_idx"].size)
            z_columns[name] = np.divide(total, count,
                                        out=np.zeros(count.size, dtype=float),
                                        where=count > 0)
        n_sibs = np.bincount(group_idx, minlength=groups.size).astype(np.float64)
        sib_total = np.bincount(pos, weights=n_sibs[group_idx],
                                minlength=rec["recipe_idx"].size)
        sib_count = np.bincount(pos, minlength=rec["recipe_idx"].size)
        n_siblings = np.divide(sib_total, sib_count,
                               out=np.zeros(sib_count.size, dtype=float),
                               where=sib_count > 0)

        # ------------------------------------------------ system-shape block
        sysrel = pq.read_table(ctx["out_dir"] / slug / "economy" /
                               "system_reliability.parquet")
        sys_names = sysrel.column("system").to_pylist()
        sys_row = {name: i for i, name in enumerate(sys_names)}
        sys_cols = {}
        for name in ("zero_input_share", "noop_share", "mean_inputs",
                     "mean_outputs", "cross_system_output_share",
                     "empty_id_share", "no_machine_share", "atypicality",
                     "mean_reliability"):
            sys_cols[name] = column(sysrel, name)
        sys_cols["log_n_recipes"] = np.log1p(column(sysrel, "n_recipes"))
        take = np.asarray([sys_row.get(s, -1) for s in systems])

        def system_column(name):
            values = sys_cols[name]
            out = np.full(n_recipes, np.nan)
            ok = take >= 0
            out[ok] = values[take[ok]]
            return out

        per = {
            "l5_out_ns_eq_recipe_ns": (out_ns == recipe_ns).astype(float),
            "l5_out_ns_eq_majority_in_ns": (out_ns == maj_in_ns).astype(float),
            "l5_in_from_recipe_ns_share": in_share,
            "l5_system_is_primary": is_primary,
        }
        for name in ("zero_input_share", "noop_share", "mean_inputs",
                     "mean_outputs", "cross_system_output_share",
                     "empty_id_share", "no_machine_share", "atypicality",
                     "mean_reliability", "log_n_recipes"):
            per[f"l5_sys_{name}"] = system_column(name)

        keep = rec["tpos"] >= 0
        posn = rec["tpos"][keep]
        n = len(ctx["templates"][slug])
        feat = ctx["features"][slug]
        for name, values in per.items():
            take_rec = values[rec["recipe_idx"]]
            feat[f"{name}_mean"] = group_mean(take_rec[keep], posn, n)
            rec[name] = take_rec
        for name, values in z_columns.items():
            rec[f"l5_z_{name}"] = values
            feat[f"l5_z_{name}_mean"] = group_mean(values[keep], posn, n)
            feat[f"l5_z_{name}_absmax"] = group_max(np.abs(values)[keep], posn, n)
        rec["l5_n_siblings"] = n_siblings
        rec["recipe_ns"] = effective_ns[rec["recipe_idx"]]
        rec["template_id"] = [ctx["templates"][slug][p] if p >= 0 else ""
                              for p in rec["tpos"]]
        feat["l5_n_siblings_mean"] = group_mean(n_siblings[keep], posn, n)
    n_cols = len(ctx["features"][ctx["packs"][0]])
    log(f"[intent] L5 done; {n_cols} columns so far; {time.time() - t0:.1f}s")


# ==================================================================== stage L6
#
# The residual: the id tokens the ontology could NOT place in an induced
# class, plus the tokens of the system path.  They carry real signal — a
# system whose path says what kind of page it is, an id whose tail says what
# the recipe is for — but writing any of them down as a rule is exactly the
# failure this project exists to fix.  So they are hashed into anonymous
# buckets and handed to the model as 32 numbers it may weigh or ignore.


def bucket_of(token: str) -> int:
    """Stable bucket of a residual token.

    ``sha1(token)`` interpreted as a big-endian integer, modulo
    :data:`HASH_BUCKETS`.  sha1 and not ``hash()`` because CPython salts the
    builtin per process, which would make the feature matrix differ between
    two runs.  The token is the *path* token only: a namespace never reaches
    this function, so the model cannot learn "this mod".
    """
    digest = hashlib.sha1(token.encode("utf-8")).digest()
    return int.from_bytes(digest, "big") % HASH_BUCKETS


def residual_system_tokens(system: str, classed: set, namespaces: set) -> list:
    """Path tokens of a system id that are neither classed nor a namespace.

    Dropping the id's own namespace is not enough: a system id that *renders*
    another registry's page carries that registry's namespace as a path
    segment, so a bucket ends up dominated by one mod name.  The namespace set
    is the pack's own — every namespace that appears on any id — so the filter
    is data-driven and needs no list.
    """
    path = split_id(system)[1]
    out = []
    for part in path.replace("/", " ").replace("_", " ").replace("-", " ") \
            .replace(".", " ").split():
        token = part.lower()
        if token and token not in classed and token not in namespaces:
            out.append(token)
    return out


def pack_namespaces(ctx: dict, slug: str) -> set:
    """Every namespace the pack uses, on an item id, a recipe id or a system."""
    out = set()
    identity = pq.read_table(ctx["out_dir"] / slug / "identity.parquet",
                             columns=["ns"])
    out.update(identity.column("ns").to_pylist())
    recipes = pq.read_table(ctx["cache_dir"] / slug / "recipes.parquet",
                            columns=["recipe_ns", "system"])
    out.update(recipes.column("recipe_ns").to_pylist())
    out.update(split_id(s)[0] for s in set(recipes.column("system").to_pylist()))
    out.discard("")
    return out


def stage_lexical(ctx: dict) -> None:
    t0 = time.time()
    cache: dict = {}
    for slug in ctx["packs"]:
        classes = read_json(ctx["out_dir"] / slug / "token-classes.json")
        classed = {m for info in classes["classes"].values()
                   for m in info["members"]}
        namespaces = pack_namespaces(ctx, slug)
        tids = ctx["templates"][slug]
        n = len(tids)
        buckets = np.zeros((HASH_BUCKETS, n), dtype=np.float64)
        n_rid = np.zeros(n)
        n_sys = np.zeros(n)
        for i, (rid, system) in enumerate(zip(ctx["residual"][slug],
                                              ctx["systems"][slug])):
            tokens = [t for t in rid
                      if t not in classed and t not in namespaces]
            sys_tokens = cache.get(system)
            if sys_tokens is None:
                sys_tokens = residual_system_tokens(system, classed,
                                                    namespaces)
                cache[system] = sys_tokens
            n_rid[i] = len(tokens)
            n_sys[i] = len(sys_tokens)
            for token in tokens + sys_tokens:
                buckets[bucket_of(token), i] += 1.0
        feat = ctx["features"][slug]
        for b in range(HASH_BUCKETS):
            feat[f"l6_h{b:02d}"] = buckets[b]
        feat["l6_n_residual_rid_tokens"] = n_rid
        feat["l6_n_residual_system_tokens"] = n_sys
        cache.clear()
    n_cols = len(ctx["features"][ctx["packs"][0]])
    log(f"[lexical] L6 done ({HASH_BUCKETS} buckets); {n_cols} columns so far; "
        f"{time.time() - t0:.1f}s")


# ======================================================= stage: recipe matrix
#
# The pair model scores two producers of the same output, so it needs the
# features at *recipe* level, not template level: the trainer builds
# ``f(a) - f(b)`` from these columns and joins the template matrix on
# ``template_id`` for everything that is constant within a template.

RECIPE_NUMERIC = (
    "n_input_slots", "n_output_slots", "tier", "machine_tier",
    "max_input_tier", "log_value_in", "log_value_ratio", "reliability",
    "l4_n_leaf_inputs", "l4_leaf_input_share", "l4_leaf_acq_mean",
    "l4_leaf_acq_min", "l4_data_gap_input_share", "l4_pool_input_share",
    "l4_pool_size_max", "l4_unknown_input_share",
    "l5_out_ns_eq_recipe_ns", "l5_out_ns_eq_majority_in_ns",
    "l5_in_from_recipe_ns_share", "l5_system_is_primary",
    "l5_sys_zero_input_share", "l5_sys_noop_share", "l5_sys_mean_inputs",
    "l5_sys_mean_outputs", "l5_sys_cross_system_output_share",
    "l5_sys_empty_id_share", "l5_sys_no_machine_share", "l5_sys_atypicality",
    "l5_sys_mean_reliability", "l5_sys_log_n_recipes",
    "l5_z_n_inputs", "l5_z_value_in", "l5_z_tier", "l5_z_n_outputs",
    "l5_n_siblings",
)


def recipe_schema() -> pa.Schema:
    fields = [("pack", pa.string()), ("pack_group", pa.string()),
              ("recipe_idx", pa.int32()), ("template_id", pa.string()),
              ("system", pa.string()), ("recipe_ns", pa.string()),
              ("shape_role", pa.string())]
    fields += [(name, pa.float32()) for name in RECIPE_NUMERIC]
    return pa.schema(fields)


def stage_recipes(ctx: dict) -> None:
    t0 = time.time()
    schema = recipe_schema()
    chunks = []
    for slug in ctx["packs"]:
        rec = ctx["recipes"][slug]
        group = ctx["pack_group"][slug]
        n = rec["recipe_idx"].size
        arrays = [
            pa.array([slug] * n, type=pa.string()),
            pa.array([group] * n, type=pa.string()),
            pa.array(rec["recipe_idx"], type=pa.int32()),
            pa.array(rec["template_id"], type=pa.string()),
            pa.array(rec["system"], type=pa.string()),
            pa.array(rec["recipe_ns"].tolist(), type=pa.string()),
            pa.array(rec["shape_role"], type=pa.string()),
        ]
        arrays += [pa.array(np.asarray(rec[name], dtype=np.float32),
                            type=pa.float32()) for name in RECIPE_NUMERIC]
        chunks.append(pa.Table.from_arrays(arrays, schema=schema))
    table = pa.concat_tables(chunks)
    path = ctx["feature_dir"] / "recipe_features.parquet"
    path.parent.mkdir(parents=True, exist_ok=True)
    pq.write_table(table, path, compression="snappy")
    ctx["meta"]["recipe_features"] = {
        "seconds": round(time.time() - t0, 2), "rows": table.num_rows,
        "columns": len(schema), "bytes": path.stat().st_size,
    }
    log(f"[recipes] {table.num_rows:,} rows x {len(schema)} columns "
        f"({path.stat().st_size / 1e6:.1f} MB); {time.time() - t0:.1f}s")


# ======================================================== stage: write it out

LAYER_OF_PREFIX = {"l1_": "L1 ontology", "l2_": "L2 conservation",
                   "l3_": "L3 progression", "l4_": "L4 resources",
                   "l5_": "L5 intent", "l6_": "L6 lexical residual"}

#: Descriptions of the named features.  These are *feature* descriptions, in
#: the export schema's own words; no registry name appears in any of them.
DESCRIPTIONS = {
    "l1_n_inputs": "abstracted input slots in the signature",
    "l1_n_outputs": "abstracted output slots in the signature",
    "l1_n_members": "recipes folded into this template in this pack",
    "l1_n_in_materials": "distinct material classes on the input side",
    "l1_n_out_materials": "distinct material classes on the output side",
    "l1_material_overlap": "Jaccard of the input and output material sets",
    "l1_in_abstract_share": "input material/form slots resolved to a class",
    "l1_out_abstract_share": "output material/form slots resolved to a class",
    "l1_n_variant_classes": "distinct component-key variant classes used",
    "l1_has_variant_class": "any slot carries a variant class",
    "l1_n_modifier_slots": "modifier classes across all slots",
    "l1_n_rid_tokens": "id path tokens that belong to no induced class",
    "l1_in_slot_repetition": "share of input slots that repeat another slot",
    "l1_amount_in_sum": "sum of abstracted input amounts",
    "l1_amount_out_sum": "sum of abstracted output amounts",
    "l1_amount_out_max": "largest abstracted output amount",
    "l1_chance_out_min": "smallest output chance bucket",
    "l1_n_machines": "abstracted machine ids on the signature",
    "l1_machine_present": "the signature names a machine",
    "l1_machine_abstract_share": "machine slots resolved to a class",
    "l2_material_set_preserved_share": "output materials equal input materials",
    "l2_out_subset_in_share": "output materials are a subset of the inputs",
    "l2_is_round_trip_share": "two items of one material reach each other",
    "l2_zero_concrete_inputs_share": "nothing on the input side resolved",
    "l2_inputs_equal_outputs_share": "folded input multiset equals the output one",
    "l2_self_dependent_share": "the recipe consumes something it also makes",
    "l2_unique_producer_share": "this is the only producer of its output",
    "l2_count_ratio_mean": "items out per item in",
    "l2_count_ratio_max": "largest items-out-per-item-in over the members",
    "l2_mat_log_ratio_max_mean": "largest per-material log count ratio",
    "l2_reliability_mean": "Phase 0b reliability of the members",
    "l2_reliability_min": "least reliable member",
    "l2_log_value_in_mean": "log input value, centred on the pack median",
    "l2_log_value_out_mean": "log output value, centred on the pack median",
    "l2_log_value_ratio_mean": "log of output value over input value",
    "l2_log_value_ratio_min": "smallest log value ratio over the members",
    "l3_tier_mean": "Phase 0b recipe tier",
    "l3_tier_max": "deepest member tier",
    "l3_machine_tier_mean": "tier of the machine the recipe runs on",
    "l3_max_input_tier_mean": "deepest input tier",
    "l3_reverse_tier_share": "members whose input tier clears the Tukey fence",
    "l3_tier_jump_mean": "recipe tier minus deepest input tier",
    "l3_tier_jump_max": "largest tier jump over the members",
    "l3_machine_unbuildable_share": "members whose machine has no producer",
    "l4_n_leaf_inputs_mean": "inputs with no in-pack producer",
    "l4_leaf_input_share_mean": "share of inputs that are leaves",
    "l4_leaf_acq_mean_mean": "mean acquirability of the leaf inputs",
    "l4_leaf_acq_min_mean": "least acquirable leaf input",
    "l4_data_gap_input_share_mean": "leaf inputs other packs can already make",
    "l4_pool_input_share_mean": "inputs that sit in a conversion pool",
    "l4_pool_size_mean_mean": "mean size of the pools the inputs sit in",
    "l4_pool_size_max": "largest pool an input sits in",
    "l4_unknown_input_share_mean": "inputs with no row in the economy table",
    "l5_out_ns_eq_recipe_ns_mean": "output namespace equals the recipe's",
    "l5_out_ns_eq_majority_in_ns_mean": "output namespace equals the inputs'",
    "l5_in_from_recipe_ns_share_mean": "inputs from the recipe's own namespace",
    "l5_system_is_primary_mean": "system carries that namespace's own outputs",
    "l5_n_siblings_mean": "producers of the same output, including this one",
    "l6_n_residual_rid_tokens": "id tokens left over after abstraction",
    "l6_n_residual_system_tokens": "system path tokens left over",
}
for _axis in ("n_inputs", "value_in", "tier", "n_outputs"):
    DESCRIPTIONS[f"l5_z_{_axis}_mean"] = (
        f"mean sibling z-score of {_axis} against the other producers")
    DESCRIPTIONS[f"l5_z_{_axis}_absmax"] = (
        f"largest absolute sibling z-score of {_axis}")
for _name in ("zero_input_share", "noop_share", "mean_inputs", "mean_outputs",
              "cross_system_output_share", "empty_id_share",
              "no_machine_share", "atypicality", "mean_reliability",
              "log_n_recipes"):
    DESCRIPTIONS[f"l5_sys_{_name}_mean"] = f"system shape: {_name}"


def describe(name: str) -> str:
    if name in DESCRIPTIONS:
        return DESCRIPTIONS[name]
    if name.startswith("l6_h"):
        return f"count of residual tokens hashed into bucket {name[4:]}"
    if name.startswith("l2_role_"):
        return "share of members with this Phase 0b shape role"
    if name.startswith("l1_in_kind_"):
        return "share of input slots of this content kind"
    if name.startswith("l1_out_kind_"):
        return "an output slot of this content kind is present"
    if name.startswith("l1_pat_"):
        return "share of slots with this match strategy"
    return ""


def aggregation_of(name: str) -> str:
    if name.startswith("l1_") or name.startswith("l6_"):
        return "signature (one value per template)"
    if name.endswith("_max") or name.endswith("_absmax"):
        return "max over member recipes"
    if name.endswith("_min"):
        return "min over member recipes"
    return "mean over member recipes"


def stage_write(ctx: dict) -> None:
    t0 = time.time()
    # run-scoped one-hot vocabulary for the match strategies
    vocab = sorted({s for slug in ctx["packs"]
                    for counter in ctx["strategies"][slug][0]
                    for s in counter})
    for slug in ctx["packs"]:
        counters, n_pat = ctx["strategies"][slug]
        feat = ctx["features"][slug]
        for strategy in vocab:
            counts = np.asarray([c.get(strategy, 0) for c in counters],
                                dtype=np.float64)
            with np.errstate(invalid="ignore"):
                feat[f"l1_pat_{strategy or 'absent'}_share"] = np.where(
                    n_pat > 0, counts / n_pat, np.nan)

    names = sorted({n for slug in ctx["packs"] for n in ctx["features"][slug]},
                   key=lambda n: (n[:3], n))
    catalog = [{"name": n, "layer": LAYER_OF_PREFIX.get(n[:3], "?"),
                "description": describe(n), "aggregation": aggregation_of(n)}
               for n in names]

    fields = [("pack", pa.string()), ("pack_group", pa.string()),
              ("template_id", pa.string()), ("n_members", pa.int32())]
    fields += [(n, pa.float32()) for n in names]
    schema = pa.schema(fields)

    chunks, nan_total, rows_total = [], np.zeros(len(names)), 0
    absent = collections.Counter()
    for slug in ctx["packs"]:
        tids = ctx["templates"][slug]
        n = len(tids)
        rows_total += n
        feat = ctx["features"][slug]
        arrays = [pa.array([slug] * n, type=pa.string()),
                  pa.array([ctx["pack_group"][slug]] * n, type=pa.string()),
                  pa.array(tids, type=pa.string()),
                  pa.array(ctx["sizes"][slug].astype(np.int32), type=pa.int32())]
        for i, name in enumerate(names):
            values = feat.get(name)
            if values is None:
                absent[name] += 1
                # a one-hot column for a value this pack never has is 0, not
                # missing: the pack *does* answer the question, with "none".
                values = np.zeros(n)
            values = np.asarray(values, dtype=np.float32)
            nan_total[i] += int(np.isnan(values).sum())
            arrays.append(pa.array(values, type=pa.float32()))
        chunks.append(pa.Table.from_arrays(arrays, schema=schema))
    table = pa.concat_tables(chunks)
    path = ctx["feature_dir"] / "template_features.parquet"
    path.parent.mkdir(parents=True, exist_ok=True)
    pq.write_table(table, path, compression="snappy")

    nan_rate = {n: round(float(c) / rows_total, 5)
                for n, c in zip(names, nan_total) if c}
    by_layer = collections.Counter(c["layer"] for c in catalog)
    write_json(ctx["feature_dir"] / "feature-catalog.json", {
        "template_features": catalog,
        "recipe_features": [{"name": n, "layer": LAYER_OF_PREFIX.get(n[:3],
                                                                     "L2/L3"),
                             "description": describe(n) or describe(n + "_mean"),
                             "aggregation": "per recipe"}
                            for n in RECIPE_NUMERIC],
        "hash": {"buckets": HASH_BUCKETS, "function": "sha1(token) mod buckets",
                 "input": "residual id path tokens + residual system path "
                          "tokens, minus every namespace the pack uses"},
        "match_strategies": vocab,
        "shape_roles": ctx["meta"].get("shape_roles", []),
    })
    ctx["meta"]["template_features"] = {
        "seconds": round(time.time() - t0, 2), "rows": table.num_rows,
        "columns": len(schema), "features": len(names),
        "features_by_layer": {k: int(v) for k, v in sorted(by_layer.items())},
        "bytes": path.stat().st_size,
        "nan_rate": nan_rate,
        "columns_absent_in_some_pack": {k: int(v) for k, v in sorted(absent.items())},
    }
    log(f"[write] {table.num_rows:,} template rows x {len(names)} features "
        f"({path.stat().st_size / 1e6:.1f} MB); by layer "
        f"{dict(sorted(by_layer.items()))}; {time.time() - t0:.1f}s")


# ==================================================================== driver


def build_context(args) -> dict:
    cache_dir = Path(args.cache_dir)
    out_dir = Path(args.out_dir)
    packs = read_json(cache_dir / "packs.json")
    if args.packs in ("all", ""):
        slugs = sorted(packs)
    else:
        slugs = sorted(p.strip() for p in args.packs.split(",") if p.strip())
        unknown = [s for s in slugs if s not in packs]
        if unknown:
            raise SystemExit(f"unknown pack slug(s): {unknown}")
    return {
        "args": args, "cache_dir": cache_dir, "out_dir": out_dir,
        "feature_dir": Path(args.write_dir or out_dir) / "features", "packs": slugs,
        "pack_group": pack_group_map(out_dir, slugs),
        "n_recipes": {s: pq.read_metadata(cache_dir / s / "recipes.parquet").num_rows
                      for s in slugs},
        "templates": {}, "systems": {}, "sizes": {}, "features": {},
        "strategies": {}, "residual": {}, "recipes": {}, "catalog": [],
        "meta": {"packs": slugs},
    }


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Phase 0c-1 feature layer")
    ap.add_argument("--cache-dir", default="cache")
    ap.add_argument("--out-dir", default="out")
    ap.add_argument("--write-dir", default="",
                    help="where to write (default: --out-dir); the inputs are "
                         "always read from --out-dir")
    ap.add_argument("--packs", default="all")
    ap.add_argument("--stage", default="write", choices=STAGES)
    args = ap.parse_args(argv)

    t0 = time.time()
    ctx = build_context(args)
    log(f"packs: {len(ctx['packs'])}; "
        f"groups: {len(set(ctx['pack_group'].values()))}")
    stop = STAGES.index(args.stage)
    for name in STAGES[:stop + 1]:
        globals()[f"stage_{name}"](ctx)
    ctx["meta"]["wall_seconds"] = round(time.time() - t0, 2)
    ctx["meta"]["peak_rss_mb"] = round(peak_rss_mb(), 1)
    if args.stage == STAGES[-1]:
        write_json(ctx["feature_dir"] / "features-meta.json", ctx["meta"])
    log(f"done in {time.time() - t0:.1f}s, peak RSS {peak_rss_mb():.0f} MB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
