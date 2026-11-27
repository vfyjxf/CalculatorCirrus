"""Phase 0c-1 — the label layer.

Collects every *external judgement* about a recipe that exists in this project
and folds it into four tables the model layer can train on:

    out/labels/recipe_labels.parquet    one row per labelled (pack, recipe_idx)
    out/labels/template_labels.parquet  one row per (pack, template_id)
    out/labels/pairwise_gold.parquet    explicit "should have chosen X" pairs
    out/labels/chain_verdicts.parquet   per-chain ok/bad verdicts
    out/labels/pairs.parquet            ProducerRanker training pairs

Four sources, in increasing order of trust:

1. **rule replay** (``experiments/rulehits-v3``) — the Java builtin rules run
   over all 22 packs; gives a weak ``wl`` in {junk, middle, useful} and the
   list of rule ids that fired.  Weak: it is the thing we are trying to
   replace.
2. **gold/silver recipe labels** (``experiments/svm/data/labeled.jsonl``) —
   112,854 human / adjudicated / subagent / SVM judgements over 10 packs.
3. **human chain audits** (``CalculatorCirrus/chain-review``) — rounds 1-5 are
   junk-pattern findings, round-006 is a routing-quality audit that names, for
   a concrete output, the recipe the planner *chose* and the recipe it
   *should* have chosen.  Those are the only truly pairwise labels we have.
4. **LLM labeller** — ``llm_labels.py``, a stub; never called here.

Hard rules inherited from Phase 0a/0b:

* **No hardcoded mod / item / material / colour / system-name vocabulary.**
  The one sanctioned exception is the label-space table below: mapping the
  *existing label category names of the label sources* onto the plan's role
  set is label-space bookkeeping, it is training-only, and it lives in exactly
  one documented block that ``tests/test_phase0c1.py`` exempts by line range.
* **Never join on recipe ids.**  28.8 % of exported recipes have an empty id;
  ids are also not unique.  Every join in this module is on ``recipe_idx``,
  which is the dense per-pack stream index both the Java loader and Phase 0a
  assign.  Recipe ids are used in exactly two places — the chain exports and
  the audit markdown, neither of which carries an index — and there they are
  *resolved* to ``recipe_idx`` through the pack's own id table, with the
  ambiguous and unresolved ones counted and reported.
* **Determinism.**  Same inputs => byte-identical parquet.  Sampling is seeded.
"""
from __future__ import annotations

import argparse
import collections
import json
import re
import time
from pathlib import Path

import numpy as np
import pyarrow as pa
import pyarrow.parquet as pq

from common import (peak_rss_mb, read_json, write_json, pack_group_map,
                    match_name, stable_hash)

# ===================== BEGIN LABEL-SPACE TABLE (sanctioned vocabulary) =======
#
# The ONLY place in this package where an externally-defined name may be
# written down.  Everything here is a *label-space* name: a verdict word, a
# category name coined by the labelling rounds, a source name, or a column
# name derived from one of those.  None of it is a mod, item, material,
# colour or system name, and none of it is ever matched against pack data —
# it only renames judgements that already exist.
#
# Role target: the plan's six-way role set.  A gold category is a statement
# about *what the recipe is for*, which is exactly what the role head has to
# predict, so the mapping is a rename and not a model.
ROLE_PRODUCTION = "production"      # makes something genuinely new
ROLE_CONVERSION = "conversion"      # same material, different form/variant
ROLE_RECYCLING = "recycling"        # takes a product apart again
ROLE_GENERATION = "generation"      # output out of nothing / a single source
ROLE_DISPLAY = "display"            # a UI page, a mirror, a quest/trade view
ROLE_UNKNOWN = "unknown"            # labelled, but not about the role

ROLES = (ROLE_PRODUCTION, ROLE_CONVERSION, ROLE_RECYCLING,
         ROLE_GENERATION, ROLE_DISPLAY, ROLE_UNKNOWN)

#: gold ``category`` (free text from a fixed set) -> role target.
CATEGORY_ROLE = {
    "real-production": ROLE_PRODUCTION,
    "real-processing": ROLE_PRODUCTION,
    "real-crafting": ROLE_PRODUCTION,
    "other-useful": ROLE_UNKNOWN,      # "useful, reason unrecorded"
    "variant-swap": ROLE_CONVERSION,
    "storage-ladder": ROLE_CONVERSION,  # block <-> ingot <-> nugget
    "recycling-reverse": ROLE_RECYCLING,
    "free-generation": ROLE_GENERATION,
    "only-source-generation": ROLE_GENERATION,
    "display-page": ROLE_DISPLAY,
    "mirror-duplicate": ROLE_DISPLAY,
    "pseudo-recipe": ROLE_DISPLAY,
    "chance-gamble": ROLE_UNKNOWN,     # a real recipe with a bad distribution
    "other-junk": ROLE_UNKNOWN,        # "junk, reason unrecorded"
    "audit-flagged": ROLE_UNKNOWN,     # plan-level finding, not a role claim
}

#: verdict words of the gold source and of the rule replay's weak label.
VERDICT_JUNK = "junk"
VERDICT_USEFUL = "useful"
WL_JUNK = "junk"
WL_MIDDLE = "middle"
WL_USEFUL = "useful"

#: ``source`` quality tiers, best first.  ``gold_tier`` is the index; an
#: unknown source falls to the last tier.  Prefix match, so every ``svm-rN``
#: round shares one tier.
SOURCE_TIERS = ("adjudication", "human-nb", "audit", "subagent", "svm-")

#: chain-audit verdict words (round-006 "链级判定" tables).
CHAIN_OK = "ok"
CHAIN_BAD = "bad"

#: pair provenance tiers, best first.
TIER_GOLD = "gold"
TIER_SILVER = "silver"
TIER_BRONZE = "bronze"
TIER_CHAIN = "chain"

#: column names that embed a label-space word.
COL_WL = "wl"
COL_JUNK_TARGET = "junk_target"
COL_N_JUNK = "n_junk"
COL_N_USEFUL = "n_useful"
COL_CHAIN_OK = "chain_ok_count"
COL_CHAIN_BAD = "chain_bad_count"
#
# ======================= END LABEL-SPACE TABLE ==============================


SOURCE_ROOT = Path("/Users/vfyjxf/ProjectDir/Planner-Final")
RULEHITS_DIR = SOURCE_ROOT / "experiments" / "rulehits-v3" / "features"
GOLD_FILE = SOURCE_ROOT / "experiments" / "svm" / "data" / "labeled.jsonl"
REVIEW_DIR = SOURCE_ROOT / "CalculatorCirrus" / "chain-review"
CHAINS_DIR = SOURCE_ROOT / "chain-exports"

STAGES = ("rules", "gold", "audit", "chains", "recipes", "templates", "pairs")

#: export-schema field values (Phase 0a contract), not a vocabulary.
SIDE_IN, SIDE_OUT = "in", "out"


def source_tier(name: str) -> int:
    """Index of ``name`` in :data:`SOURCE_TIERS` (prefix match), else the last."""
    for i, prefix in enumerate(SOURCE_TIERS):
        if name == prefix or name.startswith(prefix):
            return i
    return len(SOURCE_TIERS)


# ------------------------------------------------------------------ plumbing


def log(msg: str) -> None:
    print(msg, flush=True)


def arrow_table(columns: dict, schema: pa.Schema) -> pa.Table:
    """Build a table from ``{name: sequence}`` under an explicit schema."""
    return pa.Table.from_arrays(
        [pa.array(columns[f.name], type=f.type) for f in schema], schema=schema)


