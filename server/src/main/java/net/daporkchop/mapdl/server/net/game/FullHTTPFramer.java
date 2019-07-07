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

package net.daporkchop.mapdl.server.net.game;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.ByteProcessor;
import lombok.NonNull;
import net.daporkchop.mapdl.server.net.BaseHTTPFramer;

/**
 * @author DaPorkchop_
 */
public class FullHTTPFramer extends BaseHTTPFramer<ServerSession> {
    protected int offset = 0;
    protected int lastSuccessfulOffset = 0;
    protected int state  = 0;

    protected boolean preparedForBody = false;
    //protected boolean chunked = false;

    @Override
    public void received(@NonNull ServerSession session, @NonNull ByteBuf msg, @NonNull UnpackCallback callback) {
        if (!this.preparedForBody && this.buf != null) {
            this.buf.writeBytes(msg);
            LOOP:
            while (this.buf != null && this.buf.isReadable()) {
                switch (this.state) {
                    case 0: //read request string
                    case 1: //read headers
                        int i = this.buf.forEachByte(this.offset, this.buf.writerIndex() - this.offset, ByteProcessor.FIND_LF);
                        if (i == -1) {
                            this.offset = this.buf.writerIndex();
                            return;
                        } else {
                            //logger.info("Read %d bytes.", i - 1 - this.lastSuccessfulOffset);
                            callback.add(this.buf.slice(this.lastSuccessfulOffset, i - 1 - this.lastSuccessfulOffset), this.state);
                            this.buf.readerIndex(this.offset = this.lastSuccessfulOffset = i + 1);
                        }
                        break;
                    case 2: //read body
                        break LOOP;
                }
            }
        }
        if (this.state == 2)    {
            if (!this.preparedForBody)  {
                this.preparedForBody = true;
                this.prepareForBody(session);
            }
            /*if (this.chunked)   {
            } else {
                this.buf.writeBytes(msg);
            }*/
            this.buf.writeBytes(msg);
        }
    }

    protected void prepareForBody(@NonNull ServerSession session) {
        if (session.headers.containsKey("Content-length"))  {
            //this.chunked = false;
            int len = Integer.parseInt(session.headers.get("Content-length"));
            if (len > 1048576)  {
                throw new IllegalArgumentException();
            }
            ByteBuf newBuf = PooledByteBufAllocator.DEFAULT.ioBuffer(len);
            newBuf.writeBytes(this.buf);
            this.release(session);
            this.buf = newBuf;
        } else /*if (session.headers.containsKey("Transfer-Encoding") && session.headers.get("Transfer-Encoding").equals("chunked"))  {
            this.chunked = true;
        }*/{
            throw new IllegalStateException("Content-length missing!");
        }
    }

    public void nextState() {
        this.state++;
    }
}
