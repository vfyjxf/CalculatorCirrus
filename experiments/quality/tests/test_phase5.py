#!/usr/bin/env python
"""Phase 5 invariants — the performance options and the benchmark harness.

    ./.venv/bin/python tests/test_phase5.py

Light by design: the numbers live in ``REPORT.md`` and take ~20 minutes to
produce, so what is asserted here is that the *options preserve plans* and
that the harness runs.  Everything below is fom.
"""
from __future__ import annotations

import json
import sys
import time
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "tests"))

import bench_planner as B  # noqa: E402
import planner as P  # noqa: E402
from common import read_json  # noqa: E402

PACK = "fom"
PASSED: list = []


def check(name: str, ok: bool, detail: str = "") -> None:
    mark = "ok " if ok else "FAIL"
    print(f"  {mark} {name}" + (f"  [{detail}]" if detail else ""))
    assert ok, f"{name}: {detail}"
    PASSED.append(name)


def _targets(ix, n=60, seed=20260920):
    rng = np.random.default_rng(seed)
    pool = np.flatnonzero(ix.item_nprod > 0)
    pick = rng.choice(pool, size=min(n, pool.size), replace=False)
    return sorted(str(ix.items[i]) for i in np.sort(pick))


def _blobs(ix, pol, knobs, sv, units, targets, opt=None):
    out = []
    for t in targets:
        pl = P.plan_target(ix, pol, knobs, t, 1, None, sv, units, opt)
        j = P.plan_json(pl)
        j["seconds"] = 0.0
        j["metrics"]["wall_ms"] = 0.0
        j.pop("options", None)
        # the explanation records *why* a producer was dropped, and fold
        # pruning legitimately adds a reason; the plan is the action list
        for a in j["actions"]:
            a["why"].pop("excluded", None)
            a["why"].pop("shortlist", None)
        out.append(json.dumps(j, sort_keys=True, separators=(",", ":")))
    return out


def test_memo_sharing_preserves_plans(cache: Path, out: Path) -> None:
    knobs = P.Knobs()
    ix = P.load_pack(cache, out, PACK, knobs)
    pol = P.Policy(ix, knobs)
    units = P.pool_units(ix)
    targets = _targets(ix)
    sv = P.solve_cost(ix, pol, knobs)
    want = _blobs(ix, pol, knobs, sv, units, targets)

    P.clear_solve_memo()
    opt = P.PlanOptions(share_memo=True)
    got = _blobs(ix, pol, knobs, None, units, targets, opt)
    check("sharing the cost memo across requests changes no plan",
          got == want, f"{sum(1 for a, b in zip(got, want) if a != b)} differ")
    st = dict(P.SOLVE_MEMO_STATS)
    check("and it is one solve for the whole batch",
          st["hits"] == len(targets) - 1 and st["misses"] == 1, str(st))
    # a different stock is a different key
    P.plan_target(ix, pol, knobs, targets[0], 1,
                  {str(ix.items[0]): 4.0}, None, units, opt)
    check("a different stock signature is a different memo entry",
          P.SOLVE_MEMO_STATS["stored"] == 2,
          str(P.SOLVE_MEMO_STATS))
    P.clear_solve_memo()
    check("clearing the memo resets its counters",
          P.SOLVE_MEMO_STATS == {"hits": 0, "misses": 0, "stored": 0})


def test_fold_pruning_preserves_plans(cache: Path, out: Path) -> None:
    knobs = P.Knobs()
    ix = P.load_pack(cache, out, PACK, knobs)
    units = P.pool_units(ix)
    targets = _targets(ix)
    pol = P.Policy(ix, knobs)
    sv = P.solve_cost(ix, pol, knobs)
    want = _blobs(ix, pol, knobs, sv, units, targets)
    opt = P.PlanOptions(fold_templates=True)
    pol2 = P.Policy(ix, knobs, opt)
    sv2 = P.solve_cost(ix, pol2, knobs, None, opt)
    got = _blobs(ix, pol2, knobs, sv2, units, targets, opt)
    diff = sum(1 for a, b in zip(got, want) if a != b)
    check("folding siblings of one template leaves the action lists alone "
          "on fom", diff == 0, f"{diff}/{len(targets)} differ")
    check("the policy and the cost solve are untouched by folding",
          bool((pol.excluded == pol2.excluded).all())
          and sv.cost[:ix.n_items] == sv2.cost[:ix.n_items])


