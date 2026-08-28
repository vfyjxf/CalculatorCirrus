# 链审查 Round 1 汇总（2026-08-29）

- 范围：21 包 × 最新批次（20 包 batch-001，LuckyBlockChallenge batch-002），共约 3,089 条链
- 审查方式：每包一个 swarm agent，按 `chain-review/BATCH-REVIEW-BRIEF.md` 口径审查；逐包报告见本目录
- 汇总发现：**critical 15 / major 106 / minor 18**

## 1. 逐包统计

| 包 | 批次 | 审查链数 | critical | major | minor | 主要问题 |
|---|---|---|---|---|---|---|
| All Of Create 2.1 | 001 | 150 | 0 | 2 | 0 | 钢锭↔粒零净产出环；`\|+N` 状态后缀污染 |
| atm10 | 001 | 150 | 1 | 4 | 0 | 压缩↔解压横跳；INT_MAX 溢出；`\|+N` 变体 7.9 万行 |
| atm10-lite | 001 | 145 | 0 | 4 | 1 | atc 压缩环；`minecraft:enchantment.` 泄漏 145/145；laser_drill 溢出 |
| BlockFront | 001 | 80 | 0 | 7 | 1 | 原版 8 类块↔物零净产出往返 |
| Brick's Building | 001 | 150 | 0 | 3 | 2 | quark slab_to_block 泛化爆炸；crate/铁块往返 |
| civilization-isolaria | 001 | 150 | 0 | 4 | 1 | 红石/原矿块往返；IE 钢三态互逆 |
| cobblemon-x-create | 001 | 150 | 0 | 2 | 1 | 互逆对 3^n 指数放大；煤循环到 INT_MAX |
| create-lets-create | 001 | 150 | 1 | 2 | 2 | atc 压缩阶梯 142/142；INT/UINT_MAX 溢出 |
| create-oneblock | 001 | 150 | 0 | 2 | 1 | 贵重资源放大到 INT_MAX；块/矿/粒几何放大 |
| create-train | 001 | 150 | 0 | 4 | 1 | sugar_cane 压缩空转 1402 次；slab_to_block 拖入 530+ 原料 |
| create-ultimate | 001 | 150 | 0 | 2 | 1 | 净零块↔物往返全批；级联 unresolved 36 链 |
| CreateCustomised1.1.2 | 001 | 150 | 8 | 40 | 2 | diamond/rice 放大至 MAX_VALUE；9^7 级零净往返 40+ 链 |
| creative-drawers-producer-2 | 001 | 150 | 0 | 5 | 0 | atc 环 644 邻接对；cardboard/sourceberry 振荡 |
| enigmatica10 | 001 | 150 | 2 | 6 | 1 | chipped/chisel stonecutting 32,877 步吞预算→原版物级联未解析 |
| farmers-colonies | 001 | 150 | 1 | 2 | 0 | 块↔粒往返；原版工具配方缺失致 anvil 放大环 |
| fom | 001 | 113 | 0 | 3 | 1 | 红石/原矿/纸板往返；exchange 树悬空级联 90+ 链 |
| lost-world | 001 | 150 | 0 | 4 | 0 | anvil/enchantment 泄漏 92/38/48 链；emerald/nether_star 振荡 |
| LuckyBlockChallenge | 002 | 1 | 0 | 0 | 1 | 目标池近乎耗尽（本批仅写出 1 条）；planks 标签收敛缺陷 |
| mythcrafts | 001 | 150 | 1 | 3 | 0 | `\|+N` 污染 146/150；overleveling 112× 吹爆预算 |
| technical-electrical | 001 | 150 | 0 | 3 | 2 | no-op 往返 2966 次/148 链；损材回收绕路；感应单元 crafts 失控 |
| tfc-enhanced | 001 | 150 | 1 | 4 | 0 | slime⇄glue 环耗尽预算致 6 链不可用；any-of 标签全展开进 RAW；锯耐久误计消耗 |

## 2. 跨包系统性模式（按影响排序）

### P1 无净产出往返环（19/21 包，本轮最大噪声源）
`loop=allowBoundedAmplification` 把互逆配方对（A→B、B→A 净产出为 0）当作放大环展开：
- 原版 9:1/4:1 块↔物对（redstone/raw_iron/bone/lapis/diamond/coal/emerald/slime/honey…）
- allthecompressed compress↔decompress 多级横跳、quark compressed↔uncompress
- IE 钢三态（ingot↔nugget↔storage）、crate 往返、relic coin↔pouch（3^n）、dndesires 煤对

