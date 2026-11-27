#!/usr/bin/env python
"""Phase 0a step 2 — induce an ontology from the cached exports.

Everything here is induced from the data.  There is no mod / material /
colour / form word list anywhere; the only inputs are id strings, their token
positions, and the structural fields of the export schema.

Pipeline per pack
-----------------
1. **Token classes by analogy.**  Ids are split into ``ns`` + path tokens.
   Two ids whose token sequences have the same length and differ in exactly one
   position are one *interchange evidence* between the two differing tokens
   (implemented as a "hole bucket": ``(prefix, suffix) -> {token}``).  A second
   pass promotes frequent *bigrams* (``light_blue``, ``dark_oak``) to single
   tokens: a bigram is a unit when a two-token run substitutes for a single
   token in the same context at least ``--bigram-min`` times.  Evidence is then
   recomputed over the canonical (post-promotion) tokenisation, edges below
   ``--min-evidence`` are dropped, a ``--k-core`` prune removes tokens that only
   touch one other token, connected components are split by deterministic
   Louvain modularity, and each resulting community is a token class.
   The class *role* is guessed from position statistics only: a token
   occurrence at distance ``e`` from the end of the path votes ``form`` for
   ``e == 0``, ``material`` for ``e == 1`` and ``modifier`` for ``e >= 2``.
2. **Component key-set classes.**  Component key co-occurrence over observed
   ``component_keys`` sets; connected components of keys give a variant class
   per key-set, folding e.g. every potion-contents variant into one class.
3. **Content identity quadruple** per distinct base id:
   ``(content_type, material_class, form_class, modifier_classes)`` + variant
   classes, with ``None`` where the slot is absent.
4. **Template folding.**  A recipe's signature is
   ``(system, abstracted machines, multiset of abstracted inputs, multiset of
   abstracted outputs, residual recipe-id tokens, kind/match-strategy
   pattern)``; a content abstracts to ``(kind, content_type, material, form,
   modifiers, variant class, amount, chance bucket)``.

Cross-pack the per-pack evidence is unioned into a prior class table, and an
onboarding simulation refits the prior on 21 packs to count what the 22nd adds.
"""
from __future__ import annotations

import argparse
import sys
import time
from collections import Counter, defaultdict
from itertools import combinations
from pathlib import Path

import pyarrow as pa
import pyarrow.compute as pc
import pyarrow.parquet as pq

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import (  # noqa: E402
    canon_json,
    path_tokens,
    peak_rss_mb,
    read_json,
    slugify,
    split_id,
    stable_hash,
    write_json,
)

# ------------------------------------------------------------- default knobs
MIN_EVIDENCE = 2      # an interchange must be witnessed in >= this many contexts
K_CORE = 1            # a token must interchange with >= this many other tokens
BIGRAM_MIN_PARTNERS = 2   # distinct single tokens a bigram must interchange with
BIGRAM_MIN_CONTEXTS = 2   # distinct contexts that interchange must be seen in
MAX_BUCKET = 400      # contexts with more alternatives than this are uninformative
RESOLUTION = 1.0      # Louvain resolution
MIN_COSINE = 0.7      # context-similarity floor for an interchange edge
MIN_CLASS = 2         # a class needs at least two members

IDENTITY_SCHEMA = pa.schema([
    ("base_id", pa.string()),
    ("content_type", pa.string()),
    ("ns", pa.string()),
    ("tokens", pa.list_(pa.string())),
    ("material_class", pa.string()),
    ("form_class", pa.string()),
    ("modifier_classes", pa.list_(pa.string())),
    ("variant_classes", pa.list_(pa.string())),
    ("n_variant_keys", pa.int32()),
])

TEMPLATE_SCHEMA = pa.schema([
    ("template_id", pa.string()),
    ("signature", pa.string()),
    ("system", pa.string()),
    ("size", pa.int32()),
    ("n_inputs", pa.int32()),
    ("n_outputs", pa.int32()),
    ("examples", pa.list_(pa.string())),
])

RECIPE_TEMPLATE_SCHEMA = pa.schema([
    ("recipe_idx", pa.int32()),
    ("template_id", pa.string()),
])


# =========================================================== vocabulary input


