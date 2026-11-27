#!/usr/bin/env python
"""Phase 0a invariants.  pytest is not installed — run with the venv python:

    .venv/bin/python tests/test_phase0a.py

Everything runs on the small ``fom`` pack into a throwaway cache, so the whole
file takes a couple of seconds.
"""
from __future__ import annotations

import ast
import hashlib
import re
import shutil
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(ROOT))

import pyarrow.parquet as pq  # noqa: E402

import load_exports  # noqa: E402
import ontology  # noqa: E402
from common import find_pack_files, read_json  # noqa: E402

EXPORTS = Path("/Users/vfyjxf/ProjectDir/Planner-Final/exports")
PACK = "fom"

PASSED = []


def check(name: str, condition: bool, detail: str = "") -> None:
    assert condition, f"FAILED: {name}{(': ' + detail) if detail else ''}"
    PASSED.append(name)
    print(f"  ok  {name}" + (f"  ({detail})" if detail else ""))


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


# ----------------------------------------------------------------- the runs


def run_pipeline(workdir: Path):
    cache = workdir / "cache"
    out = workdir / "out"
    rc = load_exports.main(["--exports-root", str(EXPORTS), "--cache-dir", str(cache),
                            "--packs", PACK, "--force"])
    assert rc == 0, "load_exports returned non-zero"
    rc = ontology.main(["--cache-dir", str(cache), "--out-dir", str(out),
                        "--packs", PACK, "--no-cross-pack"])
    assert rc == 0, "ontology returned non-zero"
    return cache, out


def test_row_counts(cache: Path, out: Path) -> None:
    meta = read_json(cache / PACK / "load-meta.json")
    recipes = pq.read_table(cache / PACK / "recipes.parquet")
    contents = pq.read_table(cache / PACK / "contents.parquet")

    source = read_json(find_pack_files(EXPORTS / PACK)["recipes"])
    check("recipes rows == recipeCount in the dump",
          recipes.num_rows == source["recipeCount"],
          f"{recipes.num_rows} == {source['recipeCount']}")
    check("load-meta rows == recipeCount", meta["rows"] == source["recipeCount"])
    expected_contents = sum(len(r.get("inputs") or []) + len(r.get("outputs") or [])
                            for r in source["recipes"])
    check("contents rows == every input+output entry",
          contents.num_rows == expected_contents,
          f"{contents.num_rows} == {expected_contents}")

    n_in = recipes.column("n_inputs").to_pylist()
    n_out = recipes.column("n_outputs").to_pylist()
    dropped = recipes.column("dropped").to_pylist()
    check("n_inputs + n_outputs sums to the contents table",
          sum(n_in) + sum(n_out) == contents.num_rows)
    check("dropped is exactly 'no inputs or no outputs'",
          all(d == (i == 0 or o == 0) for d, i, o in zip(dropped, n_in, n_out)))
    check("kept + dropped == rows", meta["kept"] + meta["dropped"] == meta["rows"])

    idx = recipes.column("recipe_idx").to_pylist()
    check("recipe_idx is dense stream order", idx == list(range(len(idx))))
    con_idx = contents.column("recipe_idx").to_pylist()
    check("contents keep recipe order", con_idx == sorted(con_idx))
    check("no content references a missing recipe",
          not con_idx or max(con_idx) < recipes.num_rows)

    sentinel = contents.column("amount_sentinel").to_pylist()
    amounts = contents.column("amount").to_pylist()
    check("sentinel amounts are stored as null",
          all(a is None for a, s in zip(amounts, sentinel) if s))

    # no rule ever acts as a filter: nothing but structural emptiness is dropped
    check("no recipe dropped for a non-structural reason",
          meta["dropped"] == sum(meta["dropped_by_reason"].values()))


