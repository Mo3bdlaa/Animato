#!/usr/bin/env python3
"""Prints every class *defined* in an APK's dex files, one fully-qualified name per line.

Written rather than shelled out to because the alternatives all cost more than they are worth here:
`dexdump` means an Android SDK build-tools install on the runner, and `apkanalyzer` means the whole
SDK. Listing defined classes needs three tables out of the dex header, which is little enough code
to keep.

Deliberately reads `class_defs` and not `type_ids`: `type_ids` also holds every type the dex merely
*refers to*, so a class R8 deleted would still be listed there and the check that matters would pass
when it should not.

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


def classes_in_dex(data):
    string_ids_off = u32(data, STRING_IDS_OFF)
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

    for i in range(class_defs_size):
        type_idx = u32(data, class_defs_off + i * CLASS_DEF_ITEM_SIZE)
        descriptor = string_at(u32(data, type_ids_off + type_idx * 4))
        # "Lcom/example/Foo;" is the on-disk spelling of com.example.Foo
        if descriptor.startswith("L") and descriptor.endswith(";"):
            yield descriptor[1:-1].replace("/", ".")


def main():
    if len(sys.argv) != 2:
        print("usage: list-dex-classes.py <apk>", file=sys.stderr)
        return 2

    names = set()
    with zipfile.ZipFile(sys.argv[1]) as apk:
        dex_names = [n for n in apk.namelist() if n.startswith("classes") and n.endswith(".dex")]
        if not dex_names:
            print(f"no dex files in {sys.argv[1]}", file=sys.stderr)
            return 1
        for name in dex_names:
            names.update(classes_in_dex(apk.read(name)))

    for name in sorted(names):
        print(name)
    return 0


if __name__ == "__main__":
    sys.exit(main())
