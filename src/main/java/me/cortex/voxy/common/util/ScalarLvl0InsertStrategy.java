package me.cortex.voxy.common.util;

import me.cortex.voxy.common.world.other.Mapper;

/**
 * Scalar implementation of the level-0 insert loop. Used when Vector API is not available.
 */
public final class ScalarLvl0InsertStrategy implements Lvl0InsertStrategy {

    public static final ScalarLvl0InsertStrategy INSTANCE = new ScalarLvl0InsertStrategy();

    private ScalarLvl0InsertStrategy() {
    }

    @Override
    public void processLvl0(long[] vdat, long[] secD, int baseSec, int secMsk, int iSecMsk1, int[] out) {
        int airCount = 0;
        boolean didStateChange = false;
        int secIdx = 0;
        for (int i = 0; i <= 0xFFF; i += 4) {
            int cSecIdx = secIdx + baseSec;
            secIdx = (secIdx + iSecMsk1) & secMsk;

            long oldId0 = secD[cSecIdx + 0];
            secD[cSecIdx + 0] = vdat[i + 0];
            long oldId1 = secD[cSecIdx + 1];
            secD[cSecIdx + 1] = vdat[i + 1];
            long oldId2 = secD[cSecIdx + 2];
            secD[cSecIdx + 2] = vdat[i + 2];
            long oldId3 = secD[cSecIdx + 3];
            secD[cSecIdx + 3] = vdat[i + 3];

            airCount += Mapper.isAir(oldId0) ? 1 : 0;
            didStateChange |= vdat[i + 0] != oldId0;
            airCount += Mapper.isAir(oldId1) ? 1 : 0;
            didStateChange |= vdat[i + 1] != oldId1;
            airCount += Mapper.isAir(oldId2) ? 1 : 0;
            didStateChange |= vdat[i + 2] != oldId2;
            airCount += Mapper.isAir(oldId3) ? 1 : 0;
            didStateChange |= vdat[i + 3] != oldId3;
        }
        out[0] = airCount;
        out[1] = didStateChange ? 1 : 0;
    }
}
