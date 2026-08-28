# technical-electrical batch-004 审查报告

- pack: technical-electrical
- 批次: batch-004（written=35；类别分布：incomplete 28、colossal 6、long 1）
- 审查链数: 35（全量审查——总数仅 35 < 100 配额下限，故不完整/巨型文件均按链边界抽样 + 关键链全读；incomplete 大文件 12364 行按链边界拆分，每链至少扫头部 60–170 行 + 定向 grep）
- 发现: critical 0 / major 2 / minor 1
- 已知规划器级问题（仅一句话提及，不计入发现）：`|+N` id 后缀污染（stick|+1 / magenta_stained_glass|+16 / venus_diamond_ore|+4 / primalmagick:skyglass|+5 / stripped_cherry_wood|+280 等贯穿全批）；无净产出往返机制（所有 colossal/mega-wide 的 crafts 暴涨均来自已带标签的 storage_ladder/exchange 往返，非新问题）；any-of 基底坍缩（#钻石矿 / #楼梯 等 tag 全展开为多个 mod 变体）。

## 发现

### F-te-1 | chain=0002,0003,0015,0016,0023,0024,0026,0027,0028,0029,0030 | severity=major | category=worse-alternative

- 位置: 例如 CHAIN 0002 `[A14]→[A16]→[A17]→[A18]→[A21]`；CHAIN 0015 `[A14]→[A16]→[A17]→[A18]→[A21]`（同结构在 11 条链复现）
- 问题: 规划器先由 `mekanism:processing/osmium/ingot/from_dust_blasting`（A14）**直接产出 9 个 ingot_osmium**，随后却走 `mekanismtools:osmium/tools/shovel`×9（A16，耗 9 ingot + 18 stick）→ `mekanismtools:osmium/nugget_from_smelting`×9（A17，熔 9 把工具→9 nugget）→ `mekanism:processing/osmium/ingot/from_nuggets`（A18，9 nugget→1 ingot），最后 `mekanism:steel_casing`（A21）只需求 **1 个 ingot_osmium**。净效果：9 ingot → 9 工具 → 9 nugget → 1 ingot，材料净亏损且路径荒谬；而 A14 已有的 9 ingot 本可直接喂 A21。
- 原因: 规划器缺少直接的 `osmium_nugget` 合成边，退而用「造工具→熔工具」回收 nugget；加之 bounded-amplification 把该分支放大到 ×9。同链内已存在更优直接路径（A14 直出 ingot），属更差路径。
- 建议: 新增 `pattern/tool_ingot_recovery` 标签或 worse-alternative 降权规则——当「造 mekanismtools 工具→熔炼回收 ingot/nugget」的上游已存在同物直出 ingot 时，剔除该工具中转分支。证据：上述 11 条链（0002/0003/0015/0016/0023/0024/0026/0027/0028/0029/0030，其中 0030 为 bronze  shovel 同构）。

### F-te-2 | chain=0004,0003,0002,0017（及同类未带标签 9:1/4:1 往返） | severity=major | category=loop-or-cycle（已带标签家族仍失控）

- 位置:
  - CHAIN 0004 `[A2]…[A44]`：`oritech:biomass_blockblock`（90 biomass→10 block）↔ `oritech:biomass_blockblockinv`（10 block→90 biomass）无净产出往返，重复至 A44，贡献 `crafts=4244`。
  - CHAIN 0003 `[A17]…[A33]`：`electrodynamics:aluminum_ingot_from_storage_block`（1 block→9 ingot）↔ `immersiveengineering:ingot_aluminum_to_storage_aluminum`（9 ingot→1 block），9:1 往返重复 8 次。
  - CHAIN 0002 `[A8]→[A9]`：`minecraft:iron_nugget`（18 ingot→162 nugget）↔ `iron_ingot_from_nuggets`；以及 `[A1]…[A16]` IE `raw_aluminum_to_raw_block` 9:1 往返。
  - CHAIN 0017 `[A3]…[A11]`：`ae2:decorative/fluix_block`（4 crystal→1 block）↔ `ae2:misc/deconstruction_fluix_block`（1 block→4 crystal）4:1 往返重复 9 次。
- 问题: 上述均为 9:1 / 4:1 的 block↔ingot/nugget 互逆压缩解压族，**本应被 `pattern/storage_ladder` 覆盖，但本批这些具体配方未带 `[rule-tag: pattern/storage_ladder]`**，导致无净产出往返未被收敛，直接把 crafts/深度推爆（0004 crafts=4244、0003 depth=52、0017 depth=10 内塞了 9 层往返）。
- 原因: storage_ladder 标签正则未覆盖 mod 特定的 `*_blockblock*`、`*_ingot_from_storage_block`、`*_to_storage_*`、`raw_*_to_raw_block_*`、`minecraft:iron_nugget`（nugget 9:1）、`ae2:*fluix_block*` 等变体。属「无净产出往返机制」已知规划器问题在漏标转换上的具象化。
- 建议: 扩展 `cirrus:pattern/storage_ladder` 正则，纳入 `.*blockblock.*|.*_ingot_from_storage_block|.*_to_storage_.*|raw_.*_to_raw_block_.*|minecraft:iron_nugget|ae2:.*fluix_block.*`。证据条数：4 条代表链 + 同类未带标签转换散布于 0002/0003/0004/0017。

### F-te-3 | chain=0002 | severity=minor | category=tag-expansion-overreach

