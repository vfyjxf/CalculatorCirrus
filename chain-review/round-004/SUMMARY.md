# 链审查 Round 4 汇总（2026-08-29）

- 范围：13 个未收敛包的 batch-004（LBC batch-005、minecolonies batch-003），约 880 条链
- 汇总发现：**critical 0 / major 10 / minor 11**（round-1→4: 15/106/18 → 3/42/9 → 1/25/8 → 0/10/11，major 持续收敛）
- **5 包审查零新模式**（create-oneblock、fom、lost-world、LBC 仅 1 漏标、BlockFront 仅 1 漏标）——审查层已收敛

## 1. 逐包新发现

| 包 | 链数 | c/m/mi | 新问题 |
|---|---|---|---|
| All Of Create 2.1 | 66 | 0/2/0 | `create_compressed:` 命名空间整体漏标（882 未标步）；dndesires/ironworks/ae2 fluix 等长尾 |
| BlockFront | 59 | 0/0/1 | dried_kelp 阶梯漏标（唯一缺口） |
| cobblemon-x-create | 43 | 0/2/3 | 装饰 plaque 目标 + redye 死支路（1162 步零消费）；plaque 烧炼被当主铁源；potion NBT toString 污染 |
| create-oneblock | 99 | 0/0/0 | 无新模式（残留全为已知溢出/污染症状） |
| create-ultimate | 97 | 0/2/0 | dndesires shard 环 686 处；cgs raw_lead 9:1 环 |
| CreateCustomised1.1.2 | 43 | 0/0/3 | `*_via_vanilla_stripping` 无机器归属；已带 recycling 标签仍 INT_MAX 失控 |
| farmers-colonies | 70 | 0/1/0 | **anvil 缺口**：`jei:/synthetic/*` @ `minecraft:anvil` 附魔应用被误标 exchange，伪造 stone_axe 1→5 |
| fom | 48 | 0/0/0 | 无新模式；incomplete 全因 `\|+N` 污染 |
| lost-world | 100 | 0/0/0 | 无新模式；已带 recycling 标签的 salvage 环仍无界放大（规划器层） |
| LuckyBlockChallenge | 59 | 0/0/1 | dried_kelp 漏标（唯一缺口） |
| minecolonies-dimensional-adventure | 65 | 0/1/1 | quartz_flake/quartz_bone/allium 可逆对；cb/sc 变体环 |
| technical-electrical | 35 | 0/2/1 | mekanismtools 工具→nugget 回收绕路（11 链）；fluix/aluminum/iron_nugget 阶梯长尾 |
| tfc-enhanced | 102 | 0/1/2 | **TFC 形状反向环**（from_stairs/walls/slabs/planks，57 链）；clay_oven 拆解取粘土 |

## 2. 本轮规则动作

### builtin_rules.json（33 条）
- `storage_compression_ladders` += `^create_compressed:`、`dried_kelp(_block)`、通用 `raw_[a-z_]+_block$`、
  `(shard|flake)s?_from_`/`_from_(shard|flake)s` 对、`fluix`
- 新增 `cirrus:anvil_system_recipes`（system=`minecraft:anvil` → junk，堵 synthetic 附魔应用缺口）
- 新增 `cirrus:shape_reverse_roundtrips`（`from_(stairs?|walls?|slabs?)$` → pattern/exchange，TFC 57 链主因）
- `cirrus:recolor_recipes` += `redye`（plaque redye 1162 步）
- `cirrus:recycling_recipes` += `nugget_from_smelting`、`clay_oven_bottom_to_clay`
- `cirrus:cobble_generators` += `cobblestone_gen`、`lava_from_cobble`

### 过滤器（13 条，10 包）
plaque 目标/支路（cobblemon）、stripping（CreateCustomised）、shape 环（tfc）、
工具回收绕路（technical-electrical）、各包阶梯长尾。

## 3. 规划器层确认（收敛后剩余噪声全部归因于此）

1. 32 位溢出哨兵（create-oneblock 285 处、CreateCustomised 536 处、lost-world 全部 incomplete）
2. `|+N` id 后缀污染（LBC/fom 全部 incomplete 的根因）
3. 已带标签（storage_ladder/recycling/exchange）仍无界放大——标签不约束展开，需折叠护栏
4. any-of 基底坍缩与 tag 全展开（#planks 519 成员、#钻石矿 5 源冗余）
5. potion NBT toString 进入 id（cobblemon 231 处）

## 4. 结论

新配方族发现已收敛至每包 ≤1 个长尾项；剩余问题全部为规划器/导出层。
下一批验证过滤器命中率是否将各包推过 85% 收敛线，然后收尾。
