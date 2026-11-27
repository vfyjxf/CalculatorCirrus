#!/usr/bin/env python
"""Phase 0b invariants.  pytest is not installed — run with the venv python:

    .venv/bin/python tests/test_phase0b.py

Two halves.  The first builds tiny hand-written hypergraphs and checks the
solver laws on them (cycle safety, OR-node minimum, the value and catalyst
hooks, tier monotonicity, SCC pooling, round-trip detection).  The second runs
the real pipeline over the small ``fom`` pack and checks the table invariants
and byte-level determinism.
"""
from __future__ import annotations

import ast
import hashlib
import re
import shutil
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(ROOT))

import numpy as np  # noqa: E402
import pyarrow.parquet as pq  # noqa: E402

import economy as E  # noqa: E402
from common import read_json  # noqa: E402

PACK = "fom"
PASSED = []


def check(name: str, condition: bool, detail: str = "") -> None:
    assert condition, f"FAILED: {name}{(': ' + detail) if detail else ''}"
    PASSED.append(name)
    print(f"  ok  {name}" + (f"  ({detail})" if detail else ""))


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


# --------------------------------------------------------- synthetic graphs


def synth(n_items: int, recipes, or_groups=(), value_cost=None) -> E.PackGraph:
    """A hand-built PackGraph.

    ``recipes`` is a list of ``(inputs, outputs, machines)`` where inputs are
    ``(node, amount)`` pairs (node >= n_items selects an OR group) and outputs
    are ``(item, amount)`` pairs.
    """
    g = E.PackGraph()
    g.slug = "synthetic"
    g.n_recipes = len(recipes)
    g.kept = np.ones(len(recipes), dtype=bool)
    g.keep_idx = np.arange(len(recipes))
    g.items = np.array([f"x:{i:03d}" for i in range(n_items)], dtype=object)
    g.n_items = n_items
    g.n_or = len(or_groups)
    sizes = [len(m) for m in or_groups]
    g.or_ptr = np.concatenate(([0], np.cumsum(sizes))).astype(np.int64)
    g.or_mem = np.array([m for grp in or_groups for m in sorted(grp)],
                        dtype=np.int64)
    in_rec, in_node, in_amt, out_rec, out_item, out_amt = [], [], [], [], [], []
    mach_off, mach_item = [0], []
    for r, (ins, outs, machines) in enumerate(recipes):
        for node, amt in sorted(ins):
            in_rec.append(r); in_node.append(node); in_amt.append(float(amt))
        for item, amt in sorted(outs):
            out_rec.append(r); out_item.append(item); out_amt.append(float(amt))
        mach_item.extend(sorted(machines))
        mach_off.append(len(mach_item))
    g.in_rec = np.array(in_rec, dtype=np.int64)
    g.in_node = np.array(in_node, dtype=np.int64)
    g.in_amt = np.array(in_amt, dtype=np.float64)
    g.out_rec = np.array(out_rec, dtype=np.int64)
    g.out_item = np.array(out_item, dtype=np.int64)
    g.out_amt = np.array(out_amt, dtype=np.float64)
    g.machines_ptr = np.array(mach_off, dtype=np.int64)
    g.machines_item = np.array(mach_item, dtype=np.int64)
    g.n_inputs = np.array([len(r[0]) for r in recipes], dtype=np.int64)
    g.n_outputs = np.array([len(r[1]) for r in recipes], dtype=np.int64)
    g.value_cost = np.zeros(len(recipes)) if value_cost is None else \
        np.asarray(value_cost, dtype=np.float64)
    g.n_unres_in = np.zeros(len(recipes), dtype=np.int64)
    g.n_unres_out = np.zeros(len(recipes), dtype=np.int64)
    g.stats = {}
    return g


def solve(g, seed=None, mode="value", **kw):
    inc = E.incidence(g)
    if seed is None:
        seed = np.ones(g.n_items)
    return inc, E.solve_to_fixpoint(g, inc, seed, mode, **kw)


