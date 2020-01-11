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

package net.daporkchop.mapdl.server.util;

import io.netty.buffer.ByteBuf;
import net.daporkchop.lib.binary.netty.PUnpooled;
import net.daporkchop.lib.common.misc.file.PFiles;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Just a stupid little test to see if modifications from a write show up immediately in a mmap of the same file.
 *
 * @author DaPorkchop_
 */
public class FileChannelTest {
    public static void main(String... args) throws IOException {
        File file = PFiles.ensureFileExists(new File("data.file"));
        int maxSize = 138449920;

        ByteBuf map;
        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            channel.truncate(maxSize);
            map = PUnpooled.wrap(channel.map(FileChannel.MapMode.READ_ONLY, 0L, maxSize), true);
        }

        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.WRITE)) {
            for (int i = 0; i < 16; i++) {
                byte[] arr = new byte[ThreadLocalRandom.current().nextInt(2097152)];
                int baseOffset = ThreadLocalRandom.current().nextInt(0, maxSize - arr.length);
                ThreadLocalRandom.current().nextBytes(arr);
                int written = channel.write(ByteBuffer.wrap(arr), baseOffset);
                if (written != arr.length) {
                    throw new IllegalStateException(String.format("Wrote %d/%d bytes!", written, arr.length));
                }
                for (int j = 0; j < arr.length; j++) {
                    if (arr[j] != map.getByte(baseOffset + j)) {
                        throw new IllegalStateException();
                    }
                }
            }
        }

        map.release();
    }
}
