# cobblemon-x-create batch-002 审查报告
- pack: cobblemon-x-create
- 批次: batch-002（written=96；类别分布：incomplete 88 / medium 7 / long 1）
- 审查链数: 96（全量信号扫描 88 条 incomplete + 全读 7 medium + 1 long；incomplete 大文件 5.4MB/88 链≈60KB/条，按口径抽样+关键段精读，抽读 0005/0011/0013/0016/0017/0019/0037/0040/0045/0079/0090 等 ≥14 条全文或关键段）
- 发现: critical 0 / major 2 / minor 1

## 审查结论速览
- **无 fake-production / 无 free-resource / 无 anvil·enchantment 泄漏**：全部 `jei:` 步骤均为 `jei:category/...`（cobblemon berry_recipe / campfire_pot_recipe / create mixing·sawing·spout 等真实机器系统）或 `jei:/synthetic/<n>`（规划器合法合成 id）；CEI 的 experience_cake_slice / experience_cake 是真实切割/填充配方，非附魔泄漏。
- **核心问题 = 锥体几何放大（geometric amplification）**：本批新暴露的主要异常是 `loop=allowBoundedAmplification` 被配方图里的 ×N 反馈环（exp_candy l→m ×3、apricorn↔dye ×2、coal↔block、calcite 石切族）反复放大，把 trivial 物品需求推到荒谬量级。属本批新模式，详见 F1/F2。
- **已知规划器级问题（按 round-2 口径仅在此一句话提及，不再展开）**：①32 位溢出哨兵 `x2147483647`（及整数倍 `x17179869176`=8×、`x15032385529`=7× 出现在 0011/0019/0079 的 coal 需求）；②`|+N` 物品 id 后缀污染（`pink_apricorn|+6`、`grepa_berry|+69`、`cut_calcite_bricks|+14`、`crimsite_pillar|+14`）；③无净产出往返环（coal→block→coal、lapis↔shard 等）机制本身。

## 发现
### F-cobblemon-x-create-1 | chain=0090 (+0091~0095) | severity=major | category=amount-insanity
- 位置: `cobblemon_industries:mixing/candy/candyexp_g_recc`、`cobblemon:exp_candy_m_from_exp_candy_l`、`cobblemon_industries:mixing/candy/candyexp_gg_recc`（CHAIN 0090 A39→A62 级联）
- 问题: exp_candy 配方图存在 ×3 反馈环：`exp_candy_m_from_exp_candy_l` 把 1× exp_candy_l → 3× exp_candy_m，再经 `candyexp_g_recc`（m+pink_apricorn→l，1:1）形成每轮 ×3 的几何放大；CHAIN 0090 中该级联从 9 一路翻倍到 6561（A40→A62：9→27→81→243→729→2187→6561 的 exp_candy_l）。结果 `[RAW]` 显示单枚 `cobblemon:health_candy x1` 需要 `green/red/white/yellow/pink_apricorn` 各 x9862，合计约 **49,310 个 apricorn** 原料，且同类 candy 链（0091~0095）同构。
- 原因: 规划器 cone 把有净产出的 ×3 反馈环当作"免费放大器"反复展开，需求在锥体根部以 3ⁿ 膨胀，最终把 1 个终产物的原料需求放大到数万级。属"指数放大"型数量失控；虽 net-positive（规则层 allowedBoundedAmplification），但路径对真实玩家完全荒谬。
- 建议: 对 `exp_candy_m_from_exp_candy_l` 与 `candyexp_g_recc`/`candyexp_gg_recc` 这一族打 amplification 收敛标签并设放大上限，避免 3ⁿ 级联；或在 filters 中对该族 recipe 限制展开深度。

### F-cobblemon-x-create-2 | chain=0045 (+0037/0040/0060/0075/0081/0093) | severity=major | category=amount-insanity
- 位置: `[UNRESOLVED] minecraft:wheat x43046721`(CHAIN 0045, line 31588)、`[UNRESOLVED] minecraft:raw_copper x387420489`(CHAIN 0037/0045/0075/0093, line 25429 等)、`[UNRESOLVED] minecraft:raw_iron x387420489`(CHAIN 0060/0081, line 39810/50922)
- 问题: 多条链的 UNRESOLVED 叶出现**真·幂次几何放大**的原版 trivial 物料需求：`wheat x43046721 = 3¹⁶`、`raw_copper/raw_iron x387420489 = 3¹⁸`。这些值是 ×3 反馈环在锥体里反复相乘的结果（非溢出哨兵整数倍），且作为 `reason=BUDGET_TRUNCATED` 的未解析叶被记入——即规划器声称要"供应 3¹⁸ 个原铜"才能造出目标。同批还有 `coal` 达 `x17179869176`(=8×哨兵)/`x15032385529`(=7×哨兵) 的溢出型天文需求（0011/0019/0079），后者属已知溢出哨兵，单列不报。
- 原因: 与 F1 同源——配方图中存在 1→3（或更高）倍率的互逆/反馈配方，cone 在估算根输入时把它们几何放大；原版常见可产物料（wheat/铜矿/铁矿/coal）被放大到天文量级，使链的根需求失真、不可用于实际规划。
- 建议: 对造成 3ⁿ 放大的基础互逆配方（如 `raw_copper`/`raw_iron`/`wheat` 相关的 blast↔smelt、block↔ingot 等价族）加 amplification 收敛或展开深度上限；UNRESOLVED 叶上的天文量级需求应被判定为失真而非真实需求。

