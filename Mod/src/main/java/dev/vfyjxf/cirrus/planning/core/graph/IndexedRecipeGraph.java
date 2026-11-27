package dev.vfyjxf.cirrus.planning.core.graph;

import dev.vfyjxf.cirrus.api.planning.graph.CatalystSlot;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledCycleMacro;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.GraphCompilationStats;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.MachineRequirement;
import dev.vfyjxf.cirrus.api.planning.graph.OutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeAdvisory;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeRole;
import dev.vfyjxf.cirrus.api.planning.graph.SccTopology;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmissionPolicy;
import dev.vfyjxf.cirrus.api.planning.model.CompiledCycleMacroKey;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.api.planning.tag.CanonicalRecipeTag;
import dev.vfyjxf.cirrus.api.planning.tag.RecipeEvidenceTier;
import dev.vfyjxf.cirrus.api.planning.tag.RecipeSemanticKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class IndexedRecipeGraph implements CompiledRecipeGraph {

    private static final RecipeRole[] roleTable = RecipeRole.values();
    private static final RecipeAdvisory[] advisoryTable = RecipeAdvisory.values();
    private static final SlotMatchMode[] matchModes = SlotMatchMode.values();

    private final long catalogVersion;
    private final SystemAdmissionPolicy policy;
    private final Map<String, SystemAdmission> admissionCache;
    private final ResourceDictionary dict;
    private final CandidateTable candidates;
    private final IterativeTarjanScc sccIndex;
    private volatile GraphCompilationStats stats;
    private final Set<ResourceKey> unresolvedOutputs;

    private final int recipeCount;
    private final CompiledRecipeKey[] recipeKeys;
    private final byte[] roles;
    private final byte[] advisories;
    private final long[] sourceRefs;
    private final boolean[] handCraftable;
    private final MachineCapabilityId[] capabilities;
    private final List<String>[] compatMachines;
    private final Map<String, Double>[] recipeValues;
    private final Set<String>[] systemTags;
    private final Set<CanonicalRecipeTag>[] canonicalTags;
    private final RecipeEvidenceTier[] evidenceTiers;
    private final double[] structuralFidelities;
    private final @Nullable RecipeSemanticKey[] semanticKeys;

    private final int[] inStart;
    private final int[] inSlotIndex;
    private final int[] inCandList;
    private final long[] inAmount;
    private final byte[] inMatchMode;
    private final String[] inTagId;

    private final int[] catStart;
    private final int[] catSlotIndex;
    private final int[] catCandList;
    private final int[] catDurCost;
    private final int[] catMaxDur;

    private final int[] outStart;
    private final int[] outRes;
    private final long[] outAmount;

    private final int[] prodStart;
    private final int[] prodRecipe;
    private final int[] consStart;
    private final int[] consRecipe;
    private final int[] exactConsumerCount;

    private final Map<CompiledRecipeKey, Integer> compactIdByKey;
    private final CompiledRecipe[] materializedCache;
    private final Set<ResourceKey> producibleResources;

    private List<CompiledCycleMacro> allMacros = List.of();
    private Map<CompiledCycleMacroKey, CompiledCycleMacro> macroByKey = Map.of();
    private Map<Integer, List<CompiledCycleMacro>> macrosByOutputRes = Map.of();

    IndexedRecipeGraph(
        long catalogVersion,
        SystemAdmissionPolicy policy,
        Map<String, SystemAdmission> admissionCache,
        ResourceDictionary dict,
        CandidateTable candidates,
        IterativeTarjanScc sccIndex,
        GraphCompilationStats stats,
        Set<ResourceKey> unresolvedOutputs,
        CompiledRecipeKey[] recipeKeys,
        byte[] roles,
        byte[] advisories,
        long[] sourceRefs,
        boolean[] handCraftable,
        MachineCapabilityId[] capabilities,
        List<String>[] compatMachines,
        Map<String, Double>[] recipeValues,
        Set<String>[] systemTags,
        Set<CanonicalRecipeTag>[] canonicalTags,
        RecipeEvidenceTier[] evidenceTiers,
        double[] structuralFidelities,
        @Nullable RecipeSemanticKey[] semanticKeys,
        int[] inStart,
        int[] inSlotIndex,
        int[] inCandList,
        long[] inAmount,
        byte[] inMatchMode,
        String[] inTagId,
        int[] catStart,
        int[] catSlotIndex,
        int[] catCandList,
        int[] catDurCost,
        int[] catMaxDur,
        int[] outStart,
        int[] outRes,
        long[] outAmount,
        int[] prodStart,
        int[] prodRecipe,
        int[] consStart,
        int[] consRecipe,
        int[] exactConsumerCount
    ) {
        this.catalogVersion = catalogVersion;
        this.policy = policy;
        this.admissionCache = admissionCache;
        this.dict = dict;
        this.candidates = candidates;
        this.sccIndex = sccIndex;
        this.stats = stats;
        this.unresolvedOutputs = unresolvedOutputs;
        this.recipeCount = recipeKeys.length;
        this.recipeKeys = recipeKeys.clone();
        this.roles = roles.clone();
        this.advisories = advisories.clone();
        this.sourceRefs = sourceRefs.clone();
        this.handCraftable = handCraftable.clone();
        this.capabilities = capabilities.clone();
        this.compatMachines = compatMachines.clone();
        this.recipeValues = recipeValues.clone();
        this.systemTags = systemTags.clone();
        this.canonicalTags = canonicalTags.clone();
        this.evidenceTiers = evidenceTiers.clone();
        this.structuralFidelities = structuralFidelities.clone();
        this.semanticKeys = semanticKeys.clone();
        this.inStart = inStart.clone();
        this.inSlotIndex = inSlotIndex.clone();
        this.inCandList = inCandList.clone();
        this.inAmount = inAmount.clone();
        this.inMatchMode = inMatchMode.clone();
        this.inTagId = inTagId.clone();
        this.catStart = catStart.clone();
        this.catSlotIndex = catSlotIndex.clone();
        this.catCandList = catCandList.clone();
        this.catDurCost = catDurCost.clone();
        this.catMaxDur = catMaxDur.clone();
        this.outStart = outStart.clone();
        this.outRes = outRes.clone();
        this.outAmount = outAmount.clone();
        this.prodStart = prodStart.clone();
        this.prodRecipe = prodRecipe.clone();
        this.consStart = consStart.clone();
        this.consRecipe = consRecipe.clone();
        this.exactConsumerCount = exactConsumerCount.clone();
        this.materializedCache = new CompiledRecipe[recipeCount];

        Map<CompiledRecipeKey, Integer> keyMap = new HashMap<>((int) (recipeCount / 0.7) + 16);
        for (int i = 0; i < recipeCount; i++) {
            keyMap.put(recipeKeys[i], i);
        }
        this.compactIdByKey = keyMap;

        TreeSet<ResourceKey> producible = new TreeSet<>();
        for (int u = 0; u < dict.size(); u++) {
            if (prodStart[u + 1] > prodStart[u]) {
                producible.add(dict.keyOf(u));
            }
        }
        this.producibleResources = Collections.unmodifiableSet(producible);
    }

    void attachMacros(List<CompiledCycleMacro> macros) {
        this.allMacros = List.copyOf(macros);
        if (macros.isEmpty()) {
            this.macroByKey = Map.of();
            this.macrosByOutputRes = Map.of();
            return;
        }
        Map<CompiledCycleMacroKey, CompiledCycleMacro> byKey = new HashMap<>(macros.size() * 2);
        Map<Integer, List<CompiledCycleMacro>> byOut = new HashMap<>();
        for (CompiledCycleMacro m : macros) {
            byKey.put(m.macroKey(), m);
            for (var e : m.netDeltaPerIter().entrySet()) {
                if (e.getValue() > 0L) {
                    int resId = dict.idOf(e.getKey());
                    if (resId >= 0) {
                        byOut.computeIfAbsent(resId, ignored -> new ArrayList<>(2)).add(m);
                    }
                }
            }
        }
        Map<Integer, List<CompiledCycleMacro>> frozenByOut = new HashMap<>(byOut.size() * 2);
        for (var e : byOut.entrySet()) {
            frozenByOut.put(e.getKey(), List.copyOf(e.getValue()));
        }
        this.macroByKey = Map.copyOf(byKey);
        this.macrosByOutputRes = frozenByOut;
    }

    IndexedRecipeGraph withUpdatedStats(GraphCompilationStats updatedStats) {
        this.stats = updatedStats;
        return this;
    }

    public ResourceDictionary resourceDict() {
        return dict;
    }

    public CandidateTable candidateTable() {
        return candidates;
    }

    public IterativeTarjanScc sccIndex() {
        return sccIndex;
    }

    public int recipeCount() {
        return recipeCount;
    }

    public int resourceCount() {
        return dict.size();
    }

    public CompiledRecipeKey recipeKeyOf(int r) {
        return recipeKeys[r];
    }

    public RecipeRole roleOf(int r) {
        return roleTable[roles[r]];
    }

    public RecipeAdvisory advisoryOf(int r) {
        return advisoryTable[advisories[r]];
    }

    public boolean isHandCraftable(int r) {
        return handCraftable[r];
    }

    public MachineCapabilityId capabilityOf(int r) {
        return capabilities[r];
    }

    public List<String> compatMachinesOf(int r) {
        return compatMachines[r];
    }

    public Map<String, Double> recipeValuesOf(int r) {
        return recipeValues[r];
    }

    public Set<String> systemTagsOf(int r) {
        return systemTags[r];
    }

    public Set<CanonicalRecipeTag> canonicalTagsOf(int r) {
        return canonicalTags[r];
    }

    public RecipeEvidenceTier evidenceTierOf(int r) {
        return evidenceTiers[r];
    }

    public double structuralFidelityOf(int r) {
        return structuralFidelities[r];
    }

    public Optional<RecipeSemanticKey> semanticKeyOf(int r) {
        return Optional.ofNullable(semanticKeys[r]);
    }

    public int inStart(int r) {
        return inStart[r];
    }

    public int inEnd(int r) {
        return inStart[r + 1];
    }

    public int inSlotIndex(int idx) {
        return inSlotIndex[idx];
    }

    public int inCandList(int idx) {
        return inCandList[idx];
    }

    public long inAmount(int idx) {
        return inAmount[idx];
    }

    public SlotMatchMode inMatchMode(int idx) {
        return matchModes[inMatchMode[idx]];
    }

    public String inTagId(int idx) {
        return inTagId[idx];
    }

    public int catStart(int r) {
        return catStart[r];
    }

    public int catEnd(int r) {
        return catStart[r + 1];
    }

    public int catSlotIndex(int idx) {
        return catSlotIndex[idx];
    }

    public int catCandList(int idx) {
        return catCandList[idx];
    }

    public int catDurCost(int idx) {
        return catDurCost[idx];
    }

    public int catMaxDur(int idx) {
        return catMaxDur[idx];
    }

    public int outStart(int r) {
        return outStart[r];
    }

    public int outEnd(int r) {
        return outStart[r + 1];
    }

    public int outRes(int idx) {
        return outRes[idx];
    }

    public long outAmount(int idx) {
        return outAmount[idx];
    }

    public int prodStart(int resId) {
        return prodStart[resId];
    }

    public int prodEnd(int resId) {
        return prodStart[resId + 1];
    }

    public int prodRecipe(int idx) {
        return prodRecipe[idx];
    }

    public int producerCount(int resId) {
        return prodStart[resId + 1] - prodStart[resId];
    }

    public int consStart(int resId) {
        return consStart[resId];
    }

    public int consEnd(int resId) {
        return consStart[resId + 1];
    }

    public int consRecipe(int idx) {
        return consRecipe[idx];
    }

    public int exactConsumerCount(int resId) {
        return exactConsumerCount[resId];
    }

    public List<CompiledCycleMacro> macrosProducingRes(int resId) {
        return macrosByOutputRes.getOrDefault(resId, List.of());
    }

    public CompiledRecipe materializeRecipe(int r) {
        CompiledRecipe cached = materializedCache[r];
        if (cached != null) {
            return cached;
        }
        int is = inStart[r], ie = inStart[r + 1];
        List<InputSlot> inputs = new ArrayList<>(ie - is);
        for (int i = is; i < ie; i++) {
            inputs.add(
                new InputSlot(
                    inSlotIndex[i],
                    inAmount[i],
                    Optional.ofNullable(inTagId[i]),
                    candidates.viewOf(inCandList[i]),
                    matchModes[inMatchMode[i]]
                )
            );
        }
        int cs = catStart[r], ce = catStart[r + 1];
        List<CatalystSlot> cats = new ArrayList<>(ce - cs);
        for (int i = cs; i < ce; i++) {
            cats.add(new CatalystSlot(catSlotIndex[i], candidates.viewOf(catCandList[i]), catDurCost[i], catMaxDur[i]));
        }
        int os = outStart[r], oe = outStart[r + 1];
        List<OutputSlot> outs = new ArrayList<>(oe - os);
        for (int i = os; i < oe; i++) {
            outs.add(new OutputSlot(dict.keyOf(outRes[i]), outAmount[i]));
        }
        MachineRequirement req = new MachineRequirement(
            Optional.ofNullable(capabilities[r]),
            compatMachines[r],
            handCraftable[r]
        );
        CompiledRecipe built = new CompiledRecipe(
            r,
            recipeKeys[r],
            roleTable[roles[r]],
            inputs,
            cats,
            outs,
            req,
            recipeValues[r],
            advisoryTable[advisories[r]],
            systemTags[r],
            canonicalTags[r],
            evidenceTiers[r],
            structuralFidelities[r],
            Optional.ofNullable(semanticKeys[r])
        );
        materializedCache[r] = built;
        return built;
    }

    @Override
    public long catalogVersion() {
        return catalogVersion;
    }

    @Override
    public SystemAdmission admissionOf(String systemId) {
        SystemAdmission cached = admissionCache.get(systemId);
        if (cached != null) {
            return cached;
        }
        return policy.classify(systemId, "");
    }

    @Override
    public List<CompiledRecipe> producersOf(ResourceKey output) {
        int resId = dict.idOf(output);
        if (resId < 0) {
            return List.of();
        }
        int s = prodStart[resId], e = prodStart[resId + 1];
        if (s == e) {
            return List.of();
        }
        List<CompiledRecipe> out = new ArrayList<>(e - s);
        for (int i = s; i < e; i++) {
            out.add(materializeRecipe(prodRecipe[i]));
        }
        return List.copyOf(out);
    }

    @Override
    public List<CompiledRecipe> consumersOf(ResourceKey input) {
        int resId = dict.idOf(input);
        if (resId < 0) {
            return List.of();
        }
        int s = consStart[resId], e = consStart[resId + 1];
        if (s == e) {
            return List.of();
        }
        List<CompiledRecipe> out = new ArrayList<>(e - s);
        for (int i = s; i < e; i++) {
            out.add(materializeRecipe(consRecipe[i]));
        }
        return List.copyOf(out);
    }

    @Override
    public List<CompiledCycleMacro> cycleMacrosProducing(ResourceKey output) {
        int resId = dict.idOf(output);
        if (resId < 0) {
            return List.of();
        }
        return macrosByOutputRes.getOrDefault(resId, List.of());
    }

    @Override
    public Optional<CompiledRecipe> recipeByCompactId(int compactId) {
        if (compactId < 0 || compactId >= recipeCount) {
            return Optional.empty();
        }
        return Optional.of(materializeRecipe(compactId));
    }

    @Override
    public Optional<CompiledRecipe> recipeByKey(CompiledRecipeKey key) {
        Integer id = compactIdByKey.get(key);
        return id == null ? Optional.empty() : Optional.of(materializeRecipe(id));
    }

    @Override
    public Set<ResourceKey> allProducibleResources() {
        return producibleResources;
    }

    @Override
    public SccTopology sccTopology() {
        return sccIndex;
    }

    @Override
    public GraphCompilationStats stats() {
        return stats;
    }

    @Override
    public long sourceRefOf(int compactId) {
        return (compactId >= 0 && compactId < recipeCount) ? sourceRefs[compactId] : 0L;
    }

    @Override
    public Optional<CompiledCycleMacro> cycleMacroByKey(CompiledCycleMacroKey key) {
        return Optional.ofNullable(macroByKey.get(key));
    }

    @Override
    public List<CompiledCycleMacro> allCycleMacros() {
        return allMacros;
    }

    @Override
    public Set<ResourceKey> unresolvedOutputs() {
        return unresolvedOutputs;
    }
}
