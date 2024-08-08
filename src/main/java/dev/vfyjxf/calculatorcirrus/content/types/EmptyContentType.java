package dev.vfyjxf.calculatorcirrus.content.types;

import dev.vfyjxf.calculatorcirrus.api.content.ContentRenderer;
import dev.vfyjxf.calculatorcirrus.api.content.ContentSerializer;
import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.MatchContext;
import dev.vfyjxf.calculatorcirrus.api.ui.Tooltip;
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

public class EmptyContentType implements ContentType<Unit>, ContentSerializer<Unit> {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(namespace, "empty");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public Class<Unit> getTypeClass() {
        return Unit.class;
    }

    private ContentType<Unit> getType() {
        return CirrusContentTypes.EMPTY;
    }

    @Override
    public ContentRenderer<Unit> getRenderer() {
        return new ContentRenderer<>() {
            @Override
            public void render(ContentStack<Unit> content, GuiGraphics graphics, int width, int height, int mouseX, int mouseY, float delta) {

            }

            @Override
            public void render(Unit content, GuiGraphics graphics, int width, int height, int mouseX, int mouseY, float delta) {

            }

            @Override
            public void render(Unit content, GuiGraphics graphics, int mouseX, int mouseY, float delta) {

            }

            @Override
            public @Nullable Tooltip getTooltip(Unit content, TooltipContext context) {
                return null;
            }
        };
    }

    @Override
    public @Nullable String getUid(ContentStack<Unit> content, Unit value) {
        return "empty";
    }

    @Override
    public @Nullable String getModid(ContentStack<Unit> content, Unit value) {
        return null;
    }

    @Override
    public boolean isEmpty(ContentStack<Unit> content, Unit value) {
        return true;
    }

    @Override
    public Unit copy(ContentStack<Unit> content, Unit value) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit normalize(ContentStack<Unit> content, Unit value) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit wildcard(ContentStack<Unit> entry, Unit value) {
        return Unit.INSTANCE;
    }

    @Override
    public long getAmount(Unit value) {
        return -1;
    }

    @Override
    public DataComponentMap getComponents(ContentStack<Unit> content, Unit value) {
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
    public @Nullable ContentSerializer<Unit> getSerializer() {
        return this;
    }

    @Override
    public long hash(ContentStack<Unit> content, Unit value, boolean fuzzy) {
        return 0;
    }

    @Override
    public Stream<? extends TagKey<?>> getTagsFor(ContentStack<Unit> content, Unit value) {
        return Stream.empty();
    }

    @Override
    public CompoundTag save(ContentStack<Unit> stack, Unit value) {
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
