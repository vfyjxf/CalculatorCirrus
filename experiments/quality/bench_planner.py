#!/usr/bin/env python
"""Phase 5 — the deep-cone benchmark and the caching study.

    ./.venv/bin/python bench_planner.py --stage cone   --packs fom
    ./.venv/bin/python bench_planner.py --stage build  --packs atm10
    ./.venv/bin/python bench_planner.py --stage memo   --packs fom
    ./.venv/bin/python bench_planner.py --stage fold   --packs fom
    ./.venv/bin/python bench_planner.py --stage budget --packs atm10

Five questions, one per stage:

* **cone**   how long does the deepest target of the deepest pack take
* **build**  what does a cold graph build cost, what does a warm one, and how
             many bytes would a Java runtime have to cache to get the warm one
* **memo**   what does sharing the empty-stock cost solve across requests buy
* **fold**   what does collapsing sibling producers of one template buy, and
             what does it cost in plan quality
* **budget** does a different budget *shape* beat the cone-scaled one on
             atm10's 15 % truncation rate without paying for it in p95

Nothing here changes a default plan: every option goes through
``planner.PlanOptions``, whose default is the Phase 3 code path byte for byte
(``tests/test_phase4.py``).  Same hard rules as every other module: no mod /
item / material / colour / system vocabulary, no join on recipe ids inside the
planner, deterministic sampling, ``time.perf_counter`` throughout.
"""
from __future__ import annotations

import argparse
import cProfile
import gc
import io
import json
import pstats
import sys
import time
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import eval_planner as E  # noqa: E402
import planner as P  # noqa: E402
from common import peak_rss_mb, read_json, write_json  # noqa: E402

#: Which of the Phase 0 artefacts the planner actually opens, and where a Java
#: runtime would put each one.  ``node`` = a field on ``RecipeGraph.Node``;
#: ``side`` = a side table indexed by node or by item; ``resource`` = shipped
#: in the jar, identical for every pack.
CACHE_INVENTORY = (
    ("cache/<slug>/recipes.parquet", "the graph itself", "node"),
    ("cache/<slug>/contents.parquet", "the graph itself", "node"),
    ("out/<slug>/recipe_template.parquet", "template_id per recipe", "node"),
    ("out/<slug>/economy/item_values.parquet", "value/tier/leaf/pool/acq "
     "per item", "side"),
    ("out/<slug>/economy/recipe_shape.parquet", "shape role, tiers, "
     "reliability per recipe", "node"),
    ("out/<slug>/economy/machines.parquet", "machine tier per machine",
     "side"),
    ("out/<slug>/economy/pools.parquet", "strong-pool membership", "side"),
    ("out/<slug>/economy/pool_edges.parquet", "conversion edges + unit cost",
     "side"),
    ("out/<slug>/planner/template_posteriors.parquet", "junk_p + role_p per "
     "template", "side"),
    ("out/<slug>/planner/producer_scores.parquet", "S(r) per recipe", "node"),
    ("out/features/template_features.parquet", "129 features per template "
     "(Phase 4 neighbourhoods only)", "side"),
    ("out/features/recipe_features.parquet", "pair features per recipe "
     "(Phase 4 re-score only)", "side"),
    ("out/models/*.json", "the exported models", "resource"),
)


def _q(v, scale=1.0):
    v = np.asarray(v, dtype=np.float64) * scale
    if not v.size:
        return {"n": 0, "median": 0.0, "p95": 0.0, "max": 0.0, "mean": 0.0}
    return {"n": int(v.size), "median": round(float(np.median(v)), 4),
            "p95": round(float(np.percentile(v, 95)), 4),
            "max": round(float(v.max()), 4),
            "mean": round(float(v.mean()), 4)}


