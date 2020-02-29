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

package net.daporkchop.mapdl.common;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.daporkchop.lib.unsafe.PUnsafe;
import net.daporkchop.mapdl.common.util.Hidden;

/**
 * Representation of a user profile.
 *
 * @author DaPorkchop_
 */
@RequiredArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Setter
@Accessors(fluent = true, chain = true)
public class User {
    protected static final long SENTCHUNKS_OFFSET = PUnsafe.pork_getOffset(User.class, "sentChunks");

    @NonNull
    protected String name;

    //hex-encoded sha3-256 hash of UTF-8 encoded username and password
    @NonNull
    @Hidden
    protected String password;

    @Setter(AccessLevel.NONE)
    protected volatile long sentChunks = 0L;

    /**
     * Increments this user's sent chunks counter.
     *
     * @return this {@link User} instance
     */
    public User incrementSentChunks() {
        PUnsafe.getAndAddLong(this, SENTCHUNKS_OFFSET, 1L);
        return this;
    }
}
