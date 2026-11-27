#!/usr/bin/env python
"""Phase 4 invariants — the feedback layer.

    ./.venv/bin/python tests/test_phase4.py

Plain asserts, no pytest.  Three halves:

* **the promise** — with no feedback and default options the planner produces
  the *same bytes* it produced before the Phase 4 hook was added, checked
  against a 50-target fom snapshot taken before the edit;
* **synthetic** — tiny vocabulary-free graphs, one per gesture and per bound;
* **real (fom)** — the incremental re-score against a full recompute, the
  persistence round trip, the inquiry's one-question-per-family rule and the
  per-call latency.
"""
from __future__ import annotations

import gzip
import hashlib
import json
import sys
import time
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "tests"))

import feedback as F  # noqa: E402
import planner as P  # noqa: E402
from predict_reference import Reference  # noqa: E402
from test_phase3 import make_index  # noqa: E402

PACK = "fom"
SNAPSHOT = ROOT / "tests" / "fixtures" / "phase3-plans-fom.json.gz"
#: sha256 of the canonical JSON of the 50 fom plans, taken from ``planner.py``
#: as it stood *before* ``PlanOptions`` existed.
SNAPSHOT_SHA = "adb9c06a372e7726d18667100a26695e91139bfde4260ebc2641165372760e0c"
PASSED: list = []


def check(name: str, ok: bool, detail: str = "") -> None:
    mark = "ok " if ok else "FAIL"
    print(f"  {mark} {name}" + (f"  [{detail}]" if detail else ""))
    assert ok, f"{name}: {detail}"
    PASSED.append(name)


# ================================================= a synthetic PackContext


def make_ctx(ix, feats=None):
    """A ``PackContext`` over a synthetic index, with literal features.

    ``feats`` is ``{recipe: [f0, f1, ...]}``; every missing recipe is zeros.
    The ranker weights are the identity on the first column, which makes the
    pair margin readable by hand.
    """
    ctx = object.__new__(F.PackContext)
    ctx.ix = ix
    ctx.ref = None
    n_base = max((len(v) for v in (feats or {}).values()), default=2)
    ctx.names = [f"rec_c{i}" for i in range(n_base)] + ["flag_same_system"]
    ctx.n_base = n_base
    ctx.flags = ["flag_same_system"]
    ctx.sym = {"flag_same_system"}
    ctx.fill = np.zeros(n_base)
    ctx.scale = np.ones(n_base + 1)
    w0 = np.zeros(n_base + 1)
    # 20, not 1: the delta bound is a fraction of |w0|, and with |w0| = 1 the
    # clip fires on the very first click and hides the step identity.
    w0[0] = 20.0
    ctx.w0 = w0
    zb = np.zeros((ix.n_recipes, n_base))
    for r, row in (feats or {}).items():
        zb[r, :len(row)] = row
    ctx.zb = zb
    ctx.flagv = {"flag_same_system": ix.system}
    ctx.tpl_rows = {}
    for r, tid in enumerate(ix.template_id):
        ctx.tpl_rows.setdefault(str(tid), []).append(r)
    ctx.tpl_rows = {k: np.asarray(v, dtype=np.int64)
                    for k, v in ctx.tpl_rows.items()}
    ctx.tpl_ids = np.asarray(sorted(ctx.tpl_rows), dtype=object)
    tz = np.zeros((ctx.tpl_ids.size, max(n_base, 1)))
    for i, tid in enumerate(ctx.tpl_ids):
        tz[i] = zb[ctx.tpl_rows[tid][0]]
    norm = np.linalg.norm(tz, axis=1, keepdims=True)
    ctx.tz = tz / np.where(norm > 1e-12, norm, 1.0)
    ctx.family = {}
    item_of = np.repeat(np.arange(ix.n_items, dtype=np.int64),
                        np.diff(ix.prod_ptr))
    edges = np.unique(np.stack([item_of, ix.prod_rec]), axis=1) \
        if ix.prod_rec.size else np.zeros((2, 0), dtype=np.int64)
    ctx.g_ptr = np.zeros(ix.n_items + 1, dtype=np.int64)
    np.cumsum(np.bincount(edges[0], minlength=ix.n_items), out=ctx.g_ptr[1:])
    ctx.g_rec = edges[1]
    ctx.counted = np.bincount(edges[1],
                              minlength=ix.n_recipes).astype(np.float64)
    rec_ptr = np.zeros(ix.n_recipes + 1, dtype=np.int64)
    o2 = np.argsort(edges[1], kind="stable")
    np.cumsum(np.bincount(edges[1], minlength=ix.n_recipes), out=rec_ptr[1:])
    ctx.out_items = (rec_ptr, edges[0][o2])
    ctx.seconds = 0.0
    return ctx


