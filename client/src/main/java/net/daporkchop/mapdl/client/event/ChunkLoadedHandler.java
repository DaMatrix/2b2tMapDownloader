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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import lombok.NonNull;
import net.daporkchop.lib.unsafe.PUnsafe;
import net.daporkchop.mapdl.client.Client;
import net.daporkchop.mapdl.client.util.ChunkToNBT;
import net.daporkchop.mapdl.client.util.FreshChunk;
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
        ByteBuf tempBuf = PooledByteBufAllocator.DEFAULT.directBuffer(1 << 16);
        try {
            ChunkToNBT.encode(chunk, tempBuf);
            int size = tempBuf.readableBytes();
            Client.COMPRESS_QUEUE.add(new FreshChunk(
                    Unpooled.directBuffer(size, size).writeBytes(tempBuf),
                    chunk.getWorld().provider.getDimension(),
                    chunk.x,
                    chunk.z
            ));
        } finally {
            tempBuf.release();
        }
    }
}
