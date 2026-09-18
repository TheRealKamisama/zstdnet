package cn.tohsaka.factory.zstdnet.mixin;
import cn.tohsaka.factory.zstdnet.platform.ProxiedConnection;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
@Mixin(NetworkManager.class)
public abstract class NetworkManagerMixin implements ProxiedConnection {
    @Unique private Integer zstdnet$threshold;
    @Override public void zstdnet$setCompression(int threshold) { zstdnet$threshold = threshold; }
    @Override public Integer zstdnet$compression() { return zstdnet$threshold; }
    // NetHandlerLoginServer$1 re-reads server.properties in its completion callback.
    // Override at the pipeline setter too, so that callback installs the announced value.
    @ModifyVariable(method = "setCompressionThreshold", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int zstdnet$pipelineThreshold(int requested) {
        return zstdnet$threshold == null ? requested : zstdnet$threshold;
    }
}