def write_table(path: Path, columns: dict, schema: pa.Schema) -> int:
    path.parent.mkdir(parents=True, exist_ok=True)
    table = arrow_table(columns, schema)
    pq.write_table(table, path, compression="snappy")
    return table.num_rows


def resolve_packs(cache_dir: Path, spec: str) -> list[str]:
    packs = read_json(cache_dir / "packs.json")
    if spec in ("all", ""):
        return sorted(packs)
    wanted = [p.strip() for p in spec.split(",") if p.strip()]
    missing = [p for p in wanted if p not in packs]
    if missing:
        raise SystemExit(f"unknown pack slug(s): {missing}; have {sorted(packs)}")
    return sorted(wanted)


# =============================================================== stage: rules


def rulehits_path(export_name: str) -> Path:
    return RULEHITS_DIR / f"{export_name}.jsonl"


def load_rule_replay(export_name: str) -> dict:
    """``{recipe_idx: (wl, [rule ids])}`` from one pack's rule-replay dump.

    The dump's ``i`` is the same dense stream index as ``recipe_idx``; its
    ``id`` is ignored entirely (empty ids are rendered there as a synthetic
    ``jei:/synthetic/<i>`` placeholder, which would poison an id join).
    """
    path = rulehits_path(export_name)
    if not path.exists():
        return {}
    out = {}
    with open(path, "r", encoding="utf-8") as handle:
        for line in handle:
            if not line.strip():
                continue
            row = json.loads(line)
            out[int(row["i"])] = (row.get("wl") or "", row.get("f") or [])
    return out


def stage_rules(ctx: dict) -> None:
    t0 = time.time()
    packs = ctx["packs"]
    replay, stats = {}, {}
    for slug in packs:
        export_name = ctx["pack_names"][slug]
        rows = load_rule_replay(export_name)
        replay[slug] = rows
        n_pack = ctx["n_recipes"][slug]
        wl_counts = collections.Counter(v[0] for v in rows.values())
        fired = sum(1 for v in rows.values() if v[1])
        out_of_range = sum(1 for i in rows if i < 0 or i >= n_pack)
        stats[slug] = {
            "rows": len(rows), "pack_recipes": n_pack,
            "missing": n_pack - len(rows), "out_of_range": out_of_range,
            "wl": dict(sorted(wl_counts.items())),
            "recipes_with_rule_hit": fired,
        }
    ctx["replay"] = replay
    ctx["meta"]["rules"] = {"seconds": round(time.time() - t0, 2),
                            "packs": stats}
    total = sum(s["rows"] for s in stats.values())
    miss = sum(s["missing"] for s in stats.values())
    bad = sum(s["out_of_range"] for s in stats.values())
    wl_all = collections.Counter()
    for s in stats.values():
        wl_all.update(s["wl"])
    log(f"[rules] {total:,} replay rows over {len(packs)} pack(s); "
        f"missing {miss:,}; out-of-range {bad}; wl={dict(sorted(wl_all.items()))}; "
        f"{time.time() - t0:.1f}s")


# ================================================================ stage: gold


GOLD_FIELDS = ("verdict", "category", "source", "label_scope", "confidence",
               "judgment_id", "judgment_size", "sys", "id")


def stage_gold(ctx: dict) -> None:
    """Read the 112,854 gold/silver judgements and bucket them by pack.

    The file is already one row per (pack, ``i``) — verified here, not assumed.
    ``i`` is our ``recipe_idx``; the row's ``id`` is only ever used as a
    *cross-check* of that index, never as a join key.
    """
    t0 = time.time()
    wanted = set(ctx["packs"])
    gold = {slug: {} for slug in ctx["packs"]}
    seen = collections.Counter()
    skipped_packs = collections.Counter()
    with open(GOLD_FILE, "r", encoding="utf-8") as handle:
        for line in handle:
            if not line.strip():
                continue
            row = json.loads(line)
            slug = row["pack"]
            if slug not in wanted:
                skipped_packs[slug] += 1
                continue
            idx = int(row["i"])
            seen[(slug, idx)] += 1
            gold[slug][idx] = {k: row.get(k) for k in GOLD_FIELDS}

    # id cross-check: where the gold row carries a non-empty id, it must equal
    # the cached recipe's id at that index.  A mismatch would mean the index
    # spaces differ, which is the one thing that would silently corrupt every
    # label in this layer.
    checks = {}
    for slug in ctx["packs"]:
        if not gold[slug]:
            continue
        ids = pq.read_table(ctx["cache_dir"] / slug / "recipes.parquet",
                            columns=["recipe_id"]).column("recipe_id") \
                .to_numpy(zero_copy_only=False)
        n = ids.size
        checked = agree = out_of_range = 0
        for idx, rec in gold[slug].items():
            if idx < 0 or idx >= n:
                out_of_range += 1
                continue
            rid = rec.get("id") or ""
            cached = str(ids[idx])
            # An empty cached id is the 28.8 % case; the label sources render
            # those as a synthetic placeholder, which would only ever
            # disagree.  Nothing is inferred from the placeholder's text.
            if not rid or not cached:
                continue
            checked += 1
            agree += int(cached == rid)
        checks[slug] = {"rows": len(gold[slug]), "id_checked": checked,
                        "id_agree": agree, "out_of_range": out_of_range,
                        "id_agree_pct": round(100.0 * agree / checked, 2)
                        if checked else None}

    ctx["gold"] = gold
    dupes = sum(1 for v in seen.values() if v > 1)
    by_src = collections.Counter()
    by_scope = collections.Counter()
    by_cat = collections.Counter()
    for slug in ctx["packs"]:
        for rec in gold[slug].values():
            by_src[rec["source"]] += 1
            by_scope[rec["label_scope"]] += 1
            by_cat[rec["category"] or ""] += 1
    ctx["meta"]["gold"] = {
        "seconds": round(time.time() - t0, 2),
        "rows_in_scope": sum(len(v) for v in gold.values()),
        "duplicate_pack_idx": dupes,
        "rows_other_packs": sum(skipped_packs.values()),
        "by_source": dict(sorted(by_src.items())),
        "by_scope": dict(sorted(by_scope.items())),
        "by_category": dict(sorted(by_cat.items())),
        "id_check": checks,
    }
    total = sum(len(v) for v in gold.values())
    worst = min((c["id_agree_pct"] for c in checks.values()
                 if c["id_agree_pct"] is not None), default=None)
    log(f"[gold] {total:,} rows in scope ({dupes} duplicate keys); "
        f"sources={len(by_src)}; scope={dict(sorted(by_scope.items()))}; "
        f"worst id-agreement {worst}%; {time.time() - t0:.1f}s")



# =============================================================== stage: audit
#
# The chain audits are human markdown, not a data format.  Everything below is
# *structural* parsing: table headers name their own columns, finding headers
# have a fixed ``key=value`` shape, and recipe ids are the backticked tokens.
# No pack, mod or item name is ever written down; the file/pack mapping is
# resolved by normalised string matching and printed.

