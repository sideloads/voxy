package me.cortex.voxy.common.world;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.commonImpl.IWorldGetIdentifier;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.azureaaron.hmapi.events.HypixelPacketEvents;
import net.azureaaron.hmapi.network.HypixelNetworking;
import net.azureaaron.hmapi.network.packet.s2c.ErrorS2CPacket;
import net.azureaaron.hmapi.network.packet.v1.s2c.LocationUpdateS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.Util;

import java.util.Optional;

public class SkyblockListener {

    private static boolean onHypixel = false;
    private static String currentSkyblockMode = "";

    public static String getCurrentContext() {
        return onHypixel ? currentSkyblockMode : "";
    }

    public static void init() {
        HypixelNetworking.registerToEvents(Util.make(new Object2IntOpenHashMap<>(), map -> {
            map.put(LocationUpdateS2CPacket.ID, 1);
        }));

        HypixelPacketEvents.HELLO.register(packet -> {
            onHypixel = true;
            currentSkyblockMode = "";
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            onHypixel = false;
            currentSkyblockMode = "";
        });

        HypixelPacketEvents.LOCATION_UPDATE.register(packet -> {
            if (packet instanceof ErrorS2CPacket) {
                Logger.error("Hypixel Packet Error: " + ((ErrorS2CPacket) packet).reason());
                return;
            }

            if (packet instanceof LocationUpdateS2CPacket loc) {
                Optional<String> modeOpt = loc.mode();
                Optional<String> serverTypeOpt = loc.serverType();

                boolean isSkyblock = serverTypeOpt.isPresent() && serverTypeOpt.get().equalsIgnoreCase("SKYBLOCK");

                if (isSkyblock && modeOpt.isPresent()) {
                    String newMode = modeOpt.get();

                    if (!newMode.equals(currentSkyblockMode)) {
                        currentSkyblockMode = newMode;
                        // todo: remove extra verbosity
                        Logger.info("Voxy Hypixel Integration: Detected Skyblock Mode: " + newMode);
                        updateVoxyContext();
                    }
                } else {
                    currentSkyblockMode = "";
                }
            }
        });
    }

    private static void updateVoxyContext() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        mc.execute(() -> {
            ClientLevel level = mc.level;

            WorldIdentifier currentId = ((IWorldGetIdentifier) level).voxy$getIdentifier();
            if (currentId == null) return;

            WorldIdentifier newId = new WorldIdentifier(
                    currentId.key,
                    currentId.biomeSeed,
                    currentId.dimension,
                    currentSkyblockMode
            );

            ((IWorldGetIdentifier) level).voxy$setIdentifier(newId);

            // big yike
            IGetVoxyRenderSystem rendererAccessor = (IGetVoxyRenderSystem) mc.levelRenderer;
            rendererAccessor.shutdownRenderer();

            // slightly hacky
            rendererAccessor.createRenderer();

            Logger.info("Voxy: Reloaded renderer for Skyblock context: " + currentSkyblockMode);
        });
    }
}