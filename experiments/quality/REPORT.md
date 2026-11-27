# Cirrus intelligent planner refactor — experiment report

## Phase 0a — export loader + ontology induction

Two programs (`load_exports.py`, `ontology.py`) turn the 22 raw pack dumps into
a columnar cache and an induced ontology.  Schemas, algorithms and thresholds
are in `DESIGN.md`; this file records what the real run produced.

Everything below is from one clean end-to-end run of `./run_phase0a.sh --force`
on 2026-09-19 (10-core / 16 GB macOS, venv Python 3.12, ijson `yajl2_c`,
pyarrow 25).

### Timings and budgets

| step | wall | peak RSS | budget | verdict |
|---|---:|---:|---|---|
| load 22 packs (streamed, `--force`) | **32.8 s** | **674 MB** | 5–10 min, RSS < 6 GB | 10–18x under |
| ontology, 22 packs + cross-pack prior + full onboarding | **35.6 s** | **2.01 GB** | < 2 min | under |
| — of which per-pack induction | 22.1 s | | | |
| — of which cross-pack prior | 1.4 s | | | |
| — of which 22x leave-one-out onboarding | 11.9 s | | | |
| invariant checks (`tests/test_phase0a.py`, fom, 2 full runs) | 1.0 s | 160 MB | — | 40 checks pass |

The ontology run was measured while a Gradle test build was using the machine,
so treat it as an upper bound.  (v1 of the bigram rule took 71.3 s; the
non-compositional rule is both better and ~2x faster because it mints ~20x
fewer tokens.)

Cache on disk: 66 MB (`cache/`), 54 MB (`out/`) for 694 k recipes / 3.66 M
content rows.  The single 732 MB pack (atm10, 283 422 recipes) parses in
14.4 s at a flat ~500 MB RSS because parquet row groups are flushed every
20 000 recipes.

### Per pack

| pack | recipes | kept | dropped | content rows | parse s | ontology s | ids | token classes | templates | multi-member | coverage |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| atm10 | 283,422 | 283,422 | 0 | 1,584,779 | 15.33 | 8.11 | 62,488 | 361 | 96,393 | 21,423 | 73.6% |
| minecolonies-dimensional-adventure | 71,899 | 71,899 | 0 | 361,214 | 3.14 | 4.42 | 37,448 | 271 | 15,562 | 4,265 | 84.3% |
| create-train | 56,391 | 56,391 | 0 | 253,079 | 2.26 | 1.32 | 13,434 | 165 | 25,093 | 5,191 | 64.7% |
| enigmatica10 | 50,054 | 50,054 | 0 | 259,758 | 2.06 | 1.71 | 28,594 | 225 | 15,983 | 4,377 | 76.8% |
| civilization-isolaria | 30,979 | 30,979 | 0 | 195,747 | 1.46 | 0.96 | 11,756 | 148 | 10,051 | 2,620 | 76.0% |
| mythcrafts | 24,865 | 24,865 | 0 | 118,521 | 0.92 | 0.59 | 10,833 | 115 | 6,697 | 1,916 | 80.8% |
| atm10-lite | 24,624 | 24,624 | 0 | 164,718 | 1.52 | 0.77 | 9,823 | 135 | 9,061 | 2,049 | 71.5% |
| creative-drawers-producer-2 | 19,181 | 19,181 | 0 | 125,711 | 0.92 | 0.57 | 8,044 | 108 | 6,798 | 1,594 | 72.9% |
| create-lets-create | 18,754 | 18,754 | 0 | 93,234 | 0.75 | 0.56 | 13,395 | 114 | 6,451 | 1,855 | 75.5% |
| Brick's Building 1.4.1 | 18,525 | 18,525 | 0 | 74,948 | 0.55 | 0.38 | 4,485 | 66 | 6,791 | 1,663 | 72.3% |
| cobblemon-x-create | 18,397 | 18,397 | 0 | 82,969 | 0.62 | 0.41 | 6,555 | 67 | 7,451 | 1,711 | 68.8% |
| All Of Create 2.1 | 13,744 | 13,744 | 0 | 57,919 | 0.74 | 0.67 | 10,904 | 119 | 6,356 | 1,199 | 62.5% |
| technical-electrical | 12,548 | 12,548 | 0 | 67,318 | 0.49 | 0.40 | 10,336 | 123 | 7,974 | 1,039 | 44.7% |
| create-oneblock | 11,282 | 11,282 | 0 | 49,949 | 0.41 | 0.24 | 2,901 | 50 | 4,248 | 834 | 69.7% |
| create-ultimate | 10,647 | 10,647 | 0 | 42,239 | 0.32 | 0.27 | 7,337 | 84 | 3,595 | 815 | 73.9% |
| CreateCustomised1.1.2 | 10,505 | 10,505 | 0 | 42,942 | 0.30 | 0.22 | 2,916 | 44 | 3,969 | 731 | 69.2% |
| lost-world | 5,140 | 5,140 | 0 | 24,110 | 0.18 | 0.13 | 2,626 | 39 | 2,117 | 412 | 66.8% |
| tfc-enhanced | 4,341 | 4,341 | 0 | 22,775 | 0.15 | 0.14 | 4,242 | 50 | 1,173 | 361 | 81.3% |
| fom | 3,442 | 3,442 | 0 | 15,650 | 0.10 | 0.09 | 2,625 | 38 | 1,769 | 293 | 57.1% |
| farmers-colonies | 2,658 | 2,658 | 0 | 12,148 | 0.09 | 0.06 | 1,213 | 20 | 1,455 | 165 | 51.5% |
| LuckyBlockChallenge | 1,277 | 1,277 | 0 | 5,858 | 0.03 | 0.04 | 995 | 20 | 695 | 125 | 55.4% |
| BlockFront - Official Mod Pack 0.9.0.24b | 1,277 | 1,277 | 0 | 5,858 | 0.03 | 0.03 | 995 | 20 | 695 | 125 | 55.4% |
| **total (22 packs)** | **693,952** | **693,952** | **0** | **3,661,444** | **32.4** | **22.1** | | | | | |

`peak RSS` is process-cumulative rather than per pack (all packs are loaded in
one process), so it is reported once above instead of per row.  Every pack's
row count equals the `recipeCount` written in its own dump header.

**Nothing was dropped anywhere.**  The only drop rule is structural (no inputs
or no outputs) and no recipe in any of the 22 packs is empty on either side.
No rule-based filtering exists in this layer at all — the deliberate difference
from `ExportPackLoader.java`, which drops junk/duplicate recipes through the
builtin classifier before returning.

### Cross-pack prior

| metric | value |
|---|---:|
| distinct path-token sequences (union of 22 packs) | 107,441 |
| interchange evidence pairs | 566,973 |
| promoted bigrams | 724 |
| **token classes** | **540** |
| classified tokens | 2,752 |
| roles: material / form / modifier | 222 / 172 / 146 |
| classes with > 100 members | 2 |
| eight largest classes | 114, 109, 90, 52, 50, 43, 42, 41 |
| context buckets skipped as too wide (> 400) | 6 |
| induction time | 1.4 s |

Written to `out/token-classes.json` (full membership) with the parameters that
produced it.

### Onboarding simulation (fit the prior on 21 packs, then add the 22nd)

| pack | tokens | new tokens | prior classes (21) | classes touched | **new classes** | newly classified tokens |
|---|---:|---:|---:|---:|---:|---:|
| minecolonies-dimensional-adventure | 5,155 | 1,845 | 443 | 366 | 108 | 518 |
| atm10 | 7,667 | 2,197 | 484 | 405 | 65 | 385 |
| create-train | 3,190 | 500 | 521 | 259 | 24 | 104 |
| tfc-enhanced | 796 | 122 | 527 | 97 | 14 | 73 |
| creative-drawers-producer-2 | 2,172 | 66 | 537 | 199 | 6 | 24 |
| mythcrafts | 1,864 | 92 | 536 | 196 | 6 | 37 |
| technical-electrical | 3,557 | 915 | 534 | 233 | 6 | 44 |
| cobblemon-x-create | 2,225 | 934 | 539 | 158 | 4 | 52 |
| atm10-lite | 2,458 | 26 | 538 | 229 | 3 | 11 |
| create-ultimate | 1,560 | 17 | 540 | 153 | 3 | 10 |
| enigmatica10 | 4,157 | 38 | 537 | 321 | 3 | 17 |
| All Of Create 2.1 | 2,151 | 37 | 539 | 199 | 2 | 8 |
| CreateCustomised1.1.2 | 971 | 11 | 539 | 105 | 2 | 4 |
| civilization-isolaria | 3,125 | 127 | 542 | 253 | 1 | 6 |
| BlockFront - Official Mod Pack 0.9.0.24b | 399 | 0 | 540 | 51 | 0 | 0 |
| Brick's Building 1.4.1 | 1,176 | 1 | 541 | 135 | 0 | 0 |
| LuckyBlockChallenge | 399 | 0 | 540 | 51 | 0 | 0 |
| create-lets-create | 1,921 | 13 | 542 | 192 | 0 | 0 |
| create-oneblock | 901 | 1 | 540 | 109 | 0 | 1 |
| farmers-colonies | 566 | 0 | 540 | 64 | 0 | 0 |
| fom | 929 | 1 | 540 | 94 | 0 | 0 |
| lost-world | 707 | 0 | 541 | 85 | 0 | 0 |

Reading: onboarding a *typical* new pack costs almost nothing — 8 of 22 packs
introduce zero new classes and reuse 80–470 existing ones; only the two packs
that supply most of the corpus vocabulary (minecolonies 108, atm10 65)
introduce a large number.  The prior is worth carrying: a fresh pack is
classified by 80–470 pre-existing classes before a single new one is induced.
`out/onboarding.json` holds the per-pack detail and examples.

### Purity — a dozen induced classes (nothing here is a word list)

From the cross-pack prior (`out/token-classes.json`), ordered by evidence mass;
`role` is the positional majority vote, members verbatim:

```
cls:black          role=modifier n= 16  black, blue, brown, cyan, gray, green, light_blue, light_gray,
                                        lime, magenta, orange, pink, purple, red, white, yellow
cls:acacia         role=modifier n= 11  acacia, bamboo, birch, cherry, crimson, dark_oak, jungle,
                                        mangrove, oak, spruce, warped
cls:alpha          role=modifier n= 38  alpha, aspen, baobab, blackwood, brimwood, cika, cobalt,
                                        cypress, dead, ebony, empyreal, eucalyptus, fir, florus, …
cls:alder          role=material n=109  alder, allspice, almond, apricorn, apricot, aquilaria, avocado,
                                        balsa, bay_leaf, beech, blackthorn, boxwood, brazil_nut, …
cls:chert          role=material n= 12  chert, claystone, conglomerate, dacite, dolomite, gabbro,
                                        gneiss, phyllite, quartzite, rhyolite, schist, shale
cls:fancy          role=modifier n=  5  fancy, flat, raised, small, tall
cls:1x             role=form     n=  9  1x, 2x, 3x, 4x, 5x, 6x, 7x, 8x, 9x
cls:16k            role=form     n= 10  16k, 16m, 1k, 1m, 256k, 256m, 4k, 4m, 64k, 64m
cls:0              role=form     n= 16  0, 1, 2, … 15
cls:bookshelf      role=form     n=  9  bookshelf, button, hanging_sign, leaves, pressure_plate,
                                        sapling, sign, stripped_log, stripped_wood
cls:bookcase       role=form     n=  6  bookcase, grandfather_clock, label, potion_shelf, shelf,
                                        tool_rack
cls:biomesoplenty  role=modifier n=  3  biomesoplenty, biomeswevegone, regions_unexplored
cls:banyin         role=modifier n=  7  banyin, cradlewood, jinglestem, lunar, northland, scarlet,
                                        torreya
cls:aether_skyroot role=modifier n= 15  aether_skyroot, allthemodium_ancient, allthemodium_demonic,
                                        allthemodium_soul, deeperdarker_bloom, deeperdarker_echo, …
```

`cls:biomesoplenty` is worth pointing at: the corpus induced a *mod-name* class
on its own, because those three namespaces appear interchangeably in the same
id slot.  Nothing in the code knows what a mod is.

Per pack the classes are tighter still — fom (`out/fom/token-classes.json`):

```
cls:black       n=16  the 16 dye colours (light_blue / light_gray arrive as promoted bigrams)
cls:acacia      n=12  acacia, bamboo, birch, cherry, crimson, dark_oak, jungle, mangrove, oak,
                      pale_oak, spruce, warped
cls:asurine     n=13  asurine, calcite, crimsite, deepslate, diorite, dripstone, granite, limestone,
                      ochrum, scorchia, scoria, tuff, veridium          <- the stone-type class
cls:stripped_acacia n=7  stripped_<each wood>
cls:exposed     n= 7  exposed, oxidized, waxed, waxed_exposed, waxed_oxidized, waxed_weathered,
                      weathered                       <- copper oxidation states, induced
cls:banner      n=23  banner, bed, candle, carpet, chair, concrete_powder, cushion, dye, envelope,
                      floor_chair, glazed_terracotta, handle, harness, nameplate, portstone, postbox,
                      seat, sharestone, …              <- the "dyeable form" class
cls:automatic_clutch n=16  automatic_clutch, chain_conveyor, clutch, configurable_gearbox, deployer,
                      depot, encased_fan, gearbox, gearshift, mechanical_drill, mechanical_harvester,
                      mechanical_plough, mechanical_roller, mechanical_saw, mixer, press
                                                      <- the Create machine class
cls:brick_slab  n= 4  brick_slab, brick_stairs, brick_wall, bricks
cls:axe         n= 9  axe, boots, chestplate, helmet, hoe, leggings, pickaxe, shovel, sword
cls:alchemy     n=14  alchemy, compacting, deposit, feeding, filter, jukebox, magnet, mob_catcher,
                      pickup, pump, refill, restock, tool_swapper, void  <- upgrade-kind class
```

Identity quadruples read as expected, e.g.

```
minecraft:light_blue_wool       tokens=[light_blue, wool]        material=cls:black   form=cls:banner
minecraft:oak_stairs            tokens=[oak, stairs]             material=cls:acacia  form=cls:slab
minecraft:dark_oak_stairs       tokens=[dark_oak, stairs]        material=cls:acacia  form=cls:slab
create:cut_limestone_brick_slab tokens=[cut, limestone, brick_slab]
                                material=cls:asurine  form=cls:brick_slab  modifiers=[cls:cut]
minecraft:waxed_copper_block    tokens=[waxed, copper, block]
                                material=t:copper     form=t:block         modifiers=[cls:exposed]
```

### Template folding

Largest fom templates (`out/fom/templates.parquet`):

| size | system | example recipe ids |
|---:|---|---|
| 78 | minecraft:stonecutting | `create:small_limestone_brick_wall_from_stone_types_limestone_stonecutting`, `create:cut_asurine_brick_wall_from_…`, `create:cut_scorchia_bricks_from_…` |
| 48 | minecraft:crafting | `minecraft:dye_pink_wool`, `minecraft:dye_green_bed`, `minecraft:dye_white_carpet` |
| 32 | minecraft:crafting | `interiors:crafting/floor_chair/white_floor_chair_from_seat`, … |
| 26 | minecraft:crafting | `create:small_veridium_brick_slab_recycling`, `create:cut_granite_brick_slab_recycling`, … |
| 26 | minecraft:stonecutting | `create:cut_limestone_stairs_from_…`, `create:cut_ochrum_wall_from_…` |
| 26 | minecraft:crafting | `create:cut_ochrum_brick_stairs`, `create:small_tuff_brick_stairs`, … |

The 48-member dye template folds *colour x dyeable form* into one signature:

```json
{"sys":"minecraft:crafting",
 "mach":["cls:crafting|t:table|"],
 "in":["stack|item|cls:black|cls:banner|||1|1","typedAny|item|cls:black|cls:banner|||1|1"],
 "out":["stack|item|cls:black|cls:banner|||1|1"],
 "pat":["in:stack:identityAndComponent","in:typedAny:identity","out:stack:identityAndComponent"],
 "rid":[]}
```

and the 78-member stonecutting template folds *stone type x brick form* — with
`cut` recognised as a processing-state modifier — into one:

```json
{"sys":"minecraft:stonecutting",
 "mach":["|t:stonecutter|"],
 "in":["tag|item|t:types|cls:asurine|t:stone||1|1"],
 "out":["stack|item|cls:asurine|cls:brick_slab|cls:cut||1|1"],
 "pat":["in:tag:identity","out:stack:identityAndComponent"],
 "rid":["from","stone","types","stonecutting"]}
```

Component key-set folding (variant classes) — the packs where it matters:

| pack | key-sets | variant classes | distinct variant keys | key-set fold | variant fold |
|---|---:|---:|---:|---:|---:|
| atm10 | 223 | 145 | 6,068 | 1.54x | 41.8x |
| atm10-lite | 75 | 38 | 2,052 | 1.97x | 54.0x |
| create-train | 35 | 25 | 1,324 | 1.40x | 53.0x |
| enigmatica10 | 123 | 100 | 726 | 1.23x | 7.3x |
| minecolonies-dimensional-adventure | 37 | 22 | 347 | 1.68x | 15.8x |
| farmers-colonies | 3 | 2 | 163 | 1.50x | 81.5x |

i.e. on atm10 6,068 concrete component payloads collapse into 145 variant
classes; folding is strongest exactly where variant-heavy content (enchanted
books, potions, coloured storage) dominates.

### Comparison with the plan's fom sanity target

| quantity | prototype target | this run | note |
|---|---:|---:|---|
| recipes | 3,442 | 3,442 | exact |
| templates | ~1,931 | 1,769 | 8% fewer (folds slightly harder) |
| multi-member templates | 382 | 293 | |
| coverage by multi-member templates | ~55% | 57.1% | |
| dye recipes in one template | 42 | 48 | ours also folds bed/carpet/banner into the same dyeable-form class |
| stone wall/stair/slab family | 28 in one template | 78 in one template | stone type x brick form x `cut` modifier now fold together; the crafting-side reverse is a separate 26-member `_recycling` template |
| material classes | ~49 | 22 | see below |
| form classes | ~31 | 14 | see below |
| colour class | 14 | 1 class of 16 members | one clean class rather than 14 |
| dyeable-form class | yes | yes (`cls:banner`, 23 members) | |
| processing-state (chiseled/cut/polished/smooth) | yes | yes (`cls:cut` as a modifier slot) | visible in the 78-member template's `modifiers=[cls:cut]` |
| copper oxidation | yes | yes (`cls:exposed`, 7 members) | |
| Create machine class | yes | yes (`cls:automatic_clutch`, 16 members) | |

The class counts differ because the two algorithms carve the same structure
differently: this run produces *fewer, larger* classes (38 classes covering 224
tokens on fom) where the prototype produced many small ones, and it merges what
the prototype counted as 14 separate colour classes into one 16-member colour
class.  The template numbers — the thing that actually matters downstream — land
within 10% of the target, and every qualitative check (dye family, stone family,
processing state, copper oxidation, Create machines, dyeable forms) reproduces.

### v1 -> v2: bigram promotion by non-compositionality

**The defect.**  v1 promoted a two-token run to a single token when it had acted
as a unit `>= 3` times.  That frequency rule also promotes *compositional*
compounds, because those genuinely do align with single tokens:
`<colour>_<wood>_seat` aligns with `<other colour>_seat`, so `<colour>_<wood>`
looked like a unit.  Promoting them (a) kept the colour class and the wood class
apart instead of composing them, (b) minted thousands of one-off tokens that
then interchanged only with each other and collapsed into blobs, and (c) split
one recipe shape across two templates depending on whether a compound happened
to clear the threshold.

**The fix** (DESIGN.md §2.1): a bigram `a_b` is promoted only when its *unit*
evidence — single-token partners other than `a` and `b`, in at least 2 distinct
contexts, with at least 2 distinct partners — is at least as large as the
competing *droppable-modifier* reading, measured as `solo(a)`: the number of
contexts in which the head token `a` interchanges on its own.  A head that
stands alone all over the corpus was composed with the next token; a head that
never stands alone (the first half of a two-token colour) was not.

Decisions on the tokens the review called out (atm10 / fom):

| bigram | v1 | v2 | why |
|---|---|---|---|
| `light_blue`, `light_gray` | promoted | **promoted** | `light` never fills the slot alone (`solo=0`) |
| `dark_oak` | promoted | **promoted** | 659 unit contexts vs a tiny `solo(dark)` |
| `glazed_terracotta`, `stained_glass`, `pressure_plate` | promoted | **promoted** | bound heads |
| `black_acacia`, `blue_acacia`, … | promoted | **rejected** | `black` fills the same slot alone in hundreds of contexts |
| `circular_black`, `dim_black`, `elite_black` | promoted | **rejected** | same |
| `acacia_seat`, `acacia_fence`, `acacia_upper` | promoted | **rejected** | `acacia` is a productive slot filler |
| `a_black`, `d_black`, … | promoted | **rejected** | same |
| `waxed_exposed` (atm10) | promoted | **rejected** | `waxed` is a droppable modifier there — the correct reading |
| `fence_gate`, `concrete_powder` (atm10) | promoted | **rejected** | heads stand alone; in fom, where they do not, `concrete_powder` is still promoted |

**Corpus effect.**

| metric | v1 | v2 |
|---|---:|---:|
| promoted bigrams, cross-pack prior | 24,672 | **724** |
| promoted bigrams, sum over packs | 63,055 | **3,207** |
| prior token classes | 959 | **540** |
| prior classified tokens | 7,055 | **2,752** |
| prior classes with > 100 members | 7 | **2** |
| prior classes with > 50 members | 18 | **4** |
| eight largest prior classes | 371, 280, 259, 195, 181, 141, 138, 100 | **114, 109, 90, 52, 50, 43, 42, 41** |
| onboarding: new classes summed over 22 packs | 433 | **247** |
| onboarding: packs adding zero new classes | 7 | **8** |
| ontology wall (22 packs + prior + onboarding) | 71.3 s | **35.6 s** |
| onboarding phase | 29.2 s | **11.9 s** |

The two classes still over 100 members (114 furniture-ish forms, 109 crop/wood
material names) are single slots with genuinely many fillers, not promotion
artefacts.

**Per pack.**  Template counts fall and multi-member coverage rises almost
everywhere — the same shapes now fold together instead of being split by an
accidental compound token:

| pack | promoted v1 | promoted v2 | classes v1 | classes v2 | templates v1 | templates v2 | multi v1 | multi v2 | cover v1 | cover v2 | new cls v1 | new cls v2 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| atm10 | 14,264 | 546 | 550 | 361 | 109,794 | 96,393 | 19,205 | 21,423 | 68.0% | 73.6% | 145 | 65 |
| minecolonies-dimensional-adventure | 6,270 | 284 | 374 | 271 | 19,683 | 15,562 | 4,424 | 4,265 | 78.8% | 84.3% | 167 | 108 |
| create-train | 3,692 | 204 | 207 | 165 | 28,625 | 25,093 | 4,659 | 5,191 | 57.5% | 64.7% | 32 | 24 |
| enigmatica10 | 8,329 | 350 | 276 | 225 | 16,711 | 15,983 | 3,971 | 4,377 | 74.6% | 76.8% | 7 | 3 |
| civilization-isolaria | 4,269 | 214 | 181 | 148 | 10,164 | 10,051 | 2,527 | 2,620 | 75.3% | 76.0% | 7 | 1 |
| mythcrafts | 3,191 | 141 | 137 | 115 | 7,298 | 6,697 | 1,710 | 1,916 | 77.5% | 80.8% | 10 | 6 |
| atm10-lite | 2,338 | 153 | 159 | 135 | 9,851 | 9,061 | 1,800 | 2,049 | 67.3% | 71.5% | 2 | 3 |
| creative-drawers-producer-2 | 2,098 | 146 | 124 | 108 | 7,314 | 6,798 | 1,498 | 1,594 | 69.7% | 72.9% | 11 | 6 |
| create-lets-create | 4,076 | 156 | 147 | 114 | 7,573 | 6,451 | 1,501 | 1,855 | 67.6% | 75.5% | 0 | 0 |
| Brick's Building 1.4.1 | 1,134 | 66 | 71 | 66 | 9,167 | 6,791 | 1,422 | 1,663 | 58.2% | 72.3% | 4 | 0 |
| cobblemon-x-create | 1,371 | 101 | 75 | 67 | 8,862 | 7,451 | 1,507 | 1,711 | 60.0% | 68.8% | 9 | 4 |
| All Of Create 2.1 | 3,603 | 175 | 178 | 119 | 6,895 | 6,356 | 1,341 | 1,199 | 59.6% | 62.5% | 2 | 2 |
| technical-electrical | 2,502 | 155 | 155 | 123 | 8,387 | 7,974 | 996 | 1,039 | 41.1% | 44.7% | 5 | 6 |
| create-oneblock | 697 | 58 | 54 | 50 | 4,859 | 4,248 | 729 | 834 | 63.4% | 69.7% | 0 | 0 |
| create-ultimate | 2,074 | 115 | 95 | 84 | 4,280 | 3,595 | 756 | 815 | 66.9% | 73.9% | 8 | 3 |
| CreateCustomised1.1.2 | 692 | 62 | 57 | 44 | 5,048 | 3,969 | 772 | 731 | 59.3% | 69.2% | 2 | 2 |
| lost-world | 461 | 54 | 40 | 39 | 2,621 | 2,117 | 371 | 412 | 56.2% | 66.8% | 0 | 0 |
| tfc-enhanced | 903 | 86 | 61 | 50 | 1,496 | 1,173 | 272 | 361 | 71.8% | 81.3% | 22 | 14 |
| fom | 536 | 63 | 41 | 38 | 1,771 | 1,769 | 314 | 293 | 57.7% | 57.1% | 0 | 0 |
| farmers-colonies | 215 | 24 | 24 | 20 | 1,477 | 1,455 | 137 | 165 | 49.6% | 51.5% | 0 | 0 |
| LuckyBlockChallenge | 170 | 27 | 24 | 20 | 735 | 695 | 104 | 125 | 50.6% | 55.4% | 0 | 0 |
| BlockFront - Official Mod Pack 0.9.0.24b | 170 | 27 | 24 | 20 | 735 | 695 | 104 | 125 | 50.6% | 55.4% | 0 | 0 |
| **total** | **63,055** | **3,207** | | | | | | | | | **433** | **247** |

