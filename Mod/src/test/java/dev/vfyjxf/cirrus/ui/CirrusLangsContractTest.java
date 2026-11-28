package dev.vfyjxf.cirrus.ui;

import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CirrusLangsContractTest {
    private static final Pattern keyPattern = Pattern.compile("\"([^\"]+)\"\\s*:");

    private static final String workbenchPrefix = "cirrus.ui.workbench.";

    private static final List<String> corePlanningKeys = List.of(
        "cirrus.acquire.base",
        "cirrus.acquire.base_contract",
        "cirrus.acquire.certified_natural",
        "cirrus.acquire.cost_override",
        "cirrus.acquire.natural",
        "cirrus.acquire.player_base",
        "cirrus.acquire.player_free",
        "cirrus.acquire.rare_natural",
        "cirrus.acquire.spi",
        "cirrus.acquire.unknown",
        "cirrus.conflict.relax_rule",
        "cirrus.conflict.relax_rule_set",
        "cirrus.conflict.too_many_rules_to_localize",
        "cirrus.prompt.machine_fork.title",
        "cirrus.prompt.machine_fork.explanation",
        "cirrus.prompt.machine_fork.option.accept_build",
        "cirrus.prompt.machine_fork.option.already_owned",
        "cirrus.prompt.machine_fork.option.reject_build",
        "cirrus.prompt.tag_choice.title",
        "cirrus.prompt.tag_choice.explanation",
        "cirrus.prompt.tag_choice.option.pick",
        "cirrus.prompt.expensive_leaf.title",
        "cirrus.prompt.expensive_leaf.explanation",
        "cirrus.prompt.expensive_leaf.option.treat_free",
        "cirrus.prompt.expensive_leaf.option.avoid_resource",
        "cirrus.prompt.expensive_leaf.option.keep_gather",
        "cirrus.prompt.suspicious.title",
        "cirrus.prompt.suspicious.explanation",
        "cirrus.prompt.suspicious.option.accept_recipe",
        "cirrus.prompt.suspicious.option.ban_recipe",
        "cirrus.prompt.suspicious.option.ban_system",
        "cirrus.prompt.h1_machine.title",
        "cirrus.prompt.h1_machine.explanation",
        "cirrus.prompt.h1_machine.option.already_owned",
        "cirrus.prompt.h1_machine.option.accept_build",
        "cirrus.prompt.h1_machine.option.reject_machine",
        "cirrus.prompt.h1_machine.option.skip_system",
        "cirrus.prompt.h2_base.title",
        "cirrus.prompt.h2_base.explanation",
        "cirrus.prompt.h2_base.option.set_base",
        "cirrus.prompt.h2_base.option.avoid_resource",
        "cirrus.prompt.h2_base.option.keep_current",
        "cirrus.prompt.h3_catalyst.title",
        "cirrus.prompt.h3_catalyst.explanation",
        "cirrus.prompt.h3_catalyst.option.already_own",
        "cirrus.prompt.h3_catalyst.option.craft_with_plan",
        "cirrus.prompt.h4_tag.title",
        "cirrus.prompt.h4_tag.explanation",
        "cirrus.prompt.h4_tag.option.anchor_world",
        "cirrus.prompt.h5_suspicious.title",
        "cirrus.prompt.h5_suspicious.explanation",
        "cirrus.prompt.h5_suspicious.option.confirm_trusted",
        "cirrus.prompt.h5_suspicious.option.ban_recipe",
        "cirrus.prompt.h5_suspicious.option.ban_system",
        "cirrus.prompt.h6_trip.title",
        "cirrus.prompt.h6_trip.explanation",
        "cirrus.prompt.h6_trip.option.stay_in_base",
        "cirrus.prompt.h6_trip.option.already_base",
        "cirrus.prompt.h6_trip.option.accept_trip",
        "cirrus.strategy.balanced",
        "cirrus.strategy.min_human_time",
        "cirrus.strategy.no_new_machines",
        "cirrus.strategy.min_raw_materials",
        "cirrus.strategy.invest_automation",
        "cirrus.strategy.alternative",
        "cirrus.strategy.auto_smart",
        "cirrus.strategy.stay_at_base",
        "cirrus.strategy.quick_handcraft",
        "cirrus.strategy.factory_scale",
        "cirrus.strategy.frugal_materials",
        "cirrus.strategy.low_cognitive",
        "cirrus.strategy.alternative_branch"
    );

    private static final Set<String> workbenchFamilies = Set.of(
        "mode",
        "theme",
        "tab",
        "topbar",
        "palette",
        "flow",
        "ledger",
        "checklist",
        "pareto",
        "copilot",
        "dnd",
        "timeline",
        "action",
        "facilities",
        "settings"
    );

    @Test
    void registeredUiLangEntriesExistInBundledLanguageFiles() throws Exception {
        Set<String> entries = registeredKeys();

        assertContainsAll(resolveLangFile("en_us"), entries);
        assertContainsAll(resolveLangFile("zh_cn"), entries);
    }

    @Test
    void planningCoreKeysAreRegistered() throws Exception {
        Set<String> entries = registeredKeys();
        Set<String> missing = corePlanningKeys.stream().filter(key -> !entries.contains(key))
                .collect(Collectors.toCollection(TreeSet::new));

        assertTrue(missing.isEmpty(), "planning core keys are not registered in CirrusLangs: " + missing);
        assertContainsAll(resolveLangFile("en_us"), corePlanningKeys.stream().collect(Collectors.toUnmodifiableSet()));
        assertContainsAll(resolveLangFile("zh_cn"), corePlanningKeys.stream().collect(Collectors.toUnmodifiableSet()));
    }

    @Test
    void workbenchKeyFamiliesAreComplete() throws Exception {
        Set<String> entries = registeredKeys();
        Set<String> workbenchKeys = entries.stream().filter(key -> key.startsWith(workbenchPrefix))
                .collect(Collectors.toUnmodifiableSet());

        Set<String> families = workbenchKeys.stream().map(key -> key.substring(workbenchPrefix.length()))
                .map(CirrusLangsContractTest::familyOf).collect(Collectors.toUnmodifiableSet());
        Set<String> missingFamilies = workbenchFamilies.stream().filter(family -> !families.contains(family))
                .collect(Collectors.toCollection(TreeSet::new));

        assertTrue(missingFamilies.isEmpty(), "workbench key families are missing: " + missingFamilies);
        assertContainsAll(resolveLangFile("en_us"), workbenchKeys);
        assertContainsAll(resolveLangFile("zh_cn"), workbenchKeys);
    }

    @Test
    void workbenchThemeModesExposeRegisteredLabels() throws Exception {
        assertEquals(WorkbenchThemeMode.obsidianBlueprint, WorkbenchThemeMode.defaultMode());
        Set<String> en = keys(resolveLangFile("en_us"));
        Set<String> zh = keys(resolveLangFile("zh_cn"));

        for (WorkbenchThemeMode mode : WorkbenchThemeMode.values()) {
            String key = mode.labelKey().key();
            assertTrue(en.contains(key), "en_us is missing theme label " + key);
            assertTrue(zh.contains(key), "zh_cn is missing theme label " + key);
        }
    }

    private static String familyOf(String workbenchKey) {
        int dot = workbenchKey.indexOf('.');
        return dot < 0 ? workbenchKey : workbenchKey.substring(0, dot);
    }

    private static Set<String> registeredKeys() {
        return CirrusLangs.entries().stream().map(LangEntry::key).collect(Collectors.toUnmodifiableSet());
    }

    private static void assertContainsAll(Path file, Set<String> expected) throws Exception {
        Set<String> keys = keys(file);
        Set<String> missing = expected.stream().filter(key -> !keys.contains(key))
                .collect(Collectors.toCollection(TreeSet::new));

        assertTrue(missing.isEmpty(), file.getFileName() + " is missing lang keys: " + missing);
    }

    private static Set<String> keys(Path file) throws Exception {
        String json = Files.readString(file);
        HashSet<String> keys = new HashSet<>();
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
