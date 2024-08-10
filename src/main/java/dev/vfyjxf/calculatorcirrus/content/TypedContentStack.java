package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.ContentType;

public class TypedContentStack<T> extends AbstractContentStack<T> {
    private final ContentType<T> type;
    private long amount = 1;

    public TypedContentStack(ContentType<T> type, T value) {
        this(type, value, 1);
    }

    public TypedContentStack(ContentType<T> type, T value, long amount) {
        super(value);
        this.type = type;
        this.amount = amount;
        //TODO:collect content tooltip.
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
}
