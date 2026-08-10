package dev.vfyjxf.cirrus.internal.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;
import dev.vfyjxf.cirrus.api.content.ContentApi;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.content.type.VariantType;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * Exports the content registries of {@link ContentApi} as one JSON document per registry
 * category, so pack analyzers can resolve recipe data without a running game.
 *
 * <p>All documents share the same timestamp and header as the recipe export
 * ({@link RecipeExporter}); they are written atomically through a {@code .tmp} file each, so a
 * failure in the middle never leaves a truncated export behind. Produced files:
 * <ul>
 *     <li>{@code content-types-<timestamp>.json} — registered {@link ContentType}s.</li>
 *     <li>{@code stack-types-<timestamp>.json} — registered {@link StackType}s.</li>
 *     <li>{@code tag-types-<timestamp>.json} — registered {@link TagType}s plus their tag names.</li>
 *     <li>{@code value-types-<timestamp>.json} — registered {@link ValueType}s.</li>
 *     <li>{@code variant-types-<timestamp>.json} — registered {@link VariantType}s plus the
 *     contents each one is bound to.</li>
 * </ul>
 */
public final class ContentRegistryExporter {

    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus Content Registry Export");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final DateTimeFormatter fileTimestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public static final int FORMAT_VERSION = 1;

    private ContentRegistryExporter() {
    }

    /**
     * Exports all content registries into the given directory, one file per registry category.
     */
    public static ExportResult export(Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        String timestamp = LocalDateTime.now().format(fileTimestamp);
        ContentApi api = ContentApi.instance();

        int contentTypeCount = writeDocument(outputDirectory, "content-types-" + timestamp + ".json", writer -> {
            writer.name("contentTypes");
            writer.beginArray();
            int count = 0;
            for (ContentType<?> type : api.contentTypes()) {
                writeContentType(writer, type);
                count++;
            }
            writer.endArray();
            writer.name("contentTypeCount").value(count);
            return count;
        });

        int stackTypeCount = writeDocument(outputDirectory, "stack-types-" + timestamp + ".json", writer -> {
            writer.name("stackTypes");
            writer.beginArray();
            int count = 0;
            for (StackType<?, ?> stackType : api.stackTypes()) {
                writeStackType(writer, stackType);
                count++;
            }
            writer.endArray();
            writer.name("stackTypeCount").value(count);
            return count;
        });

        int tagTypeCount = writeDocument(outputDirectory, "tag-types-" + timestamp + ".json", writer -> {
            writer.name("tagTypes");
            writer.beginArray();
            int count = 0;
            for (TagType<?> tagType : api.tagTypes()) {
                writeTagType(writer, tagType);
                count++;
            }
            writer.endArray();
            writer.name("tagTypeCount").value(count);
            return count;
        });

        int valueTypeCount = writeDocument(outputDirectory, "value-types-" + timestamp + ".json", writer -> {
            writer.name("valueTypes");
            writer.beginArray();
            int count = 0;
            for (ValueType<?> valueType : api.valueTypes()) {
                writeValueType(writer, valueType);
                count++;
            }
            writer.endArray();
            writer.name("valueTypeCount").value(count);
            return count;
        });

        int variantTypeCount = writeDocument(outputDirectory, "variant-types-" + timestamp + ".json", writer -> {
            Map<VariantType<?, ?>, TreeSet<String>> boundContents = new LinkedHashMap<>();
            for (ContentApi.VariantBinding<?, ?> binding : api.variantBindings()) {
                String name = contentName(binding.contentType(), binding.content());
                if (name == null) {
                    continue;
                }
                boundContents.computeIfAbsent(binding.variantType(), ignored -> new TreeSet<>()).add(name);
            }
            writer.name("variantTypes");
            writer.beginArray();
            int count = 0;
            for (VariantType<?, ?> variantType : api.variantTypes()) {
                writeVariantType(writer, variantType, boundContents.get(variantType));
                count++;
            }
            writer.endArray();
            writer.name("variantTypeCount").value(count);
            return count;
        });

        logger.info(
            "Content registry export completed directory={} contentTypes={} stackTypes={} tagTypes={} valueTypes={} variantTypes={}",
            outputDirectory, contentTypeCount, stackTypeCount, tagTypeCount, valueTypeCount, variantTypeCount
        );
        return new ExportResult(
            outputDirectory, timestamp, contentTypeCount, stackTypeCount, tagTypeCount, valueTypeCount, variantTypeCount
        );
    }

    //region document writing

    private interface BodyConsumer {
        int accept(JsonWriter writer) throws IOException;
    }