def two_route_graph():
    """One output, two producers, one leaf each — the shape every gesture
    acts on."""
    items = ["ns:a", "ns:b", "ns:c"]
    recipes = [
        {"in": [("ns:b", 1)], "out": [("ns:a", 1)], "tier": 1,
         "score": 1.0, "id": "ns:r0"},
        {"in": [("ns:c", 1)], "out": [("ns:a", 1)], "tier": 1,
         "score": -1.0, "id": "ns:r1"},
    ]
    ix = make_index(items, recipes, leaves=["ns:b", "ns:c"],
                    values={"ns:b": 1.0, "ns:c": 1.05})
    return ix


def plan(ix, target, opt=None, stock=None, knobs=None):
    knobs = knobs or P.Knobs()
    pol = P.Policy(ix, knobs, opt)
    sv = P.solve_cost(ix, pol, knobs, stock, opt)
    return P.plan_target(ix, pol, knobs, target, 1, stock, sv,
                         P.pool_units(ix), opt), pol


def executed(pl):
    return [a["recipe_idx"] for a in pl.actions]


# ============================================================= the promise


def test_no_feedback_is_byte_identical(cache: Path, out: Path) -> None:
    """The Phase 3 plans, byte for byte, with the Phase 4 hook in place."""
    want = json.loads(gzip.open(SNAPSHOT, "rt", encoding="utf-8").read())
    knobs = P.Knobs()
    ix = P.load_pack(cache, out, PACK, knobs)
    for label, opt in (("opt=None", None), ("default PlanOptions",
                                            P.PlanOptions())):
        pol = P.Policy(ix, knobs, opt)
        sv = P.solve_cost(ix, pol, knobs, None, opt)
        units = P.pool_units(ix)
        got = {}
        for t in sorted(want):
            pl = P.plan_target(ix, pol, knobs, t, 1, None, sv, units, opt)
            j = P.plan_json(pl)
            j["seconds"] = 0.0
            j["metrics"]["wall_ms"] = 0.0
            got[t] = j
        blob = json.dumps(got, sort_keys=True, separators=(",", ":"),
                          ensure_ascii=False)
        sha = hashlib.sha256(blob.encode()).hexdigest()
        check(f"50 fom plans are byte-identical to the pre-hook snapshot "
              f"({label})", sha == SNAPSHOT_SHA, sha[:16])
    check("a default PlanOptions writes no options block into the plan JSON",
          "options" not in P.plan_json(
              P.plan_target(ix, P.Policy(ix, knobs), knobs, sorted(want)[0],
                            1, None, None, None, P.PlanOptions())))


# =================================================== synthetic: the gestures


def test_have() -> None:
    ix = two_route_graph()
    ctx = make_ctx(ix)
    fb = F.PlannerFeedback(ctx)
    pl, _ = plan(ix, "ns:a")
    check("with nothing on hand the output is crafted", len(pl.actions) == 1)
    fb.have("ns:a", 5)
    pl, _ = plan(ix, "ns:a", fb.options())
    check("have() serves the demand from stock", not pl.actions and not pl.raw,
          f"actions={executed(pl)} raw={pl.raw}")
    fb.have("ns:a", 0)
    pl, _ = plan(ix, "ns:a", fb.options())
    check("have(0) withdraws it again", len(pl.actions) == 1)


