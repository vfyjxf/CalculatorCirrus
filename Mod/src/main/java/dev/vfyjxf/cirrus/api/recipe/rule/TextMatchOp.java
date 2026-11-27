package dev.vfyjxf.cirrus.api.recipe.rule;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cloudlib.api.util.Codecs;

/**
 * String-matching semantics for id-style condition leaves
 * ({@code system}, {@code recipe_id}, {@code recipe_mod}, {@code machine}).
 *
 * <p>Ordering from most to least preferred: structural {@code in}/{@code
 * prefix}/{@code suffix} matchers cover catalog conventions; {@code contains}
 * and {@code glob} handle naming families; {@code regex} is an explicit
 * last-resort escape hatch for genuinely irregular ids and should carry a
 * comment in the rule file explaining why nothing narrower works.
 *
 * <p>Match semantics:
 * <ul>
 *     <li>{@code in} — the candidate equals one of the values (case-sensitive).</li>
 *     <li>{@code prefix}/{@code suffix} — the candidate starts/ends with one
 *         of the values.</li>
 *     <li>{@code contains} — one of the values occurs anywhere in the
 *         candidate.</li>
 *     <li>{@code glob} — full-match with {@code *} (any run) and {@code ?}
 *         (one char) wildcards.</li>
 *     <li>{@code regex} — Java regex, <b>substring</b> semantics
 *         ({@code find}), matching the historical pattern-rule behavior;
 *         anchor explicitly with {@code ^...$} when a full match is intended.</li>
 * </ul>
 */
public enum TextMatchOp {
    in, prefix, suffix, contains, glob, regex;

    public static final Codec<TextMatchOp> codec = Codecs.lowerCaseEnum(TextMatchOp.class);
}
