package dev.vfyjxf.calculatorcirrus.recipes;

import dev.vfyjxf.calculatorcirrus.Constants;
import dev.vfyjxf.calculatorcirrus.api.data.lang.LangBuilder;
import dev.vfyjxf.calculatorcirrus.api.data.lang.LangEntry;
import dev.vfyjxf.calculatorcirrus.api.data.lang.LangProvider;

@LangProvider
public class RecipeLangs {

    private static final LangBuilder builder = LangBuilder.create(Constants.MOD_ID, "ui.data.recipes");

    public static final LangEntry defaultDatabaseName = builder.define("default", "Default Database");
}
