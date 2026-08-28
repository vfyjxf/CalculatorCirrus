package dev.vfyjxf.cirrus.recipe.chainexport;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationResult;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassifyContent;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifier;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Streams an exported {@code recipes-<ts>.json} pack dump into solver-ready
 * {@link ExportRecipe}s backed by the offline id content model, plus the
 * display specs used by the text renderer. Junk/duplicate recipes are removed
 * up-front via the production {@link RecipeClassifier} (builtin rules v2).
 */
final class ExportPackLoader {

    static final class SlotSpec {

        enum Kind {
            stack("stack"),
            tag("tag"),
            any("any"),
            typedAny("typedAny"),
            value("value"),
            complex("complex");

            final String label;

            Kind(String label) {
                this.label = label;
            }

            static Kind of(String raw) {
                for (Kind kind : values()) {
                    if (kind.label.equals(raw)) return kind;
                }
                return complex;
            }
        }

        final Kind kind;
        final String family;
        final String displayId;
        final @Nullable String tagId;
        final List<String> memberIds;
        final double amount;
        final double chance;
        final @Nullable String valueDisplay;
        final @Nullable String note;

        SlotSpec(Kind kind, String family, String displayId, @Nullable String tagId,
                 List<String> memberIds, double amount, double chance,
                 @Nullable String valueDisplay, @Nullable String note) {
            this.kind = kind;
            this.family = family;
            this.displayId = displayId;
            this.tagId = tagId;
            this.memberIds = memberIds;
            this.amount = amount;
            this.chance = chance;
            this.valueDisplay = valueDisplay;
            this.note = note;
        }
    }

    static final class ExportRecipe {

        final int index;
        final String systemId;
        final String source;
        final String recipeId;
        final ResourceLocation rlId;
        final List<String> machines;
        final List<SlotSpec> inputSpecs;
        final List<SlotSpec> outputSpecs;
        final ImmutableList<InputContent> inputs;
        final ImmutableList<OutputContent> outputs;
        /**
         * Advisory classification tags from the builtin rules (v2) — attached after
         * classification so chain text can mark recipes the production rule system
         * already knows about (exchange/recycling/recolor patterns, ...). Exclusion
         * tags (junk/duplicate) never get here: those recipes are dropped up-front.
         */
        Set<String> classificationTags = Set.of();

        ExportRecipe(int index, String systemId, String source, String recipeId, ResourceLocation rlId,
                     List<String> machines, List<SlotSpec> inputSpecs, List<SlotSpec> outputSpecs,
                     ImmutableList<InputContent> inputs, ImmutableList<OutputContent> outputs) {
            this.index = index;
            this.systemId = systemId;
            this.source = source;
            this.recipeId = recipeId;
            this.rlId = rlId;
            this.machines = machines;
            this.inputSpecs = inputSpecs;
            this.outputSpecs = outputSpecs;
            this.inputs = inputs;
            this.outputs = outputs;
        }
    }

    static final class ExportRecipeSystem implements RecipeSystem<ExportRecipe> {

        private final Namespace systemId;
        private final ImmutableList<ExportRecipe> recipes;
        private final Map<String, ExportRecipe> byRecipeId = new ConcurrentHashMap<>();
        private final Map<ResourceLocation, ExportRecipe> byRlId = new ConcurrentHashMap<>();

        ExportRecipeSystem(Namespace systemId, List<ExportRecipe> recipes) {
            this.systemId = systemId;
            this.recipes = Lists.immutable.ofAll(recipes);
            for (ExportRecipe recipe : recipes) {
                byRecipeId.putIfAbsent(recipe.recipeId, recipe);
                byRlId.putIfAbsent(recipe.rlId, recipe);
            }
        }

        @Override
        public Namespace systemId() {
            return systemId;
        }

        @Override
        public Class<?> basicType() {
            return ExportRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(ExportRecipe recipe) {
            return recipe.rlId;
        }

        @Override
        public @Nullable ExportRecipe findRecipe(String uniqueId) {
            return byRecipeId.get(uniqueId);
        }

        @Override
        public @Nullable ExportRecipe findRecipe(ResourceLocation registryName) {
            return byRlId.get(registryName);
        }

        @Override
        public int hash(ExportRecipe recipe) {
            return recipe.index;
        }

        @Override
        public boolean sameRecipe(ExportRecipe left, ExportRecipe right) {
            return left == right;
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, ExportRecipe recipe) {
            return ResolveResult.resolved(
                RecipeProjection.create(RecipeEntry.of(this, recipe), recipe.inputs.castToList(), recipe.outputs.castToList()));
        }

        @Override
        public RichIterable<ExportRecipe> allRecipes() {
            return recipes;
        }
    }

