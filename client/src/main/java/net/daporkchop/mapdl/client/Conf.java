/*
 * Adapted from The MIT License (MIT)
 *
 * Copyright (c) 2018-2020 DaPorkchop_
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions:
 *
 * Any persons and/or organizations using this software must include the above copyright notice and this permission notice,
 * provide sufficient credit to the original authors of the project (IE: DaPorkchop_), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.daporkchop.mapdl.client;

import net.daporkchop.lib.common.util.PorkUtil;
import net.daporkchop.lib.hash.util.Digest;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.nio.charset.StandardCharsets;

import static java.lang.Math.max;

/**
 * Config options for the client.
 *
 * @author DaPorkchop_
 */
@Config(modid = Client.MOD_ID, name = Client.MOD_NAME)
public final class Conf {
    @Config.Comment({
            "Your username on the mapdl server."
    })
    @Config.Name("Username")
    public static String USERNAME = "";

    @Config.Comment({
            "Your password for authentication with the mapdl server."
    })
    @Config.Name("Password")
    public static String PASSWORD = "";

    @Config.Ignore
    public static String HASHED_PASSWORD;

    @Config.Comment({
            "The base URL of the mapdl server.",
            "Must end with a trailing slash!"
    })
    @Config.Name("Server URL")
    public static String SERVER_URL = "http://[::1]:8080/";

    @Config.Comment({
            "The number of chunk compression threads to use.",
            "Defaults to the number of CPU cores - 1, or at least 1."
    })
    @Config.RangeInt(min = 1, max = 8192)
    @Config.RequiresMcRestart
    @Config.Name("Compression Threads")
    public static int COMPRESS_THREADS = max(PorkUtil.CPU_COUNT - 1, 1);

    @Config.Comment({
            "The number of HTTP threads to use.",
            "This is also the maximum number of outgoing requests to the mapdl server that will be active at any one time.",
            "Defaults to 4."
    })
    @Config.RangeInt(min = 1, max = 8192)
    @Config.RequiresMcRestart
    @Config.Name("HTTP Threads")
    public static int HTTP_THREADS = 4;

    @Config.Comment({
            "Server addresses that will be considered to be '2b2t' when joining.",
            "Chunks will only be sent to the server when you are connected to a server with this address."
    })
    @Config.Name("2b2t address")
    public static String ADDRESS_2B2T = "2b2t.org";

    public static void updateHashedPassword() {
        HASHED_PASSWORD = Digest.SHA3_256.start()
                .append(USERNAME.getBytes(StandardCharsets.UTF_8))
                .append(':')
                .append(PASSWORD.getBytes(StandardCharsets.UTF_8))
                .hash().toHex();
    }

    @Mod.EventBusSubscriber(modid = Client.MOD_ID)
    private static class EventHandler {
        @SubscribeEvent
        public static void onConfigReload(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (!Client.MOD_ID.equals(event.getModID())) {
                return;
            }

            ConfigManager.sync(Client.MOD_ID, Config.Type.INSTANCE);
            updateHashedPassword();
        }
    }
}
