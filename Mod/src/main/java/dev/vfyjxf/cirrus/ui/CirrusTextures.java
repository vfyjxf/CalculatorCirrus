package dev.vfyjxf.cirrus.ui;

import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import dev.vfyjxf.cloudlib.api.ui.texture.NineSliceTexture;

import static dev.vfyjxf.cirrus.util.CirrusLocations.ofMod;

public final class CirrusTextures {

    // ═══════════════════ Background ═══════════════════

    public static final class Background {
        public static final NineSliceTexture frame = NineSliceTexture.sprite(ofMod("background/frame"), 32, 37, 10, 10, 10, 10);
        public static final NineSliceTexture flat = NineSliceTexture.sprite(ofMod("background/flat"), 18, 18, 4, 4, 4, 4);
        public static final NineSliceTexture inset = NineSliceTexture.sprite(ofMod("background/inset"), 18, 18, 4, 4, 4, 4);
        public static final NineSliceTexture dark = NineSliceTexture.sprite(ofMod("background/dark"), 16, 16, 4, 4, 4, 4);

        public static final class Outlined {
            public static final NineSliceTexture inset = NineSliceTexture.sprite(ofMod("background/outlined/inset"), 20, 20, 4, 4, 4, 4);
            public static final NineSliceTexture flat = NineSliceTexture.sprite(ofMod("background/outlined/flat"), 20, 20, 4, 4, 4, 4);
        }

        public static final class Border {
            public static final NineSliceTexture dark = NineSliceTexture.sprite(ofMod("background/border/dark"), 18, 18, 4, 4, 4, 4);
            public static final NineSliceTexture light = NineSliceTexture.sprite(ofMod("background/border/light"), 18, 18, 4, 4, 4, 4);
        }

        public static final class Slot {
            public static final NineSliceTexture dark = NineSliceTexture.sprite(ofMod("background/slot/dark"), 18, 18, 4, 4, 4, 4);
        }

        public static final class Tab {
            public static final ImageTexture select = ImageTexture.sprite(ofMod("background/tab/select"), 21, 22);
            public static final ImageTexture hover = ImageTexture.sprite(ofMod("background/tab/hover"), 21, 22);
            public static final ImageTexture unselect = ImageTexture.sprite(ofMod("background/tab/unselect"), 20, 22);
        }

        public static final class Sign {
            public static final ImageTexture amountSign = ImageTexture.sprite(ofMod("background/sign/amount_sign"), 7, 7);
            public static final NineSliceTexture bar = NineSliceTexture.sprite(ofMod("background/sign/bar"), 45, 4, 2, 2, 2, 2);
        }
    }

    public static final class Toggle {
        public static final class Button {
            public static final NineSliceTexture up = NineSliceTexture.sprite(ofMod("toggle/button/up"), 14, 16, 4);
            public static final NineSliceTexture hover = NineSliceTexture.sprite(ofMod("toggle/button/hover"), 14, 15, 4);
            public static final NineSliceTexture down = NineSliceTexture.sprite(ofMod("toggle/button/down"), 14, 15, 4);
        }

        public static final class Switch {
            //region rocker
            public static final ImageTexture rockerOn = ImageTexture.sprite(ofMod("toggle/switch/rocker_on"), 20, 13);
            public static final ImageTexture rockerOnHover = ImageTexture.sprite(ofMod("toggle/switch/rocker_on_hover"), 20, 13);
            public static final ImageTexture rockerOff = ImageTexture.sprite(ofMod("toggle/switch/rocker_off"), 20, 13);
            public static final ImageTexture rockerOffHover = ImageTexture.sprite(ofMod("toggle/switch/rocker_off_hover"), 20, 13);
            //endregion

            //region slide
            public static final ImageTexture slideOn = ImageTexture.sprite(ofMod("toggle/switch/slide_on"), 20, 13);
            public static final ImageTexture slideOnHover = ImageTexture.sprite(ofMod("toggle/switch/slide_on_hover"), 20, 12);
            public static final ImageTexture slideOff = ImageTexture.sprite(ofMod("toggle/switch/slide_off"), 20, 13);
            public static final ImageTexture slideOffHover = ImageTexture.sprite(ofMod("toggle/switch/slide_off_hover"), 20, 12);
            //endregion

            //region background
            public static final class RockerBg {
                public static final ImageTexture on = ImageTexture.sprite(ofMod("toggle/switch/background/rock_on"), 20, 13);
                public static final ImageTexture onHover = ImageTexture.sprite(ofMod("toggle/switch/background/rock_on_hover"), 20, 13);
                public static final ImageTexture off = ImageTexture.sprite(ofMod("toggle/switch/background/rock_off"), 20, 13);
                public static final ImageTexture offHover = ImageTexture.sprite(ofMod("toggle/switch/background/rock_off_hover"), 20, 13);
            }
            //endregion
        }

