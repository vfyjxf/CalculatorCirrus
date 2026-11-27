#!/usr/bin/env python
"""Phase 4 — the minimum-annotation feedback layer.

    ./.venv/bin/python feedback.py --stage demo   --packs fom
    ./.venv/bin/python feedback.py --stage eval   --packs all

Four user gestures, four closed-form updates, no gradient training at runtime:

    have(base_id, amount)        standing stock
    ban(recipe | template | item) / unban
    prefer(base_id | recipe_idx)
    choose(a, over=b)            implicit, from a re-selection in the UI

``FeedbackLearner`` turns each gesture into (a) Beta / Dirichlet pseudo-counts
on the *template* the recipe belongs to, spread over its nearest neighbours in
standardised feature space, and (b) one bounded logistic step on the producer
ranker's weights, stored as a **delta** on top of ``producer-ranker.json``.
Both are milliseconds; neither touches the exported models on disk.

``TemplateInquiry`` reads a finished plan and asks at most one question per
template family per session, choosing by uncertainty x impact.

Hard rules inherited from Phase 0 and Phase 3 and kept here:

* **No hardcoded mod / item / material / colour / system vocabulary.**  The
  role words come from ``labels.py``'s one sanctioned table, imported through
  ``planner.py``; everything else is a schema or column name.
* **Never join on recipe ids.**  ``recipe_idx``, ``template_id``, ``base_id``.
* **Deterministic.**  Stable sorts, seeded sampling, tie-breaks by stable keys.
"""
from __future__ import annotations

import argparse
import json
import math
import sys
import time
from pathlib import Path

import numpy as np
import pyarrow.parquet as pq

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import eval_planner as E  # noqa: E402
import planner as P  # noqa: E402
from common import read_json, stable_hash, write_json  # noqa: E402
from predict_reference import Reference  # noqa: E402

VERSION = "p4.1"

# ======================================================= the update constants
#
# Every one of these is a documented default, stated once here and repeated in
# DESIGN.md Phase 4.  Nothing below is fitted; they are the shape of the
# update, and the report measures what each of them buys.

#: Prior strength ``n0`` of the template posteriors, in pseudo-observations.
#: The exported model's belief is worth this many user clicks.
PRIOR_STRENGTH = 4.0

#: One explicit rejection is worth ``BAN_FACTOR x n0`` pseudo-observations, so
#: a single click drives the junk posterior of its template to at least
#: ``BAN_FACTOR / (1 + BAN_FACTOR)`` = 0.875 whatever the model believed —
#: just above the default exclusion threshold 0.8707.
BAN_FACTOR = 7.0

#: One explicit preference is worth ``PREFER_FACTOR x n0`` of the opposite
#: evidence, so a single click halves the junk posterior of its template.
PREFER_FACTOR = 1.0

#: Neighbourhood generalisation: at most this many templates receive a share
#: of a click's evidence, they must be at least this similar (cosine over the
#: standardised 129-dim template features), the total spread never exceeds
#: ``SPREAD_TOTAL`` times the direct evidence, and no single neighbour ever
#: receives more than ``SPREAD_PER`` times it.  Together: one click can move
#: at most 9 templates, and a neighbour's junk posterior only crosses the
#: exclusion threshold when the model already leant that way (p > 0.42).
NEIGHBOURS = 8
SIMILARITY_FLOOR = 0.70
SPREAD_TOTAL = 1.0
SPREAD_PER = 0.5

#: The pairwise online update.  ``z`` is the ranker's own scaled pair vector;
#: the step is normalised by ``|z|^2`` so ``ETA`` is the *margin* one click
#: buys, in the ranker's own units, and is therefore bounded by construction.
#: ``LAMBDA`` is the L2 pull back towards the exported weights and ``BOUND``
#: caps the delta at a fraction of ``|w0|``.
ETA = 4.0
LAMBDA = 0.02
BOUND = 0.25
#: ``prefer`` is ``choose`` against every sibling, at a quarter of the step.
PREFER_STEP_SCALE = 0.25

#: Item-level preference: the documented discount on the price that item
#: contributes to whoever consumes it.
ITEM_DISCOUNT = 0.5

#: Inquiry: how many questions a session may hold, and the shortlist margin
#: under which a *considered* (not executed) template still counts as impact.
INQUIRY_LIMIT = 20
INQUIRY_MARGIN = 2.0

BAN_SCOPES = ("recipe", "template", "item")
PREFER_SCOPES = ("recipe", "item")


# ==================================================================== the state


class FeedbackState:
    """Everything one pack's feedback store holds.  Plain data, JSON-shaped.

    Java mapping: ``api/recipe/routing/feedback/FeedbackStore`` — the same
    fields, Gson-written, versioned against ``manifest.data_hash``.
    """

    __slots__ = ("slug", "pack", "data_hash", "index_stamp", "stock",
                 "ban_recipes", "ban_templates", "ban_items",
                 "prefer_recipes", "prefer_items", "evidence", "role_evidence",
                 "delta", "choices", "asked", "n_updates")

    def __init__(self, slug: str = "", pack: str = "", data_hash: str = "",
                 index_stamp: str = "", n_pair: int = 188):
        self.slug, self.pack = slug, pack
        self.data_hash, self.index_stamp = data_hash, index_stamp
        self.stock: dict = {}
        self.ban_recipes: set = set()
        self.ban_templates: set = set()
        self.ban_items: set = set()
        self.prefer_recipes: set = set()
        self.prefer_items: set = set()
        #: ``template_id -> [junk evidence, useful evidence]`` (Beta counts).
        self.evidence: dict = {}
        #: ``template_id -> {role class: pseudo-count}`` (Dirichlet counts).
        self.role_evidence: dict = {}
        self.delta = np.zeros(int(n_pair), dtype=np.float64)
        self.choices: list = []
        self.asked: list = []
        self.n_updates = 0

    # -- serialisation ------------------------------------------------------
    def to_json(self) -> dict:
        return {
            "version": VERSION, "slug": self.slug, "pack": self.pack,
            "data_hash": self.data_hash, "index_stamp": self.index_stamp,
            "stock": {k: float(v) for k, v in sorted(self.stock.items())},
            "bans": {"recipe": sorted(int(r) for r in self.ban_recipes),
                     "template": sorted(self.ban_templates),
                     "item": sorted(self.ban_items)},
            "prefers": {"recipe": sorted(int(r) for r in self.prefer_recipes),
                        "item": sorted(self.prefer_items)},
            "evidence": {k: [round(v[0], 6), round(v[1], 6)]
                         for k, v in sorted(self.evidence.items())},
            "role_evidence": {k: {c: round(x, 6)
                                  for c, x in sorted(v.items())}
                              for k, v in sorted(self.role_evidence.items())},
            "delta_weights": [round(float(x), 9) for x in self.delta],
            "choices": self.choices,
            "asked": sorted(self.asked),
            "n_updates": int(self.n_updates),
        }

    @classmethod
    def from_json(cls, blob: dict) -> "FeedbackState":
        st = cls(blob.get("slug", ""), blob.get("pack", ""),
                 blob.get("data_hash", ""), blob.get("index_stamp", ""))
        st.stock = {str(k): float(v) for k, v in blob.get("stock", {}).items()}
        bans = blob.get("bans", {})
        st.ban_recipes = {int(r) for r in bans.get("recipe", ())}
        st.ban_templates = {str(t) for t in bans.get("template", ())}
        st.ban_items = {str(t) for t in bans.get("item", ())}
        prefs = blob.get("prefers", {})
        st.prefer_recipes = {int(r) for r in prefs.get("recipe", ())}
        st.prefer_items = {str(t) for t in prefs.get("item", ())}
        st.evidence = {str(k): [float(v[0]), float(v[1])]
                       for k, v in blob.get("evidence", {}).items()}
        st.role_evidence = {str(k): {str(c): float(x) for c, x in v.items()}
                            for k, v in blob.get("role_evidence", {}).items()}
        st.delta = np.asarray(blob.get("delta_weights", []), dtype=np.float64)
        st.choices = list(blob.get("choices", ()))
        st.asked = list(blob.get("asked", ()))
        st.n_updates = int(blob.get("n_updates", 0))
        return st

    def signature(self) -> str:
        """Stable hash of everything the planner reads off this state."""
        return stable_hash(self.to_json(), 16)

    def merge(self, other: "FeedbackState") -> "FeedbackState":
        """Absorb another store: counts add, sets union, deltas add.

        This is how a template shared across packs receives evidence directly
        — the key is the ``template_id``, which is pack-independent by
        construction (Phase 0a §2.4).
        """
        self.stock.update(other.stock)
        self.ban_recipes |= other.ban_recipes
        self.ban_templates |= other.ban_templates
        self.ban_items |= other.ban_items
        self.prefer_recipes |= other.prefer_recipes
        self.prefer_items |= other.prefer_items
        for tid, (a, b) in other.evidence.items():
            cur = self.evidence.setdefault(tid, [0.0, 0.0])
            cur[0] += a
            cur[1] += b
        for tid, counts in other.role_evidence.items():
            cur = self.role_evidence.setdefault(tid, {})
            for c, x in counts.items():
                cur[c] = cur.get(c, 0.0) + x
        if other.delta.size:
            if self.delta.size != other.delta.size:
                self.delta = np.zeros(other.delta.size)
            self.delta = self.delta + other.delta
        self.choices.extend(other.choices)
        self.n_updates += other.n_updates
        return self


