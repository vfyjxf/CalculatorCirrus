package dev.vfyjxf.cirrus.recipe.classify;

import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassifyContent;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import dev.vfyjxf.cirrus.api.recipe.content.ComplexContent;
import dev.vfyjxf.cirrus.api.recipe.content.EmptyRecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.content.TypedStackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClausedContent;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the Minecraft-free {@link RecipeClassifyView} from live mod objects
 * (recipe entries and projections), so the classification core can run
 * unchanged on in-game recipes and on exported JSON data.
 */
public final class ClassifyViewFactory {

    /**
     * Tag id -> member content ids. Tag contents are fixed for the lifetime of
     * a world session and tags number in the low thousands, while tag inputs
     * appear in hundreds of thousands of recipes; materializing member ids per
     * recipe dominated classification cost, so they are computed once per tag.
     * Cleared via {@link #clearTagMemberCache()} on client unload.
     */
    private static final ConcurrentHashMap<String, Set<String>> tagMemberCache = new ConcurrentHashMap<>();

    private ClassifyViewFactory() {}

    /** Drops the memoized tag member sets (world unload / datapack reload). */
    public static void clearTagMemberCache() {
        tagMemberCache.clear();
    }

    /**
     * Builds a view for a standalone recipe entry with an optional resolved
     * projection (recipe-database browse path: no catalog index, machines not
     * resolved).
     */
    public static RecipeClassifyView fromProjection(RecipeEntry<?> entry, @Nullable RecipeProjection projection) {
        ResourceLocation id = entry.id();
        List<ClassifyContent> inputs = List.of();
        List<ClassifyContent> outputs = List.of();
        if (projection != null) {
            inputs = new ArrayList<>(projection.inputs().size());
            for (ClausedContent<InputContent> input : projection.inputs()) {
                inputs.add(of(input.content()));
            }
            outputs = new ArrayList<>(projection.outputs().size());
            for (ClausedContent<OutputContent> output : projection.outputs()) {
                outputs.add(of(output.content()));
            }
        }
        return new RecipeClassifyView(
            -1,
            entry.systemId().toString(),
            id == null ? "" : id.toString(),
            List.of(),
            inputs,
            outputs
        );
    }

    private static ClassifyContent of(InputContent content) {
        return switch (content) {
            case StackContent<?, ?> stack -> ofStack(stack.content(), content.amount(), content.chance());
            case TagStackContent<?> tag -> ofTag(tag, content.amount(), content.chance());
            case StackListContent list ->
                ofList(list.stacks(), ClassifyContent.Kind.any, content.amount(), content.chance());
            case TypedStackListContent<?, ?> list ->
                ofList(list.stacks(), ClassifyContent.Kind.typedAny, content.amount(), content.chance());
            case ValueContent<?> value ->
                new ClassifyContent(ClassifyContent.Kind.value, Set.of(), null, content.amount(), content.chance());
            case ComplexContent complex ->
                new ClassifyContent(ClassifyContent.Kind.complex, Set.of(), null, content.amount(), content.chance());
            case EmptyRecipeContent empty -> new ClassifyContent(ClassifyContent.Kind.empty, Set.of(), null, 0, 0);
            default ->
                new ClassifyContent(ClassifyContent.Kind.complex, Set.of(), null, content.amount(), content.chance());
        };
    }

    private static ClassifyContent of(OutputContent content) {
        if (content instanceof StackContent<?, ?> stack) {
            return ofStack(stack.content(), content.amount(), content.chance());
        }
        if (content instanceof ValueContent<?>) {
            return new ClassifyContent(ClassifyContent.Kind.value, Set.of(), null, content.amount(), content.chance());
        }
        if (content instanceof EmptyRecipeContent) {
            return new ClassifyContent(ClassifyContent.Kind.empty, Set.of(), null, 0, 0);
        }
        return new ClassifyContent(ClassifyContent.Kind.complex, Set.of(), null, content.amount(), content.chance());
    }

    private static ClassifyContent ofStack(ContentStack<?, ?> stack, double amount, double chance) {
        if (stack == null || stack.isEmpty()) {
            return new ClassifyContent(ClassifyContent.Kind.empty, Set.of(), null, 0, 0);
        }
        String id = contentId(stack);
        if (id == null) {
            return new ClassifyContent(ClassifyContent.Kind.complex, Set.of(), null, amount, chance);
        }
        return ClassifyContent.stack(id, amount, chance);
    }

