#!/usr/bin/env python
"""Phase 3 — the offline A/B between the prototype and the Java baseline.

    ./.venv/bin/python eval_planner.py --stage targets --packs fom
    ./.venv/bin/python eval_planner.py --stage ab      --packs fom --targets 150
    ./.venv/bin/python eval_planner.py --stage audit   --packs all
    ./.venv/bin/python eval_planner.py --stage all     --packs all

Baseline = the exported chains of the Java ``StandardPlanner`` under
``chain-exports/<pack>/batch-NNN/`` (empty stock, builtin rules v2 in effect).
Both sides are measured by the *same* code over the *same* targets: the
baseline's ``### CHAIN`` text is parsed back into actions and run through the
identical metric function, so nothing depends on the exporter's own header.

Same hard rules as every other module here: no mod / item / material / colour
/ system vocabulary in executable code, no join on recipe ids inside the
planner, deterministic sampling.  The one place a recipe id is used at all is
the *evaluation-side* mapping from a baseline chain line back to a
``recipe_idx`` — the chain text carries no other key — and its resolution rate
is reported rather than assumed.
"""
from __future__ import annotations

import argparse
import json
import math
import re
import sys
import time
from pathlib import Path

import numpy as np
import pyarrow.parquet as pq

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import planner as P  # noqa: E402
from common import (match_name, normalise_name, read_json,  # noqa: E402
                    strip_alt_suffix, write_json)
from labels import WL_JUNK  # noqa: E402

CHAIN_ROOT = HERE.parents[2] / "chain-exports"
RULEHITS = HERE.parents[2] / "experiments" / "rulehits-v3" / "features"

RE_CHAIN = re.compile(r"^### CHAIN (\S+) \| category=(\S+)")
RE_TARGET = re.compile(r"^target: (\S+) x(\d+)")
RE_ACTION = re.compile(r"^\s*\[(A\d+)\] EXECUTE (.*?) x(\d+) @ (\S+)")
RE_IO = re.compile(r"^\s*(in |out): (.*)$")
RE_FROM = re.compile(r"^\s*<= from (.*)$")
RE_ENTRY = re.compile(r"^(.*) x([0-9]+(?:\.[0-9]+)?)$")
RE_UNRES = re.compile(r"^\[UNRESOLVED\] (.*) x([0-9.]+) \(candidates=(\d+), "
                      r"reason=([^,)]+)")


# ============================================================ baseline parsing


class Chain:
    __slots__ = ("chain_id", "category", "target", "amount", "header",
                 "actions", "raw", "unresolved", "catalysts")

    def __init__(self):
        self.actions, self.raw, self.unresolved, self.catalysts = [], {}, [], []
        self.header = {}


def _entries(text: str) -> list:
    out = []
    for part in text.split(", "):
        part = part.strip()
        if not part:
            continue
        m = RE_ENTRY.match(part)
        if not m:
            continue
        base, _ = strip_alt_suffix(m.group(1).strip())
        out.append((base, float(m.group(2))))
    return out


def parse_chain_file(path: Path, wanted: set = None) -> dict:
    """Parse the ``### CHAIN`` blocks of one file into ``{chain_id: Chain}``."""
    chains, cur, keep = {}, None, True
    with open(path, "r", encoding="utf-8") as fh:
        for line in fh:
            m = RE_CHAIN.match(line)
            if m:
                if cur is not None and keep:
                    chains[cur.chain_id] = cur
                cur = Chain()
                cur.chain_id, cur.category = m.group(1), m.group(2)
                keep = wanted is None or cur.chain_id in wanted
                continue
            if cur is None or not keep:
                continue
            m = RE_TARGET.match(line)
            if m:
                cur.target, cur.amount = m.group(1), int(m.group(2))
                continue
            if line.startswith(("status:", "search:", "rule:", "machines:",
                                "builtin-rule-tags")):
                key, _, rest = line.partition(":")
                cur.header[key.strip()] = rest.strip()
                continue
            m = RE_ACTION.match(line)
            if m:
                rid = m.group(2).split(" [rule-tag")[0].strip()
                cur.actions.append({"id": m.group(1), "recipe_id": rid,
                                    "times": int(m.group(3)),
                                    "machine": m.group(4), "inputs": [],
                                    "outputs": [], "from": []})
                continue
            m = RE_IO.match(line)
            if m and cur.actions:
                key = "inputs" if m.group(1).strip() == "in" else "outputs"
                cur.actions[-1][key] = _entries(m.group(2))
                continue
            m = RE_FROM.match(line)
            if m and cur.actions:
                cur.actions[-1]["from"] = [s for s in m.group(1).split(",") if s]
                continue
            if line.startswith("[RAW] "):
                for base, amt in _entries(line[6:].strip()):
                    cur.raw[base] = cur.raw.get(base, 0.0) + amt
                continue
            m = RE_UNRES.match(line)
            if m:
                base, _ = strip_alt_suffix(m.group(1))
                cur.unresolved.append({"base_id": base,
                                       "amount": float(m.group(2)),
                                       "candidates": int(m.group(3)),
                                       "reason": m.group(4)})
                continue
            if line.startswith("[CATALYST] "):
                cur.catalysts.append(line.strip())
    if cur is not None and keep:
        chains[cur.chain_id] = cur
    return chains


