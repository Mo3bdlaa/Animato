#!/usr/bin/env python3
"""
Writes animato-app/src/main/java/animato/di/InjektAccessors.kt: one Metro graph accessor for every
type this fork's code asks Injekt for.

## Why

Animato's dependencies live in a Metro graph now (animato.di.AnimatoGraph), which the compiler
checks: a binding nobody provides is a build error, not a crash on a device. But hundreds of call
sites still read `Injekt.get()` — screen models' default arguments, `by injectLazy()` fields — and
Injekt asks by type at run time, which a compile-time graph cannot answer by itself.

So the graph exposes an accessor for each of those types, and Injekt is answered from a map of
them. The accessors are what makes it safe: Metro has to be able to build every one, so a type
somebody asks Injekt for and nothing provides fails the build — which is what
check-injekt-bindings.sh used to approximate with regexes and a list of exceptions.

## Running it

`python3 .github/generate-injekt-accessors.py` rewrites the file. `--check` exits 1 if it is out of
date instead, which is what CI runs: a new `Injekt.get<Foo>()` without regenerating would otherwise
compile against a map that does not have Foo.
"""

import re
import subprocess
import sys
from pathlib import Path

OUT = Path("animato-app/src/main/java/animato/di/InjektAccessors.kt")
OWNED_PREFIXES = ("anime/", "animato-app/", "animato-ui-kit/")

REQUESTS = (
    re.compile(r"(?:Injekt\.get|Injekt\.getInstance|injectLazy|Injekt\.getOrNull)\s*<\s*([\w.]+)\s*>"),
    re.compile(r":\s*([A-Z][\w.]*)\s*=\s*Injekt\.get\(\)"),
    re.compile(r":\s*([A-Z][\w.]*)\s*by\s+injectLazy\(\)"),
    re.compile(r":\s*([A-Z][\w.]*)\s*by\s+lazy\s*\{\s*Injekt\.get\(\)"),
)

UNTYPED_CALL = re.compile(r"(?:Injekt\.get|injectLazy)\(\)")
# What makes an untyped call typed after all: a declared type just before it.
TYPED_CONTEXT = re.compile(r":\s*[A-Z][\w.]*\s*(?:<[^=\n]*>)?\??\s*(?:=|by)\s*(?:lazy\s*\{\s*)?$")

# Asked for by extensions, not by anything here, so no call site names them. These are the types
# Aniyomi's extension API documented as available; MetroInjektRegistrar answers the same ones.
EXTENSION_TYPES = [
    "android.app.Application",
    "android.content.Context",
    "eu.kanade.tachiyomi.network.NetworkHelper",
    "eu.kanade.tachiyomi.network.JavaScriptEngine",
    "kotlinx.serialization.json.Json",
    "kotlinx.serialization.protobuf.ProtoBuf",
    "nl.adaptivity.xmlutil.serialization.XML",
]

# The accessors this graph must not have: answered by MetroInjektRegistrar before this map is
# consulted, and either impossible or pointless to bind twice.
NOT_ACCESSORS = set()


def sources():
    listed = subprocess.run(["git", "ls-files", "*.kt"], capture_output=True, text=True, check=True).stdout.split()
    return [f for f in listed if "/build/" not in f and "/src/test/" not in f and Path(f).is_file()]


def strip_comments(text):
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return "\n".join(re.sub(r"(^|[^:\"])//.*$", r"\1", line) for line in text.split("\n"))


def ktlint_import_order(line):
    """The order ktlint's spotless step sorts imports into: everything, then java, javax, kotlin, aliases."""
    name = line.removeprefix("import ")
    if " as " in name:
        group = 4
    elif name.startswith("java."):
        group = 1
    elif name.startswith("javax."):
        group = 2
    elif name.startswith("kotlin."):
        group = 3
    else:
        group = 0
    return group, name


