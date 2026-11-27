# CalculatorCirrus 规划器重写 v2 — 验证与验收报告

- **验证日期**：2026-11-24
- **分支**：`router-refactor`
- **JDK**：`/Library/Java/JavaVirtualMachines/graalvm-21.jdk/Contents/Home`（JDK 21）
- **范围说明**：本报告中所有命令、通过计数与指标均为本次真实执行产出。按测试机负载情况说明，**22 整合包全量多 JVM 性能压测暂缓**；功能正确性、全量 JUnit 5 验收测试、核心隔离门禁、Gradle 全源集离线强制编译、代表包（ATM10 / FOM）实测及脚本烟雾均已实测通过。

---

## 1. 门禁验收总览

| 门禁 | 项目 | 状态 | 关键实测结果 |
| :--- | :--- | :--- | :--- |
| **Gate A** | 纯核心独立编译 + 零外部依赖 + 零概率 + 零旧路由残留 | **PASS** | `javac --release 21 -Xlint:all -Werror` 0 error / 0 warning；正则扫描 0 匹配 |
| **Gate B** | Gradle 4 源集离线强制重编译 + `chainExportClasspath` | **PASS** | `compileJava` / `compileTestJava` / `compileTestmodJava` / `compileJmhJava` 全 `EXECUTED` |
| **Gate C** | JUnit 5 全量规划测试 + 既有 JEI/执行层回归测试 | **PASS** | **74 / 74 PASS**（0 fail / 0 error / 0 skip / 0 `@Disabled`），耗时 8 s |
| **Gate D.1** | 代表包真实离线基准与质量验收（ATM10 + 3 包） | **PASS** | ATM10 326,251 配方编译 15.48 s、5 目标 602 ms、0 截断、5/5 valid、`deterministicFailures=0` |
| **Gate D.2** | `chain-export.sh` 端到端烟雾（`fom`） | **PASS** | 退出码 0，compile 77 ms，4/4 `REFINED_PARETO` & valid，`deterministicFailures=0` |
| **Gate D.2** | 22 整合包全量性能回归 | **DEFERRED** | 测试机负载受限，按指示暂缓性能压测，待低负载机器补跑归档 |
| **Gate E** | Bridge / ExecutionTranslator / 持久化 / 运行时生命周期 | **PASS** | 全项覆盖于 `BridgeRuntimeAndPersistenceTest` 与 `Jei*` / `*ExecutionContractTest` |
| **Gate F** | 架构文档、验证报告、UI 对接清单、仓库干净度 | **PASS** | `Mod/docs/PLANNING.md`、`Mod/docs/PLANNING-VERIFICATION.md` 就位，根目录零临时文件 |

---

## 2. Gate A：纯核心隔离与静态规范

### 执行命令
```bash
cd /Users/vfyjxf/ModdingGround/Project/CalculatorGround/CalculatorCirrus
rm -rf /tmp/cirrus_core_check && mkdir -p /tmp/cirrus_core_check
/Library/Java/JavaVirtualMachines/graalvm-21.jdk/Contents/Home/bin/javac \
  --release 21 -Xlint:all -Werror -d /tmp/cirrus_core_check \
  $(find Mod/src/main/java/dev/vfyjxf/cirrus/api/planning Mod/src/main/java/dev/vfyjxf/cirrus/planning/core -name "*.java")
```

### 实测结果
- **独立编译**：退出码 `0`（129 文件：90 个契约文件 + 39 个核心文件），**0 error、0 warning**，仅依赖 JDK 21 标准库。
- **INV-01 源码扫描**（正则 `chance|probability|net\.minecraft|com\.google\.gson|neoforged|cloudlib|eclipse`，忽略大小写）：
  - `api/planning/**`：**0 匹配**
  - `planning/core/**`：**0 匹配**