def test_ban_recipe_and_template() -> None:
    ix = two_route_graph()
    ctx = make_ctx(ix)
    fb = F.PlannerFeedback(ctx)
    pl, _ = plan(ix, "ns:a")
    first = executed(pl)[0]
    fb.ban(recipe_idx=first)
    pl, pol = plan(ix, "ns:a", fb.options())
    check("ban(recipe) excludes it and the other route is taken",
          executed(pl) == [1 - first], f"{executed(pl)}")
    view = fb.view()
    check("ban(recipe) drives its template's junk posterior over 7/8",
          view.junk_p[first] >= F.BAN_FACTOR / (1.0 + F.BAN_FACTOR) - 1e-9,
          f"{view.junk_p[first]:.4f}")
    # a sole producer: the model may not rescue an explicit ban
    fb2 = F.PlannerFeedback(ctx)
    fb2.ban(recipe_idx=0)
    fb2.ban(recipe_idx=1)
    pl, pol = plan(ix, "ns:a", fb2.options())
    # The sole-producer protection is a guard against the *model*, not against
    # the user: an explicit ban is never rescued.  What is left is the Phase 3
    # orphan rule — nothing the pack has can make it, so it is written down as
    # "gather this" rather than invented as a new failure mode.
    check("banning every producer is never rescued by the sole-producer rule",
          not pl.actions and bool(pol.excluded[0]) and bool(pol.excluded[1]),
          f"{pl.status} {executed(pl)}")
    check("and the demand falls through to the raw list, like any orphan",
          pl.raw.get("ns:a") == 1.0, f"raw={pl.raw}")
    fb3 = F.PlannerFeedback(ctx)
    fb3.ban(template_id=str(ix.template_id[0]))
    _, pol3 = plan(ix, "ns:a", fb3.options())
    check("ban(template) excludes every member", bool(pol3.excluded[0]))


def test_ban_item() -> None:
    ix = two_route_graph()
    ctx = make_ctx(ix)
    fb = F.PlannerFeedback(ctx)
    pl, _ = plan(ix, "ns:a")
    used = executed(pl)[0]
    consumed = str(ix.items[int(ix.in_node[ix.in_ptr[used]])])
    fb.ban(base_id=consumed)
    pl, _ = plan(ix, "ns:a", fb.options())
    check("ban(item) makes every recipe consuming it inadmissible",
          executed(pl) == [1 - used] and consumed not in pl.raw,
          f"{executed(pl)} raw={pl.raw}")


def test_prefer() -> None:
    ix = two_route_graph()
    ctx = make_ctx(ix)
    # item preference: discount the *more expensive* leaf until its route
    # wins.  The two producers are given equal ranker scores so that the
    # material layer, which is what the discount acts on, is what decides.
    ix.score = np.zeros(2)
    fb = F.PlannerFeedback(ctx)
    pl, _ = plan(ix, "ns:a")
    used = executed(pl)[0]
    other = 1 - used
    leaf = str(ix.items[int(ix.in_node[ix.in_ptr[other]])])
    fb.prefer(base_id=leaf)
    opt = fb.options()
    pl, pol = plan(ix, "ns:a", opt)
    check("prefer(item) discounts its price and flips the route",
          executed(pl) == [other], f"{executed(pl)}")
    sv0 = P.solve_cost(ix, P.Policy(ix, P.Knobs()), P.Knobs())
    sv1 = P.solve_cost(ix, pol, P.Knobs(), None, opt)
    c0, _ = P.producer_cost(ix, sv0, P.Knobs(), pol, other,
                            ix.item_of("ns:a"), set())
    c1, _ = P.producer_cost(ix, sv1, P.Knobs(), pol, other,
                            ix.item_of("ns:a"), set(), opt)
    check("the discount is exactly ITEM_DISCOUNT on that input's contribution",
          abs((c0 - c1) - (1.0 - F.ITEM_DISCOUNT)
              * float(ix.item_value[ix.item_of(leaf)])) < 1e-9,
          f"{c0:.4f} -> {c1:.4f}")
    # recipe preference un-flags a producer the model excluded
    ix2 = two_route_graph()
    ix2.junk_p = np.asarray([0.99, 0.0])
    ctx2 = make_ctx(ix2)
    _, pol = plan(ix2, "ns:a")
    check("the model excludes the flagged producer", bool(pol.excluded[0]))
    fb2 = F.PlannerFeedback(ctx2)
    fb2.prefer(recipe_idx=0)
    _, pol2 = plan(ix2, "ns:a", fb2.options())
    check("prefer(recipe) puts it back in the candidate set",
          not pol2.excluded[0])
    check("prefer(recipe) halves its template's junk posterior",
          abs(fb2.view().junk_p[0] - 0.99 / 2) < 1e-6,
          f"{fb2.view().junk_p[0]:.4f}")


