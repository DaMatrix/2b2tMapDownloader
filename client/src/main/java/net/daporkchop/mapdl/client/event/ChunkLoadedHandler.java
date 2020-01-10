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
import io.netty.buffer.Unpooled;
import lombok.NonNull;
import net.daporkchop.lib.binary.stream.DataOut;
import net.daporkchop.lib.common.misc.file.PFiles;
import net.daporkchop.mapdl.client.Client;
import net.daporkchop.mapdl.client.skid.ChunkToNBT;
import net.daporkchop.mapdl.client.util.ChunkSendTask;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;

/**
 * When a chunk is unloaded, this encodes it and enqueues it for compression, local storage and transmission.
 *
 * @author DaPorkchop_
 */
public final class ChunkLoadedHandler {
    @SubscribeEvent
    public void onChunkUnload(@NonNull ChunkEvent.Unload event) {
        NBTTagCompound compound = ChunkToNBT.encode(event.getChunk());

        ByteBuf buf = PooledByteBufAllocator.DEFAULT.ioBuffer(1048576, 1048576 * 4);
        try {
            String fileName = String.format("c.%d.%d.mcc", event.getChunk().x, event.getChunk().z);
            File tempFile = new File(Client.INSTANCE.tempCacheDir, fileName);

            try (OutputStream out = DataOut.wrap(buf)) {
                CompressedStreamTools.writeCompressed(compound, out);
            }
            compound = null; //allow gc

            ByteBuf actualBuf = Unpooled.directBuffer(buf.readableBytes(), buf.readableBytes());
            actualBuf.writeBytes(buf);

            //add to queue for sending later
            Client.HTTP_WORKER_POOL.submit(new ChunkSendTask(
                    actualBuf,
                    event.getChunk().x,
                    event.getChunk().z,
                    event.getChunk().getWorld().provider.getDimension()
            ));
        } catch (IOException e) {
            System.err.printf("Unable to save chunk: %d, %d!\n", event.getChunk().x,  event.getChunk().z);
            e.printStackTrace(System.err);
        } finally {
            buf.release();
        }
    }
}