    record LoadedPack(
        String name,
        int totalRecipes,
        int keptRecipes,
        int excludedJunk,
        int excludedDuplicate,
        int droppedNoContent,
        List<ExportRecipeSystem> systems,
        List<ExportRecipe> recipes,
        Map<String, Integer> tagCounts,
        long parseMs,
        long classifyMs
    ) {
    }

    static LoadedPack load(Path packDir, OfflineContent.Session session) throws IOException {
        Path recipesFile = findRecipesFile(packDir);
        if (recipesFile == null) {
            throw new IOException("no recipes-*.json in " + packDir);
        }
        Map<String, String> tagFamilies = loadRegistriesAndAttach(packDir, session);
        if (!hasAnyRegistryFile(packDir)) {
            // Old exports (atm10-era) carry no registry dumps: pre-scan the recipe
            // file itself for stack/value families so parsing still sees a
            // ContentApi that knows every type (graph hashing needs variant types).
            prescanRecipeFamilies(recipesFile, session, tagFamilies);
            session.attachRegistry();
        }

        String packName = packDir.getFileName().toString();
        IdCanonicalizer ids = new IdCanonicalizer();

        List<RecipeClassifyView> views = new ArrayList<>();
        List<Partial> partials = new ArrayList<>();
        long parseStart = System.nanoTime();
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(recipesFile))) {
            reader.beginObject();
            while (reader.hasNext()) {
                if (reader.nextName().equals("recipes")) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        Partial partial = parseRecipe(reader, views.size(), session, tagFamilies, ids);
                        views.add(partial.view());
                        partials.add(partial);
                    }
                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }
        long parseMs = (System.nanoTime() - parseStart) / 1_000_000;

        long classifyStart = System.nanoTime();
        RecipeClassifier classifier = RecipeClassifier.createDefault();
        ClassificationResult result = classifier.classifyAll(views);
        BitSet excluded = result.recipesWithTag(ClassificationTags.JUNK);
        BitSet duplicates = result.recipesWithTag(ClassificationTags.DUPLICATE);
        excluded.or(duplicates);
        long classifyMs = (System.nanoTime() - classifyStart) / 1_000_000;

        int dropped = 0;
        Map<String, List<ExportRecipe>> bySystem = new LinkedHashMap<>();
        for (Partial partial : partials) {
            if (excluded.get(partial.recipe().index)) {
                continue;
            }
            ExportRecipe recipe = partial.recipe();
            if (recipe.inputs.isEmpty() || recipe.outputs.isEmpty()) {
                dropped++;
                continue;
            }
            Set<String> tags = result.tagsOf(recipe.index);
            if (!tags.isEmpty()) {
                // advisory tags only — exclusion tags were filtered above
                recipe.classificationTags = Set.copyOf(tags);
            }
            bySystem.computeIfAbsent(recipe.systemId, s -> new ArrayList<>()).add(recipe);
        }

        List<ExportRecipeSystem> systems = new ArrayList<>();
        List<ExportRecipe> kept = new ArrayList<>();
        int junk = result.recipesWithTag(ClassificationTags.JUNK).cardinality();
        for (Map.Entry<String, List<ExportRecipe>> entry : bySystem.entrySet()) {
            Namespace ns = Objects.requireNonNullElse(Namespace.maybe(entry.getKey()), Namespace.of("cirrus_offline", "unknown"));
            systems.add(new ExportRecipeSystem(ns, entry.getValue()));
            kept.addAll(entry.getValue());
        }

