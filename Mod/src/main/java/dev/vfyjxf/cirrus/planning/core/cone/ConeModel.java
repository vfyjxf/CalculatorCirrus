package dev.vfyjxf.cirrus.planning.core.cone;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOption;
import dev.vfyjxf.cirrus.api.planning.context.AutomatedLine;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledCycleMacro;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.CoupledComponent;
import dev.vfyjxf.cirrus.api.planning.graph.EquivalenceClass;
import dev.vfyjxf.cirrus.api.planning.graph.QuerySubgraph;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeAdvisory;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeRole;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.planning.core.lang.IntIntMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ConeModel {

    public static final int INF_TIER = Integer.MAX_VALUE;
    public static final byte ACTIVE_NONE = 0;
    public static final byte ACTIVE_STANDARD = 1;
    public static final byte ACTIVE_CONVERT = 2;
    public static final byte ACTIVE_BOTH = 3;

    public final CompiledRecipeGraph graph;
    public final int nodeCount;
    public final ResourceKey[] resKeys;
    public final int[] resGlobalId;
    public final Map<ResourceKey, Integer> localByKey;
    public final IntIntMap localByGlobalRes;
    public final int[] tier;
    public final long[] usableStock;
    public final boolean[] freeBase;
    public final boolean[] dormant;
    public final boolean[] bannedRes;
    public final AcquisitionOption[] acquireOption;
    public final AutomatedLine[] bestLine;
    public final List<AutomatedLine>[] allLines;

    public final int recipeCount;
    public final int[] recCompactId;
    public final CompiledRecipe[] recObjects;
    public final IntIntMap localByCompactId;
    public final RecipeRole[] recRole;
    public final RecipeAdvisory[] recAdvisory;
    public final boolean[] recPinned;
    public final boolean[] recHandCraftable;
    public final MachineCapabilityId[] recCapability;
    public final List<String>[] recCompatMachines;
    public final byte[] recActive;
    public final int[] recPsi;

    public final int[] inStart;
    public final int[] inSlotIndex;
    public final long[] inAmount;
    public final String[] inTagId;
    public final SlotMatchMode[] inMatchMode;
    public final int[] inOrigCandCount;
    public final int[] inClassStart;
    public final int[] classRep;
    public final int[] classMemStart;
    public final int[] classMemRes;

    public final int[] catStart;
    public final int[] catSlotIndex;
    public final int[] catDurCost;
    public final int[] catMaxDur;
    public final int[] catCandStart;
    public final int[] catCandRes;

    public final int[] outStart;
    public final int[] outRes;
    public final long[] outAmount;

    public final int[] prodStart;
    public final int[] prodRecipe;

    public final List<CompiledCycleMacro> activeMacros;
    public final Map<Integer, List<CompiledCycleMacro>> macrosByLocalOut;

    public final List<CoupledComponent> coupledComponents;
    public final int collapsedOrCandidates;
    public final boolean truncatedByBudget;
    public final Set<ResourceKey> dormantResources;
    public final Map<ResourceKey, Set<String>> blockingRuleIds;

    ConeModel(
            CompiledRecipeGraph graph,
            ResourceKey[] resKeys,
            int[] resGlobalId,
            Map<ResourceKey, Integer> localByKey,
            IntIntMap localByGlobalRes,
            int[] tier,
            long[] usableStock,
            boolean[] freeBase,
            boolean[] dormant,
            boolean[] bannedRes,
            AcquisitionOption[] acquireOption,
            AutomatedLine[] bestLine,
            List<AutomatedLine>[] allLines,
            int[] recCompactId,
            CompiledRecipe[] recObjects,
            IntIntMap localByCompactId,
            RecipeRole[] recRole,
            RecipeAdvisory[] recAdvisory,
            boolean[] recPinned,
            boolean[] recHandCraftable,
            MachineCapabilityId[] recCapability,
            List<String>[] recCompatMachines,
            byte[] recActive,
            int[] recPsi,
            int[] inStart,
            int[] inSlotIndex,
            long[] inAmount,
            String[] inTagId,
            SlotMatchMode[] inMatchMode,
            int[] inOrigCandCount,
            int[] inClassStart,
            int[] classRep,
            int[] classMemStart,
            int[] classMemRes,
            int[] catStart,
            int[] catSlotIndex,
            int[] catDurCost,
            int[] catMaxDur,
            int[] catCandStart,
            int[] catCandRes,
            int[] outStart,
            int[] outRes,
            long[] outAmount,
            int[] prodStart,
            int[] prodRecipe,
            List<CompiledCycleMacro> activeMacros,
            Map<Integer, List<CompiledCycleMacro>> macrosByLocalOut,
            List<CoupledComponent> coupledComponents,
            int collapsedOrCandidates,
            boolean truncatedByBudget,
            Set<ResourceKey> dormantResources,
            Map<ResourceKey, Set<String>> blockingRuleIds
    ) {
        this.graph = graph;
        this.nodeCount = resKeys.length;
        this.resKeys = resKeys;
        this.resGlobalId = resGlobalId;
        this.localByKey = localByKey;
        this.localByGlobalRes = localByGlobalRes;
        this.tier = tier;
        this.usableStock = usableStock;
        this.freeBase = freeBase;
        this.dormant = dormant;
        this.bannedRes = bannedRes;
        this.acquireOption = acquireOption;
        this.bestLine = bestLine;
        this.allLines = allLines;
        this.recipeCount = recCompactId.length;
        this.recCompactId = recCompactId;
        this.recObjects = recObjects;
        this.localByCompactId = localByCompactId;
        this.recRole = recRole;
        this.recAdvisory = recAdvisory;
        this.recPinned = recPinned;
        this.recHandCraftable = recHandCraftable;
        this.recCapability = recCapability;
        this.recCompatMachines = recCompatMachines;
        this.recActive = recActive;
        this.recPsi = recPsi;
        this.inStart = inStart;
        this.inSlotIndex = inSlotIndex;
        this.inAmount = inAmount;
        this.inTagId = inTagId;
        this.inMatchMode = inMatchMode;
        this.inOrigCandCount = inOrigCandCount;
        this.inClassStart = inClassStart;
        this.classRep = classRep;
        this.classMemStart = classMemStart;
        this.classMemRes = classMemRes;
        this.catStart = catStart;
        this.catSlotIndex = catSlotIndex;
        this.catDurCost = catDurCost;
        this.catMaxDur = catMaxDur;
        this.catCandStart = catCandStart;
        this.catCandRes = catCandRes;
        this.outStart = outStart;
        this.outRes = outRes;
        this.outAmount = outAmount;
        this.prodStart = prodStart;
        this.prodRecipe = prodRecipe;
        this.activeMacros = activeMacros;
        this.macrosByLocalOut = macrosByLocalOut;
        this.coupledComponents = coupledComponents;
        this.collapsedOrCandidates = collapsedOrCandidates;
        this.truncatedByBudget = truncatedByBudget;
        this.dormantResources = dormantResources;
        this.blockingRuleIds = blockingRuleIds;
    }

    public int localRes(ResourceKey key) {
        Integer id = localByKey.get(key);
        return id == null ? -1 : id;
    }

    public int localRecipe(int compactId) {
        return localByCompactId.getOrDefault(compactId, -1);
    }

    public boolean isStandardActive(int localR) {
        return (recActive[localR] & ACTIVE_STANDARD) != 0;
    }

    public boolean isConvertActive(int localR) {
        return (recActive[localR] & ACTIVE_CONVERT) != 0;
    }

    public boolean isAnyActive(int localR) {
        return recActive[localR] != ACTIVE_NONE;
    }

    public List<EquivalenceClass> reducedCandidatesForLocalSlot(int slotPos) {
        int cs = inClassStart[slotPos];
        int ce = inClassStart[slotPos + 1];
        if (cs == ce) {
            return List.of();
        }
        Optional<String> tag = Optional.ofNullable(inTagId[slotPos]);
        EquivalenceClass[] arr = new EquivalenceClass[ce - cs];
        for (int i = cs; i < ce; i++) {
            int ms = classMemStart[i];
            int me = classMemStart[i + 1];
            ResourceKey[] mems = new ResourceKey[me - ms];
            for (int j = ms; j < me; j++) {
                mems[j - ms] = resKeys[classMemRes[j]];
            }
            arr[i - cs] = new EquivalenceClass(resKeys[classRep[i]], List.of(mems), tag);
        }
        return List.of(arr);
    }

    public QuerySubgraph toQuerySubgraph() {
        return new QuerySubgraphImpl(this);
    }
}
