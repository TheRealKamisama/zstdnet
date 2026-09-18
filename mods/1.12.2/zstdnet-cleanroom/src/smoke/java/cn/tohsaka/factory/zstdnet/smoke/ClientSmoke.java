package cn.tohsaka.factory.zstdnet.smoke;

import cn.tohsaka.factory.zstdnet.client.ClientProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;

/** Opt-in runClient driver: three real FML logins with world/chunk ticks. Excluded from jars. */
@Mod(modid = "zstdnetsmoke", name = "ZstdNet smoke driver", version = "1", acceptableRemoteVersions = "*", clientSideOnly = true)
public final class ClientSmoke {
    private long deadline;
    private int rounds;
    private int ticks;
    private boolean joining;
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        if (!event.getSide().isClient()) return;
        deadline = System.currentTimeMillis() + 180_000;
        MinecraftForge.EVENT_BUS.register(this);
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (System.currentTimeMillis() > deadline || mc.currentScreen instanceof GuiDisconnected) {
            LogManager.getLogger().error("ZSTDNET_SMOKE_FAILED round={} screen={}", rounds, mc.currentScreen);
            mc.shutdown();
            return;
        }
        if (!joining && mc.currentScreen instanceof GuiMainMenu) {
            joining = true;
            String target = System.getProperty("zstdnet.smokeHost");
            if (rounds == 0) {
                // The server-list constructor must enter through the normal GUI mixin.
                mc.displayGuiScreen(new net.minecraft.client.multiplayer.GuiConnecting(new GuiMainMenu(), mc,
                    new net.minecraft.client.multiplayer.ServerData("ZstdNet smoke", target, false)));
            } else if (rounds == 1) {
                var address = net.minecraft.client.multiplayer.ServerAddress.fromString(target);
                mc.displayGuiScreen(new net.minecraft.client.multiplayer.GuiConnecting(new GuiMainMenu(), mc, address.getIP(), address.getPort()));
            } else ClientProxy.connect(target, new GuiMainMenu());
        }
        if (joining && mc.player != null && mc.world != null && mc.world.isBlockLoaded(mc.player.getPosition())) {
            if (++ticks < 100) return;
            LogManager.getLogger().info("ZSTDNET_SMOKE_JOINED round={} player={} dimension={} position={}", ++rounds, mc.player.getName(), mc.player.dimension, mc.player.getPosition());
            mc.world.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
            mc.displayGuiScreen(new GuiMainMenu());
            ClientProxy.close();
            ticks = 0;
            joining = false;
            if (rounds >= 3) {
                LogManager.getLogger().info("ZSTDNET_SMOKE_SUCCESS three logins and loaded chunk ticks");
                mc.shutdown();
            }
        }
    }
}
