# create-train batch-001 审查报告
- pack: create-train
- 批次: batch-001（written=150；类别分布：incomplete 150）
- 审查链数: 150（全量定向模式扫描覆盖全部 150 链 + 11 条深读：0000/0001/0014/0024/0025/0046/0050/0053/0060/0068/0128 + index.tsv 全量）
- 抽样说明: incomplete 单文件 24MB / 150 链 ≈ 164KB/链 > 100KB/链 阈值，按口径抽样下限降为 40；实际以全文件 grep（6 模式）+ 11 条深读覆盖全部 150 链。
- 发现: critical 0 / major 4 / minor 1
- 合规: 未报告 builtin-rule-tags（exchange/recolor/recycling）、+raw 叶子、allowBoundedAmplification 净产出放大；jei:category/* 与 jei:/synthetic/* 均按真实机器系统/合法合成 id 处理，未误判 fake-production（全批未出现 quest/reward/villager/trade/JER 展示类 id）。

## 发现
### F-create-train-001 | chain=0050 | severity=major | category=amount-insanity
- 位置: dndesires:crafting/coal_piece_from_coal（A151–A159）与 dndesires:crafting/coal_from_coal_piece；chain 0050 status crafts=22100638971
- 问题: 规划器把 `coal → coal_piece(×8) → coal` 当放大器反复执行：A151 128 coal→1024 coal_piece，A152 还原 1024 coal，A153 再×8 到 8192 coal_piece … 一路滚到 A159 的 coal_piece x4194304 / coal x4194304。单链累计 crafts 达 221 亿。同链还有 molten_bronze x192000（casting 环）、water x128000（湿海绵环）等数量级失控。
- 原因: `loop=allowBoundedAmplification` 被 coal_piece↔coal 的 1:8 互转当成"净产出"放大器滥用；dndesires 该配方实为 1 coal=8 coal_piece、1 coal_piece=1 coal 的等效互转，构成无成本复制。同模式出现在 15 链（0000/0001/0002/0003/0004/0011/0013/0025/0036/0037/0040/0050/0051/0053/0060）。
- 建议: 将 dndesires:crafting/coal_piece_from_coal 与 coal_from_coal_piece 收敛为 loop / 标记 pattern-amplification，禁止作为生成源；或对该配方关闭 boundedAmplification。

### F-create-train-002 | chain=0024 | severity=major | category=loop-or-cycle
- 位置: createbigcannons:compacting/forge_bronze_nugget 与 melting/melt_bronze_nugget（A56–A66）
- 问题: molten_bronze x192000 ↔ bronze_scrap x192 在 A58↔A59 间空转：A58 192000 molten→192 scrap，A59 192 scrap→192000 molten，A60–A65 继续重复同一往返，无净产出。A56/A57 仅做桶的灌/排空，仍是同一桶内熔体空转（molten_bronze x192000 在文件中出现 12 次，全部位于本链）。
- 原因: 规划器把 createbigcannons 的熔体/锭往复配方当生成源，形成无净产出的互逆 no-op 环（compress↔decompress 类）。
- 建议: 对 createbigcannons 的 molten_bronze↔bronze_scrap 互逆配方收敛为 loop（禁止重复往返），或限制单链内该配对迭代次数。

### F-create-train-003 | chain=0053（代表性；93 链共有） | severity=major | category=loop-or-cycle
- 位置: quark:building/crafting/compressed/sugar_cane_block 与 .../sugar_cane_blockuncompress（如 0053 A1–A3、0050 A2–A5）
- 问题: sugar_cane(9)→block(1)→sugar_cane(9) 的压缩/解压空转在 150 链中出现 1402 次、横跨 93 链。净产出为零（9→1→9 还原），却被 allowBoundedAmplification 当糖甘蔗"来源"插入。
- 原因: quark 压缩方块与对应解压互逆配方被规划器当成生成路径；属口径明确点名的 compress↔decompress no-op 环。
- 建议: 将 quark:building/crafting/compressed/* 与对应 *uncompress 配对收敛为 loop / pattern，禁止作为原料生成源。

### F-create-train-004 | chain=0128（代表性；110 链共有） | severity=major | category=tag-expansion-overreach
- 位置: quark:tweaks/crafting/slab_to_block（如 0128 A7、0000 A7）
- 问题: 该配方被表示为"任意 slab → 50+ 种无关 block"的 any-of 泛化产物。链 0128 输入 polished_cut_dolomite_slab|+530 x2，输出列了约 50 种不同 mod 的方块（black_kaolin_bricks、aspen_planks、shingles、white_mangrove_planks …），随后链被拖入 530+ 跨 mod 原料池，真正的目标原料 mossy_white_dacite_bricks 始终未产出，链以 [UNRESOLVED] reason=CASCADE_EXHAUSTED 结束。
- 原因: 数据层把 slab_to_block 的输出 tag 展开成所有 slab→block 结果的并集，语义不符成员被一并引入，造成 mega-wide 爆炸式原料树（110/150 链含此配方）。
- 建议: 收敛 quark:tweaks/crafting/slab_to_block 为逐 slab 的精确配方（或标记 duplicate/pattern-exchange 排除其泛化输出），避免 530+ 原料池注入。

### F-create-train-005 | 系统性（52 链 CASCADE_EXHAUSTED；多链含 vanilla UNRESOLVED） | severity=minor | category=unresolved-expected-item (suspected)
- 位置: 多处 [UNRESOLVED] minecraft:iron_ingot / gold_ingot / redstone / diamond / copper_ingot / lapis_lazuli（candidates=5, reason=BUDGET_TRUNCATED）
- 问题: 原版常见物品 iron_ingot、gold_ingot、redstone、diamond、copper_ingot、lapis_lazuli 在多链中未解析，但 reason 均为 BUDGET_TRUNCATED 且 candidates=5，说明规划器已找到配方、仅因预算耗尽未展开。
- 原因: 本批 150 链全部 maxRootInputBudget [BUDGET_EXHAUSTED]，cone 过深（depth 8–269），导致本可解析的原版物级联失败（CASCADE_EXHAUSTED 占 52 链）。属预算/锥深度配置问题，非数据缺陷。
- 建议: 提高 maxRootInputBudget 或收紧 cone（先收敛 F-001~004 的放大/泛化噪声后预算可覆盖真实配方）；不作为单链 critical 处理。

## 链级判定
| chain | category | verdict(ok/questionable/bad) | 一句话 |
| 0000 | +incomplete+raw2 | questionable | coal_piece↔coal ×8 放大环 |
| 0001 | +incomplete+raw3 | questionable | coal_piece↔coal ×8 放大环 |
| 0002 | +incomplete+raw7 | questionable | coal_piece↔coal ×8 放大环 |
| 0003 | +incomplete+raw4 | questionable | coal_piece↔coal ×8 放大环 |
| 0004 | +incomplete+raw3 | questionable | coal_piece↔coal ×8 放大环 |
| 0005 | +incomplete+raw1 | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0006 | +incomplete+raw1+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0007 | +incomplete+raw4+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0008 | +incomplete | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0009 | +incomplete+raw1 | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0010 | +incomplete+raw2+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0011 | +incomplete+raw2 | questionable | coal_piece↔coal ×8 放大环 |
| 0012 | +incomplete+raw1+mega-wide | questionable | slab_to_block 泛化(50+ block)产物 |
| 0013 | +incomplete+raw3 | questionable | coal_piece↔coal ×8 放大环 |
| 0014 | +incomplete+raw1+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0015 | +incomplete+raw2 | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0016 | +incomplete+raw1 | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0017 | +incomplete | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0018 | +incomplete+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0019 | +incomplete+raw3+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0020 | +incomplete+raw9+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0021 | +incomplete+raw2+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0022 | +incomplete+raw3+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0023 | +incomplete+raw3+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0024 | +incomplete+raw4 | bad | molten_bronze x192000 往返空转环 |
| 0025 | +incomplete+raw16+mega-wide | questionable | coal_piece↔coal ×8 放大环 |
| 0026 | +incomplete+raw3+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0027 | +incomplete+raw16+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0028 | +incomplete+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0029 | +incomplete+raw3+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0030 | +incomplete+raw3+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0031 | +incomplete+raw2 | questionable | slab_to_block 泛化(50+ block)产物 |
| 0032 | +incomplete+raw1 | questionable | slab_to_block 泛化(50+ block)产物 |
| 0033 | +incomplete+raw1+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0034 | +incomplete+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0035 | +incomplete+raw8+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0036 | +incomplete+raw3 | questionable | coal_piece↔coal ×8 放大环 |
| 0037 | +incomplete+raw2 | questionable | coal_piece↔coal ×8 放大环 |
| 0038 | +incomplete+raw3+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0039 | +incomplete+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0040 | +incomplete+raw3+mega-wide | questionable | coal_piece↔coal ×8 放大环 |
| 0041 | +incomplete+raw1 | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0042 | +incomplete+raw6+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0043 | +incomplete | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0044 | +incomplete+raw2+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0045 | +incomplete | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0046 | +incomplete+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0047 | +incomplete+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0048 | +incomplete+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0049 | +incomplete+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0050 | +incomplete+raw4+mega-wide | bad | coal×8 放大环, crafts=221亿 |
| 0051 | +incomplete+raw5+mega-wide | questionable | coal_piece↔coal ×8 放大环 |
| 0052 | +incomplete+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0053 | +incomplete+raw1+mega-wide | questionable | coal_piece↔coal ×8 放大环 |
| 0054 | +incomplete+raw3+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0055 | +incomplete | questionable | slab_to_block 泛化(50+ block)产物 |
| 0056 | +incomplete+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0057 | +incomplete | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0058 | +incomplete+raw5+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0059 | +incomplete+raw2 | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0060 | +incomplete+raw2+mega-wide | questionable | coal_piece↔coal ×8 放大环 |
| 0061 | +incomplete+raw2 | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0062 | +incomplete | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0063 | +incomplete+raw1+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0064 | +incomplete+raw1 | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0065 | +incomplete+raw1 | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0066 | +incomplete+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0067 | +incomplete+raw1 | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0068 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0069 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0070 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0071 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0072 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0073 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0074 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0075 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0076 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0077 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0078 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0079 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0080 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0081 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0082 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0083 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0084 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0085 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0086 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0087 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0088 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0089 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0090 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0091 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0092 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0093 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0094 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0095 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0096 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0097 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0098 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0099 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0100 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0101 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0102 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0103 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0104 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0105 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0106 | +incomplete | ok | incomplete(预算耗尽), 未发现异常路径 |
| 0107 | +incomplete+raw4+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0108 | +incomplete+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0109 | +incomplete+raw1+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0110 | +incomplete+raw3 | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0111 | +incomplete+raw1+mega-wide | questionable | 糖甘蔗压缩/解压空转环 |
| 0112 | +incomplete+raw2 | questionable | slab_to_block 泛化(50+ block)产物 |
| 0113 | +incomplete+raw3 | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0114 | +incomplete+raw7+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0115 | +incomplete+raw1 | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0116 | +incomplete+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0117 | +incomplete+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0118 | +incomplete+raw3+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0119 | +incomplete | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0120 | +incomplete+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0121 | +incomplete+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0122 | +incomplete+raw3+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0123 | +incomplete+raw3 | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0124 | +incomplete+raw1 | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0125 | +incomplete+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0126 | +incomplete+raw6+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0127 | +incomplete+raw3+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0128 | +incomplete | questionable | slab_to_block 泛化(50+ block)产物 |
| 0129 | +incomplete | questionable | slab_to_block 泛化(50+ block)产物 |
| 0130 | +incomplete | questionable | slab_to_block 泛化(50+ block)产物 |
| 0131 | +incomplete+raw5+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0132 | +incomplete | questionable | slab_to_block 泛化(50+ block)产物 |
| 0133 | +incomplete | questionable | slab_to_block 泛化(50+ block)产物 |
| 0134 | +incomplete | questionable | slab_to_block 泛化(50+ block)产物 |
| 0135 | +incomplete | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0136 | +incomplete | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0137 | +incomplete+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0138 | +incomplete+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0139 | +incomplete+raw3 | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0140 | +incomplete | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0141 | +incomplete | questionable | slab_to_block 泛化(50+ block)产物 |
| 0142 | +incomplete | questionable | slab_to_block 泛化(50+ block)产物 |
| 0143 | +incomplete+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0144 | +incomplete | questionable | slab_to_block 泛化(50+ block)产物 |
| 0145 | +incomplete | questionable | slab_to_block 泛化(50+ block)产物 |
| 0146 | +incomplete+raw1+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0147 | +incomplete | questionable | slab_to_block 泛化(50+ block)产物 |
| 0148 | +incomplete+mega-wide | questionable | slab_to_block 泛化产物 + 糖甘蔗压缩环 |
| 0149 | +incomplete | questionable | slab_to_block 泛化(50+ block)产物 |

## 规则建议（给汇总者）
- targetRegex: 本批目标均为合法制品，无需排除（占位 `^$`）。
- recipeRegex:
  - `quark:building/crafting/compressed/.*` 与 `.*uncompress$` 配对 → 收敛为 loop（证据：93 链 / 1402 次 sugar_cane_blockuncompress）
  - `dndesires:crafting/coal_(piece_from_coal|from_coal_piece)` → 收敛为 pattern-amplification（证据：15 链，0050 达 coal×4194304）
  - `createbigcannons:(compacting/forge_bronze_nugget|melting/melt_bronze_nugget)` → 收敛为 loop（证据：1 链 / 12 次 molten_bronze x192000）
  - `quark:tweaks/crafting/slab_to_block` → 收敛为 duplicate/pattern-exchange 并排除泛化 any-of 输出（证据：110 链）
- builtin 候选:
  - pattern-amplification（1→N 互逆转复制，如 coal_piece↔coal ×8、湿海绵/桶内熔体空转）——跨包通用，建议标签 junk/duplicate。
  - quark compressed↔uncompress 互逆环——建议 pattern-loop。
  - 无其它新 junk/duplicate 模式外的发现。
