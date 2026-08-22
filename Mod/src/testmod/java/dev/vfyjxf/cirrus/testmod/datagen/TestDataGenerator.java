package dev.vfyjxf.cirrus.testmod.datagen;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Offline generator for the test mod's data. Two parts:
 *
 * <p><b>Hand-crafted showcase data</b> (vanilla crafting JSONs, charms tag,
 * models): the recolor loop, recycling pair, charm exchanges, and the
 * greedy-vs-exact chain.
 *
 * <p><b>Bulk mimic data</b>: replays a real modpack export (atm10-lite by
 * default, override with {@code -PcirrusExportPack=<dir>}) 1:1 onto own
 * content — every item/fluid id found in the export gets a mimic registration
 * named {@code <namespace>__<path>} (see {@code mimic_manifest.json}), every
 * recipe is rewritten onto those mimics keeping its system, tags, multi-choice
 * inputs and amounts, and every referenced tag is re-created with mimicked
 * members. Complex/value slots and recipes left without inputs or outputs are
 * dropped. Loaded by the in-memory recipe systems in {@code TestDataPlugin}.
 *
 * <p>Run with {@code bash gradlew :Mod:generateTestModData}. The export
 * directory is expected at {@code /tmp/cirrus_exports} (unzip
 * {@code exports_副本.7z} there); when absent, bulk generation is skipped and
 * only the showcase data is written.
 */
public final class TestDataGenerator {

    private static final String NS = dev.vfyjxf.cirrus.testmod.TestMod.MOD_ID;
    private static final Path EXPORT_ROOT = Path.of("/tmp/cirrus_exports");

    private static final String[] MODEL_TEXTURES = {
        "minecraft:item/iron_ingot", "minecraft:item/brick", "minecraft:item/coal",
        "minecraft:item/redstone", "minecraft:item/emerald", "minecraft:item/lapis_lazuli",
        "minecraft:item/quartz", "minecraft:item/slime_ball"
    };

    public static void main(String[] args) throws IOException {
        Path resources = locateResources();
        cleanGenerated(resources);
        List<String> written = new ArrayList<>();

        generateShowcase(resources, written);
        Map<String, String> lang = new LinkedHashMap<>();
        showcaseLang(lang);
        int bulkCount = generateBulk(resources, written, lang);
        writeLang(resources, written, lang);

        System.out.println("generated " + written.size() + " files (" + bulkCount + " bulk recipes)");
    }

