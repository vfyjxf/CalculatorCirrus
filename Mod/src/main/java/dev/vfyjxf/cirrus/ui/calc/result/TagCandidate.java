package dev.vfyjxf.cirrus.ui.calc.result;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;

public record TagCandidate(
    String optionId,
    String name,
    ContentStack<?, ?> stack
) {
}
