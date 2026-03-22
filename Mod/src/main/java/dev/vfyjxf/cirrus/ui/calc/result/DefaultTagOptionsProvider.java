package dev.vfyjxf.cirrus.ui.calc.result;

import dev.vfyjxf.cirrus.api.content.ContentApi;
import dev.vfyjxf.cirrus.api.content.render.StackRenderer;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class DefaultTagOptionsProvider implements TagOptionsProvider {
    @Override
    public List<TagCandidate> candidates(Namespace tagId) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(tagId.root(), tagId.path());
        ContentApi api = ContentApi.instance();
        List<TagCandidate> results = new ArrayList<>();

        for (TagType<?> tagType : api.tagTypes()) {
            if (!tagType.hasTag(location)) continue;
            ContentType<?> contentType = tagType.contentType();
            @SuppressWarnings("unchecked")
            ContentType<Object> typedContentType = (ContentType<Object>) contentType;
            StackType<?, ?> stackType = api.hasStackType(contentType) ? api.getStackType(contentType) : null;
            for (Object value : tagType.getTagValues(location)) {
                ContentStack<?, ?> stack;
                if (stackType == null) {
                    stack = ContentStack.empty();
                } else {
                    @SuppressWarnings("unchecked")
                    StackType<Object, Object> typedStackType = (StackType<Object, Object>) stackType;
                    stack = ContentStack.fromContent(typedStackType, value, typedStackType.quantity());
                }
                String optionId = typedContentType.getLocation(value) == null ? typedContentType.asString(value)
                    : typedContentType.getLocation(value).toString();
                String name;
                if (stack.isEmpty()) {
                    name = optionId;
                } else {
                    @SuppressWarnings("rawtypes")
                    ContentStack rawStack = stack;
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    StackRenderer renderer = StackRenderer.getStackRender(stack.type());
                    name = renderer.getDisplayName(rawStack.value());
                }
                results.add(new TagCandidate(optionId, name, stack));
            }
        }
        return results;
    }
}
