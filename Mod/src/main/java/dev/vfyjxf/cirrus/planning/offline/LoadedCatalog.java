package dev.vfyjxf.cirrus.planning.offline;

import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeCatalogSnapshot;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmissionPolicy;

import java.util.Objects;

public record LoadedCatalog(RawRecipeCatalogSnapshot snapshot, SystemAdmissionPolicy policy, ExportLoadStats stats) {
    public LoadedCatalog {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(stats, "stats");
    }
}