    private static int writeDocument(Path outputDirectory, String fileName, BodyConsumer body) throws IOException {
        Path output = outputDirectory.resolve(fileName);
        Path tmp = outputDirectory.resolve(fileName + ".tmp");
        int count;
        try (Writer fileWriter = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8);
             JsonWriter writer = GSON.newJsonWriter(fileWriter)) {
            writer.beginObject();
            writer.name("formatVersion").value(FORMAT_VERSION);
            writer.name("generatedAt").value(LocalDateTime.now().toString());
            writer.name("minecraftVersion").value(SharedConstants.getCurrentVersion().getName());
            count = body.accept(writer);
            writer.endObject();
        }
        moveIntoPlace(tmp, output);
        return count;
    }

    private static void moveIntoPlace(Path tmp, Path output) throws IOException {
        try {
            Files.move(tmp, output, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(tmp, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    //endregion

    //region serialization

    private static void writeContentType(JsonWriter writer, ContentType<?> type) throws IOException {
        writer.beginObject();
        writer.name("id").value(type.typeId().toString());
        writer.name("implementation").value(type.getClass().getName());
        if (type.getRegistryKey() != null) {
            writer.name("registry").value(type.getRegistryKey().location().toString());
        }
        if (type.tagType() != null) {
            writer.name("tagType").value(type.tagType().typeId().toString());
        }
        writer.name("supportComponents").value(type.supportComponents());
        writer.name("hasCodec").value(type.contentCodec() != null);
        writer.endObject();
    }

    private static void writeStackType(JsonWriter writer, StackType<?, ?> stackType) throws IOException {
        writer.beginObject();
        writer.name("id").value(stackType.typeId().toString());
        writer.name("contentType").value(stackType.contentType().typeId().toString());
        writer.name("implementation").value(stackType.getClass().getName());
        writer.name("maxAmount").value(stackType.maxAmount());
        writer.name("quantity").value(stackType.quantity());
        writer.name("supportComponents").value(stackType.supportComponents());
        writer.name("hasCodec").value(stackType.stackCodec() != null);
        writer.endObject();
    }

    private static void writeTagType(JsonWriter writer, TagType<?> tagType) throws IOException {
        writer.beginObject();
        writer.name("id").value(tagType.typeId().toString());
        writer.name("contentType").value(tagType.contentType().typeId().toString());
        writer.name("registry").value(tagType.registryKey().location().toString());
        TreeSet<String> tags = new TreeSet<>();
        try {
            tagType.allTags().forEach(tag -> tags.add(tag.location().toString()));
        } catch (RuntimeException | LinkageError exception) {
            logger.warn("Cannot list tags for tag type {}: {}", tagType.typeId(), exception.getMessage());
            writer.name("tagCount").value(-1);
            writer.endObject();
            return;
        }
        writer.name("tagCount").value(tags.size());
        writer.name("tags");
        writer.beginArray();
        for (String tag : tags) {
            writer.value(tag);
        }
        writer.endArray();
        writer.endObject();
    }

    private static void writeValueType(JsonWriter writer, ValueType<?> valueType) throws IOException {
        writer.beginObject();
        writer.name("id").value(valueType.typeId().toString());
        writer.name("implementation").value(valueType.getClass().getName());
        writer.endObject();
    }

    private static void writeVariantType(
        JsonWriter writer,
        VariantType<?, ?> variantType,
        @org.jetbrains.annotations.Nullable TreeSet<String> boundContents
    ) throws IOException {
        writer.beginObject();
        writer.name("id").value(variantType.typeId().toString());
        writer.name("stackType").value(variantType.stackType().typeId().toString());
        writer.name("implementation").value(variantType.getClass().getName());
        writer.name("boundContents");
        writer.beginArray();
        if (boundContents != null) {
            for (String name : boundContents) {
                writer.value(name);
            }
        }
        writer.endArray();
        writer.endObject();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static @org.jetbrains.annotations.Nullable String contentName(ContentType contentType, Object content) {
        try {
            ResourceLocation location = contentType.getLocation(content);
            return location == null ? null : location.toString();
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    //endregion

    /**
     * Paths of the produced documents plus the exported per-category counts.
     */
    public record ExportResult(
        Path directory,
        String timestamp,
        int contentTypeCount,
        int stackTypeCount,
        int tagTypeCount,
        int valueTypeCount,
        int variantTypeCount
    ) {
        public Path contentTypesFile() {
            return directory.resolve("content-types-" + timestamp + ".json");
        }

        public Path stackTypesFile() {
            return directory.resolve("stack-types-" + timestamp + ".json");
        }

        public Path tagTypesFile() {
            return directory.resolve("tag-types-" + timestamp + ".json");
        }

        public Path valueTypesFile() {
            return directory.resolve("value-types-" + timestamp + ".json");
        }

        public Path variantTypesFile() {
            return directory.resolve("variant-types-" + timestamp + ".json");
        }
    }
}
