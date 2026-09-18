package cn.tohsaka.factory.zstdnet.mixin;
import cn.tohsaka.factory.zstdnet.platform.RawHandshake;
import cn.tohsaka.factory.zstdnet.server.ForwardedAddress;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.server.network.NetHandlerHandshakeTCP;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(NetHandlerHandshakeTCP.class)
public abstract class ServerHandshakeMixin {
    @Shadow @Final private NetworkManager networkManager;
    @Inject(method = "processHandshake", at = @At("HEAD"))
    private void zstdnet$address(C00Handshake packet, CallbackInfo ci) {
        if (packet.getRequestedState() != EnumConnectionState.LOGIN) return;
        var address = ForwardedAddress.parse(networkManager.getRemoteAddress(), ((RawHandshake) packet).zstdnet$rawHost());
        if (address != null) {
            ((NetworkManagerAccessor) networkManager).zstdnet$setAddress(address);
            ((cn.tohsaka.factory.zstdnet.platform.ProxiedConnection) networkManager).zstdnet$setCompression(cn.tohsaka.factory.zstdnet.server.ServerProxyConfigFile.compressionThreshold());
        }
    }
}