class FeedbackStore:
    """``out/<slug>/feedback/state.json`` — load, save, version check.

    The "规则仓库" analogue: one JSON per pack, stamped with the model bundle's
    ``data_hash`` and the planner index stamp.  A mismatch is reported and the
    state is dropped rather than silently applied to a different corpus.
    """

    def __init__(self, out_dir: Path, slug: str):
        self.path = Path(out_dir) / slug / "feedback" / "state.json"
        self.slug = slug

    def load(self, data_hash: str = "", index_stamp: str = "",
             strict: bool = True) -> tuple:
        if not self.path.exists():
            return None, "absent"
        blob = read_json(self.path)
        if blob.get("version") != VERSION:
            return None, "version"
        st = FeedbackState.from_json(blob)
        if strict and data_hash and st.data_hash and st.data_hash != data_hash:
            return None, "data_hash"
        if strict and index_stamp and st.index_stamp \
                and st.index_stamp != index_stamp:
            return None, "index_stamp"
        return st, "ok"

    def save(self, st: FeedbackState) -> Path:
        write_json(self.path, st.to_json())
        return self.path

    def size_bytes(self) -> int:
        return self.path.stat().st_size if self.path.exists() else 0


# ============================================ per-pack caches the layer needs


class PackContext:
    """Everything the updates read, built once per pack.

    Java mapping: the side tables of ``recipe/routing/cache/DerivedIndexCache``
    — the pair-feature matrix and the standardised template matrix are exactly
    what a Java runtime would hold beside ``RecipeGraph.Node``.
    """

    __slots__ = ("ix", "ref", "names", "n_base", "flags", "sym", "fill",
                 "scale", "w0", "flagv", "zb", "tpl_ids", "tz",
                 "tpl_rows", "counted", "g_ptr", "g_rec", "out_items",
                 "seconds", "family")

    def __init__(self, out_dir: Path, ix: P.PackIndex, knobs: P.Knobs):
        t0 = time.perf_counter()
        self.ix = ix
        self.ref = Reference(Path(out_dir) / "models")
        spec = self.ref.spec["pair_features"]
        self.names = list(spec["names"])
        self.n_base = int(spec["n_base"])
        self.flags = list(spec["flags"])
        self.sym = set(spec["symmetric_flags"])
        self.fill = np.asarray(spec["recipe_fill"], dtype=np.float64)
        arm = knobs.arm_rank or self.ref.ranker["default"]
        rspec = self.ref.ranker[arm]
        if rspec["kind"] != "lr":
            raise SystemExit("the feedback layer updates the linear arm only")
        self.scale = np.asarray(rspec["scale"], dtype=np.float64)
        self.w0 = np.asarray(rspec["coef"])[0] / self.scale
        base, self.flagv = P._pair_inputs(out_dir, ix, self.names,
                                          self.n_base)
        # NaN-filled once, then the raw matrix is released: on the largest
        # pack the two together are 0.8 GB and only the filled one is read.
        self.zb = np.where(np.isfinite(base), base, self.fill)
        del base

        # template -> its recipes, and the pack's standardised feature matrix
        order = np.argsort(ix.template_id, kind="stable")
        tids = ix.template_id[order]
        starts = np.flatnonzero(np.r_[True, tids[1:] != tids[:-1]])
        self.tpl_rows = {str(tids[s]): order[s:e] for s, e in
                         zip(starts, np.r_[starts[1:], tids.size])}
        self.tpl_ids, self.tz = _template_matrix(out_dir, ix, self.ref)
        self.family: dict = {}

        # the producer groups, deduplicated exactly the way `producer_scores`
        # does it, as a CSR so a group is one slice
        item_of = np.repeat(np.arange(ix.n_items, dtype=np.int64),
                            np.diff(ix.prod_ptr))
        edges = np.unique(np.stack([item_of, ix.prod_rec]), axis=1)
        self.g_ptr = np.zeros(ix.n_items + 1, dtype=np.int64)
        np.cumsum(np.bincount(edges[0], minlength=ix.n_items),
                  out=self.g_ptr[1:])
        self.g_rec = edges[1]
        self.counted = np.bincount(edges[1],
                                   minlength=ix.n_recipes).astype(np.float64)
        rec_ptr = np.zeros(ix.n_recipes + 1, dtype=np.int64)
        o2 = np.argsort(edges[1], kind="stable")
        np.cumsum(np.bincount(edges[1], minlength=ix.n_recipes), out=rec_ptr[1:])
        self.out_items = (rec_ptr, edges[0][o2])
        self.seconds = round(time.perf_counter() - t0, 3)

    # -- neighbourhoods -----------------------------------------------------
    def neighbours(self, tid: str) -> list:
        """The ``NEIGHBOURS`` most similar templates of this pack.

        Cosine similarity over the standardised 129-dim template features, a
        floor of ``SIMILARITY_FLOOR``, ``tid`` itself excluded.  Deterministic:
        ties break by ``template_id``.
        """
        pos = int(np.searchsorted(self.tpl_ids, tid))
        if pos >= self.tpl_ids.size or self.tpl_ids[pos] != tid:
            return []
        sims = self.tz @ self.tz[pos]
        sims[pos] = -np.inf
        k = min(NEIGHBOURS, max(sims.size - 1, 0))
        if k <= 0:
            return []
        top = np.argpartition(-sims, k - 1)[:k]
        out = [(str(self.tpl_ids[j]), float(sims[j])) for j in top
               if sims[j] >= SIMILARITY_FLOOR]
        out.sort(key=lambda kv: (-kv[1], kv[0]))
        return out

    def family_of(self, tid: str) -> str:
        """A deterministic family key: the smallest id in ``{t} u N_k(t)``.

        Two templates that are each other's neighbours land in the same
        family, which is what "one question per family" means operationally.
        """
        got = self.family.get(tid)
        if got is None:
            got = min([tid] + [n for n, _ in self.neighbours(tid)])
            self.family[tid] = got
        return got

    def group(self, item: int) -> np.ndarray:
        return self.g_rec[self.g_ptr[item]:self.g_ptr[item + 1]]

    def outputs_of(self, r: int) -> np.ndarray:
        ptr, items = self.out_items
        return items[ptr[r]:ptr[r + 1]]

    def pair_vector(self, a: int, b: int) -> np.ndarray:
        """The 188-dim pair row ``f(a) - f(b)`` the ranker was trained on."""
        row = np.empty(len(self.names), dtype=np.float64)
        row[:self.n_base] = self.zb[a] - self.zb[b]
        for j, name in enumerate(self.flags):
            v = self.flagv[name]
            if name in self.sym:
                row[self.n_base + j] = 1.0 if v[a] == v[b] else 0.0
            else:
                d = float(v[a]) - float(v[b])
                row[self.n_base + j] = 0.0 if not np.isfinite(d) else d
        return row


def _template_matrix(out_dir: Path, ix: P.PackIndex, ref: Reference):
    """``(sorted template ids, L2-normalised standardised features)``.

    The standardisation is ``feature-spec.json``'s own ``fill`` / ``mean`` /
    ``std`` — the same numbers the models were trained with, so "near in
    feature space" means the same thing to the learner as to the model.
    """
    spec = ref.spec["template_features"]
    names = list(spec["names"])
    tf = P.pack_rows(pq.read_table(
        Path(out_dir) / "features" / "template_features.parquet",
        columns=["pack", "template_id"] + names), ix)
    tids = np.asarray(tf.column("template_id").to_pylist())
    x = np.column_stack([tf.column(n).to_numpy(zero_copy_only=False)
                         .astype(np.float64) for n in names])
    z = np.where(np.isfinite(x), x, np.asarray(spec["fill"]))
    z = (z - np.asarray(spec["mean"])) / np.asarray(spec["std"])
    norm = np.linalg.norm(z, axis=1, keepdims=True)
    z = z / np.where(norm > 1e-12, norm, 1.0)
    order = np.argsort(tids, kind="stable")
    return tids[order], np.ascontiguousarray(z[order])


# ====================================================== the view the planner reads


class LazyScore:
    """``S(r) + delta S(r)`` — the Bradley-Terry aggregate under the delta.

    The aggregation is linear in the weights, so the delta's contribution is
    the *same* row-mean formula run with the delta weights alone
    (``planner._group_rowmeans``, reused, not re-derived).  It is computed per
    output group on first touch and cached, which is the design's "re-scoring
    an output's producers is one dot product per producer".
    """

    __slots__ = ("ctx", "wd", "base", "gcache", "rcache", "dots", "groups")

    def __init__(self, ctx: PackContext, delta: np.ndarray):
        self.ctx = ctx
        self.wd = np.asarray(delta, dtype=np.float64) / ctx.scale
        self.base = ctx.ix.score
        self.gcache: dict = {}
        self.rcache: dict = {}
        self.dots = 0
        self.groups = 0

    def _group_theta(self, item: int) -> dict:
        got = self.gcache.get(item)
        if got is not None:
            return got
        ctx = self.ctx
        members = ctx.group(item)
        if members.size < 2:
            got = {}
        else:
            s = ctx.zb[members] @ self.wd[:ctx.n_base]   # one dot per producer
            self.dots += int(members.size)
            self.groups += 1
            theta = P._group_rowmeans(s, members, ctx.flagv, ctx.flags,
                                      ctx.sym, self.wd, ctx.n_base, ctx.ix)
            got = {int(m): float(t) for m, t in zip(members, theta)}
        self.gcache[item] = got
        return got

    def delta_of(self, r: int) -> float:
        got = self.rcache.get(r)
        if got is None:
            acc = 0.0
            for item in self.ctx.outputs_of(r):
                acc += self._group_theta(int(item)).get(int(r), 0.0)
            n = self.ctx.counted[r]
            got = acc / n if n > 0 else 0.0
            self.rcache[r] = got
        return got

    def __getitem__(self, r):
        if isinstance(r, (int, np.integer)):
            return self.base[int(r)] + self.delta_of(int(r))
        return np.asarray([self.base[int(x)] + self.delta_of(int(x))
                           for x in np.atleast_1d(r)])

    def dense(self) -> np.ndarray:
        """The whole array — for tests and for the offline metric passes."""
        return np.asarray([self[int(r)] for r in range(self.base.size)])