def collect_vocab(pack_dir: Path) -> dict:
    """Distinct content ids of a pack + the machine ids, with their families."""
    contents = pq.read_table(pack_dir / "contents.parquet",
                             columns=["kind", "content_type", "base_id", "members"])
    kind = contents.column("kind")
    ctype = contents.column("content_type")
    base = contents.column("base_id")

    stackish = pc.is_in(kind, value_set=pa.array(["stack", "any", "typedAny"]))
    known_family = pc.invert(pc.is_in(ctype, value_set=pa.array(["complex", "mixed", ""])))
    mask = pc.and_(stackish, known_family)
    pairs = pa.table({"content_type": pc.filter(ctype, mask),
                      "base_id": pc.filter(base, mask)}).group_by(
        ["content_type", "base_id"]).aggregate([])

    members = contents.column("members")
    flat = pc.list_flatten(members)
    parent = pc.list_parent_indices(members)
    member_pairs = pa.table({"content_type": pc.take(ctype, parent),
                             "base_id": flat}).group_by(
        ["content_type", "base_id"]).aggregate([])

    ids: dict[str, str] = {}
    for table in (pairs, member_pairs):
        for ct, bid in zip(table.column("content_type").to_pylist(),
                           table.column("base_id").to_pylist()):
            if not bid or ct in ("complex", "mixed", ""):
                continue
            ids.setdefault(bid, ct)

    recipes = pq.read_table(pack_dir / "recipes.parquet", columns=["machines"])
    for mid in pc.unique(pc.list_flatten(recipes.column("machines"))).to_pylist():
        if mid:
            ids.setdefault(mid, "item")

    tags = pc.unique(pc.filter(base, pc.equal(kind, "tag"))).to_pylist()
    return {"ids": ids, "tag_ids": sorted(t for t in tags if t)}


def sequences_of(ids: dict[str, str]) -> dict[tuple, int]:
    """Distinct path-token sequences over the id universe (namespace dropped).

    Evidence is counted once per distinct *sequence pair*, so a pack that ships
    the same path under five namespaces does not get five times the weight.
    """
    seqs: Counter = Counter()
    for raw in ids:
        toks = path_tokens(split_id(raw)[1])
        if toks:
            seqs[toks] += 1
    return dict(seqs)


# ================================================================== evidence


def make_buckets(seq_masks, include_bigrams: bool) -> dict:
    """``(prefix, suffix) -> {token: pack mask}`` hole buckets.

    A single token at position ``i`` and a two-token run at position ``i`` land
    in the same bucket, which is exactly what makes a bigram comparable to a
    single token.  Buckets with an empty context carry no information and are
    never created.
    """
    buckets: dict = {}
    for seq, mask in seq_masks:
        n = len(seq)
        if n >= 2:
            for i in range(n):
                key = (seq[:i], seq[i + 1:])
                slot = buckets.get(key)
                if slot is None:
                    slot = buckets[key] = {}
                tok = seq[i]
                slot[tok] = slot.get(tok, 0) | mask
        if include_bigrams and n >= 3:
            for i in range(n - 1):
                key = (seq[:i], seq[i + 2:])
                slot = buckets.get(key)
                if slot is None:
                    slot = buckets[key] = {}
                tok = seq[i] + "_" + seq[i + 1]
                slot[tok] = slot.get(tok, 0) | mask
    return buckets


def evidence_from_buckets(buckets, mask_filter: int = -1, max_bucket: int = MAX_BUCKET):
    """Pairwise interchange evidence over the tokens of each context.

    ``ctx`` counts, per token, how many qualifying contexts it appears in; it is
    the normaliser that turns raw co-occurrence into a cosine similarity so that
    frequent tokens do not drag every class into one blob.
    """
    evidence: Counter = Counter()
    ctx: Counter = Counter()
    overflow = 0
    for members in buckets.values():
        toks = sorted(t for t, m in members.items() if m & mask_filter)
        if len(toks) < 2:
            continue
        if len(toks) > max_bucket:
            overflow += 1
            continue
        for tok in toks:
            ctx[tok] += 1
        for pair in combinations(toks, 2):
            evidence[pair] += 1
    return evidence, overflow, ctx


def split_bigram(token: str) -> tuple[str, str]:
    """A merged token is ``head_tail``; raw tokens never contain ``_``."""
    head, _, tail = token.partition("_")
    return head, tail


def promote_bigrams(buckets, min_partners: int, min_contexts: int, max_bucket: int) -> dict:
    """Promote a two-token run to one token only when it is *non-compositional*.

    A frequency threshold ("this bigram acted as a unit N times") promotes
    compositional compounds — ``<colour>_<wood>``, ``<wood>_<form>`` — because
    those genuinely do align with single tokens: ``<colour>_<wood>_seat``
    aligns with ``<other colour>_seat``.  Promoting them separates the colour
    class from the wood class instead of composing them and produces thousands
    of one-off tokens that then interchange with each other into one blob.

    The criterion here is the analogy rule applied to the bigram itself.  For a
    merged token ``a_b`` occurring in context ``(prefix, suffix)``:

    * a **unit** reading needs single-token partners *other than* ``a`` and
      ``b``: some id ``prefix + x + suffix`` exists with ``x`` a single token.
      A partner equal to ``a`` means ``a`` alone already fills that slot; a
      partner equal to ``b`` means ``a`` was merely inserted in front of it.
      Both are compositional readings and are excluded from the unit evidence.
    * the competing **droppable/insertable modifier** reading is measured
      globally on the head token: ``solo(a)`` = how many contexts ``a`` itself
      interchanges in as a single token.  A head that stands on its own all
      over the corpus (a colour, a wood, a material) is a modifier that was
      composed with the next token; a head that never stands alone (a bound
      prefix such as the first half of a two-token colour) is not.

    ``a_b`` is promoted when it has at least ``min_partners`` distinct unit
    partners in at least ``min_contexts`` distinct contexts, and its unit
    evidence mass is at least as large as ``solo(a)``.
    """
    unit_contexts: dict = defaultdict(set)
    unit_partners: dict = defaultdict(set)
    solo_contexts: Counter = Counter()
    for key, members in buckets.items():
        if len(members) < 2 or len(members) > max_bucket:
            continue
        singles = {t for t in members if "_" not in t}
        if len(singles) >= 2:
            for tok in singles:
                solo_contexts[tok] += 1
        if not singles:
            continue
        for tok in members:
            if "_" not in tok:
                continue
            head, tail = split_bigram(tok)
            partners = singles - {head, tail}
            if partners:
                unit_contexts[tok].add(key)
                unit_partners[tok] |= partners
    promoted = {}
    for tok in sorted(unit_contexts):
        contexts = unit_contexts[tok]
        if len(unit_partners[tok]) < min_partners or len(contexts) < min_contexts:
            continue
        if len(contexts) < solo_contexts.get(split_bigram(tok)[0], 0):
            continue
        promoted[tok] = len(contexts)
    return promoted