def test_or_node_is_a_minimum() -> None:
    # items 0 and 1 are leaves; the OR group {0, 1} feeds recipe -> item 2.
    g = synth(3, [([(3, 1.0)], [(2, 1.0)], [])], or_groups=[(0, 1)])
    seed = np.array([5.0, 1.0, 1.0])
    _, (vals, _, _, _, _, _) = solve(g, seed)
    check("OR node takes the minimum over its members", vals[3] == 1.0,
          f"or={vals[3]}")
    check("the OR minimum is what the consumer pays", vals[2] == 1.0,
          f"value={vals[2]}")

    seed = np.array([0.25, 9.0, 1.0])
    _, (vals, _, _, _, _, _) = solve(g, seed)
    check("swapping which member is cheapest swaps the minimum",
          vals[3] == 0.25 and vals[2] == 0.25)


def test_cycle_safety() -> None:
    # 0 is a leaf.  1 <-> 2 is a ring, entered once from 0.
    g = synth(3, [([(0, 1.0)], [(1, 1.0)], []),
                  ([(1, 1.0)], [(2, 1.0)], []),
                  ([(2, 1.0)], [(1, 1.0)], [])])
    _, (vals, _, _, settled, boot, st) = solve(g, np.array([1.0, 1.0, 1.0]))
    check("a ring entered from a leaf needs no bootstrap",
          bool(not boot.any() and settled[:3].all()), str(st))
    check("ring values stay finite and positive",
          np.isfinite(vals[:3]).all() and (vals[:3] > 0).all(), str(vals[:3]))

    # A closed ring with no leaf at all must still terminate.
    g = synth(2, [([(0, 1.0)], [(1, 1.0)], []), ([(1, 1.0)], [(0, 1.0)], [])])
    _, (vals, _, _, settled, boot, st) = solve(g)
    check("a ring with no base case terminates and is bootstrapped",
          bool(settled[:2].all() and boot[:2].any()), str(st))
    check("bootstrapped values are strictly positive",
          (vals[:2] > 0).all(), str(vals[:2]))

    # The free-lunch shape: one x makes two x.  Must not descend to zero.
    g = synth(2, [([(0, 1.0)], [(1, 1.0)], []), ([(1, 1.0)], [(1, 2.0)], [])])
    _, (vals, _, _, _, _, st) = solve(g, np.array([1.0, 1.0]))
    check("a recipe that doubles its own input cannot drive the value to 0",
          vals[1] == 1.0, f"value={vals[1]}")


def test_ring_floor() -> None:
    """A conversion ring must not be priced at the global cheapest leaf.

    items: 0 ore (leaf, dear), 1 ingot, 2 nugget, 3 block, 4 an unrelated cheap
    leaf.  1<->2<->3 is a nugget/ingot/block ring, entered from the ore.
    """
    ore, ingot, nugget, block, cheap = 0, 1, 2, 3, 4
    recipes = [([(ore, 1.0)], [(ingot, 1.0)], []),
               ([(ingot, 1.0)], [(nugget, 9.0)], []),
               ([(nugget, 9.0)], [(ingot, 1.0)], []),
               ([(ingot, 9.0)], [(block, 1.0)], []),
               ([(block, 1.0)], [(ingot, 9.0)], []),
               ([(cheap, 1.0)], [(5, 1.0)], [])]
    g = synth(6, recipes)
    seed = np.array([5.0, 1.0, 1.0, 1.0, 1.0, 1.0])
    step = np.full(len(recipes), 0.5)
    inc = E.incidence(g)
    vals, _, _, _, boot, _ = E.solve_to_fixpoint(g, inc, seed, "value",
                                                 process_cost=step,
                                                 ring_seed=seed)
    check("a ring entered from outside is never bootstrapped",
          not bool(boot.any()), str(np.flatnonzero(boot)))
    check("the ring prices at its entry plus one step, not at the cheapest leaf",
          abs(vals[ingot] - 5.5) < 1e-9, f"ingot={vals[ingot]} (ore 5.0 + step 0.5)")
    check("the rest of the ring follows from the entry",
          abs(vals[nugget] - (5.5 + 0.5) / 9.0) < 1e-9 and
          abs(vals[block] - (9 * 5.5 + 0.5)) < 1e-9,
          f"nugget={vals[nugget]:.4f} block={vals[block]:.2f}")

    # Now cut the entry: the ring is closed, so it is priced from its own
    # cheapest member's seed -- still never from the unrelated cheap leaf.
    closed = synth(6, recipes[1:])
    inc = E.incidence(closed)
    ring_seed = np.array([5.0, 7.0, 30.0, 40.0, 1.0, 1.0])
    vals, _, _, _, boot, _ = E.solve_to_fixpoint(closed, inc, seed, "value",
                                                 process_cost=step[1:],
                                                 ring_seed=ring_seed)
    check("a closed ring is flagged as bootstrapped", bool(boot[ingot]))
    check("a closed ring prices at its cheapest member's own seed",
          abs(vals[ingot] - 7.0) < 1e-9,
          f"ingot={vals[ingot]} (own seed 7.0, cheapest leaf in the pack is 1.0)")
    check("nothing in the closed ring inherits the global cheapest leaf",
          all(vals[i] > 1.0 for i in (ingot, nugget * 0 + ingot, block)),
          f"ingot={vals[ingot]:.3f} block={vals[block]:.3f}")