fom is the only pack that moves slightly the wrong way on coverage
(57.7% -> 57.1%, still above the 55% floor): with 63 merged tokens instead of
536, a handful of small decoration families that v1 had accidentally welded
into one token each now abstract at finer granularity.  Its headline folds are
unchanged (dye template still 48 members) and its stone family folds much
harder (two templates of 33 -> one of 78).

### Anomalies found in the data

Counted over all 22 packs / 3.66 M content rows:

1. **`chance` is a constant.**  Every one of the 3,661,444 content rows has
   `chance == 1.0` — in every pack, for every source (`vanilla`, `jei`,
   `custom`).  The plan expected JEI-sourced systems to export `0`; neither
   happens.  **The field carries no information in this corpus** and must not
   be used as a feature until the exporter is fixed.  The loader still stores
   it raw and flags `chance_zero` / `chance_missing` if it ever varies.
2. **No `value` contents at all.**  Kind histogram: `stack` 3,076,175 ·
   `tag` 466,745 · `any` 105,409 · `typedAny` 7,562 · `complex` 5,553 ·
   **`value` 0**.  No time/energy value was exported by any pack, so the
   economy phase has no exported process cost to work from and will have to
   derive cost from structure (machines, systems) or from a re-export.
3. **No custom stack types.**  Only `item` (5,164,557) and `fluid` (34,721)
   ever appear as a stack family; no mod content type (gas, chemical, mana…)
   survived into the dumps.
4. **No `2147483647` sentinel amounts and no `|+N` suffixes.**  Both are
   handled (null + `sentinel_amount`, strip + `suffix_stripped`) but neither
   fires on this corpus.  Amounts range 1…1000, and `count` inside a stack
   never disagrees with the entry `amount` (0 `count_amount_mismatch` rows), so
   `inner_count` is currently redundant.
5. **200,053 recipes (28.8%) have an empty `id`.**  They concentrate in
   JEI-category systems: atm10 alone has 102,739 (19,507 in
   `jei:category/sawmill/woodcutting`, 13,754 in
   `jei:category/minecolonies/stonemason`, …).  These recipes have no residual
   id tokens, so they fold on structure alone — which is right, but it means
   any id-token feature is null for nearly a third of the corpus.
6. **5,566 recipes have no machine**, concentrated in atm10 (2,653) and
   create-train (1,929).
7. **192,014 tag contents have no known tag family.**  atm10 and atm10-lite
   ship no `tag-types-*.json`; those tags fall back to `item`, exactly like
   `ExportPackLoader.resolveFamily`.  The other 20 packs resolve 37,606 tags
   from their registries.
8. **Two packs are the same export twice.**  `BlockFront - Official Mod Pack
   0.9.0.24b` and `LuckyBlockChallenge` produce identical `recipes.parquet`
   (pack column aside) — same 1,277 recipes, same 735 templates.  Any
   cross-pack split must keep them on the same side or it leaks.
9. **Tag / alternative member order is not stable across exports.**  The two
   duplicate packs differ *only* in the order of `values[]` in 253 tag entries.
   Fixed here: `members` keeps the export order (fidelity) but `base_id`,
   `inner_count` and `variant_key` for `any`/`typedAny` are taken from the
   lexicographically smallest member, so template ids survive a re-export —
   verified: the duplicate pair now yields byte-identical template assignments.
10. **The `systems[]` summary understates JEI systems in some packs.**
    enigmatica10, minecolonies and fom declare 13 systems while their recipes
    use 7–10 of them; atm10 declares 505 and uses 375.  No recipe references an
    undeclared system, so `systems.parquet` is a safe join, but "which systems
    does this pack have" must be read from the recipes, not from the summary.
    Relatedly, 16,019 of enigmatica10's 16,652 JEI recipes are
    `jei:category/minecraft/anvil` — one generator dominates that pack.
11. `complex` contents (5,553) are all the `FallbackComplexContent`
    implementation; they are kept and flagged (`has_complex`, 2,964 recipes)
    rather than modelled.

### Invariants checked (`tests/test_phase0a.py`, 40 checks)

* `len(recipes.parquet) == recipeCount` from the dump; `contents.parquet` has
  exactly one row per exported input/output entry; `n_inputs + n_outputs` sums
  to it; `recipe_idx` is dense stream order and contents stay ordered by it.
* `dropped` iff "no inputs or no outputs", and `dropped` equals the sum of the
  per-reason counters — i.e. no rule ever removed a recipe.
* Sentinel amounts are stored as null.
* Every kept recipe has exactly one template; template sizes sum to the
  assignments; template ids are unique; token classes are a disjoint partition
  of >=2-member groups.
* **Determinism**: two full runs produce byte-identical `recipes.parquet`,
  `contents.parquet`, `systems.parquet`, `identity.parquet`,
  `templates.parquet`, `recipe_template.parquet` and `token-classes.json`, plus
  identical `recipe_idx -> template_id` mappings.
* **No rule vocabulary**: the three modules are parsed with `ast` and rejected
  if any executable string literal or identifier (docstrings excluded) contains
  a registry-shaped `ns:path` or a material / colour / mod / rule word.
* **Bigram promotion** (added with v2): a vocabulary-free synthetic corpus
  reproduces both shapes — two families of slot fillers that also compose
  (`m1_s1_f1`, the `black_acacia` shape) and a bound prefix that never fills the
  slot alone (`p1_m1_f1`, the `light_blue` shape).  The test asserts the
  compositional compound still clears the old frequency gates (3 contexts) yet
  is **rejected**, and that the bound-prefix bigram is **promoted**.  On real
  data it re-derives `unit_contexts` and `solo(head)` from the buckets and
  asserts no promoted bigram is out-weighed by its head standing alone.
* fom guard rails: multi-member coverage >= 55% and the colour x dyeable-form
  template still folds to >= 48 members.

---

## Phase 0b — the economy layer

`economy.py`, 22 packs, 693,952 recipes.  Everything below is reproduced by
`./run_phase0b.sh`; every table is read back out of the written parquet.

### v1 → v2 of the value stage

The v1 value layer reproduced the round-006 failure this project exists to fix.
Stages 1, 2, 4, 6 and 7 were not touched; the value solve and the acquirability
composite were rebuilt around four defects:

| # | v1 defect | v2 |
|---|---|---|
| 1 | leaf seeds spanned **119×** on fom, so the leaf score outweighed whole crafting chains | seeds bounded in `[1, K]`, `--seed-spread` default 2 |
| 2 | `n_forms_of_material` in the composite seeded `diamond_ore` cheap (14 forms in `cls:diamond`) and inflated `nether_star` (6 "forms" it does not have) | dropped from the composite, kept in the table — PC1 variance *rose*, fom 39 % → 45 %, farmers-colonies 39 % → 46 % |
| 3 | recipes were free, so `diamond == diamond_ore` exactly and a nine-deep chain cost what its leaves cost | `step_cost × (1 + recipe tier)` per recipe, `--step-cost` default 0.25 |
| 4 | **every** ring was bootstrapped at the global cheapest leaf seed, so `iron_ingot` = `raw_iron` = `cobblestone` = 0.0913 | only *closed* rings are bootstrapped, at their own cheapest member's seed; rings with an outside entry settle normally |

Plus the two hooks asked for: producer cost is divided by reliability
(normalised — see below), and `solve_hypergraph` / `solve_to_fixpoint` take
`recipe_weight` and `exclude` for Phase 0c to feed role posteriors back in.

**One deviation, flagged for review.**  The brief said "effective producer cost
= recipe cost / reliability".  Implemented literally that penalises *depth*,
not untrustworthiness: reliability carries a corpus-wide constant (the field
ceiling — `chance` and `value` are degenerate in every pack, so nothing scores
above 0.5), so a raw `1 / reliability` multiplies **every** step by ≥ 2 and
compounds to ~1000× over a ten-deep chain for a fact that distinguishes no two
recipes.  v2 therefore uses `weight = max reliability in the pack / this
recipe's reliability` — the same formula with the uninformative constant
divided out, leaving the ~1×–2× that actually varies between recipes.  This is
not a free choice: under the literal form `white_dye` "passes" its acceptance
case and under the normalised form it fails by 6.9 %, and I do not trust a pass
that comes from penalising bone meal's extra chain step with a constant.  Both
numbers are in the acceptance table; overrule me and it is a one-line change.

### Timings and budgets

| step | wall | peak RSS | budget | verdict |
|---|---:|---:|---|---|
| all 22 packs (cross-pack scan + 9 stages + write) | **17.2-18.4 s** | **2.2-2.4 GB** | < 5 min | 16x under |
| — of which the cross-pack leaf scan | 1.9 s | | | |
| — of which per-pack work | 16.7 s | | | |
| atm10 alone (283,422 recipes / 1.58 M content rows) | **7.7 s** | | <= 1 min | 8x under |
| atm10-lite | 0.41 s | | | |
| fom | 0.07 s | | | |
| invariants (`tests/test_phase0b.py`, 2 full fom runs) | 0.5 s | 140 MB | — | 65 checks pass |

atm10's 7.7 s splits as graph 0.65 · shape 1.41 · pools 0.82 · incidence 0.38 · tiers 1.71 · reliability 0.11 · values 0.92 · leaves 0.30 · values(fitted) 0.91 · write 0.42.
Peak RSS is process-cumulative (all packs in one process) and is dominated by
atm10's graph build.  The v2 value stage costs no more than v1 despite the
extra ring analysis, because only *closed* rings are now bootstrapped and the
per-drain work shrank with them.

Two things were profiled and fixed rather than waited on:

* **17.3 s → 0.54 s** on atm10's graph build, by routing every string column
  through arrow's dictionary encoder instead of comparing numpy object arrays.
* **815 → 33 cycle rounds** on fom's tier solve, by solving machine tiers in a
  separate machine-free pass instead of letting the crafting table (built in a
  crafting table) gate its own pack.

### Per pack

conv/gen/recyc/noop/prod are shares of that pack's kept recipes; `band` is the
*learned* upper edge of the conversion ratio band; `pools` counts strong pools;
`rings` counts items priced as closed generation rings; `unb` counts machines
with no in-pack producer.

| pack | recipes | conv% | gen% | recyc% | noop% | prod% | band | pools | items | leaves | gap | rings | maxT | mach | unb | rel | s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| atm10 | 283,422 | 16.5 | 0.1 | 11.1 | 0.6 | 71.7 | 9 | 1,631 | 62,488 | 3,805 | 1,540 | 874 | 32 | 677 | 33 | 0.42 | 7.7 |
| minecolonies-dimensional-adventure | 71,899 | 9.2 | 0.0 | 26.4 | 0.1 | 64.3 | 2.7953 | 288 | 37,448 | 2,020 | 421 | 453 | 24 | 126 | 11 | 0.42 | 2.5 |
| create-train | 56,391 | 20.3 | 0.3 | 10.5 | 0.3 | 68.6 | 8 | 358 | 13,434 | 1,074 | 45 | 160 | 19 | 129 | 4 | 0.44 | 1.0 |
| enigmatica10 | 50,054 | 18.7 | 0.4 | 24.6 | 0.4 | 56.0 | 2 | 406 | 28,594 | 2,233 | 1,425 | 1,169 | 19 | 49 | 9 | 0.41 | 1.4 |
| civilization-isolaria | 30,979 | 6.7 | 0.1 | 35.5 | 0.7 | 57.0 | 9 | 210 | 11,756 | 3,016 | 903 | 919 | 26 | 72 | 16 | 0.42 | 0.6 |
| mythcrafts | 24,865 | 12.1 | 0.5 | 30.8 | 0.1 | 56.6 | 9 | 245 | 10,833 | 2,350 | 207 | 218 | 17 | 34 | 1 | 0.40 | 0.4 |
| atm10-lite | 24,624 | 23.2 | 0.0 | 12.0 | 0.5 | 64.3 | 9 | 385 | 9,823 | 877 | 214 | 65 | 38 | 205 | 4 | 0.44 | 0.4 |
| creative-drawers-producer-2 | 19,181 | 17.7 | 0.0 | 33.1 | 0.7 | 48.5 | 9 | 251 | 8,044 | 1,151 | 458 | 537 | 27 | 56 | 6 | 0.39 | 0.3 |
| create-lets-create | 18,754 | 30.4 | 0.6 | 10.4 | 0.3 | 58.3 | 9 | 350 | 13,395 | 2,520 | 752 | 278 | 22 | 23 | 2 | 0.44 | 0.4 |
| brick-s-building-1-4-1 | 18,525 | 34.7 | 0.1 | 3.2 | 0.7 | 61.3 | 6 | 235 | 4,485 | 302 | 47 | 52 | 17 | 45 | 0 | 0.45 | 0.3 |
| cobblemon-x-create | 18,397 | 28.1 | 0.0 | 8.3 | 1.0 | 62.5 | 6 | 113 | 6,555 | 2,011 | 28 | 90 | 15 | 123 | 2 | 0.46 | 0.3 |
| all-of-create-2-1 | 13,744 | 28.1 | 0.4 | 0.5 | 0.6 | 70.4 | 9 | 270 | 10,904 | 2,749 | 783 | 193 | 10 | 7 | 0 | 0.47 | 0.3 |
| technical-electrical | 12,548 | 23.9 | 0.6 | 0.2 | 0.8 | 74.5 | 9 | 219 | 10,336 | 1,802 | 445 | 173 | 14 | 7 | 0 | 0.47 | 0.3 |
| create-oneblock | 11,282 | 32.1 | 0.0 | 4.8 | 0.5 | 62.6 | 8 | 95 | 2,901 | 194 | 11 | 44 | 15 | 37 | 0 | 0.45 | 0.2 |
| create-ultimate | 10,647 | 31.1 | 0.0 | 0.9 | 0.6 | 67.4 | 4 | 348 | 7,337 | 692 | 117 | 158 | 9 | 7 | 0 | 0.47 | 0.2 |
| createcustomised1-1-2 | 10,505 | 28.2 | 0.0 | 0.4 | 0.9 | 70.5 | 6 | 86 | 2,916 | 240 | 59 | 47 | 12 | 26 | 0 | 0.45 | 0.2 |
| lost-world | 5,140 | 19.2 | 2.2 | 9.0 | 0.0 | 69.6 | 9 | 32 | 2,626 | 309 | 42 | 44 | 11 | 21 | 0 | 0.45 | 0.1 |
| tfc-enhanced | 4,341 | 19.7 | 2.1 | 2.6 | 0.0 | 75.5 | 9 | 12 | 4,242 | 1,005 | 116 | 54 | 5 | 7 | 4 | 0.46 | 0.1 |
| fom | 3,442 | 26.6 | 0.0 | 0.1 | 0.9 | 72.5 | 9 | 81 | 2,625 | 286 | 66 | 44 | 10 | 7 | 0 | 0.48 | 0.1 |
| farmers-colonies | 2,658 | 21.0 | 0.0 | 12.6 | 0.0 | 66.5 | 9 | 16 | 1,213 | 188 | 60 | 28 | 7 | 11 | 0 | 0.41 | 0.0 |
| blockfront-official-mod-pack-0-9-0-24b | 1,277 | 39.9 | 0.0 | 0.1 | 0.0 | 60.0 | 9 | 16 | 995 | 158 | 36 | 25 | 6 | 7 | 0 | 0.48 | 0.0 |
| luckyblockchallenge | 1,277 | 39.9 | 0.0 | 0.1 | 0.0 | 60.0 | 9 | 16 | 995 | 158 | 36 | 25 | 6 | 7 | 0 | 0.48 | 0.0 |

Corpus shares: **production 66.3 % · conversion 18.3 % · recycling-shaped
14.8 % · noop 0.5 % · generation 0.2 %**.  29,140 leaves, of which 7,811 are
data gaps and 21,329 world resources.  Cross-pack: 112,446 distinct ids over
21 deduplicated groups; 626 ids are produced by a zero-input recipe somewhere;
8,089 ids are a leaf in *every* pack that ships them.

**Duplicate packs were found by content, not by name.**  The sha1 of the sorted
template multiset puts `blockfront-official-mod-pack-0-9-0-24b` and
`luckyblockchallenge` in one group (`a3960035550306ed`); every cross-pack count
above uses the 21 groups, not the 22 directories.

### The learned conversion band

The band is genuinely per pack, not a dressed-up constant.  Distribution of
the learned upper edge over the 22 packs: **9.0 ×14** (the block↔nugget ratio —
the round-trip evidence really is dominated by 1:9 pack/unpack pairs),
8.0 ×2 (`create-train`, `create-oneblock`), 6.0 ×3 (`brick's-building`,
`cobblemon-x-create`, `createcustomised`), 4.0 (`create-ultimate`),
2.7953 (`minecolonies-dimensional-adventure`), 2.0 (`enigmatica10`).  fom's band came from 377
confirmed ratios, atm10-lite's from 4,160.

### Value regimes

Every recipe now carries a step cost, so the neutral point of
`value_ratio = Σ out / Σ in` is no longer 1.0 but `1 + step / Σ in`: a recipe
that is its output's cheapest chain lands just above 1, not exactly on it.

| | fom | farmers-colonies |
|---|---:|---:|
| ratio quantiles p01/p25/p50/p75/p99 | 0.30 / 1.05 / 1.18 / 1.49 / 2.90 | 0.11 / 0.88 / 1.30 / 1.78 / 4.62 |
| max | 8.7 | 6.6 |
| `< 1` (material destroyed) | 374 (11 %) | 851 (32 %) |
| `>= 1` and `<= 1.02` | 303 | 41 |
| `> 1.02` | 2,765 | 1,766 |

The v1 report's three-regime split does not survive the step cost and should
not be read across: with a per-step charge, almost every recipe's outputs are
worth more than its inputs, and the informative statistic is how *far* above
the step-cost floor a recipe sits.  What does survive unchanged is the
structural fact that **regime B cannot exist**: if a recipe is the unique
producer of its single output, that output's value *is* this recipe's cost, so
its ratio is pinned — a ratio above the floor still means only that the output
has a cheaper alternative producer, that the recipe has several outputs each
priced as the sole product, or that the output sits in a bootstrapped ring.

### Acceptance check — the four cases the audit named

Effective producer cost = `(Σ input value × amount + step cost) × weight /
output amount`, exactly what the solver minimises.  **Cheapest wins.**  Run at
`--seed-spread` K ∈ {1, 2, 4}, `--step-cost 0.25`.

| case | K | winner | cost | runner-up | cost | verdict |
|---|---:|---|---:|---|---:|---|
| farmers-colonies `blue_dye` | 1 | **`blue_dye_from_cornflower`** | 2.428 | `blue_dye` (lapis) | 3.390 | pass |
| | 2 | **`blue_dye_from_cornflower`** | 4.418 | `blue_dye` (lapis) | 5.446 | **pass** |
| | 4 | **`blue_dye_from_cornflower`** | 8.063 | `blue_dye` (lapis) | 8.851 | pass |
| farmers-colonies `white_dye` | 1 | **`white_dye_from_lily_of_the_valley`** | 2.428 | `white_dye` (bone meal) | 2.492 | pass |
| | 2 | `white_dye` (bone meal) | 4.133 | `white_dye_from_lily_of_the_valley` | 4.418 | **FAIL, by 6.9 %** |
| | 4 | `white_dye` (bone meal) | 6.925 | `white_dye_from_lily_of_the_valley` | 8.063 | fail, by 16 % |
| tfc-enhanced `blue_dye` | 1 | `tfc:crafting/blue_dye` (graphite) | 1.500 | `blue_dye_from_cornflower` | 1.500 | pass (tie; lapis 2.529) |
| | 2 | `tfc:crafting/blue_dye` (graphite) | 2.202 | `blue_dye_from_cornflower` | 2.573 | **pass** (lapis 4.273) |
| | 4 | `tfc:crafting/blue_dye` (graphite) | 3.443 | `blue_dye_from_cornflower` | 4.472 | pass (lapis 7.490) |
| technical-electrical `red_dye` | 1 | **`red_dye_from_rose_bush`** | 1.000 | `ie:smelting/lead_red` | 1.529 | pass |
| | 2 | **`red_dye_from_rose_bush`** | 1.605 | `ie:smelting/lead_red` | 2.273 | **pass** |
| | 4 | **`red_dye_from_rose_bush`** | 2.627 | `ie:smelting/lead_red` | 4.040 | pass |
| lost-world `mossy_cobblestone` | 1 | **`from_vine`** | 13.289 | `from_moss_block` | 13.289 | pass (exact tie) |
| | 2 | **`from_vine`** | 22.777 | `from_moss_block` | 22.821 | **pass** |
| | 4 | **`from_vine`** | 39.878 | `from_moss_block` | 39.991 | pass |

**Four of the five pass at K = 2.**  `blue_dye` went from losing by 12× to
winning by 23 %; `red_dye` and `mossy_cobblestone` win at every K.

**`white_dye` fails at K ≥ 2, and per instruction it was not tuned further.**
The numbers say precisely why: it *passes* at K = 1 and loses as K grows, so
the failure is not in the chain arithmetic — it is the unsupervised leaf score
itself.  `lily_of_the_valley` has one consumer and is ranked among the dearest
leaves (seed 1.93 at K = 2), while `bone` is broadly consumed and ranked cheap,
so bone meal — three per bone — starts at 1.23 against the flower's 1.93.
Breadth of consumption says a single-use flower is rare; the audit says it is
free to pick.  **This is an explicit requirement for the supervised leaf fit in
Phase 0c**: it must learn that a leaf consumed by exactly one dye recipe is
cheap, which no breadth feature can express.  The gap is now 6.9 % instead of
v1's 4,700 %.

### The four v1 defects, verified fixed

fom `value_fitted`, v1 → v2:

| item | v1 | v2 | |
|---|---:|---:|---|
| `cobblestone` (leaf) | 0.091 | 1.000 | the cheapest leaf is the unit |
| `raw_iron` (closed ring) | 0.091 | 1.089 | its own seed, not cobblestone's |
| `diamond_ore` (leaf) | 0.502 | 1.326 | |
| `poppy` (leaf) | 1.509 | 1.440 | |
| `dandelion` (leaf) | 2.395 | 1.597 | |
| `rose_bush` (leaf) | 8.800 | 1.917 | leaf band is now [1, 2] |
| `iron_ingot` | **0.091** | **2.504** | was exactly cobblestone; now 1.3x the dearest leaf |
| `diamond` | 0.502 | 2.786 | was exactly `diamond_ore`; now ore + two steps |
| `lapis_lazuli` | 0.663 | 2.809 | |
| `netherite_ingot` | 3.713 | 22.352 | |

The ordering the audit wanted now holds in fom and farmers-colonies:
**every gathered leaf (<= 2.0) is cheaper than diamond (2.79) and lapis
(2.81)**, flowers included, and `iron_ingot` (2.50) is no
longer free.

Ring bootstraps collapsed because only *closed* rings are now bootstrapped:
atm10 15.1 % → **1.4 %** of items, enigmatica10 35.3 % → **4.1 %**,
civilization-isolaria 26.0 % → 7.8 %, fom 1.7 %.  v1 was calling ordinary
two-way conversions "rings with no base case" and pricing them at the floor.

### Tiers

**These numbers moved from the v1 report.**  The ring fix lives in the solver
that tiers and values share, so stage 4's output changed with it — v1
bootstrapped *any* stuck ring early and short-circuited the chains behind it,
while v2 only bootstraps closed rings.  Tiers are therefore deeper and more
machines turn out to be buildable.  Nothing else in stage 4 was touched.

| | v1 | v2 |
|---|---:|---:|
| atm10 max item tier | 30 | **32** |
| atm10-lite max item tier | 25 | **38** |
| atm10 machines with no in-pack producer | 91 | **33** |
| atm10-lite ditto | 20 | **4** |
| minecolonies ditto | 91 | **11** |

fom item-tier histogram (2,625 items): 0:330 · 2:248 · 3:812 · 4:577 · 5:191 ·
6:174 · 7:166 · 8:104 · 9:20 · 10:3.  (Nothing sits at tier 1: the crafting
table is itself tier 1, so every crafted item is at least 2.)  Recipe tiers
2…11, `reverse_tier` 95 (2.8 %), learned margin 1.0.

