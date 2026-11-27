"""Shared helpers for the Phase 0a experiment layer (export loader + ontology).

Design rule inherited from experiments/generalization/code/extract_features.py:

    NO hardcoded mod / item / colour / material / system-name vocabularies.

Nothing in this package may branch on a concrete registry name.  The only
domain knowledge allowed is *pure shape*: the export schema's own field names
(``kind``, ``stack``, ``tag``, ``values``, ``stacks``, ``amount``, ``chance``,
``matchStrategy``, ...), and structural facts such as "zero inputs" or
"inputs identical to outputs".  Every class of materials / forms / modifiers
is induced from the data by :mod:`ontology`.
"""
from __future__ import annotations

import hashlib
import json
import re
import unicodedata
from pathlib import Path

# ---------------------------------------------------------------- constants

#: Integer sentinel used by the exporter for "unbounded" amounts (Integer.MAX_VALUE).
SENTINEL_AMOUNT = 2147483647

#: Suffix produced by ExportPackLoader.java L527 when it renders an alternative
#: list as a single display id (``first|+N``).  It must never appear in raw
#: export data; if it does we strip it and flag the row.
ALT_SUFFIX_RE = re.compile(r"\|\+\d+$")

#: Registry side files, ``prefix -> (array key, output key)``.
REGISTRY_FILES = {
    "content-types-": ("contentTypes", "contentTypes"),
    "stack-types-": ("stackTypes", "stackTypes"),
    "tag-types-": ("tagTypes", "tagTypes"),
    "value-types-": ("valueTypes", "valueTypes"),
    "variant-types-": ("variantTypes", "variantTypes"),
}

_SLUG_RE = re.compile(r"[^a-z0-9]+")
_TOKEN_SPLIT_RE = re.compile(r"[/_.\-]+")


# ------------------------------------------------------------------ strings


def slugify(name: str) -> str:
    """Filesystem-safe, deterministic slug for a pack directory name."""
    norm = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode()
    slug = _SLUG_RE.sub("-", norm.lower()).strip("-")
    return slug or "pack"


def strip_alt_suffix(value: str) -> tuple[str, bool]:
    """Strip a ``|+N`` alternative-count suffix.  Returns ``(clean, stripped)``."""
    if "|+" not in value:
        return value, False
    clean = ALT_SUFFIX_RE.sub("", value)
    return clean, clean != value


def split_id(raw: str) -> tuple[str, str]:
    """``ns:path`` -> ``(ns, path)``; without a colon the namespace is ``""``."""
    if ":" in raw:
        ns, _, path = raw.partition(":")
        return ns, path
    return "", raw


def path_tokens(path: str) -> tuple[str, ...]:
    """Split an id path into tokens on ``/``, ``_``, ``.`` and ``-``.

    Purely lexical; no vocabulary is consulted.
    """
    if not path:
        return ()
    return tuple(t for t in _TOKEN_SPLIT_RE.split(path.lower()) if t)


def id_tokens(raw: str) -> tuple[str, ...]:
    """Path tokens of a full ``ns:path`` id (namespace dropped)."""
    return path_tokens(split_id(raw)[1])


# ------------------------------------------------------------------- hashes


def canon_json(obj) -> str:
    """Canonical JSON text: sorted keys, compact, stable float formatting."""
    return json.dumps(obj, sort_keys=True, separators=(",", ":"),
                      ensure_ascii=False, default=_json_default)


def _json_default(obj):
    # ijson can hand back Decimal for non-integral numbers when use_float=False;
    # everything unknown degrades to its repr so hashing never fails.
    try:
        return float(obj)
    except Exception:
        return str(obj)


def stable_hash(obj, width: int = 16) -> str:
    """Deterministic content hash (sha1 of canonical JSON), truncated."""
    return hashlib.sha1(canon_json(obj).encode("utf-8")).hexdigest()[:width]


# -------------------------------------------------------------------- files


