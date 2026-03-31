package dev.vfyjxf.cirrus.ui.execution;

import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.DriverLookup;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverApi;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverHandle;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.MenuContext;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.MenuTypeContext;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.ScreenContext;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.SideContext;
import dev.vfyjxf.cirrus.ui.execution.layout.ExecutionOverlayLayout;
import dev.vfyjxf.cirrus.ui.execution.overlay.ExecutionOverlayRoot;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.ui.overlay.OverlayEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

public class ExecutionOverlay {
	//region entry

	public static final OverlayEntry<ExecutionOverlayRoot> entry = OverlayEntry.screen(
			CirrusNamespaces.ofMod("execution"),
			context -> {
				if (!(context.screen() instanceof AbstractContainerScreen<?>)) {
					return null;
				}
				if (context.minecraft().level == null) {
					return null;
				}
				var api = ExecutionDriverApi.instance();
				ExecutionDriverHandle handle = api.resolveClient(fromScreen(context.screen()));
				if (handle == null) {
					return null;
				}
				Rect2i bounds = ExecutionOverlayLayout.panelBounds(context);
				return new ExecutionOverlayRoot(handle, bounds, context.width(), context.height());
			},
			(widget, context) -> widget.exclusionBounds()
	);
	//endregion

	//region lookup
	private static DriverLookup fromScreen(@Nullable Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
			return DriverLookup.of(
					new SideContext(ExecutionSideRequirement.client),
					screen == null ? new ScreenContext(Screen.class) : new ScreenContext(screen.getClass())
			);
		}
		AbstractContainerMenu menu = containerScreen.getMenu();
		MenuTypeContext menuTypeContext;
		try {
			menuTypeContext = new MenuTypeContext(menu.getType());
		} catch (UnsupportedOperationException ignored) {
			menuTypeContext = null;
		}
		if (menuTypeContext == null) {
			return DriverLookup.of(
					new SideContext(ExecutionSideRequirement.client),
					new ScreenContext(screen.getClass()),
					new MenuContext(menu.getClass())
			);
		}
		return DriverLookup.of(
				new SideContext(ExecutionSideRequirement.client),
				new ScreenContext(screen.getClass()),
				new MenuContext(menu.getClass()),
				menuTypeContext
		);
	}
	//endregion
}
