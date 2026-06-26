package dev.dany.playitproxyprotocol.net;

import dev.dany.playitproxyprotocol.PlayitProxyProtocol;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class RejectionLogger {
    private static final long WARN_INTERVAL_MILLIS = 60_000L;
    private static final ConcurrentMap<String, RejectionState> STATES = new ConcurrentHashMap<>();

    private RejectionLogger() {
    }

    static void log(SocketAddress remoteAddress, String reason) {
        String remote = formatAddress(remoteAddress);
        String key = groupKey(remoteAddress, reason);
        RejectionState state = STATES.computeIfAbsent(key, ignored -> new RejectionState());

        long now = System.currentTimeMillis();
        long suppressed;
        boolean warn;
        synchronized (state) {
            if (state.lastWarnMillis == 0L || now - state.lastWarnMillis >= WARN_INTERVAL_MILLIS) {
                warn = true;
                suppressed = state.suppressed;
                state.suppressed = 0L;
                state.lastWarnMillis = now;
            } else {
                warn = false;
                suppressed = 0L;
                state.suppressed++;
            }
        }

        if (warn) {
            if (suppressed > 0L) {
                PlayitProxyProtocol.LOGGER.warn(
                        "Rejecting connection from {}: {} ({} similar rejection(s) suppressed in the last {}s)",
                        remote,
                        reason,
                        suppressed,
                        WARN_INTERVAL_MILLIS / 1000L
                );
            } else {
                PlayitProxyProtocol.LOGGER.warn("Rejecting connection from {}: {}", remote, reason);
            }
        }
    }

    static String formatAddress(SocketAddress address) {
        if (address instanceof InetSocketAddress inetSocketAddress) {
            InetAddress inetAddress = inetSocketAddress.getAddress();
            String host = inetAddress == null ? inetSocketAddress.getHostString() : inetAddress.getHostAddress();
            return host + ":" + inetSocketAddress.getPort();
        }

        return String.valueOf(address);
    }

    private static String groupKey(SocketAddress address, String reason) {
        return hostOnly(address) + "|" + reason;
    }

    private static String hostOnly(SocketAddress address) {
        if (address instanceof InetSocketAddress inetSocketAddress) {
            InetAddress inetAddress = inetSocketAddress.getAddress();
            return inetAddress == null ? inetSocketAddress.getHostString() : inetAddress.getHostAddress();
        }

        return String.valueOf(address);
    }

    private static final class RejectionState {
        private long lastWarnMillis;
        private long suppressed;
    }
}
