#!/usr/bin/env python3
"""Packs the open-source icon sets into app/src/main/assets/icons/*.tsv (name TAB one-line svg).

usage: build_icons.py LUCIDE_DIR PHOSPHOR_CORE_DIR HEROICONS_DIR
Sources: github.com/lucide-icons/lucide (ISC), github.com/phosphor-icons/core (MIT), github.com/tailwindlabs/heroicons (MIT).
"""
import os, re, sys

lucide, phosphor, hero = sys.argv[1:4]
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "icons")
os.makedirs(out, exist_ok=True)

def pack(name, folder, strip_suffix=""):
    rows = []
    for f in sorted(os.listdir(folder)):
        if not f.endswith(".svg"):
            continue
        n = f[:-4]
        if strip_suffix and n.endswith(strip_suffix):
            n = n[: -len(strip_suffix)]
        svg = re.sub(r"\s+", " ", open(os.path.join(folder, f), encoding="utf-8").read()).strip()
        svg = svg.replace("> <", "><").replace('" >', '">')
        svg = svg.replace("#0F172A", "currentColor")
        rows.append(f"{n}\t{svg}")
    with open(os.path.join(out, name + ".tsv"), "w", encoding="utf-8", newline="\n") as o:
        o.write("\n".join(rows) + "\n")
    print(name, len(rows))

pack("lucide", os.path.join(lucide, "icons"))
pack("heroicons-outline", os.path.join(hero, "src", "24", "outline"))
pack("heroicons-solid", os.path.join(hero, "src", "24", "solid"))
pack("phosphor-regular", os.path.join(phosphor, "assets", "regular"))
pack("phosphor-bold", os.path.join(phosphor, "assets", "bold"), "-bold")
pack("phosphor-fill", os.path.join(phosphor, "assets", "fill"), "-fill")
