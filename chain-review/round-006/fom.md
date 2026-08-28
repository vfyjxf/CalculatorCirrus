# fom batch-005 审查报告（round-6 选路质量深审）

- pack: fom
- 批次: batch-005（written=61；类别分布：incomplete 45 / long 9 / colossal 6 / giant 1）
- 审查链数: 61（全部 61 条，≥60 配额；incomplete 45 / complete 16）
  - 方法：① 全量读取 `report.txt` / `index.tsv` / `targets.tsv`；② 用 Python 解析 6 个 `chains-*.txt`，抽取全部 3943 条 EXECUTE，筛出 **非已标签（exchange/storage_ladder）且输入不含 `|+N` 伪影** 的 **643 条"真步骤"**；③ 对每条真步骤，用 `cirrus-audit-data/fom/producers.jsonl` + `producers.tsv` 交叉核对：生产者计数、`in/out` 明细、替代者对比；④ 对染色玻璃、下界合金、create 组件、createcasing 各代表链做人工全文复核。
- 发现: critical 0 / major 0 / minor 0

## 审查口径（本轮回报 4 类）
`worse-alternative` / `silly-detour` / `impure-recipe` / 目标选择怪象。
已排除：生成器/导出伪影（`|+N`、溢出哨兵、BUDGET/RANK 截断、机器映射）、已带 rule-tag 家族（storage_ladder/exchange/recolor/recycling/generation）的放大本身、唯一生产者情形。

## 发现
无。所有被审链路的"真步骤"所选生产者，在索引可选生产者中均非明显更差；候选命中经逐一复核均为伪阳性（见下）。

### 候选排查记录（证明"无发现"而非"未查"）
- **impure-recipe：0 条。** 643 条真步骤所选配方在索引中 `outs` 均为单元素（无杂产），故不存在"选多杂产而存在更纯直接配方"的情形。
- **worse-alternative：启发式初筛 49 条 → 精炼后 1 条 → 全部伪阳性。**
  - `create:crafting/materials/andesite_alloy_from_zinc`（输入 2 锌粒+2 安山岩）在 ~40 条链中被选中，索引里另有 `create:crafting/materials/andesite_alloy_from_block`（输入 `create:andesite_alloy_block`×1）。但后者是把"合金块"拆回 1 个合金（块本身需 4 合金合成）→ 并非更廉价的生产者，属存储换算伪阳性；且 `andesite_alloy_from_zinc` 复用链内已处理的锌（raw_zinc→锌锭→锌粒），选路合理。
  - `create:crafting/appliances/slime_ball`（输入 dough+lime_dye）vs `minecraft:slime_ball`（输入 `minecraft:slime_block`）。后者为方块拆包；链 0012 仅在先用 create 配方造出 slime_ball 后再压实成 block 拆回，属无净产出往返（已标签 storage_ladder，且非本轮回报类），故"替代者"实际并不更优。
  - `create:crafting/materials/copper_ingot`（9 铜粒）vs `minecraft:copper_ingot`（铜块拆 1 锭）：后者同样是方块拆包伪阳性。
  - `petrolsparts:crafting/shaft_half_manual_only` + `shaft_from_shaft_halves` 产 `create:shaft`，与直接 `create:crafting/kinetics/shaft`（2 安山合金）**输入成本完全相同**（皆 2 安山合金），仅多一步 half 拆分，非"明显更差"，判 ok。
- **silly-detour：0 条。** 染色玻璃链（0006/0019/0028–0035/0048）经 `framed_glass` 路径产 `minecraft:glass`，但所用 `framed_glass` 等中间体来自已排除的 `pattern/exchange` 家族（stonecutting 派生），复用既有中间体而非引入新原料沙，权衡可辩护 → ok。
- **目标选择怪象：0 条。** 61 个目标均为真实可合成物（下界合金工具、染色玻璃板、create/createdieselgenerators/offroad/create_radar/aeronautics 组件、createcasing 各材质变体 press/plough/clutch/harvester/drill/valve），无展示物/重复变体/无意义目标。

