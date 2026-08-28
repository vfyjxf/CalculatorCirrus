# 配方链递进审查工具（chain-review）使用指南

> 位置：本文档在 `CalculatorCirrus/CHAIN-REVIEW.md`；工具与数据都在本仓库内。
> 面向对象：负责"审查链批 → 总结特征 → 固化过滤器 → 推进下一批"的 agent / 人。

---

## 1. 背景

### 1.1 这个系统解决什么问题

Minecraft 整合包里有数万到数十万条配方（atm10：28 万）。`CalculatorCirrus` mod 内置一个配方路径求解器（routing planner）：给定目标物品，自动规划"从原材料到成品"的完整合成链。我们想知道——**这些自动规划出来的链是否合理**（有没有荒谬路径、循环放大、假产出、被 planner 选中但其实无意义的配方），并把发现反哺到内置规则系统里。

为此构建了本工具：把 22 个整合包的游戏内配方导出（`Planner-Final/exports/<pack>/recipes-*.json`，由 ExportDriver 从真实游戏实例导出）在**纯 JVM 离线环境**下灌进真实的 Java 求解器（`RecipeGraph + RoutingService + StandardPlanner`，不起游戏），批量生成**纯文本合成链**，供大模型审查。

### 1.2 为什么是"递进式"而不是一次全量

实测证明一次全量 dump 的边际收益急剧衰减：1938 条链在 create-train 只覆盖 8.3% 的配方——链反复经过"骨干配方"，而海量同构族（换色/压缩/楼梯台阶变体）沉在长尾。所以工作流改为：

```
生成一小批 ──► 审查并总结特征 ──► 固化为过滤器(filters.json) ──► 筛掉已知模式
     ▲                                                            │
     └────────── 下一批自动定向到"未被覆盖的配方" ◄─────────────────┘
```

每批选择按"需求锥内**未覆盖配方数**"排序（每个未覆盖配方 +10 分），后续批次自动被拉向没碰过的区域；结构指纹跨批全局去重，同构链不重复出现。循环直到覆盖率达标或模式收敛。

### 1.3 已内置的规则（审查时不要重复建议这些）

生成链时规则系统已生效，链文本里有对应标注：

- **建图前已排除**（不会出现在链里）：`cirrus:quality/junk`、`cirrus:quality/duplicate`（builtin 分类规则 v2）。
- **advisory 标注**：EXECUTE 行尾 `[rule-tag: pattern/exchange]`、链头部 `builtin-rule-tags (already handled, do not re-report)` ——这些配方命中已知模式（exchange/recolor/recycling 等），当前是"可见但不自动排除"。对它们的有效建议只有"是否应升级为排除"，不要重新发现模式本身。
- **求解时已生效**：`calculator_preview` 规则 profile（5 轴评分：子树成本/合成次数/库存短缺/深度/放大风险）、source policy、有界放大环策略、锥形动态预算。链是这些规则的**输出**——planner 每步选哪个生产者已经反映它们。

审查应聚焦：**无 rule-tag 却可疑**的链/步骤、`UNRESOLVED reason=BUDGET_TRUNCATED/RANK_TRUNCATED`（planner 为何放弃）、以及 despite 规则仍荒谬的宽链/巨链。

---

## 2. 工具用法

### 2.1 命令（推荐入口：`chain-review.sh`）

```bash
cd Planner-Final/CalculatorCirrus

# 首批（每包默认 150 条链）
bash chain-review.sh init              # 全部包
bash chain-review.sh init fom,atm10    # 指定包（逗号分隔）

# 下一批（读取 state + filters，定向未覆盖区域）
bash chain-review.sh next              # 全部包
bash chain-review.sh next fom          # 指定包

# 查看全部包的覆盖率与收敛率
bash chain-review.sh status

# 审查后固化一条过滤器（核心接口）
bash chain-review.sh add-filter <包名> <名字> <targetRegex|recipeRegex> '<正则>'
# 例：bash chain-review.sh add-filter atm10 recolor-family recipeRegex '_dyed|recolor'
```

环境变量：`EXPORT_DIR`（默认 `../exports`）、`OUT_DIR`（默认 `../chain-exports`）、`BATCH`（每批条数，默认 150）、`JOBS`（并行 worker 数，默认 4）。

### 2.2 过滤器语义（`filters.json`）

| type | 匹配对象 | 效果 |
|---|---|---|
| `targetRegex` | 目标输出 id 或其生产配方 id | 命中的目标**不再被选入后续批次**（如 `decompress` 已默认内置） |
| `recipeRegex` | 链内任意被执行配方 id | 链**保留**，但计入收敛指标 `filterHitPct`（衡量"新批里多少是已知模式"） |

新增过滤器**不回溯**删除已生成的链——它们留在批次目录里作为审查记录。

### 2.3 底层直跑（可选）

```bash
# gradle 入口（单进程全量模式，批模式用 --batch）
./gradlew :Mod:chainExport -PcirrusChainsPacks=fom -PcirrusChainsOut=../chain-exports
# 裸 JVM（chain-review.sh 内部即此方式）
java -Xmx8g -cp "$(cat Mod/build/chainexport/cp.txt)" \
  dev.vfyjxf.cirrus.recipe.chainexport.ChainExportMain \
  --export-dir ../exports --out ../chain-exports --batch 150 --packs fom
```

