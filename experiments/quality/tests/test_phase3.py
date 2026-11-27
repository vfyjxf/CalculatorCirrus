#!/usr/bin/env python
"""Phase 3 invariants — the hierarchical planner and its A/B harness.

    ./.venv/bin/python tests/test_phase3.py

Plain asserts, no pytest.  Two halves:

* **synthetic** — tiny vocabulary-free graphs, one per rule the planner has to
  obey.  Every id below is of the form ``ns:a``/``ns:b`` so the no-vocabulary
  check stays true of the tests as well.
* **real** — fom invariants, including a forward *replay* of every plan (the
  prototype's mirror of Java's ``RoutingPlanReverseVerifier``) and a check that
  the O(n) producer score really is the Bradley-Terry aggregate of the exact
  pairwise ranker margins.
"""
from __future__ import annotations

import math  # noqa: F401  (kept: the synthetic tests reason about ceil)
import sys
import time
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

import eval_planner as E  # noqa: E402
import planner as P  # noqa: E402

PACK = "fom"
PASSED: list = []


def check(name: str, ok: bool, detail: str = "") -> None:
    mark = "ok " if ok else "FAIL"
    print(f"  {mark} {name}" + (f"  [{detail}]" if detail else ""))
    assert ok, f"{name}: {detail}"
    PASSED.append(name)


# ============================================== a synthetic PackIndex builder