def pack_dir_for(slug: str, pack: str) -> Path:
    """Locate a pack's chain-export directory without writing a pack name down."""
    if not CHAIN_ROOT.exists():
        return None
    names = [p.name for p in sorted(CHAIN_ROOT.iterdir()) if p.is_dir()]
    hit = match_name(pack, names) or match_name(slug, names)
    return CHAIN_ROOT / hit if hit else None


def read_index(pdir: Path) -> list:
    """One row per exported chain: batch, id, category, target, header stats."""
    rows = []
    for batch in sorted(pdir.glob("batch-*")):
        idx = batch / "index.tsv"
        if not idx.exists():
            continue
        head = None
        for line in idx.read_text(encoding="utf-8").splitlines():
            cells = line.split("\t")
            if head is None:
                head = cells
                continue
            row = dict(zip(head, cells))
            row["batch"] = batch.name
            row["path"] = batch
            rows.append(row)
    return rows


def chain_path(batch: Path, category: str) -> Path:
    hits = sorted(batch.glob(f"chains-{category}-*.txt"))
    return hits[0] if hits else None


# ================================================ evaluation-side id resolution


def recipe_index_map(ix: P.PackIndex) -> tuple:
    """``recipe_id -> recipe_idx`` for reading baseline chains back.

    Ids are not a key upstream (28.8 % are empty, and one pack reuses ids
    across 138 k rows), so this map is *only* used to look up the model and
    Phase 0b columns of a baseline action, never inside the planner.  Rows
    whose id is ambiguous are resolved by the action's machine and its primary
    output; what is still ambiguous is counted and reported.
    """
    table: dict = {}
    for r in range(ix.n_recipes):
        rid = str(ix.recipe_id[r])
        if rid:
            table.setdefault(rid, []).append(r)
    return table, sum(1 for v in table.values() if len(v) > 1)


def _signature(ix: P.PackIndex, r: int, sigs: dict):
    """``(machine ids, output ids)`` of one recipe, built once."""
    sig = sigs.get(r)
    if sig is None:
        machines = frozenset(
            str(ix.machine_ids[int(ix.mach_item[k])])
            for k in range(ix.mach_ptr[r], ix.mach_ptr[r + 1])
            if int(ix.mach_item[k]) >= 0)
        outs = frozenset(str(ix.items[int(ix.out_item[k])])
                         for k in range(ix.out_ptr[r], ix.out_ptr[r + 1]))
        sig = sigs[r] = (machines, outs)
    return sig


def resolve_action(ix: P.PackIndex, table: dict, act: dict,
                   cache: dict = None) -> int:
    """Baseline chain line -> ``recipe_idx``.

    Cached per ``(id, machine, output set)`` and backed by per-recipe
    signatures built once: rebuilding those sets inside the loop cost 4.8 ms
    per action on the pack with 47 k reused ids, i.e. 7 s for one chain.
    """
    hits = table.get(act["recipe_id"])
    if not hits:
        return -1
    if len(hits) == 1:
        return hits[0]
    if cache is None:
        cache = {}
    outs = frozenset(b for b, _ in act["outputs"])
    key = (act["recipe_id"], act["machine"], outs)
    got = cache.get(key)
    if got is not None:
        return got
    sigs = cache.setdefault("_sig", {})
    best, best_key = -1, None
    for r in hits:
        machines, mine = _signature(ix, r, sigs)
        rank = (-int(act["machine"] in machines), -len(outs & mine), r)
        if best_key is None or rank < best_key:
            best, best_key = r, rank
    cache[key] = best
    return best


def weak_labels(pack: str) -> dict:
    """``recipe_idx -> wl`` from the rule-replay features (``i`` is the idx)."""
    if not RULEHITS.exists():
        return {}
    names = [p.name[:-6] for p in sorted(RULEHITS.glob("*.jsonl"))]
    hit = match_name(pack, names)
    if hit is None:
        return {}
    out = {}
    with open(RULEHITS / f"{hit}.jsonl", "r", encoding="utf-8") as fh:
        for line in fh:
            try:
                row = json.loads(line)
            except ValueError:
                continue
            out[int(row["i"])] = row.get("wl")
    return out


# ==================================================== the shared metric function


