package me.cortex.voxy.common.util;

/**
 * Strategy for the level-0 insert loop (copy vdat to secD and compute air count + state change).
 * Allows vectorized implementation when jdk.incubator.vector is available.
 */
public interface Lvl0InsertStrategy {

    /**
     * Processes the full level-0 section: copies vdat into secD (at indices derived from baseSec, secMsk, iSecMsk1)
     * and writes airCount and didStateChange (0 or 1) into out[0] and out[1].
     */
    void processLvl0(long[] vdat, long[] secD, int baseSec, int secMsk, int iSecMsk1, int[] out);
}