        return new LoadedPack(
            packName,
            views.size(),
            kept.size(),
            junk,
            duplicates.cardinality(),
            dropped,
            systems,
            kept,
            result.tagCounts(),
            parseMs,
            classifyMs
        );
    }

    //region streaming parse

    private record Partial(ExportRecipe recipe, RecipeClassifyView view) {
    }

    private static Partial parseRecipe(JsonReader reader, int index, OfflineContent.Session session,
                                       Map<String, String> tagFamilies, IdCanonicalizer ids) throws IOException {
        String system = "";
        String source = "";
        String id = "";
        List<String> machines = new ArrayList<>();
        List<SlotSpec> inSpecs = new ArrayList<>();
        List<SlotSpec> outSpecs = new ArrayList<>();
        List<InputContent> inputs = new ArrayList<>();
        List<OutputContent> outputs = new ArrayList<>();
        List<ClassifyContent> classifyInputs = new ArrayList<>();
        List<ClassifyContent> classifyOutputs = new ArrayList<>();

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "system" -> system = reader.nextString();
                case "source" -> {
                    if (reader.peek() == JsonToken.STRING) {
                        source = reader.nextString();
                    } else {
                        reader.skipValue();
                    }
                }
                case "id" -> {
                    if (reader.peek() == JsonToken.STRING) {
                        id = reader.nextString();
                    } else {
                        reader.skipValue();
                    }
                }
                case "machines" -> machines.addAll(parseStringArray(reader, ids));
                case "inputs" -> parseContentArray(reader, session, tagFamilies, ids, inSpecs, classifyInputs, true, o -> inputs.add((InputContent) o));
                case "outputs" -> parseContentArray(reader, session, tagFamilies, ids, outSpecs, classifyOutputs, false, o -> outputs.add((OutputContent) o));
                default -> reader.skipValue();
            }
        }
        reader.endObject();

        String systemRoot = system.contains(":") ? system.substring(0, system.indexOf(':')) : "cirrus_offline";
        String recipeId;
        ResourceLocation rlId;
        if (id == null || id.isEmpty()) {
            recipeId = systemRoot + ":/synthetic/" + index;
            rlId = ResourceLocation.tryParse(recipeId);
        } else {
            recipeId = ids.canonical(id);
            rlId = ResourceLocation.tryParse(recipeId);
            if (rlId == null) {
                rlId = ResourceLocation.tryParse(systemRoot + ":/" + id.replaceAll("[^a-zA-Z0-9/._-]", "_"));
            }
            if (rlId == null) {
                rlId = ResourceLocation.tryParse(systemRoot + ":/synthetic/" + index);
            }
        }

        ExportRecipe recipe = new ExportRecipe(
            index, system, source, recipeId, rlId,
            List.copyOf(machines), List.copyOf(inSpecs), List.copyOf(outSpecs),
            Lists.immutable.ofAll(inputs), Lists.immutable.ofAll(outputs));
        RecipeClassifyView view = new RecipeClassifyView(index, system, id, machines, classifyInputs, classifyOutputs);
        return new Partial(recipe, view);
    }

    private static void parseContentArray(JsonReader reader, OfflineContent.Session session, Map<String, String> tagFamilies,
                                          IdCanonicalizer ids, List<SlotSpec> specs, List<ClassifyContent> classify,
                                          boolean input, java.util.function.Consumer<Object> sink) throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            parseContent(reader, session, tagFamilies, ids, specs, classify, input, sink);
        }
        reader.endArray();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void parseContent(JsonReader reader, OfflineContent.Session session, Map<String, String> tagFamilies,
                                     IdCanonicalizer ids, List<SlotSpec> specs, List<ClassifyContent> classify,
                                     boolean input, java.util.function.Consumer<Object> sink) throws IOException {
        String kind = "complex";
        double amount = 1;
        double chance = 1.0;
        String matchStrategy = null;
        String tagId = null;
        String valueType = null;
        String valueRaw = null;
        String implementation = null;
        String stackTypeField = null;
        List<StackMember> members = new ArrayList<>();

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "kind" -> kind = reader.nextString();
                case "amount" -> amount = reader.nextDouble();
                case "chance" -> chance = reader.nextDouble();
                case "matchStrategy" -> {
                    if (reader.peek() == JsonToken.STRING) matchStrategy = reader.nextString();
                    else reader.skipValue();
                }
                case "tag" -> tagId = reader.nextString();
                case "valueType" -> valueType = reader.nextString();
                case "value" -> valueRaw = readRawScalar(reader);
                case "implementation" -> {
                    if (reader.peek() == JsonToken.STRING) implementation = reader.nextString();
                    else reader.skipValue();
                }
                case "stackType" -> {
                    if (reader.peek() == JsonToken.STRING) stackTypeField = reader.nextString();
                    else reader.skipValue();
                }
                case "values" -> {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        members.add(new StackMember(OfflineContent.ITEM_FAMILY, reader.nextString()));
                    }
                    reader.endArray();
                }
                case "stack" -> members.add(parseStackMember(reader));
                case "stacks" -> {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        members.add(parseStackMember(reader));
                    }
                    reader.endArray();
                }
                default -> reader.skipValue();
            }
        }
        reader.endObject();

        SlotSpec.Kind slotKind = SlotSpec.Kind.of(kind);
        Set<String> classifyIds = new LinkedHashSet<>();
        String family = resolveFamily(slotKind, tagId, stackTypeField, members, tagFamilies);

        long longAmount = Math.max(1, Math.round(amount));
        float floatChance = (float) Math.max(0.0, Math.min(1.0, chance));
        MatchStrategy strategy = parseMatchStrategy(matchStrategy);

        switch (slotKind) {
            case stack -> {
                StackMember member = members.isEmpty() ? new StackMember(family, "unknown:unknown") : members.get(0);
                String displayId = member.id;
                String canonicalId = ids.canonical(displayId);
                classifyIds.add(displayId);
                OfflineContent.IdStackType type = session.stackType(member.family);
                ContentStack<String, String> stack = ContentStack.fromStack(type, canonicalId, Math.max(1, member.count > 0 ? member.count : longAmount));
                Object content = input
                    ? InputContent.fromStack(stack, floatChance, strategy)
                    : OutputContent.fromStack(stack, floatChance);
                sink.accept(content);
                specs.add(new SlotSpec(slotKind, member.family, displayId, null, List.of(),
                    member.count > 0 ? member.count : longAmount, chance, null, null));
            }
            case tag -> {
                List<String> canonicalMembers = new ArrayList<>(members.size());
                for (StackMember member : members) {
                    classifyIds.add(member.id);
                    canonicalMembers.add(ids.canonical(member.id));
                }
                String displayId = "#" + tagId;
                classifyIds.add(displayId);
                if (!canonicalMembers.isEmpty()) {
                    OfflineContent.IdStackType type = session.stackType(family);
                    Object content = input
                        ? InputContent.fromTypedStackList(type, canonicalMembers, longAmount, floatChance)
                        : typedOutputList(type, canonicalMembers, longAmount, floatChance);
                    sink.accept(content);
                } else {
                    // tag without members: keep an unsatisfiable marker so the planner reports it
                    OfflineContent.IdStackType type = session.stackType(family);
                    String marker = ids.canonical("tag:" + tagId);
                    ContentStack<String, String> stack = ContentStack.fromStack(type, marker, longAmount);
                    Object content = input
                        ? InputContent.fromStack(stack, floatChance)
                        : OutputContent.fromStack(stack, floatChance);
                    sink.accept(content);
                }
                specs.add(new SlotSpec(slotKind, family, displayId, tagId, canonicalMembers,
                    longAmount, chance, null, canonicalMembers.isEmpty() ? "tag has no members" : null));
            }
            case any, typedAny -> {
                List<String> canonicalMembers = new ArrayList<>(members.size());
                Set<String> memberFamilies = new LinkedHashSet<>();
                for (StackMember member : members) {
                    classifyIds.add(member.id);
                    canonicalMembers.add(ids.canonical(member.id));
                    memberFamilies.add(member.family);
                }
                String displayId = canonicalMembers.isEmpty() ? "(empty)" : canonicalMembers.get(0) + "|+" + (canonicalMembers.size() - 1);
                if (!canonicalMembers.isEmpty()) {
                    Object content;
                    if (memberFamilies.size() == 1) {
                        OfflineContent.IdStackType type = session.stackType(memberFamilies.iterator().next());
                        content = input
                            ? InputContent.fromTypedStackList(type, canonicalMembers, longAmount, floatChance)
                            : typedOutputList(type, canonicalMembers, longAmount, floatChance);
                    } else if (input) {
                        List<ContentStack<?, ?>> stacks = new ArrayList<>();
                        for (int i = 0; i < members.size(); i++) {
                            OfflineContent.IdStackType type = session.stackType(members.get(i).family);
                            stacks.add(ContentStack.fromStack(type, canonicalMembers.get(i), longAmount));
                        }
                        content = InputContent.fromStackList(stacks, longAmount, floatChance);
                    } else {
                        OfflineContent.IdStackType type = session.stackType(members.get(0).family);
                        content = typedOutputList(type, List.of(canonicalMembers.get(0)), longAmount, floatChance);
                    }
                    sink.accept(content);
                }
                specs.add(new SlotSpec(slotKind, family, displayId, null, canonicalMembers,
                    longAmount, chance, null, canonicalMembers.isEmpty() ? "empty alternatives" : null));
            }
            case value -> {
                String typeId = valueType != null ? valueType : "unknown";
                OfflineContent.IdValueType type = session.valueType(typeId);
                long value = parseLongValue(valueRaw);
                String displayId = typeId + "=" + value;
                Object content = input
                    ? InputContent.fromValue(type, value, floatChance)
                    : OutputContent.fromValue(type, value);
                sink.accept(content);
                specs.add(new SlotSpec(slotKind, "value", displayId, null, List.of(), longAmount, chance, displayId, null));
            }
            case complex -> {
                String displayId = "complex:" + (implementation != null ? implementation.substring(implementation.lastIndexOf('.') + 1) : "?");
                // complex slots are not modeled into the solver; kept visible for audit
                specs.add(new SlotSpec(slotKind, "complex", displayId, null, List.of(),
                    longAmount, chance, null, "not modeled (implementation fallback)"));
            }
        }
        classify.add(new ClassifyContent(toClassifyKind(slotKind), classifyIds, tagId, amount, chance));
    }

    private static Object typedOutputList(OfflineContent.IdStackType type, List<String> members, long amount, float chance) {
        // multi-alternative outputs are ambiguous by nature; model the first member as the concrete output
        return OutputContent.fromStack(ContentStack.fromStack(type, members.get(0), amount), chance);
    }

    private record StackMember(String family, String id, long count) {

        StackMember(String family, String id) {
            this(family, id, 1);
        }
    }

    private static StackMember parseStackMember(JsonReader reader) throws IOException {
        String type = OfflineContent.ITEM_FAMILY;
        String id = null;
        String fluid = null;
        long count = 1;
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "type" -> {
                    if (reader.peek() == JsonToken.STRING) type = reader.nextString();
                    else reader.skipValue();
                }
                case "id" -> {
                    if (reader.peek() == JsonToken.STRING) id = reader.nextString();
                    else reader.skipValue();
                }
                case "fluid" -> {
                    if (reader.peek() == JsonToken.STRING) fluid = reader.nextString();
                    else reader.skipValue();
                }
                case "count" -> {
                    if (reader.peek() == JsonToken.NUMBER) count = Math.round(reader.nextDouble());
                    else reader.skipValue();
                }
                case "amount" -> {
                    if (reader.peek() == JsonToken.NUMBER) count = Math.round(reader.nextDouble());
                    else reader.skipValue();
                }
                case "components" -> reader.skipValue();
                default -> reader.skipValue();
            }
        }
        reader.endObject();
        String resolvedId = id != null ? id : fluid;
        String family = switch (type) {
            case "item" -> OfflineContent.ITEM_FAMILY;
            case "fluid" -> OfflineContent.FLUID_FAMILY;
            default -> type;
        };
        return new StackMember(family, resolvedId != null ? resolvedId : "unknown:unknown", Math.max(1, count));
    }

    private static String resolveFamily(SlotSpec.Kind kind, @Nullable String tagId, @Nullable String stackTypeField,
                                        List<StackMember> members, Map<String, String> tagFamilies) {
        return switch (kind) {
            case tag -> tagId != null
                ? tagFamilies.getOrDefault(tagId, OfflineContent.ITEM_FAMILY)
                : OfflineContent.ITEM_FAMILY;
            case typedAny -> stackTypeField != null
                ? OfflineContent.familyOfStackType(stackTypeField)
                : (members.isEmpty() ? OfflineContent.ITEM_FAMILY : members.get(0).family);
            case stack -> members.isEmpty() ? OfflineContent.ITEM_FAMILY : members.get(0).family;
            default -> OfflineContent.ITEM_FAMILY;
        };
    }

    private static ClassifyContent.Kind toClassifyKind(SlotSpec.Kind kind) {
        return switch (kind) {
            case stack -> ClassifyContent.Kind.stack;
            case tag -> ClassifyContent.Kind.tag;
            case any -> ClassifyContent.Kind.any;
            case typedAny -> ClassifyContent.Kind.typedAny;
            case value -> ClassifyContent.Kind.value;
            case complex -> ClassifyContent.Kind.complex;
        };
    }

    private static long parseLongValue(@Nullable String raw) {
        if (raw == null) return 0;
        try {
            return Math.round(Double.parseDouble(raw));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String readRawScalar(JsonReader reader) throws IOException {
        JsonToken peek = reader.peek();
        return switch (peek) {
            case STRING -> reader.nextString();
            case NUMBER -> String.valueOf(reader.nextDouble());
            case BOOLEAN -> String.valueOf(reader.nextBoolean());
            default -> {
                reader.skipValue();
                yield null;
            }
        };
    }

    private static MatchStrategy parseMatchStrategy(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) return MatchStrategy.identity;
        return switch (raw) {
            case "amount" -> MatchStrategy.amount;
            case "identityAndComponent" -> MatchStrategy.identityAndComponent;
            case "identityAndAmount" -> MatchStrategy.identityAndAmount;
            case "all", "identity+amount+component" -> MatchStrategy.all;
            case "none" -> MatchStrategy.none;
            default -> MatchStrategy.identity;
        };
    }

    private static List<String> parseStringArray(JsonReader reader, IdCanonicalizer ids) throws IOException {
        List<String> values = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
            values.add(ids.canonical(reader.nextString()));
        }
        reader.endArray();
        return values;
    }

    private static @Nullable Path findRecipesFile(Path packDir) throws IOException {
        try (Stream<Path> stream = Files.list(packDir)) {
            return stream
                .filter(path -> path.getFileName().toString().startsWith("recipes-"))
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .findFirst()
                .orElse(null);
        }
    }

    /**
     * Pre-registers every content/stack/value family from the small registry dumps
     * and attaches ContentApi before recipe parsing, so TypedContentStack instances
     * capture a non-null fallback VariantType (graph hashing requires it).
     */
    private static Map<String, String> loadRegistriesAndAttach(Path packDir, OfflineContent.Session session) throws IOException {
        Map<String, String> tagFamilies = new HashMap<>();
        try (Stream<Path> stream = Files.list(packDir)) {
            List<Path> registryFiles = stream
                .filter(path -> {
                    String name = path.getFileName().toString();
                    return name.endsWith(".json") && (name.startsWith("content-types-")
                        || name.startsWith("stack-types-") || name.startsWith("tag-types-")
                        || name.startsWith("value-types-"));
                })
                .toList();
            for (Path file : registryFiles) {
                String prefix = file.getFileName().toString().split("-", 2)[0];
                String expectedArray = switch (prefix) {
                    case "content" -> "contentTypes";
                    case "stack" -> "stackTypes";
                    case "tag" -> "tagTypes";
                    case "value" -> "valueTypes";
                    default -> null;
                };
                try (JsonReader reader = new JsonReader(Files.newBufferedReader(file))) {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String arrayName = reader.nextName();
                        if (expectedArray == null || !arrayName.equals(expectedArray)) {
                            reader.skipValue();
                            continue;
                        }
                        reader.beginArray();
                        while (reader.hasNext()) {
                            switch (prefix) {
                                case "content" -> {
                                    String id = readRegistryId(reader, "id");
                                    if (id != null) {
                                        session.stackType(OfflineContent.familyOfContentType(id));
                                    }
                                }
                                case "stack" -> {
                                    // entries carry id + contentType; both map to a family
                                    String family = null;
                                    reader.beginObject();
                                    while (reader.hasNext()) {
                                        switch (reader.nextName()) {
                                            case "id" -> family = OfflineContent.familyOfStackType(reader.nextString());
                                            case "contentType" -> {
                                                if (family == null && reader.peek() == JsonToken.STRING) {
                                                    family = OfflineContent.familyOfContentType(reader.nextString());
                                                } else {
                                                    reader.skipValue();
                                                }
                                            }
                                            default -> reader.skipValue();
                                        }
                                    }
                                    reader.endObject();
                                    if (family != null) {
                                        session.stackType(family);
                                    }
                                }
                                case "tag" -> {
                                    String family = null;
                                    List<String> tags = new ArrayList<>();
                                    reader.beginObject();
                                    while (reader.hasNext()) {
                                        switch (reader.nextName()) {
                                            case "contentType" -> {
                                                if (reader.peek() == JsonToken.STRING) {
                                                    family = OfflineContent.familyOfContentType(reader.nextString());
                                                } else {
                                                    reader.skipValue();
                                                }
                                            }
                                            case "tags" -> {
                                                reader.beginArray();
                                                while (reader.hasNext()) {
                                                    tags.add(reader.nextString());
                                                }
                                                reader.endArray();
                                            }
                                            default -> reader.skipValue();
                                        }
                                    }
                                    reader.endObject();
                                    if (family != null) {
                                        for (String tag : tags) {
                                            tagFamilies.putIfAbsent(tag, family);
                                        }
                                    }
                                }
                                case "value" -> {
                                    String id = readRegistryId(reader, "id");
                                    if (id != null) {
                                        session.valueType(id);
                                    }
                                }
                                default -> reader.skipValue();
                            }
                        }
                        reader.endArray();
                    }
                    reader.endObject();
                }
            }
        }
        session.attachRegistry();
        return tagFamilies;
    }

    private static String readRegistryId(JsonReader reader, String idField) throws IOException {
        String id = null;
        reader.beginObject();
        while (reader.hasNext()) {
            if (reader.nextName().equals(idField) && reader.peek() == JsonToken.STRING) {
                id = reader.nextString();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return id;
    }

    private static boolean hasAnyRegistryFile(Path packDir) throws IOException {
        try (Stream<Path> stream = Files.list(packDir)) {
            return stream.anyMatch(path -> {
                String name = path.getFileName().toString();
                return name.endsWith(".json") && (name.startsWith("content-types-")
                    || name.startsWith("stack-types-") || name.startsWith("tag-types-")
                    || name.startsWith("value-types-"));
            });
        }
    }

    /** Shallow streaming pass over the recipe dump collecting type families (old exports). */
    private static void prescanRecipeFamilies(Path recipesFile, OfflineContent.Session session,
                                              Map<String, String> tagFamilies) throws IOException {
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(recipesFile))) {
            reader.beginObject();
            while (reader.hasNext()) {
                if (reader.nextName().equals("recipes")) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String name = reader.nextName();
                            if (name.equals("inputs") || name.equals("outputs")) {
                                reader.beginArray();
                                while (reader.hasNext()) {
                                    prescanContent(reader, session, tagFamilies);
                                }
                                reader.endArray();
                            } else {
                                reader.skipValue();
                            }
                        }
                        reader.endObject();
                    }
                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }
    }

    private static void prescanContent(JsonReader reader, OfflineContent.Session session,
                                       Map<String, String> tagFamilies) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "valueType" -> {
                    if (reader.peek() == JsonToken.STRING) {
                        session.valueType(reader.nextString());
                    } else {
                        reader.skipValue();
                    }
                }
                case "stackType" -> {
                    if (reader.peek() == JsonToken.STRING) {
                        session.stackType(OfflineContent.familyOfStackType(reader.nextString()));
                    } else {
                        reader.skipValue();
                    }
                }
                case "tag" -> {
                    if (reader.peek() == JsonToken.STRING) {
                        tagFamilies.putIfAbsent(reader.nextString(), OfflineContent.ITEM_FAMILY);
                    } else {
                        reader.skipValue();
                    }
                }
                case "stack" -> prescanStack(reader, session);
                case "stacks" -> {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        prescanStack(reader, session);
                    }
                    reader.endArray();
                }
                default -> reader.skipValue();
            }
        }
        reader.endObject();
    }

    private static void prescanStack(JsonReader reader, OfflineContent.Session session) throws IOException {
        String type = null;
        String fluid = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("type") && reader.peek() == JsonToken.STRING) {
                type = reader.nextString();
            } else if (name.equals("fluid") && reader.peek() == JsonToken.STRING) {
                fluid = reader.nextString();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        if (type == null && fluid != null) {
            type = OfflineContent.FLUID_FAMILY;
        }
        if (type != null) {
            session.stackType(switch (type) {
                case "item" -> OfflineContent.ITEM_FAMILY;
                case "fluid" -> OfflineContent.FLUID_FAMILY;
                default -> type;
            });
        }
    }

    private static final class IdCanonicalizer {

        private final HashMap<String, String> map = new HashMap<>();

        String canonical(String id) {
            String existing = map.putIfAbsent(id, id);
            return existing != null ? existing : id;
        }
    }

    //endregion
}
