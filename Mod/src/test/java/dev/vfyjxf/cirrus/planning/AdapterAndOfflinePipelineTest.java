package dev.vfyjxf.cirrus.planning;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.planning.adapter.CanonicalJson;
import dev.vfyjxf.cirrus.planning.adapter.DropReason;
import dev.vfyjxf.cirrus.planning.adapter.RawRecordMapper;
import dev.vfyjxf.cirrus.planning.adapter.StackIdentity;
import dev.vfyjxf.cirrus.planning.offline.ExportJsonCatalogLoader;
import dev.vfyjxf.cirrus.planning.offline.LoadedCatalog;
import dev.vfyjxf.cirrus.planning.offline.OfflinePlanBench;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdapterAndOfflinePipelineTest {

    @Test
    void stackIdentityDisambiguatesFluidVsItemTagsMatchStrategiesAndFallback() {
        RawRecordMapper.TagTypeRegistry reg = new RawRecordMapper.TagTypeRegistry(
            Set.of("c:honey", "c:water"),
            Set.of("minecraft:logs", "c:honey"),
            true
        );
        assertEquals(StackIdentity.fluidContentType, reg.resolveTagContentType("c:water", 250L));
        assertEquals(StackIdentity.itemContentType, reg.resolveTagContentType("minecraft:logs", 1000L));
        assertEquals(StackIdentity.fluidContentType, reg.resolveTagContentType("c:honey", 1000L));
        assertEquals(StackIdentity.itemContentType, reg.resolveTagContentType("c:honey", 1L));

        assertEquals(
            SlotMatchMode.identity,
            StackIdentity.MatchFlags.parse("identity").orElseThrow().toSlotMatchMode().orElseThrow()
        );
        assertEquals(
            SlotMatchMode.identityAndComponent,
            StackIdentity.MatchFlags.parse("identityAndComponent").orElseThrow().toSlotMatchMode().orElseThrow()
        );
        assertTrue(StackIdentity.MatchFlags.parse("amount").orElseThrow().toSlotMatchMode().isEmpty());

        final class PlainNoOverrides {}
        String t1 = StackIdentity.unencodableFallbackToken("mod:c", new PlainNoOverrides());
        String t2 = StackIdentity.unencodableFallbackToken("mod:c", new PlainNoOverrides());
        assertEquals(t1, t2);
        assertFalse(t1.contains("@"));

        JsonElement a = JsonParser.parseString("{\"b\":1.0,\"a\":[2.50,0.0]}");
        JsonElement b = JsonParser.parseString("{\"a\":[2.5,-0],\"b\":1}");
        assertEquals(CanonicalJson.canonicalize(a), CanonicalJson.canonicalize(b));
        assertEquals(CanonicalJson.variantHash(a), CanonicalJson.variantHash(b));
        assertNotEquals(CanonicalJson.variantHash(a), CanonicalJson.variantHash(JsonParser.parseString("{}")));
    }

    @Test
    void exportLoaderHandlesFixturePackDropReasonsTagTypesZipAndPathTraversal(@TempDir Path tempDir)
            throws IOException {
        Path modRoot = SystemInvariantsAndPropertyTest.locateModRoot();
        Path planningExports = modRoot.resolve("src/test/resources/planning-exports");
        Path chainExportFixtures = modRoot.resolve("src/test/resources/chainexport");

        ExportJsonCatalogLoader loader = new ExportJsonCatalogLoader();
        assertThrows(IllegalArgumentException.class, () -> ExportJsonCatalogLoader.validatePackName("../evil"));
        assertThrows(IllegalArgumentException.class, () -> ExportJsonCatalogLoader.validatePackName("a/b"));

        LoadedCatalog cat = loader.loadFromDirectory(planningExports, "fixture-pack", true);
        assertTrue(cat.stats().keptRecipes() >= 15);
        assertEquals(1, cat.stats().countOf(DropReason.complexSlot));
        assertEquals(1, cat.stats().countOf(DropReason.unknownKind));
        assertEquals(1, cat.stats().countOf(DropReason.invalidAmount));
        assertEquals(2, cat.stats().countOf(DropReason.emptySlotLists));
        assertEquals(1, cat.stats().countOf(DropReason.emptySlotCandidates));
        assertEquals(1, cat.stats().countOf(DropReason.multiStackOutput));
        assertEquals(1, cat.stats().countOf(DropReason.noOutputAfterValues));
        assertEquals(1, cat.stats().countOf(DropReason.noInputAfterValues));
        assertEquals(1, cat.stats().countOf(DropReason.invalidStack));
        assertEquals(1, cat.stats().countOf(DropReason.tagOutputMulti));
        assertEquals(1, cat.stats().countOf(DropReason.probabilistic));

        LoadedCatalog chainCat = loader.loadFromDirectory(chainExportFixtures, "fixture-pack", true);
        assertEquals(8, chainCat.stats().keptRecipes());

        // Zip loading parity.
        Path zipPath = tempDir.resolve("exports.zip");
        Path srcFile = planningExports.resolve("fixture-pack/recipes-1.json");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry("exports/fixture-pack/recipes-1.json"));
            zos.write(Files.readAllBytes(srcFile));
            zos.closeEntry();
        }
        assertEquals(List.of("fixture-pack"), loader.discoverPacksInZip(zipPath));
        LoadedCatalog fromZip = loader.loadFromZip(zipPath, "fixture-pack", true);
        assertEquals(cat.stats().keptRecipes(), fromZip.stats().keptRecipes());
        assertEquals(cat.stats().totalDropped(), fromZip.stats().totalDropped());
    }

    @Test
    void offlinePlanBenchCliRunsFixturePackDeterministically(@TempDir Path tempDir) throws IOException {
        Path exportsDir = SystemInvariantsAndPropertyTest.locateModRoot()
                .resolve("src/test/resources/planning-exports");
        Path outFile = tempDir.resolve("fixture.json");
        Path summaryFile = tempDir.resolve("summary.json");

        OfflinePlanBench.BenchConfig cfg = OfflinePlanBench.parseArgs(
            new String[]{"--export-dir", exportsDir.toString(), "--pack", "fixture-pack", "--targets",
                    "minecraft:hopper=1,minecraft:stone_bricks=5,minecraft:observer=1", "--budget-ms", "500",
                    "--repeat", "2", "--out", outFile.toString(), "--summary", summaryFile.toString()}
        );
        assertEquals(0, OfflinePlanBench.run(cfg));
        assertTrue(Files.isRegularFile(outFile));
        assertTrue(Files.isRegularFile(summaryFile));

        JsonObject report = JsonParser.parseString(Files.readString(outFile, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(3, report.get("targetsRun").getAsInt());
        assertEquals(3, report.get("validTargets").getAsInt());
        assertEquals(0, report.get("deterministicFailures").getAsInt());

        // Compile-only + per-pack modes.
        OfflinePlanBench.BenchConfig compileOnly = OfflinePlanBench.parseArgs(
            new String[]{"--export-dir", exportsDir.toString(), "--pack", "fixture-pack", "--compile-only", "--out",
                    tempDir.resolve("compile-dir").toString()}
        );
        assertEquals(0, OfflinePlanBench.run(compileOnly));
        assertTrue(Files.isRegularFile(tempDir.resolve("compile-dir/fixture-pack.json")));
    }

    @Test
    void rawRecordMapperHandlesFluidAndComponentMatchModes() {
        RawRecordMapper mapper = new RawRecordMapper();
        var fluidRes = mapper.mapJsonRecipe(
            JsonParser.parseString(
                """
                        {"system":"mod:fluid","source":"custom","id":"mod:mix","machines":["mod:mixer"],
                         "inputs":[{"kind":"stack","stack":{"type":"fluid","fluid":"minecraft:water","amount":1000},"amount":1000,"chance":1.0,"matchStrategy":"identity"}],
                         "outputs":[{"kind":"stack","stack":{"type":"fluid","fluid":"mod:brine","amount":500},"amount":500,"chance":1.0,"matchStrategy":"identity"}]}
                        """
            ).getAsJsonObject(),
            10L,
            0
        );
        assertTrue(fluidRes.isKept());
        var mappedRecord = Objects.requireNonNull(fluidRes.record(), "mapped fluid record");
        assertEquals(ResourceKey.fluid("minecraft:water"), mappedRecord.inputs().get(0).candidates().get(0));
        assertEquals(ResourceKey.fluid("mod:brine"), mappedRecord.outputs().get(0).resource());
    }
}
