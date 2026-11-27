package dev.vfyjxf.cirrus.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

public final class CirrusUiSound {

    private CirrusUiSound() {}

    public static void playClickSound() {
        playClickSound(1f);
    }

    public static void playClickSound(float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }
}