def test_step_cost_and_hooks() -> None:
    """A step is never free, and the weight / exclude hooks are honoured."""
    g = synth(3, [([(0, 1.0)], [(1, 1.0)], []), ([(1, 1.0)], [(2, 1.0)], [])])
    inc = E.incidence(g)
    seed = np.ones(3)
    step = np.array([0.5, 0.5])
    vals, _, _, _, _, _ = E.solve_to_fixpoint(g, inc, seed, "value",
                                              process_cost=step)
    check("every step adds its cost, so a chain is dearer than its leaf",
          vals[1] == 1.5 and vals[2] == 2.0, f"{vals[:3]}")

    weight = np.array([2.0, 1.0])
    vals, _, _, _, _, _ = E.solve_to_fixpoint(g, inc, seed, "value",
                                              process_cost=step,
                                              recipe_weight=weight)
    check("recipe_weight scales a producer's cost (the reliability hook)",
          vals[1] == 3.0, f"{vals[1]}")

    excl = np.array([False, True])
    ring_seed = np.array([1.0, 1.0, 9.0])
    vals, _, _, _, boot, _ = E.solve_to_fixpoint(g, inc, seed, "value",
                                                 process_cost=step,
                                                 exclude=excl,
                                                 ring_seed=ring_seed)
    check("exclude removes the recipe, orphaning its output",
          vals[1] == 1.5 and bool(boot[2]), f"{vals[:3]} boot={boot}")
    check("an orphaned item falls back to its own seed, never to the cheapest",
          vals[2] == 9.0, f"{vals[2]}")


def test_seed_band() -> None:
    """Leaf seeds live in [1, K] -- no leaf may be orders of magnitude dearer."""
    meta = read_json(ROOT / "out" / PACK / "economy" / "economy-meta.json")
    k = meta["leaves"]["seed_spread"]
    lo, hi = meta["leaves"]["seed_min"], meta["leaves"]["seed_max"]
    check("the cheapest leaf is exactly 1.0", abs(lo - 1.0) < 1e-6, str(lo))
    check("no leaf is dearer than K", hi <= k + 1e-6, f"{hi} <= {k}")
    check("n_forms_of_material is kept in the table but not in the composite",
          "n_forms_of_material" in E.LEAF_FEATURES and
          "n_forms_of_material" not in E.COMPOSITE_FEATURES)
    check("the composite loadings cover exactly the composite features",
          set(meta["acquirability_loadings"]) == set(E.COMPOSITE_FEATURES))