class FeedbackView:
    """Dense arrays the planner reads exactly where it reads the model's.

    ``PlanOptions(feedback=view)`` is the single additive hook (DESIGN.md
    Phase 4 §5); nothing else in ``planner.py`` knows this module exists.
    """

    __slots__ = ("junk_p", "role_p", "eff_role", "score", "banned",
                 "preferred", "item_banned", "item_factor", "stock", "sig",
                 "stats")

    def signature(self) -> str:
        return self.sig


#: A role click is worth exactly the model's whole prior on the role, so one
#: click moves the role posterior halfway and a *fraction* of one does not
#: dominate it.  The junk side is deliberately much stronger (``BAN_FACTOR``):
#: a rejection is first of all a statement about usefulness, only secondarily
#: about which role class the template belongs to.
ROLE_EVIDENCE_FACTOR = 1.0


def _binary_entropy(p: float) -> float:
    if p <= 0.0 or p >= 1.0:
        return 0.0
    return float(-p * math.log2(p) - (1 - p) * math.log2(1 - p))


def _entropy(q) -> float:
    q = np.asarray(q, dtype=np.float64)
    q = q[q > 0]
    if q.size < 2:
        return 0.0
    return float(-(q * np.log(q)).sum() / math.log(q.size))


# ==================================================================== the learner


class FeedbackLearner:
    """The closed-form updates.  No gradients, no epochs, no fitting.

    Java mapping: ``api/recipe/routing/feedback/FeedbackLearner``.
    """

    __slots__ = ("ctx", "state", "timings")

    def __init__(self, ctx: PackContext, state: FeedbackState):
        self.ctx, self.state = ctx, state
        self.timings: dict = {}

    # -- neighbourhood spread ----------------------------------------------
    def spread(self, tid: str) -> list:
        """``[(template_id, fraction)]`` — the template itself at 1.0, then
        its neighbours, capped per neighbour and in total."""
        out = [(tid, 1.0)]
        ns = self.ctx.neighbours(tid)
        total = sum(s for _, s in ns)
        if total <= 0:
            return out
        scale = min(1.0, SPREAD_TOTAL / total)
        for n, s in ns:
            w = min(SPREAD_PER, s * scale)
            if w > 1e-9:
                out.append((n, w))
        return out

    # -- template posteriors ------------------------------------------------
    def add_evidence(self, tid: str, junk: float, useful: float,
                     role_class: str) -> list:
        """Beta / Dirichlet pseudo-counts on ``tid`` and its neighbourhood."""
        touched = self.spread(tid)
        for t, frac in touched:
            cur = self.state.evidence.setdefault(t, [0.0, 0.0])
            cur[0] += junk * frac
            cur[1] += useful * frac
            role = self.state.role_evidence.setdefault(t, {})
            role[role_class] = role.get(role_class, 0.0) + \
                ROLE_EVIDENCE_FACTOR * PRIOR_STRENGTH * frac
        self.state.n_updates += 1
        return touched

    def ban_evidence(self, r: int) -> list:
        return self.add_evidence(str(self.ctx.ix.template_id[r]),
                                 BAN_FACTOR * PRIOR_STRENGTH, 0.0,
                                 P.ROLE_DISPLAY)

    def prefer_evidence(self, r: int) -> list:
        return self.add_evidence(str(self.ctx.ix.template_id[r]), 0.0,
                                 PREFER_FACTOR * PRIOR_STRENGTH,
                                 P.ROLE_PRODUCTION)

    # -- the pairwise online step ------------------------------------------
    def step(self, a: int, b: int, scale: float = 1.0) -> dict:
        """One bounded logistic step towards "a beats b".

            z      = (f(a) - f(b)) / ranker scale          (188 dims)
            m      = (w0 + d) . z                          current margin
            g      = sigmoid(-m)                           logistic gradient
            d     <- (1 - LAMBDA) d + ETA * scale * g * z / |z|^2
            |d|   <= BOUND * |w0|                          rescaled if over

        The ``|z|^2`` normalisation makes ``ETA`` the margin the click buys in
        the ranker's own units, so the step is bounded before the clip ever
        fires and one click can never move the margin by more than ``ETA``.
        """
        ctx = self.ctx
        x = ctx.pair_vector(a, b)          # raw pair row, the 188 columns
        z = x / ctx.scale                  # the ranker's standardised space
        nrm = float(z @ z)
        if nrm <= 1e-12:
            return {"margin_before": 0.0, "margin_after": 0.0, "norm": 0.0,
                    "cap": 0.0, "clipped": False}
        d = self.state.delta               # stored in the exported coef space
        if d.size != z.size:
            d = self.state.delta = np.zeros(z.size, dtype=np.float64)
        before = float(x @ (ctx.w0 + d / ctx.scale))
        g = 1.0 / (1.0 + math.exp(max(-60.0, min(60.0, before))))
        d = (1.0 - LAMBDA) * d + ETA * scale * g * z / nrm
        cap = BOUND * float(np.linalg.norm(ctx.w0 * ctx.scale))
        got = float(np.linalg.norm(d))
        clipped = got > cap
        if clipped:
            d = d * (cap / got)
        self.state.delta = d
        self.state.n_updates += 1
        after = float(x @ (ctx.w0 + d / ctx.scale))
        return {"margin_before": round(before, 6),
                "margin_after": round(after, 6),
                "norm": round(float(np.linalg.norm(d)), 6),
                "cap": round(cap, 6), "clipped": bool(clipped)}


# ================================================================= the API


class PlannerFeedback:
    """``have`` / ``ban`` / ``unban`` / ``prefer`` / ``choose`` / ``reset``.

    Java mapping: ``api/recipe/routing/feedback/PlannerFeedback``.  Every call
    mutates the state and invalidates the cached view; nothing recomputes the
    planner index and nothing writes to disk until ``save()``.
    """

    def __init__(self, ctx: PackContext, state: FeedbackState = None,
                 store: FeedbackStore = None):
        self.ctx = ctx
        ix = ctx.ix
        self.state = state or FeedbackState(ix.slug, ix.pack,
                                            n_pair=len(ctx.names))
        self.learner = FeedbackLearner(ctx, self.state)
        self.store = store
        self._view = None
        self.timings: dict = {}

    # -- bookkeeping --------------------------------------------------------
    def _dirty(self) -> None:
        self._view = None

    def _timed(self, name: str, t0: float) -> None:
        self.timings.setdefault(name, []).append(
            (time.perf_counter() - t0) * 1000.0)

    # -- the four gestures --------------------------------------------------
    def have(self, base_id: str, amount: float) -> None:
        t0 = time.perf_counter()
        if amount <= 0:
            self.state.stock.pop(base_id, None)
        else:
            self.state.stock[base_id] = float(amount)
        self._dirty()
        self._timed("have", t0)

    def ban(self, recipe_idx: int = None, template_id: str = None,
            base_id: str = None) -> dict:
        """Reject a producer, a whole template, or an item as an input source.

        A recipe-level ban also adds junk evidence to its template, which is
        what makes the gesture generalise to the siblings the user never saw.
        """
        t0 = time.perf_counter()
        out = {"touched": []}
        if recipe_idx is not None:
            self.state.ban_recipes.add(int(recipe_idx))
            self.state.prefer_recipes.discard(int(recipe_idx))
            out["touched"] = self.learner.ban_evidence(int(recipe_idx))
        if template_id is not None:
            self.state.ban_templates.add(str(template_id))
            out["touched"] = self.learner.add_evidence(
                str(template_id), BAN_FACTOR * PRIOR_STRENGTH, 0.0,
                P.ROLE_DISPLAY)
        if base_id is not None:
            self.state.ban_items.add(str(base_id))
            self.state.stock.pop(str(base_id), None)
            self.state.prefer_items.discard(str(base_id))
        self._dirty()
        self._timed("ban", t0)
        return out

    def unban(self, recipe_idx: int = None, template_id: str = None,
              base_id: str = None) -> None:
        """Withdraw a ban.  The evidence it added stays — it was a real
        observation — but the hard exclusion goes."""
        t0 = time.perf_counter()
        if recipe_idx is not None:
            self.state.ban_recipes.discard(int(recipe_idx))
        if template_id is not None:
            self.state.ban_templates.discard(str(template_id))
        if base_id is not None:
            self.state.ban_items.discard(str(base_id))
        self._dirty()
        self._timed("unban", t0)

    def prefer(self, base_id: str = None, recipe_idx: int = None) -> dict:
        """Prefer an item (a documented price discount) or a producer.

        Preferring a producer is ``choose`` against every sibling that makes
        the same output, at ``PREFER_STEP_SCALE`` of the step — so it moves
        the ranker in the same direction as an explicit re-selection would,
        without claiming the user compared any particular pair.
        """
        t0 = time.perf_counter()
        out = {"siblings": 0, "touched": []}
        if base_id is not None:
            self.state.prefer_items.add(str(base_id))
            self.state.ban_items.discard(str(base_id))
        if recipe_idx is not None:
            r = int(recipe_idx)
            self.state.prefer_recipes.add(r)
            self.state.ban_recipes.discard(r)
            out["touched"] = self.learner.prefer_evidence(r)
            seen = set()
            for item in self.ctx.outputs_of(r):
                for b in self.ctx.group(int(item)):
                    b = int(b)
                    if b == r or b in seen:
                        continue
                    seen.add(b)
                    self.learner.step(r, b, PREFER_STEP_SCALE)
            out["siblings"] = len(seen)
        self._dirty()
        self._timed("prefer", t0)
        return out

    def choose(self, a_recipe_idx: int, over: int) -> dict:
        """The implicit gesture: the user re-selected ``a`` where the planner
        had put ``b``.  Both must produce the same output."""
        t0 = time.perf_counter()
        a, b = int(a_recipe_idx), int(over)
        shared = set(self.ctx.outputs_of(a).tolist()) & \
            set(self.ctx.outputs_of(b).tolist())
        if not shared:
            raise ValueError("choose() needs two producers of the same output")
        info = self.learner.step(a, b, 1.0)
        self.state.choices.append({"a": a, "b": b, "weight": 1.0})
        self._dirty()
        self._timed("choose", t0)
        return info

    def reset(self) -> None:
        t0 = time.perf_counter()
        ix = self.ctx.ix
        self.state = FeedbackState(ix.slug, ix.pack, self.state.data_hash,
                                   self.state.index_stamp, len(self.ctx.names))
        self.learner = FeedbackLearner(self.ctx, self.state)
        self._dirty()
        self._timed("reset", t0)

    # -- persistence --------------------------------------------------------
    def save(self) -> Path:
        if self.store is None:
            raise SystemExit("no store attached")
        return self.store.save(self.state)

    # -- what the planner reads --------------------------------------------
    def view(self) -> FeedbackView:
        if self._view is None:
            t0 = time.perf_counter()
            self._view = build_view(self.ctx, self.state)
            self._timed("view", t0)
        return self._view

    def options(self, **kw) -> P.PlanOptions:
        return P.PlanOptions(feedback=self.view(), **kw)