FINDING_RE = re.compile(r"^###\s+(F-[^\s|]+)\s*\|(.*)$")
KV_RE = re.compile(r"(\w+)\s*=\s*([^|]+)")
BACKTICK_RE = re.compile(r"`([^`]+)`")
CHAIN_NUM_RE = re.compile(r"(?<!\d)(\d{4})(?:\s*-\s*(\d{4}))?(?!\d)")
BATCH_RE = re.compile(r"batch-(\d{3})")
#: ``<ns>:<path>``-shaped token inside a backticked chunk.  Audit prose quotes
#: whole chain lines (``[A52] EXECUTE <id> x1 @ <machine>``) inside one pair of
#: backticks, so the id has to be pulled out structurally.
ID_TOKEN_RE = re.compile(r"[a-z0-9_]+:[a-z0-9_][a-z0-9_./-]*")
#: bare path fallback, used only when a chunk has no namespaced token.
PATH_TOKEN_RE = re.compile(r"[a-z0-9_]+(?:[/_][a-z0-9_]+)+")
VERDICT_RE = re.compile(r"(ok|bad|questionable)", re.IGNORECASE)


def parse_chain_cell(cell: str) -> list[str]:
    """Chain ids out of a free-text cell: ``0004(主证),0020`` / ``0062-0068``."""
    out = []
    for first, last in CHAIN_NUM_RE.findall(cell):
        if last and 0 < int(last) - int(first) <= 100:
            out.extend(f"{n:04d}" for n in range(int(first), int(last) + 1))
        else:
            out.append(first)
            if last:
                out.append(last)
    seen, uniq = set(), []
    for c in out:
        if c not in seen:
            seen.add(c)
            uniq.append(c)
    return uniq


def parse_verdict(cell: str) -> str:
    """``**bad**`` -> bad, ``ok(known)`` -> ok, ``ok/questionable`` -> the
    weaker reading (a cell that hedges is not a verdict)."""
    hits = [h.lower() for h in VERDICT_RE.findall(cell)]
    if not hits:
        return ""
    if len(set(hits)) > 1:
        return "questionable"
    return hits[0]


def split_row(line: str) -> list[str]:
    cells = line.strip().strip("|").split("|")
    return [c.strip() for c in cells]


def parse_verdict_tables(lines: list[str]) -> list[dict]:
    """Every ``| chain | ... | verdict | ... |`` table in a review file.

    The header row names its own columns, so the column *order* is read from
    the file instead of assumed — rounds 1-6 do not agree on it.
    """
    rows, i = [], 0
    while i < len(lines):
        line = lines[i]
        if line.lstrip().startswith("|"):
            head = [c.lower() for c in split_row(line)]
            col = {}
            for name in ("chain", "verdict", "category"):
                for j, cell in enumerate(head):
                    if name in cell:
                        col[name] = j
                        break
            if "chain" in col and "verdict" in col and i + 1 < len(lines) \
                    and set(lines[i + 1].strip()) <= set("|- :"):
                i += 2
                while i < len(lines) and lines[i].lstrip().startswith("|"):
                    cells = split_row(lines[i])
                    if len(cells) > max(col.values()):
                        verdict = parse_verdict(cells[col["verdict"]])
                        category = cells[col["category"]] if "category" in col else ""
                        for chain in parse_chain_cell(cells[col["chain"]]):
                            rows.append({"chain_id": chain, "verdict": verdict,
                                         "category": category,
                                         "note": cells[-1][:200]})
                    i += 1
                continue
        i += 1
    return rows


def parse_findings(lines: list[str]) -> list[dict]:
    """``### F-<pack>-N | chain=… | severity=… | category=…`` plus its body."""
    out = []
    current = None
    for line in lines:
        m = FINDING_RE.match(line)
        if m:
            kv = dict((k.lower(), v.strip()) for k, v in KV_RE.findall(m.group(2)))
            current = {"finding_id": m.group(1),
                       "chains": parse_chain_cell(kv.get("chain", "")),
                       "severity": kv.get("severity", ""),
                       "category": kv.get("category", ""),
                       "body": []}
            out.append(current)
            continue
        if line.startswith("#"):
            current = None
            continue
        if current is not None and line.strip():
            current["body"].append(line.rstrip())
    for f in out:
        # ``fields`` keeps the finding template's own sections (位置 / 问题 /
        # 原因 / 建议) for anyone reading the parse; the pair extractor
        # deliberately does *not* use them — see extract_pairs.
        f["fields"] = {}
        for line in f["body"]:
            label, rest = split_labelled_line(line)
            if label:
                f["fields"].setdefault(label, []).append(rest)
        f["ids"] = [t for line in f["body"] for t in BACKTICK_RE.findall(line)]
    return out


def split_labelled_line(line: str):
    """``- 位置: <text>`` -> ``("位置", "<text>")``.

    The colon must be *outside* backticks: audit lines routinely quote ids that
    contain one, and splitting on those invents a field per id.
    """
    stripped = line.lstrip()
    if not stripped[:2] in ("- ", "* "):
        return None, line
    rest = stripped[2:]
    depth = 0
    for i, ch in enumerate(rest):
        if ch == "`":
            depth ^= 1
        elif ch in ":：" and not depth:
            label = rest[:i].strip()
            if 0 < len(label) <= 16 and "`" not in label:
                return label, rest[i + 1:]
            return None, line
    return None, line


class IdIndex:
    """``recipe id -> recipe_idx`` for one pack, plus a path-suffix fallback.

    Built from the cache, so it is the *export's* id space.  Ids are neither
    unique nor total (28.8 % are empty), which is exactly why nothing else in
    this layer joins on them: the audits are text and simply have no index.
    """

    def __init__(self, ids: np.ndarray, machines=None):
        self.exact = collections.defaultdict(list)
        self.tail = collections.defaultdict(list)
        self.by_machine = collections.defaultdict(list)
        for idx, raw in enumerate(ids):
            rid = str(raw)
            if not rid:
                continue
            self.exact[rid].append(idx)
            path = rid.partition(":")[2] or rid
            self.tail[path].append(idx)
            last = path.rsplit("/", 1)[-1]
            if last != path:
                self.tail[last].append(idx)
            for machine in (machines[idx] if machines is not None else None) or [""]:
                self.by_machine[(rid, str(machine))].append(idx)

    def lookup_step(self, token: str, machine: str):
        """Resolve a chain-export EXECUTE line.

        A recipe id is *not* unique: 20 % of this corpus's recipes share their
        id with another row (a JEI mirror of the same recipe under a different
        system).  The chain line also names the machine it ran on, which is a
        different export field, and that settles 87-100 % of the collisions.
        """
        hits = self.exact.get(token)
        if not hits:
            return None, "missing"
        if len(hits) == 1:
            return hits[0], "resolved"
        narrowed = self.by_machine.get((token, machine or ""))
        if narrowed and len(narrowed) == 1:
            return narrowed[0], "resolved_by_machine"
        return None, "ambiguous"

    def lookup(self, token: str):
        """``(recipe_idx, status)`` with status in resolved/ambiguous/missing."""
        if not token:
            return None, "missing"
        for table in (self.exact, self.tail):
            hits = table.get(token)
            if hits:
                return (hits[0], "resolved") if len(hits) == 1 \
                    else (hits[0], "ambiguous")
        if ":" in token:
            return self.lookup(token.partition(":")[2])
        return None, "missing"

    def resolve(self, chunk: str):
        """First recipe a backticked chunk unambiguously names.

        Returns ``(recipe_idx, status)``; ``ambiguous`` is reported but never
        used, because a token that names two recipes names neither.
        """
        chunk = (chunk or "").strip().strip("`")
        if not chunk:
            return None, "missing"
        tokens = ID_TOKEN_RE.findall(chunk) or PATH_TOKEN_RE.findall(chunk)
        worst = "missing"
        for token in tokens:
            idx, status = self.lookup(token.rstrip("./-_"))
            if status == "resolved":
                return idx, status
            if status == "ambiguous":
                worst = status
        return None, worst


