# Component Matching Performance (MatchStrategy.identityAndComponent)

Date: 2026-08-22 · MC 1.21.1 / NeoForge 21.1.219 · JMH 1.37 · macOS (Apple Silicon)

## TL;DR

The component part of `MatchStrategy.identityAndComponent` was identified as the bottleneck of
recipe calculation (配方计算) and recipe pathfinding (配方寻路). Vanilla
`PatchedDataComponentMap.equals` walks the prototype and patch maps entry-by-entry on every call
(递归进嵌套容器), with no cached hash. A **canonicalization pool + reference-equality fast path**
(now in `ContentStack.matches`, gated to exact-component strategies) removes the deep comparison
from all hot loops — **without mixins**, and scoped entirely to the mod's own matching core.

Measured results (same machine, same JMH settings, baseline jar snapshot vs optimized build):

| Area | Metric | Before | After | Speedup |
|---|---|---:|---:|---:|
| Micro | enchanted cross-origin equal compare | 132 ns | 2.4 ns | **54.9×** |
| Micro | container (nested stacks) equal compare | 52.6 ns | 2.4 ns | **21.7×** |
| Micro | potion / renamed equal compare | 16–17 ns | 2.4 ns | **6.7–7.2×** |
| Micro | enchanted unequal (miss) compare | 104–131 ns | 3.0 ns | **35–44×** |
| Micro | plain item compare | 4.2–5.0 ns | 2.4 ns | ~1.7–2.1× |
| Stock | countMatching miss, 4096 component entries | 76.9 µs | 22.2 µs | **3.5×** |
| Stock | add/consume/count hit, 1024–4096 entries | 5.7–79.6 µs | 1.4–22.3 µs | **3.3–4.6×** |
| Routing | fresh route, component chain (depth 64) | 1.00 ms | 0.41 ms | **2.4×** |
| Routing | fresh route, component wide (512 alts) | 0.29 ms | 0.22 ms | **1.3×** |
| Routing | graph build | 0.12–0.20 ms | 0.12–0.20 ms | ~1.0× (no regression) |
| Routing | warm route (fully memoized) | 16–158 µs | 19–141 µs | −6…+12% (± few µs) |

One-time canonicalization cost (per fresh component-bearing stack): 24–362 ns — amortized after a
single comparison for container/enchanted shapes; copy/normalize chains inherit the cache from
their source, so planner per-op normalization stays cheap.

## Root cause (vanilla 1.21.1, verified against decompiled + NeoForge-patched sources)

- `PatchedDataComponentMap.equals` = `prototype.equals && patch.equals`, both fastutil maps:
  same-instance compares hit a `==` fast path, but **cross-origin equals** (stock stack vs recipe
  output copy, planner demand vs graph output) walk every entry through `Optional.equals` →
  per-value deep equals; `ItemContainerContents` recurses into nested `ItemStack` compares.
