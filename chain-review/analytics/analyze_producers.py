#!/usr/bin/env python3
"""Producer/pathfinding analytics over filtered recipes, per pack + pooled.

Input:
  cirrus-audit-data/<pack>/producers.jsonl   (one line per (recipe, output item))
  CalculatorCirrus/Mod/src/main/resources/assets/calculator_cirrus/classification/builtin_rules.json

"Filtered" = recipes NOT matching any builtin exclusion rule (quality/junk,
quality/duplicate) — the recipes the routing graph is built from. Advisory
tags (pattern/*, intent/*) do not exclude; per-pack review filters
(filters.json) only mark convergence — neither is applied here.

Output: analytics/stats.json + analytics/figs/*.png
"""
import json
import os
import re
from collections import Counter, defaultdict

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))  # CalculatorCirrus
AUDIT = os.path.abspath(os.path.join(ROOT, "..", "cirrus-audit-data"))
# v2 rule set as of HEAD — the exact rules every reviewed chain batch was generated with.
# (The working tree has an in-progress v3 refactor under assets/calculator_cirrus/rules/;
#  deliberately not used here.)
RULES = os.path.join(HERE, "input_builtin_rules_v2.json")


class Rule:
    def __init__(self, obj):
        self.id = obj["id"]
        self.tags = obj.get("tags", [])
        self.system = obj.get("system")
        self.system_re = re.compile(obj["systemPattern"]) if "systemPattern" in obj else None
        self.rid_re = re.compile(obj["recipeIdPattern"]) if "recipeIdPattern" in obj else None
        self.path_re = re.compile(obj["recipePathPattern"]) if "recipePathPattern" in obj else None
        self.mod = obj.get("mod")

    @property
    def excludes(self):
        return any(t in ("cirrus:quality/junk", "cirrus:quality/duplicate") for t in self.tags)

    def match(self, sysid, rid):
        if self.system is not None and self.system != sysid:
            return False
        if self.system_re is not None and not self.system_re.search(sysid or ""):
            return False
        path = rid.split(":", 1)[1] if ":" in rid else rid
        if self.rid_re is not None and (not rid or not self.rid_re.search(rid)):
            return False
        if self.path_re is not None and (not rid or not self.path_re.search(path)):
            return False
        if self.mod is not None and (":" not in rid or rid.split(":", 1)[0] != self.mod):
            return False
        return True


def summarize(counts):
    a = np.array(counts, dtype=float)
    if not a.size:
        return {"n": 0}
    return {
        "n": int(a.size), "min": int(a.min()), "max": int(a.max()),
        "mean": round(float(a.mean()), 2), "median": float(np.median(a)),
        "p25": round(float(np.percentile(a, 25)), 2),
        "p75": round(float(np.percentile(a, 75)), 2),
        "p90": round(float(np.percentile(a, 90)), 2),
        "p99": round(float(np.percentile(a, 99)), 2),
    }