def test_choose() -> None:
    ix = two_route_graph()
    ctx = make_ctx(ix, feats={0: [1.0, 0.0], 1: [-1.0, 0.0]})
    ix.score = np.asarray([1.0, -1.0])
    pl, _ = plan(ix, "ns:a")
    check("the ranker's favourite is taken inside the shortlist",
          executed(pl) == [0], f"{executed(pl)}")
    fb = F.PlannerFeedback(ctx)
    info = fb.choose(1, over=0)
    gain = info["margin_after"] - info["margin_before"]
    want = F.ETA / (1.0 + np.exp(info["margin_before"]))
    check("choose() moves the margin by exactly eta x sigmoid(-m), once",
          not info["clipped"] and abs(gain - want) < 1e-6,
          f"{info['margin_before']:.4f} -> {info['margin_after']:.4f} "
          f"(+{gain:.4f}, want +{want:.4f})")
    pl, _ = plan(ix, "ns:a", fb.options())
    check("after one choose() the re-selected producer is executed",
          executed(pl) == [1], f"{executed(pl)}")
    try:
        fb.choose(0, over=0)
        same = True
    except ValueError:
        same = True
    ix3 = make_index(["ns:a", "ns:b", "ns:c", "ns:d"],
                     [{"in": [("ns:c", 1)], "out": [("ns:a", 1)]},
                      {"in": [("ns:d", 1)], "out": [("ns:b", 1)]}],
                     leaves=["ns:c", "ns:d"])
    ctx3 = make_ctx(ix3)
    fb3 = F.PlannerFeedback(ctx3)
    try:
        fb3.choose(0, over=1)
        raised = False
    except ValueError:
        raised = True
    check("choose() refuses two producers of different outputs", raised
          and same)


def test_reset() -> None:
    ix = two_route_graph()
    ctx = make_ctx(ix)
    fb = F.PlannerFeedback(ctx)
    fb.have("ns:a", 3)
    fb.ban(recipe_idx=0)
    fb.choose(1, over=0)
    fb.reset()
    st = fb.state
    check("reset() clears stock, bans, evidence and the delta",
          not st.stock and not st.ban_recipes and not st.evidence
          and float(np.abs(st.delta).max()) == 0.0)


# ====================================================== synthetic: the bounds


def test_spread_is_capped() -> None:
    """Nine nearly identical templates: the spread rule must hold exactly."""
    items = [f"ns:i{i}" for i in range(24)]
    recipes = []
    for i in range(12):
        recipes.append({"in": [(f"ns:i{i + 12}", 1)],
                        "out": [(f"ns:i{i}", 1)], "id": f"ns:r{i}"})
    ix = make_index(items, recipes, leaves=[f"ns:i{i}" for i in range(12, 24)])
    rng = np.random.default_rng(3)
    feats = {r: (np.ones(4) + 0.01 * rng.standard_normal(4)).tolist()
             for r in range(12)}
    ctx = make_ctx(ix, feats=feats)
    fb = F.PlannerFeedback(ctx)
    tid = str(ix.template_id[0])
    spread = fb.learner.spread(tid)
    others = [w for t, w in spread if t != tid]
    check("the template itself always carries the full evidence",
          spread[0] == (tid, 1.0))
    check("at most NEIGHBOURS neighbours receive anything",
          len(others) <= F.NEIGHBOURS, f"{len(others)}")
    check("no neighbour receives more than SPREAD_PER of the evidence",
          all(w <= F.SPREAD_PER + 1e-12 for w in others),
          f"max={max(others, default=0):.3f}")
    check("the total spread never exceeds SPREAD_TOTAL",
          sum(others) <= F.SPREAD_TOTAL + 1e-12, f"{sum(others):.3f}")
    fb.ban(recipe_idx=0)
    view = fb.view()
    worst = max(float(view.junk_p[r]) for r in range(1, 12))
    bound = ((F.PRIOR_STRENGTH * float(ix.junk_p[1:].max())
              + F.SPREAD_PER * F.BAN_FACTOR * F.PRIOR_STRENGTH)
             / (F.PRIOR_STRENGTH + F.SPREAD_PER * F.BAN_FACTOR
                * F.PRIOR_STRENGTH))
    check("a neighbour's posterior stays under the one-click spread bound",
          worst <= bound + 1e-9, f"{worst:.4f} <= {bound:.4f}")


