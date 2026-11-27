# Phase 0a — data contract

The Python experiment layer that later phases (economy, labels, features) build
on.  Two programs, one cache, one ontology:

```
exports/<pack>/recipes-<ts>.json ──load_exports.py──▶ cache/<slug>/*.parquet
                                                         │
                                            ontology.py ─┴─▶ out/<slug>/*.parquet
                                                             out/token-classes.json
```

Run everything through the pinned venv (`./.venv/bin/python`); the system
`python3` is 3.9 and has neither `pyarrow` nor `ijson`.  `./run_phase0a.sh`
runs loader, ontology and the invariant checks with `time` around each step.

---

## 0. Hard rules

1. **No hardcoded mod / item / colour / material / system-name vocabulary.**
   Every class is induced from the data.  The only domain knowledge allowed is
   the export schema's own field names and field values (`kind`, `stack`,
   `tag`, `any`, `typedAny`, `value`, `complex`, `item`, `fluid`, `amount`,
   `chance`, `matchStrategy`, …) and *pure shape* mechanics (zero inputs,
   inputs identical to outputs, empty slots).
   `tests/test_phase0a.py::test_no_rule_vocabulary` enforces this by parsing
   the three modules and rejecting any registry-shaped literal (`ns:path`) or
   material/colour/rule word that appears in executable code.
2. **No rule is ever a filter.**  The Java `ExportPackLoader` drops
   junk/duplicate recipes up front through the builtin classifier; the Python
   layer has no classifier at all, because those rules become *training labels*
   later.  Only structurally empty recipes (no inputs or no outputs) are
   marked `dropped`, and they are still written to the table.
3. **Determinism.**  Same input bytes ⇒ byte-identical parquet.  Fixed
   schemas, fixed batch size (20 000 recipes per row group), stream order
   preserved, every set/dict materialised through `sorted()`, no timestamps in
   the parquet payload, hashes are sha1 over canonical JSON.

---

## 1. `load_exports.py`

```
--exports-root  default /Users/vfyjxf/ProjectDir/Planner-Final/exports
--cache-dir     default ./cache
--packs         CSV of export directory names, or "all"
--force         re-read even when the cache is fresh
--limit N       debug only: stop after N recipes per pack
```

Streams the single `recipes-<ts>.json` of each pack with
`ijson.items(handle, "recipes.item")` (yajl2_c backend), flushing a parquet row
group every 20 000 recipes so RSS stays flat regardless of pack size.  The
top-level scalars and `systems[]` are read from a head window and a tail window
of the file rather than by a second full parse; a streaming fallback exists if
those windows do not yield valid JSON.

Cache layout — `cache/packs.json` maps `slug -> export directory name`
(slug = ASCII-folded, lowercase, non-alphanumerics collapsed to `-`).

### `cache/<slug>/recipes.parquet` — one row per exported recipe

| column | type | meaning |
|---|---|---|
| `recipe_idx` | int32 | dense stream index, per pack; the join key, and the same index the Java loader assigns |
| `pack` | string | export directory name |
| `system` | string | `system` field |
| `source` | string | `vanilla` / `jei` / `custom` |
| `recipe_type` | string (null) | `recipeType`, JEI systems only |
| `recipe_id` | string | raw `id`, empty string when the export had none |
| `recipe_ns` | string | namespace part of `recipe_id` |
| `recipe_path_tokens` | list\<string\> | path split on `/ _ . -`, lowercased |
| `machines` | list\<string\> | machine item ids |
| `n_inputs`, `n_outputs` | int32 | entry counts |
| `has_value_input` | bool | any input of kind `value` |
| `has_complex` | bool | any content of kind `complex` |
| `flags` | list\<string\> | sorted reliability signals, see below |
| `dropped` | bool | `n_inputs == 0 or n_outputs == 0` |

Flags (open set; absent flags simply never fired on this corpus):
`empty_id`, `id_no_namespace`, `suffix_stripped`, `no_machines`,
`empty_inputs`, `empty_outputs`, `sentinel_amount`, `chance_zero`,
`chance_missing`, `count_amount_mismatch`, `has_value_input`, `has_complex`,
`tag_no_members`, `empty_tag_id`, `empty_alternatives`,
`mixed_alternative_types`, `unknown_stack_member`, `custom_stack_type`,
`unknown_kind`, `malformed_stack`, `malformed_content`.

### `cache/<slug>/contents.parquet` — one row per input/output entry

| column | type | meaning |
|---|---|---|
| `recipe_idx` | int32 | join key into `recipes.parquet` |
| `side` | string | `in` / `out` |
| `slot` | int16 | position within that side |
| `kind` | string | `stack` / `tag` / `any` / `typedAny` / `value` / `complex` |
| `content_type` | string | `item`, `fluid`, custom stack family, value type id, `complex`, or `mixed` for a heterogeneous `any` |
| `base_id` | string | item/fluid id · tag id · **lexicographically smallest** member of an any/typedAny · value type · complex implementation |
| `members` | list\<string\> | `values[]` for tags, `stacks[]` ids for any/typedAny |
| `n_members` | int32 | `len(members)` |
| `variant_key` | string (null) | sha1-16 of the canonicalised `components` object |
| `component_keys` | list\<string\> | sorted top-level component keys (names only, never values) |
| `match_strategy` | string (null) | raw `matchStrategy`; null means absent (the Java loader defaults absent → `identity`) |
| `amount` | int64 (null) | entry `amount`; **null** when the export wrote `2147483647` |
| `amount_sentinel` | bool | that sentinel was seen (extension beyond the contract, keeps the signal row-local) |
| `chance` | float64 | raw `chance`, never clamped |
| `value` | string (null) | raw scalar of a `value` entry, rendered losslessly as text |
| `inner_count` | int64 (null) | `count`/`amount` *inside* the stack object |

Normalisation mirrors `ExportPackLoader.parseContent` / `parseStackMember` /
`resolveFamily`: a stack's family comes from `stack.type` (`item` / `fluid` /
custom id), a tag's family from the pack's tag registry (`tag-types-*.json`,
falling back to `item` exactly like the Java `resolveFamily`), and a
`typedAny`'s from its `stackType` via the stack-type registry.  Registry ids
are reduced to families by pure string surgery (`<ns>:<path>` → `<path>`,
`<ns>:<x>_stack` → `<x>`) so an unknown mod normalises like a known one.
A `|+N` alternative-count suffix (produced by `ExportPackLoader` L527 when it
renders an alternatives list as one display id) is stripped wherever it appears
in an id and raises `suffix_stripped`.

One deliberate divergence from the Java loader: it takes `members.get(0)` as an
alternative list's representative, but the exporter's member order is **not
stable across exports of the same pack** (two packs in this corpus are the same
dump twice and differ only in `values[]` order).  `base_id`, `inner_count` and
`variant_key` for `any`/`typedAny` therefore come from the lexicographically
smallest member; `members` itself keeps the export order.

### `cache/<slug>/systems.parquet`

`system_id`, `source`, `recipe_type`, `jei_recipe_types`, `machines`,
`recipe_count`, `skipped` (from `systems[]`), plus `skipped_listed` and
`skipped_reasons_json` joined from `skipped-<ts>.json`.  Systems that appear
only in the skipped dump are appended with `recipe_count = 0`.

### `cache/<slug>/registry.json`

Verbatim contents of the five registry side files when present, keyed
`content-types`, `stack-types`, `tag-types`, `value-types`, `variant-types`.
Old packs (atm10, atm10-lite) have none; the loader carries on and counts
`tag_family_unknown` in the meta.

### `cache/<slug>/load-meta.json`

`recipeCount` / `skippedCount` from the dump header, `rows`, `kept`,
`dropped`, `dropped_by_reason`, `content_rows`, `kind_counts`,
`stack_type_counts`, `flag_counts`, `chance_zero_systems`, `counters`,
`registry_files`, `systems`, `parse_seconds`, `peak_rss_mb`, `batch_recipes`.

---

## 2. `ontology.py`

```
--cache-dir --out-dir --packs
--min-evidence 2     interchange must be witnessed in >= N contexts
--min-cosine   0.7   context-similarity floor for an edge   <- the cohesion knob
--k-core       1     token must interchange with >= K partners (1 = off)
--bigram-min-partners 2  distinct single tokens a bigram must interchange with
--bigram-min-contexts 2  distinct contexts that interchange must be seen in
--max-bucket   400   contexts with more alternatives than this are ignored
--resolution   1.0   Louvain resolution
--onboarding   full|approx|off
--no-cross-pack
```

### 2.1 Token classes by analogy

**Universe.** Every distinct id that appears as a stack/any/typedAny `base_id`
with a concrete family, every tag/any *member* id, and every machine id.  Tag
ids themselves are excluded from evidence (their paths are namespaced
differently) but are still *abstracted* with the induced classes.

**Evidence.** Ids are reduced to their path token sequence (namespace dropped,
split on `/ _ . -`) and de-duplicated, so a path shipped under five namespaces
is counted once.  For every sequence and every position we build a *hole
bucket* keyed `(prefix, suffix)`:

```
red_wool      -> ((), ('wool',)) : red        white_wool -> ((), ('wool',)) : white
oak_stairs    -> ((), ('stairs',)): oak        oak_slab  -> (('oak',), ())  : slab
```

Two ids that differ in exactly one token position land in the same bucket, so
every bucket of size *n* contributes C(n,2) interchange evidences.  Buckets
with an empty context are never created (they carry no information), and
buckets wider than `--max-bucket` are skipped as uninformative slots.

**Bigrams (second pass).**  The same bucket key is produced for a *two-token
run*: `light_blue_wool` at position 0 reduces to `((), ('wool',))` with the
merged token `light_blue`, which therefore sits in the same bucket as the
single token `red`.

Promotion must be decided by **non-compositionality, not by frequency**.  A
frequency rule ("this merged token substituted for a single token N times")
also promotes compositional compounds — `<colour>_<wood>`, `<wood>_<form>` —
because those genuinely do align with single tokens (`<colour>_<wood>_seat`
aligns with `<other colour>_seat`).  Promoting them is actively harmful: it
separates the colour class from the wood class instead of *composing* them, it
mints thousands of one-off tokens that then interchange only with each other
and collapse into blob classes, and it splits one recipe shape across two
templates depending on whether the compound happened to clear the threshold.

So the analogy rule is applied to the bigram itself.  For a merged token `a_b`
in context `(prefix, suffix)`:

* the **unit** reading needs single-token partners *other than* `a` and `b`
  (some `prefix + x + suffix` exists).  A partner equal to `a` means `a` alone
  already fills that slot; a partner equal to `b` means `a` was merely inserted
  in front of it.  Both are compositional readings, so both are excluded from
  the unit evidence.
* the competing **droppable/insertable modifier** reading is measured on the
  head token globally: `solo(a)` = the number of contexts in which `a` itself
  interchanges as a single token.  A head that stands on its own all over the
  corpus (a colour, a wood, a material) was composed with the next token; a
  head that never stands alone (a bound prefix such as the first half of a
  two-token colour) was not.

`a_b` is promoted iff it has at least `--bigram-min-partners` distinct unit
partners in at least `--bigram-min-contexts` distinct contexts **and**
`unit_contexts(a_b) >= solo(a)`.  Sequences are then re-tokenised with a
deterministic greedy non-overlapping merge (highest unit-context count first,
then leftmost) and the evidence pass is redone over the canonical tokens.

This keeps `light_blue`, `light_gray`, `dark_oak`, `glazed_terracotta`,
`pressure_plate`, `stained_glass` and rejects `black_acacia`, `circular_black`,
`acacia_seat`, `a_black` (see REPORT.md "v1 -> v2").  Because `solo` is
measured over the corpus in scope, a compound can be a unit in a small pack and
compositional in a large one — the intended data-driven behaviour.

**Graph and clustering.**  Raw co-occurrence alone makes every frequent token
adjacent to every other one, so the edge weight is the **cosine similarity of
the two tokens' context sets**:

```
w(a,b) = evidence(a,b) / sqrt(ctx(a) * ctx(b))
```

`ctx(t)` = number of qualifying buckets containing `t`.  Edges are kept when
`evidence >= --min-evidence` **and** `w >= --min-cosine`; an optional k-core
prune (`--k-core > 1`) drops tokens with too few partners; connected components
are then split by a deterministic multi-level Louvain (nodes visited in sorted
order, ties broken by the smallest community id, aggregation repeated up to 3
levels).  A community of ≥ 2 tokens is a class, named `cls:<lexicographically
smallest member>`; classes are a disjoint partition.

Reported per class: `members`, `size`, `evidence_mass` (raw intra-class
interchange count), `cohesion` (summed intra-class cosine), `role`,
`role_votes`.

**Role guess — positions only, never a word list.**  For each occurrence of a
token in a canonical path, `e = len(seq) - 1 - position`.  `e == 0` votes
*form* (the last path token, the head noun), `e == 1` votes *material* (the
slot immediately before it), `e >= 2` votes *modifier*.  A class takes the
majority vote of its members' occurrences.

### 2.2 Component key-set classes

Only component **key names** are ever read (values never are — that is what
`variant_key` hashes).  Keys that co-occur in any observed `component_keys` set
are unioned; a key-set maps to the sorted set of key groups it touches, and
that set is hashed into a `var:<hash>` class.  All potion-shaped variants
therefore share one variant class while their individual `variant_key`s stay
distinct.  Reported: `n_keysets`, `n_classes`, `n_variant_keys`,
`keyset_fold_ratio`, `variant_fold_ratio`.

### 2.3 `out/<slug>/identity.parquet` — content identity quadruple

One row per distinct base id: `base_id`, `content_type`, `ns`, `tokens`
(canonical tokenisation), `material_class`, `form_class`, `modifier_classes`,
`variant_classes`, `n_variant_keys`.  Slots are positional: last token → form,
second to last → material, the rest → modifiers.  A token that belongs to no
class is passed through as `t:<token>` so the quadruple is total; an absent
slot is null/empty.

### 2.4 Template folding

A content abstracts to

```
kind | content_type | material | form | modifiers | variant_class | amount | chance_bucket
```

(`impl:<class name>` instead of the class slots for `complex`; chance buckets
are `1`, `0`, `na`, else rounded to 0.05).  A recipe's **template signature**
is canonical JSON over

