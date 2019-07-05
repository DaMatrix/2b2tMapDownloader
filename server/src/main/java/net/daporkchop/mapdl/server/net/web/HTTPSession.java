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

package net.daporkchop.mapdl.server.net.web;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import net.daporkchop.lib.binary.stream.DataIn;
import net.daporkchop.lib.network.util.PacketMetadata;
import net.daporkchop.lib.unsafe.PUnsafe;
import net.daporkchop.mapdl.server.Server;
import net.daporkchop.mapdl.server.net.BaseHTTPSession;
import net.daporkchop.mapdl.server.net.ContentType;
import net.daporkchop.mapdl.server.net.EncodedHTML;
import net.daporkchop.mapdl.server.net.HTTPStatus;
import net.daporkchop.mapdl.server.util.ServerConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * A lightweight HTTP session.
 *
 * @author DaPorkchop_
 */
@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public class HTTPSession extends BaseHTTPSession<HTTPSession> implements ServerConstants {
    @NonNull
    protected final Server server;

    @Override
    public void onReceive(@NonNull DataIn in, @NonNull PacketMetadata metadata) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in), 32)) {
            String line = reader.readLine();
            this.respond(line, HTTPStatus.OK, ContentType.TEXT_PLAIN);
        }
    }
}
