package dev.dany.playitproxyprotocol.mixin;

import dev.dany.playitproxyprotocol.net.ProxyProtocolPipeline;
import io.netty.channel.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.network.ServerConnectionListener$1", remap = false)
public abstract class ServerConnectionListenerMixin {
    @Inject(method = "initChannel(Lio/netty/channel/Channel;)V", at = @At("HEAD"), remap = false)
    private void playitproxyprotocol$injectProxyProtocolDecoder(Channel channel, CallbackInfo callbackInfo) {
        ProxyProtocolPipeline.inject(channel);
    }
}
