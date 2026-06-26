package dev.dany.playitproxyprotocol.net;

import dev.dany.playitproxyprotocol.PlayitProxyProtocol;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import net.minecraft.network.Connection;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;

public final class ProxyAddressUpdater {
    public static final AttributeKey<InetSocketAddress> REAL_REMOTE_ADDRESS =
            AttributeKey.valueOf(PlayitProxyProtocol.MODID + ":real_remote_address");

    private static final Field CONNECTION_ADDRESS_FIELD = findConnectionAddressField();

    private ProxyAddressUpdater() {
    }

    public static boolean apply(ChannelHandlerContext context, InetSocketAddress realRemoteAddress) {
        Channel channel = context.channel();
        channel.attr(REAL_REMOTE_ADDRESS).set(realRemoteAddress);

        Connection connection = context.pipeline().get(Connection.class);
        if (connection == null) {
            PlayitProxyProtocol.LOGGER.warn("Proxy Protocol parsed but Minecraft packet_handler was not present in the pipeline");
            return false;
        }

        if (CONNECTION_ADDRESS_FIELD == null) {
            PlayitProxyProtocol.LOGGER.warn("Proxy Protocol parsed but Connection.address could not be accessed");
            return false;
        }

        try {
            CONNECTION_ADDRESS_FIELD.set(connection, realRemoteAddress);
            return true;
        } catch (IllegalAccessException exception) {
            PlayitProxyProtocol.LOGGER.warn("Failed to update Minecraft remote address to {}", realRemoteAddress, exception);
            return false;
        }
    }

    private static Field findConnectionAddressField() {
        try {
            Field field = Connection.class.getDeclaredField("address");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            PlayitProxyProtocol.LOGGER.warn("Unable to find net.minecraft.network.Connection.address", exception);
            return null;
        }
    }
}
