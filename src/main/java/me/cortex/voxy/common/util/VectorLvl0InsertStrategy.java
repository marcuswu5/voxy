package me.cortex.voxy.common.util;

/**
 * Vectorized implementation of the level-0 insert loop using jdk.incubator.vector.
 * Loaded only when the Vector API module is available.
 */
public final class VectorLvl0InsertStrategy implements Lvl0InsertStrategy {

    private static final int LANE_COUNT = VectorOps.laneCount();

    @Override
    public void processLvl0(long[] vdat, long[] secD, int baseSec, int secMsk, int iSecMsk1, int[] out) {
        int airCount = 0;
        int didStateChange = 0;
        int secIdx = 0;
        for (int i = 0; i <= 0xFFF; i += LANE_COUNT) {
            int cSecIdx = secIdx + baseSec;
            secIdx = (secIdx + iSecMsk1) & secMsk;

            int[] r = VectorOps.copyAndCompare(vdat, secD, i, cSecIdx, LANE_COUNT);
            airCount += r[0];
            didStateChange |= r[1];
        }
        out[0] = airCount;
        out[1] = didStateChange;
    }
}
