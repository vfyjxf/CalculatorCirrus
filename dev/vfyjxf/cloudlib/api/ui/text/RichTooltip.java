    static RichTooltip from(Component... texts) {
        var tooltip = create();
        for (var text : texts) {
            tooltip.add(text);
        }
        return tooltip;
    }
    return create().add(components);