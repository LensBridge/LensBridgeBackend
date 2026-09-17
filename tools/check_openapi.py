#!/usr/bin/env python3
"""
Quality gate for the committed openapi.yaml.

The generated clients in LensBridgeFrontend, MusallahBoard/frontend and the Go
agent are only as good as this spec. A response declared as a bare `type: object`
produces `unknown` in TypeScript and `map[string]interface{}` in Go, which is
indistinguishable from having no contract at all -- so the build should refuse it.

Usage:
    python tools/check_openapi.py openapi.yaml
    python tools/check_openapi.py openapi.yaml --baseline old.yaml

Exit codes:
    0  clean
    1  violations found
    2  bad invocation / unreadable spec
"""
import argparse
import sys

try:
    import yaml
except ImportError:
    sys.exit("PyYAML is required: pip install pyyaml")

HTTP_METHODS = {"get", "put", "post", "delete", "patch", "head", "options", "trace"}

# Endpoints knowingly exempt from the typed-response rule, each with a reason.
# Keep this list short and justified -- it is the escape hatch that lets the gate
# stay strict everywhere else.
ALLOWED_UNTYPED = {
    # ("POST", "/api/example"): "returns a binary stream, not JSON",
}


def operations(spec):
    """Yield (method, path, operation) for every operation in the spec."""
    for path, item in (spec.get("paths") or {}).items():
        for method, op in (item or {}).items():
            if method.lower() in HTTP_METHODS and isinstance(op, dict):
                yield method.upper(), path, op


def is_contentless(schema):
    """True if the schema carries no usable type information."""
    if not schema:
        return True
    # `type: object` with no properties, no $ref, no additionalProperties
    return set(schema) <= {"type"} and schema.get("type") == "object"


def find_untyped(spec):
    out = []
    for method, path, op in operations(spec):
        for code, resp in (op.get("responses") or {}).items():
            for media in (resp.get("content") or {}).values():
                if is_contentless(media.get("schema")):
                    if (method, path) in ALLOWED_UNTYPED:
                        continue
                    out.append((method, path, code, op.get("operationId")))
    return out


def find_bad_operation_ids(spec):
    """
    operationIds become method names in generated clients, so they must be
    unique, present, and meaningful. springdoc derives them from Java method
    names and appends _2, _3 ... on collision, which produces churn in the
    generated code every time an unrelated method is added.
    """
    seen, dupes, missing, vague = {}, [], [], []
    # Single-word ids that say nothing once detached from their controller.
    VAGUE = {"get", "list", "create", "update", "delete", "revoke", "post", "all"}
    for method, path, op in operations(spec):
        oid = op.get("operationId")
        if not oid:
            missing.append((method, path))
            continue
        if oid.rstrip("0123456789").endswith("_"):
            dupes.append((method, path, oid))
        if oid.lower() in VAGUE:
            vague.append((method, path, oid))
        seen.setdefault(oid, []).append((method, path))
    collisions = [(o, v) for o, v in seen.items() if len(v) > 1]
    return missing, dupes, vague, collisions


def compare_baseline(spec, baseline):
    """Report operations present in baseline but missing now -- i.e. accidental
    renames or deletions. Additions are fine and not reported."""
    now = {(m, p) for m, p, _ in operations(spec)}
    was = {(m, p) for m, p, _ in operations(baseline)}
    return sorted(was - now)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("spec")
    ap.add_argument("--baseline", help="previous spec to diff against for removed operations")
    ap.add_argument("--strict-ids", action="store_true",
                    help="also fail on vague/duplicate operationIds")
    args = ap.parse_args()

    try:
        with open(args.spec, encoding="utf-8") as fh:
            spec = yaml.safe_load(fh)
    except OSError as exc:
        sys.exit(f"cannot read {args.spec}: {exc}")

    failed = False

    untyped = find_untyped(spec)
    total = sum(1 for _ in operations(spec))
    if untyped:
        failed = True
        print(f"FAIL  {len(untyped)} untyped response(s) across {total} operations:\n")
        for method, path, code, oid in sorted(untyped, key=lambda r: (r[1], r[0])):
            print(f"  {method:6} {path}  [{code}]  {oid}")
        print("\n  These generate `unknown` in TypeScript and `interface{}` in Go.")
        print("  Declare a concrete ResponseEntity<T> on the controller method.")
    else:
        print(f"OK    all {total} operations declare typed responses")

    missing, dupes, vague, collisions = find_bad_operation_ids(spec)
    if missing:
        failed = True
        print(f"\nFAIL  {len(missing)} operation(s) without an operationId:")
        for method, path in missing:
            print(f"  {method:6} {path}")
    for label, rows in (("auto-deduplicated", dupes), ("vague", vague)):
        if rows:
            if args.strict_ids:
                failed = True
            print(f"\n{'FAIL' if args.strict_ids else 'WARN'}  {len(rows)} {label} operationId(s):")
            for method, path, oid in rows:
                print(f"  {method:6} {path}  ->  {oid}")
    if collisions:
        failed = True
        print(f"\nFAIL  {len(collisions)} duplicated operationId(s):")
        for oid, locs in collisions:
            print(f"  {oid}: {', '.join(f'{m} {p}' for m, p in locs)}")

    if args.baseline:
        try:
            with open(args.baseline, encoding="utf-8") as fh:
                base = yaml.safe_load(fh)
        except OSError as exc:
            sys.exit(f"cannot read baseline {args.baseline}: {exc}")
        removed = compare_baseline(spec, base)
        if removed:
            failed = True
            print(f"\nFAIL  {len(removed)} operation(s) in baseline are gone (renamed or deleted):")
            for method, path in removed:
                print(f"  {method:6} {path}")
        else:
            print("OK    no operations removed relative to baseline")

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
