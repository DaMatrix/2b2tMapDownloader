/*
 * Adapted from the Wizardry License
 *
 * Copyright (c) 2018-2019 DaPorkchop_ and contributors
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

package net.daporkchop.mapdl.server.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import lombok.NonNull;
import net.daporkchop.lib.binary.stream.DataIn;
import net.daporkchop.lib.common.util.PorkUtil;
import net.daporkchop.lib.logging.Logger;
import net.daporkchop.lib.logging.Logging;
import net.daporkchop.lib.network.session.AbstractUserSession;
import net.daporkchop.lib.network.session.encode.SendCallback;
import net.daporkchop.lib.network.util.PacketMetadata;
import net.daporkchop.lib.unsafe.PUnsafe;
import net.daporkchop.mapdl.server.util.ServerConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * A lightweight HTTP session.
 *
 * @author DaPorkchop_
 */
public class HTTPSession extends AbstractUserSession<HTTPSession> implements ServerConstants, EncodedHTML {
    protected static final long SENT_OFFSET = PUnsafe.pork_getOffset(HTTPSession.class, "sent");

    private volatile int sent = 0;

    @Override
    public void onReceive(@NonNull DataIn in, @NonNull PacketMetadata metadata) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in), 32)) {
            String line = reader.readLine();
            this.respond(line, HTTPStatus.OK, ContentType.TEXT_PLAIN);
        }
    }

    @Override
    public void onException(Exception e) {
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.ioBuffer();
        buf.writeBytes(INTERNAL_SERVER_ERROR_PREFIX);
        if (e != null) {
            buf.writeBytes(TAG_CODE_OPEN);
            Logger.getStackTrace(e, line -> buf.writeBytes(line.getBytes(StandardCharsets.UTF_8)).writeBytes(TAG_BR));
            buf.writeBytes(TAG_CODE_CLOSE);
        }
        buf.writeBytes(TAG_BODY_CLOSE).writeBytes(TAG_HTML_CLOSE);
        this.respond(buf, HTTPStatus.INTERNAL_SERVER_ERROR, ContentType.TEXT_HTML);

        if (e != null)  {
            this.logger().alert(e);
        }
        this.closeAsync();
    }

    @Override
    public void encodeMessage(@NonNull Object msg, @NonNull PacketMetadata metadata, @NonNull SendCallback callback) {
        if (msg instanceof String)  {
            callback.send(((String) msg).getBytes(StandardCharsets.UTF_8), metadata);
        } else if (msg instanceof ByteBuf || msg instanceof byte[]) {
            callback.send(msg, metadata);
        } else {
            throw new IllegalArgumentException(PorkUtil.className(msg));
        }
    }

    public void respond(@NonNull String body, @NonNull HTTPStatus status)   {
        this.respond(Unpooled.wrappedBuffer(body.getBytes(StandardCharsets.UTF_8)), status);
    }

    public void respond(@NonNull byte[] body, @NonNull HTTPStatus status)   {
        this.respond(Unpooled.wrappedBuffer(body), status);
    }

    public void respond(@NonNull ByteBuf body, @NonNull HTTPStatus status)   {
        if (PUnsafe.compareAndSwapInt(this, SENT_OFFSET, 0, 1)) {
            this.send(String.format(
                    "HTTP/1.1 %d %s\r\nContent-length: %d\r\n\r\n",
                    status.code(),
                    status.message(),
                    body.readableBytes()
            ));
            this.sendFlush(body);
            this.closeAsync();
        }
    }

    public void respond(@NonNull String body, @NonNull HTTPStatus status, @NonNull ContentType type)   {
        this.respond(Unpooled.wrappedBuffer(body.getBytes(StandardCharsets.UTF_8)), status, type);
    }

    public void respond(@NonNull byte[] body, @NonNull HTTPStatus status, @NonNull ContentType type)   {
        this.respond(Unpooled.wrappedBuffer(body), status, type);
    }

    public void respond(@NonNull ByteBuf body, @NonNull HTTPStatus status, @NonNull ContentType type)   {
        if (PUnsafe.compareAndSwapInt(this, SENT_OFFSET, 0, 1)) {
            this.send(String.format(
                    "HTTP/1.1 %d %s\r\nContent-length: %d\r\nContent-Type: %s\r\n\r\n",
                    status.code(),
                    status.message(),
                    body.readableBytes(),
                    type.mimeType()
            ));
            this.sendFlush(body);
            this.closeAsync();
        }
    }
}
