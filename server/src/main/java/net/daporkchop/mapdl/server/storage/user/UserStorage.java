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

package net.daporkchop.mapdl.server.storage.user;

import lombok.NonNull;
import net.daporkchop.lib.binary.stream.DataIn;
import net.daporkchop.lib.binary.stream.DataOut;
import net.daporkchop.lib.common.cache.SoftThreadCache;
import net.daporkchop.lib.common.cache.ThreadCache;
import net.daporkchop.mapdl.server.Server;
import net.daporkchop.mapdl.server.storage.BaseStorage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * @author DaPorkchop_
 */
public class UserStorage extends BaseStorage<String, User> {
    protected static final ThreadCache<ByteArrayOutputStream> BAOS_CACHE = SoftThreadCache.of(ByteArrayOutputStream::new);

    public UserStorage(@NonNull Server server) throws IOException {
        super(server, new File(server.root(), "users/"));
    }

    @Override
    protected byte[] encodeKey(@NonNull String key) {
        return key.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected byte[] encodeValue(@NonNull User value) {
        ByteArrayOutputStream baos = BAOS_CACHE.get();
        baos.reset();
        try (DataOut out = DataOut.wrap(baos))  {
            value.write(out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    @Override
    protected User decodeValue(@NonNull byte[] value) {
        User user = new User();
        try (DataIn in = DataIn.wrap(ByteBuffer.wrap(value)))   {
            user.read(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return user;
    }
}
