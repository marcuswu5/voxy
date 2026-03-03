# Source root

**Purpose:** Contains all Voxy mod source: Java code and resources (shaders, assets, config). LOD generation spans world (common), mesh building (client), and section storage (common); LOD rendering uses shaders under `main/resources/assets/voxy/shaders/lod/`.

**Contents summary:**
- **main/java** – Java package root; LOD world, voxelization, section storage, and client rendering. See `src/main/java/CONTEXT.md`.
- **main/resources** – Shaders (lod/, bakery/, post/), assets, fabric.mod.json, lang. LOD shaders: `pos_util.glsl`, `section.glsl`, `quad_format.glsl`, `quad_util.glsl`; hierarchical: `node.glsl`, `screenspace.glsl`, `traversal_dev.comp`; gl46: cmdgen/prep/cull/buildtranslucents, quads. See `src/main/resources/CONTEXT.md`.

**LOD generation flow (references):**
- Voxel input and mip build: `src/main/java/me/cortex/voxy/common/voxelization/VoxelizedSection.java`, `src/main/java/me/cortex/voxy/common/voxelization/WorldConversionFactory.java#mipSection`.
- Insert into world and dirty propagation: `src/main/java/me/cortex/voxy/common/world/WorldUpdater.java#insertUpdate`.
- Section type and level: `src/main/java/me/cortex/voxy/common/world/WorldSection.java`, `src/main/java/me/cortex/voxy/common/world/WorldEngine.java`.
- Mesh from section: `src/main/java/me/cortex/voxy/client/core/rendering/building/RenderDataFactory.java#generateMesh`, `src/main/java/me/cortex/voxy/client/core/rendering/building/BuiltSection.java`.
- Build scheduling: `src/main/java/me/cortex/voxy/client/core/rendering/building/RenderGenerationService.java`.
- Section save/load (for compression): `src/main/java/me/cortex/voxy/common/world/SaveLoadSystem3.java`, `src/main/java/me/cortex/voxy/common/config/section/SectionSerializationStorage.java`, `src/main/java/me/cortex/voxy/common/config/storage/other/CompressionStorageAdaptor.java`.

**Subdirectories:**
- `main/` – Standard Maven-style layout: `java/` (code), `resources/` (assets/shaders).

**Related:** Project overview: `../CONTEXT.md`.
