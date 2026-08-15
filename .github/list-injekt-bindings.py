#!/usr/bin/env python3
"""
Reports Injekt dependencies that nothing registers.

Injekt resolves by type at run time, so a missing binding is invisible to the compiler and to every
unit test that does not construct the graph. It surfaces as an exception the moment the screen that
needs it opens — and eleven of them were found at once, after the anime side had been written,
compiled and merged without ever running on a device.

Two kinds of gap are reported:

**Requested but never registered.** `Injekt.get<T>()`, `by injectLazy()`, and the `= Injekt.get()`
default argument that Mihon's screen models are built from — which is the dangerous one, since a
missing binding there throws while the screen is being constructed.

**Registered with an argument that is not.** `addFactory { Foo(get(), get()) }` says nothing about
what those `get()`s resolve to; the constructor does. So each registration is matched against its
class's constructor parameters, and every parameter type has to be registered in its own right.

Scope: requests are read from the modules this fork owns. Registrations are read from everywhere,
Mihon's modules included, because ours are meant to resolve against theirs.

Prints one line per gap and exits 1 when there are any.
"""

import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

# Bound as instances, or supplied by Injekt itself, rather than through an `add*` call.
BOUND_ELSEWHERE = {
    "Application",
    "Context",
    "Json",
    "OkHttpClient",
    "XML",
}

# Constructor parameters that are values rather than dependencies: a registration passes these
# explicitly, so they are never resolved from the graph.
NOT_DEPENDENCIES = {
    "Boolean",
    "Int",
    "Long",
    "String",
    "Float",
    "Double",
    "CoroutineScope",
    "CoroutineDispatcher",
}

OWNED_PREFIXES = ("anime/", "animato-app/", "animato-ui-kit/")

REGISTER_GENERIC = re.compile(r"add(?:SingletonFactory|Factory|Singleton)\s*<\s*([\w.]+)")
REGISTER_LAMBDA = re.compile(r"add(?:SingletonFactory|Factory)\s*\{\s*([A-Z][\w.]*)\s*\(")
REGISTER_INSTANCE = re.compile(r"addSingleton\(\s*([A-Za-z][\w.]*)")

REQUESTS = (
    re.compile(r"(?:Injekt\.get|Injekt\.getInstance|injectLazy)\s*<\s*([\w.]+)"),
    re.compile(r":\s*([A-Z][\w.]*)\s*(?:<[^=\n]*>)?\s*=\s*Injekt\.get\(\)"),
    re.compile(r":\s*([A-Z][\w.]*)\s*(?:<[^\n]*>)?\s*by\s+injectLazy\(\)"),
    re.compile(r":\s*([A-Z][\w.]*)\s*(?:<[^\n]*>)?\s*by\s+lazy\s*\{\s*Injekt\.get\(\)"),
)

CLASS_HEAD = re.compile(r"^(?:internal\s+|private\s+|abstract\s+|open\s+)*class\s+(\w+)\s*(?:<[^>]*>)?\s*\(", re.M)


def simple(name):
    return name.split(".")[-1]


def sources():
    listed = subprocess.run(
        ["git", "ls-files", "*.kt"],
        capture_output=True,
        text=True,
        check=True,
    ).stdout.split()
    # `git ls-files` reads the index, which still names a file deleted in the working tree but not
    # yet staged — a normal state mid-change, and not a reason for the check to crash.
    return [
        f for f in listed
        if "/build/" not in f and "/src/test/" not in f and Path(f).is_file()
    ]


def strip_comments(text):
    """
    Kotlin without its comments.

    A comment reads exactly like code to a regex — a constructor whose parameters are explained in
    prose above them yields a "parameter" named after whatever word follows the colon. Block
    comments go unconditionally; a line comment goes only when the `//` is not inside a string, so
    that a URL keeps the rest of its line.
    """
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    lines = []
    for line in text.split("\n"):
        cut = 0
        while True:
            found = line.find("//", cut)
            if found == -1:
                break
            if line.count('"', 0, found) % 2 == 0:
                line = line[:found]
                break
            cut = found + 2
        lines.append(line)
    return "\n".join(lines)


def read(path):
    return strip_comments(Path(path).read_text(encoding="utf-8", errors="ignore"))


def balanced(text, open_index):
    """The text between the bracket at open_index and its match."""
    depth = 0
    for i in range(open_index, len(text)):
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return text[open_index + 1:i]
    return ""


def constructor_parameters(files):
    """Every class's constructor parameter types, by simple class name."""
    parameters = {}
    for path in files:
        text = read(path)
        for match in CLASS_HEAD.finditer(text):
            body = balanced(text, match.end() - 1)
            types = []
            for parameter in re.finditer(r":\s*([\w.]+)", body):
                types.append(simple(parameter.group(1)))
            parameters.setdefault(match.group(1), types)
    return parameters


def main():
    files = sources()

    registered = set(BOUND_ELSEWHERE)
    registrations = []  # (class name, file) for the constructor check
    for path in files:
        text = read(path)
        if "add" not in text:
            continue
        for match in REGISTER_GENERIC.finditer(text):
            registered.add(simple(match.group(1)))
        for match in REGISTER_LAMBDA.finditer(text):
            registered.add(simple(match.group(1)))
            registrations.append((simple(match.group(1)), path))
        for match in REGISTER_INSTANCE.finditer(text):
            registered.add(simple(match.group(1)))

    requested = defaultdict(set)
    for path in files:
        if not path.startswith(OWNED_PREFIXES):
            continue
        text = read(path)
        for pattern in REQUESTS:
            for match in pattern.finditer(text):
                requested[simple(match.group(1))].add(path)

    parameters = constructor_parameters(files)

    gaps = []
    for name in sorted(requested):
        if name not in registered:
            where = sorted(requested[name])[0]
            gaps.append(f"{name} is asked for in {where} and never registered")

    for name, path in sorted(set(registrations)):
        if not path.startswith(OWNED_PREFIXES) and "animato" not in path:
            continue
        for parameter in parameters.get(name, []):
            if parameter in NOT_DEPENDENCIES or parameter in registered:
                continue
            gaps.append(f"{name} is registered in {path} but takes a {parameter}, which is not")

    if gaps:
        print(f"FAIL: {len(gaps)} Injekt binding(s) would fail at run time:")
        for gap in gaps:
            print(f"  {gap}")
        return 1

    print(f"OK: every one of {len(requested)} requested types resolves.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
