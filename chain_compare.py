#!/usr/bin/env python3
"""Compare two chain-export trees (before/after a rule or scoring change).

Aggregates per-chain health metrics from the `### CHAIN` / `status:` /
`rule-tag:` / `UNRESOLVED` lines and prints a side-by-side table plus the
largest per-metric deltas. Chain targets are deterministic given the same
pack export, so aggregate drift is attributable to the rule change.

Usage: python3 chain_compare.py <oldExportPackDir> <newExportPackDir>
"""
import re
import sys
from pathlib import Path
from collections import Counter

CHAIN = re.compile(r"^### CHAIN (\d+) \| category=(\S+)")
STATUS = re.compile(r"^status: (\S+).*?actions=(\d+) recipes=(\d+) crafts=(\d+) raw_kinds=(\d+) pending=(\d+)")
TAG = re.compile(r"\[rule-tag: ([^\]]+)\]")
UNRESOLVED = re.compile(r"^\[UNRESOLVED\].*reason=(\S+)", re.M)
HEADER_TAGS = re.compile(r"^builtin-rule-tags.*?: (.*)$", re.M)
HEADER_TAG_ENTRY = re.compile(r"(\S+) x(\d+)")


def scan(root: Path):
    chains = {}
    totals = Counter()
    categories = Counter()
    statuses = Counter()
    rule_tags = Counter()
    unresolved = Counter()
    header_tags = Counter()
    for f in sorted(root.rglob("chains-*.txt")):
        cur = None
        for line in f.read_text(errors="replace").splitlines():
            m = CHAIN.match(line)
            if m:
                cur = {"actions": 0, "crafts": 0, "pending": 0, "tags": 0}
                categories[m.group(2)] += 1
                totals["chains"] += 1
                continue
            if cur is None:
                continue
            m = STATUS.match(line)
            if m:
                statuses[m.group(1)] += 1
                cur["actions"] = int(m.group(2))
                cur["crafts"] = int(m.group(4))
                cur["pending"] = int(m.group(6))
                totals["actions"] += cur["actions"]
                totals["crafts"] += cur["crafts"]
                totals["pending"] += cur["pending"]
                totals["maxCrafts"] = max(totals["maxCrafts"], cur["crafts"])
                continue
            n = len(TAG.findall(line))
            if n:
                for t in TAG.findall(line):
                    rule_tags[t] += 1
                cur["tags"] += n
                totals["taggedSteps"] += n
        text = f.read_text(errors="replace")
        for reason, count in Counter(UNRESOLVED.findall(text)).items():
            unresolved[reason] += count
        for header in HEADER_TAGS.findall(text):
            for name, count in HEADER_TAG_ENTRY.findall(header):
                header_tags[name] += int(count)
    return {
        "totals": totals,
        "categories": categories,
        "statuses": statuses,
        "rule_tags": rule_tags,
        "unresolved": unresolved,
        "header_tags": header_tags,
    }


def main():
    old, new = (Path(p) for p in sys.argv[1:3])
    a, b = scan(old), scan(new)
    rows = []
    keys = sorted(set(a["totals"]) | set(b["totals"])
                  | {f"cat:{k}" for k in set(a["categories"]) | set(b["categories"])}
                  | {f"tag:{k}" for k in set(a["rule_tags"]) | set(b["rule_tags"])}
                  | {f"hdr:{k}" for k in set(a["header_tags"]) | set(b["header_tags"])}
                  | {f"unres:{k}" for k in set(a["unresolved"]) | set(b["unresolved"])})
    for key in keys:
        if key.startswith("cat:"):
            va, vb = a["categories"].get(key[4:], 0), b["categories"].get(key[4:], 0)
        elif key.startswith("tag:"):
            va, vb = a["rule_tags"].get(key[4:], 0), b["rule_tags"].get(key[4:], 0)
        elif key.startswith("hdr:"):
            va, vb = a["header_tags"].get(key[4:], 0), b["header_tags"].get(key[4:], 0)
        elif key.startswith("unres:"):
            va, vb = a["unresolved"].get(key[6:], 0), b["unresolved"].get(key[6:], 0)
        else:
            va, vb = a["totals"].get(key, 0), b["totals"].get(key, 0)
        rows.append((key, va, vb, vb - va))
    rows.sort(key=lambda r: -abs(r[3]))
    print(f"{'metric':<40}{'old':>14}{'new':>14}{'delta':>12}")
    for key, va, vb, d in rows:
        print(f"{key:<40}{va:>14,}{vb:>14,}{d:>+12,}")
    print("\nlargest chain-craft totals:",
          "old", f"{a['totals']['maxCrafts']:,}", "new", f"{b['totals']['maxCrafts']:,}")


if __name__ == "__main__":
    main()
