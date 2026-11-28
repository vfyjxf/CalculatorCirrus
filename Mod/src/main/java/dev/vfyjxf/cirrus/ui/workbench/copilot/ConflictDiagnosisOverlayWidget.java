package dev.vfyjxf.cirrus.ui.workbench.copilot;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.human.ConflictDiagnosis;
import dev.vfyjxf.cirrus.api.planning.human.CostOverrideRule;
import dev.vfyjxf.cirrus.api.planning.human.CycleBreakRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.ModNamespaceRule;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.SystemRule;
import dev.vfyjxf.cirrus.api.planning.human.TagDefaultRule;
import dev.vfyjxf.cirrus.api.planning.model.PlanningTagKeys;
import dev.vfyjxf.cirrus.api.planning.service.PlanningDiagnostics;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Top conflict repair card: lists the minimal conflicting rule set and can relax one rule, relax all, or undo the last step.
 */
public class ConflictDiagnosisOverlayWidget extends Widget {

    public static final int rowHeight = 14;
    public static final int buttonHeight = 18;
    public static final int pad = CoPilotPaint.pad;
    public static final int maxRuleRows = 5;
    public static final int tooManyRulesThreshold = 16;

    private final CoPilotHost host;
    private @Nullable Runnable openFacilities;

    public ConflictDiagnosisOverlayWidget(CoPilotHost host) {
        this.host = Objects.requireNonNull(host, "host");
        onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            boolean handled = handleClick((int) local.x(), (int) local.y());
            if (handled) {
                CirrusUiSound.playClickSound();
            }
            return handled ? EventDispatch.consumed : EventDispatch.pass;
        });
    }

    public ConflictDiagnosisOverlayWidget openFacilities(@Nullable Runnable openFacilities) {
        this.openFacilities = openFacilities;
        return this;
    }

    public static int preferredHeight(int ruleRows) {
        return pad + 22 + Math.min(maxRuleRows, Math.max(0, ruleRows)) * rowHeight + buttonHeight + pad;
    }

    @Override
    public boolean visible() {
        return diagnosis() != null;
    }

    public @Nullable ConflictDiagnosis diagnosis() {
        WorkbenchUiSnapshot snapshot = host.snapshot();
        if (snapshot == null
                || snapshot.result() == null
                || snapshot.result().status() != PlanningStatus.infeasibleByHumanRules) {
            return null;
        }
        PlanningDiagnostics diagnostics = snapshot.result().diagnostics();
        return diagnostics == null ? null : diagnostics.conflictDiagnosis().orElse(null);
    }

    public @Nullable ComponentStackKey blockedResource() {
        ConflictDiagnosis diagnosis = diagnosis();
        return diagnosis == null ? null : diagnosis.blockedResource();
    }

    public List<String> minimalConflictRuleIds() {
        ConflictDiagnosis diagnosis = diagnosis();
        return diagnosis == null ? List.of() : diagnosis.minimalConflictRuleIds();
    }

    public boolean tooManyRules() {
        WorkbenchUiSnapshot snapshot = host.snapshot();
        return snapshot != null && snapshot.activeSessionRules().size() > tooManyRulesThreshold;
    }

    public List<GuidanceRule> sessionRules() {
        WorkbenchUiSnapshot snapshot = host.snapshot();
        return snapshot == null ? List.of() : snapshot.activeSessionRules();
    }

    public List<GuidanceRule> conflictingRules() {
        List<GuidanceRule> resolved = new ArrayList<>();
        List<String> ids = minimalConflictRuleIds();
        for (String ruleId : ids) {
            GuidanceRule rule = ruleById(ruleId);
            if (rule != null) {
                resolved.add(rule);
            }
        }
        return List.copyOf(resolved);
    }

    public @Nullable GuidanceRule ruleById(@Nullable String ruleId) {
        if (ruleId == null) {
            return null;
        }
        for (GuidanceRule rule : sessionRules()) {
            if (rule.ruleId().equals(ruleId)) {
                return rule;
            }
        }
        return null;
    }

    public Component resolutionText() {
        ConflictDiagnosis diagnosis = diagnosis();
        if (diagnosis == null) {
            return Component.empty();
        }
        String key = diagnosis.suggestedResolutionKey();
        if (key == null || key.isEmpty()) {
            return CirrusLangs.Planning.Conflict.relaxRule.get();
        }
        return Component.translatable(key);
    }

    public Component ruleSummary(GuidanceRule rule) {
        return PromptDeltaFormat.scopeTag(rule.scope()).copy().append(Component.literal(" " + rule.strength().name()))
                .append(Component.literal(" · ")).append(typeLabel(rule).get().copy())
                .append(Component.literal(" " + subjectOf(rule)));
    }

    public static LangEntry typeLabel(GuidanceRule rule) {
        return switch (rule) {
            case RecipeRule ignored -> CirrusLangs.Workbench.Action.banRecipe;
            case SystemRule ignored -> CirrusLangs.Workbench.Action.banSystem;
            case MachineRule ignored -> CirrusLangs.Workbench.Action.declareOwned;
            case ResourceRule ignored -> CirrusLangs.Workbench.Action.banResource;
            case TagDefaultRule ignored -> CirrusLangs.Workbench.Copilot.allTagCandidates;
            case CycleBreakRule ignored -> CirrusLangs.Workbench.Action.breakCycle;
            case CostOverrideRule ignored -> CirrusLangs.Workbench.Action.costOverride;
            case ModNamespaceRule ignored -> CirrusLangs.Workbench.Action.modBan;
        };
    }

    public static String subjectOf(GuidanceRule rule) {
        return switch (rule) {
            case RecipeRule r -> r.recipeKey().shortString();
            case SystemRule r -> r.systemId().toString();
            case MachineRule r -> r.machine().contentKey()
                    .map(ComponentStackKey::displayId)
                    .orElse(r.machine().canonicalKey());
            case ResourceRule r -> r.resource().displayId();
            case TagDefaultRule r -> PlanningTagKeys.format(r.tag());
            case CycleBreakRule r -> r.targetResource().displayId();
            case CostOverrideRule r -> r.resource().displayId();
            case ModNamespaceRule r -> r.modNamespace();
        };
    }

    public Rect relaxRect(int index) {
        int width = Math.max(1, width());
        int y = pad + 22 + index * rowHeight;
        return new Rect(width - 62, y, 58, rowHeight - 2);
    }

    public Rect relaxAllRect() {
        int y = Math.max(0, height() - pad - buttonHeight);
        int width = Math.max(1, width());
        return new Rect(Math.max(4, width - 132), y, 128, buttonHeight);
    }

    public Rect undoRect() {
        int y = Math.max(0, height() - pad - buttonHeight);
        int width = Math.max(1, width());
        return new Rect(Math.max(4, width - 132 - 84 - 4), y, 82, buttonHeight);
    }

    public Rect clearSessionRect() {
        int width = Math.max(1, width());
        return new Rect(pad, Math.max(0, height() - pad - buttonHeight), Math.max(80, width / 2 - 8), buttonHeight);
    }

    public Rect openFacilitiesRect() {
        int width = Math.max(1, width());
        return new Rect(
            width / 2 + 4,
            Math.max(0, height() - pad - buttonHeight),
            Math.max(80, width / 2 - 8),
            buttonHeight
        );
    }

    public boolean relaxOne(int index) {
        List<String> ids = minimalConflictRuleIds();
        if (index < 0 || index >= ids.size()) {
            return false;
        }
        host.relaxConflictRules(List.of(ids.get(index)));
        return true;
    }

    public boolean relaxAll() {
        List<String> ids = minimalConflictRuleIds();
        if (ids.isEmpty()) {
            return false;
        }
        host.relaxConflictRules(List.copyOf(ids));
        return true;
    }

    public boolean undoLastStep() {
        WorkbenchUiSnapshot snapshot = host.snapshot();
        if (snapshot == null || snapshot.undoHistory().isEmpty()) {
            return false;
        }
        host.jumpToHistory(Math.max(0, snapshot.undoHistory().size() - 1));
        return true;
    }

    public boolean clearSessionRules() {
        List<GuidanceRule> rules = sessionRules();
        if (rules.isEmpty()) {
            return false;
        }
        List<String> ids = new ArrayList<>(rules.size());
        for (GuidanceRule rule : rules) {
            ids.add(rule.ruleId());
        }
        host.relaxConflictRules(List.copyOf(ids));
        return true;
    }

    public boolean openFacilitiesWorkspace() {
        if (openFacilities != null) {
            openFacilities.run();
            return true;
        }
        host.toast(CirrusLangs.Workbench.Copilot.dropBanHint);
        return false;
    }

    private boolean handleClick(int localX, int localY) {
        if (!visible()) {
            return false;
        }
        if (tooManyRules()) {
            if (clearSessionRect().contains(localX, localY)) {
                return clearSessionRules();
            }
            if (openFacilitiesRect().contains(localX, localY)) {
                openFacilitiesWorkspace();
                return true;
            }
            return false;
        }
        int ruleCount = Math.min(maxRuleRows, minimalConflictRuleIds().size());
        for (int index = 0; index < ruleCount; index++) {
            if (relaxRect(index).contains(localX, localY)) {
                return relaxOne(index);
            }
        }
        if (relaxAllRect().contains(localX, localY)) {
            return relaxAll();
        }
        if (undoRect().contains(localX, localY)) {
            return undoLastStep();
        }
        return false;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        if (!visible()) {
            return;
        }
        CirrusPalette palette = host.palette();
        int width = width();
        int height = height();
        if (width <= 0 || height <= 0) {
            return;
        }
        OreUiPaint.popover(canvas, 0, 0, width, height, palette);
        canvas.strokeRect(0, 0, width, height, palette.ruleHardBan(), 1);
        renderHeader(canvas, palette, width);
        if (tooManyRules()) {
            renderTooMany(canvas, palette, width, height);
            return;
        }
        renderRules(canvas, palette, width, height, mouseX, mouseY);
        renderMainButtons(canvas, palette, width, height, mouseX, mouseY);
    }

    private void renderHeader(SceneCanvas canvas, CirrusPalette palette, int width) {
        ComponentStackKey blocked = blockedResource();
        OreUiPaint.slot(canvas, pad, pad, CoPilotPaint.iconMedium, palette);
        CoPilotPaint.icon(
            canvas,
            CoPilotPaint.stackOf(blocked, 1L),
            pad + 1,
            pad + 1,
            CoPilotPaint.iconMedium - 2,
            palette
        );
        int textLeft = pad + CoPilotPaint.iconMedium + 6;
        CoPilotPaint.text(
            canvas,
            CoPilotPaint.fit(canvas, CoPilotPaint.resourceName(blocked), width - textLeft - pad),
            textLeft,
            pad,
            palette.textPrimary()
        );
        CoPilotPaint.text(
            canvas,
            CoPilotPaint.fit(canvas, resolutionText(), width - textLeft - pad),
            textLeft,
            pad + 11,
            palette.ruleHardBan()
        );
    }

    private void renderRules(SceneCanvas canvas, CirrusPalette palette, int width, int height, int mouseX, int mouseY) {
        List<String> ids = minimalConflictRuleIds();
        int rows = Math.min(maxRuleRows, ids.size());
        for (int index = 0; index < rows; index++) {
            int y = pad + 24 + index * rowHeight;
            GuidanceRule rule = ruleById(ids.get(index));
            Component summary = rule == null ? Component.literal(ids.get(index)) : ruleSummary(rule);
            int ink = rule == null ? palette.textMuted() : palette.ruleStrengthColor(rule.strength());
            Rect relax = relaxRect(index);
            OreUiPaint.raisedCard(
                canvas,
                pad,
                y - 1,
                Math.max(12, relax.x() - pad - 4),
                rowHeight - 2,
                palette,
                false,
                false
            );
            CoPilotPaint.text(
                canvas,
                CoPilotPaint.fit(canvas, summary, Math.max(12, relax.x() - pad - 12)),
                pad + 5,
                ChromeText.centeredY(y - 1, rowHeight - 2, canvas),
                ink
            );
            boolean hover = relax.contains(mouseX, mouseY);
            OreUiPaint.raisedButton(
                canvas,
                relax.x(),
                relax.y(),
                relax.width(),
                relax.height(),
                palette,
                false,
                hover,
                false,
                false
            );
            CoPilotPaint.text(
                canvas,
                CoPilotPaint.fit(canvas, CirrusLangs.Workbench.Action.relaxOne.get(), relax.width() - 6),
                relax.x() + 3,
                ChromeText.centeredY(relax.y(), relax.height() - 1, canvas),
                palette.focusRing()
            );
        }
    }

    private void renderMainButtons(
        SceneCanvas canvas,
        CirrusPalette palette,
        int width,
        int height,
        int mouseX,
        int mouseY
    ) {
        Rect undo = undoRect();
        renderButton(
            canvas,
            palette,
            undo,
            CirrusLangs.Workbench.Topbar.undo.get(),
            false,
            undo.contains(mouseX, mouseY)
        );
        Rect all = relaxAllRect();
        renderButton(
            canvas,
            palette,
            all,
            CirrusLangs.Workbench.Action.relaxAll.get(),
            true,
            all.contains(mouseX, mouseY)
        );
    }

    private void renderTooMany(SceneCanvas canvas, CirrusPalette palette, int width, int height) {
        CoPilotPaint.text(
            canvas,
            CoPilotPaint.fit(canvas, CirrusLangs.Planning.Conflict.tooManyRulesToLocalize.get(), width - pad * 2 - 4),
            pad + 4,
            pad + 24,
            palette.ruleHardBan()
        );
        Rect clear = clearSessionRect();
        renderButton(canvas, palette, clear, CirrusLangs.Workbench.Timeline.removeRule.get(), false, false);
        Rect open = openFacilitiesRect();
        renderButton(canvas, palette, open, CirrusLangs.Workbench.Mode.facilities.get(), true, false);
    }

    private void renderButton(
        SceneCanvas canvas,
        CirrusPalette palette,
        Rect rect,
        Component label,
        boolean primary,
        boolean hover
    ) {
        OreUiPaint.raisedButton(
            canvas,
            rect.x(),
            rect.y(),
            rect.width(),
            rect.height(),
            palette,
            false,
            hover,
            primary,
            false
        );
        int ink = primary ? palette.textOnAccent() : palette.textPrimary();
        int textWidth = canvas.font().width(label);
        canvas.drawString(
            label,
            rect.x() + Math.max(3, (rect.width() - textWidth) / 2),
            ChromeText.centeredY(rect.y(), rect.height() - 1, canvas),
            ink,
            false
        );
    }
}
