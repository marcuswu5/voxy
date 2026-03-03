package me.cortex.voxy.common.world;

import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldConversionFactory;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.commonImpl.VoxyCommon;

import static me.cortex.voxy.common.world.WorldEngine.*;

public class WorldUpdater {
    //Executes an update to the world and automatically updates all the parent mip layers up to level 4 (e.g. where 1 chunk section is 1 block big)

    //NOTE: THIS RUNS ON THE THREAD IT WAS EXECUTED ON, when this method exits, the calling method may assume that VoxelizedSection is no longer needed
    public static void insertUpdate(WorldEngine into, VoxelizedSection section) {//TODO: add a bitset of levels to update and if it should force update

        //Do some very cheeky stuff for MiB
        if (VoxyCommon.IS_MINE_IN_ABYSS) {
            int sector = (section.x+512)>>10;
            section.setPosition(section.x-(sector<<10), section.y+16+(256-32-sector*30), section.z);//Note sector size mult is 30 because the top chunk is replicated (and so is bottom chunk)
        }

        if (!into.isLive) throw new IllegalStateException("World is not live");
        if (into.isLodCompressionEnabled()) {
            if (into.hasSky()) {
                cullToSkyExposed(section, into.getMapper());
            } else {
                cullToFaceExposed(section, into.getMapper());
            }
            WorldConversionFactory.mipSection(section, into.getMapper());
        }
        boolean shouldCheckEmptiness = false;
        WorldSection previousSection = null;
        for (int lvl = 0; lvl <= MAX_LOD_LAYER; lvl++) {
            var worldSection = into.acquire(lvl, section.x >> (lvl + 1), section.y >> (lvl + 1), section.z >> (lvl + 1));

            int emptinessStateChange = 0;
            //Propagate the child existence state of the previous iteration to this section
            if (lvl != 0 && shouldCheckEmptiness) {
                emptinessStateChange = worldSection.updateEmptyChildState(previousSection);
                //We kept the previous section acquired, so we need to release it
                previousSection.release();
                previousSection = null;
            }

            long status = insertSectionLvlIntoWorld(section, worldSection);
            boolean didStateChange = (status&1)==1;
            int airCount = (int) ((status>>1)&0x1FFF);


            if (lvl == 0) {
                int nonAirCountDelta = section.lvl0NonAirCount-(4096-airCount);
                if (nonAirCountDelta != 0) {
                    worldSection.addNonEmptyBlockCount(nonAirCountDelta);
                    emptinessStateChange = worldSection.updateLvl0State() ? 2 : 0;
                }
            }

            if (didStateChange||(emptinessStateChange!=0)) {
                //TODO: somehow foward the neighbors that are facing the updated area, this allows forwarding to the dirty consumer
                // which can decide wether to dispatch mesh rebuilds to the surounding sections
                //Bitmask of neighboring sections
                //Note, this may be zero (this is more likely to occure at higher lod levels) if it doesnt face any neighbors
                int neighbors = 0;
                if (didStateChange) {
                    neighbors |= ((section.y^(section.y-1))>>(lvl+1))==0?0:1<<0;//Down
                    neighbors |= ((section.y^(section.y+1))>>(lvl+1))==0?0:1<<1;//Up
                    neighbors |= ((section.x^(section.x-1))>>(lvl+1))==0?0:1<<2;//-x
                    neighbors |= ((section.x^(section.x+1))>>(lvl+1))==0?0:1<<3;//+x
                    neighbors |= ((section.z^(section.z-1))>>(lvl+1))==0?0:1<<4;//-z
                    neighbors |= ((section.z^(section.z+1))>>(lvl+1))==0?0:1<<5;//+z
                }

                into.markDirty(worldSection, (didStateChange?UPDATE_TYPE_BLOCK_BIT:0)|(emptinessStateChange!=0?UPDATE_TYPE_CHILD_EXISTENCE_BIT:0), neighbors);
            }

            //Need to release the section after using it
            if (didStateChange||(emptinessStateChange==2)) {
                if (emptinessStateChange==2) {
                    //Major state emptiness change, bubble up
                    shouldCheckEmptiness = true;
                    //Dont release the section, it will be released on the next loop
                    previousSection = worldSection;
                } else {
                    //Propagate up without state change
                    shouldCheckEmptiness = false;
                    previousSection = null;
                    worldSection.release();
                }
            } else {
                //If nothing changed just need to release, dont need to update parent mips
                worldSection.release();
                break;
            }
        }

        if (previousSection != null) {
            previousSection.release();
        }
    }


