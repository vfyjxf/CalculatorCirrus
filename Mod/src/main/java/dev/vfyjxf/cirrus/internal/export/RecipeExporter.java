package dev.vfyjxf.cirrus.internal.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.internal.bind.TypeAdapters;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.JsonOps;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.content.ComplexContent;
import dev.vfyjxf.cirrus.api.recipe.content.EmptyRecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.content.TypedStackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClausedContent;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveError;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.VanillaRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.jei.JeiCategoryRecipeSystem;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exports all recipes registered in Cirrus's {@link RecipeApi} as a single JSON document.
 *
 * <p>System selection follows the "built-in first" rule: vanilla systems backed by the
 * {@link net.minecraft.world.item.crafting.RecipeManager} always win for the JEI recipe types
 * they cover. A {@link JeiCategoryRecipeSystem} is only exported when no built-in system
 * covers its recipe type, so machine recipes that exist only as JEI categories are still
 * included. Custom systems registered by other mods are exported as-is.
 */
public final class RecipeExporter {

    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus Recipe Export");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final DateTimeFormatter fileTimestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public static final int FORMAT_VERSION = 1;

    private RecipeExporter() {
    }

    //region system selection

    /**
     * Select the systems to export. Built-in vanilla systems take priority; a JEI category
     * system is skipped when a built-in system already covers its recipe type.
     *
     * @param filter optional set of system ids to keep; {@code null} or empty exports everything
     */
    public static List<RecipeSystem<?>> selectSystems(RecipeApi api, @Nullable Set<String> filter) {
        List<RecipeSystem<?>> all = new ArrayList<>();
        api.allSystems().forEach(all::add);

        Set<ResourceLocation> coveredJeiTypes = new HashSet<>();
        List<RecipeSystem<?>> chosen = new ArrayList<>();

        for (RecipeSystem<?> system : all) {
            if (system instanceof VanillaRecipeSystem<?>) {
                api.getJeiRecipeTypeForSystem(system.systemId()).forEach(type -> coveredJeiTypes.add(type.getUid()));
                chosen.add(system);
            }
        }
        for (RecipeSystem<?> system : all) {
            if (system instanceof VanillaRecipeSystem<?>) {
                continue;
            }
            if (system instanceof JeiCategoryRecipeSystem<?> jeiSystem
                && coveredJeiTypes.contains(jeiSystem.recipeType().getUid())) {
                continue;
            }
            chosen.add(system);
        }

        if (filter != null && !filter.isEmpty()) {
            chosen.removeIf(system -> !filter.contains(system.systemId().toString()));
        }
        return chosen;
    }

    //endregion

    //region export