def test_ontology_outputs(cache: Path, out: Path) -> None:
    meta = read_json(out / PACK / "ontology-meta.json")
    recipes = pq.read_table(cache / PACK / "recipes.parquet")
    identity = pq.read_table(out / PACK / "identity.parquet")
    templates = pq.read_table(out / PACK / "templates.parquet")
    assign = pq.read_table(out / PACK / "recipe_template.parquet")

    base_ids = identity.column("base_id").to_pylist()
    check("identity has one row per distinct base id",
          len(base_ids) == len(set(base_ids)), f"{len(base_ids)} ids")
    kept = sum(1 for d in recipes.column("dropped").to_pylist() if not d)
    check("every kept recipe gets exactly one template",
          assign.num_rows == kept, f"{assign.num_rows} == {kept}")
    check("template sizes sum to the assignments",
          sum(templates.column("size").to_pylist()) == assign.num_rows)
    tids = set(templates.column("template_id").to_pylist())
    check("assignments only reference known templates",
          set(assign.column("template_id").to_pylist()) <= tids)
    check("template ids are unique",
          len(tids) == templates.num_rows)
    check("multi-member templates exist (folding actually folds)",
          meta["multi_member_templates"] > 0,
          f"{meta['multi_member_templates']} multi-member, "
          f"{meta['multi_member_coverage'] * 100:.1f}% coverage")
    check("token classes were induced", meta["token_classes"] > 0,
          f"{meta['token_classes']} classes")
    check("multi-member coverage stays above the 55% floor",
          meta["multi_member_coverage"] >= 0.55,
          f"{meta['multi_member_coverage'] * 100:.1f}%")
    largest = max(templates.column("size").to_pylist())
    check("the colour x dyeable-form family still folds into one template",
          largest >= 48, f"largest template = {largest}")

    classes = read_json(out / PACK / "token-classes.json")["classes"]
    seen_members = set()
    for cid, info in classes.items():
        check_members = set(info["members"])
        assert not (check_members & seen_members), f"class {cid} overlaps another"
        seen_members |= check_members
        assert len(info["members"]) >= 2, f"class {cid} is a singleton"
    check("token classes are a disjoint partition of >=2-member groups",
          True, f"{len(seen_members)} classified tokens")


def test_determinism(a: Path, b: Path) -> None:
    for rel in ("cache/%s/recipes.parquet" % PACK, "cache/%s/contents.parquet" % PACK,
                "cache/%s/systems.parquet" % PACK,
                "out/%s/identity.parquet" % PACK, "out/%s/templates.parquet" % PACK,
                "out/%s/recipe_template.parquet" % PACK,
                "out/%s/token-classes.json" % PACK):
        check(f"byte-identical across runs: {rel}", digest(a / rel) == digest(b / rel))

    ta = pq.read_table(a / f"out/{PACK}/recipe_template.parquet").to_pydict()
    tb = pq.read_table(b / f"out/{PACK}/recipe_template.parquet").to_pydict()
    check("template ids are stable between runs", ta == tb,
          f"{len(set(ta['template_id']))} distinct ids")

    meta_a = read_json(a / f"out/{PACK}/ontology-meta.json")
    meta_b = read_json(b / f"out/{PACK}/ontology-meta.json")
    volatile = {"seconds", "seconds_tokens"}
    check("ontology metadata is stable (timings aside)",
          {k: v for k, v in meta_a.items() if k not in volatile}
          == {k: v for k, v in meta_b.items() if k not in volatile})


# ------------------------------------------------ the bigram promotion criterion

def _synthetic_sequences():
    """A vocabulary-free miniature of the two shapes the criterion must separate.

    ``m*`` and ``s*`` are two families of slot fillers that BOTH occur on their
    own (``m1_f1``, ``s1_f1``) and also compose (``m1_s1_f1``) — the shape of a
    colour+material compound.  ``p1`` is a bound prefix: it only ever occurs in
    front of ``m1`` and never fills the slot alone — the shape of the first half
    of a two-token colour.  A frequency rule promotes both; the
    non-compositionality rule must promote only the second.
    """
    mods = ["m1", "m2", "m3"]
    mats = ["s1", "s2", "s3"]
    forms = ["f1", "f2", "f3"]
    seqs = set()
    for form in forms:
        for mod in mods:
            seqs.add((mod, form))
        for mat in mats:
            seqs.add((mat, form))
        for mod in mods:
            for mat in mats:
                seqs.add((mod, mat, form))
        seqs.add(("p1", "m1", form))
    return [(seq, 1) for seq in sorted(seqs)]


