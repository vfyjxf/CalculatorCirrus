package dev.vfyjxf.cirrus.api.recipe.database;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

import java.util.List;

public record DatabaseDefinition(
    String id,
    String name,
    DatabaseKind kind,
    boolean enabled,
    int priority,
    List<Namespace> sources
) {
    public static final Codec<DatabaseDefinition> codec = RecordCodecBuilder.create(
        instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(DatabaseDefinition::id),
            Codec.STRING.fieldOf("name").forGetter(DatabaseDefinition::name),
            DatabaseKind.codec.fieldOf("kind").forGetter(DatabaseDefinition::kind),
            Codec.BOOL.fieldOf("enabled").forGetter(DatabaseDefinition::enabled),
            Codec.INT.fieldOf("priority").forGetter(DatabaseDefinition::priority),
            Namespace.codec.listOf().fieldOf("sources").forGetter(DatabaseDefinition::sources)
        ).apply(instance, DatabaseDefinition::new)
    );

    public DatabaseDefinition {
        Checks.checkNotNull(id, "id");
        Checks.checkNotNull(name, "name");
        Checks.checkNotNull(kind, "kind");
        Checks.checkNotNull(sources, "sources");
        sources = List.copyOf(sources);
    }

    public DatabaseDefinition withPriority(int nextPriority) {
        return new DatabaseDefinition(id, name, kind, enabled, nextPriority, sources);
    }

    public DatabaseDefinition withEnabled(boolean nextEnabled) {
        return new DatabaseDefinition(id, name, kind, nextEnabled, priority, sources);
    }
}
