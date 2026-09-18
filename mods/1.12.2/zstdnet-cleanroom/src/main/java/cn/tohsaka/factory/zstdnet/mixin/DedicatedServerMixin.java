package cn.tohsaka.factory.zstdnet.mixin;
import cn.tohsaka.factory.zstdnet.server.DedicatedServerAutoPort;
import net.minecraft.network.NetworkSystem;
import net.minecraft.server.dedicated.DedicatedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.io.IOException;
import java.net.InetAddress;

@Mixin(DedicatedServer.class)
public abstract class DedicatedServerMixin {
    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkSystem;addEndpoint(Ljava/net/InetAddress;I)V"))
    private void zstdnet$bind(NetworkSystem network, InetAddress address, int port) throws IOException {
        var endpoint = DedicatedServerAutoPort.prepare(address, port);
        network.addEndpoint(endpoint.getAddress(), endpoint.getPort());
    }
}
