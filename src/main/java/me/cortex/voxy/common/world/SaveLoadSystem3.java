package me.cortex.voxy.common.world;

import it.unimi.dsi.fastutil.longs.Long2ShortOpenHashMap;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.ThreadLocalMemoryBuffer;
import me.cortex.voxy.common.world.other.Mapper;
import org.lwjgl.system.MemoryUtil;

public class SaveLoadSystem3 {
    public static final int STORAGE_VERSION = 0;

    /** First byte: legacy format with version prefix (enables RLE vs legacy detection on load). */
    public static final byte LEGACY_FORMAT = (byte) 0xFE;
    /** First byte: RLE format (runs of blockStateId, count). */
    public static final byte RLE_FORMAT = (byte) 0xFF;

    private record SerializationCache(Long2ShortOpenHashMap lutMapCache, MemoryBuffer memoryBuffer) {
        public SerializationCache() {
            this(new Long2ShortOpenHashMap(1024), ThreadLocalMemoryBuffer.create(WorldSection.SECTION_VOLUME*2+WorldSection.SECTION_VOLUME*8+1024));
            this.lutMapCache.defaultReturnValue((short) -1);
        }
    }
    public static int lin2z(int i) {//y,z,x
        int x = i&0x1F;
        int y = (i>>10)&0x1F;
        int z = (i>>5)&0x1F;
        return Integer.expand(x,0b1001001001001)|Integer.expand(y,0b10010010010010)|Integer.expand(z,0b100100100100100);

        //zyxzyxzyxzyxzyx
    }

    public static int z2lin(int i) {
        int x = Integer.compress(i, 0b1001001001001);
        int y = Integer.compress(i, 0b10010010010010);
        int z = Integer.compress(i, 0b100100100100100);
        return x|(y<<10)|(z<<5);
    }

    private static final ThreadLocal<SerializationCache> CACHE = ThreadLocal.withInitial(SerializationCache::new);

    /** Serialize with format selection. When useRle is true writes RLE format; otherwise writes legacy with version byte. */
    public static MemoryBuffer serialize(WorldSection section, boolean useRle) {
        if (useRle) {
            return serializeRle(section);
        }
        return serializeLegacyWithVersion(section);
    }

    /** Legacy single-arg form: no version byte, for backward compatibility when caller does not pass useRle. */
    public static MemoryBuffer serialize(WorldSection section) {
        return serialize(section, false);
    }

    private static MemoryBuffer serializeLegacyWithVersion(WorldSection section) {
        var cache = CACHE.get();
        var data = section.data;
        Long2ShortOpenHashMap LUT = cache.lutMapCache; LUT.clear();

        MemoryBuffer buffer = cache.memoryBuffer().createUntrackedUnfreeableReference();
        long ptr = buffer.address;

        MemoryUtil.memPutByte(ptr, LEGACY_FORMAT); ptr += 1;
        MemoryUtil.memPutLong(ptr, section.key); ptr += 8;
        long metadataPtr = ptr; ptr += 8;

        long blockPtr = ptr; ptr += WorldSection.SECTION_VOLUME*2;
        for (long block : data) {
            short mapping = LUT.putIfAbsent(block, (short) LUT.size());
            if (mapping == -1) {
                mapping = (short) (LUT.size()-1);
                MemoryUtil.memPutLong(ptr, block); ptr+=8;
            }
            MemoryUtil.memPutShort(blockPtr, mapping); blockPtr+=2;
        }
        if (LUT.size() >= 1<<16) {
            throw new IllegalStateException();
        }

        long metadata = 0;
        metadata |= Integer.toUnsignedLong(LUT.size());
        metadata |= Byte.toUnsignedLong(section.getNonEmptyChildren())<<16;

        MemoryUtil.memPutLong(metadataPtr, metadata);
        return buffer.subSize(ptr - buffer.address);
    }

    private static MemoryBuffer serializeRle(WorldSection section) {
        var cache = CACHE.get();
        MemoryBuffer buffer = cache.memoryBuffer().createUntrackedUnfreeableReference();
        long ptr = buffer.address;

        MemoryUtil.memPutByte(ptr, RLE_FORMAT); ptr += 1;
        MemoryUtil.memPutLong(ptr, section.key); ptr += 8;
        MemoryUtil.memPutByte(ptr, section.getNonEmptyChildren()); ptr += 1;

        long[] data = section.data;
        int i = 0;
        while (i < WorldSection.SECTION_VOLUME) {
            long blockId = data[i];
            int count = 1;
            while (i + count < WorldSection.SECTION_VOLUME && data[i + count] == blockId) {
                count++;
            }
            MemoryUtil.memPutLong(ptr, blockId); ptr += 8;
            ptr = writeVarint(ptr, count);
            i += count;
        }
        return buffer.subSize(ptr - buffer.address);
    }