def id_index(ctx: dict, slug: str) -> IdIndex:
    cache = ctx.setdefault("_id_index", {})
    if slug not in cache:
        table = pq.read_table(ctx["cache_dir"] / slug / "recipes.parquet",
                              columns=["recipe_id", "machines"])
        cache[slug] = IdIndex(
            table.column("recipe_id").to_numpy(zero_copy_only=False),
            table.column("machines").to_pylist())
    return cache[slug]


def review_files(ctx: dict) -> list[dict]:
    """Every per-pack review markdown, mapped to a slug and a chain batch."""
    found, unmapped = [], []
    slugs = ctx["packs"]
    for round_dir in sorted(REVIEW_DIR.glob("round-*")):
        for path in sorted(round_dir.glob("*.md")):
            if path.stem.upper() == "SUMMARY":
                continue
            slug = match_name(path.stem, slugs)
            if slug is None:
                unmapped.append(f"{round_dir.name}/{path.name}")
                continue
            text = path.read_text(encoding="utf-8")
            batch = BATCH_RE.search(text)
            found.append({"round": round_dir.name, "path": path, "slug": slug,
                          "batch": f"batch-{batch.group(1)}" if batch else "",
                          "lines": text.splitlines()})
    ctx["meta"].setdefault("audit", {})["unmapped_files"] = unmapped
    return found


def stage_audit(ctx: dict) -> None:
    t0 = time.time()
    files = review_files(ctx)
    mapping = {}
    chain_rows, findings = [], []
    family = {slug: {} for slug in ctx["packs"]}
    resolve_stats = collections.Counter()

    for f in files:
        mapping.setdefault(f["slug"], []).append(f"{f['round']}:{f['path'].stem}"
                                                 f"@{f['batch'] or '?'}")
        slug = f["slug"]
        idx = id_index(ctx, slug)
        last_round = f["round"] == sorted(p.name for p in REVIEW_DIR.glob("round-*"))[-1]
        for row in parse_verdict_tables(f["lines"]):
            row.update(pack=slug, round=f["round"], batch=f["batch"])
            chain_rows.append(row)
        for finding in parse_findings(f["lines"]):
            record = {"pack": slug, "round": f["round"], "batch": f["batch"],
                      "finding_id": finding["finding_id"],
                      "severity": finding["severity"],
                      "category": finding["category"],
                      "chains": finding["chains"]}
            findings.append(record)
            # rounds 1-5: a junk-pattern family.  Attach the family name to
            # every recipe the finding names.
            hit = []
            for token in finding["ids"]:
                rid, status = idx.resolve(token)
                resolve_stats[status] += 1
                if rid is not None and status == "resolved":
                    hit.append(rid)
            record["recipe_idx"] = sorted(set(hit))
            record["ids"] = finding["ids"]
            record["last_round"] = last_round
            if not last_round:
                for rid in hit:
                    family[slug].setdefault(rid, finding["category"])

    ctx["audit_family"] = family
    ctx["audit_findings"] = findings
    ctx["audit_chain_rows"] = chain_rows
    verdicts = collections.Counter(r["verdict"] for r in chain_rows)
    ctx["meta"]["audit"].update({
        "seconds": round(time.time() - t0, 2),
        "files": len(files), "pack_mapping": mapping,
        "findings": len(findings),
        "findings_last_round": sum(1 for f in findings
                                   if f["round"].endswith(str(len(list(REVIEW_DIR.glob("round-*")))).zfill(3))),
        "chain_verdict_rows": len(chain_rows),
        "chain_verdicts": dict(sorted(verdicts.items())),
        "id_resolution": dict(sorted(resolve_stats.items())),
        "recipes_with_family": {s: len(v) for s, v in sorted(family.items()) if v},
    })
    log(f"[audit] {len(files)} review files -> {len(ctx['packs'])} packs; "
        f"{len(findings)} findings; {len(chain_rows)} chain verdict rows "
        f"{dict(sorted(verdicts.items()))}; "
        f"{sum(len(v) for v in family.values())} recipes carry a family; "
        f"id-resolution {dict(sorted(resolve_stats.items()))}; "
        f"{time.time() - t0:.1f}s")




# ============================================================== stage: chains
#
# The chain exports are the planner's OUTPUT: every ``EXECUTE`` line names a
# recipe the router actually chose.  That is what turns a prose audit into a
# pair — "chosen" is not a word in the markdown, it is the recipe that appears
# in the cited chain.

CHAIN_HEAD = "### CHAIN "
EXECUTE_RE = re.compile(r"\bEXECUTE\s+(\S+)\s+x\d+(?:\s+@\s+(\S+))?")

def output_rows(ctx: dict, slug: str):
    """``(recipe_idx[], base_id[])`` over the pack's *output* content rows."""
    cache = ctx.setdefault("_outputs", {})
    if slug not in cache:
        table = pq.read_table(ctx["cache_dir"] / slug / "contents.parquet",
                              columns=["recipe_idx", "side", "base_id"])
        side = table.column("side").to_numpy(zero_copy_only=False)
        mask = side == SIDE_OUT
        cache[slug] = (
            table.column("recipe_idx").to_numpy(zero_copy_only=False)[mask],
            table.column("base_id").to_numpy(zero_copy_only=False)[mask])
    return cache[slug]


def output_sets(ctx: dict, slug: str, wanted) -> dict:
    """``{recipe_idx: frozenset(base_id)}`` for the given recipe indices."""
    ridx, bids = output_rows(ctx, slug)
    wanted = set(int(w) for w in wanted)
    out: dict = {w: set() for w in wanted}
    for r, b in zip(ridx, bids):
        r = int(r)
        if r in out:
            out[r].add(str(b))
    return {k: frozenset(v) for k, v in out.items()}


def extract_pairs(ctx: dict, slug: str, idx: IdIndex, finding: dict,
                  chain_set: set):
    """Every (chosen, preferred) pair a routing finding admits.

    The finding is prose, so neither side is read off a keyword.  Two purely
    structural facts decide it:

    * **chosen** = a recipe the cited chain actually EXECUTEd.  "The planner
      chose it" is not an opinion in the markdown, it is a line in the chain
      export.
    * **preferred** = a recipe the finding names that the cited chain did
      *not* execute and that produces the same base id as the chosen one.
      The same-output constraint keeps the prose's incidental ids (the inputs
      it blames, the items it names) out of the pair.

    A finding often names several such pairs on purpose — "these three impure
    recipes all have a pure alternative" — so *all* valid readings are kept
    and each carries ``n_readings`` so a trainer can down-weight a finding
    that admits more than one.  Returns ``(pairs, reason)``; ``reason`` names
    the structural fact that produced no pair.
    """
    def resolved(chunk_list):
        seen, out = set(), []
        for chunk in chunk_list:
            rid, status = idx.resolve(chunk)
            if rid is not None and status == "resolved" and rid not in seen:
                seen.add(rid)
                out.append(rid)
        return out

    named = resolved(finding["ids"])
    if len(named) < 2:
        return [], "fewer_than_two_recipes_named"
    chosen_c = [r for r in named if r in chain_set]
    pref_c = [r for r in named if r not in chain_set]
    if not chosen_c:
        return [], "no_named_recipe_in_the_cited_chain"
    if not pref_c:
        return [], "every_named_recipe_is_in_the_cited_chain"
    outs = output_sets(ctx, slug, set(named))
    pairs = []
    for pref in pref_c:
        for chosen in chosen_c:
            shared = outs.get(chosen, frozenset()) & outs.get(pref, frozenset())
            if shared:
                pairs.append({"chosen_recipe_idx": chosen,
                              "preferred_recipe_idx": pref,
                              "output_base_id": min(shared)})
    if not pairs:
        return [], "no_shared_output_between_any_named_pair"
    for pair in pairs:
        pair["n_readings"] = len(pairs)
    return pairs, ""



