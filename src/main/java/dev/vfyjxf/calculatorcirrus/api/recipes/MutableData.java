package dev.vfyjxf.calculatorcirrus.api.recipes;

/**
 * Data such as time, energy, etc. that are affected by "upgrades"/"plugins".
 */
public interface MutableData<T> {

    T get();

    MutableData<T> set(T value);

    record Key<T>(String key) {
        public static <T> Key<T> create(String key) {
            return new Key<>(key);
        }
    }

}