def build_view(ctx: PackContext, state: FeedbackState) -> FeedbackView:
    """Turn the state into the dense arrays ``PlanOptions`` carries.

    Posterior mean of a Beta with prior ``n0 x p`` pseudo-counts::

        junk'(t) = (n0 * junk(t) + a(t)) / (n0 + a(t) + b(t))

    and of the Dirichlet, class by class::

        role'(t, c) = (n0 * role(t, c) + c(t, c)) / (n0 + sum_c c(t, c))

    Arrays the state does not touch are *shared*, not copied, so a ban-only
    state costs nothing to view.
    """
    ix = ctx.ix
    v = FeedbackView()
    junk, role_p, eff = ix.junk_p, ix.role_p, ix.eff_role
    stats = {"templates_touched": 0, "recipes_touched": 0}
    if state.evidence:
        junk = ix.junk_p.copy()
        for tid, (a, b) in state.evidence.items():
            rows = ctx.tpl_rows.get(tid)
            if rows is None or (a <= 0 and b <= 0):
                continue
            junk[rows] = ((PRIOR_STRENGTH * ix.junk_p[rows] + a)
                          / (PRIOR_STRENGTH + a + b))
            stats["templates_touched"] += 1
            stats["recipes_touched"] += int(rows.size)
    if state.role_evidence:
        classes = list(ix.role_classes)
        role_p = ix.role_p.copy()
        for tid, counts in state.role_evidence.items():
            rows = ctx.tpl_rows.get(tid)
            if rows is None:
                continue
            c = np.zeros(len(classes), dtype=np.float64)
            for name, x in counts.items():
                if name in classes:
                    c[classes.index(name)] += x
            if c.sum() <= 0:
                continue
            role_p[rows] = ((PRIOR_STRENGTH * ix.role_p[rows] + c)
                            / (PRIOR_STRENGTH + c.sum()))
        argmax = np.asarray(classes, dtype=object)[role_p.argmax(axis=1)]
        eff = P.effective_roles(ix, argmax)

    banned = np.zeros(ix.n_recipes, dtype=bool)
    if state.ban_recipes:
        banned[[r for r in sorted(state.ban_recipes)
                if 0 <= r < ix.n_recipes]] = True
    for tid in sorted(state.ban_templates):
        rows = ctx.tpl_rows.get(tid)
        if rows is not None:
            banned[rows] = True
    preferred = np.zeros(ix.n_recipes, dtype=bool)
    if state.prefer_recipes:
        preferred[[r for r in sorted(state.prefer_recipes)
                   if 0 <= r < ix.n_recipes]] = True
    preferred &= ~banned

    item_banned = np.zeros(ix.n_items, dtype=bool)
    for base in sorted(state.ban_items):
        i = ix.item_of(base)
        if i >= 0:
            item_banned[i] = True
    item_factor = np.ones(ix.n_items, dtype=np.float64)
    for base in sorted(state.prefer_items):
        i = ix.item_of(base)
        if i >= 0:
            item_factor[i] = ITEM_DISCOUNT

    v.junk_p, v.role_p, v.eff_role = junk, role_p, eff
    v.banned, v.preferred = banned, preferred
    v.item_banned, v.item_factor = item_banned, item_factor
    v.stock = {k: val for k, val in sorted(state.stock.items())
               if ix.item_of(k) < 0 or not item_banned[ix.item_of(k)]}
    v.score = (LazyScore(ctx, state.delta)
               if state.delta.size and np.abs(state.delta).max() > 0
               else ix.score)
    v.sig = state.signature()
    v.stats = stats
    return v


# ================================================================== the inquiry


class TemplateInquiry:
    """One question per template family per session, by uncertainty x impact.

    Java mapping: ``api/recipe/routing/feedback/TemplateInquiry``.

        uncertainty(t) = H2(junk'(t)) * n0 / (n0 + evidence(t))
        impact(t)      = 0.5 * crafts(t)/max crafts + 0.5 * closeness(t)
        score(t)       = uncertainty(t) * impact(t)

    ``H2`` is the Bernoulli entropy of the Beta's posterior mean — the Beta's
    posterior-predictive entropy — and the evidence discount is what stops the
    layer asking twice about a template the user already ruled on.
    ``closeness(t)`` is ``best unit cost / this template's best unit cost`` over
    the shortlists the plan built, so a template that *almost* won is as
    interesting as one that did.  Executed templates contribute the crafts
    term; shortlisted ones the closeness term.
    """

    def __init__(self, ctx: PackContext, fb: PlannerFeedback):
        self.ctx, self.fb = ctx, fb
        self.crafts: dict = {}
        self.uses: dict = {}
        self.close: dict = {}
        self.examples: dict = {}

    def observe(self, plan: P.Plan) -> None:
        ix = self.ctx.ix
        for a in plan.actions:
            tid = str(a["template_id"])
            self.crafts[tid] = self.crafts.get(tid, 0) + int(a["times"])
            self.uses[tid] = self.uses.get(tid, 0) + 1
            self.close[tid] = max(self.close.get(tid, 0.0), 1.0)
            self.examples.setdefault(tid, []).append(int(a["recipe_idx"]))
            short = a["why"].get("shortlist") or []
            costs = [c["unit_cost"] for c in short if c["unit_cost"] > 0]
            best = min(costs) if costs else 0.0
            for entry in short:
                c = float(entry["unit_cost"])
                if c <= 0 or best <= 0 or c > INQUIRY_MARGIN * best:
                    continue
                t2 = str(ix.template_id[int(entry["recipe_idx"])])
                self.close[t2] = max(self.close.get(t2, 0.0), best / c)
                self.examples.setdefault(t2, []).append(
                    int(entry["recipe_idx"]))

    def questions(self, limit: int = INQUIRY_LIMIT) -> list:
        ix = self.ctx.ix
        view = self.fb.view()
        state = self.fb.state
        max_crafts = max(self.crafts.values()) if self.crafts else 1
        rows = []
        for tid in sorted(set(self.close) | set(self.crafts)):
            rows_of = self.ctx.tpl_rows.get(tid)
            if rows_of is None or not rows_of.size:
                continue
            r0 = int(rows_of[0])
            p = float(view.junk_p[r0])
            a, b = state.evidence.get(tid, (0.0, 0.0))
            unc = _binary_entropy(p) * PRIOR_STRENGTH / (PRIOR_STRENGTH + a + b)
            impact = (0.5 * self.crafts.get(tid, 0) / max(max_crafts, 1)
                      + 0.5 * self.close.get(tid, 0.0))
            rows.append({
                "template_id": tid,
                "family": self.ctx.family_of(tid),
                "score": round(unc * impact, 6),
                "uncertainty": round(unc, 6), "impact": round(impact, 6),
                "junk_belief": round(p, 6),
                "role_belief": {c: round(float(view.role_p[r0, j]), 6)
                                for j, c in enumerate(ix.role_classes)},
                "role_entropy": round(_entropy(view.role_p[r0]), 6),
                "evidence": [round(a, 4), round(b, 4)],
                "n_members": int(rows_of.size),
                "crafts": int(self.crafts.get(tid, 0)),
                "uses": int(self.uses.get(tid, 0)),
                "closeness": round(self.close.get(tid, 0.0), 6),
                "examples": sorted(set(self.examples.get(tid, [])))[:3],
                "answers": ["keep", "ban at template scope"],
            })
        rows.sort(key=lambda d: (-d["score"], d["template_id"]))
        out, seen = [], set(state.asked)
        for row in rows:
            if row["family"] in seen or row["score"] <= 0:
                continue
            seen.add(row["family"])
            out.append(row)
            if len(out) >= limit:
                break
        return out

    def record(self, questions: list) -> None:
        """Mark the families as asked, so the session never repeats one."""
        for q in questions:
            if q["family"] not in self.fb.state.asked:
                self.fb.state.asked.append(q["family"])


# ===================================================================== harness


def load_ctx(cache_dir: Path, out_dir: Path, slug: str, knobs: P.Knobs):
    """``(PackIndex, PackContext, Policy, pool units)`` for one pack."""
    ix = P.load_pack(cache_dir, out_dir, slug, knobs)
    ctx = PackContext(out_dir, ix, knobs)
    return ix, ctx


def solved(ix, knobs, opt=None):
    """``(policy, cost solve)`` for one configuration — both read the view."""
    pol = P.Policy(ix, knobs, opt)
    return pol, P.solve_cost(ix, pol, knobs, None, opt)