atm10-lite (9,823 items) spreads 0…38 with the mass at 11–16
(1,260 · 1,751 · 1,335 · 721 · 571 · 690); recipe tiers 1…39; `reverse_tier`
4,147 (16.8 %), learned margin 4.0.

**15 highest-tier machines, atm10-lite**: `occultism:celestial_chalice` 15,
`occultism:eldritch_chalice` 15, `occultism:ritual_dummy/summon_marid_smelter`
14, `occultism:dark_iesnium_sacrificial_bowl` 14,
`occultism:iesnium_sacrificial_bowl` 14,
`occultism:ritual_dummy/summon_marid_crusher` 14,
`occultism:dimensional_mineshaft` 14,
`occultism:ritual_dummy/summon_marid_crystallizer` 14,
`extendedcrafting:ultimate_auto_table` 13,
`occultism:ritual_dummy/summon_afrit_crusher` 12,
`extendedcrafting:ultimate_table` 12, `extendedcrafting:elite_auto_table` 12,
`ironfurnaces:unobtainium_furnace` 11, `mekanism:ultimate_injecting_factory`
11, `extendedcrafting:elite_table` 11.

Two complete upgrade ladders fall out of structure alone, in the right order
and with no vocabulary:

```
ironfurnaces      copper/iron 3 -> gold/silver 4 -> diamond 5 -> emerald/crystal 6
                  -> obsidian 7 -> netherite 8 -> allthemodium 9
                  -> million/vibranium 10 -> unobtainium 11
extendedcrafting  handheld 3 -> basic_table 9 -> basic_auto 10 -> elite 11
                  -> elite_auto 12 -> ultimate 12 -> ultimate_auto 13
```

**fom has only 7 machines**, ordered correctly: `blast_furnace` 3 ·
`stonecutter` 2 · `smithing_table` 2 · `campfire` 2 · `smoker` 2 ·
`crafting_table` 1 · `furnace` 1.  None is unbuildable.  Across the corpus **92
of 1,683** machine items have no in-pack producer (was 340 under v1's ring rule).

### Leaf acquirability

Nine features feed the composite (`n_forms_of_material` is written to the table
but excluded — see below).  PC1 explains **45 %** of the rank-normalised
variance on fom (v1: 39 %) and 46 % on farmers-colonies (v1: 39 %), and 70 %
on atm10.  fom loadings: `producer_packs` +0.54,
`n_consumers` +0.49, `leaf_pack_fraction` −0.49, `n_consumer_ns` +0.35,
`n_consumer_systems` +0.23, `n_tags` +0.22, `min_consumer_tier` −0.08,
`renewable` 0.00, `n_variant_keys` 0.00.

**Seeds are now bounded.**  With `--seed-spread 2` every leaf in every pack
lands in `[1.00, 2.00]`; v1 spanned 0.091–10.86 on fom, a 119× range that let
the leaf score outweigh an entire crafting chain.

fom, 10 cheapest → 10 dearest (seed at K = 2):

```
cheapest  cobblestone 1.00 · cobbled_deepslate 1.00 · sand 1.00 · amethyst_shard 1.01
          obsidian 1.01 · blackstone 1.01 · string 1.01 · soul_sand 1.02
          chainmail_boots 1.02 · chainmail_chestplate 1.02
dearest   large_shaftless_cogwheel 2.00 · shaftless_cogwheel 2.00 · aviation_alloy_ingot 1.97
          furnace_minecart_contraption 1.97 · minecart_contraption 1.97
          charged_kinetic_battery 1.97 · cactus_flower 1.97 · pale_moss_block 1.97
          sulfur_spike 1.97 · wildflowers 1.97
```

farmers-colonies:

```
cheapest  cobblestone 1.00 · cobbled_deepslate 1.00 · blackstone 1.01 · sand 1.01
          gunpowder 1.01 · potato 1.02 · stripped_{acacia,birch,cherry,dark_oak}_log 1.04
dearest   filled_map 2.00 · shulker_shell 1.99 · azure_bluet / blue_orchid / cornflower /
          dandelion / lilac / lily_of_the_valley / orange_tulip / peony 1.93
```

**Dropping `n_forms_of_material` was right.**  It measures how *core* a
material is, not how cheap: the induced `cls:diamond` class has 14 forms, which
seeded `diamond_ore` cheap, and `nether_star` inherited 6 "forms" only because
its material class lumps it with `nether_brick`.  Removing it raised PC1's
explained variance in every pack checked.

**The cheap end is still exactly right** (cobblestone, sand, cobbled deepslate,
logs, string, gunpowder) and **the dear end is still flowers** — but the
consequence is now bounded.  A flower is a leaf with one consumer, so breadth
ranks it beside `shulker_shell` and `filled_map`; the difference is that this
now costs at most a factor of 2 instead of a factor of 119.  The residual
damage is visible and quantified in the `white_dye` acceptance case above: a
6.9 % loss, where v1 lost by 4,700 %.  `leaf_features.parquet` keeps all ten
raw columns so Phase 0c can fit the supervised score that closes it.

### World resource vs data gap

7,811 of 29,140 leaves are data gaps.  fom: 66 gaps / 220 world resources;
enigmatica10: 1,425 / 808 (that pack ships 28,594 ids but exports very few of
the mod categories that make them).

**fom data gaps** (leaf here, produced elsewhere): `cobblestone` (17 of the 21
other packs can make it — cobble generators), `beetroot`, `carrot`, `flint`,
`milk_bucket`, `obsidian`, `potato`, `string` (15 each), `blackstone`,
`chainmail_boots` (14).
**enigmatica10 data gaps**: `cobbled_deepslate` (13), `create:copper_nugget`
(11), `chorus_fruit` (11), `create:polished_rose_quartz` (10), `bone` (10),
`prismarine_crystals` (10), `red_sand` (10), `create:copper_sheet` (8),
`create:crushed_raw_copper` (8), `create:crushed_raw_gold` (8) — the Create
crushing chain is missing from this export while eight other packs have it.

**World resources with the most consumers** read as genuinely unobtainable
inputs: fom's `interiors:seatwood_planks` (114 consumers, a leaf in every pack
that ships it), `honeycomb` (63), `create:andesite_casing` (60);
enigmatica10's `dragon_breath` (251) and the four `*:steel_ingot` variants
(148 each).

### Reliability

Every recipe in the corpus scores **≤ 0.5**, because both economy-bearing
schema fields are degenerate (see Anomalies 1–2 of Phase 0a): `chance` is a
constant and no `value` row exists anywhere.  The ceiling is measured, so a
fixed exporter lifts it automatically.  Per-pack mean reliability runs 0.40
(atm10) to 0.48 (fom).

**atm10-lite, 90 systems — the 10 least reliable** (`mean_outputs` in brackets):

| system | recipes | atypicality | reliability | why |
|---|---:|---:|---:|---|
| `jei:category/jeresources/villager` | 16 | 0.561 | 0.252 | 19.7 outputs/recipe, no id, no machine — a loot table |
| `jei:category/jeresources/plant` | 8 | 0.450 | 0.271 | 37 % noop, no id, no machine |
| `jei:category/ae2/certus_growth` | 4 | 0.346 | 0.290 | growth display |
| `jei:category/industrialforegoing/machine_produce` | 13 | 0.318 | 0.294 | 2.7 outputs, no machine |
| `jei:category/justenoughbreeding/breeding` | 3 | 0.309 | 0.296 | breeding display |
| `jei:category/ae2/attunement` | 9 | 0.333 | 0.317 | 11 % cross-system outputs |
| `jei:category/ae2/transform` | 14 | 0.164 | 0.349 | no machine |
| `jei:category/apotheosis/gem_cutting` | 5 | 0.462 | 0.361 | 17.2 outputs/recipe |
| `jei:category/rechiseled/chiseling` | 44 | 0.429 | 0.369 | **29.3 outputs/recipe** — a variant catalogue |
| `jei:category/malum/spirit_infusion` | 122 | 0.382 | 0.380 | 5.2 inputs, no id |

**the 10 most reliable**: `jei:category/mekanism/reaction` 0.495,
`farmersdelight/cutting` 0.493, `ae2/inscriber` 0.492, `mekanism/combining`
0.490, `minecraft:brewing` 0.488, `minecraft:blasting` 0.487,
`occultism/crushing` 0.485, `farmingforblockheads/market` 0.484,
`mekanism/metallurgic_infusing` 0.484, `ae2/charger` 0.484 — all real
production systems with 1–2 inputs and 1 output.  **No system name was ever
consulted**; the separation comes from output counts, missing ids and missing
machines.

**fom, 7 systems**: `minecraft:crafting` 0.498 (atypicality 0.000 — it *is* the
norm), `smithing` 0.443, `smelting` 0.421, `stonecutting` 0.406, `blasting`
0.404, `campfire_cooking` 0.402, `smoking` 0.402.  The spread is only 0.10 and
the ordering is essentially "how much does this system look like crafting":
in a pack with no display systems the atypicality term has nothing to find.
That is a real limitation of the measure on small vanilla-only packs.

### Anomalies found

1. **`jei:category/minecraft/anvil` exports XP levels as an item amount.**
   A repair/enchant row reads "1 tool + 1 enchanted book → **8** tools"; the
   output amount is the level cost, not a count.  These rows are
   self-dependent (the tool is on both sides) and expand, so they fill the
   `recycling_shaped` bucket: 2,929 of atm10-lite's 2,954 and 333 of
   farmers-colonies' 334 recycling-shaped recipes are anvil rows.  They also
   produce regime-C value ratios of 4.3–4.8 in farmers-colonies.
   **Consequence: `recycling_shaped` is the weakest of the five roles on this
   corpus.**  Excluding self-dependent rows leaves fom with 2 and
   farmers-colonies with 1 recycling-shaped recipe, and both are material-class
   coincidences (`soul_torch`, `croptopia:milk_bottle`) rather than real
   dismantling.  Genuine recycling in this corpus is material-preserving
   (iron sword → iron nugget) and lands in `conversion` with `value_ratio ≪ 1`;
   that ratio and `reverse_tier` are the signals that actually find it.
2. **Quest, salvaging and display systems short-circuit the value fixpoint,
   and reliability alone cannot stop them.**  The reliability weight is now
   wired into the solve, and it is far too flat to help: with the field ceiling
   constant across the corpus the informative spread is only about 1×–2×, while
   the damage these systems do is orders of magnitude.  The evidence, all from
   v2 artifacts:

   * atm10-lite's whole ladder is inverted — `nether_star` 1.08 <
     `diamond` 3.21 < `iron_ingot` 4.43 < `netherite_ingot` 6.58 <
     **`cobblestone` 11.78 < `oak_log` 15.05**.  Cobblestone and logs are dear
     because the pack exports no cheap producer for them and their cheapest
     routes are `jei:category/apotheosis/salvaging` and
     `jei:category/rechiseled/chiseling` display rows.
   * lost-world has no plain cobblestone source at all: every producer is
     either `silentgear:cobblestone_from_pebbles` (9 pebbles) or a
     `jei:category/silentgear/salvaging` row that turns a stone tool back into
     cobblestone.  Cobblestone ends at 17.60 and drags
     `mossy_cobblestone` to 22.78.

   **This is Phase 0c's job**, as instructed: `solve_hypergraph` takes
   `recipe_weight` and `exclude` so a learned role posterior can gate these
   producers without touching this layer.
3. **Fluid amounts are millibuckets, so fluid values are ~1/1000 of an item's.**
   atm10-lite's cheapest items are `minecraft:water`, `mekanism:brine`,
   `mekanism:lithium`, `industrialforegoing:biofuel` at 7 × 10⁻⁸ — one bucket
   makes 1,000 mB and the chains multiply.  Nothing is wrong, but any
   cross-family value comparison must be per-unit-of-what.
4. **Closed rings are real but far rarer than v1 claimed.**  0.7 %–7.8 % of a
   pack's items sit in a ring with no entry from outside (civilization-isolaria
   7.8 %, creative-drawers 6.7 %, enigmatica10 4.1 %, atm10 1.4 %, fom 1.7 %;
   5,650 items across the corpus) — overwhelmingly raw-material ↔ storage-block
   pairs where the pack exports both directions but no way in.  They are priced
   at their own cheapest member's acquirability seed and marked
   `value_source = "cycle"`.  v1 reported 6.1 %–35.3 % because it bootstrapped
   *any* stuck ring, including ordinary two-way conversions that simply had not
   been reached yet.

   The cost of this rule is visible in atm10-lite: `{nether_star,
   nether_star_block}` is a closed ring (the pack exports
   `nether_star_block → 9 nether_star` and the reverse, but no way to craft a
   first star), so a nether star is priced like a gatherable — 1.08, below
   `diamond` 3.21 and `cobblestone` 11.78.  **A valuable item that forms a
   closed ring with its own storage block is priced as if it were free to
   gather.**  The flag is there to let Phase 0c discount it.
5. **`chance` and `value` are still degenerate** (Phase 0a anomalies 1–2), so
   the catalyst hook (`net_catalysts`) and the value-flow hook
   (`parse_value_scalars` → `value_cost`) never fire on real data.  Both are
   implemented and are exercised only by the synthetic tests; a fixed exporter
   turns them on without any other change, and `field_ceiling` lifts by itself.
6. **Machines are frequently unbuildable**: 340 machine items across the corpus
   have no in-pack producer, concentrated in atm10 (91), minecolonies (91) and
   cobblemon-x-create (51) — those packs export the machine's recipes but not
   the machine itself, or the machine is a multiblock with no item form.
7. **`minecraft:crafting` dominates the reliability baseline.**  Because the
   robust centre is recipe-count weighted, a pack whose recipes are 76 %
   crafting makes crafting the definition of normal.  That is deliberate
   (it is what makes the JEI display systems stand out in atm10-lite) but it
   means the measure says little on packs with only production systems.

### Invariants checked (`tests/test_phase0b.py`, 65 checks)

Synthetic tiny graphs: OR node is a minimum (and follows when the cheapest
member changes); a ring entered from a leaf needs no bootstrap; a ring with no
base case terminates, is flagged and stays positive; `x → 2x` cannot drive a
value to zero; the value-flow hook adds exactly its magnitude; the catalyst
hook nets `min(in, out)` off both sides; leaves are tier 0, tiers rise by one
along a chain, every recipe tier exceeds its largest input tier, a machine's
tier comes from its own chain, an unbuildable machine is tier 0 and flagged;
a mutually convertible ring is one strong component and a one-way successor is
not; both directions of a same-material pair are round trips while a one-way
recipe is not; an output that is also an input is self-dependent, not a round
trip.

**The ring floor** (added in v2): a nugget/ingot/block ring fed by an ore leaf
is never bootstrapped and prices at `ore + one step`, not at the pack's
cheapest leaf; cutting the ore entry makes the ring closed, and it then prices
at its own cheapest member's seed (7.0 in the fixture) while the unrelated
cheap leaf stays at 1.0.  **Step cost and hooks**: a chain is dearer than its
leaf by exactly the step cost; `recipe_weight` scales a producer's cost;
`exclude` orphans an output, which then falls back to its own seed and is
flagged.  **The seed band**: the cheapest leaf is exactly 1.0, no leaf exceeds
K, `n_forms_of_material` is in the table but not the composite, and the
recorded loadings cover exactly the composite features.

Real pack (fom): one shape row per kept recipe in `recipe_idx` order; only the
five documented roles; base ids unique and sorted; every concrete id and every
OR member has a value row; `value_uniform` and `value_fitted` finite and
strictly positive; tiers finite and ≥ 0; `is_leaf ⇔ n_producers == 0`; leaves
tier 0; reliability in (0, 1]; no singleton pool; every pool representative is
a member; every `pool_id` on an item exists in `pools.parquet`; one leaf-feature
row per leaf; acquirability in [0, 1].  **Determinism**: two full runs produce
byte-identical parquet for all 7 tables.  Design rules: the band was learned
(not the fallback) and is symmetric in log space; the reverse margin, flag
weights and field checks are recorded in the meta; and the no-vocabulary parse
of `economy.py` is clean.

### Open questions

1. **The supervised leaf fit must learn that single-use gatherables are cheap.**
   This is now the one unresolved acceptance case (`white_dye`, losing by
   6.9 %) and the one remaining source of flower mis-pricing.  No breadth
   feature can express it: `lily_of_the_valley` has exactly one consumer, the
   same signature as `shulker_shell`.  Candidate vocabulary-free signals for
   Phase 0c: the value of the *outputs* of a leaf's consuming recipes (a flower
   makes a dye that everything uses), whether the leaf's consumers are
   themselves cheap, and how early in the tier ladder it is consumed.
2. **Producers must be gated by a learned role posterior, not by reliability.**
   Demonstrated above: reliability's informative spread is 1×–2× while
   quest / salvaging / chiseling rows distort values by orders of magnitude.
   The `recipe_weight` and `exclude` hooks are in place and unit-tested.
