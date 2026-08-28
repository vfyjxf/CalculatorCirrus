package dev.vfyjxf.cirrus.recipe.chainexport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline end-to-end smoke test: fixture export pack -> load -> graph build ->
 * solve -> classify -> render -> files on disk. No Minecraft server needed.
 */
class ChainExportSmokeTest {

    @TempDir
    Path outDir;

    @Test
    void fixturePackProducesClassifiedChains() throws Exception {
        // ModDev's unit-test runner relocates the working dir, so resolve the
        // fixture from the test classpath instead of a relative path.
        var url = ChainExportSmokeTest.class.getClassLoader().getResource("chainexport/fixture-pack");
        assertTrue(url != null, "fixture pack missing from test resources");
        Path fixture = Path.of(url.toURI());

        ChainExportMain.Config config = new ChainExportMain.Config(
            fixture.getParent(), outDir, "fixture-pack", 8, 4, 42, "preview", 5_000, 0, true, "decompress", 0);
        int chains = ChainExportMain.runPack(fixture, outDir, config);

        assertTrue(chains > 0, "no chains generated");
        Path packOut = outDir.resolve("fixture-pack");
        assertTrue(Files.isRegularFile(packOut.resolve("manifest.txt")));
        assertTrue(Files.isRegularFile(packOut.resolve("index.tsv")));
        assertTrue(Files.isRegularFile(packOut.resolve("targets.tsv")));

        List<String> index = Files.readAllLines(packOut.resolve("index.tsv"));
        assertEquals(chains, index.size() - 1, "index rows must match generated chain count");

        int chainTexts = 0;
        try (Stream<Path> files = Files.list(packOut)) {
            for (Path file : files.filter(p -> p.getFileName().toString().startsWith("chains-")).toList()) {
                chainTexts += countChains(file);
            }
        }
        assertEquals(chains, chainTexts, "rendered chains must match generated chain count");

        String manifest = Files.readString(packOut.resolve("manifest.txt"));
        assertTrue(manifest.contains("recipesTotal: 8"), "manifest should report the fixture recipe count");
        assertTrue(manifest.contains("rule:"), "manifest should record the active rule");
    }

    private static int countChains(Path file) throws Exception {
        int count = 0;
        for (String line : Files.readAllLines(file)) {
            if (line.startsWith("### CHAIN ")) {
                count++;
            }
        }
        return count;
    }
}