def executed_for(plan: P.Plan, base_id: str) -> int:
    """The recipe the plan used to make ``base_id``, or ``-1``."""
    used = -1
    for a in plan.actions:
        if any(r["base_id"] == base_id for r in a["outputs"]):
            used = int(a["recipe_idx"])
    return used


def stage_family(args, cache_dir: Path, out_dir: Path, knobs: P.Knobs) -> dict:
    """One ban on one member of a large template family, measured.

    Reports, per family: how many siblings cross the exclusion threshold, how
    much evidence leaked outside the family, and how many *plans* that used a
    sibling stop using the family afterwards.
    """
    out = {"families": [], "plans": {}}
    for slug in P.pack_slugs(cache_dir, args.packs):
        ix, ctx = load_ctx(cache_dir, out_dir, slug, knobs)
        units = P.pool_units(ix)
        base_pol = P.Policy(ix, knobs)
        sizes = sorted(((v.size, k) for k, v in ctx.tpl_rows.items()),
                       reverse=True)[:args.families]
        # targets whose default plan uses a member of one of these families
        rng = np.random.default_rng(args.seed)
        pool = np.flatnonzero(ix.item_nprod > 0)
        pick = rng.choice(pool, size=min(args.targets, pool.size),
                          replace=False)
        targets = sorted(str(ix.items[i]) for i in np.sort(pick))
        sv0 = P.solve_cost(ix, base_pol, knobs)
        base_plans = {t: P.plan_target(ix, base_pol, knobs, t, 1, None, sv0,
                                       units) for t in targets}
        for n, tid in sizes:
            rows = ctx.tpl_rows[tid]
            fb = PlannerFeedback(ctx)
            t0 = time.perf_counter()
            info = fb.ban(recipe_idx=int(rows[0]))
            ms = (time.perf_counter() - t0) * 1000.0
            view = fb.view()
            opt = P.PlanOptions(feedback=view)
            pol = P.Policy(ix, knobs, opt)
            family = {str(t) for t, _ in info["touched"]}
            outside = np.zeros(ix.n_recipes, dtype=bool)
            for t in family:
                rr = ctx.tpl_rows.get(t)
                if rr is not None:
                    outside[rr] = True
            users = [t for t, pl in base_plans.items()
                     if any(str(a["template_id"]) == tid for a in pl.actions)]
            sv = P.solve_cost(ix, pol, knobs, None, opt)
            still = 0
            for t in users:
                pl = P.plan_target(ix, pol, knobs, t, 1, None, sv, units, opt)
                if any(str(a["template_id"]) == tid for a in pl.actions):
                    still += 1
            # The inquiry's second answer: a ban at *template* scope, which
            # is absolute — the sole-producer protection may not rescue it.
            fb2 = PlannerFeedback(ctx)
            fb2.ban(template_id=tid)
            opt2 = P.PlanOptions(feedback=fb2.view())
            pol2 = P.Policy(ix, knobs, opt2)
            sv2 = P.solve_cost(ix, pol2, knobs, None, opt2)
            still2 = unres2 = 0
            for t in users:
                pl = P.plan_target(ix, pol2, knobs, t, 1, None, sv2, units,
                                   opt2)
                if any(str(a["template_id"]) == tid for a in pl.actions):
                    still2 += 1
                if pl.status != "ok":
                    unres2 += 1
            nb = np.zeros(ix.n_recipes, dtype=bool)
            for t, _ in info["touched"][1:]:
                rr = ctx.tpl_rows.get(t)
                if rr is not None:
                    nb[rr] = True
            row = {
                "slug": slug, "template_id": tid, "members": int(n),
                "junk_before": round(float(ix.junk_p[rows[0]]), 6),
                "junk_after": round(float(view.junk_p[rows[0]]), 6),
                "tau_junk": round(knobs.tau_junk, 6),
                "siblings_over_tau": int((view.junk_p[rows] >
                                          knobs.tau_junk).sum()),
                "excluded_before": int(base_pol.excluded[rows].sum()),
                "excluded_after": int(pol.excluded[rows].sum()),
                "neighbours": len(info["touched"]) - 1,
                "recipes_excluded_total_before": int(base_pol.excluded.sum()),
                "recipes_excluded_total_after": int(pol.excluded.sum()),
                "recipes_excluded_outside_family": int(
                    (pol.excluded & ~base_pol.excluded & ~outside).sum()),
                "recipes_excluded_in_neighbours": int(
                    (pol.excluded & ~base_pol.excluded & nb).sum()),
                "neighbour_members": int(nb.sum()),
                "protected_after": int((pol.protected_ & outside).sum()),
                "plans_using_family": len(users),
                "plans_still_using_family": still,
                "template_ban_excluded": int(pol2.excluded[rows].sum()),
                "template_ban_plans_still_using": still2,
                "template_ban_plans_incomplete": unres2,
                "update_ms": round(ms, 3),
            }
            out["families"].append(row)
            print(f"  {slug:<22}{tid}  n={n:>3}  junk {row['junk_before']:.4f}"
                  f" -> {row['junk_after']:.4f}   siblings>tau "
                  f"{row['siblings_over_tau']}/{n}   excluded {row['excluded_before']}"
                  f" -> {row['excluded_after']}   neighbours {row['neighbours']}"
                  f"   outside +{row['recipes_excluded_outside_family']}"
                  f" (nb +{row['recipes_excluded_in_neighbours']}"
                  f"/{row['neighbour_members']})"
                  f"   plans {still}/{len(users)} still use it"
                  f"   [template-scope ban: {still2}/{len(users)},"
                  f" {unres2} incomplete]"
                  f"   {row['update_ms']:.2f} ms")
        out["plans"][slug] = len(targets)
    return out




# --------------------------------------------------------------- audit cases


def case_verdict(ix, knobs, units, output, chosen, preferred, pol, sv,
                 opt=None) -> dict:
    """The Phase 3 audit verdict for one case, under a given configuration."""
    pl = P.plan_target(ix, pol, knobs, output, 1, None, sv, units, opt)
    used = executed_for(pl, output)
    score = P.eff_score(ix, opt)
    item = ix.item_of(output)
    verdict = ("preferred" if used in preferred else
               "chosen" if used == chosen else
               "neither" if used >= 0 else "unplanned")
    def num(r):
        if r < 0 or item < 0:
            return {}
        c, _ = P.producer_cost(ix, sv, knobs, pol, r, item, set(), opt)
        return {"recipe_idx": int(r),
                "unit_cost": round(c, 4) if c < math.inf else None,
                "producer_score": round(float(score[r]), 4),
                "excluded": bool(pol.excluded[r])}
    pref = sorted({int(r) for r in preferred})
    best = max(pref, key=lambda r: (float(score[r]), -r)) if pref else -1
    return {"output_base_id": output, "verdict": verdict,
            "executed": num(used), "chosen": num(chosen),
            "preferred": [num(r) for r in pref],
            # the ranker's own statement about this case: the score gap
            # between the audit's best-scored preferred producer and the one
            # it complained about.  Positive = the ranker agrees with the
            # audit.  This is what a ``choose`` click moves.
            "score_margin": (round(float(score[best]) - float(score[chosen]), 4)
                             if best >= 0 and 0 <= chosen < ix.n_recipes
                             else None),
            "status": pl.status, "actions": len(pl.actions)}


def _slug_of(pack: str, slugs) -> str:
    """The slug of a reading's pack — **exact** normalised equality only.

    ``common.match_name`` falls back to a unique prefix match, which resolves
    one pack's readings onto a *different* pack when that pack's own slug is
    not in the candidate list.  Here the answer has to be the pack itself or
    nothing.
    """
    from common import normalise_name
    key = normalise_name(pack)
    for s in slugs:
        if normalise_name(s) == key:
            return s
    return None


def _cases_for(out_dir: Path, slugs) -> dict:
    """``slug -> {(finding, output, chosen): {preferred, weight}}``."""
    out: dict = {}
    for g in E.gold_pairs(out_dir):
        hit = _slug_of(g["pack"], list(slugs))
        if hit:
            out.setdefault(hit, []).append(g)
    return {k: E.gold_cases(v) for k, v in out.items()}


def stage_audit(args, cache_dir: Path, out_dir: Path, knobs: P.Knobs) -> dict:
    """One ``choose`` per losing case, then every case re-scored.

    The pairs are the round-006 audit's own readings, used here as the
    *feedback stream*.  That is not a held-out measurement and is never
    reported as one: the question this stage answers is "does one click move
    the planner, and how far does it carry", not "how accurate is the layer".
    """
    slugs = P.pack_slugs(cache_dir, args.packs)
    cases = _cases_for(out_dir, slugs)
    out = {"before": [], "after": [], "shared": [], "clicks": []}
    deltas: dict = {}
    packs = {}
    for slug in sorted(cases):
        ix, ctx = load_ctx(cache_dir, out_dir, slug, knobs)
        units = P.pool_units(ix)
        pol0, sv0 = solved(ix, knobs)
        packs[slug] = (ix, ctx, units)
        for (finding, output, chosen), c in sorted(cases[slug].items()):
            row = case_verdict(ix, knobs, units, output, chosen,
                               c["preferred"], pol0, sv0)
            row.update({"slug": slug, "finding_id": finding})
            out["before"].append(row)
        # one click per case the default planner loses, in the pack's own store
        fb = PlannerFeedback(ctx)
        clicked = []
        for (finding, output, chosen), c in sorted(cases[slug].items()):
            row = next(r for r in out["before"]
                       if r["finding_id"] == finding
                       and r["output_base_id"] == output)
            if row["verdict"] != "chosen":
                continue
            best = min(c["preferred"],
                       key=lambda r: (P.producer_cost(
                           ix, sv0, knobs, pol0, r, ix.item_of(output),
                           set())[0], r))
            info = fb.choose(best, over=chosen)
            info.update({"slug": slug, "finding_id": finding,
                         "output_base_id": output, "a": int(best),
                         "b": int(chosen)})
            out["clicks"].append(info)
            clicked.append(finding)
        deltas[slug] = fb.state.delta.copy()
        opt = fb.options()
        pol1, sv1 = solved(ix, knobs, opt)
        for (finding, output, chosen), c in sorted(cases[slug].items()):
            row = case_verdict(ix, knobs, units, output, chosen,
                               c["preferred"], pol1, sv1, opt)
            row.update({"slug": slug, "finding_id": finding,
                        "clicked": finding in clicked})
            out["after"].append(row)
    # the shared-delta variant: every click from every pack, applied everywhere
    shared = None
    for slug in sorted(deltas):
        d = deltas[slug]
        if d.size and np.abs(d).max() > 0:
            shared = d.copy() if shared is None else shared + d
    if shared is not None:
        for slug in sorted(cases):
            ix, ctx, units = packs[slug]
            fb = PlannerFeedback(ctx)
            fb.state.delta = shared.copy()
            opt = fb.options()
            pol, sv = solved(ix, knobs, opt)
            for (finding, output, chosen), c in sorted(cases[slug].items()):
                row = case_verdict(ix, knobs, units, output, chosen,
                                   c["preferred"], pol, sv, opt)
                row.update({"slug": slug, "finding_id": finding})
                out["shared"].append(row)
    _print_audit(out)
    return out


