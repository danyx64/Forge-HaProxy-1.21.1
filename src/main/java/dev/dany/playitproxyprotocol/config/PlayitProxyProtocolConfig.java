package dev.dany.playitproxyprotocol.config;

import dev.dany.playitproxyprotocol.PlayitProxyProtocol;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = PlayitProxyProtocol.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PlayitProxyProtocolConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Enable HAProxy Proxy Protocol support.")
            .define("enabled", true);

    private static final ForgeConfigSpec.BooleanValue BLOCK_REGULAR_CONNECTIONS = BUILDER
            .comment("When true, inbound TCP connections without a Proxy Protocol header are rejected.")
            .define("blockRegularConnections", true);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ONLY_ALLOW_FROM = BUILDER
            .comment("Proxy source IPs allowed to send Proxy Protocol. Use [\"*\"] to allow any proxy, or IP/CIDR entries.")
            .defineList("onlyAllowFrom", List.of("*"), IpAllowList::isValidEntry);

    private static final ForgeConfigSpec.ConfigValue<String> PROTOCOL_VERSION = BUILDER
            .comment("Accepted Proxy Protocol versions: both, v1, or v2.")
            .define("protocolVersion", "both", ProtocolMode::isValidConfigValue);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private static volatile boolean enabled = true;
    private static volatile boolean blockRegularConnections = true;
    private static volatile IpAllowList onlyAllowFrom = IpAllowList.ALLOW_ALL;
    private static volatile ProtocolMode protocolMode = ProtocolMode.BOTH;

    private PlayitProxyProtocolConfig() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static boolean blockRegularConnections() {
        return blockRegularConnections;
    }

    public static ProtocolMode protocolMode() {
        return protocolMode;
    }

    public static boolean isProxySourceAllowed(SocketAddress remoteAddress) {
        if (!(remoteAddress instanceof InetSocketAddress inetSocketAddress)) {
            return false;
        }

        InetAddress address = inetSocketAddress.getAddress();
        return address != null && onlyAllowFrom.matches(address);
    }

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }

        enabled = ENABLED.get();
        blockRegularConnections = BLOCK_REGULAR_CONNECTIONS.get();
        protocolMode = ProtocolMode.fromConfig(PROTOCOL_VERSION.get());

        try {
            onlyAllowFrom = IpAllowList.parse(ONLY_ALLOW_FROM.get());
        } catch (IllegalArgumentException exception) {
            onlyAllowFrom = IpAllowList.ALLOW_ALL;
            PlayitProxyProtocol.LOGGER.warn("Invalid onlyAllowFrom config, falling back to [\"*\"]", exception);
        }

        PlayitProxyProtocol.LOGGER.info(
                "PlayitProxyProtocol config loaded: enabled={}, blockRegularConnections={}, protocolVersion={}",
                enabled,
                blockRegularConnections,
                protocolMode.configName()
        );
    }

    public enum ProtocolMode {
        BOTH("both"),
        V1("v1"),
        V2("v2");

        private final String configName;

        ProtocolMode(String configName) {
            this.configName = configName;
        }

        public String configName() {
            return configName;
        }

        public boolean allowsV1() {
            return this == BOTH || this == V1;
        }

        public boolean allowsV2() {
            return this == BOTH || this == V2;
        }

        private static ProtocolMode fromConfig(String raw) {
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "v1" -> V1;
                case "v2" -> V2;
                default -> BOTH;
            };
        }

        private static boolean isValidConfigValue(Object raw) {
            if (!(raw instanceof String value)) {
                return false;
            }

            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "both", "v1", "v2" -> true;
                default -> false;
            };
        }
    }
}