3. **A valuable item that forms a closed ring with its storage block is priced
   as a gatherable** (atm10-lite's `nether_star` at 1.08).  The rule is the one
   asked for and the flag (`value_source = "cycle"`) is there, but a better
   rule might price a closed ring from the *consumers* of its members rather
   than from its own acquirability.
4. **Absolute values are only comparable within a pack.**  The step cost
   compounds through chain depth *and* multiplicity, so atm10-lite reaches
   2.7 × 10¹⁷ (`allthecompressed:atm_star_block_9x` — 9⁹ ≈ 387 M stars, which
   is arithmetically correct) while fom tops out at 2 × 10⁴.  Any cross-pack
   feature built on values needs a per-pack normalisation.
5. **`value_ratio` has lost its old meaning** now that every recipe carries a
   step cost: the neutral point is `1 + step / Σ in`, not 1.  If Phase 0c wants
   the old "is this recipe material-neutral" signal it should be recomputed
   with `--step-cost 0`, which the CLI supports.
6. **The fluid unit problem** is unchanged: amounts are millibuckets, so fluid
   values are ~1/1000 of an item's and must be normalised per content family
   before they are compared or used as features.
7. **Multi-output cost allocation** is unchanged: every output claims the full
   recipe cost divided by its own amount, which is the right optimistic bound
   for a planner but makes multi-output recipes look like value creation.

---

## Phase 0c-1 — the label layer

`./run_phase0c1.sh` over all 22 packs: **labels.py 9.8 s, 2.4 GB peak RSS**;
**features.py 19.7 s, 2.4 GB**; `tests/test_phase0c1.py` 3.7 s, 44 checks.
Budget was 5 min per script.

| stage | seconds | what it does |
|---|---|---|
| rules | 1.1 | 692,608 rule-replay rows (249 MB of jsonl) |
| gold | 0.7 | 112,854 gold rows, plus the id cross-check |
| audit | 2.6 | 95 review files → 215 findings, 4,326 chain verdicts |
| chains | 1.4 | 85 chain batches → 3,988 chains, 685,166 EXECUTE lines |
| recipes | 0.9 | `recipe_labels.parquet` |
| templates | 1.3 | `template_labels.parquet` |
| pairs | 1.3 | `pairs.parquet` |

### Label counts per source

| source | tier | rows | scope |
|---|---|---|---|
| `adjudication` | 0 | 27,735 | system |
| `human-nb` | 1 | 42,616 | recipe — **no category**, so verdicts only |
| `audit` | 2 | 5,460 | system |
| `subagent` (+`-r2`) | 3 | 34,745 | 24,671 system / 10,074 recipe |
| `svm-rN` (13 rounds) | 4 | 2,298 | recipe |
| rule replay `wl` | — | 692,608 | useful 341,186 / middle 269,696 / junk 81,726 |

The gold file is already one row per `(pack, i)` — verified, 0 duplicate keys.
**Where a gold row carries a non-empty id it agrees with the cached id at that
index 100.0 % of the time, on all 10 packs** — that is the proof the index
spaces are the same, and it is asserted in the test suite.

`recipe_labels.parquet` has **692,616** rows: the 692,608 replay rows plus 8
recipes that carry a chain/gold/audit label but were dropped by the Java
loader before the replay ran.  1,344 of the corpus's 693,952 recipes have no
replay row at all (the same loader drop).  Every labelled recipe has a
template.

### Label counts per pack

| pack | rows | gold | system-scope | ok-chain | bad-chain | audit family |
|---|---|---|---|---|---|---|
| atm10 | 283,059 | 78,025 | 37,248 | 2,472 | 1,021 | 9 |
| minecolonies-dimensional-adventure | 71,899 | 0 | 0 | 601 | 330 | 11 |
| create-train | 56,228 | 11,834 | 2,356 | 276 | 0 | 0 |
| enigmatica10 | 49,851 | 16,442 | 16,003 | 651 | 337 | 5 |
| civilization-isolaria | 30,959 | 207 | 0 | 131 | 503 | 9 |
| mythcrafts | 24,752 | 206 | 0 | 190 | 249 | 27 |
| atm10-lite | 24,624 | 1,219 | 862 | 1,707 | 2,593 | 5 |
| creative-drawers-producer-2 | 19,180 | 0 | 0 | 180 | 453 | 8 |
| create-lets-create | 18,636 | 0 | 0 | 241 | 940 | 13 |
| brick-s-building-1-4-1 | 18,501 | 0 | 0 | 200 | 613 | 2 |
| cobblemon-x-create | 18,395 | 3,674 | 705 | 672 | 562 | 6 |
| all-of-create-2-1 | 13,693 | 0 | 0 | 582 | 190 | 48 |
| technical-electrical | 12,473 | 0 | 0 | 545 | 608 | 58 |
| create-oneblock | 11,280 | 296 | 0 | 496 | 388 | 4 |
| create-ultimate | 10,647 | 0 | 0 | 531 | 663 | 40 |
| createcustomised1-1-2 | 10,503 | 0 | 0 | 678 | 673 | 8 |
| lost-world | 5,028 | 0 | 0 | 276 | 264 | 31 |
| tfc-enhanced | 4,254 | 489 | 0 | 268 | 244 | 13 |
| fom | 3,442 | 462 | 0 | 327 | 248 | 21 |
| farmers-colonies | 2,658 | 0 | 0 | 270 | 89 | 17 |
| blockfront… / luckyblockchallenge (one group) | 1,277 each | 0 | 0 | 384 / 289 | 153 / 90 | 28 / 33 |

18,349 recipes carry chain evidence and 396 carry an audit family.  **Twelve
packs have no gold label at all** — for those the only supervision is the rule
replay and the chain audits, which is exactly the transfer case the
`pack_group`-aware split has to measure.

### Template-label coverage

240,377 templates over 22 packs.  **24,686 (10.3 %) get a majority verdict**
and **17,704 (7.4 %) get a role target**; 58 templates (0.24 % of the labelled
ones) have members whose verdicts conflict.

| role target | templates | | best tier of the role-bearing templates | templates |
|---|---|---|---|---|
| `production` | 6,820 | | `adjudication` | 4,310 |
| `display` | 5,645 | | `human-nb` | 47 |
| `unknown` | 4,200 | | `audit` | 3,903 |
| `conversion` | 813 | | `subagent` | 8,541 |
| `recycling` | 177 | | `svm-rN` | 903 |
| `generation` | 49 | | | |

`human-nb` contributes 47 role targets despite being the largest source,
because it records a verdict and no category; its 42,616 rows feed
`junk_target` and the silver pairs instead.

Per-pack role coverage: enigmatica10 22.0 %, tfc-enhanced 20.2 %, fom 18.1 %,
atm10 9.9 %, create-train 9.5 %, cobblemon-x-create 8.4 %, atm10-lite 8.0 %,
create-oneblock 3.7 %, mythcrafts 1.2 %, civilization-isolaria 0.8 %, the
other 12 packs 0 %.

Purity is very high where it exists: the mean role purity of a role-bearing
template is **0.999** and 99.8 % of them are perfectly pure, i.e. the
judgements inside a template almost never disagree.  That is expected — a
template is by construction a group of near-identical recipes — and it means
`purity` will do little work; `label_weight` is dominated by the tier term
(mean 0.451 over verdict-bearing templates).

**Cross-pack overlap.**  229,616 distinct `template_id`s; 6,340 (2.8 %) occur
in ≥ 2 pack groups.  Of the 17,569 distinct role-bearing templates, only
**130** occur in ≥ 2 groups (max 4).  Templates transfer across packs far less
than hoped, which makes the L1/L2 features, not the template identity, the
thing that has to generalise.

### The chain audits

95 review files across 6 rounds map to all 22 packs with **0 unmapped**
(mapping printed and stored in `labels-meta.json`; the two that need more than
exact normalised equality are `bricks-building` → `brick-s-building-1-4-1` and
`blockfront` → `blockfront-official-mod-pack-0-9-0-24b`).

4,326 chain verdicts: **1,863 ok, 1,101 bad, 1,356 questionable, 6 unreadable**;
4,300 of them resolve to at least one chain step.  85 batches parsed, 3,988
chains, 685,166 `EXECUTE` lines:

| resolution | lines | share |
|---|---|---|
| unique id | 485,188 | 70.8 % |
| ambiguous id, settled by the machine | 135,212 | 19.7 % |
| still ambiguous | 34,102 | 5.0 % |
| id not in the export | 30,664 | 4.5 % |

Without the machine tie-break a quarter of all chain steps would be unusable.
The 4.5 % missing are ids the chain exporter wrote that the Phase 0a cache has
no row for.

Of the 215 findings, 199 are rounds 1-5 junk-pattern families (396 recipes
resolved to an `audit_family`) and **16 are round-006 routing findings**.

### Gold pairs — resolution rate

**9 of 16 routing findings (56 %) resolve, yielding 19 pairwise-gold rows.**
Each row is a distinct valid reading of its finding and carries
`weight = 1/n_readings`.

| finding | readings | output |
|---|---|---|
| `F-atm10-lite-1` (impure-recipe) | 5 | three impure saw recipes vs their pure alternatives |
| `F-technical-electrical-1` | 6 | one smelting producer vs six single-flower alternatives |
| `F-farmers-colonies-1/2`, `F-lostworld-1/3`, `F-mythcrafts-1`, `F-tfc-enhanced-1` | 1 each | |
| `F-lostworld-2` (silly-detour) | 2 | one reading is the intended detour, one is a second same-output pair the prose also admits |

The 7 that do not resolve, with the structural reason recorded in the meta:

| finding | reason |
|---|---|
| `F-create-oneblock-1`, `F-create-ultimate-1`, `F-creative-drawers-producer-2-1` | every named recipe is in the cited chain — the audit's complaint is that the planner used *both* routes, so "not executed" cannot separate them |
| `F-create-oneblock-2` | no named recipe is in the cited chain (the chain names the step, the prose names the alternatives) |
| `F-create-ultimate-2`, `F-creative-drawers-producer-2-2` | no shared output between any named pair — the finding compares two different items |
| `F-technical-electrical-2` | fewer than two recipes named; the preferred producer is named by *item*, not by recipe |

Extracting more would mean reading the Chinese prose for intent, which is
where a wrong gold pair costs more than a missing one.  An earlier heuristic
that picked one pair per finding from the prose sections produced 4 wrong
pairs out of 12; the chain-evidence + same-output rule produces none that I
can find by inspection.

### Training pairs

**156,047 pairs** over 111,698 outputs that have ≥ 2 producers (of 224,805
distinct outputs).  Label balance 77,818 / 78,229 (+1 / −1).

| tier | pairs | share |
|---|---|---|
| `bronze` (rule replay) | 143,651 | 92.1 % |
| `silver` (two gold verdicts) | 6,291 | 4.0 % |
| `chain` (ok chain vs junk sibling) | 6,086 | 3.9 % |
| `gold` (round-006) | 19 | 0.01 % |

Per pack, the distribution is dominated by one pack: **atm10 contributes
147,237 of the 156,047 pairs (94 %)**, of which 138,930 are bronze.  Next are
create-train 2,642, atm10-lite 2,373, cobblemon-x-create 755, enigmatica10
609, createcustomised1-1-2 480, brick-s-building 474, create-oneblock 390; the
remaining 14 packs contribute fewer than 200 each and four fewer than 15.
**Any trainer that does not weight or subsample by pack will be trained on
atm10.**

### Anomalies found

1. **`human-nb`, the largest and second-best source, has no category.**
   42,616 rows, 43,213 of the corpus's category-less rows, 99.6 % of them
   `junk`.  It can supervise the junk head and the silver pairs but not the
   role head.
2. **Recipe ids are not unique.**  138,578 of atm10's 283,422 rows share an id
   with another row; brick-s-building 12,502 of 18,525.  Nine of the 22 packs
   have zero collisions and the rest have many.  This is the concrete reason
   for the index-only join rule, and the reason the chain steps need the
   machine tie-break.
3. **The bronze tier is 92 % of the pairs and 94 % of them come from one
   pack.**  The rule replay is dense and free; everything else is scarce.
4. **Chain verdict rounds disagree in shape.**  Rounds 1-5 lean `bad`
   (round-001: 528 bad / 206 ok), round-006 leans `ok` (459 ok / 21 bad) —
   the rounds were looking for different things (junk families vs routing
   quality), so the `round` column is part of the key, not decoration.
5. **Role coverage is concentrated.**  10 packs have gold labels, and 4 of
   them account for 94 % of the role targets.

---

## Phase 0c-1 — the feature layer

240,377 template rows × 133 columns (129 features) = 20.5 MB; 693,952 recipe
rows × 42 columns = 21.8 MB.  Both far under the 500 MB budget.

| stage | seconds | features |
|---|---|---|
| l1 (signature parse) | 7.0 | 34 |
| economy (L2 + L3) | 0.9 | 21 + 8 |
| resources (L4) | 3.1 | 9 |
| intent (L5) | 6.2 | 23 |
| lexical (L6) | 1.4 | 34 |
| recipe matrix | 0.5 | 35 numeric + 7 keys |
| write | 0.4 | — |

L1 is the slowest stage because it parses 240,377 canonical-JSON signatures;
L5 is next because the sibling z-scores need the full output-side content
table of every pack.

### Missing values

11 of 129 columns have any NaN at all:

| column | NaN rate | why |
|---|---|---|
| `l4_leaf_acq_mean_mean`, `l4_leaf_acq_min_mean`, `l4_data_gap_input_share_mean` | 84.2 % | most templates have **no leaf input** — they sit above the leaves in the tree |
| `l4_pool_size_mean_mean` | 69.9 % | most inputs are not in a conversion pool |
| `l1_material_overlap` | 4.1 % | neither side has a material slot |
| `l1_machine_abstract_share` | 0.9 % | hand recipes name no machine |
| `l2_count_ratio_*`, `l2_log_value_*` | 0.17 % | nothing on the input side resolved to a value |

No column contains an infinity (checked on both matrices).  Two one-hot
columns are absent in some packs and written as 0:
`l2_role_generation_share` (7 packs have no such recipe),
`l2_role_noop_share` (5).

### Correlation sanity check

On **fom**, against the rule-replay junk rate per template (n = 1,769; fom has
only 61 junk recipes, so the base rate is 1.8 % and the correlations are
necessarily small):

| feature | r |
|---|---|
| `l1_in_kind_typedAny_share` | +0.333 |
| `l5_z_tier_absmax` | +0.133 |
| `l1_in_abstract_share` | +0.117 |
| `l2_is_round_trip_share` | +0.102 |
| `l2_unique_producer_share` | −0.089 |

`typedAny` inputs dominating a template is the exchange/variant-swap shape, and
`is_round_trip` is the conversion shape — both are what the rule replay calls
`middle`/`junk`, so the signs are right.

Against the **gold** `junk_target` over all labelled templates (n = 24,686),
where the signal is much stronger:

| feature | r |
|---|---|
| `l2_self_dependent_share` | +0.624 |
| `l2_out_subset_in_share` | +0.490 |
| `l4_pool_input_share_mean` | −0.476 |
| `l2_role_recycling_shaped_share` | +0.468 |
| `l1_in_abstract_share` | −0.436 |
| `l2_mat_log_ratio_max_mean` | +0.426 |
| `l3_machine_unbuildable_share` | +0.422 |
| `l2_role_production_share` | −0.406 |

A recipe that consumes what it makes, that gives back a subset of what it ate,
or that runs on a machine nothing in the pack can build, is junk; a recipe
whose inputs sit in a conversion pool is real.  None of those is a rule this
layer asserts — they are columns the model will weigh.

### The L6 namespace leak, found and fixed

The first version hashed the residual `rid` tokens and the residual **system
path** tokens after stripping the system id's own namespace.  That is not
enough: a system id that renders another registry's page carries *that*
registry's namespace as a path segment.  On atm10, bucket 07 was 10,308 ×
`minecraft` + 2,386 × `ftbquests` + …, and it correlated **−0.798** with the
gold junk target — the model was being handed "which mod is this".

The fix is structural, not a list: a residual token is dropped if it is a
namespace **the pack itself uses** (every namespace on any item id, recipe id
or system id; 273 of them on atm10).  After the fix the top-8 correlates of
`junk_target` contain no hash bucket at all, and the buckets carry what they
should — function words from system paths and id tails.
`tests/test_phase0c1.py::test_hash_excludes_namespaces` re-derives the token
stream and asserts no namespace reaches a bucket.

### Invariants checked (`tests/test_phase0c1.py`, 44 checks)

Label joins (no id column anywhere, unique in-range keys, 100 % id agreement
where a source does carry an id), every labelled recipe has a template and
that template has a row, role targets are exactly the documented set and every
category the sources use is in the mapping table, pairs never pair a recipe
with itself / never cross packs / always carry a group / are ±1 / use the four
documented tiers / have finite positive weights, **both sides of all 156,047
pairs actually produce the named output**, every `pairwise_gold` row reaches
`pairs.parquet` as a gold pair, every chain step is an in-range index, the
feature matrix has no infinity and every NaN column is documented, the feature
and label key sets are identical, the L6 hash is the documented sha1
construction and receives no namespace, the sibling z-score is 0 (not NaN) for
a lone or unanimous group, no vocabulary in `features.py` / `labels.py` outside
the one sanctioned table, and both layers are byte-identical across two runs.

### Open questions

1. **The label mass is in the wrong place.**  94 % of the pairs and 69 % of
   the gold verdicts come from atm10.  A plain split trains an atm10 model;
   the `pack_group` column is there so the trainer can do better, but someone
   has to decide the weighting.
2. **Templates barely transfer.**  Only 130 role-bearing templates occur in
   more than one pack group.  Cross-pack generalisation has to come from the
   features, so the honest evaluation is leave-one-pack-group-out.
3. **19 gold pairs is not a training set.**  It is an *evaluation* set — the
   only data that directly expresses "the planner chose the worse producer".
   Treating it as training data would overfit 9 findings.
4. **Purity is almost always 1.**  Members of a template hardly ever disagree,
   so the purity term in `label_weight` does nothing; the weight is the source
   tier.  If a future round labels *within* templates the term will start to
   matter.
5. **The `chain` tier assumes an ok chain endorses every step it executed.**
   That is weaker than it sounds: a chain can be "ok" overall while one of its
   60 steps is mediocre.  The tier weight of 0.5 is a guess, not a measurement.
6. **`l4_leaf_*` is NaN for 84 % of templates.**  That is honest (they have no
   leaf input) but it means a tree model will split on missingness; a future
   version might add an explicit "has any leaf input" indicator instead of
   relying on the NaN.
7. **Round-006 findings that name both routes cannot become pairs.**  Three of
   the seven failures are of this shape: the audit's complaint is that the
   planner used a route *and* its alternative in the same chain.  That is a
   real defect class the pair format cannot express.

---

## Phase 0c-2 — the models

`./run_phase0c2.sh` over all 22 packs.  Everything below is
leave-one-**pack-group**-out unless it says otherwise; gold never trains.

### Timings

| step | wall | what |
|---|---:|---|
| `pair_features.py --packs all` | **3.2 s** | 693,952 rows x 23 cols, 5.6 MB |
| `train_eval.py --stage junk` | 10.3 s | 10 folds x 2 arms |
| `train_eval.py --stage recipe` | 9.7 s | the same folds, propagated to 112,854 recipes |
| `train_eval.py --stage role` | 22.8 s | 10 folds x 2 arms, 5 classes |
| `train_eval.py --stage zeroshot` | 7.6 s | 5 namespaces x 2 arms |
| `train_eval.py --stage ablation` | 67.3 s | 3 ablations x 10 folds x 2 arms + importances |
| `train_eval.py --stage pairs` | 128.4 s | 21 folds x 2 arms over 312,056 antisymmetrised rows |
| `train_eval.py --stage leaf` | 5.0 s* | + 6 economy re-solves |
| `export_model.py` | 83.1 s | final fits over every group + the golden sample |
| `tests/test_phase0c2.py` | 1.0 s | 80 checks |
| **`./run_phase0c2.sh` end to end** | **370.2 s** | 4.1 s aggregation + 295.9 s train/eval + 69.0 s export + 1.0 s tests |

Budget was 1 hour: **10x under**.  A retrain after a feature change is the
train + export legs only, **6.1 min**, against a 20-minute budget.

\* measured inside `--stage all`, where it reuses the ranker `--stage pairs`
already fitted; standalone it refits and costs 61 s.

Per-fit times: the junk head is **0.13-0.72 s** (LR) and **0.47-1.01 s**
(LightGBM, 48-132 boosting rounds after early stopping); the ranker is
**0.1-4.2 s** per fold per arm on 312 k x 188.  Nothing came near the 5-minute
cut-off, so no tree or row budget had to be reduced.

### Stage A1 — the junk head

| held-out group | n | junk | LR AUC | LR PR | LR R@P95 | GBM AUC | GBM PR | GBM R@P95 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| atm10 | 15,533 | 10,387 | 0.840 | 0.939 | 0.812 | 0.844 | 0.941 | 0.806 |
| atm10-lite | 912 | 746 | 0.922 | 0.979 | 0.893 | 0.968 | 0.993 | 0.962 |
| civilization-isolaria | 193 | 98 | 0.842 | 0.848 | 0.173 | 0.929 | 0.930 | 0.602 |
| cobblemon-x-create | 978 | 502 | 0.850 | 0.885 | 0.584 | 0.932 | 0.923 | 0.460 |
| create-oneblock | 251 | 129 | 0.742 | 0.780 | 0.318 | 0.856 | 0.875 | 0.550 |
| create-train | 2,422 | 1,795 | 0.909 | 0.969 | 0.798 | 0.880 | 0.963 | 0.773 |
| enigmatica10 | 3,557 | 3,372 | 0.988 | 0.999 | 1.000 | 0.990 | 0.999 | 1.000 |
| fom | 351 | 133 | 0.886 | 0.758 | **0.000** | 0.923 | 0.866 | 0.211 |
| mythcrafts | 185 | 101 | 0.765 | 0.817 | 0.267 | 0.915 | 0.939 | 0.743 |
| tfc-enhanced | 304 | 84 | 0.660 | 0.471 | **0.000** | 0.830 | 0.727 | 0.357 |
| **POOLED** | **24,686** | **17,347** | **0.850** | **0.945** | **0.778** | **0.871** | **0.952** | **0.809** |

Pooled precision at recall 0.90: LR **0.743**, GBM **0.762**.  Best-F1 point:
LR **0.856** at P = 0.966 / R = 0.769; GBM **0.876** at P = 0.965 / R = 0.802.
Mean *fold* AUC is higher than the pooled AUC for the tree arm (0.907 vs
0.871) because pooling is dominated by atm10, which is 63 % of the labelled
templates and the hardest fold.

`R@P95 = 0.000` on fom and tfc-enhanced is honest, not a bug: those two folds
have a **junk base rate of 38 % and 28 %**, the only two below half, so no
threshold on either fold's out-of-fold scores ever reaches precision 0.95.
The pooled curve does reach it.

**This is the prior study's ceiling, on the same gold labels, from a different
direction.**  `experiments/svm/REPORT-V2.md` plateaued at judgment-row AUC
0.81-0.86 and attributed it to 10-15 % grey-zone label noise.  Template-level
pooled LOPO AUC here is 0.850 (LR) / 0.871 (GBM) — inside that band, one
notch above its top on the tree arm.  Aggregating recipes into templates and
adding 129 structural features did **not** break the ceiling; it is a property
of the labels, not of the model class.

### Stage A2 — the role head

Five classes, `unknown` and the empty role dropped: 13,504 templates.

| arm | macro-F1 | weighted-F1 | accuracy |
|---|---:|---:|---:|
| LR | 0.466 | 0.797 | 0.766 |
| LightGBM | **0.491** | **0.816** | **0.826** |

Per-class recall (pooled out-of-fold):

| class | support | LR | LightGBM |
|---|---:|---:|---:|
| production | 6,820 | 0.742 | **0.881** |
| display | 5,645 | 0.847 | **0.861** |
| conversion | 813 | **0.483** | 0.292 |
| recycling | 177 | **0.537** | 0.232 |
| generation | 49 | 0.041 | 0.041 |

`generation` is not learnable at this sample size: **49 templates, present in
only 5 of the 10 labelled groups**, so most folds train on ~30 examples of it
and every fold that holds out a group containing it loses most of them.  The
tree arm buys its accuracy on the two big classes and pays for it on the two
small ones — macro-F1 separates by only 0.025, which is why the two arms look
so much closer than accuracy suggests.

### Stage A3 — at recipe level (what the planner consumes)

Each template's out-of-fold prediction propagated to its member recipes and
scored against `recipe_labels.gold_verdict`.  **Coverage 112,854 / 112,854 =
100 %** — every gold-verdict recipe's template has a prediction.

| arm | n | AUC | PR-AUC | R@P95 | P@R90 | best F1 | at P | at R |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| LR | 112,854 | **0.881** | 0.966 | **0.854** | **0.873** | 0.904 | 0.965 | 0.850 |
| LightGBM | 112,854 | 0.860 | 0.966 | 0.849 | 0.793 | 0.901 | 0.970 | 0.840 |

Recipe level is **better** than template level on every headline number, and
the ordering of the two arms flips.  Both are explained by the same thing: a
template's member count is its weight at recipe level, so the big, easy,
high-multiplicity templates (mirror pages, variant swaps) count more, and the
linear arm — which is flatter across templates — gains more from that than the
tree arm, which had specialised on the long tail.

### Stage A4 — new-mod zero-shot

Every template whose member recipes' majority namespace is one mod is held out
whole; training uses every pack group.  The five namespaces with the most
labelled templates, of the 327 seen, ranked and never named:

| rank | templates | junk | groups | LR AUC | LR R@P95 | GBM AUC | GBM R@P95 |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 10,161 | 8,888 | 10 | **0.961** | 0.982 | 0.776 | 0.373 |
| 2 | 644 | 394 | 7 | 0.821 | 0.345 | 0.869 | 0.508 |
| 3 | 551 | 8 | 2 | 1.000 | 1.000 | 1.000 | 1.000 |
| 4 | 486 | 453 | 4 | 0.995 | 0.996 | 1.000 | 1.000 |
| 5 | 438 | 143 | 2 | 0.993 | 0.972 | 0.999 | 0.979 |

Two cautions on reading this table high.  (i) These numbers are **not**
comparable to LOPO: the model has seen every pack group, so what is new is the
mod, not the corpus.  (ii) Rank 1 is the namespace that appears in all ten
labelled groups — holding it out removes 41 % of the labelled templates but
leaves the *packs* intact, which is a much weaker test than it looks.  Ranks 3
to 5 sit in only 2-4 groups and are close to separable.  Rank 2 — 644
templates across 7 groups, a near-even junk rate — is the only row here that
resembles a genuine new mod, and it scores **0.821 / 0.869**, i.e. the same
ceiling again.  The tree arm's collapse on rank 1 (AUC 0.776, R@P95 0.373) is
the clearest evidence in this report that LightGBM is memorising namespace-
correlated structure the linear arm is not.

### Stage A5 — ablations and importances

LOPO pooled AUC, one whole layer removed:

| variant | LR AUC | d | GBM AUC | d |
|---|---:|---:|---:|---:|
| all 129 features | 0.850 | — | 0.871 | — |
| drop L1 ontology (−34) | 0.841 | −0.009 | 0.880 | **+0.009** |
| drop L3 progression (−8) | 0.842 | −0.008 | 0.861 | −0.010 |
| drop L6 lexical (−34) | 0.824 | **−0.026** | 0.873 | +0.002 |

No layer is load-bearing for the tree arm — dropping L1 or L6 *helps* it,
within noise.  For the linear arm **L6 is the largest single contributor
(−0.026)**, which is uncomfortable: L6 is 32 hash buckets over residual tokens,
the least portable part of the feature set, and Phase 0c-1 already had to fix a
namespace leak in it.  The check that it is not leaking again: the zero-shot
rows above hold out a whole namespace and the linear arm does *not* degrade,
and the Phase 0c-1 correlation audit found no hash bucket among the top-8
correlates of the junk target.  Still, an L6-free model costs only 0.026 AUC
and the Java phases may prefer it.

Top-20 by importance (junk head, fitted over every labelled group):

| LR \|coef\| (sign) | value | LightGBM gain | gain |
|---|---:|---|---:|
| +`l5_sys_noop_share_mean` | 4.056 | `l5_sys_mean_outputs_mean` | 16,475 |
| +`l5_sys_mean_outputs_mean` | 1.946 | `l5_sys_log_n_recipes_mean` | 5,928 |
| −`l1_n_rid_tokens` | 1.790 | `l2_reliability_mean` | 2,263 |
| −`l6_h31` | 1.446 | `l5_sys_mean_inputs_mean` | 1,624 |
| +`l2_self_dependent_share` | 1.211 | `l2_self_dependent_share` | 1,250 |
| +`l5_sys_log_n_recipes_mean` | 1.049 | `l5_sys_noop_share_mean` | 845 |
| +`l2_log_value_ratio_min` | 0.962 | `l2_is_round_trip_share` | 729 |
| −`l5_sys_mean_reliability_mean` | 0.962 | `l1_n_rid_tokens` | 407 |
| +`l6_h30` | 0.903 | `l5_sys_cross_system_output_share_mean` | 363 |
| +`l1_n_outputs` | 0.721 | `l2_count_ratio_max` | 303 |

Both arms put the **system** first: a system most of whose recipes are no-ops,
or that emits many outputs per recipe, or that is very large, is a display /
mirror system.  That is the single strongest signal in the corpus and it is
structural, not lexical.  `l2_self_dependent_share` (a recipe that consumes
what it makes) is the strongest *recipe-shape* signal in both.  The Phase 0c-1
correlation audit predicted exactly these two families.

**Caveat worth stating plainly.**  The system features are aggregated per
*system*, and a template belongs to one system, so a model that leans on them
is in part learning "which system is this" — portable across packs only to the
extent that a new pack's systems look like the old ones' statistically.  That
is the intended reading (the Phase 0b system-shape columns are pack-relative
robust z-scores), but it is also why the per-fold spread is so wide.

### Stage B — the ProducerRanker

156,028 training pairs (the 19 gold readings removed), 188 pair features,
antisymmetrised to 312,056 rows.

| tier | pairs | LR LOPO acc | LightGBM LOPO acc |
|---|---:|---:|---:|
| silver (two gold verdicts) | 6,291 | **0.811** | 0.656 |
| chain (ok chain vs junk sibling) | 6,080 | 0.870 | **0.912** |
| bronze (rule replay) | 143,642 | 0.865 | **0.940** |

Cost-only baselines, whole corpus, no learning:

| ranking rule | silver | chain | bronze |
|---|---:|---:|---:|
| Phase 0b `value_in` | 0.550 | 0.548 | 0.137 |
| `value_in + step_cost x (1 + tier)` | 0.555 | 0.524 | 0.128 |

**The economy layer alone is a coin flip on the human-labelled tiers and
actively wrong on the rule replay (0.137).** That last number is the most
informative line in this table: the rule replay's `junk` recipes systematically
have *cheaper* inputs than their useful siblings — a mirror page or a
single-slot pseudo-recipe consumes one cheap thing — so cost-minimisation
walks straight into them.  This is the concrete, quantified statement of the
bug this whole project exists to fix, and it is why the ranker cannot be a
cost function.

The two arms disagree sharply and it matters which one you believe.
LightGBM wins the two tiers it has mass on (bronze is 92 % of the training set
and chain shares its provenance) and **loses badly on silver**, the only tier
built from two independent human verdicts — including one fold,
enigmatica10 with 409 silver pairs, where it scores **0.061**, i.e. it is
confidently backwards.  The linear arm is stable everywhere (worst silver fold
0.474, on 19 pairs).  Silver-LOPO is therefore the metric the export decision
uses, and it picks LR.

#### The 18 gold pairs, with margins

Margin > 0 means the model prefers the recipe the audit says it should have
picked.  `LOPO` is the model that never saw that pair's own pack group; `full`
has seen every group but never a gold pair.

| finding | rd | w | group | LR full | LR LOPO | GBM full | GBM LOPO | `value_in` | `+step` |
|---|---:|---:|---|---:|---:|---:|---:|---:|---:|
| F-atm10-lite-1 | 0 | 0.20 | atm10-lite | +0.98 | **+1.86** | +1.18 | **+1.65** | +14.05 | +14.97 |
| F-atm10-lite-1 | 1 | 0.20 | atm10-lite | +2.33 | **+2.14** | +1.13 | **+1.79** | +9.19 | +10.11 |
| F-atm10-lite-1 | 2 | 0.20 | atm10-lite | −0.63 | **+0.12** | −0.15 | **+0.39** | −71.80 | −73.64 |
| F-atm10-lite-1 | 3 | 0.20 | atm10-lite | −0.01 | **+0.73** | +1.03 | **+1.15** | +1.46 | +2.38 |
| F-atm10-lite-1 | 4 | 0.20 | atm10-lite | −0.04 | −0.39 | +0.36 | −0.22 | 0.00 | −0.92 |
| F-farmers-colonies-1 | 0 | 1.00 | farmers-colonies | +0.46 | **+0.51** | +0.92 | **+0.85** | +0.95 | +0.95 |
| F-farmers-colonies-2 | 0 | 1.00 | farmers-colonies | −0.26 | −0.23 | +0.19 | **+0.28** | −0.70 | +0.60 |
| F-lostworld-1 | 0 | 1.00 | lost-world | +0.81 | **+0.94** | +0.23 | **+0.19** | +0.04 | +0.04 |
| F-lostworld-2 | 0 | 0.50 | lost-world | +1.58 | **+1.69** | +0.99 | **+0.76** | +0.81 | +2.28 |
| F-lostworld-3 | 0 | 1.00 | lost-world | +0.64 | **+0.58** | +0.94 | **+0.93** | +1.65 | +1.65 |
| F-mythcrafts-1 | 0 | 1.00 | mythcrafts | −0.42 | −0.49 | +0.82 | **+0.66** | +209.05 | +210.15 |
| F-technical-electrical-1 | 0 | 0.17 | technical-electrical | +0.04 | −0.17 | +0.04 | **+0.10** | −1.01 | −2.39 |
| F-technical-electrical-1 | 1 | 0.17 | technical-electrical | +0.62 | **+0.29** | −0.83 | −0.61 | −1.06 | −2.43 |
| F-technical-electrical-1 | 2 | 0.17 | technical-electrical | −0.56 | −0.86 | −1.14 | −1.19 | −0.95 | −2.32 |
| F-technical-electrical-1 | 3 | 0.17 | technical-electrical | +0.48 | **+0.23** | −1.15 | −1.19 | −0.98 | −2.35 |
| F-technical-electrical-1 | 4 | 0.17 | technical-electrical | −0.42 | −0.59 | −1.00 | −0.96 | −1.14 | −2.51 |
| F-technical-electrical-1 | 5 | 0.17 | technical-electrical | −1.65 | −1.86 | −1.25 | −1.24 | −1.04 | −2.41 |
| F-tfc-enhanced-1 | 0 | 1.00 | tfc-enhanced | +2.33 | **+1.74** | +1.20 | **+0.94** | +1.20 | +2.20 |

| model | pairs won | accuracy | weighted by `weight` |
|---|---:|---:|---:|
| LR, LOPO | 11 / 18 | 0.611 | 0.663 |
| LR, full | 10 / 18 | 0.556 | 0.635 |
| LightGBM, LOPO | 12 / 18 | 0.667 | **0.878** |
| LightGBM, full | 12 / 18 | 0.667 | 0.878 |
| `value_in` | 9 / 18 | 0.500 | 0.718 |
| `value_in + step` | 10 / 18 | 0.556 | 0.835 |

**Read this table for the cases, not the accuracy.**  n = 18 readings from 9
findings; a single finding (`F-technical-electrical-1`, 6 readings weighted
1/6 each) is a third of the rows and both arms lose most of it.  Weighted
accuracy is the fairer number and it says LightGBM 0.878, cost-plus-step
0.835, LR 0.663 — i.e. on the audit cases the tree arm and the *cost baseline*
both beat the linear arm, which is the opposite of the silver-tier verdict.
Two honest readings coexist: the gold set is too small to adjudicate, and the
silver tier is 350x larger.  The export keeps both arms for exactly this
reason.

What the learned ranker adds over cost is clearest where the two disagree in
sign: `F-technical-electrical-1` reading 0 (cost says −1.01, both models say
+0.04 / +0.10) and `F-farmers-colonies-2` (cost says −0.70, GBM says +0.28).
What it does not fix: readings 2, 4 and 5 of `F-technical-electrical-1`, lost
by every model and by both baselines.

**One gold reading dropped.**  `F-lostworld-2` is a two-reading `silly-detour`
finding; reading 1 pairs two recipes the prose never compares (the finding is
about the detour in reading 0).  It is addressed in code as
`{"F-lostworld-2": (1,)}` — finding id plus reading index in the order
`labels.py` wrote them, never by registry id — and the reading count is
asserted before the drop.

### Stage C — supervised LeafAcquirability

Weights the LR ranker put on the per-recipe **mean** leaf aggregates, at raw
column scale:

| leaf column | weight (mean agg) | weight (min agg) |
|---|---:|---:|
| `leaf_pack_fraction` | **+1.779** | +0.694 |
| `min_consumer_tier` | +0.237 | −0.296 |
| `n_variant_keys` | +0.197 | −0.212 |
| `n_consumer_ns` | +0.059 | −0.064 |
| `n_consumers` | −0.006 | +0.006 |
| `n_tags` | −0.010 | +0.003 |
| `producer_packs` | −0.084 | +0.189 |
| `containing_packs` | −0.117 | +0.084 |
| `n_consumer_systems` | −0.169 | −0.005 |
| `renewable` | +0.000 | +0.000 |
| *(the unsupervised score, as one pair flag)* | **−1.773** | |

`renewable` is exactly zero because the corpus has **no renewable leaf at all**
(`renewable_leaves: 0` in every pack's economy meta) — the column is constant,
not uninformative.

**The supervised ranking is the near-inverse of the unsupervised one.**
Spearman(supervised acquirability, Phase 0b acquirability) is **negative in 21
of 22 packs**: mean **−0.648**, min −0.998, max +0.001.  The weight on the
unsupervised composite entering as its own pair flag is **−1.773**, the second
largest weight in the whole leaf block, and it points the same way.

This is a real result and it should be read carefully rather than taken as
"the unsupervised score is wrong".  Phase 0b's composite is oriented so that
*consumed by many things* means *cheap*.  The ranker is answering a different
question: **given two producers of the same item, which one should the planner
commit to?**  A producer whose leaf inputs are mass-consumed commodities is
typically the *generic* route (the bone-meal / lapis / ore path the audits
complain about), and the one the audits prefer consumes something specific and
locally abundant — which is exactly what `leaf_pack_fraction` (how much of this
pack's leaf population this id accounts for) picks up with the largest positive
weight.  So "cheap to obtain in the abstract" and "the right thing for a
planner to route through" are genuinely different orderings, and this is the
first measurement of how different: **ρ ≈ −0.65**.

#### The round-006 acceptance cases, re-priced

`economy.py` was called **as a library** (`build_graph` / `compute_shape` /
`incidence` / `solve_tiers` / `compute_reliability` / `compute_values`) with
only the seed array swapped, so these are producer costs out of the same
solver Phase 0b uses — not a re-implementation.  Producer cost =
`sum(input value x amount) + step cost`.

| finding | rd | unsup chosen | unsup preferred | ok? | sup chosen | sup preferred | ok? |
|---|---:|---:|---:|:--:|---:|---:|:--:|
| F-atm10-lite-1 | 0 | 38.88 | 12.79 | yes | 39.30 | 13.15 | yes |
| F-atm10-lite-1 | 1 | 38.88 | 17.02 | yes | 39.30 | 16.63 | yes |
| F-atm10-lite-1 | 2 | 38.88 | 174.6 | **no** | 39.30 | 172.6 | **no** |
| F-atm10-lite-1 | 3 | 16.79 | 12.79 | yes | 16.94 | 13.15 | yes |
| F-atm10-lite-1 | 4 | 38.23 | 39.14 | **no** | 39.07 | 39.99 | **no** |
| F-farmers-colonies-1 | 0 | 12.18 | 8.421 | yes | 12.26 | 7.733 | yes |
| F-farmers-colonies-2 | 0 | 10.58 | 8.421 | yes | 10.68 | 7.733 | yes |
| F-lostworld-1 | 0 | 57.96 | 57.91 | yes | 61.58 | 61.32 | yes |
| F-lostworld-2 | 0 | 96.51 | 92.60 | yes | 100.3 | 96.35 | yes |
| F-lostworld-3 | 0 | 14.31 | 9.130 | yes | 14.53 | 8.754 | yes |
| F-mythcrafts-1 | 0 | 870.3 | 387.3 | yes | 889.3 | 394.5 | yes |
| F-technical-electrical-1 | 0-5 | 4.876 | 6.79-6.98 | **no** x6 | 4.895 | 6.81-7.29 | **no** x6 |
| F-tfc-enhanced-1 | 0 | 6.976 | 3.579 | yes | 7.384 | 3.629 | yes |

**10 / 18 under the unsupervised seed, 10 / 18 under the supervised seed — the
same 10.**  The five named round-006 acceptance cases (farmers blue dye,
farmers white dye, tfc blue dye, lost-world mossy, technical-electrical red
dye) come out 4 / 5 both ways; the red-dye case fails under both seeds by a
consistent ~40 %.  Nothing was tuned to make this happen and nothing was tuned
after seeing it.

The conclusion is unwelcome but clean: **swapping the leaf seed does not move
the acceptance cases.**  The leaf band is `[1, 2]` by design (Phase 0b's
deliberate fix for the 119x v1 spread), so no reordering inside it can
overcome a 40 % cost gap that comes from chain structure — the very property
that made the band safe makes the leaf score nearly powerless here.  The
supervised leaf model is worth exporting because the *ranker* uses those
columns with a large weight, but as a seed for the cost solver its effect is a
rounding error.  Any real fix for `F-technical-electrical-1` has to come from
the ranker overriding the cost order, not from re-pricing leaves.

### The gate table

| # | gate (plan) | number | verdict |
|---|---|---|:--:|
| 1 | LOPO junk recall >= 0.90 at P >= 0.95 (template) | LR **0.778** / GBM **0.809** | **FAIL** |
| 2 | LOPO junk recall >= 0.90 at P >= 0.95 (recipe level) | LR **0.854** / GBM **0.849** | **FAIL** |
| 3 | LOPO junk recall >= 0.90 at the best-F1 point (template) | LR **0.769** / GBM **0.802** | **FAIL** |
| 4 | LOPO junk recall >= 0.90 at the best-F1 point (recipe) | LR **0.850** / GBM **0.840** | **FAIL** |
| 5 | gold precision >= 0.95 at the operating point | LR **0.966** / GBM **0.965** (recipe: 0.965 / 0.970) | **PASS** |
| 6 | pairwise accuracy >= 0.85 on silver-LOPO | LR **0.811** / GBM **0.656** | **FAIL** |
| 7 | gold-pair accuracy (reported, no threshold) | LR 11/18 = 0.611 (w 0.663); GBM 12/18 = 0.667 (w 0.878); cost baseline 10/18 = 0.556 (w 0.835) | reported |

Four of the six thresholded gates fail, and they fail in one direction: **the
models are precise enough and not recall-enough.**  At precision 0.95 the junk
head finds 78-85 % of the junk instead of the 90 % the plan asked for, and the
ranker is 0.04 short on silver.  Gate 5 passes comfortably, which means the
junk the model *does* flag is safe to act on — a planner can drop the
high-confidence junk today and would be wrong about 3.5 % of the time.

The gap is the same 10-15 % grey zone the prior study measured, now visible
from a second angle: at the best-F1 point the missing 15-23 % of junk sits in
the region where the model's score is near the threshold, which is where the
labels themselves disagree.  Closing it needs better labels, not a better
model.

### v2 re-run after the pack-key fix (2026-09-20)

`pair_features.py` labelled `recipe_leaf_agg.parquet` rows with the export
directory name while every other table keys `pack` on the slug; the two differ
for 5 of 22 packs (All Of Create, BlockFront, Brick's Building,
CreateCustomised, LuckyBlockChallenge), so `train_eval.recipe_matrix`'s left
merge silently NaN-ed all 22 leaf-aggregate columns on those packs (~11 % of
training rows).  Found by the Phase 3 agent, fixed (rows are now labelled by
slug), and the whole of `run_phase0c2.sh` was re-run (4 m 04 s train + 1 m 15 s
export).  Effect on the numbers above:

| metric | before | after |
|---|---:|---:|
| junk head, LOPO AUC (LR / GBM) | 0.8499 / 0.8715 | 0.8499 / 0.8715 (unchanged — the heads never used the leaf aggregates) |
| role head, LOPO macro-F1 (LR / GBM) | 0.4659 / 0.4911 | unchanged |
| ranker, silver-LOPO accuracy (LR / GBM) | 0.8110 / 0.6559 | 0.8110 / 0.6559 (all silver pairs live in packs whose slug equals the directory name) |
| ranker, gold accuracy LR (unweighted / weighted, n = 18) | 0.556 / 0.635 | 0.500 / 0.616 |
| ranker, gold accuracy GBM (unweighted / weighted) | 0.667 / 0.878 | unchanged |
| supervised leaf seed, audit cases won | 10 / 18 | 10 / 18 |
| `manifest.data_hash` | 027e6156… | 42da3987… |

The exported `producer-ranker.json` weights did change (the five affected
packs are bronze-only), so the Phase 3 planner indexes were rebuilt and its
A/B and audit re-run against the corrected ranker; the pre-fix metrics files
are kept under `out/models/_pre-packkey-fix/`.

### Export

| file | size | contents |
|---|---:|---|
| `feature-spec.json` | 0.03 MB | 129 template + 188 pair + 10 leaf names, layers, fill/mean/std, the L6 hash spec |
| `template-role.json` | 1.78 MB | junk head + role head, both arms |
| `producer-ranker.json` | 0.18 MB | the pairwise ranker, both arms |
| `leaf-acquirability.json` | < 0.01 MB | 10 weights, `K`, the per-pack standardisation, the Spearmans |
| `token-classes.json` | 0.19 MB | the Phase 0a prior, version-stamped |
| `golden-sample.json` | 1.57 MB | 1,000 rows / 21 groups, 500 pairs, 300 leaves + expected outputs |
| `manifest.json` | < 0.01 MB | versions, date, data hash, choices, sizes |
| `recipe_leaf_agg.parquet` | 5.6 MB | the one added aggregation (not a model) |

No file needed trimming; every JSON is under its cap.

**Default arm.**  The rule is "LR unless LightGBM is more than 0.02 ahead on
the headline LOPO metric".

| model | LR | LightGBM | delta | default |
|---|---:|---:|---:|---|
| junk head (pooled AUC) | 0.850 | 0.871 | +0.021 | **LightGBM** |
| role head (pooled macro-F1) | 0.466 | 0.491 | +0.025 | **LightGBM** |
| ranker (silver-LOPO accuracy) | 0.811 | 0.656 | −0.155 | **LR** |

Both head decisions land within 0.005 of the tolerance, which is not a
comfortable margin.  Given that the linear arm wins recipe-level AUC
(0.881 vs 0.860), wins zero-shot rank 1 by a wide margin (0.961 vs 0.776) and
is the arm the ranker uses anyway, **a Java port that ships LR everywhere
would give up ~0.02 AUC and gain one code path**; the metrics to make that
call are all in `manifest.json` and both arms are exported.

### Reference inference

`predict_reference.py` imports numpy and nothing else (asserted) and
reproduces every library prediction on the golden sample:

| model | rows | max abs delta |
|---|---:|---:|
| junk probability, LR | 1,000 | 6.1e-15 |
| junk probability, LightGBM | 1,000 | 0.0 |
| role probabilities, LR | 1,000 x 5 | 0.0 |
| role probabilities, LightGBM | 1,000 x 5 | 0.0 |
| ranker margin, LR | 500 | 0.0 |
| ranker margin, LightGBM | 500 | 0.0 |
| leaf acquirability / seed | 300 | 2.2e-16 / 4.4e-16 |

Tolerance is 1e-6, nine orders of magnitude above the worst observed error, so
the contract survives a different float-summation order in Java.

### Open questions from Phase 0c-2

1. **The recall gate misses by 0.10 and the reason is the labels.**  At
   precision 0.95 the junk head finds 78 % of junk at template level and 85 %
   at recipe level.  The prior recipe-level study hit the same wall from a
   different model class on the same gold labels.  Nothing in the feature set
   moved it: the ablations show every layer is worth <= 0.026 AUC.  More
   labels in the grey zone is the only lever left that is likely to work.
2. **The two arms disagree about which tier to believe, and the disagreement
   is structural.**  LightGBM wins bronze (0.940 vs 0.865) and chain, loses
   silver badly (0.656 vs 0.811), and wins the weighted gold set (0.878 vs
   0.663).  Bronze is 92 % of the training pairs, so a tree that fits bronze
   fits the *rule replay* — the thing being replaced.  I picked silver as the
   export metric because it is the only tier built from two independent human
   verdicts, but 18 gold readings cannot settle it.
3. **The supervised leaf seed does not move the acceptance cases at all.**
   Same 10 / 18 before and after.  The `[1, K]` band that makes the leaf score
   safe also makes it too weak to overturn a 40 % chain-cost gap.  Re-pricing
   leaves is not the fix for routing; the ranker overriding the cost order is.
4. **The supervised and unsupervised leaf rankings are near-inverses**
   (Spearman −0.648 mean, negative in 21 of 22 packs).  Either the two are
   genuinely answering different questions — plausible, and argued above — or
   the ranker has found a proxy in `leaf_pack_fraction` that will not
   transfer.  One more round of routing audits on packs outside the six that
   carry gold pairs would separate those two explanations.
5. **The strongest features are per-system aggregates.**  `l5_sys_*` takes the
   top two slots in both arms.  They are pack-relative robust z-scores, so
   they *should* transfer, but a model whose top signal is "what kind of
   system is this" will only be as good as the new pack's system profiling.
6. **L6 is the largest single contributor to the linear arm (−0.026 without
   it)** and it is 32 sha1 hash buckets over residual tokens — the least
   portable thing in the feature set, and the part that already had one
   namespace leak.  An L6-free model is 0.026 AUC cheaper and much safer to
   port.
7. **`generation` is unlearnable at n = 49** (recall 0.041 in both arms) and
   `recycling` (n = 177) only works in the linear arm (0.537 vs 0.232).  The
   role head is really a three-class model with two decorative classes.

---

## Phase 0 summary

### Every Phase 0 gate

| phase | gate | number | verdict |
|---|---|---|:--:|
| 0a | load 22 packs < 5-10 min, RSS < 6 GB | **32.8 s**, 674 MB | PASS |
| 0a | ontology + prior + onboarding < 2 min | **35.6 s**, 2.01 GB | PASS |
| 0a | `len(recipes.parquet) == recipeCount`, nothing filtered | 693,952 = 693,952 | PASS |
| 0a | byte-identical across two runs | asserted | PASS |
| 0b | economy over 22 packs < 5 min | **17.2-18.4 s** | PASS |
| 0b | atm10 alone <= 1 min | **7.7 s** | PASS |
| 0b | values finite and > 0, tiers finite and >= 0 | asserted, 65 checks | PASS |
| 0b | byte-identical across two runs | asserted | PASS |
| 0c-1 | labels + features < 5 min per script | **9.8 s** / **19.7 s** | PASS |
| 0c-1 | feature matrix < 500 MB | **42.3 MB** (20.5 + 21.8) | PASS |
| 0c-1 | feature and label key sets identical | 240,377 = 240,377 | PASS |
| 0c-1 | both sides of every pair produce the named output | 156,047 / 156,047 | PASS |
| 0c-2 | LOPO junk recall >= 0.90 at P >= 0.95 (template) | LR 0.778 / GBM **0.809** | **FAIL** |
| 0c-2 | LOPO junk recall >= 0.90 at P >= 0.95 (recipe) | LR **0.854** / GBM 0.849 | **FAIL** |
| 0c-2 | LOPO junk recall >= 0.90 at best-F1 (template) | LR 0.769 / GBM **0.802** | **FAIL** |
| 0c-2 | LOPO junk recall >= 0.90 at best-F1 (recipe) | LR **0.850** / GBM 0.840 | **FAIL** |
| 0c-2 | gold precision >= 0.95 at the operating point | LR **0.966** / GBM 0.965 | PASS |
| 0c-2 | pairwise accuracy >= 0.85 on silver-LOPO | LR **0.811** / GBM 0.656 | **FAIL** |
| 0c-2 | gold-pair accuracy (report only, n = 18) | LR 0.611 (w 0.663) / GBM 0.667 (w **0.878**) | reported |
| 0c-2 | full experiment <= 1 h | **370.2 s** | PASS |
| 0c-2 | retrain after a feature change <= 20 min | **365 s** (train + export) | PASS |
| all | no mod / item / material / colour / system vocabulary | 10 modules clean | PASS |
| all | reference inference matches the libraries to 1e-6 | worst **6.1e-15** | PASS |

**18 PASS, 5 FAIL, 1 reported.**  Every failure is the same failure: the
models are precise and not recall-enough, by 0.05-0.13 depending on where you
measure, and the ranker is 0.04 short on silver.  Nothing fails on
performance, determinism, size, or the design rules.

### Wall clock, the whole chain

| script | wall |
|---|---:|
| `./run_phase0a.sh` (load + ontology + 42 checks) | 69.4 s |
| `./run_phase0b.sh` (economy + 65 checks) | 18.9 s |
| `./run_phase0c1.sh` (labels + features + 44 checks) | 33.2 s |
| `./run_phase0c2.sh` (aggregate + train + export + 80 checks) | 370.2 s |
| **total, cold, 22 packs, 693,952 recipes** | **~491.7 s (8 min 12 s)** |

The first three rows are the timings measured in their own sections above on
this machine; only the last was re-measured for this section.  Phase 0c-2 is
**75 %** of the chain, and 80 % of *that* is the pairwise ranker's 21 LOPO
folds x 2 arms.

### What Phase 0 hands to the Java phases

`out/models/` — 3.75 MB of JSON plus one 5.6 MB parquet:

| file | size |
|---|---:|
| `template-role.json` | 1.78 MB |
| `golden-sample.json` | 1.57 MB |
| `token-classes.json` | 0.19 MB |
| `producer-ranker.json` | 0.18 MB |
| `feature-spec.json` | 0.03 MB |
| `leaf-acquirability.json` | 1.6 KB |
| `manifest.json` | 1.8 KB |
| `recipe_leaf_agg.parquet` (data, not a model) | 5.6 MB |

### Top open issues for the Java phases, ranked

1. **The junk head is not a filter yet.**  At the safe operating point it
   catches 85 % of junk at recipe level.  The Java planner must keep a second
   net (the builtin rules it already has) behind the model, and the model's
   job is to *reduce* what the rules have to catch, not to replace them in
   one step.  Shipping it as a hard filter would drop ~15 % of junk into the
   planner and, at P = 0.965, discard ~3.5 % of real recipes.
2. **Decide the arm, once, with a measurement.**  LightGBM is the default for
   both heads by +0.021 / +0.025 — barely over the 0.02 tolerance — while the
   linear arm wins recipe-level AUC (0.881 vs 0.860) and the larger zero-shot
   fold (0.961 vs 0.776).  Shipping LR everywhere costs ~0.02 AUC and buys one
   code path and one tenth the JSON.  Both arms are exported; `manifest.json`
   has the numbers.
3. **The ranker needs a routing hook, not a cheaper seed.**  Stage C is
   unambiguous: re-seeding leaves moves nothing.  The planner has to let the
   ranker reorder producers of the same output *after* the cost solve, which
   is an API decision in the Java planner, not a model decision.
4. **Reproduce the feature builder exactly or the models are noise.**  Three
   things silently diverge if they are ported loosely: the L6 sha1 bucketing
   *and* its pack-namespace filter; the per-pack centring of every value
   column; and the "absent one-hot is 0, not NaN" rule.  `feature-spec.json`
   plus `golden-sample.json` catch all three — run the fixture first, before
   any planner integration.
5. **The `l5_sys_*` block must be computed at load time.**  The top two
   features in both arms are per-system aggregates over the pack being
   planned.  They are not constants and cannot be baked into the export.
6. **Only two role classes are trustworthy.**  `production` (0.88) and
   `display` (0.86) recall are usable; `conversion` 0.29-0.48, `recycling`
   0.23-0.54, `generation` 0.04.  Fall back to the Phase 0b shape role
   whenever the role head's argmax is one of the last three.
7. **Every metric here is 63 % atm10.**  The label mass has not moved since
   Phase 0c-1 flagged it.  The single highest-value labelling investment is
   routing audits (round-006 style) on the twelve packs with no gold labels at
   all — that is also the only thing that can settle open question 2 above.

---

## Phase 3 — the hierarchical planner (Python prototype)

> Re-run note (2026-09-20): after the Phase 0c-2 pack-key fix and retrain, the planner indexes were rebuilt (`planner.py --stage index --force`) and the A/B and audit re-run. Every number below reproduces to the stated precision (corpus medians identical; paired win/loss counts within ±10 of 3,301; audit 8/13 at γ ≥ 1.5, 7/13 at γ = 1 or with the ranker off). Log: `out/planner-eval/rerun-after-packkey-fix.log`.

`planner.py` (the planner), `eval_planner.py` (the offline A/B),
`tests/test_phase3.py` (45 checks), `run_phase3.sh`.  Design in DESIGN.md
"Phase 3".  Nothing in Phases 0a–0c-2 was modified; `tests/test_phase0a.py`
gained two module names so its no-vocabulary check now covers 12 modules.

### Timings

| stage | wall | budget |
|---|---:|---|
| build index + model scores, 22 packs, **cold** (`--force`, scores computed) | **82.5 s** (atm10 35.3 s) | — |
| build index + model scores, 22 packs, **warm** (scores from parquet) | **64.5 s** | — |
| A/B, 22 packs × 150 targets, 3,301 paired targets | **94.1 s** | < 30 min |
| audit cases, 13 cases × 8 configurations + from-stock | 19.0 s | — |
| knob ablations, 10 configurations × 7 packs | 230.8 s | — |
| `tests/test_phase3.py` (45 checks) | 0.6 s | — |

Per-target wall, by pack (prototype only; the baseline is read from disk):

| pack | n | build s | median ms | p95 ms | max ms |
|---|---:|---:|---:|---:|---:|
| fom | 150 | 0.06 | **0.43** | 0.92 | 1.25 |
| atm10 | 150 | 19.09 | **19.09** | 163.71 | 338.41 |
| atm10-lite | 191 | 0.66 | 13.59 | 54.01 | 221.44 |
| minecolonies-dimensional-adventure | 150 | 9.38 | 5.07 | 12.40 | 20.37 |
| create-train | 150 | 8.33 | 6.30 | 16.80 | 37.44 |
| enigmatica10 | 150 | 7.29 | 2.10 | 11.48 | 45.15 |
| civilization-isolaria | 150 | 3.16 | 1.94 | 4.66 | 42.72 |
| the other 15 packs | 150 ea. | 0.03–2.35 | 0.15–1.26 | 0.28–6.89 | 0.38–19.57 |

**fom median 0.43 ms (budget 50 ms), atm10 median 19.09 ms (budget 500 ms).**
Build time is dominated by reading `contents.parquet`; atm10's 19 s is 1.58 M
content rows.  The first build of a pack additionally computes the model
scores (atm10: +20 s) and caches them to
`out/<slug>/planner/{template_posteriors,producer_scores}.parquet`.

Two optimisations were needed to get here and both are Java-relevant:
counting the demand cone up front cost 8 ms of an 8.1 ms two-action target
(now lazy, §8 of the design), and rebuilding machine/output sets inside the
baseline id resolver cost 4.8 ms per action on the pack with 47 k reused ids.

### Corpus A/B — 22 packs, 3,301 paired targets

Identical targets, identical metric function on both sides.  "proto<" counts
targets where the prototype's value is lower (better for every metric here).

| metric | base med | proto med | base mean | proto mean | proto < | proto > | tie |
|---|---:|---:|---:|---:|---:|---:|---:|
| actions | 82 | **12** | 235.7 | **40.5** | 3095 | 92 | 114 |
| distinct_recipes | 39 | **11** | 96.6 | **16.7** | 3116 | 64 | 121 |
| crafts | 311 | **24** | 5.0e10 | **1.2e5** | 3032 | 166 | 103 |
| raw_kinds | 68 | **6** | 247.0 | **8.0** | 2925 | 49 | 327 |
| machines | 3 | **2** | 6.83 | **4.60** | 1964 | 272 | 1065 |
| depth | 33 | **5** | 80.5 | **6.4** | 3171 | 8 | 122 |
| junk_in_path (rule replay `wl`) | 0.000 | 0.000 | **0.015** | **0.022** | 868 | **579** | 1854 |
| junk_in_path (model, `junk_p > τ`) | 0.333 | **0.000** | 0.359 | **0.014** | 2989 | 24 | 288 |
| display_in_path | 0.000 | 0.000 | 0.083 | **0.017** | 1195 | 74 | 2032 |
| round_trips | 1 | **0** | 42.8 | **0.000** | 1772 | **0** | 1529 |
| pool_amplification | 0.000 | 0.000 | 3.3e8 | **0.456** | 519 | 31 | 2751 |
| tier_reversals | 8 | **0** | 19.0 | **0.91** | 2696 | 62 | 543 |
| incomplete | 1 | **0** | 0.717 | **0.005** | 2351 | 0 | 950 |
| raw_nonleaf | 43 | **1** | 183.2 | **1.43** | 2904 | 2 | 395 |

`raw_nonleaf` is in the table deliberately: it counts `[RAW]` entries that are
*not* Phase 0b leaves, i.e. demands a planner wrote off as "go and get it".
Without it, "incomplete 0.717 → 0.005" would flatter the prototype, because
the two sides report a hopeless demand differently (the baseline as
`UNRESOLVED`, the prototype as raw).  Both numbers move the same way, so the
conclusion survives: 183.2 → 1.43.

**Round trips are 0 on every one of the 3,301 targets**, and that is by
construction, not by filtering — see the design's settle-order rule.  The
worst baseline cases:

| pack | target | baseline round trips | proto | baseline actions | proto actions |
|---|---|---:|---:|---:|---:|
| atm10-lite | `occultism:iesnium_dust` | 1793 | **0** | 3236 | 39 |
| atm10-lite | `allthemodium:allthemodium_axe` | 1788 | **0** | 3130 | 19 |
| atm10-lite | `occultism:raw_iesnium_block` | 1709 | **0** | 3091 | 610 |
| fom | `create:netherite_backtank` (the pinned regression) | 159 | **0** | 246 | 22 |

**The one metric the prototype loses is `junk_in_path_wl`** (0.015 → 0.022
mean; 579 targets worse, 868 better, 1854 tied).  It is concentrated in three
packs and is a *model-recall* result, quantified below.

Per-pack `incomplete` and `junk_in_path_wl`, baseline → prototype:

| pack | incomplete | junk_wl | | pack | incomplete | junk_wl |
|---|---|---|---|---|---|---|
| all-of-create-2-1 | 0.75 → 0.00 | 0.009 → 0.004 | | create-ultimate | 0.51 → 0.00 | 0.001 → 0.000 |
| atm10 | 1.00 → **0.15** | 0.004 → **0.082** | | createcustomised1-1-2 | 1.00 → 0.00 | 0.025 → 0.017 |
| atm10-lite | 1.00 → 0.00 | 0.002 → **0.109** | | creative-drawers-producer-2 | 0.96 → 0.00 | 0.007 → 0.002 |
| blockfront…0-9-0-24b | 0.09 → 0.00 | 0.000 → 0.013 | | enigmatica10 | 0.99 → 0.00 | 0.018 → 0.008 |
| brick-s-building-1-4-1 | 1.00 → 0.00 | 0.041 → 0.043 | | farmers-colonies | 0.33 → 0.00 | 0.001 → 0.003 |
| civilization-isolaria | 0.85 → 0.00 | 0.011 → 0.011 | | fom | 0.63 → 0.00 | 0.000 → 0.003 |
| cobblemon-x-create | 0.97 → 0.00 | 0.018 → 0.003 | | lost-world | 0.56 → 0.00 | 0.005 → 0.021 |
| create-lets-create | 0.87 → 0.00 | 0.001 → 0.001 | | luckyblockchallenge | 0.09 → 0.00 | 0.000 → 0.013 |
| create-oneblock | 1.00 → 0.00 | 0.046 → 0.046 | | minecolonies-dim.-adventure | 0.99 → 0.00 | 0.054 → **0.098** |
| create-train | 1.00 → 0.00 | 0.040 → 0.000 | | mythcrafts | 0.27 → 0.00 | 0.020 → 0.001 |
| | | | | technical-electrical | 0.81 → 0.00 | 0.011 → 0.000 |
| | | | | tfc-enhanced | 0.13 → 0.00 | 0.011 → 0.000 |

**67 % of the baseline's 8,752 exported chains are `incomplete`** (5,899 of
them; on atm10, atm10-lite, create-oneblock, create-train, enigmatica10,
createcustomised and brick-s-building it is 100 % of the sampled targets).
The prototype is incomplete on 0.5 % of targets, all of them on atm10 (15 %
of that pack's sample, all `BUDGET_TRUNCATED`).

### The `junk_in_path` regression, quantified

The baseline drops `cirrus:quality/junk` and `cirrus:quality/duplicate`
recipes **before** graph build (builtin rules v2).  The prototype's only
filter is the junk head at the P ≥ 0.95 operating point.  On atm10-lite:

* the pack has 1,742 recipes the rule replay labels `junk`;
* at τ = 0.8707 the head flags **882 of them — recall 0.506**, far below the
  0.849 pooled recipe-level recall Phase 0c-2 measured;
* after unique-producer protection, **52.1 %** of them are actually excluded;
* the 34 rule-junk recipes the prototype ends up executing have a *median*
  `junk_p` of **0.275** — the model does not merely miss them by a hair, it
  disagrees;
* the single most-used one (140 actions) has `junk_p = 0.988` and role
  `display`, and is executed **because it is protected as the sole producer
  of its output**.

This is HANDOVER open issue #1 measured end to end: **the junk head is not a
filter, and shipping it as the only filter costs junk-in-path.**  The Java
planner must keep the builtin rules as a second net behind the model, exactly
as that issue says.  Nothing here was tuned to hide it.

### Knob ablations (7 packs, 1,091 paired targets, non-gold metrics only)

Prototype means.  `junk_in_path_model` is *not* comparable across the τ rows
(it is measured at that row's own τ); `junk_in_path_wl` is.

| config | actions | crafts | raw_kinds | machines | depth | junk_wl | round trips | tier rev. | incomplete | raw_nonleaf |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| γ = 1.0 | 52.74 | 12837 | 7.52 | 4.52 | 6.69 | 0.034 | 0 | 0.570 | 0.000 | 1.722 |
| γ = 1.5 | 51.95 | 12778 | 7.70 | 4.44 | 6.64 | **0.024** | 0 | 0.596 | 0.000 | 1.411 |
| **γ = 2.0 (default)** | 53.80 | 12824 | 7.72 | 4.46 | 6.65 | **0.023** | 0 | 0.652 | 0.000 | 1.355 |
| γ = ∞ | 53.93 | 12903 | 7.57 | 4.22 | 6.65 | **0.021** | 0 | 0.651 | 0.001 | 1.354 |
| ranker off | 52.82 | 12837 | 7.49 | 4.51 | 6.70 | 0.035 | 0 | 0.670 | 0.000 | 1.722 |
| conversion gate off | 53.05 | 12823 | 7.71 | 4.38 | 6.65 | 0.021 | 0 | 0.652 | 0.000 | 1.355 |
| τ_junk = 0.5 | 69.15 | 22777 | 9.47 | 5.10 | 8.16 | 0.033 | 0 | **4.170** | **0.095** | 1.823 |
| τ_junk = 0.99 | 47.31 | **38971** | 7.67 | 4.59 | 6.45 | 0.032 | 0 | 0.576 | 0.000 | 1.343 |
| tier_penalty = 0 | 53.79 | 12832 | 7.72 | 4.46 | 6.65 | 0.023 | 0 | 0.654 | 0.000 | 1.357 |
| machine_cost = 0 | 53.82 | 12832 | 7.57 | 4.22 | 6.65 | 0.023 | 0 | 0.652 | 0.000 | 1.354 |

Five readings:

1. **The ranker measurably lowers junk-in-path.**  Ranker off 0.035 →
   γ = 2.0 with the ranker 0.023, a 34 % reduction, and `raw_nonleaf` falls
   1.72 → 1.36.  γ = 1.0 (which makes the ranker inert) reproduces the
   ranker-off numbers almost exactly, as it must.  This is the first
   *non-gold* evidence that the ranker earns its place.
2. **γ saturates at 1.5.**  Everything above it is within noise of γ = ∞.
   The default 2.0 is kept because it is the midpoint of the flat region.
3. **τ_junk is the dangerous knob in both directions.**  Loosening it to 0.99
   triples the craft count (12.8 k → 39.0 k): the junk routes the head no
   longer excludes are *cheap*, which is Phase 0c-2 Stage B's 0.137 result
   showing up as arithmetic.  Tightening it to 0.5 raises tier reversals 6×
   and makes 9.5 % of targets incomplete: over-exclusion forces the planner
   onto worse routes.
4. **The conversion gate changes almost nothing at corpus scale** (0.023 vs
   0.021 junk_wl, identical action counts).  Its effect is per-case, and the
   from-stock table below is where it shows.
5. **The tier-penalty and machine-footprint terms are nearly inert at their
   defaults.**  Setting either to zero moves tier reversals by 0.002 and
   machines by 0.24.  The synthetic tests prove both mechanisms work; the
   honest conclusion is that at `tier_penalty = 1.0` and
   `machine_cost = 0.25` they are small next to the input-value spread.  A
   Java port should either raise them deliberately or drop them — carrying
   them at these values buys almost nothing.

### The redstone ladder, before and after

fom `batch-001` CHAIN 0001, target `sophisticatedbackpacks:advanced_pump_upgrade`.

Baseline (`StandardPlanner`, builtin rules v2, empty stock):

```
[A1] EXECUTE minecraft:redstone_from_blasting_deepslate_redstone_ore x9 @ minecraft:blast_furnace
       in : minecraft:deepslate_redstone_ore x9
       out: minecraft:redstone x9
  [A2] EXECUTE minecraft:redstone_block x1 @ minecraft:crafting_table
         in : minecraft:redstone x9
         out: minecraft:redstone_block x1
    [A3] EXECUTE minecraft:redstone x1 @ minecraft:crafting_table
           in : minecraft:redstone_block x1
           out: minecraft:redstone x9
      [A4] EXECUTE minecraft:redstone_block x1 @ minecraft:crafting_table
             in : minecraft:redstone x9
             out: minecraft:redstone_block x1
        ... A5 .. A17: the same two recipes, alternating, nine times ...
```

Prototype:

```
[A1] EXECUTE minecraft:redstone_from_blasting_redstone_ore x3 @ minecraft:blast_furnace
       in : minecraft:redstone_ore x3
       out: minecraft:redstone x3
```

| | baseline | prototype |
|---|---:|---:|
| actions | 102 | **27** |
| distinct recipes | 39 | **19** |
| crafts | 173 | **43** |
| raw kinds | 48 | **12** |
| machines | 4 | **3** |
| depth | 39 | **5** |
| round trips | 46 | **0** |
| tier reversals | 36 | **0** |
| junk_in_path (model) | 0.510 | **0.000** |
| wall | 19 ms (Java) | 2.6 ms (Python) |

The ladder is impossible in the prototype for a structural reason, not a
heuristic one: `redstone` settles before `redstone_block` in the cost solve,
so the `block → 9 redstone` edge can never improve `redstone`, and the
settle-order admissibility rule refuses any producer whose inputs settled
after its output.  The full prototype chain text is in
`out/planner-eval/ladder-proto.txt`.

### The audit cases (gold pairs — evaluation only, never tuned on)

19 gold readings group into **13 cases**, one per (finding, output, chosen
producer); `F-technical-electrical-1` is six readings of one case and
`F-atm10-lite-1` is five readings over four cases.  A case is **won** when the
plan executes one of the producers the audit preferred, **lost** when it
executes the one the audit complained about, **neither** when it executes a
third producer.

| config | cases | preferred | chosen | neither | won | not-lost | weighted not-lost |
|---|---:|---:|---:|---:|---:|---:|---:|
| γ = 1.0 | 13 | 7 | 1 | 5 | 0.538 | 0.923 | 0.889 |
| γ = 1.5 | 13 | **8** | 1 | 4 | **0.615** | 0.923 | 0.889 |
| **γ = 2.0** | 13 | **8** | 1 | 4 | **0.615** | 0.923 | 0.889 |
| γ = ∞ | 13 | **8** | 1 | 4 | **0.615** | 0.923 | 0.889 |
| γ = 1.0, ranker off | 13 | 7 | 1 | 5 | 0.538 | 0.923 | 0.889 |
| γ = 1.5, ranker off | 13 | 7 | 1 | 5 | 0.538 | 0.923 | 0.889 |
| γ = 2.0, ranker off | 13 | 7 | 1 | 5 | 0.538 | 0.923 | 0.889 |
| γ = ∞, ranker off | 13 | 7 | 1 | 5 | 0.538 | 0.923 | 0.889 |

Per case at γ = 2.0 with the ranker:

| finding | output | verdict | decided by | executed cost | chosen cost | best preferred cost |
|---|---|---|---|---:|---:|---:|
| F-atm10-lite-1 | `minecraft:leather` | **preferred** | cost | 4.820 | 26.775 | 4.820 |
| F-atm10-lite-1 | `minecraft:pumpkin_seeds` | neither | cost | 4.928 | 23.357 | 7.597 |
| F-atm10-lite-1 | `minecraft:stick` | neither | cost | 0.910 | 26.775 | 3.372 |
| F-atm10-lite-1 | `minecraft:stick` | neither | cost | 0.910 | 16.041 | 3.372 |
| F-farmers-colonies-1 | `minecraft:blue_dye` | **preferred** | cost | 4.467 | 5.219 | 4.467 |
| F-farmers-colonies-2 | `minecraft:white_dye` | **chosen (lost)** | cost | 3.943 | 3.943 | 4.467 |
| F-lostworld-1 | `minecraft:mossy_cobblestone` | **preferred** | cost | 19.068 | 19.111 | 19.068 |
| F-lostworld-2 | `block_variants:polished_granite_wall` | neither | ranker | 36.854 | 36.492 | 35.335 |
| F-lostworld-2 | `minecraft:granite_wall` | **preferred** | cost | 25.516 | 35.335 | 25.516 |
| F-lostworld-3 | `minecraft:blue_dye` | **preferred** | cost | 4.280 | 5.440 | 4.280 |
| F-mythcrafts-1 | `sophisticatedstorage:advanced_filter_upgrade` | **preferred** | cost | 280.613 | 624.953 | 280.613 |
| F-technical-electrical-1 | `minecraft:red_dye` | **preferred** | cost | 1.480 | 1.900 | 1.480 |
| F-tfc-enhanced-1 | `minecraft:blue_dye` | **preferred** | **ranker** | 2.278 | 3.483 | 2.278 |

**Won by cost alone: 7.**  leather, farmers blue_dye, mossy_cobblestone,
granite_wall, lost-world blue_dye, mythcrafts filter upgrade, and — notably —
`F-technical-electrical-1` red_dye, which Phase 0c-2 Stage C lost under both
leaf seeds by a consistent ~40 %.  It flips here because the planner's cost
function is not Phase 0b's: `steps × (1 + tier)` is charged per *routed* step
and the reliability weight applies to the whole subtree, which moves the
1.900 producer above the 1.480 one.

**Won by the ranker: 1.**  `F-tfc-enhanced-1` blue_dye.  With the ranker off
the cost solve picks a third producer at unit cost 1.908 (producer score
−2.862); with the ranker and γ ≥ 1.5 the shortlist contains the audit's
preferred producer at cost 2.278 and score **+3.311**, and it wins.  This is
the entire measurable contribution of the ranker on the gold set — one case
of thirteen — and it needs γ ≥ 1.5 to be reachable at all.

**Lost: 1.**  `F-farmers-colonies-2` `white_dye`.  The bone-meal route costs
3.943 against the flower's 4.467, a 13 % gap, and the producer score agrees
with the cost (+0.262 vs −0.262).  This is the *same* case Phase 0b lost by
6.9 % and Phase 0c-2 Stage C could not move by re-seeding leaves; the
planner's step and machine terms widen it rather than closing it.  Nothing
here fixes it, and nothing was tuned to try: it needs leaf-level labelling,
as REPORT Phase 0b already concluded.

**Neither: 4.**  Three of them (`stick` ×2, `pumpkin_seeds`) are atm10-lite
cases where the prototype routes through a *cheaper third* producer (0.910
against the preferred 3.372) — the audit compared two producers, the planner
found a better one, and the gold format cannot express that.  The fourth,
`F-lostworld-2` `polished_granite_wall`, is a genuine miss: the ranker picks a
producer at 36.854 when the audit's preferred is at 35.335.

**From-stock re-solve** (the chosen producer's inputs placed in stock, which
is what the conversion gate exists for):

| finding | output | verdict with stock |
|---|---|---|
| F-atm10-lite-1 | `minecraft:leather` | preferred |
| F-atm10-lite-1 | `minecraft:pumpkin_seeds` | **chosen** |
| F-atm10-lite-1 | `minecraft:stick` ×2 | neither |
| F-farmers-colonies-1 | `minecraft:blue_dye` | preferred |
| F-farmers-colonies-2 | `minecraft:white_dye` | chosen |
| F-lostworld-1 | `minecraft:mossy_cobblestone` | preferred |
| F-lostworld-2 | `block_variants:polished_granite_wall` | **chosen** |
| F-lostworld-2 | `minecraft:granite_wall` | preferred |
| F-lostworld-3 | `minecraft:blue_dye` | **chosen** |
| F-mythcrafts-1 | `sophisticatedstorage:advanced_filter_upgrade` | **chosen** |
| F-technical-electrical-1 | `minecraft:red_dye` | preferred |
| F-tfc-enhanced-1 | `minecraft:blue_dye` | **chosen** |

Six of thirteen flip to the producer whose input the player holds, with no
re-pricing anywhere — the intended behaviour: *a person converts what they
already have*.  The other seven keep their empty-stock choice because the
held input does not actually shorten their route.

### Three bugs the invariants found

Recorded because each one is a trap a Java port will hit in the same place.

1. **The net-loss guard was evaluated against tag *membership*.**  A
   stonecutting recipe whose input is a tag whose only member is its own
   output (Phase 0b role `noop`) passed the guard and the planner recursed
   into itself; and *every* recolour recipe failed it, because its input tag
   contains the output among a dozen siblings.  Both vanish once the guard is
   evaluated against the **resolved** any-of member, the same way extraction
   resolves it.
2. **The display role has to sit inside the unique-producer protection.**
   With it as a separate gate, the only producer of a recoloured item — which
   the role head calls `display` — was rejected after being protected, and
   the plan came back `UNRESOLVED` with `candidates=1`.
3. **The ledger exception to the acyclicity rule could re-open a reverse
   edge.**  A conversion firing off a *byproduct this plan had just made*
   produced a real `A → B`, `B → A` pair on atm10-lite
   (`bhc:red_heart_melted` ↔ `bhc:yellow_heart_melted`).  The fix is a
   per-request set of conversion edges the plan has already taken; the
   round-trip assertion then holds on all 3,301 targets.
   A fourth, smaller one: the round-trip *detector* itself over-counted, because
   a single recipe consuming and producing two members of one family emits
   both `(x,y)` and `(y,x)` on its own.  Edges are now compared across
   actions only.

### An upstream defect found (not fixed here)

`out/models/recipe_leaf_agg.parquet` keys its `pack` column on the **pack
name** (`packs.json`'s value), while `out/features/*.parquet` key on the
**slug**.  They coincide for 17 of the 22 packs, so nothing upstream tripped.
For the other five, `train_eval.recipe_matrix`'s
`rf.merge(agg, on=["pack", "recipe_idx"], how="left", validate="one_to_one")`
matches nothing and silently fills the 22 leaf-aggregate columns with NaN —
which then become the training fold's median under the NaN policy.  Those
five packs are ~11 % of the training rows.  This layer resolves both
spellings (`planner.pack_rows`) and touches nothing upstream, per the brief.
**Re-running Phase 0c-2 with the key fixed would change the exported ranker**,
including the Stage C leaf weights, so the Java port should decide whether to
re-train before porting.

### Invariants — `tests/test_phase3.py`, 45 checks

Synthetic, vocabulary-free graphs: no storage ladder in a three-member pool
fed by ore (and 81 leaf units for the top of the ladder, not more);
conversion only from stock; the amplification guard; the net-loss loop guard;
unique-producer protection; junk exclusion with an alternative present
(including when the junk route is 4× cheaper); the ranker overriding inside
the γ shortlist and *not* outside it; the tier penalty; `craftTimes` exact at
2³¹ + 17; the expected-output hook (chance 0.5 doubles the crafts); byproduct
credit; budget → `incomplete` with a partial plan; determinism.

Real (fom): 200 sampled targets, every plan **replays** forward from its own
raw list (the prototype's `RoutingPlanReverseVerifier`); zero round trips;
zero pool amplification; no excluded producer executed; every craft count a
positive `int64`; median wall 0.17 ms; chain text survives emit → parse →
measure with identical metrics; two identical solves are byte-identical.
And the producer score equals the row means of the **exact** pairwise margin
matrix built through `predict_reference.Reference.margin` — max |Δ| =
**1.8e-14** over 83 groups.

Two full A/B runs over fom and lost-world produce identical metric tables.

### Deviations from the brief, and why

1. **`conversion` is demoted, not prohibited.**  The brief's §2 says a
   conversion edge is usable only when its input is in stock; taken literally,
   no storage block is craftable from empty stock, since "9 units → 1 block"
   *is* a conversion.  The brief's own §3 gives the resolution, and the
   implementation follows it: a conversion may serve a demand nothing else
   can, but never outranks a production route unless the ledger holds its
   input.  `--conversion-needs-stock 0` ablates it (corpus effect: ~nil).
2. **The machine footprint is greedy during the solve and exact during
   extraction**, as the brief allows — but the greedy per-solve set turns out
   to pay for almost every machine early (atm10: 612 of 654 machines paid),
   so the solve-time term is close to a constant.  The exact per-plan term in
   the form layer is what actually discriminates.  Measured effect at the
   default `machine_cost`: ~nil (see the ablation).
3. **The pinned regression chain is selected by data, not by name.**  Writing
   `fom / batch-001 / CHAIN 0001` into the code would put a pack name in
   executable text.  Instead each pack pins the chain with the most `A → B`
   / `B → A` item edges found by a cheap pre-scan (for fom that is
   `create:netherite_backtank`, 186 ladder edges), and `--pin
   <slug>:<batch>:<chain>` adds any chain explicitly; `run_phase3.sh` is where
   a named chain belongs.  CHAIN 0001 is reported above in full.
4. **Targets come from `index.tsv`, not `targets.tsv`.**  `targets.tsv` holds
   750 rows per pack but only the 8,752 that actually produced a chain are
   in `index.tsv` (fom: 409 of 750; the rest were dropped by the exporter's
   structure dedup).  A target with no baseline chain has nothing to pair
   against.
5. **The ranker's tree arm is not implemented.**  The O(n) Bradley–Terry
   decomposition is exact only for the linear arm, which is the manifest's
   default; asking for `--arm-rank lgb` raises rather than silently
   approximating.  The junk and role heads use whichever arm the manifest
   names (LightGBM), and `--arm-junk` / `--arm-role` switch them.
6. **The knob ablation runs on 7 packs, not 22** (1,091 paired targets).  Ten
   configurations × 22 packs would be ~16 min of wall for a table whose
   conclusions are already flat; the seven include every pack that carries a
   gold pair.
7. **The memo is one dense array per (policy, knobs, stock) rather than a
   dict keyed by item.**  Same contract, one array index instead of a hash —
   and it is what lets one solve serve 150 targets in 94 s over 22 packs.

### Open questions

1. **The junk head costs junk-in-path and the fix is not in this layer.**
   Recall 0.506 on atm10-lite at the P ≥ 0.95 point, against 0.849 pooled.
   Either the operating point is chosen per pack (there is no held-out data
   to choose it *from* on the twelve packs with no gold labels), or the Java
   planner keeps the builtin rules behind the model, which is what HANDOVER
   already recommends.
2. **atm10 is 15 % `BUDGET_TRUNCATED`**, all of it at `budget_base = 512`
   plus a cone raise.  The cone walk is capped at 200 000 producers; on atm10
   the cone of a deep target is larger than that, so the raise saturates.
   Either the budget needs a different shape on packs with 283 k recipes, or
   those targets genuinely need the `StandardPlanner` fallback.
3. **The tier-penalty and machine-footprint terms do not earn their defaults.**
   Zeroing either moves nothing at corpus scale.  They are correct
   mechanisms (the synthetic tests pin them down) with the wrong constants,
   and there is no labelled signal here to fit the constants against.
4. **Four gold cases end in "neither" and three of those are arguably wins.**
   The prototype routes `stick` at 0.910 where the audit's preferred producer
   costs 3.372.  The gold format records a *pair*, so a planner that finds a
   third, better producer scores the same as one that keeps the bad route.
   A future audit round should record the *set* of acceptable producers.
5. **`raw_nonleaf` is 1.43 per plan and it should be 0.**  Those are items
   Phase 0b can price but this layer's candidate policy orphaned — 1,012 of
   atm10-lite's 9,823 items, against 248 with the policy off.  Every one is a
   route the junk head removed and nothing replaced.
6. **The upstream `pack` key defect above changes the trained ranker.**
   Whether it changes it *materially* is one re-run of `run_phase0c2.sh` away
   and was out of scope here.

---

## Phase 4 — the feedback layer (Python prototype)

`feedback.py` (the layer), `tests/test_phase4.py` (49 checks), `run_phase4.sh`.
Design in DESIGN.md "Phase 4".  Phases 0a–0c-2 were not modified;
`planner.py` gained the additive `PlanOptions` seam of DESIGN §5 and nothing
else, and `tests/test_phase0a.py` gained one module name so its no-vocabulary
check now covers 13 modules.

### The promise first

Before a line of `planner.py` was touched, 50 fom targets were planned and
their `plan_json` canonicalised and hashed:
`tests/fixtures/phase3-plans-fom.json.gz`, sha256
**`adb9c06a372e7726d18667100a26695e91139bfde4260ebc2641165372760e0c`**.  After
the hook, both `opt=None` and a default `PlanOptions()` reproduce that hash
exactly, and `tests/test_phase3.py` still passes all 45 checks.  A default
`PlanOptions` also writes no `options` block into the plan JSON, which is why
the new fields live outside `Knobs` (`Knobs` is serialised into every plan).

### Timings

One `./run_phase4.sh`, warm caches (`out/planner-eval/phase4-run.log`).

| stage | wall | packs |
|---|---:|---|
| family generalisation, 3 families each | **4.0 s** | fom + 6 audit packs (21 families) |
| audit cases, one click each | **2.5 s** | 6 gold-bearing packs |
| learning curve, 19 readings × 5 orders + 19 leave-one-out | **40.2 s** | 6 gold-bearing packs, 360 targets |
| inquiry precision, 150 targets each | **34.8 s** | the 5 label-heaviest packs |
| per-call latency, 200 calls per method | **25.4 s** | atm10, minecolonies, fom |
| persistence round trip | 2.3 s | fom + 6 audit packs |
| `tests/test_phase4.py` (49 checks) | 1.2 s | — |
| **total** | **1 min 51 s** | budget: 20 min |

### Family generalisation — one ban, one family

One `ban(recipe_idx=…)` on **one** member of each pack's three largest
templates.  "siblings>τ" counts members whose *posterior* crosses
`τ_junk = 0.8707`; "excluded" is what the candidate policy actually removes;
"nb" is how many of the neighbourhood's recipes were newly excluded as
collateral.

| pack | template | n | junk before → after | siblings>τ | excluded before → after | nb collateral | plans using it, still using it | with a template-scope ban | ms |
|---|---|---:|---|---:|---|---|---|---|---:|
| fom | `tpl:7b62e00b9ea8` | 78 | 0.5400 → 0.9425 | **78/78** | 0 → **78** | +31/142 | 5 → **0** | 0, 0 incomplete | 0.18 |
| fom | `tpl:b13d4ed12c74` | 48 | 0.8736 → 0.9842 | **48/48** | 33 → 34 | +2/8 | 10 → **10** | **0**, 0 incomplete | 0.08 |
| fom | `tpl:0536f32bf375` | 32 | 0.4383 → 0.9298 | **32/32** | 0 → **32** | +0/68 | 0 → 0 | 0 | 0.07 |
| farmers-colonies | `tpl:8c757a04321d` | 102 | 0.9878 → 0.9985 | 102/102 | 102 → 102 | +0/225 | 0 → 0 | 0 | 0.14 |
| farmers-colonies | `tpl:58d4eccf3144` | 78 | 0.9851 → 0.9981 | 78/78 | 78 → 78 | +0/120 | 0 → 0 | 0 | 0.04 |
| farmers-colonies | `tpl:b9c720fb3677` | 72 | 0.9853 → 0.9982 | 72/72 | 72 → 72 | +0/251 | 0 → 0 | 0 | 0.04 |
| tfc-enhanced | `tpl:949f1b3273f5` | 84 | 0.2435 → 0.9054 | **84/84** | 0 → **1** | +0/259 | 4 → **4** | **0**, 0 incomplete | 0.06 |
| tfc-enhanced | `tpl:880ed537b1e5` | 84 | 0.1122 → 0.8890 | **84/84** | 0 → **1** | +0/240 | 3 → **3** | **0**, 0 incomplete | 0.04 |
| tfc-enhanced | `tpl:1d18c29c3af3` | 84 | 0.1122 → 0.8890 | **84/84** | 0 → **1** | +0/240 | 4 → **4** | **0**, 0 incomplete | 0.05 |
| technical-electrical | `tpl:77b989ec1b10` | 48 | 0.9114 → 0.9889 | 48/48 | 36 → 36 | +2/43 | 1 → **1** | **0**, 0 incomplete | 0.30 |
| technical-electrical | `tpl:b5a88217573f` | 36 | 0.2609 → 0.9076 | **36/36** | 0 → **36** | +0/119 | 2 → **0** | 0 | 0.17 |
| technical-electrical | `tpl:23d8228f9cc1` | 36 | 0.2234 → 0.9029 | **36/36** | 0 → **36** | +0/120 | 0 → 0 | 0 | 0.17 |
| lost-world | `tpl:d65c6ae3aafc` | 98 | 0.9945 → 0.9993 | 98/98 | 98 → 98 | +0/113 | 0 → 0 | 0 | 0.09 |
| lost-world | `tpl:8322fabe3189` | 80 | 0.9951 → 0.9994 | 80/80 | 80 → 80 | +0/183 | 0 → 0 | 0 | 0.06 |
| lost-world | `tpl:d5f29b9e1ef3` | 64 | 0.7750 → 0.9719 | **64/64** | 0 → **64** | **+45/46** | 5 → **0** | 0 | 0.06 |
| mythcrafts | `tpl:cc13ddd78a0e` | 1369 | 0.9987 → 0.9998 | 1369/1369 | 1369 → 1369 | +5/2981 | 0 → 0 | 0 | 0.27 |
| mythcrafts | `tpl:9ea3b47c3054` | 980 | 0.9988 → 0.9999 | 980/980 | 980 → 980 | +5/3518 | 0 → 0 | 0 | 0.19 |
| mythcrafts | `tpl:bd9b9d5d53a4` | 705 | 0.9986 → 0.9998 | 705/705 | 691 → 705 | +3/3807 | 0 → 0 | 0 | 0.17 |
| atm10-lite | `tpl:8d871d993956` | 468 | 0.9966 → 0.9996 | 468/468 | 468 → 468 | +0/919 | 0 → 0 | 0 | 0.51 |
| atm10-lite | `tpl:070caaa8dd13` | 312 | 0.9967 → 0.9996 | 312/312 | 312 → 312 | +0/615 | 0 → 0 | 0 | 0.19 |
| atm10-lite | `tpl:3a4b0da2adf1` | 288 | 0.9972 → 0.9997 | 288/288 | 288 → 288 | +0/640 | 0 → 0 | 0 | 0.22 |

**21 families of 21, every sibling's posterior crosses the threshold from one
click** — 5,146 recipes reached by 21 clicks.  That part is by construction:
the evidence is keyed by `template_id`, and `BAN_FACTOR = 7` guarantees
`junk' ≥ 0.875 > τ_junk` whatever the prior.

**Two things the posterior alone does not do, and they matter.**

1. **The sole-producer protection outranks a posterior.**  fom's 48-member
   recolour family and tfc-enhanced's three 84-member families are each the
   *only* producer of their own output, so Phase 3's unique-producer
   protection rescues 14 of 48 and 83 of 84 — and every plan that used the
   family still uses it.  The fix is the inquiry's **second answer**: a
   `ban(template_id=…)` is a statement, not a posterior, and the protection
   may not rescue it.  With it, **0 of 10 / 0 of 4 / 0 of 3 / 0 of 4 plans
   still use the family and none of them becomes incomplete** — the planner
   found another route every time.
2. **Neighbourhood collateral is real, bounded, and worth watching.**  Of the
   21 clicks, 14 excluded nothing at all outside the family; six excluded
   2–31 recipes; one (lost-world `tpl:d5f29b9e1ef3`) excluded 45 of its
   neighbourhood's 46.  The bound in DESIGN §3 held everywhere: no template
   with `junk_p ≤ 0.42` was ever excluded by a neighbour's click.

### The lost audit case — `F-farmers-colonies-2`

One implicit `choose(preferred, over=chosen)` on the one case the Phase 3
planner lost.  The pair row reproduces `predict_reference.Reference.margin`
to 4.4e-16, so the click is measured in the ranker's own units.

```
click  231 over 79:  margin -0.5011 -> +1.9898   (gain 2.4909 = ETA * sigmoid(0.5011))
                     |delta| 0.0693, cap 1.5829, not clipped
```

All 13 audit cases, before, after that one click in its own pack, and after
the same clicks with the delta shared across packs.  `margin` is
`S(best preferred) − S(chosen)`: positive means the ranker agrees with the
audit.

| finding | output | before | after | shared | margin b | margin a | margin s |
|---|---|---|---|---|---:|---:|---:|
| F-atm10-lite-1 | `minecraft:leather` | preferred | preferred | preferred | +3.828 | +3.828 | +5.584 |
| F-atm10-lite-1 | `minecraft:pumpkin_seeds` | neither | neither | neither | +0.423 | +0.423 | +0.472 |
| F-atm10-lite-1 | `minecraft:stick` (13110) | neither | neither | neither | −0.784 | −0.784 | −0.714 |
| F-atm10-lite-1 | `minecraft:stick` (13150) | neither | neither | neither | −0.199 | −0.199 | **+0.069** |
| F-farmers-colonies-1 | `minecraft:blue_dye` | preferred | preferred | preferred | +0.389 | **+4.806** | +4.806 |
| **F-farmers-colonies-2** | `minecraft:white_dye` | **chosen** | **preferred** | **preferred** | −1.002 | **+3.600** | +3.600 |
| F-lostworld-1 | `minecraft:mossy_cobblestone` | preferred | preferred | preferred | +2.199 | +2.199 | +2.958 |
| F-lostworld-2 | `block_variants:polished_granite_wall` | neither | neither | neither | +3.759 | +3.759 | +3.596 |
| F-lostworld-2 | `minecraft:granite_wall` | preferred | preferred | preferred | −6.234 | −6.234 | −6.224 |
| F-lostworld-3 | `minecraft:blue_dye` | preferred | preferred | preferred | +0.686 | +0.686 | **+3.759** |
| F-mythcrafts-1 | `sophisticatedstorage:advanced_filter_upgrade` | preferred | preferred | preferred | −0.808 | −0.808 | −0.922 |
| F-technical-electrical-1 | `minecraft:red_dye` | preferred | preferred | preferred | +0.728 | +0.728 | **+2.481** |
| F-tfc-enhanced-1 | `minecraft:blue_dye` | preferred | preferred | preferred | +3.276 | +3.276 | **+5.848** |
| **totals** | | **8 / 1 / 4** | **9 / 0 / 4** | **9 / 0 / 4** | | | |

**Yes — one click executes the preferred producer**, and nothing else
regresses, in either variant.  The generalisation is visible in the margins
rather than in the verdicts, because twelve of thirteen cases were already
won:

* **Same pack, different case.**  The `white_dye` click alone raises
  `farmers-colonies` `blue_dye`'s margin from +0.389 to +4.806.  The user
  never clicked on `blue_dye`; the feature direction carried.
* **Across packs (shared delta).**  Every remaining case moves in the audit's
  direction except two that stay won: tfc-enhanced `blue_dye` +3.276 →
  +5.848, lost-world `blue_dye` +0.686 → +3.759, technical-electrical
  `red_dye` +0.728 → +2.481, atm10-lite `leather` +3.828 → +5.584, and one
  `stick` reading crosses zero (−0.199 → +0.069) without changing its
  verdict.  `granite_wall` (−6.234 → −6.224) and `mythcrafts` (−0.808 →
  −0.922) drift slightly the wrong way and stay `preferred` — they are cases
  the *cost* wins, not the ranker.

Per-pack and shared give the same verdict table here; they differ only in the
margins.  The shared variant is the interesting one for a Java port because
the delta is 188 floats that apply to every pack at once.

### The minimum-annotation learning curve

The 19 round-006 readings are replayed as `choose` clicks, one at a time, in
5 random orders (seeds 20260920…24).  After every click the affected pack's
candidate policy, cost solve, 13 audit cases and 60-target prototype metrics
are recomputed; the other packs keep their last value.

> **The same 19 readings are both the feedback stream and the score.**  This
> table measures how fast the layer *absorbs* a known set of corrections, not
> how well it predicts unseen ones.  The leave-one-out table below is the
> held-out counterpart, and it is the one to quote.

| clicks | cases won | (min–max) | lost | `junk_in_path_wl` | `junk_in_path_model` | incomplete | actions |
|---:|---:|---|---:|---:|---:|---:|---:|
| 0 | 8.00 | 8–8 | 1.00 | 0.0219 | 0.0118 | 0.0000 | 54.74 |
| 1 | 8.00 | 8–8 | 1.00 | 0.0238 | 0.0140 | 0.0000 | 53.47 |
| 2 | 8.20 | 8–9 | 0.80 | 0.0238 | 0.0140 | 0.0000 | 53.47 |
| 3 | 8.20 | 8–9 | 0.80 | 0.0238 | 0.0140 | 0.0000 | 53.47 |
| 4 | 8.40 | 8–9 | 0.60 | 0.0247 | 0.0153 | 0.0000 | 52.67 |
| 5 | **8.80** | 8–9 | 0.20 | 0.0247 | 0.0153 | 0.0000 | 52.66 |
| 6 | 8.80 | 8–9 | 0.20 | 0.0247 | 0.0153 | 0.0000 | 52.66 |
| 7 | 8.80 | 8–9 | 0.20 | 0.0239 | 0.0145 | 0.0000 | 52.73 |
| 8 | 8.80 | 8–9 | 0.20 | 0.0248 | 0.0153 | 0.0000 | 52.57 |
| 9 | 8.80 | 8–9 | 0.20 | 0.0248 | 0.0153 | 0.0000 | 52.58 |
| 10 | 8.80 | 8–9 | 0.20 | 0.0256 | 0.0161 | 0.0000 | 52.51 |
| 11 | 8.80 | 8–9 | 0.20 | 0.0256 | 0.0161 | 0.0000 | 52.58 |
| 12 | 8.80 | 8–9 | 0.20 | 0.0256 | 0.0161 | 0.0000 | 52.58 |
| **13** | **9.00** | 9–9 | **0.00** | 0.0256 | 0.0161 | 0.0000 | 52.58 |
| 14–18 | 9.00 | 9–9 | 0.00 | 0.0256 | 0.0161 | 0.0000 | 52.56 |
| 19 | 9.00 | 9–9 | 0.00 | **0.0255** | 0.0161 | 0.0000 | **52.63** |

Readings: **the one lost case is fixed after a median of 5 clicks and in
every order by 13**, out of 19; `incomplete` never moves; mean actions falls
2 % (54.74 → 52.63).  **`junk_in_path_wl` rises 16 %, 0.0219 → 0.0255** — the
honest cost.  The clicks pull the ranker towards producers the round-006
audit preferred, and the rule replay (builtin rules v3) calls some of those
producers junk.  This is the *same* disagreement Phase 3 measured between the
junk head and the rule replay, now amplified by the feedback: the two label
sources do not agree about these routes, and the feedback layer follows the
human audit.  Nothing here was tuned to hide it.

**Leave-one-out** — all 18 other readings clicked, the held-out one scored.
This *is* a held-out measurement.

| configuration | preferred | chosen | neither | unplanned |
|---|---:|---:|---:|---:|
| Phase 3 default | 14 | **1** | 4 | 0 |
| 18 other clicks, own pack only | **15** | **0** | 4 | 0 |
| 18 other clicks, delta shared | 14 | **0** | **5** | 0 |

(19 readings, not 13 cases — one case can carry several readings.)

* **The lost `white_dye` reading is fixed by clicks it never saw**, in both
  variants.  Within `farmers-colonies` the only other reading is `blue_dye`;
  that one click alone flips `white_dye`.  That is the clearest evidence in
  this layer that the ranker delta generalises rather than memorises.
* **The shared delta costs one case**: `F-lostworld-1 mossy_cobblestone`
  goes `preferred → neither` (it routes through a third producer).  Sharing
  buys transfer and pays for it in specificity; reported, not hidden.

### Inquiry precision

On the five packs with the most labelled templates, after planning each
pack's 150 A/B targets: the top-20 questions, against the *population* of
templates those plans touched.  Labels are read only to score the questions;
they never reach the learner, the planner or the inquiry score.

| pack | targets | templates touched | asked | families | labelled (asked) | labelled (pop.) | disagree (asked) | disagree (pop.) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| atm10 | 150 | 1,996 | 20 | 20 | 0.050 | 0.069 | **1.000** | 0.153 |
| enigmatica10 | 150 | 692 | 20 | 20 | 0.000 | 0.028 | — | 0.053 |
| create-train | 150 | 787 | 20 | 20 | 0.100 | 0.053 | **1.000** | 0.286 |
| cobblemon-x-create | 150 | 724 | 20 | 20 | 0.300 | 0.175 | **0.667** | 0.110 |
| atm10-lite | 150 | 753 | 20 | 20 | 0.050 | 0.084 | **1.000** | 0.175 |
| **pooled** | | **4,952** | **100** | | **0.100** | 0.078 | **0.800** | 0.152 |

* **Coverage is barely better than chance**: 10.0 % of the questioned
  templates carry a label against 7.8 % of the population.  The inquiry is
  not finding labelled templates, and it was never asked to.
* **Worth-asking is 5.3× better than chance**: of the questioned templates
  that *do* carry a label, **80 %** have a label that contradicts the current
  belief, against 15.2 % in the population.  8 of the 10 labelled questions
  were about templates the model has wrong.  That is the number the
  active-learning claim rests on.
* One question per family, 20 families per pack, every time.
* The small denominator (10 labelled questions pooled) is the weakness: the
  80 % has a wide interval.  It is what the 10 packs' label coverage allows.

### Latency

200 calls per method, per-call wall, on the two deepest packs and fom.

| call | atm10 median | p95 | minecolonies median | p95 | fom median | p95 |
|---|---:|---:|---:|---:|---:|---:|
| `have` | 0.002 | 0.012 | 0.001 | 0.001 | 0.001 | 0.001 |
| `ban(item)` | 0.001 | 0.001 | 0.001 | 0.001 | 0.001 | 0.001 |
| `unban(item)` | 0.001 | 0.001 | 0.001 | 0.001 | 0.001 | 0.001 |
| `ban(recipe)` | **2.051** | 2.478 | 0.234 | 0.271 | 0.030 | 0.035 |
| `ban(template)` | **2.061** | 2.521 | 0.234 | 0.278 | 0.029 | 0.035 |
| `prefer(item)` | 0.001 | 0.001 | 0.001 | 0.001 | 0.001 | 0.001 |
| `prefer(recipe)` | **1.996** | 2.451 | 0.281 | 0.545 | 0.044 | 0.069 |
| `choose` | 0.023 | 0.043 | 0.015 | 0.020 | 0.012 | 0.013 |
| `reset` | 0.001 | 0.001 | 0.001 | 0.001 | 0.001 | 0.001 |
| re-score one output group | 0.027 | 0.055 | 0.034 | 0.074 | 0.024 | 0.029 |
| **view rebuild** (10 template bans) | 14.593 | 23.435 | 4.354 | 4.633 | 0.662 | 0.761 |
| **view rebuild** (delta only) | 0.069 | 0.083 | 0.052 | 0.057 | 0.050 | 0.054 |
| *[not an update]* rebuild candidate policy | 127.9 | 130.1 | 57.2 | 57.9 | 3.48 | 3.49 |
| *[not an update]* re-run the cost solve | 619.0 | 640.4 | 320.6 | 346.5 | 5.60 | 5.88 |

Pack sizes: atm10 283,422 recipes / 96,393 templates, minecolonies 71,899 /
15,562, fom 3,442 / 1,769.  Mean producers per re-scored output group: 5.5,
5.5, 2.6 (max 46, 25, 25).

**Every API call is under 5 ms on the largest pack** (worst: `prefer(recipe)`
p95 3.68 ms, which is one evidence update plus one ranker step per sibling).
The dominant term in `ban` is the k-NN: one 96,393 × 129 matrix–vector
product.  Re-scoring one output's producers is 0.038 ms.

Two honest caveats:

* **The view rebuild is 14.6 ms median (23.4 ms p95) on atm10** when
  template evidence exists,
  because the prototype materialises `junk_p` and `role_p` as per-*recipe*
  arrays (283,422 × 6 float64 = 13.6 MB to copy).  It is once per state
  change, not once per call, and with only delta weights it is 0.07 ms.  In
  Java the posteriors live per *template* (96,393 rows, 3× smaller) and the
  copy can be a copy-on-write overlay; this is a prototype artefact, not a
  design cost.
* **Acting on a click is not free.**  Rebuilding the candidate policy
  (128 ms) and re-running the cost solve (619 ms) on atm10 is what the *next*
  request pays.  §Phase 5's memo makes that once per state change rather than
  once per request, which is exactly the right shape: a click invalidates the
  memo, the next request pays for one solve, every later one is free.

### Persistence

All seven packs that carry an audit reading, plus fom.  Each store holds one
`have`, one `ban(recipe)`, one `prefer(item)` and one `choose`.

| pack | bytes | round-trip identical | view signature identical | merge adds counts | stale `data_hash` refused |
|---|---:|---|---|---|---|
| fom | 3,404 | yes | yes | yes | yes |
| farmers-colonies | 3,521 | yes | yes | yes | yes |
| tfc-enhanced | 3,780 | yes | yes | yes | yes |
| technical-electrical | 3,760 | yes | yes | yes | yes |
| lost-world | 3,707 | yes | yes | yes | yes |
| mythcrafts | 3,665 | yes | yes | yes | yes |
| atm10-lite | 3,706 | yes | yes | yes | yes |

The 188 rounded delta floats are ~85 % of the file; a session with hundreds of
bans adds ~60 bytes per template.

### Invariants — `tests/test_phase4.py`, 49 checks

The byte-identity of the 50-target snapshot under `opt=None` and under a
default `PlanOptions`; synthetic graphs for each gesture (stock served,
recipe ban rerouted, template ban absolute, item ban kills every consumer,
item preference discounts by exactly `ITEM_DISCOUNT` and flips the route,
recipe preference un-flags an excluded producer and halves its posterior,
`choose` moves the margin by exactly `ETA · σ(−m)` and re-selects,
`choose` refuses two producers of different outputs, `reset` clears
everything); the spread bounds (≤ `NEIGHBOURS` neighbours, ≤ `SPREAD_PER`
each, ≤ `SPREAD_TOTAL` in total, and the derived posterior bound); the delta
bound under 500 contradictory clicks; on fom — the lazy re-score equals a
full recompute (max |Δ| **2.3e-14** over 3,442 recipes), the pair vector
reproduces `predict_reference.margin` (4.4e-16), one ban carries to all 78
siblings and removes the whole family, the store round-trips and refuses a
stale hash, the inquiry emits one question per family and never repeats one,
every update is median < 5 ms, and two identical sessions produce the same
state signature and the same plan byte for byte.

### Deviations from the brief, and why

1. **An explicit ban overrides the sole-producer protection.**  The brief
   inherits Phase 2's gate "唯一生产者永不被排除".  That gate is a guard
   against the *model*; a user ban is a statement.  When every producer of an
   item is banned the demand falls through Phase 3's existing orphan rule to
   `[RAW]`.  Measured consequence: the template-scope ban is what actually
   removes fom's recolour family from plans (the recipe-scope ban does not).
2. **The ranker delta is stored in the exported `coef` space, not in
   `w0 = coef/scale` space.**  Same model, but it makes `|Δw| ≤ BOUND·|coef|`
   a statement about the exported file rather than about a derived quantity.
3. **`prefer(recipe)` is `choose` against every sibling at a quarter step**,
   as the brief says — with the consequence that its cost is O(siblings).  On
   atm10's largest output (3,746 producers) that is the 3.7 ms p95 in the
   latency table; a Java port should cap the sibling count or run it
   asynchronously.
4. **The role evidence is a quarter the weight of the junk evidence**
   (`ROLE_EVIDENCE_FACTOR·n0 = 4` against `BAN_FACTOR·n0 = 28`).  With equal
   weights, a neighbour receiving half the spread would cross
   `τ_display = 0.5` regardless of what the model believed, which would make
   the neighbourhood rule a display-role hammer.  Stated as a constant, not
   discovered by tuning.
5. **The learning curve re-measures only the affected pack after each click.**
   In the per-pack variant a click cannot reach another pack, so the other
   packs' numbers are unchanged by construction; this is bookkeeping, not an
   approximation.  The shared-delta variant is reported separately.
6. **`INQUIRY_MARGIN` is `γ`, not a new constant.**  "Considered with a small
   margin" already has a definition in Phase 3 — the shortlist — and reusing
   it means the inquiry and the planner disagree about nothing.
7. **The evaluation-side pack lookup was tightened.**  `common.match_name`
   falls back to a unique *prefix* match, which resolves `atm10-lite`'s
   readings onto `atm10` when `atm10-lite` is not in the candidate list.  The
   feedback harness requires exact normalised equality (`_slug_of`).  No
   Phase 3 number is affected (the Phase 3 audit always passed all 22 slugs).

### Open questions from Phase 4

1. **The feedback raises `junk_in_path_wl` by 16 %.**  Following the human
   audit moves the planner towards routes the rule replay calls junk.  Only
   one of the two sources can be right; neither is checkable from here.  The
   Java port keeping the builtin rules as a second net (HANDOVER issue #1)
   would *suppress* the effect without resolving the disagreement.
2. **Inquiry coverage is chance-level (10.0 % vs 7.8 %).**  The questions are
   well chosen among the templates that carry labels (80 % disagreement), but
   the ranking does not preferentially find labelled templates.  With 10
   labelled questions pooled, the interval on that 80 % is wide.  A larger
   label set, or an inquiry scored against a held-out audit round rather than
   against the training labels, would settle it.
3. **One click can exclude 45 of a neighbourhood's 46 recipes.**  The bound
   holds (`p > 0.42` was required in every case), but a user who bans one
   recipe may not expect a whole neighbouring family to go.  A UI that shows
   the spread before committing — the `touched` list `ban()` already returns
   — is the mitigation; a smaller `SPREAD_PER` is the alternative and costs
   generalisation.
4. **The shared delta costs one audit case** (`mossy_cobblestone`
   `preferred → neither` under leave-one-out).  Per-pack deltas do not.
   Whether a Java runtime should share the delta across packs is a product
   decision; the store supports both and `merge` is the switch.
5. **Template-level evidence is cold-start on a new pack** (HANDOVER issue 5:
   only 130 templates appear in ≥ 2 pack groups).  The ranker delta is not —
   it is a statement about the feature space.  So on an unseen pack the
   `choose` gesture generalises and the `ban` gesture does not, which is the
   opposite of what a user would guess.
6. **The view rebuild is per-recipe where it should be per-template.**
   14.6 ms median on atm10, and it is pure prototype shape.

---

## Phase 5 — the deep-cone benchmark and the caching study (Python prototype)

`bench_planner.py` (five stages), `tests/test_phase5.py` (22 checks),
`run_phase5.sh`.  Design in DESIGN.md "Phase 5".  `planner.py` gained three
additive things — the `share_memo` / `fold_templates` / `budget_shape` fields
of `PlanOptions`, the module-level `PLAN_STATS` counters, and a cached
`Policy.signature()` — and the 50-target fom snapshot is still byte-identical
(`tests/test_phase4.py`), `tests/test_phase3.py` still passes 45 checks.

### Timings

| stage | wall | packs |
|---|---:|---|
| deep-cone benchmark + `cProfile` | **28.3 s** | fom, minecolonies, atm10 |
| build breakdown, cold + warm | **48.4 s** | fom, atm10-lite, minecolonies, atm10 |
| cross-request memo | **190.8 s** + 3.3 s | 5 packs + 3 small |
| fold pruning | 35.8 s + **99.8 s** | 6 packs + 2 deep |
| budget shapes, 3 shapes × 3 bases | **833.2 s** | atm10-lite, atm10 |
| `tests/test_phase5.py` (22 checks) | 2.4 s | fom |
| **total** | **≈ 20 min** | budget: 20 min |

### The deep-cone benchmark

50 targets the *baseline* found deepest (`index.tsv` `depth`, then `actions`)
plus 150 random ones, per pack; per-target minimum over three passes after a
25-target warm-up.

| pack | recipes | items | index s | policy s | solve s | set | n | median ms | p95 ms | max ms | expansions (med) | actions (med) | `BUDGET_TRUNCATED` | peak RSS |
|---|---:|---:|---:|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|
| fom | 3,442 | 2,625 | 0.29 | 0.00 | 0.01 | deep (baseline depth 102) | 50 | **0.50** | 0.67 | 1.26 | 15 | 15 | 0 | 129 MB |
| fom | | | | | | random | 150 | 0.33 | 0.72 | 6.04 | 9 | 9 | 0 | |
| minecolonies-dim.-adventure | 71,899 | 37,448 | 2.71 | 0.05 | 0.28 | deep (baseline depth 123) | 50 | **4.64** | 10.09 | 13.62 | 46 | 46 | 0 | 1,075 MB |
| minecolonies-dim.-adventure | | | | | | random | 150 | 3.10 | 8.02 | 35.67 | 23 | 23 | 0 | |
| atm10 | 283,422 | 62,488 | 15.04 | 0.12 | 0.66 | deep (baseline depth **2,198**) | 50 | **29.11** | 320.12 | 385.92 | 331 | 331 | **0** | 1,842 MB |
| atm10 | | | | | | random | 150 | 15.24 | 139.07 | 316.75 | 98 | 98 | **0** | |

Incompleteness: fom 0/200, minecolonies 0/200, **atm10 10/200 (5.0 %), every
one of them `SELF_DEPENDENT`, none `BUDGET_TRUNCATED`.**

> **Correction to Phase 3 open question 2.**  That question says "atm10 is
> 15 % `BUDGET_TRUNCATED`, all of it at `budget_base = 512` plus a cone
> raise".  It is not.  Re-running the Phase 3 A/B sample target by target,
> all 17 of its 110 incomplete atm10 plans carry exactly one unresolved row
> and its reason is **`SELF_DEPENDENT`** — the expansion counters on those
> targets top out at 264 of an allowance of 512, so the budget is never
> exhausted.  The A/B's `incomplete` column does not record the reason, and
> the Phase 3 report attributed it to the budget without checking.  The real
> cause is one item on which several deep atm10 chains re-enter their own
> expansion.  Everything else in Phase 3's §8 stands; only the diagnosis of
> this 15 % was wrong.

`cProfile`, the 10 deepest atm10 targets, top 15 by `tottime` (0.71 s total):

| tottime | cumtime | calls | where |
|---:|---:|---:|---|
| **0.287 s** | 0.319 s | **3** | `planner.cone_stats` |
| 0.046 | 0.707 | 4,668 | `planner.demand` |
| 0.044 | 0.162 | 17,239 | `planner.producer_cost` |
| 0.043 | 0.211 | 2,957 | `planner.choose_producer` |
| 0.038 | 0.038 | 58,664 | the machine-term generator inside `producer_cost` |
| 0.035 | 0.059 | 23,540 | `PackIndex.out_amount` |
| 0.031 | 0.031 | 186,657 | `builtins.round` (the explanation blocks) |
| 0.026 | 0.040 | 57,168 | `planner._consumed_amount` |
| 0.023 | 0.055 | 2,957 | `planner._action_row` |
| 0.016 | 0.016 | 321,449 | `set.add` |
| 0.015 | 0.015 | 94,094 | `PackIndex.producers_of` |
| 0.012 | 0.015 | 110,662 | `planner._resolve_or` |
| 0.012 | 0.012 | 47,080 | `numpy.ufunc.reduce` |
| 0.010 | 0.023 | 10 | `planner.finalise` |

**41 % of the profile is three calls to the lazy cone walk.**  Each costs
~96 ms on atm10.  Everything else is flat and distributed exactly where the
design says the work is.  Two Java-relevant readings: the cone walk has to be
cached per `(root, policy)` or bounded much harder than 200 000, and
`builtins.round` at 186 k calls is the explanation block — a Java port should
build it lazily.

### Build-time breakdown, cold vs warm

| stage | fom | atm10-lite | minecolonies | atm10 |
|---|---:|---:|---:|---:|
| index (parquet → CSR) | 0.252 | 0.545 | 2.602 | **13.779** |
| posteriors + producer scores, **cold** | 0.544 | 2.583 | 3.458 | **23.368** |
| — of which template inference | 0.455 | 2.200 | 3.050 | 19.339 |
| — of which the ranker aggregation | 0.082 | 0.369 | 0.384 | 3.872 |
| posteriors + producer scores, **warm** | 0.001 | 0.006 | 0.016 | **0.092** |
| candidate policy | 0.004 | 0.017 | 0.054 | 0.116 |
| cost solve (empty stock) | 0.005 | 0.053 | 0.295 | 0.597 |
| pool units | 0.001 | 0.005 | 0.005 | 0.050 |
| feedback side tables (Phase 4) | 0.061 | 0.166 | 0.187 | 1.453 |
| **cold total** | **0.867** | **3.368** | **6.602** | **39.362** |
| **warm total** | **0.324** | **0.791** | **3.160** | **16.087** |
| peak RSS | 625 MB | 766 MB | 1,430 MB | 2,117 MB |

**Warm minus the CSR build is the number that matters for Java** — the CSR
*is* `RecipeGraph`, which the game already holds:

| pack | warm total | − index | = additional graph-build cost |
|---|---:|---:|---:|
| fom | 0.324 | 0.252 | **0.072 s** |
| atm10-lite | 0.791 | 0.545 | **0.246 s** |
| minecolonies | 3.160 | 2.602 | **0.558 s** |
| atm10 | 16.087 | 13.779 | **2.308 s** |

atm10's 2.3 s is inside the plan's 3–5 s budget, and it is Python: a
`DerivedIndexCache` read of five `float[]` arrays plus a `String[]` is not
2 s of work in Java.  The cold path (39.4 s on atm10, 19.3 s of it LightGBM
inference over 96,393 templates and 3.9 s the ranker aggregation over 51,485
producer groups) is what the disk cache exists to avoid; it has to run once
per catalog change.

### Cache inventory and sizes

All in MB, from `out/planner-bench/build.json`.

| artefact | fom | atm10-lite | minecolonies | atm10 | Java placement |
|---|---:|---:|---:|---:|---|
| `cache/<slug>/*.parquet` (the graph) | 0.25 | 1.63 | 4.96 | **32.11** | node (already `RecipeGraph`) |
| `out/<slug>/recipe_template.parquet` | 0.05 | 0.31 | 0.76 | 3.70 | node |
| `out/<slug>/economy/*.parquet` | 0.30 | 1.35 | 3.59 | 10.55 | side |
| `out/<slug>/planner/*` | 0.16 | 0.92 | 1.63 | 9.54 | side + node |
| `out/models/*.json` | 3.91 | 3.91 | 3.91 | 3.91 | resource (in the jar) |
| **what a Java runtime would cache per catalog** | **0.51** | **2.58** | **5.98** | **23.79** | |

The last row is the non-graph, non-resource rows: template ids, economy,
posteriors and scores.  The Phase 4 feature matrices are corpus-wide files
(21.8 MB `recipe_features`, 20.5 MB `template_features` for all 22 packs) and
only the feedback layer reads them; a Java runtime that ships feedback needs
the per-pack slice of both.

### The cross-request cost memo

150 random targets per pack, three variants over the same targets.  `hoisted`
is the Phase 3 harness's own path (one solve passed in by the caller) — the
upper bound the memo is chasing.

| pack | recipes | per request (total s / median ms) | shared memo | hoisted | hit rate | speed-up | plans identical |
|---|---:|---|---|---|---:|---:|---|
| farmers-colonies | 2,658 | 0.510 / 3.020 | **0.027 / 0.145** | 0.022 / 0.143 | 0.993 | **18.9×** | yes |
| fom | 3,442 | 0.890 / 5.744 | **0.066 / 0.373** | 0.057 / 0.362 | 0.993 | **13.5×** | yes |
| lost-world | 5,140 | 1.203 / 7.198 | **0.053 / 0.217** | 0.040 / 0.205 | 0.993 | **22.7×** | yes |
| atm10-lite | 24,624 | 9.699 / 62.227 | **2.835 / 15.289** | 2.691 / 13.011 | 0.993 | **3.4×** | yes |
| minecolonies | 71,899 | 47.809 / 313.013 | **0.978 / 3.360** | 0.569 / 3.286 | 0.993 | **48.9×** | yes |
| atm10 | 283,422 | 117.120 / 701.882 | **5.967 / 16.606** | 4.606 / 16.799 | 0.993 | **19.6×** | yes |

(`out/planner-bench/memo.json`; farmers-colonies from `memo-small.json`, the
same harness on the three smallest packs.)

Hit rate is 149/150 by construction — every empty-stock request of a pack is
the same key.  **The shared memo reaches the hoisted path within noise on
every pack** (atm10: 16.61 ms vs 16.80 ms median), which is the point: a
runtime that owns the cache does not have to hoist the solve into its
caller.  `tests/test_phase5.py` asserts a different stock signature is a
different entry.

One finding worth carrying to Java: the first version of the key hashed the
exclusion bitmap on **every** request, which costs 9 ms on atm10 and ate a
third of the saving (median 24.9 ms instead of 14.1 ms, measured on the same
150 targets before and after).  Caching the
signature on `Policy` closed it. `SourcePolicyDecision` should carry the
fingerprint as a field.

atm10-lite's 3.4× is the low end because its per-target extraction (15 ms) is
large next to its solve (53 ms); minecolonies' 49× is the high end because
the reverse is true (295 ms solve, 3.4 ms extraction).

### Fold-based pruning

Collapsing the admissible producers of one output that share a `template_id`
to their best-scored representative, before the shortlist.

| pack | targets | default total s | folded total s | speed-up | action lists changed | explanations changed | metric deltas |
|---|---:|---:|---:|---:|---:|---:|---|
| fom | 200 | 0.076 | 0.081 | **0.94×** | **0/200** | 1 | none to 4 dp |
| farmers-colonies | 200 | 0.031 | 0.031 | 1.00× | 0/200 | 1 | none |
| tfc-enhanced | 200 | 0.032 | 0.033 | 0.97× | 0/200 | 0 | none |
| lost-world | 200 | 0.072 | 0.074 | 0.97× | **1/200** | 32 | actions 7.6250 → 7.6300, depth 4.2100 → 4.2150 |
| technical-electrical | 200 | 0.286 | 0.273 | **1.05×** | 0/200 | 15 | none |
| atm10-lite | 191 | 3.249 | 3.276 | 0.99× | **0/191** | 191 | none |
| minecolonies | 200 | 0.917 | 0.847 | **1.08×** | **0/200** | 152 | none |
| atm10 | 200 | 9.340 | 9.923 | **0.94×** | **2/200** | 177 | actions 374.97 → 374.88, crafts 732,806.94 → 732,806.81, `junk_wl` 0.0979 → 0.0976 |

(`out/planner-bench/fold.json` + `fold-small.json`.)

**Fold pruning does not pay anywhere.**  Across two runs and eight packs the
speed-up is **0.94×–1.08×** — within noise of 1.0, in both directions,
including on atm10 whose largest output has 3,746 producers.  The reason is
structural: the shortlist is already `O(candidates)` and the fold adds a
dictionary build over the same list, so it trades one pass for another.  It
changes 0–2 executed plans in 200, and the metric deltas are in the fourth
significant figure.  On the audit cases it changes **nothing**: every verdict
is identical with and without it, including the one lost case.

A Java port should not carry it.  What the measurement *does* say is that
sibling producers of one template are interchangeable in practice — 177 of
atm10's 200 plans had a folded-away sibling in their shortlist and only 2
changed what they executed — which is an argument for the template-level
*feedback* of Phase 4, not for pruning.

### Budget shapes

Three shapes × three bases × 200 targets (50 deepest + 150 random), per-target
minimum over three passes.  `incomplete` includes the `SELF_DEPENDENT` rows,
which no budget shape can fix.

**atm10** (200 targets, 10 `SELF_DEPENDENT` at every setting):

| base | shape | truncated | rate | incomplete | median ms | p95 ms | max ms | actions med | expansions p95 |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 512 (default) | **cone (default)** | **0** | 0.000 | 0.050 | **15.24** | **191.39** | 327.55 | 114 | 1,864 |
| 512 | depth | 0 | 0.000 | 0.050 | 15.43 | 198.34 | 357.08 | 114 | 1,864 |
| 512 | time 0.05 s | 32 | 0.160 | 0.210 | 16.25 | 200.60 | 365.00 | 114 | 1,862 |
| 512 | time 0.25 s | 0 | 0.000 | 0.050 | 16.35 | 206.69 | 384.37 | 114 | 1,864 |
| 64 | cone | 0 | 0.000 | 0.050 | **96.57** | 212.13 | 367.98 | 114 | 1,864 |
| 64 | depth | 0 | 0.000 | 0.050 | 96.55 | 214.32 | 353.54 | 114 | 1,864 |
| 64 | time 0.05 s | 150 | 0.750 | 0.755 | 94.83 | 209.67 | 349.78 | 108 | 1,863 |
| 64 | time 0.25 s | 1 | 0.005 | 0.055 | 96.73 | 201.89 | 381.78 | 114 | 1,864 |
| 8 | cone | 0 | 0.000 | 0.050 | 105.26 | 263.34 | 394.78 | 114 | 1,864 |
| 8 | depth | 10 | 0.050 | 0.100 | 105.03 | 216.40 | **260.63** | 114 | 1,813 |
| 8 | time 0.05 s | 196 | 0.980 | 0.980 | 97.19 | 209.40 | 370.01 | 113 | 1,858 |
| 8 | time 0.25 s | 0 | 0.000 | 0.050 | 97.38 | 209.53 | 385.15 | 114 | 1,864 |

**atm10-lite** (191 targets, 0 incomplete at every setting): every shape at
every base truncates 0 except `depth` at base 8 (1 target, 0.5 %); medians
12.0–17.8 ms, p95 38–45 ms.

Readings:

1. **Nothing truncates at the default.**  The cone raise (`base + 4 ×
   |cone|`, cap 200 000) is so much larger than any observed expansion count
   (p95 1,864) that `budget_base` never binds on these targets.  **There is
   no truncation problem to fix**, and the 15 % the Phase 3 report attributed
   to the budget was `SELF_DEPENDENT` (above).
2. **No variant dominates.**  `depth` is within noise of `cone` at the
   default base and *worse* at base 8 (5 % truncated); the time box is either
   equivalent (0.25 s) or catastrophic (0.05 s: 16 % at the default base,
   98 % at base 8).  **The default stays.**
3. **`budget_base` is a cone-walk frequency knob, not a safety knob.**
   Lowering it 512 → 64 on atm10 raises the *median* from 15.2 ms to 96.6 ms
   — 6.3× — with no change to truncation, because every target now pays the
   ~93 ms cone walk.  That is the single most actionable number in this
   stage: in Java, either cache the cone walk per `(root, policy)` or keep
   `budget_base` comfortably above the typical expansion count.
4. **A time box is the wrong shape for this planner.**  It is not
   deterministic (`time 0.05 s` truncated 32 targets on one machine and would
   truncate a different 32 on another), and it converts a budget question
   into a scheduling question.  The one thing it does buy is a hard latency
   ceiling, which `RoutingService`'s fallback already provides more cleanly.

### Invariants — `tests/test_phase5.py`, 22 checks

Memo sharing changes no plan on fom and is one solve for the whole batch; a
different stock signature is a different entry; clearing resets the counters.
Fold pruning leaves every action list alone on fom and touches neither the
policy nor the cost solve.  The cone- and depth-scaled budgets and a generous
time box plan fom identically; an unknown shape is refused; `cone_stats`
agrees with `cone_size` and reports a hop depth.  The search counters are not
serialised into the plan and the policy signature is stable and cached.  The
harness runs all four stages on fom in **0.9 s** (budget 30 s).  Every
artefact in the cache inventory exists for fom and is classified.  The
no-vocabulary check now covers 14 modules.

### Deviations from the brief, and why

1. **Timings are the per-target minimum of three passes after a 25-target
   warm-up.**  Single-pass measurement on this machine put two *identical*
   configurations 20–60 % apart, which is larger than the fold and budget
   effects.  The cone stage's headline numbers are reported the same way.
2. **`atm10` and `minecolonies` only for the cone stage; the budget stage on
   `atm10` and `atm10-lite`.**  Three shapes × three bases × 200 targets on
   atm10 is 14 minutes on its own; adding minecolonies would have taken the
   phase past its 20-minute budget for a table whose conclusion is already
   "nothing truncates".
3. **A third, stress base (8) was added to the budget sweep.**  With nothing
   truncating at the default, a comparison of shapes at the default alone
   would have been three identical rows.  The stress bases are what make the
   shapes distinguishable, and they are labelled as stress.
4. **`fold_templates` is reported as a negative result rather than tuned.**
   The obvious variant — fold only when the group is larger than some N —
   would trade the same 1–3 % against the same 0–2 plans; there is no signal
   to fit N against.
5. **Peak RSS is process-wide** (`common.peak_rss_mb`), so the atm10 figure
   (2.2 GB) includes the harness's own parquet buffers.  The steady-state
   `PackIndex` is smaller; the number is reported as the machine cost of
   running the benchmark, which is what the plan's memory budget is about.
6. **No `--force` cold rebuild of the *parquet* stage.**  "Cold" here means
   the model inference is recomputed (`score_models(force=True)`); the
   contents parquet is still read from `cache/`.  Rebuilding that is
   `run_phase0a.sh`'s job and is measured in HANDOVER §2.

### Open questions from Phase 5

1. **atm10's 5 % `SELF_DEPENDENT` is the real incompleteness, and it is not
   a budget problem.**  Ten of 200 targets (17 of 110 in the Phase 3 A/B)
   raise a demand for one item while that item is being expanded.  The
   settle-order rule makes round trips impossible but does not make
   self-dependence impossible when the ledger exception fires.  Whether these
   are genuinely unreachable or a gap in the ledger rule is one afternoon of
   tracing and was out of scope here.
2. **The cone walk costs 93 ms on atm10 and is called lazily but not
   cached.**  Three calls were 40 % of a ten-target profile.  Caching it per
   `(root, policy)` is trivial and was not done because the default
   `budget_base` makes it rare; a Java port that lowers the base must cache
   it.
3. **The explanation block is 186 k `round()` calls per ten atm10 targets.**
   Building `why.cost` / `why.shortlist` / `why.excluded` eagerly costs real
   time on deep targets.  Java should build it on demand.
4. **Cold model inference is 22 s on atm10** (18.9 s of it the LightGBM junk
   and role heads over 96,393 templates).  HANDOVER §4.6 already recommends
   the LR arm for Java — it would cut this to a 129-wide dot product per
   template — at a cost of 0.02 AUC.  This measurement is the strongest
   argument for that recommendation so far.
5. **Memo sharing is measured only for the empty-stock key.**  A UI that
   sends the player's real inventory changes the stock signature on every
   request and the hit rate collapses to zero.  The plan's "定点按锥增量"
   (incremental by cone) would need a cone-local re-solve on stock change,
   which this prototype does not implement — it re-solves the pack.  On atm10
   that is 0.65 s, so a stock-carrying UI needs the incremental version.
