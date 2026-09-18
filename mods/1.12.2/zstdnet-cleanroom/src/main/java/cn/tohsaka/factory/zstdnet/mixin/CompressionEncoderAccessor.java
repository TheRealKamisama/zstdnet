package cn.tohsaka.factory.zstdnet.mixin;
import net.minecraft.network.NettyCompressionEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(NettyCompressionEncoder.class)
public interface CompressionEncoderAccessor {
    @Accessor("threshold") int zstdnet$threshold();
}
