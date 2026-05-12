package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public record RuleConditionSpec(
    RuleConditionKind kind,
    RuleContentChannel channel,
    List<String> values
) {
    public static final Codec<RuleConditionSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        RuleConditionKind.CODEC.fieldOf("kind").forGetter(RuleConditionSpec::kind),
        RuleContentChannel.CODEC.fieldOf("channel").forGetter(RuleConditionSpec::channel),
        Codec.STRING.listOf().fieldOf("values").forGetter(RuleConditionSpec::values)
    ).apply(instance, RuleConditionSpec::new));

    public RuleConditionSpec {
        Checks.checkNotNull(kind, "kind");
        Checks.checkNotNull(channel, "channel");
        values = normalizeValues(values);
    }

    public static RuleConditionSpec always() {
        return new RuleConditionSpec(RuleConditionKind.always, RuleContentChannel.any, List.of());
    }

    public static RuleConditionSpec material(RuleContentChannel channel, String material) {
        return materials(channel, List.of(material));
    }

    public static RuleConditionSpec materials(RuleContentChannel channel, Collection<String> materials) {
        return new RuleConditionSpec(RuleConditionKind.material, channel, materials == null ? List.of() : List.copyOf(materials));
    }

    public String firstValue() {
        return values.isEmpty() ? "" : values.getFirst();
    }

    public boolean matches(CandidateContext context) {
        Checks.checkNotNull(context, "context");
        return switch (kind) {
            case always -> true;
            case material -> matchesMaterial(context);
        };
    }

    private boolean matchesMaterial(CandidateContext context) {
        if (values.isEmpty()) {
            return false;
        }
        if (channel == RuleContentChannel.any || channel == RuleContentChannel.input) {
            for (int inputIndex = 0; inputIndex < context.recipe().inputCount(); inputIndex++) {
                RecipeContent content = context.recipe().inputAt(inputIndex);
                if (matchesAnyMaterial(content)) {
                    return true;
                }
            }
        }
        if (channel == RuleContentChannel.any || channel == RuleContentChannel.output) {
            for (int outputIndex = 0; outputIndex < context.recipe().outputCount(); outputIndex++) {
                RecipeContent content = context.recipe().outputAt(outputIndex);
                if (matchesAnyMaterial(content)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesAnyMaterial(RecipeContent content) {
        for (String value : values) {
            if (matchesMaterial(content, value)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesMaterial(RecipeContent content) {
        return matchesMaterial(content, firstValue());
    }

    private boolean matchesMaterial(RecipeContent content, String value) {
        String token = value;
        if (token.startsWith("#")) {
            return matchesTag(content, token.substring(1));
        }
        if (token.startsWith("tag:")) {
            return matchesTag(content, token.substring("tag:".length()));
        }
        if (token.startsWith("fluid:")) {
            return matchesFluid(content, token.substring("fluid:".length()));
        }
        if (token.startsWith("item:")) {
            return matchesItem(content, token.substring("item:".length()));
        }
        return matchesItem(content, token);
    }

    private static List<String> normalizeValues(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String value : source) {
            String clean = value == null ? "" : value.trim();
            if (!clean.isBlank()) {
                out.add(clean);
            }
        }
        return List.copyOf(out);
    }

    private static boolean matchesTag(RecipeContent content, String id) {
        ResourceLocation location = parseLocation(id);
        if (location == null) {
            return false;
        }
        if (content instanceof TagStackContent<?> tag && tag.tagKey().location().equals(location)) {
            return true;
        }

        TagKey<Item> itemTag = TagKey.create(Registries.ITEM, location);
        TagKey<Fluid> fluidTag = TagKey.create(Registries.FLUID, location);
        if (content instanceof InputContent input && input.isEnumerable()) {
            for (ContentVariant variant : input.enumerate()) {
                if (variantMatchesTag(variant, itemTag, fluidTag)) {
                    return true;
                }
            }
        }
        if (content instanceof OutputContent output && output.asResource() instanceof ContentVariant variant) {
            return variantMatchesTag(variant, itemTag, fluidTag);
        }
        return false;
    }

    private static boolean variantMatchesTag(ContentVariant variant, TagKey<Item> itemTag, TagKey<Fluid> fluidTag) {
        if (variant instanceof ContentStack<?, ?> stack) {
            Object content = stack.content();
            if (content instanceof Item item) {
                return item.builtInRegistryHolder().is(itemTag);
            }
            if (content instanceof Fluid fluid) {
                return fluid.builtInRegistryHolder().is(fluidTag);
            }
        }
        if (variant instanceof ContentKind<?> kind) {
            Object content = kind.value();
            if (content instanceof Item item) {
                return item.builtInRegistryHolder().is(itemTag);
            }
            if (content instanceof Fluid fluid) {
                return fluid.builtInRegistryHolder().is(fluidTag);
            }
        }
        return false;
    }

    private static boolean matchesItem(RecipeContent content, String id) {
        ResourceLocation location = parseLocation(id);
        if (location == null) {
            return false;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(location).orElse(null);
        return item != null && content.satisfy(ContentStack.fromContent(item));
    }

    private static boolean matchesFluid(RecipeContent content, String id) {
        ResourceLocation location = parseLocation(id);
        if (location == null) {
            return false;
        }
        Fluid fluid = BuiltInRegistries.FLUID.getOptional(location).orElse(null);
        return fluid != null && content.satisfy(ContentStack.fromContent(fluid));
    }

    private static ResourceLocation parseLocation(String id) {
        try {
            return ResourceLocation.parse(id);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