    /** Everything under these paths is generator-owned; wipe before regenerating. */
    private static void cleanGenerated(Path resources) throws IOException {
        deleteRecursively(resources.resolve("assets/" + NS + "/models/item"));
        deleteRecursively(resources.resolve("data/" + NS + "/recipe"));
        deleteRecursively(resources.resolve("data/" + NS + "/tags"));
        Files.deleteIfExists(resources.resolve("assets/" + NS + "/lang/en_us.json"));
        Files.deleteIfExists(resources.resolve("data/" + NS + "/bulk_recipes.json"));
        Files.deleteIfExists(resources.resolve("data/" + NS + "/mimic_manifest.json"));
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.delete(path);
            }
        }
    }

    //region showcase (hand-designed demo shapes)

    private static void generateShowcase(Path resources, List<String> written) throws IOException {
        shapeless(written, resources, "dye_red", item(NS, "dummy_block_white"), result(NS, "dummy_block_red", 1));
        shapeless(written, resources, "dye_blue", item(NS, "dummy_block_red"), result(NS, "dummy_block_blue", 1));
        shapeless(written, resources, "dye_green", item(NS, "dummy_block_blue"), result(NS, "dummy_block_green", 1));
        shapeless(written, resources, "dye_white", item(NS, "dummy_block_green"), result(NS, "dummy_block_white", 1));
        shapeless(written, resources, "green_direct", item("minecraft", "glowstone"), result(NS, "dummy_block_green", 1));

        shapeless(written, resources, "plate_from_ingot", item(NS, "dummy_ingot"), result(NS, "dummy_plate", 2));
        shapeless(written, resources, "ingot_recycling_from_plate", item(NS, "dummy_plate"), result(NS, "dummy_ingot", 1));

        tag(written, resources, "charms", NS, "dummy_charm_a", "dummy_charm_b");
        shapeless(written, resources, "charm_a_from_tag", tagIngredient(NS, "charms"), result(NS, "dummy_charm_a", 1));
        shapeless(written, resources, "charm_b_from_tag", tagIngredient(NS, "charms"), result(NS, "dummy_charm_b", 1));

        shapeless(written, resources, "z_final_via_mid", item(NS, "chain_mid"), result(NS, "dummy_final", 1));
        shapeless(written, resources, "z_mid_from_raw_a", item(NS, "chain_raw_a"), result(NS, "chain_mid", 1));
        shapeless(written, resources, "a_final_via_x3", item(NS, "chain_x3"), result(NS, "dummy_final", 1));
        shapeless(written, resources, "a_x3_from_x2", item(NS, "chain_x2"), result(NS, "chain_x3", 1));
        shapeless(written, resources, "a_x2_from_x1", item(NS, "chain_x1"), result(NS, "chain_x2", 1));
        shapeless(written, resources, "a_x1_from_raw_b", item(NS, "chain_raw_b"), result(NS, "chain_x1", 1));

        model(written, resources, "dummy_block_white", "minecraft:block/white_concrete");
        model(written, resources, "dummy_block_red", "minecraft:block/red_concrete");
        model(written, resources, "dummy_block_blue", "minecraft:block/blue_concrete");
        model(written, resources, "dummy_block_green", "minecraft:block/green_concrete");
        model(written, resources, "dummy_ingot", "minecraft:item/iron_ingot");
        model(written, resources, "dummy_plate", "minecraft:item/brick");
        model(written, resources, "dummy_charm_a", "minecraft:item/emerald");
        model(written, resources, "dummy_charm_b", "minecraft:item/diamond");
        model(written, resources, "dummy_final", "minecraft:item/nether_star");
        model(written, resources, "chain_mid", "minecraft:item/stick");
        model(written, resources, "chain_raw_a", "minecraft:item/bone");
        model(written, resources, "chain_x1", "minecraft:item/coal");
        model(written, resources, "chain_x2", "minecraft:item/redstone");
        model(written, resources, "chain_x3", "minecraft:item/lapis_lazuli");
        model(written, resources, "chain_raw_b", "minecraft:item/clay_ball");
        model(written, resources, "dummy_crystal", "minecraft:item/quartz");
        model(written, resources, "dummy_relic", "minecraft:item/brush");
    }

    private static void showcaseLang(Map<String, String> lang) {
        lang.put("itemGroup.cirrus_test", "Cirrus Test Data");
        lang.put("item.cirrus_test.dummy_block_white", "Dummy Block (White)");
        lang.put("item.cirrus_test.dummy_block_red", "Dummy Block (Red)");
        lang.put("item.cirrus_test.dummy_block_blue", "Dummy Block (Blue)");
        lang.put("item.cirrus_test.dummy_block_green", "Dummy Block (Green)");
        lang.put("item.cirrus_test.dummy_ingot", "Dummy Ingot");
        lang.put("item.cirrus_test.dummy_plate", "Dummy Plate");
        lang.put("item.cirrus_test.dummy_charm_a", "Dummy Charm A");
        lang.put("item.cirrus_test.dummy_charm_b", "Dummy Charm B");
        lang.put("item.cirrus_test.dummy_final", "Dummy Final");
        lang.put("item.cirrus_test.chain_mid", "Chain Mid");
        lang.put("item.cirrus_test.chain_raw_a", "Chain Raw A");
        lang.put("item.cirrus_test.chain_x1", "Chain X1");
        lang.put("item.cirrus_test.chain_x2", "Chain X2");
        lang.put("item.cirrus_test.chain_x3", "Chain X3");
        lang.put("item.cirrus_test.chain_raw_b", "Chain Raw B");
        lang.put("item.cirrus_test.dummy_crystal", "Dummy Crystal");
        lang.put("item.cirrus_test.dummy_relic", "Dummy Relic");
        lang.put("fluid_type.cirrus_test.dummy_fluid", "Dummy Fluid");
    }

    //endregion

    //region bulk replay from export data

    /** Collected raw stack reference: either {@code id} (item) or {@code fluid} is set. */
    private record StackRef(String id, String fluid, double amount, boolean components) {
        StackRef(String id, String fluid, double amount) {
            this(id, fluid, amount, false);
        }
    }

    /** Buffered content slot; field order inside the export object is not guaranteed. */
    private static final class SlotBuffer {
        String kind = "empty";
        double amount = 1;
        double chance = 1.0;
        StackRef stack;
        String tagId;
        final List<String> tagValues = new ArrayList<>();
        final List<StackRef> stacks = new ArrayList<>();
    }

    private static String exportPack() {
        return System.getProperty("cirrusExportPack", "atm10-lite");
    }

    /**
     * Local copy of {@code TestMod.mimicName} — calling the mod class from this
     * plain-JVM generator would trigger its static init (DeferredRegister needs
     * a bootstrapped Minecraft registry). Keep both in sync.
     */
    private static String mimicName(String rawId) {
        int split = rawId.indexOf(':');
        String namespace = split >= 0 ? rawId.substring(0, split) : "minecraft";
        String path = split >= 0 ? rawId.substring(split + 1) : rawId;
        return sanitize(namespace) + "__" + sanitize(path);
    }

    private static String sanitize(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean valid = c == '_' || c == '-' || c == '.' || c == '/'
                || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            out.append(valid ? c : '_');
        }
        return out.toString();
    }

    private static String mimicId(String rawId) {
        return NS + ":" + mimicName(rawId);
    }

    private static int generateBulk(Path resources, List<String> written, Map<String, String> lang) throws IOException {
        Path packDir = EXPORT_ROOT.resolve(exportPack());
        if (!Files.isDirectory(packDir)) {
            System.out.println("export pack not found at " + packDir + ", skipping bulk generation");
            return 0;
        }
        Path recipesFile;
        try (var stream = Files.list(packDir)) {
            recipesFile = stream
                .filter(path -> path.getFileName().toString().startsWith("recipes-"))
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .findFirst()
                .orElse(null);
        }
        if (recipesFile == null) {
            System.out.println("no recipes-*.json in " + packDir + ", skipping bulk generation");
            return 0;
        }

        // pass 1: collect every referenced content id
        Set<String> explicitItems = new HashSet<>();
        Set<String> explicitFluids = new HashSet<>();
        Map<String, Set<String>> tagMembers = new HashMap<>();
        Map<String, String> systemMachine = new LinkedHashMap<>();
        scanPack(recipesFile, explicitItems, explicitFluids, tagMembers, systemMachine);

        // a tag whose members are all known fluids is a fluid tag; everything
        // else is treated as an item tag
        Set<String> fluidTags = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : tagMembers.entrySet()) {
            if (!entry.getValue().isEmpty() && explicitFluids.containsAll(entry.getValue())) {
                fluidTags.add(entry.getKey());
            }
        }

        Set<String> registryItems = new TreeSet<>(explicitItems);
        Set<String> registryFluids = new TreeSet<>(explicitFluids);
        for (Map.Entry<String, Set<String>> entry : tagMembers.entrySet()) {
            Set<String> target = fluidTags.contains(entry.getKey()) ? registryFluids : registryItems;
            for (String member : entry.getValue()) {
                if (!member.startsWith("#")) {
                    target.add(member);
                }
            }
        }
        for (String machine : systemMachine.values()) {
            if (machine != null) {
                registryItems.add(machine);
            }
        }
        assertUniqueNames(registryItems);
        assertUniqueNames(registryFluids);

        // pass 2: rewrite the recipes onto mimic ids
        int[] count = {0};
        Path bulkOut = resources.resolve("data/" + NS + "/bulk_recipes.json");
        Files.createDirectories(bulkOut.getParent());
        Map<String, Set<String>> usedIds = new HashMap<>();
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(recipesFile));
             JsonWriter writer = new JsonWriter(Files.newBufferedWriter(bulkOut))) {
            writer.beginObject();
            writer.name("pack").value(exportPack());
            writer.name("recipes").beginArray();
            reader.beginObject();
            while (reader.hasNext()) {
                if (reader.nextName().equals("recipes")) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        if (convertRecipe(reader, fluidTags, writer, count[0], usedIds)) {
                            count[0]++;
                        }
                    }
                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            writer.endArray().endObject();
        }
        written.add("bulk_recipes.json");

        writeManifest(resources, written, registryItems, registryFluids, systemMachine);
        writeTags(resources, written, tagMembers, fluidTags);
        for (String rawId : registryItems) {
            lang.put("item." + NS + "." + mimicName(rawId), rawId);
            model(written, resources, mimicName(rawId), MODEL_TEXTURES[Math.floorMod(rawId.hashCode(), MODEL_TEXTURES.length)]);
        }
        for (String rawId : registryFluids) {
            lang.put("fluid_type." + NS + "." + mimicName(rawId), rawId);
        }
        System.out.println(
            "mimic content: " + registryItems.size() + " items, " + registryFluids.size() + " fluids, "
                + (tagMembers.size() - fluidTags.size()) + " item tags, " + fluidTags.size() + " fluid tags, "
                + systemMachine.size() + " systems"
        );
        return count[0];
    }

    private static void assertUniqueNames(Set<String> rawIds) {
        Set<String> seen = new HashSet<>();
        for (String rawId : rawIds) {
            if (!seen.add(mimicName(rawId))) {
                throw new IllegalStateException("mimic name collision: " + rawId + " -> " + mimicName(rawId));
            }
        }
    }

    //region pass 1: id collection

    private static void scanPack(
        Path recipesFile,
        Set<String> items,
        Set<String> fluids,
        Map<String, Set<String>> tagMembers,
        Map<String, String> systemMachine
    ) throws IOException {
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(recipesFile))) {
            reader.beginObject();
            while (reader.hasNext()) {
                if (reader.nextName().equals("recipes")) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        scanRecipe(reader, items, fluids, tagMembers, systemMachine);
                    }
                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }
    }

    private static void scanRecipe(
        JsonReader reader,
        Set<String> items,
        Set<String> fluids,
        Map<String, Set<String>> tagMembers,
        Map<String, String> systemMachine
    ) throws IOException {
        String system = null;
        String machine = null;
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "system" -> system = reader.nextString();
                case "machines" -> {
                    reader.beginArray();
                    if (reader.hasNext()) {
                        machine = reader.nextString();
                    }
                    while (reader.hasNext()) {
                        reader.skipValue();
                    }
                    reader.endArray();
                }
                case "inputs", "outputs" -> scanContentArray(reader, items, fluids, tagMembers);
                default -> reader.skipValue();
            }
        }
        reader.endObject();
        if (system != null && machine != null) {
            systemMachine.putIfAbsent(system, machine);
        } else if (system != null) {
            systemMachine.putIfAbsent(system, null);
        }
    }

    private static void scanContentArray(
        JsonReader reader, Set<String> items, Set<String> fluids, Map<String, Set<String>> tagMembers
    ) throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            SlotBuffer slot = readSlot(reader);
            collectSlot(slot, items, fluids, tagMembers);
        }
        reader.endArray();
    }

    private static void collectSlot(SlotBuffer slot, Set<String> items, Set<String> fluids, Map<String, Set<String>> tagMembers) {
        switch (slot.kind) {
            case "stack" -> collectStack(slot.stack, items, fluids);
            case "tag" -> {
                if (slot.tagId != null) {
                    tagMembers.computeIfAbsent(slot.tagId, key -> new LinkedHashSet<>()).addAll(slot.tagValues);
                }
            }
            case "any", "typedAny" -> {
                for (StackRef stack : slot.stacks) {
                    collectStack(stack, items, fluids);
                }
            }
            default -> {
            }
        }
    }

    private static void collectStack(StackRef stack, Set<String> items, Set<String> fluids) {
        if (stack == null) {
            return;
        }
        if (stack.id() != null) {
            items.add(stack.id());
        } else if (stack.fluid() != null) {
            fluids.add(stack.fluid());
        }
    }

    //endregion

    //region pass 2: recipe conversion

    private static boolean convertRecipe(
        JsonReader reader,
        Set<String> fluidTags,
        JsonWriter writer,
        int ordinal,
        Map<String, Set<String>> usedIds
    ) throws IOException {
        String system = null;
        String id = null;
        List<SlotBuffer> inputs = new ArrayList<>();
        List<SlotBuffer> outputs = new ArrayList<>();
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "system" -> system = reader.nextString();
                case "id" -> id = reader.nextString();
                case "inputs" -> readSlotArray(reader, inputs);
                case "outputs" -> readSlotArray(reader, outputs);
                default -> reader.skipValue();
            }
        }
        reader.endObject();
        if (system == null || system.isBlank()) {
            return false;
        }

        List<SlotBuffer> convertedInputs = convertSlots(inputs, fluidTags, true);
        List<SlotBuffer> convertedOutputs = convertSlots(outputs, fluidTags, false);
        if (convertedInputs.isEmpty() || convertedOutputs.isEmpty()) {
            return false;
        }

        String recipeId = uniqueRecipeId(system, id, ordinal, usedIds);
        writer.beginObject();
        writer.name("sys").value(system);
        writer.name("id").value(recipeId);
        writer.name("in").beginArray();
        for (SlotBuffer slot : convertedInputs) {
            writeSlot(writer, slot, fluidTags);
        }
        writer.endArray();
        writer.name("out").beginArray();
        for (SlotBuffer slot : convertedOutputs) {
            writeSlot(writer, slot, fluidTags);
        }
        writer.endArray();
        writer.endObject();
        return true;
    }

    private static String uniqueRecipeId(String system, String rawId, int ordinal, Map<String, Set<String>> usedIds) {
        String base = rawId == null || rawId.isBlank()
            ? "r" + ordinal
            : sanitize(rawId.replace(':', '/'));
        Set<String> used = usedIds.computeIfAbsent(system, key -> new HashSet<>());
        String candidate = base;
        int suffix = 2;
        while (!used.add(candidate)) {
            // ResourceLocation-path-safe suffix (~ is not a valid path char)
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    /** Keeps only slots the mimic runtime can express; outputs never carry tags/choices. */
    private static List<SlotBuffer> convertSlots(List<SlotBuffer> slots, Set<String> fluidTags, boolean input) {
        List<SlotBuffer> converted = new ArrayList<>();
        for (SlotBuffer slot : slots) {
            switch (slot.kind) {
                case "stack" -> {
                    if (slot.stack != null && (slot.stack.id() != null || slot.stack.fluid() != null)) {
                        converted.add(slot);
                    }
                }
                case "tag" -> {
                    if (input && slot.tagId != null) {
                        converted.add(slot);
                    }
                }
                case "any", "typedAny" -> {
                    if (input) {
                        slot.stacks.removeIf(stack -> stack.id() == null);
                        if (!slot.stacks.isEmpty()) {
                            converted.add(slot);
                        }
                    }
                }
                default -> {
                }
            }
        }
        return converted;
    }

    private static void writeSlot(JsonWriter writer, SlotBuffer slot, Set<String> fluidTags) throws IOException {
        switch (slot.kind) {
            case "stack" -> {
                double rawAmount = slot.stack.amount() > 0 ? slot.stack.amount() : slot.amount;
                long amount = Math.max(1, Math.round(rawAmount));
                if (slot.stack.id() != null) {
                    writer.beginArray().value("i").value(slot.stack.id()).value(amount).value(slot.chance);
                    if (slot.stack.components()) {
                        writer.value("c").value(componentMarker(slot.stack.id()));
                    }
                    writer.endArray();
                } else {
                    writer.beginArray().value("f").value(slot.stack.fluid()).value(amount).value(slot.chance);
                    if (slot.stack.components()) {
                        writer.value("c").value(componentMarker(slot.stack.fluid()));
                    }
                    writer.endArray();
                }
            }
            case "tag" -> {
                String kind = fluidTags.contains(slot.tagId) ? "ft" : "t";
                writer.beginArray().value(kind).value(slot.tagId).value(Math.max(1, Math.round(slot.amount))).value(slot.chance).endArray();
            }
            case "any", "typedAny" -> {
                writer.beginArray().value("a").value(Math.max(1, Math.round(slot.amount))).value(slot.chance);
                writer.beginArray();
                for (StackRef stack : slot.stacks) {
                    writer.value(stack.id());
                }
                writer.endArray();
                writer.endArray();
            }
            default -> throw new IllegalStateException("unconverted slot kind: " + slot.kind);
        }
    }

    //endregion

    //region slot reading (shared by both passes)

    private static void readSlotArray(JsonReader reader, List<SlotBuffer> slots) throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            slots.add(readSlot(reader));
        }
        reader.endArray();
    }

    private static SlotBuffer readSlot(JsonReader reader) throws IOException {
        SlotBuffer slot = new SlotBuffer();
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "kind" -> slot.kind = reader.nextString();
                case "amount" -> slot.amount = reader.nextDouble();
                case "chance" -> slot.chance = reader.nextDouble();
                case "tag" -> slot.tagId = reader.nextString();
                case "values" -> {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        slot.tagValues.add(reader.nextString());
                    }
                    reader.endArray();
                }
                case "stack" -> slot.stack = readStack(reader);
                case "stacks" -> {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        StackRef stack = readStack(reader);
                        if (stack != null) {
                            slot.stacks.add(stack);
                        }
                    }
                    reader.endArray();
                }
                default -> reader.skipValue();
            }
        }
        reader.endObject();
        return slot;
    }

    private static StackRef readStack(JsonReader reader) throws IOException {
        String id = null;
        String fluid = null;
        double amount = 0;
        boolean components = false;
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "id" -> id = reader.nextString();
                case "fluid" -> fluid = reader.nextString();
                case "count", "amount" -> amount = reader.nextDouble();
                case "components" -> {
                    // payloads reference mod component types that do not
                    // exist in dev; preserve presence as a synthesized marker
                    components = true;
                    reader.skipValue();
                }
                default -> reader.skipValue();
            }
        }
        reader.endObject();
        if (id == null && fluid == null) {
            return null;
        }
        return new StackRef(id, fluid, amount, components);
    }

    /** Deterministic component marker for an id: stable across generate and replay. */
    private static String componentMarker(String id) {
        return Integer.toHexString(id.hashCode() & 0xFFFF);
    }

    //endregion

    //region manifest + tags

    private static void writeManifest(
        Path resources,
        List<String> written,
        Set<String> items,
        Set<String> fluids,
        Map<String, String> systemMachine
    ) throws IOException {
        Path out = resources.resolve("data/" + NS + "/mimic_manifest.json");
        try (JsonWriter writer = new JsonWriter(Files.newBufferedWriter(out))) {
            writer.beginObject();
            writer.name("pack").value(exportPack());
            writer.name("items").beginArray();
            for (String rawId : items) {
                writer.value(rawId);
            }
            writer.endArray();
            writer.name("fluids").beginArray();
            for (String rawId : fluids) {
                writer.value(rawId);
            }
            writer.endArray();
            writer.name("systems").beginArray();
            for (Map.Entry<String, String> entry : systemMachine.entrySet()) {
                writer.beginObject();
                writer.name("id").value(entry.getKey());
                if (entry.getValue() != null) {
                    writer.name("machine").value(entry.getValue());
                }
                writer.endObject();
            }
            writer.endArray();
            writer.endObject();
        }
        written.add("mimic_manifest.json");
    }

    private static void writeTags(
        Path resources,
        List<String> written,
        Map<String, Set<String>> tagMembers,
        Set<String> fluidTags
    ) throws IOException {
        for (Map.Entry<String, Set<String>> entry : tagMembers.entrySet()) {
            String kind = fluidTags.contains(entry.getKey()) ? "fluid" : "item";
            StringBuilder values = new StringBuilder();
            for (String member : entry.getValue()) {
                if (values.length() > 0) {
                    values.append(", ");
                }
                if (member.startsWith("#")) {
                    values.append('"').append('#').append(mimicId(member.substring(1))).append('"');
                } else {
                    values.append('"').append(mimicId(member)).append('"');
                }
            }
            Path file = resources.resolve("data/" + NS + "/tags/" + kind + "/" + mimicName(entry.getKey()) + ".json");
            write(written, file, "{ \"values\": [" + values + "] }\n");
        }
    }

    //endregion

    //endregion

    //region json helpers

    private static void writeLang(Path resources, List<String> written, Map<String, String> lang) throws IOException {
        StringWriter buffer = new StringWriter();
        buffer.write("{\n");
        int i = 0;
        for (Map.Entry<String, String> entry : lang.entrySet()) {
            buffer.write("  \"");
            buffer.write(entry.getKey());
            buffer.write("\": \"");
            buffer.write(entry.getValue().replace("\"", "'"));
            buffer.write('"');
            buffer.write(++i < lang.size() ? ",\n" : "\n");
        }
        buffer.write("}\n");
        write(written, resources.resolve("assets/" + NS + "/lang/en_us.json"), buffer.toString());
    }

    private static Path locateResources() {
        Path fromModDir = Path.of("src/testmod/resources");
        if (Files.isDirectory(fromModDir.getParent())) {
            return fromModDir;
        }
        Path fromRoot = Path.of("Mod/src/testmod/resources");
        if (Files.isDirectory(fromRoot.getParent())) {
            return fromRoot;
        }
        throw new IllegalStateException("cannot locate src/testmod/resources from " + Path.of("").toAbsolutePath());
    }

    private static String item(String namespace, String id) {
        return "{\"item\": \"" + namespace + ":" + id + "\"}";
    }

    private static String tagIngredient(String namespace, String tag) {
        return "{\"tag\": \"" + namespace + ":" + tag + "\"}";
    }

    private static String result(String namespace, String id, int count) {
        return "{\"id\": \"" + namespace + ":" + id + "\", \"count\": " + count + "}";
    }

    private static void shapeless(List<String> written, Path resources, String name, String ingredient, String result) throws IOException {
        String json = """
            {
              "type": "minecraft:crafting_shapeless",
              "category": "misc",
              "ingredients": [%s],
              "result": %s
            }
            """.formatted(ingredient, result);
        write(written, resources.resolve("data/" + NS + "/recipe/" + name + ".json"), json);
    }

    private static void tag(List<String> written, Path resources, String name, String namespace, String... members) throws IOException {
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < members.length; i++) {
            if (i > 0) {
                values.append(", ");
            }
            values.append('"').append(namespace).append(':').append(members[i]).append('"');
        }
        write(written, resources.resolve("data/" + NS + "/tags/item/" + name + ".json"), "{ \"values\": [" + values + "] }\n");
    }

    private static void model(List<String> written, Path resources, String name, String texture) throws IOException {
        String json = """
            {
              "parent": "minecraft:item/generated",
              "textures": { "layer0": "%s" }
            }
            """.formatted(texture);
        write(written, resources.resolve("assets/" + NS + "/models/item/" + name + ".json"), json);
    }

    private static void write(List<String> written, Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        written.add(file.getFileName().toString());
    }

    //endregion
}
