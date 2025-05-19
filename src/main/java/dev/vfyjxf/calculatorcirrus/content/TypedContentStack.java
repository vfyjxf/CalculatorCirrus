package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.MatchContext;

public class TypedContentStack<T> extends BasicContentStack<T> {
    private final ContentType<T> type;
    private long amount;

    public TypedContentStack(ContentType<T> type, T value) {
        this(type, value, 1);
    }

    public TypedContentStack(ContentType<T> type, T value, long amount) {
        super(value);
        this.type = type;
        this.amount = amount;
        //TODO:collect content richTooltip.
    }

    @Override
    public ContentType<T> contentType() {
        return type;
    }

    @Override
    public long getAmount() {
        return amount;
    }

    @Override
    public ContentStack<T> setAmount(long amount) {
        this.amount = amount;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypedContentStack<?> that = (TypedContentStack<?>) o;
        return (amount == that.amount) && type.equals(that.type) && type.matches(value, (T) that.value, MatchContext.EXACT);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + Long.hashCode(amount);
        result = 31 * result + type.hash(value, MatchContext.EXACT);
        return result;
    }

    @Override
    public String toString() {
        return "TypedContentStack{" +
                "type=" + type +
                ", amount=" + amount +
                ", value=" + type.getAsString(value) +
                '}';
    }
}