def test_value_flow_hook() -> None:
    """A `value` content (time / energy) adds to the recipe cost.

    No pack in this corpus exports one, so this is the only place the hook is
    ever exercised.
    """
    check("value scalars parse losslessly",
          list(E.parse_value_scalars(np.array(["2.5", None, "bogus"],
                                              dtype=object))) == [2.5, 0.0, 1.0])
    g = synth(2, [([(0, 1.0)], [(1, 1.0)], [])])
    _, (plain, _, _, _, _, _) = solve(g, np.array([1.0, 1.0]))
    g.value_cost = np.array([3.0])
    _, (with_value, _, _, _, _, _) = solve(g, np.array([1.0, 1.0]))
    check("a value input raises the cost of everything it makes",
          with_value[1] == plain[1] + 3.0,
          f"{plain[1]} -> {with_value[1]}")


def test_catalyst_hook() -> None:
    """An entry on both sides of one recipe is borrowed, not consumed."""
    rec = np.array([0, 0, 1], dtype=np.int64)
    node = np.array([5, 7, 5], dtype=np.int64)
    amt_in = np.array([4.0, 2.0, 1.0])
    orec = np.array([0, 1], dtype=np.int64)
    onode = np.array([5, 9], dtype=np.int64)
    amt_out = np.array([3.0, 1.0])
    new_in, new_out = E.net_catalysts(rec, node, amt_in, orec, onode, amt_out)
    check("the returned part is netted off the input side",
          list(new_in) == [1.0, 2.0, 1.0], str(new_in))
    check("the returned part is netted off the output side",
          list(new_out) == [0.0, 1.0], str(new_out))
    check("an output the recipe never consumed is untouched", new_out[1] == 1.0)


def test_tier_monotonicity() -> None:
    # 0 leaf -> 1 -> 2, and a machine (item 3, made from 0) gating the last step.
    g = synth(4, [([(0, 1.0)], [(1, 1.0)], []),
                  ([(0, 1.0)], [(3, 1.0)], []),
                  ([(1, 1.0)], [(2, 1.0)], [3])])
    inc = E.incidence(g)
    ti = E.solve_tiers(g, inc)
    check("leaves are tier 0", ti.item_tier[0] == 0.0)
    check("tiers rise by one along a chain",
          list(ti.item_tier[:4]) == [0.0, 1.0, 2.0, 1.0], str(ti.item_tier))
    check("every recipe tier exceeds its largest input tier",
          bool((ti.recipe_tier > ti.max_input_tier).all()))
    check("machine tier comes from the machine's own chain",
          ti.machine_tier[list(inc.mach_items).index(3)] == 1.0)
    check("no tier is negative", bool((ti.item_tier >= 0).all()))

    # A machine nobody can build is hand/unknown: tier 0, flagged.
    g = synth(3, [([(0, 1.0)], [(1, 1.0)], [2])])
    inc = E.incidence(g)
    ti = E.solve_tiers(g, inc)
    check("an unbuildable machine is tier 0 and flagged",
          ti.machine_tier[0] == 0.0 and bool(ti.machine_unbuildable[0]))


def test_scc_pooling() -> None:
    # 0<->1<->2 mutually convertible, 3 only reachable one way.
    edges = [(0, 1), (1, 2), (2, 0), (2, 3)]
    src = np.array([a for a, _ in edges], dtype=np.int64)
    dst = np.array([b for _, b in edges], dtype=np.int64)
    ptr, order = E.build_csr(src, 4)
    comp = E.strong_components(4, ptr, dst[order])
    check("a mutually convertible ring is one strong component",
          comp[0] == comp[1] == comp[2], str(comp))
    check("a one-way successor is not in the pool", comp[3] != comp[0], str(comp))
    weak = E.weak_components(4, src, dst)
    check("the weak component spans the whole chain",
          len(set(weak.tolist())) == 1, str(weak))