def parse_batch(batch_dir: Path, wanted: set) -> dict:
    """``{chain_id: [recipe id, ...]}`` for the wanted chains of one batch."""
    out: dict = {}
    current = None
    for path in sorted(batch_dir.glob("chains-*.txt")):
        with open(path, "r", encoding="utf-8", errors="replace") as handle:
            for line in handle:
                if line.startswith(CHAIN_HEAD):
                    chain = line[len(CHAIN_HEAD):].split("|", 1)[0].strip()
                    current = out.setdefault(chain, []) if chain in wanted else None
                elif current is not None and "EXECUTE" in line:
                    m = EXECUTE_RE.search(line)
                    if m:
                        current.append((m.group(1), m.group(2) or ""))
    return out


def stage_chains(ctx: dict) -> None:
    t0 = time.time()
    needed: dict = {}
    for row in ctx["audit_chain_rows"]:
        needed.setdefault((row["pack"], row["batch"]), set()).add(row["chain_id"])
    for finding in ctx["audit_findings"]:
        if finding["chains"]:
            needed.setdefault((finding["pack"], finding["batch"]), set()) \
                .update(finding["chains"])

    executed: dict = {}
    stats = collections.Counter()
    missing_batches = []
    for (slug, batch), chains in sorted(needed.items()):
        if not batch:
            stats["no_batch"] += 1
            continue
        batch_dir = CHAINS_DIR / ctx["pack_names"][slug] / batch
        if not batch_dir.is_dir():
            missing_batches.append(f"{slug}/{batch}")
            continue
        idx = id_index(ctx, slug)
        for chain, ids in parse_batch(batch_dir, chains).items():
            hits = []
            for rid, machine in ids:
                r, status = idx.lookup_step(rid, machine)
                stats[f"execute_{status}"] += 1
                if r is not None:
                    hits.append(r)
            executed[(slug, batch, chain)] = sorted(set(hits))
        stats["batches"] += 1
    ctx["chain_executed"] = executed

    # ------------------------------------------------ chain_verdicts.parquet
    rows = []
    for row in ctx["audit_chain_rows"]:
        key = (row["pack"], row["batch"], row["chain_id"])
        rows.append({
            "pack": row["pack"], "pack_group": ctx["pack_group"][row["pack"]],
            "round": row["round"], "batch": row["batch"],
            "chain_id": row["chain_id"], "verdict": row["verdict"],
            "category": row["category"],
            "recipe_idx": executed.get(key, []),
            "n_recipes": len(executed.get(key, [])),
        })
    rows.sort(key=lambda r: (r["pack"], r["round"], r["batch"], r["chain_id"]))
    schema = pa.schema([
        ("pack", pa.string()), ("pack_group", pa.string()),
        ("round", pa.string()), ("batch", pa.string()),
        ("chain_id", pa.string()), ("verdict", pa.string()),
        ("category", pa.string()), ("recipe_idx", pa.list_(pa.int32())),
        ("n_recipes", pa.int32())])
    cols = {f.name: [r[f.name] for r in rows] for f in schema}
    n = write_table(ctx["label_dir"] / "chain_verdicts.parquet", cols, schema)

    # -------------------------------------- per-recipe ok/bad chain evidence
    ok_count: dict = {}
    bad_count: dict = {}
    for row in rows:
        target = ok_count if row["verdict"] == CHAIN_OK else \
            bad_count if row["verdict"] == CHAIN_BAD else None
        if target is None:
            continue
        pack = row["pack"]
        per = target.setdefault(pack, collections.Counter())
        per.update(row["recipe_idx"])
    ctx["chain_ok"] = ok_count
    ctx["chain_bad"] = bad_count

    # --------------------------------------------------- the pairwise labels
    pairwise, unresolved_findings, n_findings = [], {}, 0
    for finding in ctx["audit_findings"]:
        if not finding.get("last_round"):
            continue
        n_findings += 1
        slug = finding["pack"]
        chain_set = set()
        for chain in finding["chains"]:
            chain_set.update(executed.get((slug, finding["batch"], chain), []))
        pairs, reason = extract_pairs(ctx, slug, id_index(ctx, slug), finding,
                                      chain_set)
        if not pairs:
            unresolved_findings[f"{slug}:{finding['finding_id']}"] = reason
            continue
        for pair in pairs:
            pair.update(pack=slug, pack_group=ctx["pack_group"][slug],
                        finding_id=finding["finding_id"],
                        round=finding["round"],
                        severity=finding["severity"],
                        category=finding["category"],
                        weight=1.0 / pair["n_readings"])
            pairwise.append(pair)
    pairwise.sort(key=lambda p: (p["pack"], p["finding_id"],
                                 p["chosen_recipe_idx"],
                                 p["preferred_recipe_idx"]))
    ctx["pairwise_raw"] = pairwise
    gold_schema = pa.schema([
        ("pack", pa.string()), ("pack_group", pa.string()),
        ("output_base_id", pa.string()),
        ("chosen_recipe_idx", pa.int32()), ("preferred_recipe_idx", pa.int32()),
        ("severity", pa.string()), ("category", pa.string()),
        ("finding_id", pa.string()), ("round", pa.string()),
        ("n_readings", pa.int32()), ("weight", pa.float32())])
    write_table(ctx["label_dir"] / "pairwise_gold.parquet",
                {f.name: [p[f.name] for p in pairwise] for f in gold_schema},
                gold_schema)

    ctx.pop("_id_index", None)
    ctx.pop("_outputs", None)
    matched = sum(1 for r in rows if r["n_recipes"])
    ctx["meta"]["chains"] = {
        "seconds": round(time.time() - t0, 2),
        "batches_parsed": int(stats["batches"]),
        "missing_batches": missing_batches,
        "chains_with_steps": len(executed),
        "verdict_rows": n, "verdict_rows_with_steps": matched,
        "execute_resolution": {k: int(v) for k, v in sorted(stats.items())
                               if k.startswith("execute_")},
        "routing_findings": n_findings,
        "pairwise_rows": len(pairwise),
        "pairwise_findings_resolved": n_findings - len(unresolved_findings),
        "pairwise_unresolved": unresolved_findings,
    }
    log(f"[chains] {int(stats['batches'])} batches -> {len(executed):,} chains; "
        f"{n:,} verdict rows ({matched:,} with steps); "
        f"EXECUTE resolution {ctx['meta']['chains']['execute_resolution']}; "
        f"pairwise_gold {len(pairwise)} rows from "
        f"{n_findings - len(unresolved_findings)}/{n_findings} routing findings; "
        f"{time.time() - t0:.1f}s")


# ============================================================= stage: recipes


