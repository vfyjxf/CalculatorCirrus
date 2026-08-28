# All Of Create 2.1 batch-001 审查报告
- pack: All Of Create 2.1
- 批次: batch-001（written=150；类别分布：incomplete 120 / colossal 12 / giant 12 / long 4 / medium 2）
- 审查链数: 150（incomplete 120 / colossal 12 / giant 12 / long 4 / medium 2；全量，无抽样——各文件均低于 100KB/条阈值，incomplete 5MB 但 40KB/条亦远低于阈值，故全量过）
- 发现: critical 0 / major 2 / minor 0

## 审查方法与口径遵守
- 全读 medium(2)、long(4)、colossal(12)、giant(12)；incomplete 用定向 grep 抽取链边界、UNRESOLVED、`|+N` 畸形 id、steel 往返环等信号并逐链核对证据。
- 已内置规则未重复报告：链头 `builtin-rule-tags (pattern/exchange xN)` 与 exchange/recolor/recycling 步骤跳过；`+raw N` 叶子视为正常原材料；`loop=allowBoundedAmplification` 下单向放大（如 `create_the_air_wars` 的 redstone_block↔redstone、titanium/raw_copper 压缩放大出现在完整链中）视为声明策略，**未**判问题。
- `jei:` 命名空间：本批无任何 `jei:` EXECUTE、无 `jei:/synthetic`、无 `@ jei:` 机器误配（machines 仅 `minecraft:crafting_table`/`minecraft:smithing_table`），故无 fake-production / machine-mismatch。
- 无 free-resource（全批无空 `in :` 的 EXECUTE）、无 amount-insanity（最大 crafts=3137，未达百万级）。

## 发现
### F-AOC21-1 | chain=多链（详见下） | severity=major | category=data-pollution
- 位置: 全批 item id 携带 `|<int>` 后缀（如 `createframed:black_stained_framed_glass|+4`、`create:layered_andesite|+14`、`create_ironworks:steel_ingot|+5`、`minecraft:black_wool|+14`）
- 问题: 物品 id 被序列化进了块状态/属性数字后缀，产生一批"幽灵物品"，永远无生产者。证据：
  - 原版物品不可能有此类后缀，确证为数据损坏：`minecraft:black_wool|+14`（UNRESOLVED 5 条）、`minecraft:trapped_chest|+1`（1 条）、`minecraft:dark_oak_slab|+27`（2 条）。
  - UNRESOLVED 分布：全批 299 条 UNRESOLVED 中 **200 条**带 `|+N`；去重高频：`createframed:black_stained_framed_glass|+4`(85)、`create:layered_andesite|+14`(77)、`create_ironworks:steel_ingot|+5`(23)、`minecraft:black_wool|+14`(5) 等。
  - 跨文件命中：incomplete 47 个唯一 `|+N` id / 5201 次；colossal 12 / 218；giant 11 / 73；long 7 / 29。**所有类别文件均受影响**，complete 链中 `|+N` 作为配方输入 id 出现（说明图内存在产出该变体的配方，双向污染）。
  - 典型链内证据：CHAIN 0000 行 819-826 的 `[UNRESOLVED] createframed:black_stained_framed_glass|+4 ...` 与其 `[RAW]` 行（行 819）中正常 `createframed:black_stained_framed_glass x88` 并存——同一物理物品被拆成两个 id，后者永远不可解。
- 原因: 数据/解析层把块状态或 NBT 属性以 `|\+<int>` 拼接到 item id，既污染目标请求也污染配方产出，导致大量本可解析物品被判 UNRESOLVED，虚增 incomplete 数量（120 条 incomplete 几乎全部被此污染抬高 pending）。
- 建议: 在分类/归一化阶段加一条 `data/normalize-blockstate-suffix` 规则，剥离 item id 末尾的 `|\+[0-9]+`（或映射回基础 id），再重跑批次；可在多包复用的 builtin 候选。注：其中原版 id 的 `|+N` 污染属 critical 级（明显可解析却 unresolved），整批系统性发现按 major 计。

