package dev.vfyjxf.cirrus.api.recipe.execution.driver.context;

import net.minecraft.world.inventory.MenuType;

/**
 * Carries the runtime menu type for driver matching.
 */
public record MenuTypeContext(MenuType<?> menuType) implements DriverContext {}
