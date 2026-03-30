# OmniCalc 组件审计记录（2026-03-23）

## 范围

- 目标：对 Calculator UI 做组件级导出与布局评估，并形成可执行修正清单。
- 约束：不改整体布局结构，仅做样式与细节微调。
- 运行环境：单人世界，窗口最大化（非全屏），`CalculatorScreen`。

## 证据截图索引

- 最新树页全图：
  - `Mod/runs/client/build/moddevmcp/captures/capture-4.png`
- 最新现场快照（本轮继续精修）：
  - `Mod/runs/client/build/moddevmcp/captures/capture-71.png`
  - `Mod/runs/client/build/moddevmcp/captures/capture-72.png`
  - `Mod/runs/client/build/moddevmcp/captures/capture-73.png`
- 组件导出（本轮审计目录）：
  - `Mod/runs/client/build/moddevmcp/captures/components-2026-03-23-audit-all`
  - 包含：
    - `01_full_tree_latest.png`
    - `02_controlbar.png`
    - `03_global_left_sidebar.png`
    - `04_view_tabs.png`
    - `05_tree_left_layer_panel.png`
    - `06_tree_minimap.png`
    - `07_tree_canvas_focus.png`
    - `08_tree_card_header.png`
    - `09_tree_card_body.png`
    - `10_material_panel_header.png`
    - `11_material_panel_list.png`
    - `12_bottom_path_strip.png`
    - `13_full_matrix_reference.png`
    - `14_matrix_header.png`
    - `15_matrix_table_header.png`
    - `16_matrix_rows.png`
    - `17_milestones_header_reference.png`
    - `18_milestones_board_reference.png`
    - `19_viewswitcher_reference.png`
    - `20_controlbar_reference_old.png`
- Web 对照（Playwright）：
  - `D:/ProjectDir/CloudMods/omnicalc-pro/playwright-captures/2026-03-23-recheck2/web-1-tree.png`
  - `D:/ProjectDir/CloudMods/omnicalc-pro/playwright-captures/2026-03-23-recheck2/web-2-matrix.png`
  - `D:/ProjectDir/CloudMods/omnicalc-pro/playwright-captures/2026-03-23-recheck2/web-3-milestones.png`

## 组件布局评估

### P1（优先修正）

1. Layer 栏（`05_tree_left_layer_panel.png`）
   - 顶部示意区域与列表按钮风格不统一，视觉层级冲突。
   - 单个按钮内图标、层号、计数之间的间距与对齐不稳定（重心偏右）。
2. 树卡片头部（`08_tree_card_header.png`）
   - 顶部色条与头部过渡仍偏硬，标题基线略高，整体“压顶”感较重。
3. 底部 Path 栏（`12_bottom_path_strip.png`）
   - 左右两段文本渲染风格不一致（左侧显著更糊、更靠底）。
   - 左侧图标与标题文本间距偏紧，滚动条轨道与文本带之间留白不足。

### P2（次级修正）

1. 材料清单（`10_material_panel_header.png`, `11_material_panel_list.png`）
   - 条目标题与数值行虽然已分行，但标题/数值的竖向节奏偏挤（尤其首屏前 4 条）。
   - 头部“材料清单”字色偏浅，和浅灰背景对比不足。
2. 顶部 ControlBar（`02_controlbar.png`, `20_controlbar_reference_old.png`）
   - 中段底纹仍有“条带感”，在窄屏时视觉割裂；更适合直接使用统一 nine-slice 背景块。
   - 计算按钮图标位置可再向左收 1px，避免与文字视觉重心冲突。
3. 矩阵/里程碑参考视图（`14-18`）
   - 矩阵：表头层次可用，但行内 chip 与主文本基线略不齐，右侧 chip 组局部拥挤。
   - 里程碑：phase 列垂直滚动逻辑正确，但列头进度条与上方总进度条视觉“节奏”不一致。

## 与 Web 版本对照结论（Playwright）

- Tree：结构映射已对齐（左 Layer、中树图、右材料、底部 Path/Trace），当前偏差主要在字体对比和像素面板层级。
- Matrix：列结构一致，但 Cirrus 版应继续走亮色像素化语义，不需复刻 Web 的暗色发光风格。
- Milestones：分阶段列 + 独立纵向滚动已满足；待优化点集中在列头/卡片进度条的对齐与留白。

## 已执行改动快照（本次审计前）

