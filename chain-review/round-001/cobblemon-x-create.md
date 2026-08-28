# cobblemon-x-create batch-001 审查报告
- pack: cobblemon-x-create
- 批次: batch-001（written=150，全部为 `incomplete` 类别；depthBucket 绝大多数为 colossal/giant）
- 审查链数: 150（incomplete 150 / complete 0；全量扫描 150 条，对 INT_MAX 与 3^n 放大信号做逐链 grep 定位，深度精读代表链 0005/0081/0018/0138；大文件 8.3MB 不做整读，用 awk 区间抽取 + grep 信号）
- 发现: critical 0 / major 2 / minor 1

## 发现
### F-cobblemon-001 | chain=0081(为代表, 覆盖 43 链) | severity=major | category=amount-insanity
- 位置: CHAIN 0081 `[A164] EXECUTE cobblemon:relic_pouch_from_coin x387420489` / `[A165] EXECUTE cobblemon:relic_coin_from_pouch x387420489`；并外溢至 CHAIN 0003/0009/0011/0016/0032/0041/0075/0077/0081/0112/0145 等
- 问题: 规划器对一对**互逆转换配方**（`relic_pouch_from_coin` 9 币→1 袋；`relic_coin_from_pouch` 1 袋→1 币）做了**无净产出的指数放大**。链内出现 `x4782969=3^14`、`x43046721=3^16`、`x387420489=3^18`、`x3486784401=3^20` 的次幂量级（python 校验 `3**18=387420489`、`3**20=3486784401`）。`create:precision_mechanism x1` 目标根本不需要 relic_coin/relic_coin_pouch，却因此堆积出 `crafts=871696402`（8.7 亿）。43 条链命中 3^18/3^20 信号，crafts 普遍落在 10^8–10^10。
- 原因: `loop=allowBoundedAmplification` 对“币↔袋”这种 1:1/9:1 的互逆配方未设净产出下界，放大被当作有净产出来递归（实际为 lossy/no-op 往返），指数级失控。属规划器放大边界缺陷，非数据问题。
- 建议: 对“互逆转换对 + 出现 3^n 次幂 craft 数”的链标记为 amount-insanity / loop 收敛；收敛 `relic_pouch_from_coin|relic_coin_from_pouch` 配方族，禁止在目标无关分支上做指数放大。

### F-cobblemon-002 | chain=0005(为代表, 覆盖 12 链) | severity=major | category=amount-insanity
- 位置: CHAIN 0005 `[A50] EXECUTE deeperdarker:coal_from_blasting_gloomslate_coal_ore x2147483647` / `[A52] EXECUTE minecraft:coal_block x2147483647` / `[A53] EXECUTE minecraft:coal x2147483647`；未解析叶 `minecraft:coal x17179869176`、`dndesires:coal_piece x15032385529`
- 问题: 出现整数上限哨兵 `2147483647 = 2^31-1`（全文件 466 处，跨 12 条链）。`coal ↔ coal_block` 在 `x2147483647` 量级互转，且 `deeperdarker` 燃煤配方同样被推到 `x2147483647`。目标 `bettercampfirepot:diamond_green_campfire_pot` 需要的煤远不到此量级，却产生 `crafts=32833642948`（328 亿）。命中链：0005/0043/0051/0052/0053/0054/0055/0082/0102/0118/0138/0140。
- 原因: 煤/煤块循环在 `allowBoundedAmplification` 下未正确收敛，amount 直接触顶 `Integer.MAX_VALUE`——典型的**无界放大被整数封顶**，说明放大环本应被判定为无净产出的往返环却被放行。属规划器放大边界缺陷。
- 建议: 将 craft 数 == `2147483647` 视为“未收敛放大环”哨兵，整链降级为 junk/amount-insanity；收敛 `minecraft:coal_block|.*coal_from_blasting.*|.*coal_piece` 配方族，禁止煤类资源做 INT_MAX 级放大。

