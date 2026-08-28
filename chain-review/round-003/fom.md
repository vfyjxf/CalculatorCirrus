# fom batch-003 审查报告
- pack: fom
- 批次: batch-003（written=65；类别分布：incomplete 30 / medium 14 / long 6 / giant 6 / colossal 9）
- 审查链数: 65（全部链，因批量仅 65 条 < 100 配额，故全量审查；incomplete 30 全读 + complete 35 全读）
- 发现: critical 0 / major 0 / minor 1

## 发现
### F-fom-1 | chain=0001(及0045,0002,0003,0004等) | severity=minor | category=other
- 位置: CHAIN 0001 的 A52–A67（连续 17 次 `create:crafting/materials/raw_zinc_block` ↔ `create:crafting/materials/raw_zinc` 互逆执行，均无 `[rule-tag]`）；同族在 CHAIN 0045 A52–A67、CHAIN 0002/0003/0004 亦有。
- 问题: `raw_zinc_block↔raw_zinc` 是 1:9 的方块↔物品对（与已打标 `raw_iron_block`/`raw_copper_block`/`raw_gold_block` 完全同族），但本批未带 `[rule-tag: pattern/storage_ladder]`，因此未被排除，规划器对其做了 17 次无净收益的往返放大。CHAIN 0001 因此 depth=104、actions=126、crafts=223、pending=2；链的真实产物路径（zinc_ingot→andesite_alloy→engine_piston）仅占极少步，绝大部分是存储阶梯噪声。
- 原因: storage_ladder 分类器覆盖不一致——同族 `raw_iron/copper/gold_block` 已打标，而 `raw_zinc_block`/`raw_zinc`（以及 `brass_ingot_from_compacting`/`brass_ingot_from_decompacting`/`brass_block_from_compacting`、`createbigcannons:nethersteel_{nugget,ingot_from_block,block}`、`minecraft:resin_block`↔`resin_clump`）漏标。属"未被任何标签覆盖的可疑配方族"。
- 建议: 将 storage_ladder 规则扩展到 `raw_zinc_block|raw_zinc`、`brass_ingot_from_(de)?compacting|brass_block_from_compacting`、`nethersteel_(nugget|ingot_from_block|block)`、`resin_block|resin_clump` 等 1:9/紧凑方块↔物品↔矿锭对（证据：incomplete 中 raw_zinc 相关 EXECUTE 154 条、brass 相关 48 条、nethersteel 相关 24 条）。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | incomplete | questionable | pending 为 `polished_cut_andesite_stairs|+14`（+N 污染+截断，已知） |
| 0001 | incomplete | questionable | raw_zinc 阶梯未打标放大约17×，depth104；真实路径 OK |
| 0002 | incomplete | questionable | +14 楼梯污染 + 大量 andesite 石切交换环（已打标 exchange） |
| 0003 | incomplete | questionable | +14 楼梯污染；andesite 交换环 |
| 0004 | incomplete | questionable | +14 楼梯污染；`brass_ingot` CASCADE_EXHAUSTED（已知截断） |
| 0005 | long | ok | framed-glass 石切交换环（已打标 pattern/exchange） |
| 0006 | giant | ok | cardboard 存储阶梯（已打标）；raw cone 偏宽 |
| 0007 | giant | ok | bone/lapis 阶梯 + framed-glass 环（已打标） |
| 0008 | giant | ok | 同上，已打标 |
| 0009 | giant | ok | 同上，已打标 |
| 0010 | incomplete | questionable | +14 楼梯污染 |
| 0011 | giant | ok | 已打标 |
| 0012 | incomplete | questionable | depth104 + +14 楼梯污染 |
| 0013 | incomplete | questionable | andesite 宽锥（raw_kinds=29） |
| 0014 | incomplete | questionable | `brass_ingot` CASCADE_EXHAUSTED（已知截断） |
| 0015 | long | ok | experience block↔nugget 阶梯（已打标 storage_ladder） |
| 0016 | incomplete | questionable | +14 楼梯污染 |
| 0017 | medium | ok | 正常 |
| 0018 | medium | ok | 正常 |
| 0019 | colossal | ok | storage_ladder×34（已打标） |
| 0020 | incomplete | questionable | raw7 宽锥 |
| 0021 | medium | ok | 正常 |
| 0022 | medium | ok | 正常 |
| 0023 | colossal | ok | 已打标 |
| 0024 | medium | ok | 正常 |
| 0025 | incomplete | questionable | `cut_tuff_bricks|+14` 污染致未解析 |
| 0026 | incomplete | questionable | +14 楼梯污染 |
| 0027 | long | ok | resin_block↔resin_clump 阶梯未打标但为 1:9 已知族 |
| 0028 | colossal | ok | 已打标 |
| 0029 | colossal | ok | 背包升级合法；用 exotic 木做 chest（+N 污染，已知） |
| 0030 | long | ok | resin 阶梯 |
| 0031 | incomplete | questionable | andesite 宽锥 |
| 0032 | long | ok | resin 阶梯 |
| 0033 | colossal | ok | storage_ladder×50（已打标） |
| 0034 | long | ok | resin 阶梯 |
| 0035 | colossal | ok | 已打标 |
| 0036 | medium | ok | chest 用 exotic 木（`crimson_planks|+13`，+N 污染已知） |
| 0037 | medium | ok | 同上 |
| 0038 | incomplete | questionable | 常规 incomplete |
| 0039 | medium | ok | 正常 |
| 0040 | medium | ok | 正常 |
| 0041 | medium | ok | 正常 |
| 0042 | medium | ok | 正常 |
| 0043 | medium | ok | 正常 |
| 0044 | medium | ok | 正常 |
| 0045 | incomplete | questionable | raw10 最宽锥；raw_zinc 阶梯未打标放大约17× |
| 0046 | medium | ok | 正常 |
| 0047 | incomplete | questionable | 常规 incomplete |
| 0048 | incomplete | questionable | 常规 incomplete |
| 0049 | colossal | ok | 用 anvil 作输入（SB anvil_upgrade 合法配方） |
| 0050 | incomplete | questionable | 常规 incomplete |
| 0051 | incomplete | questionable | 常规 incomplete |
| 0052 | colossal | ok | 合法；raw_kinds=49 锥噪声（全木种） |
| 0053 | incomplete | questionable | 常规 incomplete |
| 0054 | incomplete | questionable | 常规 incomplete |
| 0055 | colossal | ok | smithing_table 作消耗（SB smithing_upgrade 合法） |
| 0056 | incomplete | questionable | +14 羊毛交叉环（黑/蓝 wool 互喂，已知+N污染） |
| 0057 | incomplete | questionable | +14 羊毛污染 |
| 0058 | incomplete | questionable | 常规 incomplete |
| 0059 | giant | ok | raw_iron/gunpowder 阶梯（已打标）×16 |
| 0060 | incomplete | questionable | +14 羊毛污染 |
| 0061 | incomplete | questionable | +14 羊毛污染 |
| 0062 | incomplete | questionable | +14 羊毛污染 |
| 0063 | incomplete | questionable | +14 羊毛污染 |
| 0064 | incomplete | questionable | +14 楼梯污染 |

