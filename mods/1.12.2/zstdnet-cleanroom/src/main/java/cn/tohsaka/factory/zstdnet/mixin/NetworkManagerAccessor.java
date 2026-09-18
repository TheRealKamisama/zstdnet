package cn.tohsaka.factory.zstdnet.mixin;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.net.SocketAddress;
@Mixin(NetworkManager.class)
public interface NetworkManagerAccessor {
    @Accessor("socketAddress") void zstdnet$setAddress(SocketAddress address);
}