        public static final class Checked {
            public static final ImageTexture select = ImageTexture.sprite(ofMod("toggle/checked/select"), 10, 10);
            public static final ImageTexture selectHover = ImageTexture.sprite(ofMod("toggle/checked/select_hover"), 10, 10);
            public static final ImageTexture unselect = ImageTexture.sprite(ofMod("toggle/checked/unselect"), 10, 10);
            public static final ImageTexture unselectHover = ImageTexture.sprite(ofMod("toggle/checked/unselect_hover"), 10, 10);

            public static final class Small {
                public static final ImageTexture select = ImageTexture.sprite(ofMod("toggle/checked/small/select"), 7, 7);
                public static final ImageTexture selectHover = ImageTexture.sprite(ofMod("toggle/checked/small/select_hover"), 7, 7);
                public static final ImageTexture unselect = ImageTexture.sprite(ofMod("toggle/checked/small/unselect"), 7, 7);
                public static final ImageTexture unselectHover = ImageTexture.sprite(ofMod("toggle/checked/small/unselect_hover"), 7, 7);
            }
        }

        public static final class Triple {
            public static final NineSliceTexture leftUnselected = NineSliceTexture.sprite(ofMod("toggle/triple/left_unselected"), 78, 28, 9);
            public static final NineSliceTexture leftSelected = NineSliceTexture.sprite(ofMod("toggle/triple/left_selected"), 42, 30, 9);
            public static final NineSliceTexture middleLeft = NineSliceTexture.sprite(ofMod("toggle/triple/middle_left"), 39, 28, 9);
            public static final NineSliceTexture middleMiddle = NineSliceTexture.sprite(ofMod("toggle/triple/middle_middle"), 42, 30, 9);
            public static final NineSliceTexture middleRight = NineSliceTexture.sprite(ofMod("toggle/triple/middle_right"), 39, 28, 9);
            public static final NineSliceTexture rightSelected = NineSliceTexture.sprite(ofMod("toggle/triple/right_selected"), 42, 30, 9);
            public static final NineSliceTexture rightUnselected = NineSliceTexture.sprite(ofMod("toggle/triple/right_unselected"), 78, 28, 9);
        }
    }

    // ═══════════════════ Icon ═══════════════════

    public static final class Icon {
        // Calculator tab icons (16×16)
        public static final ImageTexture calculatorSelect = ImageTexture.sprite(ofMod("icon/calc/calculator_select"), 16, 16);
        public static final ImageTexture calculatorHover = ImageTexture.sprite(ofMod("icon/calc/calculator_hover"), 16, 16);
        public static final ImageTexture calculatorUnselect = ImageTexture.sprite(ofMod("icon/calc/calculator_unselect"), 16, 16);

        public static final ImageTexture contextSelect = ImageTexture.sprite(ofMod("icon/calc/context_select"), 16, 16);
        public static final ImageTexture contextHover = ImageTexture.sprite(ofMod("icon/calc/context_hover"), 16, 16);
        public static final ImageTexture contextUnselect = ImageTexture.sprite(ofMod("icon/calc/context_unselect"), 16, 16);

        public static final ImageTexture databaseSelect = ImageTexture.sprite(ofMod("icon/calc/database_select"), 16, 16);
        public static final ImageTexture databaseHover = ImageTexture.sprite(ofMod("icon/calc/database_hover"), 16, 16);
        public static final ImageTexture databaseUnselect = ImageTexture.sprite(ofMod("icon/calc/database_unselect"), 16, 16);

        public static final ImageTexture settingsSelect = ImageTexture.sprite(ofMod("icon/calc/setting_select"), 16, 16);
        public static final ImageTexture settingsHover = ImageTexture.sprite(ofMod("icon/calc/setting_hover"), 16, 16);
        public static final ImageTexture settingsUnselect = ImageTexture.sprite(ofMod("icon/calc/setting_unselect"), 16, 16);

        // General icons (10×10)
        public static final ImageTexture arrowDown = ImageTexture.sprite(ofMod("icon/arrow_down"), 10, 10);
        public static final ImageTexture arrowDownSelected = ImageTexture.sprite(ofMod("icon/arrow_down_selected"), 10, 10);
        public static final ImageTexture arrowRight = ImageTexture.sprite(ofMod("icon/arrow_right"), 10, 10);
        public static final ImageTexture arrowUp = ImageTexture.sprite(ofMod("icon/arrow_up"), 10, 10);
        public static final ImageTexture arrowUpSelected = ImageTexture.sprite(ofMod("icon/arrow_up_selected"), 10, 10);
        public static final ImageTexture delete = ImageTexture.sprite(ofMod("icon/delete"), 10, 10);
        public static final ImageTexture deleteSelected = ImageTexture.sprite(ofMod("icon/delete_seleted"), 10, 10);
        public static final ImageTexture search = ImageTexture.sprite(ofMod("icon/search"), 10, 10);
        public static final ImageTexture searchSelected = ImageTexture.sprite(ofMod("icon/search_selected"), 10, 10);
        public static final ImageTexture setting = ImageTexture.sprite(ofMod("icon/setting"), 10, 10);
        public static final ImageTexture settingSelected = ImageTexture.sprite(ofMod("icon/setting_selected"), 10, 10);