def canonicalise(seq: tuple, promoted: dict) -> tuple:
    """Greedy non-overlapping merge of promoted bigrams (highest unit count first)."""
    n = len(seq)
    if n < 2 or not promoted:
        return seq
    cands = []
    for i in range(n - 1):
        merged = seq[i] + "_" + seq[i + 1]
        weight = promoted.get(merged)
        if weight:
            cands.append((-weight, i, merged))
    if not cands:
        return seq
    cands.sort()
    taken = [False] * n
    merges = {}
    for _, i, merged in cands:
        if taken[i] or taken[i + 1]:
            continue
        taken[i] = taken[i + 1] = True
        merges[i] = merged
    out = []
    i = 0
    while i < n:
        if i in merges:
            out.append(merges[i])
            i += 2
        else:
            out.append(seq[i])
            i += 1
    return tuple(out)


def induce_evidence(seq_masks, min_partners: int, min_contexts: int, max_bucket: int):
    """Two-pass evidence: promote bigrams, then re-measure on canonical tokens."""
    raw_buckets = make_buckets(seq_masks, include_bigrams=True)
    promoted = promote_bigrams(raw_buckets, min_partners, min_contexts, max_bucket)
    canon = [(canonicalise(seq, promoted), mask) for seq, mask in seq_masks]
    buckets = make_buckets(canon, include_bigrams=False)
    evidence, overflow, ctx = evidence_from_buckets(buckets, max_bucket=max_bucket)
    return evidence, promoted, canon, overflow, buckets, ctx


# ================================================================ clustering


def _components(adj: dict) -> list:
    seen = set()
    out = []
    for node in sorted(adj):
        if node in seen:
            continue
        stack = [node]
        seen.add(node)
        comp = []
        while stack:
            cur = stack.pop()
            comp.append(cur)
            for nb in adj[cur]:
                if nb not in seen:
                    seen.add(nb)
                    stack.append(nb)
        out.append(sorted(comp))
    return out


def _louvain_level(nodes, adj, resolution):
    """One deterministic Louvain local-moving phase.  Returns node -> community."""
    m2 = sum(sum(nbrs.values()) for nbrs in adj.values())
    if m2 <= 0:
        return {n: i for i, n in enumerate(nodes)}
    degree = {n: sum(adj[n].values()) for n in nodes}
    comm = {n: i for i, n in enumerate(nodes)}
    tot = {i: degree[n] for n, i in comm.items()}
    for _ in range(20):
        moved = False
        for node in nodes:
            ci = comm[node]
            kn = degree[node]
            tot[ci] -= kn
            wc: dict = {}
            for nb, w in adj[node].items():
                cnb = comm[nb]
                wc[cnb] = wc.get(cnb, 0.0) + w
            best, best_gain = ci, wc.get(ci, 0.0) - resolution * tot[ci] * kn / m2
            for cand in sorted(wc):
                gain = wc[cand] - resolution * tot.get(cand, 0.0) * kn / m2
                if gain > best_gain + 1e-12 or (abs(gain - best_gain) <= 1e-12 and cand < best):
                    best, best_gain = cand, gain
            tot[best] = tot.get(best, 0.0) + kn
            if best != ci:
                comm[node] = best
                moved = True
        if not moved:
            break
    return comm


