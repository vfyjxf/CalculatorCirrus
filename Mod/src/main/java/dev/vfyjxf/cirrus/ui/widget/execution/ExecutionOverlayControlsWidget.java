package dev.vfyjxf.cirrus.ui.widget.execution;

import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.ui.widget.ButtonWidget;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import dev.vfyjxf.cloudlib.ui.widget.RowWidget;
import dev.vfyjxf.cloudlib.ui.widget.SliderWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

import java.util.Locale;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.columnGap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.textAlignCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.widthOf;

/**
 * Reusable control strip for execution overlays.
 */
public final class ExecutionOverlayControlsWidget extends WidgetGroup<Widget> {

    record WorkbenchControlDescriptor(String id, Component label) {
    }

    //region Palette
    private static final int actionTextColor = 0xFFDDDDDD;
    private static final int actionActiveTextColor = 0xFFFBF3C2;
    private static final double minRate = 1.0d;
    private static final double maxRate = 30.0d;
    //endregion

    //region State
    private final ExecutionOverlayController controller;
    private final Runnable onAction;
    private final boolean includeSpeedControls;
    private final int controlsHeight;

    private final ButtonWidget stepButton;
    private final ButtonWidget burstButton;
    private final ButtonWidget autoButton;
    private final ButtonWidget resetButton;
    private @Nullable ButtonWidget speedDownButton;
    private @Nullable ButtonWidget speedUpButton;
    private @Nullable LabelWidget speedValueLabel;
    private @Nullable SliderWidget speedSlider;
    private boolean syncingSpeedControls;
    //endregion

    public ExecutionOverlayControlsWidget(ExecutionOverlayController controller, Runnable onAction, int controlsHeight) {
        this(controller, onAction, controlsHeight, false);
    }

    public ExecutionOverlayControlsWidget(ExecutionOverlayController controller, Runnable onAction, int controlsHeight, boolean includeSpeedControls) {
        this.controller = controller;
        this.onAction = onAction;
        this.includeSpeedControls = includeSpeedControls;
        this.controlsHeight = controlsHeight;

        useStyle(
            minHeight(0)
        );

        var controls = addWidget(RowWidget.create(4));
        controls.useStyle(
            heightOf(controlsHeight),
            columnGap(4),
            minHeight(0)
        );

        var transportRow = controls.addWidget(RowWidget.create(4));
        transportRow.useStyle(
            heightOf(controlsHeight),
            columnGap(4)
        );

        autoButton = addControlButton(transportRow, Component.literal("||"), () -> {
            controller.overlayToggleAutoAdvance();
            onAction.run();
        }, controlsHeight);
        stepButton = addControlButton(transportRow, Component.literal(">|"), () -> {
            controller.overlayAdvanceSteps(1);
            onAction.run();
        }, controlsHeight);
        burstButton = addControlButton(transportRow, Component.literal(">>"), () -> {
            controller.overlayAdvanceSteps(3);
            onAction.run();
        }, controlsHeight);
        resetButton = addControlButton(transportRow, Component.literal("[]"), () -> {
            controller.overlayReset();
            onAction.run();
        }, controlsHeight);

        if (includeSpeedControls) {
            int speedRowHeight = Math.max(18, controlsHeight);
            var speedRow = controls.addWidget(RowWidget.create(4));
            speedRow.useStyle(
                flexGrow(1),
                heightOf(speedRowHeight),
                columnGap(4)
            );

            speedDownButton = speedRow.addWidget(ButtonWidget.of(Component.literal("-"), () -> {
                controller.overlaySetRatePerSecond(controller.overlayRatePerSecond() - 0.5d);
                refreshSpeedControls();
                onAction.run();
            }).setTextures(CirrusTextures.Toggle.Button.up, CirrusTextures.Toggle.Button.hover, CirrusTextures.Toggle.Button.down));
            speedDownButton.setTextColor(actionTextColor);
            speedDownButton.useStyle(widthOf(16), heightOf(speedRowHeight));

            speedSlider = speedRow.addWidget(
                SliderWidget.create(0.0d, 1.0d, rateToSlider(controller.overlayRatePerSecond()))
                    .setStep(0.01d)
                    .setOrientation(SliderWidget.Orientation.HORIZONTAL)
                    .setColors(0xFF2E343C, 0xFF5A97E8, 0xFFF2F8FF)
                    .setThumbSize(11)
                    .onValueChanged(value -> {
                        if (syncingSpeedControls) {
                            return;
                        }
                        controller.overlaySetRatePerSecond(sliderToRate(value));
                        refreshSpeedControls();
                        onAction.run();
                    })
            );
            speedSlider.useStyle(
                flexGrow(1),
                minHeight(speedRowHeight),
                heightOf(speedRowHeight)
            );

            speedUpButton = speedRow.addWidget(ButtonWidget.of(Component.literal("+"), () -> {
                controller.overlaySetRatePerSecond(controller.overlayRatePerSecond() + 0.5d);
                refreshSpeedControls();
                onAction.run();
            }).setTextures(CirrusTextures.Toggle.Button.up, CirrusTextures.Toggle.Button.hover, CirrusTextures.Toggle.Button.down));
            speedUpButton.setTextColor(actionTextColor);
            speedUpButton.useStyle(widthOf(16), heightOf(speedRowHeight));

            speedValueLabel = speedRow.addWidget(LabelWidget.of(rateText(controller.overlayRatePerSecond())));
            speedValueLabel.setColor(actionTextColor);
            speedValueLabel.setShadow(false);
            speedValueLabel.setAlign(LabelWidget.TextAlign.CENTER);
            speedValueLabel.useStyle(widthOf(40), heightOf(speedRowHeight), textAlignCenter());
        }

        refreshState();
    }

