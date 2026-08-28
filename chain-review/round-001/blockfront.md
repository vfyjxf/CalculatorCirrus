# BlockFront - Official Mod Pack 0.9.0.24b batch-001 审查报告
- pack: BlockFront - Official Mod Pack 0.9.0.24b
- 批次: batch-001（written=80；giant 32 / medium 22 / long 17 / incomplete 6 / short 2 / colossal 1）
- 审查链数: 80（全量；批次不足 100 条故全审）。incomplete 6 / complete 74。读法：short·colossal·incomplete·medium·long 全读，giant 全读并对 bone/lapis/redstone/raw_iron/diamond/raw_gold/slime/honey 等可逆配对做 grep 取证。
- 发现: critical 0 / major 7 / minor 1

## 发现

### F-blockfront-1 | chain=0000,0003,0004,0007,0008,0010,0018,0032,0049,0052,0057,0058,0031,0055,0064,0070,0005,0021,0025,0027,0043,0048 | severity=major | category=loop-or-cycle
- 位置: 各链 `EXECUTE minecraft:bone_block x1 @` 与 `EXECUTE minecraft:bone_meal_from_bone_block` 反复成对出现（bone=4 的链：giant 12 条、long 4 条、incomplete 6 条）
- 问题: 为产出 1 份 white_dye/gray_dye，规划器先把 1 bone→9 bone_meal（A3），再 bone_block↔bone_meal 往返 4 次（如 0000 的 A4–A11），净产出为零，仅最后 A12 取 1 bone_meal。即 compress↔decompress 互逆 no-op 重复，符合口径明示的“compress↔decompress 往返环”。
- 原因: `loop=allowBoundedAmplification` 策略对可逆方块配对做无净产出放大；分类器未识别 bone_meal↔bone_block 为 9:1 互逆对。
- 建议: 对 bone_meal/bone_block 可逆对加循环检测，折叠放大步，仅保留“1 bone→骨粉”直接路径。

### F-blockfront-2 | chain=0006,0016,0017,0040,0045,0049,0051,0057,0005,0021,0025,0027,0043,0048 | severity=major | category=loop-or-cycle
- 位置: `EXECUTE minecraft:lapis_block x1 @` 与 `in : minecraft:lapis_block x1`（解压产出 lapis_lazuli）反复成对；giant 8 条、incomplete 6 条
- 问题: 链为产出 blue_dye，把 lapis_lazuli↔lapis_block 往返多次（如 0043 的 A8–A15 共 7 次解压），净产出不变，仅最终取 1 lapis_lazuli 做蓝染。无净产出的互逆往返环。
- 原因: 同上，lapis_lazuli/lapis_block 可逆 9:1 配对被 allowBoundedAmplification 放大。
- 建议: 同 F-1，对 lapis 可逆对折叠放大步。

### F-blockfront-3 | chain=0001,0011,0075 | severity=major | category=loop-or-cycle
- 位置: `EXECUTE minecraft:redstone_block x1 @` 与 `EXECUTE minecraft:redstone x1 @`（解压）成段往返；0001 的 A6–A21 共 16 次往返
- 问题: 0001(crafter) 仅最终需要 redstone x2（A42），却把 redstone↔redstone_block 往返 16 次；A5 一次 deepslate_redstone_ore 爆破已得 9 redstone，足够。纯无净产出往返环。
- 原因: redstone/redstone_block 可逆 9:1 配对被放大。
- 建议: 折叠 redstone 可逆对放大步。

### F-blockfront-4 | chain=0001,0011,0075,0035 | severity=major | category=loop-or-cycle
- 位置: `EXECUTE minecraft:raw_iron_block x1 @` 与 `EXECUTE minecraft:raw_iron x1 @`（解压）；0011 的 A19–A34、0035 的 A5–A20 及 A24–A31
- 问题: raw_iron↔raw_iron_block 往返（9:1 互逆），净产出为零。0035(hopper_minecart) 中该往返出现两段（A5–A20 与 A24–A31），目标仅各需 5 raw_iron 进熔炉。
- 原因: raw_iron/raw_iron_block 可逆配对被放大。
- 建议: 折叠 raw_iron 可逆对放大步。

### F-blockfront-5 | chain=0012,0023,0026,0028,0029,0033,0034 | severity=major | category=loop-or-cycle
- 位置: `EXECUTE minecraft:diamond_block x1 @` 与 `EXECUTE minecraft:diamond x1 @`（解压）；0012 的 A2–A9 共 8 次往返
- 问题: 下界合金装备/工具链为造 diamond_<部件> 把 diamond↔diamond_block 往返多次（净零），0012 仅需要 8 diamond 做 diamond_chestplate。链本体（netherite_scrap+gold 冶炼+smithing）合理，但嵌了钻石可逆环。
- 原因: diamond/diamond_block 可逆 9:1 配对被放大。
- 建议: 折叠 diamond 可逆对放大步；netherite 主链保留。

### F-blockfront-6 | chain=0011 | severity=major | category=loop-or-cycle
- 位置: `EXECUTE minecraft:slime_block x1 @` 与 `EXECUTE minecraft:slime_ball x1 @`（解压）；A42–A57 共 16 次往返
- 问题: slime_ball↔slime_block 往返（净零），目标 sticky_piston 仅取 1 slime_ball（A58）。无净产出往返环。
- 原因: slime_ball/slime_block 可逆 9:1 配对被放大。
- 建议: 折叠 slime 可逆对放大步。

