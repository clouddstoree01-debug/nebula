package net.nebulalimbo.mixin;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.nebulalimbo.client.NebulaLimboClient;
import net.nebulalimbo.network.LimboStatePayload;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents the player from pressing F5 (`keyTogglePerspective`) to change camera perspective
 * while a Nebula Limbo cinematic (Dying Blink, Pre-Rebirth Orbit, or Post-Rebirth Arrival) is playing.
 */
@Mixin(Minecraft.class)
public class MinecraftClientMixin {

    @Shadow
    @Final
    public Options options;

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void nebulaLimbo$lockF5DuringCinematic(CallbackInfo ci) {
        if (NebulaLimboClient.isF5Locked()) {
            // Drain all F5 key clicks before vanilla handleKeybinds can process them
            while (this.options.keyTogglePerspective.consumeClick()) {
                // Locked during cinematic
            }
            int phase = NebulaLimboClient.getCurrentPhase();
            if (phase == LimboStatePayload.PHASE_DYING_BLINK) {
                this.options.setCameraType(CameraType.FIRST_PERSON);
            } else if (phase == LimboStatePayload.PHASE_PRE_REBIRTH || phase == LimboStatePayload.PHASE_POST_REBIRTH) {
                this.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        }
    }
}
