package dev.vfyjxf.cirrus.ui.calc.state;

import dev.vfyjxf.cloudlib.api.util.Namespace;

public record TagChoice(
    Namespace tagId,
    String optionId,
    TagChoiceScope scope
) {
    public TagChoice withScope(TagChoiceScope newScope) {
        return new TagChoice(tagId, optionId, newScope);
    }
}
