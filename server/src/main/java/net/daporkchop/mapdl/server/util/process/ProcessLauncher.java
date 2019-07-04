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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import net.daporkchop.lib.common.function.io.IOPredicate;
import net.daporkchop.lib.logging.Logging;
import net.daporkchop.mapdl.server.util.ServerConstants;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A wrapper around {@link ProcessBuilder} which uses a worker thread to read command output into a buffer, allowing to efficiently do things with it
 * without having to block our web workers waiting on git command output.
 *
 * @author DaPorkchop_
 */
@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public class ProcessLauncher extends Thread implements ServerConstants {
    protected final long             interval;
    @NonNull
    protected final ByteBufAllocator alloc;
    @NonNull
    protected final EventExecutor    callbackExecutor;
    protected final AtomicBoolean       running   = new AtomicBoolean(true);
    protected final Set<RunningProcess> processes = Collections.newSetFromMap(new ConcurrentHashMap<>());
    protected final ReadWriteLock       lock      = new ReentrantReadWriteLock();

    public ProcessLauncher(long interval) {
        this(interval, PooledByteBufAllocator.DEFAULT, GlobalEventExecutor.INSTANCE);

        super.start();
    }

    @Override
    public synchronized void start() {
        throw new UnsupportedOperationException();
    }

    public void shutdown() {
        this.lock.readLock().lock();
        try {
            if (this.running.get()) {
                this.interrupt();
            }
        } finally {
            this.lock.readLock().unlock();
        }
    }

    @Override
    public void run() {
        try {
            while (this.running.get()) {
                this.processes.removeIf((IOPredicate<RunningProcess>) RunningProcess::tick);
                Thread.sleep(this.interval);
            }
        } catch (InterruptedException e) {
            //exit safely in case of interrupt
        } finally {
            this.lock.writeLock().lock();
            try {
                if (this.running.compareAndSet(true, false)) {
                    this.processes.forEach(RunningProcess::destroy);
                    this.processes.clear();
                } else {
                    throw new IllegalStateException("Process launcher already stopped?");
                }
            } finally {
                this.lock.writeLock().unlock();
            }
        }
    }

    public void submit(@NonNull ExitCallback callback, @NonNull File runDirectory, @NonNull String... command) {
        this.doSubmit(callback, new ProcessBuilder(command).directory(runDirectory));
    }

    public void submit(@NonNull ExitCallback callback, @NonNull File runDirectory, @NonNull List<String> command) {
        this.doSubmit(callback, new ProcessBuilder(command).directory(runDirectory));
    }

    protected void doSubmit(@NonNull ExitCallback callback, @NonNull ProcessBuilder builder) {
        this.lock.readLock().lock();
        try {
            if (this.running.get()) {
                this.processes.add(new RunningProcess(
                        builder.start(),
                        callback,
                        this.alloc.ioBuffer(),
                        this.alloc.ioBuffer()
                ));
            } else {
                this.callbackExecutor.execute(() -> callback.onExit(null, null, Integer.MIN_VALUE));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            this.lock.readLock().unlock();
        }
    }

    @RequiredArgsConstructor
    @Accessors(fluent = true)
    protected class RunningProcess {
        protected final Process      process;
        @NonNull
        protected final ExitCallback callback;
        @NonNull
        protected final ByteBuf      stdout;
        @NonNull
        protected final ByteBuf      stderr;
        @Getter
        protected final int hashCode = ThreadLocalRandom.current().nextInt();

        public boolean tick() throws IOException {
            InputStream in = this.process.getInputStream();
            if (in.available() != 0)    {
                logger.info("%d bytes readable!", in.available());
            }
            this.stdout.writeBytes(in, in.available());
            in = this.process.getErrorStream();
            this.stderr.writeBytes(in, in.available());

            if (this.process.isAlive()) {
                return false;
            } else {
                ProcessLauncher.this.callbackExecutor.execute(() -> {
                    try {
                        this.callback.onExit(this.stdout, this.stderr, this.process.exitValue());
                    } finally {
                        this.stdout.release();
                        this.stderr.release();
                    }
                });
                return true;
            }
        }

        public void destroy() {
            this.process.destroyForcibly();
            this.stdout.release();
            this.stderr.release();
            this.callback.onExit(null, null, Integer.MIN_VALUE);
        }
    }
}
