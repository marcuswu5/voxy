package me.cortex.voxy.common.util;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/**
 * Vectorized operations using jdk.incubator.vector. Loaded only when the Vector API module is available.
 */
public final class VectorOps {

    private static final VectorSpecies<Long> SPECIES = LongVector.SPECIES_256;
    private static final int LANE_COUNT = SPECIES.length();

    private static final VectorSpecies<Integer> INT_SPECIES = IntVector.SPECIES_256;
    private static final int INT_LANE_COUNT = INT_SPECIES.length();

    /**
     * Mask for Mapper.isAir(id): (id & (((1L<<20)-1)<<27)) == 0. Must match Mapper.isAir.
     */
    public static final long AIR_MASK = (((1L << 20) - 1) << 27);

    private VectorOps() {
    }

    /**
     * Fills arr[from..to) with zero using LongVector.
     */
    public static void fillZero(long[] arr, int from, int to) {
        int i = from;
        LongVector zero = LongVector.zero(SPECIES);
        for (; i + LANE_COUNT <= to; i += LANE_COUNT) {
            zero.intoArray(arr, i);
        }
        for (; i < to; i++) {
            arr[i] = 0L;
        }
    }

    /**
     * Copies laneCount longs from vdat[vdatOffset..] to secD[secDOffset..], and returns
     * { airCount (from old secD values), didStateChange (1 if any vdat differed from secD) }.
     * Uses the same air test as Mapper.isAir: (id & AIR_MASK) == 0.
     */
    public static int[] copyAndCompare(long[] vdat, long[] secD, int vdatOffset, int secDOffset, int laneCount) {
        LongVector oldVec = LongVector.fromArray(SPECIES, secD, secDOffset);
        LongVector newVec = LongVector.fromArray(SPECIES, vdat, vdatOffset);
        newVec.intoArray(secD, secDOffset);

        int airCount = oldVec.and(AIR_MASK).eq(0).trueCount();
        boolean didChange = newVec.compare(VectorOperators.NE, oldVec).anyTrue();
        return new int[]{ airCount, didChange ? 1 : 0 };
    }

    /**
     * Lane count for the preferred species (4 for SPECIES_256).
     */
    public static int laneCount() {
        return LANE_COUNT;
    }

    /**
     * Returns the minimum value in arr[from..to). Uses IntVector when available, otherwise scalar.
     */
    public static int minReduction(int[] arr, int from, int to) {
        if (from >= to) return Integer.MAX_VALUE;
        int min = Integer.MAX_VALUE;
        int i = from;
        for (; i + INT_LANE_COUNT <= to; i += INT_LANE_COUNT) {
            IntVector vec = IntVector.fromArray(INT_SPECIES, arr, i);
            min = Math.min(min, vec.reduceLanes(VectorOperators.MIN));
        }
        for (; i < to; i++) {
            min = Math.min(min, arr[i]);
        }
        return min;
    }
}
