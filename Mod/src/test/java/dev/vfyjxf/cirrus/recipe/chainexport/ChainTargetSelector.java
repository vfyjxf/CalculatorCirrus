package dev.vfyjxf.cirrus.recipe.chainexport;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.content.TypedStackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Picks solve targets from an offline-loaded pack, porting the in-game
 * {@code TestModAutoSample} ranking: cheap memoized tree-sum cone score,
 * exact distinct-cone re-rank for the complex tier, percentile bands for the
 * medium/plain tiers, plus a sink bias (outputs rarely consumed elsewhere) to
 * surface end-gear chains.
 */
final class ChainTargetSelector {

    record Target(String outputId, ExportPackLoader.ExportRecipe recipe, ExportPackLoader.ExportRecipeSystem system,
                  long coneScore, String tier) {
    }

    private record Scored(String rawId, Producer producer, long score) {
    }

    private record Producer(ExportPackLoader.ExportRecipe recipe, ExportPackLoader.ExportRecipeSystem system) {
    }

    private final Map<String, Producer> bestProducer = new HashMap<>();
    private final Map<ExportPackLoader.ExportRecipe, ExportPackLoader.ExportRecipeSystem> systemOf = new IdentityHashMap<>();
    private final Map<String, Integer> consumedCount = new HashMap<>();

    ChainTargetSelector(ExportPackLoader.LoadedPack pack) {
        for (ExportPackLoader.ExportRecipeSystem system : pack.systems()) {
            for (ExportPackLoader.ExportRecipe recipe : system.allRecipes()) {
                systemOf.put(recipe, system);
            }
        }
        for (ExportPackLoader.ExportRecipe recipe : pack.recipes()) {
            ExportPackLoader.ExportRecipeSystem system = systemOf.get(recipe);
            for (String outputId : outputIds(recipe)) {
                Producer existing = bestProducer.get(outputId);
                if (existing == null || betterProducer(recipe, existing.recipe)) {
                    bestProducer.put(outputId, new Producer(recipe, system));
                }
            }
            for (String inputId : inputIds(recipe)) {
                consumedCount.merge(inputId, 1, Integer::sum);
            }
        }
    }

    private static boolean betterProducer(ExportPackLoader.ExportRecipe candidate, ExportPackLoader.ExportRecipe current) {
        if (candidate.inputs.size() != current.inputs.size()) {
            return candidate.inputs.size() < current.inputs.size();
        }
        return candidate.index < current.index;
    }

    /** Review filter from the iterative audit loop (filters.json). */
    record ReviewFilter(String name, String type, String pattern) {

        static final String TARGET = "targetRegex";
        static final String RECIPE = "recipeRegex";

        java.util.regex.Pattern compiled() {
            return java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
        }
    }

