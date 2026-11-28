package dev.vfyjxf.cirrus.internal.integration.jei;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ByproductEntry;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import dev.vfyjxf.cirrus.planning.bridge.WorldFacilityRegistry;
import dev.vfyjxf.cirrus.ui.workbench.flow.StepNodeCardRenderer;
import dev.vfyjxf.cirrus.ui.workbench.layout.PreparationLedgerModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.loading.FMLPaths;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Persists bookmarked Cirrus production flows and checklist progress across workbench closes and game restarts.
 */
public final class CirrusPlanBookmarkStore {

    public record MaterialLine(ComponentStackKey resource, long needed) {}

    public record MachineLine(MachineEntry machine, int countToBuild) {}

    public record StepLine(
        String stepId,
        String kind,
        @Nullable ComponentStackKey output,
        long amount,
        long batches,
        @Nullable MachineEntry machine,
        boolean auto,
        double workSeconds,
        double waitSeconds,
        List<String> dependsOn,
        List<ComponentStackKey> inputs,
        List<MaterialLine> inputAmounts
    ) {
        public StepLine {
            amount = Math.max(1L, amount);
            batches = Math.max(1L, batches);
            workSeconds = Double.isFinite(workSeconds) ? Math.max(0.0, workSeconds) : 0.0;
            waitSeconds = Double.isFinite(waitSeconds) ? Math.max(0.0, waitSeconds) : 0.0;
            if (machine != null && machine.isEmpty()) {
                machine = null;
            }
            dependsOn = List.copyOf(dependsOn);
            inputAmounts = List.copyOf(inputAmounts);
            if (inputs.isEmpty() && !inputAmounts.isEmpty()) {
                List<ComponentStackKey> derived = new ArrayList<>(inputAmounts.size());
                for (MaterialLine line : inputAmounts) {
                    derived.add(line.resource());
                }
                inputs = List.copyOf(derived);
            } else {
                inputs = List.copyOf(inputs);
            }
        }

        public StepLine(
            String stepId,
            String kind,
            @Nullable ComponentStackKey output,
            long amount,
            List<String> dependsOn,
            List<ComponentStackKey> inputs
        ) {
            this(stepId, kind, output, amount, amount, null, false, 0.0, 0.0, dependsOn, inputs, List.of());
        }
    }

    public record PlanBookmarkEntry(
        String id,
        ComponentStackKey primaryResource,
        long primaryAmount,
        String title,
        int totalSteps,
        int craftSteps,
        double workSeconds,
        double waitSeconds,
        List<MaterialLine> rawMaterials,
        List<StepLine> steps,
        Set<String> checkedStepIds,
        List<MachineLine> machinesToProvision,
        List<ComponentStackKey> catalysts,
        List<MaterialLine> surplusByproducts,
        List<String> automatedLines,
        @Nullable ProductionPlan livePlan
    ) {
        public PlanBookmarkEntry {
            rawMaterials = List.copyOf(rawMaterials);
            steps = List.copyOf(steps);
            checkedStepIds = Set.copyOf(checkedStepIds);
            machinesToProvision = List.copyOf(machinesToProvision);
            catalysts = List.copyOf(catalysts);
            surplusByproducts = List.copyOf(surplusByproducts);
            automatedLines = List.copyOf(automatedLines);
        }

        public PlanBookmarkEntry(
            String id,
            ComponentStackKey primaryResource,
            long primaryAmount,
            String title,
            int totalSteps,
            int craftSteps,
            double workSeconds,
            double waitSeconds,
            List<MaterialLine> rawMaterials,
            List<StepLine> steps,
            Set<String> checkedStepIds,
            @Nullable ProductionPlan livePlan
        ) {
            this(
                id,
                primaryResource,
                primaryAmount,
                title,
                totalSteps,
                craftSteps,
                workSeconds,
                waitSeconds,
                rawMaterials,
                steps,
                checkedStepIds,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                livePlan
            );
        }

        public PlanBookmarkEntry withCheckedSteps(Set<String> nextChecked) {
            return new PlanBookmarkEntry(
                id,
                primaryResource,
                primaryAmount,
                title,
                totalSteps,
                craftSteps,
                workSeconds,
                waitSeconds,
                rawMaterials,
                steps,
                nextChecked,
                machinesToProvision,
                catalysts,
                surplusByproducts,
                automatedLines,
                livePlan
            );
        }
    }

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, PlanBookmarkEntry> entries = new LinkedHashMap<>();
    private static final Map<String, Set<String>> checklistProgress = new LinkedHashMap<>();
    private static volatile boolean loaded;