- `hashCode()` is *more* expensive than equals (recomputes the prototype hash every call;
  NeoForge's `hashPatch` does `IntMath.pow(31, n)` per patch entry) — hashing per comparison is
  not viable.
- Prototype maps are per-Item singletons and vanilla **already interns them** (a Guava `Interner`
  inside `Item.Properties.buildComponents`), which the patch-empty shortcut leverages for free.
- The mod's hot loops multiply the per-compare cost: `Stock.add/consume/countMatching*`,
  `ResourceLedger.peekAvailable/consumeByproductSatisfying/countByproductMatching`,
  `RecipeGraph.filterProducersForInput`, `StandardPlanner.findMatchedOutput/variantMatches*/variantsOverlap`.

## What was implemented

| Change | File(s) |
|---|---|
| `ComponentCanonicalizer`: weak-identity cache + equality pool + **generation counter**; `clear()` on datapack reload via `RecipePathSettings.clearRuntimeCaches` (+ soft cap) | `api/content/ComponentCanonicalizer.java` (new) |
| `StackType.canonicalComponents` default; item override returns the prototype singleton for patch-empty stacks | `api/content/type/StackType.java`, `content/type/ItemStackType.java` |
| `TypedContentStack`: lazy cached canonical holder (map + generation) and cached `ComponentStackKey`; `copy/normalize/copyWithAmount` adopt the caches of their COW-sharing source | `api/content/value/TypedContentStack.java` |
| `ContentStack.matches` fast path (exact-component strategies only, `usesExactComponents`): identity first → canonical `==` ⇒ equal; **different representatives in the same pool generation ⇒ provably unequal**; cross-generation falls back to the untouched vanilla path | `api/content/value/ContentStack.java`, `api/content/MatchStrategy.java` |
| `ComponentStackKey`: component-aware amount-map key (value-based hash incl. components; canonical `==` fast equality with deep-equals fallback) replacing the component-blind `TypedContentStack` keying in `Stock.stackAmounts` and `ResourceLedger.byproducts` — also fixes the latent bug where same-item/different-component amounts merged | `api/content/value/ComponentStackKey.java` (new), `api/recipe/routing/Stock.java`, `api/recipe/routing/state/ResourceLedger.java` |

Correctness: reference mismatch across pool generations never yields a wrong answer — it falls
back to the original vanilla comparison. Differential tests assert fast path == slow path ==
`ItemStack.isSameItemSameComponents` over a fixture matrix and 500 randomized cases.

## Benchmarks

`Mod/src/jmh/java/dev/vfyjxf/cirrus/performance/`:

- `ComponentComparisonBenchmark` — micro, 6 scenarios × 4 relations; `McBenchmarkBootstrap`
  bootstraps vanilla registries in the JMH fork (needs the `Multi-Release: true` jmhJar manifest
  and an empty `LoadingModList` installed reflectively — see the class javadoc).
- `StockBenchmark` — linear-scan ops over stocks of 64–4096 entries, 0–100% component-bearing.
- `ItemComponentRoutingBenchmark` + `ItemComponentRoutingScenario` — end-to-end graph build and
  routing over real component-bearing item chains (with a plain-chain reference workload).

Raw results: `Mod/build/results/jmh/phase0-*.json` (baseline) / `phase1-*.json` (optimized);
`compare.py` in the same directory prints the speedup tables.

Run:

```sh
cd CalculatorCirrus
sh gradlew :Mod:jmhJar --no-configuration-cache
java -jar Mod/build/libs/CalculatorCirrus-0.0.1-jmh.jar 'ComponentComparisonBenchmark|StockBenchmark|ItemComponentRoutingBenchmark' \
     -rf json -rff Mod/build/results/jmh/results.json
```

(Do not run two JMH suites concurrently — global `jmh.lock` and measurement pollution.)

## Known trade-offs / out of scope

- Tiny stocks (≤64 entries) with per-op normalization pay the one-time canonicalization
  (~0.2–0.4 µs/op) without enough scan length to amortize it.
- Fully-memoized warm routes regress a few µs per route (canonical setup on fresh demand stacks).
- `CraftingRecipeSystem.Lookup.withOutput` raw-ItemStack scans are outside `ContentStack.matches`
  and not accelerated (candidate for a later phase, along with structural indexes for the
  remaining linear scans).
- Execution drivers, JEI runtime, vanilla classes and the (unused) mixin placeholder are untouched.

## Phase 2 — planner hot path (profiled, data-driven)

Profiling the realistic planning workload (`chainComponentStocked`: 64-deep component chain,
512 recipes, byproducts on every step, partially-stocked inventory with 256 component swords —
`-prof stack`) found two hotspots beyond comparison cost:

1. **`Stock.add/consume/countMatching` linear scans (~18% of plan time)** — replaced with direct
   `ComponentStackKey` map lookups (key equality is exactly `identityAndComponent`, so a map hit
   is equivalent to the old scan; list scans remain for kind/satisfy-based queries). Stock ops are
   now **O(1), size-independent**: at 4096 component entries, countMatching miss 76.9→0.62 µs
   (**124×**), addHit 42.1→0.73 µs (**58×**), consume+refill 79.6→1.4 µs (**55×**).
2. **`RoutingVariantKeys.stackKey` re-hashing the components patch per call (~9%)** — the planner
   builds variant-stable keys in `collectOrderedVariantCoverage`; the deep NBT/Component hash and
   string build are now cached per stack instance in a weak identity map.

End-to-end on the stocked plan (same params, dual-fork, steady-state iterations):

| Workload | Baseline | Optimized | Speedup |
|---|---:|---:|---:|
| routeWithCachedService · chainComponentStocked | ~0.55–0.60 ms | ~0.22 ms | **~2.5×** |
| routeWithFreshService · chainComponent (empty stock) | 1.00 ms | 0.41 ms | **2.4×** |

Post-fix profile: no single dominant hotspot remains — the residual plan time spreads across
planner machinery (candidate scratch snapshots, BitSet ranking), vanilla `ItemStack` construction
in `normalize()` copies, and the graph's bounded lookup caches.

Results archived as `build/results/jmh/phase2-stock.json` / `phase2-routing.json`; true-baseline
comparison jar `build/libs/stocked-baseline.jar` (pre-optimization main sources, same benchmarks).