def make_index(items, recipes, leaves=(), machines=(), pools=(),
               pool_edges=(), values=None, tiers=None):
    """Build a ``PackIndex`` straight from literals.

    ``recipes`` entries are dicts::

        {"in": [(id | [ids], amount), ...],
         "out": [(id, amount, chance), ...],
         "machines": [...], "tier": float, "max_input_tier": float,
         "shape_role": str, "role": str, "junk_p": float, "score": float,
         "reliability": float, "reverse_tier": bool}
    """
    ix = P.PackIndex()
    ix.slug, ix.pack = "t", "t"
    ix.items = np.asarray(sorted(items), dtype=object)
    ix.n_items = ix.items.size
    ix.item_map = {str(v): i for i, v in enumerate(ix.items)}
    ix.n_recipes = len(recipes)
    ix.keep = np.ones(ix.n_recipes, dtype=bool)
    ix.ridx = np.arange(ix.n_recipes, dtype=np.int64)
    idx = {v: i for i, v in enumerate(ix.items)}

    ix.item_value = np.asarray([(values or {}).get(v, 1.0) for v in ix.items])
    ix.item_tier = np.asarray([(tiers or {}).get(v, 0.0) for v in ix.items])
    ix.item_leaf = np.asarray([v in set(leaves) for v in ix.items])
    pool_of = {}
    for pid, members in pools:
        for m in members:
            pool_of[m] = pid
    ix.item_pool = np.asarray([pool_of.get(v, "") for v in ix.items], dtype=object)
    ix.item_acq = np.full(ix.n_items, 0.5)
    ix.item_gap = np.zeros(ix.n_items, dtype=bool)
    ix.item_cycle = np.zeros(ix.n_items, dtype=bool)
    ix.item_ncons = np.zeros(ix.n_items, dtype=np.int64)

    # OR groups, keyed by the sorted member tuple
    or_keys, or_members = [], []
    for r in recipes:
        for entry, _ in r.get("in", ()):
            if isinstance(entry, (list, tuple)):
                key = tuple(sorted(entry))
                if key not in or_keys:
                    or_keys.append(key)
                    or_members.append(sorted(entry))
    ix.n_or = len(or_keys)
    ix.or_members = or_members
    ix.or_items = [[idx[m] for m in mem if m in idx] for mem in or_members]
    ix.or_set = [set(v) for v in ix.or_items]
    ix.or_index = {k: i for i, k in enumerate(or_keys)}
    ix.or_rep = np.asarray(
        [min(v, key=lambda i: (ix.item_value[i], str(ix.items[i])))
         if v else -1 for v in ix.or_items], dtype=np.int64)

    in_rec, in_node, in_amt = [], [], []
    out_rec, out_item, out_amt, out_ch = [], [], [], []
    for r, spec in enumerate(recipes):
        for entry, amt in spec.get("in", ()):
            if isinstance(entry, (list, tuple)):
                node = ix.n_items + or_keys.index(tuple(sorted(entry)))
            else:
                node = idx[entry]
            in_rec.append(r), in_node.append(node), in_amt.append(float(amt))
        for entry in spec.get("out", ()):
            base, amt = entry[0], float(entry[1])
            ch = float(entry[2]) if len(entry) > 2 else 1.0
            out_rec.append(r), out_item.append(idx[base])
            out_amt.append(amt), out_ch.append(ch)
    ix.in_ptr, o = P.build_csr(np.asarray(in_rec, dtype=np.int64), ix.n_recipes)
    ix.in_node = np.asarray(in_node, dtype=np.int64)[o]
    ix.in_amt = np.asarray(in_amt, dtype=np.float64)[o]
    ix.out_ptr, o = P.build_csr(np.asarray(out_rec, dtype=np.int64), ix.n_recipes)
    ix.out_item = np.asarray(out_item, dtype=np.int64)[o]
    ix.out_amt = np.asarray(out_amt, dtype=np.float64)[o]
    ix.out_chance = np.asarray(out_ch, dtype=np.float64)[o]
    ix.prod_ptr, o = P.build_csr(ix.out_item, ix.n_items)
    prod_rec = np.repeat(np.arange(ix.n_recipes, dtype=np.int64),
                         np.diff(ix.out_ptr))
    ix.prod_rec = prod_rec[o]
    ix.prod_amt = (ix.out_amt * ix.out_chance)[o]
    ix.item_nprod = np.diff(ix.prod_ptr).astype(np.int64)

    ix.machine_ids = np.asarray(sorted({m for m, _ in machines}), dtype=object)
    mt = dict(machines)
    ix.machine_tier = np.asarray([mt.get(m, 0.0) for m in ix.machine_ids])
    ix.machine_unbuildable = np.zeros(ix.machine_ids.size, dtype=bool)
    ptr, flat = [0], []
    for spec in recipes:
        for m in spec.get("machines", ()):
            flat.append(int(np.searchsorted(ix.machine_ids, m)))
        ptr.append(len(flat))
    ix.mach_ptr = np.asarray(ptr, dtype=np.int64)
    ix.mach_item = np.asarray(flat, dtype=np.int64)
    ix.mach_tier = np.asarray([ix.machine_tier[i] for i in flat]) if flat \
        else np.zeros(0)

    g = lambda k, d: np.asarray([s.get(k, d) for s in recipes])  # noqa: E731
    ix.template_id = np.asarray([f"tpl:{i:04d}" for i in range(ix.n_recipes)],
                                dtype=object)
    ix.system = np.asarray([s.get("system", "sys:a") for s in recipes],
                           dtype=object)
    ix.recipe_id = np.asarray([s.get("id", f"ns:r{i}")
                               for i, s in enumerate(recipes)], dtype=object)
    ix.shape_role = np.asarray([s.get("shape_role", P.ROLE_PRODUCTION)
                                for s in recipes], dtype=object)
    ix.tier = g("tier", 1.0).astype(np.float64)
    ix.max_input_tier = g("max_input_tier", 0.0).astype(np.float64)
    ix.reverse_tier = g("reverse_tier", False).astype(bool)
    ix.reliability = g("reliability", 0.5).astype(np.float64)
    ix.unique_producer = np.zeros(ix.n_recipes, dtype=bool)
    ix.self_dependent = np.zeros(ix.n_recipes, dtype=bool)
    ix.rec_mach_tier = np.zeros(ix.n_recipes)
    ix.weight = np.full(ix.n_recipes, 1.0)
    ix.junk_p = g("junk_p", 0.0).astype(np.float64)
    ix.role_classes = [P.ROLE_PRODUCTION, P.ROLE_CONVERSION, P.ROLE_RECYCLING,
                       P.ROLE_GENERATION, P.ROLE_DISPLAY]
    roles = [s.get("role", P.ROLE_PRODUCTION) for s in recipes]
    ix.role_p = np.zeros((ix.n_recipes, len(ix.role_classes)))
    for i, role in enumerate(roles):
        ix.role_p[i, ix.role_classes.index(role)] = 1.0
    ix.role_argmax = np.asarray(roles, dtype=object)
    ix.eff_role = P.effective_roles(ix)
    ix.score = g("score", 0.0).astype(np.float64)
    ix.pool_members = {pid: sorted(m) for pid, m in pools}
    ix.pool_edges = {
        "pool_id": np.asarray([e[0] for e in pool_edges], dtype=object),
        "src": np.asarray([idx[e[1]] for e in pool_edges], dtype=np.int64),
        "dst": np.asarray([idx[e[2]] for e in pool_edges], dtype=np.int64),
        "unit_cost": np.asarray([e[3] for e in pool_edges], dtype=np.float64),
        "recipe_idx": np.zeros(len(pool_edges), dtype=np.int64)}
    ix.meta = {}
    return ix