RECIPE_SCHEMA = pa.schema([
    ("pack", pa.string()), ("pack_group", pa.string()),
    ("recipe_idx", pa.int32()), ("template_id", pa.string()),
    (COL_WL, pa.string()), ("rule_hits", pa.list_(pa.string())),
    ("n_rule_hits", pa.int32()),
    ("gold_verdict", pa.string()), ("gold_category", pa.string()),
    ("gold_source", pa.string()), ("gold_tier", pa.int32()),
    ("gold_scope", pa.string()), ("gold_confidence", pa.string()),
    ("gold_role", pa.string()),
    ("system_verdict", pa.string()),
    (COL_CHAIN_OK, pa.int32()), (COL_CHAIN_BAD, pa.int32()),
    ("audit_family", pa.string()),
])


def template_map(ctx: dict, slug: str) -> dict:
    table = pq.read_table(ctx["out_dir"] / slug / "recipe_template.parquet")
    return dict(zip(table.column("recipe_idx").to_pylist(),
                    table.column("template_id").to_pylist()))


def stage_recipes(ctx: dict) -> None:
    t0 = time.time()
    cols = {f.name: [] for f in RECIPE_SCHEMA}
    per_pack = {}
    for slug in ctx["packs"]:
        group = ctx["pack_group"][slug]
        tpl = template_map(ctx, slug)
        replay = ctx["replay"].get(slug, {})
        gold = ctx["gold"].get(slug, {})
        ok = ctx["chain_ok"].get(slug, {})
        bad = ctx["chain_bad"].get(slug, {})
        family = ctx["audit_family"].get(slug, {})
        keys = sorted(set(replay) | set(gold) | set(ok) | set(bad) | set(family))
        no_template = 0
        for idx in keys:
            wl, hits = replay.get(idx, ("", []))
            g = gold.get(idx)
            tid = tpl.get(idx, "")
            no_template += int(not tid)
            cols["pack"].append(slug)
            cols["pack_group"].append(group)
            cols["recipe_idx"].append(idx)
            cols["template_id"].append(tid)
            cols[COL_WL].append(wl)
            cols["rule_hits"].append(sorted(hits))
            cols["n_rule_hits"].append(len(hits))
            cols["gold_verdict"].append((g or {}).get("verdict") or "")
            cols["gold_category"].append((g or {}).get("category") or "")
            cols["gold_source"].append((g or {}).get("source") or "")
            cols["gold_tier"].append(source_tier(g["source"]) if g else -1)
            cols["gold_scope"].append((g or {}).get("label_scope") or "")
            cols["gold_confidence"].append((g or {}).get("confidence") or "")
            cols["gold_role"].append(
                CATEGORY_ROLE.get((g or {}).get("category") or "", "") if g else "")
            cols["system_verdict"].append(
                g["verdict"] if g and g.get("label_scope") == "system" else "")
            cols[COL_CHAIN_OK].append(int(ok.get(idx, 0)))
            cols[COL_CHAIN_BAD].append(int(bad.get(idx, 0)))
            cols["audit_family"].append(family.get(idx, ""))
        per_pack[slug] = {
            "rows": len(keys), "pack_recipes": ctx["n_recipes"][slug],
            "rule_replay": len(replay), "gold": len(gold),
            "chain_ok": len(ok), "chain_bad": len(bad),
            "audit_family": len(family), "without_template": no_template,
        }
    n = write_table(ctx["label_dir"] / "recipe_labels.parquet",
                    cols, RECIPE_SCHEMA)
    ctx["recipe_labels"] = cols
    tiers = collections.Counter(t for t in cols["gold_tier"] if t >= 0)
    roles = collections.Counter(r for r in cols["gold_role"] if r)
    ctx["meta"]["recipes"] = {
        "seconds": round(time.time() - t0, 2), "rows": n,
        "with_gold": sum(1 for v in cols["gold_verdict"] if v),
        "with_rule_hit": sum(1 for v in cols["n_rule_hits"] if v),
        "with_chain_evidence": sum(1 for a, b in zip(cols[COL_CHAIN_OK],
                                                     cols[COL_CHAIN_BAD])
                                   if a or b),
        "with_audit_family": sum(1 for v in cols["audit_family"] if v),
        "without_template": sum(1 for v in cols["template_id"] if not v),
        "gold_by_tier": {SOURCE_TIERS[t] if t < len(SOURCE_TIERS) else "other":
                         int(c) for t, c in sorted(tiers.items())},
        "gold_role": {k: int(v) for k, v in sorted(roles.items())},
        "per_pack": per_pack,
    }
    m = ctx["meta"]["recipes"]
    log(f"[recipes] {n:,} rows; gold {m['with_gold']:,}; "
        f"rule-hit {m['with_rule_hit']:,}; chain evidence "
        f"{m['with_chain_evidence']:,}; family {m['with_audit_family']:,}; "
        f"no template {m['without_template']:,}; "
        f"tiers {m['gold_by_tier']}; {time.time() - t0:.1f}s")


# =========================================================== stage: templates
#
# A template is the unit the role/junk heads predict: it is what generalises
# across packs, and it is the only level at which "this kind of recipe" is a
# meaningful statement.  Folding the per-recipe judgements up to it needs a
# weighting, because the judgements do not agree and were not made equally
# carefully.


def tier_weight(tier: int) -> float:
    """Trust in one judgement, from the quality tier of its source.

    ``1 / (1 + tier)``: adjudication 1.00, human-nb 0.50, audit 0.33,
    subagent 0.25, svm 0.20.  Harmonic rather than geometric decay, so a
    thousand SVM votes still cannot outvote the adjudicated answer by more
    than 5:1 per vote, and the tiers stay on one comparable scale.
    """
    return 1.0 / (1.0 + max(0, tier))


N_TIERS = len(SOURCE_TIERS) + 1


def template_schema() -> pa.Schema:
    fields = [("pack", pa.string()), ("pack_group", pa.string()),
              ("template_id", pa.string()),
              ("n_members", pa.int32()), ("n_labeled", pa.int32()),
              (COL_N_JUNK, pa.int32()), (COL_N_USEFUL, pa.int32())]
    for tier in range(N_TIERS):
        fields.append((f"{COL_N_JUNK}_t{tier}", pa.int32()))
        fields.append((f"{COL_N_USEFUL}_t{tier}", pa.int32()))
    fields += [
        ("majority_verdict", pa.string()), ("purity", pa.float32()),
        ("conflict", pa.bool_()), ("best_tier", pa.int32()),
        ("label_weight", pa.float32()),
        (COL_JUNK_TARGET, pa.float32()),
        ("role_target", pa.string()), ("role_purity", pa.float32()),
        ("role_weight", pa.float32()), ("n_role_votes", pa.int32()),
        ("system_verdict", pa.string()),
        (f"{COL_WL}_{WL_JUNK}", pa.int32()), (f"{COL_WL}_{WL_MIDDLE}", pa.int32()),
        (f"{COL_WL}_{WL_USEFUL}", pa.int32()),
        (f"{COL_WL}_majority", pa.string()),
        ("n_rule_hits", pa.int32()),
        (COL_CHAIN_OK, pa.int32()), (COL_CHAIN_BAD, pa.int32()),
        ("audit_family", pa.string()),
    ]
    return pa.schema(fields)


def weighted_majority(votes) -> tuple:
    """``(winner, purity, total)`` over ``[(name, weight), ...]``.

    Ties break on the sorted name so two runs agree.
    """
    mass = collections.defaultdict(float)
    for name, weight in votes:
        mass[name] += weight
    if not mass:
        return "", 0.0, 0.0
    total = sum(mass.values())
    winner = max(sorted(mass), key=lambda k: mass[k])
    return winner, mass[winner] / total, total