    private static <T> ClassifyContent ofTag(TagStackContent<T> tag, double amount, double chance) {
        String tagId = tag.tagKey().location().toString();
        Set<String> members = tagMemberCache.computeIfAbsent(tagId, key -> materializeTagMembers(tag));
        return ClassifyContent.tag(tagId, members, amount, chance);
    }

    private static <T> Set<String> materializeTagMembers(TagStackContent<T> tag) {
        Set<String> members = new LinkedHashSet<>();
        ContentType<T> contentType = tag.contentType();
        for (T value : tag.values()) {
            ResourceLocation location = contentType.getLocation(value);
            if (location != null) {
                members.add(location.toString());
            }
        }
        // immutable copy: ClassifyContent's defensive Set.copyOf returns this
        // instance as-is, keeping the shared tagMemberCache cache effective
        return Set.copyOf(members);
    }

    /**
     * A stable identity key for a demanded input, used to memoize per-resource
     * planning decisions across different recipes demanding the same thing.
     * Returns {@code null} for opaque/complex inputs that cannot be keyed.
     */
    public static @Nullable String demandKey(InputContent input) {
        return switch (input) {
            case StackContent<?, ?> stack -> {
                String id = contentId(stack.content());
                yield id == null ? null : "stack:" + id;
            }
            case TagStackContent<?> tag -> "tag:" + tag.tagKey().location();
            case StackListContent list -> listKey(list.stacks(), "list");
            case TypedStackListContent<?, ?> list -> listKey(list.stacks(), "typedList");
            case ValueContent<?> value -> "value:" + value.content().type().typeId();
            default -> null;
        };
    }

    /**
     * The raw content id ({@code mod:path}) of a concrete single-stack input,
     * or {@code null} for tag/list/value/complex inputs.
     */
    public static @Nullable String resourceIdOf(InputContent input) {
        if (input instanceof StackContent<?, ?> stack) {
            return contentId(stack.content());
        }
        return null;
    }

    private static @Nullable String listKey(Iterable<? extends ContentStack<?, ?>> stacks, String prefix) {
        TreeSet<String> ids = new TreeSet<>();
        for (ContentStack<?, ?> stack : stacks) {
            String id = contentId(stack);
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return null;
        }
        return prefix + ":" + String.join("|", ids);
    }

    private static ClassifyContent ofList(
        Iterable<? extends ContentStack<?, ?>> stacks,
        ClassifyContent.Kind kind,
        double amount,
        double chance
    ) {
        Set<String> ids = new LinkedHashSet<>();
        for (ContentStack<?, ?> stack : stacks) {
            String id = contentId(stack);
            if (id != null) {
                ids.add(id);
            }
        }
        return new ClassifyContent(kind, ids, null, amount, chance);
    }

    /** The raw content id ({@code mod:path}) of a concrete stack output, or null. */
    public static @Nullable String resourceIdOf(OutputContent output) {
        if (output instanceof StackContent<?, ?> stack) {
            return contentId(stack.content());
        }
        return null;
    }

    /** The content id ({@code mod:path}) of a stack, or null when unresolvable. */
    public static @Nullable String contentId(@Nullable ContentStack<?, ?> stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return contentIdTyped(stack);
    }

    /**
     * Prefix marking a stack whose content could not be resolved to a registry
     * location: such ids are unroutable placeholders (never greppable, never
     * producible) and downstream consumers treat them as unresolvable rather
     * than as real demands.
     */
    public static final String unresolvedIdPrefix = "cirrus:unresolved/";

    private static <T, S> String contentIdTyped(ContentStack<T, S> stack) {
        ResourceLocation location = stack.contentType().getLocation(stack.content());
        if (location != null) {
            return location.toString();
        }
        // Unresolvable content: emit an explicit sentinel instead of the object
        // toString (e.g. "StackListContent{stacks=[...]}, which leaks into
        // demand keys and pending lists as an unroutable string.
        String fallback = stack.type().toString(stack.value());
        if (fallback != null && fallback.indexOf('{') >= 0) {
            return unresolvedIdPrefix + fallback.hashCode();
        }
        return unresolvedIdPrefix + Integer.hashCode(fallback == null ? 0 : fallback.hashCode());
    }
}