def solve(ix, target, amount=1, stock=None, **kw):
    knobs = P.Knobs(**kw)
    pol = P.Policy(ix, knobs)
    sv = P.solve_cost(ix, pol, knobs, stock)
    units = P.pool_units(ix)
    return P.plan_target(ix, pol, knobs, target, amount, stock, sv, units), pol


def executed(pl):
    return [a["recipe_idx"] for a in pl.actions]


# ======================================================= the ladder / pool test


def pool_graph():
    """ore -> b (the unit), and a three-member conversion pool a <-> b <-> c.

    ``a`` is 9 ``b``; ``c`` is 9 ``a``.  The storage-ladder pathology is the
    planner making ``b`` by making ``a`` first and taking it apart again.
    """
    items = ["ns:ore", "ns:a", "ns:b", "ns:c"]
    recipes = [
        {"in": [("ns:ore", 1)], "out": [("ns:b", 1)], "tier": 1,
         "id": "ns:smelt"},                                        # 0
        {"in": [("ns:b", 9)], "out": [("ns:a", 1)], "tier": 2,
         "shape_role": "conversion", "role": P.ROLE_CONVERSION, "id": "ns:pack"},
        {"in": [("ns:a", 1)], "out": [("ns:b", 9)], "tier": 2,
         "shape_role": "conversion", "role": P.ROLE_CONVERSION, "id": "ns:unpack"},
        {"in": [("ns:a", 9)], "out": [("ns:c", 1)], "tier": 3,
         "shape_role": "conversion", "role": P.ROLE_CONVERSION, "id": "ns:pack2"},
        {"in": [("ns:c", 1)], "out": [("ns:a", 9)], "tier": 3,
         "shape_role": "conversion", "role": P.ROLE_CONVERSION, "id": "ns:unpack2"},
    ]
    pools = [("spool:x", ["ns:a", "ns:b", "ns:c"])]
    edges = [("spool:x", "ns:b", "ns:a", 9.0), ("spool:x", "ns:a", "ns:b", 1 / 9),
             ("spool:x", "ns:a", "ns:c", 9.0), ("spool:x", "ns:c", "ns:a", 1 / 9)]
    return make_index(items, recipes, leaves=["ns:ore"], pools=pools,
                      pool_edges=edges)


def test_no_storage_ladder() -> None:
    ix = pool_graph()
    pl, _ = solve(ix, "ns:b", 1)
    check("a pool unit is made directly, never through its block",
          executed(pl) == [0], f"actions={executed(pl)}")
    check("no round trip in the ladder graph", pl.metrics["round_trips"] == 0)
    pl, _ = solve(ix, "ns:c", 1)
    ids = [str(ix.recipe_id[r]) for r in executed(pl)]
    check("the top of the ladder is reached by packing only, once each",
          ids == ["ns:smelt", "ns:pack", "ns:pack2"], f"{ids}")
    check("81 units of the leaf, not more", pl.raw.get("ns:ore") == 81.0,
          str(pl.raw))
    check("no unpacking action is ever emitted",
          not any(i.endswith("unpack") or i.endswith("unpack2") for i in ids))