def _print_audit(out: dict) -> None:
    def tally(rows):
        return {v: sum(1 for r in rows if r["verdict"] == v)
                for v in ("preferred", "chosen", "neither", "unplanned")}
    print(f"\n  {'configuration':<34}{'cases':>6}{'preferred':>11}"
          f"{'chosen':>8}{'neither':>9}{'unplanned':>11}")
    for name, key in (("Phase 3 default", "before"),
                      ("+ one click per lost case (per pack)", "after"),
                      ("+ the same clicks, delta shared", "shared")):
        rows = out[key]
        if not rows:
            continue
        t = tally(rows)
        print(f"  {name:<34}{len(rows):>6}{t['preferred']:>11}"
              f"{t['chosen']:>8}{t['neither']:>9}{t['unplanned']:>11}")
    print(f"\n  {'finding':<24}{'output':<42}{'before':<10}{'after':<10}"
          f"{'shared':<10}{'margin b':>10}{'margin a':>10}{'margin s':>10}")
    def key(r):
        # one finding can name two *different* complained-about producers for
        # the same output, so the case key has to carry it
        return (r["finding_id"], r["output_base_id"],
                r["chosen"].get("recipe_idx", -1))
    after = {key(r): r for r in out["after"]}
    shared = {key(r): r for r in out["shared"]}
    for r in out["before"]:
        k = key(r)
        def mg(d):
            v = d.get("score_margin")
            return "-" if v is None else f"{v:+.3f}"
        print(f"  {r['finding_id']:<24}{r['output_base_id']:<42}"
              f"{r['verdict']:<10}{after.get(k, {}).get('verdict', '-'):<10}"
              f"{shared.get(k, {}).get('verdict', '-'):<10}"
              f"{mg(r):>10}{mg(after.get(k, {})):>10}"
              f"{mg(shared.get(k, {})):>10}")
    for c in out["clicks"]:
        print(f"    click {c['finding_id']:<24} {c['a']} over {c['b']}: "
              f"margin {c['margin_before']:+.4f} -> {c['margin_after']:+.4f}"
              f"  |delta| {c['norm']:.4f} / cap {c['cap']:.4f}"
              f"{'  CLIPPED' if c['clipped'] else ''}")


# ------------------------------------------------- the minimum-annotation curve


class PackBench:
    """One pack, preloaded: index, context, targets, weak labels, units.

    What a click rebuilds is only the policy, the cost solve and the plans —
    never the index, never the features, never the models.
    """

    def __init__(self, cache_dir, out_dir, slug, knobs, n_targets, seed):
        packs = read_json(Path(cache_dir) / "packs.json")
        self.slug, self.pack = slug, packs[slug]
        self.ix, self.ctx = load_ctx(cache_dir, out_dir, slug, knobs)
        self.units = P.pool_units(self.ix)
        self.knobs = knobs
        self.wl = E.weak_labels(self.pack)
        pdir = E.pack_dir_for(slug, self.pack)
        rows = E.read_index(pdir) if pdir else []
        outs = {g["output_base_id"] for g in E.gold_pairs(Path(out_dir))}
        sel = E.select_targets(self.ix, rows, n_targets, seed, outs,
                               set()) if rows else []
        self.targets = sorted({r["target"] for r in sel})
        self.fb = PlannerFeedback(self.ctx)

    def measure(self, opt=None) -> dict:
        """Mean prototype metrics over this pack's targets, this config."""
        pol, sv = solved(self.ix, self.knobs, opt)
        keys = ("junk_in_path_wl", "junk_in_path_model", "actions",
                "incomplete", "raw_nonleaf", "tier_reversals")
        acc = {k: 0.0 for k in keys}
        n = 0
        for t in self.targets:
            pl = P.plan_target(self.ix, pol, self.knobs, t, 1, None, sv,
                               self.units, opt)
            m = E.chain_metrics(self.ix, pol, self.knobs, self.units,
                                E.plan_actions(self.ix, pl), pl.raw,
                                pl.unresolved, t, self.wl, 0.0)
            for k in keys:
                acc[k] += float(m[k])
            n += 1
        return {**{k: (v / n if n else 0.0) for k, v in acc.items()},
                "n": n, "pol": pol, "sv": sv}


def _verdicts(bench: "PackBench", cases: dict, opt=None, pol=None, sv=None):
    if pol is None:
        pol, sv = solved(bench.ix, bench.knobs, opt)
    out = {}
    for (finding, output, chosen), c in sorted(cases.items()):
        row = case_verdict(bench.ix, bench.knobs, bench.units, output, chosen,
                           c["preferred"], pol, sv, opt)
        out[(finding, output, chosen)] = row["verdict"]
    return out


def _readings(out_dir: Path, slugs) -> list:
    rows = []
    for g in E.gold_pairs(out_dir):
        hit = _slug_of(g["pack"], list(slugs))
        if hit:
            g = dict(g)
            g["slug"] = hit
            rows.append(g)
    return rows


