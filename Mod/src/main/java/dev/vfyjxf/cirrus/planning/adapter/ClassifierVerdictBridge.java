package dev.vfyjxf.cirrus.planning.adapter;

import dev.vfyjxf.cirrus.api.planning.graph.RecipeAdvisory;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationResult;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifier;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import dev.vfyjxf.cirrus.api.recipe.screening.RecipeUsePolicy;
import dev.vfyjxf.cirrus.planning.core.graph.DefaultSystemAdmissionPolicy;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ClassifierVerdictBridge {

    private static final String FEAT_JEI_VANILLA_MIRRORS = "cirrus:feat/jei_vanilla_mirrors";
    private static final String FEAT_SYNTHETIC_ANVIL_DUPLICATES = "cirrus:feat/synthetic_anvil_duplicates";
    private static final String FEAT_ANVIL_LEAK_RECIPES = "cirrus:feat/anvil_leak_recipes";
    private static final String PATTERN_STORAGE_LADDER = "cirrus:pattern/storage_ladder";

    private static final String[] EXCLUSION_TAGS = {
            ClassificationTags.JUNK,
            ClassificationTags.DUPLICATE,
            FEAT_JEI_VANILLA_MIRRORS,
            FEAT_SYNTHETIC_ANVIL_DUPLICATES,
            FEAT_ANVIL_LEAK_RECIPES
    };

    private static final String[] DISCOURAGED_TAGS = {
            ClassificationTags.RECOLOR,
            ClassificationTags.EXCHANGE,
            PATTERN_STORAGE_LADDER
    };

    private static final String[] LAST_RESORT_TAGS = {
            ClassificationTags.RECYCLING
    };

    private static volatile @Nullable RecipeClassifier defaultClassifier;

    private ClassifierVerdictBridge() {
    }

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
        Objects.requireNonNull(basePolicy, "basePolicy");
        Objects.requireNonNull(classifier, "classifier");
        Objects.requireNonNull(views, "views");
        Objects.requireNonNull(sourceRefs, "sourceRefs");
        if (views.isEmpty()) {
            return basePolicy;
        }
        if (views.size() != sourceRefs.length) {
            throw new IllegalArgumentException(
                    "views size (" + views.size() + ") != sourceRefs length (" + sourceRefs.length + ")");
        }

        ClassificationResult result = classifier.classifyAll(views);
        Map<String, BitSet> tagToRecipes = result.tagToRecipes();
        Map<Long, SystemAdmission> admissions = new HashMap<>();
        Map<Long, RecipeAdvisory> advisories = new HashMap<>();

        for (String tag : EXCLUSION_TAGS) {
            BitSet bits = tagToRecipes.get(tag);
            if (bits == null) {
                continue;
            }
            for (int idx = bits.nextSetBit(0); idx >= 0; idx = bits.nextSetBit(idx + 1)) {
                if (idx < sourceRefs.length && sourceRefs[idx] != 0L) {
                    admissions.put(sourceRefs[idx], SystemAdmission.EXCLUDED_DISPLAY);
                }
            }
        }

        for (String tag : DISCOURAGED_TAGS) {
            BitSet bits = tagToRecipes.get(tag);
            if (bits == null) {
                continue;
            }
            for (int idx = bits.nextSetBit(0); idx >= 0; idx = bits.nextSetBit(idx + 1)) {
                if (idx < sourceRefs.length && sourceRefs[idx] != 0L) {
                    long ref = sourceRefs[idx];
                    if (admissions.get(ref) != SystemAdmission.EXCLUDED_DISPLAY) {
                        advisories.merge(ref, RecipeAdvisory.DISCOURAGED, RecipeAdvisory::max);
                    }
                }
            }
        }

        for (String tag : LAST_RESORT_TAGS) {
            BitSet bits = tagToRecipes.get(tag);
            if (bits == null) {
                continue;
            }
            for (int idx = bits.nextSetBit(0); idx >= 0; idx = bits.nextSetBit(idx + 1)) {
                if (idx < sourceRefs.length && sourceRefs[idx] != 0L) {
                    long ref = sourceRefs[idx];
                    if (admissions.get(ref) != SystemAdmission.EXCLUDED_DISPLAY) {
                        advisories.merge(ref, RecipeAdvisory.LAST_RESORT, RecipeAdvisory::max);
                    }
                }
            }
        }

        for (int i = 0; i < views.size(); i++) {
            long ref = sourceRefs[i];
            if (ref == 0L || admissions.get(ref) == SystemAdmission.EXCLUDED_DISPLAY) {
                continue;
            }
            RecipeClassifyView view = views.get(i);
            RecipeUsePolicy hint = classifier.hintOf(view);
            if (hint == null || hint == RecipeUsePolicy.normal) {
                continue;
            }
            switch (hint) {
                case disabledByDefault -> {
                    admissions.put(ref, SystemAdmission.EXCLUDED_DISPLAY);
                    advisories.remove(ref);
                }
                case lastResort -> advisories.merge(ref, RecipeAdvisory.LAST_RESORT, RecipeAdvisory::max);
                case discouraged, targetOnly -> advisories.merge(ref, RecipeAdvisory.DISCOURAGED, RecipeAdvisory::max);
                case normal -> {
                }
            }
        }

        if (admissions.isEmpty() && advisories.isEmpty()) {
            return basePolicy;
        }
        return basePolicy.withExternalVerdicts(admissions, advisories);
    }
}
