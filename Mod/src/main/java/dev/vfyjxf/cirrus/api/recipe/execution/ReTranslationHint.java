package dev.vfyjxf.cirrus.api.recipe.execution;

import net.minecraft.network.chat.Component;

/**
 * Requests rebuilding an interaction execution plan from fresh runtime state.
 */
public record ReTranslationHint(Component summary) {}
