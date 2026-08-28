# farmers-colonies batch-001 审查报告
- pack: farmers-colonies
- 批次: batch-001（written=150；类别分布 medium 49 / giant 42 / long 24 / incomplete 22 / short 11 / colossal 2）
- 审查链数: 150（incomplete 22 / complete 128；全量过边界+状态+UNRESOLVED，重点链全文，含 colossal 2、short 11、medium 6、long 3、giant 3 全文 + incomplete 5 全文）
- 发现: critical 1 / major 2 / minor 0

## 发现
### F-fc-001 | chain=<多链，见证据> | severity=major | category=loop-or-cycle
- 位置: 各链中 `<ingot/gem/dye>_block ↔ <base>` 互逆步骤（如 A10–A25、A1–A16、A38–A54 等）
- 问题: 规划器在需要 N 个金属/宝石/染料时，先把原料经 blast furnace 变 ingot，再反复在「块↔粒」之间 9:1↔1:9 往返（identity round-trip，无净产出），只为凑出精确数量。这是典型的无净产出往返环（brief 明确把 compress↔decompress 列为仍属问题），把动作/配方数放大数倍。
  证据链：
  - CHAIN 0013（multipistonblock）: A1–A16 红石块↔红石（17 步 no-op）、A38–A54 生铁块↔生铁（16 步 no-op），最终 A59 活塞只用了 redstone x3；actions=61 / crafts=87。
  - CHAIN 0006（simplequarry）: A10–A25 生铁↔生铁块循环，crafts=509。
  - CHAIN 0042（detector_rail）: A1–A17 红石块↔红石、A22–A37 生铁↔生铁块；目标配方只需 redstone x1 + iron x6。
  - CHAIN 0007 A12–A20（钻石块↔钻石）、CHAIN 0112 A6–A14、CHAIN 0137（绿宝石块↔绿宝石）、CHAIN 0026 A4–A12（骨块↔骨粉）、CHAIN 0054（骨块↔骨粉、青金石块↔青金石）、CHAIN 0040 A5–A20（生铁↔生铁块）。
- 原因: `loop=allowBoundedAmplification` 的「块缓冲」策略被当作默认放大手段；这些循环本身零净产出，只是把 ore→ingot 后多余的量在块形态间绕圈。属数据/规划层噪声，非玩家可执行生产逻辑错误。
- 建议: 升级为 builtin 排除规则 `pattern/block-buffer-amplification`，将 `raw_iron_block↔raw_iron`、`diamond_block↔diamond`、`emerald_block↔emerald`、`redstone_block↔redstone`、`bone_block↔bone_meal`、`lapis_block↔lapis_lazuli`、`iron_block↔iron_ingot`、`gold` 同族循环直接归一，避免计入 craft/action 数。

### F-fc-002 | chain=0143,0145,0002,0121 | severity=critical | category=unresolved-expected-item
- 位置: `[UNRESOLVED] minecraft:stone_axe x1 (reason=CASCADE_EXHAUSTED)` / `minecraft:bow x1 (reason=CASCADE_EXHAUSTED)`
- 问题: `stone_axe`（cobblestone+stick）与 `bow`（string+stick）均为原版可直接合成的常见工具，却被判为不可解析、作为 RAW 叶子残留，且链中其实已具备其原料（CHAIN 0143 含 `cobblestone|+2` 与由 oak_planks 产出的 stick；CHAIN 0145 含 stick）。规划器未走正常合成，反而用 anvil 的 `jei:/synthetic/*`（pattern/exchange）「修复/合成」放大环（0143 用 50 本 enchanted_book、0145 用 14 本）去「倍增」一把本应直接做出的工具。
  证据链：
  - CHAIN 0143（blockhutlumberjack）: A2–A50 全是 anvil 修复/合成放大 stone_axe，末行 `[UNRESOLVED] minecraft:stone_axe x1 (CASCADE_EXHAUSTED)`，但 A55 已有 stick、A1 有 cobblestone。
  - CHAIN 0145（blockhutguardtower）: A12–A20 anvil 合成 bow 放大环，末行 `[UNRESOLVED] minecraft:bow x1 (CASCADE_EXHAUSTED)`。
  - CHAIN 0002（banner_rally_guards）、CHAIN 0121（gray_floating_carpet）: 同样 `bow x1 (CASCADE_EXHAUSTED)`。
- 原因: 本包配方图中很可能缺失原版工具配方（stone_axe/bow 及同族），导致规划器只知 anvil 修复合成路径、找不到从零合成路径，最终把工具当不可解析叶子丢弃。属图谱数据缺口，非逻辑错误。
- 建议: 在配方图/分类白名单中补回原版工具合成配方（至少 stone_axe、bow，建议全量原版工具）。修复后上述 4 条 incomplete 链可转为 complete。

