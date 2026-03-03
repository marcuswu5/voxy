# Vector API integration

This directory contains the Voxy integration with the **Java Vector API** (`jdk.incubator.vector`). The API is used for SIMD-style operations on hot paths (section insert, bulk zeroing, min-reduction). The implementation is **optional**: if the module is not available at runtime, all call sites fall back to scalar code. No separate build variant is required; the same JAR works with or without the Vector API.

## How the system works

1. **Runtime detection**  
   On class load, `VectorSupport` checks for the presence of `jdk.incubator.vector.LongVector`. If it is not available (e.g. JVM not started with `--add-modules jdk.incubator.vector`), `VectorSupport.VECTOR_AVAILABLE` is false and all operations use scalar fallbacks implemented in `VectorSupport` or `ScalarLvl0InsertStrategy`.

2. **Lazy loading of vector code**  
   Classes that depend on `jdk.incubator.vector` (`VectorOps`, `VectorLvl0InsertStrategy`) are never loaded by name unless the API is present. This avoids `NoClassDefFoundError` on JVMs that do not have the module. Entry points use reflection (e.g. `Class.forName("me.cortex.voxy.common.util.VectorOps")`) or the strategy pattern so the rest of the codebase never references vector types directly.

3. **Operations provided**  
   - **Level-0 insert**: Copy level-0 voxel data from a `VoxelizedSection` into a `WorldSection` and compute air count and dirty state. This is the hottest path in `WorldUpdater.insertSectionLvlIntoWorld` for level 0.  
   - **Bulk zero**: Fill a `long[]` range with zero (used when clearing section buffers or failed-load buffers).  
   - **Min reduction**: Compute the minimum value over an `int[]` range (used for heightmap min-Y in chunk ingest).

4. **Species and lane count**  
   Vector code uses `LongVector.SPECIES_256` and `IntVector.SPECIES_256` (4 longs / 4 ints per vector on typical 64-bit). The level-0 section has 4096 elements (16³); the insert loop advances by `VectorOps.laneCount()` so the vectorized and scalar strategies stay in sync (scalar uses a manual unroll of 4).

## File roles

| File | Role |
|------|------|
| **VectorSupport.java** | Single entry point for vector features. Holds `VECTOR_AVAILABLE`, `getLvl0InsertStrategy()`, `fillZero(long[], int, int)`, and `minReduction(int[], int, int)`. Uses reflection to invoke `VectorOps` and `VectorLvl0InsertStrategy` when the API is available; otherwise runs in-class scalar loops or returns `ScalarLvl0InsertStrategy`. No direct dependency on `jdk.incubator.vector`. |
| **VectorOps.java** | Concrete vectorized implementations. Uses `LongVector` for `fillZero` and for `copyAndCompare` (copy slice + compare for dirty detection + air count using `AIR_MASK`). Uses `IntVector` for `minReduction`. Defines `laneCount()` for the insert loop. **Loaded only when the Vector API is present** (via reflection from `VectorSupport`). |
| **VectorLvl0InsertStrategy.java** | Implements `Lvl0InsertStrategy` by iterating over the level-0 section in steps of `VectorOps.laneCount()` and calling `VectorOps.copyAndCompare` for each chunk. Writes `airCount` and `didStateChange` into the `out` array. **Loaded only when the Vector API is present.** |
| **ScalarLvl0InsertStrategy.java** | Fallback implementation of `Lvl0InsertStrategy`. Processes 4 elements per iteration (manual unroll), copies `vdat` into `secD`, and uses `Mapper.isAir` for air count and direct comparison for state change. Used whenever the Vector API is unavailable or vector strategy loading fails. |
| **Lvl0InsertStrategy.java** | Interface for the level-0 insert loop. Defines `processLvl0(long[] vdat, long[] secD, int baseSec, int secMsk, int iSecMsk1, int[] out)`. Implemented by `VectorLvl0InsertStrategy` (vector) and `ScalarLvl0InsertStrategy` (scalar). |

## Call sites (who uses this)

- **WorldUpdater** (`common.world`): Calls `VectorSupport.getLvl0InsertStrategy().processLvl0(...)` when writing level-0 data into a `WorldSection` in `insertSectionLvlIntoWorld`.
- **VoxelizedSection** (`common.voxelization`): Calls `VectorSupport.fillZero` when zeroing the section buffer.
- **SectionSerializationStorage** (`common.config.section`): Calls `VectorSupport.fillZero` when clearing a section buffer after a failed load.
- **HeightmapUtil** (`common.world.service`): Calls `VectorSupport.minReduction` to compute the minimum height over the chunk heightmap for heightmap culling.
- **VoxyDebugScreenEntry** (client): Reads `VectorSupport.VECTOR_AVAILABLE` to show “Vector API: active” or “inactive” in the debug overlay.

## Enabling the Vector API

- **Gradle / run**: The project’s run configuration adds `--add-modules jdk.incubator.vector` (see `build.gradle`), so running from the IDE or `runClient` uses the vector path when available.
- **External JVM**: Start the game with `--add-modules jdk.incubator.vector` if you want vectorized code; otherwise the scalar path is used automatically.

## Consistency note

`VectorOps.AIR_MASK` and the air-count logic in `VectorOps.copyAndCompare` must match `Mapper.isAir(long)`. The mask is documented in `VectorOps`; if `Mapper.isAir` changes, `AIR_MASK` and the vectorized air count must be updated to stay equivalent.
