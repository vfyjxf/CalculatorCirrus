package dev.vfyjxf.cirrus.api.recipe.rule;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cloudlib.api.util.Codecs;

/**
 * The pipeline stage a rule participates in:
 *
 * <ul>
 *     <li>{@code catalog} — evaluated once per recipe while the routing
 *         catalog is built; actions attach tags, exclude/include recipes, or
 *         set routing hints.</li>
 *     <li>{@code candidate} — evaluated once per planner candidate; actions
 *         adjust score components or reject the candidate.</li>
 * </ul>
 */
public enum RuleStage {
    catalog, candidate;

    public static final Codec<RuleStage> codec = Codecs.lowerCaseEnum(RuleStage.class);
}
