#!/usr/bin/env python
"""Phase 0c-2 — reference inference.  **This file is the Java contract.**

    ./.venv/bin/python predict_reference.py            # verify golden-sample

Reads nothing but ``out/models/*.json`` — no sklearn, no lightgbm, no parquet —
and reproduces every prediction the training libraries make.  A Java port is
correct exactly when it reproduces ``golden-sample.json`` the way this file
does, so keep this file small and obvious: every operation below is one loop
or one dot product.

Three models, four primitives:

* **linear**       ``p = sigmoid(((x filled) - mean) / std . w + b)``
* **linear, multi**``p = softmax(z . W^T + b)``
* **tree ensemble**``raw = sum over trees of the leaf reached; p = sigmoid(raw)``
  (multiclass: tree ``i`` belongs to class ``i mod n_classes``, then softmax)
* **antisymmetric**``margin = z . w`` with no intercept, and for the tree arm
  ``0.5 * (raw(x) - raw(swap(x)))`` where ``swap`` negates every column but
  the symmetric flags.

NaN policy: a missing feature is replaced by ``fill[i]`` *before* anything
else.  After that no NaN can reach a tree, so ``default_left`` is recorded but
never exercised; the walker honours it anyway.
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent


# ----------------------------------------------------------------- primitives


def sigmoid(x):
    return 1.0 / (1.0 + np.exp(-np.asarray(x, dtype=np.float64)))


def softmax(x):
    x = np.asarray(x, dtype=np.float64)
    m = x.max(axis=-1, keepdims=True)
    e = np.exp(x - m)
    return e / e.sum(axis=-1, keepdims=True)


def fill_nan(x, fill):
    x = np.asarray(x, dtype=np.float64)
    fill = np.asarray(fill, dtype=np.float64)
    return np.where(np.isfinite(x), x, fill)


def linear_raw(spec: dict, x) -> np.ndarray:
    """``((x filled) - mean) / std`` dotted with every weight row."""
    z = fill_nan(x, spec["fill"])
    z = (z - np.asarray(spec["mean"])) / np.asarray(spec["std"])
    return z @ np.asarray(spec["coef"]).T + np.asarray(spec["intercept"])


def tree_raw(spec: dict, x) -> np.ndarray:
    """Sum of reached leaf values, one column per class."""
    n_classes = max(int(spec.get("n_classes", 1)), 1)
    z = fill_nan(x, spec["fill"]) if "fill" in spec else np.asarray(x, float)
    if "scale" in spec:
        z = z / np.asarray(spec["scale"])
    z = np.atleast_2d(z)
    out = np.zeros((z.shape[0], n_classes))
    for t, tree in enumerate(spec["trees"]):
        cls = t % n_classes
        out[:, cls] += walk_tree(tree, z)
    return out


def walk_tree(tree: dict, z: np.ndarray) -> np.ndarray:
    """One tree, vectorised over rows.  ``child < 0`` means ``leaf -child-1``."""
    feat = tree["split_feature"]
    thr = tree["threshold"]
    left, right = tree["left"], tree["right"]
    dleft = tree["default_left"]
    leaf = tree["leaf_value"]
    root = tree["root"]
    out = np.empty(z.shape[0])
    for r in range(z.shape[0]):
        node = root
        while node >= 0:
            v = z[r, feat[node]]
            if math.isnan(v):
                go_left = bool(dleft[node])
            else:
                go_left = v <= thr[node]
            node = left[node] if go_left else right[node]
        out[r] = leaf[-node - 1]
    return out


# --------------------------------------------------------------------- models


class Reference:
    """Every exported model, loaded from JSON and nothing else."""

    def __init__(self, models_dir: Path):
        self.dir = Path(models_dir)
        self.spec = self._read("feature-spec.json")
        self.role = self._read("template-role.json")
        self.ranker = self._read("producer-ranker.json")
        self.leaf = self._read("leaf-acquirability.json")

    def _read(self, name):
        return json.loads((self.dir / name).read_text(encoding="utf-8"))

    # -- template role model ------------------------------------------------
    def junk_probability(self, x, arm: str | None = None) -> np.ndarray:
        node = self.role["junk"]
        spec = node[arm or node["default"]]
        if spec["kind"] == "lr":
            return sigmoid(linear_raw(spec, np.atleast_2d(x))[:, 0])
        return sigmoid(tree_raw(spec, x)[:, 0])

    def role_probabilities(self, x, arm: str | None = None) -> np.ndarray:
        node = self.role["role"]
        spec = node[arm or node["default"]]
        if spec["kind"] == "lr":
            raw = linear_raw(spec, np.atleast_2d(x))
            return softmax(raw) if raw.shape[1] > 1 else np.column_stack(
                [1 - sigmoid(raw[:, 0]), sigmoid(raw[:, 0])])
        return softmax(tree_raw(spec, x))

    def role_classes(self, arm: str | None = None) -> list:
        node = self.role["role"]
        return node[arm or node["default"]].get("classes", node["classes"])

    # -- producer ranker ----------------------------------------------------
    def margin(self, xpair, arm: str | None = None) -> np.ndarray:
        """Signed preference for ``a`` over ``b``; positive means prefer ``a``."""
        spec = self.ranker[arm or self.ranker["default"]]
        x = np.atleast_2d(np.asarray(xpair, dtype=np.float64))
        if spec["kind"] == "lr":
            return (x / np.asarray(spec["scale"])) @ np.asarray(spec["coef"])[0]
        return 0.5 * (tree_raw(spec, x)[:, 0]
                      - tree_raw(spec, self.swap(x))[:, 0])

    def swap(self, x) -> np.ndarray:
        """The same pair, the other way round: negate all but the symmetric flags."""
        x = np.atleast_2d(np.asarray(x, dtype=np.float64))
        names = self.spec["pair_features"]["names"]
        out = -x.copy()
        for name in self.spec["pair_features"]["symmetric_flags"]:
            out[:, names.index(name)] = x[:, names.index(name)]
        return out

    # -- leaf acquirability -------------------------------------------------
    def leaf_seed(self, z, pack_mean: float, pack_std: float):
        """``(acquirability, seed)`` for leaves of one pack.

        ``pack_mean`` / ``pack_std`` are the mean and standard deviation of
        ``w . z`` over **that pack's own leaves** — the caller computes them
        from the same pack, exactly as ``economy.py`` normalises per pack.
        """
        s = np.atleast_2d(np.asarray(z, dtype=np.float64)) @ \
            np.asarray(self.leaf["weights"])
        acq = sigmoid((s - pack_mean) / (pack_std if pack_std > 1e-12 else 1.0))
        return acq, np.power(float(self.leaf["spread"]), 1.0 - acq)


# ------------------------------------------------------------------ verifying


def verify(models_dir: Path, tol: float = 1e-6) -> int:
    ref = Reference(models_dir)
    golden = json.loads((Path(models_dir) / "golden-sample.json")
                        .read_text(encoding="utf-8"))
    rows = golden["rows"]
    x = np.array([[np.nan if v is None else v for v in r["x"]] for r in rows])
    fails = []

    def check(name, got, want):
        got, want = np.asarray(got, float), np.asarray(want, float)
        err = float(np.abs(got - want).max()) if got.size else 0.0
        status = "ok " if err <= tol else "FAIL"
        print(f"  {status} {name:<44} max |delta| = {err:.3e}  (n={got.size:,})")
        if err > tol:
            fails.append(name)

    for arm in ("lr", "lgb"):
        check(f"junk probability [{arm}]", ref.junk_probability(x, arm),
              [r[f"junk_{arm}"] for r in rows])
        check(f"role probabilities [{arm}]", ref.role_probabilities(x, arm),
              [r[f"role_{arm}"] for r in rows])

    px = np.array([p["x"] for p in golden["pairs"]])
    for arm in ("lr", "lgb"):
        check(f"ranker margin [{arm}]", ref.margin(px, arm),
              [p[f"margin_{arm}"] for p in golden["pairs"]])

    lz = np.array([l["z"] for l in golden["leaves"]])
    acq = np.empty(len(lz))
    seed = np.empty(len(lz))
    for i, row in enumerate(golden["leaves"]):
        a, s = ref.leaf_seed(row["z"], row["pack_mean"], row["pack_std"])
        acq[i], seed[i] = a[0], s[0]
    check("leaf acquirability", acq, [l["acquirability"] for l in golden["leaves"]])
    check("leaf seed", seed, [l["seed"] for l in golden["leaves"]])

    print(f"\n  {len(rows):,} template rows over {golden['n_pack_groups']} pack "
          f"groups, {len(golden['pairs'])} pairs, {len(golden['leaves'])} leaves")
    if fails:
        print(f"  FAILED: {', '.join(fails)}")
        return 1
    print("  reference inference reproduces every library prediction")
    return 0


def main(argv=None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    models = Path(args[0]) if args else HERE / "out" / "models"
    return verify(models)


if __name__ == "__main__":
    raise SystemExit(main())
