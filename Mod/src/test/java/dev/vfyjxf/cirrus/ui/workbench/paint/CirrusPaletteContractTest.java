package dev.vfyjxf.cirrus.ui.workbench.paint;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeAdvisory;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CirrusPaletteContractTest {

    @Test
    void obsidianBlueprintMatchesTheSpecTable() {
        CirrusPalette palette = CirrusPalette.obsidianBlueprint;
        assertEquals(0xCC0D1016, palette.backdropScrim());
        assertEquals(0xFF1E232D, palette.canvasVoid());
        assertEquals(0x14E6EDF7, palette.canvasGridMinor());
        assertEquals(0x2AE6EDF7, palette.canvasGridMajor());
        assertEquals(0xF7292F3B, palette.dockSurface());
        assertEquals(0xFF343B4A, palette.cardSurface());
        assertEquals(0xFF3F485A, palette.cardSurfaceHover());
        assertEquals(0xFF3B4659, palette.cardSurfaceSelected());
        assertEquals(0xFF222834, palette.recessedSurface());
        assertEquals(0xFA2D3442, palette.popoverSurface());
        assertEquals(0xFF434C5E, palette.hairlineBorder());
        assertEquals(0x24FFFFFF, palette.hairlineHighlight());
        assertEquals(0xFF63BCFC, palette.focusRing());
        assertEquals(0x7A000000, palette.dropShadow());
        assertEquals(0xFFF2F5FA, palette.textPrimary());
        assertEquals(0xFFC9D3E0, palette.textSecondary());
        assertEquals(0xFF9BA8BC, palette.textMuted());
        assertEquals(0xFF687386, palette.textDisabled());
        assertEquals(0xFFFFFFFF, palette.textOnAccent());
        assertEquals(0xFFF2F5FA, palette.textOnDark());
        assertEquals(0xFFF6BD4B, palette.costH());
        assertEquals(0xFF5AD0F5, palette.costP());
        assertEquals(0xFFFA9D52, palette.costK());
        assertEquals(0xFF56DB8B, palette.costS());
        assertEquals(0xFFCBA8FF, palette.costC());
        assertEquals(0xFFFF858E, palette.ruleHardBan());
        assertEquals(0xFF63BCFC, palette.ruleHardPin());
        assertEquals(0xFF56DB8B, palette.ruleSoftPrefer());
        assertEquals(0xFFF6BD4B, palette.ruleSoftPenalize());
        assertEquals(0xFFF6BD4B, palette.advisoryDiscouraged());
        assertEquals(0xFFFF858E, palette.advisoryLastResort());
        assertEquals(0xD9AABBD0, palette.edgeMaterial());
        assertEquals(0xE6F6D854, palette.edgeCatalyst());
        assertEquals(0xD9FA9D52, palette.edgeMachine());
        assertEquals(0xFFF6BD4B, palette.edgeCriticalPath());
        assertEquals(0xFF63BCFC, palette.edgeUpstreamTrace());
        assertEquals(0xFFFA9D52, palette.edgeDownstreamTrace());
        assertEquals(0xFF56DB8B, palette.diffAdded());
        assertEquals(0xFFFF858E, palette.diffRemoved());
    }

    @Test
    void atelierStoneMatchesTheSpecTable() {
        CirrusPalette palette = CirrusPalette.atelierStone;
        assertEquals(0x7A1C2028, palette.backdropScrim());
        assertEquals(0xFFDFE2E7, palette.canvasVoid());
        assertEquals(0x164A5260, palette.canvasGridMinor());
        assertEquals(0x304A5260, palette.canvasGridMajor());
        assertEquals(0xFAE8EAEE, palette.dockSurface());
        assertEquals(0xFFF5F6F8, palette.cardSurface());
        assertEquals(0xFFFFFFFF, palette.cardSurfaceHover());
        assertEquals(0xFFFFFFFF, palette.cardSurfaceSelected());
        assertEquals(0xFFFAFAFC, palette.recessedSurface());
        assertEquals(0xFAF0F1F4, palette.popoverSurface());
        assertEquals(0xFFA4AAB5, palette.hairlineBorder());
        assertEquals(0xF2FFFFFF, palette.hairlineHighlight());
        assertEquals(0xFF3A6B7E, palette.focusRing());
        assertEquals(0x2B000000, palette.dropShadow());
        assertEquals(0xFF22262E, palette.textPrimary());
        assertEquals(0xFF454C57, palette.textSecondary());
        assertEquals(0xFF666E7C, palette.textMuted());
        assertEquals(0xFF949CA8, palette.textDisabled());
        assertEquals(0xFFFFFFFF, palette.textOnAccent());
        assertEquals(0xFFF2F5FA, palette.textOnDark());
    }

    @Test
    void obsidianStepTintsMatchTheSpecTable() {
        CirrusPalette palette = CirrusPalette.obsidianBlueprint;
        assertEquals(new CirrusPalette.StepTint(0xFF56DB8B, 0x1F56DB8B, 0xFF386B52, 0xFF7CEBA8), palette.acquireStep());
        assertEquals(new CirrusPalette.StepTint(0xFF63BCFC, 0x1C63BCFC, 0xFF3A5D7A, 0xFF94D4FF), palette.craftStep());
        assertEquals(new CirrusPalette.StepTint(0xFF5AD0F5, 0x1C5AD0F5, 0xFF346475, 0xFF8EE2FC), palette.convertStep());
        assertEquals(
            new CirrusPalette.StepTint(0xFFCBA8FF, 0x22CBA8FF, 0xFF5F4A87, 0xFFDFC7FF),
            palette.cycleLoopStep()
        );
        assertEquals(
            new CirrusPalette.StepTint(0xFFFA9D52, 0x20FA9D52, 0xFF7A4F2E, 0xFFFFBE85),
            palette.provisionMachineStep()
        );
        assertEquals(
            new CirrusPalette.StepTint(0xFFF6BD4B, 0x20F6BD4B, 0xFF735B2A, 0xFFFFD57A),
            palette.setupLineStep()
        );
        assertEquals(
            new CirrusPalette.StepTint(0xFF4BD6B4, 0x1E4BD6B4, 0xFF30695D, 0xFF7EEBD1),
            palette.linePullStep()
        );
    }

    @Test
    void bothThemesKeepSevenDistinguishableStepTints() {
        for (WorkbenchThemeMode mode : WorkbenchThemeMode.values()) {
            CirrusPalette palette = CirrusPalette.of(mode);
            List<CirrusPalette.StepTint> tints = List.of(
                palette.acquireStep(),
                palette.craftStep(),
                palette.convertStep(),
                palette.cycleLoopStep(),
                palette.provisionMachineStep(),
                palette.setupLineStep(),
                palette.linePullStep()
            );
            Set<CirrusPalette.StepTint> distinctTints = tints.stream().collect(Collectors.toUnmodifiableSet());
            Set<Integer> distinctAccents = tints.stream().map(CirrusPalette.StepTint::accent)
                    .collect(Collectors.toUnmodifiableSet());
            assertEquals(7, distinctTints.size(), mode + " step tints must be pairwise distinguishable");
            assertEquals(7, distinctAccents.size(), mode + " step accents must be pairwise distinguishable");
        }
        assertEquals(0xFF1D7340, CirrusPalette.atelierStone.acquireStep().accent());
        assertEquals(0xFF235A6B, CirrusPalette.atelierStone.craftStep().accent());
        assertEquals(0xFF166778, CirrusPalette.atelierStone.convertStep().accent());
        assertEquals(0xFF644791, CirrusPalette.atelierStone.cycleLoopStep().accent());
        assertEquals(0xFFB04910, CirrusPalette.atelierStone.provisionMachineStep().accent());
        assertEquals(0xFF945B00, CirrusPalette.atelierStone.setupLineStep().accent());
        assertEquals(0xFF1D7364, CirrusPalette.atelierStone.linePullStep().accent());
    }

    @Test
    // Negative test: stepTint is contractually non-null, so the only way to assert the NPE is to pass null.
    @SuppressWarnings("NullAway")
    void stepTintDispatchesTheAcquireStep() {
        AcquireStep step = new AcquireStep(
            "acquire-1",
            List.of(),
            ResourceKey.item("minecraft:stone"),
            4L,
            AcquisitionKind.manualGather,
            CostVector.zero
        );
        assertEquals(CirrusPalette.obsidianBlueprint.acquireStep(), CirrusPalette.obsidianBlueprint.stepTint(step));
        assertEquals(CirrusPalette.atelierStone.acquireStep(), CirrusPalette.atelierStone.stepTint(step));
        assertThrows(NullPointerException.class, () -> CirrusPalette.obsidianBlueprint.stepTint(null));
    }

    @Test
    void costAxisColorsAreIndexedZeroToFour() {
        CirrusPalette palette = CirrusPalette.obsidianBlueprint;
        assertEquals(palette.costH(), palette.costColor(0));
        assertEquals(palette.costP(), palette.costColor(1));
        assertEquals(palette.costK(), palette.costColor(2));
        assertEquals(palette.costS(), palette.costColor(3));
        assertEquals(palette.costC(), palette.costColor(4));
        assertThrows(IllegalArgumentException.class, () -> palette.costColor(-1));
        assertThrows(IllegalArgumentException.class, () -> palette.costColor(5));
    }

    @Test
    void ruleStrengthAndAdvisoryColorsFollowTheSemanticTable() {
        CirrusPalette palette = CirrusPalette.obsidianBlueprint;
        assertEquals(palette.ruleHardBan(), palette.ruleStrengthColor(RuleStrength.hardBan));
        assertEquals(palette.ruleHardPin(), palette.ruleStrengthColor(RuleStrength.hardPin));
        assertEquals(palette.ruleSoftPrefer(), palette.ruleStrengthColor(RuleStrength.softPrefer));
        assertEquals(palette.ruleSoftPenalize(), palette.ruleStrengthColor(RuleStrength.softPenalize));

        assertEquals(palette.hairlineBorder(), palette.advisoryColor(RecipeAdvisory.none));
        assertEquals(palette.advisoryDiscouraged(), palette.advisoryColor(RecipeAdvisory.discouraged));
        assertEquals(palette.advisoryLastResort(), palette.advisoryColor(RecipeAdvisory.lastResort));
    }

    @Test
    // Negative test: palette lookup asserts its NPE contract by passing null on purpose.
    @SuppressWarnings("NullAway")
    void paletteLookupRejectsUnknownModes() {
        assertThrows(NullPointerException.class, () -> CirrusPalette.of(null));
    }
}