- 树卡片背景：改为现成纹理优先，减少纯色混合叠加。
- 树卡片顶部条：改为素材条 + 单线强调，去除混合感较重的叠层。
- Layer 栏：已做一轮基础重排（icon + 层号 + count badge）。

## 下一步执行策略

1. 先完成 P1 的三项（Layer 栏、树卡片头部、Path 栏文本一致性）并实机回归。
2. 每完成一项：
   - 编译校验（`compileJava`）
   - ModDevMCP 截图留档
   - 与审计图对照，确认后再进下一项。
3. P1 通过后再进入 P2 微调。

## 阶段验证记录

### 阶段 A（P1 第一轮）已验证

- 编译：`./gradlew :Mod:compileJava --no-daemon` 通过。
- 游戏端：已重启客户端并最大化窗口，进入单人世界后打开 `OmniCalcScreen` 回归。
- 留档：
  - `Mod/runs/client/build/moddevmcp/captures/capture-2.png`
  - `Mod/runs/client/build/moddevmcp/captures/components-2026-03-23-pass-p1/p1-01-full-tree.png`
  - `Mod/runs/client/build/moddevmcp/captures/components-2026-03-23-pass-p1/p1-02-layer-panel.png`
  - `Mod/runs/client/build/moddevmcp/captures/components-2026-03-23-pass-p1/p1-03-tree-card-header.png`
  - `Mod/runs/client/build/moddevmcp/captures/components-2026-03-23-pass-p1/p1-04-bottom-path-strip.png`
  - `Mod/runs/client/build/moddevmcp/captures/components-2026-03-23-pass-p1/p1-05-controlbar.png`
- 结果：
  - `Layer` 栏：头部示意与按钮排版已统一，按钮内图标/层号/计数对齐改善。
  - 树卡片头：顶部色条与头部过渡改为纹理主导，标题基线下移，压顶感降低。
  - 底部 `Path`：左/右两段文本均改为无缩放像素字渲染，风格一致性提升。
- 剩余：
  - `ControlBar` 中段底纹与计算按钮细节仍可继续微调（P2）。
  - 材料清单与矩阵/里程碑仍需做二轮细修（P2）。

### 阶段 B（全组件复查）分析记录

- 复查证据：
  - `Mod/runs/client/build/moddevmcp/captures/capture-21.png`（Tree）
  - `Mod/runs/client/build/moddevmcp/captures/capture-22.png`（Matrix）
  - `Mod/runs/client/build/moddevmcp/captures/capture-23.png`（Milestones）
  - `Mod/runs/client/build/moddevmcp/captures/components-2026-03-23-final-pass/*`
- Web 对照再次确认：
  - `D:/ProjectDir/CloudMods/omnicalc-pro/playwright-captures/2026-03-23-recheck2/web-1-tree.png`
  - `D:/ProjectDir/CloudMods/omnicalc-pro/playwright-captures/2026-03-23-recheck2/web-2-matrix.png`
  - `D:/ProjectDir/CloudMods/omnicalc-pro/playwright-captures/2026-03-23-recheck2/web-3-milestones.png`

#### 发现问题（按本轮执行顺序）

1. ControlBar（P1）
   - 顶部中段仍偏“平铺灰条”观感，未充分体现独立 nine-slice 条块层级。
   - 计算按钮 icon 与文字视觉重心偏右，按钮内边距不稳定。
2. 材料清单（P2）
   - 条目标题和两行数值虽已分行，但数值行离标题仍偏紧，首屏密度偏高。
3. Matrix（P2）
   - 输出列文本存在 `++` 前缀拼接问题（数据值已带 `+`，渲染又追加 `+`）。
   - 输入 chip 文本基线略偏下，右列阅读节奏偏挤。
4. Milestones（P2）
   - 总进度条与 phase 进度条的内边距和线宽节奏仍有轻微差异，需统一到同一视觉节拍。

#### 本轮确认标准

- 每一步都执行：
  - `:Mod:compileJava` 通过；
  - 进入单人世界，窗口最大化（非全屏）；
  - `ui.capture` 留档（整页+组件裁图）；
  - 对照上述问题逐条确认“已消除”后再进入下一步。

### 阶段 C（本轮逐步验收）已验证

- Step-1 `ControlBar`
  - 改动：顶部中段改回 `control_bar` 纹理条块；计算按钮图标左移并重排文字重心。
  - 验证：
    - 编译通过：`:Mod:compileJava`
    - 实机留档：`Mod/runs/client/build/moddevmcp/captures/capture-2.png`（CalculatorScreen）
  - 结论：中段不再是纯平铺灰条，按钮视觉重心恢复正常。

