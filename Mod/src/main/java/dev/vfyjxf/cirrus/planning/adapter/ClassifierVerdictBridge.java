package dev.vfyjxf.cirrus.planning.adapter;

import dev.vfyjxf.cirrus.api.planning.graph.RecipeAdvisory;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationResult;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifier;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import dev.vfyjxf.cirrus.api.recipe.screening.RecipeUsePolicy;
import dev.vfyjxf.cirrus.planning.core.graph.DefaultSystemAdmissionPolicy;
import org.jspecify.annotations.Nullable;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ClassifierVerdictBridge {

    private static final String featJeiVanillaMirrors = "cirrus:feat/jei_vanilla_mirrors";
    private static final String featSyntheticAnvilDuplicates = "cirrus:feat/synthetic_anvil_duplicates";
    private static final String featAnvilLeakRecipes = "cirrus:feat/anvil_leak_recipes";
    private static final String patternStorageLadder = "cirrus:pattern/storage_ladder";

    private static final String[] exclusionTags = {ClassificationTags.junk, ClassificationTags.duplicate,
            featJeiVanillaMirrors, featSyntheticAnvilDuplicates, featAnvilLeakRecipes};

    private static final String[] discouragedTags = {ClassificationTags.recolor, ClassificationTags.exchange,
            patternStorageLadder};

    private static final String[] lastResortTags = {ClassificationTags.recycling};

    private static volatile @Nullable RecipeClassifier defaultClassifier;

    private ClassifierVerdictBridge() {}

    public static @Nullable RecipeClassifier defaultClassifierOrNull() {
        RecipeClassifier current = defaultClassifier;
        if (current != null) {
            return current;
        }
        synchronized (ClassifierVerdictBridge.class) {
            current = defaultClassifier;
            if (current == null) {
                try {
                    current = RecipeClassifier.createDefault();
                    defaultClassifier = current;
                } catch (RuntimeException | LinkageError ignored) {
                    return null;
                }
            }
            return current;
        }
    }

    public static DefaultSystemAdmissionPolicy classifyToPolicy(
        DefaultSystemAdmissionPolicy basePolicy,
        List<RecipeClassifyView> views,
        long[] sourceRefs
    ) {
        RecipeClassifier classifier = defaultClassifierOrNull();
        if (classifier == null) {
            return Objects.requireNonNull(basePolicy, "basePolicy");
        }
        return classifyToPolicy(basePolicy, classifier, views, sourceRefs);
    }

    public static DefaultSystemAdmissionPolicy classifyToPolicy(
        DefaultSystemAdmissionPolicy basePolicy,
        RecipeClassifier classifier,
        List<RecipeClassifyView> views,
        long[] sourceRefs
    ) {
        return classifyToPolicy(basePolicy, classifier, views, sourceRefs, null);
    }

    public static DefaultSystemAdmissionPolicy classifyToPolicy(
        DefaultSystemAdmissionPolicy basePolicy,
        RecipeClassifier classifier,
        List<RecipeClassifyView> views,
        long[] sourceRefs,
        @Nullable Map<Long, RecipeUsePolicy> systemHintPolicies
    ) {
        Objects.requireNonNull(basePolicy, "basePolicy");
        Objects.requireNonNull(classifier, "classifier");
        Objects.requireNonNull(views, "views");
        Objects.requireNonNull(sourceRefs, "sourceRefs");
        boolean hasSystemHints = systemHintPolicies != null && !systemHintPolicies.isEmpty();
        if (views.isEmpty() && !hasSystemHints) {
            return basePolicy;
        }
        if (views.size() != sourceRefs.length) {
            throw new IllegalArgumentException(
                "views size (" + views.size() + ") != sourceRefs length (" + sourceRefs.length + ")"
            );
        }

        Map<Long, SystemAdmission> admissions = new HashMap<>();
        Map<Long, RecipeAdvisory> advisories = new HashMap<>();

        if (!views.isEmpty()) {
            ClassificationResult result = classifier.classifyAll(views);
            Map<String, BitSet> tagToRecipes = result.tagToRecipes();

            for (String tag : exclusionTags) {
                BitSet bits = tagToRecipes.get(tag);
                if (bits == null) {
                    continue;
                }
                for (int idx = bits.nextSetBit(0); idx >= 0; idx = bits.nextSetBit(idx + 1)) {
                    if (idx < sourceRefs.length && sourceRefs[idx] != 0L) {
                        admissions.put(sourceRefs[idx], SystemAdmission.excludedDisplay);
                    }
                }
            }

            for (String tag : discouragedTags) {
                BitSet bits = tagToRecipes.get(tag);
                if (bits == null) {
                    continue;
                }
                for (int idx = bits.nextSetBit(0); idx >= 0; idx = bits.nextSetBit(idx + 1)) {
                    if (idx < sourceRefs.length && sourceRefs[idx] != 0L) {
                        long ref = sourceRefs[idx];
                        if (admissions.get(ref) != SystemAdmission.excludedDisplay) {
                            advisories.merge(ref, RecipeAdvisory.discouraged, RecipeAdvisory::max);
                        }
                    }
                }
            }

            for (String tag : lastResortTags) {
                BitSet bits = tagToRecipes.get(tag);
                if (bits == null) {
                    continue;
                }
                for (int idx = bits.nextSetBit(0); idx >= 0; idx = bits.nextSetBit(idx + 1)) {
                    if (idx < sourceRefs.length && sourceRefs[idx] != 0L) {
                        long ref = sourceRefs[idx];
                        if (admissions.get(ref) != SystemAdmission.excludedDisplay) {
                            advisories.merge(ref, RecipeAdvisory.lastResort, RecipeAdvisory::max);
                        }
                    }
                }
            }

            for (int i = 0; i < views.size(); i++) {
                long ref = sourceRefs[i];
                if (ref == 0L) {
                    continue;
                }
                applyUsePolicy(ref, classifier.hintOf(views.get(i)), admissions, advisories);
            }
        }

        if (hasSystemHints && systemHintPolicies != null) {
            for (Map.Entry<Long, RecipeUsePolicy> entry : systemHintPolicies.entrySet()) {
                Long ref = entry.getKey();
                if (ref != null && ref != 0L) {
                    applyUsePolicy(ref, entry.getValue(), admissions, advisories);
                }
            }
        }

        if (admissions.isEmpty() && advisories.isEmpty()) {
            return basePolicy;
        }
        return basePolicy.withExternalVerdicts(admissions, advisories);
    }

    private static void applyUsePolicy(
        long ref,
        @Nullable RecipeUsePolicy policy,
        Map<Long, SystemAdmission> admissions,
        Map<Long, RecipeAdvisory> advisories
    ) {
        if (policy == null
                || policy == RecipeUsePolicy.normal
                || admissions.get(ref) == SystemAdmission.excludedDisplay) {
            return;
        }
        switch (policy) {
            case disabledByDefault -> {
                admissions.put(ref, SystemAdmission.excludedDisplay);
                advisories.remove(ref);
            }
            case lastResort -> advisories.merge(ref, RecipeAdvisory.lastResort, RecipeAdvisory::max);
            case discouraged, targetOnly -> advisories.merge(ref, RecipeAdvisory.discouraged, RecipeAdvisory::max);
            case normal -> {}
        }
    }
}