def louvain(adj: dict, resolution: float, levels: int = 3) -> dict:
    """Deterministic multi-level Louvain; returns node -> community label."""
    nodes = sorted(adj)
    if not nodes:
        return {}
    mapping = {n: n for n in nodes}
    cur_adj = adj
    for _ in range(levels):
        cur_nodes = sorted(cur_adj)
        comm = _louvain_level(cur_nodes, cur_adj, resolution)
        groups = defaultdict(list)
        for node, cid in comm.items():
            groups[cid].append(node)
        if len(groups) == len(cur_nodes):
            break
        label = {}
        for cid, members in groups.items():
            label[cid] = min(members)
        mapping = {orig: label[comm[cur]] for orig, cur in mapping.items()}
        new_adj: dict = defaultdict(dict)
        for node, nbrs in cur_adj.items():
            src = label[comm[node]]
            for nb, w in nbrs.items():
                dst = label[comm[nb]]
                if src == dst:
                    continue
                new_adj[src][dst] = new_adj[src].get(dst, 0.0) + w
        new_adj = {k: dict(v) for k, v in new_adj.items()}
        for node in {label[c] for c in groups}:
            new_adj.setdefault(node, {})
        if len(new_adj) == len(cur_adj):
            break
        cur_adj = new_adj
    return mapping


def cluster_tokens(evidence: Counter, ctx: Counter, min_evidence: int, k_core: int,
                   resolution: float, min_cosine: float = 0.0):
    """Evidence counter -> ``{token: class_id}`` plus per-class statistics.

    Edge weight is the cosine similarity of the two tokens' context sets,
    ``ev(a,b) / sqrt(ctx(a) * ctx(b))``.  Raw co-occurrence alone makes every
    frequent token adjacent to every other one and modularity then returns one
    giant blob; the cosine makes "these two tokens appear in *the same kinds of*
    contexts" the thing being clustered, which is the cohesion criterion.
    """
    adj: dict = defaultdict(dict)
    for (a, b), w in evidence.items():
        if w < min_evidence:
            continue
        denom = (ctx.get(a, 0) * ctx.get(b, 0)) ** 0.5
        sim = (w / denom) if denom else 0.0
        if sim < min_cosine:
            continue
        adj[a][b] = sim
        adj[b][a] = sim
    # k-core prune: a token seen interchanging with a single partner is noise.
    changed = True
    while changed and k_core > 1:
        changed = False
        for node in list(adj):
            if len(adj[node]) < k_core:
                for nb in list(adj[node]):
                    adj[nb].pop(node, None)
                adj.pop(node, None)
                changed = True
    adj = {k: v for k, v in adj.items() if v}

    token_class: dict = {}
    class_members = defaultdict(list)
    for comp in _components(adj):
        comp_set = set(comp)
        sub = {n: {nb: w for nb, w in adj[n].items() if nb in comp_set} for n in comp}
        labels = louvain(sub, resolution)
        for node in comp:
            class_members[labels.get(node, node)].append(node)
    classes = {}
    for label, members in class_members.items():
        members = sorted(members)
        if len(members) < MIN_CLASS:
            continue
        cid = "cls:" + members[0]
        member_set = set(members)
        mass = 0
        cohesion = 0.0
        for node in members:
            for nb, sim in adj[node].items():
                if nb in member_set:
                    cohesion += sim
                    pair = (node, nb) if node < nb else (nb, node)
                    mass += evidence[pair]
        classes[cid] = {"members": members, "size": len(members),
                        "evidence_mass": mass // 2,
                        "cohesion": round(cohesion / 2, 3)}
        for node in members:
            token_class[node] = cid
    return token_class, classes


def role_of(class_members, position_stats) -> tuple[str, dict]:
    """Role guess from position statistics only (no word list)."""
    agg = Counter()
    for token in class_members:
        stats = position_stats.get(token)
        if not stats:
            continue
        agg["form"] += stats.get(0, 0)
        agg["material"] += stats.get(1, 0)
        agg["modifier"] += stats.get(2, 0)
    total = sum(agg.values())
    if total == 0:
        return "unknown", {}
    role = max(sorted(agg.items()), key=lambda kv: kv[1])[0]
    return role, {k: v for k, v in sorted(agg.items())}


def position_statistics(canon_seqs) -> dict:
    """token -> {0: form-slot count, 1: material-slot count, 2: modifier-slot count}."""
    stats: dict = defaultdict(Counter)
    for seq, _mask in canon_seqs:
        n = len(seq)
        for i, tok in enumerate(seq):
            e = n - 1 - i
            stats[tok][min(e, 2)] += 1
    return {k: dict(v) for k, v in stats.items()}


# ================================================== component key-set classes


