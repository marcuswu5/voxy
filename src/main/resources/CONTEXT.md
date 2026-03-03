# Resources

**Purpose:** Fabric mod resources: shaders (LOD, bakery, post, hiz, util), assets, lang, and mod metadata. LOD rendering and traversal are implemented in `assets/voxy/shaders/lod/`.

**Contents summary:**
- **LOD position/level encoding:** `pos_util.glsl` – `getLoDLevel(uvec2 packedPos)`, `getLoDPosition(uvec2 packedPos)`. Used by section and node types. See `assets/voxy/shaders/lod/pos_util.glsl`.
- **Section and quad types:** `section.glsl` (section LOD level/position helpers); `quad_format.glsl` (Quad layout: pos, size, face, stateId, biomeId, lightId); `quad_util.glsl` (lodScale, `makeRemainingAttributes` with lodLevel, face encoding). See `assets/voxy/shaders/lod/section.glsl`, `assets/voxy/shaders/lod/quad_format.glsl`, `assets/voxy/shaders/lod/quad_util.glsl`.
- **Hierarchical LOD:** `node.glsl` (node with `lodLevel`, `pos`, flags, meshPtr, childPtr); `screenspace.glsl` (frustum/screenspace from node); `traversal_dev.comp` (traversal, render counts per `node.lodLevel`); `queue.glsl`; cleaner (sort_visibility, result_transformer, batch_visibility_set); debug (node_outline, setup). See `assets/voxy/shaders/lod/hierarchical/node.glsl`, `assets/voxy/shaders/lod/hierarchical/screenspace.glsl`, `assets/voxy/shaders/lod/hierarchical/traversal_dev.comp`.
- **GL46 section rendering:** `bindings.glsl`; `cmdgen.comp`, `prep.comp` (section metadata); `cull/raster.vert`/`raster.frag`; `buildtranslucents.comp`; `quads3.vert`, `quads.frag` (quad rendering, textureLod). See `assets/voxy/shaders/lod/gl46/bindings.glsl`, `assets/voxy/shaders/lod/gl46/cmdgen.comp`, `assets/voxy/shaders/lod/gl46/quads3.vert`, `assets/voxy/shaders/lod/gl46/quads.frag`.
- **Other:** `lod/block_model.glsl`, `lod/frustum.glsl`; `bakery/`, `post/`, `hiz/`, `util/`; `fabric.mod.json`; `lang/en_us.json` (includes LOD-related tooltips).

**Subdirectories:**
- `assets/voxy/shaders/lod/` – LOD shaders: shared (pos_util, section, quad_format, quad_util, block_model, frustum), hierarchical/, gl46/ (and gl46/cull/, gl46/test/), hierarchical/cleaner/, hierarchical/debug/.

**Related:** Java LOD and rendering: `../java/CONTEXT.md`. Source overview: `../../CONTEXT.md`.