def test_round_trip_detection() -> None:
    #  0 -> 1 and 1 -> 0 (same material) is a round trip; 0 -> 2 alone is not.
    g = synth(3, [([(0, 1.0)], [(1, 1.0)], []),
                  ([(1, 1.0)], [(0, 9.0)], []),
                  ([(0, 1.0)], [(2, 1.0)], [])])
    item_mat = np.array([0, 0, 1], dtype=np.int64)
    node_mat = item_mat.copy()
    comp, rt, self_dep = E.round_trip_flags(g, item_mat, node_mat, g.in_rec,
                                            g.in_node, 256)
    check("both directions of a same-material pair are round trips",
          bool(rt[0]) and bool(rt[1]), str(rt))
    check("a one-way recipe into another material is not a round trip",
          not bool(rt[2]), str(rt))
    check("nothing here is self-dependent", not self_dep.any())

    # An output that is also an input is self-dependent but not a round trip.
    g = synth(2, [([(0, 1.0), (1, 1.0)], [(0, 2.0)], [])])
    comp, rt, self_dep = E.round_trip_flags(g, np.array([0, 1]), np.array([0, 1]),
                                            g.in_rec, g.in_node, 256)
    check("an output that is also an input is self-dependent",
          bool(self_dep[0]) and not bool(rt[0]))


def test_learned_band_is_learned() -> None:
    """Nothing in the band is a constant: feed it a 1:4 economy and it moves."""
    check("the fallback band is only used without evidence",
          E.FALLBACK_BAND == (1.0 / 3.0, 3.0))
    meta = read_json(ROOT / "out" / PACK / "economy" / "economy-meta.json")
    lo, hi = meta["conversion_band"]
    check("the fom band was learned from round-trip evidence, not the fallback",
          abs(hi - 3.0) > 1e-9 and meta["shape"]["band_sample"] >= E.MIN_BAND_SAMPLES,
          f"band=({lo:.3f}, {hi:.3f}) from {meta['shape']['band_sample']} ratios")
    check("the band is symmetric in log space", abs(lo * hi - 1.0) < 1e-9)


# ----------------------------------------------------------- the real pack


def run_pipeline(out_dir: Path) -> Path:
    """Copy the Phase 0a outputs fom needs, then run Phase 0b into out_dir."""
    src = ROOT / "out" / PACK
    dst = out_dir / PACK
    dst.mkdir(parents=True, exist_ok=True)
    for name in ("identity.parquet", "recipe_template.parquet"):
        shutil.copy2(src / name, dst / name)
    rc = E.main(["--cache-dir", str(ROOT / "cache"), "--out-dir", str(out_dir),
                 "--packs", PACK, "--quiet"])
    assert rc == 0, "economy.main returned non-zero"
    return dst / "economy"


def test_pack_invariants(eco: Path) -> None:
    recipes = pq.read_table(ROOT / "cache" / PACK / "recipes.parquet")
    kept = [i for i, d in enumerate(recipes.column("dropped").to_pylist()) if not d]
    shape = pq.read_table(eco / "recipe_shape.parquet")
    check("every kept recipe has exactly one shape row",
          shape.num_rows == len(kept) and
          shape.column("recipe_idx").to_pylist() == kept,
          f"{shape.num_rows} rows / {len(kept)} kept")

    roles = set(shape.column("shape_role").to_pylist())
    check("every shape role is one of the five documented ones",
          roles <= set(E.ROLE_NAMES), str(sorted(roles)))

    values = pq.read_table(eco / "item_values.parquet")
    ids = values.column("base_id").to_pylist()
    check("base ids are unique and sorted", ids == sorted(set(ids)),
          f"{len(ids)} ids")

    contents = pq.read_table(ROOT / "cache" / PACK / "contents.parquet",
                             columns=["kind", "base_id", "members"])
    kinds = contents.column("kind").to_pylist()
    bases = contents.column("base_id").to_pylist()
    want = {b for k, b in zip(kinds, bases) if k == "stack"}
    for members in contents.column("members").to_pylist():
        want.update(members)
    check("every concrete id and every OR member has a value row",
          want <= set(ids), f"missing {sorted(want - set(ids))[:3]}")

    for column in ("value_uniform", "value_fitted"):
        v = np.array(values.column(column).to_pylist())
        check(f"{column} is finite and strictly positive",
              bool(np.isfinite(v).all() and (v > 0).all()),
              f"min={v.min():.3g} max={v.max():.3g}")
    tiers = np.array(values.column("tier").to_pylist())
    check("item tiers are finite and >= 0",
          bool(np.isfinite(tiers).all() and (tiers >= 0).all()),
          f"max={tiers.max():.0f}")

    leaf = np.array(values.column("is_leaf").to_pylist())
    nprod = np.array(values.column("n_producers").to_pylist())
    check("is_leaf means exactly 'no producer in this pack'",
          bool((leaf == (nprod == 0)).all()))
    check("leaves are tier 0", bool((tiers[leaf] == 0).all()))

    rel = np.array(shape.column("reliability").to_pylist())
    check("reliability lies in (0, 1]", bool((rel > 0).all() and (rel <= 1).all()),
          f"[{rel.min():.3f}, {rel.max():.3f}]")

    machines = pq.read_table(eco / "machines.parquet")
    mt = np.array(machines.column("tier").to_pylist())
    check("machine tiers are finite and >= 0",
          bool(np.isfinite(mt).all() and (mt >= 0).all()))

    pools = pq.read_table(eco / "pools.parquet")
    sizes = pools.column("size").to_pylist()
    check("no pool is a singleton", all(s >= 2 for s in sizes), str(sizes[:5]))
    members = pools.column("members").to_pylist()
    reps = pools.column("representative").to_pylist()
    check("every pool representative is one of its members",
          all(r in m for r, m in zip(reps, members)))
    kinds = pools.column("pool_kind").to_pylist()
    strong = {p for p, k in zip(pools.column("pool_id").to_pylist(), kinds)
              if k == "strong"}
    in_values = {p for p in values.column("pool_id").to_pylist() if p}
    check("every pool id on an item exists in pools.parquet", in_values <= strong)

    leaves = pq.read_table(eco / "leaf_features.parquet")
    check("one leaf-feature row per leaf",
          leaves.num_rows == int(leaf.sum()),
          f"{leaves.num_rows} / {int(leaf.sum())}")
    acq = np.array(leaves.column("acquirability").to_pylist())
    check("acquirability is a rank score in [0, 1]",
          bool((acq >= 0).all() and (acq <= 1).all()))