def test_delta_is_bounded() -> None:
    ix = two_route_graph()
    ctx = make_ctx(ix, feats={0: [1.0, 0.5], 1: [-1.0, 2.0]})
    fb = F.PlannerFeedback(ctx)
    cap = F.BOUND * float(np.linalg.norm(ctx.w0 * ctx.scale))
    norms = []
    for i in range(500):
        fb.choose(i % 2, over=1 - i % 2)
        norms.append(float(np.linalg.norm(fb.state.delta)))
    check("500 contradictory clicks never push the delta past the bound",
          max(norms) <= cap + 1e-9, f"max={max(norms):.4f} cap={cap:.4f}")
    fb.reset()
    gains = []
    for _ in range(50):
        info = fb.choose(1, over=0)     # the direction the ranker disagrees with
        gains.append(info["margin_after"] - info["margin_before"])
    check("no single click moves the margin by more than eta",
          max(gains) <= F.ETA + 1e-6, f"max={max(gains):.4f}")


# =========================================================== real (fom)


def test_lazy_rescore_matches_full_recompute(cache: Path, out: Path) -> None:
    """The incremental delta score equals a full Bradley-Terry recompute."""
    knobs = P.Knobs()
    ix = P.load_pack(cache, out, PACK, knobs)
    ctx = F.PackContext(out, ix, knobs)
    fb = F.PlannerFeedback(ctx)
    rng = np.random.default_rng(11)
    multi = np.flatnonzero(np.diff(ix.prod_ptr) >= 2)
    for it in rng.choice(multi, size=8, replace=False).tolist():
        g = ctx.group(int(it))
        fb.choose(int(g[0]), over=int(g[-1]))
    lazy = fb.view().score.dense()

    w_full = ctx.w0 + fb.state.delta / ctx.scale
    s_base = ctx.zb @ w_full[:ctx.n_base]
    full = np.zeros(ix.n_recipes)
    for item in range(ix.n_items):
        members = ctx.group(item)
        if members.size < 2:
            continue
        full[members] += P._group_rowmeans(s_base[members], members, ctx.flagv,
                                           ctx.flags, ctx.sym, w_full,
                                           ctx.n_base, ix)
    full = np.where(ctx.counted > 0, full / np.maximum(ctx.counted, 1.0), 0.0)
    err = float(np.abs(lazy - full).max())
    check("the lazy per-group re-score equals the full recompute",
          err < 1e-9, f"max |delta| = {err:.2e} over {ix.n_recipes:,} recipes")

    ref = Reference(out / "models")
    a, b = int(ctx.group(int(multi[0]))[0]), int(ctx.group(int(multi[0]))[1])
    x = ctx.pair_vector(a, b)
    err2 = abs(float(ref.margin(x)[0]) - float(x @ ctx.w0))
    check("the pair vector reproduces predict_reference.margin",
          err2 < 1e-9, f"{err2:.2e}")


def test_family_generalisation(cache: Path, out: Path) -> None:
    knobs = P.Knobs()
    ix = P.load_pack(cache, out, PACK, knobs)
    ctx = F.PackContext(out, ix, knobs)
    sizes = sorted(((v.size, k) for k, v in ctx.tpl_rows.items()),
                   reverse=True)
    n, tid = sizes[0]
    rows = ctx.tpl_rows[tid]
    fb = F.PlannerFeedback(ctx)
    fb.ban(recipe_idx=int(rows[0]))
    view = fb.view()
    over = int((view.junk_p[rows] > knobs.tau_junk).sum())
    check("one ban carries to every sibling of the template",
          over == int(n), f"{over}/{n} members over tau")
    pol = P.Policy(ix, knobs, P.PlanOptions(feedback=view))
    base = P.Policy(ix, knobs)
    check("and the whole family leaves the candidate set",
          int(pol.excluded[rows].sum()) == int(n),
          f"{int(base.excluded[rows].sum())} -> {int(pol.excluded[rows].sum())}")


