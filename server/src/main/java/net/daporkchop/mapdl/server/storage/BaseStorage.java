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

package net.daporkchop.mapdl.server.storage;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import net.daporkchop.mapdl.server.Server;
import org.fusesource.leveldbjni.JniDBFactory;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;

import java.io.File;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

/**
 * Container for a LevelDB database.
 *
 * @author DaPorkchop_
 */
@Getter
@Accessors(fluent = true)
public abstract class BaseStorage<K, V> implements AutoCloseable {
    protected static final NoSuchElementException NO_SUCH_ELEMENT_EXCEPTION = new NoSuchElementException();

    protected final Server             server;
    protected final DB                 delegate;
    protected final LoadingCache<K, V> cache;

    public BaseStorage(@NonNull Server server, @NonNull File path) throws IOException {
        this.server = server;

        this.delegate = JniDBFactory.factory.open(path, this.options());
        this.cache = this.buildCache();
    }

    @Override
    public void close() throws IOException {
        this.delegate.close();
        if (this.cache != null) {
            this.cache.invalidateAll();
        }
    }

    public V get(@NonNull K key) {
        try {
            return this.cache == null ? this.load(key) : this.cache.getUnchecked(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void put(@NonNull K key, @NonNull V value) {
        if (this.cache != null) {
            this.cache.put(key, value);
        }
        this.delegate.put(this.encodeKey(key), this.encodeValue(value));
    }

    /**
     * Loads a value from disk.
     *
     * @param key the key
     * @return the loaded value. May not be {@code null}!
     */
    protected V load(@NonNull K key) {
        byte[] encodedValue = this.delegate.get(this.encodeKey(key));
        if (encodedValue != null) {
            return this.decodeValue(encodedValue);
        } else {
            throw NO_SUCH_ELEMENT_EXCEPTION;
        }
    }

    /**
     * Encodes a key to a binary format that can be used by LevelDB.
     *
     * @param key the key as an object
     * @return the encoded key
     */
    protected abstract byte[] encodeKey(@NonNull K key);

    /**
     * Encodes a value to a binary format that can be used by LevelDB.
     *
     * @param value the value as an object
     * @return the encoded value
     */
    protected abstract byte[] encodeValue(@NonNull V value);

    /**
     * Decodes a value from the internal binary format, as used by LevelDB.
     *
     * @param value the value in its binary format
     * @return the decoded value
     */
    protected abstract V decodeValue(@NonNull byte[] value);

    /**
     * @return the options to be used when opening this db
     */
    protected Options options() {
        return new Options();
    }

    /**
     * @return the cache to use for storing values. If {@code null}, the cache will not be used
     */
    protected LoadingCache<K, V> buildCache() {
        return CacheBuilder.newBuilder()
                           .expireAfterAccess(5L, TimeUnit.MINUTES)
                           .maximumSize(100L)
                           .build(CacheLoader.from(this::load));
    }
}
