package net.nebulalimbo.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.nebulalimbo.NebulaLimboMod;
import net.nebulalimbo.network.LimboStatePayload;

public class NebulaLimboClient implements ClientModInitializer {

    private static int currentPhase = LimboStatePayload.PHASE_NONE;
    private static int phaseDurationTicks = 0;
    private static int clientPhaseTicks = 0;
    private static boolean lockF5DuringCinematic = true;

    public static int getCurrentPhase() {
        return currentPhase;
    }

    /**
     * Returns true when a cinematic camera sequence is active (Collapse/Blink, Nebula 360 Orbit, or Overworld Rebirth)
     * and F5 perspective toggling should be blocked.
     */
    public static boolean isF5Locked() {
        if (!lockF5DuringCinematic) return false;
        return currentPhase == LimboStatePayload.PHASE_DYING_BLINK
                || currentPhase == LimboStatePayload.PHASE_PRE_REBIRTH
                || currentPhase == LimboStatePayload.PHASE_POST_REBIRTH;
    }

    public static float getPhaseProgress(float partialTick) {
        if (phaseDurationTicks <= 0) return 0.0f;
        return Mth.clamp((clientPhaseTicks + partialTick) / (float) phaseDurationTicks, 0.0f, 1.0f);
    }

    public static int getClientPhaseTicks() {
        return clientPhaseTicks;
    }

