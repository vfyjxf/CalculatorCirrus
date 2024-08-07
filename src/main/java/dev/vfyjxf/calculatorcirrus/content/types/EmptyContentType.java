package dev.vfyjxf.calculatorcirrus.content.types;

import dev.vfyjxf.calculatorcirrus.api.content.IContentRenderer;
import dev.vfyjxf.calculatorcirrus.api.content.IContentSerializer;
import dev.vfyjxf.calculatorcirrus.api.content.IContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.IContentType;
import dev.vfyjxf.calculatorcirrus.api.content.MatchContext;
import dev.vfyjxf.calculatorcirrus.api.ui.ITooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipContext;
import dev.vfyjxf.calculatorcirrus.api.content.CirrusContentTypes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

public class EmptyContentType implements IContentType<Unit>, IContentSerializer<Unit> {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(namespace, "empty");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public Class<Unit> getTypeClass() {
        return Unit.class;
    }

    private IContentType<Unit> getType() {
        return CirrusContentTypes.EMPTY;
    }

    @Override
    public IContentRenderer<Unit> getRenderer() {
        return new IContentRenderer<>() {
            @Override
            public void render(IContentStack<Unit> content, GuiGraphics graphics, int width, int height, int mouseX, int mouseY, float delta) {

            }

            @Override
            public void render(Unit content, GuiGraphics graphics, int width, int height, int mouseX, int mouseY, float delta) {

            }

            @Override
            public void render(Unit content, GuiGraphics graphics, int mouseX, int mouseY, float delta) {

            }

            @Override
            public @Nullable ITooltip getTooltip(Unit content, TooltipContext context) {
                return null;
            }
        };
    }

    @Override
    public @Nullable String getUid(IContentStack<Unit> content, Unit value) {
        return "empty";
    }

    @Override
    public @Nullable String getModid(IContentStack<Unit> content, Unit value) {
        return null;
    }

    @Override
    public boolean isEmpty(IContentStack<Unit> content, Unit value) {
        return true;
    }

    @Override
    public Unit copy(IContentStack<Unit> content, Unit value) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit normalize(IContentStack<Unit> content, Unit value) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit wildcard(IContentStack<Unit> entry, Unit value) {
        return Unit.INSTANCE;
    }

    @Override
    public long getAmount(Unit value) {
        return -1;
    }

    @Override
    public DataComponentMap getComponents(IContentStack<Unit> content, Unit value) {
        return null;
    }

    @Override
    public Unit merge(Unit value1, Unit value2, MatchContext matchContext) {
        return Unit.INSTANCE;
    }

    @Override
    public boolean matches(Unit value1, Unit value2, MatchContext matchContext) {
        return true;
    }

    @Override
    public @Nullable IContentSerializer<Unit> getSerializer() {
        return this;
    }

    @Override
    public long hash(IContentStack<Unit> content, Unit value, boolean fuzzy) {
        return 0;
    }

    @Override
    public Stream<? extends TagKey<?>> getTagsFor(IContentStack<Unit> content, Unit value) {
        return Stream.empty();
    }

    @Override
    public CompoundTag save(IContentStack<Unit> stack, Unit value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().getId().toString());
        tag.putString("content", "empty");
        return tag;
    }

    @Override
    public Unit read(CompoundTag tag) {
        if (tag.contains("type") && tag.contains("content")) {
            ResourceLocation loaded = ResourceLocation.parse(tag.getString("type"));
            if (getType().getId().equals(loaded)) {
                return Unit.INSTANCE;
            }
        }
        return null;
    }
}
