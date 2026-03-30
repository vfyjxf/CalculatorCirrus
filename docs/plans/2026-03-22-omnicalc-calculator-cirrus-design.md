# OmniCalc Calculator Cirrus Design

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 `omnicalc-pro` 的计算器页面完整迁移到 `CalculatorCirrus`，保持既有 `omnicalc` 页面布局不变，只做微量游戏化调整，并统一替换为 Cirrus 自有素材与控件语言。

**Architecture:** 现有 `dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator` 目录作为主承载层，继续使用 `OmniCalculatorView`、`TreeViewWidget`、`TreeCanvasWidget`、`MatrixViewWidget`、`MilestonesViewWidget` 组成固定布局。最终入口落到 [`TabContainer`](D:\ProjectDir\CloudMods\CalculatorCirrus\Mod\src\main\java\dev\vfyjxf\cirrus\ui\widget\TabContainer.java) 的计算器页签，不新增外层页面结构，不新增 `ManifestPanelWidget`。

**Tech Stack:** Java, NeoForge, CloudLib UI widgets/canvas, Cirrus texture atlas resources, ModDevMCP UI inspection/capture/hotswap workflow

---

## 1. 目标边界

- 保持 `omnicalc` 计算器现有布局关系不变。
- 不新建独立的右侧清单组件，继续沿用代码中已经存在的“物料与资源清单”实现。
- 计算器最终挂载到 `TabContainer` 的计算器页签中，而不是继续依赖独立演示式入口。
- 视觉风格从 web 圆角深色稿切换为 Cirrus 的像素 GUI 风格，但只做微量调整，不改变信息结构。

## 2. 页面结构

页面结构维持 `omnicalc-pro` 的固定骨架：

- 顶部控制栏
- 左侧主内容区
- 中部三视图切换
- 右侧物料与资源清单

三种视图职责不变：

- Tree：配方树、层级导航、minimap、底部路径/追踪栏
- Matrix：拓扑聚合矩阵
- Milestones：里程碑/任务看板

## 3. 宿主与集成

- `TabContainer` 继续保留其外层页签结构和主框体。
- 计算器页签内容切换为 `omnicalc` 计算器承载内容，而不是旧的独立 `CalculatorWidget` 工作流。
- `OmniCalcScreen` 可继续保留为调试入口，但不再作为主接入点设计。

## 4. 视觉改造范围

### 4.1 保留的内容

- 现有布局尺寸、分栏关系、信息层次
- `TreeCanvasWidget` 的交互主骨架：缩放、平移、折叠、追踪、minimap、底部 breadcrumb
- 右侧清单现有字段口径

### 4.2 替换的内容

- `ControlBarWidget`、`ViewSwitcherWidget`、`OmniCalcSidebar` 里的 web 风圆角按钮、pill、阴影
- `MatrixViewWidget`、`MilestonesViewWidget` 中纯色占位式卡片与表头
- `TreeCanvasWidget` 中仍然明显偏 web 的面板皮肤、搜索框、滚动条、条目标识

### 4.3 素材来源

优先使用 [`CirrusTextures`](D:\ProjectDir\CloudMods\CalculatorCirrus\Mod\src\main\java\dev\vfyjxf\cirrus\ui\CirrusTextures.java) 已注册素材：

- `background/*`
- `background/outlined/*`
- `background/tab/*`
- `toggle/*`
- `icon/calc/*`
- `icon/sign/*`

补图来源：

- `D:\ProjectDir\CloudMods\gui_output (1).png`
- `D:\ProjectDir\CloudMods\Untitled 03-15-2026 12-45-49.png`

这些图集用于补齐当前代码还未接入的 tab 状态、小按钮态、勾选态、轨迹线片段、清单辅助标识和小型边框切片。

## 5. 组件职责

### 5.1 `OmniCalculatorView`

- 继续作为 `omnicalc` 计算器页面根组件。
- 管理控制栏、空态/结果态、视图切换和内部主区域。
- 不额外拆出新的主布局组件。

### 5.2 `TreeViewWidget` / `TreeCanvasWidget`

- 继续负责树图主交互和现有材料/路径信息展示。
- 这次以“样式替换 + 与 web 参考图对齐”为主，不重写树图算法。

### 5.3 `MatrixViewWidget`

- 从静态展示升级到对齐 `omnicalc-pro` 布局的聚合矩阵页。
- 保留当前可复用的数据口径，不强行照搬 web 版所有字段。

### 5.4 `MilestonesViewWidget`

- 从静态示意升级到对齐 `omnicalc-pro` 的阶段列式任务板。
- 保持轻量游戏化处理，只替换视觉语言，不改变板块结构。

## 6. 风险与控制

- `TreeCanvasWidget` 承载了大量命中、缩放和滚动逻辑，任何视觉替换都不能顺手改动交互热区。
- Matrix / Milestones 当前更接近占位实现，补齐时要避免各自分叉出不同的数据来源。
- 补切贴图必须延续现有资源目录与命名体系，不能临时发明第二套素材结构。
- UI 验证不能只依赖静态阅读和单元测试，必须通过 ModDevMCP 的 `status.get`、`ui.inspect`、`ui.capture` 和 `hotswap.reload` 在游戏内检查真实界面。

## 7. 验收标准

- `omnicalc` 计算器固定布局保持不变。
- 计算器内容成功挂入 `TabContainer` 的计算器页签。
- Tree / Matrix / Milestones 三视图都统一到 Cirrus 的视觉语言。
- 右侧物料与资源清单继续沿用现有实现，但材质、状态图标和边框统一为 Cirrus 素材。
- 缺失贴图补齐后，不再依赖字符模拟或临时纯色块伪装正式控件状态。
- 至少完成一次基于 ModDevMCP 的游戏内 UI 检查，并保留 capture 作为视觉验证证据。
