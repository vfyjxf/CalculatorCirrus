package dev.vfyjxf.cirrus;

import java.util.List;

public class Type {

    void foo(List<?> list) {
        record Ty<T>(T value) {}
        var ty = new Ty<>(list.getFirst());
    }

}