def stage_curve(args, cache_dir: Path, out_dir: Path, knobs: P.Knobs) -> dict:
    """Replay the audit's own readings as a feedback stream, one at a time.

    **The same readings are both the stream and the score.**  Stated, not
    hidden: this table shows how fast the layer absorbs a known set of
    corrections, not how well it predicts unseen ones.  The leave-one-out
    table is the held-out counterpart.
    """
    slugs = P.pack_slugs(cache_dir, args.packs)
    readings = _readings(out_dir, slugs)
    cases = _cases_for(out_dir, slugs)
    order = sorted({g["slug"] for g in readings})
    bench = {s: PackBench(cache_dir, out_dir, s, knobs, args.curve_targets,
                          args.seed) for s in order}
    base_metrics = {s: bench[s].measure() for s in order}
    base_verdicts = {s: _verdicts(bench[s], cases[s],
                                  pol=base_metrics[s]["pol"],
                                  sv=base_metrics[s]["sv"]) for s in order}
    n_targets = sum(m["n"] for m in base_metrics.values())
    n_cases = sum(len(c) for c in cases.values())

    def roll(metrics, verdicts):
        won = sum(1 for v in verdicts.values() for x in v.values()
                  if x == "preferred")
        lost = sum(1 for v in verdicts.values() for x in v.values()
                   if x == "chosen")
        def wmean(key):
            return sum(metrics[s][key] * metrics[s]["n"] for s in order) \
                / max(n_targets, 1)
        return {"cases_won": won, "cases_lost": lost,
                "junk_in_path_wl": round(wmean("junk_in_path_wl"), 6),
                "junk_in_path_model": round(wmean("junk_in_path_model"), 6),
                "incomplete": round(wmean("incomplete"), 6),
                "actions": round(wmean("actions"), 4)}

    curves = []
    for seed in range(args.seeds):
        rng = np.random.default_rng(args.seed + seed)
        perm = rng.permutation(len(readings)).tolist()
        for s in order:
            bench[s].fb.reset()
        metrics = {s: dict(base_metrics[s]) for s in order}
        verdicts = {s: dict(base_verdicts[s]) for s in order}
        points = [{"clicks": 0, **roll(metrics, verdicts)}]
        for i, j in enumerate(perm, start=1):
            g = readings[j]
            b = bench[g["slug"]]
            try:
                b.fb.choose(int(g["preferred_recipe_idx"]),
                            over=int(g["chosen_recipe_idx"]))
            except ValueError:
                pass
            opt = b.fb.options()
            metrics[g["slug"]] = b.measure(opt)
            verdicts[g["slug"]] = _verdicts(b, cases[g["slug"]], opt,
                                            metrics[g["slug"]]["pol"],
                                            metrics[g["slug"]]["sv"])
            points.append({"clicks": i, **roll(metrics, verdicts)})
        curves.append(points)
        print(f"  seed {seed}: won {points[0]['cases_won']} -> "
              f"{points[-1]['cases_won']}, lost {points[0]['cases_lost']} -> "
              f"{points[-1]['cases_lost']}, junk_wl "
              f"{points[0]['junk_in_path_wl']:.4f} -> "
              f"{points[-1]['junk_in_path_wl']:.4f}")

    keys = ("cases_won", "cases_lost", "junk_in_path_wl",
            "junk_in_path_model", "incomplete", "actions")
    mean = []
    for i in range(len(curves[0])):
        row = {"clicks": i}
        for k in keys:
            vals = [c[i][k] for c in curves]
            row[k] = round(float(np.mean(vals)), 6)
            row[k + "_lo"] = round(float(np.min(vals)), 6)
            row[k + "_hi"] = round(float(np.max(vals)), 6)
        mean.append(row)
    print(f"\n  learning curve, mean over {args.seeds} random orders "
          f"({n_targets} targets, {n_cases} cases, {len(readings)} readings)")
    print(f"  {'clicks':>7}{'cases won':>11}{'won lo-hi':>12}{'lost':>7}"
          f"{'junk_wl':>10}{'junk_model':>12}{'incomplete':>12}{'actions':>10}")
    for row in mean:
        span = f"{row['cases_won_lo']:.0f}-{row['cases_won_hi']:.0f}"
        print(f"  {row['clicks']:>7}{row['cases_won']:>11.2f}{span:>12}"
              f"{row['cases_lost']:>7.2f}{row['junk_in_path_wl']:>10.4f}"
              f"{row['junk_in_path_model']:>12.4f}"
              f"{row['incomplete']:>12.4f}{row['actions']:>10.2f}")

    # ---- leave-one-out: every click but one, then score the held-out one ---
    loo = []
    for held in range(len(readings)):
        g = readings[held]
        s = g["slug"]
        for t in order:
            bench[t].fb.reset()
        for j, other in enumerate(readings):
            if j == held:
                continue
            try:
                bench[other["slug"]].fb.choose(
                    int(other["preferred_recipe_idx"]),
                    over=int(other["chosen_recipe_idx"]))
            except ValueError:
                pass
        key = (g["finding_id"], g["output_base_id"],
               int(g["chosen_recipe_idx"]))
        own = _verdicts(bench[s], cases[s], bench[s].fb.options())
        shared_state = FeedbackState(s, bench[s].pack,
                                     n_pair=len(bench[s].ctx.names))
        for t in order:
            if bench[t].fb.state.delta.size:
                shared_state.delta = (shared_state.delta
                                      + bench[t].fb.state.delta)
        shared_fb = PlannerFeedback(bench[s].ctx, shared_state)
        sh = _verdicts(bench[s], cases[s], shared_fb.options())
        loo.append({"finding_id": g["finding_id"],
                    "output_base_id": g["output_base_id"],
                    "slug": s,
                    "baseline": base_verdicts[s].get(key, "-"),
                    "held_out_own_pack": own.get(key, "-"),
                    "held_out_shared": sh.get(key, "-")})
    def tally(key):
        return {v: sum(1 for r in loo if r[key] == v)
                for v in ("preferred", "chosen", "neither", "unplanned")}
    print(f"\n  leave-one-out over {len(readings)} readings "
          f"(the held-out reading is never clicked)")
    print(f"  {'configuration':<40}{'preferred':>11}{'chosen':>8}"
          f"{'neither':>9}{'unplanned':>11}")
    n_other = len(readings) - 1
    for name, key in (("Phase 3 default", "baseline"),
                      (f"{n_other} other clicks, own pack only",
                       "held_out_own_pack"),
                      (f"{n_other} other clicks, delta shared",
                       "held_out_shared")):
        t = tally(key)
        print(f"  {name:<40}{t['preferred']:>11}{t['chosen']:>8}"
              f"{t['neither']:>9}{t['unplanned']:>11}")
    for r in loo:
        if r["baseline"] != r["held_out_shared"] or r["baseline"] == "chosen":
            print(f"    {r['finding_id']:<24}{r['output_base_id']:<42}"
                  f"{r['baseline']:<11}{r['held_out_own_pack']:<11}"
                  f"{r['held_out_shared']}")
    return {"curve": mean, "seeds": curves, "leave_one_out": loo,
            "n_targets": n_targets, "n_readings": len(readings),
            "n_cases": n_cases}


# ------------------------------------------------------------ inquiry precision


def _template_labels(out_dir: Path):
    """``(pack, template_id) -> (label is junk, tier)`` for labelled rows only.

    Evaluation only: the label layer never reaches the learner, the planner or
    the inquiry score.  It is read here to ask whether the questions the
    inquiry chose were worth asking.
    """
    t = pq.read_table(Path(out_dir) / "labels" / "template_labels.parquet",
                      columns=["pack", "template_id", "junk_target",
                               "best_tier", "n_labeled"])
    pack = np.asarray(t.column("pack").to_pylist())
    tid = np.asarray(t.column("template_id").to_pylist())
    tgt = t.column("junk_target").to_numpy(zero_copy_only=False)
    tier = t.column("best_tier").to_numpy(zero_copy_only=False)
    ok = np.isfinite(tgt)
    out: dict = {}
    counts: dict = {}
    for k, v, g, b in zip(pack[ok], tid[ok], tgt[ok], tier[ok]):
        out[(str(k), str(v))] = (float(g) >= 0.5, int(b))
        counts[str(k)] = counts.get(str(k), 0) + 1
    return out, counts


def stage_inquiry(args, cache_dir: Path, out_dir: Path, knobs: P.Knobs) -> dict:
    """Are the questions worth asking?

    For each pack: plan the A/B target sample, feed every plan to
    ``TemplateInquiry``, take the top ``INQUIRY_LIMIT`` questions, and compare
    them against the *population* of templates those plans touched.  Two
    numbers matter — the share of questioned templates that carry a label at
    all, and the share whose label disagrees with the current belief.  Both
    are reported against the population rate, because a ranking that matches
    the population has added nothing.
    """
    labels, counts = _template_labels(out_dir)
    slugs = P.pack_slugs(cache_dir, args.packs)
    available = [s for s in slugs if counts.get(s, 0) > 0]
    ranked = sorted(available, key=lambda s: (-counts[s], s))[:args.packs_top]
    packs = read_json(Path(cache_dir) / "packs.json")
    out = {"packs": [], "questions": []}
    for slug in ranked:
        t0 = time.perf_counter()
        ix, ctx = load_ctx(cache_dir, out_dir, slug, knobs)
        units = P.pool_units(ix)
        fb = PlannerFeedback(ctx)
        pol, sv = solved(ix, knobs)
        pdir = E.pack_dir_for(slug, packs[slug])
        rows = E.read_index(pdir) if pdir else []
        outs = {g["output_base_id"] for g in E.gold_pairs(out_dir)}
        sel = E.select_targets(ix, rows, args.targets, args.seed, outs, set())
        targets = sorted({r["target"] for r in sel})
        inq = TemplateInquiry(ctx, fb)
        for t in targets:
            inq.observe(P.plan_target(ix, pol, knobs, t, 1, None, sv, units))
        qs = inq.questions(args.inquiry_limit)
        pop = sorted(set(inq.close) | set(inq.crafts))

        def score_set(tids):
            lab = dis = 0
            for tid in tids:
                got = labels.get((slug, tid))
                if got is None:
                    continue
                lab += 1
                rows_of = ctx.tpl_rows.get(tid)
                if rows_of is None or not rows_of.size:
                    continue
                belief = bool(ix.junk_p[rows_of[0]] > knobs.tau_junk)
                if belief != got[0]:
                    dis += 1
            return lab, dis
        q_lab, q_dis = score_set([q["template_id"] for q in qs])
        p_lab, p_dis = score_set(pop)
        row = {
            "slug": slug, "labelled_templates": counts[slug],
            "targets": len(targets), "templates_touched": len(pop),
            "questions": len(qs),
            "questions_labelled": q_lab,
            "questions_labelled_share": round(q_lab / len(qs), 4) if qs else 0.0,
            "questions_disagreeing": q_dis,
            "questions_disagree_share": round(q_dis / q_lab, 4) if q_lab else 0.0,
            "population_labelled": p_lab,
            "population_labelled_share": round(p_lab / len(pop), 4) if pop else 0.0,
            "population_disagreeing": p_dis,
            "population_disagree_share": round(p_dis / p_lab, 4) if p_lab else 0.0,
            "families": len({q["family"] for q in qs}),
            "seconds": round(time.perf_counter() - t0, 2),
        }
        out["packs"].append(row)
        for q in qs:
            got = labels.get((slug, q["template_id"]))
            out["questions"].append({
                "slug": slug, **{k: q[k] for k in
                                 ("template_id", "family", "score",
                                  "uncertainty", "impact", "junk_belief",
                                  "n_members", "crafts", "closeness",
                                  "examples")},
                "label": None if got is None else bool(got[0]),
                "label_tier": None if got is None else got[1]})
    print(f"\n  {'pack':<38}{'targets':>8}{'touched':>9}{'asked':>7}"
          f"{'families':>9}{'labelled':>10}{'pop.':>8}{'disagree':>10}{'pop.':>8}")
    for r in out["packs"]:
        print(f"  {r['slug']:<38}{r['targets']:>8}{r['templates_touched']:>9}"
              f"{r['questions']:>7}{r['families']:>9}"
              f"{r['questions_labelled_share']:>10.3f}"
              f"{r['population_labelled_share']:>8.3f}"
              f"{r['questions_disagree_share']:>10.3f}"
              f"{r['population_disagree_share']:>8.3f}")
    tot_q = sum(r["questions"] for r in out["packs"])
    tot_ql = sum(r["questions_labelled"] for r in out["packs"])
    tot_qd = sum(r["questions_disagreeing"] for r in out["packs"])
    tot_p = sum(r["templates_touched"] for r in out["packs"])
    tot_pl = sum(r["population_labelled"] for r in out["packs"])
    tot_pd = sum(r["population_disagreeing"] for r in out["packs"])
    print(f"  {'pooled':<38}{'':>8}{tot_p:>9}{tot_q:>7}{'':>9}"
          f"{tot_ql / max(tot_q, 1):>10.3f}{tot_pl / max(tot_p, 1):>8.3f}"
          f"{tot_qd / max(tot_ql, 1):>10.3f}{tot_pd / max(tot_pl, 1):>8.3f}")
    out["pooled"] = {
        "questions": tot_q, "questions_labelled": tot_ql,
        "questions_labelled_share": round(tot_ql / max(tot_q, 1), 4),
        "questions_disagree_share": round(tot_qd / max(tot_ql, 1), 4),
        "population": tot_p, "population_labelled": tot_pl,
        "population_labelled_share": round(tot_pl / max(tot_p, 1), 4),
        "population_disagree_share": round(tot_pd / max(tot_pl, 1), 4)}
    return out


