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

package net.daporkchop.mapdl.server.repo;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.daporkchop.lib.binary.Data;
import net.daporkchop.lib.binary.stream.DataIn;
import net.daporkchop.lib.binary.stream.DataOut;
import net.daporkchop.lib.encoding.basen.Base58;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.regex.Matcher;

/**
 * @author DaPorkchop_
 */
@RequiredArgsConstructor
@Getter
@Setter(AccessLevel.PROTECTED)
@Accessors(fluent = true, chain = true)
public class Commit implements Data {
    @NonNull
    protected final String hash;
    @NonNull
    protected final UUID author;
    @NonNull
    protected final String date;
    @NonNull
    protected final String relativeDate;

    protected int  version;
    protected UUID acceptor;
    protected long totalChunks;
    protected long newChunks;

    @Override
    public void read(@NonNull DataIn in) throws IOException {
        this.version(in.readVarInt())
            .acceptor(new UUID(in.readLong(), in.readLong()))
            .totalChunks(in.readVarLong())
            .newChunks(in.readVarLong());
    }

    @Override
    public void write(@NonNull DataOut out) throws IOException {
        out.writeVarInt(this.version)
           .writeLong(this.acceptor.getMostSignificantBits()).writeLong(this.acceptor.getLeastSignificantBits())
           .writeVarLong(this.totalChunks)
           .writeVarLong(this.newChunks);
    }
}
