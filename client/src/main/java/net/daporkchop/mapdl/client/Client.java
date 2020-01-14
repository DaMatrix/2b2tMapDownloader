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

package net.daporkchop.mapdl.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.NonNull;
import net.daporkchop.lib.binary.netty.PUnpooled;
import net.daporkchop.lib.common.misc.file.PFiles;
import net.daporkchop.lib.common.pool.selection.SelectionPool;
import net.daporkchop.lib.common.util.PorkUtil;
import net.daporkchop.lib.http.HttpClient;
import net.daporkchop.lib.http.impl.java.JavaHttpClientBuilder;
import net.daporkchop.lib.unsafe.PUnsafe;
import net.daporkchop.lib.unsafe.UnsafeStaticField;
import net.daporkchop.mapdl.client.event.GlobalHandler;
import net.daporkchop.mapdl.client.util.CompressWorkerThread;
import net.daporkchop.mapdl.client.util.FreshChunk;
import net.daporkchop.mapdl.client.util.HttpWorkerThread;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import sun.nio.ch.DirectBuffer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

@Mod(modid = Client.MOD_ID, name = Client.MOD_NAME, version = Client.VERSION, clientSideOnly = true)
public class Client {
    public static final String MOD_ID   = "mapdl-client";
    public static final String MOD_NAME = "2b2t Map Downloader";
    public static final String VERSION  = "0.0.1";

    private static final UnsafeStaticField FIELD_HTTP_QUEUE = PUnsafe.pork_getStaticField(Client.class, "HTTP_QUEUE");
    private static final UnsafeStaticField FIELD_COMPRESS_QUEUE = PUnsafe.pork_getStaticField(Client.class, "COMPRESS_QUEUE");

    public static final HttpClient HTTP_CLIENT = new JavaHttpClientBuilder()
            .userAgents(SelectionPool.singleton("PorkLib/" + PorkUtil.PORKLIB_VERSION + " 2b2tMapDownloader/" + Client.VERSION))
            .blockingRequests(true)
            .build();

    public static CompressWorkerThread[] COMPRESS_WORKERS;
    public static CountDownLatch         COMPRESS_SHUTDOWN;
    public static volatile BlockingQueue<FreshChunk> COMPRESS_QUEUE = new LinkedBlockingQueue<>();

    public static HttpWorkerThread[] HTTP_WORKERS;
    public static CountDownLatch     HTTP_SHUTDOWN;
    public static volatile BlockingQueue<ByteBuf> HTTP_QUEUE = new LinkedBlockingQueue<>();

    @Mod.Instance(MOD_ID)
    public static Client INSTANCE;

    public File baseDir;
    public File persistedFile;

    @Mod.EventHandler
    public void construction(FMLConstructionEvent event) {
    }

    @Mod.EventHandler
    public void preinit(FMLPreInitializationEvent event) {
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
    }

    @Mod.EventHandler
    public void postinit(FMLPostInitializationEvent event) {
        this.baseDir = PFiles.ensureDirectoryExists(new File(Minecraft.getMinecraft().gameDir, "2b2tMapDownloader"));
        this.persistedFile = new File(this.baseDir, "persistedChunks");

        //set initial value of hashed password
        Conf.updateHashedPassword();

        this.loadPersistedChunks();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            //cancel queue and wait for compression workers to finish up and die
            try {
                BlockingQueue<FreshChunk> compressQueue = COMPRESS_QUEUE;
                if (compressQueue == null) {
                    throw new NullPointerException("COMPRESS_QUEUE");
                } else if (!PUnsafe.compareAndSwapObject(FIELD_COMPRESS_QUEUE.base(), FIELD_COMPRESS_QUEUE.offset(), compressQueue, null)) {
                    throw new IllegalStateException("COMPRESS_QUEUE");
                }
                for (CompressWorkerThread worker : COMPRESS_WORKERS) {
                    worker.interrupt();
                }
                COMPRESS_SHUTDOWN.await();

                if (!compressQueue.isEmpty())   {
                    throw new IllegalStateException();
                }
            } catch (InterruptedException e)    {
                throw new RuntimeException(e);
            }

            //cancel http queue and wait for http workers to die, then write all pending chunks to disk
            try {
                BlockingQueue<ByteBuf> httpQueue = HTTP_QUEUE;
                if (httpQueue == null) {
                    throw new NullPointerException("HTTP_QUEUE");
                } else if (!PUnsafe.compareAndSwapObject(FIELD_HTTP_QUEUE.base(), FIELD_HTTP_QUEUE.offset(), httpQueue, null)) {
                    throw new IllegalStateException("HTTP_QUEUE");
                }
                for (HttpWorkerThread worker : HTTP_WORKERS) {
                    worker.requestShutdown();
                }
                HTTP_SHUTDOWN.await();

                this.saveQueuedChunks(httpQueue);
            } catch (InterruptedException e)    {
                throw new RuntimeException(e);
            }
        }, "2b2tMapDownloader chunk persistence thread"));

        COMPRESS_WORKERS = new CompressWorkerThread[Conf.COMPRESS_THREADS];
        for (int i = 0; i < Conf.COMPRESS_THREADS; i++) {
            (COMPRESS_WORKERS[i] = new CompressWorkerThread(i)).start();
        }
        COMPRESS_SHUTDOWN = new CountDownLatch(Conf.COMPRESS_THREADS);

        HTTP_WORKERS = new HttpWorkerThread[Conf.HTTP_THREADS];
        for (int i = 0; i < Conf.HTTP_THREADS; i++) {
            (HTTP_WORKERS[i] = new HttpWorkerThread(i)).start();
        }
        HTTP_SHUTDOWN = new CountDownLatch(Conf.HTTP_THREADS);

        MinecraftForge.EVENT_BUS.register(new GlobalHandler());
    }

    protected void loadPersistedChunks()    {
        if (!PFiles.checkFileExists(this.persistedFile)) {
            return;
        }

        ByteBuf buf;
        try (FileChannel channel = FileChannel.open(this.persistedFile.toPath(), StandardOpenOption.READ)) {
            buf = PUnpooled.wrap(channel.map(FileChannel.MapMode.READ_ONLY, 0L, channel.size()), true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        while (buf.isReadable())  {
            int size = buf.readInt();
            HTTP_QUEUE.add(Unpooled.directBuffer(size, size).writeBytes(buf, size));
        }
    }

    protected void saveQueuedChunks(@NonNull BlockingQueue<ByteBuf> queue)    {
        ByteBuf buf = Unpooled.buffer(4, 4);
        try (FileChannel channel = FileChannel.open(this.persistedFile.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            Collection<ByteBuf> buffers = new ArrayList<>();
            queue.drainTo(buffers);
            for (ByteBuf chunk : buffers)   {
                buf.clear().writeInt(chunk.readableBytes()).readBytes(channel, buf.readableBytes());
                chunk.readBytes(channel, chunk.readableBytes());
                chunk.release();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
