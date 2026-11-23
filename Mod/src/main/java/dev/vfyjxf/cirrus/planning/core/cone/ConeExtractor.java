package dev.vfyjxf.cirrus.planning.core.cone;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOption;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOracle;
import dev.vfyjxf.cirrus.api.planning.context.AutomatedLine;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.OwnedMachineGroup;
import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.graph.CatalystSlot;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledCycleMacro;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.CoupledComponent;
import dev.vfyjxf.cirrus.api.planning.graph.CoupledKind;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.OutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeAdvisory;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeRole;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.planning.human.CostOverrideRule;
import dev.vfyjxf.cirrus.api.planning.human.CycleBreakRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.ModNamespaceRule;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.human.SystemRule;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultAcquisitionOracle;
import dev.vfyjxf.cirrus.planning.core.cost.StepCosts;
import dev.vfyjxf.cirrus.planning.core.cost.SystemCostTiers;
import dev.vfyjxf.cirrus.planning.core.graph.CandidateTable;
import dev.vfyjxf.cirrus.planning.core.graph.IndexedRecipeGraph;
import dev.vfyjxf.cirrus.planning.core.graph.ResourceDictionary;
import dev.vfyjxf.cirrus.planning.core.lang.IntIntMap;
import dev.vfyjxf.cirrus.planning.core.lang.WorkBudget;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ConeExtractor {

    private static final int HIGH_FANOUT_THRESHOLD = 8;
    private static final int[] RELAXATION_CAPS = {6, 16, 64, Integer.MAX_VALUE};
    private static final int MAX_ADVISORY_PRODUCERS = 4;

    private final CompiledRecipeGraph graph;

    public ConeExtractor(CompiledRecipeGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
    }

    public ConeModel extract(
            PlanningRequest request,
            Set<Integer> maskedRecipeCompactIds,
            WorkBudget workBudget
    ) {
        return extractWithExtraRoots(request, Set.of(), maskedRecipeCompactIds, workBudget);
    }

    public ConeModel extractWithExtraRoots(
            PlanningRequest request,
            Set<ResourceKey> extraRoots,
            Set<Integer> maskedRecipeCompactIds,
            WorkBudget workBudget
    ) {
        Set<Integer> masked = maskedRecipeCompactIds == null ? Set.of() : maskedRecipeCompactIds;
        ConeModel last = null;
        for (int attempt = 0; attempt < RELAXATION_CAPS.length; attempt++) {
            int cap = RELAXATION_CAPS[attempt];
            last = extractSinglePass(request, extraRoots, masked, cap, workBudget);
            if (allTargetsHaveFiniteTier(last, request) || !last.truncatedByBudget
                    || (workBudget != null && workBudget.isHardTimeExceeded())) {
                return last;
            }
        }
        return last;
    }

    private static boolean allTargetsHaveFiniteTier(ConeModel cone, PlanningRequest request) {
        for (TargetDemand td : request.targets()) {
            int u = cone.localRes(td.resource());
            if (u < 0 || cone.tier[u] >= ConeModel.INF_TIER) {
                return false;
            }
        }
        return true;
    }

    private ConeModel extractSinglePass(
            PlanningRequest request,
            Set<ResourceKey> extraRoots,
            Set<Integer> masked,
            int highFanoutCap,
            WorkBudget workBudget
    ) {
        PlanningContext ctx = request.context();
        InventorySnapshot inv = ctx.inventory();
        FacilitySnapshot fac = ctx.facilities();
        HumanGuidanceView guidance = ctx.guidance();
        PlanningBudget budget = request.budget();

        // Pre-index owned machines, capabilities, automated lines, and usable inventory.
        Set<String> ownedMachines = new HashSet<>();
        Set<MachineCapabilityId> ownedCaps = new HashSet<>();
        for (OwnedMachineGroup g : fac.ownedMachines()) {
            if (g.count() > 0) {
                ownedMachines.add(g.machineId());
                ownedCaps.add(g.capability());
            }
        }
        Map<ResourceKey, List<AutomatedLine>> linesByRes = new HashMap<>();
        for (AutomatedLine line : fac.automatedLines()) {
            linesByRes.computeIfAbsent(line.output(), ignored -> new ArrayList<>(1)).add(line);
        }
        Map<ResourceKey, Long> usableMap = inv.allUsable();

        // Precompute one-hop stocked upstream flags for IndexedRecipeGraph when inventory is non-empty.
        IndexedRecipeGraph ig = (graph instanceof IndexedRecipeGraph indexed) ? indexed : null;
        boolean[] oneHopFromStock = null;
        if (ig != null && !usableMap.isEmpty()) {
            oneHopFromStock = computeOneHopStocked(ig, usableMap);
        }

        // Index rule IDs touched by guidance for conflict diagnosis and OR quotient singletons.
        Set<ResourceKey> guidanceTouchedRes = new HashSet<>();
        for (GuidanceRule r : guidance.allRules()) {
            if (r instanceof ResourceRule rr) guidanceTouchedRes.add(rr.resource());
            else if (r instanceof CostOverrideRule cor) guidanceTouchedRes.add(cor.resource());
        }

        int maxNodes = budget.maxSubgraphNodes();
        int maxRecipes = budget.maxSubgraphRecipes();
        int maxProdPerRes = budget.maxProducersPerResource();

        // 1. Wave-based (depth-first-per-rank) extraction.
        LinkedHashSet<ResourceKey> visitedNodes = new LinkedHashSet<>();
        Map<Integer, CompiledRecipe> extractedRecipes = new LinkedHashMap<>();
        Map<ResourceKey, List<CompiledRecipe>> rankedProducersCache = new HashMap<>();
        Map<ResourceKey, Set<String>> blockingRules = new HashMap<>();
        Set<ResourceKey> dormantResources = new HashSet<>();
        Map<Integer, List<ResourceKey>> highFanoutCache = new HashMap<>();
        Set<ResourceKey> targetSet = new HashSet<>();
        boolean truncated = false;

        for (TargetDemand td : request.targets()) {
            visitedNodes.add(td.resource());
            targetSet.add(td.resource());
        }
        for (ResourceKey extra : extraRoots) {
            visitedNodes.add(extra);
        }

        // Also seed working seeds & reagents of active cycle macros producing any root target.
        List<CompiledCycleMacro> candidateMacros = new ArrayList<>();
        Set<String> seenMacroIds = new HashSet<>();
        for (ResourceKey root : List.copyOf(visitedNodes)) {
            for (CompiledCycleMacro m : graph.cycleMacrosProducing(root)) {
                if (seenMacroIds.add(m.macroKey().macroId()) && isMacroAllowedWithGraph(m, fac, guidance, masked)) {
                    candidateMacros.add(m);
                    for (ResourceKey s : m.workingSeeds().keySet()) {
                        visitedNodes.add(s);
                    }
                    for (var e : m.netDeltaPerIter().entrySet()) {
                        if (e.getValue() < 0L) {
                            visitedNodes.add(e.getKey());
                        }
                    }
                    for (CompiledRecipe inner : m.innerRecipes()) {
                        extractedRecipes.putIfAbsent(inner.compactId(), inner);
                    }
                }
            }
        }

        // Wave k expands the k-th ranked producer for every node in the cone (depth-first backbone first!).
        int workTick = 0;
        for (int wave = 0; wave < maxProdPerRes; wave++) {
            if (visitedNodes.size() >= maxNodes || extractedRecipes.size() >= maxRecipes) {
                truncated = true;
                break;
            }
            if (workBudget != null && workBudget.isHardTimeExceeded()) {
                truncated = true;
                break;
            }
            ArrayDeque<ResourceKey> waveQueue = new ArrayDeque<>(visitedNodes);
            Set<ResourceKey> processedInWave = new HashSet<>(visitedNodes.size() * 2);

            while (!waveQueue.isEmpty()) {
                if ((++workTick & 63) == 0 && workBudget != null && workBudget.isHardTimeExceeded()) {
                    truncated = true;
                    break;
                }
                if (visitedNodes.size() >= maxNodes || extractedRecipes.size() >= maxRecipes) {
                    truncated = true;
                    break;
                }
                ResourceKey v = waveQueue.poll();
                if (!processedInWave.add(v)) {
                    continue;
                }
                if (guidance.isResourceBanned(v)) {
                    recordResourceBan(v, guidance, blockingRules);
                    continue;
                }
                List<CompiledRecipe> ranked = rankedProducersCache.computeIfAbsent(v, key ->
                        rankProducers(key, targetSet.contains(key), ig, fac, guidance, ownedMachines, ownedCaps,
                                masked, maxProdPerRes, dormantResources, blockingRules));

                if (guidance.isResourceTreatedAsBase(v)) {
                    boolean hasPinned = !ranked.isEmpty() && guidance.isRecipePinned(ranked.get(0).key());
                    if (!hasPinned) {
                        continue;
                    }
                }

                // In wave 0, expand rank 0; in wave k, expand any newly discovered node up to rank k.
                int limit = Math.min(wave + 1, ranked.size());
                int startRank = (wave == 0) ? 0 : Math.min(wave, ranked.size());
                for (int ri = startRank; ri < limit; ri++) {
                    CompiledRecipe r = ranked.get(ri);
                    if (extractedRecipes.size() >= maxRecipes && !extractedRecipes.containsKey(r.compactId())) {
                        truncated = true;
                        break;
                    }
                    boolean newlyAdded = extractedRecipes.putIfAbsent(r.compactId(), r) == null;
                    if (!newlyAdded) {
                        continue;
                    }
                    // Include any active cycle macros for newly added nodes if needed.
                    if (ig != null) {
                        enqueueSlotCandidatesFast(
                                ig, r.compactId(), highFanoutCap, usableMap, linesByRes, guidance,
                                oneHopFromStock, highFanoutCache, visitedNodes, waveQueue, maxNodes
                        );
                        if (visitedNodes.size() >= maxNodes) {
                            truncated = true;
                        }
                    } else {
                        for (InputSlot slot : r.inputs()) {
                            if (enqueueSlotGeneric(slot, highFanoutCap, usableMap, linesByRes, guidance,
                                    visitedNodes, waveQueue, maxNodes)) {
                                truncated = true;
                            }
                        }
                        for (CatalystSlot cat : r.catalysts()) {
                            for (ResourceKey c : cat.candidates()) {
                                if (visitedNodes.add(c)) {
                                    waveQueue.add(c);
                                }
                            }
                        }
                    }
                }
            }
            if (truncated) {
                break;
            }
        }

        // Also scan cycle macros for all cone nodes (not just top targets).
        for (ResourceKey node : List.copyOf(visitedNodes)) {
            for (CompiledCycleMacro m : graph.cycleMacrosProducing(node)) {
                if (seenMacroIds.add(m.macroKey().macroId()) && isMacroAllowedWithGraph(m, fac, guidance, masked)) {
                    boolean allInside = true;
                    for (ResourceKey s : m.workingSeeds().keySet()) {
                        if (!visitedNodes.contains(s)) {
                            visitedNodes.add(s);
                        }
                    }
                    for (var e : m.netDeltaPerIter().entrySet()) {
                        if (e.getValue() < 0L && !visitedNodes.contains(e.getKey())) {
                            visitedNodes.add(e.getKey());
                        }
                    }
                    for (CompiledRecipe inner : m.innerRecipes()) {
                        extractedRecipes.putIfAbsent(inner.compactId(), inner);
                    }
                    if (allInside) {
                        candidateMacros.add(m);
                    }
                }
            }
        }

        // Pull unowned candidate machine items into the same cone up to maxMachineRecursionDepth.
        int maxMachDepth = Math.min(3, request.profile().maxMachineRecursionDepth());
        Set<String> expandedMachineIds = new HashSet<>();
        for (int depth = 0; depth < maxMachDepth && visitedNodes.size() < maxNodes
                && extractedRecipes.size() < maxRecipes; depth++) {
            List<ResourceKey> newMachineItems = new ArrayList<>();
            for (CompiledRecipe rec : List.copyOf(extractedRecipes.values())) {
                if (rec.machineRequirement().handCraftable()
                        || isMachineOwned(rec, ownedMachines, ownedCaps)) {
                    continue;
                }
                int picked = 0;
                for (String mid : rec.machineRequirement().compatibleMachineIds()) {
                    if (picked >= 3) break;
                    if (!guidance.isWillingToBuild(mid)) continue;
                    picked++;
                    if (expandedMachineIds.add(mid)) {
                        ResourceKey mk = ResourceKey.item(mid);
                        if (visitedNodes.add(mk)) {
                            newMachineItems.add(mk);
                        }
                    }
                }
            }
            if (newMachineItems.isEmpty()) {
                break;
            }
            ArrayDeque<ResourceKey> machQueue = new ArrayDeque<>(newMachineItems);
            Set<ResourceKey> seenMachWave = new HashSet<>();
            int machRecipesBudget = Math.min(maxRecipes - extractedRecipes.size(), 96);
            int machAdded = 0;
            while (!machQueue.isEmpty() && machAdded < machRecipesBudget && visitedNodes.size() < maxNodes) {
                ResourceKey mv = machQueue.poll();
                if (!seenMachWave.add(mv) || guidance.isResourceBanned(mv)) continue;
                List<CompiledRecipe> mProds = rankedProducersCache.computeIfAbsent(mv, key ->
                        rankProducers(key, false, ig, fac, guidance, ownedMachines, ownedCaps,
                                masked, Math.min(4, maxProdPerRes), dormantResources, blockingRules));
                if (guidance.isResourceTreatedAsBase(mv)
                        && (mProds.isEmpty() || !guidance.isRecipePinned(mProds.get(0).key()))) {
                    continue;
                }
                int take = Math.min(2, mProds.size());
                for (int i = 0; i < take && machAdded < machRecipesBudget; i++) {
                    CompiledRecipe mr = mProds.get(i);
                    if (extractedRecipes.putIfAbsent(mr.compactId(), mr) == null) {
                        machAdded++;
                        if (ig != null) {
                            enqueueSlotCandidatesFast(ig, mr.compactId(), Math.min(4, highFanoutCap),
                                    usableMap, linesByRes, guidance, oneHopFromStock, highFanoutCache,
                                    visitedNodes, machQueue, maxNodes);
                        } else {
                            for (InputSlot slot : mr.inputs()) {
                                enqueueSlotGeneric(slot, Math.min(4, highFanoutCap), usableMap, linesByRes,
                                        guidance, visitedNodes, machQueue, maxNodes);
                            }
                            for (CatalystSlot cat : mr.catalysts()) {
                                for (ResourceKey c : cat.candidates()) {
                                    if (visitedNodes.size() < maxNodes && visitedNodes.add(c)) {
                                        machQueue.add(c);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Ensure all outputs of extracted recipes are present in visitedNodes so CSR indices are closed.
        for (CompiledRecipe r : extractedRecipes.values()) {
            for (OutputSlot out : r.outputs()) {
                visitedNodes.add(out.resource());
            }
        }

        // 2. Build dense canonical local IDs for resources and recipes.
        ResourceKey[] resKeys = visitedNodes.toArray(new ResourceKey[0]);
        Arrays.sort(resKeys);
        int nodeCount = resKeys.length;
        Map<ResourceKey, Integer> localByKey = new HashMap<>((int) (nodeCount / 0.7) + 16);
        int[] resGlobalId = new int[nodeCount];
        IntIntMap localByGlobalRes = new IntIntMap(nodeCount);
        ResourceDictionary dict = ig != null ? ig.resourceDict() : null;
        for (int u = 0; u < nodeCount; u++) {
            localByKey.put(resKeys[u], u);
            if (dict != null) {
                int gid = dict.idOf(resKeys[u]);
                resGlobalId[u] = gid;
                if (gid >= 0) {
                    localByGlobalRes.put(gid, u);
                }
            } else {
                resGlobalId[u] = -1;
            }
        }

        CompiledRecipe[] recObjects = extractedRecipes.values().toArray(new CompiledRecipe[0]);
        Arrays.sort(recObjects, Comparator.comparingInt(CompiledRecipe::compactId));
        int recipeCount = recObjects.length;
        int[] recCompactId = new int[recipeCount];
        IntIntMap localByCompactId = new IntIntMap(recipeCount);
        RecipeRole[] recRole = new RecipeRole[recipeCount];
        RecipeAdvisory[] recAdvisory = new RecipeAdvisory[recipeCount];
        boolean[] recPinned = new boolean[recipeCount];
        boolean[] recHandCraftable = new boolean[recipeCount];
        MachineCapabilityId[] recCapability = new MachineCapabilityId[recipeCount];
        @SuppressWarnings("unchecked")
        List<String>[] recCompatMachines = (List<String>[]) new List<?>[recipeCount];

        for (int r = 0; r < recipeCount; r++) {
            CompiledRecipe rec = recObjects[r];
            recCompactId[r] = rec.compactId();
            localByCompactId.put(rec.compactId(), r);
            recRole[r] = rec.role();
            recAdvisory[r] = rec.advisory();
            recPinned[r] = guidance.isRecipePinned(rec.key());
            recHandCraftable[r] = rec.machineRequirement().handCraftable();
            recCapability[r] = rec.machineRequirement().capabilityId().orElse(null);
            recCompatMachines[r] = rec.machineRequirement().compatibleMachineIds();
        }

        // 3. Populate per-resource context arrays (stock, lines, base, dormant, oracle).
        AcquisitionOracle effectiveOracle = ctx.acquisitionOracle();
        if (effectiveOracle instanceof DefaultAcquisitionOracle dao) {
            effectiveOracle = dao.withGuidance(guidance).withDormantResources(dormantResources);
        }

        long[] usableStock = new long[nodeCount];
        boolean[] freeBase = new boolean[nodeCount];
        boolean[] dormantArr = new boolean[nodeCount];
        boolean[] bannedRes = new boolean[nodeCount];
        AcquisitionOption[] acqOpts = new AcquisitionOption[nodeCount];
        AutomatedLine[] bestLines = new AutomatedLine[nodeCount];
        @SuppressWarnings("unchecked")
        List<AutomatedLine>[] allLinesArr = (List<AutomatedLine>[]) new List<?>[nodeCount];

        for (int u = 0; u < nodeCount; u++) {
            ResourceKey rk = resKeys[u];
            bannedRes[u] = guidance.isResourceBanned(rk);
            usableStock[u] = usableMap.getOrDefault(rk, 0L);
            freeBase[u] = guidance.isResourceTreatedAsBase(rk);
            dormantArr[u] = dormantResources.contains(rk);

            List<AutomatedLine> lines = linesByRes.get(rk);
            if (lines != null && !lines.isEmpty()) {
                allLinesArr[u] = List.copyOf(lines);
                AutomatedLine best = lines.get(0);
                for (int i = 1; i < lines.size(); i++) {
                    AutomatedLine c = lines.get(i);
                    if (c.humanAttentionSecondsPerPull() < best.humanAttentionSecondsPerPull()
                            || (c.humanAttentionSecondsPerPull() == best.humanAttentionSecondsPerPull()
                            && c.throughputPerMinute() > best.throughputPerMinute())) {
                        best = c;
                    }
                }
                bestLines[u] = best;
            } else {
                allLinesArr[u] = List.of();
            }

            if (!bannedRes[u]) {
                Optional<CostVector> override = guidance.overriddenUnitCost(rk);
                if (override.isPresent()) {
                    acqOpts[u] = DefaultAcquisitionOracle.fromUnitCost(override.get(), DefaultAcquisitionOracle.KEY_OVERRIDE);
                } else if (freeBase[u]) {
                    acqOpts[u] = new AcquisitionOption(
                            dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind.PLAYER_DECLARED_FREE,
                            0.0, 0.0, 0.0, 0.0, DefaultAcquisitionOracle.KEY_FREE);
                } else {
                    acqOpts[u] = effectiveOracle.assess(rk, 1L, ctx.progression()).orElse(null);
                }
            }
        }

        // 4. Incremental O(|E_cone|) Acquisition Stratification (A4/A5).
        int[] tier = new int[nodeCount];
        Arrays.fill(tier, ConeModel.INF_TIER);
        int[] bfsQueue = new int[Math.max(16, nodeCount * 2)];
        int qHead = 0, qTail = 0;
        boolean[] inQueue = new boolean[nodeCount];

        for (int u = 0; u < nodeCount; u++) {
            if (!bannedRes[u] && (usableStock[u] > 0L || bestLines[u] != null || freeBase[u] || acqOpts[u] != null)) {
                tier[u] = 0;
                bfsQueue[qTail++] = u;
                inQueue[u] = true;
            }
        }

        // Build compact local slot/catalyst/output structures for stratification.
        int totalInSlots = 0, totalCatSlots = 0, totalOuts = 0;
        for (int r = 0; r < recipeCount; r++) {
            totalInSlots += recObjects[r].inputs().size();
            totalCatSlots += recObjects[r].catalysts().size();
            totalOuts += recObjects[r].outputs().size();
        }
        int totalStratSlots = totalInSlots + totalCatSlots;
        int[] stratSlotStart = new int[recipeCount + 1];
        int[] slotMinTier = new int[totalStratSlots];
        Arrays.fill(slotMinTier, ConeModel.INF_TIER);
        int[] unmetSlots = new int[recipeCount];
        int[] recBestTier = new int[recipeCount];
        Arrays.fill(recBestTier, ConeModel.INF_TIER);

        // Local candidate lists per strat-slot (filtered to in-cone & non-banned & HARD_PIN tag rule).
        int[][] stratSlotCands = new int[totalStratSlots][];
        int[] outStart = new int[recipeCount + 1];
        int[] outRes = new int[totalOuts];
        long[] outAmount = new long[totalOuts];

        // Count consumer occurrences to build local (u -> stratSlot) CSR.
        int[] resToSlotCount = new int[nodeCount];
        int slotCur = 0;
        int outCur = 0;

        for (int r = 0; r < recipeCount; r++) {
            CompiledRecipe rec = recObjects[r];
            stratSlotStart[r] = slotCur;
            int unmet = 0;
            for (InputSlot slot : rec.inputs()) {
                int[] cands = resolveLocalCandidates(slot.tagId().orElse(null), slot.candidates(),
                        localByKey, bannedRes, guidance);
                stratSlotCands[slotCur] = cands;
                for (int u : cands) {
                    resToSlotCount[u]++;
                }
                unmet++;
                slotCur++;
            }
            for (CatalystSlot cat : rec.catalysts()) {
                int[] cands = resolveLocalCandidates(null, cat.candidates(), localByKey, bannedRes, guidance);
                stratSlotCands[slotCur] = cands;
                for (int u : cands) {
                    resToSlotCount[u]++;
                }
                unmet++;
                slotCur++;
            }
            unmetSlots[r] = unmet;

            outStart[r] = outCur;
            for (OutputSlot o : rec.outputs()) {
                outRes[outCur] = localByKey.get(o.resource());
                outAmount[outCur] = o.amountPerBatch();
                outCur++;
            }
        }
        stratSlotStart[recipeCount] = slotCur;
        outStart[recipeCount] = outCur;

        int[] resToSlotStart = new int[nodeCount + 1];
        for (int u = 0; u < nodeCount; u++) {
            resToSlotStart[u + 1] = resToSlotStart[u] + resToSlotCount[u];
        }
        int[] resToSlotId = new int[resToSlotStart[nodeCount]];
        int[] resToSlotRec = new int[resToSlotStart[nodeCount]];
        int[] resToSlotCursor = Arrays.copyOf(resToSlotStart, nodeCount);

        for (int r = 0; r < recipeCount; r++) {
            for (int ss = stratSlotStart[r]; ss < stratSlotStart[r + 1]; ss++) {
                for (int u : stratSlotCands[ss]) {
                    int pos = resToSlotCursor[u]++;
                    resToSlotId[pos] = ss;
                    resToSlotRec[pos] = r;
                }
            }
        }

        // Propagate tiers through recipes and active cycle macros.
        while (qHead != qTail) {
            int u = bfsQueue[qHead++];
            if (qHead == bfsQueue.length) qHead = 0;
            inQueue[u] = false;
            int tu = tier[u];

            for (int idx = resToSlotStart[u]; idx < resToSlotStart[u + 1]; idx++) {
                int ss = resToSlotId[idx];
                if (tu >= slotMinTier[ss]) {
                    continue;
                }
                int r = resToSlotRec[idx];
                if (slotMinTier[ss] == ConeModel.INF_TIER) {
                    unmetSlots[r]--;
                }
                slotMinTier[ss] = tu;
                if (unmetSlots[r] == 0) {
                    int maxSlot = 0;
                    for (int k = stratSlotStart[r]; k < stratSlotStart[r + 1]; k++) {
                        if (slotMinTier[k] > maxSlot) {
                            maxSlot = slotMinTier[k];
                        }
                    }
                    int rTier = maxSlot + 1;
                    if (rTier < recBestTier[r]) {
                        recBestTier[r] = rTier;
                        for (int oi = outStart[r]; oi < outStart[r + 1]; oi++) {
                            int v = outRes[oi];
                            if (!bannedRes[v] && rTier < tier[v]) {
                                tier[v] = rTier;
                                if (!inQueue[v]) {
                                    bfsQueue[qTail++] = v;
                                    if (qTail == bfsQueue.length) qTail = 0;
                                    inQueue[v] = true;
                                }
                            }
                        }
                    }
                }
            }

            // Also propagate cycle macros whenever all seeds + negative reagents have finite tier.
            for (CompiledCycleMacro m : candidateMacros) {
                int maxInTier = 0;
                boolean ok = true;
                for (ResourceKey s : m.workingSeeds().keySet()) {
                    Integer ls = localByKey.get(s);
                    if (ls == null || tier[ls] >= ConeModel.INF_TIER) {
                        ok = false;
                        break;
                    }
                    if (tier[ls] > maxInTier) maxInTier = tier[ls];
                }
                if (!ok) continue;
                for (var e : m.netDeltaPerIter().entrySet()) {
                    if (e.getValue() < 0L) {
                        Integer lr = localByKey.get(e.getKey());
                        if (lr == null || tier[lr] >= ConeModel.INF_TIER) {
                            ok = false;
                            break;
                        }
                        if (tier[lr] > maxInTier) maxInTier = tier[lr];
                    }
                }
                if (!ok) continue;
                int mTier = maxInTier + 1;
                for (var e : m.netDeltaPerIter().entrySet()) {
                    if (e.getValue() > 0L) {
                        Integer lo = localByKey.get(e.getKey());
                        if (lo != null && !bannedRes[lo] && mTier < tier[lo]) {
                            tier[lo] = mTier;
                            if (!inQueue[lo]) {
                                bfsQueue[qTail++] = lo;
                                if (qTail == bfsQueue.length) qTail = 0;
                                inQueue[lo] = true;
                            }
                        }
                    }
                }
            }
        }

        // 5. Binding-level monotonicity & active recipe classification.
        byte[] recActive = new byte[recipeCount];
        int[] recPsi = new int[recipeCount];
        Arrays.fill(recPsi, ConeModel.INF_TIER);
        boolean[] hasActiveProducer = new boolean[nodeCount];
        boolean[] isCoproduced = new boolean[nodeCount];

        for (int r = 0; r < recipeCount; r++) {
            if (unmetSlots[r] > 0) {
                continue;
            }
            int psi = ConeModel.INF_TIER;
            boolean anyOutInScc = false;
            for (int oi = outStart[r]; oi < outStart[r + 1]; oi++) {
                int v = outRes[oi];
                if (tier[v] < psi) psi = tier[v];
                if (graph.sccTopology().isInNonTrivialScc(resKeys[v])) {
                    anyOutInScc = true;
                }
            }
            recPsi[r] = psi;
            if (psi >= ConeModel.INF_TIER) {
                continue;
            }

            boolean pinned = recPinned[r];
            boolean reversible = recObjects[r].isReversibleConversion();
            int inCount = recObjects[r].inputs().size();
            int baseSS = stratSlotStart[r];

            boolean allSlotsHaveMonotone = true;
            if (anyOutInScc && !pinned) {
                for (int i = 0; i < inCount; i++) {
                    if (slotMinTier[baseSS + i] >= psi) {
                        allSlotsHaveMonotone = false;
                        break;
                    }
                }
            }

            if (reversible) {
                boolean inputHasStockOrLine = false;
                if (inCount >= 1) {
                    for (int u : stratSlotCands[baseSS]) {
                        if (usableStock[u] > 0L || bestLines[u] != null) {
                            inputHasStockOrLine = true;
                            break;
                        }
                    }
                }
                byte mode = ConeModel.ACTIVE_NONE;
                if (inputHasStockOrLine) {
                    mode |= ConeModel.ACTIVE_CONVERT;
                }
                if (allSlotsHaveMonotone || pinned) {
                    mode |= ConeModel.ACTIVE_STANDARD;
                }
                recActive[r] = mode;
            } else {
                if (allSlotsHaveMonotone || pinned) {
                    recActive[r] = ConeModel.ACTIVE_STANDARD;
                }
            }

            if (recActive[r] != ConeModel.ACTIVE_NONE) {
                int oCnt = outStart[r + 1] - outStart[r];
                for (int oi = outStart[r]; oi < outStart[r + 1]; oi++) {
                    int v = outRes[oi];
                    hasActiveProducer[v] = true;
                    if (oCnt > 1) {
                        isCoproduced[v] = true;
                    }
                }
            }
        }

        // 6. Memoized OR-slot quotient (A3) + binding-level monotone filtering.
        String[] leafSigMemo = new String[nodeCount];
        boolean[] isSingletonRes = new boolean[nodeCount];
        for (int u = 0; u < nodeCount; u++) {
            if (usableStock[u] > 0L || bestLines[u] != null || hasActiveProducer[u]
                    || isCoproduced[u] || guidanceTouchedRes.contains(resKeys[u]) || acqOpts[u] == null) {
                isSingletonRes[u] = true;
            } else {
                CostVector unitCost = StepCosts.acquireCost(acqOpts[u], 1L, freeBase[u]);
                leafSigMemo[u] = unitCost.quantizedSignature();
            }
        }

        int[] inStart = new int[recipeCount + 1];
        int[] inSlotIndex = new int[totalInSlots];
        long[] inAmount = new long[totalInSlots];
        String[] inTagId = new String[totalInSlots];
        SlotMatchMode[] inMatchMode = new SlotMatchMode[totalInSlots];
        int[] inOrigCandCount = new int[totalInSlots];
        int[] inClassStart = new int[totalInSlots + 1];

        IntList classRepList = new IntList(totalInSlots * 2);
        IntList classMemStartList = new IntList(totalInSlots * 2);
        IntList classMemResList = new IntList(totalInSlots * 4);
        int collapsedOr = 0;

        int inCur = 0;
        for (int r = 0; r < recipeCount; r++) {
            CompiledRecipe rec = recObjects[r];
            inStart[r] = inCur;
            boolean enforceMonotone = !recPinned[r] && isStandardActiveOrAnyScc(rec, r, recActive, graph);
            int psi = recPsi[r];
            int baseSS = stratSlotStart[r];

            for (int i = 0; i < rec.inputs().size(); i++) {
                InputSlot slot = rec.inputs().get(i);
                inSlotIndex[inCur] = slot.slotIndex();
                inAmount[inCur] = slot.amountPerBatch();
                inTagId[inCur] = slot.tagId().orElse(null);
                inMatchMode[inCur] = slot.matchMode();
                inOrigCandCount[inCur] = slot.candidates().size();
                inClassStart[inCur] = classRepList.size();

                int[] rawLocal = stratSlotCands[baseSS + i];
                if (recActive[r] != ConeModel.ACTIVE_NONE && rawLocal.length > 0) {
                    // Filter to reachable & (when in SCC and not convert-only) monotone candidates.
                    boolean requireMono = enforceMonotone && recActive[r] == ConeModel.ACTIVE_STANDARD;
                    int[] filtered = filterReachable(rawLocal, tier, requireMono ? psi : ConeModel.INF_TIER);
                    if (filtered.length == 0 && recActive[r] != ConeModel.ACTIVE_NONE) {
                        filtered = filterReachable(rawLocal, tier, ConeModel.INF_TIER);
                    }
                    String tagId = inTagId[inCur];
                    ResourceKey prefTag = tagId != null ? guidance.preferredTagMember(tagId).orElse(null) : null;
                    collapsedOr += buildQuotientClasses(
                            filtered, prefTag, isSingletonRes, leafSigMemo, ig, resGlobalId,
                            classRepList, classMemStartList, classMemResList
                    );
                }
                inCur++;
            }
        }
        inStart[recipeCount] = inCur;
        inClassStart[inCur] = classRepList.size();
        classMemStartList.add(classMemResList.size());

        // Pack catalysts CSR.
        int[] catStart = new int[recipeCount + 1];
        int[] catSlotIndex = new int[totalCatSlots];
        int[] catDurCost = new int[totalCatSlots];
        int[] catMaxDur = new int[totalCatSlots];
        int[] catCandStart = new int[totalCatSlots + 1];
        IntList catCandResList = new IntList(totalCatSlots * 2);
        int catCur = 0;
        for (int r = 0; r < recipeCount; r++) {
            CompiledRecipe rec = recObjects[r];
            catStart[r] = catCur;
            int baseCatSS = stratSlotStart[r] + rec.inputs().size();
            for (int i = 0; i < rec.catalysts().size(); i++) {
                CatalystSlot cat = rec.catalysts().get(i);
                catSlotIndex[catCur] = cat.slotIndex();
                catDurCost[catCur] = cat.durabilityCostPerBatch();
                catMaxDur[catCur] = cat.maxDurability();
                catCandStart[catCur] = catCandResList.size();
                for (int u : stratSlotCands[baseCatSS + i]) {
                    if (tier[u] < ConeModel.INF_TIER) {
                        catCandResList.add(u);
                    }
                }
                catCur++;
            }
        }
        catStart[recipeCount] = catCur;
        catCandStart[catCur] = catCandResList.size();

        // 7. Build local Producer CSR (ordered by rank per output resource).
        List<List<Integer>> localProds = new ArrayList<>(nodeCount);
        for (int u = 0; u < nodeCount; u++) {
            localProds.add(new ArrayList<>(2));
        }
        for (int r = 0; r < recipeCount; r++) {
            if (recActive[r] == ConeModel.ACTIVE_NONE) continue;
            for (int oi = outStart[r]; oi < outStart[r + 1]; oi++) {
                localProds.get(outRes[oi]).add(r);
            }
        }
        int[] prodStart = new int[nodeCount + 1];
        for (int u = 0; u < nodeCount; u++) {
            List<Integer> list = localProds.get(u);
            if (list.size() > 1) {
                list.sort((a, b) -> compareLocalProducers(a, b, recPinned, recAdvisory, recHandCraftable,
                        recCapability, recCompatMachines, recObjects, ownedMachines, ownedCaps));
            }
            prodStart[u + 1] = prodStart[u] + list.size();
        }
        int[] prodRecipe = new int[prodStart[nodeCount]];
        for (int u = 0; u < nodeCount; u++) {
            List<Integer> list = localProds.get(u);
            int p = prodStart[u];
            for (int i = 0; i < list.size(); i++) {
                prodRecipe[p + i] = list.get(i);
            }
        }

        // Index active cycle macros by local positive output resource.
        List<CompiledCycleMacro> activeMacros = new ArrayList<>();
        Map<Integer, List<CompiledCycleMacro>> macrosByLocalOut = new HashMap<>();
        for (CompiledCycleMacro m : candidateMacros) {
            boolean feasible = true;
            for (ResourceKey s : m.workingSeeds().keySet()) {
                Integer lu = localByKey.get(s);
                if (lu == null || tier[lu] >= ConeModel.INF_TIER) {
                    feasible = false;
                    break;
                }
            }
            if (!feasible) continue;
            for (var e : m.netDeltaPerIter().entrySet()) {
                if (e.getValue() < 0L) {
                    Integer lu = localByKey.get(e.getKey());
                    if (lu == null || tier[lu] >= ConeModel.INF_TIER) {
                        feasible = false;
                        break;
                    }
                }
            }
            if (!feasible) continue;
            activeMacros.add(m);
            for (var e : m.netDeltaPerIter().entrySet()) {
                if (e.getValue() > 0L) {
                    Integer lo = localByKey.get(e.getKey());
                    if (lo != null) {
                        macrosByLocalOut.computeIfAbsent(lo, ignored -> new ArrayList<>(2)).add(m);
                    }
                }
            }
        }
        activeMacros.sort(Comparator.comparing(CompiledCycleMacro::macroKey));

        // 8. Detect CoupledComponents (for QuerySubgraph diagnostics & Stage 2 metadata).
        List<CoupledComponent> coupled = detectCoupledComponents(
                nodeCount, resKeys, prodStart, prodRecipe, recObjects, recActive, recHandCraftable,
                recCapability, recCompatMachines, ownedMachines, ownedCaps, usableStock
        );

        return new ConeModel(
                graph, resKeys, resGlobalId, localByKey, localByGlobalRes,
                tier, usableStock, freeBase, dormantArr, bannedRes, acqOpts, bestLines, allLinesArr,
                recCompactId, recObjects, localByCompactId, recRole, recAdvisory, recPinned,
                recHandCraftable, recCapability, recCompatMachines, recActive, recPsi,
                inStart, inSlotIndex, inAmount, inTagId, inMatchMode, inOrigCandCount,
                inClassStart, classRepList.toArray(), classMemStartList.toArray(), classMemResList.toArray(),
                catStart, catSlotIndex, catDurCost, catMaxDur, catCandStart, catCandResList.toArray(),
                outStart, outRes, outAmount, prodStart, prodRecipe,
                List.copyOf(activeMacros), macrosByLocalOut, coupled,
                collapsedOr, truncated, Collections.unmodifiableSet(dormantResources), blockingRules
        );
    }

    private List<CompiledRecipe> rankProducers(
            ResourceKey target,
            boolean isDirectTarget,
            IndexedRecipeGraph ig,
            FacilitySnapshot fac,
            HumanGuidanceView guidance,
            Set<String> ownedMachines,
            Set<MachineCapabilityId> ownedCaps,
            Set<Integer> masked,
            int maxProdPerRes,
            Set<ResourceKey> dormantResources,
            Map<ResourceKey, Set<String>> blockingRules
    ) {
        List<CompiledRecipe> allProds = graph.producersOf(target);
        if (allProds.isEmpty()) {
            return List.of();
        }
        List<CompiledRecipe> eligible = new ArrayList<>(Math.min(allProds.size(), maxProdPerRes * 2));
        boolean anyBlockedByHuman = false;
        boolean anyBlockedBySystemOptIn = false;

        for (CompiledRecipe r : allProds) {
            if (masked.contains(r.compactId())) {
                anyBlockedByHuman = true;
                continue;
            }
            if (guidance.isRecipeBanned(r.key())) {
                anyBlockedByHuman = true;
                recordRecipeBan(target, r.key(), guidance, blockingRules);
                continue;
            }
            if (isDirectTarget && guidance.isRecipeBannedForTarget(target, r.key())) {
                anyBlockedByHuman = true;
                recordCycleBreakBan(target, r.key(), guidance, blockingRules);
                continue;
            }
            String sysId = r.key().systemId();
            SystemAdmission adm = graph.admissionOf(sysId);
            boolean unlocked = fac.unlockedSystems().contains(sysId);
            if (!guidance.isSystemEnabled(sysId, adm, unlocked)) {
                if (adm == SystemAdmission.OPT_IN_ONLY && !unlocked) {
                    anyBlockedBySystemOptIn = true;
                } else {
                    anyBlockedByHuman = true;
                    recordSystemBan(target, sysId, guidance, blockingRules);
                }
                continue;
            }
            if (!r.machineRequirement().handCraftable() && !canUseOrBuildMachine(r, ownedMachines, ownedCaps, guidance)) {
                anyBlockedByHuman = true;
                recordMachineBan(target, r, guidance, blockingRules);
                continue;
            }
            eligible.add(r);
        }

        if (eligible.isEmpty()) {
            if (anyBlockedBySystemOptIn && !anyBlockedByHuman) {
                dormantResources.add(target);
            }
            return List.of();
        }

        eligible.sort((a, b) -> {
            boolean pa = guidance.isRecipePinned(a.key());
            boolean pb = guidance.isRecipePinned(b.key());
            if (pa != pb) return pa ? -1 : 1;
            int advA = pa ? 0 : a.advisory().ordinal();
            int advB = pb ? 0 : b.advisory().ordinal();
            if (advA != advB) return Integer.compare(advA, advB);
            int ownA = isMachineOwned(a, ownedMachines, ownedCaps) ? 0 : 1;
            int ownB = isMachineOwned(b, ownedMachines, ownedCaps) ? 0 : 1;
            if (ownA != ownB) return Integer.compare(ownA, ownB);
            int sysA = SystemCostTiers.systemPriority(a.key().systemId());
            int sysB = SystemCostTiers.systemPriority(b.key().systemId());
            if (sysA != sysB) return Integer.compare(sysA, sysB);
            int inA = a.inputs().size();
            int inB = b.inputs().size();
            if (inA != inB) return Integer.compare(inA, inB);
            return Integer.compare(a.compactId(), b.compactId());
        });

        if (eligible.size() <= maxProdPerRes) {
            return eligible;
        }
        List<CompiledRecipe> capped = new ArrayList<>(maxProdPerRes);
        int advCount = 0;
        for (CompiledRecipe r : eligible) {
            boolean pinned = guidance.isRecipePinned(r.key());
            if (pinned) {
                capped.add(r);
                continue;
            }
            if (capped.size() >= maxProdPerRes) {
                break;
            }
            if (r.advisory() != RecipeAdvisory.NONE) {
                if (advCount >= MAX_ADVISORY_PRODUCERS && !capped.isEmpty()) {
                    continue;
                }
                advCount++;
            }
            capped.add(r);
        }
        return capped;
    }

    private void enqueueSlotCandidatesFast(
            IndexedRecipeGraph ig,
            int compactId,
            int highFanoutCap,
            Map<ResourceKey, Long> usableMap,
            Map<ResourceKey, List<AutomatedLine>> linesByRes,
            HumanGuidanceView guidance,
            boolean[] oneHopFromStock,
            Map<Integer, List<ResourceKey>> highFanoutCache,
            LinkedHashSet<ResourceKey> visitedNodes,
            ArrayDeque<ResourceKey> waveQueue,
            int maxNodes
    ) {
        ResourceDictionary dict = ig.resourceDict();
        CandidateTable cands = ig.candidateTable();
        for (int si = ig.inStart(compactId); si < ig.inEnd(compactId); si++) {
            int listId = ig.inCandList(si);
            int[] members = cands.membersOf(listId);
            String tagId = ig.inTagId(si);
            if (tagId != null) {
                Optional<ResourceKey> pref = guidance.preferredTagMember(tagId);
                if (pref.isPresent() && !guidance.isResourceBanned(pref.get())) {
                    if (visitedNodes.add(pref.get())) {
                        waveQueue.add(pref.get());
                    }
                    if (guidance.isTagHardPinned(tagId)) {
                        continue;
                    }
                }
            }
            if (members.length <= HIGH_FANOUT_THRESHOLD) {
                for (int gid : members) {
                    ResourceKey rk = dict.keyOf(gid);
                    if (visitedNodes.size() < maxNodes && visitedNodes.add(rk)) {
                        waveQueue.add(rk);
                    }
                }
            } else {
                List<ResourceKey> picks = highFanoutCache.computeIfAbsent(listId, lid ->
                        selectHighFanoutFast(ig, members, highFanoutCap, usableMap, linesByRes, guidance, oneHopFromStock));
                for (ResourceKey rk : picks) {
                    if (visitedNodes.size() >= maxNodes) break;
                    if (visitedNodes.add(rk)) {
                        if (!DefaultAcquisitionOracle.isKnownBaseNatural(rk)
                                && !guidance.isResourceTreatedAsBase(rk)) {
                            waveQueue.add(rk);
                        }
                    }
                }
            }
        }
        for (int ci = ig.catStart(compactId); ci < ig.catEnd(compactId); ci++) {
            int[] members = cands.membersOf(ig.catCandList(ci));
            int limit = Math.min(members.length, Math.max(HIGH_FANOUT_THRESHOLD, highFanoutCap));
            for (int i = 0; i < limit; i++) {
                ResourceKey rk = dict.keyOf(members[i]);
                if (visitedNodes.size() < maxNodes && visitedNodes.add(rk)) {
                    waveQueue.add(rk);
                }
            }
        }
    }

    private static List<ResourceKey> selectHighFanoutFast(
            IndexedRecipeGraph ig,
            int[] members,
            int cap,
            Map<ResourceKey, Long> usableMap,
            Map<ResourceKey, List<AutomatedLine>> linesByRes,
            HumanGuidanceView guidance,
            boolean[] oneHopFromStock
    ) {
        ResourceDictionary dict = ig.resourceDict();
        List<ResourceKey> direct = new ArrayList<>();
        List<Integer> producible = new ArrayList<>();
        ResourceKey firstNatural = null;

        for (int gid : members) {
            ResourceKey rk = dict.keyOf(gid);
            if (guidance.isResourceBanned(rk)) continue;
            if (usableMap.containsKey(rk) || linesByRes.containsKey(rk) || guidance.isResourceTreatedAsBase(rk)) {
                direct.add(rk);
                continue;
            }
            if (DefaultAcquisitionOracle.isKnownBaseNatural(rk)) {
                if (firstNatural == null) {
                    firstNatural = rk;
                }
                direct.add(rk);
                continue;
            }
            if (ig.producerCount(gid) > 0) {
                producible.add(gid);
            } else if (direct.size() < 4) {
                direct.add(rk);
            }
        }

        if (cap < Integer.MAX_VALUE && producible.size() > cap) {
            producible.sort((a, b) -> {
                int sa = (oneHopFromStock != null && oneHopFromStock[a]) ? 0 : 1;
                int sb = (oneHopFromStock != null && oneHopFromStock[b]) ? 0 : 1;
                if (sa != sb) return Integer.compare(sa, sb);
                int pa = ig.producerCount(a);
                int pb = ig.producerCount(b);
                if (pa != pb) return Integer.compare(pa, pb);
                int ca = ig.exactConsumerCount(a);
                int cb = ig.exactConsumerCount(b);
                if (ca != cb) return Integer.compare(cb, ca);
                return Integer.compare(a, b);
            });
            producible = producible.subList(0, cap);
        }

        List<ResourceKey> out = new ArrayList<>(direct.size() + producible.size());
        out.addAll(direct);
        for (int gid : producible) {
            out.add(dict.keyOf(gid));
        }
        return out;
    }

    private boolean enqueueSlotGeneric(
            InputSlot slot,
            int highFanoutCap,
            Map<ResourceKey, Long> usableMap,
            Map<ResourceKey, List<AutomatedLine>> linesByRes,
            HumanGuidanceView guidance,
            LinkedHashSet<ResourceKey> visitedNodes,
            ArrayDeque<ResourceKey> waveQueue,
            int maxNodes
    ) {
        boolean trunc = false;
        if (slot.tagId().isPresent()) {
            Optional<ResourceKey> pref = guidance.preferredTagMember(slot.tagId().get());
            if (pref.isPresent() && !guidance.isResourceBanned(pref.get())) {
                if (visitedNodes.add(pref.get())) waveQueue.add(pref.get());
                if (guidance.isTagHardPinned(slot.tagId().get())) return false;
            }
        }
        List<ResourceKey> cands = slot.candidates();
        if (cands.size() <= HIGH_FANOUT_THRESHOLD) {
            for (ResourceKey rk : cands) {
                if (visitedNodes.size() >= maxNodes) return true;
                if (visitedNodes.add(rk)) waveQueue.add(rk);
            }
            return false;
        }
        int nonBaseAdded = 0;
        for (ResourceKey rk : cands) {
            if (guidance.isResourceBanned(rk)) continue;
            if (visitedNodes.size() >= maxNodes) return true;
            if (usableMap.containsKey(rk) || linesByRes.containsKey(rk)
                    || guidance.isResourceTreatedAsBase(rk) || DefaultAcquisitionOracle.isKnownBaseNatural(rk)) {
                if (visitedNodes.add(rk) && !DefaultAcquisitionOracle.isKnownBaseNatural(rk)) {
                    waveQueue.add(rk);
                }
            } else if (nonBaseAdded < highFanoutCap) {
                nonBaseAdded++;
                if (visitedNodes.add(rk)) waveQueue.add(rk);
            } else {
                trunc = true;
            }
        }
        return trunc;
    }

    private static boolean[] computeOneHopStocked(IndexedRecipeGraph ig, Map<ResourceKey, Long> usableMap) {
        ResourceDictionary dict = ig.resourceDict();
        boolean[] oneHop = new boolean[dict.size()];
        for (ResourceKey rk : usableMap.keySet()) {
            int gid = dict.idOf(rk);
            if (gid < 0) continue;
            for (int ci = ig.consStart(gid); ci < ig.consEnd(gid); ci++) {
                int r = ig.consRecipe(ci);
                for (int oi = ig.outStart(r); oi < ig.outEnd(r); oi++) {
                    oneHop[ig.outRes(oi)] = true;
                }
            }
        }
        return oneHop;
    }

    private static int[] resolveLocalCandidates(
            String tagId,
            List<ResourceKey> candidates,
            Map<ResourceKey, Integer> localByKey,
            boolean[] bannedRes,
            HumanGuidanceView guidance
    ) {
        if (tagId != null && guidance.isTagHardPinned(tagId)) {
            Optional<ResourceKey> pref = guidance.preferredTagMember(tagId);
            if (pref.isPresent() && candidates.contains(pref.get())) {
                Integer lu = localByKey.get(pref.get());
                if (lu != null && !bannedRes[lu]) {
                    return new int[]{lu};
                }
            }
        }
        int[] buf = new int[candidates.size()];
        int len = 0;
        for (ResourceKey rk : candidates) {
            Integer lu = localByKey.get(rk);
            if (lu != null && !bannedRes[lu]) {
                buf[len++] = lu;
            }
        }
        return len == buf.length ? buf : Arrays.copyOf(buf, len);
    }

    private static int[] filterReachable(int[] raw, int[] tier, int maxExclusiveTier) {
        int count = 0;
        for (int u : raw) {
            if (tier[u] < maxExclusiveTier) count++;
        }
        if (count == raw.length) return raw;
        int[] out = new int[count];
        int w = 0;
        for (int u : raw) {
            if (tier[u] < maxExclusiveTier) out[w++] = u;
        }
        return out;
    }

    private static int buildQuotientClasses(
            int[] cands,
            ResourceKey prefTag,
            boolean[] isSingleton,
            String[] leafSig,
            IndexedRecipeGraph ig,
            int[] resGlobalId,
            IntList classRepList,
            IntList classMemStartList,
            IntList classMemResList
    ) {
        if (cands.length == 0) return 0;
        if (cands.length == 1) {
            classMemStartList.add(classMemResList.size());
            classMemResList.add(cands[0]);
            classRepList.add(cands[0]);
            return 0;
        }
        int collapsed = 0;
        Map<String, List<Integer>> leafBuckets = new LinkedHashMap<>();
        List<int[]> classes = new ArrayList<>(cands.length);

        for (int u : cands) {
            if (isSingleton[u] || leafSig[u] == null) {
                classes.add(new int[]{u});
            } else {
                leafBuckets.computeIfAbsent(leafSig[u], ignored -> new ArrayList<>(4)).add(u);
            }
        }
        for (List<Integer> bucket : leafBuckets.values()) {
            int[] arr = new int[bucket.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = bucket.get(i);
            Arrays.sort(arr);
            classes.add(arr);
            if (arr.length > 1) {
                collapsed += arr.length - 1;
            }
        }

        // Choose deterministic representative per class, then sort classes by representative.
        record ClassPick(int rep, int[] members) {
        }
        List<ClassPick> picks = new ArrayList<>(classes.size());
        for (int[] mems : classes) {
            int rep = chooseRepresentative(mems, prefTag, ig, resGlobalId);
            picks.add(new ClassPick(rep, mems));
        }
        picks.sort(Comparator.comparingInt(ClassPick::rep));

        for (ClassPick p : picks) {
            classMemStartList.add(classMemResList.size());
            for (int u : p.members) {
                classMemResList.add(u);
            }
            classRepList.add(p.rep);
        }
        return collapsed;
    }

    private static int chooseRepresentative(int[] members, ResourceKey prefTag, IndexedRecipeGraph ig, int[] resGlobalId) {
        if (members.length == 1) {
            return members[0];
        }
        if (ig == null) {
            return members[0];
        }
        int best = members[0];
        int bestCentrality = resGlobalId[best] >= 0 ? ig.exactConsumerCount(resGlobalId[best]) : 0;
        for (int i = 1; i < members.length; i++) {
            int u = members[i];
            int cent = resGlobalId[u] >= 0 ? ig.exactConsumerCount(resGlobalId[u]) : 0;
            if (cent > bestCentrality) {
                best = u;
                bestCentrality = cent;
            }
        }
        return best;
    }

    private static boolean isStandardActiveOrAnyScc(
            CompiledRecipe rec, int r, byte[] recActive, CompiledRecipeGraph graph
    ) {
        if (recActive[r] == ConeModel.ACTIVE_NONE) return false;
        for (OutputSlot o : rec.outputs()) {
            if (graph.sccTopology().isInNonTrivialScc(o.resource())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMacroAllowed(
            CompiledCycleMacro macro, FacilitySnapshot fac, HumanGuidanceView guidance, Set<Integer> masked
    ) {
        for (CompiledRecipe inner : macro.innerRecipes()) {
            if (masked.contains(inner.compactId()) || guidance.isRecipeBanned(inner.key())) {
                return false;
            }
            String sysId = inner.key().systemId();
            if (!guidance.isSystemEnabled(sysId, graphAdmissionStatic(inner, sysId),
                    fac.unlockedSystems().contains(sysId))) {
                return false;
            }
        }
        return true;
    }

    private boolean isMacroAllowedWithGraph(
            CompiledCycleMacro macro, FacilitySnapshot fac, HumanGuidanceView guidance, Set<Integer> masked
    ) {
        for (CompiledRecipe inner : macro.innerRecipes()) {
            if (masked.contains(inner.compactId()) || guidance.isRecipeBanned(inner.key())) {
                return false;
            }
            String sysId = inner.key().systemId();
            if (!guidance.isSystemEnabled(sysId, graph.admissionOf(sysId), fac.unlockedSystems().contains(sysId))) {
                return false;
            }
        }
        return true;
    }

    private static SystemAdmission graphAdmissionStatic(CompiledRecipe inner, String sysId) {
        return dev.vfyjxf.cirrus.planning.core.graph.DefaultSystemAdmissionPolicy.defaults()
                .classify(sysId, "");
    }

    private static boolean isMachineOwned(
            CompiledRecipe r, Set<String> ownedMachines, Set<MachineCapabilityId> ownedCaps
    ) {
        if (r.machineRequirement().handCraftable()) return true;
        if (r.machineRequirement().capabilityId().isPresent()
                && ownedCaps.contains(r.machineRequirement().capabilityId().get())) {
            return true;
        }
        for (String m : r.machineRequirement().compatibleMachineIds()) {
            if (ownedMachines.contains(m)) return true;
        }
        return false;
    }

    private static boolean canUseOrBuildMachine(
            CompiledRecipe r, Set<String> ownedMachines, Set<MachineCapabilityId> ownedCaps, HumanGuidanceView guidance
    ) {
        if (isMachineOwned(r, ownedMachines, ownedCaps)) return true;
        List<String> compat = r.machineRequirement().compatibleMachineIds();
        if (compat.isEmpty()) {
            return true;
        }
        for (String m : compat) {
            if (guidance.isWillingToBuild(m)) return true;
        }
        return false;
    }

    private static int compareLocalProducers(
            int a, int b,
            boolean[] recPinned,
            RecipeAdvisory[] recAdvisory,
            boolean[] recHand,
            MachineCapabilityId[] recCap,
            List<String>[] recCompat,
            CompiledRecipe[] recObjects,
            Set<String> ownedMachines,
            Set<MachineCapabilityId> ownedCaps
    ) {
        if (recPinned[a] != recPinned[b]) return recPinned[a] ? -1 : 1;
        int advA = recPinned[a] ? 0 : recAdvisory[a].ordinal();
        int advB = recPinned[b] ? 0 : recAdvisory[b].ordinal();
        if (advA != advB) return Integer.compare(advA, advB);
        int ownA = (recHand[a] || (recCap[a] != null && ownedCaps.contains(recCap[a]))
                || anyOwned(recCompat[a], ownedMachines)) ? 0 : 1;
        int ownB = (recHand[b] || (recCap[b] != null && ownedCaps.contains(recCap[b]))
                || anyOwned(recCompat[b], ownedMachines)) ? 0 : 1;
        if (ownA != ownB) return Integer.compare(ownA, ownB);
        int sysA = SystemCostTiers.systemPriority(recObjects[a].key().systemId());
        int sysB = SystemCostTiers.systemPriority(recObjects[b].key().systemId());
        if (sysA != sysB) return Integer.compare(sysA, sysB);
        return Integer.compare(recObjects[a].compactId(), recObjects[b].compactId());
    }

    private static boolean anyOwned(List<String> machines, Set<String> owned) {
        for (String m : machines) {
            if (owned.contains(m)) return true;
        }
        return false;
    }

    private static List<CoupledComponent> detectCoupledComponents(
            int nodeCount,
            ResourceKey[] resKeys,
            int[] prodStart,
            int[] prodRecipe,
            CompiledRecipe[] recObjects,
            byte[] recActive,
            boolean[] recHand,
            MachineCapabilityId[] recCap,
            List<String>[] recCompat,
            Set<String> ownedMachines,
            Set<MachineCapabilityId> ownedCaps,
            long[] usableStock
    ) {
        List<CoupledComponent> out = new ArrayList<>();
        // BATCH_AND_COPRODUCTION components.
        for (int u = 0; u < nodeCount; u++) {
            int ps = prodStart[u], pe = prodStart[u + 1];
            if (pe - ps < 2) continue;
            boolean interesting = false;
            Set<Integer> compactIds = new TreeSet<>();
            for (int i = ps; i < pe; i++) {
                CompiledRecipe rec = recObjects[prodRecipe[i]];
                compactIds.add(rec.compactId());
                if (rec.hasBatchGreaterThanOne() || rec.isMultiOutput()) {
                    interesting = true;
                }
            }
            if (interesting) {
                out.add(new CoupledComponent(
                        CoupledKind.BATCH_AND_COPRODUCTION,
                        Set.of(resKeys[u]),
                        compactIds,
                        Set.of()
                ));
            }
        }
        // SHARED_MACHINE_SETUP component if multiple unowned-machine recipes share candidates.
        Set<String> newMachines = new TreeSet<>();
        Set<Integer> machRecipes = new TreeSet<>();
        Set<ResourceKey> machRes = new TreeSet<>();
        for (int r = 0; r < recObjects.length; r++) {
            if (recActive[r] == ConeModel.ACTIVE_NONE || recHand[r]) continue;
            boolean owned = (recCap[r] != null && ownedCaps.contains(recCap[r]))
                    || anyOwned(recCompat[r], ownedMachines);
            if (!owned && !recCompat[r].isEmpty()) {
                machRecipes.add(recObjects[r].compactId());
                newMachines.addAll(recCompat[r]);
                for (OutputSlot o : recObjects[r].outputs()) {
                    machRes.add(o.resource());
                }
            }
        }
        if (machRecipes.size() >= 2 && !newMachines.isEmpty()) {
            out.add(new CoupledComponent(CoupledKind.SHARED_MACHINE_SETUP, machRes, machRecipes, newMachines));
        }
        return List.copyOf(out);
    }

    private static void recordRecipeBan(
            ResourceKey target, CompiledRecipeKey rk, HumanGuidanceView guidance, Map<ResourceKey, Set<String>> map
    ) {
        for (GuidanceRule r : guidance.hardBanRules()) {
            if (r instanceof RecipeRule rr && rr.recipeKey().equals(rk)) {
                map.computeIfAbsent(target, ignored -> new TreeSet<>()).add(rr.ruleId());
            } else if (r instanceof ModNamespaceRule mnr && matchesMod(mnr.modNamespace(), rk)) {
                map.computeIfAbsent(target, ignored -> new TreeSet<>()).add(mnr.ruleId());
            }
        }
    }

    private static void recordCycleBreakBan(
            ResourceKey target, CompiledRecipeKey rk, HumanGuidanceView guidance, Map<ResourceKey, Set<String>> map
    ) {
        for (GuidanceRule r : guidance.hardBanRules()) {
            if (r instanceof CycleBreakRule cbr
                    && cbr.targetResource().equals(target)
                    && cbr.bannedSourceRecipe().equals(rk)) {
                map.computeIfAbsent(target, ignored -> new TreeSet<>()).add(cbr.ruleId());
            }
        }
    }

    private static void recordSystemBan(
            ResourceKey target, String sysId, HumanGuidanceView guidance, Map<ResourceKey, Set<String>> map
    ) {
        for (GuidanceRule r : guidance.hardBanRules()) {
            if (r instanceof SystemRule sr && sr.systemId().equals(sysId)) {
                map.computeIfAbsent(target, ignored -> new TreeSet<>()).add(sr.ruleId());
            } else if (r instanceof ModNamespaceRule mnr && sysMatchesMod(mnr.modNamespace(), sysId)) {
                map.computeIfAbsent(target, ignored -> new TreeSet<>()).add(mnr.ruleId());
            }
        }
    }

    private static void recordMachineBan(
            ResourceKey target, CompiledRecipe rec, HumanGuidanceView guidance, Map<ResourceKey, Set<String>> map
    ) {
        for (String mid : rec.machineRequirement().compatibleMachineIds()) {
            for (GuidanceRule r : guidance.hardBanRules()) {
                if (r instanceof MachineRule mr && mr.machineId().equals(mid)) {
                    map.computeIfAbsent(target, ignored -> new TreeSet<>()).add(mr.ruleId());
                } else if (r instanceof ModNamespaceRule mnr && mid.startsWith(mnr.modNamespace() + ":")) {
                    map.computeIfAbsent(target, ignored -> new TreeSet<>()).add(mnr.ruleId());
                }
            }
        }
    }

    private static void recordResourceBan(
            ResourceKey target, HumanGuidanceView guidance, Map<ResourceKey, Set<String>> map
    ) {
        for (GuidanceRule r : guidance.hardBanRules()) {
            if (r instanceof ResourceRule rr && rr.resource().equals(target)) {
                map.computeIfAbsent(target, ignored -> new TreeSet<>()).add(rr.ruleId());
            }
        }
    }

    private static boolean matchesMod(String ns, CompiledRecipeKey rk) {
        if (rk.explicitRecipeId().isPresent() && rk.explicitRecipeId().get().startsWith(ns + ":")) {
            return true;
        }
        return sysMatchesMod(ns, rk.systemId());
    }

    private static boolean sysMatchesMod(String ns, String sysId) {
        return sysId.startsWith(ns + ":") || sysId.startsWith("jei:category/" + ns + "/");
    }

    private static final class IntList {
        private int[] data;
        private int size;

        IntList(int initialCapacity) {
            this.data = new int[Math.max(16, initialCapacity)];
        }

        void add(int v) {
            if (size == data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            data[size++] = v;
        }

        int size() {
            return size;
        }

        int[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }
}