def deep_and_random(ix: P.PackIndex, slug: str, pack: str, n_deep: int,
                    n_rand: int, seed: int) -> tuple:
    """``(deepest baseline targets, random targets)``.

    "Deepest" is the *baseline's* own verdict, read from ``index.tsv``
    (``depth`` then ``actions``), so the hard targets are chosen by the thing
    being compared against rather than by the prototype's own opinion.  The
    random sample is drawn from the same index, seeded.
    """
    pdir = E.pack_dir_for(slug, pack)
    rows = E.read_index(pdir) if pdir else []
    by_target: dict = {}
    for r in rows:
        try:
            key = (int(r.get("depth", 0)), int(r.get("actions", 0)))
        except ValueError:
            key = (0, 0)
        if r["target"] not in by_target or key > by_target[r["target"]][0]:
            by_target[r["target"]] = (key, r)
    live = [t for t in sorted(by_target) if ix.item_of(t) >= 0]
    deep = sorted(live, key=lambda t: (-by_target[t][0][0],
                                       -by_target[t][0][1], t))[:n_deep]
    rest = [t for t in live if t not in set(deep)]
    rng = np.random.default_rng(seed)
    if rest:
        pick = rng.choice(len(rest), size=min(n_rand, len(rest)),
                          replace=False)
        rand = [rest[i] for i in sorted(pick.tolist())]
    else:
        rand = []
    return deep, rand, {t: by_target[t][0] for t in live}


def run_targets(ix, pol, knobs, sv, units, targets, opt=None,
                warmup: int = 0, repeats: int = 1) -> dict:
    """Time one list of targets; return the timing block and the counters.

    ``warmup`` runs the first few targets untimed and ``repeats`` takes the
    **per-target minimum** over several passes.  Both are there because the
    measured spread between two identical variants on this machine is larger
    than the effect several of the stages below are looking for: the first
    pass over a pack is 20-30 % slower than every later one, and a single
    pass reports the scheduler as often as the code.
    """
    exp, trunc, acts, inc = [], 0, [], 0
    reasons: dict = {}
    best = [float("inf")] * len(targets)
    for t in targets[:warmup]:
        P.plan_target(ix, pol, knobs, t, 1, None, sv, units, opt)
    for rep in range(max(1, repeats)):
        for i, t in enumerate(targets):
            t0 = time.perf_counter()
            pl = P.plan_target(ix, pol, knobs, t, 1, None, sv, units, opt)
            best[i] = min(best[i], (time.perf_counter() - t0) * 1000.0)
            if rep:
                continue
            exp.append(P.PLAN_STATS["expansions"])
            acts.append(len(pl.actions))
            for u in pl.unresolved:
                reasons[u["reason"]] = reasons.get(u["reason"], 0) + 1
            if any(u["reason"] == "BUDGET_TRUNCATED" for u in pl.unresolved):
                trunc += 1
            if pl.status != "ok":
                inc += 1
    ms = best
    return {"wall_ms": _q(ms), "expansions": _q(exp), "actions": _q(acts),
            "budget_truncated": trunc, "incomplete": inc,
            "unresolved_reasons": dict(sorted(reasons.items())),
            "incomplete_rate": round(inc / len(targets), 4) if targets else 0.0,
            "truncation_rate": round(trunc / len(targets), 4) if targets
            else 0.0}


def stage_cone(args, cache_dir: Path, out_dir: Path, knobs: P.Knobs) -> dict:
    """The deep-cone benchmark: the baseline's 50 deepest targets, plus 150
    random ones, per pack."""
    packs = read_json(cache_dir / "packs.json")
    out = {"packs": []}
    for slug in P.pack_slugs(cache_dir, args.packs):
        gc.collect()
        t0 = time.perf_counter()
        ix = P.load_pack(cache_dir, out_dir, slug, knobs)
        t_index = time.perf_counter() - t0
        t0 = time.perf_counter()
        pol = P.Policy(ix, knobs)
        t_policy = time.perf_counter() - t0
        t0 = time.perf_counter()
        sv = P.solve_cost(ix, pol, knobs)
        t_solve = time.perf_counter() - t0
        units = P.pool_units(ix)
        deep, rand, depths = deep_and_random(ix, slug, packs[slug],
                                             args.deep, args.random,
                                             args.seed)
        row = {"slug": slug, "n_recipes": int(ix.n_recipes),
               "n_items": int(ix.n_items),
               "index_seconds": round(t_index, 3),
               "policy_seconds": round(t_policy, 3),
               "solve_seconds": round(t_solve, 3),
               "n_deep": len(deep), "n_random": len(rand),
               "baseline_depth_median": (
                   int(np.median([depths[t][0] for t in deep])) if deep else 0),
               "baseline_actions_median": (
                   int(np.median([depths[t][1] for t in deep])) if deep else 0),
               "deep": run_targets(ix, pol, knobs, sv, units, deep),
               "random": run_targets(ix, pol, knobs, sv, units, rand),
               "peak_rss_mb": round(peak_rss_mb(), 1)}
        out["packs"].append(row)
        _print_cone_row(row)
        if args.profile and slug == args.profile:
            out["profile"] = _profile(ix, pol, knobs, sv, units,
                                      deep[:args.profile_targets])
    return out