def chain_metrics(ix: P.PackIndex, pol: P.Policy, knobs: P.Knobs, units: dict,
                  actions: list, raw: dict, unresolved: list, target: str,
                  wl: dict, wall_ms: float = 0.0) -> dict:
    """The one metric function both sides go through.

    ``actions`` is a list of ``{"id", "recipe_idx", "times", "machine",
    "inputs": [(base, amount)], "outputs": [...], "from": [ids]}``.
    """
    ids = {a["id"] for a in actions}
    depth_of: dict = {}
    for a in actions:                      # the file is already in exec order
        d = 0
        for src in a["from"]:
            if src in depth_of:
                d = max(d, depth_of[src] + 1)
        depth_of[a["id"]] = d
    root_pool = ""
    ti = ix.item_of(target)
    if ti >= 0:
        root_pool = str(ix.item_pool[ti])

    pool_net, pools_touched, seen_edges, round_trips = {}, set(), set(), 0
    reversals = junk_model = junk_wl = display = resolved = 0
    for a in actions:
        r = a["recipe_idx"]
        for base, _ in a["inputs"] + a["outputs"]:
            i = ix.item_of(base)
            if i >= 0 and str(ix.item_pool[i]):
                pools_touched.add(str(ix.item_pool[i]))
        if r >= 0:
            resolved += 1
            if float(ix.junk_p[r]) > knobs.tau_junk:
                junk_model += 1
            if pol.role[r] == P.ROLE_DISPLAY:
                display += 1
            if bool(ix.reverse_tier[r]):
                reversals += 1
            if wl.get(r) == WL_JUNK:
                junk_wl += 1
        is_conv = r >= 0 and pol.role[r] == P.ROLE_CONVERSION
        if not is_conv:
            continue
        for sign, rows in ((+1.0, a["outputs"]), (-1.0, a["inputs"])):
            for base, amt in rows:
                i = ix.item_of(base)
                if i < 0 or i not in units:
                    continue
                pid = str(ix.item_pool[i])
                pool_net[pid] = pool_net.get(pid, 0.0) + sign * units[i] * amt
        ins = {b for b, _ in a["inputs"]}
        outs = {b for b, _ in a["outputs"]}
        edges = {(s, d2) for s in ins for d2 in outs if s != d2}
        round_trips += sum(1 for s, d2 in edges if (d2, s) in seen_edges)
        seen_edges |= edges
    amp = max([v for pid, v in pool_net.items() if pid != root_pool] + [0.0])
    # A raw entry that is *not* a Phase 0b leaf is a demand the planner gave
    # up on and wrote down as "gather it": the honest counterpart of the
    # baseline's UNRESOLVED, and it must be counted on both sides.
    raw_nonleaf = 0
    for base in raw:
        i = ix.item_of(base)
        if i >= 0 and not bool(ix.item_leaf[i]):
            raw_nonleaf += 1
    n = len(actions)
    return {
        "actions": n,
        "distinct_recipes": len({a["recipe_idx"] if a["recipe_idx"] >= 0
                                 else a["id"] for a in actions}),
        "crafts": int(sum(int(a["times"]) for a in actions)),
        "raw_kinds": len(raw),
        "machines": len({a["machine"] for a in actions if a["machine"] != "-"}),
        "depth": (max(depth_of.values()) + 1) if depth_of else 0,
        "junk_in_path_wl": round(junk_wl / resolved, 6) if resolved else 0.0,
        "junk_in_path_model": round(junk_model / resolved, 6) if resolved else 0.0,
        "display_in_path": round(display / resolved, 6) if resolved else 0.0,
        "round_trips": round_trips,
        "pool_amplification": round(amp, 6),
        "tier_reversals": reversals,
        "incomplete": int(bool(unresolved)),
        "unresolved": len(unresolved),
        "raw_nonleaf": raw_nonleaf,
        "unsatisfied": int(bool(unresolved)) + int(raw_nonleaf > 0),
        "resolved_actions": resolved,
        "wall_ms": round(wall_ms, 3),
        "ids": len(ids),
    }


def plan_actions(ix: P.PackIndex, pl: P.Plan) -> list:
    """The prototype's plan in the shape ``chain_metrics`` consumes."""
    out = []
    for a in pl.actions:
        out.append({
            "id": a["id"], "recipe_idx": a["recipe_idx"],
            "times": a["times"],
            "machine": a["machines"][0] if a["machines"] else "-",
            "inputs": [(r["base_id"], r["amount"]) for r in a["inputs"]
                       if r["base_id"]],
            "outputs": [(r["base_id"], r["amount"]) for r in a["outputs"]],
            "from": [s for r in a["inputs"] for s in r["from"]
                     if s.startswith("A")]})
    return out


def baseline_actions(ix: P.PackIndex, table: dict, ch: Chain,
                     cache: dict = None) -> tuple:
    acts, unresolved_ids = [], 0
    for a in ch.actions:
        r = resolve_action(ix, table, a, cache)
        if r < 0:
            unresolved_ids += 1
        acts.append({"id": a["id"], "recipe_idx": r, "times": a["times"],
                     "machine": a["machine"], "inputs": a["inputs"],
                     "outputs": a["outputs"], "from": a["from"]})
    return acts, unresolved_ids


# ======================================================== target selection


def gold_pairs(out_dir: Path):
    path = out_dir / "labels" / "pairwise_gold.parquet"
    if not path.exists():
        return []
    t = pq.read_table(path).to_pylist()
    return t


