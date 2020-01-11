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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import lombok.NonNull;
import net.daporkchop.mapdl.client.Client;
import net.daporkchop.mapdl.client.util.ChunkSendTask;
import net.daporkchop.mapdl.client.util.ChunkToNBT;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.lang.reflect.Field;

/**
 * When a chunk is unloaded, this encodes it and enqueues it for compression, local storage and transmission.
 *
 * @author DaPorkchop_
 */
public final class ChunkLoadedHandler {
    @SubscribeEvent
    public void onChunkUnload(@NonNull ChunkEvent.Unload event) {
        this.actuallySaveChunk(event.getChunk());
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public void onDisconnect(@NonNull FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        System.out.println("Disabling chunk saving.");
        MinecraftForge.EVENT_BUS.unregister(this);

        World world = Minecraft.getMinecraft().world;
        if (world != null) {
            System.out.println("Saving all currently loaded chunks...");

            try {
                Field field = ChunkProviderClient.class.getDeclaredField("loadedChunks");
                field.setAccessible(true);

                Long2ObjectMap<Chunk> chunks = (Long2ObjectMap<Chunk>) field.get(world.getChunkProvider());
                chunks.values().forEach(this::actuallySaveChunk);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            System.out.println("World is null?!?");
        }
    }

    protected void actuallySaveChunk(@NonNull Chunk chunk) {
        ByteBuf rawChunk = PooledByteBufAllocator.DEFAULT.directBuffer();
        ChunkToNBT.encode(chunk, rawChunk);
        Client.HTTP_WORKER_POOL.submit(new ChunkSendTask(
                rawChunk,
                chunk.x,
                chunk.z,
                chunk.getWorld().provider.getDimension()
        ));
    }
}