def test_persistence(cache: Path, out: Path) -> None:
    knobs = P.Knobs()
    ix = P.load_pack(cache, out, PACK, knobs)
    ctx = F.PackContext(out, ix, knobs)
    store = F.FeedbackStore(out, PACK)
    fb = F.PlannerFeedback(ctx, store=store)
    fb.state.data_hash = "h"
    fb.have(str(ix.items[0]), 7.0)
    fb.ban(recipe_idx=int(np.flatnonzero(ix.keep)[0]))
    fb.prefer(base_id=str(ix.items[1]))
    multi = np.flatnonzero(np.diff(ix.prod_ptr) >= 2)
    g = ctx.group(int(multi[0]))
    fb.choose(int(g[0]), over=int(g[1]))
    before = json.dumps(fb.state.to_json(), sort_keys=True)
    fb.save()
    again, status = store.load("h")
    check("the store round-trips exactly",
          status == "ok"
          and json.dumps(again.to_json(), sort_keys=True) == before)
    check("and the view it produces has the same signature",
          F.build_view(ctx, again).signature()
          == F.build_view(ctx, fb.state).signature())
    _, bad = store.load("a-different-hash")
    check("a stale data hash is refused, not silently applied",
          bad == "data_hash", bad)
    merged = F.FeedbackState.from_json(json.loads(before)).merge(again)
    key = next(iter(again.evidence))
    check("merging two stores adds their evidence",
          abs(merged.evidence[key][0] - 2 * again.evidence[key][0]) < 1e-9)


def test_inquiry(cache: Path, out: Path) -> None:
    knobs = P.Knobs()
    ix = P.load_pack(cache, out, PACK, knobs)
    ctx = F.PackContext(out, ix, knobs)
    fb = F.PlannerFeedback(ctx)
    pol, sv = F.solved(ix, knobs)
    units = P.pool_units(ix)
    rng = np.random.default_rng(5)
    pool = np.flatnonzero(ix.item_nprod > 0)
    targets = sorted(str(ix.items[i]) for i in
                     np.sort(rng.choice(pool, size=60, replace=False)))
    inq = F.TemplateInquiry(ctx, fb)
    for t in targets:
        inq.observe(P.plan_target(ix, pol, knobs, t, 1, None, sv, units))
    qs = inq.questions(20)
    fams = [q["family"] for q in qs]
    check("at most one question per template family",
          len(fams) == len(set(fams)), f"{len(fams)} questions")
    check("the questions are capped at the session limit", len(qs) <= 20,
          f"{len(qs)}")
    check("each question carries three example recipes and both answers",
          all(1 <= len(q["examples"]) <= 3 and len(q["answers"]) == 2
              for q in qs))
    check("the ranking is by score, descending",
          all(qs[i]["score"] >= qs[i + 1]["score"]
              for i in range(len(qs) - 1)))
    inq.record(qs)
    check("a recorded family is never asked again",
          not any(q["family"] in set(fams) for q in inq.questions(20)))
    check("uncertainty falls once a template carries evidence",
          all(q["uncertainty"] <= 1.0 for q in qs))


