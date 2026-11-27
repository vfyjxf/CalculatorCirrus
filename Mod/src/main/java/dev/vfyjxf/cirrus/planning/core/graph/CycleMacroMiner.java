package dev.vfyjxf.cirrus.planning.core.graph;

import dev.vfyjxf.cirrus.api.planning.graph.CompiledCycleMacro;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeAdvisory;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeRole;
import dev.vfyjxf.cirrus.api.planning.model.CompiledCycleMacroKey;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.planning.core.lang.Fnv1a64;
import dev.vfyjxf.cirrus.planning.core.lang.SafeMath;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class CycleMacroMiner {

    private static final int maxLen2Mult = 8;
    private static final int maxLongerDegree = 64;
    private static final int maxLongerExpansions = 120_000;
    private static final int maxMacros = 16_384;

    private CycleMacroMiner() {}

    static List<CompiledCycleMacro> mine(
        IndexedRecipeGraph graph,
        Map<Integer, Map<Integer, Long>> selfAmplifyingSeedsByRecipe
    ) {
        ResourceDictionary dict = graph.resourceDict();
        CandidateTable cands = graph.candidateTable();
        IterativeTarjanScc scc = graph.sccIndex();

        Map<CompiledCycleMacroKey, CompiledCycleMacro> mined = new TreeMap<>();

        // 1. Length-1 self-amplifying loops captured during catalyst promotion.
        for (var entry : selfAmplifyingSeedsByRecipe.entrySet()) {
            int r = entry.getKey();
            if (!isEligibleForMacro(graph, r)) {
                continue;
            }
            CompiledCycleMacro macro = buildMacro(graph, new int[]{r}, new long[]{1L}, entry.getValue());
            if (macro != null && hasPositiveNetOutput(macro)) {
                mined.putIfAbsent(macro.macroKey(), macro);
            }
        }

        // 2. Build clean single-candidate directed edges inside non-trivial SCCs.
        int nRecipes = graph.recipeCount();
        int nRes = dict.size();
        Map<Long, List<EdgeInfo>> edgesByPair = new HashMap<>();
        List<List<EdgeInfo>> outEdges = new ArrayList<>(nRes);
        for (int i = 0; i < nRes; i++) {
            outEdges.add(null);
        }

        for (int r = 0; r < nRecipes; r++) {
            if (!isEligibleForMacro(graph, r) || !isSingleCandidateRecipe(graph, cands, r)) {
                continue;
            }
            int inS = graph.inStart(r);
            int inE = graph.inEnd(r);
            int outS = graph.outStart(r);
            int outE = graph.outEnd(r);
            for (int si = inS; si < inE; si++) {
                int inRes = cands.membersOf(graph.inCandList(si))[0];
                int inScc = scc.sccIdOf(inRes);
                if (inScc < 0 || scc.sccSize(inScc) <= 1) {
                    continue;
                }
                long inAmt = graph.inAmount(si);
                for (int oi = outS; oi < outE; oi++) {
                    int outRes = graph.outRes(oi);
                    if (outRes == inRes || scc.sccIdOf(outRes) != inScc) {
                        continue;
                    }
                    long outAmt = graph.outAmount(oi);
                    EdgeInfo ei = new EdgeInfo(r, inRes, inAmt, outRes, outAmt);
                    long pairKey = (((long) inRes) << 32) | (outRes & 0xffffffffL);
                    edgesByPair.computeIfAbsent(pairKey, ignored -> new ArrayList<>(2)).add(ei);
                    List<EdgeInfo> list = outEdges.get(inRes);
                    if (list == null) {
                        list = new ArrayList<>(4);
                        outEdges.set(inRes, list);
                    }
                    list.add(ei);
                }
            }
        }

        // 3. Length-2 regenerative catalyst cycles.
        Set<Long> seenRecipePairs = new HashSet<>();
        for (int a = 0; a < nRes && mined.size() < maxMacros; a++) {
            List<EdgeInfo> fwdList = outEdges.get(a);
            if (fwdList == null) {
                continue;
            }
            for (EdgeInfo e1 : fwdList) {
                int b = e1.toRes;
                if (b <= a) {
                    continue;
                }
                long revKey = (((long) b) << 32) | (a & 0xffffffffL);
                List<EdgeInfo> bwdList = edgesByPair.get(revKey);
                if (bwdList == null) {
                    continue;
                }
                for (EdgeInfo e2 : bwdList) {
                    if (e1.recipe == e2.recipe) {
                        continue;
                    }
                    long rpKey = (((long) Math.min(e1.recipe, e2.recipe)) << 32)
                            | (Math.max(e1.recipe, e2.recipe) & 0xffffffffL);
                    if (!seenRecipePairs.add(rpKey)) {
                        continue;
                    }
                    long[] mults = findMinimalLen2Multipliers(e1.inAmt, e1.outAmt, e2.inAmt, e2.outAmt);
                    if (mults == null) {
                        continue;
                    }
                    CompiledCycleMacro macro = buildMacro(graph, new int[]{e1.recipe, e2.recipe}, mults, null);
                    if (macro != null && hasPositiveExternalOutput(macro, dict.keyOf(a), dict.keyOf(b))) {
                        mined.putIfAbsent(macro.macroKey(), macro);
                        if (mined.size() >= maxMacros) {
                            break;
                        }
                    }
                }
            }
        }

        // 4. Bounded length-3 and length-4 regenerative catalyst cycles on low-degree SCC vertices.
        int expansions = 0;
        for (int a = 0; a < nRes && mined.size() < maxMacros && expansions < maxLongerExpansions; a++) {
            List<EdgeInfo> l1 = outEdges.get(a);
            if (l1 == null || l1.size() > maxLongerDegree) {
                continue;
            }
            for (EdgeInfo e1 : l1) {
                if (expansions >= maxLongerExpansions || mined.size() >= maxMacros) {
                    break;
                }
                int b = e1.toRes;
                if (b <= a) {
                    continue;
                }
                List<EdgeInfo> l2 = outEdges.get(b);
                if (l2 == null || l2.size() > maxLongerDegree) {
                    continue;
                }
                for (EdgeInfo e2 : l2) {
                    expansions++;
                    if (expansions >= maxLongerExpansions || mined.size() >= maxMacros) {
                        break;
                    }
                    int c = e2.toRes;
                    if (c <= a || c == b || e2.recipe == e1.recipe) {
                        continue;
                    }
                    // Try closing length 3: c -> a.
                    List<EdgeInfo> close3 = edgesByPair.get((((long) c) << 32) | (a & 0xffffffffL));
                    if (close3 != null) {
                        for (EdgeInfo e3 : close3) {
                            if (e3.recipe == e1.recipe || e3.recipe == e2.recipe) {
                                continue;
                            }
                            if (e1.outAmt >= e2.inAmt && e2.outAmt >= e3.inAmt && e3.outAmt >= e1.inAmt) {
                                CompiledCycleMacro macro = buildMacro(
                                    graph,
                                    new int[]{e1.recipe, e2.recipe, e3.recipe},
                                    new long[]{1L, 1L, 1L},
                                    null
                                );
                                if (macro != null
                                        && hasPositiveExternalOutput(
                                            macro,
                                            dict.keyOf(a),
                                            dict.keyOf(b),
                                            dict.keyOf(c)
                                        )) {
                                    mined.putIfAbsent(macro.macroKey(), macro);
                                }
                            }
                        }
                    }
                    // Try closing length 4: c -> d -> a.
                    List<EdgeInfo> l3 = outEdges.get(c);
                    if (l3 == null || l3.size() > maxLongerDegree) {
                        continue;
                    }
                    for (EdgeInfo e3 : l3) {
                        expansions++;
                        if (expansions >= maxLongerExpansions || mined.size() >= maxMacros) {
                            break;
                        }
                        int d = e3.toRes;
                        if (d <= a || d == b || d == c || e3.recipe == e1.recipe || e3.recipe == e2.recipe) {
                            continue;
                        }
                        List<EdgeInfo> close4 = edgesByPair.get((((long) d) << 32) | (a & 0xffffffffL));
                        if (close4 != null) {
                            for (EdgeInfo e4 : close4) {
                                if (e4.recipe == e1.recipe || e4.recipe == e2.recipe || e4.recipe == e3.recipe) {
                                    continue;
                                }
                                if (e1.outAmt >= e2.inAmt
                                        && e2.outAmt >= e3.inAmt
                                        && e3.outAmt >= e4.inAmt
                                        && e4.outAmt >= e1.inAmt) {
                                    CompiledCycleMacro macro = buildMacro(
                                        graph,
                                        new int[]{e1.recipe, e2.recipe, e3.recipe, e4.recipe},
                                        new long[]{1L, 1L, 1L, 1L},
                                        null
                                    );
                                    if (macro != null
                                            && hasPositiveExternalOutput(
                                                macro,
                                                dict.keyOf(a),
                                                dict.keyOf(b),
                                                dict.keyOf(c),
                                                dict.keyOf(d)
                                            )) {
                                        mined.putIfAbsent(macro.macroKey(), macro);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        List<CompiledCycleMacro> result = new ArrayList<>(mined.values());
        result.sort(Comparator.comparing(CompiledCycleMacro::macroKey));
        return List.copyOf(result);
    }

    private static boolean isEligibleForMacro(IndexedRecipeGraph graph, int r) {
        RecipeRole role = graph.roleOf(r);
        if (role == RecipeRole.reversiblePacking
                || role == RecipeRole.reversibleRecoloring
                || role == RecipeRole.recycling) {
            return false;
        }
        return graph.advisoryOf(r) != RecipeAdvisory.lastResort;
    }

    private static boolean isSingleCandidateRecipe(IndexedRecipeGraph graph, CandidateTable cands, int r) {
        for (int si = graph.inStart(r); si < graph.inEnd(r); si++) {
            if (cands.sizeOf(graph.inCandList(si)) != 1) {
                return false;
            }
        }
        for (int ci = graph.catStart(r); ci < graph.catEnd(r); ci++) {
            if (cands.sizeOf(graph.catCandList(ci)) != 1) {
                return false;
            }
        }
        return graph.outEnd(r) > graph.outStart(r);
    }

    private static long @Nullable [] findMinimalLen2Multipliers(long a1, long b1, long b2, long a2) {
        for (int sum = 2; sum <= maxLen2Mult * 2; sum++) {
            for (int m1 = 1; m1 <= Math.min(maxLen2Mult, sum - 1); m1++) {
                int m2 = sum - m1;
                if (m2 < 1 || m2 > maxLen2Mult) {
                    continue;
                }
                if (SafeMath.mulSaturating(m1, b1) >= SafeMath.mulSaturating(m2, b2)
                        && SafeMath.mulSaturating(m2, a2) >= SafeMath.mulSaturating(m1, a1)) {
                    return new long[]{m1, m2};
                }
            }
        }
        return null;
    }

    /** Computes exact netDelta and peak intra-iteration draw as workingSeeds. */
    private static @Nullable CompiledCycleMacro buildMacro(
        IndexedRecipeGraph graph,
        int[] recipeIds,
        long[] multipliers,
        @Nullable Map<Integer, Long> explicitMinSeeds
    ) {
        ResourceDictionary dict = graph.resourceDict();
        CandidateTable cands = graph.candidateTable();

        Map<Integer, Long> running = new HashMap<>();
        Map<Integer, Long> peakDraw = new HashMap<>();
        Map<Integer, Long> catalystMin = new HashMap<>();
        List<CompiledRecipe> innerRecipes = new ArrayList<>(recipeIds.length);
        List<Long> innerMults = new ArrayList<>(multipliers.length);

        for (int i = 0; i < recipeIds.length; i++) {
            int r = recipeIds[i];
            long mult = multipliers[i];
            innerRecipes.add(graph.materializeRecipe(r));
            innerMults.add(mult);

            for (int ci = graph.catStart(r); ci < graph.catEnd(r); ci++) {
                int[] members = cands.membersOf(graph.catCandList(ci));
                if (members.length != 1) {
                    return null;
                }
                int catRes = members[0];
                int dur = graph.catDurCost(ci);
                int maxDur = graph.catMaxDur(ci);
                long needed = (dur > 0 && maxDur > 0)
                        ? SafeMath.ceilDiv(SafeMath.mulSaturating(mult, dur), maxDur)
                        : 1L;
                long cur = running.getOrDefault(catRes, 0L);
                long reqAtStart = Math.max(needed, needed - cur);
                catalystMin.merge(catRes, reqAtStart, Math::max);
            }

            for (int si = graph.inStart(r); si < graph.inEnd(r); si++) {
                int[] members = cands.membersOf(graph.inCandList(si));
                if (members.length != 1) {
                    return null;
                }
                int inRes = members[0];
                long consume = SafeMath.mulSaturating(mult, graph.inAmount(si));
                long after = SafeMath.addSaturating(running.getOrDefault(inRes, 0L), -consume);
                running.put(inRes, after);
                if (after < 0L) {
                    peakDraw.merge(inRes, -after, Math::max);
                }
            }

            for (int oi = graph.outStart(r); oi < graph.outEnd(r); oi++) {
                int outRes = graph.outRes(oi);
                long prod = SafeMath.mulSaturating(mult, graph.outAmount(oi));
                long after = SafeMath.addSaturating(running.getOrDefault(outRes, 0L), prod);
                running.put(outRes, after);
            }
        }

        Map<ResourceKey, Long> netDelta = new TreeMap<>();
        Map<ResourceKey, Long> seeds = new TreeMap<>();
        Set<Integer> allTouched = new HashSet<>(running.keySet());
        allTouched.addAll(peakDraw.keySet());
        allTouched.addAll(catalystMin.keySet());
        if (explicitMinSeeds != null) {
            allTouched.addAll(explicitMinSeeds.keySet());
        }

        for (int resId : allTouched) {
            long delta = running.getOrDefault(resId, 0L);
            ResourceKey rk = dict.keyOf(resId);
            if (delta != 0L
                    || peakDraw.containsKey(resId)
                    || catalystMin.containsKey(resId)
                    || (explicitMinSeeds != null && explicitMinSeeds.containsKey(resId))) {
                netDelta.put(rk, delta);
            }
            if (delta >= 0L) {
                long needSeed = Math.max(peakDraw.getOrDefault(resId, 0L), catalystMin.getOrDefault(resId, 0L));
                if (explicitMinSeeds != null) {
                    needSeed = Math.max(needSeed, explicitMinSeeds.getOrDefault(resId, 0L));
                }
                if (needSeed > 0L) {
                    seeds.put(rk, needSeed);
                }
            }
        }

        if (seeds.isEmpty()) {
            return null;
        }

        CompiledCycleMacroKey macroKey = stableMacroKey(innerRecipes, innerMults);
        return new CompiledCycleMacro(macroKey, innerRecipes, innerMults, seeds, netDelta);
    }

    private static CompiledCycleMacroKey stableMacroKey(List<CompiledRecipe> inner, List<Long> mults) {
        long h = Fnv1a64.initial();
        h = Fnv1a64.mixString(h, "MACRO:");
        h = Fnv1a64.mixLong(h, inner.size());
        for (int i = 0; i < inner.size(); i++) {
            CompiledRecipeKey rk = inner.get(i).key();
            h = Fnv1a64.mixByte(h, '|');
            h = Fnv1a64.mixString(h, rk.systemId());
            h = Fnv1a64.mixByte(h, ':');
            h = Fnv1a64.mixString(h, rk.explicitRecipeId().orElse(""));
            h = Fnv1a64.mixByte(h, '#');
            h = Fnv1a64.mixString(h, Long.toHexString(rk.structuralHash64()));
            h = Fnv1a64.mixByte(h, '*');
            h = Fnv1a64.mixLong(h, mults.get(i));
        }
        return new CompiledCycleMacroKey("cycle" + inner.size() + ":" + Long.toHexString(h));
    }

    private static boolean hasPositiveNetOutput(CompiledCycleMacro macro) {
        for (long v : macro.netDeltaPerIter().values()) {
            if (v > 0L) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPositiveExternalOutput(CompiledCycleMacro macro, ResourceKey... cycleCarrierKeys) {
        for (var e : macro.netDeltaPerIter().entrySet()) {
            if (e.getValue() <= 0L) {
                continue;
            }
            boolean isCarrier = false;
            for (ResourceKey carrier : cycleCarrierKeys) {
                if (carrier.equals(e.getKey())) {
                    isCarrier = true;
                    break;
                }
            }
            if (!isCarrier) {
                return true;
            }
        }
        return false;
    }

    private record EdgeInfo(int recipe, int fromRes, long inAmt, int toRes, long outAmt) {}
}
