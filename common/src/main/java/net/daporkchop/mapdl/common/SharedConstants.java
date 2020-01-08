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

package net.daporkchop.mapdl.common;

import lombok.experimental.UtilityClass;
import net.daporkchop.lib.common.pool.handle.DefaultThreadHandledPool;
import net.daporkchop.lib.common.pool.handle.HandledPool;
import net.daporkchop.lib.natives.PNatives;
import net.daporkchop.lib.natives.zlib.PDeflater;
import net.daporkchop.lib.natives.zlib.PInflater;
import net.daporkchop.lib.natives.zlib.Zlib;

/**
 * Random constants used by both the client and the server.
 *
 * @author DaPorkchop_
 */
@UtilityClass
public class SharedConstants {
    public final HandledPool<PInflater> INFLATER_POOL = new DefaultThreadHandledPool<>(() -> PNatives.ZLIB.get().inflater(Zlib.ZLIB_MODE_AUTO), 1);
    public final HandledPool<PDeflater> DEFLATER_POOL = new DefaultThreadHandledPool<>(() -> PNatives.ZLIB.get().deflater(Zlib.ZLIB_LEVEL_BEST), 1);
}
