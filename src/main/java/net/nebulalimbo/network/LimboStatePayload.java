package net.nebulalimbo.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nebulalimbo.NebulaLimboMod;

/**
 * Synchronizes the player's cinematic & Limbo phase from Server to Client.
 * Phases:
 * 0 = NONE (Normal gameplay)
 * 1 = DYING_BLINK (Video 1: Collapse to sleeping pose + camera roll/drop + 2x eyelid blink + fade to black)
 * 2 = IN_LIMBO (Video 1 & 2: Wake-up eyelid open inside Nebula dimension + custom 360 Nebula skybox active)
 * 3 = PRE_REBIRTH_CINEMATIC (Video 2 00:09-00:14: BossBar 00:00 -> F5-locked 3rd-person 360 orbit in Nebula + fullscreen white flash)
 * 4 = POST_REBIRTH_CINEMATIC (Video 2 00:15-00:18: Overworld teleport -> white flash fade-out + 3rd-person zoom-in orbit + shockwave ring)
 */
public record LimboStatePayload(int phase, int durationTicks, int elapsedTicks, boolean lockF5DuringCinematic) implements CustomPacketPayload {

    public static final int PHASE_NONE = 0;
    public static final int PHASE_DYING_BLINK = 1;
    public static final int PHASE_IN_LIMBO = 2;
    public static final int PHASE_PRE_REBIRTH = 3;
    public static final int PHASE_POST_REBIRTH = 4;

    public static final Identifier PAYLOAD_ID = NebulaLimboMod.id("limbo_state");
    public static final CustomPacketPayload.Type<LimboStatePayload> TYPE = new CustomPacketPayload.Type<>(PAYLOAD_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, LimboStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LimboStatePayload::phase,
            ByteBufCodecs.VAR_INT, LimboStatePayload::durationTicks,
            ByteBufCodecs.VAR_INT, LimboStatePayload::elapsedTicks,
            ByteBufCodecs.BOOL, LimboStatePayload::lockF5DuringCinematic,
            LimboStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
