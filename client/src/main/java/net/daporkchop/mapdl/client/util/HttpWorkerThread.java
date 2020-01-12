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
import lombok.experimental.Accessors;
import net.daporkchop.mapdl.client.Client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author DaPorkchop_
 */
@Accessors(fluent = true)
public final class HttpWorkerThread extends Thread {
    protected static final long MAX_SEND_INTERVAL = TimeUnit.SECONDS.toMillis(30L);

    @Getter
    protected final    int     id;
    protected volatile boolean shutdown;

    public HttpWorkerThread(int id) {
        super("2b2tMapDownloader HTTP Thread #" + id);

        this.id = id;
    }

    public void requestShutdown() {
        this.shutdown = true;
        this.interrupt();
    }

    @Override
    public void run() {
        final BlockingQueue<ByteBuf> queue = Client.HTTP_QUEUE;
        if (queue == null) {
            //already shut down! exit now
            return;
        }

        long lastSent = System.currentTimeMillis();
        final ByteBuf buf = Unpooled.directBuffer(1 << 20, 1 << 20); //1 MiB
        final Collection<ByteBuf> pendingBuffers = new ArrayList<>();
        try {
            //TODO: this
        } finally {
            Client.HTTP_QUEUE.addAll(pendingBuffers); //re-add any chunks to the queue if they couldn't be sent
            Client.HTTP_SHUTDOWN.countDown();
            buf.release();
        }
    }
}