### F-blockfront-7 | chain=0063 | severity=major | category=loop-or-cycle
- 位置: `EXECUTE minecraft:honey_block x1 @` 与 `EXECUTE minecraft:honey_bottle x1 @`（解压，in 含 glass_bottle）；A1–A6、A8–A11、A13–A16、A18–A21 多段往返
- 问题: 用 `sugar_from_honey_bottle` 造糖时，把 honey_block↔honey_bottle 反复往返放大产出 honey_bottle（每段 1 honey_block→4 honey_bottle，再封存回 honey_block），净产出≈0、且反复消耗 glass_bottle。属带副产物消耗的放大往返环，与 F-1~6 同根因。
- 原因: honey_block/honey_bottle 可逆配对 + allowBoundedAmplification 放大。
- 建议: 折叠 honey 可逆对放大步；糖优先走 sugar_cane（见 0019/0020 直链更短）。

### F-blockfront-8 | chain=0005,0021,0025,0027,0043,0048 | severity=minor | category=unresolved-expected-item
- 位置: 各链尾部 `[UNRESOLVED] minecraft:black_wool|+14 x6 (candidates=5, reason=RANK_TRUNCATED, truncated)`（0021 为 black_bed|+14，0027 为 black_carpet|+14，0035 无）
- 问题: 6 条染色链把 black_wool / black_bed / black_carpet 作为染基材输入，但基材本身 [UNRESOLVED]。black_wool/bed/carpet 均为原版常见可生产物（白羊毛+黑染料 / 染床 / 染地毯），本应可解析，因 RANK_TRUNCATED 放弃。根因是规划器 rank 预算耗尽而非不可生产。
- 原因: 染色重着色模式(pattern/recolor) 以 black_* 为基材，但 black_* 的解析未纳入同批预算；`|+14` 为染色 tag 变体标记。截断本身按口径纪律#3 豁免，但“原版常见物未解析”达到标记门槛，故降 minor 单列。
- 建议: 将 black_wool/bed/carpet 视为可解析原版物（或直接作 raw 叶子豁免），或加 builtin 模式识别 `dye_X_<base>` 系列以 black_<base> 为基材。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000,0003,0004,0007,0008,0010,0018,0032,0049,0052,0057,0058 | giant | bad | bone_meal↔bone_block no-op 往返（F-1） |
| 0006,0016,0017,0040,0045,0051 | giant | bad | lapis_lazuli↔lapis_block no-op 往返（F-2） |
| 0049,0057 | giant | bad | bone+lapis 双环（F-1+F-2） |
| 0001 | giant | bad | redstone+raw_iron 双环（F-3+F-4） |
| 0011 | giant | bad | redstone+raw_iron+slime 三环（F-3+F-4+F-6） |
| 0075 | giant | bad | redstone+raw_iron 双环（F-3+F-4） |
| 0012,0023,0026,0028,0029,0033,0034 | giant | bad | diamond↔diamond_block no-op 环（F-5；主链合理） |
| 0035 | colossal | bad | raw_iron↔raw_iron_block 双段环（F-4） |
| 0031,0055,0064,0070 | long | bad | bone_meal↔bone_block no-op 环（F-1） |
| 0063 | long | bad | honey_block↔honey_bottle 放大环（F-7） |
| 0005,0021,0025,0027,0043,0048 | incomplete | bad | bone/lapis 环 + black_* 未解析（F-1/F-2/F-8） |
| 0002,0009,0015,0022,0054,0062,0065,0066,0067,0068,0069,0071 | long | ok | 无环、直链合理 |
| 0014,0030,0037,0038,0039,0041,0042,0046,0047,0050,0053,0056,0059,0060,0061,0072,0073,0074,0076,0077,0078,0079 | medium | ok | 无环、stonecutting/crafting 直链合理 |
| 0019,0020 | short | ok | 直链糖/派，无异常 |

## 规则建议（给汇总者）
- targetRegex: 无（本批目标均合理，无应排除目标）。
- recipeRegex: 收敛可逆方块配对 `minecraft:(bone_meal_from_bone_block|lapis_lazuli|redstone|raw_iron|raw_gold|diamond|slime_ball|honey_bottle) ...` 与其对应 `*_block x1 @` 压缩步——标记为循环候选并折叠。证据：全批 30+ 链出现，见 F-1~F-7。
- builtin 候选: `cirrus:pattern/block-pair-amplification`（建议标签 pattern-cyclic-block-amplification / junk）——检测 9:1 与 4:1 可逆 item↔block 配对、在 `allowBoundedAmplification` 下产生的无净产出往返环，折叠放大步。证据：bone/lapis/redstone/raw_iron/raw_gold/diamond/slime/honey 八类配对全批复现。
- builtin 候选（补充）: `cirrus:pattern/dye-base-black`（建议标签 pattern-recolor-base）——识别 `dye_X_<base>` 系列以 black_<base>（wool/bed/carpet）为基材，将 black_* 视为可解析原版物或 raw 叶子豁免，消除 F-8 的 RANK_TRUNCATED 未解析。证据：6 条 incomplete 链。
- 无新模式时写“无”：除以上两条 builtin 候选外无其它新模式。