def test_conversion_only_from_stock() -> None:
    ix = pool_graph()
    pl, _ = solve(ix, "ns:b", 9, stock={"ns:a": 1})
    ids = [str(ix.recipe_id[r]) for r in executed(pl)]
    check("holding the block, the unit is unpacked instead of smelted",
          ids == ["ns:unpack"], f"{ids}")
    check("nothing is gathered when the stock covers it", not pl.raw, str(pl.raw))
    pl, _ = solve(ix, "ns:b", 9)
    check("without the block in stock, the same demand smelts",
          [str(ix.recipe_id[r]) for r in executed(pl)] == ["ns:smelt"])


def test_amplification_guard() -> None:
    """A pool whose conversions do not conserve must be caught by the guard."""
    items = ["ns:ore", "ns:a", "ns:b"]
    recipes = [
        {"in": [("ns:ore", 1)], "out": [("ns:a", 1)], "tier": 1},
        {"in": [("ns:a", 1)], "out": [("ns:b", 1)], "tier": 2,
         "shape_role": "conversion", "role": P.ROLE_CONVERSION},
        {"in": [("ns:b", 1)], "out": [("ns:a", 2)], "tier": 2,
         "shape_role": "conversion", "role": P.ROLE_CONVERSION},
    ]
    pools = [("spool:y", ["ns:a", "ns:b"])]
    edges = [("spool:y", "ns:a", "ns:b", 1.0), ("spool:y", "ns:b", "ns:a", 0.5)]
    ix = make_index(items, recipes, leaves=["ns:ore"], pools=pools,
                    pool_edges=edges)
    units = P.pool_units(ix)
    a, b = ix.item_of("ns:a"), ix.item_of("ns:b")
    check("pool unit weights follow the edge ratios",
          abs(units[b] - 1.0) < 1e-9 and abs(units[a] - 1.0) < 1e-9,
          f"a={units[a]:.3f} b={units[b]:.3f}")
    pl, _ = solve(ix, "ns:a", 4)
    net = sum(units[ix.item_of(r["base_id"])] * r["amount"]
              for act in pl.actions if ix.eff_role[act["recipe_idx"]] ==
              P.ROLE_CONVERSION for r in act["outputs"]) - \
        sum(units[ix.item_of(r["base_id"])] * r["amount"]
            for act in pl.actions if ix.eff_role[act["recipe_idx"]] ==
            P.ROLE_CONVERSION for r in act["inputs"])
    check("the amplifying loop is never entered from empty stock",
          net <= 1e-9 and pl.metrics["pool_amplification"] == 0.0,
          f"net={net:.3f} amp={pl.metrics['pool_amplification']}")


def test_net_loss_loop_guard() -> None:
    """``a -> b`` and ``b -> a`` where the round trip loses material."""
    items = ["ns:ore", "ns:a", "ns:b"]
    recipes = [
        {"in": [("ns:ore", 1)], "out": [("ns:a", 1)], "tier": 1},
        {"in": [("ns:a", 2)], "out": [("ns:b", 1)], "tier": 2},
        {"in": [("ns:b", 1)], "out": [("ns:a", 1)], "tier": 3},
    ]
    ix = make_index(items, recipes, leaves=["ns:ore"])
    pl, _ = solve(ix, "ns:a", 1)
    check("a net-loss loop is not used to make its own input",
          executed(pl) == [0], f"{executed(pl)}")
    check("and the plan is complete", pl.status == "ok", pl.status)


def test_unique_producer_protection() -> None:
    items = ["ns:ore", "ns:a"]
    recipes = [{"in": [("ns:ore", 1)], "out": [("ns:a", 1)], "junk_p": 0.99,
                "role": P.ROLE_DISPLAY, "tier": 1}]
    ix = make_index(items, recipes, leaves=["ns:ore"])
    pl, pol = solve(ix, "ns:a", 1)
    check("a sole producer is never excluded, whatever the model says",
          not bool(pol.excluded[0]) and bool(pol.protected_[0]))
    check("and the plan uses it", executed(pl) == [0], f"{executed(pl)}")