    //region Public API
    public int preferredHeight() {
        return controlsHeight;
    }

    public void refreshState() {
        var session = controller.executionOverlaySession();
        boolean completed = session.completed();

        stepButton.setEnabled(!completed);
        burstButton.setEnabled(!completed);
        resetButton.setEnabled(session.nextStepIndex() > 0 || session.paused() || completed);
        autoButton.setEnabled(!completed);
        autoButton.setLabel(Component.literal(controller.overlayAutoAdvanceEnabled() ? "||" : ">"));
        autoButton.setTextColor(controller.overlayAutoAdvanceEnabled() ? actionActiveTextColor : actionTextColor);
        if (speedDownButton != null) {
            speedDownButton.setEnabled(!completed);
        }
        if (speedUpButton != null) {
            speedUpButton.setEnabled(!completed);
        }

        refreshSpeedControls();
    }
    //endregion

    //region Helpers
    static ImmutableList<WorkbenchControlDescriptor> workbenchTransportDescriptors() {
        return Lists.immutable.of(
            new WorkbenchControlDescriptor("playback", Component.literal("||")),
            new WorkbenchControlDescriptor("step", Component.literal(">|")),
            new WorkbenchControlDescriptor("burst", Component.literal(">>")),
            new WorkbenchControlDescriptor("reset", Component.literal("[]"))
        );
    }

    private void refreshSpeedControls() {
        if (!includeSpeedControls || speedSlider == null || speedValueLabel == null) {
            return;
        }
        syncingSpeedControls = true;
        try {
            double rate = controller.overlayRatePerSecond();
            speedValueLabel.setText(rateText(rate));
            speedSlider.setValue(rateToSlider(rate));
        } finally {
            syncingSpeedControls = false;
        }
    }

    private static ButtonWidget addControlButton(RowWidget controls, Component label, Runnable action, int controlsHeight) {
        var button = controls.addWidget(
            ButtonWidget.of(label, action)
                .setTextures(CirrusTextures.Toggle.Button.up, CirrusTextures.Toggle.Button.hover, CirrusTextures.Toggle.Button.down)
        );
        button.setTextColor(actionTextColor);
        button.useStyle(
            widthOf(Math.max(24, controlsHeight)),
            heightOf(Math.max(12, controlsHeight))
        );
        return button;
    }

    private static Component rateText(double rate) {
        return Component.literal(String.format(Locale.ROOT, "x%.1f", rate));
    }

    static double minRate() {
        return minRate;
    }

    static double maxRate() {
        return maxRate;
    }

    public static double clampRate(double rate) {
        return Math.max(minRate, Math.min(maxRate, rate));
    }

    static double debugMinRate() {
        return minRate;
    }

    static double debugMaxRate() {
        return maxRate;
    }

    static double sliderToRate(double sliderValue) {
        double clamped = Math.max(0.0d, Math.min(1.0d, sliderValue));
        return minRate * Math.pow(maxRate / minRate, clamped);
    }

    static double rateToSlider(double rate) {
        double clamped = clampRate(rate);
        return Math.log(clamped / minRate) / Math.log(maxRate / minRate);
    }
    //endregion
}