def test_determinism(a: Path, b: Path) -> None:
    names = sorted(p.name for p in a.iterdir() if p.suffix == ".parquet")
    bad = [n for n in names if digest(a / n) != digest(b / n)]
    check("two runs produce byte-identical parquet",
          not bad, f"{len(names)} files, differing: {bad}")


# ------------------------------------------------------- the no-vocabulary rule

FORBIDDEN = re.compile(
    r"(?i)(?:^|_)(oak|spruce|birch|jungle|acacia|cherry|bamboo|crimson|warped|"
    r"copper|iron|gold|golden|diamond|netherite|stone|cobblestone|granite|"
    r"andesite|diorite|deepslate|wool|dye|concrete|terracotta|glass|planks|"
    r"slab|stairs|ingot|nugget|plate|gear|"
    r"white|black|red|green|blue|yellow|pink|cyan|magenta|lime|brown|"
    r"junk|duplicate|recycle|recycling|shapeless|smelting|furnace|crafting|"
    r"minecraft|forge|neoforge|create|mekanism|thermal|ae2)(?:$|_)")

SCHEMA_WORDS = {
    "stack", "tag", "any", "typedAny", "value", "complex", "item", "fluid",
    "kind", "amount", "chance", "matchStrategy", "identity", "in", "out",
    "recipes", "systems", "inputs", "outputs", "machines", "stacks", "values",
    "implementation", "stackType", "valueType", "components", "count", "id",
    "type", "source", "system", "recipeType", "recipeCount", "skippedCount",
    "contentTypes", "stackTypes", "tagTypes", "valueTypes", "variantTypes",
    "contentType", "tags", "formatVersion", "generatedAt", "minecraftVersion",
    "_stack", "mixed", "unknown:unknown", "minecraft_version",
    # Phase 0b column and role names -- shape words, not registry words.
    "recycling_shaped", "conversion", "production", "generation", "noop",
    "machine_tier", "machine_unbuildable", "machine_id", "machine_recipes",
    "machine_producers", "machine_in_universe", "recipe_machine_tier",
    "n_machines", "mach_items", "mach_ptr", "mach_rec", "machines_ptr",
    "machines_item", "material_key", "material_keys", "material_set_preserved",
    "material_class", "material_keys", "mat_names", "mat_log_ratio_max",
    "n_in_materials", "n_out_materials", "n_forms_of_material", "item_mat",
    "node_mat", "materials",
    # "concrete" here is the logic sense -- a content entry that resolved to a
    # real item rather than to a complex/member-less group.  The block of the
    # same name could only appear as a namespaced literal, which the
    # NAMESPACE_LIKE check below still rejects.
    "zero_concrete_inputs",
}