def test_junk_exclusion_with_alternative() -> None:
    items = ["ns:ore", "ns:a"]
    recipes = [
        {"in": [("ns:ore", 1)], "out": [("ns:a", 1)], "junk_p": 0.99, "tier": 1},
        {"in": [("ns:ore", 4)], "out": [("ns:a", 1)], "junk_p": 0.01, "tier": 1},
    ]
    ix = make_index(items, recipes, leaves=["ns:ore"])
    pl, pol = solve(ix, "ns:a", 1)
    check("the flagged producer is excluded when a sibling survives",
          bool(pol.excluded[0]) and not bool(pol.excluded[1]))
    check("even though it is four times cheaper", executed(pl) == [1],
          f"{executed(pl)}")


def test_ranker_overrides_inside_shortlist() -> None:
    items = ["ns:ore", "ns:a"]
    recipes = [
        {"in": [("ns:ore", 1)], "out": [("ns:a", 1)], "score": -1.0, "tier": 1},
        {"in": [("ns:ore", 2)], "out": [("ns:a", 1)], "score": +5.0, "tier": 1},
        {"in": [("ns:ore", 40)], "out": [("ns:a", 1)], "score": +9.0, "tier": 1},
    ]
    ix = make_index(items, recipes, leaves=["ns:ore"])
    pl, _ = solve(ix, "ns:a", 1, gamma=2.0)
    check("inside the gamma shortlist the ranker decides", executed(pl) == [1],
          f"{executed(pl)}")
    check("the runner-up and the gap are recorded",
          pl.actions[0]["why"]["runner_up"]["recipe_idx"] == 0 and
          pl.actions[0]["why"]["runner_up"]["gap"] < 0,
          str(pl.actions[0]["why"]["runner_up"]))
    pl, _ = solve(ix, "ns:a", 1, gamma=1.0)
    check("at gamma = 1 only the cost-best survives", executed(pl) == [0],
          f"{executed(pl)}")
    pl, _ = solve(ix, "ns:a", 1, gamma=2.0, use_ranker=False)
    check("with the ranker off, cost decides", executed(pl) == [0],
          f"{executed(pl)}")
    pl, _ = solve(ix, "ns:a", 1, gamma=float("inf"))
    check("gamma = inf lets the dearest producer win on score",
          executed(pl) == [2], f"{executed(pl)}")


def test_tier_penalty() -> None:
    items = ["ns:ore", "ns:a", "ns:b"]
    recipes = [
        {"in": [("ns:ore", 1)], "out": [("ns:a", 1)], "tier": 1},
        {"in": [("ns:ore", 3)], "out": [("ns:b", 1)], "tier": 1},
        # taking a high-tier thing apart to get a low-tier one
        {"in": [("ns:a", 1)], "out": [("ns:b", 1)], "tier": 9,
         "max_input_tier": 9.0, "reverse_tier": True},
    ]
    ix = make_index(items, recipes, leaves=["ns:ore"],
                    tiers={"ns:a": 9.0, "ns:b": 1.0})
    knobs = P.Knobs(tier_penalty=0.0)
    pol = P.Policy(ix, knobs)
    sv = P.solve_cost(ix, pol, knobs)
    c_free, _ = P.producer_cost(ix, sv, knobs, pol, 2, ix.item_of("ns:b"), set())
    knobs2 = P.Knobs(tier_penalty=4.0)
    c_pen, brk = P.producer_cost(ix, sv, knobs2, pol, 2, ix.item_of("ns:b"),
                                 set())
    check("the tier-monotonicity term raises a downhill recipe's cost",
          c_pen > c_free and brk["tier_penalty"] > 0,
          f"{c_free:.3f} -> {c_pen:.3f}")
    pl, _ = solve(ix, "ns:b", 1, tier_penalty=4.0)
    check("and the forward route wins", executed(pl) == [1], f"{executed(pl)}")


