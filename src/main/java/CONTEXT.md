# Java source

**Purpose:** Voxy mod logic. LOD generation involves: common world and voxelization (section data, mip levels, storage); client rendering and mesh building (geometry from sections, upload, hierarchical traversal).

**Contents summary:**
- **World and LOD levels:** `WorldEngine` defines `MAX_LOD_LAYER`, section ID encoding (`getWorldSectionId`, `getLevel`, `getX`/`getY`/`getZ`). `WorldSection` holds 32³ block data per level. `ActiveSectionTracker` loads/supplies sections. See `me/cortex/voxy/common/world/WorldEngine.java`, `me/cortex/voxy/common/world/WorldSection.java`, `me/cortex/voxy/common/world/ActiveSectionTracker.java`.
- **LOD data propagation:** `VoxelizedSection` carries level 0..4 voxel data; `WorldConversionFactory.convert` and `mipSection` produce mip levels; `WorldUpdater.insertUpdate` writes into `WorldSection` at each level and marks dirty. See `me/cortex/voxy/common/voxelization/VoxelizedSection.java`, `me/cortex/voxy/common/voxelization/WorldConversionFactory.java`, `me/cortex/voxy/common/world/WorldUpdater.java`.
- **Mesh generation (LOD → geometry):** `RenderGenerationService` queues build tasks by section position; `RenderDataFactory.generateMesh(WorldSection)` produces `BuiltSection` (geometry buffer, offsets, childExistence). Face generation uses `Mesher` and neighbor data. See `me/cortex/voxy/client/core/rendering/building/RenderGenerationService.java`, `me/cortex/voxy/client/core/rendering/building/RenderDataFactory.java#generateMesh`, `me/cortex/voxy/client/core/rendering/building/BuiltSection.java`.
- **Geometry upload and rendering:** `BasicAsyncGeometryManager` / `BasicSectionGeometryManager` upload `BuiltSection`; `NodeManager` / `AsyncNodeManager` drive hierarchical nodes and submit geometry. Section renderer (e.g. `MDICSectionRenderer`) uses LOD shaders. See `me/cortex/voxy/client/core/rendering/section/geometry/BasicAsyncGeometryManager.java`, `me/cortex/voxy/client/core/rendering/hierachical/AsyncNodeManager.java`, `me/cortex/voxy/client/core/rendering/hierachical/NodeManager.java`.
- **Section storage and compression:** `SectionStorage` abstract load/save; `SectionSerializationStorage` uses `SaveLoadSystem3.serialize`/`deserialize`. `CompressionStorageAdaptor` wraps a backend with `StorageCompressor` (ZSTD, LZ4, LZMA). Section data (block LUT + indices) is compressed; geometry is not persisted. See `me/cortex/voxy/common/config/section/SectionStorage.java`, `me/cortex/voxy/common/config/section/SectionSerializationStorage.java`, `me/cortex/voxy/common/world/SaveLoadSystem3.java`, `me/cortex/voxy/common/config/storage/other/CompressionStorageAdaptor.java`, `me/cortex/voxy/common/config/compressors/`.

**Subdirectories:**
- `me/cortex/voxy/client/` – Client-only: config, mixins, render pipeline, rendering (building, hierarchical, section backend), model bakery. LOD mesh build and render entry points live here.
- `me/cortex/voxy/common/` – Shared: world engine, section storage config, compressors, serialization, voxelization, thread/utils. LOD level constants, section serialization, and compression adaptors live here.
- `me/cortex/voxy/commonImpl/` – Concrete implementations: `VoxyInstance`, importers (e.g. `WorldImporter`, `DHImporter` using `VoxelizedSection`).

**Related:** Source layout and LOD flow: `../CONTEXT.md`. Shaders: `../resources/CONTEXT.md`.
