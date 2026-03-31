package dev.vfyjxf.cirrus.ui.execution.tooltip;

import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.jei.JeiCategoryRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.jei.JeiRecipeLayoutResolver;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.drawable.IScalableDrawable;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.common.Internal;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public record JeiRecipeTooltipComponent(
        IRecipeLayoutDrawable<?> layout,
        int width,
        int height,
        int borderOffsetX,
        int borderOffsetY,
        int leftPadding
) implements TooltipComponent {
    private static final int layoutCacheMaxSize = 128;
    private static final int recipePreviewBorderSize = 4;
    private static final Map<LayoutCacheKey, IRecipeLayoutDrawable<?>> layoutCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<LayoutCacheKey, IRecipeLayoutDrawable<?>> eldest) {
            return size() > layoutCacheMaxSize;
        }
    };
    private static final Map<IRecipeLayoutDrawable<?>, Long> layoutTickCache = new WeakHashMap<>();
    private static final IScalableDrawable recipePreviewBackground = new CirrusRecipePreviewBackground();

    public static @Nullable JeiRecipeTooltipComponent create(@Nullable RecipeEntry<?> recipe) {
        return create(recipe, 0);
    }

    public static @Nullable JeiRecipeTooltipComponent create(@Nullable RecipeEntry<?> recipe, int leftPadding) {
        IRecipeLayoutDrawable<?> layout = createLayoutDrawable(recipe);
        if (layout == null) {
            return null;
        }
        layout.setPosition(0, 0);
        Rect2i rect = layout.getRectWithBorder();
        return new JeiRecipeTooltipComponent(
                layout,
                Math.max(1, rect.getWidth()),
                Math.max(1, rect.getHeight()),
                rect.getX(),
                rect.getY(),
                Math.max(0, leftPadding)
        );
    }

    public static ClientTooltipComponent toClient(JeiRecipeTooltipComponent data) {
        return new Client(data);
    }

    public static void clearLayoutCache() {
        synchronized (layoutCache) {
            layoutCache.clear();
        }
        synchronized (layoutTickCache) {
            layoutTickCache.clear();
        }
    }

    public static @Nullable IRecipeLayoutDrawable<?> createLayoutDrawable(@Nullable RecipeEntry<?> entry) {
        return createLayoutDrawable(entry, true);
    }

    public static @Nullable IRecipeLayoutDrawable<?> createIndependentLayoutDrawable(@Nullable RecipeEntry<?> entry) {
        return createLayoutDrawable(entry, false);
    }

    private static @Nullable IRecipeLayoutDrawable<?> createLayoutDrawable(@Nullable RecipeEntry<?> entry, boolean cache) {
        if (entry == null) {
            return null;
        }
        try {
            LayoutContext context = layoutContextOrNull();
            if (context == null) {
                return null;
            }
            RecipeSystem<?> system = entry.system();
            if (system instanceof JeiCategoryRecipeSystem<?> jeiSystem) {
                IRecipeLayoutDrawable<?> layout = createLayout(context, entry, jeiSystem.recipeType(), entry.recipe(), entry.id(), cache);
                if (layout != null) {
                    return layout;
                }
            }
            for (RecipeType<?> recipeType : RecipeApi.instance().getJeiRecipeTypeForSystem(entry.systemId())) {
                IRecipeLayoutDrawable<?> layout = createLayout(context, entry, recipeType, entry.recipe(), entry.id(), cache);
                if (layout != null) {
                    return layout;
                }
                layout = createLayoutByRecipeId(context, entry, recipeType, entry.id(), cache);
                if (layout != null) {
                    return layout;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static <T> @Nullable IRecipeLayoutDrawable<T> createLayout(
        LayoutContext context,
        RecipeEntry<?> entry,
        RecipeType<T> recipeType,
        Object recipe,
        @Nullable ResourceLocation preferredId,
        boolean cache
    ) {
        if (!recipeType.getRecipeClass().isInstance(recipe)) {
            return null;
        }
        var category = context.manager().getRecipeCategory(recipeType);
        T typedRecipe = recipeType.getRecipeClass().cast(recipe);
        @Nullable ResourceLocation recipeId = preferredId == null ? category.getRegistryName(typedRecipe) : preferredId;
        if (JeiRecipeLayoutResolver.shouldSkipLayout(recipeType, category, typedRecipe, recipeId)) {
            return null;
        }
        Supplier<@Nullable IRecipeLayoutDrawable<?>> factory = () -> context.manager().createRecipeLayoutDrawable(
                category,
                typedRecipe,
                context.focusFactory().getEmptyFocusGroup(),
                context.recipePreviewBackground(),
                recipePreviewBorderSize
        ).orElse(null);
        if (!cache) {
            try {
                @SuppressWarnings("unchecked")
                IRecipeLayoutDrawable<T> created = (IRecipeLayoutDrawable<T>) factory.get();
                return created;
            } catch (RuntimeException | LinkageError ignored) {
                JeiRecipeLayoutResolver.rememberLayoutFailure(recipeType, typedRecipe, recipeId);
                return null;
            }
        }
        LayoutCacheKey key = recipeId == null
                ? LayoutCacheKey.create(entry)
                : LayoutCacheKey.create(recipeType, recipeId);
        try {
            @SuppressWarnings("unchecked")
            IRecipeLayoutDrawable<T> cached = (IRecipeLayoutDrawable<T>) cachedLayout(key, factory);
            return cached;
        } catch (RuntimeException | LinkageError ignored) {
            JeiRecipeLayoutResolver.rememberLayoutFailure(recipeType, typedRecipe, recipeId);
            return null;
        }
    }

    private static <T> @Nullable IRecipeLayoutDrawable<T> createLayoutByRecipeId(
        LayoutContext context,
        RecipeEntry<?> entry,
        RecipeType<T> recipeType,
        @Nullable ResourceLocation id,
        boolean cache
    ) {
        if (id == null) {
            return null;
        }
        IRecipeCategory<T> category = context.manager().getRecipeCategory(recipeType);
        return context.manager().createRecipeLookup(recipeType)
            .get()
            .filter(candidate -> id.equals(category.getRegistryName(candidate)))
            .findFirst()
            .map(candidate -> createLayout(context, entry, recipeType, candidate, id, cache))
            .orElse(null);
    }

    private static @Nullable IRecipeLayoutDrawable<?> cachedLayout(
        LayoutCacheKey key,
        Supplier<@Nullable IRecipeLayoutDrawable<?>> factory
    ) {
        synchronized (layoutCache) {
            IRecipeLayoutDrawable<?> existing = layoutCache.get(key);
            if (existing != null) {
                return existing;
            }
        }
        IRecipeLayoutDrawable<?> created = factory.get();
        if (created == null) {
            return null;
        }
        synchronized (layoutCache) {
            IRecipeLayoutDrawable<?> existing = layoutCache.get(key);
            if (existing != null) {
                return existing;
            }
            layoutCache.put(key, created);
            return created;
        }
    }

    private static @Nullable LayoutContext layoutContextOrNull() {
        try {
            var jeiRuntime = Internal.getJeiRuntime();
            return new LayoutContext(
                    jeiRuntime.getRecipeManager(),
                    jeiRuntime.getJeiHelpers().getFocusFactory()
            );
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    public static void tickLayoutOncePerClientTick(IRecipeLayoutDrawable<?> layout) {
        long tick = Util.getMillis() / 50L;
        synchronized (layoutTickCache) {
            Long lastTick = layoutTickCache.get(layout);
            if (lastTick != null && lastTick == tick) {
                return;
            }
            layoutTickCache.put(layout, tick);
        }
        layout.tick();
    }

    private record LayoutContext(
            IRecipeManager manager,
            IFocusFactory focusFactory
    ) {
        private IScalableDrawable recipePreviewBackground() {
            return recipePreviewBackground;
        }
    }

    private static final class CirrusRecipePreviewBackground implements IScalableDrawable {
        @Override
        public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset, int width, int height) {
            CirrusTextures.Background.Outlined.flat.render(guiGraphics, xOffset, yOffset, width, height);
        }
    }

    private interface LayoutCacheKey {
        static LayoutCacheKey create(RecipeEntry<?> entry) {
            return new RecipeEntryLayoutCacheKey(entry);
        }

        static LayoutCacheKey create(RecipeType<?> recipeType, ResourceLocation recipeId) {
            return new RecipeIdLayoutCacheKey(recipeType, recipeId);
        }
    }

    private record RecipeIdLayoutCacheKey(RecipeType<?> recipeType, ResourceLocation recipeId) implements LayoutCacheKey {
    }

    private static final class RecipeEntryLayoutCacheKey implements LayoutCacheKey {
        private final RecipeSystem<?> system;
        private final Object recipe;
        private final int hash;

        private RecipeEntryLayoutCacheKey(RecipeEntry<?> entry) {
            this.system = entry.system();
            this.recipe = entry.recipe();
            this.hash = 31 * entry.systemId().hashCode() + recipeHash(entry.system(), entry.recipe());
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            return object instanceof RecipeEntryLayoutCacheKey other
                && system == other.system
                && sameRecipe(system, recipe, other.recipe);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    @SuppressWarnings("unchecked")
    private static <R> boolean sameRecipe(RecipeSystem<R> system, Object left, Object right) {
        return system.sameRecipe((R) left, (R) right);
    }

    @SuppressWarnings("unchecked")
    private static <R> int recipeHash(RecipeSystem<R> system, Object recipe) {
        return system.hash((R) recipe);
    }

    private static final class Client implements ClientTooltipComponent {
        private final JeiRecipeTooltipComponent data;

        private Client(JeiRecipeTooltipComponent data) {
            this.data = data;
        }

        @Override
        public int getWidth(Font font) {
            return data.width() + data.leftPadding();
        }

        @Override
        public int getHeight() {
            return data.height();
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
            IRecipeLayoutDrawable<?> layout = data.layout();
            layout.setPosition(x + data.leftPadding() - data.borderOffsetX(), y - data.borderOffsetY());
            tickLayoutOncePerClientTick(layout);
            layout.drawRecipe(graphics, -1, -1);
        }
    }
}
