package dev.vfyjxf.cirrus.planning.core.graph;

import dev.vfyjxf.cirrus.api.planning.graph.CompiledCycleMacro;
import dev.vfyjxf.cirrus.api.planning.graph.GraphCompilationStats;
import dev.vfyjxf.cirrus.api.planning.graph.RawIngredientSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawOutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeCatalogSnapshot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeAdvisory;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeGraphCompiler;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeRole;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmissionPolicy;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.planning.core.lang.Fnv1a64;
import dev.vfyjxf.cirrus.planning.core.lang.SafeMath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class DefaultRecipeGraphCompiler implements RecipeGraphCompiler {

    public static final long MAX_SENSIBLE_AMOUNT = 1_000_000_000L;
    private static final int JEI_MULTI_OUTPUT_SPLIT_THRESHOLD = 4;
    private static final int MASS_SINK_THRESHOLD = 64;

    private static final Set<String> COLORS = Set.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    );
    private static final Set<String> RECYCLING_OUTPUT_SUFFIXES = Set.of(
            "ingot", "nugget", "dust", "gem", "scrap", "shard"
    );
    private static final Set<String> RECYCLING_PRODUCT_TOKENS = Set.of(
            "plate", "gear", "rod", "wire", "sword", "pickaxe", "axe", "shovel", "hoe",
            "helmet", "chestplate", "leggings", "boots", "armor", "horse_armor", "shield",
            "door", "trapdoor", "bars", "chain", "bucket", "cauldron", "hopper", "minecart",
            "rail", "lantern", "anvil", "block", "slab", "stairs", "wall", "fence", "tool"
    );

    public DefaultRecipeGraphCompiler() {
    }

    @Override
    public IndexedRecipeGraph compile(RawRecipeCatalogSnapshot rawCatalog, SystemAdmissionPolicy policy) {
        Objects.requireNonNull(rawCatalog, "rawCatalog");
        Objects.requireNonNull(policy, "policy");

        int totalRaw = rawCatalog.recipes().size();
        int excludedInvalid = 0;
        int excludedDisplay = 0;
        int excludedByPolicy = 0;
        int excludedInsane = 0;
        int excludedNoInputs = 0;
        int[] normalizedIds = new int[1];

        Map<String, SystemAdmission> admissionCache = new HashMap<>();
        ResourceDictionary.Builder dictBuilder = new ResourceDictionary.Builder();
        List<StageRecord> staged = new ArrayList<>(totalRaw);

        for (RawRecipeRecord raw : rawCatalog.recipes()) {
            if (raw == null || raw.systemId().isEmpty()) {
                excludedInvalid++;
                continue;
            }
            SystemAdmission sysAdm = admissionCache.computeIfAbsent(
                    raw.systemId(), s -> policy.classify(s, raw.sourceKind()));
            if (sysAdm == SystemAdmission.EXCLUDED_DISPLAY) {
                excludedDisplay++;
                continue;
            }
            SystemAdmission recAdm = policy.classifyRecipe(raw);
            if (recAdm == SystemAdmission.EXCLUDED_DISPLAY) {
                excludedByPolicy++;
                continue;
            }
            if (raw.outputs().isEmpty()) {
                excludedInvalid++;
                continue;
            }

            // Merge outputs by normalized ResourceKey.
            LinkedHashMap<Integer, Long> mergedOuts = new LinkedHashMap<>();
            boolean invalid = false;
            boolean insane = false;
            for (RawOutputSlot out : raw.outputs()) {
                if (out == null || out.amount() <= 0L) {
                    invalid = true;
                    break;
                }
                if (out.amount() > MAX_SENSIBLE_AMOUNT) {
                    insane = true;
                    break;
                }
                ResourceKey normKey = ResourceDictionary.normalizeOrNull(out.resource(), normalizedIds);
                if (normKey == null) {
                    invalid = true;
                    break;
                }
                int resTempId = dictBuilder.intern(normKey);
                long sum = SafeMath.addSaturating(mergedOuts.getOrDefault(resTempId, 0L), out.amount());
                if (sum > MAX_SENSIBLE_AMOUNT) {
                    insane = true;
                    break;
                }
                mergedOuts.put(resTempId, sum);
            }
            if (insane) {
                excludedInsane++;
                continue;
            }
            if (invalid || mergedOuts.isEmpty()) {
                excludedInvalid++;
                continue;
            }

            // Parse input slots.
            List<TempInput> parsedInputs = new ArrayList<>(raw.inputs().size());
            List<TempCat> parsedCats = new ArrayList<>(2);
            for (RawIngredientSlot in : raw.inputs()) {
                if (in == null || in.candidates().isEmpty()) {
                    invalid = true;
                    break;
                }
                if (in.amount() < 0L) {
                    invalid = true;
                    break;
                }
                if (in.amount() > MAX_SENSIBLE_AMOUNT) {
                    insane = true;
                    break;
                }
                int[] candTempIds = new int[in.candidates().size()];
                int validCount = 0;
                for (ResourceKey cand : in.candidates()) {
                    ResourceKey normKey = ResourceDictionary.normalizeOrNull(cand, normalizedIds);
                    if (normKey != null) {
                        candTempIds[validCount++] = dictBuilder.intern(normKey);
                    }
                }
                if (validCount == 0) {
                    invalid = true;
                    break;
                }
                if (validCount < candTempIds.length) {
                    candTempIds = Arrays.copyOf(candTempIds, validCount);
                }
                if (in.amount() == 0L) {
                    // Explicit non-consumed catalyst slot from runtime notConsumed() or durability clause.
                    if (in.durabilityCostPerBatch() > 0 || in.maxDurability() > 0) {
                        parsedCats.add(new TempCat(candTempIds, in.durabilityCostPerBatch(), in.maxDurability()));
                    } else {
                        invalid = true;
                        break;
                    }
                    continue;
                }
                if (in.durabilityCostPerBatch() > 0 && in.maxDurability() > 0) {
                    parsedCats.add(new TempCat(candTempIds, in.durabilityCostPerBatch(), in.maxDurability()));
                    continue;
                }
                parsedInputs.add(new TempInput(
                        in.tagId().orElse(null),
                        candTempIds,
                        in.amount(),
                        in.matchMode()
                ));
            }
            if (insane) {
                excludedInsane++;
                continue;
            }
            if (invalid) {
                excludedInvalid++;
                continue;
            }

            RecipeAdvisory baseAdvisory = policy.advisoryOf(raw);
            MachineCapabilityId cap = policy.capabilityOf(raw.systemId());
            List<String> orderedMachines = policy.orderMachinesForCapability(raw.systemId(), raw.machineIds());
            boolean hand = MachineCapabilityGroups.isHandCraftable(raw.systemId(), orderedMachines, parsedInputs.size());
            String explicitId = raw.explicitId().filter(s -> !s.isEmpty()).orElse(null);

            // JEI cycling-output display slots (>= 4 distinct outputs) represent alternatives, not co-production.
            if ("jei".equalsIgnoreCase(raw.sourceKind()) && mergedOuts.size() >= JEI_MULTI_OUTPUT_SPLIT_THRESHOLD) {
                RecipeAdvisory splitAdv = RecipeAdvisory.max(baseAdvisory, RecipeAdvisory.DISCOURAGED);
                for (var outEntry : mergedOuts.entrySet()) {
                    LinkedHashMap<Integer, Long> singleOut = new LinkedHashMap<>(2);
                    singleOut.put(outEntry.getKey(), outEntry.getValue());
                    StageRecord sr = buildStageRecord(
                            raw.systemId(), explicitId, raw.sourceRef(), splitAdv, cap, orderedMachines,
                            hand, raw.recipeValues(), parsedInputs, parsedCats, singleOut
                    );
                    if (sr == null) {
                        excludedNoInputs++;
                    } else {
                        staged.add(sr);
                    }
                }
            } else {
                StageRecord sr = buildStageRecord(
                        raw.systemId(), explicitId, raw.sourceRef(), baseAdvisory, cap, orderedMachines,
                        hand, raw.recipeValues(), parsedInputs, parsedCats, mergedOuts
                );
                if (sr == null) {
                    excludedNoInputs++;
                } else {
                    staged.add(sr);
                }
            }
        }

        // Normalize unresolvedOutputs into canonical dictionary too.
        Set<ResourceKey> normalizedUnresolved = new TreeSet<>();
        for (ResourceKey u : rawCatalog.unresolvedOutputs()) {
            ResourceKey norm = ResourceDictionary.normalizeOrNull(u, null);
            if (norm != null) {
                normalizedUnresolved.add(norm);
            }
        }

        // Freeze ResourceDictionary -> canonical dense int IDs 0..R-1.
        ResourceDictionary.Built builtDict = dictBuilder.freeze();
        ResourceDictionary dict = builtDict.dictionary();
        int[] perm = builtDict.oldToCanonical();

        // Remap staged records to canonical resource IDs, intern candidate lists, and compute FNV-1a keys.
        CandidateTable.Builder candBuilder = new CandidateTable.Builder();
        List<CanonicalRecord> canonicalRecords = new ArrayList<>(staged.size());

        for (StageRecord sr : staged) {
            CanonicalRecord cr = canonicalize(sr, perm, dict, candBuilder);
            if (cr != null) {
                canonicalRecords.add(cr);
            } else {
                excludedInvalid++;
            }
        }

        // Deterministic sort and deduplication (pure function of the input record set).
        canonicalRecords.sort(CanonicalRecord::compareCanonical);

        List<CanonicalRecord> deduped = new ArrayList<>(canonicalRecords.size());
        int deduplicated = 0;
        Map<String, Map<Long, List<CanonicalRecord>>> hashBucketsBySystem = new HashMap<>();

        for (CanonicalRecord cr : canonicalRecords) {
            Map<Long, List<CanonicalRecord>> byHash = hashBucketsBySystem.computeIfAbsent(
                    cr.systemId, ignored -> new HashMap<>());
            List<CanonicalRecord> bucket = byHash.computeIfAbsent(cr.structuralHash64, ignored -> new ArrayList<>(1));
            boolean duplicate = false;
            for (CanonicalRecord existing : bucket) {
                if (cr.sameStructure(existing) && Objects.equals(cr.explicitId, existing.explicitId)) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) {
                deduplicated++;
                continue;
            }
            // Guarantee INV-02 even on theoretical 64-bit FNV collision with different structure.
            long finalHash = cr.structuralHash64;
            if (!bucket.isEmpty()) {
                int salt = 1;
                while (hasKeyCollision(byHash, cr.explicitId, finalHash)) {
                    finalHash = Fnv1a64.mixString(cr.structuralHash64, "|#" + salt++);
                }
                cr = cr.withFinalHash(finalHash);
            }
            byHash.computeIfAbsent(finalHash, ignored -> new ArrayList<>(1)).add(cr);
            deduped.add(cr);
        }

        // Final sort by CompiledRecipeKey for deterministic compactId assignment.
        deduped.sort(Comparator.comparing(cr -> cr.key));

        // Colony mirror exclusion: minecolonies recipe whose primary output also has a non-colony producer.
        boolean[] hasNonColonyProducer = new boolean[dict.size()];
        for (CanonicalRecord cr : deduped) {
            String normSys = DefaultSystemAdmissionPolicy.normalizeSystemId(cr.systemId);
            if (!normSys.startsWith("jei:category/minecolonies/")) {
                for (int outRes : cr.outRes) {
                    hasNonColonyProducer[outRes] = true;
                }
            }
        }
        List<CanonicalRecord> finalRecords = new ArrayList<>(deduped.size());
        for (CanonicalRecord cr : deduped) {
            String normSys = DefaultSystemAdmissionPolicy.normalizeSystemId(cr.systemId);
            if (normSys.startsWith("jei:category/minecolonies/")) {
                if (cr.outRes.length > 0 && hasNonColonyProducer[cr.outRes[0]]) {
                    excludedByPolicy++;
                    continue;
                }
                cr = cr.withAdvisory(RecipeAdvisory.max(cr.advisory, RecipeAdvisory.DISCOURAGED));
            }
            finalRecords.add(cr);
        }

        CandidateTable candTable = candBuilder.freeze(dict);
        int n = finalRecords.size();
        int rCount = dict.size();

        CompiledRecipeKey[] recipeKeys = new CompiledRecipeKey[n];
        byte[] roles = new byte[n];
        byte[] advisories = new byte[n];
        long[] sourceRefs = new long[n];
        boolean[] handCraftable = new boolean[n];
        MachineCapabilityId[] capabilities = new MachineCapabilityId[n];
        @SuppressWarnings("unchecked")
        List<String>[] compatMachines = (List<String>[]) new List<?>[n];
        @SuppressWarnings("unchecked")
        Map<String, Double>[] recipeValues = (Map<String, Double>[]) new Map<?, ?>[n];

        int totalIn = 0, totalCat = 0, totalOut = 0;
        for (CanonicalRecord cr : finalRecords) {
            totalIn += cr.inCandList.length;
            totalCat += cr.catCandList.length;
            totalOut += cr.outRes.length;
        }

        int[] inStart = new int[n + 1];
        int[] inSlotIndex = new int[totalIn];
        int[] inCandList = new int[totalIn];
        long[] inAmount = new long[totalIn];
        byte[] inMatchMode = new byte[totalIn];
        String[] inTagId = new String[totalIn];

        int[] catStart = new int[n + 1];
        int[] catSlotIndex = new int[totalCat];
        int[] catCandList = new int[totalCat];
        int[] catDurCost = new int[totalCat];
        int[] catMaxDur = new int[totalCat];

        int[] outStart = new int[n + 1];
        int[] outRes = new int[totalOut];
        long[] outAmount = new long[totalOut];

        Map<Integer, Map<Integer, Long>> selfAmpSeedsByRecipe = new HashMap<>();

        int inCur = 0, catCur = 0, outCur = 0;
        for (int r = 0; r < n; r++) {
            CanonicalRecord cr = finalRecords.get(r);
            recipeKeys[r] = cr.key;
            roles[r] = (byte) RecipeRole.STANDARD_PRODUCTION.ordinal();
            advisories[r] = (byte) cr.advisory.ordinal();
            sourceRefs[r] = cr.sourceRef;
            handCraftable[r] = cr.handCraftable;
            capabilities[r] = cr.capability;
            compatMachines[r] = cr.orderedMachines;
            recipeValues[r] = cr.recipeValues;

            inStart[r] = inCur;
            for (int i = 0; i < cr.inCandList.length; i++) {
                inSlotIndex[inCur] = i;
                inCandList[inCur] = cr.inCandList[i];
                inAmount[inCur] = cr.inAmount[i];
                inMatchMode[inCur] = cr.inMatchMode[i];
                inTagId[inCur] = cr.inTagId[i];
                inCur++;
            }

            catStart[r] = catCur;
            for (int i = 0; i < cr.catCandList.length; i++) {
                catSlotIndex[catCur] = i;
                catCandList[catCur] = cr.catCandList[i];
                catDurCost[catCur] = cr.catDurCost[i];
                catMaxDur[catCur] = cr.catMaxDur[i];
                catCur++;
            }

            outStart[r] = outCur;
            for (int i = 0; i < cr.outRes.length; i++) {
                outRes[outCur] = cr.outRes[i];
                outAmount[outCur] = cr.outAmount[i];
                outCur++;
            }

            if (cr.selfAmpSeeds != null && !cr.selfAmpSeeds.isEmpty()) {
                selfAmpSeedsByRecipe.put(r, cr.selfAmpSeeds);
            }
        }
        inStart[n] = inCur;
        catStart[n] = catCur;
        outStart[n] = outCur;

        // Build Producer CSR.
        int[] prodCounts = new int[rCount];
        for (int r = 0; r < n; r++) {
            for (int oi = outStart[r]; oi < outStart[r + 1]; oi++) {
                prodCounts[outRes[oi]]++;
            }
        }
        int[] prodStart = new int[rCount + 1];
        for (int u = 0; u < rCount; u++) {
            prodStart[u + 1] = prodStart[u] + prodCounts[u];
        }
        int[] prodRecipe = new int[prodStart[rCount]];
        int[] prodCursor = Arrays.copyOf(prodStart, rCount);
        for (int r = 0; r < n; r++) {
            for (int oi = outStart[r]; oi < outStart[r + 1]; oi++) {
                prodRecipe[prodCursor[outRes[oi]]++] = r;
            }
        }

        // Build Consumer CSR + exactConsumerCount using a stamp array (O(sum |candidates|), zero hashing).
        int[] consCounts = new int[rCount];
        int[] exactConsumerCount = new int[rCount];
        int[] lastSeen = new int[rCount];
        Arrays.fill(lastSeen, -1);
        for (int r = 0; r < n; r++) {
            for (int si = inStart[r]; si < inStart[r + 1]; si++) {
                int[] members = candTable.membersOf(inCandList[si]);
                if (members.length == 1) {
                    exactConsumerCount[members[0]]++;
                }
                for (int u : members) {
                    if (lastSeen[u] != r) {
                        lastSeen[u] = r;
                        consCounts[u]++;
                    }
                }
            }
            for (int ci = catStart[r]; ci < catStart[r + 1]; ci++) {
                int[] members = candTable.membersOf(catCandList[ci]);
                for (int u : members) {
                    if (lastSeen[u] != r) {
                        lastSeen[u] = r;
                        consCounts[u]++;
                    }
                }
            }
        }
        int[] consStart = new int[rCount + 1];
        for (int u = 0; u < rCount; u++) {
            consStart[u + 1] = consStart[u] + consCounts[u];
        }
        int[] consRecipe = new int[consStart[rCount]];
        int[] consCursor = Arrays.copyOf(consStart, rCount);
        Arrays.fill(lastSeen, -1);
        for (int r = 0; r < n; r++) {
            for (int si = inStart[r]; si < inStart[r + 1]; si++) {
                for (int u : candTable.membersOf(inCandList[si])) {
                    if (lastSeen[u] != r) {
                        lastSeen[u] = r;
                        consRecipe[consCursor[u]++] = r;
                    }
                }
            }
            for (int ci = catStart[r]; ci < catStart[r + 1]; ci++) {
                for (int u : candTable.membersOf(catCandList[ci])) {
                    if (lastSeen[u] != r) {
                        lastSeen[u] = r;
                        consRecipe[consCursor[u]++] = r;
                    }
                }
            }
        }

        // Build bipartite (CandidateList -> OutputResource) CSR for fast Tarjan SCC.
        int lCount = candTable.count();
        int[] listOutDeg = new int[lCount];
        for (int r = 0; r < n; r++) {
            int outCnt = outStart[r + 1] - outStart[r];
            for (int si = inStart[r]; si < inStart[r + 1]; si++) {
                listOutDeg[inCandList[si]] += outCnt;
            }
            for (int ci = catStart[r]; ci < catStart[r + 1]; ci++) {
                listOutDeg[catCandList[ci]] += outCnt;
            }
        }
        int[] rawListOutStart = new int[lCount + 1];
        for (int lid = 0; lid < lCount; lid++) {
            rawListOutStart[lid + 1] = rawListOutStart[lid] + listOutDeg[lid];
        }
        int[] rawListOutRes = new int[rawListOutStart[lCount]];
        int[] listOutCur = Arrays.copyOf(rawListOutStart, lCount);
        for (int r = 0; r < n; r++) {
            for (int si = inStart[r]; si < inStart[r + 1]; si++) {
                int lid = inCandList[si];
                for (int oi = outStart[r]; oi < outStart[r + 1]; oi++) {
                    rawListOutRes[listOutCur[lid]++] = outRes[oi];
                }
            }
            for (int ci = catStart[r]; ci < catStart[r + 1]; ci++) {
                int lid = catCandList[ci];
                for (int oi = outStart[r]; oi < outStart[r + 1]; oi++) {
                    rawListOutRes[listOutCur[lid]++] = outRes[oi];
                }
            }
        }
        // Sort and deduplicate each list's outgoing resources in place.
        int[] listOutStart = new int[lCount + 1];
        int writePos = 0;
        for (int lid = 0; lid < lCount; lid++) {
            listOutStart[lid] = writePos;
            int s = rawListOutStart[lid];
            int e = rawListOutStart[lid + 1];
            if (e > s) {
                Arrays.sort(rawListOutRes, s, e);
                int prev = -1;
                for (int i = s; i < e; i++) {
                    int v = rawListOutRes[i];
                    if (v != prev) {
                        rawListOutRes[writePos++] = v;
                        prev = v;
                    }
                }
            }
        }
        listOutStart[lCount] = writePos;

        IterativeTarjanScc sccIndex = IterativeTarjanScc.computeBipartite(dict, candTable, listOutStart, rawListOutRes);

        // Structural role and advisory detection directly on CSR.
        int reversibleCount = detectRolesAndAdvisories(
                dict, candTable, sccIndex, n, recipeKeys, roles, advisories,
                inStart, inCandList, inAmount, inTagId, outStart, outRes, outAmount
        );

        int discouragedCount = 0;
        int lastResortCount = 0;
        for (int r = 0; r < n; r++) {
            if (advisories[r] == RecipeAdvisory.DISCOURAGED.ordinal()) {
                discouragedCount++;
            } else if (advisories[r] == RecipeAdvisory.LAST_RESORT.ordinal()) {
                lastResortCount++;
            }
        }

        GraphCompilationStats initialStats = new GraphCompilationStats(
                totalRaw, n, excludedInvalid, excludedDisplay, deduplicated,
                reversibleCount, 0, sccIndex.largestSccSize(),
                excludedByPolicy, excludedInsane, excludedNoInputs,
                normalizedIds[0], discouragedCount, lastResortCount
        );

        IndexedRecipeGraph graph = new IndexedRecipeGraph(
                rawCatalog.version(), policy, Map.copyOf(admissionCache), dict, candTable, sccIndex,
                initialStats, Collections.unmodifiableSet(normalizedUnresolved),
                recipeKeys, roles, advisories, sourceRefs, handCraftable, capabilities,
                compatMachines, recipeValues,
                inStart, inSlotIndex, inCandList, inAmount, inMatchMode, inTagId,
                catStart, catSlotIndex, catCandList, catDurCost, catMaxDur,
                outStart, outRes, outAmount, prodStart, prodRecipe, consStart, consRecipe, exactConsumerCount
        );

        List<CompiledCycleMacro> macros = CycleMacroMiner.mine(graph, selfAmpSeedsByRecipe);
        graph.attachMacros(macros);
        if (!macros.isEmpty()) {
            GraphCompilationStats finalStats = new GraphCompilationStats(
                    totalRaw, n, excludedInvalid, excludedDisplay, deduplicated,
                    reversibleCount, macros.size(), sccIndex.largestSccSize(),
                    excludedByPolicy, excludedInsane, excludedNoInputs,
                    normalizedIds[0], discouragedCount, lastResortCount
            );
            return graph.withUpdatedStats(finalStats);
        }
        return graph;
    }

    private static StageRecord buildStageRecord(
            String systemId,
            String explicitId,
            long sourceRef,
            RecipeAdvisory advisory,
            MachineCapabilityId cap,
            List<String> orderedMachines,
            boolean handCraftable,
            Map<String, Double> recipeValues,
            List<TempInput> rawInputs,
            List<TempCat> existingCats,
            LinkedHashMap<Integer, Long> mergedOuts
    ) {
        // Coalesce identical single-candidate inputs before catalyst promotion.
        LinkedHashMap<Integer, Long> singleInAmounts = new LinkedHashMap<>();
        List<TempInput> workingInputs = new ArrayList<>(rawInputs.size());
        for (TempInput ti : rawInputs) {
            if (ti.cands.length == 1 && ti.tagId == null) {
                int c = ti.cands[0];
                singleInAmounts.merge(c, ti.amount, SafeMath::addSaturating);
            }
            workingInputs.add(ti);
        }

        List<TempCat> promotedCats = new ArrayList<>(existingCats);
        Map<Integer, Long> selfAmpSeeds = null;
        LinkedHashMap<Integer, Long> outsAfter = new LinkedHashMap<>(mergedOuts);
        Set<Integer> handledSingles = new HashSet<>();
        List<TempInput> finalInputs = new ArrayList<>(workingInputs.size());

        for (TempInput ti : workingInputs) {
            if (ti.cands.length == 1 && ti.tagId == null && outsAfter.containsKey(ti.cands[0])) {
                int resTempId = ti.cands[0];
                if (!handledSingles.add(resTempId)) {
                    continue;
                }
                long inAmt = singleInAmounts.get(resTempId);
                long outAmt = outsAfter.get(resTempId);
                if (inAmt == outAmt) {
                    outsAfter.remove(resTempId);
                    promotedCats.add(new TempCat(new int[]{resTempId}, 0, 0));
                } else if (inAmt < outAmt) {
                    outsAfter.put(resTempId, outAmt - inAmt);
                    promotedCats.add(new TempCat(new int[]{resTempId}, 0, 0));
                    if (selfAmpSeeds == null) {
                        selfAmpSeeds = new HashMap<>(2);
                    }
                    selfAmpSeeds.put(resTempId, inAmt);
                } else {
                    outsAfter.remove(resTempId);
                    finalInputs.add(new TempInput(null, new int[]{resTempId}, inAmt - outAmt, ti.matchMode));
                    promotedCats.add(new TempCat(new int[]{resTempId}, 0, 0));
                }
            } else {
                finalInputs.add(ti);
            }
        }

        if (outsAfter.isEmpty()) {
            return null;
        }
        // Free generation check: reject only when BOTH inputs and catalysts are empty.
        if (finalInputs.isEmpty() && promotedCats.isEmpty()) {
            return null;
        }

        return new StageRecord(
                systemId, explicitId, sourceRef, advisory, cap, orderedMachines,
                handCraftable, recipeValues, finalInputs, promotedCats, outsAfter, selfAmpSeeds
        );
    }

    private static CanonicalRecord canonicalize(
            StageRecord sr,
            int[] perm,
            ResourceDictionary dict,
            CandidateTable.Builder candBuilder
    ) {
        // Remap, sort, dedupe, and coalesce inputs with identical (tagId, candList, matchMode).
        Map<InputCoalesceKey, Long> coalescedInputs = new TreeMap<>();
        for (TempInput ti : sr.inputs) {
            int[] mapped = remapSortDedupe(ti.cands, perm);
            int listId = candBuilder.internSortedUnique(mapped);
            InputCoalesceKey ck = new InputCoalesceKey(ti.tagId, listId, ti.matchMode, mapped);
            coalescedInputs.merge(ck, ti.amount, SafeMath::addSaturating);
        }

        int inCount = coalescedInputs.size();
        int[] inCandList = new int[inCount];
        long[] inAmount = new long[inCount];
        byte[] inMatchMode = new byte[inCount];
        String[] inTagId = new String[inCount];
        int[][] inMembersForHash = new int[inCount][];
        int idx = 0;
        for (var e : coalescedInputs.entrySet()) {
            InputCoalesceKey ck = e.getKey();
            inCandList[idx] = ck.listId;
            inAmount[idx] = e.getValue();
            inMatchMode[idx] = (byte) ck.matchMode.ordinal();
            inTagId[idx] = ck.tagId;
            inMembersForHash[idx] = ck.members;
            idx++;
        }

        // Remap and sort catalysts.
        List<CanonCat> canonCats = new ArrayList<>(sr.cats.size());
        for (TempCat tc : sr.cats) {
            int[] mapped = remapSortDedupe(tc.cands, perm);
            int listId = candBuilder.internSortedUnique(mapped);
            canonCats.add(new CanonCat(listId, mapped, tc.durCost, tc.maxDur));
        }
        canonCats.sort(CanonCat::compareTo);
        int catCount = canonCats.size();
        int[] catCandList = new int[catCount];
        int[] catDurCost = new int[catCount];
        int[] catMaxDur = new int[catCount];
        for (int i = 0; i < catCount; i++) {
            CanonCat cc = canonCats.get(i);
            catCandList[i] = cc.listId;
            catDurCost[i] = cc.durCost;
            catMaxDur[i] = cc.maxDur;
        }

        // Remap and sort outputs by canonical resource id.
        int outCount = sr.outs.size();
        int[] outRes = new int[outCount];
        long[] outAmount = new long[outCount];
        TreeMap<Integer, Long> sortedOuts = new TreeMap<>();
        for (var e : sr.outs.entrySet()) {
            sortedOuts.put(perm[e.getKey()], e.getValue());
        }
        idx = 0;
        for (var e : sortedOuts.entrySet()) {
            outRes[idx] = e.getKey();
            outAmount[idx] = e.getValue();
            idx++;
        }

        Map<Integer, Long> canonSelfSeeds = null;
        if (sr.selfAmpSeeds != null) {
            canonSelfSeeds = new HashMap<>(sr.selfAmpSeeds.size() * 2);
            for (var e : sr.selfAmpSeeds.entrySet()) {
                canonSelfSeeds.put(perm[e.getKey()], e.getValue());
            }
        }

        // Streaming FNV-1a 64-bit structural fingerprint per spec 02 §2.1.3.
        long h = Fnv1a64.initial();
        h = Fnv1a64.mixString(h, "SYS:");
        h = Fnv1a64.mixString(h, sr.systemId);
        h = Fnv1a64.mixString(h, "|IN:");
        for (int i = 0; i < inCount; i++) {
            if (i > 0) h = Fnv1a64.mixByte(h, ';');
            if (inTagId[i] != null) h = Fnv1a64.mixString(h, inTagId[i]);
            h = Fnv1a64.mixByte(h, ':');
            int[] members = inMembersForHash[i];
            for (int j = 0; j < members.length; j++) {
                if (j > 0) h = Fnv1a64.mixByte(h, ',');
                h = Fnv1a64.mixString(h, dict.shortStringOf(members[j]));
            }
            h = Fnv1a64.mixByte(h, '*');
            h = Fnv1a64.mixLong(h, inAmount[i]);
        }
        h = Fnv1a64.mixString(h, "|CAT:");
        for (int i = 0; i < catCount; i++) {
            if (i > 0) h = Fnv1a64.mixByte(h, ';');
            int[] members = canonCats.get(i).members;
            for (int j = 0; j < members.length; j++) {
                if (j > 0) h = Fnv1a64.mixByte(h, ',');
                h = Fnv1a64.mixString(h, dict.shortStringOf(members[j]));
            }
            h = Fnv1a64.mixByte(h, ':');
            h = Fnv1a64.mixLong(h, catDurCost[i]);
        }
        h = Fnv1a64.mixString(h, "|OUT:");
        for (int i = 0; i < outCount; i++) {
            if (i > 0) h = Fnv1a64.mixByte(h, ';');
            h = Fnv1a64.mixString(h, dict.shortStringOf(outRes[i]));
            h = Fnv1a64.mixByte(h, '*');
            h = Fnv1a64.mixLong(h, outAmount[i]);
        }
        h = Fnv1a64.mixString(h, "|MACH:");
        if (sr.capability != null) {
            h = Fnv1a64.mixString(h, sr.capability.id());
        } else {
            for (int i = 0; i < sr.orderedMachines.size(); i++) {
                if (i > 0) h = Fnv1a64.mixByte(h, ',');
                h = Fnv1a64.mixString(h, sr.orderedMachines.get(i));
            }
        }

        CompiledRecipeKey key = new CompiledRecipeKey(sr.systemId, Optional.ofNullable(sr.explicitId), h);
        return new CanonicalRecord(
                sr.systemId, sr.explicitId, h, key, sr.sourceRef, sr.advisory,
                sr.capability, sr.orderedMachines, sr.handCraftable, sr.recipeValues,
                inCandList, inAmount, inMatchMode, inTagId,
                catCandList, catDurCost, catMaxDur,
                outRes, outAmount, canonSelfSeeds
        );
    }

    private static int[] remapSortDedupe(int[] tempIds, int[] perm) {
        int len = tempIds.length;
        int[] out = new int[len];
        for (int i = 0; i < len; i++) {
            out[i] = perm[tempIds[i]];
        }
        if (len <= 1) {
            return out;
        }
        Arrays.sort(out);
        int w = 1;
        for (int i = 1; i < len; i++) {
            if (out[i] != out[w - 1]) {
                out[w++] = out[i];
            }
        }
        return w == len ? out : Arrays.copyOf(out, w);
    }

    private static boolean hasKeyCollision(Map<Long, List<CanonicalRecord>> byHash, String explicitId, long hash) {
        List<CanonicalRecord> bucket = byHash.get(hash);
        if (bucket == null) {
            return false;
        }
        for (CanonicalRecord existing : bucket) {
            if (Objects.equals(existing.explicitId, explicitId)) {
                return true;
            }
        }
        return false;
    }

    private static int detectRolesAndAdvisories(
            ResourceDictionary dict,
            CandidateTable cands,
            IterativeTarjanScc scc,
            int n,
            CompiledRecipeKey[] keys,
            byte[] roles,
            byte[] advisories,
            int[] inStart,
            int[] inCandList,
            long[] inAmount,
            String[] inTagId,
            int[] outStart,
            int[] outRes,
            long[] outAmount
    ) {
        // 1. Index single-input single-output recipes for REVERSIBLE_PACKING & CONTAINER_CONVERSION.
        Map<SinglePairKey, List<Integer>> singlePairs = new HashMap<>();
        for (int r = 0; r < n; r++) {
            if (inStart[r + 1] - inStart[r] == 1 && outStart[r + 1] - outStart[r] == 1) {
                int[] members = cands.membersOf(inCandList[inStart[r]]);
                if (members.length == 1) {
                    int x = members[0];
                    int y = outRes[outStart[r]];
                    if (x != y) {
                        singlePairs.computeIfAbsent(
                                new SinglePairKey(x, inAmount[inStart[r]], y, outAmount[outStart[r]]),
                                ignored -> new ArrayList<>(1)
                        ).add(r);
                    }
                }
            }
        }

        int reversibleCount = 0;
        for (var e : singlePairs.entrySet()) {
            SinglePairKey fwd = e.getKey();
            SinglePairKey rev = new SinglePairKey(fwd.outRes, fwd.outAmt, fwd.inRes, fwd.inAmt);
            if (singlePairs.containsKey(rev)
                    && scc.sccIdOf(fwd.inRes) >= 0
                    && scc.sccIdOf(fwd.inRes) == scc.sccIdOf(fwd.outRes)) {
                for (int r : e.getValue()) {
                    roles[r] = (byte) RecipeRole.REVERSIBLE_PACKING.ordinal();
                    reversibleCount++;
                }
            }
        }

        // 2. Per-recipe structural role & advisory pass.
        Map<SysOutKey, List<Integer>> massSinkGroups = new HashMap<>();
        for (int r = 0; r < n; r++) {
            int inS = inStart[r], inE = inStart[r + 1];
            int outS = outStart[r], outE = outStart[r + 1];
            int inCnt = inE - inS;
            int outCnt = outE - outS;

            if (outCnt >= 1) {
                SysOutKey sok = new SysOutKey(keys[r].systemId(), outRes[outS]);
                massSinkGroups.computeIfAbsent(sok, ignored -> new ArrayList<>(2)).add(r);
            }

            if (roles[r] != RecipeRole.STANDARD_PRODUCTION.ordinal()) {
                continue;
            }

            // CONTAINER_CONVERSION: single-in single-out bucket/bottle pair.
            if (inCnt == 1 && outCnt == 1 && cands.sizeOf(inCandList[inS]) == 1) {
                String inId = dict.keyOf(cands.membersOf(inCandList[inS])[0]).resourceId();
                String outId = dict.keyOf(outRes[outS]).resourceId();
                if (isContainerPair(inId, outId)) {
                    roles[r] = (byte) RecipeRole.CONTAINER_CONVERSION.ordinal();
                    continue;
                }
            }

            // REVERSIBLE_RECOLORING: output appears inside a multi-candidate input slot, or color token swap.
            if (outCnt == 1) {
                int y = outRes[outS];
                boolean selfInMultiSlot = false;
                for (int si = inS; si < inE; si++) {
                    int[] members = cands.membersOf(inCandList[si]);
                    if (members.length >= 2 && Arrays.binarySearch(members, y) >= 0) {
                        selfInMultiSlot = true;
                        break;
                    }
                }
                if (selfInMultiSlot) {
                    roles[r] = (byte) RecipeRole.REVERSIBLE_RECOLORING.ordinal();
                    reversibleCount++;
                    continue;
                }
                if (isLexicalRecolor(dict, cands, inS, inE, inCandList, y)) {
                    roles[r] = (byte) RecipeRole.REVERSIBLE_RECOLORING.ordinal();
                    reversibleCount++;
                    continue;
                }
            }

            // EXCHANGE advisory: any output appears inside any input slot candidates.
            boolean exchange = false;
            for (int oi = outS; oi < outE && !exchange; oi++) {
                int y = outRes[oi];
                for (int si = inS; si < inE; si++) {
                    int[] members = cands.membersOf(inCandList[si]);
                    if (Arrays.binarySearch(members, y) >= 0) {
                        exchange = true;
                        break;
                    }
                }
            }
            if (exchange) {
                advisories[r] = (byte) Math.max(advisories[r], RecipeAdvisory.DISCOURAGED.ordinal());
            }

            // RECYCLING: output is base metal/dust/nugget form and input is a crafted product of that material.
            if (outCnt == 1 && isRecyclingRecipe(dict, cands, keys[r].systemId(), inS, inE, inCandList, inTagId, outRes[outS])) {
                roles[r] = (byte) RecipeRole.RECYCLING.ordinal();
                advisories[r] = (byte) RecipeAdvisory.LAST_RESORT.ordinal();
            }
        }

        // 3. MASS_SINK structural advisory: >= 64 recipes in same system -> same primary output, mostly non-tag inputs.
        for (List<Integer> group : massSinkGroups.values()) {
            if (group.size() < MASS_SINK_THRESHOLD) {
                continue;
            }
            int tagRecipes = 0;
            for (int r : group) {
                boolean hasTag = false;
                for (int si = inStart[r]; si < inStart[r + 1]; si++) {
                    if (inTagId[si] != null) {
                        hasTag = true;
                        break;
                    }
                }
                if (hasTag) {
                    tagRecipes++;
                }
            }
            if (tagRecipes * 2 < group.size()) {
                for (int r : group) {
                    advisories[r] = (byte) RecipeAdvisory.LAST_RESORT.ordinal();
                }
            }
        }

        return reversibleCount;
    }

    private static boolean isContainerPair(String a, String b) {
        return a.equals(b + "_bucket") || b.equals(a + "_bucket")
                || a.equals(b + "_bottle") || b.equals(a + "_bottle");
    }

    private static boolean isLexicalRecolor(
            ResourceDictionary dict, CandidateTable cands, int inS, int inE, int[] inCandList, int outRes
    ) {
        ColorSplit outSplit = splitColor(dict.keyOf(outRes).resourceId());
        if (outSplit == null) {
            return false;
        }
        for (int si = inS; si < inE; si++) {
            int[] members = cands.membersOf(inCandList[si]);
            if (members.length != 1) {
                continue;
            }
            ColorSplit inSplit = splitColor(dict.keyOf(members[0]).resourceId());
            if (inSplit != null && !inSplit.color.equals(outSplit.color) && inSplit.base.equals(outSplit.base)) {
                return true;
            }
        }
        return false;
    }

    private static ColorSplit splitColor(String id) {
        int colon = id.indexOf(':');
        if (colon < 0) return null;
        String ns = id.substring(0, colon);
        String path = id.substring(colon + 1);
        for (String c : COLORS) {
            if (path.startsWith(c + "_")) {
                return new ColorSplit(c, ns + ":" + path.substring(c.length() + 1));
            }
            if (path.endsWith("_" + c)) {
                return new ColorSplit(c, ns + ":" + path.substring(0, path.length() - c.length() - 1));
            }
        }
        return null;
    }

    private static boolean isRecyclingRecipe(
            ResourceDictionary dict,
            CandidateTable cands,
            String systemId,
            int inS,
            int inE,
            int[] inCandList,
            String[] inTagId,
            int outRes
    ) {
        String outPath = pathOf(dict.keyOf(outRes).resourceId());
        String material = extractMaterialPrefix(outPath);
        if (material == null) {
            return false;
        }
        boolean recyclingSystem = systemId.contains("smelting") || systemId.contains("blasting")
                || systemId.contains("crushing") || systemId.contains("milling")
                || systemId.contains("pulveriz") || systemId.contains("salvag")
                || systemId.contains("arc_furnace") || systemId.contains("disassembl")
                || systemId.contains("grind");
        if (!recyclingSystem) {
            return false;
        }
        for (int si = inS; si < inE; si++) {
            String tag = inTagId[si];
            if (tag != null && tag.contains(material) && containsProductToken(tag)) {
                return true;
            }
            int[] members = cands.membersOf(inCandList[si]);
            if (members.length <= 4) {
                for (int u : members) {
                    String inPath = pathOf(dict.keyOf(u).resourceId());
                    if (!inPath.equals(outPath) && inPath.contains(material) && containsProductToken(inPath)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String extractMaterialPrefix(String path) {
        for (String suf : RECYCLING_OUTPUT_SUFFIXES) {
            if (path.endsWith("_" + suf) && path.length() > suf.length() + 2) {
                return path.substring(0, path.length() - suf.length() - 1);
            }
            if (path.startsWith(suf + "_") && path.length() > suf.length() + 2) {
                return path.substring(suf.length() + 1);
            }
        }
        return null;
    }

    private static boolean containsProductToken(String text) {
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '_' || text.charAt(i) == '/' || text.charAt(i) == ':') {
                if (i > start) {
                    String tok = text.substring(start, i);
                    if (RECYCLING_PRODUCT_TOKENS.contains(tok)) {
                        return true;
                    }
                }
                start = i + 1;
            }
        }
        return false;
    }

    private static String pathOf(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    private record ColorSplit(String color, String base) {
    }

    private record SinglePairKey(int inRes, long inAmt, int outRes, long outAmt) {
    }

    private record SysOutKey(String systemId, int outRes) {
    }

    private record TempInput(String tagId, int[] cands, long amount, SlotMatchMode matchMode) {
    }

    private record TempCat(int[] cands, int durCost, int maxDur) {
    }

    private record StageRecord(
            String systemId,
            String explicitId,
            long sourceRef,
            RecipeAdvisory advisory,
            MachineCapabilityId capability,
            List<String> orderedMachines,
            boolean handCraftable,
            Map<String, Double> recipeValues,
            List<TempInput> inputs,
            List<TempCat> cats,
            LinkedHashMap<Integer, Long> outs,
            Map<Integer, Long> selfAmpSeeds
    ) {
    }

    private record InputCoalesceKey(String tagId, int listId, SlotMatchMode matchMode, int[] members)
            implements Comparable<InputCoalesceKey> {

        @Override
        public int compareTo(InputCoalesceKey o) {
            int c = (tagId == null ? "" : tagId).compareTo(o.tagId == null ? "" : o.tagId);
            if (c != 0) return c;
            c = Arrays.compare(members, o.members);
            if (c != 0) return c;
            return Integer.compare(matchMode.ordinal(), o.matchMode.ordinal());
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof InputCoalesceKey o
                    && listId == o.listId
                    && matchMode == o.matchMode
                    && Objects.equals(tagId, o.tagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tagId, listId, matchMode);
        }
    }

    private record CanonCat(int listId, int[] members, int durCost, int maxDur) implements Comparable<CanonCat> {

        @Override
        public int compareTo(CanonCat o) {
            int c = Arrays.compare(members, o.members);
            if (c != 0) return c;
            c = Integer.compare(durCost, o.durCost);
            if (c != 0) return c;
            return Integer.compare(maxDur, o.maxDur);
        }
    }

    private record CanonicalRecord(
            String systemId,
            String explicitId,
            long structuralHash64,
            CompiledRecipeKey key,
            long sourceRef,
            RecipeAdvisory advisory,
            MachineCapabilityId capability,
            List<String> orderedMachines,
            boolean handCraftable,
            Map<String, Double> recipeValues,
            int[] inCandList,
            long[] inAmount,
            byte[] inMatchMode,
            String[] inTagId,
            int[] catCandList,
            int[] catDurCost,
            int[] catMaxDur,
            int[] outRes,
            long[] outAmount,
            Map<Integer, Long> selfAmpSeeds
    ) {

        int compareCanonical(CanonicalRecord o) {
            int c = key.compareTo(o.key);
            if (c != 0) return c;
            c = Arrays.compare(outRes, o.outRes);
            if (c != 0) return c;
            c = Arrays.compare(outAmount, o.outAmount);
            if (c != 0) return c;
            c = Arrays.compare(inCandList, o.inCandList);
            if (c != 0) return c;
            c = Arrays.compare(inAmount, o.inAmount);
            if (c != 0) return c;
            return Long.compare(sourceRef, o.sourceRef);
        }

        boolean sameStructure(CanonicalRecord o) {
            return systemId.equals(o.systemId)
                    && Arrays.equals(inCandList, o.inCandList)
                    && Arrays.equals(inAmount, o.inAmount)
                    && Arrays.equals(inMatchMode, o.inMatchMode)
                    && Arrays.equals(inTagId, o.inTagId)
                    && Arrays.equals(catCandList, o.catCandList)
                    && Arrays.equals(catDurCost, o.catDurCost)
                    && Arrays.equals(catMaxDur, o.catMaxDur)
                    && Arrays.equals(outRes, o.outRes)
                    && Arrays.equals(outAmount, o.outAmount);
        }

        CanonicalRecord withFinalHash(long newHash) {
            return new CanonicalRecord(
                    systemId, explicitId, newHash,
                    new CompiledRecipeKey(systemId, Optional.ofNullable(explicitId), newHash),
                    sourceRef, advisory, capability, orderedMachines, handCraftable, recipeValues,
                    inCandList, inAmount, inMatchMode, inTagId,
                    catCandList, catDurCost, catMaxDur, outRes, outAmount, selfAmpSeeds
            );
        }

        CanonicalRecord withAdvisory(RecipeAdvisory newAdvisory) {
            return new CanonicalRecord(
                    systemId, explicitId, structuralHash64, key, sourceRef, newAdvisory,
                    capability, orderedMachines, handCraftable, recipeValues,
                    inCandList, inAmount, inMatchMode, inTagId,
                    catCandList, catDurCost, catMaxDur, outRes, outAmount, selfAmpSeeds
            );
        }
    }
}
