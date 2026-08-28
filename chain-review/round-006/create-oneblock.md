# create-oneblock batch-005 审查报告（选路质量深审 / round-6）
- pack: create-oneblock
- 批次: batch-005（written=64，全部 `incomplete`；depthBucket 分布：colossal 居多，少量 mega-wide / giant / long）
- 审查链数: 64（覆盖全部 64 条；incomplete 大文件 `chains-incomplete-001.txt` 约 40k 行，用「非标签 EXECUTE 全量聚合 + 链边界抽样」交叉核对 producers 索引）
- 方法: 提取所有非 `rule-tag`（exchange/recolor/storage_ladder/recycling）的 EXECUTE 步骤，对每条中间品 `out` 用 `grep -F '<id>\t' producers.tsv` 取生产者数、用 `grep '"for": "<id>"' producers.jsonl` 取替代者明细，比对链内所选配方 vs 索引更优者。
- 发现: critical 0 / major 1 / minor 1

## 发现

### F-create-oneblock-1 | chain=0004(主证),0020,0037,0042,0063 | severity=major | category=worse-alternative (+silly-detour)
- 位置: `minecraft:leather` 的生产步骤 —— 典型链 0004 的 `A69 EXECUTE minecraft:leather_horse_armor`（craft, 输入 `minecraft:leather x51625`→`leather_horse_armor x7375`）紧接 `A70 EXECUTE create:crushing/leather_horse_armor`（crushing_wheel, 输入 `leather_horse_armor x7375`→`leather x14750`），并逐层重复 A71–A84（craft→crush→craft…），每轮 leather 净亏（7 leather→1 armor→2 leather，净 −5/轮），量级从 51625 一路衰减到 2。
- 问题: planner 为 `minecraft:leather` 选的生产者是 `create:crushing/leather_horse_armor`（及上游 `minecraft:leather_horse_armor` 合成），而该生产者自身的输入（`leather_horse_armor`）又由「消耗 leather 合成」得到 —— 这是一个**自体循环、净亏损的绕路**：必须先有 leather 才能造 armor，再粉碎回 leather。同一链里 A68 `farmersdelight:salvaging/leather_horse_armor`（recycling，已带标签）其实已经产出了 leather，后面的 craft+crush 循环纯属把已有 leather 越拆越少。
- 替代者（索引实证，producers 中 `minecraft:leather` 共 12 个生产者）:
  - `minecraft:leather`（`minecraft:crafting`）：`in:[rabbit_hide x4]` → `out:[leather]`，**纯直产、单输出、输入常见**。
  - `minecraft:leather`（`jei:category/create/automatic_shaped`）：同上，机器自动化等价。
  - 两相对比：所选 `create:crushing/leather_horse_armor` 需先以 7 份 leather 造 1 份 armor，再粉碎得 2 份 leather —— 既非直连也无更优输入，明显差于 `minecraft:leather`（rabbit_hide）。
- 系统性: 5 条链（0004/0020/0037/0042/0063）均出现 `leather_horse_armor` 合成 + `create:crushing/leather_horse_armor` 配对，聚合统计 `minecraft:leather_horse_armor` craft 共 33 次、`create:crushing/leather_horse_armor` 共 33 次，而**直产配方 `minecraft:leather`(rabbit_hide) 在所有 64 条链中 0 次被选用**。
- 建议: 选路层应优先 `minecraft:leather`(rabbit_hide x4) 或已有的 `farmersdelight:salvaging/leather_horse_armor` 作为 leather 来源，禁止「craft leather_horse_armor → crush」作为 leather 生产者；该循环（净亏损）也应被 loop 检测拦截。

### F-create-oneblock-2 | chain=0001,0002,0003,...(批次内 32 次) | severity=minor | category=worse-alternative (suspected)
- 位置: `minecraft:clay_ball` 的生产步骤 `EXECUTE create:milling/dripstone_block`（crushing_wheel/millstone，输入 `minecraft:dripstone_block x1` → `clay_ball x1`），聚合出现 32 次。
- 问题: 索引中 `minecraft:clay_ball` 共 8 个生产者，存在输入更常见的直产替代 `create:splashing/sand`（`jei:category/create/fan_washing`，`in:[sand x1]`→`clay_ball x1`）。sand 远比 dripstone_block  ubiquitous，且同为 1:1 单输入。
- 权衡: dripstone_block 也可通过滴水石农场稳定获取，故「可辩护」；但若 planner 已能取得 sand，选 sand-splashing 更优。判 minor + suspected，不强制。
- 建议: 选路偏好 `create:splashing/sand` 或 `create:crushing/gravel`（gravel→sand+flint+clay_ball）作为 clay_ball 来源，相对 `create:milling/dripstone_block` 输入更常见。

## 未触发（已扫过、判 ok 的常见生产者）
- `minecraft:diamond` via `diamond_from_blasting_deepslate_diamond_ore`（164 次）：与 `diamond_from_smelting/blasting_diamond_ore`、crushing 等价，属合理选路。
- `minecraft:stick` / `*_axe` / `*_planks` / `*_button` / `*_fence`：均为原版/Create 直产配方，无更优替代。
- `create:cut_*_slab|_brick_slab|_wall|_stairs` 系列：均为 stonecutter 直切，唯一/等价生产者。
- `create:blasting/*_ingot_from_crushed`（iron/gold）：Create 矿石→碎料→锭 的标准自动化链路，与直接熔炼等价，属可辩护自动化。
- `farmersdelight:bamboo_basket`（craft）/ `farmersdelight:canvas`（straw→canvas，31 次）/`sandstone`/`terracotta`/`bamboo_block` 等：直产或等价，ok。
- 已带 `rule-tag` 的 exchange/recolor/storage_ladder/recycling 步骤按口径不重复报告。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0004 | incomplete | bad | leather 经 leather_horse_armor 合成→粉碎自循环(净亏)产皮，应选 minecraft:leather(rabbit_hide) |
| 0020 | incomplete | bad | 同上 leather craft+crush 自循环 |
| 0037 | incomplete | bad | 同上 leather craft+crush 自循环 |
| 0042 | incomplete | questionable | 出现 1 次 leather craft+crush 配对（轻度） |
| 0063 | incomplete | bad | 同上 leather craft+crush 自循环 |
| 其余 59 条 | incomplete | ok | 非标签 EXECUTE 生产者选择合理，无更优替代可证 |

## 规则建议（给汇总者）
- targetRegex: 无新增（本批目标均为合法游戏物品，无展示物/重复变体怪象）。
- recipeRegex: `^create:crushing/leather_horse_armor$`（及上游 `^minecraft:leather_horse_armor$` 合成）作为 **leather 生产者时收敛** —— 当产出为 `minecraft:leather` 时，强制改用 `minecraft:leather`(rabbit_hide x4) 或既有 `farmersdelight:salvaging/leather_horse_armor`；证据：5 链共 33 craft+33 crush，直产 0 次。
- builtin 候选: 建议新增「craft X → crush X 双步净亏环」检测（leather 案例为典型：`leather_horse_armor` 由 leather 合成、粉碎回 leather，每轮净 −5），跨包可能通用，建议标签 `loop/net-loss-amplification`。
- 无新模式时写"无"。→ 除上述 leather 收敛与净亏环检测外，无其它新需收敛配方族。
