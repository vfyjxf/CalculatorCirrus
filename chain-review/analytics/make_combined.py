#!/usr/bin/env python3
"""Render the six analytics charts as one combined Chinese figure (2 cols x 3 rows)."""
import json
import os

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
FIGS = os.path.join(HERE, "figs")


def main():
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    plt.rcParams["font.sans-serif"] = ["Hiragino Sans GB", "Arial Unicode MS", "DejaVu Sans"]
    plt.rcParams["axes.unicode_minus"] = False

    stats = json.load(open(os.path.join(HERE, "stats.json")))
    z = np.load(os.path.join(HERE, "raw_arrays.npz"))
    pooled_prod = z["pooled_prod"].tolist()
    pooled_inputs = z["pooled_inputs"].tolist()
    per_pack = stats["per_pack"]
    pack_raw = {str(n): z[f"pack__{n}"].tolist() for n in z["pack_names"]}

    fig, axes = plt.subplots(2, 3, figsize=(21, 13.2))

    # ---- 1 histogram ----
    ax = axes[0][0]
    ax.hist(pooled_prod, bins=np.logspace(0, np.log10(max(2, max(pooled_prod))), 40),
            color="#3b7dd8", edgecolor="white")
    ax.set_xscale("log"); ax.set_yscale("log")
    ax.set_xlabel("每个 ingredient 的生产者数（log 轴）")
    ax.set_ylabel("ingredient 条目数（log 轴）")
    ax.set_title("① 生产者数分布（22 包合并，n=%s）" % f"{len(pooled_prod):,}")
    for q, lbl, y in [(50, "中位数", 0.5), (90, "P90", 0.3)]:
        v = np.percentile(pooled_prod, q)
        ax.axvline(v, color="#d9534f", ls="--", lw=1)
        ax.text(v, ax.get_ylim()[1] * y, f" {lbl}={v:.0f}", color="#d9534f", fontsize=9)

    # ---- 2 buckets ----
    ax = axes[0][1]
    buckets = [("0\n(raw 叶)", lambda c: c == 0), ("1\n(唯一源)", lambda c: c == 1),
               ("2–3", lambda c: 2 <= c <= 3), ("4–9", lambda c: 4 <= c <= 9),
               ("10–49", lambda c: 10 <= c <= 49), ("50–199", lambda c: 50 <= c <= 199),
               ("≥200", lambda c: c >= 200)]
    vals = [100.0 * sum(1 for c in pooled_prod if b[1](c)) / len(pooled_prod) for b in buckets]
    bars = ax.bar([b[0] for b in buckets], vals,
                  color=["#d9534f", "#f0ad4e", "#5cb85c", "#3b7dd8", "#7b4bd8", "#d84bb0", "#444444"])
    for bar, v in zip(bars, vals):
        ax.text(bar.get_x() + bar.get_width() / 2, v + 0.4, f"{v:.1f}%", ha="center", fontsize=9)
    ax.set_ylim(0, max(vals) * 1.18)
    ax.set_ylabel("占 ingredient 条目比例 %")
    ax.set_title("② 生产者数分桶（0=只能当原材料 / 1=无选择 / 多=选择空间）")

    # ---- 3 box by pack ----
    ax = axes[0][2]
    names = sorted(per_pack, key=lambda p: -per_pack[p]["producers_per_ingredient"]["median"])
    ax.boxplot([pack_raw[p] for p in names], showfliers=False, patch_artist=True,
               boxprops=dict(facecolor="#cfe0f7"), medianprops=dict(color="#d9534f"))
    ax.set_yscale("log")
    ax.set_xticks(range(1, len(names) + 1))
    ax.set_xticklabels([p[:18] for p in names], rotation=72, ha="right", fontsize=6)
    ax.set_ylabel("生产者数（log 轴）")
    ax.set_title("③ 每包箱线图（按中位数降序；红线=中位数，箱=P25–P75）")

    # ---- 4 top hubs ----
    ax = axes[1][0]
    top = stats["pooled"]["top_hub_items"][:12][::-1]
    labels = [t[0][:40] + (f" …{t[2]}包" if t[2] > 1 else "") for t in top]
    ax.barh(labels, [t[1] for t in top], color="#7b4bd8")
    ax.set_xlabel("合并生产者总数（跨包求和）")
    ax.set_title("④ Top 12 枢纽物品（生产者最多 = 分支爆炸风险点）")
    ax.tick_params(labelsize=8)

    # ---- 5 inputs per recipe ----
    ax = axes[1][1]
    clip = [min(n, 12) for n in pooled_inputs]
    ax.hist(clip, bins=np.arange(0.5, 13.5, 1), color="#5cb85c", edgecolor="white")
    ax.set_xticks(range(1, 13)); ax.set_xticklabels([str(i) for i in range(1, 12)] + ["12+"])
    ax.set_yscale("log")
    ax.set_xlabel("每配方输入数（item+tag，截断 12+）")
    ax.set_ylabel("配方数（log 轴）")
    ax.set_title("⑤ 配方输入数分布（合并 n=%s；9 处=3×3 合成格上限）" % f"{len(pooled_inputs):,}")

    # ---- 6 exclusion funnel ----
    ax = axes[1][2]
    names6 = sorted(per_pack, key=lambda p: -per_pack[p]["total_recipes"])
    kept_v = [per_pack[p]["kept"] for p in names6]
    exc_v = [per_pack[p]["excluded"] for p in names6]
    ax.bar(range(len(names6)), kept_v, label="保留（进入建图）", color="#5cb85c")
    ax.bar(range(len(names6)), exc_v, bottom=kept_v, label="被内置 junk/duplicate 排除", color="#d9534f")
    ax.set_xticks(range(len(names6)))
    ax.set_xticklabels([p[:18] for p in names6], rotation=72, ha="right", fontsize=6)
    ax.set_ylabel("配方数（log 轴）"); ax.set_yscale("log")
    ax.legend(fontsize=8)
    for i, p in enumerate(names6):
        if per_pack[p]["excluded"] > 0:
            ax.text(i, (kept_v[i] + exc_v[i]) * 1.15, f"-{per_pack[p]['excluded']}",
                    ha="center", fontsize=5.5, color="#d9534f", rotation=90)
    ax.set_title("⑥ 每包筛选漏斗：排除量 vs 保留量")

    fig.suptitle("筛选后配方：ingredient 生产者与寻路指标（22 个整合包 · 420,231 条保留配方 · 87,521 个包内 ingredient 条目）",
                 fontsize=16, y=0.995)
    fig.tight_layout(rect=(0, 0, 1, 0.975))
    out = os.path.join(FIGS, "0_combined_charts.png")
    fig.savefig(out, dpi=150)
    print("combined ->", out)


if __name__ == "__main__":
    main()