def select_targets(ix: P.PackIndex, rows: list, n: int, seed: int,
                   gold_outputs: set, pinned: set) -> list:
    """Seeded sample of the union of ``targets.tsv``, plus the must-haves."""
    by_target: dict = {}
    for row in rows:
        by_target.setdefault(row["target"], row)
    keys = sorted(by_target)
    must = [k for k in keys if k in gold_outputs or k in pinned]
    rest = [k for k in keys if k not in set(must)]
    rng = np.random.default_rng(seed)
    take = max(0, n - len(must))
    if take and rest:
        pick = rng.choice(len(rest), size=min(take, len(rest)), replace=False)
        chosen = [rest[i] for i in sorted(pick.tolist())]
    else:
        chosen = []
    return [by_target[k] for k in sorted(set(must) | set(chosen))]


def gold_chain_targets(pdir: Path, rows: list, gold_outputs: set) -> set:
    """Targets whose baseline chain executes a recipe producing a gold output.

    Scans the chain text of every batch once; cheap because it only reads the
    ``out:`` lines.
    """
    if not gold_outputs:
        return set()
    by_id = {(r["batch"], r["id"]): r["target"] for r in rows}
    hits = set()
    for batch in sorted(pdir.glob("batch-*")):
        for path in sorted(batch.glob("chains-*.txt")):
            cur = None
            with open(path, "r", encoding="utf-8") as fh:
                for line in fh:
                    m = RE_CHAIN.match(line)
                    if m:
                        cur = (batch.name, m.group(1))
                        continue
                    if cur is None or not line.lstrip().startswith("out: "):
                        continue
                    for base, _ in _entries(line.split("out: ", 1)[1].strip()):
                        if base in gold_outputs and cur in by_id:
                            hits.add(by_id[cur])
    return hits


# ===================================================================== stages


def stage_targets(args, cache_dir: Path, out_dir: Path) -> dict:
    packs = read_json(cache_dir / "packs.json")
    gold = gold_pairs(out_dir)
    report = {}
    for slug in P.pack_slugs(cache_dir, args.packs):
        pdir = pack_dir_for(slug, packs[slug])
        if pdir is None:
            print(f"  {slug:<40} no chain export")
            continue
        rows = read_index(pdir)
        outs = {g["output_base_id"] for g in gold
                if normalise_name(g["pack"]) == normalise_name(slug)}
        cats = {}
        for r in rows:
            cats[r["category"]] = cats.get(r["category"], 0) + 1
        report[slug] = {"chains": len(rows), "targets": len({r["target"]
                                                             for r in rows}),
                        "categories": cats, "gold_outputs": len(outs),
                        "dir": pdir.name}
        print(f"  {slug:<40} {len(rows):>5} chains  "
              f"{len({r['target'] for r in rows}):>5} targets  "
              f"gold-outputs {len(outs):>2}  " +
              " ".join(f"{k}={v}" for k, v in sorted(cats.items())))
    return report


def prescan_ladders(pdir: Path) -> dict:
    """Per chain, how many (A -> B then B -> A) item edges it contains.

    Role-free and id-free, so it is a plain line scan: the point is only to
    *pin* each pack's worst storage-ladder chain into the sample, so that the
    regression case is always measured and never sampled away.
    """
    scores: dict = {}
    for batch in sorted(pdir.glob("batch-*")):
        for path in sorted(batch.glob("chains-*.txt")):
            key, ins, seen, score = None, None, set(), 0
            with open(path, "r", encoding="utf-8") as fh:
                for line in fh:
                    m = RE_CHAIN.match(line)
                    if m:
                        if key:
                            scores[key] = score
                        key = (batch.name, m.group(1))
                        ins, seen, score = None, set(), 0
                        continue
                    if key is None:
                        continue
                    s = line.lstrip()
                    if s.startswith("in : "):
                        ins = {b for b, _ in _entries(s[5:].strip())}
                    elif s.startswith("out: ") and ins is not None:
                        for a in sorted(ins):
                            for b in sorted({x for x, _ in
                                             _entries(s[5:].strip())}):
                                if (b, a) in seen:
                                    score += 1
                                seen.add((a, b))
                        ins = None
            if key:
                scores[key] = score
    return scores