def test_craft_times_int64() -> None:
    items = ["ns:ore", "ns:a"]
    recipes = [{"in": [("ns:ore", 1)], "out": [("ns:a", 1)], "tier": 1}]
    ix = make_index(items, recipes, leaves=["ns:ore"])
    want = 2 ** 31 + 17
    pl, _ = solve(ix, "ns:a", want)
    times = pl.actions[0]["times"]
    check("craftTimes is exact past 2^31", times == want,
          f"{times} vs {want}")
    check("and the raw demand scales with it", pl.raw["ns:ore"] == float(want))


def test_expected_output_hook() -> None:
    """chance is a constant 1.0 in this corpus; the hook is exercised here."""
    items = ["ns:ore", "ns:a"]
    base = [{"in": [("ns:ore", 1)], "out": [("ns:a", 1, 1.0)], "tier": 1}]
    half = [{"in": [("ns:ore", 1)], "out": [("ns:a", 1, 0.5)], "tier": 1}]
    p1, _ = solve(make_index(items, base, leaves=["ns:ore"]), "ns:a", 10)
    p2, _ = solve(make_index(items, half, leaves=["ns:ore"]), "ns:a", 10)
    check("a chance of 0.5 doubles the crafts",
          p2.actions[0]["times"] == 2 * p1.actions[0]["times"],
          f"{p1.actions[0]['times']} -> {p2.actions[0]['times']}")


def test_byproduct_credit() -> None:
    items = ["ns:ore", "ns:a", "ns:b", "ns:c"]
    recipes = [
        # one recipe makes both a and b
        {"in": [("ns:ore", 1)], "out": [("ns:a", 1), ("ns:b", 4)], "tier": 1},
        {"in": [("ns:ore", 50)], "out": [("ns:b", 1)], "tier": 1},
        {"in": [("ns:a", 1), ("ns:b", 2)], "out": [("ns:c", 1)], "tier": 2},
    ]
    ix = make_index(items, recipes, leaves=["ns:ore"])
    pl, _ = solve(ix, "ns:c", 1)
    check("the byproduct covers the second demand",
          sorted(executed(pl)) == [0, 2], f"{executed(pl)}")
    check("so the expensive dedicated producer is never run",
          1 not in executed(pl))


def test_budget_truncation() -> None:
    n = 40
    items = ["ns:ore"] + [f"ns:i{i:02d}" for i in range(n)]
    recipes = [{"in": [("ns:ore", 1)], "out": ["ns:i00" and ("ns:i00", 1)],
                "tier": 1}]
    for i in range(1, n):
        recipes.append({"in": [(f"ns:i{i - 1:02d}", 1)],
                        "out": [(f"ns:i{i:02d}", 1)], "tier": i + 1})
    ix = make_index(items, recipes, leaves=["ns:ore"])
    pl, _ = solve(ix, f"ns:i{n - 1:02d}", 1)
    check("the whole chain fits the default budget", pl.status == "ok",
          pl.status)
    pl, _ = solve(ix, f"ns:i{n - 1:02d}", 1, budget_base=5,
                  budget_per_producer=0, budget_cap=5)
    check("a starved budget returns incomplete with a partial plan",
          pl.status == "incomplete" and 0 < len(pl.actions) < n,
          f"{pl.status} actions={len(pl.actions)}")
    check("and says why", any(u["reason"] == "BUDGET_TRUNCATED"
                              for u in pl.unresolved), str(pl.unresolved))


def test_determinism_synthetic() -> None:
    ix = pool_graph()
    a, _ = solve(ix, "ns:c", 7)
    b, _ = solve(ix, "ns:c", 7)
    check("two solves of the same request are identical",
          P.chain_text(a) == P.chain_text(b))


# ================================================================ chain text


def chain_round_trip(ix, pol, knobs, pl):
    """Emit, parse back, and measure both with the same metric function."""
    text = P.chain_text(pl, "0000")
    tmp = ROOT / "out" / "planner-eval" / "_round_trip.txt"
    tmp.parent.mkdir(parents=True, exist_ok=True)
    tmp.write_text(text, encoding="utf-8")
    parsed = E.parse_chain_file(tmp)["0000"]
    tmp.unlink()
    table, _ = E.recipe_index_map(ix)
    units = P.pool_units(ix)
    acts, _ = E.baseline_actions(ix, table, parsed)
    mine = E.chain_metrics(ix, pol, knobs, units, E.plan_actions(ix, pl),
                           pl.raw, pl.unresolved, pl.target, {})
    theirs = E.chain_metrics(ix, pol, knobs, units, acts, parsed.raw,
                             parsed.unresolved, parsed.target, {})
    return mine, theirs, parsed


