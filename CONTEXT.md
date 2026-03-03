# Voxy – Project context

**Purpose:** Minecraft mod for far-distance rendering using a Level-of-Detail (LOD) system. This file orients the codebase with a focus on LOD generation; section storage and serialization are documented for future LOD compression work.

**LOD generation (high level):**
- **LOD levels:** `WorldEngine.MAX_LOD_LAYER = 4`; level 0 = full 32³ section, higher levels are coarser (see `src/main/java/me/cortex/voxy/common/world/WorldEngine.java#getLevel`).
- **Section ID encoding:** Level and position packed in a long; see `src/main/java/me/cortex/voxy/common/world/WorldEngine.java#getWorldSectionId` and `getLevel`/`getX`/`getY`/`getZ`.
- **Data → LODs:** Ingested chunks become `VoxelizedSection` (16³ + 8³ + 4³ + 2³ + 1); `WorldConversionFactory.mipSection` builds mip levels; `WorldUpdater.insertUpdate` writes into `WorldSection` at each level 0..MAX_LOD_LAYER and marks sections dirty (`src/main/java/me/cortex/voxy/common/world/WorldUpdater.java`).
- **Mesh generation:** Dirty sections are meshed by `RenderGenerationService` → `RenderDataFactory.generateMesh(WorldSection)` → `BuiltSection` (geometry buffer of quads). Geometry is uploaded via geometry managers (e.g. `BasicAsyncGeometryManager`) to GPU buffers; not persisted to disk.
- **Rendering:** Hierarchical traversal and section rendering use LOD level from section/section key; shaders decode level/position via `pos_util.glsl` (`getLoDLevel`/`getLoDPosition`).

**Section storage (for LOD compression):**
- Sections are stored by `SectionStorage`; load/save use `SaveLoadSystem3.serialize` / `deserialize` on `WorldSection` (block data + metadata + LUT). See `src/main/java/me/cortex/voxy/common/config/section/SectionStorage.java`, `src/main/java/me/cortex/voxy/common/world/SaveLoadSystem3.java`.
- Optional compression: `CompressionStorageAdaptor` wraps a backend with `StorageCompressor` (ZSTD, LZ4, LZMA). Default config uses ZSTD (e.g. `StorageConfigUtil.createDefaultSerializer`). See `src/main/java/me/cortex/voxy/common/config/storage/other/CompressionStorageAdaptor.java`, `src/main/java/me/cortex/voxy/common/config/compressors/`.

**Contents summary:**
- **Source:** `src/` – Java under `src/main/java/`, shaders and assets under `src/main/resources/`.
- **Build:** Gradle; Fabric mod entry in `src/main/resources/fabric.mod.json`.

**Subdirectories:**
- `src/` – All application and mod source; LOD world, rendering, and storage live here. See `src/CONTEXT.md`.

**Related:** None (root).