def _print_cone_row(row: dict) -> None:
    print(f"\n  {row['slug']}  ({row['n_recipes']:,} recipes, "
          f"{row['n_items']:,} items)  index {row['index_seconds']:.2f}s + "
          f"policy {row['policy_seconds']:.2f}s + solve "
          f"{row['solve_seconds']:.2f}s   peak RSS {row['peak_rss_mb']} MB")
    print(f"  {'targets':<28}{'n':>5}{'median ms':>11}{'p95 ms':>10}"
          f"{'max ms':>10}{'expansions':>12}{'actions':>9}{'trunc':>7}")
    for name in ("deep", "random"):
        b = row[name]
        label = (f"{name} (baseline depth "
                 f"{row['baseline_depth_median']})" if name == "deep"
                 else name)
        print(f"  {label:<28}{b['wall_ms']['n']:>5}"
              f"{b['wall_ms']['median']:>11.2f}{b['wall_ms']['p95']:>10.2f}"
              f"{b['wall_ms']['max']:>10.2f}"
              f"{b['expansions']['median']:>12.0f}"
              f"{b['actions']['median']:>9.0f}"
              f"{b['truncation_rate']:>7.3f}")
    reasons: dict = {}
    for name in ("deep", "random"):
        for k, v in row[name]["unresolved_reasons"].items():
            reasons[k] = reasons.get(k, 0) + v
    inc = row["deep"]["incomplete"] + row["random"]["incomplete"]
    print(f"  incomplete {inc}/{row['n_deep'] + row['n_random']}; unresolved "
          f"rows by reason: {reasons or 'none'}")


def _profile(ix, pol, knobs, sv, units, targets) -> list:
    """``cProfile`` over a handful of the deepest targets, top 15 by tottime."""
    pr = cProfile.Profile()
    pr.enable()
    for t in targets:
        P.plan_target(ix, pol, knobs, t, 1, None, sv, units)
    pr.disable()
    buf = io.StringIO()
    st = pstats.Stats(pr, stream=buf).sort_stats("tottime")
    st.print_stats(15)
    rows = []
    print(f"\n  cProfile, {len(targets)} deepest targets, top 15 by tottime")
    for line in buf.getvalue().splitlines():
        parts = line.split(None, 5)
        if len(parts) != 6 or not parts[0].replace("/", "").isdigit():
            continue
        try:
            tot, cum = float(parts[1]), float(parts[3])
        except ValueError:          # the "N function calls in X seconds" line
            continue
        rows.append({"ncalls": parts[0], "tottime": tot, "cumtime": cum,
                     "where": parts[5]})
        print(f"    {tot:>9.3f}s tot {cum:>9.3f}s cum  "
              f"{parts[0]:>12} calls  {parts[5]}")
    return rows


# ------------------------------------------------------- build-time breakdown


def _dir_bytes(path: Path, pattern: str = "*") -> int:
    if not path.exists():
        return 0
    return sum(p.stat().st_size for p in path.glob(pattern) if p.is_file())


