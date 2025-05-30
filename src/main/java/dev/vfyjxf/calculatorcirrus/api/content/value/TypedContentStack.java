package dev.vfyjxf.calculatorcirrus.api.content.value;

import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackContentType;

class TypedContentStack<T> extends BasicContentStack<T> {
    private final StackContentType<T> type;
    private long amount;

    public TypedContentStack(StackContentType<T> type, T value) {
        this(type, value, 1);
    }

    public TypedContentStack(StackContentType<T> type, T value, long amount) {
        super(value);
        this.type = type;
        this.amount = amount;
        //TODO:collect content richTooltip.
    }

    @Override
    public StackContentType<T> contentType() {
        return type;
    }

    @Override
    public long amount() {
        return amount;
    }

    @Override
    public ContentStack<T> setAmount(long amount) {
        this.amount = amount;
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypedContentStack<?> that = (TypedContentStack<?>) o;
        return (amount == that.amount) &&
                type.equals(that.type) &&
                type.matches(value, (T) that.value, MatchStrategy.IDENTITY_COMPONENT);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + Long.hashCode(amount);
        result = 31 * result + type.hash(value, MatchStrategy.IDENTITY_COMPONENT);
        return result;
    }

    @Override
    public String toString() {
        return "TypedContentStack{" +
                "type=" + type +
                ", amount=" + amount +
                ", value=" + type.asString(value) +
                '}';
    }
}