    List<Target> select(int count, int modCap, String blacklist) {
        List<String> blacklistTokens = blacklist == null || blacklist.isBlank()
            ? List.of()
            : java.util.Arrays.stream(blacklist.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .toList();
        List<Scored> scored = new ArrayList<>(bestProducer.size());
        for (Map.Entry<String, Producer> entry : bestProducer.entrySet()) {
            if (!blacklistTokens.isEmpty() && isBlacklisted(entry.getKey(), entry.getValue().recipe(), blacklistTokens)) {
                continue;
            }
            scored.add(new Scored(entry.getKey(), entry.getValue(), coneScore(entry.getKey())));
        }
        scored.sort(Comparator.<Scored>comparingLong(Scored::score).reversed());

        List<Target> targets = new ArrayList<>(count);
        Set<String> taken = new HashSet<>();
        Map<String, Integer> perMod = new HashMap<>();

        int complexQuota = (int) Math.round(count * 0.40);
        int mediumQuota = (int) Math.round(count * 0.35);
        int plainQuota = count - complexQuota - mediumQuota;

        // complex tier: exact re-rank of the top slice
        int reRank = Math.min(scored.size(), Math.max(complexQuota * 4, 200));
        List<Scored> exact = new ArrayList<>(reRank);
        for (int i = 0; i < reRank; i++) {
            Scored candidate = scored.get(i);
            exact.add(new Scored(candidate.rawId(), candidate.producer(), distinctConeSize(candidate.rawId())));
        }
        exact.sort(Comparator.<Scored>comparingLong(Scored::score).reversed());
        for (Scored candidate : exact) {
            if (targets.size() >= complexQuota) break;
            addIfAllowed(candidate, "complex", targets, taken, perMod, modCap);
        }

        // medium / plain tiers: evenly spaced picks inside percentile bands
        fillBand(scored, 0.35, 0.65, mediumQuota, "medium", targets, taken, perMod, modCap);
        fillBand(scored, 0.75, 0.95, plainQuota, "plain", targets, taken, perMod, modCap);

        // sink fill: outputs consumed by few other recipes, hardest cones first
        if (targets.size() < count) {
            List<Scored> sinks = new ArrayList<>(scored);
            sinks.sort(Comparator
                .<Scored>comparingInt(s -> consumedCount.getOrDefault(s.rawId(), 0))
                .thenComparing(Comparator.<Scored>comparingLong(Scored::score).reversed()));
            for (Scored candidate : sinks) {
                if (targets.size() >= count) break;
                addIfAllowed(candidate, "sink", targets, taken, perMod, modCap);
            }
        }
        return targets;
    }

    private void fillBand(List<Scored> scored, double lo, double hi, int quota, String tier,
                          List<Target> targets, Set<String> taken, Map<String, Integer> perMod, int modCap) {
        int total = scored.size();
        if (total == 0 || quota <= 0) return;
        int from = (int) Math.floor(total * lo);
        int to = Math.min(total, Math.max(from + quota, (int) Math.ceil(total * hi)));
        List<Scored> band = scored.subList(from, to);
        int stride = Math.max(1, band.size() / (quota * 2));
        for (int pass = 0; pass < 2 && targetsByTier(tier, targets) < quota; pass++) {
            for (int i = pass == 0 ? 0 : 1; i < band.size() && targetsByTier(tier, targets) < quota; i += stride) {
                addIfAllowed(band.get(i), tier, targets, taken, perMod, modCap);
            }
        }
    }

    private static int targetsByTier(String tier, List<Target> targets) {
        int n = 0;
        for (Target target : targets) {
            if (target.tier().equals(tier)) n++;
        }
        return n;
    }

    private void addIfAllowed(Scored candidate, String tier, List<Target> targets,
                              Set<String> taken, Map<String, Integer> perMod, int modCap) {
        if (!taken.add(candidate.rawId())) return;
        String mod = modOf(candidate.rawId());
        if (perMod.getOrDefault(mod, 0) >= modCap) return;
        perMod.merge(mod, 1, Integer::sum);
        targets.add(new Target(candidate.rawId(), candidate.producer().recipe(), candidate.producer().system(), candidate.score(), tier));
    }

    /** Blacklist matches the target output id or the producer recipe id (case-insensitive substring). */
    /**
     * Iterative-audit selection: orders candidates by how many UNCOVERED recipes
     * their demand cone reaches (10 pts per uncovered recipe, +1 per tag/value
     * slot), so each successive batch steers into unexplored regions of the pack.
     * targetRegex filters remove known-pattern targets up-front.
     */
    List<Target> selectBatch(int count, int modCap, List<ReviewFilter> filters, Set<String> coveredRecipes) {
        List<java.util.regex.Pattern> targetFilters = new ArrayList<>();
        for (ReviewFilter filter : filters) {
            if (ReviewFilter.TARGET.equals(filter.type())) {
                targetFilters.add(filter.compiled());
            }
        }
        record Weighted(String rawId, Producer producer, long score) {
        }
        Map<String, Long> memo = new HashMap<>();
        List<Weighted> scored = new ArrayList<>(bestProducer.size());
        for (Map.Entry<String, Producer> entry : bestProducer.entrySet()) {
            boolean filtered = false;
            for (java.util.regex.Pattern pattern : targetFilters) {
                if (pattern.matcher(entry.getKey()).find()
                    || pattern.matcher(entry.getValue().recipe().recipeId).find()) {
                    filtered = true;
                    break;
                }
            }
            if (filtered) {
                continue;
            }
            scored.add(new Weighted(entry.getKey(), entry.getValue(),
                uncoveredScore(entry.getKey(), coveredRecipes, memo, new HashSet<>(), 0)));
        }
        scored.sort(Comparator.<Weighted>comparingLong(Weighted::score).reversed());

        List<Target> targets = new ArrayList<>(count);
        Set<String> taken = new HashSet<>();
        Map<String, Integer> perMod = new HashMap<>();
        for (Weighted candidate : scored) {
            if (targets.size() >= count) break;
            if (!taken.add(candidate.rawId())) continue;
            String mod = modOf(candidate.rawId());
            if (perMod.getOrDefault(mod, 0) >= modCap) continue;
            perMod.merge(mod, 1, Integer::sum);
            targets.add(new Target(candidate.rawId(), candidate.producer().recipe(), candidate.producer().system(), candidate.score(), "uncovered"));
        }
        return targets;
    }

    private long uncoveredScore(String rawId, Set<String> covered, Map<String, Long> memo, Set<String> onStack, int depth) {
        if (depth > 240) return 0;
        Long cached = memo.get(rawId);
        if (cached != null) return cached;
        if (!onStack.add(rawId)) return 0;
        Producer producer = bestProducer.get(rawId);
        long score = 1;
        if (producer != null) {
            if (!covered.contains(producer.recipe().recipeId)) {
                score += 10; // strong pull toward recipes no chain has touched yet
            }
            Set<String> seenInputs = new HashSet<>();
            for (InputContent input : producer.recipe().inputs) {
                String inputId = singleInputId(input);
                if (inputId == null) {
                    score += 1;
                    continue;
                }
                if (seenInputs.add(inputId)) {
                    score += uncoveredScore(inputId, covered, memo, onStack, depth + 1);
                }
            }
        }
        onStack.remove(rawId);
        memo.put(rawId, score);
        return score;
    }

    private static boolean isBlacklisted(String outputId, ExportPackLoader.ExportRecipe recipe, List<String> tokens) {
        String outputLower = outputId.toLowerCase();
        String recipeLower = recipe.recipeId.toLowerCase();
        for (String token : tokens) {
            if (outputLower.contains(token) || recipeLower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static String modOf(String id) {
        int idx = id.indexOf(':');
        return idx > 0 ? id.substring(0, idx) : id;
    }

    //region cone ranking (ported from TestModAutoSample)

    private long coneScore(String rawId) {
        return coneScore(rawId, new HashMap<>(), new HashSet<>(), 0);
    }

    private long coneScore(String rawId, Map<String, Long> memo, Set<String> onStack, int depth) {
        if (depth > 240) return 0;
        Long cached = memo.get(rawId);
        if (cached != null) return cached;
        if (!onStack.add(rawId)) return 0;
        Producer producer = bestProducer.get(rawId);
        long score = 1;
        if (producer != null) {
            Set<String> seenInputs = new HashSet<>();
            for (InputContent input : producer.recipe().inputs) {
                String inputId = singleInputId(input);
                if (inputId == null) {
                    score += 2; // tag / list / value slot
                    continue;
                }
                if (seenInputs.add(inputId)) {
                    score += coneScore(inputId, memo, onStack, depth + 1);
                }
            }
        }
        onStack.remove(rawId);
        memo.put(rawId, score);
        return score;
    }

    private long distinctConeSize(String rawId) {
        Set<String> visitedItems = new HashSet<>();
        Set<ExportPackLoader.ExportRecipe> visitedRecipes = new HashSet<>();
        List<String> queue = new ArrayList<>();
        queue.add(rawId);
        visitedItems.add(rawId);
        while (!queue.isEmpty() && visitedItems.size() < 60_000) {
            String current = queue.remove(queue.size() - 1);
            Producer producer = bestProducer.get(current);
            if (producer == null || !visitedRecipes.add(producer.recipe())) continue;
            for (InputContent input : producer.recipe().inputs) {
                String inputId = singleInputId(input);
                if (inputId != null && visitedItems.add(inputId)) {
                    queue.add(inputId);
                }
            }
        }
        return (long) visitedItems.size() * 1000 + visitedRecipes.size();
    }

    //endregion

    //region id extraction

    private static @Nullable String singleInputId(InputContent input) {
        if (input instanceof StackContent<?, ?> stack) {
            Object value = stack.content().value();
            return value != null ? value.toString() : null;
        }
        return null;
    }

    private static Set<String> inputIds(ExportPackLoader.ExportRecipe recipe) {
        Set<String> ids = new LinkedHashSet<>();
        for (InputContent input : recipe.inputs) {
            collectIds(input, ids);
        }
        return ids;
    }

    private static Set<String> outputIds(ExportPackLoader.ExportRecipe recipe) {
        Set<String> ids = new LinkedHashSet<>();
        for (OutputContent output : recipe.outputs) {
            if (output instanceof StackContent<?, ?> stack) {
                Object value = stack.content().value();
                if (value != null) ids.add(value.toString());
            }
        }
        return ids;
    }

    private static void collectIds(InputContent input, Set<String> ids) {
        switch (input) {
            case StackContent<?, ?> stack -> {
                Object value = stack.content().value();
                if (value != null) ids.add(value.toString());
            }
            case TypedStackListContent<?, ?> typed -> {
                for (ContentStack<?, ?> content : typed.stacks()) {
                    Object value = content.value();
                    if (value != null) ids.add(value.toString());
                }
            }
            case StackListContent list -> {
                for (ContentStack<?, ?> content : list.stacks()) {
                    Object value = content.value();
                    if (value != null) ids.add(value.toString());
                }
            }
            case TagStackContent<?> tag -> ids.add("#" + tag.tagKey().location());
            case ValueContent<?> value -> ids.add(value.type().typeId().toString());
            default -> {
            }
        }
    }

    //endregion
}
