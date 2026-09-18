package dev.vfyjxf.cirrus.recipe.chainexport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationContext;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationRule;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationSink;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifier;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.ColonyViewMirrorRule;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.ExchangeStructuralRule;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.GenerationStructuralRule;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.MassSinkStructuralRule;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.RecolorStructuralRule;
import dev.vfyjxf.cirrus.api.recipe.classify.structural.RecyclingStructuralRule;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.Stock;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHeuristics;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleDefinitions;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleDefinition;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.RuleProfile;
import dev.vfyjxf.cirrus.api.recipe.routing.service.LoopPolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.service.PlannerMode;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingBudget;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingMetrics;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.api.recipe.rule.CatalogRuleInput;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleAction;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleCondition;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleSpec;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cirrus.recipe.routing.topology.ConeStats;
import org.eclipse.collections.api.factory.Lists;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Naive-Bayes junk-heuristic experiment driver (offline, real pack dumps).
 *
 * <p>Two subcommands:
 * <ul>
 *     <li>{@code features} — loads each pack with {@link ExportPackLoader.LoadOptions#keepJunk()}
 *     and evaluates every builtin catalog rule (JSON + structural) individually per recipe,
 *     producing per-recipe binary feature vectors plus rule-derived weak labels
 *     (junk / middle / useful). Output feeds NB training (Python side).</li>
 *     <li>{@code bench} — runs the real planner over the same target set in three modes:
 *     A (no junk knowledge, full graph), B (current behavior: junk/duplicate hard-filtered
 *     graph), C (full graph + NB log-odds injected as per-recipe routing-hint penalties via
 *     {@link RecipeRoutingHeuristics}; candidates are only re-ordered, never removed).</li>
 * </ul>
 *
 * <p>Run on the test runtime classpath (see {@code chain-export.sh}); same heap sizing applies.
 */
public final class BayesExperimentMain {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: BayesExperimentMain features|--export-dir ../exports --out ../experiments/naive-bayes --packs atm10,...");
            System.err.println("       BayesExperimentMain bench   [--model model.json] [--targets N] [--scale 66] [--cap 15] ...same flags");
            System.exit(2);
        }
        Map<String, String> flags = parseFlags(args);
        String command = args[0];
        switch (command) {
            case "features" -> runFeatures(flags);
            case "bench" -> runBench(flags);
            default -> {
                System.err.println("unknown command: " + command);
                System.exit(2);
            }
        }
    }

    private static Map<String, String> parseFlags(String[] args) {
        Map<String, String> flags = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i].substring(2);
                String value = i + 1 < args.length && !args[i + 1].startsWith("--") ? args[++i] : "true";
                flags.put(key, value);
            }
        }
        return flags;
    }

    private static String flag(Map<String, String> flags, String key, String fallback) {
        String value = flags.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static List<Path> discoverPacks(Path exportDir, String filter) throws IOException {
        List<String> wanted = filter == null || filter.isBlank()
            ? null
            : Stream.of(filter.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        try (Stream<Path> stream = Files.list(exportDir)) {
            List<Path> packs = new ArrayList<>();
            stream.filter(Files::isDirectory).sorted().forEach(path -> {
                String name = path.getFileName().toString();
                if (wanted == null || wanted.contains(name)) {
                    packs.add(path);
                }
            });
            return packs;
        }
    }

    //region feature extraction

    /** Production disposition of a rule: what the boolean system does when it fires. */
    enum Disposition {
        exclude,
        lastResort,
        discouraged,
        markOnly
    }

    record FeatureInfo(String id, Disposition disposition) {
    }

    /** Per-recipe feature bits (one long, ≤64 features) plus weak label. */
    record PackFeatures(
        List<FeatureInfo> catalog,
        Map<Integer, Long> firedBits,
        Map<Integer, String> weakLabel
    ) {
        boolean fired(long bits, int index) {
            return (bits & (1L << index)) != 0;
        }
    }

    private static List<FeatureInfo> featureCatalog(List<RuleSpec> jsonSpecs) {
        List<FeatureInfo> out = new ArrayList<>();
        Map<String, Disposition> relationDispositions = relationDispositions(jsonSpecs);
        for (RuleSpec spec : jsonSpecs) {
            // layered files: detection rules only emit cirrus:feat/<name> tags and their
            // disposition lives in the relation layer; flat files carry it in the actions
            Disposition disposition = STRUCTURAL_DISPOSITION_OVERRIDES.containsKey(spec.id().toString())
                ? STRUCTURAL_DISPOSITION_OVERRIDES.get(spec.id().toString())
                : relationDispositions.getOrDefault(spec.id().toString(), dispositionOf(spec));
            out.add(new FeatureInfo(spec.id().toString(), disposition));
        }
        out.add(new FeatureInfo(ExchangeStructuralRule.ID, Disposition.discouraged));
        out.add(new FeatureInfo(RecolorStructuralRule.ID, Disposition.discouraged));
        out.add(new FeatureInfo(RecyclingStructuralRule.ID, Disposition.lastResort));
        out.add(new FeatureInfo(GenerationStructuralRule.ID, Disposition.markOnly));
        out.add(new FeatureInfo(MassSinkStructuralRule.ID, Disposition.lastResort));
        out.add(new FeatureInfo(ColonyViewMirrorRule.ID, Disposition.exclude));
        return out;
    }

    /** Structural-layer disposition overrides the layered file cannot express. */
    private static final Map<String, Disposition> STRUCTURAL_DISPOSITION_OVERRIDES = Map.of(
        // sole colony producers are now the soft case (mirror handled by the
        // colony_view_mirror feature above)
        "cirrus:colony_views", Disposition.discouraged
    );

    /**
     * Feature id -> disposition derived from relation rules: rules whose conditions
     * reference {@code cirrus:feat/<name>} tags carry the disposition for the
     * corresponding detection rule ({@code cirrus:<name>}).
     */
    private static Map<String, Disposition> relationDispositions(List<RuleSpec> jsonSpecs) {
        Map<String, String> featTagToFeatureId = new HashMap<>();
        for (RuleSpec spec : jsonSpecs) {
            for (RuleAction action : spec.actions()) {
                if (action instanceof RuleAction.Tag tag && tag.tag().startsWith(FEATURE_TAG_PREFIX)) {
                    featTagToFeatureId.put(tag.tag(), spec.id().toString());
                }
            }
        }
        Map<String, Disposition> out = new HashMap<>();
        for (RuleSpec spec : jsonSpecs) {
            for (String featTag : referencedFeatureTags(spec.condition())) {
                String featureId = featTagToFeatureId.get(featTag);
                if (featureId != null) {
                    out.put(featureId, dispositionOf(spec));
                }
            }
        }
        return out;
    }

    private static final String FEATURE_TAG_PREFIX = "cirrus:feat/";

    private static void collectFeatureTags(RuleCondition condition, List<String> into) {
        if (condition instanceof RuleCondition.ClassificationTag tag) {
            if (tag.tag().startsWith(FEATURE_TAG_PREFIX)) {
                into.add(tag.tag());
            }
        } else if (condition instanceof RuleCondition.AllOf all) {
            for (RuleCondition child : all.conditions()) {
                collectFeatureTags(child, into);
            }
        } else if (condition instanceof RuleCondition.AnyOf any) {
            for (RuleCondition child : any.conditions()) {
                collectFeatureTags(child, into);
            }
        } else if (condition instanceof RuleCondition.Not not) {
            collectFeatureTags(not.condition(), into);
        }
    }

    private static List<String> referencedFeatureTags(RuleCondition condition) {
        List<String> into = new ArrayList<>();
        collectFeatureTags(condition, into);
        return into;
    }

    private static Disposition dispositionOf(RuleSpec spec) {
        Disposition found = Disposition.markOnly;
        for (RuleAction action : spec.actions()) {
            if (action instanceof RuleAction.Tag tag
                && (ClassificationTags.JUNK.equals(tag.tag()) || ClassificationTags.DUPLICATE.equals(tag.tag()))) {
                return Disposition.exclude;
            }
            if (action instanceof RuleAction.Hint hint) {
                if (hint.policy() == dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeUsePolicy.lastResort) {
                    return Disposition.lastResort;
                }
                if (hint.policy() == dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeUsePolicy.discouraged) {
                    found = Disposition.discouraged;
                }
            }
        }
        return found;
    }

    /** Package-visible alias for the V2 bench harness. */
    static PackFeatures extractFeaturesPublic(ExportPackLoader.LoadedPack pack) {
        return extractFeatures(pack);
    }

    private static PackFeatures extractFeatures(ExportPackLoader.LoadedPack pack) {
        List<RuleSpec> specs = RecipeClassifier.loadBuiltinRuleSpecs();
        List<FeatureInfo> catalog = featureCatalog(specs);
        int jsonCount = specs.size();
        ClassificationRule[] structuralLocal = {            ExchangeStructuralRule.instance,
            RecolorStructuralRule.instance,
            RecyclingStructuralRule.instance,
            GenerationStructuralRule.instance
        };

        BitSet kept = new BitSet();
        for (ExportPackLoader.ExportRecipe recipe : pack.recipes()) {
            kept.set(recipe.index);
        }

        ClassificationContext context = ClassificationContext.of(pack.views());
        BitSet massSink = new BitSet();
        MassSinkStructuralRule.create().classifyAll(context, (index, tag) -> massSink.set(index));
        // colony views: mirror (duplicate) sets the feature bit; sole producers only
        // soften the base colony_views feature (discouraged) — they must stay reachable
        BitSet colonyMirror = new BitSet();
        for (RecipeClassifyView view : pack.views()) {
            String[] firedTag = {null};
            ColonyViewMirrorRule.instance.classify(view, context, tag -> firedTag[0] = tag);
            if (ClassificationTags.DUPLICATE.equals(firedTag[0])) {
                colonyMirror.set(view.index());
            }
        }

        Map<Integer, Long> firedBits = new HashMap<>();
        Map<Integer, String> weakLabel = new HashMap<>();
        for (RecipeClassifyView view : pack.views()) {
            if (!kept.get(view.index())) {
                continue;
            }
            long bits = 0L;
            CatalogRuleInput input = new CatalogRuleInput(view);
            for (int i = 0; i < jsonCount; i++) {
                RuleSpec spec = specs.get(i);
                if (spec.enabled() && spec.condition().matches(input)) {
                    bits |= 1L << i;
                }
            }
            for (int s = 0; s < structuralLocal.length; s++) {
                boolean[] hit = {false};
                ClassificationSink sink = tag -> hit[0] = true;
                structuralLocal[s].classify(view, context, sink);
                if (hit[0]) {
                    bits |= 1L << (jsonCount + s);
                }
            }
            if (massSink.get(view.index())) {
                bits |= 1L << (jsonCount + structuralLocal.length);
            }
            if (colonyMirror.get(view.index())) {
                bits |= 1L << (jsonCount + structuralLocal.length + 1);
            }

            boolean anyJunk = false;
            boolean anyOther = false;
            for (int i = 0; i < catalog.size(); i++) {
                if ((bits & (1L << i)) == 0) {
                    continue;
                }
                Disposition disposition = catalog.get(i).disposition();
                if (disposition == Disposition.exclude || disposition == Disposition.lastResort) {
                    anyJunk = true;
                } else {
                    anyOther = true;
                }
            }
            firedBits.put(view.index(), bits);
            weakLabel.put(view.index(), anyJunk ? "junk" : anyOther ? "middle" : "useful");
        }
        return new PackFeatures(catalog, firedBits, weakLabel);
    }

    //endregion

    //region features subcommand

    private static void runFeatures(Map<String, String> flags) throws Exception {
        Path exportDir = Path.of(flag(flags, "export-dir", "../exports"));
        Path outDir = Path.of(flag(flags, "out", "../experiments/naive-bayes")).resolve("features");
        List<Path> packs = discoverPacks(exportDir, flag(flags, "packs", ""));
        if (packs.isEmpty()) {
            System.err.println("no packs found under " + exportDir);
            System.exit(2);
        }
        Files.createDirectories(outDir);

        for (Path packDir : packs) {
            String name = packDir.getFileName().toString();
            long start = System.nanoTime();
            OfflineContent.Session session = OfflineContent.newSession();
            ExportPackLoader.LoadedPack pack = ExportPackLoader.load(packDir, session, new ExportPackLoader.LoadOptions(true));
            PackFeatures features = extractFeatures(pack);

            Map<Integer, ExportPackLoader.ExportRecipe> byIndex = new HashMap<>();
            for (ExportPackLoader.ExportRecipe recipe : pack.recipes()) {
                byIndex.put(recipe.index, recipe);
            }

            Path jsonl = outDir.resolve(name + ".jsonl");
            try (BufferedWriter writer = Files.newBufferedWriter(jsonl, StandardCharsets.UTF_8)) {
                for (Map.Entry<Integer, Long> entry : new TreeMap<>(features.firedBits()).entrySet()) {
                    ExportPackLoader.ExportRecipe recipe = byIndex.get(entry.getKey());
                    if (recipe == null) {
                        continue;
                    }
                    JsonObject line = new JsonObject();
                    line.addProperty("i", entry.getKey());
                    line.addProperty("id", recipe.recipeId);
                    line.addProperty("sys", recipe.systemId);
                    JsonArray fired = new JsonArray();
                    long bits = entry.getValue();
                    for (int i = 0; i < features.catalog().size(); i++) {
                        if (features.fired(bits, i)) {
                            fired.add(features.catalog().get(i).id());
                        }
                    }
                    line.add("f", fired);
                    line.addProperty("wl", features.weakLabel().get(entry.getKey()));
                    JsonObject io = new JsonObject();
                    io.add("in", slotSummaries(recipe.inputSpecs));
                    io.add("out", slotSummaries(recipe.outputSpecs));
                    line.add("io", io);
                    JsonArray outs = new JsonArray();
                    for (ExportPackLoader.SlotSpec spec : recipe.outputSpecs) {
                        if (spec.kind == ExportPackLoader.SlotSpec.Kind.stack && spec.tagId == null) {
                            outs.add(spec.displayId);
                        } else if (spec.tagId != null) {
                            for (String member : spec.memberIds) {
                                outs.add(member);
                            }
                        }
                        if (outs.size() > 12) {
                            break;
                        }
                    }
                    line.add("outs", outs);
                    writer.write(GSON.toJson(line));
                    writer.write('\n');
                }
            }

            // stats: label counts + per-feature firing counts and label cross counts
            long[] labelCounts = new long[3];
            long[] firedCounts = new long[features.catalog().size()];
            long[][] byLabel = new long[features.catalog().size()][3];
            for (Map.Entry<Integer, Long> entry : features.firedBits().entrySet()) {
                String label = features.weakLabel().get(entry.getKey());
                int li = "junk".equals(label) ? 0 : "middle".equals(label) ? 1 : 2;
                labelCounts[li]++;
                long bits = entry.getValue();
                for (int i = 0; i < features.catalog().size(); i++) {
                    if (features.fired(bits, i)) {
                        firedCounts[i]++;
                        byLabel[i][li]++;
                    }
                }
            }
            JsonObject stats = new JsonObject();
            stats.addProperty("pack", name);
            stats.addProperty("totalRecipes", pack.totalRecipes());
            stats.addProperty("keptRecipes", pack.keptRecipes());
            stats.addProperty("parseMs", pack.parseMs());
            stats.addProperty("classifyMs", pack.classifyMs());
            stats.addProperty("labeledJunk", labelCounts[0]);
            stats.addProperty("labeledMiddle", labelCounts[1]);
            stats.addProperty("labeledUseful", labelCounts[2]);
            JsonArray featureStats = new JsonArray();
            for (int i = 0; i < features.catalog().size(); i++) {
                JsonObject fs = new JsonObject();
                fs.addProperty("id", features.catalog().get(i).id());
                fs.addProperty("disposition", features.catalog().get(i).disposition().name());
                fs.addProperty("fired", firedCounts[i]);
                fs.addProperty("firedJunk", byLabel[i][0]);
                fs.addProperty("firedMiddle", byLabel[i][1]);
                fs.addProperty("firedUseful", byLabel[i][2]);
                featureStats.add(fs);
            }
            stats.add("features", featureStats);
            Files.writeString(outDir.resolve(name + ".stats.json"), GSON.toJson(stats));

            // shared catalog description (same for every pack; write once)
            JsonObject catalogJson = new JsonObject();
            JsonArray catalogArray = new JsonArray();
            for (FeatureInfo info : features.catalog()) {
                JsonObject f = new JsonObject();
                f.addProperty("id", info.id());
                f.addProperty("disposition", info.disposition().name());
                catalogArray.add(f);
            }
            catalogJson.add("features", catalogArray);
            Files.writeString(outDir.resolve("feature-catalog.json"), GSON.toJson(catalogJson));

            System.out.printf("[features] %-30s recipes=%d junk=%d middle=%d useful=%d elapsedMs=%d%n",
                name, features.firedBits().size(), labelCounts[0], labelCounts[1], labelCounts[2],
                (System.nanoTime() - start) / 1_000_000);
        }
    }

    private static JsonArray slotSummaries(List<ExportPackLoader.SlotSpec> specs) {
        JsonArray out = new JsonArray();
        int limit = Math.min(specs.size(), 4);
        for (int i = 0; i < limit; i++) {
            out.add(slotSummary(specs.get(i)));
        }
        if (specs.size() > limit) {
            out.add("+" + (specs.size() - limit) + " more");
        }
        return out;
    }

    private static String slotSummary(ExportPackLoader.SlotSpec slot) {
        String id = slot.tagId != null ? "#" + slot.tagId : slot.displayId;
        if (id.length() > 48) {
            id = id.substring(0, 45) + "...";
        }
        String amount = slot.amount == Math.floor(slot.amount) && !Double.isInfinite(slot.amount)
            ? String.valueOf((long) slot.amount)
            : String.format(Locale.ROOT, "%.1f", slot.amount);
        String chance = slot.chance > 0 && slot.chance < 1
            ? String.format(Locale.ROOT, "@%.0f%%", slot.chance * 100)
            : "";
        return id + " x" + amount + chance;
    }

    //endregion

    //region bench subcommand

    private record Model(double priorLogOdds, Map<String, double[]> featureWeights) {
    }

    private static Model loadModel(Path path) throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, double[]> weights = new LinkedHashMap<>();
        for (var element : json.getAsJsonArray("features")) {
            JsonObject f = element.getAsJsonObject();
            weights.put(f.get("id").getAsString(), new double[]{
                f.get("wPresent").getAsDouble(), f.get("wAbsent").getAsDouble()});
        }
        return new Model(json.get("priorLogOdds").getAsDouble(), weights);
    }

    private static void runBench(Map<String, String> flags) throws Exception {
        ChainExportMain.ensureVanillaBootstrap();
        Path exportDir = Path.of(flag(flags, "export-dir", "../exports"));
        Path outDir = Path.of(flag(flags, "out", "../experiments/naive-bayes")).resolve("results");
        List<Path> packs = discoverPacks(exportDir, flag(flags, "packs", ""));
        int targetCount = Integer.parseInt(flag(flags, "targets", "100"));
        double cap = Double.parseDouble(flag(flags, "cap", "12"));
        double maxMult = Double.parseDouble(flag(flags, "max-mult", "50"));
        String modes = flag(flags, "modes", "a,b,c");
        String blacklist = flag(flags, "target-blacklist", "decompress");
        if (modes.contains("c") && !flags.containsKey("model")) {
            System.err.println("mode c requires --model <bayes-model.json>");
            System.exit(2);
        }
        Model model = flags.containsKey("model") ? loadModel(Path.of(flags.get("model"))) : null;
        Files.createDirectories(outDir);

        for (Path packDir : packs) {
            String name = packDir.getFileName().toString();
            long start = System.nanoTime();
            OfflineContent.Session session = OfflineContent.newSession();
            ExportPackLoader.LoadedPack pack = ExportPackLoader.load(packDir, session, new ExportPackLoader.LoadOptions(true));
            PackFeatures features = extractFeatures(pack);
            BitSet junkOrDuplicate = pack.junkOrDuplicate();

            // B-view of the pack: junk/duplicate removed, exactly like the production loader
            List<ExportPackLoader.ExportRecipeSystem> bSystems = new ArrayList<>();
            List<ExportPackLoader.ExportRecipe> bRecipes = new ArrayList<>();
            for (ExportPackLoader.ExportRecipeSystem system : pack.systems()) {
                List<ExportPackLoader.ExportRecipe> kept = new ArrayList<>();
                for (ExportPackLoader.ExportRecipe recipe : system.allRecipes()) {
                    if (!junkOrDuplicate.get(recipe.index)) {
                        kept.add(recipe);
                    }
                }
                if (!kept.isEmpty()) {
                    bSystems.add(new ExportPackLoader.ExportRecipeSystem(system.systemId(), kept));
                    bRecipes.addAll(kept);
                }
            }
            ExportPackLoader.LoadedPack bView = new ExportPackLoader.LoadedPack(
                name, pack.totalRecipes(), bRecipes.size(), pack.excludedJunk(), pack.excludedDuplicate(),
                pack.droppedNoContent(), bSystems, bRecipes, Map.of(), pack.parseMs(), pack.classifyMs(),
                null, null);

            // targets exist in the B graph by construction; A/C graphs are supersets
            ChainTargetSelector selector = new ChainTargetSelector(bView);
            List<ChainTargetSelector.Target> targets = selector.select(targetCount, 80, blacklist);
            System.out.printf("[bench] %-30s targets=%d (graph recipes: full=%d filtered=%d)%n",
                name, targets.size(), pack.recipes().size(), bRecipes.size());

            List<RecipeEntry<?>> allEntries = new ArrayList<>(pack.recipes().size());
            RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
            for (ExportPackLoader.ExportRecipeSystem system : pack.systems()) {
                builder.registerSystem(system);
                for (ExportPackLoader.ExportRecipe recipe : system.allRecipes()) {
                    allEntries.add(RecipeEntry.of(system, recipe));
                }
            }
            List<RecipeEntry<?>> bEntries = new ArrayList<>(bRecipes.size());
            for (ExportPackLoader.ExportRecipeSystem system : bSystems) {
                for (ExportPackLoader.ExportRecipe recipe : system.allRecipes()) {
                    bEntries.add(RecipeEntry.of(system, recipe));
                }
            }
            session.attachRegistry();
            RecipeApi current = RecipeApiImpl.instance();
            RecipeApiImpl previous = current instanceof RecipeApiImpl impl ? impl : null;
            RecipeApiImpl.attach(new RecipeApiImpl(builder));
            try {
                RuleDefinition activeRule = ChainExportMain.ruleFor("preview");
                Map<String, List<JsonObject>> resultsByMode = new LinkedHashMap<>();
                Map<String, ExportPackLoader.ExportRecipeSystem> systemsById = new HashMap<>();
                for (ExportPackLoader.ExportRecipeSystem system : pack.systems()) {
                    systemsById.put(system.systemId().toString(), system);
                }
                Map<String, ExportPackLoader.ExportRecipeSystem> bSystemsById = new HashMap<>();
                for (ExportPackLoader.ExportRecipeSystem system : bSystems) {
                    bSystemsById.put(system.systemId().toString(), system);
                }

                if (modes.contains("a")) {
                    RecipeRoutingHeuristics.clear();
                    RoutingService service = new RoutingService(RecipeGraph.buildGraph(Lists.immutable.ofAll(allEntries)));
                    registerDefaults(service);
                    resultsByMode.put("a", runTargets(service, "a", name, targets, features, systemsById,
                        activeRule.profile().profileId()));
                    service = null;
                }
                if (modes.contains("c") && model != null) {
                    RecipeRoutingHeuristics.clear();
                    RoutingService service = new RoutingService(RecipeGraph.buildGraph(Lists.immutable.ofAll(allEntries)));
                    registerDefaults(service);
                    RuleProfile bayesProfile = bayesProfile(service, features, model, cap, maxMult);
                    service.registerRuleProfile(bayesProfile);
                    resultsByMode.put("c", runTargets(service, "c", name, targets, features, systemsById,
                        bayesProfile.profileId()));
                    service = null;
                }
                if (modes.contains("b")) {
                    RecipeRoutingHeuristics.clear();
                    RoutingService service = new RoutingService(RecipeGraph.buildGraph(Lists.immutable.ofAll(bEntries)));
                    registerDefaults(service);
                    resultsByMode.put("b", runTargets(service, "b", name, targets, features, bSystemsById,
                        activeRule.profile().profileId()));
                    service = null;
                }

                writeBenchOutputs(outDir, name, targets, resultsByMode);
            } finally {
                if (previous != null) {
                    RecipeApiImpl.attach(previous);
                }
            }
            System.out.printf("[bench] %-30s done elapsedMs=%d%n", name, (System.nanoTime() - start) / 1_000_000);
        }
    }

    private static void registerDefaults(RoutingService service) {
        for (RuleDefinition rule : DefaultRuleDefinitions.defaultCollection().rules()) {
            service.registerRuleDefinition(rule);
        }
    }

    /** NB log-odds per recipe (ExportRecipe index): prior + present/absent weights, raw (unclamped). */
    private static double[] logOddsByIndex(PackFeatures features, Model model) {
        int total = 0;
        for (int index : features.firedBits().keySet()) {
            total = Math.max(total, index + 1);
        }
        double[] logOdds = new double[total];
        java.util.Arrays.fill(logOdds, model.priorLogOdds());
        List<FeatureInfo> catalog = features.catalog();
        double[] wPresent = new double[catalog.size()];
        double[] wAbsent = new double[catalog.size()];
        for (int i = 0; i < catalog.size(); i++) {
            double[] weights = model.featureWeights().get(catalog.get(i).id());
            if (weights != null) {
                wPresent[i] = weights[0];
                wAbsent[i] = weights[1];
            }
        }
        for (Map.Entry<Integer, Long> entry : features.firedBits().entrySet()) {
            long bits = entry.getValue();
            double value = model.priorLogOdds();
            for (int i = 0; i < catalog.size(); i++) {
                value += features.fired(bits, i) ? wPresent[i] : wAbsent[i];
            }
            logOdds[entry.getKey()] = value;
        }
        return logOdds;
    }

    /**
     * Builds a preview-profile clone carrying a per-recipe advisory cost
     * multiplier derived from the NB log-odds: {@code maxMult^(clamp(lo,0,cap)/cap)}.
     * A junk-leaning recipe (log-odds ≥ cap) costs {@code maxMult}× more in the
     * advisory oracle, sinking it below alternatives in the cost tier while
     * keeping it usable when it is the only source — nothing is removed.
     */
    private static RuleProfile bayesProfile(RoutingService service, PackFeatures features, Model model,
                                            double cap, double maxMult) {
        RuleProfile base = ChainExportMain.ruleFor("preview").toProfile();
        double[] logOdds = logOddsByIndex(features, model);
        int size = service.catalog().size();
        double[] multiplier = new double[size];
        java.util.Arrays.fill(multiplier, 1.0);
        int boosted = 0;
        double maxSeen = 1.0;
        for (int i = 0; i < size; i++) {
            if (service.catalog().nodeAt(i).recipe().recipe() instanceof ExportPackLoader.ExportRecipe recipe
                && recipe.index < logOdds.length) {
                double clamped = Math.max(0.0, Math.min(cap, logOdds[recipe.index]));
                if (clamped > 0) {
                    multiplier[i] = Math.pow(maxMult, clamped / cap);
                    boosted++;
                    maxSeen = Math.max(maxSeen, multiplier[i]);
                }
            }
        }
        System.out.printf("[bench] NB cost multiplier: nodes=%d boosted=%d maxMult=%.1f (max applied %.1f)%n",
            size, boosted, maxMult, maxSeen);
        return new RuleProfile(
            dev.vfyjxf.cloudlib.api.util.Namespace.of("cirrus_bayes", "preview_nb"),
            base.model(),
            base.hardRules(),
            base.scoreRules(),
            base.rankPolicy(),
            base.tieBreakPolicy(),
            base.explorePolicy(),
            base.unresolvedInputRule(),
            base.capabilities(),
            base.shortlistK(),
            base.rollbackWidth(),
            base.nearTieEpsilon(),
            base.amplificationSoftThreshold(),
            base.amplificationHardThreshold(),
            base.liftMultiSemanticChildShortages(),
            base.fastPathEligible(),
            index -> index >= 0 && index < multiplier.length ? multiplier[index] : 1.0d,
            null);
    }

    private record TargetResult(JsonObject json) {
    }

    private static List<JsonObject> runTargets(RoutingService service, String mode, String packName,
                                               List<ChainTargetSelector.Target> targets, PackFeatures features,
                                               Map<String, ExportPackLoader.ExportRecipeSystem> systemsById,
                                               dev.vfyjxf.cloudlib.api.util.Namespace profileId) {
        RuleDefinition activeRule = ChainExportMain.ruleFor("preview");
        List<JsonObject> results = new ArrayList<>(targets.size());
        long modeStart = System.nanoTime();
        for (ChainTargetSelector.Target target : targets) {
            // resolve the system instance that belongs to this mode's graph
            // (targets were selected on the B-view; A/C graphs hold the original systems)
            ExportPackLoader.ExportRecipeSystem system = systemsById.get(target.system().systemId().toString());
            if (system == null) {
                system = target.system();
            }
            RecipeEntry<ExportPackLoader.ExportRecipe> entry = RecipeEntry.of(system, target.recipe());
            RoutingBudget budget = budgetFor(service, entry, target);
            RoutingRequest request = RoutingRequest.builder()
                .plannerMode(PlannerMode.standard)
                .targetRecipe(entry, 1)
                .ruleProfileId(profileId)
                .sourcePolicyId(activeRule.sourcePolicy().policyId())
                .stock(Stock.empty())
                .loopPolicy(LoopPolicy.allowBoundedAmplification)
                .maxProducerCandidates(16)
                .budget(budget)
                .build();
            RoutingOutcome outcome = service.route(request);
            results.add(describe(outcome, mode, target, features));
        }
        System.out.printf("[bench] %-30s mode=%s targets=%d elapsedMs=%d%n",
            packName, mode, targets.size(), (System.nanoTime() - modeStart) / 1_000_000);
        return results;
    }

    private static RoutingBudget budgetFor(RoutingService service, RecipeEntry<ExportPackLoader.ExportRecipe> entry,
                                           ChainTargetSelector.Target target) {
        int targetIndex = service.catalog().indexOf(entry);
        if (targetIndex < 0) {
            return RoutingBudget.standardDefault();
        }
        BitSet allAllowed = new BitSet(service.catalog().size());
        allAllowed.set(0, service.catalog().size());
        ConeStats cone = service.coneStats(targetIndex, allAllowed);
        return RoutingBudget.fromConeStats(cone, PlannerMode.standard, Math.max(1, target.recipe().inputs.size()));
    }

    private static JsonObject describe(RoutingOutcome outcome, String mode, ChainTargetSelector.Target target,
                                       PackFeatures features) {
        JsonObject json = new JsonObject();
        json.addProperty("mode", mode);
        json.addProperty("target", target.outputId());
        json.addProperty("tier", target.tier());
        json.addProperty("status", outcome.status().toString());
        RoutingMetrics metrics = outcome.metrics();
        json.addProperty("elapsedMs", metrics.elapsedMs());
        json.addProperty("expandedNodes", metrics.expandedNodes());
        json.addProperty("generatedCandidates", metrics.generatedCandidates());
        json.addProperty("acceptedCandidates", metrics.acceptedCandidates());
        json.addProperty("scannedRefs", metrics.scannedRefs());
        json.addProperty("candidateRollbacks", metrics.candidateRollbacks());
        json.addProperty("explorationBranches", metrics.explorationBranchesExpanded());
        json.addProperty("budgetLimit", metrics.budgetLimit());
        json.addProperty("budgetUsed", metrics.budgetUsed());
        int steps = 0;
        int junk = 0;
        int middle = 0;
        JsonArray path = new JsonArray();
        if (outcome.plan() != null) {
            for (PlanAction action : outcome.plan().actions().castToList()) {
                if (action instanceof PlanAction.ExecuteRecipe exec
                    && exec.recipe().recipe() instanceof ExportPackLoader.ExportRecipe recipe) {
                    steps++;
                    String label = features.weakLabel().get(recipe.index);
                    if ("junk".equals(label)) {
                        junk++;
                    } else if ("middle".equals(label)) {
                        middle++;
                    }
                    if (path.size() < 12) {
                        path.add(recipe.recipeId);
                    }
                }
            }
        }
        json.addProperty("planSteps", steps);
        json.addProperty("junkInPath", junk);
        json.addProperty("middleInPath", middle);
        json.add("path", path);
        return json;
    }

    private static void writeBenchOutputs(Path outDir, String name, List<ChainTargetSelector.Target> targets,
                                          Map<String, List<JsonObject>> resultsByMode) throws IOException {
        Path packOut = outDir.resolve(name);
        Files.createDirectories(packOut);
        for (Map.Entry<String, List<JsonObject>> entry : resultsByMode.entrySet()) {
            try (BufferedWriter writer = Files.newBufferedWriter(packOut.resolve("mode-" + entry.getKey() + ".jsonl"),
                StandardCharsets.UTF_8)) {
                for (JsonObject json : entry.getValue()) {
                    writer.write(GSON.toJson(json));
                    writer.write('\n');
                }
            }
        }

        JsonObject summary = new JsonObject();
        summary.addProperty("pack", name);
        summary.addProperty("targets", targets.size());
        JsonObject modesJson = new JsonObject();
        for (Map.Entry<String, List<JsonObject>> entry : resultsByMode.entrySet()) {
            JsonObject m = new JsonObject();
            long success = entry.getValue().stream().filter(j -> "success".equals(j.get("status").getAsString())).count();
            long partial = entry.getValue().stream().filter(j -> "partial".equals(j.get("status").getAsString())).count();
            long failed = entry.getValue().stream().filter(j -> "failed".equals(j.get("status").getAsString())).count();
            m.addProperty("success", success);
            m.addProperty("partial", partial);
            m.addProperty("failed", failed);
            m.addProperty("sumElapsedMs", entry.getValue().stream().mapToLong(j -> j.get("elapsedMs").getAsLong()).sum());
            m.addProperty("sumExpandedNodes", entry.getValue().stream().mapToLong(j -> j.get("expandedNodes").getAsLong()).sum());
            m.addProperty("sumGeneratedCandidates", entry.getValue().stream().mapToLong(j -> j.get("generatedCandidates").getAsLong()).sum());
            m.addProperty("sumPlanSteps", entry.getValue().stream().mapToLong(j -> j.get("planSteps").getAsLong()).sum());
            m.addProperty("sumJunkInPath", entry.getValue().stream().mapToLong(j -> j.get("junkInPath").getAsLong()).sum());
            m.addProperty("sumMiddleInPath", entry.getValue().stream().mapToLong(j -> j.get("middleInPath").getAsLong()).sum());
            modesJson.add(entry.getKey(), m);
        }
        summary.add("modes", modesJson);

        // cases: targets the hard filter lost (B not successful) but the unfiltered modes solved
        if (resultsByMode.containsKey("b")) {
            Map<String, JsonObject> byTargetB = new HashMap<>();
            for (JsonObject json : resultsByMode.get("b")) {
                byTargetB.put(json.get("target").getAsString(), json);
            }
            JsonArray lostByHardFilter = new JsonArray();
            for (Map.Entry<String, List<JsonObject>> entry : resultsByMode.entrySet()) {
                if (entry.getKey().equals("b")) {
                    continue;
                }
                for (JsonObject json : entry.getValue()) {
                    JsonObject b = byTargetB.get(json.get("target").getAsString());
                    if (b == null || "success".equals(b.get("status").getAsString())) {
                        continue;
                    }
                    boolean solvedHere = "success".equals(json.get("status").getAsString());
                    if (solvedHere) {
                        JsonObject c = new JsonObject();
                        c.addProperty("mode", entry.getKey());
                        c.addProperty("target", json.get("target").getAsString());
                        c.addProperty("bStatus", b.get("status").getAsString());
                        c.addProperty("bJunkInPath", b.get("junkInPath").getAsInt());
                        c.addProperty("hereJunkInPath", json.get("junkInPath").getAsInt());
                        c.addProperty("hereMiddleInPath", json.get("middleInPath").getAsInt());
                        JsonArray path = json.getAsJsonArray("path");
                        JsonArray copied = new JsonArray();
                        for (int i = 0; i < path.size(); i++) {
                            copied.add(path.get(i));
                        }
                        c.add("path", copied);
                        lostByHardFilter.add(c);
                    }
                }
            }
            summary.add("hardFilterLostCases", lostByHardFilter);
        }
        Files.writeString(packOut.resolve("summary.json"), GSON.toJson(summary));
    }

    //endregion
}
