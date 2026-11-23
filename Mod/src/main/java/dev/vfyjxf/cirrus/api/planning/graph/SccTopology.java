package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.Set;

public interface SccTopology {

    int sccIdOf(ResourceKey resource);

    int sccSize(int sccId);

    boolean isInNonTrivialScc(ResourceKey resource);

    Set<ResourceKey> membersOfScc(int sccId);

    int largestSccSize();
}