def run_pack_ab(slug: str, pack: str, cache_dir: Path, out_dir: Path,
                knobs: P.Knobs, n_targets: int, seed: int,
                pins: list) -> dict:
    """One pack's paired A/B.  Returns per-target rows plus timings."""
    t0 = time.perf_counter()
    ix = P.load_pack(cache_dir, out_dir, slug, knobs)
    pol = P.Policy(ix, knobs)
    sv = P.solve_cost(ix, pol, knobs)
    units = P.pool_units(ix)
    build_s = time.perf_counter() - t0
    table, ambiguous = recipe_index_map(ix)
    wl = weak_labels(pack)

    pdir = pack_dir_for(slug, pack)
    rows = read_index(pdir)
    gold = [g for g in gold_pairs(out_dir)
            if normalise_name(g["pack"]) == normalise_name(slug)]
    gold_outputs = {g["output_base_id"] for g in gold}
    pinned = gold_chain_targets(pdir, rows, gold_outputs)
    ladders = prescan_ladders(pdir)
    by_key = {(r["batch"], r["id"]): r for r in rows}
    if ladders:
        worst = max(sorted(ladders), key=lambda k: (ladders[k], k))
        if ladders[worst] > 0 and worst in by_key:
            pinned.add(by_key[worst]["target"])
    for spec in pins:
        bits = spec.split(":")
        if len(bits) == 3 and normalise_name(bits[0]) == normalise_name(slug):
            key = (bits[1], bits[2])
            if key in by_key:
                pinned.add(by_key[key]["target"])

    sel = select_targets(ix, rows, n_targets, seed, gold_outputs, pinned)
    files: dict = {}
    for row in sel:
        files.setdefault((row["path"], row["category"]), set()).add(row["id"])
    parsed: dict = {}
    for (batch, category), ids in sorted(files.items(), key=lambda kv: str(kv[0])):
        path = chain_path(batch, category)
        if path is None:
            continue
        for cid, ch in parse_chain_file(path, ids).items():
            parsed[(batch.name, cid)] = ch

    out_rows, pro_ms, id_cache = [], [], {}
    for row in sel:
        ch = parsed.get((row["batch"], row["id"]))
        if ch is None or not hasattr(ch, "target"):
            continue
        bacts, unresolved_ids = baseline_actions(ix, table, ch, id_cache)
        base_m = chain_metrics(ix, pol, knobs, units, bacts, ch.raw,
                               ch.unresolved, ch.target, wl, 0.0)
        base_m["unresolved_ids"] = unresolved_ids
        t1 = time.perf_counter()
        pl = P.plan_target(ix, pol, knobs, ch.target, ch.amount, None, sv, units)
        ms = (time.perf_counter() - t1) * 1000.0
        pro_ms.append(ms)
        pacts = plan_actions(ix, pl)
        pro_m = chain_metrics(ix, pol, knobs, units, pacts, pl.raw,
                              pl.unresolved, ch.target, wl, ms)
        out_rows.append({"slug": slug, "target": ch.target,
                         "batch": row["batch"], "chain_id": row["id"],
                         "category": row["category"],
                         "ladder": ladders.get((row["batch"], row["id"]), 0),
                         "gold": ch.target in gold_outputs or
                                 ch.target in pinned,
                         "baseline": base_m, "prototype": pro_m})
    return {"slug": slug, "pack": pack, "rows": out_rows,
            "build_seconds": round(build_s, 3),
            "ambiguous_ids": ambiguous,
            "n_chains": len(rows), "n_selected": len(sel),
            "per_target_ms": {
                "median": round(float(np.median(pro_ms)), 3) if pro_ms else 0.0,
                "p95": round(float(np.percentile(pro_ms, 95)), 3) if pro_ms else 0.0,
                "max": round(max(pro_ms), 3) if pro_ms else 0.0},
            "policy": pol.stats, "solve": sv.stats}


METRIC_KEYS = ("actions", "distinct_recipes", "crafts", "raw_kinds",
               "machines", "depth", "junk_in_path_wl", "junk_in_path_model",
               "display_in_path", "round_trips", "pool_amplification",
               "tier_reversals", "incomplete", "raw_nonleaf")


def summarise(rows: list) -> dict:
    """Per-metric medians, means and win/loss counts over paired targets."""
    out = {}
    for key in METRIC_KEYS:
        b = np.asarray([r["baseline"][key] for r in rows], dtype=np.float64)
        p = np.asarray([r["prototype"][key] for r in rows], dtype=np.float64)
        if not b.size:
            continue
        out[key] = {
            "baseline_median": round(float(np.median(b)), 4),
            "prototype_median": round(float(np.median(p)), 4),
            "baseline_mean": round(float(b.mean()), 4),
            "prototype_mean": round(float(p.mean()), 4),
            "prototype_lower": int((p < b).sum()),
            "prototype_higher": int((p > b).sum()),
            "tied": int((p == b).sum()),
            "n": int(b.size)}
    return out


def print_ab_table(title: str, summary: dict, n: int) -> None:
    print(f"\n  {title}  (n = {n} paired targets)")
    print(f"  {'metric':<22}{'base med':>10}{'proto med':>11}"
          f"{'base mean':>11}{'proto mean':>12}{'proto<':>8}{'proto>':>8}{'tie':>6}")
    for key, v in summary.items():
        print(f"  {key:<22}{v['baseline_median']:>10.3f}"
              f"{v['prototype_median']:>11.3f}{v['baseline_mean']:>11.3f}"
              f"{v['prototype_mean']:>12.3f}{v['prototype_lower']:>8}"
              f"{v['prototype_higher']:>8}{v['tied']:>6}")


