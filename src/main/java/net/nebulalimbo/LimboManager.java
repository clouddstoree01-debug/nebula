package net.nebulalimbo;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.nebulalimbo.network.LimboStatePayload;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LimboManager {
    private static final LimboManager INSTANCE = new LimboManager();

    // Exact frame-calibrated timings matching Video 1 & Video 2:
    public static final int DYING_BLINK_TICKS = 110;       // 5.5s: Collapse to sleeping pose + 2x slow blink + full blackout
    public static final int PRE_REBIRTH_TICKS = 95;        // 4.75s (00:09-00:14): 3rd-person orbit in Nebula + white flash climax
    public static final int POST_REBIRTH_TICKS = 75;       // 3.75s (00:15-00:18): Overworld beam + shockwave ring + zoom back to 1st-person

    // Default wait time in Limbo: 10 minutes (600s) as requested in Video 1 (configurable via `/hc limbo time 30`)
    private int defaultLimboMinutes = 10;
    private boolean preferLastDeathLocation = false;
    // By default, F5 is locked during cinematics so the player cannot break the 3rd-person orbit or 1st-person blink
    private boolean lockF5DuringCinematic = true;

    private final Map<UUID, PlayerLimboSession> sessions = new ConcurrentHashMap<>();

    public static LimboManager getInstance() {
        return INSTANCE;
    }

    public void setDefaultLimboMinutes(int minutes) {
        this.defaultLimboMinutes = Math.max(1, minutes);
    }

    public void setPreferLastDeathLocation(boolean preferLastDeathLocation) {
        this.preferLastDeathLocation = preferLastDeathLocation;
    }

    public void setLockF5DuringCinematic(boolean lockF5DuringCinematic) {
        this.lockF5DuringCinematic = lockF5DuringCinematic;
    }

    /**
     * Intercepts fatal damage before the vanilla "You Died!" screen appears.
     */
    public boolean onPlayerFatalDamage(ServerPlayer player, DamageSource source) {
        UUID uuid = player.getUUID();
        if (sessions.containsKey(uuid)) {
            player.setHealth(player.getMaxHealth());
            return false;
        }

        // Cancel vanilla death and preserve full health/inventory
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.setRemainingFireTicks(0);

        // Broadcast vanilla death message first (e.g. "Azrealon_ was slain by Zombie" in Frame 08)
        Component deathMsg = source.getLocalizedDeathMessage(player);
        if (player.level() instanceof ServerLevel sl) { sl.getServer().getPlayerList().broadcastSystemMessage(deathMsg, false); }

        // Save origin location (last death coordinates & dimension)
        ResourceKey<Level> deathDim = player.level().dimension();
        Vec3 deathPos = player.position();
        float deathYaw = player.getYRot();
        float deathPitch = player.getXRot();
        GameType previousGameMode = player.gameMode.getGameModeForPlayer();

        int totalLimboTicks = defaultLimboMinutes * 60 * 20;

        ServerBossEvent bossBar = new ServerBossEvent(
                Component.literal("✦ Rebirth in " + formatTime(totalLimboTicks / 20) + " ✦").withStyle(ChatFormatting.AQUA),
                BossEvent.BossBarColor.BLUE,
                BossEvent.BossBarOverlay.NOTCHED_10
        );
        bossBar.setProgress(1.0f);

        PlayerLimboSession session = new PlayerLimboSession(
                uuid,
                deathDim,
                deathPos,
                deathYaw,
                deathPitch,
                previousGameMode,
                totalLimboTicks,
                bossBar
        );
        sessions.put(uuid, session);

        // Put player into collapsed/sleeping pose on the ground during the 2x blink sequence (Video 1)
        player.setPose(Pose.SLEEPING);
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DYING_BLINK_TICKS + 20, 255, false, false, false));
        player.level().playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.0f, 0.6f);

        // Notify client to start Phase 1: Fall + 2x Eyelid Blink + Full Blackout
        sendState(player, LimboStatePayload.PHASE_DYING_BLINK, DYING_BLINK_TICKS, 0);
        return false;
    }

    public void tick(MinecraftServer server) {
        for (Map.Entry<UUID, PlayerLimboSession> entry : sessions.entrySet()) {
            UUID uuid = entry.getKey();
            PlayerLimboSession session = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;

            session.phaseTicks++;

            switch (session.phase) {
                case LimboStatePayload.PHASE_DYING_BLINK -> tickDyingBlink(server, player, session);
                case LimboStatePayload.PHASE_IN_LIMBO -> tickInLimbo(server, player, session);
                case LimboStatePayload.PHASE_PRE_REBIRTH -> tickPreRebirthCinematic(server, player, session);
                case LimboStatePayload.PHASE_POST_REBIRTH -> tickPostRebirthCinematic(server, player, session);
            }
        }
    }

    private void tickDyingBlink(MinecraftServer server, ServerPlayer player, PlayerLimboSession session) {
        // Keep player collapsed on the ground at death site
        player.setDeltaMovement(Vec3.ZERO);
        player.setPose(Pose.SLEEPING);

        if (session.phaseTicks >= DYING_BLINK_TICKS) {
            // Screen is now 100% black after the 2nd blink -> Teleport to Nebula Limbo dimension!
            enterNebulaLimbo(server, player, session);
        }
    }

    private void enterNebulaLimbo(MinecraftServer server, ServerPlayer player, PlayerLimboSession session) {
        session.phase = LimboStatePayload.PHASE_IN_LIMBO;
        session.phaseTicks = 0;

        ServerLevel nebulaLevel = server.getLevel(NebulaLimboMod.NEBULA_DIMENSION);
        if (nebulaLevel == null) {
            nebulaLevel = server.overworld();
        }

        // Build an invisible 11x11 barrier platform at (0, 99, 0) so the player stands in deep space
        BlockPos centerFloor = new BlockPos(0, 99, 0);
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                BlockPos pos = centerFloor.offset(dx, 0, dz);
                if (nebulaLevel.getBlockState(pos).isAir()) {
                    nebulaLevel.setBlockAndUpdate(pos, Blocks.BARRIER.defaultBlockState());
                }
            }
        }

        // Reset pose and switch to Adventure Mode (matching Frame 08 chat log: "Set own game mode to Adventure Mode")
        player.setPose(Pose.STANDING);
        player.setGameMode(GameType.ADVENTURE);

        TeleportTransition transition = new TeleportTransition(
                nebulaLevel,
                new Vec3(0.5, 100.0, 0.5),
                Vec3.ZERO,
                player.getYRot(),
                -25.0f, // Look slightly up toward the glowing cyan Nebula core
                TeleportTransition.DO_NOTHING
        );
        player.teleport(transition);

        // Show BossBar
        session.bossBar.addPlayer(player);

        // Broadcast Nebula message matching Frame 08: "✦ <Player> has fallen into the Nebula for X minutes." (Red/Coral text)
        Component nebulaMsg = Component.literal("✦ ")
                .withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(player.getScoreboardName() + " has fallen into the Nebula for " + defaultLimboMinutes + " minutes.")
                        .withStyle(ChatFormatting.RED));
        server.getPlayerList().broadcastSystemMessage(nebulaMsg, false);

        nebulaLevel.playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.AMBIENT, 1.0f, 0.7f);

        // Notify client to open eyes inside Nebula Limbo and render custom 360 Nebula skybox
        sendState(player, LimboStatePayload.PHASE_IN_LIMBO, session.remainingLimboTicks, 0);
    }

    private void tickInLimbo(MinecraftServer server, ServerPlayer player, PlayerLimboSession session) {
        session.remainingLimboTicks--;

        // Prevent falling into the void if player walks off the barrier platform
        if (player.getY() < 95.0) {
            player.teleportTo(0.5, 100.0, 0.5);
            player.setDeltaMovement(Vec3.ZERO);
        }

        // Update BossBar timer every second (20 ticks)
        if (session.remainingLimboTicks % 20 == 0 && session.remainingLimboTicks >= 0) {
            int secondsLeft = session.remainingLimboTicks / 20;
            session.bossBar.setName(
                    Component.literal("✦ Rebirth in " + formatTime(secondsLeft) + " ✦").withStyle(ChatFormatting.AQUA)
            );
            float progress = Math.clamp((float) session.remainingLimboTicks / (float) session.totalLimboTicks, 0.0f, 1.0f);
            session.bossBar.setProgress(progress);
        }

        // Spawn ambient floating cosmic particles in Limbo (matching Frame 08 & Frame 09: dark void motes + white diamond stars)
        if (session.phaseTicks % 4 == 0 && player.level() instanceof ServerLevel serverLevel) {
            Vec3 pos = player.position();
            serverLevel.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 3.5, pos.z, 3, 7.0, 4.5, 7.0, 0.008);
            serverLevel.sendParticles(ParticleTypes.SQUID_INK, pos.x, pos.y + 3.0, pos.z, 4, 6.5, 4.0, 6.5, 0.005);
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 2.0, pos.z, 6, 6.0, 4.0, 6.0, 0.02);
        }

        // When BossBar reaches 00:00 -> Start Phase 3: Pre-Rebirth 3rd-Person Orbit Cinematic!
        if (session.remainingLimboTicks <= 0) {
            triggerRebirthSequence(player);
        }
    }

    /**
     * Triggers the Rebirth Cinematic (either when BossBar reaches 00:00 or via `/hc limbo revive <player>`).
     */
    public boolean triggerRebirthSequence(ServerPlayer player) {
        PlayerLimboSession session = sessions.get(player.getUUID());
        if (session == null || session.phase == LimboStatePayload.PHASE_PRE_REBIRTH || session.phase == LimboStatePayload.PHASE_POST_REBIRTH) {
            return false;
        }

        session.phase = LimboStatePayload.PHASE_PRE_REBIRTH;
        session.phaseTicks = 0;

        // Update BossBar exactly as shown at 00:10 in Video 2 (Frame 11): "✦ The Nebula releases you ✦"
        session.bossBar.setName(Component.literal("✦ The Nebula releases you ✦").withStyle(ChatFormatting.AQUA));
        session.bossBar.setProgress(1.0f);
        if (!session.bossBar.getPlayers().contains(player)) {
            session.bossBar.addPlayer(player);
        }

        player.setNoGravity(true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 0.85f);

        // Notify client to lock F5, switch to 3rd-person camera orbit around the real player, and schedule fullscreen white flash
        sendState(player, LimboStatePayload.PHASE_PRE_REBIRTH, PRE_REBIRTH_TICKS, 0);
        return true;
    }

    private void tickPreRebirthCinematic(MinecraftServer server, ServerPlayer player, PlayerLimboSession session) {
        // Gentle upward levitation in the center of the Nebula while camera orbits the real player
        player.setDeltaMovement(0.0, 0.045, 0.0);
        player.hurtMarked = true;

        if (player.level() instanceof ServerLevel serverLevel) {
            Vec3 center = player.position().add(0.0, 1.0, 0.0);
            double progress = (double) session.phaseTicks / PRE_REBIRTH_TICKS;
            double radius = 2.5 - (progress * 1.3); // Swirling spherical shell tightening around player

            // Exact particle mix from Frame 10, 11, 12:
            // Bright white glowing diamonds (END_ROD + FIREWORK) + dark cosmic void clusters (SQUID_INK + REVERSE_PORTAL)
            for (int i = 0; i < 12; i++) {
                double angle = (session.phaseTicks * 0.26) + (i * Math.PI * 2.0 / 12.0);
                double yOffset = Math.sin(angle * 1.5 + session.phaseTicks * 0.12) * 1.65;
                double px = center.x + Math.cos(angle) * radius;
                double pz = center.z + Math.sin(angle) * radius;

                serverLevel.sendParticles(ParticleTypes.END_ROD, px, center.y + yOffset, pz, 2, 0.08, 0.08, 0.08, 0.015);
                serverLevel.sendParticles(ParticleTypes.SQUID_INK, px, center.y - yOffset * 0.8, pz, 2, 0.12, 0.12, 0.12, 0.01);
                if (i % 2 == 0) {
                    serverLevel.sendParticles(ParticleTypes.FIREWORK, px, center.y + yOffset * 0.5, pz, 1, 0.06, 0.06, 0.06, 0.02);
                }
            }
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 16, 1.1, 1.3, 1.1, 0.06);
        }

        // At tick 75 (when client screen hits pure white flash in Frame 14-15), play climax sound
        if (session.phaseTicks == PRE_REBIRTH_TICKS - 20) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.15f);
        }

        // At tick 95 (screen is 100% pure white), teleport player back to Bed / Last Death Location and start 00:15 Overworld Cinematic
        if (session.phaseTicks >= PRE_REBIRTH_TICKS) {
            teleportToRebirthDestination(server, player, session);
        }
    }

    private void teleportToRebirthDestination(MinecraftServer server, ServerPlayer player, PlayerLimboSession session) {
        session.phase = LimboStatePayload.PHASE_POST_REBIRTH;
        session.phaseTicks = 0;

        player.setNoGravity(false);
        player.setPose(Pose.STANDING);

        // Hide BossBar right as the player enters the Overworld (matching Frame 16-19 where BossBar is gone)
        session.bossBar.removeAllPlayers();

        // Determine target destination: Bed / Respawn Anchor OR Last Death Location
        ServerLevel targetLevel = null;
        Vec3 targetPos = null;
        float targetYaw = session.deathYaw;
        float targetPitch = 0.0f;

        if (!preferLastDeathLocation) {
            TeleportTransition respawnTransition = player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
            if (respawnTransition != null && respawnTransition.newLevel() != null && !respawnTransition.missingRespawnBlock()) {
                targetLevel = respawnTransition.newLevel();
                targetPos = respawnTransition.position();
                targetYaw = respawnTransition.yRot();
            }
        }

        // Fallback to exact last death location if no bed exists or if last_death_only is enabled
        if (targetLevel == null || targetPos == null) {
            targetLevel = server.getLevel(session.deathDimension);
            if (targetLevel == null) {
                targetLevel = server.overworld();
            }
            targetPos = session.deathPos;
        }

        TeleportTransition rebirthTeleport = new TeleportTransition(
                targetLevel,
                targetPos,
                Vec3.ZERO,
                targetYaw,
                targetPitch,
                TeleportTransition.DO_NOTHING
        );
        player.teleport(rebirthTeleport);

        // Restore Survival/previous GameMode and grant exact 3 buffs shown in top-right of Frame 18 & 19:
        // 1. Regeneration (Heart icon), 2. Speed (Wind/Boot icon), 3. Resistance (Shield icon)
        GameType restoreMode = (session.previousGameMode == GameType.ADVENTURE || session.previousGameMode == GameType.SPECTATOR)
                ? GameType.SURVIVAL : session.previousGameMode;
        player.setGameMode(restoreMode);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);

        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 15, 1));
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 15, 0));
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 10, 2));

        // Broadcast rebirth announcement matching Frame 18 & 19: "✦ <Player> has been reborn from the Nebula."
        Component rebornMsg = Component.literal("✦ ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(player.getScoreboardName() + " has been reborn from the Nebula.")
                        .withStyle(ChatFormatting.GREEN));
        server.getPlayerList().broadcastSystemMessage(rebornMsg, false);

        targetLevel.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.4f, 1.0f);

        // Trigger Phase 4 on client: White flash fades out into 3rd-person Overworld cinematic (00:15-00:18 in Video 2)
        sendState(player, LimboStatePayload.PHASE_POST_REBIRTH, POST_REBIRTH_TICKS, 0);
    }

    private void tickPostRebirthCinematic(MinecraftServer server, ServerPlayer player, PlayerLimboSession session) {
        // Keep player anchored at rebirth spot during the Overworld arrival cinematic
        player.setDeltaMovement(0.0, Math.min(0.0, player.getDeltaMovement().y), 0.0);

        if (player.level() instanceof ServerLevel serverLevel) {
            Vec3 center = player.position();

            // 1. Vertical celestial beam descending onto the player (Frame 16: END_ROD + dark SQUID_INK spiral)
            if (session.phaseTicks < 45) {
                for (double y = 0.4; y <= 9.5; y += 0.65) {
                    serverLevel.sendParticles(ParticleTypes.END_ROD, center.x, center.y + y, center.z, 1, 0.06, 0.08, 0.06, 0.005);
                    if (((int) (y * 2)) % 2 == 0) {
                        serverLevel.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y + y, center.z, 1, 0.12, 0.08, 0.12, 0.005);
                    }
                }
            }

            // 2. Expanding circular ground shockwave ring of END_ROD + central burst of green/yellow TOTEM_OF_UNDYING (Frame 16-19)
            double ringRadius = 0.7 + (session.phaseTicks * 0.13);
            int points = 40;
            for (int i = 0; i < points; i++) {
                double angle = (i * 2.0 * Math.PI) / points;
                double rx = center.x + Math.cos(angle) * ringRadius;
                double rz = center.z + Math.sin(angle) * ringRadius;
                serverLevel.sendParticles(ParticleTypes.END_ROD, rx, center.y + 0.35, rz, 1, 0.0, 0.01, 0.0, 0.0);
            }
            if (session.phaseTicks < 40) {
                serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, center.x, center.y + 1.0, center.z, 6, 0.6, 0.8, 0.6, 0.08);
            }
        }

        if (session.phaseTicks >= POST_REBIRTH_TICKS) {
            // Cinematic finished -> Unlock F5, return camera seamlessly to 1st-person (Frame 19), unlock movement!
            session.bossBar.removeAllPlayers();
            sessions.remove(player.getUUID());
            sendState(player, LimboStatePayload.PHASE_NONE, 0, 0);
        }
    }

    public void onPlayerDisconnect(ServerPlayer player) {
        PlayerLimboSession session = sessions.remove(player.getUUID());
        if (session != null) {
            session.bossBar.removeAllPlayers();
        }
    }

    private void sendState(ServerPlayer player, int phase, int durationTicks, int elapsedTicks) {
        ServerPlayNetworking.send(player, new LimboStatePayload(phase, durationTicks, elapsedTicks, lockF5DuringCinematic));
    }

    private static String formatTime(int totalSeconds) {
        int mins = Math.max(0, totalSeconds) / 60;
        int secs = Math.max(0, totalSeconds) % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    public static class PlayerLimboSession {
        public final UUID playerUuid;
        public final ResourceKey<Level> deathDimension;
        public final Vec3 deathPos;
        public final float deathYaw;
        public final float deathPitch;
        public final GameType previousGameMode;
        public final int totalLimboTicks;
        public int remainingLimboTicks;
        public final ServerBossEvent bossBar;

        public int phase = LimboStatePayload.PHASE_DYING_BLINK;
        public int phaseTicks = 0;

        public PlayerLimboSession(
                UUID playerUuid,
                ResourceKey<Level> deathDimension,
                Vec3 deathPos,
                float deathYaw,
                float deathPitch,
                GameType previousGameMode,
                int totalLimboTicks,
                ServerBossEvent bossBar
        ) {
            this.playerUuid = playerUuid;
            this.deathDimension = deathDimension;
            this.deathPos = deathPos;
            this.deathYaw = deathYaw;
            this.deathPitch = deathPitch;
            this.previousGameMode = previousGameMode;
            this.totalLimboTicks = totalLimboTicks;
            this.remainingLimboTicks = totalLimboTicks;
            this.bossBar = bossBar;
        }
    }
}
