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

package net.daporkchop.mapdl.client.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import net.daporkchop.lib.natives.PNatives;
import net.daporkchop.lib.natives.zlib.PDeflater;
import net.daporkchop.lib.natives.zlib.Zlib;
import net.daporkchop.mapdl.client.Client;

import java.util.concurrent.BlockingQueue;

import static net.daporkchop.mapdl.common.SharedConstants.*;

/**
 * @author DaPorkchop_
 */
@Getter
@Accessors(fluent = true)
public final class CompressWorkerThread extends Thread {
    protected final int id;

    public CompressWorkerThread(int id) {
        super("2b2tMapDownloader Compression Thread #" + id);

        this.id = id;
    }

    @Override
    public void run() {
        final BlockingQueue<FreshChunk> queue = Client.COMPRESS_QUEUE;
        if (queue == null) {
            //already shut down! exit now
            return;
        }

        final ByteBuf buf = Unpooled.directBuffer(MAX_REQUEST_SIZE, MAX_REQUEST_SIZE);
        try (PDeflater deflater = PNatives.ZLIB.get().deflater(Zlib.ZLIB_LEVEL_BEST)) {
            try {
                while (true) {
                    this.processChunk(buf, queue.take(), deflater);
                }
            } catch (InterruptedException e) {
                //only way to exit loop is to be interrupted
            }

            //work off rest of queue before exiting
            FreshChunk chunk;
            while ((chunk = queue.poll()) != null) {
                this.processChunk(buf, chunk, deflater);
            }
        } finally {
            Client.COMPRESS_SHUTDOWN.countDown();
            buf.release();
        }
    }

    protected void processChunk(@NonNull ByteBuf buf, @NonNull FreshChunk chunk, @NonNull PDeflater deflater) {
        try {//write basic chunk info
            buf.clear()
                    .writeByte(chunk.dimension())
                    .writeLong(chunk.time())
                    .writeInt(chunk.x())
                    .writeInt(chunk.z())
                    .writeInt(-1) //length (placeholder)
                    .writeByte(2); //version: zlib

            //compress chunk
            deflater.deflate(chunk.data, buf);
            deflater.reset();

            //set length
            int written = buf.writerIndex();
            buf.setInt(1 + 8 + 4 + 4, written - (1 + 8 + 4 + 4) - 4);

            Client.HTTP_QUEUE.add(Unpooled.directBuffer(written, written).writeBytes(buf));
        } finally {
            chunk.data.release();
        }
    }
}