### F-fc-003 | chain=0008,0017,0029,0051,0053,0054,0059,0103,0121,0122,0123,0124,0125,0127,0128,0130,0131,0133,0134 | severity=major | category=unresolved-expected-item
- 位置: `[UNRESOLVED] minecraft:black_wool|+14 xN (reason=RANK_TRUNCATED, truncated)` 与 `minecraft:black_bed|+14 x1 (reason=RANK_TRUNCATED, truncated)`
- 问题: 约 19 条 incomplete 链（所有染色床/地毯/羊毛/旗帜目标）都把 `black_wool|+14` 或 `black_bed|+14` 当作染色基底（recolor 起点），但该带 `|+14` 块状态变体的物品被判 RANK_TRUNCATED 不可解析。black_wool / black_bed 本身是原版可染物（black_dye + white_wool / 任意床+黑染料），「显然本可解析」；`|+14` 看起来是导出时的块状态/注册表变体后缀，与标准合成输出节点不匹配，从而卡住整条染色链。
  证据链（行号对应 chains-incomplete-001.txt）: 268,544,748,923,1115,1319,1476,1641,1995,2187,2473,2744,3022,3300,3578,3856,4135,4413,4695 均为 `black_wool|+14`/`black_bed|+14` + `reason=RANK_TRUNCATED, truncated`；CHAIN 0054 末行 `[UNRESOLVED] minecraft:black_bed|+14 x1 (candidates=5, reason=RANK_TRUNCATED, truncated)`。
- 原因: 染色基底物品携带了不被任何合成配方产出的 `|+14` 块状态变体（数据/注册表产物），导致 RANK_TRUNCATED。这是跨链系统性数据瑕疵，使约 18 条链整体 incomplete。
- 建议: 归一化/折叠 `black_wool|+14`→`black_wool`、`black_bed|+14`→`black_bed`（或更通用的 `\|+NN` 染色基底变体折叠）；或为染色基底补回对应 re-dyeing 配方。否则在 filters 中排除 `black_wool|+14`/`black_bed|+14` 目标。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0002 | incomplete | bad | bow 不可解析（CASCADE_EXHAUSTED），原版可合成 |
| 0008 | incomplete | questionable | black_wool\|+14 不可解析（RANK_TRUNCATED） |
| 0017 | incomplete | questionable | black_wool\|+14 不可解析 |
| 0029 | incomplete | questionable | black_wool\|+14 不可解析 |
| 0051 | incomplete | questionable | black_wool\|+14 不可解析 |
| 0053 | incomplete | questionable | black_wool\|+14 不可解析 |
| 0054 | incomplete | questionable | black_bed\|+14 不可解析 |
| 0059 | incomplete | questionable | black_wool\|+14 不可解析 |
| 0103 | incomplete | questionable | black_wool\|+14 不可解析 |
| 0121 | incomplete | bad | bow 不可解析，原版可合成 |
| 0122 | incomplete | questionable | black_wool\|+14 不可解析 |
| 0123 | incomplete | questionable | black_wool\|+14 不可解析 |
| 0124 | incomplete | questionable | black_wool\|+14 不可解析 |
| 0125 | incomplete | questionable | black_wool\|+14 不可解析 |
| 0127 | incomplete | questionable | black_wool\|+14 不可解析 |
| 0128 | incomplete | questionable | black_wool\|+14 不可解析 |
| 0130 | incomplete | questionable | black_wool\|+14 不可解析 |
| 0131 | incomplete | questionable | black_wool\|+14 不可解析 |
| 0133 | incomplete | questionable | black_wool\|+14 不可解析 |
| 0134 | incomplete | questionable | black_wool\|+14 不可解析 |
| 0143 | incomplete | bad | stone_axe 不可解析（CASCADE_EXHAUSTED），原版可合成 |
| 0145 | incomplete | bad | bow 不可解析，原版可合成 |
| 其余 128 条 complete 链 | medium/giant/long/short/colossal | ok | 目标可达，真实步骤均为合理原版/MineColonies 配方；均含 F-fc-001 的块缓冲放大环（不影响产物，仅膨胀 crafts/actions） |

## 规则建议（给汇总者）
- targetRegex: `minecraft:black_wool\|\+14|minecraft:black_bed\|\+14`（更通用：`\|(black_wool\|black_bed)\|\+\d+`）—— 排除带块状态变体的染色基底目标；证据 19 条链。
- recipeRegex: `minecraft:(raw_iron_block|diamond_block|emerald_block|redstone_block|bone_block|lapis_block|iron_block|gold_block)↔对应 base` 收敛为 `pattern/block-buffer-amplification`；证据：colossal/giant/long 中普遍存在（0013/0006/0042/0007/0112/0137/0026/0054/0040）。
- builtin 候选: `pattern/block-buffer-amplification`（标签 junk/duplicate 类排除）—— 互逆 9:1↔1:9 块↔粒往返环，零净产出，建议整体排除而非计入。
- 数据修复（非规则）: 配方图补回原版工具合成（stone_axe、bow 及同族），消除 F-fc-002。
- 无新模式时写"无": 本次未见到 fake-production / free-resource / machine-mismatch / impure / worse-alternative / silly-detour / tag-expansion / generation-abuse / catalyst-misuse / data-pollution（除 `|+14` 变体外）。`jei:/synthetic/*` 均为合法 anvil 修复/合成（pattern/exchange），非 fake-production。
