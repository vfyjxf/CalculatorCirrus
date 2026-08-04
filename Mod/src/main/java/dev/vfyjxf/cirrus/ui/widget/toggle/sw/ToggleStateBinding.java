package dev.vfyjxf.cirrus.ui.widget.toggle.sw;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface ToggleStateBinding {

    /**
     * Returns the current toggle side.
     * `false` means the left side is selected.
     * `true` means the right side is selected.
     */
    boolean getAsBoolean();

    /**
     * Updates the current toggle side.
     * `false` selects the left side.
     * `true` selects the right side.
     */
    void set(boolean value);

    default void toggle() {
        set(!getAsBoolean());
    }

    static ToggleStateBinding of(BooleanSupplier getter, Consumer<Boolean> setter) {
        return new ToggleStateBinding() {
            @Override
            public boolean getAsBoolean() {
                return getter.getAsBoolean();
            }

            @Override
            public void set(boolean value) {
                setter.accept(value);
            }
        };
    }
}