def cluster_component_keysets(pack_dir: Path):
    """Variant classes from component-key co-occurrence."""
    table = pq.read_table(pack_dir / "contents.parquet",
                          columns=["component_keys", "variant_key", "base_id"])
    keysets: Counter = Counter()
    variant_keys_per_set = defaultdict(set)
    for keys, vkey in zip(table.column("component_keys").to_pylist(),
                          table.column("variant_key").to_pylist()):
        if not keys:
            continue
        ks = tuple(keys)
        keysets[ks] += 1
        if vkey:
            variant_keys_per_set[ks].add(vkey)

    adj: dict = defaultdict(set)
    for ks in keysets:
        for key in ks:
            adj[key]  # touch
        for a, b in combinations(sorted(set(ks)), 2):
            adj[a].add(b)
            adj[b].add(a)
    key_group: dict = {}
    seen = set()
    for key in sorted(adj):
        if key in seen:
            continue
        stack = [key]
        seen.add(key)
        group = []
        while stack:
            cur = stack.pop()
            group.append(cur)
            for nb in adj[cur]:
                if nb not in seen:
                    seen.add(nb)
                    stack.append(nb)
        gid = "kg:" + min(group)
        for member in group:
            key_group[member] = gid

    keyset_class: dict = {}
    class_keysets = defaultdict(list)
    for ks in sorted(keysets):
        gids = sorted({key_group[k] for k in ks})
        cid = "var:" + stable_hash(gids, 10)
        keyset_class[ks] = cid
        class_keysets[cid].append(ks)
    n_variant_keys = sum(len(v) for v in variant_keys_per_set.values())
    return {
        "keyset_class": keyset_class,
        "key_group": key_group,
        "n_keysets": len(keysets),
        "n_classes": len(class_keysets),
        "n_variant_keys": n_variant_keys,
        "keyset_counts": keysets,
        "class_keysets": {k: [list(x) for x in v] for k, v in sorted(class_keysets.items())},
    }


# ================================================================ abstraction


class Abstractor:
    """Maps ids to induced classes and recipes to template signatures."""

    def __init__(self, token_class: dict, promoted: dict, keyset_class: dict):
        self.token_class = token_class
        self.promoted = promoted
        self.keyset_class = keyset_class
        self._cache: dict = {}

    def tokens(self, raw_id: str) -> tuple:
        cached = self._cache.get(raw_id)
        if cached is None:
            cached = canonicalise(path_tokens(split_id(raw_id)[1]), self.promoted)
            self._cache[raw_id] = cached
        return cached

    def slot_classes(self, raw_id: str):
        """(material, form, modifiers) using the induced classes where available."""
        toks = self.tokens(raw_id)
        if not toks:
            return None, None, ()
        form = self._label(toks[-1])
        material = self._label(toks[-2]) if len(toks) >= 2 else None
        mods = tuple(self._label(t) for t in toks[:-2])
        return material, form, mods

    def _label(self, token: str) -> str:
        cid = self.token_class.get(token)
        return cid if cid else "t:" + token

    def abstract_id(self, raw_id: str) -> str:
        """Class-level abstraction of a bare id (used for machines)."""
        material, form, mods = self.slot_classes(raw_id)
        return "|".join([material or "", form or "", ",".join(mods)])

    def residual_tokens(self, raw_id: str) -> tuple:
        return tuple(t for t in self.tokens(raw_id) if t not in self.token_class)

    def variant_class(self, component_keys) -> str:
        if not component_keys:
            return ""
        return self.keyset_class.get(tuple(component_keys), "var:?")


def chance_bucket(chance) -> str:
    if chance is None:
        return "na"
    if chance >= 1.0:
        return "1"
    if chance <= 0.0:
        return "0"
    return f"{round(float(chance) * 20) / 20:.2f}"


def abstract_content(row, abstractor: Abstractor) -> str:
    kind, ctype, base_id, component_keys, amount, chance, n_members = row
    if kind == "complex":
        head = base_id.rsplit(".", 1)[-1] if base_id else "?"
        return f"{kind}|{ctype}|impl:{head}|||{amount}|{chance_bucket(chance)}"
    material, form, mods = abstractor.slot_classes(base_id)
    variant = abstractor.variant_class(component_keys)
    return "|".join([
        kind,
        ctype,
        material or "",
        form or "",
        ",".join(mods),
        variant,
        str(amount if amount is not None else "*"),
        chance_bucket(chance),
    ])


# =============================================================== the pipeline