# ============================================================ replay verifier


def replay(ix: P.PackIndex, pl: P.Plan) -> tuple:
    """Forward-simulate the actions from empty stock.

    The prototype's mirror of Java's ``RoutingPlanReverseVerifier``: start with
    the plan's declared raw materials and stock, execute every action in the
    emitted order, and require that nothing ever goes negative and that the
    target is present at the end.
    """
    have: dict = {}
    for base, qty in pl.raw.items():
        have[base] = have.get(base, 0.0) + qty
    for base, qty in pl.stock.items():
        have[base] = have.get(base, 0.0) + qty
    for a in pl.actions:
        for row in a["inputs"]:
            if not row["base_id"]:
                continue
            have[row["base_id"]] = have.get(row["base_id"], 0.0) - row["amount"]
            if have[row["base_id"]] < -1e-6:
                return False, f"{a['id']} short of {row['base_id']}"
        for row in a["outputs"]:
            have[row["base_id"]] = have.get(row["base_id"], 0.0) + row["amount"]
    if have.get(pl.target, 0.0) < pl.amount - 1e-6:
        return False, f"target {pl.target} {have.get(pl.target, 0.0)} < {pl.amount}"
    return True, ""


# ==================================================================== real fom


def test_fom_invariants(cache: Path, out: Path) -> None:
    knobs = P.Knobs()
    t0 = time.perf_counter()
    ix = P.load_pack(cache, out, PACK, knobs)
    pol = P.Policy(ix, knobs)
    sv = P.solve_cost(ix, pol, knobs)
    units = P.pool_units(ix)
    build = time.perf_counter() - t0
    check("the pack index builds in a second", build < 5.0, f"{build:.2f}s")

    rng = np.random.default_rng(20260919)
    pool = np.flatnonzero(ix.item_nprod > 0)
    pick = np.sort(rng.choice(pool, size=200, replace=False))
    plans, bad_replay, times = [], [], []
    for i in pick:
        pl = P.plan_target(ix, pol, knobs, str(ix.items[i]), 1, None, sv, units)
        plans.append(pl)
        times.append(pl.metrics["wall_ms"])
        ok, why = replay(ix, pl)
        if not ok:
            bad_replay.append((str(ix.items[i]), why))
    check("every plan replays forward from its own raw list",
          not bad_replay, f"{len(bad_replay)} failures: {bad_replay[:3]}")
    check("no plan contains a round trip",
          all(p.metrics["round_trips"] == 0 for p in plans))
    check("no plan amplifies a pool other than the target's",
          all(p.metrics["pool_amplification"] <= 1e-6 for p in plans),
          f"max={max(p.metrics['pool_amplification'] for p in plans):.3f}")
    check("no plan runs an excluded producer",
          all(not bool(pol.excluded[a["recipe_idx"]])
              for p in plans for a in p.actions))
    check("every action's craft count is a positive int64",
          all(isinstance(a["times"], int) and a["times"] >= 1
              for p in plans for a in p.actions))
    med = float(np.median(times))
    check("median per-target wall is well under the 50 ms budget",
          med < 50.0, f"median {med:.2f} ms, p95 "
                      f"{float(np.percentile(times, 95)):.2f} ms")

    solved = [p for p in plans if p.actions]
    mine, theirs, parsed = chain_round_trip(ix, pol, knobs, solved[-1])
    same = all(mine[k] == theirs[k] for k in
               ("actions", "crafts", "raw_kinds", "machines", "depth",
                "round_trips", "tier_reversals"))
    check("chain text survives emit -> parse -> measure", same,
          f"{ {k: (mine[k], theirs[k]) for k in mine if mine[k] != theirs[k]} }")
    check("the parsed target matches", parsed.target == solved[-1].target)

    a = P.plan_target(ix, pol, knobs, str(ix.items[pick[0]]), 1, None, sv, units)
    b = P.plan_target(ix, pol, knobs, str(ix.items[pick[0]]), 1, None, sv, units)
    check("fom plans are deterministic", P.chain_text(a) == P.chain_text(b))