def test_bigram_promotion_is_non_compositional(out: Path) -> None:
    seqs = _synthetic_sequences()
    buckets = ontology.make_buckets(seqs, include_bigrams=True)
    candidate_contexts = [key for key, members in buckets.items()
                          if "m1_s1" in members
                          and len({t for t in members if "_" not in t} - {"m1", "s1"}) >= 2]
    check("a compositional compound still passes the frequency gates",
          len(candidate_contexts) >= 3, f"{len(candidate_contexts)} contexts")

    promoted = ontology.promote_bigrams(buckets, 2, 2, ontology.MAX_BUCKET)
    check("compositional compound is NOT promoted (the black_acacia case)",
          "m1_s1" not in promoted, f"promoted={sorted(promoted)}")
    check("bound-prefix bigram IS promoted (the light_blue case)",
          "p1_m1" in promoted, f"promoted={sorted(promoted)}")
    for tok in promoted:
        head, tail = ontology.split_bigram(tok)
        assert head and tail, f"malformed merged token {tok!r}"

    meta = read_json(out / PACK / "ontology-meta.json")
    check("promotion stays a small minority of the id vocabulary",
          meta["promoted_bigrams"] < meta["distinct_tokens"] / 4,
          f"{meta['promoted_bigrams']} promoted / {meta['distinct_tokens']} tokens")


def test_promotion_criterion_on_real_data(cache: Path) -> None:
    """Every promoted bigram must out-weigh the 'head is a droppable modifier'
    reading — recomputed here from the buckets, not read back from the run."""
    vocab = ontology.collect_vocab(cache / PACK)
    seq_masks = [(seq, 1) for seq in sorted(ontology.sequences_of(vocab["ids"]))]
    buckets = ontology.make_buckets(seq_masks, include_bigrams=True)
    promoted = ontology.promote_bigrams(buckets, 2, 2, ontology.MAX_BUCKET)

    unit_contexts: dict = {}
    solo_contexts: dict = {}
    for key, members in buckets.items():
        if len(members) < 2 or len(members) > ontology.MAX_BUCKET:
            continue
        singles = {t for t in members if "_" not in t}
        if len(singles) >= 2:
            for tok in singles:
                solo_contexts[tok] = solo_contexts.get(tok, 0) + 1
        for tok in members:
            if "_" in tok and singles - set(ontology.split_bigram(tok)):
                unit_contexts[tok] = unit_contexts.get(tok, 0) + 1
    offenders = [tok for tok in promoted
                 if unit_contexts.get(tok, 0) < solo_contexts.get(
                     ontology.split_bigram(tok)[0], 0)]
    check("no promoted bigram is out-weighed by its head standing alone",
          not offenders, f"{len(promoted)} promoted, offenders={offenders[:5]}")
    heads = {ontology.split_bigram(tok)[0] for tok in promoted}
    check("promoted heads are bound, not productive slot fillers",
          all(solo_contexts.get(h, 0) <= max(unit_contexts.get(t, 0)
                                             for t in promoted
                                             if ontology.split_bigram(t)[0] == h)
              for h in heads))


# ------------------------------------------------------- the no-vocabulary rule

FORBIDDEN = re.compile(
    r"(?i)(?:^|_)(oak|spruce|birch|jungle|acacia|cherry|bamboo|crimson|warped|"
    r"copper|iron|gold|golden|diamond|netherite|stone|cobblestone|granite|"
    r"andesite|diorite|deepslate|wool|dye|concrete|terracotta|glass|planks|"
    r"slab|stairs|ingot|nugget|plate|gear|"
    r"white|black|red|green|blue|yellow|pink|cyan|magenta|lime|brown|"
    r"junk|duplicate|recycle|recycling|shapeless|smelting|furnace|crafting|"
    r"minecraft|forge|neoforge|create|mekanism|thermal|ae2)(?:$|_)")

# Values of the export schema's own enumerations (SCHEMA.md) — these are field
# names and field values, not a registry vocabulary.
SCHEMA_WORDS = {
    "stack", "tag", "any", "typedAny", "value", "complex", "item", "fluid",
    "kind", "amount", "chance", "matchStrategy", "identity", "in", "out",
    "recipes", "systems", "inputs", "outputs", "machines", "stacks", "values",
    "implementation", "stackType", "valueType", "components", "count", "id",
    "type", "source", "system", "recipeType", "recipeCount", "skippedCount",
    "contentTypes", "stackTypes", "tagTypes", "valueTypes", "variantTypes",
    "contentType", "tags", "formatVersion", "generatedAt", "minecraftVersion",
    "_stack", "mixed", "unknown:unknown", "minecraft_version",
    # Phase 0b column and role names, reused verbatim by the Phase 0c-1
    # feature layer.  "concrete" here is the logic sense (a content entry that
    # resolved to a real item rather than to a member-less group), and the
    # roles are shape words the economy layer coined, not registry words.
    "recycling_shaped", "conversion", "production", "generation", "noop",
    "zero_concrete_inputs", "machine_tier", "machine_unbuildable",
    "machine_id", "recipe_shape", "shape_role",
}