    /**
     * Exports the selected recipes to {@code recipes-<timestamp>.json} inside the given directory.
     * Recipes that failed to resolve are not mixed into the main document; their ids and reasons
     * are collected into a separate {@code skipped-<timestamp>.json} file.
     *
     * <p>Recipes are resolved and written to disk one at a time through a streaming
     * {@link JsonWriter}, so peak memory stays proportional to a single recipe instead of the
     * whole pack. Each document is first written to a {@code .tmp} file and atomically renamed
     * on success, so a failure in the middle never leaves a truncated export behind.
     */
    public static ExportResult export(Path outputDirectory, @Nullable Set<String> filter) throws IOException {
        RecipeApi api = RecipeApi.instance();
        List<RecipeSystem<?>> systems = selectSystems(api, filter);
        if (systems.isEmpty()) {
            throw new IOException("No recipe systems to export");
        }

        Files.createDirectories(outputDirectory);
        String timestamp = LocalDateTime.now().format(fileTimestamp);
        Path output = outputDirectory.resolve("recipes-" + timestamp + ".json");
        Path skippedOutput = outputDirectory.resolve("skipped-" + timestamp + ".json");
        Path tmp = outputDirectory.resolve(output.getFileName() + ".tmp");
        List<SystemStats> statsList = new ArrayList<>(systems.size());

        int totalRecipes = 0;
        int totalSkipped = 0;
        EnumMap<ResolveError, Integer> totalSkipReasons = new EnumMap<>(ResolveError.class);
        try (Writer fileWriter = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8);
             JsonWriter writer = GSON.newJsonWriter(fileWriter)) {
            writer.beginObject();
            writer.name("formatVersion").value(FORMAT_VERSION);
            writer.name("generatedAt").value(LocalDateTime.now().toString());
            writer.name("minecraftVersion").value(SharedConstants.getCurrentVersion().getName());

            writer.name("recipes");
            writer.beginArray();
            for (RecipeSystem<?> system : systems) {
                SystemStats stats = exportSystem(api, system, writer, totalSkipReasons);
                statsList.add(stats);
                totalRecipes += stats.recipeCount;
                totalSkipped += stats.skipped;
                logger.info("Export system {} recipes={} skipped={}", system.systemId(), stats.recipeCount, stats.skipped);
            }
            writer.endArray();

            writer.name("systems");
            writer.beginArray();
            for (SystemStats stats : statsList) {
                TypeAdapters.JSON_ELEMENT.write(writer, stats.summary);
            }
            writer.endArray();

            writer.name("recipeCount").value(totalRecipes);
            writer.name("skippedCount").value(totalSkipped);
            writer.endObject();
        }
        moveIntoPlace(tmp, output);
        writeSkippedFile(outputDirectory, timestamp, statsList, totalRecipes, totalSkipped, totalSkipReasons);
        return new ExportResult(output, skippedOutput, systems.size(), totalRecipes, totalSkipped, totalSkipReasons);
    }

    /**
     * Writes {@code skipped-<timestamp>.json}: per-reason counts plus, for every system that
     * skipped at least one recipe, the list of skipped recipe ids grouped by reason.
     */
    private static void writeSkippedFile(
        Path outputDirectory,
        String timestamp,
        List<SystemStats> statsList,
        int totalRecipes,
        int totalSkipped,
        Map<ResolveError, Integer> totalSkipReasons
    ) throws IOException {
        Path output = outputDirectory.resolve("skipped-" + timestamp + ".json");
        Path tmp = outputDirectory.resolve(output.getFileName() + ".tmp");
        try (Writer fileWriter = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8);
             JsonWriter writer = GSON.newJsonWriter(fileWriter)) {
            writer.beginObject();
            writer.name("formatVersion").value(FORMAT_VERSION);
            writer.name("generatedAt").value(LocalDateTime.now().toString());
            writer.name("minecraftVersion").value(SharedConstants.getCurrentVersion().getName());
            writer.name("recipeCount").value(totalRecipes);
            writer.name("skippedCount").value(totalSkipped);
            writer.name("skippedReasons");
            writer.beginObject();
            for (Map.Entry<ResolveError, Integer> entry : totalSkipReasons.entrySet()) {
                writer.name(entry.getKey().name()).value(entry.getValue());
            }
            writer.endObject();

            writer.name("systems");
            writer.beginArray();
            for (SystemStats stats : statsList) {
                if (stats.skipped == 0) {
                    continue;
                }
                writer.beginObject();
                writer.name("id").value(stats.summary.get("id").getAsString());
                writer.name("skipped").value(stats.skipped);
                writer.name("reasons");
                writer.beginObject();
                for (Map.Entry<ResolveError, Integer> entry : stats.skipReasons.entrySet()) {
                    writer.name(entry.getKey().name()).value(entry.getValue());
                }
                writer.endObject();
                writer.name("recipes");
                writer.beginArray();
                for (SkipEntry skip : stats.skippedEntries) {
                    writer.value(skip.recipeId);
                }
                writer.endArray();
                writer.endObject();
            }
            writer.endArray();
            writer.endObject();
        }
        moveIntoPlace(tmp, output);
    }

    private static void moveIntoPlace(Path tmp, Path output) throws IOException {
        try {
            Files.move(tmp, output, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(tmp, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static SystemStats exportSystem(
        RecipeApi api,
        RecipeSystem<?> system,
        JsonWriter writer,
        Map<ResolveError, Integer> totalSkipReasons
    ) throws IOException {
        JsonObject summary = new JsonObject();
        summary.addProperty("id", system.systemId().toString());
        summary.addProperty("source", sourceOf(system));
        if (system instanceof JeiCategoryRecipeSystem<?> jeiSystem) {
            summary.addProperty("recipeType", jeiSystem.recipeType().getUid().toString());
        }
        JsonArray jeiRecipeTypes = new JsonArray();
        api.getJeiRecipeTypeForSystem(system.systemId())
            .forEach(type -> jeiRecipeTypes.add(type.getUid().toString()));
        summary.add("jeiRecipeTypes", jeiRecipeTypes);
        JsonArray machines = machinesJson(api, system);
        summary.add("machines", machines);

        int count = 0;
        int skipped = 0;
        EnumMap<ResolveError, Integer> skipReasons = new EnumMap<>(ResolveError.class);
        List<SkipEntry> skippedEntries = new ArrayList<>();
        for (Object recipe : system.allRecipes()) {
            JsonObject entry = toRecipeJson(api, system, recipe, machines, skipReasons, skippedEntries);
            if (entry == null) {
                skipped++;
                continue;
            }
            TypeAdapters.JSON_ELEMENT.write(writer, entry);
            count++;
            if (count % 500 == 0) {
                logger.info("Export system {} progress {} (skipped {})", system.systemId(), count, skipped);
            }
        }
        summary.addProperty("recipeCount", count);
        summary.addProperty("skipped", skipped);
        for (Map.Entry<ResolveError, Integer> entry : skipReasons.entrySet()) {
            totalSkipReasons.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        return new SystemStats(summary, count, skipped, skipReasons, skippedEntries);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static @Nullable JsonObject toRecipeJson(
        RecipeApi api,
        RecipeSystem system,
        Object recipe,
        JsonArray machines,
        Map<ResolveError, Integer> skipReasons,
        List<SkipEntry> skippedEntries
    ) {
        ResolveResult result = api.resolve(ResolveContext.empty(), system, recipe);
        if (!result.resolved()) {
            ResourceLocation id = safeId(system, recipe);
            skipReasons.merge(result.error(), 1, Integer::sum);
            skippedEntries.add(new SkipEntry(id == null ? "" : id.toString(), result.error()));
            logger.debug("Skip recipe {} in {}: {}", id, system.systemId(), result.error());
            return null;
        }
        RecipeProjection projection = result.projection();
        ResourceLocation id = safeId(system, recipe);
        JsonObject entry = new JsonObject();
        entry.addProperty("system", system.systemId().toString());
        entry.addProperty("source", sourceOf(system));
        if (system instanceof JeiCategoryRecipeSystem<?> jeiSystem) {
            entry.addProperty("recipeType", jeiSystem.recipeType().getUid().toString());
        }
        entry.addProperty("id", id == null ? "" : id.toString());
        entry.add("machines", machines.deepCopy());
        entry.add("inputs", inputsJson(projection));
        entry.add("outputs", outputsJson(projection));
        return entry;
    }

    private static JsonArray inputsJson(RecipeProjection projection) {
        JsonArray array = new JsonArray();
        for (ClausedContent<InputContent> claused : projection.inputs()) {
            addContent(array, claused.content());
        }
        return array;
    }

    private static JsonArray outputsJson(RecipeProjection projection) {
        JsonArray array = new JsonArray();
        for (ClausedContent<OutputContent> claused : projection.outputs()) {
            addContent(array, claused.content());
        }
        return array;
    }

    private static void addContent(JsonArray array, RecipeContent content) {
        if (content.isEmpty()) {
            return;
        }
        JsonObject json = contentJson(content);
        if (json != null) {
            array.add(json);
        }
    }

    //endregion

    //region content serialization

    private static @Nullable JsonObject contentJson(RecipeContent content) {
        return switch (content) {
            case EmptyRecipeContent ignored -> null;
            case StackContent<?, ?> stack -> stackJson(stack);
            case TagStackContent<?> tag -> tagJson(tag);
            case StackListContent list -> stackListJson(list.stacks(), list.amount(), list.chance(), "any");
            case TypedStackListContent<?, ?> typedList -> {
                JsonObject json = stackListJson(typedList.stacks(), typedList.amount(), typedList.chance(), "typedAny");
                json.addProperty("stackType", typedList.type().typeId().toString());
                yield json;
            }
            case ValueContent<?> value -> valueJson(value);
            case ComplexContent complex -> complexJson(complex);
            default -> null;
        };
    }

    private static JsonObject stackJson(StackContent<?, ?> stackContent) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", "stack");
        json.add("stack", stackToJson(stackContent.content()));
        json.addProperty("amount", stackContent.amount());
        json.addProperty("chance", stackContent.chance());
        json.addProperty("matchStrategy", matchStrategyName(stackContent.matchStrategy()));
        return json;
    }

    private static JsonObject tagJson(TagStackContent<?> tag) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", "tag");
        json.addProperty("tag", tag.tagKey().location().toString());
        json.addProperty("amount", tag.amount());
        json.addProperty("chance", tag.chance());
        JsonArray values = new JsonArray();
        for (Object value : tag.values()) {
            values.add(registryNameOf(value));
        }
        json.add("values", values);
        return json;
    }

    private static JsonObject stackListJson(Iterable<? extends ContentStack<?, ?>> stacks, long amount, float chance, String kind) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", kind);
        json.addProperty("amount", amount);
        json.addProperty("chance", chance);
        JsonArray stackArray = new JsonArray();
        for (ContentStack<?, ?> stack : stacks) {
            stackArray.add(stackToJson(stack));
        }
        json.add("stacks", stackArray);
        return json;
    }

    private static JsonObject valueJson(ValueContent<?> value) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", "value");
        json.addProperty("valueType", value.type().typeId().toString());
        json.addProperty("value", value.content().value());
        json.addProperty("chance", value.chance());
        return json;
    }

    private static JsonObject complexJson(ComplexContent complex) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", "complex");
        json.addProperty("amount", complex.amount());
        json.addProperty("chance", complex.chance());
        json.addProperty("implementation", complex.getClass().getName());
        return json;
    }

    private static JsonObject stackToJson(ContentStack<?, ?> stack) {
        if (stack.isEmpty()) {
            return new JsonObject();
        }
        if (stack.isItem()) {
            JsonObject json = new JsonObject();
            json.addProperty("type", "item");
            json.addProperty("id", BuiltInRegistries.ITEM.getKey((Item) stack.content()).toString());
            json.addProperty("count", stack.amount());
            addComponents(json, stack.componentsPatch());
            return json;
        }
        if (stack.isFluid()) {
            JsonObject json = new JsonObject();
            json.addProperty("type", "fluid");
            json.addProperty("fluid", BuiltInRegistries.FLUID.getKey((Fluid) stack.content()).toString());
            json.addProperty("amount", stack.amount());
            return json;
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", stack.type().typeId().toString());
        json.addProperty("amount", stack.amount());
        json.addProperty("value", String.valueOf(stack.value()));
        return json;
    }

    private static void addComponents(JsonObject json, @Nullable DataComponentPatch patch) {
        if (patch == null || patch.isEmpty()) {
            return;
        }
        try {
            var encoded = DataComponentPatch.CODEC.encodeStart(JsonOps.INSTANCE, patch).result();
            if (encoded.isPresent()) {
                json.add("components", encoded.get());
            }
        } catch (RuntimeException ignored) {
            // components are best-effort metadata; a packable stack still exports without them
        }
    }

    private static String registryNameOf(Object value) {
        if (value instanceof Item item) {
            return BuiltInRegistries.ITEM.getKey(item).toString();
        }
        if (value instanceof Fluid fluid) {
            return BuiltInRegistries.FLUID.getKey(fluid).toString();
        }
        return String.valueOf(value);
    }

    private static String matchStrategyName(MatchStrategy strategy) {
        if (strategy == MatchStrategy.identity) return "identity";
        if (strategy == MatchStrategy.amount) return "amount";
        if (strategy == MatchStrategy.identityAndComponent) return "identityAndComponent";
        if (strategy == MatchStrategy.identityAndAmount) return "identityAndAmount";
        if (strategy == MatchStrategy.all) return "all";
        if (strategy == MatchStrategy.none) return "none";
        StringBuilder name = new StringBuilder();
        if (strategy.withIdentity()) name.append("identity");
        if (strategy.withAmount()) name.append(name.isEmpty() ? "" : "+").append("amount");
        if (strategy.withComponent()) name.append(name.isEmpty() ? "" : "+").append("component");
        return name.isEmpty() ? "custom" : name.toString();
    }

    //endregion

    //region system metadata

    private static JsonArray machinesJson(RecipeApi api, RecipeSystem<?> system) {
        JsonArray machines = new JsonArray();
        for (MachineEntry machine : api.getMachines(system.systemId(), true)) {
            if (machine.isEmpty()) {
                continue;
            }
            ItemStack stack = machine.toItemStack();
            if (stack.isEmpty()) {
                continue;
            }
            machines.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        return machines;
    }

    private static String sourceOf(RecipeSystem<?> system) {
        if (system instanceof VanillaRecipeSystem<?>) {
            return "vanilla";
        }
        if (system instanceof JeiCategoryRecipeSystem<?>) {
            return "jei";
        }
        return "custom";
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static @Nullable ResourceLocation safeId(RecipeSystem system, Object recipe) {
        try {
            return system.getId(recipe);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    //endregion

    //region diagnostics

    /**
     * Renders one line per selected system for the {@code /cirrus recipes list} command.
     */
    public static List<String> listSystems(@Nullable Set<String> filter) {
        RecipeApi api = RecipeApi.instance();
        List<String> lines = new ArrayList<>();
        for (RecipeSystem<?> system : selectSystems(api, filter)) {
            StringBuilder line = new StringBuilder();
            line.append(system.systemId());
            line.append("  [").append(sourceOf(system)).append(']');
            if (system instanceof JeiCategoryRecipeSystem<?> jeiSystem) {
                line.append("  recipeType=").append(jeiSystem.recipeType().getUid());
            }
            line.append("  recipes=");
            int count = -1;
            try {
                count = system.allRecipes().size();
            } catch (RuntimeException | LinkageError ignored) {
                // recipe lookup unavailable (e.g. JEI not loaded); report unknown count
            }
            line.append(count);
            lines.add(line.toString());
        }
        return lines;
    }

    //endregion

    public record ExportResult(
        Path file,
        Path skippedFile,
        int systemCount,
        int recipeCount,
        int skippedCount,
        Map<ResolveError, Integer> skipReasons
    ) {
    }

    private record SystemStats(
        JsonObject summary,
        int recipeCount,
        int skipped,
        EnumMap<ResolveError, Integer> skipReasons,
        List<SkipEntry> skippedEntries
    ) {
    }

    private record SkipEntry(String recipeId, ResolveError error) {
    }
}
