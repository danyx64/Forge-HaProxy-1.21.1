package dev.dany.playitproxyprotocol;

import com.mojang.logging.LogUtils;
import dev.dany.playitproxyprotocol.config.PlayitProxyProtocolConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(PlayitProxyProtocol.MODID)
public final class PlayitProxyProtocol {
    public static final String MODID = "playitproxyprotocol";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PlayitProxyProtocol(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, PlayitProxyProtocolConfig.SPEC, "playitproxyprotocol.toml");
        LOGGER.info("PlayitProxyProtocol loaded. Waiting for inbound server connections.");
    }
}
