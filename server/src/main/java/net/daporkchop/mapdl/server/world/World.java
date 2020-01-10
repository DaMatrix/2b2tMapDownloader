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

package net.daporkchop.mapdl.server.world;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import lombok.NonNull;
import lombok.experimental.Accessors;
import net.daporkchop.lib.common.cache.Cache;
import net.daporkchop.lib.common.cache.SoftCache;
import net.daporkchop.lib.common.function.io.IOBiConsumer;
import net.daporkchop.lib.common.function.io.IOConsumer;
import net.daporkchop.lib.common.function.io.IOFunction;
import net.daporkchop.lib.common.misc.file.PFiles;
import net.daporkchop.lib.common.pool.handle.Handle;
import net.daporkchop.lib.math.vector.i.Vec2i;
import net.daporkchop.lib.minecraft.world.format.anvil.region.RegionConstants;
import net.daporkchop.lib.minecraft.world.format.anvil.region.RegionFile;
import net.daporkchop.lib.minecraft.world.format.anvil.region.RegionOpenOptions;
import net.daporkchop.lib.natives.zlib.PDeflater;
import net.daporkchop.lib.natives.zlib.PInflater;
import net.daporkchop.lib.unsafe.PUnsafe;
import net.daporkchop.lib.unsafe.util.exception.AlreadyReleasedException;
import net.daporkchop.mapdl.server.Server;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.daporkchop.lib.logging.Logging.*;
import static net.daporkchop.mapdl.common.SharedConstants.*;

/**
 * Needs a new name, this class actually represents the regions of a single dimension.
 *
 * @author DaPorkchop_
 */
@Accessors(fluent = true)
public class World implements AutoCloseable {
    protected static final Pattern        REGION_PATTERN               = Pattern.compile("^r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mca$");
    protected static final Cache<Matcher> REGION_PATTERN_MATCHER_CACHE = new SoftCache<>(() -> REGION_PATTERN.matcher(""));

    protected static final RegionOpenOptions OPEN_OPTIONS        = new RegionOpenOptions().access(RegionFile.Access.WRITE_REQUIRED);
    protected static final RegionOpenOptions CREATE_OPEN_OPTIONS = new RegionOpenOptions().access(RegionFile.Access.WRITE_REQUIRED).createNewFiles(true);

    protected final Server server;
    protected final File   root;

    protected final Map<Vec2i, RegionFile> regions = Collections.synchronizedMap(new HashMap<>());
    protected final IOFunction<Vec2i, RegionFile> regionCreator;

    protected final ReadWriteLock lock = new ReentrantReadWriteLock();

    protected final int dimension;

    protected volatile boolean closed = false;

