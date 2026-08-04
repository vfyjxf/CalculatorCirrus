package dev.vfyjxf.cirrus.ui.calc.state;

import dev.vfyjxf.cloudlib.api.util.Namespace;

public final class TagChoiceApplier {
    private final CalculatorSession session;
    private final TagPreferenceStore store;

    public TagChoiceApplier(CalculatorSession session, TagPreferenceStore store) {
        this.session = session;
        this.store = store;
        this.session.applyDefaults(store.loadDefaults());
    }

    public void apply(Namespace tag, String optionId, TagChoiceScope scope) {
        TagChoice choice = new TagChoice(tag, optionId, scope);
        if (scope == TagChoiceScope.RUN_ONLY) {
            session.setRunChoice(tag, choice);
        } else {
            session.setGlobalDefault(tag, choice);
            store.saveDefault(choice);
        }
    }
}
