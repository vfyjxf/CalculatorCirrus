package dev.vfyjxf.cirrus.ui;

import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CirrusLangsContractTest {
    private static final Pattern keyPattern = Pattern.compile("\"([^\"]+)\"\\s*:");

    @Test
    void registeredUiLangEntriesExistInBundledLanguageFiles() throws Exception {
        Set<String> entries = CirrusLangs.entries().stream()
            .map(LangEntry::key)
            .collect(Collectors.toUnmodifiableSet());

        assertTrue(entries.size() > 80, "expected the UI lang catalog to cover the main UI surfaces");
        assertContainsAll(resolveLangFile("en_us"), entries);
        assertContainsAll(resolveLangFile("zh_cn"), entries);
    }

    private static void assertContainsAll(Path file, Set<String> expected) throws Exception {
        Set<String> keys = keys(file);
        Set<String> missing = expected.stream()
            .filter(key -> !keys.contains(key))
            .collect(Collectors.toCollection(java.util.TreeSet::new));

        assertTrue(missing.isEmpty(), file.getFileName() + " is missing lang keys: " + missing);
    }

    private static Set<String> keys(Path file) throws Exception {
        String json = Files.readString(file);
        java.util.HashSet<String> keys = new java.util.HashSet<>();
        var matcher = keyPattern.matcher(json);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    private static Path resolveLangFile(String locale) {
        String relative = "src/main/resources/assets/calculator_cirrus/lang/" + locale + ".json";
        String moduleRelative = "Mod/" + relative;
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path local = cursor.resolve(relative);
            if (Files.exists(local)) {
                return local;
            }
            Path module = cursor.resolve(moduleRelative);
            if (Files.exists(module)) {
                return module;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate " + relative + " from " + Path.of("").toAbsolutePath());
    }
}
