package dev.vfyjxf.cirrus.planning.offline;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.vfyjxf.cirrus.api.planning.graph.RawIngredientSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawOutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeCatalogSnapshot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmissionPolicy;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.api.recipe.classify.ClassifyContent;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import dev.vfyjxf.cirrus.planning.adapter.ClassifierVerdictBridge;
import dev.vfyjxf.cirrus.planning.adapter.DropReason;
import dev.vfyjxf.cirrus.planning.adapter.RawRecordMapper;
import dev.vfyjxf.cirrus.planning.adapter.StackIdentity;
import dev.vfyjxf.cirrus.planning.core.graph.DefaultSystemAdmissionPolicy;
import org.jspecify.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ExportJsonCatalogLoader {

    private static final String fluidTagTypeId = "minecraft:tag/fluid";
    private static final String itemTagTypeId = "minecraft:tag/item";

    public ExportJsonCatalogLoader() {}

    public static void validatePackName(String packName) {
        if (packName == null || packName.isBlank()) {
            throw new IllegalArgumentException("packName must not be empty");
        }
        if (".".equals(packName)
                || "..".equals(packName)
                || packName.indexOf('/') >= 0
                || packName.indexOf('\\') >= 0
                || packName.indexOf('\0') >= 0
                || packName.contains("..")) {
            throw new IllegalArgumentException("Invalid pack name: " + packName);
        }
    }

    public List<String> discoverPacksInDirectory(Path exportDir) throws IOException {
        Objects.requireNonNull(exportDir, "exportDir");
        if (!Files.isDirectory(exportDir)) {
            throw new IOException("Export directory does not exist: " + exportDir);
        }
        TreeSet<String> packs = new TreeSet<>();
        try (Stream<Path> children = Files.list(exportDir)) {
            List<Path> dirs = children.filter(Files::isDirectory).toList();
            for (Path dir : dirs) {
                if (!findMatchingFiles(dir, "recipes-").isEmpty()) {
                    packs.add(dir.getFileName().toString());
                }
            }
        }
        return List.copyOf(packs);
    }

    public List<String> discoverPacksInZip(Path zipPath) throws IOException {
        Objects.requireNonNull(zipPath, "zipPath");
        TreeSet<String> packs = new TreeSet<>();
        try (ZipFile zip = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                String pack = extractPackNameFromZipEntry(name, "recipes-");
                if (pack != null) {
                    packs.add(pack);
                }
            }
        }
        return List.copyOf(packs);
    }

    public LoadedCatalog loadFromDirectory(Path exportDir, String packName) throws IOException {
        return loadFromDirectory(exportDir, packName, true);
    }

    public LoadedCatalog loadFromDirectory(Path exportDir, String packName, boolean runClassifier) throws IOException {
        Objects.requireNonNull(exportDir, "exportDir");
        validatePackName(packName);
        Path base = exportDir.toAbsolutePath().normalize();
        Path packDir = base.resolve(packName).normalize();
        if (!packDir.startsWith(base) || !Files.isDirectory(packDir)) {
            throw new IOException("Pack directory not found: " + packDir);
        }
        return loadPackDirectory(packDir, runClassifier);
    }

    public LoadedCatalog loadPackDirectory(Path packDir) throws IOException {
        return loadPackDirectory(packDir, true);
    }

    public LoadedCatalog loadPackDirectory(Path packDir, boolean runClassifier) throws IOException {
        Objects.requireNonNull(packDir, "packDir");
        if (!Files.isDirectory(packDir)) {
            throw new IOException("Pack directory does not exist: " + packDir);
        }
        List<Path> recipeFiles = findMatchingFiles(packDir, "recipes-");
        if (recipeFiles.isEmpty()) {
            throw new IOException("No recipes-*.json files found in " + packDir);
        }
        List<Path> tagFiles = findMatchingFiles(packDir, "tag-types-");
        RawRecordMapper.TagTypeRegistry tagRegistry = readTagTypesFromPaths(tagFiles);

        LoadAccumulator acc = new LoadAccumulator(tagRegistry, runClassifier);
        for (Path recipeFile : recipeFiles) {
            try (InputStream in = new BufferedInputStream(Files.newInputStream(recipeFile), 65536)) {
                acc.readRecipesStream(in);
            }
        }
        return acc.finish(1L);
    }

    public RawRecipeCatalogSnapshot loadSnapshot(Path packDir) throws IOException {
        return loadPackDirectory(packDir, false).snapshot();
    }

    public LoadedCatalog loadFromZip(Path zipPath, String packName) throws IOException {
        return loadFromZip(zipPath, packName, true);
    }

    public LoadedCatalog loadFromZip(Path zipPath, String packName, boolean runClassifier) throws IOException {
        Objects.requireNonNull(zipPath, "zipPath");
        validatePackName(packName);
        try (ZipFile zip = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            List<ZipEntry> recipeEntries = new ArrayList<>();
            List<ZipEntry> tagEntries = new ArrayList<>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (matchesPackEntry(name, packName, "recipes-")) {
                    recipeEntries.add(entry);
                } else if (matchesPackEntry(name, packName, "tag-types-")) {
                    tagEntries.add(entry);
                }
            }
            if (recipeEntries.isEmpty()) {
                throw new IOException("No recipes-*.json found for pack '" + packName + "' in " + zipPath);
            }
            recipeEntries.sort(Comparator.comparing(ZipEntry::getName));
            tagEntries.sort(Comparator.comparing(ZipEntry::getName));

            RawRecordMapper.TagTypeRegistry tagRegistry = readTagTypesFromZip(zip, tagEntries);
            LoadAccumulator acc = new LoadAccumulator(tagRegistry, runClassifier);
            for (ZipEntry entry : recipeEntries) {
                try (InputStream in = new BufferedInputStream(zip.getInputStream(entry), 65536)) {
                    acc.readRecipesStream(in);
                }
            }
            return acc.finish(1L);
        }
    }

    public LoadedCatalog loadStream(
        InputStream in,
        long catalogVersion,
        RawRecordMapper.TagTypeRegistry tagRegistry,
        boolean runClassifier
    ) throws IOException {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(tagRegistry, "tagRegistry");
        LoadAccumulator acc = new LoadAccumulator(tagRegistry, runClassifier);
        acc.readRecipesStream(in);
        return acc.finish(catalogVersion);
    }

    public static RawRecordMapper.TagTypeRegistry parseTagTypesStream(InputStream in) throws IOException {
        Set<String> fluidTags = new HashSet<>();
        Set<String> itemTags = new HashSet<>();
        boolean foundAny = false;
        try (JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                return RawRecordMapper.TagTypeRegistry.absent;
            }
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("tagTypes".equals(name) && reader.peek() == JsonToken.BEGIN_ARRAY) {
                    foundAny = true;
                    reader.beginArray();
                    while (reader.hasNext()) {
                        readTagTypeEntry(reader, fluidTags, itemTags);
                    }
                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }
        return foundAny
                ? new RawRecordMapper.TagTypeRegistry(fluidTags, itemTags, true)
                : RawRecordMapper.TagTypeRegistry.absent;
    }

    private static void readTagTypeEntry(JsonReader reader, Set<String> fluidTags, Set<String> itemTags)
            throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue();
            return;
        }
        String id = "";
        String contentType = "";
        List<String> tags = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String field = reader.nextName();
            switch (field) {
                case "id" -> id = readStringOrNull(reader, "");
                case "contentType" -> contentType = readStringOrNull(reader, "");
                case "tags" -> {
                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        tags = new ArrayList<>();
                        reader.beginArray();
                        while (reader.hasNext()) {
                            if (reader.peek() == JsonToken.STRING) {
                                String t = reader.nextString().trim();
                                if (!t.isEmpty()) {
                                    tags.add(t);
                                }
                            } else {
                                reader.skipValue();
                            }
                        }
                        reader.endArray();
                    } else {
                        reader.skipValue();
                    }
                }
                default -> reader.skipValue();
            }
        }
        reader.endObject();
        if (tags == null || tags.isEmpty()) {
            return;
        }
        if (fluidTagTypeId.equals(id) || StackIdentity.fluidContentType.equals(contentType)) {
            fluidTags.addAll(tags);
        } else if (itemTagTypeId.equals(id) || StackIdentity.itemContentType.equals(contentType)) {
            itemTags.addAll(tags);
        }
    }

    private static RawRecordMapper.TagTypeRegistry readTagTypesFromPaths(List<Path> tagFiles) throws IOException {
        if (tagFiles.isEmpty()) {
            return RawRecordMapper.TagTypeRegistry.absent;
        }
        Set<String> fluidTags = new HashSet<>();
        Set<String> itemTags = new HashSet<>();
        boolean present = false;
        for (Path p : tagFiles) {
            try (InputStream in = new BufferedInputStream(Files.newInputStream(p), 32768)) {
                RawRecordMapper.TagTypeRegistry parsed = parseTagTypesStream(in);
                if (parsed.present()) {
                    present = true;
                    fluidTags.addAll(parsed.fluidTags());
                    itemTags.addAll(parsed.itemTags());
                }
            }
        }
        return present
                ? new RawRecordMapper.TagTypeRegistry(fluidTags, itemTags, true)
                : RawRecordMapper.TagTypeRegistry.absent;
    }

    private static RawRecordMapper.TagTypeRegistry readTagTypesFromZip(ZipFile zip, List<ZipEntry> tagEntries)
            throws IOException {
        if (tagEntries.isEmpty()) {
            return RawRecordMapper.TagTypeRegistry.absent;
        }
        Set<String> fluidTags = new HashSet<>();
        Set<String> itemTags = new HashSet<>();
        boolean present = false;
        for (ZipEntry entry : tagEntries) {
            try (InputStream in = new BufferedInputStream(zip.getInputStream(entry), 32768)) {
                RawRecordMapper.TagTypeRegistry parsed = parseTagTypesStream(in);
                if (parsed.present()) {
                    present = true;
                    fluidTags.addAll(parsed.fluidTags());
                    itemTags.addAll(parsed.itemTags());
                }
            }
        }
        return present
                ? new RawRecordMapper.TagTypeRegistry(fluidTags, itemTags, true)
                : RawRecordMapper.TagTypeRegistry.absent;
    }

    private static List<Path> findMatchingFiles(Path dir, String prefix) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile).filter(p -> {
                String name = p.getFileName().toString();
                return name.startsWith(prefix) && name.endsWith(".json");
            }).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        }
    }

    private static @Nullable String extractPackNameFromZipEntry(String entryName, String filePrefix) {
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        String[] parts = normalized.split("/");
        if (parts.length == 3
                && "exports".equals(parts[0])
                && parts[2].startsWith(filePrefix)
                && parts[2].endsWith(".json")) {
            return parts[1];
        }
        if (parts.length == 2 && parts[1].startsWith(filePrefix) && parts[1].endsWith(".json")) {
            return parts[0];
        }
        return null;
    }

    private static boolean matchesPackEntry(String entryName, String packName, String filePrefix) {
        String found = extractPackNameFromZipEntry(entryName, filePrefix);
        return packName.equals(found);
    }

    private static String readStringOrNull(JsonReader reader, String fallback) throws IOException {
        JsonToken tok = reader.peek();
        if (tok == JsonToken.STRING) {
            return reader.nextString().trim();
        }
        reader.skipValue();
        return fallback;
    }

    private static @Nullable String readNullableString(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.STRING) {
            return reader.nextString().trim();
        }
        reader.skipValue();
        return null;
    }

    private static final class LoadAccumulator {
        private final RawRecordMapper.TagTypeRegistry tagRegistry;
        private final RawRecordMapper.CandidatePool pool = new RawRecordMapper.CandidatePool();
        private final boolean runClassifier;
        private final List<RawRecipeRecord> keptRecords = new ArrayList<>(4096);
        private final List<RecipeClassifyView> classifyViews;
        private final EnumMap<DropReason, Long> drops = new EnumMap<>(DropReason.class);
        private long rawCount;
        private long nextSourceRef = 1L;

        private LoadAccumulator(RawRecordMapper.TagTypeRegistry tagRegistry, boolean runClassifier) {
            this.tagRegistry = tagRegistry;
            this.runClassifier = runClassifier;
            this.classifyViews = runClassifier ? new ArrayList<>(4096) : List.of();
        }

        private void readRecipesStream(InputStream in) throws IOException {
            try (JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                    throw new IOException("Expected JSON object at root of recipes export");
                }
                reader.beginObject();
                while (reader.hasNext()) {
                    String field = reader.nextName();
                    if ("recipes".equals(field) && reader.peek() == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray();
                        while (reader.hasNext()) {
                            rawCount++;
                            readOneRecipe(reader);
                        }
                        reader.endArray();
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endObject();
            }
        }

        private void readOneRecipe(JsonReader reader) throws IOException {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue();
                drop(DropReason.unknownKind);
                return;
            }
            String systemId = "";
            String sourceKind = "";
            String explicitId = "";
            List<String> machines = List.of();
            List<ParsedSlot> rawInputs = null;
            List<ParsedSlot> rawOutputs = null;
            DropReason parseError = null;

            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                switch (field) {
                    case "system" -> systemId = readStringOrNull(reader, "");
                    case "source" -> sourceKind = readStringOrNull(reader, "");
                    case "id" -> explicitId = readStringOrNull(reader, "");
                    case "machines" -> machines = readStringList(reader);
                    case "inputs" -> {
                        SlotArrayResult res = readSlotArray(reader);
                        if (parseError == null && res.error != null) {
                            parseError = res.error;
                        }
                        rawInputs = res.slots;
                    }
                    case "outputs" -> {
                        SlotArrayResult res = readSlotArray(reader);
                        if (parseError == null && res.error != null) {
                            parseError = res.error;
                        }
                        rawOutputs = res.slots;
                    }
                    default -> reader.skipValue();
                }
            }
            reader.endObject();

            if (rawInputs == null || rawInputs.isEmpty() || rawOutputs == null || rawOutputs.isEmpty()) {
                drop(DropReason.emptySlotLists);
                return;
            }
            if (parseError != null) {
                drop(parseError);
                return;
            }
            if (sourceKind.isEmpty()) {
                sourceKind = systemId.startsWith("jei:") ? "jei" : "custom";
            }

            List<RawIngredientSlot> inputs = new ArrayList<>(rawInputs.size());
            List<RawOutputSlot> outputs = new ArrayList<>(rawOutputs.size());
            List<RawOutputSlot> catalystReturnOutputs = null;
            Map<String, Double> recipeValues = null;
            List<ClassifyContent> cInputs = runClassifier ? new ArrayList<>(rawInputs.size()) : null;
            List<ClassifyContent> cOutputs = runClassifier ? new ArrayList<>(rawOutputs.size()) : null;

            for (ParsedSlot slot : rawInputs) {
                if (!Double.isFinite(slot.chance)
                        || slot.chance < 0.0
                        || slot.chance > 1.0
                        || (slot.chance > 0.0 && slot.chance < 1.0)) {
                    drop(DropReason.probabilistic);
                    return;
                }
                boolean catalyst = slot.chance == 0.0;
                switch (slot.kind) {
                    case "value" -> {
                        if (slot.valueType == null
                                || slot.valueType.isEmpty()
                                || slot.numericValue == null
                                || !Double.isFinite(slot.numericValue)) {
                            drop(DropReason.invalidAmount);
                            return;
                        }
                        if (!catalyst) {
                            if (recipeValues == null) {
                                recipeValues = new LinkedHashMap<>();
                            }
                            recipeValues.merge(slot.valueType, slot.numericValue, Math::max);
                        }
                        if (cInputs != null) {
                            cInputs.add(
                                new ClassifyContent(
                                    ClassifyContent.Kind.value,
                                    Set.of(),
                                    null,
                                    slot.numericValue,
                                    slot.chance
                                )
                            );
                        }
                    }
                    case "complex" -> {
                        drop(DropReason.complexSlot);
                        return;
                    }
                    case "stack" -> {
                        if (slot.exactAmount == null || slot.exactAmount <= 0L) {
                            drop(DropReason.invalidAmount);
                            return;
                        }
                        Optional<StackIdentity.MatchFlags> flags = StackIdentity.MatchFlags.parse(slot.matchStrategy);
                        if (flags.isEmpty() || flags.get().toSlotMatchMode().isEmpty()) {
                            drop(DropReason.unsupportedMatchStrategy);
                            return;
                        }
                        if (slot.stack == null) {
                            drop(DropReason.invalidStack);
                            return;
                        }
                        StackIdentity.MappedStack mapped = mapParsedStackInput(slot.stack, flags.get());
                        if (mapped.key().isEmpty()) {
                            drop(DropReason.invalidStack);
                            return;
                        }
                        ResourceKey key = mapped.key().get();
                        SlotMatchMode mode = mapped.mode().orElseThrow();
                        inputs.add(new RawIngredientSlot(Optional.empty(), List.of(key), slot.exactAmount, mode));
                        if (catalyst) {
                            if (catalystReturnOutputs == null) {
                                catalystReturnOutputs = new ArrayList<>(2);
                            }
                            catalystReturnOutputs.add(new RawOutputSlot(key, slot.exactAmount));
                        }
                        if (cInputs != null) {
                            cInputs.add(ClassifyContent.stack(key.resourceId(), slot.exactAmount, slot.chance));
                        }
                    }
                    case "tag" -> {
                        if (slot.exactAmount == null || slot.exactAmount <= 0L) {
                            drop(DropReason.invalidAmount);
                            return;
                        }
                        if (slot.tagId == null
                                || slot.tagId.isEmpty()
                                || slot.tagValues == null
                                || slot.tagValues.isEmpty()) {
                            drop(DropReason.emptySlotCandidates);
                            return;
                        }
                        String contentTypeId = tagRegistry.resolveTagContentType(slot.tagId, slot.exactAmount);
                        LinkedHashSet<ResourceKey> unique = new LinkedHashSet<>(slot.tagValues.size());
                        LinkedHashSet<String> idStrings = runClassifier
                                ? new LinkedHashSet<>(slot.tagValues.size())
                                : null;
                        for (String rawId : slot.tagValues) {
                            if (!rawId.isEmpty()) {
                                unique.add(pool.internBare(contentTypeId, rawId));
                                if (idStrings != null) {
                                    idStrings.add(rawId);
                                }
                            }
                        }
                        if (unique.isEmpty()) {
                            drop(DropReason.emptySlotCandidates);
                            return;
                        }
                        List<ResourceKey> candidates = pool.internList(new ArrayList<>(unique));
                        if (catalyst) {
                            if (candidates.size() == 1) {
                                inputs.add(
                                    new RawIngredientSlot(
                                        Optional.empty(),
                                        candidates,
                                        slot.exactAmount,
                                        SlotMatchMode.identity
                                    )
                                );
                                if (catalystReturnOutputs == null) {
                                    catalystReturnOutputs = new ArrayList<>(2);
                                }
                                catalystReturnOutputs.add(new RawOutputSlot(candidates.get(0), slot.exactAmount));
                            } else {
                                inputs.add(
                                    new RawIngredientSlot(
                                        Optional.of(slot.tagId),
                                        candidates,
                                        0L,
                                        SlotMatchMode.identity,
                                        0,
                                        1
                                    )
                                );
                            }
                        } else {
                            inputs.add(
                                new RawIngredientSlot(
                                    Optional.of(slot.tagId),
                                    candidates,
                                    slot.exactAmount,
                                    SlotMatchMode.identity
                                )
                            );
                        }
                        if (cInputs != null && idStrings != null) {
                            cInputs.add(
                                ClassifyContent
                                        .tag(slot.tagId, pool.internIdSet(idStrings), slot.exactAmount, slot.chance)
                            );
                        }
                    }
                    case "any", "typedAny" -> {
                        if (slot.exactAmount == null || slot.exactAmount <= 0L) {
                            drop(DropReason.invalidAmount);
                            return;
                        }
                        if (slot.stacks == null || slot.stacks.isEmpty()) {
                            drop(DropReason.emptySlotCandidates);
                            return;
                        }
                        LinkedHashSet<ResourceKey> unique = new LinkedHashSet<>(slot.stacks.size());
                        LinkedHashSet<String> idStrings = runClassifier
                                ? new LinkedHashSet<>(slot.stacks.size())
                                : null;
                        for (ParsedStack ps : slot.stacks) {
                            Optional<ResourceKey> key = mapParsedStackConcrete(ps);
                            if (key.isEmpty()) {
                                drop(DropReason.invalidStack);
                                return;
                            }
                            unique.add(key.get());
                            if (idStrings != null) {
                                idStrings.add(key.get().resourceId());
                            }
                        }
                        if (unique.isEmpty()) {
                            drop(DropReason.emptySlotCandidates);
                            return;
                        }
                        List<ResourceKey> candidates = pool.internList(new ArrayList<>(unique));
                        SlotMatchMode mode = StackIdentity.listSlotMatchMode(candidates);
                        if (catalyst) {
                            if (candidates.size() == 1) {
                                inputs.add(new RawIngredientSlot(Optional.empty(), candidates, slot.exactAmount, mode));
                                if (catalystReturnOutputs == null) {
                                    catalystReturnOutputs = new ArrayList<>(2);
                                }
                                catalystReturnOutputs.add(new RawOutputSlot(candidates.get(0), slot.exactAmount));
                            } else {
                                inputs.add(new RawIngredientSlot(Optional.empty(), candidates, 0L, mode, 0, 1));
                            }
                        } else {
                            inputs.add(new RawIngredientSlot(Optional.empty(), candidates, slot.exactAmount, mode));
                        }
                        if (cInputs != null && idStrings != null) {
                            ClassifyContent.Kind cKind = "typedAny".equals(slot.kind)
                                    ? ClassifyContent.Kind.typedAny
                                    : ClassifyContent.Kind.any;
                            cInputs.add(
                                new ClassifyContent(
                                    cKind,
                                    pool.internIdSet(idStrings),
                                    null,
                                    slot.exactAmount,
                                    slot.chance
                                )
                            );
                        }
                    }
                    default -> {
                        drop(DropReason.unknownKind);
                        return;
                    }
                }
            }

            if (inputs.isEmpty()) {
                drop(DropReason.noInputAfterValues);
                return;
            }

            for (ParsedSlot slot : rawOutputs) {
                if (!Double.isFinite(slot.chance)
                        || slot.chance < 0.0
                        || slot.chance > 1.0
                        || (slot.chance > 0.0 && slot.chance < 1.0)) {
                    drop(DropReason.probabilistic);
                    return;
                }
                boolean neverProduced = slot.chance == 0.0;
                switch (slot.kind) {
                    case "value" -> {
                        if (slot.valueType == null
                                || slot.valueType.isEmpty()
                                || slot.numericValue == null
                                || !Double.isFinite(slot.numericValue)) {
                            drop(DropReason.invalidAmount);
                            return;
                        }
                        if (!neverProduced) {
                            if (recipeValues == null) {
                                recipeValues = new LinkedHashMap<>();
                            }
                            recipeValues.merge(slot.valueType, slot.numericValue, Math::max);
                        }
                        if (cOutputs != null) {
                            cOutputs.add(
                                new ClassifyContent(
                                    ClassifyContent.Kind.value,
                                    Set.of(),
                                    null,
                                    slot.numericValue,
                                    slot.chance
                                )
                            );
                        }
                    }
                    case "complex" -> {
                        drop(DropReason.complexSlot);
                        return;
                    }
                    case "stack" -> {
                        if (slot.exactAmount == null || slot.exactAmount <= 0L) {
                            drop(DropReason.invalidAmount);
                            return;
                        }
                        if (slot.stack == null) {
                            drop(DropReason.invalidStack);
                            return;
                        }
                        Optional<ResourceKey> key = mapParsedStackConcrete(slot.stack);
                        if (key.isEmpty()) {
                            drop(DropReason.invalidStack);
                            return;
                        }
                        if (!neverProduced) {
                            outputs.add(new RawOutputSlot(key.get(), slot.exactAmount));
                            if (cOutputs != null) {
                                cOutputs.add(
                                    ClassifyContent.stack(key.get().resourceId(), slot.exactAmount, slot.chance)
                                );
                            }
                        }
                    }
                    case "tag" -> {
                        if (slot.exactAmount == null || slot.exactAmount <= 0L) {
                            drop(DropReason.invalidAmount);
                            return;
                        }
                        if (slot.tagId == null
                                || slot.tagId.isEmpty()
                                || slot.tagValues == null
                                || slot.tagValues.isEmpty()) {
                            drop(DropReason.emptySlotCandidates);
                            return;
                        }
                        String contentTypeId = tagRegistry.resolveTagContentType(slot.tagId, slot.exactAmount);
                        LinkedHashSet<ResourceKey> unique = new LinkedHashSet<>(slot.tagValues.size());
                        for (String rawId : slot.tagValues) {
                            if (!rawId.isEmpty()) {
                                unique.add(pool.internBare(contentTypeId, rawId));
                            }
                        }
                        if (unique.isEmpty()) {
                            drop(DropReason.emptySlotCandidates);
                            return;
                        }
                        if (unique.size() > 1) {
                            drop(DropReason.tagOutputMulti);
                            return;
                        }
                        ResourceKey only = unique.iterator().next();
                        if (!neverProduced) {
                            outputs.add(new RawOutputSlot(only, slot.exactAmount));
                            if (cOutputs != null) {
                                cOutputs.add(ClassifyContent.stack(only.resourceId(), slot.exactAmount, slot.chance));
                            }
                        }
                    }
                    case "any", "typedAny" -> {
                        if (slot.exactAmount == null || slot.exactAmount <= 0L) {
                            drop(DropReason.invalidAmount);
                            return;
                        }
                        if (slot.stacks == null || slot.stacks.isEmpty()) {
                            drop(DropReason.emptySlotCandidates);
                            return;
                        }
                        LinkedHashSet<ResourceKey> unique = new LinkedHashSet<>(slot.stacks.size());
                        for (ParsedStack ps : slot.stacks) {
                            Optional<ResourceKey> key = mapParsedStackConcrete(ps);
                            if (key.isEmpty()) {
                                drop(DropReason.invalidStack);
                                return;
                            }
                            unique.add(key.get());
                        }
                        if (unique.isEmpty()) {
                            drop(DropReason.emptySlotCandidates);
                            return;
                        }
                        if (unique.size() > 1) {
                            drop(DropReason.multiStackOutput);
                            return;
                        }
                        ResourceKey only = unique.iterator().next();
                        if (!neverProduced) {
                            outputs.add(new RawOutputSlot(only, slot.exactAmount));
                            if (cOutputs != null) {
                                cOutputs.add(ClassifyContent.stack(only.resourceId(), slot.exactAmount, slot.chance));
                            }
                        }
                    }
                    default -> {
                        drop(DropReason.unknownKind);
                        return;
                    }
                }
            }

            if (outputs.isEmpty()) {
                drop(DropReason.noOutputAfterValues);
                return;
            }
            if (catalystReturnOutputs != null) {
                outputs.addAll(catalystReturnOutputs);
            }

            long sourceRef = nextSourceRef++;
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
            keptRecords.add(record);
            if (runClassifier) {
                int viewIndex = classifyViews.size();
                classifyViews.add(
                    new RecipeClassifyView(
                        viewIndex,
                        systemId,
                        explicitId,
                        machines,
                        Objects.requireNonNull(cInputs),
                        Objects.requireNonNull(cOutputs)
                    )
                );
            }
        }

        private StackIdentity.MappedStack mapParsedStackInput(ParsedStack ps, StackIdentity.MatchFlags flags) {
            String contentTypeId = StackIdentity.normalizeContentTypeId(ps.type);
            String resId = extractParsedStackResourceId(ps, contentTypeId);
            if (contentTypeId.isEmpty() || resId.isEmpty()) {
                return new StackIdentity.MappedStack(Optional.empty(), flags.toSlotMatchMode());
            }
            Optional<SlotMatchMode> mode = flags.toSlotMatchMode();
            if (ps.components == null
                    || ps.components.isJsonNull()
                    || (ps.components.isJsonObject() && ps.components.getAsJsonObject().isEmpty())
                    || mode.orElse(SlotMatchMode.identity) == SlotMatchMode.identity) {
                return new StackIdentity.MappedStack(Optional.of(pool.internBare(contentTypeId, resId)), mode);
            }
            return StackIdentity.mapInput(new StackIdentity.StackSpec(contentTypeId, resId, ps.components, flags));
        }

        private Optional<ResourceKey> mapParsedStackConcrete(ParsedStack ps) {
            String contentTypeId = StackIdentity.normalizeContentTypeId(ps.type);
            String resId = extractParsedStackResourceId(ps, contentTypeId);
            if (contentTypeId.isEmpty() || resId.isEmpty()) {
                return Optional.empty();
            }
            if (ps.components == null
                    || ps.components.isJsonNull()
                    || (ps.components.isJsonObject() && ps.components.getAsJsonObject().isEmpty())) {
                return Optional.of(pool.internBare(contentTypeId, resId));
            }
            return StackIdentity.mapConcrete(contentTypeId, resId, ps.components);
        }

        private static String extractParsedStackResourceId(ParsedStack ps, String contentTypeId) {
            if (StackIdentity.itemContentType.equals(contentTypeId)) {
                return ps.id;
            }
            if (StackIdentity.fluidContentType.equals(contentTypeId)) {
                return !ps.fluid.isEmpty() ? ps.fluid : ps.id;
            }
            if (!ps.id.isEmpty()) {
                return ps.id;
            }
            if (ps.value.isEmpty()) {
                return "";
            }
            if (ps.value.indexOf(':') >= 0) {
                return ps.value;
            }
            String lower = ps.value.toLowerCase(Locale.ROOT);
            StringBuilder sb = new StringBuilder(lower.length());
            for (int i = 0; i < lower.length(); i++) {
                char c = lower.charAt(i);
                if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-' || c == '/') {
                    sb.append(c);
                } else {
                    sb.append('_');
                }
            }
            return sb.isEmpty() ? "" : contentTypeId + "/" + sb;
        }

        private SlotArrayResult readSlotArray(JsonReader reader) throws IOException {
            if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                reader.skipValue();
                return new SlotArrayResult(List.of(), null);
            }
            List<ParsedSlot> slots = new ArrayList<>(4);
            DropReason firstErr = null;
            reader.beginArray();
            while (reader.hasNext()) {
                if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                    reader.skipValue();
                    if (firstErr == null) {
                        firstErr = DropReason.unknownKind;
                    }
                    continue;
                }
                ParsedSlot slot = readSlotObject(reader);
                slots.add(slot);
            }
            reader.endArray();
            return new SlotArrayResult(slots, firstErr);
        }

        private ParsedSlot readSlotObject(JsonReader reader) throws IOException {
            ParsedSlot slot = new ParsedSlot();
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                switch (field) {
                    case "kind" -> slot.kind = readStringOrNull(reader, "");
                    case "amount" -> slot.exactAmount = readExactPositiveLong(reader);
                    case "chance" -> slot.chance = readChance(reader);
                    case "matchStrategy" -> slot.matchStrategy = readNullableString(reader);
                    case "tag" -> slot.tagId = readStringOrNull(reader, "");
                    case "values" -> slot.tagValues = readStringList(reader);
                    case "stack" -> slot.stack = readStackObject(reader);
                    case "stacks" -> slot.stacks = readStackList(reader);
                    case "valueType" -> slot.valueType = readStringOrNull(reader, "");
                    case "value" -> slot.numericValue = readFiniteDouble(reader);
                    default -> reader.skipValue();
                }
            }
            reader.endObject();
            return slot;
        }

        private @Nullable ParsedStack readStackObject(JsonReader reader) throws IOException {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue();
                return null;
            }
            ParsedStack ps = new ParsedStack();
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                switch (field) {
                    case "type" -> ps.type = readStringOrNull(reader, "");
                    case "id" -> ps.id = readStringOrNull(reader, "");
                    case "fluid" -> ps.fluid = readStringOrNull(reader, "");
                    case "value" -> ps.value = readScalarAsString(reader);
                    case "components" -> {
                        if (reader.peek() == JsonToken.NULL) {
                            reader.nextNull();
                        } else {
                            ps.components = JsonParser.parseReader(reader);
                        }
                    }
                    default -> reader.skipValue();
                }
            }
            reader.endObject();
            return ps;
        }

        private List<ParsedStack> readStackList(JsonReader reader) throws IOException {
            if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                reader.skipValue();
                return List.of();
            }
            List<ParsedStack> list = new ArrayList<>(4);
            reader.beginArray();
            while (reader.hasNext()) {
                ParsedStack ps = readStackObject(reader);
                list.add(ps == null ? new ParsedStack() : ps);
            }
            reader.endArray();
            return list;
        }

        private static List<String> readStringList(JsonReader reader) throws IOException {
            if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                reader.skipValue();
                return List.of();
            }
            List<String> list = new ArrayList<>(4);
            reader.beginArray();
            while (reader.hasNext()) {
                if (reader.peek() == JsonToken.STRING) {
                    String s = reader.nextString().trim();
                    if (!s.isEmpty()) {
                        list.add(s);
                    }
                } else {
                    reader.skipValue();
                }
            }
            reader.endArray();
            return list.isEmpty() ? List.of() : Collections.unmodifiableList(list);
        }

        private static @Nullable Long readExactPositiveLong(JsonReader reader) throws IOException {
            JsonToken tok = reader.peek();
            if (tok != JsonToken.NUMBER) {
                reader.skipValue();
                return null;
            }
            String raw = reader.nextString().trim();
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

        private static double readChance(JsonReader reader) throws IOException {
            JsonToken tok = reader.peek();
            if (tok == JsonToken.NULL) {
                reader.nextNull();
                return 1.0;
            }
            if (tok != JsonToken.NUMBER) {
                reader.skipValue();
                return Double.NaN;
            }
            return reader.nextDouble();
        }

        private static @Nullable Double readFiniteDouble(JsonReader reader) throws IOException {
            JsonToken tok = reader.peek();
            if (tok != JsonToken.NUMBER) {
                reader.skipValue();
                return null;
            }
            double d = reader.nextDouble();
            return Double.isFinite(d) ? d : null;
        }

        private static String readScalarAsString(JsonReader reader) throws IOException {
            JsonToken tok = reader.peek();
            if (tok == JsonToken.STRING || tok == JsonToken.NUMBER) {
                return reader.nextString().trim();
            }
            if (tok == JsonToken.BOOLEAN) {
                return Boolean.toString(reader.nextBoolean());
            }
            reader.skipValue();
            return "";
        }

        private void drop(DropReason reason) {
            drops.merge(reason, 1L, Long::sum);
        }

        private LoadedCatalog finish(long catalogVersion) {
            RawRecipeCatalogSnapshot snapshot = new RawRecipeCatalogSnapshot(catalogVersion, keptRecords, Set.of());
            ExportLoadStats stats = new ExportLoadStats(rawCount, keptRecords.size(), drops);
            SystemAdmissionPolicy policy = DefaultSystemAdmissionPolicy.defaults();
            if (runClassifier && !classifyViews.isEmpty()) {
                long[] sourceRefs = new long[classifyViews.size()];
                for (int i = 0; i < keptRecords.size(); i++) {
                    sourceRefs[i] = keptRecords.get(i).sourceRef();
                }
                policy = ClassifierVerdictBridge
                        .classifyToPolicy(DefaultSystemAdmissionPolicy.defaults(), classifyViews, sourceRefs);
            }
            return new LoadedCatalog(snapshot, policy, stats);
        }
    }

    private record SlotArrayResult(List<ParsedSlot> slots, @Nullable DropReason error) {}

    private static final class ParsedSlot {
        String kind = "";
        @Nullable
        Long exactAmount;
        double chance = 1.0;
        @Nullable
        String matchStrategy;
        @Nullable
        String tagId;
        @Nullable
        List<String> tagValues;
        @Nullable
        ParsedStack stack;
        @Nullable
        List<ParsedStack> stacks;
        @Nullable
        String valueType;
        @Nullable
        Double numericValue;
    }

    private static final class ParsedStack {
        String type = "";
        String id = "";
        String fluid = "";
        String value = "";
        @Nullable
        JsonElement components;
    }
}