def main():
    rules = [r for r in (Rule(o) for o in json.load(open(RULES))["rules"]) if r.excludes]
    packs = sorted(d for d in os.listdir(AUDIT)
                   if os.path.isfile(os.path.join(AUDIT, d, "producers.jsonl")))

    per_pack, pack_raw = {}, {}
    pooled_prod, pooled_inputs, pooled_outputs = [], [], []
    pooled_tag_entries = pooled_item_entries = 0
    hub_counter, hub_pack_presence, all_recipes_total = Counter(), Counter(), 0

    for pack in packs:
        jsonl = os.path.join(AUDIT, pack, "producers.jsonl")
        recipes = {}  # (sys,id) -> {"outs": set, "n_in", "n_tag"}
        with open(jsonl) as f:
            for line in f:
                row = json.loads(line)
                key = (row.get("sys", ""), row.get("id", ""))
                r = recipes.get(key)
                if r is None:
                    ins = row.get("in", [])
                    r = recipes[key] = {"outs": set(), "n_in": len(ins),
                                        "n_tag": sum(1 for i in ins if i[0] == "t")}
                for o in row.get("outs", []):
                    r["outs"].add(o)
        all_recipes_total += len(recipes)

        excluded, rule_hits = set(), Counter()
        for key in recipes:
            for rule in rules:
                if rule.match(key[0], key[1]):
                    excluded.add(key)
                    rule_hits[rule.id] += 1
                    break
        kept = {k: v for k, v in recipes.items() if k not in excluded}

        producers = defaultdict(set)
        for key, r in kept.items():
            for o in r["outs"]:
                producers[o].add(key)

        ingredients = set()
        with open(jsonl) as f:
            for line in f:
                row = json.loads(line)
                key = (row.get("sys", ""), row.get("id", ""))
                if key in excluded:
                    continue
                for i in row.get("in", []):
                    if i[0] == "i":
                        ingredients.add(i[1])
                        pooled_item_entries += 1
                    elif i[0] == "t":
                        pooled_tag_entries += 1

        prod_counts = [len(producers.get(ing, ())) for ing in ingredients]
        pooled_prod.extend(prod_counts)
        pack_raw[pack] = prod_counts
        for ing in ingredients:
            hub_counter[ing] += len(producers.get(ing, ()))
            if producers.get(ing):
                hub_pack_presence[ing] += 1

        inputs_per_recipe = [r["n_in"] for r in kept.values()]
        outputs_per_recipe = [len(r["outs"]) for r in kept.values()]
        pooled_inputs.extend(inputs_per_recipe)
        pooled_outputs.extend(outputs_per_recipe)
        n_ing = max(1, len(prod_counts))
        per_pack[pack] = {
            "total_recipes": len(recipes), "excluded": len(excluded), "kept": len(kept),
            "rule_hits": dict(rule_hits.most_common(6)),
            "ingredients": len(prod_counts),
            "producers_per_ingredient": summarize(prod_counts),
            "pct_raw_leaf": round(100.0 * sum(1 for c in prod_counts if c == 0) / n_ing, 1),
            "pct_single_source": round(100.0 * sum(1 for c in prod_counts if c == 1) / n_ing, 1),
            "inputs_per_recipe": summarize(inputs_per_recipe),
            "outputs_per_recipe": summarize(outputs_per_recipe),
            "pct_byproduct_recipes": round(100.0 * sum(1 for n in outputs_per_recipe if n >= 2) / max(1, len(outputs_per_recipe)), 1),
            "pct_tag_inputs": round(100.0 * sum(r["n_tag"] for r in kept.values()) / max(1, sum(r["n_in"] for r in kept.values())), 1),
            "top_hubs": sorted(((i, len(s)) for i, s in producers.items()), key=lambda kv: -kv[1])[:8],
        }

    pooled = {
        "packs": len(packs), "recipes_total": all_recipe_rows_safe(all_recipes_total),
        "producers_per_ingredient": summarize(pooled_prod),
        "pct_raw_leaf": round(100.0 * sum(1 for c in pooled_prod if c == 0) / max(1, len(pooled_prod)), 1),
        "pct_single_source": round(100.0 * sum(1 for c in pooled_prod if c == 1) / max(1, len(pooled_prod)), 1),
        "inputs_per_recipe": summarize(pooled_inputs),
        "outputs_per_recipe": summarize(pooled_outputs),
        "pct_tag_inputs": round(100.0 * pooled_tag_entries / max(1, pooled_tag_entries + pooled_item_entries), 1),
        "pct_byproduct_recipes": round(100.0 * sum(1 for n in pooled_outputs if n >= 2) / max(1, len(pooled_outputs)), 1),
        "top_hub_items": [[i, c, hub_pack_presence[i]] for i, c in hub_counter.most_common(20)],
    }
    json.dump({"pooled": pooled, "per_pack": per_pack},
              open(os.path.join(HERE, "stats.json"), "w"), ensure_ascii=False, indent=1)
    make_charts(packs, per_pack, pack_raw, pooled_prod, pooled_inputs)
    print("packs:", len(packs), "| pooled ingredients:", len(pooled_prod),
          "| pooled median producers:", pooled["producers_per_ingredient"]["median"])


def all_recipe_rows_safe(n):
    return int(n)