关键 CLI 参数：`--batch N`（>0 启用递进模式）、`--per-pack auto|N|max`（全量模式配额）、`--rule preview|default|execution|strict|proven`、`--mod-cap`、`--seed`、`--overwrite`。

---

## 3. 数据接口（每包 `chain-exports/<pack>/`）

```
<pack>/
  coverage.tsv            批次账本（追加式）
  batch-001/              每批一个目录：chains-<类别>-001.txt / index.tsv / targets.tsv / manifest.txt / report.txt
  batch-002/ ...
  state/                  递进状态（机器接口核心）
    covered-recipes.json  累计被链执行过的配方 id（只增不减）
    structure-seen.json   已写出的链结构指纹（跨批去重）
    filters.json          当前过滤器集合（add-filter 修改的就是它）
```

### coverage.tsv 列含义

| 列 | 含义 |
|---|---|
| `batch` | 批次号 |
| `written` | 本批实际写出的链数（求解数减去噪声/重复筛选） |
| `newlyCovered` | 本批**真新增**的覆盖配方数（本批触及 − 之前已覆盖） |
| `totalCovered` | 累计覆盖配方数 |
| `kept` | 建图配方总数（分母） |
| `coveragePct` | `totalCovered/kept` |
| `targetPct` | 覆盖率目标（见 §4） |
| `filterHitPct` | **收敛率**：本批写出链中命中 recipeRegex 过滤器的比例 |

### 链文本格式（每条链）

```
### CHAIN 0042 | category=long | depthBucket=long | flags=complete+raw5
target: <目标物品 id> x1 | recipe/system/tier/coneScore 元信息
status/search/rule/machines 头部行（含 builtin-rule-tags 已处理模式汇总）
[A0] EXECUTE <配方 id> x<次数> @ <机器> [rule-tag: 已知模式]
       in : <输入 id> x<数量>, ...
       out: <输出 id> x<数量>, ...
       <= from A<x>            （上游动作，缩进=依赖深度）
[RAW] <原材料 id> x<数量>, ...
[UNRESOLVED] <输入> x<数量> (candidates=N, reason=...)
[CATALYST] <催化剂> x<数量> for <配方>
```

分类（按最长依赖路径）：`trivial ≤1｜short 2-3｜medium 4-6｜long 7-11｜giant 12-19｜colossal ≥20｜mega-wide ≥300动作`；`incomplete` = 存在非原材料性未解决需求（带 reason）。`+raw N` 标记真原材料叶（包内无生产者或唯一生产者自环，属正常）。

---

## 4. 指标与停止标准

**覆盖率目标**（按建图配方数，已内置到账本 `targetPct`）：

| 包规模 (kept) | 目标 | 依据 |
|---|---|---|
| < 2k | 70% | 结构少，两三批收敛 |
| 2k–6k | 60% | |
| 6k–25k | 50% | 同构族开始主导长尾 |
| 25k–100k | 32% | 全量实测 2000 条仅 8–24% |
| ≥ 100k (atm10) | 20% | 28 万配方大量压缩/换色族 |

**停止信号（满足其一）**：
1. `filterHitPct`（收敛率）≥ **85%** —— 新批里绝大多数是已知模式，继续生成只是重复；
2. `coveragePct ≥ targetPct`。

配方覆盖率是上限指引，**收敛率才是停止信号**。

### 建议的审查循环（每包）

1. `bash chain-review.sh next <pack>` 生成新批；
2. 读 `batch-NNN/chains-*.txt`（优先 incomplete 与 colossal/giant 类）；
3. 总结新问题模式 → `add-filter` 固化（要"排除目标"用 targetRegex，只"标记收敛"用 recipeRegex）；
4. `bash chain-review.sh status` 观察覆盖率与收敛率变化；
5. 收敛率 ≥85% 或覆盖率达标 → 该包完成，转向下一包。

---

## 5. 注意事项

- **完全离线**：不起游戏，多进程并行（大包自动大堆内存，atm10 建议独占 worker）；atm10 一批约 1–3 分钟。
- **老格式导出**（atm10/atm10-lite 无 registry 文件）已自动做类型预扫描，无需处理。
- **incomplete 占多数是真实行为**：与游戏内采样一致（复杂目标必有原材料 pending）；区分 `+raw`（正常）与 `UNRESOLVED`（planner 放弃，值得审查）。
- 已知噪声筛选（写盘前自动）：空失败链、跨批结构重复、trivial 上限 50/包、`decompress` 目标黑名单。
- 全量一次性 dump 的历史数据存档于 `Planner-Final/chain-exports-full-dump-20260829/`（约 1.4GB，含规则标注），可与递进数据对照。
- 源码：`Mod/src/test/java/dev/vfyjxf/cirrus/recipe/chainexport/`（OfflineContent / ExportPackLoader / ChainTargetSelector / ChainClassifier / ChainTextRenderer / ChainExportMain），冒烟测试 `ChainExportSmokeTest` 在常规 `:Mod:test` 中。
