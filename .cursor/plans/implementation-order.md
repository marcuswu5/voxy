# LOD Compression — Implementation Order

Single source of truth for what to build when. Features in the same wave can be implemented in parallel.

```mermaid
flowchart TB
  subgraph wave1 [Wave 1]
    F01[feature-01 Sky-exposed plus config]
  end
  subgraph wave2 [Wave 2 - parallel]
    F02[feature-02 Dimension-aware]
    F04[feature-04 RLE storage]
  end
  subgraph wave3 [Wave 3]
    F05[feature-05 Wood log + double layer + 5-face]
  end
  subgraph wave4 [Wave 4]
    F03[feature-03 LOD-level-aware]
  end
  wave1 --> wave2
  wave2 --> wave3
  wave3 --> wave4
  F01 --> F02
  F01 --> F04
  F02 --> F05
  F05 --> F03
```

## Progress checklist

- [x] Wave 1: feature-01 (Sky-exposed plus config)
- [x] Wave 2: feature-02 (Dimension-aware)
- [x] Wave 2: feature-04 (RLE storage)
- [ ] Wave 3: feature-05 (Wood log + double layer + 5-face)
- [ ] Wave 4: feature-03 (LOD-level-aware)