def stage_templates(ctx: dict) -> None:
    t0 = time.time()
    labels = ctx["recipe_labels"]
    by_pack: dict = {}
    for i, pack in enumerate(labels["pack"]):
        by_pack.setdefault(pack, []).append(i)

    schema = template_schema()
    cols = {f.name: [] for f in schema}
    role_counts = collections.Counter()
    role_by_tier = collections.Counter()
    group_of_template: dict = {}
    per_pack = {}

    for slug in ctx["packs"]:
        group = ctx["pack_group"][slug]
        table = pq.read_table(ctx["out_dir"] / slug / "recipe_template.parquet")
        members = collections.Counter(table.column("template_id").to_pylist())
        rows: dict = {}
        for i in by_pack.get(slug, []):
            tid = labels["template_id"][i]
            if tid:
                rows.setdefault(tid, []).append(i)
        n_role = 0
        for tid in sorted(members):
            idxs = rows.get(tid, [])
            verdict_votes, role_votes = [], []
            junk_t = [0] * N_TIERS
            useful_t = [0] * N_TIERS
            wl_counts = collections.Counter()
            rule_hits = chain_ok = chain_bad = 0
            family = ""
            sys_votes = []
            for i in idxs:
                wl_counts[labels[COL_WL][i]] += 1
                rule_hits += labels["n_rule_hits"][i]
                chain_ok += labels[COL_CHAIN_OK][i]
                chain_bad += labels[COL_CHAIN_BAD][i]
                family = family or labels["audit_family"][i]
                if labels["system_verdict"][i]:
                    sys_votes.append((labels["system_verdict"][i], 1.0))
                verdict = labels["gold_verdict"][i]
                if not verdict:
                    continue
                tier = labels["gold_tier"][i]
                verdict_votes.append((verdict, tier_weight(tier)))
                (junk_t if verdict == VERDICT_JUNK else useful_t)[tier] += 1
                role = labels["gold_role"][i]
                if role:
                    role_votes.append((role, tier_weight(tier)))
                    role_by_tier[(SOURCE_TIERS[tier] if tier < len(SOURCE_TIERS)
                                  else "other", role)] += 1
            majority, purity, _ = weighted_majority(verdict_votes)
            role, role_purity, _ = weighted_majority(role_votes)
            best = min((labels["gold_tier"][i] for i in idxs
                        if labels["gold_verdict"][i]), default=-1)
            weight = purity * tier_weight(best) if best >= 0 else 0.0
            n_role += int(bool(role))
            role_counts[role or "(none)"] += 1
            if role:
                group_of_template.setdefault(tid, set()).add(group)

            cols["pack"].append(slug)
            cols["pack_group"].append(group)
            cols["template_id"].append(tid)
            cols["n_members"].append(int(members[tid]))
            cols["n_labeled"].append(len(verdict_votes))
            cols[COL_N_JUNK].append(sum(junk_t))
            cols[COL_N_USEFUL].append(sum(useful_t))
            for tier in range(N_TIERS):
                cols[f"{COL_N_JUNK}_t{tier}"].append(junk_t[tier])
                cols[f"{COL_N_USEFUL}_t{tier}"].append(useful_t[tier])
            cols["majority_verdict"].append(majority)
            cols["purity"].append(purity)
            cols["conflict"].append(bool(sum(junk_t) and sum(useful_t)))
            cols["best_tier"].append(best)
            cols["label_weight"].append(weight)
            cols[COL_JUNK_TARGET].append(
                float("nan") if not majority else
                (1.0 if majority == VERDICT_JUNK else 0.0))
            cols["role_target"].append(role)
            cols["role_purity"].append(role_purity)
            cols["role_weight"].append(
                role_purity * tier_weight(best) if role else 0.0)
            cols["n_role_votes"].append(len(role_votes))
            cols["system_verdict"].append(weighted_majority(sys_votes)[0])
            cols[f"{COL_WL}_{WL_JUNK}"].append(int(wl_counts[WL_JUNK]))
            cols[f"{COL_WL}_{WL_MIDDLE}"].append(int(wl_counts[WL_MIDDLE]))
            cols[f"{COL_WL}_{WL_USEFUL}"].append(int(wl_counts[WL_USEFUL]))
            cols[f"{COL_WL}_majority"].append(
                weighted_majority([(k, v) for k, v in wl_counts.items() if k])[0])
            cols["n_rule_hits"].append(rule_hits)
            cols[COL_CHAIN_OK].append(chain_ok)
            cols[COL_CHAIN_BAD].append(chain_bad)
            cols["audit_family"].append(family)
        per_pack[slug] = {"templates": len(members), "with_role": n_role}

    n = write_table(ctx["label_dir"] / "template_labels.parquet", cols, schema)
    ctx["template_labels"] = cols
    cross = sum(1 for groups in group_of_template.values() if len(groups) >= 2)
    ctx["meta"]["templates"] = {
        "seconds": round(time.time() - t0, 2), "rows": n,
        "with_verdict": sum(1 for v in cols["majority_verdict"] if v),
        "with_role": sum(1 for v in cols["role_target"] if v),
        "conflicting": int(sum(cols["conflict"])),
        "role_counts": {k: int(v) for k, v in sorted(role_counts.items())},
        "role_by_source_tier": {f"{a}|{b}": int(c)
                                for (a, b), c in sorted(role_by_tier.items())},
        "role_templates_distinct": len(group_of_template),
        "role_templates_in_2plus_groups": cross,
        "per_pack": per_pack,
    }
    m = ctx["meta"]["templates"]
    log(f"[templates] {n:,} rows; verdict {m['with_verdict']:,}; "
        f"role {m['with_role']:,}; conflicts {m['conflicting']:,}; "
        f"distinct role templates {m['role_templates_distinct']:,} "
        f"({cross:,} in >=2 pack groups); {time.time() - t0:.1f}s")


# =============================================================== stage: pairs
#
# The ProducerRanker's training signal.  A pair is only meaningful between
# *siblings* — two recipes that produce the same base id — because that is the
# only choice the router ever makes.  Pairs are built per tier from the
# evidence that supports them, never by sampling blindly and hoping a label
# lands: three of the four tiers need a label on *both* sides, so enumerating
# the cross product of the labelled sets is both cheaper and denser.

#: base weight of a pair tier.  Gold is a human routing judgement about this
#: exact pair; silver two independent recipe verdicts; chain a human "this
#: chain is fine" against a junk verdict; bronze the rule replay, which is the
#: thing this project exists to replace, so it barely counts.
TIER_WEIGHT_BASE = {TIER_GOLD: 1.0, TIER_SILVER: 1.0,
                    TIER_CHAIN: 0.5, TIER_BRONZE: 0.1}
#: tiers in precedence order; a pair takes the best tier that applies.
TIER_ORDER = (TIER_GOLD, TIER_SILVER, TIER_CHAIN, TIER_BRONZE)

PAIR_SCHEMA = pa.schema([
    ("pack", pa.string()), ("pack_group", pa.string()),
    ("output_base_id", pa.string()),
    ("a_recipe_idx", pa.int32()), ("b_recipe_idx", pa.int32()),
    ("label", pa.int8()), ("tier", pa.string()), ("weight", pa.float32()),
    ("n_producers", pa.int32()),
])


