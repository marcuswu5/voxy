package me.cortex.voxy.common.world.service;

import me.cortex.voxy.common.util.VectorSupport;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Computes the minimum height (local min) over a chunk's heightmap for section culling.
 */
public final class HeightmapUtil {

    private static final int HEIGHTMAP_SIZE = 16 * 16;
    private static final ThreadLocal<int[]> HEIGHTMAP_SCRATCH = ThreadLocal.withInitial(() -> new int[HEIGHTMAP_SIZE]);

    private HeightmapUtil() {
    }

    /**
     * Returns the minimum block Y in the chunk's heightmap for the given type, or a value that
     * disables culling (chunk min block Y) if the heightmap is missing or unprimed.
     */
    public static int getChunkMinY(ChunkAccess chunk, Heightmap.Types type) {
        Heightmap heightmap = null;
        for (var entry : chunk.getHeightmaps()) {
            if (entry.getKey() == type) {
                heightmap = entry.getValue();
                break;
            }
        }
        if (heightmap == null) {
            return chunk.getMinSectionY() * 16;
        }
        int[] arr = HEIGHTMAP_SCRATCH.get();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                arr[z * 16 + x] = heightmap.getFirstAvailable(x, z);
            }
        }
        int min = VectorSupport.minReduction(arr, 0, HEIGHTMAP_SIZE);
        return min == Integer.MAX_VALUE ? chunk.getMinSectionY() * 16 : min;
    }
}