def stage_ab(args, cache_dir: Path, out_dir: Path, knobs: P.Knobs) -> dict:
    packs = read_json(cache_dir / "packs.json")
    pins = [s for s in (args.pin or "").split(",") if s]
    all_rows, per_pack = [], {}
    for slug in P.pack_slugs(cache_dir, args.packs):
        if pack_dir_for(slug, packs[slug]) is None:
            continue
        res = run_pack_ab(slug, packs[slug], cache_dir, out_dir, knobs,
                          args.targets, args.seed, pins)
        rows = res.pop("rows")
        per_pack[slug] = {**res, "summary": summarise(rows)}
        all_rows.extend(rows)
        s = per_pack[slug]["summary"]
        print(f"  {slug:<38} n={len(rows):>4} build={res['build_seconds']:>6.2f}s "
              f"med={res['per_target_ms']['median']:>7.2f}ms "
              f"p95={res['per_target_ms']['p95']:>7.2f}ms  "
              f"incomplete {s.get('incomplete', {}).get('baseline_mean', 0):.2f}"
              f"->{s.get('incomplete', {}).get('prototype_mean', 0):.2f}  "
              f"junk_wl {s.get('junk_in_path_wl', {}).get('baseline_mean', 0):.3f}"
              f"->{s.get('junk_in_path_wl', {}).get('prototype_mean', 0):.3f}")
    corpus = summarise(all_rows)
    print_ab_table("corpus", corpus, len(all_rows))
    return {"per_pack": per_pack, "corpus": corpus,
            "rows": all_rows, "n": len(all_rows)}


# ======================================================= stage: the audit cases


GAMMAS = (1.0, 1.5, 2.0, float("inf"))


def audit_case(ix: P.PackIndex, pol: P.Policy, knobs: P.Knobs, units: dict,
               output: str, chosen: int, preferred: list, sv=None) -> dict:
    """One gold *case* — one (output, chosen producer) with every preferred
    alternative the audit named for it.  Evaluation only; never tuned on.

    Verdict: ``preferred`` when the plan executes any of the alternatives,
    ``chosen`` when it executes the producer the audit complained about,
    ``neither`` when it executes a third producer, ``unplanned`` when the
    output is not produced at all.
    """
    item = ix.item_of(output)
    if sv is None:
        sv = P.solve_cost(ix, pol, knobs)
    pl = P.plan_target(ix, pol, knobs, output, 1, None, sv, units)
    act = None
    for a in pl.actions:
        if any(r["base_id"] == output for r in a["outputs"]):
            act = a
    used = act["recipe_idx"] if act else -1
    verdict = ("preferred" if used in preferred else
               "chosen" if used == chosen else
               "neither" if used >= 0 else "unplanned")

    def num(r):
        if item < 0 or r < 0 or r >= ix.n_recipes:
            return {}
        c, _ = P.producer_cost(ix, sv, knobs, pol, r, item, set())
        short = {x["recipe_idx"] for x in act["why"]["shortlist"]} if act else set()
        return {"recipe_idx": int(r),
                "unit_cost": round(c, 4) if c < math.inf else None,
                "producer_score": round(float(ix.score[r]), 4),
                "junk_p": round(float(ix.junk_p[r]), 4),
                "role": str(pol.role[r]),
                "excluded": bool(pol.excluded[r]),
                "in_shortlist": int(r) in short}
    return {"output_base_id": output, "verdict": verdict,
            "executed": num(used), "chosen": num(chosen),
            "preferred": [num(r) for r in preferred],
            "decided_by": act["why"]["decided_by"] if act else "-",
            "n_shortlist": len(act["why"]["shortlist"]) if act else 0,
            "plan_status": pl.status, "actions": len(pl.actions)}


def audit_from_stock(ix: P.PackIndex, pol: P.Policy, knobs: P.Knobs,
                     units: dict, output: str, chosen: int,
                     preferred: list) -> dict:
    """Re-solve with the *chosen* producer's inputs in stock.

    The conversion-from-stock behaviour the plan asks for: holding what a
    route consumes must make that route win, with no re-pricing anywhere.
    """
    stock = {}
    for k in range(ix.in_ptr[chosen], ix.in_ptr[chosen + 1]):
        node = int(ix.in_node[k])
        pick = node if node < ix.n_items else int(ix.or_rep[node - ix.n_items])
        if pick >= 0:
            stock[str(ix.items[pick])] = float(ix.in_amt[k]) * 64.0
    sv = P.solve_cost(ix, pol, knobs, stock)
    pl = P.plan_target(ix, pol, knobs, output, 1, stock, sv, units)
    act = None
    for a in pl.actions:
        if any(r["base_id"] == output for r in a["outputs"]):
            act = a
    used = act["recipe_idx"] if act else -1
    return {"output_base_id": output, "stock": sorted(stock),
            "verdict": ("chosen" if used == chosen else
                        "preferred" if used in preferred else
                        "neither" if used >= 0 else "served-from-stock"),
            "actions": len(pl.actions), "status": pl.status}


