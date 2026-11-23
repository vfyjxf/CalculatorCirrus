package dev.vfyjxf.cirrus.planning.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.vfyjxf.cirrus.api.planning.graph.RawIngredientSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawOutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassifyContent;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class RawRecordMapper {

    public static final long FLUID_TAG_FALLBACK_AMOUNT = 1000L;

    public record TagTypeRegistry(Set<String> fluidTags, Set<String> itemTags, boolean present) {
        public static final TagTypeRegistry ABSENT = new TagTypeRegistry(Set.of(), Set.of(), false);

        public TagTypeRegistry {
            fluidTags = fluidTags == null ? Set.of() : Set.copyOf(fluidTags);
            itemTags = itemTags == null ? Set.of() : Set.copyOf(itemTags);
        }

        public String resolveTagContentType(String tagId, long slotAmount) {
            if (!present) {
                return slotAmount == FLUID_TAG_FALLBACK_AMOUNT
                        ? StackIdentity.FLUID_CONTENT_TYPE
                        : StackIdentity.ITEM_CONTENT_TYPE;
            }
            boolean inFluid = fluidTags.contains(tagId);
            boolean inItem = itemTags.contains(tagId);
            if (inFluid && !inItem) {
                return StackIdentity.FLUID_CONTENT_TYPE;
            }
            if (inFluid && inItem && slotAmount == FLUID_TAG_FALLBACK_AMOUNT) {
                return StackIdentity.FLUID_CONTENT_TYPE;
            }
            return StackIdentity.ITEM_CONTENT_TYPE;
        }
    }

    public static final class CandidatePool {
        private final Map<String, ResourceKey> bareItems = new HashMap<>(4096);
        private final Map<String, ResourceKey> bareFluids = new HashMap<>(512);
        private final Map<List<ResourceKey>, List<ResourceKey>> internedLists = new HashMap<>(2048);
        private final Map<Set<String>, Set<String>> internedIdSets = new HashMap<>(2048);

        public ResourceKey internBare(String contentTypeId, String resourceId) {
            if (StackIdentity.ITEM_CONTENT_TYPE.equals(contentTypeId)) {
                return bareItems.computeIfAbsent(resourceId, ResourceKey::item);
            }
            if (StackIdentity.FLUID_CONTENT_TYPE.equals(contentTypeId)) {
                return bareFluids.computeIfAbsent(resourceId, ResourceKey::fluid);
            }
            return new ResourceKey(contentTypeId, resourceId, Optional.empty());
        }

        public List<ResourceKey> internList(List<ResourceKey> candidates) {
            if (candidates.size() == 1) {
                return List.of(candidates.get(0));
            }
            List<ResourceKey> existing = internedLists.get(candidates);
            if (existing != null) {
                return existing;
            }
            List<ResourceKey> frozen = List.copyOf(candidates);
            internedLists.put(frozen, frozen);
            return frozen;
        }

        public Set<String> internIdSet(Set<String> ids) {
            if (ids.isEmpty()) {
                return Set.of();
            }
            Set<String> existing = internedIdSets.get(ids);
            if (existing != null) {
                return existing;
            }
            Set<String> frozen = Set.copyOf(ids);
            internedIdSets.put(frozen, frozen);
            return frozen;
        }
    }

    public record MappingResult(
            @Nullable RawRecipeRecord record,
            @Nullable RecipeClassifyView classifyView,
            @Nullable DropReason dropReason
    ) {
        public static MappingResult kept(RawRecipeRecord record, RecipeClassifyView view) {
            return new MappingResult(
                    Objects.requireNonNull(record, "record"),
                    Objects.requireNonNull(view, "view"),
                    null
            );
        }

        public static MappingResult dropped(DropReason reason) {
            return new MappingResult(null, null, Objects.requireNonNull(reason, "reason"));
        }

        public boolean isKept() {
            return record != null;
        }
    }

    private final TagTypeRegistry tagRegistry;
    private final CandidatePool pool;

    public RawRecordMapper(TagTypeRegistry tagRegistry, CandidatePool pool) {
        this.tagRegistry = Objects.requireNonNull(tagRegistry, "tagRegistry");
        this.pool = Objects.requireNonNull(pool, "pool");
    }

    public RawRecordMapper() {
        this(TagTypeRegistry.ABSENT, new CandidatePool());
    }

    public MappingResult mapJsonRecipe(JsonObject obj, long sourceRef, int classifyIndex) {
        String systemId = stringOrEmpty(obj, "system");
        String sourceKind = stringOrEmpty(obj, "source");
        if (sourceKind.isEmpty()) {
            sourceKind = systemId.startsWith("jei:") ? "jei" : "custom";
        }
        String explicitId = stringOrEmpty(obj, "id");

        JsonArray machinesArr = arrayOrNull(obj, "machines");
        List<String> machines = parseMachines(machinesArr);

        JsonArray inputsArr = arrayOrNull(obj, "inputs");
        JsonArray outputsArr = arrayOrNull(obj, "outputs");
        if (inputsArr == null || inputsArr.isEmpty() || outputsArr == null || outputsArr.isEmpty()) {
            return MappingResult.dropped(DropReason.EMPTY_SLOT_LISTS);
        }

        List<RawIngredientSlot> inputs = new ArrayList<>(inputsArr.size());
        List<RawOutputSlot> outputs = new ArrayList<>(outputsArr.size());
        List<RawOutputSlot> catalystReturnOutputs = null;
        Map<String, Double> recipeValues = null;
        List<ClassifyContent> classifyInputs = new ArrayList<>(inputsArr.size());
        List<ClassifyContent> classifyOutputs = new ArrayList<>(outputsArr.size());

        for (int i = 0; i < inputsArr.size(); i++) {
            JsonElement elem = inputsArr.get(i);
            if (!elem.isJsonObject()) {
                return MappingResult.dropped(DropReason.UNKNOWN_KIND);
            }
            JsonObject slot = elem.getAsJsonObject();
            String kind = stringOrEmpty(slot, "kind");
            double chance = parseChance(slot);
            if (!Double.isFinite(chance) || chance < 0.0 || chance > 1.0 || (chance > 0.0 && chance < 1.0)) {
                return MappingResult.dropped(DropReason.PROBABILISTIC);
            }
            boolean catalyst = chance == 0.0;

            switch (kind) {
                case "value" -> {
                    String valueType = stringOrEmpty(slot, "valueType");
                    Double numeric = parseFiniteDouble(slot.get("value"));
                    if (valueType.isEmpty() || numeric == null) {
                        return MappingResult.dropped(DropReason.INVALID_AMOUNT);
                    }
                    if (!catalyst) {
                        if (recipeValues == null) {
                            recipeValues = new LinkedHashMap<>();
                        }
                        recipeValues.merge(valueType, numeric, Math::max);
                    }
                    classifyInputs.add(new ClassifyContent(ClassifyContent.Kind.value, Set.of(), null, numeric, chance));
                }
                case "complex" -> {
                    return MappingResult.dropped(DropReason.COMPLEX_SLOT);
                }
                case "stack" -> {
                    Long amount = parseExactPositiveLong(slot.get("amount"));
                    if (amount == null) {
                        return MappingResult.dropped(DropReason.INVALID_AMOUNT);
                    }
                    Optional<StackIdentity.MatchFlags> flags = StackIdentity.MatchFlags.parse(stringOrNull(slot, "matchStrategy"));
                    if (flags.isEmpty() || flags.get().toSlotMatchMode().isEmpty()) {
                        return MappingResult.dropped(DropReason.UNSUPPORTED_MATCH_STRATEGY);
                    }
                    JsonObject stackObj = objectOrNull(slot, "stack");
                    if (stackObj == null) {
                        return MappingResult.dropped(DropReason.INVALID_STACK);
                    }
                    StackIdentity.MappedStack mapped = mapStackObject(stackObj, flags.get(), true);
                    if (mapped.key().isEmpty()) {
                        return MappingResult.dropped(DropReason.INVALID_STACK);
                    }
                    ResourceKey key = mapped.key().get();
                    SlotMatchMode mode = mapped.mode().orElseThrow();
                    inputs.add(new RawIngredientSlot(Optional.empty(), List.of(key), amount, mode));
                    if (catalyst) {
                        if (catalystReturnOutputs == null) {
                            catalystReturnOutputs = new ArrayList<>(2);
                        }
                        catalystReturnOutputs.add(new RawOutputSlot(key, amount));
                    }
                    classifyInputs.add(ClassifyContent.stack(key.resourceId(), amount, chance));
                }
                case "tag" -> {
                    Long amount = parseExactPositiveLong(slot.get("amount"));
                    if (amount == null) {
                        return MappingResult.dropped(DropReason.INVALID_AMOUNT);
                    }
                    String tagId = stringOrEmpty(slot, "tag");
                    JsonArray valuesArr = arrayOrNull(slot, "values");
                    if (tagId.isEmpty() || valuesArr == null || valuesArr.isEmpty()) {
                        return MappingResult.dropped(DropReason.EMPTY_SLOT_CANDIDATES);
                    }
                    String contentTypeId = tagRegistry.resolveTagContentType(tagId, amount);
                    LinkedHashSet<ResourceKey> unique = new LinkedHashSet<>(valuesArr.size());
                    LinkedHashSet<String> idStrings = new LinkedHashSet<>(valuesArr.size());
                    for (int j = 0; j < valuesArr.size(); j++) {
                        JsonElement ve = valuesArr.get(j);
                        if (ve.isJsonPrimitive() && ve.getAsJsonPrimitive().isString()) {
                            String rawId = ve.getAsString().trim();
                            if (!rawId.isEmpty()) {
                                unique.add(pool.internBare(contentTypeId, rawId));
                                idStrings.add(rawId);
                            }
                        }
                    }
                    if (unique.isEmpty()) {
                        return MappingResult.dropped(DropReason.EMPTY_SLOT_CANDIDATES);
                    }
                    List<ResourceKey> candidates = pool.internList(new ArrayList<>(unique));
                    if (catalyst) {
                        if (candidates.size() == 1) {
                            inputs.add(new RawIngredientSlot(Optional.empty(), candidates, amount, SlotMatchMode.IDENTITY));
                            if (catalystReturnOutputs == null) {
                                catalystReturnOutputs = new ArrayList<>(2);
                            }
                            catalystReturnOutputs.add(new RawOutputSlot(candidates.get(0), amount));
                        } else {
                            inputs.add(new RawIngredientSlot(Optional.of(tagId), candidates, 0L, SlotMatchMode.IDENTITY, 0, 1));
                        }
                    } else {
                        inputs.add(new RawIngredientSlot(Optional.of(tagId), candidates, amount, SlotMatchMode.IDENTITY));
                    }
                    classifyInputs.add(ClassifyContent.tag(tagId, pool.internIdSet(idStrings), amount, chance));
                }
                case "any", "typedAny" -> {
                    Long amount = parseExactPositiveLong(slot.get("amount"));
                    if (amount == null) {
                        return MappingResult.dropped(DropReason.INVALID_AMOUNT);
                    }
                    JsonArray stacksArr = arrayOrNull(slot, "stacks");
                    if (stacksArr == null || stacksArr.isEmpty()) {
                        return MappingResult.dropped(DropReason.EMPTY_SLOT_CANDIDATES);
                    }
                    LinkedHashSet<ResourceKey> unique = new LinkedHashSet<>(stacksArr.size());
                    LinkedHashSet<String> idStrings = new LinkedHashSet<>(stacksArr.size());
                    for (int j = 0; j < stacksArr.size(); j++) {
                        JsonElement se = stacksArr.get(j);
                        if (!se.isJsonObject()) {
                            return MappingResult.dropped(DropReason.INVALID_STACK);
                        }
                        Optional<ResourceKey> key = mapConcreteStackObject(se.getAsJsonObject());
                        if (key.isEmpty()) {
                            return MappingResult.dropped(DropReason.INVALID_STACK);
                        }
                        unique.add(key.get());
                        idStrings.add(key.get().resourceId());
                    }
                    if (unique.isEmpty()) {
                        return MappingResult.dropped(DropReason.EMPTY_SLOT_CANDIDATES);
                    }
                    List<ResourceKey> candidates = pool.internList(new ArrayList<>(unique));
                    SlotMatchMode mode = StackIdentity.listSlotMatchMode(candidates);
                    if (catalyst) {
                        if (candidates.size() == 1) {
                            inputs.add(new RawIngredientSlot(Optional.empty(), candidates, amount, mode));
                            if (catalystReturnOutputs == null) {
                                catalystReturnOutputs = new ArrayList<>(2);
                            }
                            catalystReturnOutputs.add(new RawOutputSlot(candidates.get(0), amount));
                        } else {
                            inputs.add(new RawIngredientSlot(Optional.empty(), candidates, 0L, mode, 0, 1));
                        }
                    } else {
                        inputs.add(new RawIngredientSlot(Optional.empty(), candidates, amount, mode));
                    }
                    ClassifyContent.Kind cKind = "typedAny".equals(kind)
                            ? ClassifyContent.Kind.typedAny
                            : ClassifyContent.Kind.any;
                    classifyInputs.add(new ClassifyContent(cKind, pool.internIdSet(idStrings), null, amount, chance));
                }
                default -> {
                    return MappingResult.dropped(DropReason.UNKNOWN_KIND);
                }
            }
        }

        if (inputs.isEmpty()) {
            return MappingResult.dropped(DropReason.NO_INPUT_AFTER_VALUES);
        }

        for (int i = 0; i < outputsArr.size(); i++) {
            JsonElement elem = outputsArr.get(i);
            if (!elem.isJsonObject()) {
                return MappingResult.dropped(DropReason.UNKNOWN_KIND);
            }
            JsonObject slot = elem.getAsJsonObject();
            String kind = stringOrEmpty(slot, "kind");
            double chance = parseChance(slot);
            if (!Double.isFinite(chance) || chance < 0.0 || chance > 1.0 || (chance > 0.0 && chance < 1.0)) {
                return MappingResult.dropped(DropReason.PROBABILISTIC);
            }
            boolean neverProduced = chance == 0.0;

            switch (kind) {
                case "value" -> {
                    String valueType = stringOrEmpty(slot, "valueType");
                    Double numeric = parseFiniteDouble(slot.get("value"));
                    if (valueType.isEmpty() || numeric == null) {
                        return MappingResult.dropped(DropReason.INVALID_AMOUNT);
                    }
                    if (!neverProduced) {
                        if (recipeValues == null) {
                            recipeValues = new LinkedHashMap<>();
                        }
                        recipeValues.merge(valueType, numeric, Math::max);
                    }
                    classifyOutputs.add(new ClassifyContent(ClassifyContent.Kind.value, Set.of(), null, numeric, chance));
                }
                case "complex" -> {
                    return MappingResult.dropped(DropReason.COMPLEX_SLOT);
                }
                case "stack" -> {
                    Long amount = parseExactPositiveLong(slot.get("amount"));
                    if (amount == null) {
                        return MappingResult.dropped(DropReason.INVALID_AMOUNT);
                    }
                    JsonObject stackObj = objectOrNull(slot, "stack");
                    if (stackObj == null) {
                        return MappingResult.dropped(DropReason.INVALID_STACK);
                    }
                    Optional<ResourceKey> key = mapConcreteStackObject(stackObj);
                    if (key.isEmpty()) {
                        return MappingResult.dropped(DropReason.INVALID_STACK);
                    }
                    if (!neverProduced) {
                        outputs.add(new RawOutputSlot(key.get(), amount));
                        classifyOutputs.add(ClassifyContent.stack(key.get().resourceId(), amount, chance));
                    }
                }
                case "tag" -> {
                    Long amount = parseExactPositiveLong(slot.get("amount"));
                    if (amount == null) {
                        return MappingResult.dropped(DropReason.INVALID_AMOUNT);
                    }
                    String tagId = stringOrEmpty(slot, "tag");
                    JsonArray valuesArr = arrayOrNull(slot, "values");
                    if (tagId.isEmpty() || valuesArr == null || valuesArr.isEmpty()) {
                        return MappingResult.dropped(DropReason.EMPTY_SLOT_CANDIDATES);
                    }
                    String contentTypeId = tagRegistry.resolveTagContentType(tagId, amount);
                    LinkedHashSet<ResourceKey> unique = new LinkedHashSet<>(valuesArr.size());
                    for (int j = 0; j < valuesArr.size(); j++) {
                        JsonElement ve = valuesArr.get(j);
                        if (ve.isJsonPrimitive() && ve.getAsJsonPrimitive().isString()) {
                            String rawId = ve.getAsString().trim();
                            if (!rawId.isEmpty()) {
                                unique.add(pool.internBare(contentTypeId, rawId));
                            }
                        }
                    }
                    if (unique.isEmpty()) {
                        return MappingResult.dropped(DropReason.EMPTY_SLOT_CANDIDATES);
                    }
                    if (unique.size() > 1) {
                        return MappingResult.dropped(DropReason.TAG_OUTPUT_MULTI);
                    }
                    ResourceKey only = unique.iterator().next();
                    if (!neverProduced) {
                        outputs.add(new RawOutputSlot(only, amount));
                        classifyOutputs.add(ClassifyContent.stack(only.resourceId(), amount, chance));
                    }
                }
                case "any", "typedAny" -> {
                    Long amount = parseExactPositiveLong(slot.get("amount"));
                    if (amount == null) {
                        return MappingResult.dropped(DropReason.INVALID_AMOUNT);
                    }
                    JsonArray stacksArr = arrayOrNull(slot, "stacks");
                    if (stacksArr == null || stacksArr.isEmpty()) {
                        return MappingResult.dropped(DropReason.EMPTY_SLOT_CANDIDATES);
                    }
                    LinkedHashSet<ResourceKey> unique = new LinkedHashSet<>(stacksArr.size());
                    for (int j = 0; j < stacksArr.size(); j++) {
                        JsonElement se = stacksArr.get(j);
                        if (!se.isJsonObject()) {
                            return MappingResult.dropped(DropReason.INVALID_STACK);
                        }
                        Optional<ResourceKey> key = mapConcreteStackObject(se.getAsJsonObject());
                        if (key.isEmpty()) {
                            return MappingResult.dropped(DropReason.INVALID_STACK);
                        }
                        unique.add(key.get());
                    }
                    if (unique.isEmpty()) {
                        return MappingResult.dropped(DropReason.EMPTY_SLOT_CANDIDATES);
                    }
                    if (unique.size() > 1) {
                        return MappingResult.dropped(DropReason.MULTI_STACK_OUTPUT);
                    }
                    ResourceKey only = unique.iterator().next();
                    if (!neverProduced) {
                        outputs.add(new RawOutputSlot(only, amount));
                        classifyOutputs.add(ClassifyContent.stack(only.resourceId(), amount, chance));
                    }
                }
                default -> {
                    return MappingResult.dropped(DropReason.UNKNOWN_KIND);
                }
            }
        }

        if (outputs.isEmpty()) {
            return MappingResult.dropped(DropReason.NO_OUTPUT_AFTER_VALUES);
        }
        if (catalystReturnOutputs != null) {
            outputs.addAll(catalystReturnOutputs);
        }

        RawRecipeRecord record = new RawRecipeRecord(
                systemId,
                sourceKind,
                explicitId.isEmpty() ? Optional.empty() : Optional.of(explicitId),
                machines,
                inputs,
                outputs,
                recipeValues == null ? Map.of() : recipeValues,
                sourceRef
        );
        RecipeClassifyView view = new RecipeClassifyView(
                classifyIndex,
                systemId,
                explicitId,
                machines,
                classifyInputs,
                classifyOutputs
        );
        return MappingResult.kept(record, view);
    }

    private StackIdentity.MappedStack mapStackObject(
            JsonObject stackObj,
            StackIdentity.MatchFlags matchFlags,
            boolean internBare
    ) {
        String rawType = stringOrEmpty(stackObj, "type");
        String contentTypeId = StackIdentity.normalizeContentTypeId(rawType);
        String resourceId = extractStackResourceId(stackObj, contentTypeId);
        if (contentTypeId.isEmpty() || resourceId.isEmpty()) {
            return new StackIdentity.MappedStack(Optional.empty(), matchFlags.toSlotMatchMode());
        }
        JsonElement components = stackObj.get("components");
        if (internBare && (components == null || components.isJsonNull()
                || (components.isJsonObject() && components.getAsJsonObject().isEmpty())
                || matchFlags.toSlotMatchMode().orElse(SlotMatchMode.IDENTITY) == SlotMatchMode.IDENTITY)) {
            Optional<SlotMatchMode> mode = matchFlags.toSlotMatchMode();
            return new StackIdentity.MappedStack(
                    Optional.of(pool.internBare(contentTypeId, resourceId)),
                    mode
            );
        }
        return StackIdentity.mapInput(new StackIdentity.StackSpec(contentTypeId, resourceId, components, matchFlags));
    }

    private Optional<ResourceKey> mapConcreteStackObject(JsonObject stackObj) {
        String rawType = stringOrEmpty(stackObj, "type");
        String contentTypeId = StackIdentity.normalizeContentTypeId(rawType);
        String resourceId = extractStackResourceId(stackObj, contentTypeId);
        if (contentTypeId.isEmpty() || resourceId.isEmpty()) {
            return Optional.empty();
        }
        JsonElement components = stackObj.get("components");
        if (components == null || components.isJsonNull()
                || (components.isJsonObject() && components.getAsJsonObject().isEmpty())) {
            return Optional.of(pool.internBare(contentTypeId, resourceId));
        }
        return StackIdentity.mapConcrete(contentTypeId, resourceId, components);
    }

    private static String extractStackResourceId(JsonObject stackObj, String contentTypeId) {
        if (StackIdentity.ITEM_CONTENT_TYPE.equals(contentTypeId)) {
            return stringOrEmpty(stackObj, "id");
        }
        if (StackIdentity.FLUID_CONTENT_TYPE.equals(contentTypeId)) {
            String fluid = stringOrEmpty(stackObj, "fluid");
            return !fluid.isEmpty() ? fluid : stringOrEmpty(stackObj, "id");
        }
        String id = stringOrEmpty(stackObj, "id");
        if (!id.isEmpty()) {
            return id;
        }
        String value = stringOrEmpty(stackObj, "value");
        if (value.isEmpty()) {
            return "";
        }
        if (value.indexOf(':') >= 0) {
            return value;
        }
        String sanitized = sanitizeCustomToken(value);
        return sanitized.isEmpty() ? "" : contentTypeId + "/" + sanitized;
    }

    private static String sanitizeCustomToken(String value) {
        String lower = value.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-' || c == '/') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    private static List<String> parseMachines(@Nullable JsonArray arr) {
        if (arr == null || arr.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            JsonElement e = arr.get(i);
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                String m = e.getAsString().trim();
                if (!m.isEmpty()) {
                    out.add(m);
                }
            }
        }
        return out.isEmpty() ? List.of() : Collections.unmodifiableList(out);
    }

    private static double parseChance(JsonObject slot) {
        JsonElement elem = slot.get("chance");
        if (elem == null || elem.isJsonNull()) {
            return 1.0;
        }
        if (!elem.isJsonPrimitive() || !elem.getAsJsonPrimitive().isNumber()) {
            return Double.NaN;
        }
        return elem.getAsDouble();
    }

    public static @Nullable Long parseExactPositiveLong(@Nullable JsonElement elem) {
        if (elem == null || elem.isJsonNull() || !elem.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive prim = elem.getAsJsonPrimitive();
        if (!prim.isNumber()) {
            return null;
        }
        String raw = prim.getAsString().trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            if (raw.indexOf('.') < 0 && raw.indexOf('e') < 0 && raw.indexOf('E') < 0) {
                long v = Long.parseLong(raw);
                return v > 0L ? v : null;
            }
            BigDecimal bd = new BigDecimal(raw);
            if (bd.signum() <= 0) {
                return null;
            }
            BigDecimal stripped = bd.stripTrailingZeros();
            if (stripped.scale() > 0) {
                return null;
            }
            long v = stripped.longValueExact();
            return v > 0L ? v : null;
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    private static @Nullable Double parseFiniteDouble(@Nullable JsonElement elem) {
        if (elem == null || elem.isJsonNull() || !elem.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive prim = elem.getAsJsonPrimitive();
        if (!prim.isNumber()) {
            return null;
        }
        double d = prim.getAsDouble();
        return Double.isFinite(d) ? d : null;
    }

    private static String stringOrEmpty(JsonObject obj, String field) {
        String s = stringOrNull(obj, field);
        return s == null ? "" : s;
    }

    private static @Nullable String stringOrNull(JsonObject obj, String field) {
        JsonElement elem = obj.get(field);
        if (elem == null || elem.isJsonNull() || !elem.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive prim = elem.getAsJsonPrimitive();
        if (!prim.isString()) {
            return null;
        }
        return prim.getAsString().trim();
    }

    private static @Nullable JsonArray arrayOrNull(JsonObject obj, String field) {
        JsonElement elem = obj.get(field);
        return (elem != null && elem.isJsonArray()) ? elem.getAsJsonArray() : null;
    }

    private static @Nullable JsonObject objectOrNull(JsonObject obj, String field) {
        JsonElement elem = obj.get(field);
        return (elem != null && elem.isJsonObject()) ? elem.getAsJsonObject() : null;
    }
}