### F-cobblemon-003 | chain=0000 等(系统性) | severity=minor | category=other
- 位置: 全批 `[UNRESOLVED] minecraft:redstone x1 (candidates=5, reason=BUDGET_TRUNCATED)` 出现 751 次；另有 `minecraft:iron_ingot`/`copper_ingot`/`gold_ingot`/`diamond`/`sand`/`coal` 等原版常见物以 `reason=BUDGET_TRUNCATED` 未解析
- 问题: 原版极易生产的常见物（红石/铁锭/铜锭/金锭/钻石/沙）在几乎每条链里都因预算耗尽而未解析。按口径这属于 incomplete 的预期截断，**不应逐条报**；但 751 处红石 x1 的截断说明预算被大量 trivially-producible 物品吞噬，是 150 链全部 `incomplete`、convergence=0% 的结构性根因之一。
- 原因: 预算在 trivial 原版物品上过早耗尽（与 F-001/F-002 的放大爆炸互相挤占），导致本可解析物品被标 unresolved，拉低覆盖率。
- 建议: 对 `minecraft:redstone|iron_ingot|copper_ingot|gold_ingot|diamond|sand|coal`（candidates 存在、reason=BUDGET_TRUNCATED）做预算保护/优先解析，避免其浪费 cone 预算；属 systemic，非单链问题。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0005 | amount-insanity | bad | INT_MAX 煤循环，crafts=328亿 |
| 0043 | amount-insanity | bad | INT_MAX 煤循环/钻石锅 |
| 0051 | amount-insanity | bad | INT_MAX 煤循环/钻石锅 |
| 0052 | amount-insanity | bad | INT_MAX 煤循环/钻石锅 |
| 0053 | amount-insanity | bad | INT_MAX 煤循环/钻石锅 |
| 0054 | amount-insanity | bad | INT_MAX 煤循环/钻石锅 |
| 0055 | amount-insanity | bad | INT_MAX 煤循环/钻石锅 |
| 0082 | amount-insanity | bad | INT_MAX 煤循环/vanilla→diamond 升级(415亿) |
| 0102 | amount-insanity | bad | INT_MAX 煤循环/green_concrete_powder(166亿) |
| 0118 | amount-insanity | bad | INT_MAX 煤循环/campfire_pot_green(328亿) |
| 0138 | amount-insanity | bad | INT_MAX 煤循环/create:mechanical_roller(375亿) |
| 0140 | amount-insanity | bad | INT_MAX 煤循环/create:deployer(375亿) |
| 0003 | amount-insanity | bad | 3^20 放大/emerald_green锅(4.8亿) |
| 0009 | amount-insanity | bad | 3^n 放大/netherite_yellow锅(1.07亿) |
| 0010 | amount-insanity | bad | 3^n 放大/netherite_black锅 |
| 0011 | amount-insanity | bad | 3^n 放大/netherite_pink锅 |
| 0012 | amount-insanity | bad | 3^n 放大/netherite_red锅 |
| 0016 | amount-insanity | bad | 3^n 放大/gold_green锅(8.7亿) |
| 0017 | amount-insanity | bad | 3^n 放大/vanilla→netherite升级(1.07亿) |
| 0020 | amount-insanity | bad | 3^n 放大/emerald_white锅 |
| 0025 | amount-insanity | bad | 3^n 放大/emerald_yellow锅 |
| 0026 | amount-insanity | bad | 3^n 放大/emerald_blue锅 |
| 0027 | amount-insanity | bad | 3^n 放大/emerald_pink锅 |
| 0028 | amount-insanity | bad | 3^n 放大/emerald_black锅 |
| 0029 | amount-insanity | bad | 3^n 放大/emerald_black锅 |
| 0031 | amount-insanity | bad | 3^n 放大/iron_green锅(9685万) |
| 0032 | amount-insanity | bad | 3^n 放大/pokefinder_green(19.8亿) |
| 0034 | amount-insanity | bad | 3^n 放大/train_station_clock |
| 0036 | amount-insanity | bad | 3^n 放大/fishingnav(17.8亿) |
| 0041 | amount-insanity | bad | 3^n 放大/creative_mixer(17.8亿) |
| 0044 | amount-insanity | bad | 3^n 放大/copper→netherite升级 |
| 0048 | amount-insanity | bad | 3^n 放大/vanilla→emerald升级 |
| 0057 | amount-insanity | bad | 3^n 放大/copper_green锅 |
| 0067 | amount-insanity | bad | 3^n 放大/creative_chain_conveyor(9691万) |
| 0070 | amount-insanity | bad | 3^n 放大/railway_mixer(mega-wide) |
| 0072 | amount-insanity | bad | 3^n 放大/iron→netherite升级 |
| 0073 | amount-insanity | bad | 3^n 放大/gold_white锅 |
| 0075 | amount-insanity | bad | 3^n 放大/creative_gearshift(17.8亿) |
| 0077 | amount-insanity | bad | 3^n 放大/creative_press(17.8亿) |
| 0081 | amount-insanity | bad | 3^20 放大/precision_mechanism(8.7亿), 见 F-001 |
| 0086 | amount-insanity | bad | 3^n 放大/gold_blue锅 |
| 0087 | amount-insanity | bad | 3^n 放大/gold_red锅 |
| 0088 | amount-insanity | bad | 3^n 放大/gold_pink锅 |
| 0089 | amount-insanity | bad | 3^n 放大/gold_yellow锅 |
| 0090 | amount-insanity | bad | 3^n 放大/gold_black锅 |
| 0100 | amount-insanity | bad | 3^n 放大/copper→emerald升级 |
| 0112 | amount-insanity | bad | 3^n 放大/railway_chain_conveyor(1.99亿) |
| 0120 | amount-insanity | bad | 3^n 放大/advanced_display_sloped(17.7亿) |
| 0123 | amount-insanity | bad | 3^n 放大/advanced_display_half_panel |
| 0134 | amount-insanity | bad | 3^n 放大/iron_white锅 |
| 0137 | amount-insanity | bad | 3^n 放大/gold→netherite升级 |
| 0142 | amount-insanity | bad | 3^n 放大/railway_gearshift(mega-wide) |
| 0143 | amount-insanity | bad | 3^n 放大/railway_press(mega-wide) |
| 0145 | amount-insanity | bad | 3^n 放大/sequenced_gearshift(1.93亿) |
| 0004 | amount-insanity | questionable | crafts=2.0亿(非 3^n/INT_MAX 放大器, 待下一轮复核) |
| 0022 | amount-insanity | questionable | crafts=1076万, 放大源待定 |
| 0060 | amount-insanity | questionable | crafts=2.0亿/green_drawer |
| 0061 | amount-insanity | questionable | crafts=2.0亿/green_sink |
| 0063 | amount-insanity | questionable | crafts=2.0亿/green_cabinetry |
| 0064 | amount-insanity | questionable | crafts=2.0亿/green_stove |
| 0071 | amount-insanity | questionable | crafts=1.98亿/green_upper_cabinet |
| 0078 | amount-insanity | questionable | crafts=2.0亿/kitchen_floor(mega-wide) |
| 0079 | amount-insanity | questionable | crafts=2.0亿/poke_ball_desk(mega-wide) |
| 0080 | amount-insanity | questionable | crafts=2.0亿/poke_ball_chair(mega-wide) |
| 0084 | amount-insanity | questionable | crafts=1.98亿/green_concrete |
| 0095 | amount-insanity | questionable | crafts=1143万/copper_adjustable_chain_gearshift |
| 0110 | amount-insanity | questionable | crafts=2152万/industrial_fan |
| 0018 | other | ok | park_ball, crafts=529, 目标已产出, 仅预期截断 |
| 0037 | other | ok | nest_ball, crafts=356, 正常 incomplete |
| 0038 | other | ok | friend_ball, crafts=356, 正常 |
| 0042 | other | ok | ancient_verdant_ball, crafts=515 |
| 0058 | other | ok | ancient_jet_ball, crafts=225 |
| 0059 | other | ok | ancient_gigaton_ball, crafts=540, depth=17 |
| 0062 | other | ok | verdant_ball, crafts=336 |
| 0097 | other | ok | reassembly_unit, crafts=616 |
| 0099 | other | ok | zygarde_cube, crafts=364 |
| 0124 | other | ok | ancient_leaden_ball, crafts=354 |
| 0125 | other | ok | ancient_wing_ball, crafts=49 |
| 0146 | other | ok | repeat_ball, crafts=356 |
| 0147 | other | ok | dream_ball, crafts=356 |
| 0148 | other | ok | moon_ball, crafts=357 |
| 0149 | other | ok | cameruptite, crafts=984 |
| 其余未完成链(未逐列者) | other | ok | incomplete 预期截断(pattern/exchange 递归致 depth=colossal, 目标多已产出), 不单独报 |