#: Prefixes / suffixes the feature layer bolts onto a Phase 0a/0b column name.
#: Stripping them before the lookup keeps the allowance to the *column*, not to
#: the word: ``l2_zero_concrete_inputs_share`` is allowed, ``blue_concrete``
#: still is not.
AFFIX_RE = re.compile(r"^(?:l\d_)?(.*?)(?:_share|_mean|_max|_min|_absmax)?$")

#: The one sanctioned vocabulary in this package: the label-space table of
#: ``labels.py``.  Its markers delimit it; the words it defines are then
#: allowed elsewhere in that module and nowhere else.
LABEL_BLOCK = ("BEGIN LABEL-SPACE TABLE", "END LABEL-SPACE TABLE")


def _label_space_words(path: Path) -> set:
    """Tokens defined inside ``labels.py``'s single sanctioned block.

    Anything the block writes down — verdict words, the label sources'
    category names, source names, role names and the column names derived
    from them — may appear elsewhere in that module.  Anything else may not,
    so a mod or item name cannot sneak in under the exemption.
    """
    text = path.read_text(encoding="utf-8")
    start, end = text.index(LABEL_BLOCK[0]), text.index(LABEL_BLOCK[1])
    first = text.count("\n", 0, start) + 1
    last = text.count("\n", 0, end) + 1
    words = set()
    for node in ast.walk(ast.parse(text)):
        texts = []
        if isinstance(node, ast.Constant) and isinstance(node.value, str):
            texts = [node.value]
        elif isinstance(node, ast.Name):
            texts = [node.id]
        if not texts or not (first <= getattr(node, "lineno", 0) <= last):
            continue
        for raw in texts:
            words.update(t for t in re.split(r"[^A-Za-z0-9]+", raw.lower()) if t)
    return words


def _allowed(text: str, extra: set) -> bool:
    """True when every forbidden-looking token of ``text`` is in ``extra``."""
    tokens = [t for t in re.split(r"[^A-Za-z0-9]+", text.lower()) if t]
    return all(not FORBIDDEN.search(t) or t in extra for t in tokens)


#: ``<ns>:<path>`` shaped literal — a registry id would look like this.
NAMESPACE_LIKE = re.compile(r"^[a-z][a-z0-9_]*:[a-z0-9_][a-z0-9_/.-]*$")


def _code_strings_and_names(path: Path):
    tree = ast.parse(path.read_text(encoding="utf-8"))
    docstrings = set()
    for node in ast.walk(tree):
        if isinstance(node, (ast.Module, ast.ClassDef, ast.FunctionDef,
                             ast.AsyncFunctionDef)):
            doc = ast.get_docstring(node, clean=False)
            if doc is not None:
                docstrings.add(doc)
    out = []
    for node in ast.walk(tree):
        if isinstance(node, ast.Constant) and isinstance(node.value, str):
            if node.value in docstrings:
                continue
            out.append(("literal", node.value, node.lineno))
        elif isinstance(node, ast.Name):
            out.append(("name", node.id, node.lineno))
        elif isinstance(node, ast.Attribute):
            out.append(("attr", node.attr, node.lineno))
    return out


MODULES = ("common.py", "load_exports.py", "ontology.py",
           "features.py", "labels.py", "llm_labels.py",
           # Phase 0c-2: the trainer, the leaf aggregation, the exporter and
           # the reference inference are held to the same rule.
           "pair_features.py", "train_eval.py", "export_model.py",
           "predict_reference.py",
           # Phase 3: the hierarchical planner and its offline A/B harness.
           "planner.py", "eval_planner.py",
           # Phase 4-5: the feedback layer and the performance harness.
           "feedback.py", "bench_planner.py")

