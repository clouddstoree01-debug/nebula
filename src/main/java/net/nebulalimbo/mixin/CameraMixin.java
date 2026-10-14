package net.nebulalimbo.mixin;

import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.nebulalimbo.client.NebulaLimboClient;
import net.nebulalimbo.network.LimboStatePayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void move(float zoomOffset, float verticalOffset, float horizontalOffset);

    @Shadow
    protected abstract float getMaxZoom(float maxZoom);

    @Shadow
    private boolean detached;

    @Shadow
    private float yRot;

    @Shadow
    private float xRot;

    @Inject(method = "setup", at = @At("TAIL"))
    private void nebulaLimbo$onCameraSetup(
            BlockGetter level,
            Entity focusedEntity,
            boolean detachedParam,
            boolean thirdPersonReverse,
            float partialTick,
            CallbackInfo ci
    ) {
        int phase = NebulaLimboClient.getCurrentPhase();
        if (phase == LimboStatePayload.PHASE_NONE || focusedEntity == null) {
            return;
        }

        float progress = NebulaLimboClient.getPhaseProgress(partialTick);
        float ticks = NebulaLimboClient.getClientPhaseTicks() + partialTick;

        if (phase == LimboStatePayload.PHASE_DYING_BLINK) {
            // Video 1: Player collapses to the ground as if falling asleep, camera stays in 1st-person and drops/tilts
            this.detached = false;
            float fallCurve = Mth.clamp(progress / 0.25f, 0.0f, 1.0f);
            fallCurve = fallCurve * fallCurve * (3.0f - 2.0f * fallCurve);

            double eyeX = Mth.lerp(partialTick, focusedEntity.xo, focusedEntity.getX());
            double eyeY = Mth.lerp(partialTick, focusedEntity.yo, focusedEntity.getY()) + Mth.lerp(fallCurve, 1.62f, 0.26f);
            double eyeZ = Mth.lerp(partialTick, focusedEntity.zo, focusedEntity.getZ());

            this.setPosition(eyeX, eyeY, eyeZ);
            float targetPitch = Mth.lerp(fallCurve, this.xRot, -28.0f + Mth.sin(ticks * 0.08f) * 3.0f);
            float targetYaw = this.yRot + (fallCurve * 24.0f);
            this.setRotation(targetYaw, targetPitch);

        } else if (phase == LimboStatePayload.PHASE_PRE_REBIRTH) {
            // Video 2 (00:09 - 00:13, Frame 10-12): Force detached 3rd-person so the REAL player model is rendered in center!
            this.detached = true;
            double px = Mth.lerp(partialTick, focusedEntity.xo, focusedEntity.getX());
            double py = Mth.lerp(partialTick, focusedEntity.yo, focusedEntity.getY()) + 1.15;
            double pz = Mth.lerp(partialTick, focusedEntity.zo, focusedEntity.getZ());

            float orbitYaw = (focusedEntity.getYRot() + ticks * 5.4f) % 360.0f;
            float orbitPitch = 18.0f + Mth.sin(ticks * 0.09f) * 14.0f;
            float desiredDistance = Mth.lerp(progress, 4.8f, 3.1f);

            this.setPosition(px, py, pz);
            this.setRotation(orbitYaw, orbitPitch);
            this.move(-this.getMaxZoom(desiredDistance), 0.0f, 0.0f);

        } else if (phase == LimboStatePayload.PHASE_POST_REBIRTH) {
            // Video 2 (00:15 - 00:18, Frame 16-19):
            // Starts in high-angle 3rd-person watching the celestial beam & shockwave ring, then smoothly zooms into 1st-person!
            this.detached = (progress < 0.88f);
            double px = Mth.lerp(partialTick, focusedEntity.xo, focusedEntity.getX());
            double py = Mth.lerp(partialTick, focusedEntity.yo, focusedEntity.getY()) + Mth.lerp(progress, 1.35f, 1.62f);
            double pz = Mth.lerp(partialTick, focusedEntity.zo, focusedEntity.getZ());

            float smoothZoom = progress * progress * (3.0f - 2.0f * progress);
            float orbitYaw = Mth.lerp(smoothZoom, focusedEntity.getYRot() + 160.0f, focusedEntity.getYRot());
            float orbitPitch = Mth.lerp(smoothZoom, 28.0f, focusedEntity.getXRot());
            float desiredDistance = Mth.lerp(smoothZoom, 5.8f, 0.0f);

            this.setPosition(px, py, pz);
            this.setRotation(orbitYaw, orbitPitch);
            if (desiredDistance > 0.05f) {
                this.move(-this.getMaxZoom(desiredDistance), 0.0f, 0.0f);
            }
        }
    }

    @Inject(method = "isDetached", at = @At("RETURN"), cancellable = true)
    private void nebulaLimbo$forceDetachedState(CallbackInfoReturnable<Boolean> cir) {
        int phase = NebulaLimboClient.getCurrentPhase();
        if (phase == LimboStatePayload.PHASE_PRE_REBIRTH) {
            cir.setReturnValue(true);
        } else if (phase == LimboStatePayload.PHASE_POST_REBIRTH) {
            cir.setReturnValue(NebulaLimboClient.getPhaseProgress(0.0f) < 0.88f);
        } else if (phase == LimboStatePayload.PHASE_DYING_BLINK) {
            cir.setReturnValue(false);
        }
    }
}
