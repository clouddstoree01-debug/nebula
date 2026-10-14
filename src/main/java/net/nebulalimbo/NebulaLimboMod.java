package net.nebulalimbo;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.nebulalimbo.network.LimboStatePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class NebulaLimboMod implements ModInitializer {
    public static final String MOD_ID = "nebula_limbo";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final ResourceKey<Level> NEBULA_DIMENSION = ResourceKey.create(
            Registries.DIMENSION,
            id("nebula")
    );

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Nebula Limbo Rebirth Mod v1.1.0 for Minecraft 26.3 (Java 25)");

        // 1. Register S2C Custom Payload
        PayloadTypeRegistry.clientboundPlay().register(LimboStatePayload.TYPE, LimboStatePayload.CODEC);

        // 2. Intercept Fatal Player Damage (Prevent Vanilla Death Screen -> Trigger Collapse & 2x Blink)
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (entity instanceof ServerPlayer player) {
                return LimboManager.getInstance().onPlayerFatalDamage(player, damageSource);
            }
            return true;
        });

        // 3. Server Tick Loop for BossBar Countdown, Nebula Particles, and Rebirth Cinematics
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            LimboManager.getInstance().tick(server);
        });

        // 4. Clean up on disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LimboManager.getInstance().onPlayerDisconnect(handler.getPlayer());
        });

        // 5. Register Commands: `/hc limbo ...` (exact syntax from Video 2) and `/limbo ...`
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("hc")
                    .then(Commands.literal("limbo")
                            .then(Commands.literal("revive")
                                    .then(Commands.argument("targets", EntityArgument.players())
                                            .executes(NebulaLimboMod::executeRevive)))
                            .then(Commands.literal("time")
                                    .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 180))
                                            .executes(NebulaLimboMod::executeSetDuration)))
                            .then(Commands.literal("lock_f5")
                                    .then(Commands.argument("locked", BoolArgumentType.bool())
                                            .executes(NebulaLimboMod::executeSetLockF5)))
                            .then(Commands.literal("respawn_mode")
                                    .then(Commands.literal("bed_or_death")
                                            .executes(ctx -> setRespawnMode(ctx, false)))
                                    .then(Commands.literal("last_death_only")
                                            .executes(ctx -> setRespawnMode(ctx, true))))
                    ));

            dispatcher.register(Commands.literal("limbo")
                    .then(Commands.literal("revive")
                            .then(Commands.argument("targets", EntityArgument.players())
                                    .executes(NebulaLimboMod::executeRevive)))
                    .then(Commands.literal("time")
                            .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 180))
                                    .executes(NebulaLimboMod::executeSetDuration)))
                    .then(Commands.literal("lock_f5")
                            .then(Commands.argument("locked", BoolArgumentType.bool())
                                    .executes(NebulaLimboMod::executeSetLockF5)))
            );
        });
    }

    private static int executeRevive(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        int revivedCount = 0;
        for (ServerPlayer target : targets) {
            if (LimboManager.getInstance().triggerRebirthSequence(target)) {
                revivedCount++;
            }
        }
        final int count = revivedCount;
        context.getSource().sendSuccess(() -> Component.literal("Reviving " + count + " player(s) from the Nebula."), true);
        return count;
    }

    private static int executeSetDuration(CommandContext<CommandSourceStack> context) {
        int minutes = IntegerArgumentType.getInteger(context, "minutes");
        LimboManager.getInstance().setDefaultLimboMinutes(minutes);
        context.getSource().sendSuccess(() -> Component.literal("§b✦ Nebula Limbo wait duration set to " + minutes + " minute(s)."), true);
        return 1;
    }

    private static int executeSetLockF5(CommandContext<CommandSourceStack> context) {
        boolean locked = BoolArgumentType.getBool(context, "locked");
        LimboManager.getInstance().setLockF5DuringCinematic(locked);
        context.getSource().sendSuccess(() -> Component.literal("§b✦ F5 perspective lock during cinematic set to: " + locked), true);
        return 1;
    }

    private static int setRespawnMode(CommandContext<CommandSourceStack> context, boolean preferLastDeath) {
        LimboManager.getInstance().setPreferLastDeathLocation(preferLastDeath);
        String mode = preferLastDeath ? "Last Death Location" : "Bed / Respawn Anchor (fallback to Last Death)";
        context.getSource().sendSuccess(() -> Component.literal("§b✦ Nebula Rebirth teleport target set to: " + mode), true);
        return 1;
    }
}
