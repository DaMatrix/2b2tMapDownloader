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
