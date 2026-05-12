package dev.vfyjxf.cirrus.ui.widget.calculator;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cirrus.ui.widget.TabContainer;
import dev.vfyjxf.cloudlib.api.ui.InputContext;
import dev.vfyjxf.cloudlib.api.ui.base.BasicScreen;
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

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.marginAuto;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

@EventBusSubscriber(modid = CirrusConstants.modId, value = Dist.CLIENT)
public class CalculatorScreen extends BasicScreen {

    private final TabContainer tabs;
    private boolean released;

	//region keybinds

	private static final KeyMapping openCalculatorScreen = new KeyMapping(
			"Open Calculator Screen",
			KeyConflictContext.GUI,
			KeyModifier.CONTROL,
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_C,
			"cirrus"
	);

	@SubscribeEvent
	private static void onInputKey(InputEvent.Key event) {
		if (FMLEnvironment.production) return;
		InputContext inputContext = InputContext.fromEvent(event);
		if (inputContext.released(openCalculatorScreen)) {
			Minecraft.getInstance().setScreen(new CalculatorScreen());
		}
	}

	//endregion

	public CalculatorScreen() {
		var container = new WidgetGroup<>();
		container.useStyle(UIStyle.of(
				marginAuto()
		));
        tabs = new TabContainer();
		container.addWidget(tabs);
		mainGroup.addWidget(container);
		mainGroup.useStyle(
				sizeOf(TaffyDimension.percent(1), TaffyDimension.percent(1))
		);
	}

    @Override
    public void onClose() {
        releaseScreenState();
        super.onClose();
    }

    @Override
    public void removed() {
        releaseScreenState();
        super.removed();
    }

    private void releaseScreenState() {
        if (released) {
            return;
        }
        released = true;
        tabs.releaseCachedContent();
    }


}