- 位置: CHAIN 0002 `[RAW]` 段与 `[A47]`
- 问题: 钻石输入将 `#钻石矿` 类 tag 坍缩为 5 个互不相同的 mod 矿（`stellaris:venus_diamond_ore`×18、`stellaris:mars_diamond_ore`×18、`pastel:blackslag_diamond_ore`×18、`minecraft:deepslate_diamond_ore`×18、`minecraft:diamond_ore`×18，共 90 矿），但实际仅 `[A47]` 消耗 `venus_diamond_ore` 产出 9 钻石。即 90 矿换 9 钻石，其余 4 个 mod 矿为冗余多源采集。
- 原因: any-of 基底坍缩把 `#forge:ores/diamond`（或等价 tag）全展开为全部 mod 变体并各自预算，规划器未择优单一来源。属已知规划器问题，降级为 minor（非错误配方，仅为预算/覆盖产物）。
- 建议: 钻石类 tag 仅保留一个稳定来源（如原版 `deepslate_diamond_ore` 或任一 mod 矿）参与预算，抑制多源冗余采集。证据：CHAIN 0002 RAW 段 5 类钻石矿各 ×18。

## 链级判定

| chain | category | verdict | 一句话 |
|---|---|---|---|
| 0000 | colossal | ok | 全 tagged storage_ladder 往返（scanner_door），已知往返问题 |
| 0001 | colossal | ok | tagged storage_ladder/recolor 往返，已知 |
| 0002 | incomplete | questionable | F-te-1 工具中转 detour + F-te-3 钻石多源坍缩 |
| 0003 | incomplete | questionable | F-te-1 工具 detour + F-te-2 未标 aluminum storage 往返 |
| 0004 | incomplete | questionable | F-te-2 oritech biomass 未标 9:1 往返（crafts=4244） |
| 0005 | incomplete | ok | tagged exchange/storage_ladder 往返为主 |
| 0006 | incomplete | ok | tagged storage_ladder 往返为主 |
| 0007 | incomplete | ok | tagged storage_ladder 往返为主 |
| 0008 | incomplete | ok | tagged 往返 + UNRESOLVED 预期内（meka 套件） |
| 0009 | incomplete | ok | 全 tagged storage_ladder 往返（crafts=13132，已知往返） |
| 0010 | incomplete | ok | tagged exchange×50/storage_ladder×62 往返（crafts=39326，已知） |
| 0011 | incomplete | ok | tagged storage_ladder 往返为主 |
| 0012 | incomplete | ok | tagged storage_ladder 往返为主 |
| 0013 | colossal | ok | tagged storage_ladder 往返，已知 |
| 0014 | incomplete | ok | tagged storage_ladder 往返为主 |
| 0015 | incomplete | questionable | F-te-1 工具 detour（osmium shovel） |
| 0016 | incomplete | questionable | F-te-1 工具 detour（osmium shovel） |
| 0017 | long | questionable | F-te-2 ae2 fluix 4:1 未标往返 A3–A11 |
| 0018 | incomplete | ok | 全 tagged storage_ladder 往返（crafts=38893，已知） |
| 0019 | colossal | ok | tagged storage_ladder/recolor 往返，已知 |
| 0020 | colossal | ok | tagged storage_ladder 往返，已知 |
| 0021 | incomplete | ok | tagged 往返为主 |
| 0022 | incomplete | ok | tagged storage_ladder 往返为主 |
| 0023 | incomplete | questionable | F-te-1 工具 detour（osmium shovel） |
| 0024 | incomplete | questionable | F-te-1 工具 detour（osmium shovel） |
| 0025 | colossal | ok | tagged storage_ladder 往返，已知 |
| 0026 | incomplete | questionable | F-te-1 工具 detour（osmium shovel） |
| 0027 | incomplete | questionable | F-te-1 工具 detour（osmium shovel） |
| 0028 | incomplete | questionable | F-te-1 工具 detour（osmium shovel） |
| 0029 | incomplete | questionable | F-te-1 工具 detour（osmium shovel） |
| 0030 | incomplete | questionable | F-te-1 工具 detour（bronze shovel，同构）+ tagged exchange×91 往返 |
| 0031 | incomplete | ok | tagged storage_ladder 往返为主 |
| 0032 | incomplete | ok | tagged storage_ladder 往返为主 |
| 0033 | incomplete | ok | 全 tagged storage_ladder（prefab compressed glowstone）往返，已知 |
| 0034 | incomplete | ok | 全 tagged storage_ladder 往返（crafts=37173，已知） |

## 规则建议（给汇总者）

- targetRegex: 无新增（本批目标均为合法合成物，无 junk/奖励页目标混入）。
- recipeRegex:
  - 扩展 storage_ladder 覆盖 `.*blockblock.*|.*_ingot_from_storage_block|.*_to_storage_.*|raw_.*_to_raw_block_.*|minecraft:iron_nugget|ae2:.*fluix_block.*` —— 收敛未带标签的 9:1/4:1 互逆压缩解压族；证据：CHAIN 0004/0003/0002/0017（共 4 条代表 + 散布同类）。
  - 新增 worse-alternative 降权：`mekanismtools:.*/nugget_from_smelting` 且其上游为同批刚合成的 mekanismtools 工具、且同链已存在该 ingot 直出边 —— 抑制「造工具→熔工具回收 ingot」；证据：11 条链（0002/0003/0015/0016/0023/0024/0026/0027/0028/0029/0030）。
- builtin 候选: 将 `cirrus:pattern/storage_ladder` 泛化到 mod 特定 block↔ingot/nugget（oritech biomass、electrodynamics/immersiveengineering aluminum storage、IE raw_*_block、ae2 fluix）—— 标签 `pattern/storage_ladder`，证据同上 4 条链。
- 无完全新模式：本批未出现 fake-production / free-resource / generation-abuse（无 jei: 合成奖励/交易/JER 展示页，仅 `create:book` 等合法配方）；剩余异常 90% 为已固化标签的往返放大 + 漏标 storage_ladder 变体 + any-of 坍缩，符合"该包已收敛"判断。
