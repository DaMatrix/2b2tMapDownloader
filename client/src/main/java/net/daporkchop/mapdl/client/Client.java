package net.daporkchop.mapdl.client;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(
        modid = Client.MOD_ID,
        name = Client.MOD_NAME,
        version = Client.VERSION,
        dependencies = "required-after:depmanager@[0.0.1,);"
)
public class Client {

    public static final String MOD_ID = "client";
    public static final String MOD_NAME = "2b2t Map Downloader";
    public static final String VERSION = "0.0.1";

    @Mod.Instance(MOD_ID)
    public static Client INSTANCE;

    @Mod.EventHandler
    public void preinit(FMLPreInitializationEvent event) {

    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {

    }

    @Mod.EventHandler
    public void postinit(FMLPostInitializationEvent event) {

    }
}
