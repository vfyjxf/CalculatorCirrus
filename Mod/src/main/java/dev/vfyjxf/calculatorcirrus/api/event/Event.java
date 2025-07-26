package dev.vfyjxf.calculatorcirrus.api.event;

public interface Event<T> {

    T invoker();

    T register(T listener);

    void unregister(T listener);

    boolean isRegistered(T listener);

    void clearListeners();

}