def stage_build(args, cache_dir: Path, out_dir: Path, knobs: P.Knobs) -> dict:
    """Cold vs warm build, stage by stage, plus the on-disk cache inventory."""
    out = {"packs": []}
    for slug in P.pack_slugs(cache_dir, args.packs):
        packs = read_json(cache_dir / "packs.json")
        stages = {}
        gc.collect()
        rss0 = peak_rss_mb()
        t0 = time.perf_counter()
        ix = P.build_index(cache_dir, out_dir, slug, packs[slug], knobs)
        stages["index (parquet -> CSR)"] = time.perf_counter() - t0
        t0 = time.perf_counter()
        P.score_models(out_dir, ix, knobs, force=True)
        stages["posteriors + producer scores (cold)"] = time.perf_counter() - t0
        t0 = time.perf_counter()
        P.score_models(out_dir, ix, knobs, force=False)
        stages["posteriors + producer scores (warm)"] = time.perf_counter() - t0
        t0 = time.perf_counter()
        pol = P.Policy(ix, knobs)
        stages["candidate policy"] = time.perf_counter() - t0
        t0 = time.perf_counter()
        sv = P.solve_cost(ix, pol, knobs)
        stages["cost solve (empty stock)"] = time.perf_counter() - t0
        t0 = time.perf_counter()
        P.pool_units(ix)
        stages["pool units"] = time.perf_counter() - t0
        try:
            import feedback as F
            t0 = time.perf_counter()
            F.PackContext(out_dir, ix, knobs)
            stages["feedback side tables (Phase 4)"] = \
                time.perf_counter() - t0
        except Exception as exc:                       # pragma: no cover
            stages["feedback side tables (Phase 4)"] = float("nan")
            print(f"    (feedback side tables unavailable: {exc})")
        meta = read_json(out_dir / slug / "planner" / "planner-index-meta.json")
        sizes = {
            "cache/<slug>/*.parquet": _dir_bytes(cache_dir / slug, "*.parquet"),
            "out/<slug>/recipe_template.parquet": _dir_bytes(
                out_dir / slug, "recipe_template.parquet"),
            "out/<slug>/economy/*.parquet": _dir_bytes(
                out_dir / slug / "economy", "*.parquet"),
            "out/<slug>/planner/*": _dir_bytes(out_dir / slug / "planner"),
            "out/models/*.json": _dir_bytes(out_dir / "models", "*.json"),
        }
        row = {"slug": slug, "n_recipes": int(ix.n_recipes),
               "n_items": int(ix.n_items),
               "stages": {k: round(v, 3) for k, v in stages.items()},
               "cold_total": round(sum(
                   v for k, v in stages.items() if "warm" not in k), 3),
               "warm_total": round(sum(
                   v for k, v in stages.items() if "cold" not in k), 3),
               "sizes_bytes": sizes,
               "meta": {k: meta.get(k) for k in
                        ("template_seconds", "ranker_seconds",
                         "ranker_groups", "n_kept", "n_or", "n_machines")},
               "rss_before_mb": round(rss0, 1),
               "peak_rss_mb": round(peak_rss_mb(), 1),
               "n_settled": int(sum(1 for c in sv.cost[:ix.n_items]
                                    if c < float("inf")))}
        out["packs"].append(row)
        print(f"\n  {slug}  ({ix.n_recipes:,} recipes)")
        print(f"  {'stage':<44}{'seconds':>10}")
        for k, v in row["stages"].items():
            print(f"  {k:<44}{v:>10.3f}")
        print(f"  {'cold total (without the warm re-read)':<44}"
              f"{row['cold_total']:>10.3f}")
        print(f"  {'warm total (scores read from parquet)':<44}"
              f"{row['warm_total']:>10.3f}")
        print(f"\n  {'on disk':<44}{'bytes':>14}{'MB':>9}")
        for k, v in sizes.items():
            print(f"  {k:<44}{v:>14,}{v / 1e6:>9.2f}")
        print(f"  peak RSS {row['peak_rss_mb']} MB")
    print(f"\n  cache inventory -> Java placement (JAVA-DESIGN.md §4)")
    print(f"  {'artefact':<52}{'holds':<48}{'where'}")
    for path, holds, where in CACHE_INVENTORY:
        print(f"  {path:<52}{holds:<48}{where}")
    out["inventory"] = [{"artefact": a, "holds": h, "java": w}
                        for a, h, w in CACHE_INVENTORY]
    return out


# ---------------------------------------------- the cross-request cost memo


def _plan_blob(pl: P.Plan) -> str:
    j = P.plan_json(pl)
    j["seconds"] = 0.0
    j["metrics"]["wall_ms"] = 0.0
    j.pop("options", None)
    return json.dumps(j, sort_keys=True, separators=(",", ":"))


