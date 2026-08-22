package dev.vfyjxf.cirrus.api.recipe.classify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A declarative pattern rule loaded from JSON. All specified matchers are
 * ANDed; unspecified matchers pass. On a match, all listed tags are attached.
 *
 * <p>JSON schema:
 * <pre>{@code
 * {
 *   "id": "cirrus:ftbquests",
 *   "system": "jei:category/ftbquests/quest",       // exact system id
 *   "systemPattern": "^jei:category/ftbquests/",    // regex over the system id
 *   "recipeIdPattern": "(?i)recycl",                // regex over the full recipe id
 *   "recipePathPattern": "...",                     // regex over the recipe id path
 *   "mod": "ftbquests",                             // recipe id namespace
 *   "tags": ["cirrus:quality/junk"]
 * }
 * }</pre>
 */
public record PatternClassificationRule(
    String id,
    @Nullable String system,
    @Nullable Pattern systemPattern,
    @Nullable Pattern recipeIdPattern,
    @Nullable Pattern recipePathPattern,
    @Nullable String mod,
    List<String> tags
) implements ClassificationRule {

    public PatternClassificationRule {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /**
     * Per-rule memo of the system-id matchers ({@link #system} + {@link
     * #systemPattern}). Distinct system ids are few (hundreds) while recipes
     * are hundreds of thousands, and regex matching dominates classification
     * cost; system matchers depend only on the system id, so they are cached
     * per rule for the whole session.
     */
    private static final java.util.concurrent.ConcurrentHashMap<PatternClassificationRule, java.util.concurrent.ConcurrentHashMap<String, Boolean>> systemMatchCache = new java.util.concurrent.ConcurrentHashMap<>();

    private boolean systemMatches(String systemId) {
        if (system == null && systemPattern == null) {
            return true;
        }
        java.util.concurrent.ConcurrentHashMap<String, Boolean> bySystem = systemMatchCache
            .computeIfAbsent(this, key -> new java.util.concurrent.ConcurrentHashMap<>());
        Boolean cached = bySystem.get(systemId);
        if (cached != null) {
            return cached;
        }
        // Compute outside the bin lock: racing threads may duplicate the regex
        // evaluation, but the verdict is deterministic and lock convoys during
        // parallel graph building cost far more than a few duplicate matches.
        boolean verdict = systemMatchesUncached(systemId);
        bySystem.putIfAbsent(systemId, verdict);
        return verdict;
    }

    private boolean systemMatchesUncached(String systemId) {
        if (system != null && !system.equals(systemId)) {
            return false;
        }
        return systemPattern == null || systemPattern.matcher(systemId).find();
    }

    @Override
    public void classify(RecipeClassifyView recipe, ClassificationContext context, ClassificationSink sink) {
        if (!matches(recipe)) {
            return;
        }
        for (String tag : tags) {
            sink.tag(tag);
        }
    }

    public boolean matches(RecipeClassifyView recipe) {
        if (!systemMatches(recipe.systemId())) {
            return false;
        }
        if (recipeIdPattern != null && (recipe.recipeId().isEmpty() || !recipeIdPattern.matcher(recipe.recipeId()).find())) {
            return false;
        }
        if (recipePathPattern != null && (recipe.recipeId().isEmpty() || !recipePathPattern.matcher(recipe.recipePath()).find())) {
            return false;
        }
        return mod == null || mod.equals(recipe.recipeMod());
    }

    /** Matches only against a system id; used for system-level dispositions. */
    public boolean matchesSystem(String systemId) {
        if (recipeIdPattern != null || recipePathPattern != null || mod != null) {
            return false;
        }
        return systemMatches(systemId);
    }

    /** Parses one rule object; returns null when the object is unusable. */
    public static @Nullable PatternClassificationRule fromJson(JsonObject object) {
        if (object == null || !object.has("id")) {
            return null;
        }
        String id = object.get("id").getAsString();
        String system = optString(object, "system");
        Pattern systemPattern = optPattern(object, "systemPattern");
        Pattern recipeIdPattern = optPattern(object, "recipeIdPattern");
        Pattern recipePathPattern = optPattern(object, "recipePathPattern");
        String mod = optString(object, "mod");
        List<String> tags = new ArrayList<>();
        if (object.has("tags") && object.get("tags").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("tags")) {
                if (element.isJsonPrimitive()) {
                    tags.add(element.getAsString());
                }
            }
        }
        if (tags.isEmpty()) {
            return null;
        }
        return new PatternClassificationRule(id, system, systemPattern, recipeIdPattern, recipePathPattern, mod, tags);
    }

    /** Parses a {@code {"version":1,"rules":[...]}} document. */
    public static List<PatternClassificationRule> listFromJson(JsonObject document) {
        List<PatternClassificationRule> rules = new ArrayList<>();
        if (document == null || !document.has("rules")) {
            return rules;
        }
        JsonArray array = document.getAsJsonArray("rules");
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                PatternClassificationRule rule = fromJson(element.getAsJsonObject());
                if (rule != null) {
                    rules.add(rule);
                }
            }
        }
        return rules;
    }

    private static @Nullable String optString(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
    }

    private static @Nullable Pattern optPattern(JsonObject object, String key) {
        String value = optString(object, key);
        return value == null ? null : Pattern.compile(value);
    }
}
