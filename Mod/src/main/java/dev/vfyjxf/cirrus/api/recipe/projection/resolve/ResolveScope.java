package dev.vfyjxf.cirrus.api.recipe.projection.resolve;

public enum ResolveScope {
    input, output, both;

    //region scope

    public boolean allowInput() {
        return this == input || this == both;
    }

    public boolean allowOutput() {
        return this == output || this == both;
    }

    //endregion
}
