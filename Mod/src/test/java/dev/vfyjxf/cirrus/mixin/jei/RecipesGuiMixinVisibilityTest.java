package dev.vfyjxf.cirrus.test.mixin.jei;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipesGuiMixinVisibilityTest {

    @Test
    void recipeGuiButtonsUseJeiAdvancedButtonControllerApi() throws Exception {
        String plugin = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/internal/integration/jei/CirrusJeiPlugin.java"));
        String config = Files.readString(resolvePath("src/main/resources/calculator_cirrus.mixins.json"));

        assertTrue(plugin.contains("registration.addRecipeButtonFactory(JeiRecipeCalculatorTargetButtonHelper.buttonFactory());"));
        assertTrue(plugin.contains("registration.addRecipeButtonFactory(JeiRecipeDatabaseButtonHelper.buttonFactory());"));
        assertTrue(plugin.contains("registration.addRecipeButtonFactory(JeiTargetButtonHelper.buttonFactory());"));
        assertFalse(config.contains("\"jei.RecipesGuiMixin\""));
        assertFalse(config.contains("\"jei.RecipeGuiLayoutsAccessor\""));
        assertFalse(config.contains("\"jei.RecipeLayoutWithButtonsAccessor\""));
    }

    @Test
    void recipeGuiButtonsNoLongerUseCustomRecipesGuiRenderingMixin() throws Exception {
        assertFalse(Files.exists(resolveOptionalPath("src/main/java/dev/vfyjxf/cirrus/mixin/jei/RecipesGuiMixin.java")));
        assertFalse(Files.exists(resolveOptionalPath("src/main/java/dev/vfyjxf/cirrus/mixin/jei/RecipeGuiLayoutsAccessor.java")));
        assertFalse(Files.exists(resolveOptionalPath("src/main/java/dev/vfyjxf/cirrus/mixin/jei/RecipeLayoutWithButtonsAccessor.java")));
    }

    @Test
    void recipeButtonControllersFilterByRecipeSystemCapability() throws Exception {
        String calculator = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/internal/integration/jei/JeiRecipeCalculatorTargetButtonHelper.java"));
        String database = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/internal/integration/jei/JeiRecipeDatabaseButtonHelper.java"));
        String execution = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/internal/integration/jei/JeiTargetButtonHelper.java"));
        String bridge = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/internal/integration/jei/JeiTargetRecipeBridge.java"));

        assertTrue(calculator.contains("public @Nullable <T> IIconButtonController createButtonController"));
        assertTrue(database.contains("public @Nullable <T> IIconButtonController createButtonController"));
        assertTrue(execution.contains("public @Nullable <T> IIconButtonController createButtonController"));
        assertTrue(calculator.contains("JeiTargetRecipeBridge.resolveCalculator(recipeLayoutDrawable) == null"));
        assertTrue(database.contains("JeiTargetRecipeBridge.resolveRecipeDatabase(recipeLayoutDrawable) == null"));
        assertTrue(execution.contains("JeiTargetRecipeBridge.resolveExecution(recipeLayoutDrawable) == null"));
        assertTrue(bridge.contains("entry.supportsCalculator()"));
        assertTrue(bridge.contains("entry.supportsRecipeDatabase()"));
        assertTrue(bridge.contains("entry.supportsExecution()"));
    }

    @Test
    void recipeButtonControllersKeepExistingActionsAndTooltips() throws Exception {
        String calculator = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/internal/integration/jei/JeiRecipeCalculatorTargetButtonHelper.java"));
        String database = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/internal/integration/jei/JeiRecipeDatabaseButtonHelper.java"));
        String execution = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/internal/integration/jei/JeiTargetButtonHelper.java"));
        String actions = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/internal/integration/jei/JeiTargetSelectionActions.java"));

        assertTrue(calculator.contains("CalculatorTargetRecipeState.set(entry, 1)"));
        assertTrue(calculator.contains("Minecraft.getInstance().setScreen(new CalculatorScreen())"));
        assertTrue(calculator.contains("CirrusLangs.Calculator.Jei.Target.tooltip"));
        assertTrue(database.contains("addToActiveDatabase(entry)"));
        assertTrue(database.contains("CirrusLangs.Calculator.Jei.Database.tooltipCurrent"));
        assertTrue(database.contains("state.setActive(supported && activeDatabaseEditable())"));
        assertTrue(execution.contains("ExecutionTargetRecipeState.set(entry, times)"));
        assertTrue(execution.contains("JeiTargetSelectionActions.closeCurrentRecipeScreenIfNeeded"));
        assertTrue(execution.contains("CirrusLangs.Execution.Jei.Target.tooltip"));
        assertTrue(actions.contains("recipesGui.onClose();"));
    }

    @Test
    void recipeButtonControllersUseJeiButtonStateIcons() throws Exception {
        String calculator = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/internal/integration/jei/JeiRecipeCalculatorTargetButtonHelper.java"));
        String database = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/internal/integration/jei/JeiRecipeDatabaseButtonHelper.java"));
        String execution = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/internal/integration/jei/JeiTargetButtonHelper.java"));

        assertTrue(calculator.contains("state.setIcon(JeiTargetButtonHelper.drawable(CirrusTextures.Icon.calculatorTarget))"));
        assertTrue(database.contains("state.setIcon(JeiTargetButtonHelper.drawable(CirrusTextures.Icon.Small.plus))"));
        assertTrue(execution.contains("state.setIcon(drawable(CirrusTextures.Icon.start))"));
        assertTrue(execution.contains("private record SizedTextureDrawable"));
        assertFalse(calculator.contains("renderButton("));
        assertFalse(database.contains("renderButton("));
        assertFalse(execution.contains("collectButtons("));
    }

    @Test
    void bookmarkOverlayMixinRemainsRegisteredForBookmarkOverlayButtonsOnly() throws Exception {
        String config = Files.readString(resolvePath("src/main/resources/calculator_cirrus.mixins.json"));
        String mixin = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/mixin/jei/BookmarkOverlayMixin.java"));

        assertTrue(config.contains("\"jei.BookmarkOverlayMixin\""));
        assertTrue(mixin.contains("JeiBookmarkOverlayToggleButton.renderCalculator"));
        assertTrue(mixin.contains("JeiBookmarkOverlayToggleButton.renderOverlayToggle"));
        assertTrue(mixin.contains("Minecraft.getInstance().setScreen(new CalculatorScreen());"));
        assertTrue(mixin.contains("ExecutionOverlayOpenState.toggle();"));
        assertFalse(mixin.contains("recipesGui.onClose();"));
    }

    @Test
    void jeiRecipePreviewCachesLayoutDrawables() throws Exception {
        String tooltip = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/ui/execution/tooltip/JeiRecipeTooltipComponent.java"));
        String plugin = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/internal/integration/jei/CirrusJeiPlugin.java"));

        assertTrue(tooltip.contains("private static final Map<LayoutCacheKey, IRecipeLayoutDrawable<?>> layoutCache"));
        assertTrue(tooltip.contains("private static final int recipePreviewBorderSize = 4;"));
        assertTrue(tooltip.contains("var jeiRuntime = Internal.getJeiRuntime();"));
        assertTrue(tooltip.contains("Internal.getTextures().getRecipePreviewBackground()"));
        assertTrue(tooltip.contains("Supplier<@Nullable IRecipeLayoutDrawable<?>> factory = () -> context.manager().createRecipeLayoutDrawable"));
        assertTrue(tooltip.contains("context.recipePreviewBackground()"));
        assertTrue(tooltip.contains("recipePreviewBorderSize"));
        assertTrue(tooltip.contains("cachedLayout(key, factory)"));
        assertTrue(tooltip.contains("public static @Nullable IRecipeLayoutDrawable<?> createIndependentLayoutDrawable"));
        assertTrue(tooltip.contains("return createLayoutDrawable(entry, false);"));
        assertTrue(tooltip.contains("private static final Map<IRecipeLayoutDrawable<?>, Long> layoutTickCache = new WeakHashMap<>();"));
        assertTrue(tooltip.contains("public static void tickLayoutOncePerClientTick(IRecipeLayoutDrawable<?> layout)"));
        assertTrue(tooltip.contains("RecipeIdLayoutCacheKey"));
        assertTrue(tooltip.contains("RecipeEntryLayoutCacheKey"));
        assertFalse(tooltip.contains("RecipeIdentityLayoutCacheKey"));
        assertTrue(tooltip.contains("public static void clearLayoutCache()"));
        assertTrue(plugin.contains("JeiRecipeTooltipComponent.clearLayoutCache();"));
    }

    @Test
    void jeiPreviewWidgetsKeepTheirOwnTimerLayouts() throws Exception {
        String widget = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/ui/execution/widget/JeiRecipePreviewWidget.java"));
        String card = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/ui/execution/page/RecipeCurrentRecipeCardWidget.java"));

        assertTrue(widget.contains("JeiRecipeTooltipComponent.createIndependentLayoutDrawable(recipe)"));
        assertTrue(widget.contains("public boolean matches(@Nullable RecipeEntry<?> recipe)"));
        assertTrue(widget.contains("setTickable(true);"));
        assertTrue(widget.contains("public void tick()"));
        assertTrue(widget.contains("JeiRecipeTooltipComponent.tickLayoutOncePerClientTick(layout);"));
        String renderBody = widget.substring(widget.indexOf("protected void renderInternal"));
        assertFalse(renderBody.contains("createRecipeLayoutDrawable"));
        assertFalse(renderBody.contains("createLayoutDrawable"));
        assertFalse(renderBody.contains("layout.tick();"));

        assertTrue(card.contains("JeiRecipePreviewWidget preview = ensureJeiPreview(context.recipe());"));
        assertTrue(card.contains("if (active != null && active.matches(recipe))"));
        assertFalse(card.contains("JeiRecipePreviewWidget preview = JeiRecipePreviewWidget.create(context.recipe());"));
    }

    @Test
    void jeiLayoutCreationSkipsUnhandledRecipesBeforeCallingJei() throws Exception {
        String resolver = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/recipe/system/jei/JeiRecipeLayoutResolver.java"));
        String tooltip = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/ui/execution/tooltip/JeiRecipeTooltipComponent.java"));
        String plugin = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/internal/integration/jei/CirrusJeiPlugin.java"));

        assertTrue(resolver.contains("ignoredLayoutClasses = ConcurrentHashMap.newKeySet()"));
        assertTrue(resolver.contains("ignoredLayoutRecipes = ConcurrentHashMap.newKeySet()"));
        assertTrue(resolver.contains("if (!category.isHandled(recipe))"));
        assertTrue(resolver.contains("rememberLayoutSkip(recipeType, recipe, recipeId);"));
        assertTrue(resolver.contains("if (recipeId == null)"));
        assertTrue(resolver.contains("ignoredLayoutClasses.add(LayoutIgnoreClassKey.create(recipeType, recipe));"));
        assertTrue(resolver.contains("rememberLayoutFailure(recipeType, recipe"));
        assertTrue(resolver.contains("effectiveRecipeClassName(recipe)"));
        assertTrue(resolver.contains("holder.value().getClass().getName()"));
        assertTrue(resolver.indexOf("shouldSkipLayout(recipeType, category, recipe, recipeId)") <
            resolver.indexOf("manager.createRecipeLayoutDrawable(category, recipe"));

        assertTrue(tooltip.contains("JeiRecipeLayoutResolver.shouldSkipLayout(recipeType, category, typedRecipe, recipeId)"));
        assertTrue(tooltip.contains("JeiRecipeLayoutResolver.rememberLayoutFailure(recipeType, typedRecipe, recipeId);"));
        assertTrue(tooltip.indexOf("JeiRecipeLayoutResolver.shouldSkipLayout(recipeType, category, typedRecipe, recipeId)") <
            tooltip.indexOf("context.manager().createRecipeLayoutDrawable"));

        assertTrue(plugin.contains("JeiRecipeLayoutResolver.clearIgnoredLayouts();"));
    }

    private static Path resolvePath(String relative) {
        Path path = resolveOptionalPath(relative);
        if (Files.exists(path)) {
            return path;
        }
        throw new IllegalStateException("Unable to locate " + relative + " from " + Path.of("").toAbsolutePath());
    }

    private static Path resolveOptionalPath(String relative) {
        String moduleRelative = "Mod/" + relative;
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path local = cursor.resolve(relative);
            if (Files.exists(local)) {
                return local;
            }
            Path module = cursor.resolve(moduleRelative);
            if (Files.exists(module)) {
                return module;
            }
            cursor = cursor.getParent();
        }
        return Path.of("").toAbsolutePath().resolve(moduleRelative);
    }
}