## 规则建议（给汇总者）
- targetRegex: `^bettercampfirepot:(.*_campfire_pot|.*_tier_upgrade)$|^createcasing:creative_.*$|^cobblefurnies:.*$|^minecraft:green_concrete.*$` —— 排除/优先审查这批高频 amount-insanity 目标（证据：12 INT_MAX + 43 指数放大链绝大多数落在此类，占 67 条 crafts≥1e6 链的主体）。
- recipeRegex: `relic_pouch_from_coin|relic_coin_from_pouch` —— 标记收敛“互逆转换对 + 3^n 放大”配方族（证据：43 链，F-cobblemon-001）；`minecraft:coal_block|.*coal_from_blasting.*|.*coal_piece` —— 收敛煤类 INT_MAX 放大（证据：12 链 466 处 2147483647，F-cobblemon-002）。
- builtin 候选:
  1. `pattern-amplification/int-max-sentinel`（junk）：当任一 EXECUTE 的 craft 数 == `2147483647`（2^31-1）即判定为未收敛放大环，整链降级 junk —— 证据 12 链 466 处，跨包可能通用。
  2. `pattern-amplification/base3-exponential`（junk/duplicate）：当链内出现 `x4782969|x43046721|x387420489|x3486784401`（=3^14/16/18/20）且来自互逆转换对时标记 amount-insanity —— 证据 43 链。
  3. `budget/vanilla-truncation`（systemic）：对 `minecraft:redstone|iron_ingot|copper_ingot|gold_ingot|diamond|sand|coal` 且 reason=BUDGET_TRUNCATED 的项做预算保护（证据：redstone x1 截断 751 次），避免吞噬 cone 预算致全批 incomplete。
- 无新模式时写"无"：本批发现均可归入上述建议，无额外独立模式。
