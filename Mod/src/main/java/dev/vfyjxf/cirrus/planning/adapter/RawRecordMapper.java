package dev.vfyjxf.cirrus.planning.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.graph.RawIngredientSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawOutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassifyContent;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.Nullable;

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

    public static final long fluidTagFallbackAmount = 1000L;

    public record TagTypeRegistry(Set<String> fluidTags, Set<String> itemTags, boolean present) {
        public static final TagTypeRegistry absent = new TagTypeRegistry(Set.of(), Set.of(), false);

        public TagTypeRegistry {
            fluidTags = fluidTags == null ? Set.of() : Set.copyOf(fluidTags);
            itemTags = itemTags == null ? Set.of() : Set.copyOf(itemTags);
        }

        public Namespace resolveTagContentType(String tagId, long slotAmount) {
            if (!present) {
                return slotAmount == fluidTagFallbackAmount
                        ? OfflineContentTypes.fluidContentType
                        : OfflineContentTypes.itemContentType;
            }
            boolean inFluid = fluidTags.contains(tagId);
            boolean inItem = itemTags.contains(tagId);
            if (inFluid && !inItem) {
                return OfflineContentTypes.fluidContentType;
            }
            if (inFluid && inItem && slotAmount == fluidTagFallbackAmount) {
                return OfflineContentTypes.fluidContentType;
            }
            return OfflineContentTypes.itemContentType;
        }
    }

    public static final class CandidatePool {
        private final Map<String, ComponentStackKey> bareItems = new HashMap<>(4096);
        private final Map<String, ComponentStackKey> bareFluids = new HashMap<>(512);
        private final Map<List<ComponentStackKey>, List<ComponentStackKey>> internedLists = new HashMap<>(2048);
        private final Map<Set<String>, Set<String>> internedIdSets = new HashMap<>(2048);

        public ComponentStackKey internBare(Namespace contentTypeId, String resourceId) {
            if (OfflineContentTypes.itemContentType.equals(contentTypeId)) {
                return bareItems.computeIfAbsent(resourceId, OfflineContentTypes::item);
            }
            if (OfflineContentTypes.fluidContentType.equals(contentTypeId)) {
                return bareFluids.computeIfAbsent(resourceId, OfflineContentTypes::fluid);
            }
            return OfflineContentTypes.bare(contentTypeId, resourceId);
        }

        public List<ComponentStackKey> internList(List<ComponentStackKey> candidates) {
            if (candidates.size() == 1) {
                return List.of(candidates.get(0));
            }
            List<ComponentStackKey> existing = internedLists.get(candidates);
            if (existing != null) {
                return existing;
            }
            List<ComponentStackKey> frozen = List.copyOf(candidates);
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
        this(TagTypeRegistry.absent, new CandidatePool());
    }

    public MappingResult mapJsonRecipe(JsonObject obj, long sourceRef, int classifyIndex) {
        String systemIdStr = stringOrEmpty(obj, "system");
        Namespace systemId = OfflineContentTypes.parseNamespace(systemIdStr);
        String sourceKind = stringOrEmpty(obj, "source");
        if (sourceKind.isEmpty()) {
            sourceKind = systemIdStr.startsWith("jei:") ? "jei" : "custom";
        }
        String explicitId = stringOrEmpty(obj, "id");

        JsonArray machinesArr = arrayOrNull(obj, "machines");
        List<String> machineIds = parseMachines(machinesArr);
        List<MachineEntry> machines = toMachineEntries(machineIds);

        JsonArray inputsArr = arrayOrNull(obj, "inputs");
        JsonArray outputsArr = arrayOrNull(obj, "outputs");
        if (inputsArr == null || inputsArr.isEmpty() || outputsArr == null || outputsArr.isEmpty()) {
            return MappingResult.dropped(DropReason.emptySlotLists);
        }

        List<RawIngredientSlot> inputs = new ArrayList<>(inputsArr.size());
        List<RawOutputSlot> outputs = new ArrayList<>(outputsArr.size());
        List<RawOutputSlot> catalystReturnOutputs = null;
        Map<Namespace, Double> recipeValues = null;
        List<ClassifyContent> classifyInputs = new ArrayList<>(inputsArr.size());
        List<ClassifyContent> classifyOutputs = new ArrayList<>(outputsArr.size());

        for (int i = 0; i < inputsArr.size(); i++) {
            JsonElement elem = inputsArr.get(i);
            if (!elem.isJsonObject()) {
                return MappingResult.dropped(DropReason.unknownKind);
            }
            JsonObject slot = elem.getAsJsonObject();
            String kind = stringOrEmpty(slot, "kind");
            double chance = parseChance(slot);
            if (!Double.isFinite(chance) || chance < 0.0 || chance > 1.0 || (chance > 0.0 && chance < 1.0)) {
                return MappingResult.dropped(DropReason.probabilistic);
            }
            boolean catalyst = chance == 0.0;

            switch (kind) {
                case "value" -> {
                    String valueType = stringOrEmpty(slot, "valueType");
                    Double numeric = parseFiniteDouble(slot.get("value"));
                    if (valueType.isEmpty() || numeric == null) {
                        return MappingResult.dropped(DropReason.invalidAmount);
                    }
                    if (!catalyst) {
                        if (recipeValues == null) {
                            recipeValues = new LinkedHashMap<>();
                        }
                        recipeValues.merge(OfflineContentTypes.parseNamespace(valueType), numeric, Double::sum);
                    }
                    classifyInputs
                            .add(new ClassifyContent(ClassifyContent.Kind.value, Set.of(), null, numeric, chance));
                }
                case "complex" -> {
                    return MappingResult.dropped(DropReason.complexSlot);
                }
                case "stack" -> {
                    Long amount = parseExactPositiveLong(slot.get("amount"));
                    if (amount == null) {
                        return MappingResult.dropped(DropReason.invalidAmount);
                    }
                    Optional<SlotMatchMode> modeOpt = OfflineContentTypes
                            .parseMatchStrategy(stringOrNull(slot, "matchStrategy"));
                    if (modeOpt.isEmpty()) {
                        return MappingResult.dropped(DropReason.unsupportedMatchStrategy);
                    }
                    JsonObject stackObj = objectOrNull(slot, "stack");
                    if (stackObj == null) {
                        return MappingResult.dropped(DropReason.invalidStack);
                    }
                    SlotMatchMode mode = modeOpt.get();
                    Optional<ComponentStackKey> keyOpt = mapStackObject(stackObj, mode);
                    if (keyOpt.isEmpty()) {
                        return MappingResult.dropped(DropReason.invalidStack);
                    }
                    ComponentStackKey key = keyOpt.get();
                    inputs.add(new RawIngredientSlot(Optional.empty(), List.of(key), amount, mode));
                    if (catalyst) {
                        if (catalystReturnOutputs == null) {
                            catalystReturnOutputs = new ArrayList<>(2);
                        }
                        catalystReturnOutputs.add(new RawOutputSlot(key, amount));
                    }
                    classifyInputs.add(ClassifyContent.stack(key.displayId(), amount, chance));
                }
                case "tag" -> {
                    Long amount = parseExactPositiveLong(slot.get("amount"));
                    if (amount == null) {
                        return MappingResult.dropped(DropReason.invalidAmount);
                    }
                    String tagId = stringOrEmpty(slot, "tag");
                    JsonArray valuesArr = arrayOrNull(slot, "values");
                    if (tagId.isEmpty() || valuesArr == null || valuesArr.isEmpty()) {
                        return MappingResult.dropped(DropReason.emptySlotCandidates);
                    }
                    Namespace contentTypeId = tagRegistry.resolveTagContentType(tagId, amount);
                    TagKey<?> tagKey = OfflineContentTypes.tagFor(contentTypeId, tagId);
                    LinkedHashSet<ComponentStackKey> unique = new LinkedHashSet<>(valuesArr.size());
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
                        return MappingResult.dropped(DropReason.emptySlotCandidates);
                    }
                    List<ComponentStackKey> candidates = pool.internList(new ArrayList<>(unique));
                    if (catalyst) {
                        if (candidates.size() == 1) {
                            inputs.add(
                                new RawIngredientSlot(Optional.empty(), candidates, amount, SlotMatchMode.identityOnly)
                            );
                            if (catalystReturnOutputs == null) {
                                catalystReturnOutputs = new ArrayList<>(2);
                            }
                            catalystReturnOutputs.add(new RawOutputSlot(candidates.get(0), amount));
                        } else {
                            inputs.add(
                                new RawIngredientSlot(Optional.of(tagKey), candidates, 0L, SlotMatchMode.identityOnly, 0, 1)
                            );
                        }
                    } else {
                        inputs.add(
                            new RawIngredientSlot(Optional.of(tagKey), candidates, amount, SlotMatchMode.identityOnly)
                        );
                    }
                    classifyInputs.add(ClassifyContent.tag(tagId, pool.internIdSet(idStrings), amount, chance));
                }
                case "any", "typedAny" -> {
                    Long amount = parseExactPositiveLong(slot.get("amount"));
                    if (amount == null) {
                        return MappingResult.dropped(DropReason.invalidAmount);
                    }
                    JsonArray stacksArr = arrayOrNull(slot, "stacks");
                    if (stacksArr == null || stacksArr.isEmpty()) {
                        return MappingResult.dropped(DropReason.emptySlotCandidates);
                    }
                    LinkedHashSet<ComponentStackKey> unique = new LinkedHashSet<>(stacksArr.size());
                    LinkedHashSet<String> idStrings = new LinkedHashSet<>(stacksArr.size());
                    for (int j = 0; j < stacksArr.size(); j++) {
                        JsonElement se = stacksArr.get(j);
                        if (!se.isJsonObject()) {
                            return MappingResult.dropped(DropReason.invalidStack);
                        }
                        Optional<ComponentStackKey> key = mapConcreteStackObject(se.getAsJsonObject());
                        if (key.isEmpty()) {
                            return MappingResult.dropped(DropReason.invalidStack);
                        }
                        unique.add(key.get());
                        idStrings.add(key.get().displayId());
                    }
                    if (unique.isEmpty()) {
                        return MappingResult.dropped(DropReason.emptySlotCandidates);
                    }
                    List<ComponentStackKey> candidates = pool.internList(new ArrayList<>(unique));
                    SlotMatchMode mode = OfflineContentTypes.listSlotMatchMode(candidates);
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
                    return MappingResult.dropped(DropReason.unknownKind);
                }
            }
        }

        if (inputs.isEmpty()) {
            return MappingResult.dropped(DropReason.noInputAfterValues);
        }

        for (int i = 0; i < outputsArr.size(); i++) {
            JsonElement elem = outputsArr.get(i);
            if (!elem.isJsonObject()) {
                return MappingResult.dropped(DropReason.unknownKind);
            }
            JsonObject slot = elem.getAsJsonObject();
            String kind = stringOrEmpty(slot, "kind");
            double chance = parseChance(slot);
            if (!Double.isFinite(chance) || chance < 0.0 || chance > 1.0 || (chance > 0.0 && chance < 1.0)) {
                return MappingResult.dropped(DropReason.probabilistic);
            }
            boolean neverProduced = chance == 0.0;

            switch (kind) {
                case "value" -> {
                    String valueType = stringOrEmpty(slot, "valueType");
                    Double numeric = parseFiniteDouble(slot.get("value"));
                    if (valueType.isEmpty() || numeric == null) {
                        return MappingResult.dropped(DropReason.invalidAmount);
                    }
                    classifyOutputs
                            .add(new ClassifyContent(ClassifyContent.Kind.value, Set.of(), null, numeric, chance));
                }
                case "complex" -> {
                    return MappingResult.dropped(DropReason.complexSlot);
                }
                case "stack" -> {
                    Long amount = parseExactPositiveLong(slot.get("amount"));
                    if (amount == null) {
                        return MappingResult.dropped(DropReason.invalidAmount);
                    }
                    JsonObject stackObj = objectOrNull(slot, "stack");
                    if (stackObj == null) {
                        return MappingResult.dropped(DropReason.invalidStack);
                    }
                    Optional<ComponentStackKey> key = mapConcreteStackObject(stackObj);
                    if (key.isEmpty()) {
                        return MappingResult.dropped(DropReason.invalidStack);
                    }
                    if (!neverProduced) {
                        outputs.add(new RawOutputSlot(key.get(), amount));
                        classifyOutputs.add(ClassifyContent.stack(key.get().displayId(), amount, chance));
                    }
                }
                case "tag" -> {
                    Long amount = parseExactPositiveLong(slot.get("amount"));
                    if (amount == null) {
                        return MappingResult.dropped(DropReason.invalidAmount);
                    }
                    String tagId = stringOrEmpty(slot, "tag");
                    JsonArray valuesArr = arrayOrNull(slot, "values");
                    if (tagId.isEmpty() || valuesArr == null || valuesArr.isEmpty()) {
                        return MappingResult.dropped(DropReason.emptySlotCandidates);
                    }
                    Namespace contentTypeId = tagRegistry.resolveTagContentType(tagId, amount);
                    LinkedHashSet<ComponentStackKey> unique = new LinkedHashSet<>(valuesArr.size());
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
                        return MappingResult.dropped(DropReason.emptySlotCandidates);
                    }
                    if (unique.size() > 1) {
                        return MappingResult.dropped(DropReason.tagOutputMulti);
                    }
                    ComponentStackKey only = unique.iterator().next();
                    if (!neverProduced) {
                        outputs.add(new RawOutputSlot(only, amount));
                        classifyOutputs.add(ClassifyContent.stack(only.displayId(), amount, chance));
                    }
                }
                case "any", "typedAny" -> {
                    Long amount = parseExactPositiveLong(slot.get("amount"));
                    if (amount == null) {
                        return MappingResult.dropped(DropReason.invalidAmount);
                    }
                    JsonArray stacksArr = arrayOrNull(slot, "stacks");
                    if (stacksArr == null || stacksArr.isEmpty()) {
                        return MappingResult.dropped(DropReason.emptySlotCandidates);
                    }
                    LinkedHashSet<ComponentStackKey> unique = new LinkedHashSet<>(stacksArr.size());
                    for (int j = 0; j < stacksArr.size(); j++) {
                        JsonElement se = stacksArr.get(j);
                        if (!se.isJsonObject()) {
                            return MappingResult.dropped(DropReason.invalidStack);
                        }
                        Optional<ComponentStackKey> key = mapConcreteStackObject(se.getAsJsonObject());
                        if (key.isEmpty()) {
                            return MappingResult.dropped(DropReason.invalidStack);
                        }
                        unique.add(key.get());
                    }
                    if (unique.isEmpty()) {
                        return MappingResult.dropped(DropReason.emptySlotCandidates);
                    }
                    if (unique.size() > 1) {
                        return MappingResult.dropped(DropReason.multiStackOutput);
                    }
                    ComponentStackKey only = unique.iterator().next();
                    if (!neverProduced) {
                        outputs.add(new RawOutputSlot(only, amount));
                        classifyOutputs.add(ClassifyContent.stack(only.displayId(), amount, chance));
                    }
                }
                default -> {
                    return MappingResult.dropped(DropReason.unknownKind);
                }
            }
        }

        if (outputs.isEmpty()) {
            return MappingResult.dropped(DropReason.noOutputAfterValues);
        }
        if (catalystReturnOutputs != null) {
            outputs.addAll(catalystReturnOutputs);
        }

        RawRecipeRecord record = new RawRecipeRecord(
            systemId,
            sourceKind,
            OfflineContentTypes.tryParseLocation(explicitId),
            machines,
            inputs,
            outputs,
            recipeValues == null ? Map.of() : recipeValues,
            sourceRef
        );
        RecipeClassifyView view = new RecipeClassifyView(
            classifyIndex,
            systemIdStr,
            explicitId,
            machineIds,
            classifyInputs,
            classifyOutputs
        );
        return MappingResult.kept(record, view);
    }

    private Optional<ComponentStackKey> mapStackObject(JsonObject stackObj, SlotMatchMode mode) {
        Namespace contentTypeId = OfflineContentTypes.normalizeContentTypeId(stringOrEmpty(stackObj, "type"));
        if (contentTypeId == null) {
            return Optional.empty();
        }
        String resourceId = extractStackResourceId(stackObj, contentTypeId);
        if (resourceId.isEmpty()) {
            return Optional.empty();
        }
        JsonElement components = stackObj.get("components");
        if (mode == SlotMatchMode.identityOnly
                || components == null
                || components.isJsonNull()
                || (components.isJsonObject() && components.getAsJsonObject().isEmpty())) {
            return Optional.of(pool.internBare(contentTypeId, resourceId));
        }
        return Optional.of(OfflineContentTypes.withComponents(contentTypeId, resourceId, components));
    }

    private Optional<ComponentStackKey> mapConcreteStackObject(JsonObject stackObj) {
        return mapStackObject(stackObj, SlotMatchMode.exactComponent);
    }

    private static String extractStackResourceId(JsonObject stackObj, Namespace contentTypeId) {
        if (OfflineContentTypes.itemContentType.equals(contentTypeId)) {
            return stringOrEmpty(stackObj, "id");
        }
        if (OfflineContentTypes.fluidContentType.equals(contentTypeId)) {
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

    private static List<MachineEntry> toMachineEntries(List<String> ids) {
        if (ids.isEmpty()) return List.of();
        List<MachineEntry> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            out.add(OfflineContentTypes.machine(id));
        }
        return Collections.unmodifiableList(out);
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