- Step-2 `Layer` 面板（根据新增反馈插入）
  - 改动：
    - 列表按钮改成纯 `L0/L1/L2...` 文本；
    - 只保留顶部示意区图标；
    - 去除图标外多余框体。
  - 验证：
    - 编译通过：`:Mod:compileJava`
    - 实机留档：`Mod/runs/client/build/moddevmcp/captures/capture-2.png`（OmniCalcScreen tree 页）
  - 结论：左侧层级列表已简化为文本层号表达，顶部示意区保留图标但不再有多余包框。

- Step-3 `Matrix` / `Milestones`
  - 改动：
    - `Matrix` 行输出前缀修正，去掉重复 `+`；
    - 输入 chip 轻微左移并上调文字基线；
    - `Milestones` 总进度条与 phase 进度条统一左右内边距与填充起点。
  - 验证：
    - 编译通过：`:Mod:compileJava`
    - 实机留档：
      - `Mod/runs/client/build/moddevmcp/captures/capture-1.png`（Matrix）
      - `Mod/runs/client/build/moddevmcp/captures/capture-2.png`（Milestones）
  - 结论：
    - `Matrix` 首列不再出现 `++`，右侧 chip 排版更稳；
    - `Milestones` 总/分阶段进度条节奏已更接近统一。

### 阶段 D（树卡片与左侧文本精修）已验证

- 触发原因：
  - 树卡片右上 `fork/focus` 只有一个小字符，点击区和视觉存在感都不足；
  - 卡片信息层级过于扁平，机器信息缺少扩展位；
  - 左侧四个主导航文本仍使用阴影，字色偏重。
- 根因：
  1. `TreeCanvasWidget` 里 `focus` 入口只是 `⊕` 文本，点击判定仍按 16×16 小区域硬编码。
  2. 卡片正文只剩 `产物 + tier/time` 两行，缺少独立的机器名、配方名、额外运行信息组织。
  3. `OmniCalcSidebar.NavButton` 的 `LabelWidget` 仍为 `.setShadow(true)`。
- 本轮改动：
  1. 为树卡片新增 `focusButtonBounds(...)` 与 `buildStepMetaLines(...)` 辅助逻辑。
  2. `fork/focus` 改为真实像素按钮，使用现成图标纹理而非字符占位。
  3. 树卡片头部改为：机器图标 + 机器名 + 配方名 + 独立按钮。
  4. 树卡片正文改为：产物图标 + 产物名 + 数量 + 扩展元信息行（等级/时长、EU/t、可选机型数等）。
  5. 左侧四个导航文本取消阴影，并整体上调亮色文本色阶。
- 自动验证：
  - `:Mod:test --tests "*TreeCanvasWidgetPathNavigationTest" --no-daemon` 通过。
  - `:Mod:compileJava --no-daemon` 通过。
- 实机验证：
  - 客户端已重启，窗口已最大化（非全屏），进入单人世界 `Codex UITest (1)`。
  - `fork/focus` 已实际点击验证，树上下文发生切换，说明按钮不仅可见且可交互。
- 留档：
  - `Mod/runs/client/build/moddevmcp/captures/capture-1.png`（重启后概览）
  - `Mod/runs/client/build/moddevmcp/captures/capture-2.png`（完整树页）
  - `Mod/runs/client/build/moddevmcp/captures/capture-3.png`（点击 `fork/focus` 后的子树上下文）
  - `Mod/runs/client/build/moddevmcp/captures/capture-4.png`（回到完整树页后的最终停留画面）

### 阶段 E（规范对齐二轮收敛）已验证

- 根因分析：
  1. 树卡片头部与正文都停留在同一组浅灰语义里，机器信息和产物信息层次不够分明。
  2. `Layer` 面板按钮普遍使用嵌套框体，视觉过重，不符合“仅保留 `L0/L1...` 简洁层号”的要求。
  3. `Matrix` 右侧 `chip` 文本没有在渲染前截断，长文本会挤出槽位边界。
  4. `Milestones` 总进度条、phase 进度条、卡片进度条使用了不同内边距，节拍不统一。
