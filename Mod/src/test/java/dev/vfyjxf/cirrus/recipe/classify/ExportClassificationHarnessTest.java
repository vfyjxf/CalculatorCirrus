package dev.vfyjxf.cirrus.recipe.classify;

import com.google.gson.stream.JsonReader;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationResult;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassifyContent;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifier;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline analysis harness for the recipe classifier. Reads exported recipe
 * datasets (produced by the {@code feat/export-content-registry} exporter)
 * from a directory pointed to by the {@code cirrus.export.dir} system property
 * or the {@code CIRRUS_EXPORT_DIR} environment variable, and prints a per-pack
 * classification report: tag counts, exclusion ratio, producer fan-in
 * histogram before/after exclusion, top high-fan-in outputs, and coverage-loss
 * samples (outputs that lost all producers through exclusion).
 *
 * <p>The test is skipped when no export directory is configured; the export
 * data itself is never committed to the repository.
 */
class ExportClassificationHarnessTest {

    @Test
    void analyzeExports() throws IOException {
        Path exportDir = exportDir();
        Assumptions.assumeTrue(
            exportDir != null && Files.isDirectory(exportDir),
            "no export directory (set -Dcirrus.export.dir=... or CIRRUS_EXPORT_DIR)"
        );

        RecipeClassifier classifier = RecipeClassifier.createDefault();
        List<Path> packs;
        try (Stream<Path> stream = Files.list(exportDir)) {
            packs = stream.filter(Files::isDirectory).sorted().toList();
        }
        assertTrue(!packs.isEmpty(), "export directory has no packs");

        StringBuilder report = new StringBuilder();
        for (Path pack : packs) {
            Path recipesFile = findRecipesFile(pack);
            if (recipesFile == null) {
                continue;
            }
            analyzePack(classifier, pack.getFileName().toString(), recipesFile, report);
        }
        System.out.println(report);
    }

    private static @Nullable Path exportDir() {
        String property = System.getProperty("cirrus.export.dir");
        if (property == null || property.isBlank()) {
            property = System.getenv("CIRRUS_EXPORT_DIR");
        }
        return property == null || property.isBlank() ? null : Path.of(property);
    }

    private static @Nullable Path findRecipesFile(Path pack) throws IOException {
        try (Stream<Path> stream = Files.list(pack)) {
            return stream.filter(path -> path.getFileName().toString().startsWith("recipes-"))
                    .filter(path -> path.getFileName().toString().endsWith(".json")).findFirst().orElse(null);
        }
    }

    private static void analyzePack(RecipeClassifier classifier, String pack, Path recipesFile, StringBuilder report)
            throws IOException {
        long started = System.nanoTime();
        List<RecipeClassifyView> views = parseRecipes(recipesFile);
        long parsed = System.nanoTime();
        ClassificationResult result = classifier.classifyAll(views);
        long classified = System.nanoTime();

        BitSet excluded = result.recipesWithTag(ClassificationTags.junk);
        excluded.or(result.recipesWithTag(ClassificationTags.duplicate));

        Map<String, Integer> fanInBefore = fanIn(views, null);
        Map<String, Integer> fanInAfter = fanIn(views, excluded);

        report.append("\n=== ").append(pack).append(" ===\n");
        report.append(
            String.format(
                "recipes=%d parseMs=%d classifyMs=%d%n",
                views.size(),
                (parsed - started) / 1_000_000,
                (classified - parsed) / 1_000_000
            )
        );
        report.append(
            String.format(
                "excluded=%d (%.1f%%) kept=%d%n",
                excluded.cardinality(),
                100.0 * excluded.cardinality() / Math.max(1, views.size()),
                views.size() - excluded.cardinality()
            )
        );
        for (Map.Entry<String, Integer> entry : result.tagCounts().entrySet()) {
            report.append(String.format("  %-28s %d%n", entry.getKey(), entry.getValue()));
        }
        // per-tag samples for eyeballing over/under-tagging
        Map<String, Integer> sampleBudget = new HashMap<>();
        for (RecipeClassifyView view : views) {
            Set<String> tags = result.tagsOf(view.index());
            for (String tag : tags) {
                int left = sampleBudget.merge(tag, 1, Integer::sum);
                if (left <= 8) {
                    report.append(
                        String.format(
                            "    [%s] %s | %s | in=%s out=%s%n",
                            tag.substring(tag.indexOf(':') + 1),
                            view.systemId(),
                            view.recipeId(),
                            abbreviate(view.allInputIds()),
                            abbreviate(view.allOutputIds())
                        )
                    );
                }
            }
        }
        // systems feeding the top fan-in survivors
        report.append("  top fan-in survivor systems:\n");
        fanInAfter.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(8)
                .forEach(top -> {
                    Map<String, Integer> bySystem = new TreeMap<>();
                    for (RecipeClassifyView view : views) {
                        if (excluded.get(view.index()) || !view.allOutputIds().contains(top.getKey())) {
                            continue;
                        }
                        bySystem.merge(view.systemId(), 1, Integer::sum);
                    }
                    StringBuilder line = new StringBuilder("    ").append(top.getKey()).append(" <- ");
                    bySystem.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                            .limit(5)
                            .forEach(e -> line.append(e.getKey()).append('(').append(e.getValue()).append(") "));
                    report.append(line).append('\n');
                });
        report.append("  fan-in before: ").append(histogram(fanInBefore)).append('\n');
        report.append("  fan-in after : ").append(histogram(fanInAfter)).append('\n');
        report.append("  top after exclusion:\n");
        fanInAfter.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(25)
                .forEach(entry -> report.append(String.format("    %6d  %s%n", entry.getValue(), entry.getKey())));
        List<String> coverageLoss = coverageLoss(fanInBefore, fanInAfter);
        report.append("  coverage loss (outputs losing all producers): ").append(coverageLoss.size()).append('\n');
        coverageLoss.stream().limit(20).forEach(id -> report.append("    ").append(id).append('\n'));
    }