def gold_cases(gold: list) -> dict:
    """Group the gold readings into cases: one (finding, output, chosen)."""
    cases: dict = {}
    for g in gold:
        key = (g["finding_id"], g["output_base_id"], int(g["chosen_recipe_idx"]))
        c = cases.setdefault(key, {"preferred": [], "weight": 0.0,
                                   "readings": 0})
        c["preferred"].append(int(g["preferred_recipe_idx"]))
        c["weight"] += float(g["weight"])
        c["readings"] += 1
    return cases


def stage_audit(args, cache_dir: Path, out_dir: Path,
                base_knobs: P.Knobs) -> dict:
    gold = gold_pairs(out_dir)
    slugs = P.pack_slugs(cache_dir, args.packs)
    by_slug: dict = {}
    for g in gold:
        hit = match_name(g["pack"], slugs)
        if hit:
            by_slug.setdefault(hit, []).append(g)
    configs = [(f"gamma={gv}", dict(gamma=gv, use_ranker=True)) for gv in GAMMAS]
    configs += [(f"gamma={gv} no-ranker", dict(gamma=gv, use_ranker=False))
                for gv in GAMMAS]
    out = {"cases": [], "from_stock": [], "summary": {}}
    for slug in sorted(by_slug):
        cases = gold_cases(by_slug[slug])
        for label, over in configs:
            knobs = P.Knobs(**{**base_knobs.as_dict(), **over})
            ix = P.load_pack(cache_dir, out_dir, slug, knobs)
            pol, units = P.Policy(ix, knobs), P.pool_units(ix)
            sv = P.solve_cost(ix, pol, knobs)
            for (finding, output, chosen), c in sorted(cases.items()):
                row = audit_case(ix, pol, knobs, units, output, chosen,
                                 c["preferred"], sv)
                row.update({"config": label, "slug": slug,
                            "finding_id": finding, "weight": c["weight"],
                            "readings": c["readings"]})
                out["cases"].append(row)
        knobs = P.Knobs(**base_knobs.as_dict())
        ix = P.load_pack(cache_dir, out_dir, slug, knobs)
        pol, units = P.Policy(ix, knobs), P.pool_units(ix)
        for (finding, output, chosen), c in sorted(cases.items()):
            row = audit_from_stock(ix, pol, knobs, units, output, chosen,
                                   c["preferred"])
            row.update({"slug": slug, "finding_id": finding})
            out["from_stock"].append(row)

    for label, _ in configs:
        rows = [r for r in out["cases"] if r["config"] == label]
        if not rows:
            continue
        w = sum(r["weight"] for r in rows) or 1.0
        out["summary"][label] = {
            "cases": len(rows),
            "preferred": sum(1 for r in rows if r["verdict"] == "preferred"),
            "chosen": sum(1 for r in rows if r["verdict"] == "chosen"),
            "neither": sum(1 for r in rows if r["verdict"] == "neither"),
            "unplanned": sum(1 for r in rows if r["verdict"] == "unplanned"),
            "won": round(sum(1 for r in rows
                             if r["verdict"] == "preferred") / len(rows), 4),
            "not_lost": round(sum(1 for r in rows
                                  if r["verdict"] != "chosen") / len(rows), 4),
            "weighted_not_lost": round(sum(r["weight"] for r in rows
                                           if r["verdict"] != "chosen") / w, 4)}
    print(f"\n  {'config':<26}{'cases':>6}{'preferred':>11}{'chosen':>8}"
          f"{'neither':>9}{'unpl':>6}{'won':>8}{'not-lost':>10}{'w.n-l':>8}")
    for label, v in out["summary"].items():
        print(f"  {label:<26}{v['cases']:>6}{v['preferred']:>11}{v['chosen']:>8}"
              f"{v['neither']:>9}{v['unplanned']:>6}{v['won']:>8.3f}"
              f"{v['not_lost']:>10.3f}{v['weighted_not_lost']:>8.3f}")
    print("\n  per case (gamma=2.0, ranker on)")
    print(f"  {'finding':<26}{'output':<44}{'verdict':<11}{'by':<8}"
          f"{'exec cost':>10}{'chosen cost':>12}{'best pref':>11}")
    for r in out["cases"]:
        if r["config"] != "gamma=2.0":
            continue
        pref = [p.get("unit_cost") for p in r["preferred"]
                if p.get("unit_cost") is not None]
        print(f"  {r['finding_id']:<26}{r['output_base_id']:<44}"
              f"{r['verdict']:<11}{r['decided_by']:<8}"
              f"{_n(r['executed'].get('unit_cost')):>10}"
              f"{_n(r['chosen'].get('unit_cost')):>12}"
              f"{_n(min(pref) if pref else None):>11}")
    print("\n  from-stock re-solve (the chosen producer's inputs held)")
    for r in out["from_stock"]:
        print(f"    {r['finding_id']:<26}{r['output_base_id']:<44}"
              f"{r['verdict']:<18}actions={r['actions']:>3}")
    return out


def _n(v) -> str:
    return "-" if v is None else f"{v:.3f}"


# ===================================================== stage: knob ablations


ABLATION_KEYS = ("actions", "distinct_recipes", "crafts", "raw_kinds",
                 "machines", "depth", "junk_in_path_wl", "junk_in_path_model",
                 "display_in_path", "round_trips", "tier_reversals",
                 "incomplete", "raw_nonleaf")


