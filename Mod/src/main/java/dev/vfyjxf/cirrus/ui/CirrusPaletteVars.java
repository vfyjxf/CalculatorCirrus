package dev.vfyjxf.cirrus.ui;

import dev.vfyjxf.cloudlib.api.ui.style.StyleVar;
import dev.vfyjxf.cloudlib.api.ui.texture.VisualTexture;

public final class CirrusPaletteVars {

    public static final StyleVar<Integer> backdropScrim = StyleVar.color("--cirrus-backdrop-scrim");
    public static final StyleVar<Integer> canvasVoid = StyleVar.color("--cirrus-canvas-void");
    public static final StyleVar<Integer> canvasGridMinor = StyleVar.color("--cirrus-canvas-grid-minor");
    public static final StyleVar<Integer> canvasGridMajor = StyleVar.color("--cirrus-canvas-grid-major");
    public static final StyleVar<Integer> dockSurface = StyleVar.color("--cirrus-dock-surface");
    public static final StyleVar<Integer> cardSurface = StyleVar.color("--cirrus-card-surface");
    public static final StyleVar<Integer> cardSurfaceHover = StyleVar.color("--cirrus-card-surface-hover");
    public static final StyleVar<Integer> cardSurfaceSelected = StyleVar.color("--cirrus-card-surface-selected");
    public static final StyleVar<Integer> recessedSurface = StyleVar.color("--cirrus-recessed-surface");
    public static final StyleVar<Integer> popoverSurface = StyleVar.color("--cirrus-popover-surface");
    public static final StyleVar<Integer> hairlineBorder = StyleVar.color("--cirrus-hairline-border");
    public static final StyleVar<Integer> hairlineHighlight = StyleVar.color("--cirrus-hairline-highlight");
    public static final StyleVar<Integer> focusRing = StyleVar.color("--cirrus-focus-ring");
    public static final StyleVar<Integer> dropShadow = StyleVar.color("--cirrus-drop-shadow");
    public static final StyleVar<Integer> textPrimary = StyleVar.color("--cirrus-text-primary");
    public static final StyleVar<Integer> textSecondary = StyleVar.color("--cirrus-text-secondary");
    public static final StyleVar<Integer> textMuted = StyleVar.color("--cirrus-text-muted");
    public static final StyleVar<Integer> textDisabled = StyleVar.color("--cirrus-text-disabled");
    public static final StyleVar<Integer> textOnAccent = StyleVar.color("--cirrus-text-on-accent");
    public static final StyleVar<Integer> textOnDark = StyleVar.color("--cirrus-text-on-dark");
    public static final StyleVar<Integer> costH = StyleVar.color("--cirrus-cost-h");
    public static final StyleVar<Integer> costP = StyleVar.color("--cirrus-cost-p");
    public static final StyleVar<Integer> costK = StyleVar.color("--cirrus-cost-k");
    public static final StyleVar<Integer> costS = StyleVar.color("--cirrus-cost-s");
    public static final StyleVar<Integer> costC = StyleVar.color("--cirrus-cost-c");
    public static final StyleVar<Integer> ruleHardBan = StyleVar.color("--cirrus-rule-hard-ban");
    public static final StyleVar<Integer> ruleHardPin = StyleVar.color("--cirrus-rule-hard-pin");
    public static final StyleVar<Integer> ruleSoftPrefer = StyleVar.color("--cirrus-rule-soft-prefer");
    public static final StyleVar<Integer> ruleSoftPenalize = StyleVar.color("--cirrus-rule-soft-penalize");
    public static final StyleVar<Integer> advisoryDiscouraged = StyleVar.color("--cirrus-advisory-discouraged");
    public static final StyleVar<Integer> advisoryLastResort = StyleVar.color("--cirrus-advisory-last-resort");
    public static final StyleVar<Integer> edgeMaterial = StyleVar.color("--cirrus-edge-material");
    public static final StyleVar<Integer> edgeCatalyst = StyleVar.color("--cirrus-edge-catalyst");
    public static final StyleVar<Integer> edgeMachine = StyleVar.color("--cirrus-edge-machine");
    public static final StyleVar<Integer> edgeCriticalPath = StyleVar.color("--cirrus-edge-critical-path");
    public static final StyleVar<Integer> edgeUpstreamTrace = StyleVar.color("--cirrus-edge-upstream-trace");
    public static final StyleVar<Integer> edgeDownstreamTrace = StyleVar.color("--cirrus-edge-downstream-trace");
    public static final StyleVar<Integer> diffAdded = StyleVar.color("--cirrus-diff-added");
    public static final StyleVar<Integer> diffRemoved = StyleVar.color("--cirrus-diff-removed");

