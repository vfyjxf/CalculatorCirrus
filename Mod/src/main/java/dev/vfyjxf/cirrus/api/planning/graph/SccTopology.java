package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;

import java.util.Set;

public interface SccTopology {

    int sccIdOf(ComponentStackKey resource);

    int sccSize(int sccId);

    boolean isInNonTrivialScc(ComponentStackKey resource);

    Set<ComponentStackKey> membersOfScc(int sccId);

    int largestSccSize();
}
