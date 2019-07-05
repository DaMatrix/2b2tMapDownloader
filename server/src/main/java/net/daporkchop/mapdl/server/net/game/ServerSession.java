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
import lombok.NonNull;
import net.daporkchop.lib.binary.stream.DataIn;
import net.daporkchop.lib.network.session.AbstractUserSession;
import net.daporkchop.lib.network.session.encode.SendCallback;
import net.daporkchop.lib.network.util.PacketMetadata;
import net.daporkchop.mapdl.server.net.BaseHTTPSession;
import net.daporkchop.mapdl.server.net.HTTPMethod;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A session to a client (using the client mod) on the server side.
 *
 * @author DaPorkchop_
 */
public class ServerSession extends BaseHTTPSession<ServerSession> {
    @NonNull
    protected HTTPMethod method;
    protected String url;
    protected Map<String, String> headers = new HashMap<>();

    @Override
    public void onReceive(@NonNull DataIn in, @NonNull PacketMetadata metadata) throws IOException {
    }
}