    public static final StyleVar<Integer> acquireAccent = StyleVar.color("--cirrus-step-acquire-accent");
    public static final StyleVar<Integer> acquireTintBg = StyleVar.color("--cirrus-step-acquire-bg");
    public static final StyleVar<Integer> acquireBorderIdle = StyleVar.color("--cirrus-step-acquire-idle");
    public static final StyleVar<Integer> acquireBorderSelected = StyleVar.color("--cirrus-step-acquire-selected");
    public static final StyleVar<Integer> craftAccent = StyleVar.color("--cirrus-step-craft-accent");
    public static final StyleVar<Integer> craftTintBg = StyleVar.color("--cirrus-step-craft-bg");
    public static final StyleVar<Integer> craftBorderIdle = StyleVar.color("--cirrus-step-craft-idle");
    public static final StyleVar<Integer> craftBorderSelected = StyleVar.color("--cirrus-step-craft-selected");
    public static final StyleVar<Integer> convertAccent = StyleVar.color("--cirrus-step-convert-accent");
    public static final StyleVar<Integer> convertTintBg = StyleVar.color("--cirrus-step-convert-bg");
    public static final StyleVar<Integer> convertBorderIdle = StyleVar.color("--cirrus-step-convert-idle");
    public static final StyleVar<Integer> convertBorderSelected = StyleVar.color("--cirrus-step-convert-selected");
    public static final StyleVar<Integer> cycleLoopAccent = StyleVar.color("--cirrus-step-cycle-loop-accent");
    public static final StyleVar<Integer> cycleLoopTintBg = StyleVar.color("--cirrus-step-cycle-loop-bg");
    public static final StyleVar<Integer> cycleLoopBorderIdle = StyleVar.color("--cirrus-step-cycle-loop-idle");
    public static final StyleVar<Integer> cycleLoopBorderSelected = StyleVar.color("--cirrus-step-cycle-loop-selected");
    public static final StyleVar<Integer> provisionMachineAccent = StyleVar
            .color("--cirrus-step-provision-machine-accent");
    public static final StyleVar<Integer> provisionMachineTintBg = StyleVar.color("--cirrus-step-provision-machine-bg");
    public static final StyleVar<Integer> provisionMachineBorderIdle = StyleVar
            .color("--cirrus-step-provision-machine-idle");
    public static final StyleVar<Integer> provisionMachineBorderSelected = StyleVar
            .color("--cirrus-step-provision-machine-selected");
    public static final StyleVar<Integer> setupLineAccent = StyleVar.color("--cirrus-step-setup-line-accent");
    public static final StyleVar<Integer> setupLineTintBg = StyleVar.color("--cirrus-step-setup-line-bg");
    public static final StyleVar<Integer> setupLineBorderIdle = StyleVar.color("--cirrus-step-setup-line-idle");
    public static final StyleVar<Integer> setupLineBorderSelected = StyleVar.color("--cirrus-step-setup-line-selected");
    public static final StyleVar<Integer> linePullAccent = StyleVar.color("--cirrus-step-line-pull-accent");
    public static final StyleVar<Integer> linePullTintBg = StyleVar.color("--cirrus-step-line-pull-bg");
    public static final StyleVar<Integer> linePullBorderIdle = StyleVar.color("--cirrus-step-line-pull-idle");
    public static final StyleVar<Integer> linePullBorderSelected = StyleVar.color("--cirrus-step-line-pull-selected");

    public static final StyleVar<Integer> meterP = StyleVar.color("--meter-p");
    public static final StyleVar<Integer> meterK = StyleVar.color("--meter-k");
    public static final StyleVar<Integer> meterC = StyleVar.color("--meter-c");
    public static final StyleVar<Integer> meterOk = StyleVar.color("--meter-ok");
    public static final StyleVar<Integer> meterWarn = StyleVar.color("--meter-warn");
    public static final StyleVar<Integer> meterDanger = StyleVar.color("--meter-danger");
    public static final StyleVar<Integer> meterFocus = StyleVar.color("--meter-focus");

