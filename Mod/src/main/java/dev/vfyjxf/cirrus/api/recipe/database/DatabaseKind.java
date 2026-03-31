package dev.vfyjxf.cirrus.api.recipe.database;

import com.mojang.serialization.Codec;

public enum DatabaseKind {
    plan,
    profile,
    custom,
    system,
    jei;

    public static final Codec<DatabaseKind> CODEC = dev.vfyjxf.cloudlib.api.util.Codecs.lowerCaseEnum(DatabaseKind.class);

    public int defaultRank() {
        return switch (this) {
            case plan -> 0;
            case profile -> 1;
            case custom -> 2;
            case system -> 3;
            case jei -> 4;
        };
    }
}