- 本轮改动：
  1. `TreeCanvasWidget`
     - 树卡片改为更明确的头/身分离：深色头部 + 白字硬阴影；机器图标与产物图标统一回到 `Slot` 容器。
     - 去掉卡片外侧多余软阴影，保留像素边框与追踪高亮。
  2. `TreeViewWidget`
     - `Layer` 按钮统一为 `L0/L1/L2...` 简洁文本。
     - 头部示意区改为轻量预览，不再堆叠额外小框。
  3. `MatrixViewWidget`
     - 新增长文本截断规则，右侧输入 `chip` 不再穿出边界。
  4. `MilestonesViewWidget`
     - 将 summary / phase / step 三类进度条统一到同一水平内边距。
- 自动验证：
  - `:Mod:test --tests "*TreeCanvasWidgetPathNavigationTest" --tests "*TreeViewWidgetContractTest" --tests "*MatrixMilestonesContractTest" --no-daemon --no-configuration-cache` 通过。
- 实机验证：
  - 客户端已重启。
  - 已通过 ModDevMCP 进入单人世界 `Codex UITest (1)`。
  - 窗口已重新最大化（非全屏）。
  - 已逐页检查 Tree / Matrix / Milestones。
- 本轮留档：
  - `Mod/runs/client/build/moddevmcp/captures/capture-1.png`（Tree）
  - `Mod/runs/client/build/moddevmcp/captures/capture-2.png`（Matrix）
  - `Mod/runs/client/build/moddevmcp/captures/capture-4.png`（Milestones）

### 阶段 F（布局终极提示词兼容收敛）已验证

- 目标：
  - 不推翻现有 Calculator 壳层的前提下，把布局进一步向“固定像素约束 + 高密度工业终端”收紧。
- 根因分析：
  1. `ControlBar` 仍然偏高，目标槽、倍率输入和计算按钮的尺寸没有压回固定像素节奏。
  2. 左侧主侧栏与 Tree 内部 `Layer` 栏虽然已可用，但按钮间距和图标槽位仍偏松。
  3. `ViewSwitcher` 与右上角树页动作按钮仍偏大，顶栏纵向占用过多。
  4. `Matrix` 行高依旧偏宽松，不符合高密度报表感。
- 本轮改动：
  1. `ControlBarWidget`
     - 固定高度收紧为紧凑条；
     - 目标槽改为标准 18px 槽位；
     - 数量输入缩到 36px；
     - 计算按钮缩到 60px 宽的紧凑按钮。
  2. `OmniCalcSidebar`
     - 保持 `56px / 20px` 的刚性展开/折叠宽度；
     - 导航按钮的图标槽位统一为 16px，图标和文本 gap 收到 2px；
     - 折叠状态仍通过隐藏文本脱离布局树保留紧凑感。
  3. `ViewSwitcherWidget`
     - 顶部页签带与右侧动作按钮同步收紧；
     - 动作按钮降到 16px。
  4. `TreeViewWidget`
     - `Layer` 栏宽度缩到 36px；
     - 底部 Path/Trace 条缩到 18px。
  5. `MatrixViewWidget`
     - 行高压到 30px；
     - 行内图标、双行文本与右侧 chip 的 Y 位置随之重排。
- 自动验证：
  - `:Mod:test --tests "*OmniCalculatorViewContractTest" --tests "*TreeViewWidgetContractTest" --tests "*MatrixMilestonesContractTest" --no-daemon --no-configuration-cache` 通过。
- 实机验证：
  - 客户端已再次重启并最大化（非全屏）；
  - 已进入单人世界 `Codex UITest (1)` 并打开 `OmniCalcScreen`；
  - Tree 页已实机截图确认顶栏、侧栏、Layer 栏与材料面板的压缩布局生效；
  - 本轮 `Matrix` 页切换在 ModDevMCP 坐标点击下未稳定切换，因此 `Matrix` 的最终确认以自动化断言为主，游戏现场保持停留在 `Tree` 页供继续手调。
- 本轮留档：
  - `Mod/runs/client/build/moddevmcp/captures/capture-1.png`（Tree，终极布局收敛后）
  - `Mod/runs/client/build/moddevmcp/captures/capture-2.png`（Tree，点击后再次确认）
  - `Mod/runs/client/build/moddevmcp/captures/capture-3.png`（Tree，再次尝试切换后的现场）
  - `Mod/runs/client/build/moddevmcp/captures/capture-4.png`（Tree，framebuffer 坐标尝试后的现场）

### 阶段 G（里程碑紧贴进度条与树页分区修正）已验证

