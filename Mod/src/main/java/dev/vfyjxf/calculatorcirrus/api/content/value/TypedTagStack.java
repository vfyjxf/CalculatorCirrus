//package dev.vfyjxf.calculatorcirrus.api.content.value;
//
//import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
//import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
//import dev.vfyjxf.cloudlib.api.ui.text.RichTooltip;
//import dev.vfyjxf.cloudlib.api.ui.widget.TooltipContext;
//import net.minecraft.tags.TagKey;
//import net.neoforged.fml.loading.FMLEnvironment;
//import org.eclipse.collections.api.factory.Lists;
//import org.eclipse.collections.api.list.MutableList;
//import org.jetbrains.annotations.Nullable;
//import org.jetbrains.annotations.UnmodifiableView;
//
//import java.util.function.BiConsumer;
//
//final class TypedTagStack<T> implements TagStack<T> {
//
//    private final TagType<T> type;
//    private final TagKey<T> tagKey;
//    private final MutableList<T> values;
//    private final boolean valid;
//    private final MutableList<BiConsumer<RichTooltip, TooltipContext>> tooltipConfigurers = Lists.mutable.empty();
//    private long amount;
//
//    TypedTagStack(TagType<T> type, TagKey<T> tagKey, long amount) {
//        this.type = type;
//        this.tagKey = tagKey;
//        this.amount = amount;
//        this.values = type.getTagValues(tagKey).toList();
//        this.valid = values.anySatisfy(value -> !type.contentType().isEmpty(value));
//    }
//
//    @Override
//    public TagType<T> type() {
//        return type;
//    }
//
//    @Override
//    public TagKey<T> tagKey() {
//        return tagKey;
//    }
//
//    @Override
//    public boolean isEmpty() {
//        return !valid && amount > 0;
//    }
//
//    @Override
//    public @UnmodifiableView MutableList<T> values() {
//        return values.asUnmodifiable();
//    }
//
//    @Override
//    public long amount() {
//        return amount;
//    }
//
//    @Override
//    public TagStack<T> setAmount(long amount) {
//        this.amount = amount;
//        return this;
//    }
//
//    @Override
//    public <S> MutableList<S> toStacks(StackType<T, S> stackType) {
//        return values.collect(content -> stackType.fromContent(content, stackType.quantity()))
//                .toList();
//    }
//
//    @Nullable
//    @Override
//    public RichTooltip getTooltip(TooltipContext context) {
//        if (FMLEnvironment.dist.isDedicatedServer()) return null;
//        RichTooltip richTooltip = RichTooltip.create();
//        this.configureTooltip(richTooltip, context);
//        return richTooltip;
//    }
//
//    @Override
//    public void configureTooltip(RichTooltip tooltip, TooltipContext context) {
//        for (var configurer : tooltipConfigurers) {
//            configurer.accept(tooltip, context);
//        }
//    }
//
//    @Override
//    public TagStack<T> appendTooltip(BiConsumer<RichTooltip, TooltipContext> configurator) {
//        if (FMLEnvironment.dist.isDedicatedServer()) return this;
//        this.tooltipConfigurers.add(configurator);
//        return this;
//    }
//
//    @Override
//    public boolean equals(Object object) {
//        if (object == null || getClass() != object.getClass()) return false;
//
//        TypedTagStack<?> that = (TypedTagStack<?>) object;
//        return amount == that.amount &&
//                type.equals(that.type) &&
//                tagKey.equals(that.tagKey);
//    }
//
//    @Override
//    public int hashCode() {
//        int result = type.hashCode();
//        result = 31 * result + tagKey.hashCode();
//        result = 31 * result + Long.hashCode(amount);
//        return result;
//    }
//
//    @Override
//    public String toString() {
//        return "TypedTagStack{" +
//                "type=" + type +
//                ", tagKey=" + tagKey +
//                ", amount=" + amount +
//                ", valid=" + valid +
//                '}';
//    }
//}
//
//final class EmptyTagStack<T> implements TagStack<T> {
//
//    @SuppressWarnings("rawtypes")
//    static final TagStack instance = new EmptyTagStack<>();
//
//    @Override
//    public TagType<T> type() {
//        throw new UnsupportedOperationException("This operation is not supported for an empty tag stack.");
//    }
//
//    @Override
//    public TagKey<T> tagKey() {
//        throw new UnsupportedOperationException("This operation is not supported for an empty tag stack.");
//    }
//
//    @Override
//    public boolean isEmpty() {
//        return true;
//    }
//
//    @Override
//    public @UnmodifiableView MutableList<T> values() {
//        throw new UnsupportedOperationException("This operation is not supported for an empty tag stack.");
//    }
//
//    @Override
//    public long amount() {
//        return 0;
//    }
//
//    @Override
//    public TagStack<T> setAmount(long amount) {
//        throw new UnsupportedOperationException("This operation is not supported for an empty tag stack.");
//    }
//
//    @Override
//    public TagStack<T> appendTooltip(BiConsumer<RichTooltip, TooltipContext> configurator) {
//        throw new UnsupportedOperationException("This operation is not supported for an empty tag stack.");
//    }
//
//    @Override
//    public void configureTooltip(RichTooltip tooltip, TooltipContext context) {
//        throw new UnsupportedOperationException("This operation is not supported for an empty tag stack.");
//    }
//
//    @Override
//    public @Nullable RichTooltip getTooltip(TooltipContext context) {
//        return null;
//    }
//
//    @Override
//    public <S> MutableList<S> toStacks(StackType<T, S> stackType) {
//        throw new UnsupportedOperationException("This operation is not supported for an empty tag stack.");
//    }
//
//    @Override
//    public String toString() {
//        return "empty tag stack";
//    }
//}