| key | value |
|---|---|
| `sys` | the recipe's `system` |
| `mach` | sorted abstractions of the machine ids (`material|form|modifiers`) |
| `in` | sorted multiset of abstracted inputs (order-independent) |
| `out` | sorted multiset of abstracted outputs |
| `rid` | recipe-id path tokens that belong to **no** induced class (the residue after the material/colour/form tokens are abstracted away) |
| `pat` | sorted `side:kind:match_strategy` pattern (absent strategy reads as `identity`, matching the Java default) |

`template_id = "tpl:" + sha1(signature)[:12]`, stable across runs and packs.
Outputs: `templates.parquet` (`template_id`, `signature` as canonical JSON
text, `system`, `size`, `n_inputs`, `n_outputs`, three example recipe ids) and
`recipe_template.parquet` (`recipe_idx` → `template_id`, kept recipes only).

Worked example (fom, 48 members):

```json
{"sys":"minecraft:crafting",
 "mach":["cls:crafting|t:table|"],
 "in":["stack|item|cls:black|cls:banner|||1|1","typedAny|item|cls:black|cls:banner|||1|1"],
 "out":["stack|item|cls:black|cls:banner|||1|1"],
 "pat":["in:stack:identityAndComponent","in:typedAny:identity","out:stack:identityAndComponent"],
 "rid":[]}
```

### 2.5 Cross-pack prior and onboarding

Per-pack sequences are unioned with a pack bitmask; the prior is induced over
that union and written to `out/token-classes.json` (classes, roles, members,
parameters, contributing packs).  `--onboarding full` then refits the prior
22 times — once per left-out pack, reusing the global hole buckets and
filtering their members by the mask, which is exact because the mask is carried
at *sequence* level — and records, per pack: tokens, tokens absent from the
other 21 packs, prior class count, classes the pack touches, classes that could
not be induced without it, and tokens the pack newly pulls into a class.
Written to `out/onboarding.json`.

---

## 3. Thresholds, and why

| knob | value | reason |
|---|---|---|
| `--min-evidence` | 2 | a single coincidental analogy is not evidence |
| `--min-cosine` | 0.7 | sweep on fom: 0.0 → 4 classes (two giant blobs), 0.5 → 18, 0.7 → 41 with clean colour/wood/stone/armour/machine classes, 0.8 → 42 and starts splitting real families |
| `--k-core` | 1 (off) | with the cosine floor in place, k-core only deletes true two-token classes (e.g. the raw-ore class) |
| `--bigram-min-partners` | 2 | one partner is a coincidence; two distinct single tokens filling the same slot is an interchange |
| `--bigram-min-contexts` | 2 | the interchange must repeat in a second context, else it is one accidental id pair |
| (no frequency threshold) | — | replaced by `unit_contexts(a_b) >= solo(head)`; the frequency rule promoted 63,055 bigrams across the corpus (24,672 in the prior) and produced the 371/280/259-member blob classes |
| `--max-bucket` | 400 | guards the O(n²) pair enumeration; on this corpus it fires 10 times across 22 packs |
| `--resolution` | 1.0 | after the cosine floor the components are already tight; Louvain only has to split the residual blobs |
| batch | 20 000 recipes | keeps loader RSS flat (~0.7 GB peak on a 732 MB pack) |

## 4. What later phases can rely on

* `recipe_idx` is dense and stable per pack; it is the join key for every
  future table (labels, features, economy).
* `len(recipes.parquet) == recipeCount` of the dump — nothing is filtered.
* `dropped` is purely structural; every other observation is a `flag`, not a
  removal.
* `template_id` is a pure function of the signature, so template tables from
  different runs (and different packs) are comparable.
* Class ids (`cls:…`, `var:…`) are stable for a fixed member set; they change
  if the member set changes, so persist the class table alongside any model
  trained on it.

---

# Phase 0b — the economy layer

```
cache/<slug>/{recipes,contents}.parquet ─┐
out/<slug>/identity.parquet ─────────────┼─ economy.py ─▶ out/<slug>/economy/*.parquet
out/<slug>/recipe_template.parquet ──────┘                out/economy/{leaf_stats.parquet,summary.json}
```

`./run_phase0b.sh` runs the layer over every cached pack and then the invariant
checks.  Phase 0a is never touched: `economy.py` only reads its outputs, and
adds nothing to `common.py`.

## 0. The same hard rules

1. **No hardcoded mod / item / colour / material / system-name vocabulary.**
   Every threshold below is either *learned from the pack's own distribution*
   or *pure shape* (how many slots, whether a multiset is preserved, whether
   the item graph loops back).  `tests/test_phase0b.py::test_no_rule_vocabulary`
   parses `economy.py` and rejects registry-shaped literals and material /
   colour / rule words, exactly like the Phase 0a check.
2. **No rule is a filter.**  Every kept recipe gets a row in every table.  The
   suspicious ones are *scored* (`reliability`, `reverse_tier`, `value_ratio`),
   never dropped, because those judgements are Phase 0c's training labels.
3. **Determinism.**  Same inputs ⇒ byte-identical parquet: fixed schemas, one
   row group, `np.unique`/`sorted` everywhere, stable sorts, hashes over
   canonical JSON.  Checked by running the whole pack twice.

## 1. Graph build

Each content row resolves to a **node**:

| kind | node |
|---|---|
| `stack` | the item it names |
| `tag` / `any` / `typedAny` with members | an **OR node** over its members |
| `tag` / `any` with no members, `complex` | nothing (counted in `unresolved_rows`) |
| `value` | no node; its magnitude enters `value_cost[recipe]` (the value hook) |

OR nodes are keyed by an **order-independent hash of the member set**
(`segment_hash`), so one tag is one node however often it is used and whatever
order the exporter wrote its `values[]` in.  That collapses atm10's 9.17 M
member slots to 240 k distinct ones and is what keeps the layer linear.

Item universe = every concrete stack id ∪ every OR member id ∪ every machine
id.  Inputs are folded per `(recipe, node)`, outputs per `(recipe, item)`;
`amount` is multiplied by `chance` in `effective_amounts` (the chance hook —
inert here, `chance` is a constant 1.0).

Every string column goes through arrow's dictionary encoder
(`_dict_codes` / `_mask_of` / `_remap`).  Comparing numpy object arrays instead
costs ~17 s per pass on atm10; dictionary codes cost 0.5 s.

## 2. Material conservation → shape roles  (`recipe_shape.parquet`)

**Material key** of an item = its Phase 0a `material_class`.  An id whose path
is a single token has no material slot, so its `form_class` stands in (its one
token *is* its identity); an id the ontology never saw falls back to the same
positional rule over its raw path tokens, i.e. `t:<token>`.  An OR node takes
its members' common material, else `mixed`.

**Round trip** — the strongest conversion evidence — is read off the strongly
connected components of the *same-material item graph*.  Instead of the
O(inputs × outputs) product of same-material pairs, every `(recipe, material)`
that occurs on both sides becomes a hub node (`item → hub → item`);
reachability between items, and therefore their strong components, are
identical and the graph stays linear.  An input that the recipe also outputs is
removed from the input side first, so a shared component always means two
*different* items.

**The conversion band is learned, never assumed.**  Take every
`(recipe, material)` count ratio of the recipes that are both material-set
preserving *and* round-trip confirmed, and keep the central 90 % of
`|log ratio|`.  On 14 of the 22 packs (13 of the 21 deduplicated groups) that
lands on exactly **9.0** — the block↔nugget ratio — and on the rest between
2.0 and 8.0.  A pack with fewer
than `MIN_BAND_SAMPLES` (30) confirmed ratios falls back to (1/3, 3).

Roles, in precedence order:

| role | definition (pure shape) |
|---|---|
| `noop` | the folded input multiset equals the output multiset, amounts included |
| `generation` | no input resolved to anything concrete |
| `conversion` | material set preserved and every per-material ratio inside the learned band |
| `recycling_shaped` | no new material appears (output materials ⊆ input materials) **and** the recipe expands — strictly more items out than in |
| `production` | everything else |

The expansion term in `recycling_shaped` is load-bearing: "output materials are
a subset of the input materials" alone is satisfied by ordinary production
whenever a colour or a wood is shared between an ingredient and the product
(`blue_wool + glass + leather → blue_harness`).  A dismantle recipe that
returns *foreign* materials (bed → wool + planks) fails the subset test and
stays `production`; `value_ratio ≪ 1` and `reverse_tier` are the signals that
catch those.  See REPORT.md — on this corpus the role is dominated by an
export artifact and is the weakest of the five.

## 3. Pools  (`pools.parquet`, `pool_edges.parquet`)

The item graph restricted to `conversion` recipes: an edge per
(input item, output item) pair of such a recipe, `unit_cost = input amount /
output amount` (input count per unit of output).  **Strong** pools are its
strongly connected components, **weak** pools its weakly connected components;
singletons are not pools.  `pool_id = "spool:"/"wpool:" + sha1(sorted members)`,
so the same family gets the same id in every pack that has it.  The
representative is the member with the most consuming recipes, ties broken by
producers then by id.

## 4. Tiers  (`machines.parquet`, `tier` columns)

```
item tier    = min over producers of recipe tier ;  no producer -> 0
recipe tier  = max(max input tier, machine tier) + 1
machine tier = the machine item's own tier
```

Solved in **two passes**, because a machine built in itself (the crafting
table) would otherwise make a whole pack one cycle: pass 1 ignores machines and
gives every machine item the tier of its own cheapest chain; pass 2 injects
`max(machine tier)` as a per-recipe floor.  A machine with no in-pack producer,
or one that only exists because a ring had to be bootstrapped, is hand/unknown:
tier 0 plus `machine_unbuildable`.  Recipes with no machine at all are hand
recipes (`no_machine_share` feeds the reliability model).

`reverse_tier` fires when `max input tier − output tier` clears a **Tukey upper
fence** (`q75 + 1.5 · IQR`) learned from the pack's own gap distribution, floor
1.  Forward recipes pile up at gap = −1 (they *are* their output's cheapest
chain), so the fence sits just above the pack's normal slack — 1.0 on packs
with a tight ladder, 9.0 on `creative-drawers-producer-2`.  A quantile would
have flagged a fixed 5 % by construction; the fence follows the spread.

## 5. Values  (`item_values.parquet`)

Knuth's generalisation of Dijkstra over the hypergraph:

```
cost(recipe) = (Σ input value × amount  +  step_cost × (1 + recipe tier)) × weight(recipe)
value(item)  = min over producers of cost(recipe) / output amount
value(OR)    = min over members
value(leaf)  = the acquirability seed, in [1, K]
```

A node settles once, in non-decreasing order, so a recipe that consumes its own
output can only fire *after* that item has settled — that is what stops
`x → 2x` from spiralling to zero.  The tier function (`max + 1`) is strictly
superior so tiers are exact; the value function is not (dividing by an output
amount > 1 can undercut an input), so values are the well-defined Dijkstra
**upper bound** — the cheapest chain a planner can actually commit to.

**Running a recipe costs something** (`--step-cost`, default 0.25).  The charge
is `step_cost × median leaf seed × (1 + recipe tier)`: a fraction of a gathered
item per step, growing with how deep in the pack the step sits, so the value
ladder and the tier ladder point the same way.  Without it a smelted ore costs
exactly what the ore costs and a nine-deep chain costs exactly what its leaves
cost — the flat ladder v1 produced.  `--step-cost 0` recovers a pure
material-cost valuation.

**Untrusted producers cost more** — `weight = max reliability in the pack /
this recipe's reliability`.  That is `cost / reliability` with the *constant*
part divided out, and the normalisation is load-bearing: reliability carries a
corpus-wide field ceiling (`chance` and `value` are degenerate in every pack,
so nothing scores above 0.5), and a raw `1 / reliability` turns that constant
into a ≥2× multiplier on *every* step, compounding to ~1000× over a ten-deep
chain for a fact that distinguishes no two recipes.  What survives — roughly
1×–2× here — is the informative part: how much less trustworthy this recipe is
than the best one in its own pack.  It is far too flat to gate quest or display
systems; that is Phase 0c's job, and `recipe_weight` / `exclude` on
`solve_hypergraph` are the hooks it feeds role posteriors back through.

**Rings.**  The unsettled sub-graph is decomposed into strong components each
time the queue drains.  A component is **closed** when every producer of every
member depends on another member — no way in.  Only closed rings are ever
bootstrapped, and they are priced as what they are: *nothing in the pack makes
them*, so they take the acquirability seed of their cheapest member (projected
onto non-leaves by ranking their features against the leaf distribution) and
are flagged `value_source = "cycle"`.  A ring that *does* have an entry from
outside is never bootstrapped at all — it settles by ordinary relaxation as
soon as the ring it depends on has settled.  Because a cycle among rings would
be one ring, the ring-dependency graph is a DAG and at least one closed ring
always exists, so this terminates.  An item every one of whose producers is
gone (excluded by a caller, or unable to fire) is *orphaned* and priced the
same way.

The solve then re-runs with the ring values injected as leaves, so everything
downstream settles in the right queue order; only the value a ring was *seeded*
at is carried over, never the value it was later relaxed to, because re-feeding
a relaxed ring value would let the ring get cheaper on every pass.  Two passes
converge on every pack here.

The whole solve runs twice: once with a uniform seed of 1.0 (`value_uniform`)
and once with the fitted acquirability seed (`value_fitted`).

The **catalyst hook** (`net_catalysts` / `netted_graph`, `--net-catalysts`) nets
`min(in, out)` of any entry that appears on both sides off both sides before the
solve, restoring a recipe whose outputs would vanish entirely so that netting
can never delete an item's only producer.  It is **off by default**: this corpus
exports no chance-0 catalyst, so the only shapes it would fire on are the anvil
export artifacts.

## 6. Leaf acquirability  (`leaf_features.parquet`)

Leaves are items with no in-pack producer.  Ten features, all structural:

`n_consumers`, `n_consumer_ns`, `n_consumer_systems`, `n_tags`,
`n_forms_of_material`, `min_consumer_tier`, `renewable` (produced by a
zero-input recipe anywhere in the corpus), `leaf_pack_fraction`,
`producer_packs`, `n_variant_keys`.

A consuming recipe's namespace is its id's namespace, or — for the 28.8 % of
recipes with no id — the namespace of its first output.

**Nine of the ten feed the unsupervised composite.**  `n_forms_of_material` is
written to the table for Phase 0c but excluded from the fit: it measures how
*core* a material is, not how cheap it is to obtain.  On fom it seeded
diamond_ore cheap (the induced `cls:diamond` class has 14 forms) and inflated
nether_star, whose material class lumps it with nether_brick so it inherits 6
"forms" it does not have.  Dropping it also *raised* PC1's explained variance
(fom 39 % -> 45 %, farmers-colonies 39 % -> 46 %).