- 触发原因：
  - 里程碑总览头部还保留了一条可滚动说明文本，进度条位置偏松；
  - 里程碑步骤卡缺少用户手动完成标记；
  - 树页 `Layer` 头仍是文字+示意混排；
  - 树卡片的机器区与物料区仍靠贴图块生硬断开，右上角按钮也不是 fork 语义；
  - 小地图标题与追踪线 glow 仍在干扰画面。
- 根因：
  1. `MilestonesViewWidget` 头部和步骤卡仍沿用旧的 subtitle / progress 布局。
  2. `MilestoneBoardWidget` 没有独立的手动完成状态和点击命中区。
  3. `TreeViewWidget.LayerHeaderWidget` 仍直接绘制 `"LAYER"` 文本。
  4. `TreeCanvasWidget` 仍保留 tracked glow 和 minimap title，且 step card 只靠头/body 贴图边界分区。
- 本轮改动：
  1. `MilestonesViewWidget`
     - 删除 summary subtitle，收紧 summary bar 的垂直位置；
     - 调整 phase progress 与 step progress 的 Y 位；
     - 为每个步骤卡加入手动 `Done` 勾选，状态独立保存并可点击切换。
  2. `TreeViewWidget`
     - `Layer` 顶部头改成纯图标展示，不再混排文字说明。
  3. `TreeCanvasWidget`
     - 去掉 tracked line glow，只保留干净的高亮连线；
     - 去掉 minimap 标题，让内容区直接占满；
     - step card 改成手绘分隔线，拉开机器区和产物/元信息区间距；
     - 焦点按钮改成小尺寸 fork glyph，而不是误用 path 纹理。
- 自动验证：
  - `:Mod:test --tests "*TreeCanvasWidgetContractTest" --tests "*TreeCanvasWidgetPathNavigationTest" --tests "*TreeViewWidgetContractTest" --tests "*MatrixMilestonesContractTest" --no-daemon --no-configuration-cache` 通过。
- 实机验证：
  - 客户端已重启，窗口再次最大化（非全屏），进入单人世界 `Codex UITest (1)`。
  - 已打开 `OmniCalcScreen`，完成一次计算并切换到 `Tree` / `Milestones` 实机检查。
  - `Milestones` 页签切换与截图已通过 ModDevMCP 的 `input.action click` 在 GUI 坐标下验证成功。
- 本轮留档：
  - `Mod/runs/client/build/moddevmcp/captures/capture-8.png`（Tree，移除 minimap title / tracked glow 后）
  - `Mod/runs/client/build/moddevmcp/captures/capture-9.png`（Milestones，总览头和 phase/step 进度条收紧后）
  - `Mod/runs/client/build/moddevmcp/captures/capture-10.png`（Milestones，手动完成勾选交互检查现场）

### 阶段 H（里程碑单色进度条与轻量头部）已验证

- 触发原因：
  - 里程碑总进度条和 phase / step 进度条仍没有贴满可用容器宽度；
  - 卡片背景和总览头部边框偏重，视觉层次过多；
  - 手动完成控件还是小勾选，不够像现成的开关组件；
  - 进度条填充色混用了多种状态色。
- 本轮改动：
  1. `MilestonesViewWidget`
     - `PROGRESS_INSET` 收到 `0`，总览 / phase / step 三类进度条都按容器整宽铺开；
     - 进度条填充统一为固定绿色，不再跟随步骤状态切换多色；
     - 手动完成控件改为现成 `Toggle.Switch.slideOn/slideOff` 纹理；
     - `Factory Milestones` 标题改为更直接的 `Milestone Progress`；
     - 总览头去掉重外框，改成单层 `controlBar` 风格；
     - 列与卡片背景从重框体切到更轻的 `Outlined.flat + flat` 组合。
- 自动验证：
  - `:Mod:test --tests "*MatrixMilestonesContractTest" --no-daemon --no-configuration-cache` 通过。
- 实机验证：
  - 客户端已重启，窗口重新最大化（非全屏），进入 `Codex UITest (1)`。
  - 已从背包稳定打开 `OmniCalcScreen`，计算后切换到 `Milestones` 页。
  - 现场图确认：
    - 总进度条整宽铺开；
    - phase / step 条统一为单色绿；
    - 卡片内完成控件已替换成现成 switch。
- 本轮留档：
  - `Mod/runs/client/build/moddevmcp/captures/capture-2.png`（Milestones，单色整宽进度条 + switch 版完成控件）