def main():
    files = sources()
    declared = {}  # package -> set of top-level names declared in it
    for path in files:
        text = Path(path).read_text(encoding="utf-8", errors="ignore")
        pkg = re.search(r"^package\s+([\w.]+)", text, re.M)
        if not pkg:
            continue
        names = declared.setdefault(pkg.group(1), set())
        for match in re.finditer(r"^(?:[\w@ ]*\s)?(?:class|interface|object)\s+(\w+)", text, re.M):
            names.add(match.group(1))

    wanted = set(EXTENSION_TYPES)
    unresolved = []
    untyped = []
    for path in files:
        if not path.startswith(OWNED_PREFIXES) or path == str(OUT):
            continue
        text = strip_comments(Path(path).read_text(encoding="utf-8", errors="ignore"))
        if "Injekt" not in text and "injectLazy" not in text:
            continue
        pkg = re.search(r"^package\s+([\w.]+)", text, re.M).group(1)
        imports = {m.group(2) or m.group(1).split(".")[-1]: m.group(1)
                   for m in re.finditer(r"^import\s+([\w.]+)(?:\s+as\s+(\w+))?", text, re.M)}
        # An untyped request — `Foo(Injekt.get())`, the type inferred from a parameter — names no
        # type this script can read, so the accessor it needs would silently be missing. It
        # happened: the local source was built from five of them and failed on a device.
        for match in UNTYPED_CALL.finditer(text):
            before = text[max(0, match.start() - 200):match.start()]
            if not TYPED_CONTEXT.search(before):
                line = text.count("\n", 0, match.start()) + 1
                untyped.append(f"{path}:{line}")
        for pattern in REQUESTS:
            for match in pattern.finditer(text):
                name = match.group(1)
                if "." in name and name[0].islower():
                    wanted.add(name)
                elif name in imports:
                    wanted.add(imports[name])
                elif name in declared.get(pkg, ()):
                    wanted.add(f"{pkg}.{name}")
                else:
                    unresolved.append(f"{name} in {path}")

    if untyped:
        print("These Injekt requests do not name their type, so no accessor can be generated for them.")
        print("Write Injekt.get<Foo>() — or better, take Foo as a constructor parameter:")
        for entry in sorted(set(untyped)):
            print(f"  {entry}")
        return 1

    if unresolved:
        print("Cannot tell which class these Injekt requests mean:")
        for entry in sorted(set(unresolved)):
            print(f"  {entry}")
        return 1

    wanted -= NOT_ACCESSORS
    ordered = sorted(wanted, key=lambda fqn: (fqn.split(".")[-1], fqn))
    # Simple names where they are unique, an import alias where two classes share one.
    simple_names = {}
    for fqn in ordered:
        simple_names.setdefault(fqn.split(".")[-1], []).append(fqn)
    local = {}
    for name, fqns in simple_names.items():
        for fqn in fqns:
            if len(fqns) == 1:
                local[fqn] = name
            else:
                prefix = "".join(part.capitalize() for part in fqn.split(".")[:-1][-2:])
                local[fqn] = prefix + name

    imports = sorted(
        f"import {fqn}" + (f" as {local[fqn]}" if local[fqn] != fqn.split(".")[-1] else "")
        for fqn in ordered
    )
    imports = sorted(set(imports + ["import java.lang.reflect.Type"]), key=ktlint_import_order)
    accessor = {fqn: local[fqn][0].lower() + local[fqn][1:] for fqn in ordered}

    lines = [
        "// Generated by .github/generate-injekt-accessors.py — do not edit by hand. Run it again after",
        "// adding an Injekt request; CI fails while this file is out of date.",
        "package animato.di",
        "",
        *imports,
        "",
        "/**",
        " * One accessor per type this fork asks Injekt for, so that Metro has to be able to build each of",
        " * them — a request nothing provides is a compile error in [AnimatoGraph]. See the generator.",
        " */",
        "interface InjektAccessors {",
    ]
    for fqn in ordered:
        lines.append(f"    val {accessor[fqn]}: {local[fqn]}")
    lines += [
        "}",
        "",
        "/** The same accessors by type, for [AnimatoInjektRegistrar] to answer Injekt from. */",
        "internal fun InjektAccessors.injektBindings(): Map<Type, () -> Any> = mapOf(",
    ]
    for fqn in ordered:
        lines.append(f"    {local[fqn]}::class.java to {{ {accessor[fqn]} }},")
    lines += [")", ""]
    content = "\n".join(lines)

    if "--check" in sys.argv:
        if not OUT.exists() or OUT.read_text() != content:
            print(f"{OUT} is out of date. Run: python3 .github/generate-injekt-accessors.py")
            return 1
        print(f"OK: {OUT} covers all {len(ordered)} requested types.")
        return 0

    OUT.write_text(content)
    print(f"Wrote {len(ordered)} accessors to {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
