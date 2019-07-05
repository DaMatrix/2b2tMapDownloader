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
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import net.daporkchop.lib.binary.netty.NettyByteBufIn;
import net.daporkchop.lib.binary.stream.DataIn;
import net.daporkchop.lib.encoding.Hexadecimal;
import net.daporkchop.lib.hash.util.Digest;
import net.daporkchop.lib.network.tcp.session.TCPSession;
import net.daporkchop.lib.network.util.PacketMetadata;
import net.daporkchop.mapdl.server.Server;
import net.daporkchop.mapdl.server.net.BaseHTTPSession;
import net.daporkchop.mapdl.server.net.HTTPMethod;
import net.daporkchop.mapdl.server.net.HTTPStatus;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A session to a client (using the client mod) on the server side.
 *
 * @author DaPorkchop_
 */
@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public class ServerSession extends BaseHTTPSession<ServerSession> {
    protected static final Pattern REQUEST_PATTERN = Pattern.compile("([A-Z]+) (\\S+) (\\S+)");
    protected static final Pattern HEADER_PATTERN  = Pattern.compile("(.*?): ([^$]+)");

    @NonNull
    protected final Server server;

    protected HTTPMethod          method;
    protected String              url;
    protected Map<String, String> headers;

    @Override
    public void onReceive(@NonNull DataIn in, @NonNull PacketMetadata metadata) throws IOException {
        ByteBuf buf = ((NettyByteBufIn) in).buf();
        switch (metadata.protocolId()) {
            case 0: {
                if (this.headers == null) {
                    this.headers = new HashMap<>();
                    Matcher matcher = REQUEST_PATTERN.matcher(buf.toString(StandardCharsets.UTF_8));
                    if (!matcher.find()) {
                        throw new IllegalStateException("Invalid request line!");
                    } else if ((this.method = HTTPMethod.valueOf(matcher.group(1))) != HTTPMethod.POST) {
                        throw new IllegalStateException("Must be a POST request!");
                    }
                    this.url = matcher.group(2);
                    ((FullHTTPFramer) ((TCPSession<ServerSession>) this.internalSession()).framer()).nextState();
                } else {
                    throw new IllegalStateException("Headers already initialized!");
                }
            }
            break;
            case 1: {
                if (buf.readableBytes() == 0) {
                    logger.info("No headers remain.");
                    this.validateCredentials();
                    ((FullHTTPFramer) ((TCPSession<ServerSession>) this.internalSession()).framer()).nextState();
                } else {
                    Matcher matcher = HEADER_PATTERN.matcher(buf.toString(StandardCharsets.UTF_8));
                    if (!matcher.find()) {
                        logger.warn("Invalid header: %s", buf.toString(StandardCharsets.UTF_8));
                        throw new IllegalStateException("Invalid header!");
                    }
                    this.headers.put(matcher.group(1), matcher.group(2));
                    logger.info("  %s: %s", matcher.group(1), matcher.group(2));
                }
            }
            break;
            case 2: { //handle actual post data
            }
            break;
        }
    }

    protected void validateCredentials()    {
        String username = this.headers.get("2b2t-Username");
        String password = this.headers.get("2b2t-Password");

        if (username == null || password == null)   {
            throw new IllegalStateException("Credentials not given!");
        }

        File userFile = new File(this.server.root(), String.format("users/%s", username));
        if (!userFile.exists()) {
            this.respond("", HTTPStatus.FORBIDDEN);
            return;
        }
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(userFile)))   {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) in.readObject();
            if (!Arrays.equals(
                    (byte[]) map.get("password"),
                    Digest.WHIRLPOOL.hash(Hexadecimal.decode(password), this.server.salt()).getHash()
            ))  {
                this.respond("", HTTPStatus.FORBIDDEN);
                return;
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
