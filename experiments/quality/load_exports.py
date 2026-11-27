#!/usr/bin/env python
"""Phase 0a step 1 — stream the recipe exports into a columnar cache.

For every pack directory under ``--exports-root`` this streams the single
``recipes-<ts>.json`` with ijson (``recipes.item``) and writes

    <cache>/<pack-slug>/recipes.parquet
    <cache>/<pack-slug>/contents.parquet
    <cache>/<pack-slug>/systems.parquet
    <cache>/<pack-slug>/registry.json
    <cache>/<pack-slug>/load-meta.json

plus ``<cache>/packs.json`` mapping slug -> export directory name.

Normalisation mirrors
``Mod/src/test/java/dev/vfyjxf/cirrus/recipe/chainexport/ExportPackLoader.java``
(parseRecipe / parseContent / parseStackMember / resolveFamily / ...) with one
deliberate difference: **no rule is applied as a filter**.  The Java loader
drops junk/duplicate recipes up-front via the builtin classifier; here the
classifier does not exist at all, because those rules become training labels in
a later phase.  The only recipes marked ``dropped`` are the structurally empty
ones (no inputs or no outputs), and they are still written out with
``dropped=True`` so that ``len(recipes.parquet) == recipeCount`` always holds.

Determinism: identical input bytes produce byte-identical parquet files
(fixed schema, fixed batch size, stream order preserved, no timestamps).
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from collections import Counter
from pathlib import Path

import ijson
import pyarrow as pa
import pyarrow.parquet as pq

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import (  # noqa: E402
    SENTINEL_AMOUNT,
    find_pack_files,
    list_packs,
    path_tokens,
    peak_rss_mb,
    read_json,
    slugify,
    split_id,
    stable_hash,
    strip_alt_suffix,
    write_json,
)

BATCH_RECIPES = 20000

ITEM_FAMILY = "item"
FLUID_FAMILY = "fluid"

RECIPE_SCHEMA = pa.schema([
    ("recipe_idx", pa.int32()),
    ("pack", pa.string()),
    ("system", pa.string()),
    ("source", pa.string()),
    ("recipe_type", pa.string()),
    ("recipe_id", pa.string()),
    ("recipe_ns", pa.string()),
    ("recipe_path_tokens", pa.list_(pa.string())),
    ("machines", pa.list_(pa.string())),
    ("n_inputs", pa.int32()),
    ("n_outputs", pa.int32()),
    ("has_value_input", pa.bool_()),
    ("has_complex", pa.bool_()),
    ("flags", pa.list_(pa.string())),
    ("dropped", pa.bool_()),
])

CONTENT_SCHEMA = pa.schema([
    ("recipe_idx", pa.int32()),
    ("side", pa.string()),
    ("slot", pa.int16()),
    ("kind", pa.string()),
    ("content_type", pa.string()),
    ("base_id", pa.string()),
    ("members", pa.list_(pa.string())),
    ("n_members", pa.int32()),
    ("variant_key", pa.string()),
    ("component_keys", pa.list_(pa.string())),
    ("match_strategy", pa.string()),
    ("amount", pa.int64()),
    ("amount_sentinel", pa.bool_()),
    ("chance", pa.float64()),
    ("value", pa.string()),
    ("inner_count", pa.int64()),
])

SYSTEM_SCHEMA = pa.schema([
    ("system_id", pa.string()),
    ("source", pa.string()),
    ("recipe_type", pa.string()),
    ("jei_recipe_types", pa.list_(pa.string())),
    ("machines", pa.list_(pa.string())),
    ("recipe_count", pa.int64()),
    ("skipped", pa.int64()),
    ("skipped_listed", pa.int64()),
    ("skipped_reasons_json", pa.string()),
])


# ------------------------------------------------------------ schema helpers
# ``item`` / ``fluid`` below are the export schema's own literal values of the
# ``stack.type`` field (SCHEMA.md section 1), in the same class as ``kind`` or
# ``amount``.  No registry namespace is ever written down: a registry id is
# reduced to its path by pure string surgery, so an unknown mod's content type
# normalises exactly like a known one.

def family_of_content_type(content_type_id: str) -> str:
    """``<ns>:<path>`` -> ``<path>``; a bare path is already a family."""
    return content_type_id.rpartition(":")[2] or content_type_id


def family_of_stack_type(stack_type_id: str) -> str:
    """``<ns>:<family>_stack`` -> ``<family>`` (schema-level suffix only)."""
    tail = family_of_content_type(stack_type_id)
    suffix = "_stack"
    return tail[:-len(suffix)] if tail.endswith(suffix) else tail


def _num(value, default=None):
    if value is None:
        return default
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def _as_int(value, default=None):
    f = _num(value)
    if f is None:
        return default
    return int(round(f))


def _scalar_text(value):
    if value is None:
        return None
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, str):
        return value
    f = _num(value)
    if f is None:
        return str(value)
    if f == int(f):
        return str(int(f))
    return repr(float(f))


def read_header_and_systems(recipes_file: Path) -> tuple[dict, list]:
    """Top-level scalars + ``systems[]`` without a second full parse.

    The dump is ``{scalars..., "recipes":[ huge ], "systems":[ small ],
    "recipeCount":N, "skippedCount":M}``, so the head bytes carry the leading
    scalars and a tail window carries systems[] plus the trailing counts.
    Falls back to a streaming pass whenever the byte-window trick does not
    produce valid JSON.
    """
    header: dict = {}
    systems = None
    size = recipes_file.stat().st_size
    with open(recipes_file, "rb") as handle:
        head = handle.read(min(size, 1 << 16)).decode("utf-8", "ignore")
    cut = head.find('"recipes"')
    if cut > 0:
        try:
            header = json.loads(head[:cut].rstrip().rstrip(",") + "}")
        except Exception:
            header = {}

    window = 1 << 20
    while systems is None:
        start = max(0, size - window)
        with open(recipes_file, "rb") as handle:
            handle.seek(start)
            tail = handle.read(size - start).decode("utf-8", "ignore")
        pos = len(tail)
        while True:
            pos = tail.rfind('"systems"', 0, pos)
            if pos < 0:
                break
            try:
                blob = json.loads("{" + tail[pos:])
            except Exception:
                continue
            if isinstance(blob, dict) and isinstance(blob.get("systems"), list):
                systems = blob["systems"]
                for key, value in blob.items():
                    if key != "systems" and not isinstance(value, (list, dict)):
                        header[key] = value
                break
        if systems is not None or start == 0 or window >= (1 << 27):
            break
        window *= 4

    if systems is None:
        systems = []
        with open(recipes_file, "rb") as handle:
            for item in ijson.items(handle, "systems.item", use_float=True):
                systems.append(item)
        with open(recipes_file, "rb") as handle:
            for prefix, event, value in ijson.parse(handle, use_float=True):
                if prefix and "." not in prefix and event in ("number", "string", "boolean"):
                    header[prefix] = value
    return header, systems


# ---------------------------------------------------------------- the loader


class PackLoader:
    """Streams one pack into the columnar cache."""

    def __init__(self, pack_dir: Path, out_dir: Path, pack_name: str, limit: int = 0):
        self.pack_dir = pack_dir
        self.out_dir = out_dir
        self.pack_name = pack_name
        self.limit = limit
        self.files = find_pack_files(pack_dir)
        self.tag_family: dict[str, str] = {}
        self.stack_type_family: dict[str, str] = {}
        self.registry: dict = {}
        self.counters: Counter = Counter()
        self.flag_counts: Counter = Counter()
        self.kind_counts: Counter = Counter()
        self.stack_type_counts: Counter = Counter()
        self.chance_zero_systems: Counter = Counter()
        self.system_row_counts: Counter = Counter()

    # -- registries ---------------------------------------------------------

    def load_registries(self) -> None:
        for key, path in sorted(self.files["registry"].items()):
            try:
                blob = read_json(path)
            except Exception as exc:  # pragma: no cover - corrupt side file
                self.counters[f"registry_unreadable:{key}"] += 1
                self.registry[key] = {"error": str(exc)}
                continue
            self.registry[key] = blob
            if key == "tag-types":
                for entry in blob.get("tagTypes") or []:
                    family = family_of_content_type(str(entry.get("contentType") or ""))
                    for tag in entry.get("tags") or []:
                        self.tag_family.setdefault(str(tag), family)
            elif key == "stack-types":
                for entry in blob.get("stackTypes") or []:
                    sid = entry.get("id")
                    ctype = entry.get("contentType")
                    if sid:
                        family = (family_of_content_type(str(ctype)) if ctype
                                  else family_of_stack_type(str(sid)))
                        self.stack_type_family.setdefault(str(sid), family)
        self.counters["registry_tag_families"] = len(self.tag_family)

    # -- content parsing ----------------------------------------------------

    def _stack_member(self, stack, rec_flags: set) -> dict:
        """Mirror of ExportPackLoader.parseStackMember."""
        if not isinstance(stack, dict):
            rec_flags.add("malformed_stack")
            return {"family": ITEM_FAMILY, "id": "unknown:unknown", "count": None,
                    "components": None}
        stype = stack.get("type")
        stype = str(stype) if isinstance(stype, str) else ITEM_FAMILY
        raw_id = stack.get("id")
        raw_fluid = stack.get("fluid")
        resolved = raw_id if isinstance(raw_id, str) else (
            raw_fluid if isinstance(raw_fluid, str) else None)
        count = _as_int(stack.get("count"))
        if count is None:
            count = _as_int(stack.get("amount"))
        if resolved is None:
            rec_flags.add("unknown_stack_member")
            resolved = "unknown:unknown"
        clean, stripped = strip_alt_suffix(resolved)
        if stripped:
            rec_flags.add("suffix_stripped")
            resolved = clean
        if stype == "item":
            family = ITEM_FAMILY
        elif stype == "fluid":
            family = FLUID_FAMILY
        else:
            family = stype
            rec_flags.add("custom_stack_type")
        self.stack_type_counts[family] += 1
        comps = stack.get("components")
        return {"family": family, "id": resolved, "count": count,
                "components": comps if isinstance(comps, dict) else None}

    def _content_row(self, entry, side: str, slot: int, idx: int, rec_flags: set) -> dict:
        if not isinstance(entry, dict):
            rec_flags.add("malformed_content")
            entry = {}
        kind = entry.get("kind")
        kind = str(kind) if isinstance(kind, str) else "complex"
        if kind not in ("stack", "tag", "any", "typedAny", "value", "complex"):
            rec_flags.add("unknown_kind")
            kind = "complex"
        self.kind_counts[kind] += 1

        amount_raw = _as_int(entry.get("amount"))
        sentinel = amount_raw == SENTINEL_AMOUNT
        if sentinel:
            rec_flags.add("sentinel_amount")
            amount = None
        else:
            amount = amount_raw
        chance = _num(entry.get("chance"))
        if chance is None:
            rec_flags.add("chance_missing")
        elif chance == 0.0:
            rec_flags.add("chance_zero")
        match_strategy = entry.get("matchStrategy")
        match_strategy = str(match_strategy) if isinstance(match_strategy, str) else None

        members: list[str] = []
        member_components = None
        content_type = None
        base_id = None
        value_text = None
        inner_count = None

        if kind == "stack":
            member = self._stack_member(entry.get("stack"), rec_flags)
            content_type = member["family"]
            base_id = member["id"]
            inner_count = member["count"]
            member_components = member["components"]
            if inner_count is not None and amount is not None and inner_count != amount:
                rec_flags.add("count_amount_mismatch")
        elif kind == "tag":
            tag_id = entry.get("tag")
            tag_id = str(tag_id) if isinstance(tag_id, str) else ""
            clean, stripped = strip_alt_suffix(tag_id)
            if stripped:
                rec_flags.add("suffix_stripped")
                tag_id = clean
            base_id = tag_id
            for value in entry.get("values") or []:
                if isinstance(value, str):
                    mclean, mstripped = strip_alt_suffix(value)
                    if mstripped:
                        rec_flags.add("suffix_stripped")
                    members.append(mclean)
                elif isinstance(value, dict):
                    members.append(self._stack_member(value, rec_flags)["id"])
            if tag_id in self.tag_family:
                content_type = self.tag_family[tag_id]
            else:
                content_type = ITEM_FAMILY  # ExportPackLoader.resolveFamily default
                self.counters["tag_family_unknown"] += 1
            if not members:
                rec_flags.add("tag_no_members")
            if not tag_id:
                rec_flags.add("empty_tag_id")
        elif kind in ("any", "typedAny"):
            families = []
            parsed = []
            for stack in entry.get("stacks") or []:
                member = self._stack_member(stack, rec_flags)
                members.append(member["id"])
                families.append(member["family"])
                parsed.append(member)
            if parsed:
                # The exporter's alternative order is NOT stable across exports of
                # the same pack (proved by two packs in this corpus that are the
                # same dump twice and differ only in member order), so the
                # canonical member is chosen by id, not by position.  `members`
                # itself stays in export order.
                canonical = min(parsed, key=lambda m: m["id"])
                member_components = canonical["components"]
                inner_count = canonical["count"]
            stack_type_field = entry.get("stackType")
            if isinstance(stack_type_field, str):
                content_type = self.stack_type_family.get(
                    stack_type_field, family_of_stack_type(stack_type_field))
            elif families:
                uniq = sorted(set(families))
                content_type = uniq[0] if len(uniq) == 1 else "mixed"
            else:
                content_type = ITEM_FAMILY
            if len(set(families)) > 1:
                rec_flags.add("mixed_alternative_types")
            base_id = min(members) if members else ""
            if not members:
                rec_flags.add("empty_alternatives")
        elif kind == "value":
            value_type = entry.get("valueType")
            value_type = str(value_type) if isinstance(value_type, str) else "unknown"
            content_type = value_type
            base_id = value_type
            value_text = _scalar_text(entry.get("value"))
        else:  # complex
            impl = entry.get("implementation")
            impl = str(impl) if isinstance(impl, str) else ""
            content_type = "complex"
            base_id = impl

        if base_id:
            clean, stripped = strip_alt_suffix(base_id)
            if stripped:
                rec_flags.add("suffix_stripped")
                base_id = clean

        variant_key = None
        component_keys: list[str] = []
        if isinstance(member_components, dict) and member_components:
            variant_key = stable_hash(member_components)
            component_keys = sorted(str(k) for k in member_components.keys())

        return {
            "recipe_idx": idx,
            "side": side,
            "slot": slot,
            "kind": kind,
            "content_type": content_type or "",
            "base_id": base_id or "",
            "members": members,
            "n_members": len(members),
            "variant_key": variant_key,
            "component_keys": component_keys,
            "match_strategy": match_strategy,
            "amount": amount,
            "amount_sentinel": bool(sentinel),
            "chance": chance,
            "value": value_text,
            "inner_count": inner_count,
        }

    # -- the streaming pass -------------------------------------------------

    def run(self) -> dict:
        recipes_file = self.files["recipes"]
        if recipes_file is None:
            raise FileNotFoundError(f"no recipes-*.json in {self.pack_dir}")
        self.out_dir.mkdir(parents=True, exist_ok=True)
        self.load_registries()

        started = time.time()
        rec_rows: list[dict] = []
        con_rows: list[dict] = []
        header, systems_blob = read_header_and_systems(recipes_file)

        rec_writer = pq.ParquetWriter(self.out_dir / "recipes.parquet", RECIPE_SCHEMA,
                                      compression="snappy")
        con_writer = pq.ParquetWriter(self.out_dir / "contents.parquet", CONTENT_SCHEMA,
                                      compression="snappy")
        idx = 0
        try:
            with open(recipes_file, "rb") as handle:
                for recipe in ijson.items(handle, "recipes.item", use_float=True):
                    rec_flags: set = set()
                    system = recipe.get("system")
                    system = str(system) if isinstance(system, str) else ""
                    source = recipe.get("source")
                    source = str(source) if isinstance(source, str) else ""
                    recipe_type = recipe.get("recipeType")
                    recipe_type = str(recipe_type) if isinstance(recipe_type, str) else None
                    raw_id = recipe.get("id")
                    raw_id = raw_id if isinstance(raw_id, str) else ""
                    if not raw_id:
                        rec_flags.add("empty_id")
                    clean_id, stripped = strip_alt_suffix(raw_id)
                    if stripped:
                        rec_flags.add("suffix_stripped")
                        raw_id = clean_id
                    machines = [m for m in (recipe.get("machines") or [])
                                if isinstance(m, str)]
                    if not machines:
                        rec_flags.add("no_machines")

                    inputs = recipe.get("inputs") or []
                    outputs = recipe.get("outputs") or []
                    has_value_input = False
                    has_complex = False
                    for side, entries in (("in", inputs), ("out", outputs)):
                        for slot, entry in enumerate(entries):
                            row = self._content_row(entry, side, slot, idx, rec_flags)
                            if row["kind"] == "value" and side == "in":
                                has_value_input = True
                            if row["kind"] == "complex":
                                has_complex = True
                            con_rows.append(row)
                    if not inputs:
                        rec_flags.add("empty_inputs")
                    if not outputs:
                        rec_flags.add("empty_outputs")
                    dropped = (not inputs) or (not outputs)
                    if has_value_input:
                        rec_flags.add("has_value_input")
                    if has_complex:
                        rec_flags.add("has_complex")
                    if "chance_zero" in rec_flags:
                        self.chance_zero_systems[system] += 1
                    self.system_row_counts[system] += 1

                    ns, path = split_id(raw_id)
                    if raw_id and not ns:
                        rec_flags.add("id_no_namespace")
                    rec_rows.append({
                        "recipe_idx": idx,
                        "pack": self.pack_name,
                        "system": system,
                        "source": source,
                        "recipe_type": recipe_type,
                        "recipe_id": raw_id,
                        "recipe_ns": ns,
                        "recipe_path_tokens": list(path_tokens(path)),
                        "machines": machines,
                        "n_inputs": len(inputs),
                        "n_outputs": len(outputs),
                        "has_value_input": has_value_input,
                        "has_complex": has_complex,
                        "flags": sorted(rec_flags),
                        "dropped": dropped,
                    })
                    for flag in rec_flags:
                        self.flag_counts[flag] += 1
                    if dropped:
                        self.counters["dropped"] += 1
                        if not inputs and not outputs:
                            self.counters["dropped_empty_both"] += 1
                        elif not inputs:
                            self.counters["dropped_empty_inputs"] += 1
                        else:
                            self.counters["dropped_empty_outputs"] += 1
                    idx += 1
                    if len(rec_rows) >= BATCH_RECIPES:
                        _flush(rec_writer, RECIPE_SCHEMA, rec_rows)
                        _flush(con_writer, CONTENT_SCHEMA, con_rows)
                    if self.limit and idx >= self.limit:
                        break
            _flush(rec_writer, RECIPE_SCHEMA, rec_rows, force=True)
            _flush(con_writer, CONTENT_SCHEMA, con_rows, force=True)
        finally:
            rec_writer.close()
            con_writer.close()
        parse_seconds = time.time() - started

        skipped_blob = {}
        if self.files["skipped"] is not None:
            try:
                skipped_blob = read_json(self.files["skipped"])
            except Exception as exc:  # pragma: no cover
                self.counters["skipped_file_unreadable"] += 1
                skipped_blob = {"error": str(exc)}
        self._write_systems(systems_blob, skipped_blob)

        write_json(self.out_dir / "registry.json", self.registry)
        meta = {
            "pack": self.pack_name,
            "pack_dir": str(self.pack_dir),
            "recipes_file": self.files["recipes"].name,
            "recipes_file_bytes": self.files["recipes"].stat().st_size,
            "format_version": header.get("formatVersion"),
            "generated_at": header.get("generatedAt"),
            "minecraft_version": header.get("minecraftVersion"),
            "recipeCount": header.get("recipeCount"),
            "skippedCount": header.get("skippedCount"),
            "rows": idx,
            "kept": idx - self.counters["dropped"],
            "dropped": self.counters["dropped"],
            "dropped_by_reason": {
                "empty_inputs": self.counters["dropped_empty_inputs"],
                "empty_outputs": self.counters["dropped_empty_outputs"],
                "empty_both": self.counters["dropped_empty_both"],
            },
            "content_rows": sum(self.kind_counts.values()),
            "kind_counts": dict(sorted(self.kind_counts.items())),
            "stack_type_counts": dict(sorted(self.stack_type_counts.items())),
            "flag_counts": dict(sorted(self.flag_counts.items())),
            "chance_zero_systems": dict(sorted(self.chance_zero_systems.items(),
                                               key=lambda kv: (-kv[1], kv[0]))[:20]),
            "chance_zero_recipes": sum(self.chance_zero_systems.values()),
            "counters": dict(sorted(self.counters.items())),
            "registry_files": {k: v.name for k, v in sorted(self.files["registry"].items())},
            "has_registry": bool(self.files["registry"]),
            "systems": len(systems_blob),
            "parse_seconds": round(parse_seconds, 3),
            "peak_rss_mb": round(peak_rss_mb(), 1),
            "batch_recipes": BATCH_RECIPES,
        }
        write_json(self.out_dir / "load-meta.json", meta)
        return meta

    def _write_systems(self, systems_blob, skipped_blob) -> None:
        skipped_by_system = {}
        for entry in (skipped_blob.get("systems") or []) if isinstance(skipped_blob, dict) else []:
            sid = str(entry.get("id") or "")
            skipped_by_system[sid] = entry
        rows = []
        for entry in systems_blob:
            sid = str(entry.get("id") or "")
            sk = skipped_by_system.get(sid, {})
            rows.append({
                "system_id": sid,
                "source": str(entry.get("source") or ""),
                "recipe_type": (str(entry.get("recipeType"))
                                if isinstance(entry.get("recipeType"), str) else None),
                "jei_recipe_types": [str(t) for t in (entry.get("jeiRecipeTypes") or [])],
                "machines": [str(m) for m in (entry.get("machines") or [])],
                "recipe_count": _as_int(entry.get("recipeCount"), 0),
                "skipped": _as_int(entry.get("skipped"), 0),
                "skipped_listed": len(sk.get("recipes") or []),
                "skipped_reasons_json": json.dumps(sk.get("reasons") or {}, sort_keys=True),
            })
        # also surface systems that only appear in the skipped dump
        seen = {r["system_id"] for r in rows}
        for sid, sk in sorted(skipped_by_system.items()):
            if sid in seen:
                continue
            rows.append({
                "system_id": sid, "source": "", "recipe_type": None,
                "jei_recipe_types": [], "machines": [], "recipe_count": 0,
                "skipped": _as_int(sk.get("skipped"), 0),
                "skipped_listed": len(sk.get("recipes") or []),
                "skipped_reasons_json": json.dumps(sk.get("reasons") or {}, sort_keys=True),
            })
        table = _table(SYSTEM_SCHEMA, rows)
        pq.write_table(table, self.out_dir / "systems.parquet", compression="snappy")


def _table(schema: pa.Schema, rows: list[dict]) -> pa.Table:
    columns = []
    for field in schema:
        columns.append(pa.array([row[field.name] for row in rows], type=field.type))
    return pa.Table.from_arrays(columns, schema=schema)


def _flush(writer: pq.ParquetWriter, schema: pa.Schema, rows: list[dict], force: bool = False):
    if not rows and not force:
        return
    if rows:
        writer.write_table(_table(schema, rows))
        rows.clear()


# ------------------------------------------------------------------- driver


def _cache_is_fresh(out_dir: Path, recipes_file: Path) -> bool:
    meta_path = out_dir / "load-meta.json"
    if not meta_path.exists():
        return False
    try:
        meta = read_json(meta_path)
    except Exception:
        return False
    if meta.get("recipes_file") != recipes_file.name:
        return False
    if meta.get("recipes_file_bytes") != recipes_file.stat().st_size:
        return False
    return all((out_dir / name).exists()
               for name in ("recipes.parquet", "contents.parquet", "systems.parquet"))


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    here = Path(__file__).resolve().parent
    ap.add_argument("--exports-root", default="/Users/vfyjxf/ProjectDir/Planner-Final/exports")
    ap.add_argument("--cache-dir", default=str(here / "cache"))
    ap.add_argument("--packs", default="all",
                    help="comma separated export directory names, or 'all'")
    ap.add_argument("--force", action="store_true", help="re-load even if cached")
    ap.add_argument("--limit", type=int, default=0,
                    help="debug: stop after N recipes per pack (never use for real runs)")
    args = ap.parse_args(argv)

    exports_root = Path(args.exports_root)
    cache_dir = Path(args.cache_dir)
    cache_dir.mkdir(parents=True, exist_ok=True)

    available = list_packs(exports_root)
    if args.packs == "all":
        packs = available
    else:
        packs = [p.strip() for p in args.packs.split(",") if p.strip()]
        missing = [p for p in packs if p not in available]
        if missing:
            print(f"!! unknown packs: {missing}", file=sys.stderr)
            return 2

    packs_map_path = cache_dir / "packs.json"
    packs_map = read_json(packs_map_path) if packs_map_path.exists() else {}
    totals = Counter()
    t0 = time.time()
    for name in packs:
        slug = slugify(name)
        out_dir = cache_dir / slug
        recipes_file = find_pack_files(exports_root / name)["recipes"]
        packs_map[slug] = name
        if recipes_file is None:
            print(f"-- {name}: no recipes file, skipped")
            continue
        if not args.force and _cache_is_fresh(out_dir, recipes_file):
            meta = read_json(out_dir / "load-meta.json")
            print(f"== {name} [{slug}] cached rows={meta['rows']} "
                  f"dropped={meta['dropped']} parse={meta['parse_seconds']}s")
        else:
            meta = PackLoader(exports_root / name, out_dir, name, limit=args.limit).run()
            print(f"== {name} [{slug}] rows={meta['rows']} kept={meta['kept']} "
                  f"dropped={meta['dropped']} contents={meta['content_rows']} "
                  f"systems={meta['systems']} parse={meta['parse_seconds']}s "
                  f"rss={meta['peak_rss_mb']}MB registry={'y' if meta['has_registry'] else 'n'}")
        totals["rows"] += meta["rows"]
        totals["dropped"] += meta["dropped"]
        totals["contents"] += meta["content_rows"]
    write_json(packs_map_path, dict(sorted(packs_map.items())))
    print(f"-- total rows={totals['rows']} dropped={totals['dropped']} "
          f"contents={totals['contents']} wall={time.time() - t0:.1f}s "
          f"peak_rss={peak_rss_mb():.0f}MB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