## 链级判定
| chain | category | verdict | 一句话 |
| --- | --- | --- | --- |
| 0000 | +complete+raw5 | ok | tom 料斗；storage_ladder 放大 + 末端 canonical 合成 |
| 0001 | +complete+raw12 | ok | 下界合金斧；stick+钻石+废料+金 canonical smith |
| 0002 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；目标配方不在导出内→incomplete（数据限制，非选路问题） |
| 0003 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；目标配方不在导出内→incomplete（数据限制，非选路问题） |
| 0004 | +incomplete+raw3 | ok | zinc→andesite_alloy→shaft 引导 + canonical 末端合成；选路合理 |
| 0005 | +complete+raw11 | ok | 下界合金靴；canonical smith |
| 0006 | +complete+raw2 | ok | 玻璃复用 exchange 产物产出，合理；末端染色玻璃 canonical |
| 0007 | +incomplete+raw2 | ok | zinc→andesite_alloy→shaft 引导 + canonical 末端合成；选路合理 |
| 0008 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0009 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0010 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0011 | +incomplete+raw3 | ok | zinc→andesite_alloy→shaft 引导 + canonical 末端合成；选路合理 |
| 0012 | +incomplete+raw8 | ok | zinc→andesite_alloy→shaft 引导 + canonical 末端合成；slime_ball 选法合理 |
| 0013 | +complete+raw2 | ok | 玻璃复用 exchange 产物产出，合理；末端染色玻璃 canonical |
| 0014 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0015 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0016 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0017 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0018 | +incomplete+raw2 | ok | zinc→andesite_alloy→shaft 引导 + canonical 末端合成；选路合理 |
| 0019 | +complete+raw2 | ok | 玻璃复用 exchange 产物产出，合理；末端染色玻璃 canonical |
| 0020 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0021 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0022 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0023 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0024 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0025 | +complete+raw12 | ok | 下界合金锄；canonical smith |
| 0026 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0027 | +incomplete+raw2 | ok | zinc→andesite_alloy→shaft 引导 + canonical 末端合成；选路合理 |
| 0028 | +complete+raw2 | ok | 玻璃复用 exchange 产物产出，合理；末端染色玻璃 canonical |
| 0029 | +complete+raw2 | ok | 玻璃复用 exchange 产物产出，合理；末端染色玻璃 canonical |
| 0030 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0031 | +complete+raw2 | ok | 玻璃复用 exchange 产物产出，合理；末端染色玻璃 canonical |
| 0032 | +complete+raw2 | ok | 玻璃复用 exchange 产物产出，合理；末端染色玻璃 canonical |
| 0033 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0034 | +complete+raw2 | ok | 玻璃复用 exchange 产物产出，合理；末端染色玻璃 canonical |
| 0035 | +complete+raw2 | ok | 玻璃复用 exchange 产物产出，合理；末端染色玻璃 canonical |
| 0036 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0037 | +incomplete+raw4 | ok | zinc→andesite_alloy→shaft 引导 + canonical 末端合成；选路合理 |
| 0038 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0039 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0040 | +incomplete+raw2 | ok | zinc→andesite_alloy→shaft 引导 + canonical 末端合成；选路合理 |
| 0041 | +incomplete+raw3 | ok | zinc→andesite_alloy→shaft 引导 + canonical 末端合成；选路合理 |
| 0042 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0043 | +incomplete+raw3 | ok | zinc→andesite_alloy→shaft 引导 + canonical 末端合成；选路合理 |
| 0044 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0045 | +incomplete+raw2 | ok | zinc→andesite_alloy→shaft 引导 + canonical 末端合成；选路合理 |
| 0046 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0047 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0048 | +complete+raw3 | ok | zinc→andesite_alloy→shaft 引导 + canonical 末端合成；选路合理 |
| 0049 | +incomplete+raw3 | ok | zinc→andesite_alloy→shaft 引导 + canonical 末端合成；选路合理 |
| 0050 | +incomplete+raw2 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0051 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0052 | +incomplete+raw2 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0053 | +complete+raw12 | ok | 下界合金锹；canonical smith |
| 0054 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0055 | +complete+raw12 | ok | 下界合金剑；canonical smith |
| 0056 | +incomplete+raw2 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0057 | +incomplete+raw2 | ok | zinc→andesite_alloy→shaft 引导 + canonical 末端合成；选路合理 |
| 0058 | +incomplete+raw2 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0059 | +incomplete+raw3 | ok | exchange/storage 放大主导；末端 canonical createcasing 合成；incomplete 为数据限制 |
| 0060 | +incomplete+raw2 | ok | zinc→andesite_alloy→shaft 引导 + canonical 末端合成；选路合理 |

## 规则建议（给汇总者）
- 无系统性的 mod 配方族误选：本批 643 条真步骤所选生产者均合理，未出现"明显更差却被选"的收敛型错误。
- targetRegex: 无。
- recipeRegex: 无。
- builtin 候选: 无新模式（exchange/storage_ladder 家族放大已由既有规则覆盖，本轮回报类无新增）。
