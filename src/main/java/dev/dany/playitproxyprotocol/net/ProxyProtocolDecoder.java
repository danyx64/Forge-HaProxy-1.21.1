package dev.dany.playitproxyprotocol.net;

import com.google.common.net.InetAddresses;
import dev.dany.playitproxyprotocol.PlayitProxyProtocol;
import dev.dany.playitproxyprotocol.config.PlayitProxyProtocolConfig;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public final class ProxyProtocolDecoder extends ByteToMessageDecoder {
    private static final byte[] V1_PREFIX = "PROXY ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] V2_SIGNATURE = new byte[]{
            '\r', '\n', '\r', '\n', 0, '\r', '\n', 'Q', 'U', 'I', 'T', '\n'
    };
    private static final int V1_MAX_HEADER_LENGTH = 108;
    private static final int V2_BASE_HEADER_LENGTH = 16;

    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf input, List<Object> output) throws Exception {
        if (!PlayitProxyProtocolConfig.enabled()) {
            passThrough(context, input, output);
            return;
        }

        HeaderDetection detection = detectHeader(input);
        switch (detection.kind()) {
            case NEED_MORE -> {
            }
            case REGULAR -> handleRegularConnection(context, input, output);
            case MALFORMED -> reject(context, input, detection.reason());
            case V1 -> handleV1(context, input, output);
            case V2 -> handleV2(context, input, output);
        }
    }

    private void handleRegularConnection(ChannelHandlerContext context, ByteBuf input, List<Object> output) {
        if (PlayitProxyProtocolConfig.blockRegularConnections()) {
            reject(context, input, "missing Proxy Protocol header and blockRegularConnections=true");
            return;
        }

        PlayitProxyProtocol.LOGGER.debug(
                "Accepting regular connection without Proxy Protocol from {}",
                formatAddress(context.channel().remoteAddress())
        );
        passThrough(context, input, output);
    }

    private void handleV1(ChannelHandlerContext context, ByteBuf input, List<Object> output) throws Exception {
        if (!PlayitProxyProtocolConfig.protocolMode().allowsV1()) {
            reject(context, input, "Proxy Protocol v1 header received while protocolVersion=" + PlayitProxyProtocolConfig.protocolMode().configName());
            return;
        }

        int startIndex = input.readerIndex();
        int lineFeedIndex = findLineFeed(input, V1_MAX_HEADER_LENGTH);
        int headerLength = lineFeedIndex - startIndex + 1;
        if (headerLength > V1_MAX_HEADER_LENGTH) {
            reject(context, input, "Proxy Protocol v1 header is too long");
            return;
        }

        if (input.getByte(lineFeedIndex - 1) != '\r') {
            reject(context, input, "Proxy Protocol v1 header does not end with CRLF");
            return;
        }

        String header = input.toString(startIndex, headerLength - 2, StandardCharsets.US_ASCII);
        String[] parts = header.split(" ");
        if (parts.length != 6 || !parts[0].equals("PROXY")) {
            reject(context, input, "malformed Proxy Protocol v1 header");
            return;
        }

        String transport = parts[1].toUpperCase(Locale.ROOT);
        if (!transport.equals("TCP4") && !transport.equals("TCP6")) {
            reject(context, input, "unsupported Proxy Protocol v1 transport: " + parts[1]);
            return;
        }

        InetAddress sourceAddress;
        InetAddress destinationAddress;
        int sourcePort;
        int destinationPort;
        try {
            sourceAddress = InetAddresses.forString(parts[2]);
            destinationAddress = InetAddresses.forString(parts[3]);
            sourcePort = parsePort(parts[4]);
            destinationPort = parsePort(parts[5]);
        } catch (IllegalArgumentException exception) {
            reject(context, input, "invalid Proxy Protocol v1 address or port");
            return;
        }

        int expectedBytes = transport.equals("TCP4") ? 4 : 16;
        if (sourceAddress.getAddress().length != expectedBytes || destinationAddress.getAddress().length != expectedBytes) {
            reject(context, input, "Proxy Protocol v1 " + transport + " address family mismatch");
            return;
        }

        acceptProxyAddress(context, input, output, "v1", sourceAddress, sourcePort, headerLength);
        PlayitProxyProtocol.LOGGER.debug("Proxy Protocol v1 destination was {}:{}", destinationAddress.getHostAddress(), destinationPort);
    }

    private void handleV2(ChannelHandlerContext context, ByteBuf input, List<Object> output) throws Exception {
        if (!PlayitProxyProtocolConfig.protocolMode().allowsV2()) {
            reject(context, input, "Proxy Protocol v2 header received while protocolVersion=" + PlayitProxyProtocolConfig.protocolMode().configName());
            return;
        }

        int startIndex = input.readerIndex();
        int versionAndCommand = input.getUnsignedByte(startIndex + 12);
        int version = (versionAndCommand & 0xF0) >>> 4;
        int command = versionAndCommand & 0x0F;
        int familyAndProtocol = input.getUnsignedByte(startIndex + 13);
        int family = (familyAndProtocol & 0xF0) >>> 4;
        int transport = familyAndProtocol & 0x0F;
        int payloadLength = input.getUnsignedShort(startIndex + 14);
        int headerLength = V2_BASE_HEADER_LENGTH + payloadLength;

        if (input.readableBytes() < headerLength) {
            return;
        }

        if (version != 2) {
            reject(context, input, "invalid Proxy Protocol v2 version: " + version);
            return;
        }

        if (command != 1) {
            reject(context, input, "unsupported Proxy Protocol v2 command: " + command);
            return;
        }

        if (transport != 1) {
            reject(context, input, "unsupported Proxy Protocol v2 transport: " + transport);
            return;
        }

        InetAddress sourceAddress;
        int sourcePort;
        if (family == 1) {
            if (payloadLength < 12) {
                reject(context, input, "Proxy Protocol v2 IPv4 payload is too short");
                return;
            }

            sourceAddress = InetAddress.getByAddress(readBytes(input, startIndex + 16, 4));
            sourcePort = input.getUnsignedShort(startIndex + 16 + 8);
        } else if (family == 2) {
            if (payloadLength < 36) {
                reject(context, input, "Proxy Protocol v2 IPv6 payload is too short");
                return;
            }

            sourceAddress = InetAddress.getByAddress(readBytes(input, startIndex + 16, 16));
            sourcePort = input.getUnsignedShort(startIndex + 16 + 32);
        } else {
            reject(context, input, "unsupported Proxy Protocol v2 address family: " + family);
            return;
        }

        acceptProxyAddress(context, input, output, "v2", sourceAddress, sourcePort, headerLength);
    }

    private void acceptProxyAddress(
            ChannelHandlerContext context,
            ByteBuf input,
            List<Object> output,
            String protocol,
            InetAddress realAddress,
            int realPort,
            int headerLength
    ) {
        SocketAddress proxyAddress = context.channel().remoteAddress();
        PlayitProxyProtocol.LOGGER.info(
                "Detected Proxy Protocol {} from proxy {}. Real player address is {}:{}",
                protocol,
                formatAddress(proxyAddress),
                realAddress.getHostAddress(),
                realPort
        );

        if (!PlayitProxyProtocolConfig.isProxySourceAllowed(proxyAddress)) {
            reject(context, input, "proxy source is not allowed by onlyAllowFrom: " + formatAddress(proxyAddress));
            return;
        }

        input.skipBytes(headerLength);
        InetSocketAddress realRemoteAddress = new InetSocketAddress(realAddress, realPort);
        if (!ProxyAddressUpdater.apply(context, realRemoteAddress)) {
            reject(context, input, "failed to update Minecraft remote address after Proxy Protocol parsing");
            return;
        }

        passThrough(context, input, output);
    }

    private HeaderDetection detectHeader(ByteBuf input) {
        if (!input.isReadable()) {
            return HeaderDetection.needMore();
        }

        if (matchesCompletePrefix(input, V2_SIGNATURE)) {
            if (input.readableBytes() < V2_BASE_HEADER_LENGTH) {
                return HeaderDetection.needMore();
            }
            return HeaderDetection.v2();
        }

        if (matchesPartialPrefix(input, V2_SIGNATURE)) {
            return HeaderDetection.needMore();
        }

        if (matchesCompletePrefix(input, V1_PREFIX)) {
            int lineFeedIndex = findLineFeed(input, V1_MAX_HEADER_LENGTH);
            if (lineFeedIndex >= 0) {
                return HeaderDetection.v1();
            }

            if (input.readableBytes() > V1_MAX_HEADER_LENGTH) {
                return HeaderDetection.malformed("Proxy Protocol v1 header exceeded " + V1_MAX_HEADER_LENGTH + " bytes");
            }

            return HeaderDetection.needMore();
        }

        if (matchesPartialPrefix(input, V1_PREFIX)) {
            return HeaderDetection.needMore();
        }

        return HeaderDetection.regular();
    }

    private void passThrough(ChannelHandlerContext context, ByteBuf input, List<Object> output) {
        if (context.pipeline().context(this) != null) {
            context.pipeline().remove(this);
        }

        if (input.isReadable()) {
            output.add(input.readBytes(input.readableBytes()));
        }
    }

    private void reject(ChannelHandlerContext context, ByteBuf input, String reason) {
        RejectionLogger.log(context.channel().remoteAddress(), reason);
        input.skipBytes(input.readableBytes());
        context.close();
    }

    private static boolean matchesCompletePrefix(ByteBuf input, byte[] prefix) {
        if (input.readableBytes() < prefix.length) {
            return false;
        }

        int startIndex = input.readerIndex();
        for (int i = 0; i < prefix.length; i++) {
            if (input.getByte(startIndex + i) != prefix[i]) {
                return false;
            }
        }

        return true;
    }

    private static boolean matchesPartialPrefix(ByteBuf input, byte[] prefix) {
        int readable = input.readableBytes();
        if (readable >= prefix.length) {
            return false;
        }

        int startIndex = input.readerIndex();
        for (int i = 0; i < readable; i++) {
            if (input.getByte(startIndex + i) != prefix[i]) {
                return false;
            }
        }

        return true;
    }

    private static int findLineFeed(ByteBuf input, int maxLength) {
        int startIndex = input.readerIndex();
        int bytesToInspect = Math.min(input.readableBytes(), maxLength);
        for (int i = 0; i < bytesToInspect; i++) {
            if (input.getByte(startIndex + i) == '\n') {
                return startIndex + i;
            }
        }
        return -1;
    }

    private static int parsePort(String rawPort) {
        int port = Integer.parseInt(rawPort);
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port out of range: " + rawPort);
        }
        return port;
    }

    private static byte[] readBytes(ByteBuf input, int index, int length) {
        byte[] bytes = new byte[length];
        input.getBytes(index, bytes);
        return bytes;
    }

    private static String formatAddress(SocketAddress address) {
        if (address instanceof InetSocketAddress inetSocketAddress) {
            InetAddress inetAddress = inetSocketAddress.getAddress();
            String host = inetAddress == null ? inetSocketAddress.getHostString() : inetAddress.getHostAddress();
            return host + ":" + inetSocketAddress.getPort();
        }

        return String.valueOf(address);
    }

    private enum HeaderKind {
        NEED_MORE,
        REGULAR,
        MALFORMED,
        V1,
        V2
    }

    private record HeaderDetection(HeaderKind kind, String reason) {
        private static HeaderDetection needMore() {
            return new HeaderDetection(HeaderKind.NEED_MORE, "");
        }

        private static HeaderDetection regular() {
            return new HeaderDetection(HeaderKind.REGULAR, "");
        }

        private static HeaderDetection malformed(String reason) {
            return new HeaderDetection(HeaderKind.MALFORMED, reason);
        }

        private static HeaderDetection v1() {
            return new HeaderDetection(HeaderKind.V1, "");
        }

        private static HeaderDetection v2() {
            return new HeaderDetection(HeaderKind.V2, "");
        }
    }
}
