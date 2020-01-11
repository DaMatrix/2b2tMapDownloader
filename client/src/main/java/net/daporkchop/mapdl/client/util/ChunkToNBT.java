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

package net.daporkchop.mapdl.client.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import net.daporkchop.lib.common.cache.Cache;
import net.daporkchop.lib.common.cache.ThreadCache;
import net.daporkchop.lib.nbt.streaming.encode.StreamingCompoundTagEncoder;
import net.daporkchop.lib.nbt.streaming.encode.StreamingListTagEncoder;
import net.daporkchop.lib.nbt.tag.notch.CompoundTag;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.io.DataOutput;
import java.util.List;

/**
 * Initially skidded from:
 * https://github.com/Pokechu22/WorldDownloader/blob/v4/share/src/main/java/wdl/WDLChunkLoader.java
 * https://github.com/Pokechu22/WorldDownloader/blob/v4/share/src/main/java/wdl/WDLChunkLoader_1_12.java
 * <p>
 * I've basically rewritten the entire thing now though...
 *
 * @author DaPorkchop_
 */
@UtilityClass
public class ChunkToNBT {
    private static final byte[]     EMPTY_LIGHT_ARRAY = new byte[2048];
    private static final NBTTagList EMPTY_LIST_TAG    = new NBTTagList();

    private static final Cache<byte[]>      BLOCK_IDS_CACHE  = ThreadCache.soft(() -> new byte[4096]);
    private static final Cache<NibbleArray> BLOCK_DATA_CACHE = ThreadCache.soft(NibbleArray::new);

    public void encode(@NonNull Chunk chunk, @NonNull ByteBuf dst) {
        try (StreamingCompoundTagEncoder rootTag = new StreamingCompoundTagEncoder(dst);
             StreamingCompoundTagEncoder levelTag = rootTag.pushCompound("Level")) {
            levelTag.appendInt("xPos", chunk.getPos().x);
            levelTag.appendInt("zPos", chunk.getPos().z);
            levelTag.appendLong("LastUpdate", chunk.getWorld().getTotalWorldTime());
            levelTag.appendIntArray("HeightMap", chunk.getHeightMap());
            levelTag.appendBoolean("TerrainPopulated", true);  // We always want this
            levelTag.appendBoolean("LightPopulated", chunk.isLightPopulated());
            levelTag.appendLong("InhabitedTime", chunk.getInhabitedTime());

            try (StreamingListTagEncoder chunkList = levelTag.pushList("Sections", CompoundTag.class)) {
                byte[] blockIds = BLOCK_IDS_CACHE.get();
                NibbleArray blockData = BLOCK_DATA_CACHE.get();

                for (ExtendedBlockStorage storage : chunk.getBlockStorageArray()) {
                    if (storage == Chunk.NULL_BLOCK_STORAGE) {
                        continue;
                    }
                    try (StreamingCompoundTagEncoder sectionTag = chunkList.pushCompound()) {
                        sectionTag.appendByte("Y", (byte) ((storage.getYLocation() >> 4) & 0xFF));

                        NibbleArray add = storage.getData().getDataForNBT(blockIds, blockData);
                        sectionTag.appendByteArray("Blocks", blockIds);
                        sectionTag.appendByteArray("Data", blockData.getData());
                        if (add != null) {
                            sectionTag.appendByteArray("Add", add.getData());
                        }

                        sectionTag.appendByteArray("BlockLight", storage.getBlockLight().getData());

                        NibbleArray skyLight = storage.getSkyLight();
                        sectionTag.appendByteArray("SkyLight", skyLight == null ? EMPTY_LIGHT_ARRAY : skyLight.getData());
                    }
                }
            }

            levelTag.appendByteArray("Biomes", chunk.getBiomeArray());

            try (StreamingListTagEncoder entityList = levelTag.pushList("Entities", CompoundTag.class)) {
                //nothing!
            }

            ByteBuf buf = PooledByteBufAllocator.DEFAULT.ioBuffer();
            try (StreamingListTagEncoder tileEntityList = levelTag.pushList("TileEntities", CompoundTag.class)) {
                //we need to do some hackery, because TileEntities are encoded to notchian and not porkian NBT

                DataOutput out = new ByteBufOutputStream(buf);
                chunk.getTileEntityMap().forEach((pos, te) -> {
                    NBTTagCompound compound = new NBTTagCompound();
                    try {
                        //encode TileEntity to compound tag
                        te.writeToNBT(compound);

                        //encode compound tag
                        compound.write(out);

                        //copy encoded tag to dst buffer
                        dst.writeBytes(buf);
                        buf.clear();

                        tileEntityList._internal_incrementCounter();
                    } catch (Exception e) {
                        System.err.println("Unable to save tile entity! Compound: " + compound);
                        e.printStackTrace(System.err);
                    }
                });
            } finally {
                buf.release();
            }

            List<NextTickListEntry> updateList = chunk.getWorld().getPendingBlockUpdates(chunk, false);
            if (updateList != null) {
                long worldTime = chunk.getWorld().getTotalWorldTime();
                try (StreamingListTagEncoder tileTicksList = levelTag.pushList("TileTicks", CompoundTag.class)) {
                    for (NextTickListEntry entry : updateList) {
                        try (StreamingCompoundTagEncoder tileTickTag = tileTicksList.pushCompound()) {
                            ResourceLocation location = Block.REGISTRY.getNameForObject(entry.getBlock());
                            tileTickTag.appendString("i", location == null ? "" : location.toString());
                            tileTickTag.appendInt("x", entry.position.getX());
                            tileTickTag.appendInt("y", entry.position.getY());
                            tileTickTag.appendInt("z", entry.position.getZ());
                            tileTickTag.appendInt("t", (int) (entry.scheduledTime - worldTime));
                            tileTickTag.appendInt("p", entry.priority);
                        }
                    }
                }
            }
        }
    }
}
