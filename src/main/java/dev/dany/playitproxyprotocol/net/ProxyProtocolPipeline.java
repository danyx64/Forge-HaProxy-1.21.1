package dev.dany.playitproxyprotocol.net;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

public final class ProxyProtocolPipeline {
    public static final String HANDLER_NAME = "playit_proxy_protocol";

    private ProxyProtocolPipeline() {
    }

    public static void inject(Channel channel) {
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(HANDLER_NAME) == null) {
            pipeline.addFirst(HANDLER_NAME, new ProxyProtocolDecoder());
        }
    }
}
