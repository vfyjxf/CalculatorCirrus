# tfc-enhanced batch-004 审查报告
- pack: tfc-enhanced
- 批次: batch-004（written=102；giant 25 / colossal 25 / medium 23 / long 17 / short 7 / incomplete 3，coverage 1214/4249 已保留）
- 审查链数: 102（全量；short 7 + medium 23 + incomplete 3 全读，giant/colossal/long 抽样 + 全量 grep 扫描）
- 发现: critical 0 / major 1 / minor 2

## 发现

### F-tfc-1 | chain=0002,0005,0037,…(共 57 条含 from_* 反向配方) | severity=major | category=loop-or-cycle
- 位置: `[A*] EXECUTE tfc:crafting/rock/mossy_loose/<rock>_from_stairs|from_walls|from_slabs` 与 `tfc:crafting/wood/lumber/<wood>_from_slabs|from_planks`（在 CHAIN 0002/0005/0011/0015/0018/0021/0037/0038/0051/0060/0061/0075 等）
- 问题: TFC 的"形状反向配方"（`*_from_stairs`/`*_from_walls`/`*_from_slabs`/`*_from_planks`）被 `loop=allowBoundedAmplification` 当成放大环使用，形成**无净产出往返**：
  - CHAIN 0002：A1 `chert→chert_stairs(6→8)` → A2 `chert_from_stairs(8→24 loose)` → A3 `loose→cobble(24→6)` → A4 `cobble→chert_wall`。A1–A3 净 6 cobble→6 cobble，纯往返；最终 wall 配方 A4 本就直接吃 cobble（RAW 12）。
  - CHAIN 0005：A1 `slab_from_slabs(6→12 loose)` → A2 `loose→cobble(12→3)` → A3 `cobble→slab(3→6)`，目标 slab 反而作为 RAW 被消耗又被复现，无净产出。
  - CHAIN 0037（trapped_chest/willow）：A28 `lumber_from_planks` → A29 `planks_from_lumber` → A30 `planks→slab` → A33 `lumber_from_slabs`，lumber↔planks↔slab 同样无净环。
- 原因: 规划器把"形状互转"配方当作有净产出的放大步骤；但这些是 1:1 可逆 no-op（cobble↔stairs↔loose↔cobble、lumber↔planks↔slab），不构成有效生产。属已声明 `allowBoundedAmplification` 下放任的 **无净产出往返环**，按口径文档仍判问题。
- 建议: 新增 builtin 标签 `pattern/tfc_shape_roundtrip`，对 `tfc:crafting/(rock|wood)/**/(from_stairs|from_walls|from_slabs|from_planks)` 的环收敛（仅在无净产出时标记，避免误伤目标本身就是反向配方的链如 0004/0006）。

### F-tfc-2 | chain=0052,0054,0055,0058,0063,0075,0083,0092(+giant 3 条) | severity=minor | category=silly-detour（suspected）
- 位置: `[A*] EXECUTE firmalife:crafting/clay_oven_bottom_to_clay` （clay_oven_bottom → clay_ball x5）
- 问题: 制备陶土系产物（terracotta/tiles/rustic_brick/tile_brick）时，以**拆毁 clay oven bottom 方块**换取 clay_ball 作为粘土来源（RAW 中列 `firmalife:clay_oven_bottom`）。该方块本身由粘土制成，拆解为球为净损耗路径，且存在更直接的粘土/粘土球来源。属把成品方块当原料回收的兜圈子。
- 原因: 在锥预算下 clay 直链可能不可达，规划器退而用 reverse 拆解配方取粘土；配方本身真实可执行，故仅标 minor suspected。
- 建议: 若确认 clay 直链可生产，将 `firmalife:crafting/clay_oven_bottom_to_clay` 列入 `pattern/recycling` 类收敛候选（低优先级）。

