#!/usr/bin/env python3
"""Prints the classes in an APK's dex files, one fully-qualified name per line.

By default: every class **defined**. With `--referenced`: every class the dex **mentions**, whether
or not it is there.

Written rather than shelled out to because the alternatives all cost more than they are worth here:
`dexdump` means an Android SDK build-tools install on the runner, and `apkanalyzer` means the whole
SDK. Both tables come out of the dex header, which is little enough code to keep.

The two modes answer opposite questions and must not be confused. `class_defs` is what survived R8,
so it is what a keep-rule check asks about — reading `type_ids` there would list a class R8 deleted
and pass when it should not. `type_ids` is every type the code refers to, so the *difference* between
them is the set of classes this APK will look for and not find. See check-dex-keeps.sh.

Format reference: https://source.android.com/docs/core/runtime/dex-format
"""

import struct
import sys
import zipfile

# Byte offsets into the dex header of the tables this needs.
STRING_IDS_SIZE = 0x38
STRING_IDS_OFF = 0x3C
TYPE_IDS_SIZE = 0x40
TYPE_IDS_OFF = 0x44
CLASS_DEFS_SIZE = 0x60
CLASS_DEFS_OFF = 0x64

CLASS_DEF_ITEM_SIZE = 32


def read_uleb128(data, offset):
    """Returns (value, next_offset). Lengths in a dex are variable-width."""
    result = 0
    shift = 0
    while True:
        byte = data[offset]
        offset += 1
        result |= (byte & 0x7F) << shift
        if byte & 0x80 == 0:
            return result, offset
        shift += 7


def u32(data, offset):
    return struct.unpack_from("<I", data, offset)[0]


def classes_in_dex(data, referenced):
    string_ids_off = u32(data, STRING_IDS_OFF)
    type_ids_size = u32(data, TYPE_IDS_SIZE)
    type_ids_off = u32(data, TYPE_IDS_OFF)
    class_defs_size = u32(data, CLASS_DEFS_SIZE)
    class_defs_off = u32(data, CLASS_DEFS_OFF)

    def string_at(index):
        data_off = u32(data, string_ids_off + index * 4)
        # The stored length counts UTF-16 units, which is not the byte length, so the string is
        # read to its terminator instead.
        _, start = read_uleb128(data, data_off)
        end = data.index(b"\x00", start)
        return data[start:end].decode("utf-8", errors="replace")

    def descriptor_of(type_idx):
        return string_at(u32(data, type_ids_off + type_idx * 4))

    if referenced:
        descriptors = (descriptor_of(i) for i in range(type_ids_size))
    else:
        descriptors = (
            descriptor_of(u32(data, class_defs_off + i * CLASS_DEF_ITEM_SIZE))
            for i in range(class_defs_size)
        )

    for descriptor in descriptors:
        # "Lcom/example/Foo;" is the on-disk spelling of com.example.Foo. Primitives ("I") and
        # arrays ("[Lcom/example/Foo;") are skipped — an array's element type has its own entry.
        if descriptor.startswith("L") and descriptor.endswith(";"):
            yield descriptor[1:-1].replace("/", ".")


def main():
    args = sys.argv[1:]
    referenced = "--referenced" in args
    if referenced:
        args.remove("--referenced")
    if len(args) != 1:
        print("usage: list-dex-classes.py [--referenced] <apk>", file=sys.stderr)
        return 2

    names = set()
    with zipfile.ZipFile(args[0]) as apk:
        dex_names = [n for n in apk.namelist() if n.startswith("classes") and n.endswith(".dex")]
        if not dex_names:
            print(f"no dex files in {args[0]}", file=sys.stderr)
            return 1
        for name in dex_names:
            names.update(classes_in_dex(apk.read(name), referenced))

    for name in sorted(names):
        print(name)
    return 0


if __name__ == "__main__":
    sys.exit(main())
