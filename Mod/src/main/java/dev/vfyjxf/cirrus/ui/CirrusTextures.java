package dev.vfyjxf.cirrus.ui;

import dev.vfyjxf.cloudlib.api.ui.texture.BatchableTexture;
import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import dev.vfyjxf.cloudlib.api.ui.texture.NineSliceTexture;
import dev.vfyjxf.cloudlib.api.ui.texture.SizedTexture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;

import static dev.vfyjxf.cirrus.util.CirrusLocations.ofMod;

public final class CirrusTextures {

    private record SpriteSize(int width, int height) {
    }

    private static ImageTexture autoSprite(String relativePath) {
        SpriteSize size = loadSpriteSize(relativePath);
        return ImageTexture.sprite(ofMod(relativePath), size.width(), size.height());
    }

    private static SizedTexture croppedGuiTexture(
        String relativePath,
        int u,
        int v,
        int sourceWidth,
        int sourceHeight,
        int displayWidth,
        int displayHeight
    ) {
        SpriteSize size = loadSpriteSize(relativePath);
        return new CroppedGuiTexture(
            ofMod("textures/cirrus/gui/" + relativePath + ".png"),
            u,
            v,
            sourceWidth,
            sourceHeight,
            size.width(),
            size.height(),
            displayWidth,
            displayHeight
        );
    }