def process_pack(cache_dir: Path, slug: str, out_root: Path, args) -> dict:
    pack_dir = cache_dir / slug
    t0 = time.time()
    vocab = collect_vocab(pack_dir)
    seqs = sequences_of(vocab["ids"])
    seq_masks = [(seq, 1) for seq in sorted(seqs)]
    evidence, promoted, canon, overflow, _, ctx = induce_evidence(
        seq_masks, args.bigram_min_partners, args.bigram_min_contexts, args.max_bucket)
    token_class, classes = cluster_tokens(evidence, ctx, args.min_evidence,
                                          args.k_core, args.resolution,
                                          args.min_cosine)
    pos_stats = position_statistics(canon)
    roles = Counter()
    for cid, info in classes.items():
        role, votes = role_of(info["members"], pos_stats)
        info["role"] = role
        info["role_votes"] = votes
        roles[role] += 1
    t_tokens = time.time() - t0

    keysets = cluster_component_keysets(pack_dir)
    abstractor = Abstractor(token_class, promoted, keysets["keyset_class"])

    out_dir = out_root / slug
    out_dir.mkdir(parents=True, exist_ok=True)

    identity_rows = write_identity(pack_dir, out_dir, vocab, abstractor)
    template_stats = fold_templates(pack_dir, out_dir, abstractor)

    seconds = time.time() - t0
    meta = {
        "pack_slug": slug,
        "ids": len(vocab["ids"]),
        "tag_ids": len(vocab["tag_ids"]),
        "sequences": len(seqs),
        "evidence_pairs": len(evidence),
        "evidence_mass": int(sum(evidence.values())),
        "bucket_overflow": overflow,
        "promoted_bigrams": len(promoted),
        "promoted_examples": sorted(promoted, key=lambda t: (-promoted[t], t))[:15],
        "token_classes": len(classes),
        "classified_tokens": len(token_class),
        "distinct_tokens": len(pos_stats),
        "roles": dict(sorted(roles.items())),
        "keyset_classes": {
            "n_keysets": keysets["n_keysets"],
            "n_classes": keysets["n_classes"],
            "n_variant_keys": keysets["n_variant_keys"],
            "keyset_fold_ratio": round(keysets["n_keysets"] / keysets["n_classes"], 3)
            if keysets["n_classes"] else 0.0,
            "variant_fold_ratio": round(keysets["n_variant_keys"] / keysets["n_classes"], 3)
            if keysets["n_classes"] else 0.0,
        },
        "identity_rows": identity_rows,
        "seconds_tokens": round(t_tokens, 2),
        "seconds": round(seconds, 2),
    }
    meta.update(template_stats)
    write_json(out_dir / "ontology-meta.json", meta)
    write_json(out_dir / "token-classes.json", {
        "params": {"min_evidence": args.min_evidence, "k_core": args.k_core,
                   "bigram_min_partners": args.bigram_min_partners,
                   "bigram_min_contexts": args.bigram_min_contexts,
                   "resolution": args.resolution,
                   "max_bucket": args.max_bucket},
        "classes": {cid: {"role": info["role"], "size": info["size"],
                          "evidence_mass": info["evidence_mass"],
                          "role_votes": info["role_votes"],
                          "members": info["members"]}
                    for cid, info in sorted(classes.items())},
    })
    meta["_seqs"] = seqs
    return meta


def write_identity(pack_dir: Path, out_dir: Path, vocab, abstractor: Abstractor) -> int:
    variants = pq.read_table(pack_dir / "contents.parquet",
                             columns=["base_id", "component_keys", "variant_key"])
    per_id_classes = defaultdict(set)
    per_id_vkeys = defaultdict(set)
    for bid, keys, vkey in zip(variants.column("base_id").to_pylist(),
                               variants.column("component_keys").to_pylist(),
                               variants.column("variant_key").to_pylist()):
        if keys:
            per_id_classes[bid].add(abstractor.variant_class(keys))
        if vkey:
            per_id_vkeys[bid].add(vkey)

    rows = []
    for raw_id in sorted(vocab["ids"]):
        ns, _ = split_id(raw_id)
        material, form, mods = abstractor.slot_classes(raw_id)
        rows.append({
            "base_id": raw_id,
            "content_type": vocab["ids"][raw_id],
            "ns": ns,
            "tokens": list(abstractor.tokens(raw_id)),
            "material_class": material,
            "form_class": form,
            "modifier_classes": list(mods),
            "variant_classes": sorted(per_id_classes.get(raw_id, ())),
            "n_variant_keys": len(per_id_vkeys.get(raw_id, ())),
        })
    columns = [pa.array([r[f.name] for r in rows], type=f.type) for f in IDENTITY_SCHEMA]
    pq.write_table(pa.Table.from_arrays(columns, schema=IDENTITY_SCHEMA),
                   out_dir / "identity.parquet", compression="snappy")
    return len(rows)


