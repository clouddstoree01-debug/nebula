package net.nebulalimbo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.nebulalimbo.NebulaLimboMod;

/**
 * Provides the custom Nebula sky texture (`assets/nebula_limbo/textures/environment/nebula_sky.png`)
 * and dynamic cosmic purple-cyan sky/fog colors for the `nebula_limbo:nebula` dimension.
 */
public class NebulaSkyRenderer {

    public static final Identifier NEBULA_SKY_TEXTURE = NebulaLimboMod.id("textures/environment/nebula_sky.png");

    public static final int NEBULA_DEEP_SPACE = 0xFF16082B;
    public static final int NEBULA_PURPLE = 0xFF6C228E;
    public static final int NEBULA_CYAN_CORE = 0xFF68EAFF;

    public static boolean isInNebulaDimension() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension().equals(NebulaLimboMod.NEBULA_DIMENSION);
    }

    public static int getCurrentNebulaSkyColor() {
        double timeSec = Util.getMillis() / 1000.0;
        float pulse = (Mth.sin((float) (timeSec * 0.45)) + 1.0f) * 0.5f;
        return ARGB.linearLerp(pulse * 0.35f, NEBULA_PURPLE, NEBULA_CYAN_CORE);
    }
}
