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

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.daporkchop.lib.binary.Data;
import net.daporkchop.lib.binary.UTF8;
import net.daporkchop.lib.binary.stream.DataIn;
import net.daporkchop.lib.binary.stream.DataOut;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * @author DaPorkchop_
 */
@Getter
@Setter
@Accessors(fluent = true, chain = true)
public class CommitData implements Data {
    public static CommitData from(@NonNull RevCommit commit) {
        try {
            CommitData data = new CommitData();
            data.read(DataIn.wrap(ByteBuffer.wrap(commit.getFullMessage().getBytes(UTF8.utf8))));
            return data;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected int  version;
    protected UUID author;
    protected UUID acceptor;
    protected long totalChunks;
    protected long newChunks;

    @Override
    public void read(@NonNull DataIn in) throws IOException {
        this.version(in.readVarInt())
            .author(new UUID(in.readLong(), in.readLong()))
            .acceptor(new UUID(in.readLong(), in.readLong()))
            .totalChunks(in.readVarLong())
            .newChunks(in.readVarLong());
    }

    @Override
    public void write(@NonNull DataOut out) throws IOException {
        out.writeVarInt(this.version)
           .writeLong(this.author.getMostSignificantBits()).writeLong(this.author.getLeastSignificantBits())
           .writeLong(this.acceptor.getMostSignificantBits()).writeLong(this.acceptor.getLeastSignificantBits())
           .writeVarLong(this.totalChunks)
           .writeVarLong(this.newChunks);
    }
}
