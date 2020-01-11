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
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.concurrent.Future;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import net.daporkchop.lib.binary.stream.DataOut;
import net.daporkchop.lib.common.function.io.IORunnable;
import net.daporkchop.lib.common.pool.handle.DefaultThreadHandledPool;
import net.daporkchop.lib.common.pool.handle.Handle;
import net.daporkchop.lib.common.pool.handle.HandledPool;
import net.daporkchop.lib.common.util.PorkUtil;
import net.daporkchop.lib.encoding.Hexadecimal;
import net.daporkchop.lib.http.HttpMethod;
import net.daporkchop.lib.http.entity.ByteBufHttpEntity;
import net.daporkchop.lib.http.entity.ReusableByteBufHttpEntity;
import net.daporkchop.lib.http.entity.content.type.StandardContentType;
import net.daporkchop.lib.http.request.Request;
import net.daporkchop.lib.http.response.ResponseBody;
import net.daporkchop.lib.natives.PNatives;
import net.daporkchop.lib.natives.zlib.PDeflater;
import net.daporkchop.lib.natives.zlib.Zlib;
import net.daporkchop.lib.unsafe.PUnsafe;
import net.daporkchop.mapdl.client.Client;
import net.daporkchop.mapdl.client.Conf;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * @author DaPorkchop_
 */
@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public final class ChunkSendTask implements IORunnable {
    protected static final HandledPool<PDeflater> DEFLATER_POOL = new DefaultThreadHandledPool<>(() -> PNatives.ZLIB.get().deflater(Zlib.ZLIB_LEVEL_BEST), 1);

    @NonNull
    protected final NBTTagCompound compoundTag;

    protected final int x;
    protected final int z;

    protected final int dim;

    @Override
    public void runThrowing() throws IOException {
        ByteBuf rawBuf = PooledByteBufAllocator.DEFAULT.ioBuffer();
        ByteBuf compressedBuf = PooledByteBufAllocator.DEFAULT.ioBuffer();

        try {
            //encode NBT data
            CompressedStreamTools.write(this.compoundTag, new DataOutputStream(DataOut.wrap(rawBuf)));

            //compress NBT data
            try (Handle<PDeflater> handle = DEFLATER_POOL.get())    {
                PDeflater deflater = handle.value();
                try {
                    compressedBuf.writeInt(-1) //length prefix
                            .writeByte(2); //compression version (zlib)
                    deflater.deflate(rawBuf, compressedBuf);
                    compressedBuf.setInt(0, compressedBuf.readableBytes() - 4);
                } finally {
                    deflater.reset();
                }
            }

            Request<String> request = Client.HTTP_CLIENT.request(HttpMethod.POST, Conf.SERVER_URL + "api/submit")
                    .body(new ReusableByteBufHttpEntity(StandardContentType.APPLICATION_OCTET_STREAM, compressedBuf))
                    .userAgent("PorkLib/" + PorkUtil.PORKLIB_VERSION + " 2b2tMapDownloader/" + Client.VERSION)
                    .header("mapdl-username", Conf.USERNAME)
                    .header("mapdl-password", Conf.HASHED_PASSWORD)
                    .header("mapdl-dim", String.valueOf(this.dim))
                    .header("mapdl-x", String.valueOf(this.x))
                    .header("mapdl-z", String.valueOf(this.z))
                    .aggregateToString()
                    .send();

            Future<ResponseBody<String>> bodyFuture = request.bodyFuture().awaitUninterruptibly();

            if (!bodyFuture.isSuccess()) {
                //handle failure somehow
                //TODO: maybe write to disk or something?

                Minecraft.getMinecraft().addScheduledTask(() -> PUnsafe.throwException(bodyFuture.cause()));
            }
        } catch (Throwable t)   {
            t.printStackTrace();
            PUnsafe.throwException(t);
        } finally {
            rawBuf.release();
            compressedBuf.release();
        }
    }
}