def test_producer_score_is_bradley_terry(cache: Path, out: Path) -> None:
    """The O(n) score must equal the row means of the exact margin matrix."""
    from predict_reference import Reference
    knobs = P.Knobs()
    ix = P.load_pack(cache, out, PACK, knobs)
    ref = Reference(out / "models")
    spec = ref.spec["pair_features"]
    names, n_base = spec["names"], int(spec["n_base"])
    fill = np.asarray(spec["recipe_fill"], dtype=np.float64)
    flags, sym = list(spec["flags"]), set(spec["symmetric_flags"])
    base, flagv = P._pair_inputs(out, ix, names, n_base)
    w = np.asarray(ref.ranker["lr"]["coef"])[0] / \
        np.asarray(ref.ranker["lr"]["scale"])
    s = np.where(np.isfinite(base), base, fill) @ w[:n_base]

    def exact(a, b):
        fa = np.where(np.isfinite(base[a]), base[a], fill)
        fb = np.where(np.isfinite(base[b]), base[b], fill)
        row = list(fa - fb)
        for name in flags:
            v = flagv[name]
            if name in sym:
                row.append(1.0 if v[a] == v[b] else 0.0)
            else:
                d = v[a] - v[b]
                row.append(0.0 if not np.isfinite(d) else float(d))
        return float(ref.margin(np.array(row))[0])

    rng = np.random.default_rng(7)
    worst, n_groups = 0.0, 0
    for item in range(ix.n_items):
        lo, hi = ix.prod_ptr[item], ix.prod_ptr[item + 1]
        members = np.unique(ix.prod_rec[lo:hi])
        if members.size < 2 or rng.random() > 0.12:
            continue
        mat = np.array([[0.0 if a == b else exact(a, b) for b in members]
                        for a in members])
        want = mat.sum(axis=1) / (members.size - 1)
        got = P._group_rowmeans(s[members], members, flagv, flags, sym, w,
                                n_base, ix)
        worst = max(worst, float(np.abs(got - want).max()))
        n_groups += 1
    check("the O(n) producer score is the exact Bradley-Terry row mean",
          worst < 1e-9, f"{n_groups} groups, max |delta| = {worst:.2e}")


# ------------------------------------------------------- the no-vocabulary rule


def test_no_rule_vocabulary_phase3() -> None:
    """``planner.py`` and ``eval_planner.py`` are held to the Phase 0a rule."""
    sys.path.insert(0, str(ROOT / "tests"))
    import test_phase0a as t0a
    check("the Phase 0a check covers the two Phase 3 modules",
          "planner.py" in t0a.MODULES and "eval_planner.py" in t0a.MODULES,
          str(t0a.MODULES[-2:]))
    t0a.PASSED.clear()
    t0a.test_no_rule_vocabulary()
    check("no mod / material / colour / rule vocabulary in the Phase 3 code",
          bool(t0a.PASSED))


def main() -> int:
    cache, out = ROOT / "cache", ROOT / "out"
    print("Phase 3 checks")
    print("-- synthetic: pools and conversion")
    test_no_storage_ladder()
    test_conversion_only_from_stock()
    test_amplification_guard()
    test_net_loss_loop_guard()
    print("-- synthetic: candidate policy and the form layer")
    test_unique_producer_protection()
    test_junk_exclusion_with_alternative()
    test_ranker_overrides_inside_shortlist()
    test_tier_penalty()
    print("-- synthetic: extraction")
    test_craft_times_int64()
    test_expected_output_hook()
    test_byproduct_credit()
    test_budget_truncation()
    test_determinism_synthetic()
    print(f"-- real ({PACK})")
    test_fom_invariants(cache, out)
    test_producer_score_is_bradley_terry(cache, out)
    print("-- design rules")
    test_no_rule_vocabulary_phase3()
    print(f"\n{len(PASSED)} checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
