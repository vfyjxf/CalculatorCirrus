package dev.vfyjxf.cirrus.ui.widget.omnicalc;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator.OmniCalculatorView;
import dev.vfyjxf.cirrus.ui.widget.omnicalc.context.OmniContextView;
import dev.vfyjxf.cirrus.ui.widget.omnicalc.library.OmniLibraryView;
import dev.vfyjxf.cirrus.ui.widget.omnicalc.rules.OmniRulesView;
import dev.vfyjxf.cloudlib.api.ui.InputContext;
import dev.vfyjxf.cloudlib.api.ui.base.BasicScreen;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.taffy.style.TaffyDimension;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.*;

/**
 * Full-screen OmniCalc Pro screen.
 * <p>
 * Replicates the browser-based omnicalc-pro application layout:
 * <pre>
 * ┌──────────────────────────────────────────────────┐
 * │  ┌────────┐  ┌───────────────────────────────┐   │
 * │  │Sidebar │  │       Main Content Area       │   │
 * │  │ (Nav)  │  │     (active view page)        │   │
 * │  └────────┘  └───────────────────────────────┘   │
 * └──────────────────────────────────────────────────┘
 * </pre>
 */
@EventBusSubscriber(modid = CirrusConstants.MOD_ID, value = Dist.CLIENT)
public class OmniCalcScreen extends BasicScreen {

    //region keybinding

    private static final KeyMapping OPEN_OMNICALC = new KeyMapping(
            "Open OmniCalc Pro",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            "cirrus"
    );

    @SubscribeEvent
    private static void onInputKey(InputEvent.Key event) {
        if (FMLEnvironment.production) return;
        InputContext inputContext = InputContext.fromEvent(event);
        if (inputContext.released(OPEN_OMNICALC)) {
            Minecraft.getInstance().setScreen(new OmniCalcScreen());
        }
    }

    //endregion

    private final OmniCalcSidebar sidebar;
    private final WidgetGroup<Widget> contentArea;
    private ViewState currentView = ViewState.CALCULATOR;

    public OmniCalcScreen() {
        // Root: horizontal flex, full screen, dark background
        mainGroup.useStyle(UIStyle.of(
                flexRow(),
                sizeOf(TaffyDimension.percent(1), TaffyDimension.percent(1)),
                background(OmniCalcColors.TEX_BG_PRIMARY)
        ));

        // ── Sidebar ──
        sidebar = new OmniCalcSidebar(this);
        mainGroup.addWidget(sidebar);

        // ── Main content area ──
        contentArea = new WidgetGroup<>();
        contentArea.useStyle(UIStyle.of(
                flexColumn(),
                flexGrow(1),
                minWidth(0),
                background(OmniCalcColors.TEX_BG_PRIMARY)
        ));
        mainGroup.addWidget(contentArea);

        // Show default view
        switchView(ViewState.CALCULATOR);
    }

    public void switchView(ViewState view) {
        if (currentView == view && !contentArea.children().isEmpty()) return;
        currentView = view;
        contentArea.clear();

        Widget viewWidget = switch (view) {
            case CALCULATOR -> new OmniCalculatorView();
            case LIBRARY -> new OmniLibraryView();
            case RULES -> new OmniRulesView();
            case CONTEXT -> new OmniContextView();
        };
        viewWidget.useStyle(flexGrow(1));
        contentArea.addWidget(viewWidget);
        sidebar.setActiveView(view);
    }

    public ViewState currentView() {
        return currentView;
    }

    public enum ViewState {
        CALCULATOR, LIBRARY, RULES, CONTEXT
    }
}