### F-AOC21-2 | chain=0064（为主，另 0124/0106/0126/0075/0043） | severity=major | category=loop-or-cycle
- 位置: CHAIN 0064，`createnuclear:crafting/steel_nugget_from_decompacting`（A1,A3,A5…共 31 次）与 `tfmg:crafting/materials/steel_ingot_from_compacting`（A2,A4,A6…共 31 次）互逆往返，行 24230-24312 起重复 A1→A21+。
- 问题: 钢 ingot↔nugget 零净产出往返环被重复执行。每一步 `steel_ingot(8) → steel_nugget(72) → steel_ingot(8)`，净产出为零；链 0064 中该环占 62/292 个动作（约 21%）。更严重的是**跨 mod 混用**：输入 `create_ironworks:steel_ingot|+5`（创建铁工）→ 产出 `createnuclear:steel_nugget` → 输入 `tfmg:steel_nugget|+4` → 产出 `tfmg:steel_ingot`，把 create_ironworks / createnuclear / tfmg 三种 mod 的钢当成等价物用 decompact/compact 互转，本质上是一段无意义的循环。
- 原因: decompact/compact 配方被当作跨 mod 钢的等价转换，叠加 F-AOC21-1 的 `|+N` 畸形 id，形成无净产出往返环；规划器未对该"压缩↔解压"互逆 no-op 做去环保护。
- 建议: 对该钢 decompact/compact 配方族加 cycle-guard（标记 `.*steel_nugget_from_decompacting|.*steel_ingot_from_compacting` 为环敏感，禁止零净产出往返）；并先修 F-AOC21-1 的 id 归一后再评估是否仍有真实需求。其他命中链：0124（36 次）、0106、0126、0075、0043（见链级判定）。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0064 | incomplete | bad | steel 零净产出环 62 次 + 跨 3 mod 钢混用 + `|+N` 污染 |
| 0124 | incomplete | bad | steel 往返环 36 次（create:netherite_diving_boots） |
| 0043 | incomplete | questionable | steel 往返环（createpropulsion:liquid_burner） |
| 0075 | incomplete | questionable | steel 往返环（garnished:warden_hatchet） |
| 0106 | incomplete | questionable | steel 往返环（tfmg:red_multimeter） |
| 0126 | incomplete | questionable | steel 往返环（tfmg:traffic_light） |
| 0000,0001,0002,0003,0004,0005,0006,0007,0008,0009,0010,0011,0012,0013,0014,0015,0016,0020,0021,0022,0023,0024,0025,0026,0027,0029,0030,0031,0033,0035,0036,0037,0038,0040,0041,0042,0044,0045,0047,0048,0049,0050,0052,0053,0054,0055,0056,0057,0058,0059,0060,0061,0062,0065,0066,0067,0068,0070,0071,0072,0073,0074,0077,0079,0080,0081,0082,0083*,0084,0085*,0086,0087,0088,0089,0090,0091,0092,0093*,0094,0095,0096,0097,0098*,0099,0100,0101*,0102,0103*,0104*,0105,0107,0108,0109*,0110,0111,0112,0113,0114,0115,0116,0117*,0118*,0119,0120,0121*,0122,0123,0125*,0127,0128,0129,0130,0131,0132,0133,0134,0135*,0136,0137*,0138*,0139,0140,0141*,0142*,0143,0144*,0145,0146,0147,0148,0149（其余 114 条 incomplete） | incomplete | questionable | 普遍含 `|+N` 畸形 id 致 UNRESOLVED，目标仍可达，pending 被虚增 |
| 0018,0019,0028,0032,0039,0093,0103,0104,0109,0121,0125,0135（colossal 12） | colossal | ok | pending=0；部分步骤含 `|+N` 输入 id（数据污染但可解析） |
| 0034,0046,0051,0063,0069,0085,0098,0118,0138,0141,0142,0144（giant 12） | giant | ok | pending=0；createfood batter 系列各为目标独立配方，无更优路径；部分含 `|+N` 输入 id |
| 0083,0101,0117,0137（long 4） | long | ok | pending=0 |
| 0017,0078（medium 2） | medium | ok | 正常，压缩放大属允许范围 |

（注：带 * 的链在完整文件中作为 `|+N` 输入 id 出现，仍 pending=0 故判 ok；0063 `create_the_air_wars:efssdfsdf` 命名怪异但为 mod 内真实 minecraft:crafting 配方，非假生产，不报。）

## 规则建议（给汇总者）
- targetRegex: 无。目标名怪异（如 `create_the_air_wars:efssdfsdf`、`:l`）实为 mod 内真实配方，不宜按名排除。
- recipeRegex: `.*steel_nugget_from_decompacting|.*steel_ingot_from_compacting` —— 标记收敛为环敏感配方族，禁止零净产出往返（证据：6 链、122+ 次命中：0064×62、0124×36、0106、0126、0075、0043）。
- builtin 候选: `data/normalize-blockstate-suffix`（标签 junk/duplicate-ish 归一化）——剥离 item id 末尾 `|\+[0-9]+` 映射回基础 id；跨包通用，证据：本批 47 唯一 `|+N` id / 5521 次命中，且原版物品 `minecraft:black_wool|+14`、`minecraft:trapped_chest|+1`、`minecraft:dark_oak_slab|+27` 被污染，证明为解析层块状态序列化错误。
- 无新模式时写"无"：除上述外，未检出 fake-production / free-resource / amount-insanity / impure-recipe / worse-alternative / silly-detour / tag-expansion-overreach / catalyst-misuse / generation-abuse / machine-mismatch 的实据。
