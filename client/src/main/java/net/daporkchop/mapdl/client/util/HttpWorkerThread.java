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
import io.netty.util.concurrent.Future;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.daporkchop.lib.http.HttpMethod;
import net.daporkchop.lib.http.entity.ReusableByteBufHttpEntity;
import net.daporkchop.lib.http.entity.content.type.StandardContentType;
import net.daporkchop.lib.http.request.Request;
import net.daporkchop.lib.http.response.ResponseBody;
import net.daporkchop.mapdl.client.Client;
import net.daporkchop.mapdl.client.Conf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static net.daporkchop.mapdl.common.SharedConstants.*;

/**
 * @author DaPorkchop_
 */
@Accessors(fluent = true)
public final class HttpWorkerThread extends Thread {
    //the maximum amount of time that a chunk may be buffered in an HTTP worker's queue before being forcibly sent
    protected static final long MAX_WAIT_TIME = TimeUnit.SECONDS.toMillis(15L);

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

        final ByteBuf buf = Unpooled.directBuffer(MAX_REQUEST_SIZE, MAX_REQUEST_SIZE);
        final Collection<ByteBuf> pendingBuffers = new ArrayList<>();
        ByteBuf chunk = null;
        try {
            do {
                //empty pending buffers list
                pendingBuffers.forEach(ByteBuf::release);
                pendingBuffers.clear();

                if (chunk == null) {
                    //chunk may be non-null if the previous chunk sending cycle didn't have enough space in the buffer
                    chunk = queue.take();
                }
                pendingBuffers.add(chunk);
                chunk.getBytes(0, buf); //chunk will never be larger than MAX_REQUEST_SIZE
                chunk = null;

                //continually poll for more chunks until buffer fills up or timeout expires
                long endTime = System.currentTimeMillis() + MAX_WAIT_TIME;
                do {
                    chunk = queue.poll(endTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                    if (chunk.writerIndex() <= buf.writableBytes()) {
                        //if there is enough space, append chunk to buffer
                        pendingBuffers.add(chunk);
                        chunk.getBytes(0, buf); //chunk will never be larger than MAX_REQUEST_SIZE
                        chunk = null;
                    } else {
                        //leave chunk value set so we can make another attempt at sending it later
                        endTime = 0L;
                    }
                } while (buf.isWritable() && System.currentTimeMillis() < endTime);

                //actually send request
                Request<String> request = Client.HTTP_CLIENT.request(HttpMethod.POST, Conf.SERVER_URL + "api/submit")
                        .body(new ReusableByteBufHttpEntity(StandardContentType.APPLICATION_OCTET_STREAM, buf))
                        .putHeader("mapdl-username", Conf.USERNAME)
                        .putHeader("mapdl-password", Conf.HASHED_PASSWORD)
                        .aggregateToString()
                        .send();

                Future<ResponseBody<String>> bodyFuture = request.bodyFuture().awaitUninterruptibly();

                if (!bodyFuture.isSuccess()) {
                    //re-enqueue chunks
                    Client.HTTP_QUEUE.addAll(pendingBuffers);
                    Thread.sleep(TimeUnit.SECONDS.toMillis(10L)); //wait 10 seconds (to avoid sending a billion requests over and over again if the server is actually down or something)
                }
            } while (!this.shutdown);

            //empty pending buffers list
            pendingBuffers.forEach(ByteBuf::release);
            pendingBuffers.clear();
        } catch (InterruptedException e) {
            //exit safely if interrupted
        } finally {
            if (chunk != null) {
                Client.HTTP_QUEUE.add(chunk);
            }
            Client.HTTP_QUEUE.addAll(pendingBuffers); //re-add any chunks to the queue if they couldn't be sent
            Client.HTTP_SHUTDOWN.countDown();
            buf.release();
        }
    }
}
