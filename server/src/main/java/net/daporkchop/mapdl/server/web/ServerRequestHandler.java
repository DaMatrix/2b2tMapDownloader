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

package net.daporkchop.mapdl.server.web;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.NonNull;
import net.daporkchop.lib.common.function.throwing.ETriConsumer;
import net.daporkchop.lib.encoding.Hexadecimal;
import net.daporkchop.lib.hash.util.Digest;
import net.daporkchop.lib.http.HttpMethod;
import net.daporkchop.lib.http.entity.HttpEntity;
import net.daporkchop.lib.http.entity.ReusableByteBufHttpEntity;
import net.daporkchop.lib.http.entity.content.type.StandardContentType;
import net.daporkchop.lib.http.header.map.HeaderMap;
import net.daporkchop.lib.http.message.Message;
import net.daporkchop.lib.http.request.query.Query;
import net.daporkchop.lib.http.server.ResponseBuilder;
import net.daporkchop.lib.http.server.handle.ServerHandler;
import net.daporkchop.lib.http.util.StatusCodes;
import net.daporkchop.lib.http.util.exception.GenericHttpException;
import net.daporkchop.mapdl.common.User;
import net.daporkchop.mapdl.server.Server;
import net.daporkchop.mapdl.server.world.World;

import java.io.File;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

import static net.daporkchop.lib.logging.Logging.*;
import static net.daporkchop.mapdl.common.SharedConstants.MAX_REQUEST_SIZE;

/**
 * Handles incoming HTTP requests.
 *
 * @author DaPorkchop_
 */
public final class ServerRequestHandler implements ServerHandler {
    protected static final HttpEntity EMPTY_ENTITY = new ReusableByteBufHttpEntity(StandardContentType.TEXT_PLAIN_ASCII, Unpooled.EMPTY_BUFFER);

    protected final Map<String, ETriConsumer<Query, Message, ResponseBuilder>> handlers = new HashMap<>();
    protected final Server server;

    public ServerRequestHandler(@NonNull Server server) {
        this.server = server;

        this.handlers.put("/api/submit", (query, message, response) -> {
            if (query.method() != HttpMethod.POST) {
                throw StatusCodes.Method_Not_Allowed.exception();
            }
            User user = this.getAuthenticatedUser(message.headers());
            ByteBuf buf = (ByteBuf) message.body();

            try (FileChannel channel = FileChannel.open(new File("data").toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))  {
                buf.getBytes(0, channel, 0L, buf.readableBytes());
            }

            do {
                int dimension = buf.readByte();
                World world = this.server.worlds().get(dimension);
                if (world == null)  {
                    throw new GenericHttpException(StatusCodes.Bad_Request, "Unknown dimension: " + dimension);
                }
                long time = buf.readLong();
                int x = buf.readInt();
                int z = buf.readInt();
                int size = buf.markReaderIndex().readInt() + 4;
                world.putChunk(x, z, buf.resetReaderIndex().readRetainedSlice(size), time);
                user.incrementSentChunks();

                logger.trace("User \"%s\" submitted chunk (%s,%s) @ %.2f KiB", user.name(), x, z, size / 1024.0d);
            } while (buf.isReadable());

            logger.trace("Request was %.2f KiB", buf.writerIndex() / 1024.0d);

            response.status(StatusCodes.OK).body(EMPTY_ENTITY);
        });

        this.handlers.put("/api/register", (query, message, response) -> {
            if (query.method() != HttpMethod.POST) {
                throw StatusCodes.Method_Not_Allowed.exception();
            }

            //authenticate the person who's registering the new account
            this.getAuthenticatedUser(message.headers());

            String username = message.headers().getValue("mapdl-new-username");
            String password = message.headers().getValue("mapdl-new-password");
            if (username == null || password == null) {
                throw new GenericHttpException(StatusCodes.Bad_Request, "No new user given!");
            }

            String initialHash = Hexadecimal.encode(Digest.SHA3_256.start()
                    .append(username.getBytes(StandardCharsets.UTF_8))
                    .append(':')
                    .append(password.getBytes(StandardCharsets.UTF_8))
                    .hashToByteArray());

            String saltedHash = Hexadecimal.encode(Digest.SHA3_256.start()
                    .append(username.getBytes(StandardCharsets.UTF_8))
                    .append(':')
                    .append(initialHash.getBytes(StandardCharsets.UTF_8))
                    .hashToByteArray());

            User user = new User(username, saltedHash);
            if (server.users().putIfAbsent(username, user) != null) {
                throw new GenericHttpException(StatusCodes.Internal_Server_Error, "Username already registered: " + username);
            }

            response.status(StatusCodes.OK)
                    .body(StandardContentType.TEXT_PLAIN, Unpooled.EMPTY_BUFFER);
        });
    }

    protected User getAuthenticatedUser(@NonNull HeaderMap headers) throws Exception {
        String username = headers.getValue("mapdl-username");
        String password = headers.getValue("mapdl-password");
        if (username == null || password == null) {
            throw StatusCodes.Unauthorized.exception();
        }
        User user = this.server.users().get(username);
        if (user == null) {
            throw StatusCodes.Forbidden.exception();
        }
        String saltedHash = Hexadecimal.encode(Digest.SHA3_256.start()
                .append(username.getBytes(StandardCharsets.UTF_8))
                .append(':')
                .append(password.getBytes(StandardCharsets.UTF_8))
                .hashToByteArray());
        if (!saltedHash.equals(user.password())) {
            throw StatusCodes.Forbidden.exception();
        }
        return user;
    }

    @Override
    public int maxBodySize() {
        return MAX_REQUEST_SIZE;
    }

    @Override
    public void handle(@NonNull Query query, @NonNull Message message, @NonNull ResponseBuilder response) throws Exception {
        ETriConsumer<Query, Message, ResponseBuilder> handler = this.handlers.get(query.path());
        if (handler != null) {
            handler.acceptThrowing(query, message, response);
        } else {
            throw StatusCodes.Not_Found.exception();
        }
    }
}
