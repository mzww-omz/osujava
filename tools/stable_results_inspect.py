#!/usr/bin/env python3
"""Read-only CLR metadata/IL inspection for the documented b20230727.9 build.

Requires dnfile==0.18.0 and dncil==1.0.2. Does not execute assemblies,
decrypt strings, or export embedded resources. Redirect output outside Git.
Offsets include the method header, matching dncil's Instruction.offset.
"""

import argparse
import hashlib
from importlib.metadata import version
from pathlib import Path

import dnfile
from dncil.cil.body.reader import read_method_body_from_bytes


EXPECTED_SHA256 = "bfa4ad675cdcd773b7b1c899e0a5e193d05d055d93e001271f06756c8185a28a"


def token(text):
    return int(text, 16)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("assembly", type=Path)
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--types", nargs="+", type=token, metavar="TOKEN")
    group.add_argument("--methods", nargs="+", type=token, metavar="TOKEN")
    group.add_argument("--refs", nargs="+", type=token, metavar="TOKEN")
    group.add_argument("--ranking-locations", action="store_true")
    args = parser.parse_args()
    digest = hashlib.sha256(args.assembly.read_bytes()).hexdigest()
    if digest != EXPECTED_SHA256:
        parser.error("assembly hash differs from the researched build; tokens cannot be reused")
    print(f"sha256={digest} dnfile={version('dnfile')} dncil={version('dncil')}")
    pe = dnfile.dnPE(str(args.assembly))
    tables = pe.net.mdtables
    owners = {m.row_index: i for i, t in enumerate(tables.TypeDef.rows, 1) for m in t.MethodList}

    def row(table, value, expected):
        if value >> 24 != expected or not 1 <= (value & 0xffffff) <= len(table.rows):
            parser.error(f"invalid token for this table: {value:08x}")
        return table.rows[(value & 0xffffff) - 1]

    def body(method):
        if not method.Rva:
            return None
        return read_method_body_from_bytes(pe.get_data(method.Rva, 250000))

    def operand(value):
        number = getattr(value, "value", None)
        if number is None:
            return str(value)
        table = {2: tables.TypeDef, 6: tables.MethodDef, 4: tables.Field,
                 10: tables.MemberRef, 1: tables.TypeRef}.get(number >> 24)
        if table:
            target = row(table, number, number >> 24)
            name = str(getattr(target, "Name", getattr(target, "TypeName", "")))
            if hasattr(target, "Class"):
                name = str(getattr(target.Class.row, "TypeName", "")) + "::" + name
            return f"{number:08x} {name}"
        # Deliberately do not resolve user strings or resource tokens.
        return f"{number:08x}"

    if args.methods:
        for value in args.methods:
            method = row(tables.MethodDef, value, 6)
            print(f"METHOD {value:08x} TYPE {0x2000000 + owners[value & 0xffffff]:08x}")
            parsed = body(method)
            if parsed:
                for instruction in parsed.instructions:
                    print(f"{instruction.offset:04x} {instruction.opcode} {operand(instruction.operand)}")
        return

    if args.refs or args.ranking_locations:
        targets = set(args.refs or [])
        failures = []
        for i, method in enumerate(tables.MethodDef.rows, 1):
            try:
                parsed = body(method)
            except Exception as error:
                failures.append((f"{0x6000000 + i:08x}", type(error).__name__))
                continue
            if parsed is None:
                continue
            if args.refs:
                for instruction in parsed.instructions:
                    if getattr(instruction.operand, "value", None) in targets:
                        print(f"{0x6000000 + i:08x} {instruction.offset:04x} "
                              f"{instruction.opcode} {operand(instruction.operand)}")
            else:
                ids = [a.operand for a, b in zip(parsed.instructions, parsed.instructions[1:])
                       if str(a.opcode) == "ldc.i4" and 1225 <= a.operand <= 1244
                       and str(b.opcode) == "call" and getattr(b.operand, "value", None) == 0x0600392d]
                if ids:
                    print(f"{0x6000000 + i:08x} TYPE {0x2000000 + owners[i]:08x} localisation={ids}")
        print(f"unparsed_methods={failures}")
        return

    for value in args.types or [0x02000385, 0x02000449, 0x020003c6, 0x020002bf, 0x02000282]:
        target = row(tables.TypeDef, value, 2)
        print(f"TYPE {value:08x}")
        for field in target.FieldList:
            print(f"FIELD {0x4000000 + field.row_index:08x} signature={field.row.Signature.value.hex()}")
        for method in target.MethodList:
            print(f"METHOD {0x6000000 + method.row_index:08x} "
                  f"signature={method.row.Signature.value.hex()} virtual={method.row.Flags.mdVirtual}")


if __name__ == "__main__":
    main()