    public World(@NonNull Server server, int dimension) {
        try {
            this.server = server;
            this.dimension = dimension;

            this.root = PFiles.ensureDirectoryExists(new File(this.server.root(), dimension == 0 ? "world/region/" : String.format("world/DIM%d/region/", dimension)));

            this.regionCreator = pos -> {
                File file = new File(this.root, String.format("r.%d.%d.mca", pos.getX(), pos.getY()));
                if (PFiles.checkFileExists(file)) {
                    throw new IllegalStateException("Region file already exists: " + file.getAbsolutePath());
                }
                return RegionFile.open(file, CREATE_OPEN_OPTIONS);
            };

            Arrays.stream(this.root.listFiles())
                    .parallel()
                    .forEach((IOConsumer<File>) file -> {
                        Matcher matcher = REGION_PATTERN_MATCHER_CACHE.get().reset(file.getName());
                        if (matcher.find()) {
                            this.regions.put(new Vec2i(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))), RegionFile.open(file, OPEN_OPTIONS));
                        }
                    });
        } catch (Exception e) {
            try {
                this.regions.forEach((IOBiConsumer<Vec2i, RegionFile>) (pos, region) -> region.close());
            } catch (Exception e1) {
                logger.alert("Exception while aborting world load:", e1);
            } finally {
                PUnsafe.throwException(e);
            }
            throw new RuntimeException(e); //unreachable
        }
    }

    @Override
    public void close() throws IOException {
        Lock lock = this.lock.writeLock();
        lock.lock();
        try {
            this.assertOpen();
            this.closed = true;

            //only handle first exception, but make an attempt to close every region
            AtomicReference<IOException> ref = new AtomicReference<>();
            this.regions.forEach((pos, region) -> {
                try {
                    region.close();
                } catch (IOException e) {
                    ref.compareAndSet(null, e);
                }
            });
            this.regions.clear();

            if (ref.get() != null) {
                throw ref.get();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the chunk at the given chunk coordinates.
     * <p>
     * Will return {@code null} if the chunk does not exist in the corresponding region, or if the region does not exist.
     *
     * @param x the X coordinate of the chunk
     * @param z the Z coordinate of the chunk
     * @return a {@link ByteBuf} containing the chunk data, or {@code null} if the chunk doesn't exist
     * @throws IOException if an IO exception occurs you dummy
     */
    public ByteBuf getChunk(int x, int z) throws IOException {
        Lock lock = this.lock.readLock();
        lock.lock();
        try {
            this.assertOpen();

            RegionFile region = this.regions.get(new Vec2i(x >> 5, z >> 5));
            return region == null ? null : region.readDirect(x & 0x1F, z & 0x1F);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the chunk at the given chunk coordinates.
     * <p>
     * This will result in creation of a new region file if one doesn't exist already.
     * <p>
     * This will inflate and re-compress the chunk at the maximum ZLIB level.
     *
     * @param x   the X coordinate of the chunk
     * @param z   the Z coordinate of the chunk
     * @param buf a {@link ByteBuf} containing the chunk data
     * @return the size of the compressed chunk
     * @throws IOException if an IO exception occurs you dummy
     */
    public int putChunk(int x, int z, @NonNull ByteBuf buf) throws IOException {
        //re-compress chunk at max level to obtain best compression ratio
        ByteBuf recompressed = PooledByteBufAllocator.DEFAULT.ioBuffer(buf.readableBytes() + RegionConstants.LENGTH_HEADER_SIZE)
                .writeInt(-1)
                .writeByte(RegionConstants.ID_ZLIB);
        Lock lock = this.lock.readLock();
        lock.lock();
        try {
            this.assertOpen();

            ByteBuf temp = PooledByteBufAllocator.DEFAULT.ioBuffer(buf.readableBytes() << 4);
            try (Handle<PInflater> handleInflater = INFLATER_POOL.get();
                 Handle<PDeflater> handleDeflater = DEFLATER_POOL.get()) {
                PInflater inflater = handleInflater.value();
                PDeflater deflater = handleDeflater.value();

                switch (buf.readByte() & 0xFF) {
                    case RegionConstants.ID_GZIP:
                    case RegionConstants.ID_ZLIB:
                        try {
                            inflater.inflate(buf, temp); //native zlib is magic
                        } finally {
                            inflater.reset();
                        }
                        try {
                            deflater.deflate(temp, recompressed);
                        } finally {
                            deflater.reset();
                        }
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown compression mode:" + (buf.getByte(buf.readerIndex() - 1) & 0xFF));
                }
            } finally {
                temp.release();
                temp = null;
            }

            recompressed.setInt(0, recompressed.writerIndex() - RegionConstants.LENGTH_HEADER_SIZE);
            int size = recompressed.readableBytes();

            RegionFile region = this.regions.computeIfAbsent(new Vec2i(x >> 5, z >> 5), this.regionCreator);
            region.writeDirect(x & 0x1F, z & 0x1F, recompressed.retain());
            return size;
        } finally {
            lock.unlock();
            recompressed.release();
        }
    }

    protected void assertOpen() {
        if (this.closed) {
            throw new AlreadyReleasedException();
        }
    }
}