        // Small icons (5×6)
        public static final class Small {
            public static final ImageTexture arrowLeft = ImageTexture.sprite(ofMod("icon/small/arrow_left"), 5, 6);
            public static final ImageTexture arrowLeftSelected = ImageTexture.sprite(ofMod("icon/small/arrow_left_selected"), 5, 6);
            public static final ImageTexture arrowRight = ImageTexture.sprite(ofMod("icon/small/arrow_right"), 5, 6);
            public static final ImageTexture arrowRightSelected = ImageTexture.sprite(ofMod("icon/small/arrow_right_selected"), 5, 6);
            public static final ImageTexture minus = ImageTexture.sprite(ofMod("icon/small/minus"), 5, 6);
            public static final ImageTexture minusSelected = ImageTexture.sprite(ofMod("icon/small/minus_selected"), 5, 6);
            public static final ImageTexture plus = ImageTexture.sprite(ofMod("icon/small/plus"), 5, 6);
            public static final ImageTexture plusSelected = ImageTexture.sprite(ofMod("icon/small/plus_selected"), 5, 6);
        }

        public static final class Calc {
            public static final class Path {
                public static final ImageTexture branch = ImageTexture.sprite(ofMod("icon/calc/path/branch_path"), 16, 16);
                public static final ImageTexture tree = ImageTexture.sprite(ofMod("icon/calc/path/tree_path"), 16, 16);
            }

            public static final class Summary {
                public static final ImageTexture tabSelected = ImageTexture.sprite(ofMod("icon/calc/summary/tab_selected"), 16, 16);
                public static final ImageTexture tabUnselected = ImageTexture.sprite(ofMod("icon/calc/summary/tab_unselected"), 16, 16);
            }

            public static final class Task {
                public static final ImageTexture tabSelected = ImageTexture.sprite(ofMod("icon/calc/task/tab_selected"), 16, 16);
                public static final ImageTexture tabUnselected = ImageTexture.sprite(ofMod("icon/calc/task/tab_unselected"), 16, 16);
                public static final ImageTexture track = ImageTexture.sprite(ofMod("icon/calc/task/track"), 13, 13);
                public static final ImageTexture trackSelected = ImageTexture.sprite(ofMod("icon/calc/task/track_selected"), 13, 13);
            }

            public static final class Tree {
                public static final ImageTexture tabSelected = ImageTexture.sprite(ofMod("icon/calc/tree/tab_selected"), 16, 16);
                public static final ImageTexture tabUnselected = ImageTexture.sprite(ofMod("icon/calc/tree/tab_unselected"), 16, 16);
            }
        }

        // Sign icons
        public static final class Sign {
            public static final class Amount {
                public static final ImageTexture available = ImageTexture.sprite(ofMod("icon/sign/amount/avaible"), 5, 5);
                public static final ImageTexture catalyst = ImageTexture.sprite(ofMod("icon/sign/amount/catalyst"), 5, 5);
                public static final ImageTexture missing = ImageTexture.sprite(ofMod("icon/sign/amount/missing"), 5, 5);
            }

            public static final class Energy {
                public static final ImageTexture ae = ImageTexture.sprite(ofMod("icon/sign/energy/ae"), 9, 9);
                public static final ImageTexture eu = ImageTexture.sprite(ofMod("icon/sign/energy/eu"), 9, 9);
                public static final ImageTexture fe = ImageTexture.sprite(ofMod("icon/sign/energy/fe"), 9, 9);
                public static final ImageTexture mana = ImageTexture.sprite(ofMod("icon/sign/energy/mana"), 9, 9);
                public static final ImageTexture time = ImageTexture.sprite(ofMod("icon/sign/energy/time"), 9, 9);
                public static final ImageTexture vis = ImageTexture.sprite(ofMod("icon/sign/energy/vis"), 9, 9);
            }
        }

        public static final NineSliceTexture scrollBar = NineSliceTexture.sprite(ofMod("icon/scrollbar"), 7, 8, 2, 4, 3, 3);
    }

    // ═══════════════════ Sign ═══════════════════

    public static final class Sign {
        public static final class Group {
            public static final ImageTexture catalyst = ImageTexture.sprite(ofMod("sign/group/catalyst"), 3, 19);
            public static final ImageTexture fluid = ImageTexture.sprite(ofMod("sign/group/fluid"), 3, 19);
            public static final ImageTexture item = ImageTexture.sprite(ofMod("sign/group/item"), 3, 19);
        }
    }

    // ═══════════════════ Tab (standalone) ═══════════════════

    public static final class Tab {
        public static final ImageTexture select = ImageTexture.sprite(ofMod("tab/select"), 21, 22);
        public static final ImageTexture hover = ImageTexture.sprite(ofMod("tab/hover"), 21, 22);
        public static final ImageTexture unselect = ImageTexture.sprite(ofMod("tab/unselect"), 20, 22);
    }

}