    private static long writeVarint(long ptr, int value) {
        while (value > 0x7F) {
            MemoryUtil.memPutByte(ptr++, (byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        MemoryUtil.memPutByte(ptr++, (byte) (value & 0x7F));
        return ptr;
    }

    private static long readVarint(long[] ptrHolder) {
        long ptr = ptrHolder[0];
        int result = 0;
        int shift = 0;
        byte b;
        do {
            b = MemoryUtil.memGetByte(ptr++);
            result |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 28) throw new IllegalStateException("Varint too large");
        } while ((b & 0x80) != 0);
        ptrHolder[0] = ptr;
        return result;
    }

    public static boolean deserialize(WorldSection section, MemoryBuffer data) {
        long ptr = data.address;
        final long end = data.address + data.size;
        byte formatByte = MemoryUtil.memGetByte(ptr); ptr += 1;

        if (formatByte == RLE_FORMAT) {
            return deserializeRle(section, ptr, end);
        }
        if (formatByte == LEGACY_FORMAT) {
            return deserializeLegacy(section, ptr, end);
        }
        // Old save: no version byte; first byte was part of key. Rewind and parse as legacy.
        ptr = data.address;
        return deserializeLegacy(section, ptr, end);
    }

    private static boolean deserializeRle(WorldSection section, long ptr, long end) {
        long key = MemoryUtil.memGetLong(ptr); ptr += 8;
        if (section.key != key) {
            Logger.error("Decompressed section not the same as requested. got: " + key + " expected: " + section.key);
            return false;
        }
        section.nonEmptyChildren = MemoryUtil.memGetByte(ptr); ptr += 1;

        long[] blockData = section.data;
        int idx = 0;
        long[] ptrHolder = { ptr };
        while (idx < WorldSection.SECTION_VOLUME) {
            long blockId = MemoryUtil.memGetLong(ptrHolder[0]); ptrHolder[0] += 8;
            int count = (int) readVarint(ptrHolder);
            if (count <= 0 || idx + count > WorldSection.SECTION_VOLUME) {
                Logger.error("RLE invalid run count: " + count + " idx=" + idx);
                return false;
            }
            for (int i = 0; i < count; i++) {
                blockData[idx++] = blockId;
            }
        }
        if (section.lvl == 0) {
            int nonEmptyBlockCount = 0;
            for (long blockId : blockData) {
                if (!Mapper.isAir(blockId)) nonEmptyBlockCount++;
            }
            section.nonEmptyBlockCount = nonEmptyBlockCount;
        }
        return true;
    }

    private static boolean deserializeLegacy(WorldSection section, long ptr, long end) {
        long key = MemoryUtil.memGetLong(ptr); ptr += 8;

        if (section.key != key) {
            Logger.error("Decompressed section not the same as requested. got: " + key + " expected: " + section.key);
            return false;
        }

        final long metadata = MemoryUtil.memGetLong(ptr); ptr += 8;
        section.nonEmptyChildren = (byte) ((metadata>>>16)&0xFF);
        final long lutBasePtr = ptr + WorldSection.SECTION_VOLUME * 2;
        if (section.lvl == 0) {
            int nonEmptyBlockCount = 0;
            final var blockData = section.data;
            for (int i = 0; i < WorldSection.SECTION_VOLUME; i++) {
                final short lutId = MemoryUtil.memGetShort(ptr); ptr += 2;
                final long blockId = MemoryUtil.memGetLong(lutBasePtr + Short.toUnsignedLong(lutId) * 8L);
                nonEmptyBlockCount += Mapper.isAir(blockId) ? 0 : 1;
                blockData[i] = blockId;
            }
            section.nonEmptyBlockCount = nonEmptyBlockCount;
        } else {
            final var blockData = section.data;
            for (int i = 0; i < WorldSection.SECTION_VOLUME; i++) {
                blockData[i] = MemoryUtil.memGetLong(lutBasePtr + Short.toUnsignedLong(MemoryUtil.memGetShort(ptr)) * 8L); ptr += 2;
            }
        }
        return true;
    }

    /** Round-trip verification: legacy (with version), RLE, and legacy-without-version (old format) load correctly. */
    public static void main(String[] args) {
        var section = WorldSection._createRawUntrackedUnsafeSection(0, 1, 2, 3);
        section._unsafeSetNonEmptyChildren((byte) 0b10110011);
        for (int i = 0; i < WorldSection.SECTION_VOLUME; i++) {
            section.data[i] = Mapper.composeMappingId((byte) (i % 256), 12 + (i % 1666), i % 300);
        }

        // Legacy (with version byte)
        var legacyBuf = serialize(section, false);
        var section2 = WorldSection._createRawUntrackedUnsafeSection(section.lvl, section.x, section.y, section.z);
        if (!deserialize(section2, legacyBuf)) throw new IllegalStateException("Legacy deserialize failed");
        for (int i = 0; i < WorldSection.SECTION_VOLUME; i++) {
            if (section.data[i] != section2.data[i]) throw new IllegalStateException("Legacy round-trip data mismatch at " + i);
        }
        if (section.getNonEmptyChildren() != section2.getNonEmptyChildren()) throw new IllegalStateException("Legacy round-trip nonEmptyChildren mismatch");

        // RLE
        var rleBuf = serialize(section, true);
        var section3 = WorldSection._createRawUntrackedUnsafeSection(section.lvl, section.x, section.y, section.z);
        if (!deserialize(section3, rleBuf)) throw new IllegalStateException("RLE deserialize failed");
        for (int i = 0; i < WorldSection.SECTION_VOLUME; i++) {
            if (section.data[i] != section3.data[i]) throw new IllegalStateException("RLE round-trip data mismatch at " + i);
        }

        // Old format (no version byte): buffer starts at key
        var oldFormatView = MemoryBuffer.createUntrackedUnfreeableRawFrom(legacyBuf.address + 1, legacyBuf.size - 1);
        var section4 = WorldSection._createRawUntrackedUnsafeSection(section.lvl, section.x, section.y, section.z);
        if (!deserialize(section4, oldFormatView)) throw new IllegalStateException("Old-format deserialize failed");
        for (int i = 0; i < WorldSection.SECTION_VOLUME; i++) {
            if (section.data[i] != section4.data[i]) throw new IllegalStateException("Old-format round-trip data mismatch at " + i);
        }

        System.out.println("SaveLoadSystem3 round-trip OK: legacy, RLE, old-format");
    }
}
