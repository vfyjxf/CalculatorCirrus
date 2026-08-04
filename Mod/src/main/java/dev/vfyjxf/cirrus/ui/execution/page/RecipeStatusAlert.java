package dev.vfyjxf.cirrus.ui.execution.page;

import org.jetbrains.annotations.Nullable;

record RecipeStatusAlert(String summary, @Nullable String detail, int color) {
}
