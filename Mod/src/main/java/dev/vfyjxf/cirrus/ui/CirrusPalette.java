package dev.vfyjxf.cirrus.ui;

import dev.vfyjxf.cirrus.api.planning.graph.RecipeAdvisory;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cloudlib.api.ui.style.StyleVar;
import dev.vfyjxf.cloudlib.api.ui.style.Theme;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public record CirrusPalette(
    int backdropScrim,
    int canvasVoid,
    int canvasGridMinor,
    int canvasGridMajor,
    int dockSurface,
    int cardSurface,
    int cardSurfaceHover,
    int cardSurfaceSelected,
    int recessedSurface,
    int popoverSurface,
    int hairlineBorder,
    int hairlineHighlight,
    int focusRing,
    int dropShadow,
    int textPrimary,
    int textSecondary,
    int textMuted,
    int textDisabled,
    int textOnAccent,
    int textOnDark,
    int costH,
    int costP,
    int costK,
    int costS,
    int costC,
    int ruleHardBan,
    int ruleHardPin,
    int ruleSoftPrefer,
    int ruleSoftPenalize,
    int advisoryDiscouraged,
    int advisoryLastResort,
    int edgeMaterial,
    int edgeCatalyst,
    int edgeMachine,
    int edgeCriticalPath,
    int edgeUpstreamTrace,
    int edgeDownstreamTrace,
    int diffAdded,
    int diffRemoved,
    StepTint acquireStep,
    StepTint craftStep,
    StepTint convertStep,
    StepTint cycleLoopStep,
    StepTint provisionMachineStep,
    StepTint setupLineStep,
    StepTint linePullStep
) {

    public record StepTint(int accent, int tintBg, int borderIdle, int borderSelected) {}

    public static final CirrusPalette obsidianBlueprint = new CirrusPalette(
        0xCC0D1016,
        0xFF1E232D,
        0x14E6EDF7,
        0x2AE6EDF7,
        0xF7292F3B,
        0xFF343B4A,
        0xFF3F485A,
        0xFF3B4659,
        0xFF222834,
        0xFA2D3442,
        0xFF434C5E,
        0x24FFFFFF,
        0xFF63BCFC,
        0x7A000000,
        0xFFF2F5FA,
        0xFFC9D3E0,
        0xFF9BA8BC,
        0xFF687386,
        0xFFFFFFFF,
        0xFFF2F5FA,
        0xFFF6BD4B,
        0xFF5AD0F5,
        0xFFFA9D52,
        0xFF56DB8B,
        0xFFCBA8FF,
        0xFFFF858E,
        0xFF63BCFC,
        0xFF56DB8B,
        0xFFF6BD4B,
        0xFFF6BD4B,
        0xFFFF858E,
        0xD9AABBD0,
        0xE6F6D854,
        0xD9FA9D52,
        0xFFF6BD4B,
        0xFF63BCFC,
        0xFFFA9D52,
        0xFF56DB8B,
        0xFFFF858E,
        new StepTint(0xFF56DB8B, 0x1F56DB8B, 0xFF386B52, 0xFF7CEBA8),
        new StepTint(0xFF63BCFC, 0x1C63BCFC, 0xFF3A5D7A, 0xFF94D4FF),
        new StepTint(0xFF5AD0F5, 0x1C5AD0F5, 0xFF346475, 0xFF8EE2FC),
        new StepTint(0xFFCBA8FF, 0x22CBA8FF, 0xFF5F4A87, 0xFFDFC7FF),
        new StepTint(0xFFFA9D52, 0x20FA9D52, 0xFF7A4F2E, 0xFFFFBE85),
        new StepTint(0xFFF6BD4B, 0x20F6BD4B, 0xFF735B2A, 0xFFFFD57A),
        new StepTint(0xFF4BD6B4, 0x1E4BD6B4, 0xFF30695D, 0xFF7EEBD1)
    );

    public static final CirrusPalette atelierStone = new CirrusPalette(
        0x7A181C24,
        0xFFB2B7C0,
        0x1A3E4552,
        0x343E4552,
        0xFAC2C6CE,
        0xFFCFD3DA,
        0xFFD9DDE4,
        0xFFCFDCE6,
        0xFFA7ACB6,
        0xFAC9CDD5,
        0xFF7D8390,
        0xD8ECEFF4,
        0xFF2B6584,
        0x36000000,
        0xFF1A1E26,
        0xFF363D49,
        0xFF505866,
        0xFF747C8A,
        0xFFFFFFFF,
        0xFFF2F5FA,
        0xFF8C5300,
        0xFF145E6E,
        0xFFA6430D,
        0xFF196839,
        0xFF5C3E8A,
        0xFFA8232C,
        0xFF1F5468,
        0xFF196839,
        0xFF8C5300,
        0xFF8C5300,
        0xFFA8232C,
        0xE0363C47,
        0xEB8A6B00,
        0xE0A6430D,
        0xFF995B00,
        0xFF145E6E,
        0xFFA6430D,
        0xFF196839,
        0xFFA8232C,
        new StepTint(0xFF196839, 0x1E196839, 0xFF5D9273, 0xFF196839),
        new StepTint(0xFF1F5468, 0x1E1F5468, 0xFF638796, 0xFF1F5468),
        new StepTint(0xFF145E6E, 0x1E145E6E, 0xFF5B8E99, 0xFF145E6E),
        new StepTint(0xFF5C3E8A, 0x205C3E8A, 0xFF8773A8, 0xFF5C3E8A),
        new StepTint(0xFFA6430D, 0x20A6430D, 0xFFB87C5C, 0xFFA6430D),
        new StepTint(0xFF8C5300, 0x208C5300, 0xFFB38F52, 0xFF8C5300),
        new StepTint(0xFF19685A, 0x1E19685A, 0xFF5E948A, 0xFF19685A)
    );

    private static final Map<Theme, EnumMap<WorkbenchThemeMode, CirrusPalette>> cache = new WeakHashMap<>();

    public static CirrusPalette declared(WorkbenchThemeMode mode) {
        return switch (Objects.requireNonNull(mode, "mode")) {
            case obsidianBlueprint -> obsidianBlueprint;
            case atelierStone -> atelierStone;
        };
    }

    public static CirrusPalette of(WorkbenchThemeMode mode) {
        Objects.requireNonNull(mode, "mode");
        return of(CirrusThemeStyle.resolveTheme(mode), mode);
    }

    public static CirrusPalette of(@Nullable Theme theme, WorkbenchThemeMode fallbackMode) {
        Objects.requireNonNull(fallbackMode, "fallbackMode");
        CirrusPalette declared = declared(fallbackMode);
        if (theme == null) {
            return declared;
        }
        synchronized (cache) {
            EnumMap<WorkbenchThemeMode, CirrusPalette> byMode = cache
                    .computeIfAbsent(theme, ignored -> new EnumMap<>(WorkbenchThemeMode.class));
            CirrusPalette cached = byMode.get(fallbackMode);
            if (cached != null) {
                return cached;
            }
            CirrusPalette resolved = resolve(theme, declared);
            byMode.put(fallbackMode, resolved);
            return resolved;
        }
    }

    private static CirrusPalette resolve(Theme theme, CirrusPalette fallback) {
        return new CirrusPalette(
            CirrusThemeStyle.color(theme, CirrusPaletteVars.backdropScrim, fallback.backdropScrim),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.canvasVoid, fallback.canvasVoid),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.canvasGridMinor, fallback.canvasGridMinor),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.canvasGridMajor, fallback.canvasGridMajor),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.dockSurface, fallback.dockSurface),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.cardSurface, fallback.cardSurface),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.cardSurfaceHover, fallback.cardSurfaceHover),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.cardSurfaceSelected, fallback.cardSurfaceSelected),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.recessedSurface, fallback.recessedSurface),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.popoverSurface, fallback.popoverSurface),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.hairlineBorder, fallback.hairlineBorder),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.hairlineHighlight, fallback.hairlineHighlight),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.focusRing, fallback.focusRing),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.dropShadow, fallback.dropShadow),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.textPrimary, fallback.textPrimary),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.textSecondary, fallback.textSecondary),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.textMuted, fallback.textMuted),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.textDisabled, fallback.textDisabled),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.textOnAccent, fallback.textOnAccent),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.textOnDark, fallback.textOnDark),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.costH, fallback.costH),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.costP, fallback.costP),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.costK, fallback.costK),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.costS, fallback.costS),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.costC, fallback.costC),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.ruleHardBan, fallback.ruleHardBan),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.ruleHardPin, fallback.ruleHardPin),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.ruleSoftPrefer, fallback.ruleSoftPrefer),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.ruleSoftPenalize, fallback.ruleSoftPenalize),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.advisoryDiscouraged, fallback.advisoryDiscouraged),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.advisoryLastResort, fallback.advisoryLastResort),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.edgeMaterial, fallback.edgeMaterial),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.edgeCatalyst, fallback.edgeCatalyst),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.edgeMachine, fallback.edgeMachine),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.edgeCriticalPath, fallback.edgeCriticalPath),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.edgeUpstreamTrace, fallback.edgeUpstreamTrace),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.edgeDownstreamTrace, fallback.edgeDownstreamTrace),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.diffAdded, fallback.diffAdded),
            CirrusThemeStyle.color(theme, CirrusPaletteVars.diffRemoved, fallback.diffRemoved),
            pickTint(
                theme,
                CirrusPaletteVars.acquireAccent,
                CirrusPaletteVars.acquireTintBg,
                CirrusPaletteVars.acquireBorderIdle,
                CirrusPaletteVars.acquireBorderSelected,
                fallback.acquireStep
            ),
            pickTint(
                theme,
                CirrusPaletteVars.craftAccent,
                CirrusPaletteVars.craftTintBg,
                CirrusPaletteVars.craftBorderIdle,
                CirrusPaletteVars.craftBorderSelected,
                fallback.craftStep
            ),
            pickTint(
                theme,
                CirrusPaletteVars.convertAccent,
                CirrusPaletteVars.convertTintBg,
                CirrusPaletteVars.convertBorderIdle,
                CirrusPaletteVars.convertBorderSelected,
                fallback.convertStep
            ),
            pickTint(
                theme,
                CirrusPaletteVars.cycleLoopAccent,
                CirrusPaletteVars.cycleLoopTintBg,
                CirrusPaletteVars.cycleLoopBorderIdle,
                CirrusPaletteVars.cycleLoopBorderSelected,
                fallback.cycleLoopStep
            ),
            pickTint(
                theme,
                CirrusPaletteVars.provisionMachineAccent,
                CirrusPaletteVars.provisionMachineTintBg,
                CirrusPaletteVars.provisionMachineBorderIdle,
                CirrusPaletteVars.provisionMachineBorderSelected,
                fallback.provisionMachineStep
            ),
            pickTint(
                theme,
                CirrusPaletteVars.setupLineAccent,
                CirrusPaletteVars.setupLineTintBg,
                CirrusPaletteVars.setupLineBorderIdle,
                CirrusPaletteVars.setupLineBorderSelected,
                fallback.setupLineStep
            ),
            pickTint(
                theme,
                CirrusPaletteVars.linePullAccent,
                CirrusPaletteVars.linePullTintBg,
                CirrusPaletteVars.linePullBorderIdle,
                CirrusPaletteVars.linePullBorderSelected,
                fallback.linePullStep
            )
        );
    }

    private static StepTint pickTint(
        Theme theme,
        StyleVar<Integer> accent,
        StyleVar<Integer> tintBg,
        StyleVar<Integer> borderIdle,
        StyleVar<Integer> borderSelected,
        StepTint fallback
    ) {
        return new StepTint(
            CirrusThemeStyle.color(theme, accent, fallback.accent()),
            CirrusThemeStyle.color(theme, tintBg, fallback.tintBg()),
            CirrusThemeStyle.color(theme, borderIdle, fallback.borderIdle()),
            CirrusThemeStyle.color(theme, borderSelected, fallback.borderSelected())
        );
    }

    public StepTint stepTint(PlanStep step) {
        Objects.requireNonNull(step, "step");
        if (step instanceof AcquireStep) {
            return acquireStep;
        }
        if (step instanceof CraftStep) {
            return craftStep;
        }
        if (step instanceof ConvertStep) {
            return convertStep;
        }
        if (step instanceof CycleLoopStep) {
            return cycleLoopStep;
        }
        if (step instanceof ProvisionMachineStep) {
            return provisionMachineStep;
        }
        if (step instanceof SetupLineStep) {
            return setupLineStep;
        }
        if (step instanceof LinePullStep) {
            return linePullStep;
        }
        throw new IllegalArgumentException("Unsupported plan step: " + step.getClass().getName());
    }

    public int costColor(int axis) {
        return switch (axis) {
            case 0 -> costH;
            case 1 -> costP;
            case 2 -> costK;
            case 3 -> costS;
            case 4 -> costC;
            default -> throw new IllegalArgumentException("Cost axis must be in [0, 4], got " + axis);
        };
    }

    public int ruleStrengthColor(RuleStrength strength) {
        return switch (Objects.requireNonNull(strength, "strength")) {
            case hardBan -> ruleHardBan;
            case hardPin -> ruleHardPin;
            case softPrefer -> ruleSoftPrefer;
            case softPenalize -> ruleSoftPenalize;
        };
    }

    public int advisoryColor(RecipeAdvisory advisory) {
        return switch (Objects.requireNonNull(advisory, "advisory")) {
            case none -> hairlineBorder;
            case discouraged -> advisoryDiscouraged;
            case lastResort -> advisoryLastResort;
        };
    }
}