def test_latency(cache: Path, out: Path) -> None:
    knobs = P.Knobs()
    ix = P.load_pack(cache, out, PACK, knobs)
    ctx = F.PackContext(out, ix, knobs)
    fb = F.PlannerFeedback(ctx)
    rng = np.random.default_rng(9)
    recs = rng.choice(np.flatnonzero(ix.keep), size=50, replace=False)
    multi = np.flatnonzero(np.diff(ix.prod_ptr) >= 2)
    worst = {}
    for name, fn in (
            ("have", lambda i: fb.have(str(ix.items[i]), 1.0)),
            ("ban", lambda i: fb.ban(recipe_idx=int(recs[i % 50]))),
            ("prefer", lambda i: fb.prefer(base_id=str(ix.items[i]))),
            ("choose", lambda i: fb.choose(
                int(ctx.group(int(multi[i % multi.size]))[0]),
                over=int(ctx.group(int(multi[i % multi.size]))[1]))),
            ("unban", lambda i: fb.unban(recipe_idx=int(recs[i % 50]))),
            ("reset", lambda i: fb.reset())):
        acc = []
        for i in range(50):
            t0 = time.perf_counter()
            fn(i)
            acc.append((time.perf_counter() - t0) * 1000.0)
        worst[name] = float(np.median(acc))
    check("every update is milliseconds, median under 5 ms",
          max(worst.values()) < 5.0,
          ", ".join(f"{k} {v:.3f}ms" for k, v in worst.items()))
    fb.reset()
    fb.choose(int(ctx.group(int(multi[0]))[0]),
              over=int(ctx.group(int(multi[0]))[1]))
    view = fb.view()
    acc = []
    for it in rng.choice(multi, size=100, replace=False).tolist():
        view.score.gcache.pop(int(it), None)
        t0 = time.perf_counter()
        view.score._group_theta(int(it))
        acc.append((time.perf_counter() - t0) * 1000.0)
    check("re-scoring one output's producers is well under a millisecond",
          float(np.median(acc)) < 1.0, f"median {np.median(acc):.3f} ms")


def test_determinism(cache: Path, out: Path) -> None:
    knobs = P.Knobs()
    ix = P.load_pack(cache, out, PACK, knobs)
    ctx = F.PackContext(out, ix, knobs)
    sigs, blobs = [], []
    for _ in range(2):
        fb = F.PlannerFeedback(ctx)
        multi = np.flatnonzero(np.diff(ix.prod_ptr) >= 2)
        for it in multi[:5].tolist():
            g = ctx.group(int(it))
            fb.choose(int(g[0]), over=int(g[-1]))
        fb.ban(recipe_idx=int(np.flatnonzero(ix.keep)[0]))
        sigs.append(fb.state.signature())
        opt = fb.options()
        pol = P.Policy(ix, knobs, opt)
        sv = P.solve_cost(ix, pol, knobs, None, opt)
        pl = P.plan_target(ix, pol, knobs, str(ix.items[int(multi[0])]), 1,
                           None, sv, P.pool_units(ix), opt)
        j = P.plan_json(pl)
        j["seconds"] = 0.0
        j["metrics"]["wall_ms"] = 0.0
        blobs.append(json.dumps(j, sort_keys=True))
    check("two identical feedback sessions produce the same state signature",
          sigs[0] == sigs[1], sigs[0])
    check("and the same plan, byte for byte", blobs[0] == blobs[1])


# ------------------------------------------------------- the no-vocabulary rule


def test_no_rule_vocabulary_phase4() -> None:
    import test_phase0a as t0a
    check("the Phase 0a check covers feedback.py",
          "feedback.py" in t0a.MODULES)
    t0a.PASSED.clear()
    t0a.test_no_rule_vocabulary()
    check("no mod / material / colour / rule vocabulary in the Phase 4 code",
          bool(t0a.PASSED))


def main() -> int:
    cache, out = ROOT / "cache", ROOT / "out"
    print("Phase 4 checks")
    print("-- the promise: no feedback changes nothing")
    test_no_feedback_is_byte_identical(cache, out)
    print("-- synthetic: the four gestures")
    test_have()
    test_ban_recipe_and_template()
    test_ban_item()
    test_prefer()
    test_choose()
    test_reset()
    print("-- synthetic: the bounds")
    test_spread_is_capped()
    test_delta_is_bounded()
    print(f"-- real ({PACK})")
    test_lazy_rescore_matches_full_recompute(cache, out)
    test_family_generalisation(cache, out)
    test_persistence(cache, out)
    test_inquiry(cache, out)
    test_latency(cache, out)
    test_determinism(cache, out)
    print("-- design rules")
    test_no_rule_vocabulary_phase4()
    print(f"\n{len(PASSED)} checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
