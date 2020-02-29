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

import java.net.ConnectException;
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
    protected static final long MAX_WAIT_TIME = TimeUnit.SECONDS.toMillis(5L);

    @Getter
    protected final    int     id;
    protected volatile boolean shutdown;

    public HttpWorkerThread(int id) {
        super("2b2tMapDownloader HTTP Thread #" + id);

        this.id = id;
    }

    public void requestShutdown() {
        this.shutdown = true;
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
                //synchronize on queue so that we only have one worker thread filling up a buffer at a time
                //this limits the number of total requests in favor of larger chunk volume per request (16 megabytes per 5 seconds is basically impossible)
                synchronized (queue) {
                    if (this.shutdown)  {
                        //break out of loop if shutdown occurred while waiting for lock on queue
                        break;
                    }
                    //chunk may be non-null if the previous chunk sending cycle didn't have enough space in the buffer
                    if (chunk == null && (chunk = queue.poll(MAX_WAIT_TIME, TimeUnit.MILLISECONDS)) == null) {
                        //don't wait on this forever
                        continue;
                    }
                    pendingBuffers.add(chunk);
                    chunk.getBytes(0, buf, chunk.readableBytes()); //chunk will never be larger than MAX_REQUEST_SIZE
                    chunk = null;

                    //continually poll for more chunks until buffer fills up or timeout expires
                    long endTime = System.currentTimeMillis() + MAX_WAIT_TIME;
                    do {
                        chunk = queue.poll(endTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                        if (chunk == null)  {
                            //if timeout is reached, silently exit loop
                            break;
                        }
                        if (chunk.writerIndex() <= buf.writableBytes()) {
                            //if there is enough space, append chunk to buffer
                            pendingBuffers.add(chunk);
                            chunk.getBytes(0, buf, chunk.readableBytes()); //chunk will never be larger than MAX_REQUEST_SIZE
                            chunk = null;
                        } else {
                            //leave chunk value set so we can make another attempt at sending it later
                            endTime = 0L;
                        }
                    } while (buf.isWritable() && System.currentTimeMillis() < endTime);
                }

                //actually send request
                Request<String> request = Client.HTTP_CLIENT.request(HttpMethod.POST, Conf.SERVER_URL + "api/submit")
                        .body(new ReusableByteBufHttpEntity(StandardContentType.APPLICATION_OCTET_STREAM, buf))
                        .putHeader("mapdl-username", Conf.USERNAME)
                        .putHeader("mapdl-password", Conf.HASHED_PASSWORD)
                        .aggregateToString()
                        .send();

                Future<ResponseBody<String>> bodyFuture = request.bodyFuture().awaitUninterruptibly();

                if (!bodyFuture.isSuccess()) {
                    if (bodyFuture.cause() instanceof ConnectException) {
                        System.err.println("Connection refused: " + Conf.SERVER_URL);
                    } else {
                        bodyFuture.cause().printStackTrace();
                    }
                    //re-enqueue chunks
                    queue.addAll(pendingBuffers);
                    Thread.sleep(TimeUnit.SECONDS.toMillis(10L)); //wait 10 seconds (to avoid sending a billion requests over and over again if the server is actually down or something)
                } else {
                    pendingBuffers.forEach(bb -> {
                        if (!bb.release())  {
                            throw new IllegalStateException(String.valueOf(bb.refCnt()));
                        }
                    });
                }

                //empty pending buffers list
                pendingBuffers.clear();
            } while (!this.shutdown);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (chunk != null) {
                    queue.add(chunk);
                }
                queue.addAll(pendingBuffers); //re-add any chunks to the queue if they couldn't be sent
                buf.release();
            } finally {
                Client.HTTP_SHUTDOWN.countDown();
            }
        }
    }
}