    @Override
    public void onInitializeClient() {
        // 1. Receive phase updates from server
        ClientPlayNetworking.registerGlobalReceiver(LimboStatePayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> {
                onPhaseChange(client, payload.phase(), payload.durationTicks(), payload.elapsedTicks(), payload.lockF5DuringCinematic());
            });
        });

        // 2. Advance client animation ticks & enforce camera perspective lock if F5 is pressed
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) {
                currentPhase = LimboStatePayload.PHASE_NONE;
                return;
            }
            if (currentPhase != LimboStatePayload.PHASE_NONE) {
                clientPhaseTicks++;
            }

            // Enforce camera perspective during active cinematics so F5 cannot break the cutscene
            if (isF5Locked()) {
                while (client.options.keyTogglePerspective.consumeClick()) {
                    // Consume and ignore F5 presses during cinematic
                }
                if (currentPhase == LimboStatePayload.PHASE_DYING_BLINK) {
                    client.options.setCameraType(CameraType.FIRST_PERSON);
                } else if (currentPhase == LimboStatePayload.PHASE_PRE_REBIRTH || currentPhase == LimboStatePayload.PHASE_POST_REBIRTH) {
                    client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                }
            }
        });

        // 3. Register Fullscreen HUD Overlay at the VERY TOP of the HUD stack (HudElementRegistry.addLast)
        // so the White Flash (Frame 14-15) and Black Eyelid Blink cover the entire screen including hotbar & chat!
        HudElementRegistry.addLast(
                NebulaLimboMod.id("limbo_cinematic_overlay"),
                NebulaLimboClient::renderCinematicOverlay
        );
    }

    private static void onPhaseChange(Minecraft client, int newPhase, int durationTicks, int elapsedTicks, boolean lockF5) {
        int previousPhase = currentPhase;
        currentPhase = newPhase;
        phaseDurationTicks = durationTicks;
        clientPhaseTicks = elapsedTicks;
        lockF5DuringCinematic = lockF5;

        if (newPhase == LimboStatePayload.PHASE_DYING_BLINK) {
            client.options.setCameraType(CameraType.FIRST_PERSON);
        } else if (newPhase == LimboStatePayload.PHASE_PRE_REBIRTH || newPhase == LimboStatePayload.PHASE_POST_REBIRTH) {
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (newPhase == LimboStatePayload.PHASE_NONE &&
                (previousPhase == LimboStatePayload.PHASE_PRE_REBIRTH || previousPhase == LimboStatePayload.PHASE_POST_REBIRTH)) {
            // Frame 19: Seamlessly return to 1st-person once the Overworld zoom-in finishes
            client.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    private static void renderCinematicOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (currentPhase == LimboStatePayload.PHASE_NONE) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        float ticks = clientPhaseTicks + partialTick;

        if (currentPhase == LimboStatePayload.PHASE_DYING_BLINK) {
            float progress = getPhaseProgress(partialTick);
            float closure = computeDoubleBlinkClosure(progress);
            drawEyelids(graphics, screenWidth, screenHeight, closure);
        } else if (currentPhase == LimboStatePayload.PHASE_IN_LIMBO) {
            // First 45 ticks (2.25s) in Limbo: player opens their eyes from blackout to see the Nebula sky
            if (ticks < 45.0f) {
                float wakeProgress = ticks / 45.0f;
                float closure = 1.0f - smoothStep(wakeProgress);
                drawEyelids(graphics, screenWidth, screenHeight, closure);
            }
        } else if (currentPhase == LimboStatePayload.PHASE_PRE_REBIRTH) {
            // Frame 13-15: Fullscreen pure white flash builds up during the final 25 ticks of the Nebula 3rd-person orbit
            float flashStartTick = Math.max(10.0f, phaseDurationTicks - 25.0f);
            if (ticks >= flashStartTick) {
                float alpha = Mth.clamp((ticks - flashStartTick) / 18.0f, 0.0f, 1.0f);
                int color = ARGB.color((int) (alpha * 255.0f), 255, 255, 255);
                graphics.fill(0, 0, screenWidth, screenHeight, color);
            }
        } else if (currentPhase == LimboStatePayload.PHASE_POST_REBIRTH) {
            // Frame 15-16: Fullscreen white flash holds briefly for 8 ticks then smoothly fades out over 24 ticks in the Overworld
            if (ticks < 32.0f) {
                float fadeProgress = Mth.clamp((ticks - 6.0f) / 26.0f, 0.0f, 1.0f);
                float alpha = 1.0f - smoothStep(fadeProgress);
                int color = ARGB.color((int) (alpha * 255.0f), 255, 255, 255);
                graphics.fill(0, 0, screenWidth, screenHeight, color);
            }
        }
    }

    private static float computeDoubleBlinkClosure(float p) {
        if (p < 0.18f) {
            return smoothStep(p / 0.18f) * 0.35f;
        } else if (p < 0.27f) {
            return Mth.lerp(smoothStep((p - 0.18f) / 0.09f), 0.35f, 0.96f);
        } else if (p < 0.36f) {
            return Mth.lerp(smoothStep((p - 0.27f) / 0.09f), 0.96f, 0.22f);
        } else if (p < 0.50f) {
            return Mth.lerp(smoothStep((p - 0.36f) / 0.14f), 0.22f, 1.0f);
        } else if (p < 0.64f) {
            return Mth.lerp(smoothStep((p - 0.50f) / 0.14f), 1.0f, 0.36f);
        } else if (p < 0.82f) {
            return Mth.lerp(smoothStep((p - 0.64f) / 0.18f), 0.36f, 1.0f);
        } else {
            return 1.0f;
        }
    }

    private static void drawEyelids(GuiGraphicsExtractor graphics, int width, int height, float closure) {
        closure = Mth.clamp(closure, 0.0f, 1.0f);
        if (closure <= 0.001f) return;

        int barHeight = (int) ((height * 0.55f) * closure);
        int softEdge = 20;

        // Top eyelid solid black + soft gradient edge
        graphics.fill(0, 0, width, barHeight, 0xFF000000);
        graphics.fillGradient(0, barHeight, width, barHeight + softEdge, 0xFF000000, 0x00000000);

        // Bottom eyelid solid black + soft gradient edge
        graphics.fill(0, height - barHeight, width, height, 0xFF000000);
        graphics.fillGradient(0, height - barHeight - softEdge, width, height - barHeight, 0x00000000, 0xFF000000);

        // Ambient darkening across the whole screen proportional to closure
        int ambientAlpha = (int) (closure * 190.0f);
        graphics.fill(0, 0, width, height, ARGB.color(ambientAlpha, 0, 0, 0));
    }

    private static float smoothStep(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
