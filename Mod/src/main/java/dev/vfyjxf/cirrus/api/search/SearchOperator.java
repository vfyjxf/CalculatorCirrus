package dev.vfyjxf.cirrus.api.search;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cloudlib.api.util.Codecs;

public enum SearchOperator {
    contains, exact, wildcard, range, greaterThan, greaterThanOrEqual, lessThan, lessThanOrEqual, exists;

    public static final Codec<SearchOperator> codec = Codecs.lowerCaseEnum(SearchOperator.class);
}