def fold_templates(pack_dir: Path, out_dir: Path, abstractor: Abstractor) -> dict:
    recipes = pq.read_table(pack_dir / "recipes.parquet",
                            columns=["recipe_idx", "system", "recipe_id", "machines",
                                     "dropped"])
    idx_col = recipes.column("recipe_idx").to_pylist()
    systems = recipes.column("system").to_pylist()
    rids = recipes.column("recipe_id").to_pylist()
    machines = recipes.column("machines").to_pylist()
    dropped = recipes.column("dropped").to_pylist()

    per_recipe_in = defaultdict(list)
    per_recipe_out = defaultdict(list)
    per_recipe_pat = defaultdict(list)
    pf = pq.ParquetFile(pack_dir / "contents.parquet")
    for batch in pf.iter_batches(batch_size=200000,
                                 columns=["recipe_idx", "side", "kind", "content_type",
                                          "base_id", "component_keys", "amount",
                                          "chance", "n_members", "match_strategy"]):
        cols = {name: batch.column(name).to_pylist() for name in batch.schema.names}
        for i in range(batch.num_rows):
            row = (cols["kind"][i], cols["content_type"][i], cols["base_id"][i],
                   cols["component_keys"][i], cols["amount"][i], cols["chance"][i],
                   cols["n_members"][i])
            key = cols["recipe_idx"][i]
            abstract = abstract_content(row, abstractor)
            if cols["side"][i] == "in":
                per_recipe_in[key].append(abstract)
            else:
                per_recipe_out[key].append(abstract)
            ms = cols["match_strategy"][i] or "identity"  # Java default when absent
            per_recipe_pat[key].append(f"{cols['side'][i]}:{cols['kind'][i]}:{ms}")

    template_members = defaultdict(list)
    template_sig = {}
    assign_idx = []
    assign_tid = []
    for pos, idx in enumerate(idx_col):
        if dropped[pos]:
            continue
        sig = {
            "sys": systems[pos],
            "mach": sorted(abstractor.abstract_id(m) for m in machines[pos]),
            "in": sorted(per_recipe_in.get(idx, ())),
            "out": sorted(per_recipe_out.get(idx, ())),
            "rid": list(abstractor.residual_tokens(rids[pos])),
            "pat": sorted(per_recipe_pat.get(idx, ())),
        }
        text = stable_hash(sig, 40)
        tid = "tpl:" + text[:12]
        if tid not in template_sig:
            template_sig[tid] = sig
        template_members[tid].append((idx, rids[pos]))
        assign_idx.append(idx)
        assign_tid.append(tid)

    rows = []
    for tid, members in sorted(template_members.items()):
        sig = template_sig[tid]
        rows.append({
            "template_id": tid,
            "signature": canon_json(sig),
            "system": sig["sys"],
            "size": len(members),
            "n_inputs": len(sig["in"]),
            "n_outputs": len(sig["out"]),
            "examples": [rid for _, rid in members[:3]],
        })
    columns = [pa.array([r[f.name] for r in rows], type=f.type) for f in TEMPLATE_SCHEMA]
    pq.write_table(pa.Table.from_arrays(columns, schema=TEMPLATE_SCHEMA),
                   out_dir / "templates.parquet", compression="snappy")
    pq.write_table(pa.Table.from_arrays(
        [pa.array(assign_idx, type=pa.int32()), pa.array(assign_tid, type=pa.string())],
        schema=RECIPE_TEMPLATE_SCHEMA), out_dir / "recipe_template.parquet",
        compression="snappy")

    kept = len(assign_idx)
    multi = [r for r in rows if r["size"] > 1]
    covered = sum(r["size"] for r in multi)
    largest = sorted(rows, key=lambda r: (-r["size"], r["template_id"]))[:10]
    return {
        "kept_recipes": kept,
        "templates": len(rows),
        "multi_member_templates": len(multi),
        "multi_member_coverage": round(covered / kept, 4) if kept else 0.0,
        "fold_ratio": round(kept / len(rows), 3) if rows else 0.0,
        "largest_templates": [{"template_id": r["template_id"], "size": r["size"],
                               "system": r["system"], "examples": r["examples"]}
                              for r in largest],
    }


# ============================================================ cross-pack work


def cross_pack(metas: dict, out_root: Path, args, onboarding: str) -> dict:
    t0 = time.time()
    pack_slugs = sorted(metas)
    bit = {slug: 1 << i for i, slug in enumerate(pack_slugs)}
    seq_masks_map: dict = {}
    for slug in pack_slugs:
        for seq in metas[slug]["_seqs"]:
            seq_masks_map[seq] = seq_masks_map.get(seq, 0) | bit[slug]
    seq_masks = sorted(seq_masks_map.items())

    evidence, promoted, canon, overflow, buckets, ctx = induce_evidence(
        seq_masks, args.bigram_min_partners, args.bigram_min_contexts, args.max_bucket)
    token_class, classes = cluster_tokens(evidence, ctx, args.min_evidence,
                                          args.k_core, args.resolution,
                                          args.min_cosine)
    pos_stats = position_statistics(canon)
    roles = Counter()
    for cid, info in classes.items():
        role, votes = role_of(info["members"], pos_stats)
        info["role"] = role
        info["role_votes"] = votes
        roles[role] += 1
    prior_seconds = time.time() - t0

    write_json(out_root / "token-classes.json", {
        "scope": "cross-pack prior (all packs)",
        "params": {"min_evidence": args.min_evidence, "k_core": args.k_core,
                   "bigram_min_partners": args.bigram_min_partners,
                   "bigram_min_contexts": args.bigram_min_contexts,
                   "resolution": args.resolution,
                   "max_bucket": args.max_bucket},
        "packs": pack_slugs,
        "sequences": len(seq_masks),
        "evidence_pairs": len(evidence),
        "token_classes": len(classes),
        "roles": dict(sorted(roles.items())),
        "classes": {cid: {"role": info["role"], "size": info["size"],
                          "evidence_mass": info["evidence_mass"],
                          "role_votes": info["role_votes"],
                          "members": info["members"]}
                    for cid, info in sorted(classes.items())},
    })

    onboard = {}
    if onboarding != "off":
        t1 = time.time()
        pack_tokens = {}
        for slug in pack_slugs:
            toks = set()
            for seq in metas[slug]["_seqs"]:
                toks.update(canonicalise(seq, promoted))
            pack_tokens[slug] = toks
        for slug in pack_slugs:
            others = [s for s in pack_slugs if s != slug]
            prior_tokens = set()
            for other in others:
                prior_tokens |= pack_tokens[other]
            new_tokens = sorted(pack_tokens[slug] - prior_tokens)
            entry = {
                "tokens_in_pack": len(pack_tokens[slug]),
                "new_tokens": len(new_tokens),
                "new_token_examples": new_tokens[:15],
            }
            if onboarding == "full":
                mask = ~bit[slug]
                prior_ev, _, prior_ctx = evidence_from_buckets(
                    buckets, mask_filter=mask, max_bucket=args.max_bucket)
                prior_tc, prior_classes = cluster_tokens(
                    prior_ev, prior_ctx, args.min_evidence, args.k_core,
                    args.resolution, args.min_cosine)
                pack_classes = {cid for tok, cid in token_class.items()
                                if tok in pack_tokens[slug]}
                new_classes = []
                for cid in sorted(pack_classes):
                    members = set(classes[cid]["members"])
                    if not any(tok in prior_tc for tok in members):
                        new_classes.append(cid)
                entry.update({
                    "prior_classes": len(prior_classes),
                    "prior_classified_tokens": len(prior_tc),
                    "classes_touched_by_pack": len(pack_classes),
                    "new_classes": len(new_classes),
                    "new_class_examples": new_classes[:10],
                    "newly_classified_tokens": sum(
                        1 for tok in pack_tokens[slug]
                        if tok in token_class and tok not in prior_tc),
                })
            onboard[slug] = entry
        onboard["_seconds"] = round(time.time() - t1, 2)
    write_json(out_root / "onboarding.json", onboard)

    return {
        "prior_sequences": len(seq_masks),
        "prior_evidence_pairs": len(evidence),
        "prior_token_classes": len(classes),
        "prior_classified_tokens": len(token_class),
        "prior_roles": dict(sorted(roles.items())),
        "prior_promoted_bigrams": len(promoted),
        "prior_bucket_overflow": overflow,
        "prior_seconds": round(prior_seconds, 2),
        "onboarding_mode": onboarding,
    }