### P2 需求计数 32 位溢出（8 包）
放大环内计数触达 2,147,483,647（INT_MAX）甚至 2^33/UINT_MAX，crafts 达 10^9–10^13，
链整体不可用。根因：`allowBoundedAmplification` 计数器用 int 累加。

### P3 `|+N` 块状态后缀污染物品 id（5 包）
`minecraft:black_wool|+14`、`polished_cut_andesite_stairs|+14`、`dark_oak_log|+267` 等
幽灵 id 进入需求/UNRESOLVED（atm10 单批 7.9 万行输入 + 3783 未解析），级联造成
大量假 incomplete。根因：导出/解析层把方块状态序列化进了物品 id。

### P4 anvil/enchantment toString 泄漏（5 包）
`minecraft:anvil.self_repair.*`、`minecraft:anvil.materials_repair.*`、
`minecraft:enchantment.*`、`crafting_on_a_stick:anvil` 产出乱码 id 进入链。

### P5 泛化 any-of 配方输出爆炸（3 包）
quark `slab_to_block`（单配方展开 ~80 方块）、chipped/chisel stonecutting
（enigmatica 单批 32,877 步）作为"生产基材"吞掉锥预算 → 原版常见物被
BUDGET_TRUNCATED 级联成未解析。

### P6 其他（单包或规划器层）
- laser_drill 等生成配方被放大（已扩入 builtin `resource_generators`）
- tfc：催化剂（锯/锤耐久）被计为消耗品；any-of 标签全成员进 RAW
- farmers-colonies：原版工具（stone_axe/bow）配方缺失走 anvil 环（数据缺口）
- LBC：标签展开后自制分支未被消费、依赖边标注不一致

## 3. 本轮固化的规则

### builtin_rules.json（28 条规则，新增 5 + 扩展 2）

| 规则 | 匹配 | 标签 | 证据 |
|---|---|---|---|
| `cirrus:anvil_leak_recipes`（新） | `^minecraft:anvil\.(self_repair\|materials_repair)`、`^minecraft:enchantment\.`、`^crafting_on_a_stick:anvil$`、`enchantment\.lvl_\d+` | quality/junk | atm10-lite 145/145、lost-world 92/38/48、Brick's 5 |
| `cirrus:storage_compression_ladders`（新） | allthecompressed 压/解、quark compressed、crate 往返、IE 钢三态、create cardboard | pattern/storage_ladder | ≥7 包，最高 148/149 链 |
| `cirrus:slab_to_block_generalized`（新） | `quark:tweaks/crafting/slab_to_block` | pattern/exchange | Brick's 140/150、create-train 110 |
| `cirrus:chisel_stonecutting_variants`（新） | `stonecutting/(chipped\|chisel)_` | pattern/exchange | enigmatica10 32,877 步 |
| `cirrus:salvage_mod_roundtrips`（新） | mod=create_salvage | intent/recycling | CreateCustomised diamond↔nugget |
| `cirrus:resource_generators`（扩展） | + `laser_drill` | intent/generation | atm10-lite 136/145 |
| `cirrus:recycling_recipes` 等既有规则保持不变（salvage 命名空间走新 mod 规则） | | | |

排除类（quality/*）只加了 anvil 泄漏（纯乱码产出）；压缩阶梯用 advisory 标签
（合法存储配方，不能整体排除）。

### filters.json（35 条，覆盖 20 包）

要点：atc-ladder×5 包、occultism ritual_dummy 目标（atm10-lite）、
bettercampfirepot/createcasing/cobblefurnies/green_concrete 目标（cobblemon）、
mekanism 感应单元目标（technical-electrical）、smithing_template 目标（create-ultimate）、
其余为各包零净产出往返族的收敛标记。明细见各包 `state/filters.json`。

## 4. 规则层无法表达、需规划器/导出层修复（后续 backlog）

1. **P0** 放大环计数用 long + 溢出熔断（≥INT_MAX 判 bad）
2. **P0** 物品 id 归一化剥离 `\|\+[0-9]+` 块状态后缀，回退基物品再求解
3. **P1** no-op 往返环检测：互逆配方成对出现且物品计数回起点 → 不展开
4. **P1** 原版常见物（planks/ingot/redstone/diamond…）预算保护，先于装饰变体解析
5. **P2** 催化剂耐久不当消耗计入；any-of 标签 RAW 只取代表成员
6. **P2** 依赖边标注与真实输入一致性校验（LBC A7/A8）

## 5. 下一轮

`bash chain-review.sh next` 生成 batch-002（其余 20 包）/batch-003（LBC 视目标池情况），
观察覆盖率与 filterHitPct；对未收敛包做 Round 2 审查。