def stage_memo(args, cache_dir: Path, out_dir: Path, knobs: P.Knobs) -> dict:
    """What sharing the empty-stock cost solve across requests buys.

    Three variants over the same targets:

    ``per request``  every request re-solves — what a naive ``RoutePlanner``
                     that owns no cache would do
    ``shared memo``  ``PlanOptions(share_memo=True)``: the memo key is
                     (pack, policy, knobs, feedback, stock), so every
                     empty-stock request of a pack hits after the first
    ``hoisted``      the Phase 3 harness's own path: one solve passed in by
                     the caller — the upper bound the memo is chasing

    The plans of all three must be identical, and that is asserted rather than
    assumed.
    """
    packs = read_json(cache_dir / "packs.json")
    out = {"packs": []}
    for slug in P.pack_slugs(cache_dir, args.packs):
        ix = P.load_pack(cache_dir, out_dir, slug, knobs)
        pol = P.Policy(ix, knobs)
        units = P.pool_units(ix)
        deep, rand, _ = deep_and_random(ix, slug, packs[slug], args.deep,
                                        args.random, args.seed)
        targets = rand or deep
        blobs: dict = {}
        res = {}
        for name, opt in (("per request", None),
                          ("shared memo", P.PlanOptions(share_memo=True))):
            P.clear_solve_memo()
            ms, out_blobs = [], []
            for t in targets:
                t0 = time.perf_counter()
                pl = P.plan_target(ix, pol, knobs, t, 1, None, None, units,
                                   opt)
                ms.append((time.perf_counter() - t0) * 1000.0)
                out_blobs.append(_plan_blob(pl))
            res[name] = {"wall_ms": _q(ms),
                         "total_s": round(sum(ms) / 1000.0, 3),
                         "memo": dict(P.SOLVE_MEMO_STATS)}
            blobs[name] = out_blobs
        sv = P.solve_cost(ix, pol, knobs)
        ms, out_blobs = [], []
        for t in targets:
            t0 = time.perf_counter()
            pl = P.plan_target(ix, pol, knobs, t, 1, None, sv, units)
            ms.append((time.perf_counter() - t0) * 1000.0)
            out_blobs.append(_plan_blob(pl))
        res["hoisted"] = {"wall_ms": _q(ms),
                          "total_s": round(sum(ms) / 1000.0, 3),
                          "memo": {"hits": 0, "misses": 0, "stored": 0}}
        blobs["hoisted"] = out_blobs
        same = (blobs["per request"] == blobs["shared memo"]
                == blobs["hoisted"])
        hits = res["shared memo"]["memo"]["hits"]
        n = len(targets)
        row = {"slug": slug, "n_targets": n, "variants": res,
               "hit_rate": round(hits / n, 4) if n else 0.0,
               "speedup_vs_per_request": round(
                   res["per request"]["total_s"]
                   / max(res["shared memo"]["total_s"], 1e-9), 2),
               "plans_identical": bool(same)}
        out["packs"].append(row)
        print(f"\n  {slug}  ({n} targets, {ix.n_recipes:,} recipes)")
        print(f"  {'variant':<16}{'total s':>10}{'median ms':>12}"
              f"{'p95 ms':>10}{'memo hits':>11}{'misses':>9}")
        for name in ("per request", "shared memo", "hoisted"):
            v = res[name]
            print(f"  {name:<16}{v['total_s']:>10.3f}"
                  f"{v['wall_ms']['median']:>12.3f}{v['wall_ms']['p95']:>10.3f}"
                  f"{v['memo']['hits']:>11}{v['memo']['misses']:>9}")
        print(f"  hit rate {row['hit_rate']:.3f}, speed-up "
              f"{row['speedup_vs_per_request']}x, plans identical "
              f"{row['plans_identical']}")
        assert same, f"{slug}: memo sharing changed a plan"
    P.clear_solve_memo()
    return out


# --------------------------------------------------------- fold-based pruning


