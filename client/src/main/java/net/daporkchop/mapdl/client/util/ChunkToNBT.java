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

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

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

    public NBTTagCompound encode(@NonNull Chunk chunk) {
        NBTTagCompound compound = new NBTTagCompound();

        compound.setInteger("xPos", chunk.getPos().x);
        compound.setInteger("zPos", chunk.getPos().z);
        compound.setLong("LastUpdate", chunk.getWorld().getTotalWorldTime());
        compound.setIntArray("HeightMap", chunk.getHeightMap());
        compound.setBoolean("TerrainPopulated", true);  // We always want this
        compound.setBoolean("LightPopulated", chunk.isLightPopulated());
        compound.setLong("InhabitedTime", chunk.getInhabitedTime());

        ExtendedBlockStorage[] chunkSections = chunk.getBlockStorageArray();
        NBTTagList chunkSectionList = new NBTTagList();
        //boolean hasSky = chunk.getWorld().getWorldType().;

        for (ExtendedBlockStorage chunkSection : chunkSections) {
            if (chunkSection != Chunk.NULL_BLOCK_STORAGE) {
                NBTTagCompound sectionNBT = new NBTTagCompound();
                sectionNBT.setByte("Y", (byte) (chunkSection.getYLocation() >> 4 & 255));
                byte[] buffer = new byte[4096];
                NibbleArray nibblearray = new NibbleArray();
                NibbleArray nibblearray1 = chunkSection.getData().getDataForNBT(buffer, nibblearray);
                sectionNBT.setByteArray("Blocks", buffer);
                sectionNBT.setByteArray("Data", nibblearray.getData());

                if (nibblearray1 != null) {
                    sectionNBT.setByteArray("Add", nibblearray1.getData());
                }

                NibbleArray blocklightArray = chunkSection.getBlockLight();
                sectionNBT.setByteArray("BlockLight", blocklightArray.getData());

                if (blocklightArray.getData().length != EMPTY_LIGHT_ARRAY.length) {
                    throw new IllegalStateException("Invalid block light length: " + blocklightArray.getData().length);
                }

                NibbleArray skylightArray = chunkSection.getSkyLight();
                if (skylightArray != null) {
                    sectionNBT.setByteArray("SkyLight", skylightArray.getData());
                } else {
                    sectionNBT.setByteArray("SkyLight", EMPTY_LIGHT_ARRAY);
                }

                chunkSectionList.appendTag(sectionNBT);
            }
        }

        compound.setTag("Sections", chunkSectionList);
        compound.setByteArray("Biomes", chunk.getBiomeArray());

        compound.setTag("Entities", EMPTY_LIST_TAG);

        NBTTagList tileEntityList = getTileEntityList(chunk);
        compound.setTag("TileEntities", tileEntityList);

        List<NextTickListEntry> updateList = chunk.getWorld().getPendingBlockUpdates(chunk, false);
        if (updateList != null) {
            long worldTime = chunk.getWorld().getTotalWorldTime();
            NBTTagList entries = new NBTTagList();

            for (NextTickListEntry entry : updateList) {
                NBTTagCompound entryTag = new NBTTagCompound();
                ResourceLocation location = Block.REGISTRY.getNameForObject(entry.getBlock());
                entryTag.setString("i", location == null ? "" : location.toString());
                entryTag.setInteger("x", entry.position.getX());
                entryTag.setInteger("y", entry.position.getY());
                entryTag.setInteger("z", entry.position.getZ());
                entryTag.setInteger("t", (int) (entry.scheduledTime - worldTime));
                entryTag.setInteger("p", entry.priority);
                entries.appendTag(entryTag);
            }

            compound.setTag("TileTicks", entries);
        }

        NBTTagCompound rootTag = new NBTTagCompound();
        rootTag.setTag("Level", compound);
        return rootTag;
    }

    protected NBTTagList getTileEntityList(Chunk chunk) {
        NBTTagList tileEntityList = new NBTTagList();

        chunk.getTileEntityMap().forEach((pos, te) -> {
            NBTTagCompound compound = new NBTTagCompound();
            try {
                te.writeToNBT(compound);
            } catch (Exception e) {
                System.err.println("Unable to save tile entity! Compound: " + compound);
                e.printStackTrace(System.err);
                return;
            }

            tileEntityList.appendTag(compound);
        });

        return tileEntityList;
    }
}