    private CirrusPlanBookmarkStore() {}

    public static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = storagePath();
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            loadFromJson(raw);
        } catch (IOException | RuntimeException ignored) {
            // Corrupt or unavailable file starts clean without crashing client startup.
        }
    }

    public static synchronized List<PlanBookmarkEntry> entries() {
        ensureLoaded();
        return List.copyOf(entries.values());
    }

    public static synchronized @Nullable PlanBookmarkEntry find(String id) {
        ensureLoaded();
        return entries.get(id);
    }

    public static synchronized boolean contains(String id) {
        ensureLoaded();
        return entries.containsKey(id);
    }

    public static synchronized PlanBookmarkEntry put(PlanBookmarkEntry entry) {
        ensureLoaded();
        entries.put(entry.id(), entry);
        if (!entry.checkedStepIds().isEmpty()) {
            checklistProgress.put(entry.id(), new TreeSet<>(entry.checkedStepIds()));
        }
        saveToDisk();
        return entry;
    }

    public static synchronized boolean remove(String id) {
        ensureLoaded();
        PlanBookmarkEntry removed = entries.remove(id);
        if (removed != null) {
            saveToDisk();
            return true;
        }
        return false;
    }

    public static synchronized Set<String> checkedSteps(String planKey) {
        ensureLoaded();
        Set<String> stored = checklistProgress.get(planKey);
        if (stored != null) {
            return Set.copyOf(stored);
        }
        PlanBookmarkEntry entry = entries.get(planKey);
        return entry == null ? Set.of() : entry.checkedStepIds();
    }

    public static synchronized void saveCheckedSteps(String planKey, Set<String> checked) {
        ensureLoaded();
        Set<String> copy = new TreeSet<>(checked);
        checklistProgress.put(planKey, copy);
        PlanBookmarkEntry existing = entries.get(planKey);
        if (existing != null) {
            entries.put(planKey, existing.withCheckedSteps(copy));
            saveToDisk();
        }
    }

    public static String planKeyOf(ProductionPlan plan) {
        return planKeyOf(plan, List.of());
    }

    public static String planKeyOf(ProductionPlan plan, List<TargetDemand> targets) {
        if (targets != null && !targets.isEmpty()) {
            TargetDemand first = targets.get(0);
            return first.resource().canonicalKey() + "x" + first.amount();
        }
        return plan.planId();
    }

    public static PlanBookmarkEntry fromPlan(
        ProductionPlan plan,
        @Nullable PreparationLedgerModel ledger,
        Set<String> checkedStepIds,
        String displayTitle
    ) {
        return fromPlan(plan, List.of(), ledger, checkedStepIds, displayTitle);
    }

    public static PlanBookmarkEntry fromPlan(
        ProductionPlan plan,
        List<TargetDemand> targets,
        @Nullable PreparationLedgerModel ledger,
        Set<String> checkedStepIds,
        String displayTitle
    ) {
        Objects.requireNonNull(plan, "plan");
        String id = planKeyOf(plan, targets);
        CirrusPlanningRuntime runtime = CirrusPlanningRuntime.sharedIfCreated();
        CompiledRecipeGraph graph = runtime == null ? null : runtime.currentGraph().orElse(null);

        ComponentStackKey primaryResource = null;
        long primaryAmount = 1L;
        if (targets != null && !targets.isEmpty()) {
            primaryResource = targets.get(0).resource();
            primaryAmount = Math.max(1L, targets.get(0).amount());
        } else {
            for (int i = plan.steps().size() - 1; i >= 0; i--) {
                ComponentStackKey candidate = outputResourceOf(plan.steps().get(i), graph);
                if (candidate != null) {
                    primaryResource = candidate;
                    primaryAmount = outputAmountOf(plan.steps().get(i), graph);
                    break;
                }
            }
        }
        if (primaryResource == null) {
            primaryResource = defaultPrimaryResource();
        }

        List<MaterialLine> materials = new ArrayList<>();
        if (ledger != null && !ledger.acquireRows().isEmpty()) {
            for (PreparationLedgerModel.AcquireRow row : ledger.acquireRows()) {
                materials.add(new MaterialLine(row.resource(), Math.max(1L, row.neededAmount())));
            }
        } else {
            Map<ComponentStackKey, Long> rawTotals = new LinkedHashMap<>();
            for (Map.Entry<ComponentStackKey, Long> e : plan.preparation().toAcquireFromWorld().entries().entrySet()) {
                if (e.getValue() > 0L) {
                    rawTotals.merge(e.getKey(), e.getValue(), Long::sum);
                }
            }
            for (Map.Entry<ComponentStackKey, Long> e : plan.preparation().consumedFromInventory().entries().entrySet()) {
                if (e.getValue() > 0L) {
                    rawTotals.merge(e.getKey(), e.getValue(), Long::sum);
                }
            }
            if (rawTotals.isEmpty()) {
                for (PlanStep step : plan.steps()) {
                    if (step instanceof AcquireStep acquire) {
                        rawTotals.merge(acquire.resource(), Math.max(1L, acquire.amount()), Long::sum);
                    }
                }
            }
            for (Map.Entry<ComponentStackKey, Long> e : rawTotals.entrySet()) {
                materials.add(new MaterialLine(e.getKey(), e.getValue()));
            }
        }

        List<StepLine> stepLines = new ArrayList<>(plan.steps().size());
        int craftSteps = 0;
        for (PlanStep step : plan.steps()) {
            if (step instanceof CraftStep || step instanceof ConvertStep) {
                craftSteps++;
            }
            StepNodeCardRenderer.StepVisuals visuals = StepNodeCardRenderer.resolve(step, graph, null, false);
            ComponentStackKey out = step instanceof AcquireStep acquire ? acquire.resource() : visuals.primaryOutput();
            long outAmount = resolvedOutputAmount(step, visuals);
            long batches = resolvedBatches(step, visuals);
            MachineEntry machine = resolvedMachine(step, visuals);
            List<MaterialLine> inputAmounts = resolvedInputAmounts(step, visuals);
            List<ComponentStackKey> inputKeys = new ArrayList<>(inputAmounts.size());
            for (MaterialLine inLine : inputAmounts) {
                inputKeys.add(inLine.resource());
            }
            stepLines.add(
                new StepLine(
                    step.stepId(),
                    step.getClass().getSimpleName(),
                    out,
                    outAmount,
                    batches,
                    machine,
                    visuals.auto(),
                    step.stepCost().humanActiveSeconds(),
                    step.stepCost().passiveWaitSeconds(),
                    step.dependsOnStepIds(),
                    inputKeys,
                    inputAmounts
                )
            );
        }

        List<MachineLine> machinesToProvision = new ArrayList<>();
        for (Map.Entry<MachineEntry, Integer> e : plan.preparation().machinesToProvision().entrySet()) {
            if (e.getValue() > 0 && !e.getKey().isEmpty()) {
                machinesToProvision.add(new MachineLine(e.getKey(), e.getValue()));
            }
        }
        List<ComponentStackKey> catalysts = new ArrayList<>(plan.preparation().catalystsRequired());
        Map<ComponentStackKey, Long> mergedByproducts = new LinkedHashMap<>();
        for (ByproductEntry byp : plan.surplusByproducts()) {
            if (byp.amount() > 0L) {
                mergedByproducts.merge(byp.resource(), byp.amount(), Long::sum);
            }
        }
        List<MaterialLine> surplusByproducts = new ArrayList<>(mergedByproducts.size());
        for (Map.Entry<ComponentStackKey, Long> e : mergedByproducts.entrySet()) {
            surplusByproducts.add(new MaterialLine(e.getKey(), e.getValue()));
        }
        List<String> automatedLines = List.copyOf(plan.preparation().existingLinesUsed());

        Set<String> mergedChecked = new LinkedHashSet<>(checkedSteps(id));
        mergedChecked.addAll(checkedStepIds);
        String resolvedTitle = displayTitle == null || displayTitle.isBlank()
                ? primaryResource.displayId()
                : displayTitle;
        return new PlanBookmarkEntry(
            id,
            primaryResource,
            primaryAmount,
            resolvedTitle,
            plan.steps().size(),
            craftSteps,
            plan.totalCost().humanActiveSeconds(),
            plan.totalCost().passiveWaitSeconds(),
            materials,
            stepLines,
            mergedChecked,
            machinesToProvision,
            catalysts,
            surplusByproducts,
            automatedLines,
            plan
        );
    }

    static String serializeToJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 2);
        JsonArray arr = new JsonArray();
        for (PlanBookmarkEntry entry : entries.values()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", entry.id());
            JsonElement primaryJson = encodeKey(entry.primaryResource());
            if (primaryJson != null) {
                obj.add("primaryResource", primaryJson);
            }
            obj.addProperty("amount", entry.primaryAmount());
            obj.addProperty("title", entry.title());
            obj.addProperty("totalSteps", entry.totalSteps());
            obj.addProperty("craftSteps", entry.craftSteps());
            obj.addProperty("workSeconds", entry.workSeconds());
            obj.addProperty("waitSeconds", entry.waitSeconds());

            JsonArray mats = new JsonArray();
            for (MaterialLine m : entry.rawMaterials()) {
                JsonElement encoded = encodeMaterialLine(m);
                if (encoded != null) {
                    mats.add(encoded);
                }
            }
            obj.add("rawMaterials", mats);

            JsonArray stepsArr = new JsonArray();
            for (StepLine s : entry.steps()) {
                JsonObject so = new JsonObject();
                so.addProperty("stepId", s.stepId());
                so.addProperty("kind", s.kind());
                if (s.output() != null) {
                    JsonElement outJson = encodeKey(s.output());
                    if (outJson != null) {
                        so.add("output", outJson);
                    }
                }
                so.addProperty("amount", s.amount());
                so.addProperty("batches", s.batches());
                if (s.machine() != null && !s.machine().isEmpty()) {
                    JsonElement mJson = encodeMachine(s.machine());
                    if (mJson != null) {
                        so.add("machine", mJson);
                    }
                }
                if (s.auto()) {
                    so.addProperty("auto", true);
                }
                if (s.workSeconds() > 0.0) {
                    so.addProperty("workSeconds", s.workSeconds());
                }
                if (s.waitSeconds() > 0.0) {
                    so.addProperty("waitSeconds", s.waitSeconds());
                }
                JsonArray depsArr = new JsonArray();
                for (String dep : s.dependsOn()) {
                    depsArr.add(dep);
                }
                so.add("dependsOn", depsArr);
                JsonArray insArr = new JsonArray();
                for (ComponentStackKey in : s.inputs()) {
                    JsonElement inJson = encodeKey(in);
                    if (inJson != null) {
                        insArr.add(inJson);
                    }
                }
                so.add("inputs", insArr);
                if (!s.inputAmounts().isEmpty()) {
                    JsonArray inAmtArr = new JsonArray();
                    for (MaterialLine inLine : s.inputAmounts()) {
                        JsonElement lineJson = encodeMaterialLine(inLine);
                        if (lineJson != null) {
                            inAmtArr.add(lineJson);
                        }
                    }
                    so.add("inputAmounts", inAmtArr);
                }
                stepsArr.add(so);
            }
            obj.add("steps", stepsArr);

            if (!entry.machinesToProvision().isEmpty()) {
                JsonArray machArr = new JsonArray();
                for (MachineLine ml : entry.machinesToProvision()) {
                    JsonElement mJson = encodeMachine(ml.machine());
                    if (mJson != null) {
                        JsonObject mo = new JsonObject();
                        mo.add("machine", mJson);
                        mo.addProperty("countToBuild", ml.countToBuild());
                        machArr.add(mo);
                    }
                }
                obj.add("machinesToProvision", machArr);
            }
            if (!entry.catalysts().isEmpty()) {
                JsonArray catArr = new JsonArray();
                for (ComponentStackKey cat : entry.catalysts()) {
                    JsonElement cJson = encodeKey(cat);
                    if (cJson != null) {
                        catArr.add(cJson);
                    }
                }
                obj.add("catalysts", catArr);
            }
            if (!entry.surplusByproducts().isEmpty()) {
                JsonArray surpArr = new JsonArray();
                for (MaterialLine surp : entry.surplusByproducts()) {
                    JsonElement sJson = encodeMaterialLine(surp);
                    if (sJson != null) {
                        surpArr.add(sJson);
                    }
                }
                obj.add("surplusByproducts", surpArr);
            }
            if (!entry.automatedLines().isEmpty()) {
                JsonArray linesArr = new JsonArray();
                for (String line : entry.automatedLines()) {
                    linesArr.add(line);
                }
                obj.add("automatedLines", linesArr);
            }

            JsonArray checked = new JsonArray();
            for (String stepId : entry.checkedStepIds()) {
                checked.add(stepId);
            }
            obj.add("checkedStepIds", checked);
            arr.add(obj);
        }
        root.add("bookmarks", arr);
        return gson.toJson(root);
    }

    static void loadFromJson(String raw) {
        JsonElement parsed = JsonParser.parseString(raw);
        if (!parsed.isJsonObject()) {
            return;
        }
        JsonObject root = parsed.getAsJsonObject();
        JsonArray arr = root.getAsJsonArray("bookmarks");
        if (arr == null) {
            return;
        }
        entries.clear();
        for (JsonElement elem : arr) {
            if (!elem.isJsonObject()) {
                continue;
            }
            JsonObject obj = elem.getAsJsonObject();
            String id = getString(obj, "id", "");
            ComponentStackKey primary = decodeKey(obj.get("primaryResource"));
            if (primary == null) {
                primary = decodeLegacyKey(getString(obj, "typeId", "minecraft:item"), getString(obj, "resourceId", ""));
            }
            if (id.isBlank() || primary == null) {
                continue;
            }
            long amount = Math.max(1L, getLong(obj, "amount", 1L));
            String title = getString(obj, "title", primary.displayId());
            int totalSteps = Math.max(0, getInt(obj, "totalSteps", 0));
            int craftSteps = Math.max(0, getInt(obj, "craftSteps", 0));
            double work = getDouble(obj, "workSeconds", 0.0);
            double wait = getDouble(obj, "waitSeconds", 0.0);

            List<MaterialLine> mats = parseMaterialLines(obj.getAsJsonArray("rawMaterials"));
            List<StepLine> parsedSteps = new ArrayList<>();
            JsonArray stepsArr = obj.getAsJsonArray("steps");
            if (stepsArr != null) {
                for (JsonElement se : stepsArr) {
                    if (!se.isJsonObject()) {
                        continue;
                    }
                    JsonObject so = se.getAsJsonObject();
                    String sid = getString(so, "stepId", "");
                    String kind = getString(so, "kind", "CraftStep");
                    ComponentStackKey outRes = decodeKey(so.get("output"));
                    if (outRes == null) {
                        outRes = decodeLegacyKey(getString(so, "outType", "minecraft:item"), getString(so, "outId", ""));
                    }
                    if (!sid.isBlank()) {
                        List<String> deps = new ArrayList<>();
                        JsonArray depsArr = so.getAsJsonArray("dependsOn");
                        if (depsArr != null) {
                            for (JsonElement de : depsArr) {
                                if (de.isJsonPrimitive()) {
                                    deps.add(de.getAsString());
                                }
                            }
                        }
                        List<ComponentStackKey> ins = new ArrayList<>();
                        JsonArray insArr = so.getAsJsonArray("inputs");
                        if (insArr != null) {
                            for (JsonElement ie : insArr) {
                                ComponentStackKey ik = decodeKey(ie);
                                if (ik == null && ie.isJsonObject()) {
                                    JsonObject io = ie.getAsJsonObject();
                                    ik = decodeLegacyKey(
                                        getString(io, "typeId", "minecraft:item"),
                                        getString(io, "resourceId", "")
                                    );
                                }
                                if (ik != null) {
                                    ins.add(ik);
                                }
                            }
                        }
                        List<MaterialLine> inAmts = parseMaterialLines(so.getAsJsonArray("inputAmounts"));
                        long amt = Math.max(1L, getLong(so, "amount", 1L));
                        long batches = Math.max(1L, getLong(so, "batches", amt));
                        MachineEntry machine = decodeMachine(so.get("machine"));
                        if (machine == null) {
                            machine = decodeLegacyMachine(getString(so, "machineId", ""));
                        }
                        boolean auto = getBoolean(so, "auto", false);
                        double stepWork = getDouble(so, "workSeconds", 0.0);
                        double stepWait = getDouble(so, "waitSeconds", 0.0);
                        parsedSteps.add(
                            new StepLine(
                                sid,
                                kind,
                                outRes,
                                amt,
                                batches,
                                machine,
                                auto,
                                stepWork,
                                stepWait,
                                deps,
                                ins,
                                inAmts
                            )
                        );
                    }
                }
            }
            List<MachineLine> parsedMachines = new ArrayList<>();
            JsonArray machArr = obj.getAsJsonArray("machinesToProvision");
            if (machArr != null) {
                for (JsonElement me : machArr) {
                    if (!me.isJsonObject()) {
                        continue;
                    }
                    JsonObject mo = me.getAsJsonObject();
                    MachineEntry m = decodeMachine(mo.get("machine"));
                    if (m == null) {
                        m = decodeLegacyMachine(getString(mo, "machineId", ""));
                    }
                    int cnt = getInt(mo, "countToBuild", 0);
                    if (m != null && !m.isEmpty() && cnt > 0) {
                        parsedMachines.add(new MachineLine(m, cnt));
                    }
                }
            }
            List<ComponentStackKey> parsedCatalysts = new ArrayList<>();
            JsonArray catArr = obj.getAsJsonArray("catalysts");
            if (catArr != null) {
                for (JsonElement ce : catArr) {
                    ComponentStackKey ck = decodeKey(ce);
                    if (ck == null && ce.isJsonObject()) {
                        JsonObject co = ce.getAsJsonObject();
                        ck = decodeLegacyKey(
                            getString(co, "typeId", "minecraft:item"),
                            getString(co, "resourceId", "")
                        );
                    }
                    if (ck != null) {
                        parsedCatalysts.add(ck);
                    }
                }
            }
            List<MaterialLine> parsedSurplus = parseMaterialLines(obj.getAsJsonArray("surplusByproducts"));
            List<String> parsedLines = new ArrayList<>();
            JsonArray linesArr = obj.getAsJsonArray("automatedLines");
            if (linesArr != null) {
                for (JsonElement le : linesArr) {
                    if (le.isJsonPrimitive()) {
                        parsedLines.add(le.getAsString());
                    }
                }
            }
            Set<String> checked = new TreeSet<>();
            JsonArray checkedArr = obj.getAsJsonArray("checkedStepIds");
            if (checkedArr != null) {
                for (JsonElement ce : checkedArr) {
                    if (ce.isJsonPrimitive()) {
                        checked.add(ce.getAsString());
                    }
                }
            }
            PlanBookmarkEntry entry = new PlanBookmarkEntry(
                id,
                primary,
                amount,
                title,
                totalSteps,
                craftSteps,
                work,
                wait,
                mats,
                parsedSteps,
                checked,
                parsedMachines,
                parsedCatalysts,
                parsedSurplus,
                parsedLines,
                null
            );
            entries.put(id, entry);
            if (!checked.isEmpty()) {
                checklistProgress.put(id, new TreeSet<>(checked));
            }
        }
    }

    static synchronized void resetForTests() {
        entries.clear();
        checklistProgress.clear();
        loaded = true;
    }

    private static void saveToDisk() {
        Path path = storagePath();
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, serializeToJson(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ignored) {
            // Best-effort persistence.
        }
    }

    private static @Nullable Path storagePath() {
        try {
            Path configDir = FMLPaths.CONFIGDIR.get();
            return configDir == null ? null : configDir.resolve("calculator_cirrus").resolve("jei_plan_bookmarks.json");
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static ComponentStackKey defaultPrimaryResource() {
        return ComponentStackKey.ofItem(Items.CRAFTING_TABLE);
    }

    private static @Nullable JsonElement encodeKey(ComponentStackKey key) {
        return WorldFacilityRegistry.resourceKeyCodec.encodeStart(JsonOps.INSTANCE, key).result().orElse(null);
    }

    private static @Nullable ComponentStackKey decodeKey(@Nullable JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return null;
        }
        return WorldFacilityRegistry.resourceKeyCodec.parse(JsonOps.INSTANCE, el).result().orElse(null);
    }

    private static @Nullable ComponentStackKey decodeLegacyKey(String typeId, String resourceId) {
        if (resourceId == null || resourceId.isBlank()) {
            return null;
        }
        ResourceLocation loc = ResourceLocation.tryParse(resourceId);
        if (loc == null) {
            return null;
        }
        if ("minecraft:fluid".equals(typeId)) {
            Optional<Fluid> fluid = BuiltInRegistries.FLUID.getOptional(loc);
            return fluid.map(ComponentStackKey::ofFluid).orElse(null);
        }
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(loc);
        return item.map(ComponentStackKey::ofItem).orElse(null);
    }

    private static @Nullable JsonElement encodeMachine(MachineEntry machine) {
        return WorldFacilityRegistry.machineEntryCodec.encodeStart(JsonOps.INSTANCE, machine).result().orElse(null);
    }

    private static @Nullable MachineEntry decodeMachine(@Nullable JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return null;
        }
        return WorldFacilityRegistry.machineEntryCodec.parse(JsonOps.INSTANCE, el).result().orElse(null);
    }

    private static @Nullable MachineEntry decodeLegacyMachine(String machineId) {
        ComponentStackKey key = decodeLegacyKey("minecraft:item", machineId);
        return key == null ? null : MachineEntry.fromKey(key);
    }

    private static @Nullable JsonElement encodeMaterialLine(MaterialLine line) {
        JsonElement keyJson = encodeKey(line.resource());
        if (keyJson == null) {
            return null;
        }
        JsonObject mo = new JsonObject();
        mo.add("resource", keyJson);
        mo.addProperty("needed", line.needed());
        return mo;
    }

    private static List<MaterialLine> parseMaterialLines(@Nullable JsonArray array) {
        if (array == null) {
            return List.of();
        }
        List<MaterialLine> list = new ArrayList<>();
        for (JsonElement el : array) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject mo = el.getAsJsonObject();
            ComponentStackKey res = decodeKey(mo.get("resource"));
            if (res == null) {
                res = decodeLegacyKey(getString(mo, "typeId", "minecraft:item"), getString(mo, "resourceId", ""));
            }
            if (res != null) {
                list.add(new MaterialLine(res, Math.max(1L, getLong(mo, "needed", 1L))));
            }
        }
        return list;
    }

    private static @Nullable ComponentStackKey outputResourceOf(PlanStep step, @Nullable CompiledRecipeGraph graph) {
        if (step instanceof AcquireStep acquire) {
            return acquire.resource();
        }
        StepNodeCardRenderer.StepVisuals visuals = StepNodeCardRenderer.resolve(step, graph, null, false);
        return visuals.primaryOutput();
    }

    private static long outputAmountOf(PlanStep step, @Nullable CompiledRecipeGraph graph) {
        StepNodeCardRenderer.StepVisuals visuals = StepNodeCardRenderer.resolve(step, graph, null, false);
        return resolvedOutputAmount(step, visuals);
    }

    private static long resolvedOutputAmount(PlanStep step, StepNodeCardRenderer.StepVisuals visuals) {
        if (step instanceof AcquireStep acquire) {
            return Math.max(1L, acquire.amount());
        }
        if (visuals.outputAmount() > 0L) {
            return visuals.outputAmount();
        }
        if (visuals.count() > 0L) {
            return visuals.count();
        }
        return resolvedBatches(step, visuals);
    }

    private static long resolvedBatches(PlanStep step, StepNodeCardRenderer.StepVisuals visuals) {
        if (visuals.batchCount() > 0L) {
            return visuals.batchCount();
        }
        if (step instanceof CraftStep craft) {
            return Math.max(1L, craft.batches());
        }
        if (step instanceof ConvertStep convert) {
            return Math.max(1L, convert.batches());
        }
        if (step instanceof ProvisionMachineStep provision) {
            return Math.max(1L, provision.countToBuild());
        }
        return 1L;
    }

    private static @Nullable MachineEntry resolvedMachine(PlanStep step, StepNodeCardRenderer.StepVisuals visuals) {
        if (visuals.machine() != null && !visuals.machine().isEmpty()) {
            return visuals.machine();
        }
        if (step instanceof SetupLineStep setup && !setup.primaryMachine().isEmpty()) {
            return setup.primaryMachine();
        }
        if (step instanceof ProvisionMachineStep provision && !provision.machine().isEmpty()) {
            return provision.machine();
        }
        return null;
    }

    private static List<MaterialLine> resolvedInputAmounts(PlanStep step, StepNodeCardRenderer.StepVisuals visuals) {
        if (step instanceof AcquireStep) {
            return List.of();
        }
        List<StepNodeCardRenderer.InputAmount> raw = visuals.inputAmounts();
        if (!raw.isEmpty()) {
            int limit = Math.min(8, raw.size());
            List<MaterialLine> out = new ArrayList<>(limit);
            for (int i = 0; i < limit; i++) {
                StepNodeCardRenderer.InputAmount item = raw.get(i);
                out.add(new MaterialLine(item.resource(), Math.max(1L, item.amount())));
            }
            return out;
        }
        List<ComponentStackKey> keys = visuals.inputs();
        if (keys.isEmpty()) {
            return List.of();
        }
        int limit = Math.min(8, keys.size());
        List<MaterialLine> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            out.add(new MaterialLine(keys.get(i), 1L));
        }
        return out;
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : fallback;
    }

    private static long getLong(JsonObject obj, String key, long fallback) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsLong() : fallback;
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsInt() : fallback;
    }

    private static double getDouble(JsonObject obj, String key, double fallback) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsDouble() : fallback;
    }
}