def main(argv=None) -> int:
    here = Path(__file__).resolve().parent
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--cache-dir", default=str(here / "cache"))
    ap.add_argument("--out-dir", default=str(here / "out"))
    ap.add_argument("--packs", default="all")
    ap.add_argument("--min-evidence", type=int, default=MIN_EVIDENCE)
    ap.add_argument("--k-core", type=int, default=K_CORE)
    ap.add_argument("--bigram-min-partners", type=int, default=BIGRAM_MIN_PARTNERS)
    ap.add_argument("--bigram-min-contexts", type=int, default=BIGRAM_MIN_CONTEXTS)
    ap.add_argument("--max-bucket", type=int, default=MAX_BUCKET)
    ap.add_argument("--resolution", type=float, default=RESOLUTION)
    ap.add_argument("--min-cosine", type=float, default=MIN_COSINE)
    ap.add_argument("--onboarding", choices=["full", "approx", "off"], default="full")
    ap.add_argument("--no-cross-pack", action="store_true")
    args = ap.parse_args(argv)

    cache_dir = Path(args.cache_dir)
    out_root = Path(args.out_dir)
    out_root.mkdir(parents=True, exist_ok=True)
    packs_map = read_json(cache_dir / "packs.json")
    if args.packs == "all":
        slugs = sorted(packs_map)
    else:
        wanted = [p.strip() for p in args.packs.split(",") if p.strip()]
        slugs = []
        for name in wanted:
            slug = name if name in packs_map else slugify(name)
            if slug not in packs_map:
                print(f"!! unknown pack {name}", file=sys.stderr)
                return 2
            slugs.append(slug)

    t0 = time.time()
    metas = {}
    for slug in slugs:
        meta = process_pack(cache_dir, slug, out_root, args)
        metas[slug] = meta
        print(f"== {packs_map[slug]} [{slug}] ids={meta['ids']} "
              f"classes={meta['token_classes']} (roles {meta['roles']}) "
              f"templates={meta['templates']} multi={meta['multi_member_templates']} "
              f"cover={meta['multi_member_coverage'] * 100:.1f}% "
              f"{meta['seconds']}s")
    summary = {"packs": {}, "params": vars(args)}
    for slug, meta in metas.items():
        summary["packs"][slug] = {k: v for k, v in meta.items() if not k.startswith("_")}
    if not args.no_cross_pack and len(metas) > 1:
        summary["cross_pack"] = cross_pack(metas, out_root, args, args.onboarding)
        print(f"-- prior: classes={summary['cross_pack']['prior_token_classes']} "
              f"tokens={summary['cross_pack']['prior_classified_tokens']} "
              f"{summary['cross_pack']['prior_seconds']}s")
    summary["total_seconds"] = round(time.time() - t0, 2)
    summary["peak_rss_mb"] = round(peak_rss_mb(), 1)
    write_json(out_root / "ontology-summary.json", summary)
    print(f"-- ontology wall={summary['total_seconds']}s peak_rss={summary['peak_rss_mb']}MB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
