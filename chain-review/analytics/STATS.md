# 筛选后配方：ingredient 生产者与寻路指标统计（2026-08-30）

- 数据：22 包 `producers.jsonl`（由各包 recipes 导出构建）+ **v2 内置规则快照**
  （`input_builtin_rules_v2.json` = HEAD `bc570dd` 的 34 条规则，即全部审查批次实际生效的规则集）
- "筛选后" = 排除命中 `quality/junk`、`quality/duplicate` 的配方（planner 建图口径）；
  advisory 标签与审查过滤器不影响建图，不计入
- 脚本：`analyze_producers.py`；图表：`figs/`；原始数：`stats.json`
- 总量：**420,231 条保留配方**（排除 7,453 条），**87,521 个包内 ingredient 条目**

## 1. 核心指标：每个 ingredient 的生产者数（pooled）

| 指标 | 值 |
|---|---|
| 中位数 | **2** |
| 均值 | 2.71 |
| P25 / P75 / P90 / P99 | 1 / 3 / **5** / **20** |
| 最少 / 最多 | 0 / **2,125**（`extended_industrialization:canned_food`，仅 atm10） |
| **0 生产者（raw 叶）** | **11.8%** —— planner 只能当原材料要 |
| **恰好 1 生产者（无选择）** | **32.0%** —— 近三分之一 ingredient 是独源 |
| 2–3 生产者 | 37.1%（主群体） |
| ≥10 生产者 | 3.2%（分支爆炸区） |

→ 寻路含义：一半以上 ingredient 只有 ≤2 条获取路径，选择空间集中在少数枢纽物品；
44% 的 ingredient（0 或 1 生产者）对 planner 而言没有选择余地。

## 2. 每包差异（`figs/3_box_by_pack.png`）

| 包 | 中位数 | 均值 | 最大 | raw 叶% | 独源% | tag 输入% | 保留配方 |
|---|---|---|---|---|---|---|---|
| Brick's Building 1.4.1 | **4** | 5.63 | 137 | 8.5 | 5.7 | 22.9 | 16,943 |
| CreateCustomised1.1.2 | 3 | 4.31 | 42 | 10.4 | 7.4 | 30.8 | 10,145 |
| atm10 | 3 | 4.20 | 2,125 | 4.6 | 8.9 | 24.2 | 134,840 |
| cobblemon-x-create | 3 | 4.65 | 106 | 13.8 | 9.8 | 16.4 | 14,614 |
| create-oneblock / create-train | 3 | 5.15/4.89 | 98/920 | ~11 | ~6 | ~25 | 9,871 / 43,439 |
| atm10-lite | 2 | 2.24 | 203 | 5.9 | 30.4 | 30.4 | 13,445 |
| All Of Create 2.1 | 1 | 1.69 | 21 | 21.3 | 36.7 | 37.8 | 13,744 |
| BlockFront / LuckyBlockChallenge（纯原版） | 1 | 1.05 | 10 | **34.8** | 45.1 | 20.4 | 1,277 |
| enigmatica10 | 1 | 1.70 | 274 | 8.5 | **54.2** | 34.3 | 33,412 |
| minecolonies-dimensional-adventure | 1 | 1.38 | 221 | 12.2 | **66.1** | 22.5 | 41,621 |
| tfc-enhanced | 1 | 0.91 | 36 | **45.9** | 42.5 | 15.7 | 4,341 |

- 原版包（BlockFront/LBC）与 TFC 的 raw 叶占比最高（35–46%）：大量自然物/农产品无生产配方
- minecolonies 66% 独源：装饰变体海量、一物一配方，天然"无选择"
- Create 系包中位数 3–4：机械多路径，选择空间最大

## 3. 枢纽物品（生产者 Top，`figs/4_top_hubs.png`）

| 物品 | 合并生产者数 | 出现包数 |
|---|---|---|
| minecraft:lingering_potion / splash_potion | 2,598 | 14 |
| extended_industrialization:canned_food | 2,125 | 1（atm10） |
| minecraft:bone_meal | 1,751 | 22 |
| minecraft:potion | 1,512 | 15 |
| minecraft:enchanted_book | 1,350 | 10 |
| minecraft:charcoal | 1,005 | 12 |
| minecraft:bucket | 995 | 18 |

药水族（potion/splash/lingering，任意 NBT 组合各成"配方"）与 bone_meal 是跨包最普遍的
分支爆炸点；anvil 泄漏排除后 enchanted_book 仍有 1,350 个生产者（附魔组合）。

## 4. 配方形态指标

| 指标 | 值 | 寻路含义 |
|---|---|---|
| 每配方输入数 | 中位 2 / P75 6 / P90=**P99=9**（3×3 合成格上限）/ max 55 | 长尾>9 的多为机械多槽配方 |
| 每配方输出物品种类 | 中位 1 / P99 2 / **1.6% 配方有杂产** | impure 风险面很小 |
| tag 输入占比 | **25.5%** | 四分之一输入是 any-of 标签 → 展开是预算大头 |
| 单配方最大输出种类 | 11,463 | 泛化 any-of 输出（slab_to_block 类）仍是数据级风险 |

## 5. 筛选漏斗（`figs/6_exclusion_funnel.png`）

- 排除总量 7,453/427,684（1.7%）；主力规则：`jei_vanilla_mirrors` 6,221、
  `anvil_leak_recipes` 814、`trades` 332
- 排除最多：atm10 1,077、enigmatica10 748、minecolonies 676、civilization-isolaria 673

## 6. 图表清单

| 文件 | 内容 |
|---|---|
| `figs/0_combined_charts.png` | **六图合一总览**（`make_combined.py` 渲染） |
| `figs/1_producer_histogram.png` | 生产者数 log-log 直方图（标中位数/P90） |
| `figs/2_producer_buckets.png` | 0/1/2–3/4–9/10–49/50–199/≥200 分桶占比 |
| `figs/3_box_by_pack.png` | 每包箱线图（log 轴，按中位数降序） |
| `figs/4_top_hubs.png` | Top 15 枢纽物品 |
| `figs/5_inputs_per_recipe.png` | 每配方输入数分布（注意 9 处的 3×3 格尖峰） |
| `figs/6_exclusion_funnel.png` | 每包排除/保留漏斗 |

## 备注

- 工作区存在未提交的规则系统 v3 重构（`assets/calculator_cirrus/rules/builtin_rules.json`，
  schema `{id,label,stage,when,then}`）。本统计刻意使用 HEAD 的 v2 快照以匹配全部已审查
  批次的实际筛选口径。**迁移核对**：v3 文件中 `storage_compression_ladders` 与
  `mineral_dye_sources` 两条未见对应规则（anvil_system_recipes 已在）——若为遗漏请补。
