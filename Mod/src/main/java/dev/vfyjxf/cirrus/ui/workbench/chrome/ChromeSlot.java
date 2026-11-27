package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cloudlib.api.ui.base.Widget;

/**
 * A chrome chip slot that only reserves layout space and is painted by its parent.
 */
final class ChromeSlot extends Widget {

    ChromeSlot() {
        setInteractive(false);
        setVisible(true);
    }
}