# ------------------------------------------------------------------- latency


def _q(v):
    v = np.asarray(v, dtype=np.float64)
    return {"n": int(v.size), "median": round(float(np.median(v)), 4),
            "p95": round(float(np.percentile(v, 95)), 4),
            "max": round(float(v.max()), 4)}


def stage_latency(args, cache_dir: Path, out_dir: Path, knobs: P.Knobs) -> dict:
    """Per-call wall of every API method, the view rebuild and the re-score.

    The target is the plan's own: milliseconds, under 5 ms per update on the
    largest pack.  What is *not* an update — rebuilding the candidate policy
    and re-running the cost solve so the next request sees the change — is
    timed separately and reported, because it is the honest cost of acting on
    a click rather than of recording one.
    """
    out = {"packs": []}
    for slug in P.pack_slugs(cache_dir, args.packs):
        t0 = time.perf_counter()
        ix, ctx = load_ctx(cache_dir, out_dir, slug, knobs)
        build_s = time.perf_counter() - t0
        rng = np.random.default_rng(args.seed)
        live = np.flatnonzero(ix.keep)
        multi = np.flatnonzero(np.diff(ix.prod_ptr) >= 2)
        tids = list(ctx.tpl_rows)
        items = rng.choice(ix.n_items, size=min(args.calls, ix.n_items),
                           replace=False)
        recs = rng.choice(live, size=min(args.calls, live.size), replace=False)
        timings: dict = {}

        def run(name, fn, n):
            acc = []
            for i in range(n):
                t = time.perf_counter()
                fn(i)
                acc.append((time.perf_counter() - t) * 1000.0)
            timings[name] = _q(acc)

        fb = PlannerFeedback(ctx)
        run("have", lambda i: fb.have(str(ix.items[items[i]]), 64.0),
            len(items))
        run("ban(item)", lambda i: fb.ban(base_id=str(ix.items[items[i]])),
            len(items))
        run("unban(item)", lambda i: fb.unban(base_id=str(ix.items[items[i]])),
            len(items))
        fb.reset()
        run("ban(recipe)", lambda i: fb.ban(recipe_idx=int(recs[i])),
            min(args.calls, len(recs)))
        fb.reset()
        run("ban(template)",
            lambda i: fb.ban(template_id=tids[i % len(tids)]),
            min(args.calls, len(tids)))
        fb.reset()
        run("prefer(item)",
            lambda i: fb.prefer(base_id=str(ix.items[items[i]])), len(items))
        fb.reset()
        # choose: a real sibling pair from a multi-producer output
        pairs = []
        for it in rng.choice(multi, size=min(args.calls, multi.size),
                             replace=False).tolist():
            g = ctx.group(int(it))
            if g.size >= 2:
                pairs.append((int(g[0]), int(g[1])))
        run("choose", lambda i: fb.choose(pairs[i][0], over=pairs[i][1]),
            len(pairs))
        fb.reset()
        prefer_recs = [int(ctx.group(int(it))[0]) for it in
                       rng.choice(multi, size=min(50, multi.size),
                                  replace=False).tolist()]
        run("prefer(recipe)",
            lambda i: fb.prefer(recipe_idx=prefer_recs[i]), len(prefer_recs))
        fb.reset()
        run("reset", lambda i: fb.reset(), 20)

        # the view rebuild, with and without template evidence
        fb.reset()
        for r in recs[:10].tolist():
            fb.ban(recipe_idx=int(r))
        acc = []
        for _ in range(20):
            fb._dirty()
            t = time.perf_counter()
            fb.view()
            acc.append((time.perf_counter() - t) * 1000.0)
        timings["view (10 template bans)"] = _q(acc)
        fb.reset()
        fb.choose(pairs[0][0], over=pairs[0][1])
        acc = []
        for _ in range(20):
            fb._dirty()
            t = time.perf_counter()
            fb.view()
            acc.append((time.perf_counter() - t) * 1000.0)
        timings["view (delta only)"] = _q(acc)

        # the re-score: one output group's producers, cold cache each time
        view = fb.view()
        acc, sizes = [], []
        for it in rng.choice(multi, size=min(args.calls, multi.size),
                             replace=False).tolist():
            g = ctx.group(int(it))
            view.score.gcache.pop(int(it), None)
            t = time.perf_counter()
            view.score._group_theta(int(it))
            acc.append((time.perf_counter() - t) * 1000.0)
            sizes.append(int(g.size))
        timings["re-score one output group"] = _q(acc)

        # not an update: what the next request has to rebuild
        opt = fb.options()
        acc = []
        for _ in range(3):
            t = time.perf_counter()
            P.Policy(ix, knobs, opt)
            acc.append((time.perf_counter() - t) * 1000.0)
        timings["[rebuild] candidate policy"] = _q(acc)
        pol = P.Policy(ix, knobs, opt)
        acc = []
        for _ in range(3):
            t = time.perf_counter()
            P.solve_cost(ix, pol, knobs, None, opt)
            acc.append((time.perf_counter() - t) * 1000.0)
        timings["[rebuild] cost solve"] = _q(acc)

        row = {"slug": slug, "n_recipes": int(ix.n_recipes),
               "n_items": int(ix.n_items),
               "n_templates": int(len(ctx.tpl_rows)),
               "index_build_s": round(build_s, 2),
               "context_build_s": ctx.seconds,
               "mean_group_size": round(float(np.mean(sizes)), 2),
               "max_group_size": int(np.max(sizes)),
               "timings": timings}
        out["packs"].append(row)
        print(f"\n  {slug}  ({ix.n_recipes:,} recipes, {len(ctx.tpl_rows):,} "
              f"templates; index {build_s:.1f}s + context {ctx.seconds:.1f}s)")
        print(f"  {'call':<30}{'n':>6}{'median ms':>11}{'p95 ms':>9}"
              f"{'max ms':>9}")
        for name, q in timings.items():
            print(f"  {name:<30}{q['n']:>6}{q['median']:>11.3f}"
                  f"{q['p95']:>9.3f}{q['max']:>9.3f}")
    return out


# --------------------------------------------------------------- persistence


def stage_persist(args, cache_dir: Path, out_dir: Path, knobs: P.Knobs) -> dict:
    """Save, reload, merge and version-check one pack's store."""
    out = {"packs": []}
    manifest = read_json(Path(out_dir) / "models" / "manifest.json")
    for slug in P.pack_slugs(cache_dir, args.packs):
        ix, ctx = load_ctx(cache_dir, out_dir, slug, knobs)
        store = FeedbackStore(out_dir, slug)
        fb = PlannerFeedback(ctx, store=store)
        fb.state.data_hash = manifest.get("data_hash", "")
        fb.state.index_stamp = str(ix.meta.get("stamp", ""))
        fb.have(str(ix.items[0]), 12.0)
        fb.ban(recipe_idx=int(np.flatnonzero(ix.keep)[0]))
        fb.prefer(base_id=str(ix.items[1]))
        multi = np.flatnonzero(np.diff(ix.prod_ptr) >= 2)
        g = ctx.group(int(multi[0]))
        fb.choose(int(g[0]), over=int(g[1]))
        before = json.dumps(fb.state.to_json(), sort_keys=True)
        path = fb.save()
        again, status = store.load(fb.state.data_hash, fb.state.index_stamp)
        after = json.dumps(again.to_json(), sort_keys=True)
        v1, v2 = build_view(ctx, fb.state), build_view(ctx, again)
        merged = FeedbackState.from_json(json.loads(before)).merge(again)
        _, bad = store.load("a-different-data-hash", "")
        row = {"slug": slug, "path": str(path),
               "bytes": store.size_bytes(), "status": status,
               "round_trip_identical": before == after,
               "view_signature_identical": v1.signature() == v2.signature(),
               "merge_doubles_evidence": all(
                   abs(merged.evidence[k][0] - 2 * again.evidence[k][0])
                   < 1e-9 for k in again.evidence),
               "stale_hash_rejected": bad == "data_hash",
               "n_updates": int(fb.state.n_updates)}
        out["packs"].append(row)
        print(f"  {slug:<26}{row['bytes']:>8} B  round-trip "
              f"{row['round_trip_identical']}  signature "
              f"{row['view_signature_identical']}  merge "
              f"{row['merge_doubles_evidence']}  stale-hash-rejected "
              f"{row['stale_hash_rejected']}")
    return out
def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--cache-dir", default=str(HERE / "cache"))
    ap.add_argument("--out-dir", default=str(HERE / "out"))
    ap.add_argument("--packs", default="fom")
    ap.add_argument("--stage", default="family",
                    choices=("family", "audit", "curve", "inquiry",
                             "latency", "persist", "all"))
    ap.add_argument("--targets", type=int, default=150)
    ap.add_argument("--families", type=int, default=3)
    ap.add_argument("--seed", type=int, default=20260920)
    ap.add_argument("--seeds", type=int, default=5)
    ap.add_argument("--curve-targets", type=int, default=60)
    ap.add_argument("--packs-top", type=int, default=5)
    ap.add_argument("--calls", type=int, default=200)
    ap.add_argument("--inquiry-limit", type=int, default=INQUIRY_LIMIT)
    ap.add_argument("--report", default=None)
    args = ap.parse_args(argv)
    cache_dir, out_dir = Path(args.cache_dir), Path(args.out_dir)
    knobs = P.Knobs()
    stages = (("family", "audit", "curve", "inquiry", "latency", "persist")
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