    private static long insertSectionLvlIntoWorld(VoxelizedSection section, WorldSection worldSection) {
        final long[] vdat = section.section;
        final int lvl = worldSection.lvl;

        final int msk = (1<<(lvl+1))-1;
        final int bx = (section.x&msk)<<(4-lvl);
        final int by = (section.y&msk)<<(4-lvl);
        final int bz = (section.z&msk)<<(4-lvl);

        int airCount = 0;
        boolean didStateChange = false;


        //TODO: remove the nonAirCountDelta stuff if level != 0

        {//Do a bunch of funny math
            var secD = worldSection.data;
            int baseSec = bx | (bz << 5) | (by << 10);
            if (lvl == 0) {
                final int secMsk = 0b1100|(0xf << 5) | (0xf << 10);
                final int iSecMsk1 = (~secMsk) + 1;

                int secIdx = 0;

                //TODO rotate the loop parralelization
                // i.e. instead of doing 4 consecutive blocks, which would all be in the same cache line
                // do 4 seperate rows so they are in different cache lines, should allow
                // more instruction pipelining (in theory)
                for (int i = 0; i <= 0xFFF; i+=4) {
                    int cSecIdx = secIdx + baseSec;
                    secIdx = (secIdx + iSecMsk1) & secMsk;

                    long oldId0 = secD[cSecIdx+0]; secD[cSecIdx+0] = vdat[i+0];
                    long oldId1 = secD[cSecIdx+1]; secD[cSecIdx+1] = vdat[i+1];
                    long oldId2 = secD[cSecIdx+2]; secD[cSecIdx+2] = vdat[i+2];
                    long oldId3 = secD[cSecIdx+3]; secD[cSecIdx+3] = vdat[i+3];

                    airCount += Mapper.isAir(oldId0)?1:0; didStateChange |= vdat[i+0] != oldId0;
                    airCount += Mapper.isAir(oldId1)?1:0; didStateChange |= vdat[i+1] != oldId1;
                    airCount += Mapper.isAir(oldId2)?1:0; didStateChange |= vdat[i+2] != oldId2;
                    airCount += Mapper.isAir(oldId3)?1:0; didStateChange |= vdat[i+3] != oldId3;
                }
            } else {
                int baseVIdx = VoxelizedSection.getBaseIndexForLevel(lvl);

                int secMsk = 0xF >> lvl;
                secMsk |= (secMsk << 5) | (secMsk << 10);
                int iSecMsk1 = (~secMsk) + 1;

                int secIdx = 0;
                //TODO: manually unroll and do e.g. 4 iterations per loop
                for (int i = baseVIdx; i <= (0xFFF >> (lvl * 3)) + baseVIdx; i++) {
                    int cSecIdx = secIdx + baseSec;
                    secIdx = (secIdx + iSecMsk1) & secMsk;
                    long newId = vdat[i];
                    long oldId = secD[cSecIdx];
                    didStateChange |= newId != oldId;
                    secD[cSecIdx] = newId;
                }
            }
        }

        long status = 0;
        status |= didStateChange?1:0;
        status |= Integer.toUnsignedLong(airCount)<<1;//VERY VERY VERY IMPORTANT NOTE: IS 13 BITS BIG NOT 12 BITS (since it can be 4096 which is 6 bits large)
        return status;
    }

    /**
     * Sky-exposed culling: replace non-sky-exposed blocks with air in the level-0 section.
     * Logs are never culled and are always kept (so they are always rendered).
     * Level-0 layout: index i = (y<<8)|(z<<4)|x. "Above" = same x,z, higher y.
     * Opaque = mapper.getBlockStateOpacity(id) > 0. Water is treated as surface (opaque for "above" check)
     * so that water-at-top-of-column is kept and water surface is preserved (MVP edge case).
     * Updates section.lvl0NonAirCount.
     */
    private static void cullToSkyExposed(VoxelizedSection section, Mapper mapper) {
        final long[] vdat = section.section;
        int nonAirCount = 0;
        for (int i = 0; i <= 0xFFF; i++) {
            long id = vdat[i];
            if (mapper.isLog(id)) {
                nonAirCount++;
                continue;
            }
            int x = i & 0xF;
            int z = (i >> 4) & 0xF;
            int y = (i >> 8) & 0xF;
            boolean skyExposed = true;
            for (int yy = y + 1; yy < 16; yy++) {
                int j = (yy << 8) | (z << 4) | x;
                long aboveId = vdat[j];
                // Treat water as surface block: blocks below water are not sky-exposed; water surface is kept
                if (mapper.getBlockStateOpacity(aboveId) > 0 || mapper.isWater(aboveId)) {
                    skyExposed = false;
                    break;
                }
            }
            if (!skyExposed) {
                vdat[i] = Mapper.AIR;
            } else if (!Mapper.isAir(id)) {
                nonAirCount++;
            }
        }
        section.lvl0NonAirCount = nonAirCount;
    }

    /**
     * Face-exposed culling: keep block only if at least one of the 6 neighbours is air or non-opaque.
     * Logs are never culled and are always kept (so they are always rendered).
     * Used for dimensions without sky (Nether, End) so terrain is not over-culled.
     * Section boundaries: neighbours outside section are treated as transparent (block is kept).
     * Level-0 layout: index i = (y<<8)|(z<<4)|x.
     */
    private static void cullToFaceExposed(VoxelizedSection section, Mapper mapper) {
        final long[] vdat = section.section;
        int nonAirCount = 0;
        for (int i = 0; i <= 0xFFF; i++) {
            long id = vdat[i];
            if (mapper.isLog(id)) {
                nonAirCount++;
                continue;
            }
            int x = i & 0xF;
            int z = (i >> 4) & 0xF;
            int y = (i >> 8) & 0xF;
            boolean faceExposed = false;
            // +x, -x, +y, -y, +z, -z
            int[][] deltas = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            for (int[] d : deltas) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                if (nx < 0 || nx > 15 || ny < 0 || ny > 15 || nz < 0 || nz > 15) {
                    // Out-of-section: treat as transparent so we keep the block (per existing conventions)
                    faceExposed = true;
                    break;
                }
                int j = (ny << 8) | (nz << 4) | nx;
                long nid = vdat[j];
                if (Mapper.isAir(nid) || mapper.getBlockStateOpacity(nid) == 0) {
                    faceExposed = true;
                    break;
                }
            }
            if (!faceExposed) {
                vdat[i] = Mapper.AIR;
            } else if (!Mapper.isAir(id)) {
                nonAirCount++;
            }
        }
        section.lvl0NonAirCount = nonAirCount;
    }
}