    private static Map<String, Integer> fanIn(List<RecipeClassifyView> views, @Nullable BitSet excluded) {
        Map<String, Integer> producers = new HashMap<>();
        for (RecipeClassifyView view : views) {
            if (excluded != null && excluded.get(view.index())) {
                continue;
            }
            for (String output : view.allOutputIds()) {
                producers.merge(output, 1, Integer::sum);
            }
        }
        return producers;
    }

    private static String histogram(Map<String, Integer> fanIn) {
        long one = 0, few = 0, some = 0, many = 0, huge = 0;
        for (int count : fanIn.values()) {
            if (count == 1) one++;
            else if (count <= 3) few++;
            else if (count <= 10) some++;
            else if (count <= 50) many++;
            else huge++;
        }
        return String.format("{1=%d, 2-3=%d, 4-10=%d, 11-50=%d, >50=%d}", one, few, some, many, huge);
    }

    private static List<String> coverageLoss(Map<String, Integer> before, Map<String, Integer> after) {
        List<String> lost = new ArrayList<>();
        for (String id : before.keySet()) {
            if (!after.containsKey(id)) {
                lost.add(id);
            }
        }
        return lost;
    }

    private static String abbreviate(Collection<String> ids) {
        if (ids.size() <= 3) {
            return ids.toString();
        }
        return "[" + String.join(", ", ids.stream().limit(3).toList()) + ", ...(" + ids.size() + ")]";
    }

    // region streaming JSON parsing (atm10's recipes file is ~700MB)

    private static List<RecipeClassifyView> parseRecipes(Path file) throws IOException {
        List<RecipeClassifyView> views = new ArrayList<>();
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(file))) {
            reader.beginObject();
            while (reader.hasNext()) {
                if (reader.nextName().equals("recipes")) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        views.add(parseRecipe(reader, views.size()));
                    }
                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }
        return views;
    }

    private static RecipeClassifyView parseRecipe(JsonReader reader, int index) throws IOException {
        String system = "";
        String id = "";
        List<String> machines = List.of();
        List<ClassifyContent> inputs = List.of();
        List<ClassifyContent> outputs = List.of();
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "system" -> system = reader.nextString();
                case "id" -> id = reader.nextString();
                case "machines" -> machines = parseStringArray(reader);
                case "inputs" -> inputs = parseContentArray(reader);
                case "outputs" -> outputs = parseContentArray(reader);
                default -> reader.skipValue();
            }
        }
        reader.endObject();
        return new RecipeClassifyView(index, system, id, machines, inputs, outputs);
    }

    private static List<String> parseStringArray(JsonReader reader) throws IOException {
        List<String> values = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
            values.add(reader.nextString());
        }
        reader.endArray();
        return values;
    }

    private static List<ClassifyContent> parseContentArray(JsonReader reader) throws IOException {
        List<ClassifyContent> contents = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
            contents.add(parseContent(reader));
        }
        reader.endArray();
        return contents;
    }

    private static ClassifyContent parseContent(JsonReader reader) throws IOException {
        String kind = "empty";
        String tagId = null;
        double amount = 1;
        double chance = 1.0;
        Set<String> ids = new LinkedHashSet<>();
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "kind" -> kind = reader.nextString();
                case "amount" -> amount = reader.nextDouble();
                case "chance" -> chance = reader.nextDouble();
                case "tag" -> tagId = reader.nextString();
                case "values" -> ids.addAll(parseStringArray(reader));
                case "stack" -> {
                    String id = parseStackId(reader);
                    if (id != null) {
                        ids.add(id);
                    }
                }
                case "stacks" -> {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        String id = parseStackId(reader);
                        if (id != null) {
                            ids.add(id);
                        }
                    }
                    reader.endArray();
                }
                default -> reader.skipValue();
            }
        }
        reader.endObject();
        ClassifyContent.Kind contentKind = switch (kind) {
            case "stack" -> ClassifyContent.Kind.stack;
            case "tag" -> ClassifyContent.Kind.tag;
            case "any" -> ClassifyContent.Kind.any;
            case "typedAny" -> ClassifyContent.Kind.typedAny;
            case "value" -> ClassifyContent.Kind.value;
            case "complex" -> ClassifyContent.Kind.complex;
            default -> ClassifyContent.Kind.empty;
        };
        return new ClassifyContent(contentKind, ids, tagId, amount, chance);
    }

    private static @Nullable String parseStackId(JsonReader reader) throws IOException {
        String id = null;
        String fluid = null;
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "id" -> id = reader.nextString();
                case "fluid" -> fluid = reader.nextString();
                default -> reader.skipValue();
            }
        }
        reader.endObject();
        return id != null ? id : fluid;
    }

    // endregion
}
