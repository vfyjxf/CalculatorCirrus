package dev.vfyjxf.cirrus.planning.core.graph;

import dev.vfyjxf.cirrus.api.planning.graph.SccTopology;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class IterativeTarjanScc implements SccTopology {

    private final ResourceDictionary dict;
    private final int[] sccIdByRes;
    private final int[] sccSizes;
    private final int largestSize;

    public IterativeTarjanScc(Map<ResourceKey, List<ResourceKey>> adjacency) {
        ResourceDictionary.Builder db = new ResourceDictionary.Builder();
        for (var e : adjacency.entrySet()) {
            db.intern(e.getKey());
            for (ResourceKey target : e.getValue()) {
                db.intern(target);
            }
        }
        ResourceDictionary.Built built = db.freeze();
        this.dict = built.dictionary();
        int n = dict.size();

        int[] counts = new int[n];
        for (var e : adjacency.entrySet()) {
            int u = dict.idOf(e.getKey());
            counts[u] += e.getValue().size();
        }
        int[] start = new int[n + 1];
        for (int i = 0; i < n; i++) {
            start[i + 1] = start[i] + counts[i];
        }
        int[] succ = new int[start[n]];
        int[] cursor = Arrays.copyOf(start, n);
        for (var e : adjacency.entrySet()) {
            int u = dict.idOf(e.getKey());
            for (ResourceKey target : e.getValue()) {
                succ[cursor[u]++] = dict.idOf(target);
            }
        }
        for (int i = 0; i < n; i++) {
            Arrays.sort(succ, start[i], start[i + 1]);
        }

        TarjanResult res = runPrimitive(n, n, start, succ);
        this.sccIdByRes = res.sccIdByRes;
        this.sccSizes = res.sccSizes;
        this.largestSize = res.largestSize;
    }

    private IterativeTarjanScc(ResourceDictionary dict, int[] sccIdByRes, int[] sccSizes, int largestSize) {
        this.dict = dict;
        this.sccIdByRes = sccIdByRes;
        this.sccSizes = sccSizes;
        this.largestSize = largestSize;
    }

    /** Computes resource SCCs on the bipartite (Resources + CandidateLists) graph. */
    static IterativeTarjanScc computeBipartite(
            ResourceDictionary dict,
            CandidateTable candidates,
            int[] listOutStart,
            int[] listOutRes
    ) {
        int rCount = dict.size();
        int lCount = candidates.count();
        int totalV = rCount + lCount;

        int[] deg = new int[totalV];
        for (int lid = 0; lid < lCount; lid++) {
            int[] members = candidates.membersOf(lid);
            for (int u : members) {
                deg[u]++;
            }
            deg[rCount + lid] = listOutStart[lid + 1] - listOutStart[lid];
        }

        int[] start = new int[totalV + 1];
        for (int i = 0; i < totalV; i++) {
            start[i + 1] = start[i] + deg[i];
        }
        int[] succ = new int[start[totalV]];
        int[] cursor = Arrays.copyOf(start, totalV);
        for (int lid = 0; lid < lCount; lid++) {
            int lNode = rCount + lid;
            int[] members = candidates.membersOf(lid);
            for (int u : members) {
                succ[cursor[u]++] = lNode;
            }
            int s = listOutStart[lid];
            int e = listOutStart[lid + 1];
            System.arraycopy(listOutRes, s, succ, cursor[lNode], e - s);
            cursor[lNode] += (e - s);
        }

        TarjanResult res = runPrimitive(rCount, totalV, start, succ);
        return new IterativeTarjanScc(dict, res.sccIdByRes, res.sccSizes, res.largestSize);
    }

    private record TarjanResult(int[] sccIdByRes, int[] sccSizes, int largestSize) {
    }

    private static TarjanResult runPrimitive(int resourceCount, int totalVertices, int[] start, int[] succ) {
        int[] index = new int[totalVertices];
        Arrays.fill(index, -1);
        int[] lowlink = new int[totalVertices];
        boolean[] onStack = new boolean[totalVertices];
        int[] tarjanStack = new int[totalVertices];
        int tarjanTop = 0;

        int[] callNode = new int[totalVertices];
        int[] callEdge = new int[totalVertices];
        int callTop = 0;

        int[] sccIdByRes = new int[resourceCount];
        Arrays.fill(sccIdByRes, -1);
        int[] sccSizesBuf = new int[Math.max(16, resourceCount)];
        int nextScc = 0;
        int nextIndex = 0;
        int largest = 0;

        for (int root = 0; root < totalVertices; root++) {
            if (index[root] != -1) {
                continue;
            }
            index[root] = nextIndex;
            lowlink[root] = nextIndex++;
            tarjanStack[tarjanTop++] = root;
            onStack[root] = true;
            callNode[callTop] = root;
            callEdge[callTop] = start[root];
            callTop++;

            while (callTop > 0) {
                int v = callNode[callTop - 1];
                int edgeIdx = callEdge[callTop - 1];
                int edgeEnd = start[v + 1];
                if (edgeIdx < edgeEnd) {
                    callEdge[callTop - 1] = edgeIdx + 1;
                    int w = succ[edgeIdx];
                    if (index[w] == -1) {
                        index[w] = nextIndex;
                        lowlink[w] = nextIndex++;
                        tarjanStack[tarjanTop++] = w;
                        onStack[w] = true;
                        callNode[callTop] = w;
                        callEdge[callTop] = start[w];
                        callTop++;
                    } else if (onStack[w]) {
                        if (index[w] < lowlink[v]) {
                            lowlink[v] = index[w];
                        }
                    }
                } else {
                    callTop--;
                    if (callTop > 0) {
                        int parent = callNode[callTop - 1];
                        if (lowlink[v] < lowlink[parent]) {
                            lowlink[parent] = lowlink[v];
                        }
                    }
                    if (lowlink[v] == index[v]) {
                        int resCountInScc = 0;
                        int startTop = tarjanTop;
                        while (true) {
                            int w = tarjanStack[--tarjanTop];
                            onStack[w] = false;
                            if (w < resourceCount) {
                                resCountInScc++;
                            }
                            if (w == v) {
                                break;
                            }
                        }
                        if (resCountInScc > 0) {
                            int sccId = nextScc++;
                            if (sccId == sccSizesBuf.length) {
                                sccSizesBuf = Arrays.copyOf(sccSizesBuf, sccSizesBuf.length * 2);
                            }
                            sccSizesBuf[sccId] = resCountInScc;
                            if (resCountInScc > largest) {
                                largest = resCountInScc;
                            }
                            for (int i = tarjanTop; i < startTop; i++) {
                                int w = tarjanStack[i];
                                if (w < resourceCount) {
                                    sccIdByRes[w] = sccId;
                                }
                            }
                        }
                    }
                }
            }
        }

        return new TarjanResult(sccIdByRes, Arrays.copyOf(sccSizesBuf, nextScc), largest);
    }

    public int sccIdOf(int resId) {
        return (resId >= 0 && resId < sccIdByRes.length) ? sccIdByRes[resId] : -1;
    }

    public boolean isInNonTrivialScc(int resId) {
        int id = sccIdOf(resId);
        return id >= 0 && sccSizes[id] > 1;
    }

    @Override
    public int sccIdOf(ResourceKey resource) {
        int resId = dict.idOf(resource);
        return sccIdOf(resId);
    }

    @Override
    public int sccSize(int sccId) {
        return (sccId >= 0 && sccId < sccSizes.length) ? sccSizes[sccId] : 0;
    }

    @Override
    public boolean isInNonTrivialScc(ResourceKey resource) {
        int id = sccIdOf(resource);
        return id >= 0 && sccSizes[id] > 1;
    }

    @Override
    public Set<ResourceKey> membersOfScc(int sccId) {
        if (sccId < 0 || sccId >= sccSizes.length) {
            return Set.of();
        }
        TreeSet<ResourceKey> members = new TreeSet<>();
        for (int i = 0; i < sccIdByRes.length; i++) {
            if (sccIdByRes[i] == sccId) {
                members.add(dict.keyOf(i));
            }
        }
        return Collections.unmodifiableSet(members);
    }

    @Override
    public int largestSccSize() {
        return largestSize;
    }
}
