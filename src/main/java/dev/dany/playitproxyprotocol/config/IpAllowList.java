package dev.dany.playitproxyprotocol.config;

import com.google.common.net.InetAddresses;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public final class IpAllowList {
    public static final IpAllowList ALLOW_ALL = new IpAllowList(true, List.of());

    private final boolean allowAll;
    private final List<Network> networks;

    private IpAllowList(boolean allowAll, List<Network> networks) {
        this.allowAll = allowAll;
        this.networks = networks;
    }

    public static IpAllowList parse(List<? extends String> entries) {
        boolean allowAll = false;
        List<Network> networks = new ArrayList<>();

        for (String rawEntry : entries) {
            String entry = rawEntry.trim();
            if (entry.equals("*")) {
                allowAll = true;
            } else {
                networks.add(Network.parse(entry));
            }
        }

        if (allowAll) {
            return ALLOW_ALL;
        }

        return new IpAllowList(false, List.copyOf(networks));
    }

    public static boolean isValidEntry(Object rawEntry) {
        if (!(rawEntry instanceof String entry) || entry.isBlank()) {
            return false;
        }

        String trimmed = entry.trim();
        if (trimmed.equals("*")) {
            return true;
        }

        try {
            Network.parse(trimmed);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public boolean matches(InetAddress address) {
        if (allowAll) {
            return true;
        }

        for (Network network : networks) {
            if (network.matches(address)) {
                return true;
            }
        }

        return false;
    }

    private record Network(byte[] address, int prefixLength) {
        private static Network parse(String raw) {
            String[] parts = raw.split("/", -1);
            if (parts.length > 2 || parts[0].isBlank()) {
                throw new IllegalArgumentException("Invalid IP/CIDR entry: " + raw);
            }

            InetAddress parsedAddress = InetAddresses.forString(parts[0]);
            byte[] addressBytes = parsedAddress.getAddress();
            int bitLength = addressBytes.length * 8;
            int prefixLength = bitLength;

            if (parts.length == 2) {
                if (parts[1].isBlank()) {
                    throw new IllegalArgumentException("Missing CIDR prefix length: " + raw);
                }

                try {
                    prefixLength = Integer.parseInt(parts[1]);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Invalid CIDR prefix length: " + raw, exception);
                }

                if (prefixLength < 0 || prefixLength > bitLength) {
                    throw new IllegalArgumentException("CIDR prefix out of range: " + raw);
                }
            }

            byte[] networkAddress = addressBytes.clone();
            clearHostBits(networkAddress, prefixLength);
            return new Network(networkAddress, prefixLength);
        }

        private boolean matches(InetAddress candidate) {
            byte[] candidateAddress = candidate.getAddress();
            if (candidateAddress.length != address.length) {
                return false;
            }

            return prefixMatches(address, candidateAddress, prefixLength);
        }

        private static boolean prefixMatches(byte[] expected, byte[] candidate, int prefixLength) {
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (expected[i] != candidate[i]) {
                    return false;
                }
            }

            if (remainingBits == 0) {
                return true;
            }

            int mask = 0xFF << (8 - remainingBits);
            return (expected[fullBytes] & mask) == (candidate[fullBytes] & mask);
        }

        private static void clearHostBits(byte[] address, int prefixLength) {
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            int firstHostByte = fullBytes;

            if (remainingBits != 0) {
                int mask = 0xFF << (8 - remainingBits);
                address[fullBytes] = (byte) (address[fullBytes] & mask);
                firstHostByte++;
            }

            for (int i = firstHostByte; i < address.length; i++) {
                address[i] = 0;
            }
        }
    }
}
