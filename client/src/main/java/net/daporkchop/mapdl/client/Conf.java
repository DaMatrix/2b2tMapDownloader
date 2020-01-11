/*
 * Adapted from the Wizardry License
 *
 * Copyright (c) 2018-2020 DaPorkchop_ and contributors
 *
 * Permission is hereby granted to any persons and/or organizations using this software to copy, modify, merge, publish, and distribute it. Said persons and/or organizations are not allowed to use the software or any derivatives of the work for commercial use or any other means to generate income, nor are they allowed to claim this software as their own.
 *
 * The persons and/or organizations are also disallowed from sub-licensing and/or trademarking this software without explicit permission from DaPorkchop_.
 *
 * Any persons and/or organizations using this software must disclose their source code and have it publicly available, include this license, provide sufficient credit to the original authors of the project (IE: DaPorkchop_), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
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
            "The number of concurrent worker threads to use.",
            "Worker threads will handle compression and sending of chunks, as well as temporarily writing them to a disk cache.",
            "This is also the maximum number of outgoing requests to the mapdl server that will be active at any one time.",
            "Defaults to 2 * the number of CPU cores."
    })
    @Config.RangeInt(min = 1, max = 8192)
    @Config.RequiresMcRestart
    @Config.Name("Worker Threads")
    public static int HTTP_WORKER_THREADS = PorkUtil.CPU_COUNT << 1;

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
