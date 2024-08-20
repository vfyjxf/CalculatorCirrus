package dev.vfyjxf.calculatorcirrus.api.event;


public interface EventHandler<T> {

    EventChannel<T> channel();

    default EventContext.Common common() {
        return channel().context();
    }

    default EventContext.Cancelable cancelable() {
        return channel().cancelable();
    }

    default EventContext.Interruptible interruptible() {
        return channel().interruptible();
    }

    default <E extends T> E listeners(EventDefinition<E> definition) {
        return channel().get(definition).invoker();
    }

    default <E extends T> E register(EventDefinition<E> definition, E listener) {
        return channel().get(definition).register(listener);
    }

    default <E extends T> void unregister(EventDefinition<E> definition, E listener) {
        channel().get(definition).unregister(listener);
    }

    default <E extends T> void clear(EventDefinition<E> definition) {
        channel().get(definition).clearListeners();
    }

    default void clearAll() {
        channel().clearAllListeners();
    }

}