def stage_fold(args, cache_dir: Path, out_dir: Path, knobs: P.Knobs) -> dict:
    """Collapse sibling producers that share a ``template_id`` to one.

    The representative is the best producer score, ties by cost then
    ``recipe_idx`` — the same order the shortlist uses, applied one step
    earlier.  Measured on wall *and* on the A/B metrics *and* on the audit
    cases, because a pruning that loses a case is not a speed-up.
    """
    import feedback as F
    packs = read_json(cache_dir / "packs.json")
    cases = F._cases_for(out_dir, P.pack_slugs(cache_dir, args.packs))
    keys = ("actions", "distinct_recipes", "crafts", "raw_kinds", "depth",
            "junk_in_path_wl", "junk_in_path_model", "round_trips",
            "tier_reversals", "incomplete", "raw_nonleaf")
    out = {"packs": [], "cases": []}
    for slug in P.pack_slugs(cache_dir, args.packs):
        ix = P.load_pack(cache_dir, out_dir, slug, knobs)
        units = P.pool_units(ix)
        wl = E.weak_labels(packs[slug])
        deep, rand, _ = deep_and_random(ix, slug, packs[slug], args.deep,
                                        args.random, args.seed)
        targets = sorted(set(deep) | set(rand))
        res, changed = {}, 0
        blobs, execs = {}, {}
        for name, opt in (("default", None),
                          ("folded", P.PlanOptions(fold_templates=True))):
            pol = P.Policy(ix, knobs, opt)
            sv = P.solve_cost(ix, pol, knobs, None, opt)
            timed = run_targets(ix, pol, knobs, sv, units, targets, opt,
                                warmup=min(25, len(targets)),
                                repeats=args.repeats)
            acc, bl, ex = {k: 0.0 for k in keys}, [], []
            for t in targets:
                pl = P.plan_target(ix, pol, knobs, t, 1, None, sv, units, opt)
                m = E.chain_metrics(ix, pol, knobs, units,
                                    E.plan_actions(ix, pl), pl.raw,
                                    pl.unresolved, t, wl, 0.0)
                for k in keys:
                    acc[k] += float(m[k])
                bl.append(_plan_blob(pl))
                ex.append([a["recipe_idx"] for a in pl.actions])
            execs[name] = ex
            res[name] = {"wall_ms": timed["wall_ms"],
                         "total_s": round(
                             timed["wall_ms"]["mean"] * len(targets) / 1000.0,
                             3),
                         "metrics": {k: round(v / len(targets), 4)
                                     for k, v in acc.items()}}
            blobs[name] = bl
        changed = sum(1 for a, b in zip(blobs["default"], blobs["folded"])
                      if a != b)
        # A blob differs as soon as the *explanation* differs (a folded-away
        # sibling appears in `why.excluded`).  What matters is whether the
        # plan the user executes differs, so both are counted.
        acts_changed = sum(1 for a, b in zip(execs["default"], execs["folded"])
                           if a != b)
        row = {"slug": slug, "n_targets": len(targets), "variants": res,
               "plans_changed": changed, "action_lists_changed": acts_changed,
               "speedup": round(res["default"]["total_s"]
                                / max(res["folded"]["total_s"], 1e-9), 3)}
        out["packs"].append(row)
        print(f"\n  {slug}  ({len(targets)} targets)")
        print(f"  {'variant':<10}{'total s':>10}{'median ms':>12}{'p95 ms':>10}"
              + "".join(f"{k[:11]:>12}" for k in keys[:6]))
        for name in ("default", "folded"):
            v = res[name]
            print(f"  {name:<10}{v['total_s']:>10.3f}"
                  f"{v['wall_ms']['median']:>12.3f}{v['wall_ms']['p95']:>10.3f}"
                  + "".join(f"{v['metrics'][k]:>12.4f}" for k in keys[:6]))
        print(f"  speed-up {row['speedup']}x, {acts_changed}/{len(targets)} "
              f"action lists changed ({changed} explanations changed)")
        if slug in cases:
            for name, opt in (("default", None),
                              ("folded", P.PlanOptions(fold_templates=True))):
                pol = P.Policy(ix, knobs, opt)
                sv = P.solve_cost(ix, pol, knobs, None, opt)
                for (finding, output, chosen), c in sorted(cases[slug].items()):
                    v = F.case_verdict(ix, knobs, units, output, chosen,
                                       c["preferred"], pol, sv, opt)
                    out["cases"].append({"slug": slug, "config": name,
                                         "finding_id": finding,
                                         "output_base_id": output,
                                         "verdict": v["verdict"]})
    if out["cases"]:
        print(f"\n  audit cases under fold pruning")
        print(f"  {'finding':<26}{'output':<44}{'default':<11}{'folded':<11}")
        byk = {}
        for r in out["cases"]:
            byk.setdefault((r["finding_id"], r["output_base_id"]),
                           {})[r["config"]] = r["verdict"]
        for (f, o), v in sorted(byk.items()):
            flag = "" if v.get("default") == v.get("folded") else "   CHANGED"
            print(f"  {f:<26}{o:<44}{v.get('default', '-'):<11}"
                  f"{v.get('folded', '-'):<11}{flag}")
    return out


# ------------------------------------------------------------- budget shapes