## 规则建议（给汇总者）
- targetRegex: 本批无需新增 target 排除（`decompress` 已在批次 blacklist）。
- recipeRegex:
  - `raw_zinc_block|raw_zinc|raw_copper_block|raw_copper`（补齐 storage_ladder；raw_copper 部分已打标但 raw_zinc 漏标）—— 证据：incomplete 中 raw_zinc 系列 EXECUTE 154 条、单链放大约17×。
  - `brass_ingot_from_compacting|brass_ingot_from_decompacting|brass_block_from_compacting|nethersteel_nugget|nethersteel_ingot_from_block|nethersteel_block|resin_block|resin_clump` —— 同属 1:9/紧凑块↔物↔锭 存储阶梯，证据：brass 48 条、nethersteel 24 条、resin 长链 4 条。
- builtin 候选:
  - **最优先**：在图谱构建期剥离物品 id 的 `|\+N` 后缀（`+1/+12/+13/+14` 等）。本批 37 处 `[UNRESOLVED]` 全部携带 `+N` 后缀（`polished_cut_andesite_stairs|+14`、`black_wool|+14`、`cut_tuff_bricks|+14` 等），且大量 EXECUTE 输入侧也带 `+N`，造成"产出的无后缀物"与"需要的带后缀物"不匹配的伪未解析与交叉环（如 0056 黑/蓝 wool 互喂）。归一化 id 后可消除绝大多数 incomplete 的 pending=1/2。属 `data-pollution`（已知规划器级问题，但本批影响面最大，故提为最高优先规则建议）。
  - **其次**：对 Create `stone_types_andesite` 等变体石切族提升 RANK 预算或显式打 `pattern/exchange`——当前因 `RANK_TRUNCATED`/`CASCADE_EXHAUSTED` 导致 `polished_cut_andesite_stairs`、部分 `brass_ingot` 在 incomplete 链中未解析（已知"原版常见物预算截断"）。
- 无其它新模式（无 fake-production、无 jei: 展示页、无 amount-insanity、无未覆盖的假生产/绕路）。