    public static final StyleVar<VisualTexture> texDock = StyleVar.texture("--cirrus-tex-dock");
    public static final StyleVar<VisualTexture> texCard = StyleVar.texture("--cirrus-tex-card");
    public static final StyleVar<VisualTexture> texCardHover = StyleVar.texture("--cirrus-tex-card-hover");
    public static final StyleVar<VisualTexture> texCardSelected = StyleVar.texture("--cirrus-tex-card-selected");
    public static final StyleVar<VisualTexture> texFlat = StyleVar.texture("--cirrus-tex-flat");
    public static final StyleVar<VisualTexture> texInset = StyleVar.texture("--cirrus-tex-inset");
    public static final StyleVar<VisualTexture> texRecessed = StyleVar.texture("--cirrus-tex-recessed");
    public static final StyleVar<VisualTexture> texSlot = StyleVar.texture("--cirrus-tex-slot");
    public static final StyleVar<VisualTexture> texSlotDark = StyleVar.texture("--cirrus-tex-slot-dark");
    public static final StyleVar<VisualTexture> texBtnUp = StyleVar.texture("--cirrus-tex-btn-up");
    public static final StyleVar<VisualTexture> texBtnHover = StyleVar.texture("--cirrus-tex-btn-hover");
    public static final StyleVar<VisualTexture> texBtnDown = StyleVar.texture("--cirrus-tex-btn-down");
    public static final StyleVar<VisualTexture> texCtaPrimary = StyleVar.texture("--cirrus-tex-cta-primary");
    public static final StyleVar<VisualTexture> texCtaDanger = StyleVar.texture("--cirrus-tex-cta-danger");
    public static final StyleVar<VisualTexture> texSwitchOn = StyleVar.texture("--cirrus-tex-switch-on");
    public static final StyleVar<VisualTexture> texSwitchOnHover = StyleVar.texture("--cirrus-tex-switch-on-hover");
    public static final StyleVar<VisualTexture> texSwitchOff = StyleVar.texture("--cirrus-tex-switch-off");
    public static final StyleVar<VisualTexture> texSwitchOffHover = StyleVar.texture("--cirrus-tex-switch-off-hover");
    public static final StyleVar<VisualTexture> texScrollTrack = StyleVar.texture("--cirrus-tex-scroll-track");
    public static final StyleVar<VisualTexture> texScrollThumb = StyleVar.texture("--cirrus-tex-scroll-thumb");
    public static final StyleVar<VisualTexture> texScrollThumbHorizontal = StyleVar
            .texture("--cirrus-tex-scroll-thumb-h");
    public static final StyleVar<VisualTexture> texScrollTrackFill = StyleVar.texture("--cirrus-tex-scroll-track-fill");
    public static final StyleVar<VisualTexture> texMinimapFrame = StyleVar.texture("--cirrus-tex-minimap-frame");
    public static final StyleVar<VisualTexture> texMeterTrack = StyleVar.texture("--cirrus-tex-meter-track");
    public static final StyleVar<VisualTexture> texTripleLeftUnselected = StyleVar
            .texture("--cirrus-tex-triple-left-off");
    public static final StyleVar<VisualTexture> texTripleLeftSelected = StyleVar.texture("--cirrus-tex-triple-left-on");
    public static final StyleVar<VisualTexture> texTripleMiddleLeft = StyleVar.texture("--cirrus-tex-triple-mid-left");
    public static final StyleVar<VisualTexture> texTripleMiddleMiddle = StyleVar.texture("--cirrus-tex-triple-mid");
    public static final StyleVar<VisualTexture> texTripleMiddleRight = StyleVar
            .texture("--cirrus-tex-triple-mid-right");
    public static final StyleVar<VisualTexture> texTripleRightSelected = StyleVar
            .texture("--cirrus-tex-triple-right-on");
    public static final StyleVar<VisualTexture> texTripleRightUnselected = StyleVar
            .texture("--cirrus-tex-triple-right-off");

    private CirrusPaletteVars() {}
}
