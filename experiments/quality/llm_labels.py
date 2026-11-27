"""Phase 0c-1 — optional LLM labeller.  **Stub: it never calls an API.**

The label layer runs out of human judgement long before it runs out of
templates: 17,704 of 240,377 templates (7.4 %) carry a role target, and the
tail is exactly where a model has no supervision.  An LLM could label the
most *uncertain* templates cheaply — but only under a budget that cannot be
exceeded by accident, so this module ships with the call site removed.

What it does today:

* ``--dry-run`` (the default, and the only mode that works) prints the prompts
  it *would* send for the N templates the label layer is least sure about, and
  the estimated cost.
* ``--budget-usd`` is a hard guard: the estimated cost of the batch must fit
  inside it or the run aborts before anything is rendered.
* Without ``--dry-run`` the module raises.  Wiring an API client in is a
  deliberate edit, not a flag.

Uncertainty, in order: a template with no verdict at all is the most
uncertain; then one whose members disagree (low purity), then one whose best
source is a weak tier, then the largest ones (a bigger template is worth more
per answer).
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import pyarrow.parquet as pq

#: Rough price of one labelling call, in USD.  Deliberately pessimistic: the
#: guard must never under-estimate.
COST_PER_TEMPLATE_USD = 0.01

PROMPT = """You are auditing one *template* of crafting recipes from a
Minecraft modpack export.  A template is a group of recipes that are identical
once materials, colours and forms are abstracted away.

signature: {signature}
system: {system}
members: {size} recipes in pack {pack}
examples: {examples}

Answer with JSON only: {{"verdict": "junk"|"useful", "role": "production"|
"conversion"|"recycling"|"generation"|"display"|"unknown", "why": "<= 20 words"}}
"""


def uncertainty(row) -> tuple:
    """Sort key: most uncertain first.  Pure ordering, no thresholds."""
    has_verdict = 1 if row["majority_verdict"] else 0
    return (has_verdict, row["purity"], -row["best_tier"], -row["n_members"])


def pick(out_dir: Path, limit: int) -> list:
    table = pq.read_table(out_dir / "labels" / "template_labels.parquet",
                          columns=["pack", "template_id", "n_members",
                                   "majority_verdict", "purity", "best_tier",
                                   "role_target"])
    rows = table.to_pylist()
    rows.sort(key=uncertainty)
    return rows[:limit]


def render(rows: list, out_dir: Path) -> list:
    """Attach each template's signature and render the prompts."""
    signatures: dict = {}
    prompts = []
    for row in rows:
        pack = row["pack"]
        if pack not in signatures:
            tbl = pq.read_table(out_dir / pack / "templates.parquet")
            signatures[pack] = {t: (s, sy, ex) for t, s, sy, ex in zip(
                tbl.column("template_id").to_pylist(),
                tbl.column("signature").to_pylist(),
                tbl.column("system").to_pylist(),
                tbl.column("examples").to_pylist())}
        sig, system, examples = signatures[pack].get(
            row["template_id"], ("", "", []))
        prompts.append(PROMPT.format(signature=sig, system=system,
                                     size=row["n_members"], pack=pack,
                                     examples=", ".join(examples[:3])))
    return prompts


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Phase 0c-1 LLM labeller (stub)")
    ap.add_argument("--out-dir", default="out")
    ap.add_argument("--limit", type=int, default=20)
    ap.add_argument("--budget-usd", type=float, default=0.0)
    ap.add_argument("--dry-run", action="store_true", default=True)
    ap.add_argument("--no-dry-run", dest="dry_run", action="store_false")
    args = ap.parse_args(argv)

    estimate = args.limit * COST_PER_TEMPLATE_USD
    if estimate > args.budget_usd:
        raise SystemExit(
            f"refusing to run: {args.limit} templates would cost about "
            f"${estimate:.2f}, budget is ${args.budget_usd:.2f}")
    if not args.dry_run:
        raise SystemExit("this module has no API client on purpose; "
                         "only --dry-run is implemented")

    rows = pick(Path(args.out_dir), args.limit)
    prompts = render(rows, Path(args.out_dir))
    print(f"would send {len(prompts)} prompts, about ${estimate:.2f}, "
          f"budget ${args.budget_usd:.2f}\n")
    for row, prompt in zip(rows, prompts):
        print(f"--- {row['pack']} {row['template_id']} "
              f"(members={row['n_members']}, verdict="
              f"{row['majority_verdict'] or 'none'}, purity={row['purity']:.2f})")
        print(prompt)
    print(json.dumps({"templates": len(prompts), "estimated_usd": estimate,
                      "sent": 0}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
