# 链审查 Round 6 汇总：选路质量深审（producers 索引交叉核对，2026-08-29）

- 动机：前五轮收敛的是"模式族"；`worse-alternative`/`silly-detour`/`impure-recipe` 这三类
  **选路质量**问题因缺 producers 索引，一直只能标 suspected。本轮为 22 包建齐索引
  （`cirrus-audit-data/<pack>/producers.tsv|jsonl`，复用 `build_producer_index.py`），
  22 个 agent 对每包最新有内容批次做索引交叉核对。
- 每链的非平凡 EXECUTE 产出物 → 查索引生产者数 → 比对所选 vs 替代者的输入/直连性/杂产。
- 生成器/导出伪影按口径排除；唯一生产者的选择一律 ok。

## 结论：**16/22 包零发现**——planner 的生产者选择总体合理

多生产者物品的选取绝大多数为最直连/等价/可辩护（矿石选 blast_furnace、石变体选直切、
Create 机械 vs 工作台等价、利用副产的多产配方等）。零发现包：All Of Create、atm10、
BlockFront、Brick's、civilization-isolaria、cobblemon、create-lets-create、create-train、
CreateCustomised、enigmatica10、fom、LBC、minecolonies、atm10-lite(minor×1 可辩护)、
mythcrafts(见下)——详见各报告。

## 发现：7 major / 4 minor（选路层面，全部带索引证据）

| 包 | 严重度 | 问题 | 应选（索引证据） |
|---|---|---|---|
| farmers-colonies | major×2 | blue_dye 走 lapis（连带 13 段 storage_ladder）；white_dye 走 bone_meal（5 段放大），7 链 | `blue_dye_from_cornflower`（1 矢车菊）、`white_dye_from_lily_of_the_valley`（1 铃兰） |
| tfc-enhanced | major | blue_dye 走深板岩青金石矿 4 步链，6 链 | `blue_dye_from_cornflower`（raw leaf 1 步） |
| lost-world | major×3 | mossy_cobblestone 走 moss_block+cobblestone；granite wall 绕两步；blue_dye 同上 | `from_vine`（单输入）、`from_granite`（直连 1:1）、cornflower |
| technical-electrical | major | red_dye 走 IE 铅矿 4 步子链（`smelting/lead_red`），3 链 | poppy/beetroot/tulip 等 6 个单物品配方 |
| create-oneblock | major | leather 走"合成鞍→粉碎"净亏环（每轮 -5 皮），5 链 33 次循环 | `minecraft:leather`（rabbit_hide×4，12 生产者中 0 次被选） |
| create-ultimate | major×2 | gunpowder 走 gunpowder_pinch 循环放大（输入依赖 gunpowder 自身），17+ 层 | 直连 `minecraft:gunpowder`（同链已在用） |
| mythcrafts | major | sophisticatedstorage 升级件绕 backpacks 跨模转换 +5 木板 | 同模直接生产者（输入等价） |
| creative-drawers | minor×2 | stone/glass 走 occultism marid 仪式而非熔炉（I/O 相同） | `minecraft:smelting` 熔炉配方 |
| atm10-lite | minor | mekanism 锯切多产配方取单品（副产被链复用，可辩护） | — |

## 跨包系统性模式：原版染料矿源偏好（已固化）

blue/white/red_dye 四包复现同一偏好：planner 选 `minecraft:<色>_dye`（矿/骨粉源）而
无视 `*_from_<flower>` 单物品直连。根因推断：花是 raw leaf（无生产者），评分层把
"有生产者的输入"（lapis/bone/lead）当作更可得——**raw leaf 应视为最廉输入**。
已固化：builtin `cirrus:mineral_dye_sources`（advisory `pattern/mineral_dye_source`，
仅 blue/white 精确匹配，不误伤 green/brown/black 等无花源染料）+ 3 包过滤器。

## 其他规则动作

- builtin（34 条）：`resource_generators` += `occultism:miner`、`nucleosynthesizing`
  （atm10-lite 分类缺口，502 次未标 generation）
- 过滤器（8 条）：lead_red、leather 粉碎环、gunpowder_pinch 环、storage↔backpacks
  跨模转换、marid 仪式 vs 熔炉、染料矿源×3 包
- 索引层建议（记档）：构建 producers 索引时过滤 `minecraft:enchantment.`（~4 万条）与
  quest 展示页伪生产者，避免自动化"输入更少=更优"扫描误报

## 规划器层 backlog（选路质量专项）

1. raw-leaf 输入（包内无生产者的常见物）应在评分中视为最廉可得，优先于矿产多步链
2. "单输入/直连优先"权重：同输出下 1 输入配方 vs 多输入合成、1 步 vs 2 步绕行
   （mossy from_vine、granite 直连、同模直产 vs 跨模转换）
3. 净亏双步环（craft X→crush X，每轮净损）应被环检测捕获——与 no-op 环同类但更糟
4. 循环依赖生产者（输入含自身产物，如 gunpowder_from_pinches）应天然劣后
