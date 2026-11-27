package dev.vfyjxf.cirrus.api.recipe.database;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cloudlib.api.util.Codecs;

public enum DatabaseKind {
    plan, profile, custom, system, jei;

    public static final Codec<DatabaseKind> codec = Codecs.lowerCaseEnum(DatabaseKind.class);

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
