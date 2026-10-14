package net.nebulalimbo.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.nebulalimbo.client.NebulaSkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders the custom Nebula Limbo cosmic sky dome (swirling cyan core, purple-magenta nebula arms,
 * and floating dark SQUID_INK + bright END_ROD void stars matching Frame 08 & Frame 09) when inside `nebula_limbo:nebula`.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Unique
    private final RandomSource nebulaLimbo$random = RandomSource.create();

    @Unique
    private static final DustParticleOptions NEBULA_CYAN_DUST = new DustParticleOptions(0x68EAFF, 4.0f);

    @Unique
    private static final DustParticleOptions NEBULA_PURPLE_DUST = new DustParticleOptions(0x6C228E, 4.0f);

    @Unique
    private static final DustParticleOptions NEBULA_MAGENTA_DUST = new DustParticleOptions(0x9C35B5, 3.5f);

    @Inject(method = "tick", at = @At("TAIL"))
    private void nebulaLimbo$onClientRendererTick(CallbackInfo ci) {
        if (!NebulaSkyRenderer.isInNebulaDimension()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused()) {
            return;
        }

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();

        // 1. Swirling Cyan & Purple Nebula Sky Dome overhead (matching Video 1 & Video 2 custom sky)
        for (int i = 0; i < 16; i++) {
            double angle = nebulaLimbo$random.nextDouble() * Math.PI * 2.0;
            double dist = 3.0 + nebulaLimbo$random.nextDouble() * 28.0;
            double spiralX = Math.cos(angle) * dist;
            double spiralZ = Math.sin(angle) * dist;
            double domeY = 11.0 + Math.max(0.0, 15.0 - dist * 0.35) + (nebulaLimbo$random.nextDouble() - 0.5) * 4.0;

            DustParticleOptions dust = (dist < 9.5)
                    ? NEBULA_CYAN_DUST
                    : ((i % 2 == 0) ? NEBULA_PURPLE_DUST : NEBULA_MAGENTA_DUST);

            mc.level.addParticle(dust, px + spiralX, py + domeY, pz + spiralZ, 0.0, 0.002, 0.0);
        }

        // 2. Floating cosmic white diamond stars (END_ROD) and dark pixel clusters (SQUID_INK) around the player
        for (int i = 0; i < 8; i++) {
            double ox = (nebulaLimbo$random.nextDouble() - 0.5) * 34.0;
            double oy = (nebulaLimbo$random.nextDouble() - 0.15) * 22.0;
            double oz = (nebulaLimbo$random.nextDouble() - 0.5) * 34.0;
            mc.level.addParticle(
                    (i % 2 == 0) ? ParticleTypes.END_ROD : ParticleTypes.SQUID_INK,
                    px + ox,
                    py + oy,
                    pz + oz,
                    0.0, 0.004, 0.0
            );
        }
    }
}