def stage_ablation(args, cache_dir: Path, out_dir: Path,
                   base: P.Knobs) -> dict:
    """Every knob the brief names, measured on the **non-gold** metrics first.

    Each row is the same paired sample as the A/B, re-planned under one knob
    setting.  The gold cases are reported separately (``--stage audit``) and
    nothing here is tuned on them.
    """
    packs = read_json(cache_dir / "packs.json")
    grid = [(f"gamma={g}", dict(gamma=g)) for g in GAMMAS]
    grid += [("no-ranker", dict(use_ranker=False)),
             ("no-conversion-gate", dict(conversion_needs_stock=False)),
             ("tau_junk=0.5", dict(tau_junk=0.5)),
             ("tau_junk=0.99", dict(tau_junk=0.99)),
             ("tier_penalty=0", dict(tier_penalty=0.0)),
             ("machine_cost=0", dict(machine_cost=0.0))]
    out = {}
    slugs = P.pack_slugs(cache_dir, args.packs)
    for label, over in grid:
        knobs = P.Knobs(**{**base.as_dict(), **over})
        rows = []
        for slug in slugs:
            if pack_dir_for(slug, packs[slug]) is None:
                continue
            res = run_pack_ab(slug, packs[slug], cache_dir, out_dir, knobs,
                              args.targets, args.seed, [])
            rows.extend(res["rows"])
        summary = summarise(rows)
        out[label] = {"n": len(rows),
                      "metrics": {k: summary[k]["prototype_mean"]
                                  for k in ABLATION_KEYS if k in summary}}
        print(f"  {label:<22} n={len(rows):>4}  " + "  ".join(
            f"{k.split('_')[0]}={out[label]['metrics'].get(k, 0):.3f}"
            for k in ("actions", "crafts", "depth", "junk_in_path_wl",
                      "round_trips", "tier_reversals", "incomplete")))
    print(f"\n  {'config':<22}" + "".join(f"{k[:13]:>15}"
                                           for k in ABLATION_KEYS))
    for label, v in out.items():
        print(f"  {label:<22}" + "".join(
            f"{v['metrics'].get(k, 0):>15.3f}" for k in ABLATION_KEYS))
    return out


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--cache-dir", default=str(HERE / "cache"))
    ap.add_argument("--out-dir", default=str(HERE / "out"))
    ap.add_argument("--packs", default="fom")
    ap.add_argument("--stage", default="targets",
                    choices=("targets", "ab", "audit", "ablation", "all"))
    ap.add_argument("--targets", type=int, default=150)
    ap.add_argument("--seed", type=int, default=20260919)
    ap.add_argument("--gamma", type=float)
    ap.add_argument("--tau-junk", type=float)
    ap.add_argument("--tau-display", type=float)
    ap.add_argument("--no-ranker", action="store_true")
    ap.add_argument("--no-conversion-gate", action="store_true")
    ap.add_argument("--step-cost", type=float)
    ap.add_argument("--machine-cost", type=float)
    ap.add_argument("--tier-penalty", type=float)
    ap.add_argument("--budget-base", type=int)
    ap.add_argument("--arm-junk"), ap.add_argument("--arm-role")
    ap.add_argument("--arm-rank")
    ap.add_argument("--pin", default=None,
                    help="pack:batch:chain ids to force into the sample")
    ap.add_argument("--report", default=None)
    args = ap.parse_args(argv)
    cache_dir, out_dir = Path(args.cache_dir), Path(args.out_dir)
    knobs = P.Knobs(gamma=args.gamma, tau_junk=args.tau_junk,
                    tau_display=args.tau_display, step_cost=args.step_cost,
                    machine_cost=args.machine_cost,
                    tier_penalty=args.tier_penalty,
                    budget_base=args.budget_base,
                    use_ranker=False if args.no_ranker else None,
                    conversion_needs_stock=(False if args.no_conversion_gate
                                            else None),
                    arm_junk=args.arm_junk, arm_role=args.arm_role,
                    arm_rank=args.arm_rank)
    t0 = time.perf_counter()
    result = {"knobs": knobs.as_dict(), "seed": args.seed,
              "targets_per_pack": args.targets}
    if args.stage in ("targets",):
        print("== targets")
        result["targets"] = stage_targets(args, cache_dir, out_dir)
    if args.stage in ("ab", "all"):
        print("== A/B")
        result["ab"] = stage_ab(args, cache_dir, out_dir, knobs)
    if args.stage in ("audit", "all"):
        print("== audit cases")
        result["audit"] = stage_audit(args, cache_dir, out_dir, knobs)
    if args.stage in ("ablation", "all"):
        print("== knob ablations (non-gold metrics)")
        result["ablation"] = stage_ablation(args, cache_dir, out_dir, knobs)
    print(f"\n  {time.perf_counter() - t0:.1f}s")
    if args.report:
        write_json(Path(args.report), result)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