Each remaining feature is rank-normalised (ties share the mid-rank) and the
score is the **first principal component**, sign-oriented so that "consumed by
many things" reads as cheap.  The score maps into a **bounded multiplicative
band**:

```
seed = K ** (1 - acquirability)        K = --seed-spread, default 2
```

so the cheapest leaf in a pack is exactly 1.0 and the dearest is K.  `K = 1`
reproduces the uniform solve.  The band is the whole point: a leaf is by
definition hand-gatherable, and nothing in the structural features justifies
two orders of magnitude between two gatherables.  v1 mapped the score through
an unbounded octaves-per-sigma rule and produced a 119× spread on fom, which
let the leaf score outweigh entire crafting chains.  The score now only breaks
*ties* between gatherables; a single crafting step (≥ 0.25 × median seed) is
comparable to the whole leaf band, so chain structure dominates as it should.

The raw feature matrix — all ten columns — is kept so Phase 0c can fit a
supervised score over the same rows.

## 7. World resource vs data gap

For each leaf, `producer_packs` counts the **other** packs — duplicate exports
collapsed — where the same base id has at least one producer.  A leaf is a
**data gap** when a majority of the other packs that ship the id can already
make it:

```
k = ceil(containing_other / 2),  at least 1      data_gap = producer_packs >= k
```

`k` is not a constant: the majority of the packs that *carry* the id is the
only threshold that means the same thing for an id present in 2 packs and one
present in 21.  Everything else is a `world_resource`.

Duplicate packs are detected by **content**, never by name: the sha1 of a
pack's sorted template multiset.  The two exports of the same pack in this
corpus collapse to one group and are counted once in every cross-pack statistic.

## 8. Reliability  (`system_reliability.parquet`, `reliability` column)

```
reliability = field_ceiling × flag_factor × system_factor
```

* **`field_ceiling`** — measured, not assumed.  Two schema fields carry economy
  information this layer would use: `chance` (byproducts, returned catalysts)
  and `value` (time / energy).  A field whose distribution is degenerate (one
  distinct value, or no rows) costs every recipe the same fraction of its
  trust.  Both are degenerate in this corpus, so **no recipe anywhere scores
  above 0.5** — an honest statement that half the exported economy is missing,
  and a constant factor that changes no ordering.
* **`flag_factor`** — `1 − MAX_PENALTY · Σ weight(flag)` over the Phase 0a
  flags the recipe carries, where `weight(f) = surprisal(f) / Σ surprisal`
  and `surprisal(f) = −log2(share of kept recipes carrying f)`.  Rare flags are
  anomalies and weigh more; a flag that fires on a third of the corpus says
  little.  A recipe carrying every flag still keeps half its trust.
* **`system_factor`** — `1 − MAX_PENALTY · atypicality`, where atypicality is
  the mean over seven system-shape features (`zero_input_share`, `noop_share`,
  `mean_inputs`, `mean_outputs`, `cross_system_output_share`, `empty_id_share`,
  `no_machine_share`) of `clip(|robust z| / 3, 0, 1)`.  Centre and scale are
  the **recipe-count-weighted** median and MAD across systems, so "normal"
  means "what most recipes in this pack look like" rather than "what most
  systems look like" — a pack with 375 JEI categories and one crafting system
  still treats crafting as normal.

## 9. Outputs

`out/<slug>/economy/`

| file | rows | key columns |
|---|---|---|
| `recipe_shape.parquet` | one per kept recipe | `recipe_idx`, `shape_role`, the shape signals, `tier`, `reverse_tier`, `value_ratio`, `reliability` |
| `item_values.parquet` | one per base id | `value_uniform`, `value_fitted`, `tier`, `is_leaf`, `pool_id`, `weak_pool_id`, `acquirability`, `data_gap`, `producer_packs`, `value_source` |
| `pools.parquet` | one per pool (both kinds) | `members`, `material_keys`, `representative`, `n_edges` |
| `pool_edges.parquet` | one per intra-pool edge | `src`, `dst`, `unit_cost`, `recipe_idx` |
| `machines.parquet` | one per machine item | `tier`, `n_recipes`, `machine_unbuildable`, `in_content_universe` |
| `leaf_features.parquet` | one per leaf | the ten raw features, `acquirability`, `seed_value`, `data_gap`, `world_resource` |
| `system_reliability.parquet` | one per system | the seven shape features, `atypicality`, `mean_reliability` |
| `economy-meta.json` | — | timings, peak RSS, learned band, reverse margin, PCA loadings, flag weights, field checks |

