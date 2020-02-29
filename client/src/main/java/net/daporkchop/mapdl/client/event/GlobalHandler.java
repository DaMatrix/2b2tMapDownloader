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
