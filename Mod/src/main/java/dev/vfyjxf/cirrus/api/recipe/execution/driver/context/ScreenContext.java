package dev.vfyjxf.cirrus.api.recipe.execution.driver.context;

import net.minecraft.client.gui.screens.Screen;

/**
 * Carries the runtime screen class for client-side driver matching.
 */
public record ScreenContext(Class<? extends Screen> screenClass) implements DriverContext {}