def sample_cross(left, right, cap, rng):
    """Up to ``cap`` distinct ``(l, r)`` pairs from ``left x right``.

    Enumerates when the product is small and samples index pairs when it is
    not, so a 900-producer output costs the cap and not 400 000 tuples.
    """
    n, m = len(left), len(right)
    if n * m <= cap:
        return [(l, r) for l in left for r in right if l != r]
    seen, out = set(), []
    for _ in range(cap * 4):
        if len(out) >= cap:
            break
        i, j = int(rng.integers(n)), int(rng.integers(m))
        if left[i] == right[j] or (i, j) in seen:
            continue
        seen.add((i, j))
        out.append((left[i], right[j]))
    return out


def stage_pairs(ctx: dict) -> None:
    t0 = time.time()
    cap = ctx["args"].max_pairs_per_output
    labels = ctx["recipe_labels"]
    rows_by_pack: dict = {}
    for i, pack in enumerate(labels["pack"]):
        rows_by_pack.setdefault(pack, {})[labels["recipe_idx"][i]] = i

    gold_pairs: dict = {}
    for p in ctx["pairwise_raw"]:
        gold_pairs.setdefault(p["pack"], []).append(p)

    cols = {f.name: [] for f in PAIR_SCHEMA}
    per_pack, per_tier = {}, collections.Counter()
    label_balance = collections.Counter()
    outputs_total = outputs_multi = 0

    for slug in ctx["packs"]:
        # CPython salts ``hash()`` per process, so the pack seed comes from
        # the same sha1-over-canonical-JSON the rest of the stack uses.
        rng = np.random.default_rng(
            int(stable_hash([ctx["args"].seed, slug], 8), 16))
        group = ctx["pack_group"][slug]
        index = rows_by_pack.get(slug, {})
        kept = set(template_map(ctx, slug))
        ridx, bids = output_rows(ctx, slug)
        producers: dict = {}
        for r, b in zip(ridx, bids):
            r = int(r)
            if r in kept:
                producers.setdefault(str(b), set()).add(r)

        emitted: dict = {}
        n_pack = 0
        for pair in gold_pairs.get(slug, []):
            key = (pair["output_base_id"], pair["preferred_recipe_idx"],
                   pair["chosen_recipe_idx"])
            emitted[key[1:]] = (TIER_GOLD, pair["weight"], key[0])

        for base_id in sorted(producers):
            outputs_total += 1
            sibs = sorted(producers[base_id])
            if len(sibs) < 2:
                continue
            outputs_multi += 1
            wl_of, gv_of, gt_of, ok_of = {}, {}, {}, {}
            for r in sibs:
                i = index.get(r)
                if i is None:
                    continue
                wl_of[r] = labels[COL_WL][i]
                if labels["gold_verdict"][i]:
                    gv_of[r] = labels["gold_verdict"][i]
                    gt_of[r] = labels["gold_tier"][i]
                ok_of[r] = labels[COL_CHAIN_OK][i]
            g_use = [r for r in sibs if gv_of.get(r) == VERDICT_USEFUL]
            g_junk = [r for r in sibs if gv_of.get(r) == VERDICT_JUNK]
            w_use = [r for r in sibs if wl_of.get(r) == WL_USEFUL]
            w_junk = [r for r in sibs if wl_of.get(r) == WL_JUNK]
            chain_ok = [r for r in sibs if ok_of.get(r, 0) > 0]
            junky = sorted(set(g_junk) | set(w_junk))

            budget = cap
            for tier, left, right in ((TIER_SILVER, g_use, g_junk),
                                      (TIER_CHAIN, chain_ok, junky),
                                      (TIER_BRONZE, w_use, w_junk)):
                if budget <= 0 or not left or not right:
                    continue
                for a, b in sample_cross(left, right, budget, rng):
                    if (a, b) in emitted or (b, a) in emitted:
                        continue
                    if tier == TIER_SILVER:
                        weight = tier_weight(max(gt_of[a], gt_of[b]))
                    else:
                        weight = TIER_WEIGHT_BASE[tier]
                    emitted[(a, b)] = (tier, weight, base_id)
                    budget -= 1
                    if budget <= 0:
                        break

        for (a, b), (tier, weight, base_id) in sorted(emitted.items()):
            flip = bool(rng.integers(2))
            cols["pack"].append(slug)
            cols["pack_group"].append(group)
            cols["output_base_id"].append(base_id)
            cols["a_recipe_idx"].append(b if flip else a)
            cols["b_recipe_idx"].append(a if flip else b)
            cols["label"].append(-1 if flip else 1)
            cols["tier"].append(tier)
            cols["weight"].append(float(weight))
            cols["n_producers"].append(len(producers.get(base_id, ())))
            per_tier[tier] += 1
            label_balance[-1 if flip else 1] += 1
            n_pack += 1
        per_pack[slug] = n_pack
        ctx.setdefault("_outputs", {}).pop(slug, None)

    n = write_table(ctx["label_dir"] / "pairs.parquet", cols, PAIR_SCHEMA)
    ctx["meta"]["pairs"] = {
        "seconds": round(time.time() - t0, 2), "rows": n,
        "cap_per_output": cap, "seed": ctx["args"].seed,
        "outputs": outputs_total, "outputs_with_siblings": outputs_multi,
        "by_tier": {k: int(v) for k, v in sorted(per_tier.items())},
        "label_balance": {str(k): int(v) for k, v in sorted(label_balance.items())},
        "per_pack": per_pack,
    }
    log(f"[pairs] {n:,} pairs over {outputs_multi:,} outputs with siblings "
        f"(of {outputs_total:,}); tiers {ctx['meta']['pairs']['by_tier']}; "
        f"balance {ctx['meta']['pairs']['label_balance']}; "
        f"{time.time() - t0:.1f}s")


# ==================================================================== driver


def build_context(args) -> dict:
    cache_dir = Path(args.cache_dir)
    out_dir = Path(args.out_dir)
    packs = resolve_packs(cache_dir, args.packs)
    pack_names = read_json(cache_dir / "packs.json")
    groups = pack_group_map(out_dir, packs)
    n_recipes = {slug: pq.read_metadata(cache_dir / slug / "recipes.parquet").num_rows
                 for slug in packs}
    return {
        "args": args, "cache_dir": cache_dir, "out_dir": out_dir,
        "label_dir": Path(args.write_dir or out_dir) / "labels", "packs": packs,
        "pack_names": {s: pack_names[s] for s in packs},
        "pack_group": groups, "n_recipes": n_recipes,
        "meta": {"packs": packs, "pack_group": groups},
    }


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Phase 0c-1 label layer")
    ap.add_argument("--cache-dir", default="cache")
    ap.add_argument("--out-dir", default="out")
    ap.add_argument("--write-dir", default="",
                    help="where to write (default: --out-dir); the inputs are "
                         "always read from --out-dir")
    ap.add_argument("--packs", default="all")
    ap.add_argument("--stage", default="pairs", choices=STAGES,
                    help="run stages in order and stop after this one")
    ap.add_argument("--max-pairs-per-output", type=int, default=20,
                    help="cap on sampled sibling pairs per (pack, output)")
    ap.add_argument("--seed", type=int, default=20260919)
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
        write_json(ctx["label_dir"] / "labels-meta.json", ctx["meta"])
    log(f"done in {time.time() - t0:.1f}s, peak RSS {peak_rss_mb():.0f} MB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
