package dev.vfyjxf.cirrus.api.recipe.projection.schema;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

public final class ProjectionSchemaKey<T> {

    public static <T> ProjectionSchemaKey<T> create(Namespace id, Class<T> valueType) {
        return new ProjectionSchemaKey<>(id, valueType);
    }

    private final Namespace id;
    private final Class<T> valueType;

    private ProjectionSchemaKey(Namespace id, Class<T> valueType) {
        this.id = Checks.checkNotNull(id, "id");
        this.valueType = Checks.checkNotNull(valueType, "valueType");
    }

    public Namespace id() {
        return id;
    }

    public Class<T> valueType() {
        return valueType;
    }

    public T cast(Object value) {
        Checks.checkNotNull(value, "value");
        Checks.checkArgument(valueType.isInstance(value), "Schema value type mismatch for " + id);
        return valueType.cast(value);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof ProjectionSchemaKey<?> that)) return false;
        return id.equals(that.id) && valueType.equals(that.valueType);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + valueType.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ProjectionSchemaKey{" +
               "id=" + id +
               ", valueType=" + valueType +
               '}';
    }
}
