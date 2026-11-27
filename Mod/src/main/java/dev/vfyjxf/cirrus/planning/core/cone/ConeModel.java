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

    public static final int infTier = Integer.MAX_VALUE;
    public static final byte activeNone = 0;
    public static final byte activeStandard = 1;
    public static final byte activeConvert = 2;
    public static final byte activeBoth = 3;

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
        this.resKeys = resKeys.clone();
        this.resGlobalId = resGlobalId.clone();
        this.localByKey = localByKey;
        this.localByGlobalRes = localByGlobalRes;
        this.tier = tier.clone();
        this.usableStock = usableStock.clone();
        this.freeBase = freeBase.clone();
        this.dormant = dormant.clone();
        this.bannedRes = bannedRes.clone();
        this.acquireOption = acquireOption.clone();
        this.bestLine = bestLine.clone();
        this.allLines = allLines.clone();
        this.recipeCount = recCompactId.length;
        this.recCompactId = recCompactId.clone();
        this.recObjects = recObjects.clone();
        this.localByCompactId = localByCompactId;
        this.recRole = recRole.clone();
        this.recAdvisory = recAdvisory.clone();
        this.recPinned = recPinned.clone();
        this.recHandCraftable = recHandCraftable.clone();
        this.recCapability = recCapability.clone();
        this.recCompatMachines = recCompatMachines.clone();
        this.recActive = recActive.clone();
        this.recPsi = recPsi.clone();
        this.inStart = inStart.clone();
        this.inSlotIndex = inSlotIndex.clone();
        this.inAmount = inAmount.clone();
        this.inTagId = inTagId.clone();
        this.inMatchMode = inMatchMode.clone();
        this.inOrigCandCount = inOrigCandCount.clone();
        this.inClassStart = inClassStart.clone();
        this.classRep = classRep.clone();
        this.classMemStart = classMemStart.clone();
        this.classMemRes = classMemRes.clone();
        this.catStart = catStart.clone();
        this.catSlotIndex = catSlotIndex.clone();
        this.catDurCost = catDurCost.clone();
        this.catMaxDur = catMaxDur.clone();
        this.catCandStart = catCandStart.clone();
        this.catCandRes = catCandRes.clone();
        this.outStart = outStart.clone();
        this.outRes = outRes.clone();
        this.outAmount = outAmount.clone();
        this.prodStart = prodStart.clone();
        this.prodRecipe = prodRecipe.clone();
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
        return (recActive[localR] & activeStandard) != 0;
    }

    public boolean isConvertActive(int localR) {
        return (recActive[localR] & activeConvert) != 0;
    }

    public boolean isAnyActive(int localR) {
        return recActive[localR] != activeNone;
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
