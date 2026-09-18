package cn.tohsaka.factory.zstdnet.mixin;
import cn.tohsaka.factory.zstdnet.platform.ProxiedConnection;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.NetHandlerLoginServer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(NetHandlerLoginServer.class)
public abstract class LoginCompressionMixin {
    @Shadow @Final public NetworkManager networkManager;
    // Select the announcement and whether compression is enabled. The separate vanilla
    // completion callback is covered by NetworkManagerMixin at the pipeline setter.
    @Redirect(method = "tryAcceptPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getNetworkCompressionThreshold()I"))
    private int zstdnet$threshold(MinecraftServer server) {
        Integer threshold = ((ProxiedConnection) networkManager).zstdnet$compression();
        return threshold == null ? server.getNetworkCompressionThreshold() : threshold;
    }
}
