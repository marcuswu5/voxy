# Culling optimizations

Voxy reduces memory, storage, and mesh load by not storing blocks that are never visible. Two independent culling optimizations apply during chunk ingest and world update.

## Overview

| Optimization | When | What it does | Config |
|---------------|------|--------------|--------|
| **Heightmap culling** | Ingest (per chunk) | Skip ingesting sections entirely below the terrain, and zero blocks below the surface in the section that contains the heightmap min | `heightmapCulling` (client config, default true) |
| **LOD compression** | World update (per section) | Replace non–face-exposed (or non–sky-exposed) blocks with air in the level-0 section before writing to the world | `lodCompression` (client config, default false) |

Both only remove data that would not produce visible geometry; meshing sees the same visible surface. See the “Meshing” section below.

---

## 1. Heightmap culling

**Purpose:** Avoid loading and storing sections that are entirely below the lowest block in each chunk, and zero out blocks below the visible surface in the one section that contains that minimum.

**Where it runs:** In the ingest pipeline, before `WorldConversionFactory.mipSection` and `WorldUpdater.insertUpdate`.

### Flow

1. When enqueueing chunk ingest, the client checks `isHeightmapCullingEnabled()` (backed by `VoxyConfig.CONFIG.heightmapCulling`). If enabled, the chunk’s **MOTION_BLOCKING** heightmap is read and the minimum block Y over the 16×16 column is computed (`HeightmapUtil.getChunkMinY`). Sections with section Y below `(chunkMinY - 2) >> 4` are **not enqueued** at all (saves work and space).
2. For sections that are enqueued, each ingest task carries `chunkMinY` (or -1 if heightmap culling is off).
3. In `VoxelIngestService.processJob`, after conversion, `cullBelowHeightmapInSection(csec, task.cy, task.chunkMinY)` runs:
   - If `chunkMinY < 0` or the section does not contain the heightmap-min block Y, it returns without change.
   - Otherwise it computes `keepFromBlockY = chunkMinY - 2` (keep the block at that Y and above). Only the section that contains `keepFromBlockY` is modified: every voxel with local `y < localMinY` is set to air, and `section.lvl0NonAirCount` is updated.

So the **visible surface** (and one block below) is preserved; only blocks deep below the heightmap are zeroed. The section that contains the bottom of the terrain gets its bottom rows zeroed so the lowest kept block has its bottom face exposed correctly for meshing.

### Relevant files

| File | Role |
|------|------|
| **VoxelIngestService** | Enqueues ingest with `chunkMinY` when heightmap culling is on; calls `cullBelowHeightmapInSection` after convert and before mip/insert. |
| **HeightmapUtil** | `getChunkMinY(chunk, MOTION_BLOCKING)` — samples the chunk heightmap into a scratch array and returns the min block Y (uses `VectorSupport.minReduction` when available). Returns a safe default if the heightmap is missing so culling is effectively disabled. |
| **VoxyClientInstance** | `isHeightmapCullingEnabled()` → `VoxyConfig.CONFIG.heightmapCulling`. |
| **VoxyConfig / VoxyConfigMenu** | `heightmapCulling` option and “Heightmap culling” UI. |

---

## 2. LOD compression (surface culling)

**Purpose:** Store only blocks that have at least one visible face (or, for sky-exposed mode, that see the sky). Interior blocks are replaced with air so they are not stored, meshed, or rendered.

**Where it runs:** In `WorldUpdater.insertUpdate`, before the loop that writes into `WorldSection` at each LOD level. Only the level-0 slice of the `VoxelizedSection` is modified; then `WorldConversionFactory.mipSection` rebuilds higher LOD levels from that slice.

### Modes

- **Face-exposed (current default):** A block is kept iff at least one of its 6 neighbours is air or non-opaque (or the block is on the section boundary; boundary neighbours are treated as transparent). Used for all dimensions in the current branch; suitable for Nether/End where “sky” is meaningless.
- **Sky-exposed (implemented but not currently used):** A block is kept iff it has “sky above”: at least one of y+1 or y+2 is non-opaque (water is treated as opaque for this check). Top of section (y=15) is treated as sky. Intended for Overworld; commented out in favour of face-exposed in `WorldUpdater.insertUpdate`.

### Special blocks

- **Logs** (`BlockTags.LOGS`) and **leaves** (`BlockTags.LEAVES`): Never culled; always kept so they are always rendered.
- **Water:** In sky-exposed mode, water is treated as surface (opaque for “above” check) so the water surface is preserved.

### Flow

1. `WorldEngine` is created with `lodCompressionEnabled` and `hasSky` from the world identifier and client config.
2. In `WorldUpdater.insertUpdate`, if `into.isLodCompressionEnabled()`:
   - Either `cullToFaceExposed(section, mapper)` or (if uncommented) `cullToSkyExposed` / `cullToFaceExposed` based on `into.hasSky()`.
   - Then `WorldConversionFactory.mipSection(section, mapper)` is called so mip levels match the culled level-0.
3. The rest of the insert loop writes the (possibly culled) section into each LOD level as before.

### Relevant files

| File | Role |
|------|------|
| **WorldUpdater** | Runs `cullToFaceExposed` (or `cullToSkyExposed`) when LOD compression is enabled, then `mipSection`; contains the full culling logic and section index layout (level-0: `i = (y<<8)\|(z<<4)\|x`). |
| **WorldEngine** | Holds `lodCompressionEnabled` and `hasSky`; exposes `isLodCompressionEnabled()` and `hasSky()`. |
| **Mapper** | `getBlockStateOpacity`, `isWater`, `isLog`, `isLeaves` used by culling predicates. |
| **VoxyClientInstance** | `isLodCompressionEnabled()` → `VoxyConfig.CONFIG.lodCompression`. |
| **VoxyConfig** | `lodCompression` option (default false). |

---

## Meshing

Meshing reads level-0 section data from `WorldSection` (the same data that was written after culling). It only generates quads for **visible** faces (neighbour is air or non-opaque). So:

- **Heightmap culling** only zeros blocks below the visible surface; the block at the cull boundary keeps its top (and the mesher sees air below and generates the bottom face). No visible geometry is removed.
- **LOD compression** only replaces blocks that have **no** visible faces (fully surrounded by opaque, or not sky-exposed). Those blocks would have generated zero quads anyway. Neighbours that become air after culling correctly get an extra face toward that cell.

So block culling does not change the visible mesh; it only drops data that would not have produced quads.

---

## Config summary

| Config key | Type | Default | Effect |
|------------|------|---------|--------|
| `heightmapCulling` | boolean | true | Enable heightmap-based section skip and below-surface zeroing in ingest. |
| `lodCompression` | boolean | false | Enable face-exposed (or sky-exposed) culling in WorldUpdater before writing sections. |

Both are client-side and apply per world when creating the `WorldEngine`.
