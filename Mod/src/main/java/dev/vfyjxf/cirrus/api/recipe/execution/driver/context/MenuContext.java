package dev.vfyjxf.cirrus.api.recipe.execution.driver.context;

import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Carries the runtime menu class for driver matching.
 */
public record MenuContext(Class<? extends AbstractContainerMenu> menuClass) implements DriverContext {}
