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

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.daporkchop.lib.binary.Data;
import net.daporkchop.lib.binary.stream.DataIn;
import net.daporkchop.lib.binary.stream.DataOut;

import java.io.IOException;
import java.util.UUID;

/**
 * @author DaPorkchop_
 */
@Setter
@Getter
@Accessors(fluent = true, chain = true)
public class User implements Data {
    public static final int FLAG_ADMIN = 1 << 0;
    public static final int FLAG_OWNER = 1 << 1;

    @NonNull
    protected String name;
    @NonNull
    protected UUID   uuid;
    protected long   submittedChunks;
    protected long   acceptedChunks;
    protected int    flags;

    @Override
    public void read(@NonNull DataIn in) throws IOException {
        in.readVarInt(); //version
        this.name = in.readUTF();
        this.uuid = new UUID(in.readLong(), in.readLong());
        this.submittedChunks = in.readVarLong();
        this.acceptedChunks = in.readVarLong();
        this.flags = in.readVarInt();
    }

    @Override
    public void write(@NonNull DataOut out) throws IOException {
        out.writeVarInt(1); //version
        out.writeUTF(this.name);
        out.writeLong(this.uuid.getMostSignificantBits()).writeLong(this.uuid.getLeastSignificantBits());
        out.writeVarLong(this.submittedChunks);
        out.writeVarLong(this.acceptedChunks);
        out.writeVarInt(this.flags);
    }

    public boolean isAdmin() {
        return (this.flags & FLAG_ADMIN) != 0;
    }

    public boolean isOwner() {
        return (this.flags & FLAG_OWNER) != 0;
    }
}