def test_budget_shapes(cache: Path, out: Path) -> None:
    knobs = P.Knobs()
    ix = P.load_pack(cache, out, PACK, knobs)
    pol = P.Policy(ix, knobs)
    sv = P.solve_cost(ix, pol, knobs)
    units = P.pool_units(ix)
    targets = _targets(ix, 40)
    want = _blobs(ix, pol, knobs, sv, units, targets)
    for shape in ("cone", "depth"):
        opt = P.PlanOptions(budget_shape=shape)
        got = _blobs(ix, pol, knobs, sv, units, targets, opt)
        check(f"the {shape}-scaled budget plans fom identically "
              f"(nothing truncates)", got == want)
    opt = P.PlanOptions(budget_shape="time", budget_seconds=5.0)
    got = _blobs(ix, pol, knobs, sv, units, targets, opt)
    check("a generous time box plans fom identically", got == want)
    try:
        P.PlanOptions(budget_shape="nonsense")
        raised = False
    except SystemExit:
        raised = True
    check("an unknown budget shape is refused, not ignored", raised)
    n, depth = P.cone_stats(ix, pol, ix.item_of(targets[0]), 200000)
    check("cone_stats agrees with cone_size and reports a hop depth",
          n == P.cone_size(ix, pol, ix.item_of(targets[0]), 200000)
          and depth >= 0, f"{n} producers, depth {depth}")


def test_counters_do_not_leak(cache: Path, out: Path) -> None:
    knobs = P.Knobs()
    ix = P.load_pack(cache, out, PACK, knobs)
    pol = P.Policy(ix, knobs)
    sv = P.solve_cost(ix, pol, knobs)
    units = P.pool_units(ix)
    t = _targets(ix, 5)[0]
    pl = P.plan_target(ix, pol, knobs, t, 1, None, sv, units)
    j = P.plan_json(pl)
    check("the search counters are not serialised into the plan",
          "expansions" not in json.dumps(j)
          and P.PLAN_STATS["expansions"] >= 0,
          str(P.PLAN_STATS))
    check("the policy signature is stable and cached",
          pol.signature() == pol.signature()
          == P.Policy(ix, knobs).signature(), pol.signature())


def test_harness_runs(cache: Path, out: Path) -> None:
    """Every benchmark stage, on fom, inside 30 s."""
    class Args:
        packs = PACK
        deep = 25
        random = 50
        seed = 20260920
        repeats = 1
        profile = None
        profile_targets = 3
        budget_seconds = [0.25]
        budget_bases = [512]
    knobs = P.Knobs()
    t0 = time.perf_counter()
    res = {}
    for name in ("cone", "memo", "fold", "budget"):
        res[name] = getattr(B, "stage_" + name)(Args(), cache, out, knobs)
    el = time.perf_counter() - t0
    check("the benchmark harness runs every stage on fom in under 30 s",
          el < 30.0, f"{el:.1f}s")
    check("the cone stage reports both target sets",
          res["cone"]["packs"][0]["deep"]["wall_ms"]["n"] == 25
          and res["cone"]["packs"][0]["random"]["wall_ms"]["n"] == 50)
    check("the memo stage asserts its own plan equality",
          res["memo"]["packs"][0]["plans_identical"])
    check("the fold stage reports action-list changes separately from "
          "explanation changes",
          "action_lists_changed" in res["fold"]["packs"][0])
    check("the budget stage compares every shape",
          len(res["budget"]["packs"][0]["bases"]["512"]["variants"]) == 3)
    P.clear_solve_memo()


def test_cache_inventory(cache: Path, out: Path) -> None:
    """Every artefact the inventory names exists and is classified."""
    missing, unclassified = [], []
    for path, _holds, where in B.CACHE_INVENTORY:
        if where not in ("node", "side", "resource"):
            unclassified.append(path)
        real = path.replace("<slug>", PACK)
        base = ROOT / real
        if "*" in base.name:
            if not list(base.parent.glob(base.name)):
                missing.append(real)
        elif not base.exists():
            missing.append(real)
    check("every artefact in the cache inventory exists for fom",
          not missing, ", ".join(missing[:3]))
    check("and every one is placed on a node, a side table or a resource",
          not unclassified, ", ".join(unclassified[:3]))


def test_no_rule_vocabulary_phase5() -> None:
    import test_phase0a as t0a
    if "bench_planner.py" not in t0a.MODULES:
        t0a.MODULES = t0a.MODULES + ("bench_planner.py",)
    check("the Phase 0a check covers bench_planner.py",
          "bench_planner.py" in t0a.MODULES)
    t0a.PASSED.clear()
    t0a.test_no_rule_vocabulary()
    check("no mod / material / colour / rule vocabulary in the Phase 5 code",
          bool(t0a.PASSED))


def main() -> int:
    cache, out = ROOT / "cache", ROOT / "out"
    print("Phase 5 checks")
    print("-- the options preserve plans")
    test_memo_sharing_preserves_plans(cache, out)
    test_fold_pruning_preserves_plans(cache, out)
    test_budget_shapes(cache, out)
    test_counters_do_not_leak(cache, out)
    print("-- the harness")
    test_harness_runs(cache, out)
    test_cache_inventory(cache, out)
    print("-- design rules")
    test_no_rule_vocabulary_phase5()
    print(f"\n{len(PASSED)} checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