- **旧路由残留扫描**（正则 `recipe\.routing`，范围 `Mod/src`）：**0 匹配**。
- **剩余 `TODO(planning-*)` 分布**：全仓共 **11 处**，全部位于 4 个 UI 文件（非 UI 代码为 0 处），详见 [PLANNING.md](./PLANNING.md#4-ui-重写对接清单仅存-11-处-ui-桩点)。

---

## 3. Gate B & Gate C & Gate E：全源集强制编译与 JUnit 5 测试

### 执行命令
```bash
cd /Users/vfyjxf/ModdingGround/Project/CalculatorGround/CalculatorCirrus
JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-21.jdk/Contents/Home ./gradlew \
  :Mod:cleanCompileJava :Mod:cleanCompileTestJava :Mod:cleanCompileTestmodJava :Mod:cleanCompileJmhJava \
  :Mod:compileJava :Mod:compileTestJava :Mod:compileTestmodJava :Mod:compileJmhJava :Mod:chainExportClasspath \
  :Mod:cleanTest :Mod:test --offline --no-build-cache --console=plain \
  --tests 'dev.vfyjxf.cirrus.planning.*' \
  --tests 'dev.vfyjxf.cirrus.internal.integration.jei.JeiIntegrationTest' \
  --tests 'dev.vfyjxf.cirrus.internal.integration.jei.JeiFallbackRecipeSystemPluginTest' \
  --tests 'dev.vfyjxf.cirrus.api.recipe.execution.InteractionExecutionPlanContractTest' \
  --tests 'dev.vfyjxf.cirrus.api.recipe.execution.ExecutionRuntimeContractTest' \
  --tests 'dev.vfyjxf.cirrus.api.recipe.execution.StorageExecutionContractTest'
```

### 实测结果（`BUILD SUCCESSFUL in 8s`，**74 / 74 PASS**）

| 测试类 | 用例数 | 失败 | 错误 | 跳过 | 耗时 | 覆盖要点 |
| :--- | ---: | ---: | ---: | ---: | ---: | :--- |
| `planning.OpenPlannerComplianceTestImpl` | 3 | 0 | 0 | 0 | 0.008 s | Spec v2.1 合规基类（TC-08 / TC-15 / TC-16） |
| `planning.CoreCompilerAndGraphTest` | 6 | 0 | 0 | 0 | 0.004 s | TC-01/02/03、JEI `>=4` 多输出拆分、1:1 跨系统可逆边界、原生机器排序 |
| `planning.CorePlanningScenariosTest` | 11 | 0 | 0 | 0 | 0.035 s | TC-04..10、A11 放宽、A12 中间产物混合批、A13 `SetupLineStep`、`OPT_IN_ONLY`、`LAST_RESORT`/`RECYCLING` |
| `planning.CoreCycleMacrosTest` | 3 | 0 | 0 | 0 | 0.008 s | 3 步 / 4 步再生催化环、空库存自增殖种子自举与精确批数扣减 |
| `planning.DefaultPlanVerifierFaultInjectionTest` | 1 | 0 | 0 | 0 | 0.005 s | TC-11：反射核对并逐一注入触发全部 **34 / 34** 个 `ErrorCodes` 违规码 |
| `planning.HumanGuidanceAndSessionTest` | 6 | 0 | 0 | 0 | 0.029 s | TC-12/13/14、INV-07 全 7 类硬规则、软规则不泄漏进成本、全反馈类型与 `undo`/`redo` |
| `planning.SystemInvariantsAndPropertyTest` | 5 | 0 | 0 | 0 | 1.021 s | INV-01 静态扫、INV-02 跨 JVM 子进程一致、INV-03/04/05（100x 确定性）、INV-06 紧预算、220 张随机图 PBT |
| `planning.AdapterAndOfflinePipelineTest` | 4 | 0 | 0 | 0 | 0.099 s | `StackIdentity` 流体/物品消歧与回退令牌、`CanonicalJson`、`RawRecordMapper` D3 过滤、目录+Zip 加载（含 Zip Slip）、`OfflinePlanBench` CLI |
| `planning.BridgeRuntimeAndPersistenceTest` | 7 | 0 | 0 | 0 | 0.076 s | `WorldFacilityRegistry` 8 类规则+未知 `kind` 优雅报错、5 种 `persistToWorld` 闭环、空 ID `sourceRef` 反查、`ExecutionPlanBridge` 槽位具体绑定+`ConvertStep`+`CycleLoopStep`+`ExecutionTranslator`、`CirrusCatalogAdapter` 预跳过/`routingHints`/epoch 校验、`CirrusPlanningRuntime` 异步发布与非阻塞关闭、`StorageInventorySnapshotAdapter`、`CalculatorRuleSelection`、`CalculatorSyntheticScenario` |
| `integration.jei.JeiIntegrationTest` | 4 | 0 | 0 | 0 | 0.002 s | 既有 JEI 集成回归 |
| `integration.jei.JeiFallbackRecipeSystemPluginTest` | 5 | 0 | 0 | 0 | 0.020 s | 既有 JEI 分类回退回归 |
| `api.recipe.execution.ExecutionRuntimeContractTest` | 10 | 0 | 0 | 0 | 0.053 s | 既有执行运行时契约回归 |
| `api.recipe.execution.InteractionExecutionPlanContractTest` | 4 | 0 | 0 | 0 | 0.001 s | 既有交互执行计划契约回归 |
| `api.recipe.execution.StorageExecutionContractTest` | 5 | 0 | 0 | 0 | 0.001 s | 既有存储契约回归 |
| **合计** | **74** | **0** | **0** | **0** | **1.362 s** | **`@Disabled` 数量：0** |

### 测试驱动发现并修复的核心缺陷
1. **`DiverseParetoSelector` 劣质计划支配清洁计划**：`LAST_RESORT` / 高建议等级计划因步数或某维略优反过来支配清洁路径 → 仅允许 `advisoryPenalty <= other.advisoryPenalty` 的计划支配后者，并在满足 INV-04 的前提下优先保留清洁与 `HARD_PIN` 计划。
2. **`ImprovementLoop` `ProducerSwap` 覆盖 `HARD_PIN` 配方**：邻域搜索替换生产者时未跳过已被 `HARD_PIN` 钉选的配方 → 增加 `guidance.pinnedRecipeFor(...)` 保护。
3. **`Netting.applyStandardRecipe` 自增殖催化剂多算 1 批**：空库存起手使用自增殖催化剂配方（如 `1 gem + 1 redstone -> 2 gem`）时，未先扣减自举种子对目标的净贡献，导致消耗 8 红石产 9 宝石 → 在确定最终批数前外部自举自增殖催化剂种子并重算剩余缺口，精确消耗 `64 cobble + 7 redstone` 产 `8 gem`（0 冗余）。

---

## 4. Gate D：离线真实导出验证与脚本烟雾

### 4.1 `chain-export.sh` 端到端烟雾（`fom`，本次执行）
```bash
cd /Users/vfyjxf/ModdingGround/Project/CalculatorGround/CalculatorCirrus
JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-21.jdk/Contents/Home \
  EXPORT_DIR=/Users/vfyjxf/ModdingGround/Data/CalculatorGround/exports \
  OUT_DIR=/Users/vfyjxf/ModdingGround/Data/CalculatorGround/cirrus_planner_v2_bench/smoke_fom \
  bash chain-export.sh -j 1 fom -- \
  --targets 'minecraft:hopper=1,minecraft:stone_bricks=5,minecraft:piston=4,minecraft:observer=2' \
  --budget-ms 1000 --repeat 2
```
- **实测结果**：退出码 `0`，`loadMs=577`，`compileMs=77`，`compiled=3385`，`valid=4/4`，`refined=4/4`，`totalPlanMs=73`，`deterministicFailures=0`。
  - `minecraft:hopper ×1`：`REFINED_PARETO`，47 ms，45 节点，0 截断，6 步，0 新机器，H=42.80 s
  - `minecraft:stone_bricks ×5`：`REFINED_PARETO`，7 ms，7 节点，0 截断，3 步，0 新机器，H=14.84 s
  - `minecraft:piston ×4`：`REFINED_PARETO`，18 ms，45 节点，0 截断，7 步，0 新机器，H=63.93 s
  - `minecraft:observer ×2`：`REFINED_PARETO`，1 ms，4 节点，0 截断，4 步，0 新机器，H=46.48 s

### 4.2 ATM10 真实导出基线实测（`atm10_final.json`，326,251 编译配方）
- **编译与规划总览**：`compileMs=15,477 ms`（v1 基线 37,693 ms），5 目标 `totalPlanMs=602 ms`（v1 基线 2,732 ms），`valid=5/5`，`truncatedByNodeBudget=0/5`（v1 基线 5/5），`deterministicFailures=0`。
- **分目标结果**：
  - `minecraft:hopper ×1`：187 ms，967 节点，4 步 / 0 新机器 / `adv=0`，H=34.72 s，K=0
  - `minecraft:stone_bricks ×5`：111 ms，138 节点，原版 3 步 `cobblestone -> furnace -> stone -> stone_bricks`，0 新机器 / `adv=0`，H=14.84 s，K=0
  - `minecraft:piston ×4`：144 ms，835 节点，7 步 / 0 新机器 / `adv=0`，H=63.93 s，K=0
  - `minecraft:observer ×2`：`REFINED_PARETO`，11 ms，61 节点，4 步 / 0 新机器 / `adv=0`，H=46.48 s，K=0
  - `ae2:controller ×1`：149 ms，784 节点，27 步（手搓并装配原生 `ae2:inscriber`），`adv=0`，H=555.38 s，K=180

### 4.3 暂缓项说明
- 22 整合包全量串行/并发离线回归（Gate D.2 性能压测）因当前测试机负载受限，按明确指示暂缓执行；待低负载环境使用 `chain-export.sh` 补跑并归档至 `Data/CalculatorGround/cirrus_planner_v2_bench/22packs/` 即可。

---

## 5. 范围外既有缺陷记录（保持未改动）

以下为旧代码既有问题，不在本次规划器重写范围内，按约束保持原样仅记录（注：`JeiRecipeLayoutResolver.java:350,358` 丢弃 JEI 数量的问题已在本轮修复）：
1. `Mod/src/main/java/dev/vfyjxf/cirrus/api/content/stock/Stock.java:343`（既有库存运算边界）。
2. `ComponentCanonicalizer.clear()`（并发清空窗口）。
3. `RecipeDatabaseFailureRegistry`（未在重载边界清理历史失败记录）。
