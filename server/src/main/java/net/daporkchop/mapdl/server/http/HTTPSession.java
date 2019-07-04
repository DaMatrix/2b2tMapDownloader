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
import lombok.NonNull;
import net.daporkchop.lib.binary.stream.DataIn;
import net.daporkchop.lib.common.util.PorkUtil;
import net.daporkchop.lib.network.session.AbstractUserSession;
import net.daporkchop.lib.network.session.encode.SendCallback;
import net.daporkchop.lib.network.util.PacketMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A lightweight HTTP session.
 *
 * @author DaPorkchop_
 */
public class HTTPSession extends AbstractUserSession<HTTPSession> {
    protected static final byte[] INTERNAL_SERVER_ERROR_RESPONSE = (
            "HTTP/1.1 500 Internal Server Error\r\n\r\n" +
                    "<html><body><h1>500 Internal Server Error</h1></body></html>"
    ).getBytes(StandardCharsets.UTF_8);

    @Override
    public void onOpened(boolean incoming) {
    }

    @Override
    public void onReceive(@NonNull DataIn in, @NonNull PacketMetadata metadata) throws IOException {
        this.sendFlushAsync(INTERNAL_SERVER_ERROR_RESPONSE).addListener(this::closeAsync);
    }

    @Override
    public void encodeMessage(@NonNull Object msg, @NonNull PacketMetadata metadata, @NonNull SendCallback callback) {
        if (msg instanceof byte[] || msg instanceof ByteBuf) {
            callback.send(msg, metadata);
        } else {
            throw new IllegalArgumentException(PorkUtil.className(msg));
        }
    }
}