def find_pack_files(pack_dir: Path) -> dict:
    """Locate the recipes file, the skipped file and the registry side files.

    Mirrors ExportPackLoader.findRecipesFile / loadRegistriesAndAttach: a pack
    directory holds exactly one ``recipes-<ts>.json``; registry files may be
    missing entirely (old packs such as atm10).
    """
    found: dict = {"recipes": None, "skipped": None, "export_done": None, "registry": {}}
    for path in sorted(pack_dir.iterdir()):
        name = path.name
        if not name.endswith(".json"):
            continue
        if name.startswith("recipes-"):
            if found["recipes"] is None:
                found["recipes"] = path
        elif name.startswith("skipped-"):
            if found["skipped"] is None:
                found["skipped"] = path
        elif name == "EXPORT_DONE.json":
            found["export_done"] = path
        else:
            for prefix in REGISTRY_FILES:
                if name.startswith(prefix):
                    found["registry"].setdefault(prefix.rstrip("-"), path)
    return found


def read_json(path: Path):
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path: Path, obj) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(obj, handle, ensure_ascii=False, indent=2, sort_keys=False,
                  default=_json_default)
        handle.write("\n")


def list_packs(exports_root: Path) -> list[str]:
    """Pack directory names that contain a recipes dump, sorted."""
    packs = []
    for entry in sorted(exports_root.iterdir()):
        if not entry.is_dir():
            continue
        if any(p.name.startswith("recipes-") and p.name.endswith(".json")
               for p in entry.iterdir()):
            packs.append(entry.name)
    return packs


def peak_rss_mb() -> float:
    import resource
    import sys
    value = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
    # Linux reports KiB, macOS reports bytes.
    return value / (1024.0 * 1024.0) if sys.platform == "darwin" else value / 1024.0


# ------------------------------------------------------------------ parquet


def write_parquet(path: Path, rows, schema) -> int:
    """Write ``rows`` (a list of dicts) under ``schema``, deterministically.

    Same settings the Phase 0a writers use (snappy, one row group), so two runs
    over the same input produce byte-identical files.
    """
    import pyarrow as pa
    import pyarrow.parquet as pq

    path.parent.mkdir(parents=True, exist_ok=True)
    columns = [pa.array([r[f.name] for r in rows], type=f.type) for f in schema]
    pq.write_table(pa.Table.from_arrays(columns, schema=schema), path,
                   compression="snappy")
    return len(rows)


# ------------------------------------------------------- cross-phase helpers


def normalise_name(name: str) -> str:
    """Lowercase, keep only ``[a-z0-9]``.

    Used to line up the three different spellings the same pack has across the
    corpus (export directory name, cache slug, review-file basename) without
    ever writing a pack name down.
    """
    return re.sub(r"[^a-z0-9]+", "", name.lower())


def match_name(name: str, candidates) -> str | None:
    """Resolve ``name`` against ``candidates`` by normalised form.

    Exact normalised equality first, then a *unique* prefix match, then a
    *unique* containment match.  Ambiguity resolves to ``None`` so the caller
    can count and report it rather than guess.
    """
    key = normalise_name(name)
    if not key:
        return None
    norm = {c: normalise_name(c) for c in candidates}
    exact = [c for c, n in norm.items() if n == key]
    if len(exact) == 1:
        return exact[0]
    pref = [c for c, n in norm.items() if n.startswith(key) or key.startswith(n)]
    if len(pref) == 1:
        return pref[0]
    contains = [c for c, n in norm.items() if key in n or n in key]
    if len(contains) == 1:
        return contains[0]
    return None


def pack_group_map(out_dir: Path, slugs) -> dict:
    """``slug -> pack_group`` where a group is one *content-identical* export.

    Two exports of the same pack differ in directory name, file name and
    member order but fold to exactly the same template multiset, so the sha1
    of that multiset identifies them without ever looking at a pack name.
    (Same fingerprint the Phase 0b cross-pack stage uses.)  The group id is
    the lexicographically smallest slug in the group, so it is readable and
    stable.
    """
    import numpy as np
    import pyarrow.parquet as pq

    fingerprints = {}
    for slug in slugs:
        table = pq.read_table(Path(out_dir) / slug / "recipe_template.parquet",
                              columns=["template_id"])
        ids, counts = np.unique(
            table.column("template_id").to_numpy(zero_copy_only=False),
            return_counts=True)
        fingerprints[slug] = stable_hash(
            [[str(i), int(c)] for i, c in zip(ids, counts)], 16)
    members = {}
    for slug, fp in fingerprints.items():
        members.setdefault(fp, []).append(slug)
    return {slug: min(members[fp]) for slug, fp in fingerprints.items()}
