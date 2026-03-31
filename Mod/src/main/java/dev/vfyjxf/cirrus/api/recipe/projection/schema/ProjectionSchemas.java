package dev.vfyjxf.cirrus.api.recipe.projection.schema;

import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.Namespace;

public final class ProjectionSchemas {

    public static final ProjectionSchemaKey<GridSize> gridSize = key("grid_size", GridSize.class);
    public static final ProjectionSchemaKey<GridIndex> gridIndex = key("grid_index", GridIndex.class);

    private ProjectionSchemas() {
    }

    private static <T> ProjectionSchemaKey<T> key(String path, Class<T> valueType) {
        Namespace id = CirrusNamespaces.ofMod("projection/schema/" + path);
        return ProjectionSchemaKey.create(id, valueType);
    }
}