def make_charts(packs, per_pack, pack_raw, pooled_prod, pooled_inputs):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    plt.rcParams["font.sans-serif"] = ["Hiragino Sans GB", "Arial Unicode MS", "DejaVu Sans"]
    plt.rcParams["axes.unicode_minus"] = False
    figs = os.path.join(HERE, "figs")
    os.makedirs(figs, exist_ok=True)

    # 1 pooled histogram log-log
    fig, ax = plt.subplots(figsize=(8, 4.5))
    ax.hist(pooled_prod, bins=np.logspace(0, np.log10(max(2, max(pooled_prod))), 40),
            color="#3b7dd8", edgecolor="white")
    ax.set_xscale("log"); ax.set_yscale("log")
    ax.set_xlabel("每个 ingredient 的生产者数（log 轴）"); ax.set_ylabel("ingredient 条目数（log 轴）")
    ax.set_title(f"筛选后 ingredient 生产者数分布（22 包合并，n={len(pooled_prod):,}）")
    for q, lbl, y in [(50, "中位数", 0.5), (90, "P90", 0.3)]:
        v = np.percentile(pooled_prod, q)
        ax.axvline(v, color="#d9534f", ls="--", lw=1)
        ax.text(v, ax.get_ylim()[1] * y, f" {lbl}={v:.0f}", color="#d9534f", fontsize=9)
    fig.tight_layout(); fig.savefig(os.path.join(figs, "1_producer_histogram.png"), dpi=150); plt.close(fig)

    # 2 buckets
    buckets = [("0\n(raw 叶)", lambda c: c == 0), ("1\n(唯一源)", lambda c: c == 1),
               ("2–3", lambda c: 2 <= c <= 3), ("4–9", lambda c: 4 <= c <= 9),
               ("10–49", lambda c: 10 <= c <= 49), ("50–199", lambda c: 50 <= c <= 199),
               ("≥200", lambda c: c >= 200)]
    vals = [100.0 * sum(1 for c in pooled_prod if b[1](c)) / len(pooled_prod) for b in buckets]
    fig, ax = plt.subplots(figsize=(8, 4.4))
    bars = ax.bar([b[0] for b in buckets], vals,
                  color=["#d9534f", "#f0ad4e", "#5cb85c", "#3b7dd8", "#7b4bd8", "#d84bb0", "#444444"])
    for bar, v in zip(bars, vals):
        ax.text(bar.get_x() + bar.get_width() / 2, v + 0.4, f"{v:.1f}%", ha="center", fontsize=9)
    ax.set_ylabel("占 ingredient 条目比例 %"); ax.set_ylim(0, max(vals) * 1.18)
    ax.set_title("生产者数分桶（寻路视角：0=只能当原材料 / 1=无选择 / 多=选择空间与分支爆炸）")
    fig.tight_layout(); fig.savefig(os.path.join(figs, "2_producer_buckets.png"), dpi=150); plt.close(fig)

    # 3 per-pack box
    names = sorted(per_pack, key=lambda p: -per_pack[p]["producers_per_ingredient"]["median"])
    fig, ax = plt.subplots(figsize=(9.5, 5))
    ax.boxplot([pack_raw[p] for p in names], showfliers=False, patch_artist=True,
               boxprops=dict(facecolor="#cfe0f7"), medianprops=dict(color="#d9534f"))
    ax.set_yscale("log")
    ax.set_xticks(range(1, len(names) + 1))
    ax.set_xticklabels([p[:24] for p in names], rotation=60, ha="right", fontsize=7)
    ax.set_ylabel("生产者数（log 轴）")
    ax.set_title("每包 ingredient 生产者数箱线图（按中位数降序；红线=中位数，箱=P25–P75）")
    fig.tight_layout(); fig.savefig(os.path.join(figs, "3_box_by_pack.png"), dpi=150); plt.close(fig)

    # 4 top hubs
    stats = json.load(open(os.path.join(HERE, "stats.json")))
    top = stats["pooled"]["top_hub_items"][:15][::-1]
    fig, ax = plt.subplots(figsize=(9, 5.8))
    labels = [t[0][:46] + (f" …{t[2]}包" if t[2] > 1 else "") for t in top]
    ax.barh(labels, [t[1] for t in top], color="#7b4bd8")
    ax.set_xlabel("合并生产者总数（跨包求和）")
    ax.set_title("Top 15 枢纽物品（生产者最多 = 寻路分支爆炸风险点）")
    fig.tight_layout(); fig.savefig(os.path.join(figs, "4_top_hubs.png"), dpi=150); plt.close(fig)

    # 5 inputs per recipe
    fig, ax = plt.subplots(figsize=(8, 4.2))
    clip = [min(n, 12) for n in pooled_inputs]
    ax.hist(clip, bins=np.arange(0.5, 13.5, 1), color="#5cb85c", edgecolor="white")
    ax.set_xticks(range(1, 13)); ax.set_xticklabels([str(i) for i in range(1, 12)] + ["12+"])
    ax.set_yscale("log")
    ax.set_xlabel("每配方输入数（item+tag，截断至 12+）"); ax.set_ylabel("配方数（log 轴）")
    ax.set_title(f"筛选后配方输入数分布（合并 n={len(pooled_inputs):,} 配方）")
    fig.tight_layout(); fig.savefig(os.path.join(figs, "5_inputs_per_recipe.png"), dpi=150); plt.close(fig)

    # 6 exclusion funnel
    names6 = sorted(per_pack, key=lambda p: -per_pack[p]["total_recipes"])
    kept_v = [per_pack[p]["kept"] for p in names6]
    exc_v = [per_pack[p]["excluded"] for p in names6]
    fig, ax = plt.subplots(figsize=(9.5, 5))
    ax.bar(range(len(names6)), kept_v, label="保留（进入建图）", color="#5cb85c")
    ax.bar(range(len(names6)), exc_v, bottom=kept_v, label="被内置 junk/duplicate 排除", color="#d9534f")
    ax.set_xticks(range(len(names6)))
    ax.set_xticklabels([p[:24] for p in names6], rotation=60, ha="right", fontsize=7)
    ax.set_ylabel("配方数（log 轴）"); ax.set_yscale("log")
    ax.legend()
    for i, p in enumerate(names6):
        if per_pack[p]["excluded"] > 0:
            ax.text(i, (kept_v[i] + exc_v[i]) * 1.15, f"-{per_pack[p]['excluded']}",
                    ha="center", fontsize=6, color="#d9534f", rotation=90)
    ax.set_title("每包筛选漏斗：内置规则排除量 vs 保留量（log 轴）")
    fig.tight_layout(); fig.savefig(os.path.join(figs, "6_exclusion_funnel.png"), dpi=150); plt.close(fig)

    print("charts ->", figs)


if __name__ == "__main__":
    main()
