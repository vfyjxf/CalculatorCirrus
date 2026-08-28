# fom batch-005 审查报告
- pack: fom
- 批次: batch-005（written=61；类别分布：incomplete 45 / long 9 / colossal 6 / giant 1）
- 审查链数: 61（incomplete 45 / complete 16；全批仅 61 条，已逐链结构审查 + 全局配方族分布扫描，无抽样遗漏）
- 发现: critical 0 / major 0 / minor 0

## 审查范围与读法
- 全读 `report.txt`、`index.tsv`、`manifest.txt`、`targets.tsv`。
- `chains-incomplete-001.txt`（1.29MB / 45 条）未整读，但做了：(a) 45 条链边界定位；(b) 全部 UNRESOLVED 项提取与去重；(c) 全局 EXECUTE 配方 sys 前缀与 `create:*` 配方族后缀分布；(d) `reason=BUDGET_TRUNCATED|RANK_TRUNCATED`、`crafts>=100000` 定向 grep；(e) jei:/quest/trade/JER/anvil/any-of/potion NBT/free-resource 全量负向扫描。
- `chains-colossal/giant/long` 全读，并抽取 recipe 家族分布。
- 抽样覆盖：incomplete 全 45 条（结构 + 唯一悬项 + 配方族），complete 全 16 条（结构 + 配方族）。

## 发现
无新模式（本轮为最终验证轮，未发现任何超出前四轮已固化规则与已知规划器级问题之外的新可疑配方族 / 目标类）。审查层收敛。

补充说明（均为已知规划器级问题，按 round-5 口径只在链级一句话提及，不计入新发现）：
- 全部 45 条 incomplete 链的唯一悬项高度一致：`create:polished_cut_andesite_stairs|+14`，`reason=RANK_TRUNCATED`，`candidates=5`。`|+14` 属已知 `|+N` id 后缀污染；该物对应的真实配方 `create:polished_cut_andesite_stairs_from_stone_types_andesite` 在图中存在（全批出现 46 次），属 `pattern/exchange` 的 `from_stairs/walls/slabs$` 形状反向环，已被覆盖。规划器因 rank 截断请求了污染变体，并非链本身缺陷。
- `CHAIN 0002` 等存在 `minecraft:raw_iron_block ↔ minecraft:raw_iron` 压缩/解压往返环（A1–A16 重复 16 次，全部带 `[rule-tag: pattern/storage_ladder]`），属已知"已带标签仍无界放大"，无净产出但不计新发现。
- complete 链（netherite 工具、染色玻璃 pane、create_radar:binoculars、toms_storage:basic_inventory_hopper、create 机械件）均为原版/标准 crafting + `create:*` 材料 + andesite 石材切割交换环，无可疑族。

## 链级判定
| chain | category | verdict(ok/questionable/bad) | 一句话 |
| --- | --- | --- | --- |
| 0000 | colossal | ok | toms_storage 箱+create 材料, 标准 |
| 0001 | colossal | ok | 原版下界合金锻造, 标准 |
| 0002 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0003 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0004 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0005 | colossal | ok | 原版下界合金锻造, 标准 |
| 0006 | long | ok | 原版染色玻璃+create 框玻璃, 标准 |
| 0007 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0008 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0009 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0010 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0011 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0012 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0013 | giant | ok | 原版染色玻璃+create 框玻璃, 标准 |
| 0014 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0015 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0016 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0017 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0018 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0019 | long | ok | 原版染色玻璃+create 框玻璃, 标准 |
| 0020 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0021 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0022 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0023 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0024 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0025 | colossal | ok | 原版下界合金锻造, 标准 |
| 0026 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0027 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0028 | long | ok | 原版染色玻璃+create 框玻璃, 标准 |
| 0029 | long | ok | 原版染色玻璃+create 框玻璃, 标准 |
| 0030 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0031 | long | ok | 原版染色玻璃+create 框玻璃, 标准 |
| 0032 | long | ok | 原版染色玻璃+create 框玻璃, 标准 |
| 0033 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0034 | long | ok | 原版染色玻璃+create 框玻璃, 标准 |
| 0035 | long | ok | 原版染色玻璃+create 框玻璃, 标准 |
| 0036 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0037 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0038 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0039 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0040 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0041 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0042 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0043 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0044 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0045 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0046 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0047 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0048 | long | ok | create_radar 铜+玻璃, 标准 |
| 0049 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0050 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0051 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0052 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0053 | colossal | ok | 原版下界合金锻造, 标准 |
| 0054 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0055 | colossal | ok | 原版下界合金锻造, 标准 |
| 0056 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0057 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0058 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0059 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |
| 0060 | incomplete | ok (known) | 唯一悬项 create:polished_cut_andesite_stairs|+14 (reason=RANK_TRUNCATED, +|N 污染, 已知) |

## 规则建议（给汇总者）
- targetRegex: 无新增（本批无可排除的新目标类）。
- recipeRegex: 无新增（主导族 `create:*_from_stone_types_andesite` 已属 `pattern/exchange`；`raw_iron_block↔raw_iron` 已属 `pattern/storage_ladder`）。
- builtin 候选: 无（无跨包通用新候选）。
- 结论：无新模式，审查层收敛。
- 备注（已知规划器级，非本批新引入，供下一轮参考）：本批 `|+|N` 污染集中在 `create:polished_cut_andesite_stairs|+14` 单点，建议规划器侧修复 rank 截断下 `|+|N` 变体回退到真实 `create:polished_cut_andesite_stairs`，可使 45 条 incomplete 全部转 complete；属数据/规划器层，不计入 review 过滤规则。
