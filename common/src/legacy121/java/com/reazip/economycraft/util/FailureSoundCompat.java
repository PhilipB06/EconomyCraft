package com.reazip.economycraft.util;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

final class FailureSoundCompat {
    private FailureSoundCompat() {}

    static SoundEvent sound() {
        return SoundEvents.VILLAGER_NO;
    }
}
