package cn.tohsaka.factory.zstdnet;

import cn.tohsaka.factory.zstdnet.platform.Platform;
import cn.tohsaka.factory.zstdnet.platform.CommonProxy;
import cn.tohsaka.factory.zstdnet.server.ServerProxyBootstrap;
import com.github.luben.zstd.Zstd;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.*;

@Mod(modid = Zstdnet.MODID, name = "ZstdNet", version = "1.4.7-cleanroom-alpha.1",
     acceptedMinecraftVersions = "[1.12.2]", acceptableRemoteVersions = "*",
     guiFactory = "cn.tohsaka.factory.zstdnet.client.ConfigGuiFactory")
public final class Zstdnet {
    public static final String MODID = "zstdnet";
    @SidedProxy(clientSide = "cn.tohsaka.factory.zstdnet.client.ClientProxy", serverSide = "cn.tohsaka.factory.zstdnet.platform.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        Platform.initialize(event.getModConfigurationDirectory().toPath().getParent(), event.getSide().isClient());
        byte[] probe = "ZstdNet Java 25 native probe".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(probe, Zstd.decompress(Zstd.compress(probe), probe.length))) {
            throw new IllegalStateException("zstd-jni native roundtrip failed");
        }
        cn.tohsaka.factory.zstdnet.network.ControlNetwork.init();
        proxy.initialize();
    }
    @Mod.EventHandler public void started(FMLServerStartedEvent event) { ServerProxyBootstrap.startDedicated(); }
    @Mod.EventHandler public void starting(FMLServerStartingEvent event) { event.registerServerCommand(new cn.tohsaka.factory.zstdnet.server.ServerCommand()); }
    @Mod.EventHandler public void stopped(FMLServerStoppedEvent event) { ServerProxyBootstrap.stop(); }
}
