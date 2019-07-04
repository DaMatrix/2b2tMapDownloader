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

package net.daporkchop.mapdl.server.util.process;

import io.netty.buffer.ByteBuf;

/**
 * Called after a process launched by {@link ProcessLauncher} exits.
 *
 * @author DaPorkchop_
 */
@FunctionalInterface
public interface ExitCallback {
    /**
     * Called after a process launched by {@link ProcessLauncher} exits.
     * <p>
     * Note: both of the buffers will be released as soon as this method returns, make sure to retain them if needed!
     * <p>
     * If the {@link ProcessLauncher} is closed before the process exits, the process will be terminated and this callback will be invoked with both
     * stdout and stderr parameters being {@code null}, and exitCode being {@link Integer#MIN_VALUE}.
     *
     * @param stdout   the contents of the process' standard output (stdout)
     * @param stderr   the contents of the process' standard error (stderr)
     * @param exitCode the process' exit code
     */
    void onExit(ByteBuf stdout, ByteBuf stderr, int exitCode);
}
