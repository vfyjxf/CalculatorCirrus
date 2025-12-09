package dev.vfyjxf.cirrus.api.register;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface RecipeSystemTagRegister {

    void registerSystemTagEntry(Namespace tag, Namespace systemId);

}
