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

package net.daporkchop.mapdl.client.event;

import lombok.NonNull;
import net.daporkchop.mapdl.client.Conf;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.net.InetSocketAddress;

/**
 * This handler is always registered. It handles global events regardless of the currently connected server.
 *
 * @author DaPorkchop_
 */
public final class GlobalHandler {
    protected final ChunkLoadedHandler chunkHandler = new ChunkLoadedHandler();

    @SubscribeEvent
    public void onConnect(@NonNull FMLNetworkEvent.ClientConnectedToServerEvent event) {
        if (!event.isLocal()) {
            InetSocketAddress address = (InetSocketAddress) event.getManager().getRemoteAddress();
            if (Conf.ADDRESS_2B2T.equalsIgnoreCase(address.getHostName()) || Conf.ADDRESS_2B2T.equalsIgnoreCase(address.getHostString())) {
                System.out.println("Joined 2b2t! Enabling chunk saving.");
                MinecraftForge.EVENT_BUS.register(this.chunkHandler);
                return;
            }
        }
        System.out.println("Joined a world that isn't 2b2t, not enabling chunk saving.");
    }
}
