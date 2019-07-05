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

package net.daporkchop.mapdl.server.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import lombok.NonNull;
import net.daporkchop.lib.network.session.AbstractUserSession;
import net.daporkchop.lib.network.tcp.frame.Framer;
import net.daporkchop.lib.network.util.PacketMetadata;
import net.daporkchop.mapdl.server.net.web.HTTPSession;
import net.daporkchop.mapdl.server.util.ServerConstants;

import java.util.List;

/**
 * @author DaPorkchop_
 */
public abstract class BaseHTTPFramer<S extends AbstractUserSession<S>> implements Framer<S>, ServerConstants {
    protected static final int MAX_BUF_SIZE = 4096;

    protected static boolean startsWith(@NonNull ByteBuf buf, @NonNull String s) {
        if (buf.writerIndex() >= s.length()) {
            for (int i = s.length() - 1; i >= 0; i--) {
                if (buf.getByte(i) != s.charAt(i)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    protected static boolean endsWith(@NonNull ByteBuf buf, @NonNull String s) {
        int len = buf.writerIndex();
        if (len >= s.length()) {
            len -= s.length();
            for (int i = s.length() - 1; i >= 0; i--) {
                if (buf.getByte(len + i) != s.charAt(i)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    protected ByteBuf buf;

    @Override
    public void sending(@NonNull S session, @NonNull ByteBuf msg, @NonNull PacketMetadata metadata, @NonNull List<ByteBuf> frames) {
        frames.add(msg);
    }

    @Override
    public void init(@NonNull S session) {
        this.buf = PooledByteBufAllocator.DEFAULT.ioBuffer(16, MAX_BUF_SIZE);
    }

    @Override
    public void release(@NonNull S session) {
        if (this.buf != null) {
            this.buf.release();
            this.buf = null;
        }
    }
}