### F-tfc-3 | chain=0000,0001,0024,0032,…(48 条含 stick bundle/bunch) | severity=minor | category=loop-or-cycle（suspected）
- 位置: `tfc:crafting/stick_bundle` ↔ `tfc:crafting/stick_from_bundle` ↔ `tfc:crafting/stick_bunch` ↔ `tfc:crafting/stick_from_bunch`（如 CHAIN 0000 A10–A13、0024 A1–A5、0050 A4–A13）
- 问题: 大量木制/家具链用 stick bundle/bunch 互转反复产出 stick，链路被拉得很长。每轮确有 net stick 产出，按 `allowBoundedAmplification` 属"有净产出放大"的灰色地带；但 `<= from` 标注在此类链中噪声明显（如上步出 stick 下步入 twig），疑似把 bundle/bunch 当可互逆环用。
- 原因: TFC 真实制棍流程（twig→bunch→bundle→stick）被规划器多层嵌套放大；是否纯 no-op 环证据不足。
- 建议: 暂不打标签；若后续发现其仅做往返不增 stick，再升级为 `pattern/tfc_shape_roundtrip` 子集。

## 链级判定
| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0002–0023（mossy cobble wall/stairs/slab） | loop-or-cycle | bad | cobble↔stairs/wall↔loose 无净往返（F-tfc-1） |
| 0004–0006（mossy loose chalk/marble） | loop-or-cycle | bad | 经 wall/stairs 多步往返回收到 loose，目标即反向配方本身 |
| 0005/0011/0015/0016/0018/0021（slab） | loop-or-cycle | bad | slab→loose→cobble→slab 无净环（F-tfc-1） |
| 0037–0040/0051（trapped_chest/lectern 木形） | loop-or-cycle | bad | lumber↔planks↔slab 往返（F-tfc-1） |
| 0060/0061/0075（polished_diorite/tile_brick） | loop-or-cycle | bad | 借 andesite slab→loose→cobble 往返取料（F-tfc-1） |
| 0052/0054/0055/0058/0063/0083/0092 | silly-detour | questionable | clay_oven_bottom 拆解为粘土源（F-tfc-2） |
| 0000/0024/0032/0043/0047/0084/0086/0093/0096/0097 等木/家具/陶瓷/金属链 | loop-or-cycle(suspected) | questionable | stick bundle/bunch 长放大环（F-tfc-3），余步骤基本合理 |
| 0001/0073（windmill blade, incomplete） | unresolved-cascade | questionable | BUDGET_EXHAUSTED，UNRESOLVED `stick_bunch`(BUDGET_TRUNCATED)/`metal/saw/steel|+8`，均属真实物料，规划器级预算截断 |
| 0069（purple_carpet, incomplete） | data-pollution | questionable | `minecraft:black_wool|+14` UNRESOLVED(RANK_TRUNCATED)，`|+N` 后缀污染（已知规划器级问题） |
| 其余 medium/long/short（terracotta 染色、fence_gate、copper_bulb、planter、grindstone、map 等） | — | ok | 染料/石切/陶土/金属流程正常，无非模式问题 |

## 已知规划器级问题（仅链级提及）
- `|+N` id 后缀污染：54 条链出现（如 `tfc:wood/twig/kapok|+20`、`tfc:rock/cobble/gabbro|+21`、`minecraft:black_wool|+14`），含 0069 的 UNRESOLVED。属 round-4 已列 32 位溢出哨兵/`|+N` 污染，非本批新发现。
- 无净产出往返机制缺失 + 锥预算截断：0001/0073 的 BUDGET_EXHAUSTED 与 BUDGET_TRUNCATED 属规划器级，非链级可修。

## 规则建议（给汇总者）
- targetRegex: `^tfc:rock/mossy_(cobble|loose)/.+_(wall|stairs|slab)$|^tfc:wood/(lumber|planks)/.+_(from_slabs|from_planks)$` —— 收敛"以反向形状配方为目标/中间"的链，证据 57 条（F-tfc-1）。
- recipeRegex: `^tfc:crafting/(rock|wood)/.+/from_(stairs|walls|slabs|planks)$` —— 标记形状往返环配方族，证据 57 条。
- builtin 候选: `pattern/tfc_shape_roundtrip`（junk 级收敛，仅当环无净产出）—— 与已固化 `pattern/storage_ladder`（9:1/4:1 压缩）、`pattern/exchange`（变体环）区分，本包为 TFC 特有的 cobble/loose/wood 形状互转环。证据：0002/0005/0037 等。
- 另：`firmalife:crafting/clay_oven_bottom_to_clay`（reverse 拆解取粘土）建议入 `pattern/recycling` 候选（minor，证据 ~20 条，F-tfc-2）。
- 无全新 fake-production / free-resource（全批 0 条 jei:/trade/quest/reward/JER 标记），无 amount-insanity（无 crafts≥100000 链）。