#: Modules allowed to *use* — never to extend — the words ``labels.py``'s one
#: sanctioned table defines.  The Phase 0c-2 modules import those names from
#: ``labels.py`` instead of restating them, so the exemption stays a single
#: table and a mod or item name still cannot appear anywhere.
LABEL_SPACE_USERS = ("labels.py", "pair_features.py", "train_eval.py",
                     "export_model.py", "predict_reference.py",
                     "planner.py", "eval_planner.py", "feedback.py",
                     "bench_planner.py")

#: One software-engineering term the forbidden-word regex catches as a false
#: positive: a *golden file* is recorded expected output, the testing sense,
#: and has nothing to do with the material.  It is allowed only inside the
#: Phase 0c-2 modules, only as this bare token, and the namespace-literal
#: check still rejects any ``<ns>:<path>`` that happens to contain it.
TOOLING_WORDS = {"golden"}


def test_no_rule_vocabulary() -> None:
    """No module may write down a mod / item / material / colour name.

    Phase 0c-1 extends the rule to ``features.py`` (no exemption at all) and
    to ``labels.py``, which gets exactly one: the words its single documented
    label-space table defines.  That table renames the *label sources'* own
    category names onto the plan's role set — label bookkeeping, training
    only — and nothing outside it may introduce a new word.
    """
    label_words = _label_space_words(ROOT / "labels.py")
    offenders = []
    for name in MODULES:
        path = ROOT / name
        extra = (label_words | TOOLING_WORDS
                 if name in LABEL_SPACE_USERS else set())
        for kind, text, lineno in _code_strings_and_names(path):
            if text in SCHEMA_WORDS or AFFIX_RE.sub(r"\1", text) in SCHEMA_WORDS:
                continue
            if FORBIDDEN.search(text) and not _allowed(text, extra):
                offenders.append(f"{name}:{lineno} {kind} {text!r}")
            if kind == "literal" and NAMESPACE_LIKE.match(text) \
                    and not text.startswith(("cls:", "t:", "var:", "kg:", "tpl:",
                                             "spool:", "wpool:")):
                offenders.append(f"{name}:{lineno} namespace-like literal {text!r}")
    check("no mod / material / colour / rule vocabulary in executable code",
          not offenders, "; ".join(offenders[:5]) or
          f"clean over {len(MODULES)} modules")


def test_label_space_table_is_the_only_exemption() -> None:
    """The exemption must be a *table*, not a licence.

    Every forbidden-looking word that ``labels.py`` uses outside the block has
    to be defined inside it, and the block itself must not contain a
    registry-shaped id.
    """
    words = _label_space_words(ROOT / "labels.py")
    check("the label-space block defines a bounded vocabulary",
          0 < len(words) < 200, f"{len(words)} tokens")
    text = (ROOT / "labels.py").read_text(encoding="utf-8")
    start, end = text.index(LABEL_BLOCK[0]), text.index(LABEL_BLOCK[1])
    block = text[start:end]
    ids = [line for line in block.splitlines()
           if re.search(r"\"[a-z0-9_]+:[a-z0-9_/]+\"", line)]
    check("the label-space block names no registry id", not ids,
          "; ".join(ids[:2]) or "clean")


def test_loader_matches_java_shape() -> None:
    """The few normalisations copied from ExportPackLoader stay vocabulary-free."""
    check("content type ids reduce to their path",
          load_exports.family_of_content_type("some_mod:thing") == "thing")
    check("stack type ids reduce to their family",
          load_exports.family_of_stack_type("some_mod:thing_stack") == "thing")
    check("bare families pass through",
          load_exports.family_of_content_type("item") == "item")


def main() -> int:
    print("Phase 0a checks (pack: %s)" % PACK)
    tmp = Path(tempfile.mkdtemp(prefix="phase0a-test-"))
    try:
        a = tmp / "run-a"
        b = tmp / "run-b"
        cache_a, out_a = run_pipeline(a)
        run_pipeline(b)
        print("-- data invariants")
        test_row_counts(cache_a, out_a)
        print("-- ontology invariants")
        test_ontology_outputs(cache_a, out_a)
        print("-- determinism")
        test_determinism(a, b)
        print("-- bigram promotion")
        test_bigram_promotion_is_non_compositional(out_a)
        test_promotion_criterion_on_real_data(cache_a)
        print("-- design rules")
        test_no_rule_vocabulary()
        test_label_space_table_is_the_only_exemption()
        test_loader_matches_java_shape()
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    print(f"\n{len(PASSED)} checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