### F-cobblemon-x-create-3 | chain=0001~0004 (+全批) | severity=minor | category=unresolved-expected-item
- 位置: `[UNRESOLVED] minecraft:potion x1 (reason=CASCADE_EXHAUSTED)`（CHAIN 0001 line 1562、0002/0003/0004 同构）、`[UNRESOLVED] minecraft:redstone x1`（全批共 1674 处，如 line 723+）
- 问题: 大量本应可生产的原版常见物被标 UNRESOLVED：`minecraft:redstone`（可采矿/熔炼）全批 1674 处、`minecraft:potion`（可酿造）在 0001~0004 等链以 `reason=CASCADE_EXHAUSTED` 收尾、`iron_ingot`/`gold_ingot`/`diamond` 亦见小量未解析。它们不是真正不可产，而是 cone 预算/级联耗尽前没来得及解析到。
- 原因: 规划器 cone 体量大（depth 100~250、actions 200~500），在触及 trivial 原版物前预算（BUDGET/RANK_TRUNCATED）或上游级联（CASCADE_EXHAUSTED）已耗尽；属规划器级预算问题而非配方数据错误。
- 建议: 此类"原版可产物未解析"应在汇总层视为预算截断噪声、不计入真实缺口；若需精确根需求，建议提高 trivial 原版物的解析优先级或预算下限。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0005 | incomplete | questionable | coal↔block 无净往返环 + apricorn↔dye ×2 反馈放大（已知机制），green_apricorn 自放大 |
| 0011 | incomplete | bad | pokedex_green；coal 需求 x17179869176(=8×哨兵)，天文量级 UNRESOLVED 叶 |
| 0013 | incomplete | questionable | apricorn↔dye ×2 自放大 13 级联（net-positive，已知机制）；`grepa_berry|+69` 后缀污染 |
| 0016 | incomplete | ok | experience_cake_slice（CEI 真实切割/填充），无假生产 |
| 0017 | incomplete | questionable | limited_diamond_barrel_4；coal 溢出型需求，已知哨兵放大 |
| 0019 | incomplete | bad | limited_diamond_barrel_3；coal x17179869176 / x15032385529 天文量级叶 |
| 0034 | medium | ok | green_dye via 浆果杂交（jei:category/cobblemon/berry_recipe，合法） |
| 0037 | incomplete | bad | hydraulic_press；raw_copper x387420489(=3¹⁸) 几何放大未解析 |
| 0040 | incomplete | questionable | weighted_ejector；coal x570508289 放大叶 |
| 0045 | incomplete | bad | asphalt；wheat x43046721(=3¹⁶) 几何放大未解析 |
| 0048/0083/0085/0087/0088/0089 | medium | ok | 各浆果 via berry_recipe 杂交链，合法无异常 |
| 0053 | long | ok | ultranecrozium_z；moon/sun_stone↔block 往返放大（已知机制），net-positive 完成 |
| 0056 | incomplete | questionable | trainer_spawner（rctmod 真实生成配方），mega-wide 体量大 |
| 0079 | incomplete | bad | adjustable_chain_gearshift；coal x17179869176 溢出型天文量级叶 |
| 0090 | incomplete | bad | health_candy；exp_candy ×3 级联→单产物需 ~49,310 apricorn（F1） |
| 0091~0095 | incomplete | bad | 各 candy 链，exp_candy ×3 级联同构，原料需求荒谬（F1） |
| 其余 60+ incomplete 链 | incomplete | ok/questionable | 多为 sophisticatedstorage/storagedrawers/cobblemon 物品；常见 redstone/potion 等原版物因预算截断未解析（F3），无假生产、无附魔泄漏 |

## 规则建议（给汇总者）
- targetRegex: 本批**无新增目标排除**（无 anvil/enchant 泄漏目标、无假生产目标）。不建议排除 exp_candy_*（其为合法终产物）；问题在放大而非目标本身，宜用 recipe 层收敛而非目标黑名单。
- recipeRegex:
  - `cobblemon:exp_candy_m_from_exp_candy_l|cobblemon_industries:mixing/candy/candyexp_(g|gg)_recc` —— 标记/收敛 ×3 反馈放大族，证据 CHAIN 0090(+0091~0095) 共 ≥7 条 candy 链，单产物需求膨胀到 3ⁿ。
  - `cobblemon_industries:mixing/apricorn_.*|cobblemon:[a-z_]*_apricorn_smelt_to_dye` —— 标记 apricorn↔dye 净正 ×2 反馈环（已知机制但仍可加 amplification-watch），证据 CHAIN 0005/0013。
  - 证据条数：candy 族 ≥7 条、apricorn 族 ≥2 条、3ⁿ 原版放大族（wheat/raw_copper/raw_iron）≥6 条。
- builtin 候选:
  - ① **`\|\+[0-9]+$` 物品 id 后缀污染** → 建议 sanitize 为 junk/duplicate（跨包通用）。证据：`pink_apricorn|+6`、`grepa_berry|+69`、`cut_calcite_bricks|+14`、`crimsite_pillar|+14`，分布于 0013/0090 等多条链，且污染 id 直接进入 UNRESOLVED 叶。
  - ② **32 位溢出哨兵 `x2147483647`（及整数倍 x17179869176 / x15032385529）** → 已知 data-pollution，继续在 filters 中排除/归一；证据 CHAIN 0011/0019/0079 的 coal 需求。
  - ③ **3ⁿ 几何放大叶（wheat x43046721=3¹⁶、raw_copper/raw_iron x387420489=3¹⁸）** → 建议对 UNRESOLVED 叶上的"幂次级"需求量设上限判定（失真而非真实需求），证据 CHAIN 0045/0037/0060/0081/0093。