`out/economy/leaf_stats.parquet` (one row per id across the corpus) and
`out/economy/summary.json` (every pack's meta plus the duplicate-export groups).

## 10. Thresholds, and why

| knob | value | reason |
|---|---|---|
| conversion band | **learned** per pack, central 90 % of round-trip-confirmed ratios | 3 blocks → 6 slabs and 1 block → 9 nuggets are both legitimate; assuming ≈1 would call them anomalies |
| `BAND_QUANTILE` 0.05 | central 90 % | the same "central mass" convention the rest of the layer uses |
| `MIN_BAND_SAMPLES` 30 | fall back to (1/3, 3) below it | a band fitted on 5 ratios is noise |
| reverse-tier margin | **learned**, Tukey fence on the tier gap | follows the pack's own spread instead of flagging a fixed share |
| `--max-or-expand` 256 | performance guard on OR expansion | same role as Phase 0a's `--max-bucket`; 2.1 % of the corpus's 579,716 OR rows are wider than it and 0.96 % are actually skipped in the round-trip / pool expansion, counted in `or_capped`.  The pool cross-product guard (`max_pairs` 4,096) never fires on this corpus (`cross_skipped` 0 everywhere) |
| `--step-cost` 0.25 | cost of one recipe step, x median leaf seed x (1 + tier) | four hand steps ~ one gathered item; without it a smelted ore costs exactly what the ore costs |
| `--seed-spread` (K) 2.0 | leaf seeds live in [1, K] | a leaf is hand-gatherable by definition; nothing structural justifies more than a small multiple between two gatherables.  Acceptance cases were checked at K = 1, 2 and 4 (REPORT.md) |
| reliability weight | `max reliability / this reliability` | `cost / reliability` with the corpus-wide field ceiling divided out, so a constant that distinguishes no two recipes cannot compound ~1000x over a deep chain |
| ring floor | the ring's own cheapest member's acquirability seed | never the global cheapest leaf: v1 priced every metal ring at cobblestone |
| `MAX_PENALTY` 0.5 | cap on any one reliability term | no single signal may zero a recipe out |
| `ATYPICAL_SCALE` 3.0 | robust z at which a system is fully atypical | the usual 3-sigma convention, on a robust scale |
| data-gap `k` | **learned** per id: majority of the packs carrying it | a fixed k means different things for a 2-pack id and a 21-pack id |
| `max_passes` 4 | ring-refeed budget | 2 passes converge on every pack here |

## 11. What later phases can rely on

* `recipe_idx` still joins everything; `recipe_shape.parquet` has exactly the
  kept recipes in `recipe_idx` order.
* Every concrete id and every OR member has a row in `item_values.parquet`,
  values are finite and strictly positive, tiers are finite and ≥ 0.
* `is_leaf` ⇔ `n_producers == 0`; leaves are tier 0.
* `pool_id` is a content hash, so pools are comparable across packs.
* Shape roles, tiers and values are **unsupervised signals, not labels**.
  Phase 0c fits the role and acquirability models; `leaf_features.parquet` and
  the shape columns are its feature matrix.
* `solve_hypergraph` / `solve_to_fixpoint` take `recipe_weight` and `exclude`.
  Phase 0c and Phase 2 push role posteriors back into the economy through them
  without editing this layer.
* Values are comparable **within** a pack, not across packs: the step cost
  compounds through chain depth and multiplicity, so a pack of compressed
  blocks reaches 10^17 while a decoration pack tops out at 10^4.

---

## v1 -> v2 of the value stage (stages 1, 2, 4, 6, 7 unchanged)

v1 reproduced the failure this project exists to fix: `blue_dye_from_cornflower`
lost to the lapis route by 12x, `white_dye` from a flower lost to bone meal by
47x, and `iron_ingot` was priced at exactly cobblestone.  Four causes, four
fixes:

| # | v1 | v2 |
|---|---|---|
| 1 | leaf seeds spanned 119x on fom (unbounded octaves per sigma), so the leaf score outweighed whole chains | seeds in `[1, K]`, `K = --seed-spread` default 2 |
| 2 | `n_forms_of_material` in the composite seeded diamond_ore cheap and inflated nether_star | dropped from the composite, kept in the table; PC1 variance *rose* |
| 3 | recipes were free, so `diamond == diamond_ore` and a 9-deep chain cost what its leaves cost | `step_cost x (1 + tier)` per recipe |
| 4 | every ring was bootstrapped at the **global cheapest leaf seed**, so every metal ingot/nugget/block ring collapsed to cobblestone price | only *closed* rings are bootstrapped, at their own cheapest member's acquirability seed; rings with an outside entry settle normally |

Effect on the ring count alone: generation rings fell from 15.1 % of atm10's
items to 1.4 %, and from 35.3 % to 4.1 % on enigmatica10 — v1 was calling
ordinary two-way conversions "rings with no base case" and pricing them at the
floor.

---

# Phase 0c-1 — the label layer and the feature layer

```
experiments/rulehits-v3/features/<pack>.jsonl ─┐
experiments/svm/data/labeled.jsonl ────────────┤
CalculatorCirrus/chain-review/round-00{1..6}/ ─┼─ labels.py ─▶ out/labels/*.parquet
chain-exports/<pack>/batch-NNN/ ───────────────┘

cache/<slug>/{recipes,contents}.parquet ─┐
out/<slug>/{templates,identity,recipe_template}.parquet ─┼─ features.py ─▶ out/features/*.parquet
out/<slug>/economy/*.parquet ────────────┘
```

`./run_phase0c1.sh` runs both layers over every cached pack, checks that the
LLM labeller sent nothing, and then runs `tests/test_phase0c1.py`.  Phase 0a
and Phase 0b are read-only here; the only edit outside this phase is two
shared helpers in `common.py` (`normalise_name` / `match_name` /
`pack_group_map`).

## 0. The same hard rules, plus one exception

1. **No hardcoded mod / item / material / colour / system-name vocabulary.**
   `features.py` has **no** exemption: every value vocabulary that becomes a
   one-hot (shape roles, match strategies) is read off the data at run time
   and recorded in `feature-catalog.json`, and the L6 hash is fed only tokens
   that are neither an induced class member nor a namespace *the pack itself
   uses*.
   `labels.py` has exactly **one**: the block between
   `BEGIN LABEL-SPACE TABLE` and `END LABEL-SPACE TABLE`.  It renames the
   *label sources' own* category names onto the plan's role set, names the
   source quality tiers, and defines the handful of column names that embed a
   verdict word.  `tests/test_phase0a.py::test_no_rule_vocabulary` extracts
   the tokens that block defines and allows **only those** elsewhere in
   `labels.py`; `test_label_space_table_is_the_only_exemption` additionally
   requires the block to contain no registry-shaped id.
2. **Never join on a recipe id.**  28.8 % of exported recipes have no id, and
   of those that do, 20 % share theirs with another row (a JEI mirror of the
   same recipe under a different system: 138,578 of atm10's 283,422 rows).
   Every join is on `recipe_idx`.  Ids appear in exactly two places that have
   no index — the audit markdown and the chain exports — and there they are
   *resolved* to an index, with every ambiguous and missing resolution
   counted and reported.
3. **Determinism.**  Same inputs ⇒ byte-identical parquet.  Pair sampling is
   seeded from `sha1(canonical JSON)` of `(seed, slug)` and **not** from
   `hash()`, which CPython salts per process.

## 1. `labels.py`

```
--cache-dir --out-dir --packs --write-dir
--stage rules|gold|audit|chains|recipes|templates|pairs   run up to this stage
--max-pairs-per-output 20     cap on sampled sibling pairs per (pack, output)
--seed 20260919
```

### 1.1 The four sources, and what each is worth

| source | scope | rows | what it is |
|---|---|---|---|
| rule replay (`rulehits-v3`) | all 22 packs | 692,608 | the Java builtin rules replayed; `wl ∈ {junk, middle, useful}` plus the fired rule ids.  Weak *by construction* — it is the thing this project replaces |
| gold labels (`svm/data/labeled.jsonl`) | 10 packs | 112,854 | human / adjudicated / subagent / SVM verdicts, one row per `(pack, i)` |
| chain audits (`chain-review`) | all 22 packs | 95 files, 215 findings, 4,326 chain verdicts | rounds 1-5 junk-pattern families, round-006 routing quality |
| LLM labeller | — | 0 | `llm_labels.py`, a stub with a hard budget guard; never called |

**Source quality tiers** (`SOURCE_TIERS`, best first, prefix match so every
`svm-rN` round shares a tier): `adjudication` 0, `human-nb` 1, `audit` 2,
`subagent` 3, `svm-` 4, anything else 5.  The weight of one judgement is
`tier_weight(t) = 1 / (1 + t)` — harmonic, not geometric, so a thousand weak
votes cannot outvote the adjudicated answer by more than 5:1 per vote and all
tiers stay on one comparable scale.

### 1.2 The category → role table (the sanctioned vocabulary)

A gold `category` is a statement about *what the recipe is for*, which is
exactly what the role head has to predict, so the mapping is a rename:

| gold category | role target |
|---|---|
| `real-production`, `real-processing`, `real-crafting` | `production` |
| `variant-swap`, `storage-ladder` | `conversion` |
| `recycling-reverse` | `recycling` |
| `free-generation`, `only-source-generation` | `generation` |
| `display-page`, `mirror-duplicate`, `pseudo-recipe` | `display` |
| `other-junk`, `other-useful`, `chance-gamble`, `audit-flagged` | `unknown` |

`unknown` is a real target, not a missing value: those four categories say
"junk/useful, reason unrecorded" or "flagged at plan level", which is a
statement about the verdict and not about the role.  A template with no
categorised member gets an **empty** role target instead, and is excluded from
the role head's training set.

`human-nb` — 42,616 rows, the second-best tier and the single largest source —
carries **no** category, so it contributes verdicts but never a role.

### 1.3 Resolving the audits

The audits are markdown, so everything is parsed structurally:

* **pack ↔ file mapping** by normalised name (`match_name`: lowercase, keep
  `[a-z0-9]`; exact, then unique prefix, then unique containment).  95 files
  map, 0 unmapped; the mapping is printed and stored in `labels-meta.json`.
* **verdict tables** read their own column order from the header row, because
  rounds 1-6 do not agree on it.  A chain cell is parsed for 4-digit ids with
  range expansion (`0062-0068`), a verdict cell for `ok` / `bad` /
  `questionable` (a cell that hedges — `ok/questionable` — reads as
  `questionable`).
* **chain steps.**  Each `EXECUTE <id> x<n> @ <machine>` line is resolved id →
  `recipe_idx`; when the id is not unique the **machine** (a different export
  field) settles it, which recovers 87-100 % of the collisions.
* **the pairwise labels.**  A routing finding names a recipe the planner chose
  and one it should have chosen, but neither is marked as such in prose.  Two
  structural facts decide it:
  1. **chosen** = a recipe the *cited chain actually executed* — a line in the
     chain export, not an opinion in the markdown;
  2. **preferred** = a recipe the finding names that the cited chain did not
     execute **and that produces the same base id** as the chosen one.

  A finding often names several such pairs on purpose ("these three impure
  recipes all have a pure alternative"), so *every* valid reading is kept and
  each row carries `n_readings` with `weight = 1 / n_readings`, so a finding
  that admits two readings contributes half a vote per reading.

### 1.4 Outputs

`out/labels/`

| file | rows | key columns |
|---|---|---|
| `recipe_labels.parquet` | one per labelled `(pack, recipe_idx)` | `template_id`, `wl`, `rule_hits`, `gold_verdict/category/source/tier/scope/confidence`, `gold_role`, `system_verdict`, `chain_ok_count`, `chain_bad_count`, `audit_family` |
| `template_labels.parquet` | one per `(pack, template_id)`, **all** templates | `n_members`, `n_labeled`, `n_junk`/`n_useful` overall and per tier, `majority_verdict`, `purity`, `conflict`, `best_tier`, `label_weight`, `junk_target`, `role_target`, `role_purity`, `role_weight`, `wl_*`, `chain_*`, `audit_family` |
| `pairwise_gold.parquet` | one per round-006 reading | `output_base_id`, `chosen_recipe_idx`, `preferred_recipe_idx`, `severity`, `category`, `finding_id`, `n_readings`, `weight` |
| `chain_verdicts.parquet` | one per audited chain per round | `round`, `batch`, `chain_id`, `verdict`, `category`, `recipe_idx` (the steps), `n_recipes` |
| `pairs.parquet` | the ranker's training pairs | `output_base_id`, `a_recipe_idx`, `b_recipe_idx`, `label`, `tier`, `weight`, `n_producers` |
| `labels-meta.json` | — | counts, timings, the pack mapping, id-resolution counters, unresolved findings with the reason |

Every table carries `pack` **and** `pack_group`.  A group is one
content-identical export, found by the sha1 of the pack's template multiset
(the same fingerprint Phase 0b uses) and named after the lexicographically
smallest slug in it, so a later split can be group-aware without ever looking
at a pack name.  22 packs ⇒ 21 groups.

### 1.5 Template aggregation

```
w_i      = 1 / (1 + tier_i)                      trust in one judgement
purity   = Σ w_i over the majority / Σ w_i        do the members agree?
best     = min tier over the labelled members     who is the best source?
weight   = purity × 1/(1+best)                    what the template is worth
```

`junk_target` is 1 / 0 / NaN from `majority_verdict`; `role_target` is the
weighted majority over the members that carry a category, with ties broken on
the sorted role name so two runs agree.

### 1.6 Pair construction

Pairs only exist between **siblings** — two producers of the same output base
id — because that is the only choice the router ever makes.  Three of the four
tiers need a label on *both* sides, so the cross product of the labelled sets
is enumerated (or sampled, when it is larger than the cap) instead of sampling
sibling pairs blindly and hoping a label lands.

| tier | rule | base weight |
|---|---|---|
| `gold` | `pairwise_gold`: preferred ≻ chosen | `1 / n_readings` |
| `silver` | gold verdict `useful` ≻ gold verdict `junk` | `1/(1+max(tier_a, tier_b))` |
| `chain` | a recipe an audited **ok** chain executed ≻ a junk-labelled sibling | 0.5 |
| `bronze` | `wl useful` ≻ `wl junk` (`middle` is skipped) | 0.1 |

Precedence is gold > silver > chain > bronze and a pair takes the best tier
that applies: `chain` outranks `bronze` because an audited chain is a human
judgement and `wl` is the rule replay.  Orientation is a seeded coin flip, so
`label` is +1 or −1 in roughly equal numbers instead of +1 by construction.
The per-output cap (`--max-pairs-per-output`, default 20) is spent
best-tier-first.

## 2. `features.py`

```
--cache-dir --out-dir --packs --write-dir
--stage l1|economy|resources|intent|lexical|recipes|write
```

One row per `(pack, template_id)`, float32, 129 features in six layers, plus a
per-recipe matrix for the pair model.

| layer | n | what it reads |
|---|---|---|
| L1 ontology | 34 | the template **signature** alone — slot counts, material classes, how much of it the ontology could abstract, variant classes, residual `rid` tokens, content-kind shares, match-strategy shares, machine presence |
| L2 conservation / economy | 21 | `recipe_shape.parquet`, aggregated over the members: shape-role shares, material preservation, count ratio, round trip, self-dependence, values, reliability |
| L3 progression | 8 | tiers: recipe, machine, deepest input, reverse-tier share, tier jump, unbuildable machines |
| L4 resources | 9 | `item_values` / `pools`: leaf inputs, leaf acquirability, data-gap inputs, conversion-pool membership and pool size |
| L5 intent | 23 | namespace agreement, whether the system is the namespace's primary production system, **sibling z-scores**, and the ten Phase 0b system-shape columns |
| L6 lexical residual | 34 | 32 hash buckets over the residual tokens, plus the two counts |

### 2.1 Three definitions worth stating

**Values are centred per pack.**  Phase 0b values are comparable within a pack
only (the step cost compounds through chain depth, so one pack reaches 10¹⁷
and another 10⁴).  Every value column is `log(v) − median(log v)` over the
pack, so the column means the same thing everywhere.

**The sibling z-score.**  For one output base id, every producer is scored
against the mean and the *population* standard deviation of the producers of
that output, itself included, on four axes: `n_inputs`, centred `log value_in`,
`tier`, `n_output_slots`.  A recipe with several outputs averages its z over
them; a template averages over its members and also keeps the per-axis
`absmax`.  A lone producer, and a group whose producers all agree, score
**0, not NaN** — "no sibling disagrees with me" is information, not a missing
value.  (The leave-one-out variant differs by a factor `n/(n−1)` and is
undefined at n = 2.)

**The L6 hash.**  Bucket = `int.from_bytes(sha1(token), "big") % 32`.  sha1 and
not `hash()`, which is salted per process.  The input is the template's
residual `rid` tokens plus the residual tokens of its **system path**, where
"residual" means: not a member of any induced token class **and not a
namespace the pack uses**.  The second filter is load-bearing: a system id
that renders another registry's page carries that registry's namespace as a
*path segment*, and without the filter one bucket became "this mod" — on
atm10 it correlated −0.80 with the gold junk target.  The namespace set is the
pack's own (every namespace on any item id, recipe id or system id), so no
list is written down.

### 2.2 Outputs

`out/features/`

| file | rows × cols | notes |
|---|---|---|
| `template_features.parquet` | 240,377 × 133 | `pack`, `pack_group`, `template_id`, `n_members` + 129 float32 features |
| `recipe_features.parquet` | 693,952 × 42 | `pack`, `pack_group`, `recipe_idx`, `template_id`, `system`, `recipe_ns`, `shape_role` + 35 float32; the trainer builds `f(a) − f(b)` from these and joins the template matrix on `template_id` |
| `feature-catalog.json` | — | name, layer, description, aggregation per feature; plus the hash spec, the run-scoped match-strategy vocabulary and the shape roles |
| `features-meta.json` | — | timings, peak RSS, per-column NaN rate, columns absent in some pack |

A one-hot column for a value a pack never has is written as **0, not NaN**:
the pack does answer the question, with "none".  Two such columns exist
(`l2_role_generation_share` absent in 7 packs, `l2_role_noop_share` in 5).

## 3. Thresholds, and why

| knob | value | reason |
|---|---|---|
| `tier_weight` | `1/(1+tier)` | harmonic decay keeps all five source tiers on one scale; geometric decay would make `svm-` rounds worth nothing and stop them breaking ties |
| pair tier weights | gold `1/n_readings`, silver `1/(1+worst tier)`, chain 0.5, bronze 0.1 | bronze is the rule replay — the thing being replaced — so it may break ties and nothing more |
| `--max-pairs-per-output` | 20 | one output with 900 producers would otherwise contribute 400 k near-identical pairs |
| L6 buckets | 32 | enough to separate the frequent residual tokens without one column per rare token; collisions are the point, not a defect |
| pair orientation | seeded coin flip | a pair file that is +1 by construction trains a constant |
| chain-step machine tie-break | on | recovers 87-100 % of the ambiguous ids; without it 25 % of all chain steps are unusable |
| gold-pair same-output constraint | required | without it the prose's incidental ids (the inputs it blames) become "preferred"; it turned 4 of 12 extracted pairs from wrong to right |

## 4. What later phases can rely on

* `(pack, recipe_idx)` and `(pack, template_id)` are unique in every table, and
  `template_features` and `template_labels` have **exactly** the same key set,
  so the join is 1:1 with no filtering.
* `pack_group` is on every table; a group-aware split is a `groupby`.
* No feature column contains an infinity; the NaN rate of every column is in
  `features-meta.json`, and the worst is `l4_leaf_acq_*` at 84 % (most
  templates simply have no leaf input).
* `junk_target` / `role_target` / `label_weight` / `role_weight` are the four
  columns a template-level head needs; `pairs.parquet` is the ranker's.
* Both layers are pure functions of their inputs: two runs are byte-identical.

---

# Phase 0c-2 — the models

```
out/features/{template,recipe}_features.parquet ─┐
out/labels/{template_labels,pairs,pairwise_gold, │
            recipe_labels}.parquet ──────────────┼─ train_eval.py ─▶ out/models/metrics-*.json
out/<slug>/economy/leaf_features.parquet ────────┤
out/models/recipe_leaf_agg.parquet ◀─ pair_features.py
                                                 │
                                                 └─ export_model.py ─▶ out/models/*.json
                                                                       │
                                                    predict_reference.py (reads the JSON only)
```

`./run_phase0c2.sh` runs the aggregation, every training stage, the export and
`tests/test_phase0c2.py`.  Phase 0a/0b/0c-1 are read-only; the only file this
phase adds outside `out/models/` is nothing at all.

## 0. The same hard rules

1. **No hardcoded mod / item / material / colour / system vocabulary.**  The
   four new modules introduce **no** new word: every label-space name they use
   (`junk_target`, the five role names, the four pair tiers, the verdict words)
   is *imported* from `labels.py`'s single sanctioned table.
   `tests/test_phase0a.py::test_no_rule_vocabulary` now covers all ten modules;
   `LABEL_SPACE_USERS` lists the ones allowed to use — never extend — that
   table, and `TOOLING_WORDS` allows exactly one software-engineering term
   (*golden*, in the "golden file" sense) that the forbidden-word regex
   catches as a false positive.
2. **Group-aware evaluation only.**  Every split is on `pack_group`, and the
   test asserts that each fold's train count plus the held-out group's row
   count is exactly the labelled total, so a group can never be on both sides.
3. **Gold is evaluation only.**  `load_pairs` drops the gold tier before
   anything is fitted, and the test additionally asserts that no gold recipe
   pair reappears under another tier.
4. **Determinism.**  Fixed seed (`20260919`), sorted group order, seeded
   sampling, no `hash()`.

## 1. `pair_features.py` — the one added aggregation

Phase 0c-1 reduced the Phase 0b leaf table to three per-recipe numbers, two of
which (`l4_leaf_acq_mean`, `l4_leaf_acq_min`) *are* the unsupervised composite
this phase is meant to replace.  The ranker needs the **raw** leaf columns, so
this module recomputes, per `(pack, recipe_idx)`, the **mean and min over the
recipe's leaf inputs** of every structural column of `leaf_features.parquet`
except three:

| excluded | why |
|---|---|
| `acquirability` | the unsupervised score itself — fitting it onto itself |
| `seed_value` | its exponential map, same argument |
| `n_forms_of_material` | Phase 0b excluded it from its own composite: it measures how *core* a material is, not how cheap |

Ten columns survive (`containing_packs`, `leaf_pack_fraction`,
`min_consumer_tier`, `n_consumer_ns`, `n_consumer_systems`, `n_consumers`,
`n_tags`, `n_variant_keys`, `producer_packs`, `renewable`), so the output is
`leaf_agg_n` + 20 aggregates.  Leaf-input resolution is a copy of
`features.py::stage_resources`: input-side content rows, a `tag` represented by
its lexicographically smallest member, matched against `item_values.is_leaf`.

Output `out/models/recipe_leaf_agg.parquet`, 693,952 × 23 — exactly one row per
recipe, asserted by the test.

## 2. `train_eval.py`

```
--stage junk|recipe|role|zeroshot|ablation|pairs|leaf|all
--quick        3 pack groups and 20k pairs — a smoke test, never a result
--out-dir --cache-dir
```

### 2.1 Sample weighting

```
w_i = label_weight_i x scale(group_i)
```

`scale` is found by water-filling: any pack group whose share of the total
weight exceeds `GROUP_WEIGHT_CAP = 0.40` is scaled down to exactly that share
*given the other groups' current mass*,

```
scale_g  <-  scale_g x (cap / (1 - cap)) x (T - W_g) / W_g
```

repeated until every share is under the cap.  Groups under the cap are never
scaled **up**: a group with 78 labelled templates does not get the same say as
one with 15,533, it only stops being drowned.  atm10 carries 69 % of the gold
verdicts and 94 % of the pairs, so without this every model is an atm10 model.

### 2.2 Imputation, and what it costs

Both arms see the **same** matrix: NaN is replaced by the training fold's
column median before anything else.  That is a deliberate simplification for
the Java port (one fill vector, no missing-value branch in the tree walker),
and it does cost the tree arm the missingness signal that 84 % of templates
carry on `l4_leaf_acq_*`.  It is affordable because the *non*-NaN column
`l4_leaf_input_share_mean` already answers "does this template have a leaf
input at all".

### 2.3 Evaluation protocol

* **LOPO** — leave one pack group out, over the 10 groups that carry labels.
  Trained on the other groups' labelled templates, predicted over the **whole**
  held-out group so the recipe-level pass has a prediction for every member.
* **Operating points** — from the pooled out-of-fold PR curve: the highest
  recall at precision >= 0.95, the highest precision at recall >= 0.90, and the
  best-F1 point.
* **Recipe level** — each template's out-of-fold prediction propagated to its
  member recipes and scored against `recipe_labels.gold_verdict`.  This is what
  the planner actually consumes.
* **New-mod zero-shot** — the five recipe namespaces with the most labelled
  templates, each held out whole while training on every pack group.  The
  namespaces are ranked, never named.
* **Ablations** — LOPO pooled AUC with L1, L3 or L6 removed entirely.

### 2.4 The ranker

Pair feature = `f(a) - f(b)` over the per-recipe block (Phase 0c-1 recipe
matrix + the Stage-B leaf aggregates, prefixed `rec_`) and the template block
(prefixed `tpl_`), plus eight named pair flags:

```
flag_same_template  flag_same_system  flag_same_ns          (symmetric)
flag_d_tier  flag_d_n_inputs  flag_d_log_value_in
flag_d_n_leaf_inputs  flag_d_leaf_acq_min                   (antisymmetric)
```

`tier`, `n_input_slots`, `log_value_in`, `l4_leaf_acq_mean` and
`l4_leaf_acq_min` are removed from the base block, because their difference
*is* the flag and keeping both would split one weight over two collinear
columns.  188 pair features in total.

**Antisymmetrisation.**  Both orientations are included with flipped labels and
the linear arm is fitted without an intercept, so the model satisfies
`m(a,b) = -m(b,a)` by construction.  The three *symmetric* flags keep their
value under the swap, which drives their linear weight to zero — a
mathematical consequence, not a bug, and the reason they are still exported:
the tree arm is not constrained and does use them.  The tree arm is
antisymmetrised at inference instead: `m = 0.5 (raw(a,b) - raw(b,a))`.

**Tier weights.**  The pair's own `weight` column is normalised to mean 1
inside its tier, then multiplied by `TIER_MULT`: silver 1.0, chain 0.5,
bronze 0.1.  So the multiplier *is* the mean say of one pair of that tier: an
audited chain is worth half a two-verdict comparison, and the rule replay —
the thing this project replaces — is worth a tenth and may only break ties.

**Gold** (19 readings) is evaluation only.  One reading is dropped by hand:
`DROPPED_GOLD_READINGS` addresses it as `finding id -> reading index` in the
deterministic order `labels.py` wrote, never by registry id, and the reading
count is asserted before the drop so an upstream change trips instead of
silently dropping the wrong row.

**Cost-only baselines.**  Rank by Phase 0b `value_in`, and by
`value_in + step_cost x (1 + tier)` — the same charge `economy.py` puts on
running a recipe.  This is what learning has to beat.

### 2.5 The supervised leaf scorer

The LR ranker is linear in the scaled pair differences, so its weight on a raw
column is `coef / scale`.  The weights on the **`_mean`** leaf aggregates are
the per-leaf model (`mean_c(a) - mean_c(b)` is exactly that per-leaf property
differenced; the `_min` aggregate is an order statistic of a whole recipe and
has no per-leaf reading, so it is reported but not used):

```
s    = w . z                      raw leaf columns
acq  = sigmoid((s - mu) / sigma)  mu, sigma over the pack's own leaves
seed = K ** (1 - acq)             K = --seed-spread, 2.0
```

so the band is exactly the `[1, K]` `economy.py` defines and the two seeds are
directly comparable.  The acceptance cases are re-scored by calling
`economy.py` **as a library** — `build_graph` / `compute_shape` / `incidence` /
`solve_tiers` / `compute_reliability` / `compute_values` — with only the seed
array swapped, so the number reported is a producer cost out of the same
solver, not a re-implementation.  Producer cost of a recipe is
`sum(input value x amount) + step cost` = what committing to that producer
costs once its inputs are priced.

## 3. `export_model.py` — the JSON schemas

### `feature-spec.json`

```json
{"version": "0c2.1",
 "template_features": {"names": [...129], "layers": [...], "fill": [...],
                       "mean": [...], "std": [...], "nan_policy": "..."},
 "pair_features": {"names": [...188], "n_base": 180, "flags": [...8],
                   "symmetric_flags": [...3], "recipe_fill": [...],
                   "construction": "..."},
 "leaf_features": {"names": [...10], "excluded": [...3], "construction": "..."},
 "hash": {"buckets": 32, "function": "sha1(token) mod buckets", "input": "..."},
 "match_strategies": [...], "shape_roles": [...]}
```

`hash`, `match_strategies` and `shape_roles` are copied verbatim from
`feature-catalog.json`, so the Java feature builder has one source of truth for
the L6 bucketing and the two run-scoped one-hot vocabularies.

### `template-role.json` / `producer-ranker.json`

```json
{"version": "...",
 "junk": {"default": "lr" | "lgb", "why": "...",
          "lr":  {"kind": "lr",  "features": [...], "coef": [[...]],
                  "intercept": [...], "fill": [...], "mean": [...], "std": [...]},
          "lgb": {"kind": "lgb", "features": [...], "n_trees": N,
                  "n_classes": 1, "fill": [...], "trees": [...]}},
 "role": {..., "classes": ["production", "conversion", ...]}}
```

A tree is flattened out of `booster.dump_model()` into six parallel arrays a
Java loop can walk:

```json
{"root": 0, "split_feature": [...], "threshold": [...], "default_left": [...],
 "left": [...], "right": [...], "leaf_value": [...]}
```

`left[i]` / `right[i]` hold a child **node index** when `>= 0` and
`-(leaf + 1)` when the child is a leaf.  Only numerical `<=` splits are
exported; anything else raises rather than exporting a model the reference
cannot reproduce.  Each file is capped at 5 MB by dropping trailing boosting
rounds (recorded as `trimmed_trees`, never silent).

The ranker file is the same shape with `scale` instead of `fill/mean/std`, a
zero intercept, and `symmetric_flags` at the top level.

**Default arm.**  The linear arm is the default unless LightGBM beats it by
more than `LR_TOLERANCE = 0.02` on that model's headline LOPO metric (junk:
pooled AUC; role: pooled macro-F1; ranker: pooled silver accuracy).  The
choice and the two numbers are recorded in `manifest.json`.

### `leaf-acquirability.json`

`features`, `weights` (one per feature, raw-column scale), `spread`,
`standardisation`, `source`, and the per-pack Spearman against the
unsupervised ranking.

### `golden-sample.json`

The Java parity fixture: 1,000 template rows sampled evenly across the pack
groups, 500 pairs, 300 leaves.  Every feature value is rounded to 6 decimals
**before** the expected outputs are computed, so the file is self-contained —
a port that reads these exact numbers must produce these exact outputs.  Under
3 MB (compact JSON, no indentation).

### `manifest.json`

Version, training date, `data_hash` (sha1 over the size + sha1 of all nine
input tables), library versions, row counts, the three default-arm choices with
their numbers, the metrics files on disk, every exported size, and the export
wall-clock.

## 4. The reference-inference contract

`predict_reference.py` imports **numpy and nothing else** — no sklearn, no
lightgbm, no pyarrow — and is asserted to do so.  Four primitives:

| model | inference |
|---|---|
| linear, binary | `p = sigmoid(((x filled) - mean) / std . w + b)` |
| linear, multiclass | `p = softmax(z . W^T + b)` |
| tree ensemble | `raw = sum of the leaf reached in each tree`; `p = sigmoid(raw)`, multiclass `tree i` belongs to class `i mod n_classes` then `softmax` |
| ranker | `m = (x / scale) . w`, no intercept; tree arm `0.5 (raw(x) - raw(swap(x)))` |

`swap` negates every pair column except `symmetric_flags`.  NaN is replaced by
`fill[i]` first, so no NaN reaches a tree; `default_left` is exported and
honoured anyway.

`predict_reference.py --` verifies itself against `golden-sample.json` and is
run by the test suite at tolerance **1e-6**; the observed worst error is
6e-15.  **A Java port is correct exactly when it reproduces that file the way
this module does.**

## 5. Thresholds, and why

| knob | value | reason |
|---|---|---|
| `GROUP_WEIGHT_CAP` | 0.40 | one pack group must not be able to out-vote the other nine; 0.40 still lets the largest group lead |
| NaN fill | training-fold column median | one vector for both arms and for Java; costs the tree arm the missingness split |
| LightGBM | <= 300 trees, <= 31 leaves, lr 0.05, early stop 30 | the plan's cap; early stopping on an inner *group* split when >= 3 groups remain, else a seeded 80/20 row split (the outer fold is untouched either way) |
| LightGBM fallback | fixed 300 rounds | when early stopping finds no improving round, a fixed budget beats exporting a constant model |
| `TIER_MULT` | silver 1.0, chain 0.5, bronze 0.1 | after per-tier mean normalisation the multiplier *is* the mean say of one pair of that tier |
| pair flags | 8, four of them removing their source column | the difference of a column *is* the flag; keeping both splits one weight |
| `LR_TOLERANCE` | 0.02 AUC | a dot product ports to Java in ten lines, 300 trees do not; the gap has to be worth it |
| JSON caps | 5 MB per model, 3 MB golden sample | trailing boosting rounds are dropped and recorded |
| reference tolerance | 1e-6 | far above the observed 6e-15, so the contract survives a different float-summation order in Java |

## 6. What the Java phases can rely on

* `out/models/feature-spec.json` is the complete feature contract: order,
  layer, fill, standardisation, the L6 hash spec and the two run-scoped
  vocabularies.  Rebuild features from it, never from a hardcoded list.
* Every model file carries **both** arms plus a `default`; switching arm is a
  config change, not a re-export.
* The tree format is six flat arrays; the walker is ten lines and is written
  out in `predict_reference.py::walk_tree`.
* The ranker is antisymmetric by construction for the linear arm and by
  explicit averaging for the tree arm; a port that skips the averaging will
  disagree with `golden-sample.json` and the fixture will say so.
* `leaf-acquirability.json` plugs straight into `economy.py --seed-spread`:
  same band, same `K`, same per-pack normalisation.
* The models are **unsupervised-signal consumers, not replacements**: the
  economy layer still produces `value_in`, `tier` and `reliability`, and the
  ranker's job is to reorder producers the cost function alone gets wrong.

---

# Phase 3 — the hierarchical planner

```
cache/<slug>/{recipes,contents}.parquet ──┐
out/<slug>/{identity,recipe_template}.pq ─┤
out/<slug>/economy/*.parquet ─────────────┼─ planner.py ─▶ out/<slug>/planner/*.parquet
out/features/*.parquet ───────────────────┤                a Plan (JSON + `### CHAIN` text)
out/models/*.json ────────────────────────┘
chain-exports/<pack>/batch-NNN/ ──────────── eval_planner.py ─▶ out/planner-eval/*.json
```

`./run_phase3.sh` builds the per-pack caches, runs the A/B and the audit
cases, and then the invariant checks.  Phases 0a–0c-2 are **read-only**: this
layer adds `planner.py`, `eval_planner.py`, `tests/test_phase3.py` and
`run_phase3.sh`, and two lines to `tests/test_phase0a.py` extending its
no-vocabulary check to the two new modules.

## 0. The same hard rules

1. **No hardcoded mod / item / material / colour / system vocabulary.**  The
   five role words come from `labels.py`'s one sanctioned label-space table
   (imported, never restated); everything else is an export-schema field name
   or a Phase 0a/0b column name.  `tests/test_phase0a.py::test_no_rule_vocabulary`
   now covers 12 modules.
2. **No join on recipe ids.**  `recipe_idx`, `template_id` and `base_id` only.
   The single exception lives in `eval_planner.py` and is *evaluation-side*:
   a baseline chain line carries no other key, so its id is resolved back to a
   `recipe_idx` and the resolution rate is reported (fom: 100 %, corpus: see
   REPORT).
3. **Determinism.**  Stable sorts, seeded sampling, tie-breaks by `recipe_idx`
   / `base_id`, `craftTimes` in `int64`, timings by `time.perf_counter`.

## 1. Two layers, one pass

The plan text's two layers map onto two passes over the same index:

| layer | question | code | output |
|---|---|---|---|
| **material** | *which materials, and how much* | `solve_cost` | `cost[item]`, `seq[item]` |
| **form** | *which concrete form, made by whom* | `choose_producer` | one producer per demand |
| **extraction** | *in what order, with what on hand* | `plan_target` | the action list |

The material layer is **request-scoped but pack-wide**: one hypergraph
fixpoint prices every item at once, so the per-target work is only the
extraction walk.  That is what buys the 0.17 ms median on fom and 1.25 ms on
atm10 (a per-target re-solve would be ~7 ms and ~600 ms).

## 2. `PackIndex` — the build-time cache

One object per pack, built from parquet and cached where it is expensive.

| block | contents |
|---|---|
| items | sorted `base_id`, `value_fitted`, `tier`, `is_leaf`, `pool_id`, `acquirability`, `data_gap`, `n_producers`, `n_consumers` |
| OR nodes | member list, member **item indices** and their `set` (so "do I hold any member" is one set intersection), and the representative |
| recipes | CSR inputs `(node, amount)` and outputs `(item, amount, chance)`; machines and their tiers; `shape_role`, `tier`, `max_input_tier`, `reverse_tier`, `reliability`, `unique_producer`, `self_dependent`, `template_id`, `system` |
| producers | CSR `item -> recipes`, with the effective output amount |
| pools | strong-pool membership and `pool_edges` (`src`, `dst`, `unit_cost`) |
| model | `junk_p`, `role_p`, `role_argmax`, `eff_role`, `producer score` |

**OR-node representative.**  The cheapest member by `value_fitted`, ties to the
most-consumed, then to the id — one sort key over three stable columns.  The
full member list is kept, because stock matching needs it.

**Effective role.**  The role head's argmax where the Phase 0c-2 report says it
is usable (`production`, `display`); for every other argmax the Phase 0b shape
role decides, renamed through one table (`recycling_shaped -> recycling`,
`noop -> display`).  That is REPORT Phase 0c-2 issue 6 implemented literally.

### 2.1 Cached model outputs

`out/<slug>/planner/template_posteriors.parquet` — one row per template of the
pack: `junk_p` plus one column per role class.
`out/<slug>/planner/producer_scores.parquet` — one row per recipe.
`planner-index-meta.json` carries the arm choice, the group count and the
timings; a stamp over (arms, recipe count) invalidates the cache.

### 2.2 Producer score — Bradley–Terry, in O(n)

The ranker answers *pairs*; the planner needs one number per producer.  For an
output with producers `P` the pairwise margins form an antisymmetric matrix
`M`.  The Bradley–Terry strengths `θ` that best explain `M` in least squares
are its **row means**, `θ_a = mean_{b≠a} M[a][b]` — the closed form for a
complete antisymmetric comparison matrix.  A recipe producing several outputs
takes the mean of its per-output scores.

The linear arm makes that exact *and* cheap.  Its margin is
`((f(a) − f(b)) / scale) · w`, which over the 180 base columns is `S(a) − S(b)`
with `S(r) = (fill(f(r)) / scale) · w`; the row mean is then
`S(a) − mean(S)`, and the O(n²) matrix is never formed.  The eight pair flags
are handled exactly, not dropped:

* five are differences of per-recipe scalars **zeroed when either side is
  NaN** (`d_tier`, `d_n_inputs`, `d_log_value_in`, `d_n_leaf_inputs`,
  `d_leaf_acq_min`).  With `c_a = #{b≠a : b finite}` and `T` the sum of the
  finite values, the row-mean term is `w · (c_a·v_a − (T − v_a)) / (n−1)` when
  `v_a` is finite and 0 otherwise.  This matters: `d_leaf_acq_min` carries the
  second-largest weight in the model (−1.773) and is NaN on 70 % of fom's
  recipes.
* three are symmetric equality flags (`same_template`, `same_system`,
  `same_ns`); their row-mean term is `w · (count of equal peers) / (n−1)`.
  Their fitted weights are 6.9e-4, 5.3e-6 and 4.5e-8 — the antisymmetrisation
  drove them to zero, as designed — so they contribute nothing but are
  computed anyway rather than assumed away.

`tests/test_phase3.py` asserts the O(n) score equals the row means of the
**exact** matrix built through `predict_reference.Reference.margin`: max
|Δ| = 1.8e-14 over 83 fom groups.  The tree arm is not implemented here (it is
not antisymmetric in closed form); asking for it raises rather than
approximating.

*Complexity note for the Java port:* the naive form is 25.6 M pairs on atm10
(max group 3,746).  The decomposition makes it one 283 k × 180 matrix–vector
product.

## 3. The material layer — `solve_cost`

Knuth's generalisation of Dijkstra over the recipe hypergraph, the same shape
`economy.py` uses, with four additions.  Every term is named:

```
step_unit   = step_cost × median(leaf seed)                    # pack constant
inputs(r)   = Σ over input nodes of cost[node] × amount
steps(r)    = step_unit × (1 + tier[r])
machines(r) = machine_cost × step_unit × Σ machine_tier[m]     # m not yet paid
tierpen(r)  = tier_penalty × step_unit × max(0, max_input_tier[r] − out_tier(r))
              × (2 if reverse_tier[r] else 1)
weight(r)   = max reliability in pack / reliability[r]
rolemult(r) = recycling_mult if role = recycling
              generation_mult if role = generation
              1 otherwise
cost(r)     = (inputs + steps + machines + tierpen) × weight × rolemult
cost[item]  = min over producers of cost(r) / (output amount × chance)
cost[leaf]  = the Phase 0b acquirability seed (`value_fitted` of that leaf)
cost[stock] = 0
cost[OR]    = min over members
```

`out_tier(r)` is the smallest Phase 0b item tier among `r`'s outputs.  The
defaults are `step_cost 0.25` (Phase 0b's), `machine_cost 0.25`,
`tier_penalty 1.0`, `recycling_mult 8.0`, `generation_mult 1.0`.

**Machine footprint.**  The plan text asks that the *first* use of a machine
pay its tier and later uses pay nothing.  During the solve there is no single
plan, so the set is filled greedily: a machine is marked paid the first time a
recipe using it improves any item.  Because Dijkstra settles in non-decreasing
cost, the machines that go free are the shallow ones and a deep, high-tier
machine still pays when it is first reached.  This is an **approximation** and
is labelled as one; the form layer makes it exact, charging only machines the
*plan under construction* has not used yet, and the extraction reports the
exact footprint.

**Settle order is the acyclicity certificate.**  `seq[node]` is 0 for a node
that never settles and 1, 2, 3… in settle order otherwise.  A producer `r` is
admissible for item `X` only if **every** input node has `seq < seq[X]`, unless
the ledger already holds that input.  Because `seq` is a total order, a plan
built under this rule cannot contain both `A → B` and `B → A`: round trips are
impossible *by construction*, and the guard that looks for them is an
assertion, not a filter.  This single rule replaced three earlier partial ones
(a same-item ban, a net-loss ban, and an "is it on the expansion stack" test),
each of which leaked — see REPORT Phase 3 "Three bugs the invariants found".

**Storage ladders.**  A pool's conversion recipes are ordinary edges in the
solve.  Settling once, in non-decreasing order, already makes a ladder
impossible: `cost[unit] < cost[block]`, so the unit settles first and the
`block → 9 units` edge can never improve it.  The pool rule then says what to
do with a *demand* inside a pool: it is served by the pool's cheapest
production entry plus a simple conversion path, which is exactly what the
Dijkstra route is, and never by a round trip, which the `seq` rule forbids.

**Rings and orphans.**  A leaf, a producerless item, and an item Phase 0b
marked `value_source = "cycle"` all enter the queue at the Phase 0b
acquirability seed: all three mean "nothing in this pack makes it".  Reading
Phase 0b's own ring verdict instead of re-deriving it is deliberate — it is
one decision, made once, and re-implementing it here would let the two layers
disagree.  What the candidate policy orphans afterwards is cleaned up by up
to `MAX_BOOT_ROUNDS` (24) extra rounds, each seeding either every orphan (an
item no live producer can reach) or every **ring entry** — an unsettled item
nothing it still waits on is cheaper than, which is `economy.py`'s "cheapest
member of a closed ring" evaluated locally so that independent rings are all
entered in the same round.  Measured: fom needs 0 rounds, atm10 and
atm10-lite need 1 (967 and 1,012 items), and after it every item in all
three packs has a finite cost.  Items seeded this way are flagged
`gathered`, and extraction writes them to `[RAW]` rather than expanding them
— they really are "go and get it".

**Memo key.**  The solve is memoised per
`(pack fingerprint, policy signature, knob signature, stock signature)`, where
the stock signature is the stable hash of the sorted `(base_id, quantity)`
multiset of the *request's initial* stock.  The per-item dimension of the memo
is materialised as the dense `cost[]` / `seq[]` arrays, so "memoised per (item,
remaining-stock-signature)" is one array lookup.  The ledger changes during
extraction; the cost arrays do not — prices are a routing heuristic, the ledger
is the truth about what is on hand, and keeping them apart is what makes one
solve serve a whole batch of targets.

Stock enters as a zero-cost seed.  Quantity is deliberately *not* modelled in
the price (an item you hold is free to route through however much you hold);
the ledger enforces the quantity during extraction.

## 4. Candidate policy

```
flagged  = junk_p > τ_junk  or  P(display) > τ_display  or  effective role = display
protected = for each output whose producers are ALL flagged, the one with the
            lowest junk_p (ties by recipe_idx)
excluded = flagged and not protected
```

`τ_junk` defaults to **0.8707**, the LightGBM arm's recipe-level P ≥ 0.95
operating point from `out/models/metrics-recipe.json` (recall 0.849);
`τ_display` defaults to 0.5.  Both are CLI-overridable, and the arm is a CLI
option (`--arm-junk`, `--arm-role`, `--arm-rank`) defaulting to the manifest's
choice.

The display role is inside `flagged` rather than a separate gate so that the
unique-producer protection covers it too.  It has to: the only producer of a
recoloured item is a recolour recipe, which the role head calls a conversion
and the junk head scores at 0.87 — protecting it is the difference between a
plan and an `UNRESOLVED`.

Role handling after that:

| role | treatment |
|---|---|
| `production` | normal |
| `conversion` | allowed as a producer, but **demoted out of the shortlist** whenever a non-conversion candidate exists and the ledger holds none of its inputs |
| `recycling` | last resort: an ×8 cost multiplier, and dropped from the shortlist whenever any non-recycling candidate exists |
| `generation` | allowed, priced with the same reliability weight (`generation_mult` 1.0) |
| `display` | excluded, unless protected as a sole producer |

**Deviation, stated.**  The brief reads "`conversion` edge usable *only* when
its input form is already in stock or byproducts".  Taken literally, no storage
block is ever craftable from empty stock, because "9 units → 1 block" *is* a
conversion recipe; every pool would become `UNRESOLVED`.  The brief's own §3
resolves it ("a demand for an item in a conversion pool is served by the pool's
cheapest production entry unless a pool member is in stock"), so the rule is
implemented as **demotion**, not prohibition: a conversion route may serve a
demand no other route can, but it never wins over a production route unless the
ledger already holds its input.  `--conversion-needs-stock 0` turns the
demotion off for ablation.

## 5. The form layer

For a demanded item:

1. collect the admissible producers (policy, `seq` rule, net-loss ban);
2. price each with `producer_cost` (the formula of §3, with the **exact**
   machine term for the plan so far);
3. shortlist everything within `cost ≤ γ · best` (γ default 2.0);
4. inside the shortlist, sort by producer score descending, then by cost, then
   by `recipe_idx`;
5. record the runner-up, the gap, and every rejected producer with its reason.

γ is the whole ranker/cost trade-off in one number: γ = 1 is cost-only,
γ = ∞ is ranker-only.  The Phase 0c-2 measurement that forces this shape is
that pure cost minimisation ranks producers at **0.137** on the rule replay —
junk recipes have *cheaper* inputs than their useful siblings — so the ranker
must reorder **after** the cost solve, and the junk posterior must exclude
producers rather than reprice them.  Neither is folded into the leaf seed,
because Phase 0c-2 Stage C showed re-seeding leaves moves nothing.

## 6. Extraction

A recursive top-down walk with a ledger (`ResourceLedger`), keyed by **item
index** so an any-of input costs one set intersection whatever the tag's size.

```
demand(item, qty, depth):
    qty -= ledger.take(item, qty)                      # stock, then byproducts
    if qty <= 0: return
    if leaf, or no producer, or cost[item] = inf:  raw[item] += qty; return
    if item is on the expansion stack:  UNRESOLVED(SELF_DEPENDENT)
    if expansions >= budget:  raise the budget from the cone once, else
                              UNRESOLVED(BUDGET_TRUNCATED)
    r = choose_producer(item)
    times = ceil(qty / (out_amount × chance))          # int64
    resolve every input slot, FOLD by resolved item, demand each once
    credit every output × times to the ledger (surplus of the demanded item,
        and every byproduct in full)
    emit the action
```

Three details that are load-bearing:

* **Fold the demands, print the slots.**  Four glass slots are one demand for
  four glass, not four demands for one.  Without this the prototype emitted
  three separate one-ingot smelts for a three-ingot bucket.  The printed
  `in :` row stays per slot, like the baseline's.
* **`cost[item] = inf` means raw.**  Nothing in the pack makes it from leaves —
  a closed ring, or an item whose only producer consumes it — so writing it
  down as "gather this" is the truth, and `raw_nonleaf` counts exactly those
  on both sides of the A/B so the comparison stays honest.
* **Any-of resolution.**  Whatever the ledger holds (cheapest such member),
  else the member the solve settled the OR node at, else the build-time
  representative.

## 7. Guards

| guard | rule | how it is enforced |
|---|---|---|
| round trip | no plan may contain `A → B` and `B → A` | impossible by construction (the `seq` rule); an assertion re-checks every plan |
| pool amplification | per-pool net gain of the plan's **conversion** actions ≤ 0, for every pool but the target's | measured in pool units (below) and reported per plan |
| net-loss loop | a demand for `X` may not be served by a recipe that consumes `X` unless the net is strictly positive | `_consumed_amount` resolves the any-of inputs first, then compares with the output amount |
| self dependence | a demand for `X` raised while `X` is being expanded is `UNRESOLVED` | expansion stack |
| tier reversal | counted, not forbidden | `reverse_tier` per action, plus the cost penalty |

**Pool units.**  Each pool member gets a weight `u(m)` propagated over
`pool_edges` from the pool's cheapest member: an edge `src → dst` with
`unit_cost = a/b` gives `u(dst) = u(src) · a/b`.  A conversion that conserves
material has `Σ u·amount` equal on both sides; one that creates material does
not, and that difference *is* the amplification number.  No value, no
vocabulary, no tolerance constant.

## 8. Budget and fallback

```
budget = budget_base                                   (default 512)
on exhaustion, once:
    cone   = distinct admissible producers reachable from the target (cap 200 000)
    budget = min(budget_cap, budget_base + budget_per_producer × cone)
```

The cone is counted **lazily**.  Counting it up front cost 8 ms of an 8.1 ms
two-action target on `lost-world`; deferring it to the moment the base
allowance runs out made the same target 0.16 ms and changed no plan.  This
mirrors Java's `RoutingBudget.fromConeStats` but defers the `GraphTopology`
cone walk until it can pay for itself.

On exhaustion the plan is returned with `status = "incomplete"` and the partial
action list plus `UNRESOLVED` rows — the point at which the Java
`RoutingService` falls back to `StandardPlanner`.

## 9. The plan and action schema

```json
{"pack", "slug", "target", "amount", "status": "ok|incomplete|unknown-target",
 "stock": {...}, "knobs": {...}, "seconds": 0.0,
 "actions": [
   {"id": "A1", "recipe_idx": 0, "recipe_id": "", "template_id": "tpl:...",
    "system": "", "machines": [...], "times": 1, "depth": 0,
    "chain_depth": 0, "for": "<base_id>",
    "inputs":  [{"base_id", "amount", "from": ["A2"|"stock"|"raw"]}],
    "outputs": [{"base_id", "amount"}],
    "why": {
      "decided_by": "cost|ranker",
      "cost": {"inputs", "steps", "machines", "tier_penalty",
               "reliability_weight", "role_multiplier", "out_amount",
               "unit_cost"},
      "runner_up": {"recipe_idx", "recipe_id", "unit_cost", "producer_score",
                    "gap"},
      "judgements": {"junk_p", "role_argmax", "effective_role", "shape_role",
                     "producer_score", "reliability", "tier", "reverse_tier"},
      "excluded":  [{"recipe_idx", "reason", "junk_p"}],
      "shortlist": [{"recipe_idx", "unit_cost", "producer_score"}]}}],
 "raw": {"<base_id>": amount},
 "unresolved": [{"base_id", "amount", "reason", "candidates"}],
 "catalysts": [],
 "profile": {"leaves_by_acquirability": {"low","mid","high"},
             "machine_footprint": [{"machine_id", "tier"}],
             "mod_footprint": [...], "pools_touched": [...],
             "guards_fired": [...], "policy": {...}},
 "metrics": {...}}
```

`chain_text(plan)` renders the same `### CHAIN` format the baseline exporter
writes, so a human can diff the two side by side, and
`eval_planner.parse_chain_file` reads either back — the round trip
(emit → parse → measure) is asserted.

## 10. Thresholds, and why

| knob | default | reason |
|---|---|---|
| `--tau-junk` | 0.8707 | the LightGBM arm's recipe-level P ≥ 0.95 point (`metrics-recipe.json`); recall 0.849, so 15 % of junk still reaches the planner and the guards must stand on their own |
| `--tau-display` | 0.5 | the role head's `display` recall is 0.86, the only other class worth acting on |
| `--gamma` | 2.0 | the cost/ranker split; reported at 1, 1.5, 2 and ∞ |
| `--step-cost` | 0.25 | Phase 0b's, unchanged, so the two layers price a step the same way |
| `--machine-cost` | 0.25 | one machine tier ≈ one crafting step; the aesthetic term against chains spanning three mods' machines |
| `--tier-penalty` | 1.0 | one step per tier of regression, doubled when Phase 0b's Tukey fence already called it `reverse_tier` |
| `--recycling-mult` | 8.0 | "last resort" as a price rather than a ban, so a recycling recipe can still be the only way |
| `--budget-base` | 512 | covers every fom / atm10-lite target measured; the cone raise is the escape hatch |
| `--budget-per-producer` | 4 | Java's cone-proportional budget, same shape |
| `--budget-cap` | 200 000 | a hard stop so a pathological cone cannot hang a solve |
| `MAX_BOOT_ROUNDS` | 24 | ring/orphan bootstrap rounds; 0-1 are used in practice |
| OR representative | cheapest `value_fitted`, then most consumers, then id | three stable columns, no vocabulary |

## 11. Java mapping

| prototype | Java seam (HANDOVER §7) |
|---|---|
| `PackIndex` | per-node caches on `RecipeGraph.Node` (:1458–1928) + `GraphTopology.of(graph)` for the producer/consumer CSRs, which `GraphTopology` does not yet compute |
| `PackIndex.or_items` / `or_set` | tag expansion beside `RoutingVariantKeys.inputKey`; the member `set` is what makes stock matching O(1) |
| `score_models` cache | `FMLPaths.CONFIGDIR/cirrus/cache/<catalog-hash>/` (the disk cache HANDOVER §6 says is a new pattern), Gson-written |
| `Reference` (numpy) | `predict_reference.py` + `golden-sample.json` parity fixture; load the JSON with Gson from `assets/calculator_cirrus/quality/` |
| producer score (§2.2) | a `FeatureType` axis fed by `DefaultScoreModels.build`; the O(n) decomposition means one dot product per recipe at graph-build time |
| `Policy.excluded` | `SourcePolicyDecision.allowedRecipes()` bitmap read at `StandardPlanner.scanFilteredProducers` (:1213–1272, exclusion bitmap :1218) |
| `Policy.protected_` | the P2 gate "唯一生产者永不被排除" |
| `solve_cost` | a `RecipeCostHeuristic` attached the way `BayesRuntime.attach` is, at `RecipePathSettings.java:531` |
| `CostSolve.seq` | a new per-node field; it is the cheapest acyclicity certificate available and removes the need for `estimateAmplificationRisk` (:3218) |
| `choose_producer` | replaces `producerScanLimit` truncation (:1307–1311): shortlist by cost, order by score |
| `Ledger` | `ResourceLedger` |
| `plan_target` / `Plan` | `PlanBuilder` → `RoutingPlan`; `craftTimes` must become `long` through :1384/:1508/:1719/:1759/:2024/:2715/:3367/:3654/:3823 and `FeatureType.craftTimes` at :1735 |
| budget (§8) | `RoutingBudget.fromConeStats`, but computed lazily |
| guards (§7) | replaces `estimateAmplificationRisk(cycleCandidate, depth)`; pool units come from `pool_edges` |
| `chain_text` | `ChainExportMain`'s writer; `replay()` in the tests is `RoutingPlanReverseVerifier` |
| fallback on `incomplete` | `RoutingService.choosePlanner` (:797–804) + `PlannerMode.hierarchical`, with `AcyclicPlanner(RoutePlanner fallback)` as the delegation precedent |

---

# Phase 4 — the minimum-annotation feedback layer

```
out/<slug>/planner/*.parquet ─┐
out/features/*.parquet ───────┼─ feedback.py ─▶ out/<slug>/feedback/state.json
out/models/*.json ────────────┘                 a FeedbackView (dense arrays)
                                                TemplateInquiry questions
planner.py  ──(one additive hook: PlanOptions.feedback)──▶ the same Plan
```

`./run_phase4.sh` runs the six stages and then the invariants.  Phases
0a–0c-2 and the Phase 3 *behaviour* are read-only: this layer adds
`feedback.py`, `tests/test_phase4.py`, `run_phase4.sh`, and to `planner.py`
only the additive `PlanOptions` seam of §5.

## 0. The same hard rules

1. **No hardcoded mod / item / material / colour / system vocabulary.**  The
   five role words are imported from `planner.py`, which imports them from
   `labels.py`'s one sanctioned table.  `tests/test_phase0a.py` now covers 13
   modules.
2. **No join on recipe ids.**  `recipe_idx`, `template_id`, `base_id`.
3. **No training at runtime.**  Every update below is closed form: a
   pseudo-count addition, or one bounded logistic step whose size is known
   before it is taken.  There is no loss to minimise over a dataset, no epoch
   and no learning-rate schedule.
4. **A run with no feedback is the Phase 3 run, byte for byte.**  Asserted
   against a 50-target fom snapshot taken before the hook existed
   (`tests/fixtures/phase3-plans-fom.json.gz`, sha256 `adb9c06a…`).

## 1. `FeedbackState` — the schema

One object per pack, JSON on disk at `out/<slug>/feedback/state.json`.

| field | type | meaning |
|---|---|---|
| `version` | string | `p4.1`; a mismatch drops the state rather than applying it |
| `slug`, `pack` | string | which pack this is |
| `data_hash` | string | `manifest.json`'s `data_hash`; a mismatch is refused |
| `index_stamp` | string | `planner-index-meta.json`'s `stamp` (arms + recipe count) |
| `stock` | `{base_id: qty}` | `have` |
| `bans.recipe` | `[recipe_idx]` | `ban(recipe_idx=…)` |
| `bans.template` | `[template_id]` | `ban(template_id=…)` — the inquiry's second answer |
| `bans.item` | `[base_id]` | `ban(base_id=…)` — "not available as an input source" |
| `prefers.recipe` | `[recipe_idx]` | `prefer(recipe_idx=…)` |
| `prefers.item` | `[base_id]` | `prefer(base_id=…)` — a price discount |
| `evidence` | `{template_id: [a, b]}` | Beta pseudo-counts: `a` junk, `b` useful |
| `role_evidence` | `{template_id: {role: c}}` | Dirichlet pseudo-counts |
| `delta_weights` | `float[188]` | the ranker delta, in the exported `coef` space |
| `choices` | `[{a, b, weight}]` | the audit trail of implicit gestures |
| `asked` | `[family_id]` | families the session has already asked about |
| `n_updates` | int | how many closed-form updates this state has absorbed |

Keying the evidence by `template_id` is what makes it portable: the id is a
hash of an abstract signature (Phase 0a §2.4) and is pack-independent, so
`FeedbackStore.merge` is "counts add, sets union, deltas add" and a template
that two packs share receives the evidence directly, with no similarity step.

## 2. The template posteriors — Beta and Dirichlet, closed form

The exported model's belief about a template is treated as a prior of
strength `n0` pseudo-observations:

```
prior(junk)  = Beta(n0 * junk_p(t),  n0 * (1 - junk_p(t)))
prior(role)  = Dirichlet(n0 * role_p(t, ·))
```

and a click adds counts.  The planner reads posterior **means**:

```
junk'(t)     = (n0 * junk_p(t) + a(t)) / (n0 + a(t) + b(t))
role'(t, c)  = (n0 * role_p(t, c) + c(t, c)) / (n0 + Σ_c c(t, c))
eff_role'(t) = planner.effective_roles(ix, argmax_c role'(t, ·))
```

`effective_roles` is Phase 3's own rule, called with the new argmax — the
feedback never invents a second way to decide a role.

| constant | default | why |
|---|---|---|
| `PRIOR_STRENGTH` `n0` | 4.0 | the model's belief is worth four clicks.  Low enough that one explicit click wins, high enough that a single stray one cannot flip a template the model is sure about *in the other direction* (a `prefer` only halves the posterior) |
| `BAN_FACTOR` | 7.0 | a ban adds `7 n0 = 28` junk counts, so `junk' ≥ 7/8 = 0.875` **whatever the model believed** — just above the default exclusion threshold `τ_junk = 0.8707`.  That inequality is the design: one click always excludes its own template |
| `PREFER_FACTOR` | 1.0 | a preference adds `n0 = 4` useful counts, so `junk' = junk_p/2` exactly.  Deliberately weaker than a ban: "I like this one" is not "that one is rubbish" |
| `ROLE_EVIDENCE_FACTOR` | 1.0 | a click adds `n0` role counts — the model's whole role prior — so one click moves the role posterior *halfway* and a fraction of one cannot dominate it.  A ban votes `display`, a preference votes `production`: the two classes Phase 0c-2 says the role head can be trusted on, and the two the candidate policy acts on |

## 3. Feature-neighbourhood generalisation

A click is also evidence about templates that *look like* the one clicked.
The similarity is cosine over the **standardised 129-dim template features** —
`feature-spec.json`'s own `fill` / `mean` / `std`, so "near" means the same
thing to the learner as it does to the model.

```
N(t)      = the NEIGHBOURS most similar templates of this pack, cosine ≥ FLOOR
scale     = min(1, SPREAD_TOTAL / Σ_{n∈N(t)} sim(t,n))
share(n)  = min(SPREAD_PER, sim(t,n) * scale)
evidence  = W on t itself, and W * share(n) on each n
```

| constant | default | why |
|---|---|---|
| `NEIGHBOURS` | 8 | one click moves at most 9 templates |
| `SIMILARITY_FLOOR` | 0.70 | the cosine Phase 0a already uses as its "same thing" threshold for token-class evidence |
| `SPREAD_TOTAL` | 1.0 | the total off-template evidence never exceeds the on-template evidence |
| `SPREAD_PER` | 0.5 | no neighbour ever receives more than half |

The two caps give a statement that can be checked by hand and is asserted in
`tests/test_phase4.py`: after one ban, a neighbour's junk posterior is at most

```
(n0 * p + 0.5 * BAN_FACTOR * n0) / (n0 + 0.5 * BAN_FACTOR * n0) = (4p + 14) / 18
```

which crosses `τ_junk = 0.8707` only when `p > 0.42`.  **A click cannot
exclude a template the model thought was clean.**  On the role side the same
fraction gives `(4 q + 2) / 6`, which crosses `τ_display = 0.5` only when
`q > 0.25`.

## 4. The pairwise online ranker update

`choose(a, over=b)` is one step on the exported linear ranker, stored as a
delta and never written back to `producer-ranker.json`:

```
x  = f(a) - f(b)                      the 188-column pair row, built exactly
                                      as train_eval built it (NaN-filled base
                                      difference + 5 NaN-zeroed scalar
                                      differences + 3 equality flags)
z  = x / scale                        the ranker's standardised space
m  = x . (w0 + d / scale)             the current margin; w0 = coef / scale
g  = sigmoid(-m)                      the logistic gradient for "a wins"
d <- (1 - LAMBDA) * d + ETA * s * g * z / (z . z)
d <- d * min(1, BOUND * |coef| / |d|)
```

| constant | default | why |
|---|---|---|
| `ETA` | 4.0 | because the step is divided by `z·z`, the margin moves by exactly `ETA * s * g` and by no more — `ETA` *is* the margin one click buys, in the ranker's own units.  4.0 is roughly 8× the median gold-case margin (0.5), so one click decides a case it cares about and still leaves the ranker's own ordering intact everywhere the click says nothing |
| `LAMBDA` | 0.02 | the L2 pull back towards the exported weights: with no further clicks the delta decays by half in 35 updates |
| `BOUND` | 0.25 | `|d| ≤ 0.25 |coef|`; 500 contradictory clicks in the test reach the cap and stop there |
| `PREFER_STEP_SCALE` | 0.25 | `prefer(recipe)` is `choose` against every sibling at a quarter step |

Generalisation is by construction: `S(r) = f(r)·(w0 + d/scale)` is linear, so
*every* producer whose features point the same way as `f(a) - f(b)` moves
with it, in every pack, with no similarity step and no per-pack state.  That
is why the store keeps the delta separately from the evidence: evidence is
about *this pack's* templates, the delta is about the *feature space*.

**Re-scoring.**  The Bradley–Terry aggregation of Phase 3 §2.2 is linear in
the weights, so the delta's contribution is the same row-mean formula run
with `d/scale` alone:

```
S'(r) = S(r) + (1/|outputs(r)|) Σ_{item ∈ outputs(r)} rowmean_d(item)[r]
```

`LazyScore` computes `rowmean_d(item)` on first touch — **one dot product per
producer of that output**, then `planner._group_rowmeans` (reused, not
re-derived, so the flag handling stays identical) — and caches it per group
and per recipe.  `tests/test_phase4.py` asserts the lazy result equals a full
recompute at `w0 + d/scale` to 2.3e-14 over fom's 3,442 recipes.

## 5. The planner hook

One optional object, `planner.PlanOptions`, threaded through `Policy`,
`solve_cost`, `choose_producer`, `plan_target` and `_action_row`.  `None`
everywhere is the default and is the Phase 3 code path.  The `feedback` field
is a `FeedbackView`: dense arrays the planner reads **exactly where it reads
the model's own**.

| view field | read by | effect |
|---|---|---|
| `junk_p`, `role_p`, `eff_role` | `Policy.__init__` | posterior overrides feed the existing `flagged` expression; nothing else changes |
| `banned` | `Policy.__init__` | flagged unconditionally, and the sole-producer protection **may not rescue it** |
| `preferred` | `Policy.__init__` | never flagged |
| `score` | `choose_producer`, `_action_row` | `S(r) + ΔS(r)`, lazily |
| `stock` | `plan_target`, `solve_cost` | standing stock; the request's own stock wins on a clash |
| `item_banned` | `solve_cost`, `_resolve_or`, `_input_held` | every recipe with that item as a *direct* input dies; an any-of input simply loses that member |
| `item_factor` | `solve_cost`, `producer_cost` | the discount on the price that item contributes to whoever consumes it |

Two decisions worth stating:

* **An explicit ban beats the sole-producer protection.**  That protection
  exists to stop the *model* making an output unreachable (Phase 2 gate
  "唯一生产者永不被排除"); it is not a veto on the user.  When every producer
  of an item is banned the demand falls through Phase 3's existing orphan
  rule and is written to `[RAW]` — "go and get it" — rather than inventing a
  new failure mode.
* **The item discount is applied where the price is *consumed*, not where it
  settles**, so the settle order — Phase 3's acyclicity certificate — is
  untouched.  It is the same kind of approximation the output-amount division
  already makes (a 9-output recipe already prices its output below its input).

## 6. `TemplateInquiry`

After a plan, every template it *used* and every template that reached a
shortlist within `INQUIRY_MARGIN` (2.0) of the winning cost is a candidate
question.

```
uncertainty(t) = H2(junk'(t)) * n0 / (n0 + a(t) + b(t))
impact(t)      = 0.5 * crafts(t) / max crafts  +  0.5 * closeness(t)
closeness(t)   = max over shortlists of (best unit cost / this template's)
score(t)       = uncertainty(t) * impact(t)
```

`H2` is the Bernoulli entropy of the Beta's posterior mean — its
posterior-predictive entropy — and the evidence discount is what stops the
layer asking twice about a template the user has already ruled on.  An
executed template contributes the crafts term (and `closeness = 1`); a
shortlisted one contributes only closeness, so a route that *almost* won is
as interesting as one that did.

**Family.**  `family(t) = min({t} ∪ N(t))` by `template_id` — the
lexicographically smallest id in the template's own neighbourhood.  It is
deterministic, needs no clustering pass, and makes mutual neighbours share a
family.  At most one question per family per session (`state.asked`), at most
`INQUIRY_LIMIT` (20) questions.

Each question carries the `template_id`, the family, three example
`recipe_idx`, the current junk and role beliefs with the role entropy, the
evidence counts so far, the member count, and the two answers: **keep**, or
**ban at template scope**.

## 7. Persistence

`FeedbackStore` writes `out/<slug>/feedback/state.json` (3.4–3.7 kB for a
four-gesture session; the delta's 188 rounded floats dominate).  `load()`
returns `(state, status)` with status `absent` / `version` / `data_hash` /
`index_stamp` / `ok`; a stale stamp is **refused and reported**, never
silently applied to a different corpus.  `merge` adds counts, unions sets and
adds deltas — which is also how the *shared-delta* variant of the evaluation
is built.

## 8. Thresholds, and why

| knob | default | reason |
|---|---|---|
| `PRIOR_STRENGTH` | 4.0 | §2 |
| `BAN_FACTOR` | 7.0 | one ban ⇒ `junk' ≥ 0.875 > τ_junk` |
| `PREFER_FACTOR` | 1.0 | one preference ⇒ `junk' = junk_p / 2` |
| `ROLE_EVIDENCE_FACTOR` | 1.0 | one click moves the role posterior halfway |
| `NEIGHBOURS` / `SIMILARITY_FLOOR` | 8 / 0.70 | §3 |
| `SPREAD_TOTAL` / `SPREAD_PER` | 1.0 / 0.5 | the neighbour bound `(4p+14)/18` |
| `ETA` / `LAMBDA` / `BOUND` | 4.0 / 0.02 / 0.25 | §4 |
| `PREFER_STEP_SCALE` | 0.25 | a preference is a quarter of a re-selection |
| `ITEM_DISCOUNT` | 0.5 | an item the user prefers is priced at half; the smallest discount that beats the per-step cost on a one-step route |
| `INQUIRY_LIMIT` / `INQUIRY_MARGIN` | 20 / 2.0 | a session's question budget; the margin is `γ`, so "considered" means the same thing to the inquiry as to the shortlist |

## 9. Java mapping

| prototype | Java (`JAVA-DESIGN.md` §1) |
|---|---|
| `FeedbackState` + `FeedbackStore` | `api/recipe/routing/feedback/FeedbackStore` — same JSON, Gson, written atomically beside `DerivedIndexCache` under `CONFIGDIR/cirrus`; `data_hash` + catalog key are the version stamp |
| `PlannerFeedback` | `api/recipe/routing/feedback/PlannerFeedback` — the UI-facing API; every method O(1) except the neighbourhood walk |
| `FeedbackLearner.add_evidence` / `spread` | `api/recipe/routing/feedback/FeedbackLearner` — the Beta/Dirichlet counters and the k-NN over the standardised template matrix.  The matrix is a side table of `DerivedIndexCache`, `float[nTemplates][129]`, L2-normalised once |
| `FeedbackLearner.step` | the same class: one 188-float vector, `BLAS`-free, `d` kept as `float[188]` |
| `LazyScore` | the producer-score axis of `QualityRuntime`: `S(r)` stays on `RecipeGraph.Node`, `ΔS(r)` is a per-request cache keyed by output node |
| `FeedbackView` | what `CandidatePolicy` and `ProducerChooser` read instead of the raw model arrays — a wrapper, not a copy, for the arrays the state does not touch |
| `TemplateInquiry` | `api/recipe/routing/feedback/TemplateInquiry`; the question list is what the UI shows between requests |
| `PlanOptions` | a `RoutingRequest` option object; **not** part of the plan's own identity, so a request that uses none serialises identically |

---

# Phase 5 — the deep-cone benchmark and the caching design

```
cache/<slug>/*.parquet ─┐
out/<slug>/**           ├─ bench_planner.py ─▶ out/planner-bench/*.json
chain-exports/<pack>/ ──┘                      (cone, build, memo, fold, budget)
planner.py ──(the same additive PlanOptions seam)──▶ the same Plan
```

`./run_phase5.sh` runs the five stages and then the invariants.  This layer
adds `bench_planner.py`, `tests/test_phase5.py` and `run_phase5.sh`, and to
`planner.py` only three things, all additive: the `share_memo` /
`fold_templates` / `budget_shape` fields of `PlanOptions`, the module-level
`PLAN_STATS` counters, and a cached `Policy.signature()`.

## 0. The same hard rules

The four of Phase 4 §0, unchanged, plus one: **a benchmark may not change
what it measures.**  Every option defaults to off; `PLAN_STATS` is a
module-level dict rather than a plan field, precisely so that reading the
expansion count does not alter a plan's bytes.

## 1. Target selection

Two sets per pack, both from the baseline's own `index.tsv`:

* **deep** — the 50 targets the *baseline* found deepest (`depth`, then
  `actions`).  Choosing by the thing being compared against, rather than by
  the prototype's own opinion, is the point.
* **random** — 150 more, seeded, disjoint from the deep set.

Timing takes the **per-target minimum over three passes** after a 25-target
warm-up.  Measured on this machine, the first pass over a pack is 20–30 %
slower than every later one and single-pass spread between two *identical*
variants exceeds the effect several stages below are looking for.

## 2. What a Java runtime has to cache

| artefact | holds | Java placement |
|---|---|---|
| `cache/<slug>/recipes.parquet`, `contents.parquet` | the graph itself | **node** — `RecipeGraph` already has it |
| `out/<slug>/recipe_template.parquet` | `template_id` per recipe | **node** — a new `Node` field (`JAVA-DESIGN.md` §2 step 3) |
| `out/<slug>/economy/item_values.parquet` | value / tier / leaf / pool / acquirability per item | **side** — one `float[]` per column, indexed by item ordinal |
| `out/<slug>/economy/recipe_shape.parquet` | shape role, tiers, reliability, `reverse_tier` per recipe | **node** |
| `out/<slug>/economy/machines.parquet` | machine tier | **side**, indexed by machine ordinal |
| `out/<slug>/economy/pools.parquet`, `pool_edges.parquet` | strong-pool membership, conversion edges with `unit_cost` | **side** — the guard's pool units are derived from these once |
| `out/<slug>/planner/template_posteriors.parquet` | `junk_p` + one column per role class, per **template** | **side**, indexed by template ordinal (3× smaller than per recipe) |
| `out/<slug>/planner/producer_scores.parquet` | `S(r)` per recipe | **node** |
| `out/features/template_features.parquet` | 129 standardised features per template | **side** — Phase 4 neighbourhoods only |
| `out/features/recipe_features.parquet` | the 180 pair columns per recipe | **side** — Phase 4 re-score only |
| `out/models/*.json` | the exported models | **resource** — in the jar, identical for every pack |

`recipe/routing/cache/DerivedIndexCache` owns everything marked **side** and
the **node** columns that are not already on `RecipeGraph.Node`, keyed by the
catalog key of `JAVA-DESIGN.md` §4.  The `resource` row is not cached at all.

## 3. The cross-request cost memo

Phase 3 already memoises the solve per `(pack, policy, knobs, stock)`; what it
did not do is *keep* it between requests.  `PlanOptions.share_memo` adds a
process-level dictionary:

```
key = (slug, n_recipes, n_items,
       policy.signature(),          # sha1 of the exclusion bitmap, cached
       knobs.signature(),
       feedback view signature or "",
       sha1 of the sorted (base_id, qty) stock multiset)
```

Three rules make it safe:

1. **The key contains everything the solve reads.**  Exclusions, knobs,
   feedback state and stock.  Anything that changes any of them is a
   different entry, so a feedback click invalidates the memo by construction
   rather than by an explicit flush.
2. **The value is immutable.**  `CostSolve` holds `cost[]`, `seq[]`,
   `source[]` and the paid-machine set; extraction never writes to it (the
   ledger is the mutable half, and it is per request).
3. **The policy signature is computed once.**  Hashing the exclusion bitmap
   on every request costs 9 ms on the largest pack — most of what the memo
   saves — so `Policy` caches it.  Java's `SourcePolicyDecision` should carry
   the same fingerprint as a field.

The empty-stock entry is the one that matters: it serves every request that
starts from nothing, which is most of them.

## 4. Fold-based pruning

`PlanOptions.fold_templates` collapses, per demanded output, every admissible
producer sharing a `template_id` down to one representative — best producer
score, ties by cost then `recipe_idx`, the same order the shortlist uses,
applied one step earlier.  The folded-away siblings are recorded in
`why.excluded` with the reason `folded into its template`, so the explanation
still says what happened.

It is off by default.  §Phase 5 of the report measures why.

## 5. Budget shapes

```
cone   (default)  budget = min(cap, base + per_producer * |cone producers|)
depth             budget = min(cap, base * (1 + cone hop depth)^2)
time              no expansion ceiling; stop when the request exceeds
                  budget_seconds of wall
```

All three use the **same lazy walk**: `cone_stats(ix, pol, root, cap)` is
breadth-first and returns `(producers, hop depth)` in one pass, so the depth
variant costs nothing extra over the cone variant.  The walk still fires only
when the base allowance runs out (Phase 3 §8), which is why `budget_base` is
really "how often do you pay for the cone walk" rather than a safety limit.

## 6. Build-time and request-time targets

| phase | measurement (Python prototype, atm10) | Java target |
|---|---|---|
| graph build, cold | 39.4 s total, of which 13.8 s is parquet → CSR and 23.4 s is model inference | ≤ 3–5 s *additional* over the existing graph build |
| graph build, warm | 16.1 s, of which 13.8 s is parquet → CSR | milliseconds: the CSR is `RecipeGraph` itself, and everything else is a `DerivedIndexCache` read |
| per request, median | 15.2 ms (atm10 random), 29.1 ms (atm10 deep), 4.6 ms (minecolonies), 0.5 ms (fom) | same order |
| per request, p95 | 139 ms (atm10 random), 320 ms (atm10 deep) | same order; `RoutingService` falls back on `incomplete` |
| per feedback click | ≤ 2.5 ms p95 | ≤ 5 ms |

The Python "cold" number is not the Java number: 13.8 s of it is pandas/pyarrow
materialising 1.58 M content rows into Python objects, which Java does not do
because the graph is already in memory.  The honest Java estimate is the warm
total *minus* the CSR build: **≈ 2 s on atm10**, which is inside the plan's
3–5 s budget.

## 7. Thresholds, and why

| knob | default | reason |
|---|---|---|
| `--deep` / `--random` | 50 / 150 | the brief's |
| `--repeats` | 3, per-target minimum | the measured variant-to-variant noise on this machine exceeds the effects being measured |
| warm-up | 25 targets | the first pass is 20–30 % slower |
| `PlanOptions.share_memo` | off | it holds dense arrays over every item of the pack; opt-in is the honest default for a library |
| `PlanOptions.fold_templates` | off | it does not pay (report §Phase 5) |
| `PlanOptions.budget_shape` | `cone` | no variant dominates it (report §Phase 5) |
| `budget_seconds` | 0.25 | a quarter second is the largest stall a UI can absorb between keystrokes |

## 8. Java mapping

| prototype | Java (`JAVA-DESIGN.md`) |
|---|---|
| `CACHE_INVENTORY` + `stage_build` sizes | §4 `recipe/routing/cache/DerivedIndexCache`: the file list, the placement column and the byte budget |
| `planner._SOLVE_MEMO` + `memo_key` | a per-`RecipeGraph` cache beside `GraphTopology.of(graph)`; the key is the same tuple, `stockSignature` from the request |
| `Policy.signature()` | a fingerprint field on `SourcePolicyDecision` |
| `PlanOptions.share_memo` | on by default in the Java runtime — the reason it is opt-in here is memory bookkeeping in a batch harness, not correctness |
| `PlanOptions.fold_templates` | do **not** port: measured at 0.97–1.0× and it changes 0–2 plans in 200 |
| `cone_stats` | `GraphTopology`'s cone walk, returning producers *and* hop depth in one pass; `RoutingBudget.fromConeStats` reads the first |
| `PLAN_STATS` | JMH counters / `RoutingPlanDebugText`, never `RoutingPlan` fields |
| `stage_cone`'s deep/random split | `RecipeRoutingScaleBenchmark` target sets |