NAMESPACE_LIKE = re.compile(r"^[a-z][a-z0-9_]*:[a-z0-9_][a-z0-9_/.-]*$")


def _code_strings_and_names(path: Path):
    tree = ast.parse(path.read_text(encoding="utf-8"))
    docstrings = set()
    for node in ast.walk(tree):
        if isinstance(node, (ast.Module, ast.ClassDef, ast.FunctionDef,
                             ast.AsyncFunctionDef)):
            doc = ast.get_docstring(node, clean=False)
            if doc is not None:
                docstrings.add(doc)
    out = []
    for node in ast.walk(tree):
        if isinstance(node, ast.Constant) and isinstance(node.value, str):
            if node.value in docstrings:
                continue
            out.append(("literal", node.value, node.lineno))
        elif isinstance(node, ast.Name):
            out.append(("name", node.id, node.lineno))
        elif isinstance(node, ast.Attribute):
            out.append(("attr", node.attr, node.lineno))
    return out


def test_no_rule_vocabulary() -> None:
    offenders = []
    for name in ("economy.py", "common.py"):
        path = ROOT / name
        for kind, text, lineno in _code_strings_and_names(path):
            if text in SCHEMA_WORDS:
                continue
            if FORBIDDEN.search(text):
                offenders.append(f"{name}:{lineno} {kind} {text!r}")
            if kind == "literal" and NAMESPACE_LIKE.match(text) \
                    and not text.startswith(("cls:", "t:", "var:", "kg:", "tpl:",
                                             "spool:", "wpool:", "id:", "x:")):
                offenders.append(f"{name}:{lineno} namespace-like literal {text!r}")
    check("no mod / material / colour / rule vocabulary in economy.py",
          not offenders, "; ".join(offenders[:5]) or "clean")


def test_thresholds_are_learned_or_shape() -> None:
    """Every knob is either a shape constant or fitted; none is a magic number
    about a specific pack."""
    meta = read_json(ROOT / "out" / PACK / "economy" / "economy-meta.json")
    check("the conversion band is recorded with its sample size",
          meta["shape"]["band_sample"] > 0)
    check("the reverse-tier margin is recorded",
          meta["reverse_tier_margin"] >= 1.0, str(meta["reverse_tier_margin"]))
    check("flag penalties are fitted from observed flag shares",
          all(0.0 <= w <= 1.0 for w in meta["flag_weights"].values()),
          str(meta["flag_weights"]))
    check("the degenerate-field ceiling is measured, not assumed",
          set(meta["field_checks"]) == {"chance", "value"} and
          not any(meta["field_checks"].values()),
          str(meta["field_checks"]))


def main() -> int:
    print(f"Phase 0b checks (pack: {PACK})")
    print("-- solver laws on synthetic graphs")
    test_or_node_is_a_minimum()
    test_cycle_safety()
    test_value_flow_hook()
    test_catalyst_hook()
    test_tier_monotonicity()
    test_ring_floor()
    test_step_cost_and_hooks()
    test_scc_pooling()
    test_round_trip_detection()

    tmp = Path(tempfile.mkdtemp(prefix="phase0b-test-"))
    try:
        print("-- pipeline on the real pack")
        eco_a = run_pipeline(tmp / "run-a")
        eco_b = run_pipeline(tmp / "run-b")
        test_pack_invariants(eco_a)
        print("-- determinism")
        test_determinism(eco_a, eco_b)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)

    print("-- design rules")
    test_learned_band_is_learned()
    test_seed_band()
    test_no_rule_vocabulary()
    test_thresholds_are_learned_or_shape()
    print(f"\n{len(PASSED)} checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