    private static SpriteSize loadSpriteSize(String relativePath) {
        String namespace = ofMod(relativePath).getNamespace();
        String path = ofMod(relativePath).getPath();
        String resourcePath = "assets/" + namespace + "/textures/cirrus/gui/" + path + ".png";
        try (InputStream stream = CirrusTextures.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing sprite resource: " + resourcePath);
            }
            var image = ImageIO.read(stream);
            if (image == null) {
                throw new IllegalStateException("Unreadable sprite resource: " + resourcePath);
            }
            return new SpriteSize(image.getWidth(), image.getHeight());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read sprite size: " + resourcePath, e);
        }
    }

    private record CroppedGuiTexture(
        ResourceLocation location,
        int u,
        int v,
        int sourceWidth,
        int sourceHeight,
        int textureWidth,
        int textureHeight,
        int width,
        int height
    ) implements SizedTexture, BatchableTexture {
        @Override
        public void render(GuiGraphics graphics, int x, int y, int width, int height) {
            graphics.blit(location, x, y, width, height, u, v, sourceWidth, sourceHeight, textureWidth, textureHeight);
        }

        @Override
        public void emit(VertexEmitter emitter, float x, float y, float width, float height, int color) {
            float u0 = (float) u / textureWidth;
            float v0 = (float) v / textureHeight;
            float u1 = (float) (u + sourceWidth) / textureWidth;
            float v1 = (float) (v + sourceHeight) / textureHeight;
            emitter.textured(location, x, y, width, height, u0, v0, u1, v1, color);
        }
    }

    public static final class Background {
        public static final NineSliceTexture frame = NineSliceTexture.sprite(ofMod("background/frame"), 32, 37, 10, 10, 10, 10);
        public static final NineSliceTexture flat = NineSliceTexture.sprite(ofMod("background/flat"), 18, 18, 4, 4, 4, 4);
        public static final NineSliceTexture inset = NineSliceTexture.sprite(ofMod("background/inset"), 18, 18, 4, 4, 4, 4);
        public static final NineSliceTexture dark = NineSliceTexture.sprite(ofMod("background/dark"), 16, 16, 4, 4, 4, 4);
        public static final NineSliceTexture scroll = NineSliceTexture.sprite(ofMod("background/scoll"), 18, 9, 4, 4, 4, 4);
        public static final class Outlined {
            public static final NineSliceTexture inset = NineSliceTexture.sprite(ofMod("background/outlined/inset"), 20, 20, 4, 4, 4, 4);
            public static final NineSliceTexture flat = NineSliceTexture.sprite(ofMod("background/outlined/flat"), 20, 20, 4, 4, 4, 4);
        }

        public static final class Border {
            public static final NineSliceTexture dark = NineSliceTexture.sprite(ofMod("background/border/dark"), 18, 18, 4, 4, 4, 4);
            public static final NineSliceTexture light = NineSliceTexture.sprite(ofMod("background/border/light"), 18, 18, 4, 4, 4, 4);
        }

        public static final class Slot {
            public static final NineSliceTexture base = NineSliceTexture.sprite(ofMod("background/slot/base"), 18, 18, 4, 4, 4, 4);
            public static final NineSliceTexture dark = NineSliceTexture.sprite(ofMod("background/slot/dark"), 18, 18, 4, 4, 4, 4);
        }

        public static final class Paged {
            public static final NineSliceTexture arrow = NineSliceTexture.sprite(ofMod("background/paged/arrow"), 9, 10, 3, 3, 3, 3);
            public static final NineSliceTexture button = NineSliceTexture.sprite(ofMod("background/paged/button"), 8, 10, 3, 3, 3, 3);
        }

        public static final class Tab {
            public static final ImageTexture select = ImageTexture.sprite(ofMod("tab/select"), 21, 22);
            public static final ImageTexture hover = ImageTexture.sprite(ofMod("tab/hover"), 21, 22);
            public static final ImageTexture unselect = ImageTexture.sprite(ofMod("tab/unselect"), 20, 22);
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
                public static final class Rock {
                    // Trace toggle uses split rocker parts instead of a full slide texture.
                    public static final ImageTexture leftOn = autoSprite("toggle/switch/rock_left_on");
                    public static final ImageTexture leftOnHover = autoSprite("toggle/switch/rock_left_on_hover");
                    public static final ImageTexture leftOff = autoSprite("toggle/switch/rock_left_off");
                    public static final ImageTexture leftOffHover = autoSprite("toggle/switch/rock_left_off_hover");
                    public static final ImageTexture rightOn = autoSprite("toggle/switch/rock_right_on");
                    public static final ImageTexture rightOnHover = autoSprite("toggle/switch/rock_right_on_hover");
                    public static final ImageTexture rightOff = autoSprite("toggle/switch/rock_right_off");
                    public static final ImageTexture rightOffHover = autoSprite("toggle/switch/rock_right_off_hover");

                    public static final class Bg {
                        public static final ImageTexture onLeft = autoSprite("toggle/switch/background/rock_on_left");
                        public static final ImageTexture onHoverLeft = autoSprite("toggle/switch/background/rock_on_hover_left");
                    public static final ImageTexture onRight = autoSprite("toggle/switch/background/rock_on_right");
                    public static final ImageTexture onHoverRight = autoSprite("toggle/switch/background/rock_on_hover_right");
                    public static final ImageTexture offLeft = autoSprite("toggle/switch/background/rock_off_left");
                    public static final ImageTexture offHoverLeft = autoSprite("toggle/switch/background/rock_off_hover_left");
                    public static final ImageTexture offRight = autoSprite("toggle/switch/background/rock_off_right");
                    public static final ImageTexture offHoverRight = autoSprite("toggle/switch/background/rock_off_hover_right");
                }
            }

            public static final class Slide {
                public static final ImageTexture on = autoSprite("toggle/switch/slide_on");
                public static final ImageTexture onHover = autoSprite("toggle/switch/slide_on_hover");
                public static final ImageTexture off = autoSprite("toggle/switch/slide_off");
                public static final ImageTexture offHover = autoSprite("toggle/switch/slide_off_hover");
            }

            //region calc
            public static final class Calc {
                public static final SizedTexture gridOn = croppedGuiTexture("toggle/switch/clac/grid_on", 2, 2, 5, 6, 10, 12);
                public static final SizedTexture gridOff = croppedGuiTexture("toggle/switch/clac/grid_off", 2, 2, 5, 6, 10, 12);
                public static final SizedTexture gridHover = croppedGuiTexture("toggle/switch/clac/grid_hover", 2, 2, 5, 6, 10, 12);
                public static final SizedTexture listOn = croppedGuiTexture("toggle/switch/clac/list_on", 2, 2, 5, 6, 10, 12);
                public static final SizedTexture listOff = croppedGuiTexture("toggle/switch/clac/list_off", 2, 2, 5, 6, 10, 12);
                public static final SizedTexture listHover = croppedGuiTexture("toggle/switch/clac/list_hover", 2, 2, 5, 6, 10, 12);
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

    public static final class Button {
        public static final class Calc {
            public static final NineSliceTexture primary = NineSliceTexture.sprite(ofMod("background/paged/button"), 8, 10, 3, 3, 3, 3);
        }
    }

    public static final class Icon {
        // Calculator tab icons (16×16)
        public static final ImageTexture calculatorSelect = ImageTexture.sprite(ofMod("icon/calc/calculator_select"), 16, 16);
        public static final ImageTexture calculatorHover = ImageTexture.sprite(ofMod("icon/calc/calculator_hover"), 16, 16);
        public static final ImageTexture calculatorUnselect = ImageTexture.sprite(ofMod("icon/calc/calculator_unselect"), 16, 16);
        public static final ImageTexture calculatorOpen = ImageTexture.sprite(ofMod("icon/calc/calculator_open"), 16, 16);
        public static final ImageTexture calculatorOpenPressed = ImageTexture.sprite(ofMod("icon/calc/calculator_open_pressed"), 16, 16);
        public static final ImageTexture calculatorJei = ImageTexture.sprite(ofMod("icon/calc/calculator_jei"), 16, 16);
        public static final ImageTexture calculatorJeiPressed = ImageTexture.sprite(ofMod("icon/calc/calculator_jei_pressed"), 16, 16);
        public static final ImageTexture calculatorTarget = ImageTexture.sprite(ofMod("icon/calc/calculator_target"), 12, 12);
        public static final ImageTexture calculatorTargetPressed = ImageTexture.sprite(ofMod("icon/calc/calculator_target_pressed"), 12, 12);
        public static final ImageTexture sortAscending = ImageTexture.sprite(ofMod("icon/calc/sort_ascending"), 16, 16);
        public static final ImageTexture sortDescending = ImageTexture.sprite(ofMod("icon/calc/sort_descending"), 16, 16);

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
        public static final ImageTexture finished = ImageTexture.sprite(ofMod("icon/finished"), 11, 11);
        public static final ImageTexture next = ImageTexture.sprite(ofMod("icon/next"), 12, 12);
        public static final ImageTexture nextPressed = ImageTexture.sprite(ofMod("icon/next_pressed"), 12, 12);
        public static final ImageTexture pause = ImageTexture.sprite(ofMod("icon/pause"), 11, 11);
        public static final ImageTexture play = ImageTexture.sprite(ofMod("icon/play"), 11, 11);
        public static final ImageTexture start = ImageTexture.sprite(ofMod("icon/start"), 12, 12);
        public static final ImageTexture startPressed = ImageTexture.sprite(ofMod("icon/start_pressed"), 12, 12);

        public static final NineSliceTexture scrollBarHorizontal = NineSliceTexture.sprite(ofMod("icon/scrollbar_horizontal"), 7, 9, 2, 2, 3, 3);
        public static final NineSliceTexture scrollTrackFill = NineSliceTexture.sprite(ofMod("icon/scroll_track_fill"), 3, 3, 1, 1, 1, 1);

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

        public static final class Middle {
            public static final ImageTexture arrowRight = autoSprite("icon/middle/arrow_right");
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

            // Use existing complete icons from the atlas (no ad-hoc slicing).
            public static final ImageTexture layerNav = ImageTexture.sprite(ofMod("icon/calc/path/tree_path"), 16, 16);
            public static final ImageTexture calculate = ImageTexture.sprite(ofMod("icon/arrow_right"), 10, 10);

            public static final class Map {
                public static final ImageTexture background = ImageTexture.sprite(ofMod("icon/calc/map/background"), 48, 24);
            }

            public static final class Sort {
                public static final ImageTexture availableFirst = ImageTexture.sprite(ofMod("icon/calc/sort/available_first"), 16, 16);
                public static final ImageTexture availableFirstPressed = ImageTexture.sprite(ofMod("icon/calc/sort/available_first_pressed"), 16, 16);
                public static final ImageTexture id = ImageTexture.sprite(ofMod("icon/calc/sort/id"), 16, 16);
                public static final ImageTexture idPressed = ImageTexture.sprite(ofMod("icon/calc/sort/id_pressed"), 16, 16);
                public static final ImageTexture missingFirst = ImageTexture.sprite(ofMod("icon/calc/sort/missing_first"), 16, 16);
                public static final ImageTexture missingFirstPressed = ImageTexture.sprite(ofMod("icon/calc/sort/missing_first_pressed"), 16, 16);
                public static final ImageTexture number = ImageTexture.sprite(ofMod("icon/calc/sort/number"), 16, 16);
                public static final ImageTexture numberPressed = ImageTexture.sprite(ofMod("icon/calc/sort/number_pressed"), 16, 16);
                public static final ImageTexture name = ImageTexture.sprite(ofMod("icon/calc/sort_name"), 16, 16);
                public static final ImageTexture namePressed = ImageTexture.sprite(ofMod("icon/calc/sort_name_pressed"), 16, 16);
            }
        }

        public static final class Execution {
            public static final ImageTexture executionDisabledJei = ImageTexture.sprite(ofMod("icon/exc/execution_disabled_jei"), 16, 16);
            public static final ImageTexture executionDisabledJeiPressed = ImageTexture.sprite(ofMod("icon/exc/execution_disabled_jei_pressed"), 16, 16);
            public static final ImageTexture executionEnabledJei = ImageTexture.sprite(ofMod("icon/exc/execution_enabled_jei"), 16, 16);
            public static final ImageTexture executionEnabledJeiPressed = ImageTexture.sprite(ofMod("icon/exc/execution_enabled_jei_pressed"), 16, 16);

            public static final class Category {
                public static final ImageTexture recipe = ImageTexture.sprite(ofMod("icon/exc/category/recipe_selected"), 16, 16);
                public static final ImageTexture recipeSelected = ImageTexture.sprite(ofMod("icon/exc/category/recipe"), 16, 16);
                public static final ImageTexture timeline = ImageTexture.sprite(ofMod("icon/exc/category/timeline_selected"), 16, 16);
                public static final ImageTexture timelineSelected = ImageTexture.sprite(ofMod("icon/exc/category/timeline"), 16, 16);
                public static final ImageTexture ingredients = ImageTexture.sprite(ofMod("icon/exc/category/ingredients_selected"), 16, 16);
                public static final ImageTexture ingredientsSelected = ImageTexture.sprite(ofMod("icon/exc/category/ingredients"), 16, 16);
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

        public static final NineSliceTexture scrollBarVertical = NineSliceTexture.sprite(ofMod("icon/scrollbar"), 7, 8, 2, 4, 3, 3);
    }

    public static final class Sign {
        public static final class Group {
            public static final ImageTexture catalyst = ImageTexture.sprite(ofMod("sign/group/catalyst"), 3, 19);
            public static final ImageTexture fluid = ImageTexture.sprite(ofMod("sign/group/fluid"), 3, 19);
            public static final ImageTexture item = ImageTexture.sprite(ofMod("sign/group/item"), 3, 19);
        }

        public static final class Exc {
            public static final ImageTexture path = ImageTexture.sprite(ofMod("sign/exc/path"), 9, 9);
        }
    }

    public static final class Tab {
        public static final ImageTexture select = ImageTexture.sprite(ofMod("tab/select"), 21, 22);
        public static final ImageTexture hover = ImageTexture.sprite(ofMod("tab/hover"), 21, 22);
        public static final ImageTexture unselect = ImageTexture.sprite(ofMod("tab/unselect"), 20, 22);
    }

}