def stage_budget(args, cache_dir: Path, out_dir: Path, knobs: P.Knobs) -> dict:
    """Three budget shapes over the same targets.

    ``cone``   base 512 + 4 per admissible producer in the demand cone,
               capped at 200 000 — Phase 3's default
    ``depth``  base 512 x (1 + cone hop depth)^2, same cap, same lazy walk
    ``time``   no expansion ceiling; the stop is a wall-clock budget

    A variant replaces the default only if it dominates on *both* truncation
    rate and p95 wall.
    """
    packs = read_json(cache_dir / "packs.json")
    out = {"packs": []}
    for slug in P.pack_slugs(cache_dir, args.packs):
        ix = P.load_pack(cache_dir, out_dir, slug, knobs)
        pol = P.Policy(ix, knobs)
        sv = P.solve_cost(ix, pol, knobs)
        units = P.pool_units(ix)
        deep, rand, _ = deep_and_random(ix, slug, packs[slug], args.deep,
                                        args.random, args.seed)
        targets = sorted(set(deep) | set(rand))
        row = {"slug": slug, "n_targets": len(targets), "bases": {}}
        print(f"\n  {slug}  ({len(targets)} targets: {len(deep)} deepest + "
              f"{len(rand)} random)")
        for base_val in args.budget_bases:
            kn = P.Knobs(**{**knobs.as_dict(), "budget_base": base_val})
            variants = [("cone (default shape)", None),
                        ("depth", P.PlanOptions(budget_shape="depth"))]
            for sec in args.budget_seconds:
                variants.append((f"time {sec:g}s",
                                 P.PlanOptions(budget_shape="time",
                                               budget_seconds=sec)))
            res = {}
            for name, opt in variants:
                res[name] = run_targets(ix, pol, kn, sv, units, targets, opt,
                                        warmup=min(25, len(targets)),
                                        repeats=args.repeats)
            tag = ("default" if base_val == P.Knobs().budget_base
                   else "stress")
            print(f"\n  budget_base = {base_val} ({tag})")
            print(f"  {'budget shape':<20}{'truncated':>11}{'rate':>8}"
                  f"{'incomplete':>12}{'median ms':>11}{'p95 ms':>10}"
                  f"{'max ms':>10}{'actions med':>13}{'exp p95':>10}")
            for name, _ in variants:
                v = res[name]
                print(f"  {name:<20}{v['budget_truncated']:>11}"
                      f"{v['truncation_rate']:>8.3f}"
                      f"{v['incomplete_rate']:>12.3f}"
                      f"{v['wall_ms']['median']:>11.2f}"
                      f"{v['wall_ms']['p95']:>10.2f}"
                      f"{v['wall_ms']['max']:>10.2f}"
                      f"{v['actions']['median']:>13.0f}"
                      f"{v['expansions']['p95']:>10.0f}")
            b = res["cone (default shape)"]
            wins = [n for n, _ in variants[1:]
                    if res[n]["truncation_rate"] < b["truncation_rate"]
                    and res[n]["wall_ms"]["p95"] <= b["wall_ms"]["p95"]]
            print(f"  strictly better on truncation and no worse on p95: "
                  f"{wins or 'none — the default shape stands'}")
            row["bases"][str(base_val)] = {
                "variants": res, "dominating": wins,
                "reasons": b["unresolved_reasons"]}
        out["packs"].append(row)
    return out
def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--cache-dir", default=str(HERE / "cache"))
    ap.add_argument("--out-dir", default=str(HERE / "out"))
    ap.add_argument("--packs", default="fom")
    ap.add_argument("--stage", default="cone",
                    choices=("cone", "build", "memo", "fold", "budget",
                             "all"))
    ap.add_argument("--deep", type=int, default=50)
    ap.add_argument("--random", type=int, default=150)
    ap.add_argument("--seed", type=int, default=20260920)
    ap.add_argument("--profile", default=None,
                    help="slug to run cProfile over in the cone stage")
    ap.add_argument("--profile-targets", type=int, default=10)
    ap.add_argument("--repeats", type=int, default=3)
    ap.add_argument("--budget-bases", type=int, nargs="*",
                    default=[512, 64, 8])
    ap.add_argument("--budget-seconds", type=float, nargs="*",
                    default=[0.05, 0.25])
    ap.add_argument("--report", default=None)
    args = ap.parse_args(argv)
    cache_dir, out_dir = Path(args.cache_dir), Path(args.out_dir)
    knobs = P.Knobs()
    stages = (("cone", "build", "memo", "fold", "budget")
              if args.stage == "all" else (args.stage,))
    report = {}
    for name in stages:
        print(f"\n== stage {name}")
        t0 = time.perf_counter()
        report[name] = globals()["stage_" + name](
            args, cache_dir, out_dir, knobs)
        print(f"  ({time.perf_counter() - t0:.1f}s)")
    if args.report:
        write_json(Path(args.report), report)
        print(f"\n  wrote {args.report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
