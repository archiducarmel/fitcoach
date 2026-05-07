"""V2.6 audit — format specifier parity + key coverage between FR canonical and V2 translations.

Run: python3 tools/i18n_audit.py
Compares values/strings.xml against values-{es,it,pt,de}/strings.xml.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path("app/src/main/res")
SPEC_RE = re.compile(r"%(?:[0-9]+\$)?(?:\.[0-9]+)?[sdfn%]")


def extract_specifiers(text):
    if text is None:
        return []
    return sorted(SPEC_RE.findall(text))


def parse_strings(path):
    tree = ET.parse(path)
    root = tree.getroot()
    result = {}
    for s in root.findall("string"):
        name = s.get("name")
        text = s.text or ""
        result[name] = (extract_specifiers(text), text)
    return result


def parse_plurals(path):
    tree = ET.parse(path)
    root = tree.getroot()
    result = {}
    for p in root.findall("plurals"):
        name = p.get("name")
        items = {item.get("quantity"): item.text or "" for item in p.findall("item")}
        result[name] = items
    return result


def main():
    fr_path = ROOT / "values" / "strings.xml"
    fr = parse_strings(fr_path)
    fr_plurals = parse_plurals(fr_path)
    print(f"FR canonical: {len(fr)} <string> keys, {len(fr_plurals)} <plurals> blocks")

    issues_total = 0
    for lang in ["es", "it", "pt", "de"]:
        path = ROOT / f"values-{lang}" / "strings.xml"
        if not path.exists():
            print(f"\n[{lang.upper()}] FILE MISSING: {path}")
            issues_total += 1
            continue
        other = parse_strings(path)
        other_plurals = parse_plurals(path)

        missing = [k for k in fr if k not in other]
        extra = [k for k in other if k not in fr]
        mismatches = []
        empty_translations = []
        same_as_fr = []  # likely untranslated keys

        for k, (fr_specs, fr_text) in fr.items():
            if k not in other:
                continue
            other_specs, other_text = other[k]
            if fr_specs != other_specs:
                mismatches.append((k, fr_specs, other_specs))
            if not other_text.strip() and fr_text.strip():
                empty_translations.append(k)
            # heuristic: same text and length > 5 chars and contains a letter
            if (
                other_text == fr_text
                and len(fr_text) > 5
                and re.search(r"[A-Za-zÀ-ÿ]", fr_text)
                # exclude very short or numeric/format-only strings
            ):
                same_as_fr.append(k)

        plural_missing = [k for k in fr_plurals if k not in other_plurals]
        plural_qty_mismatch = []
        for k, items in fr_plurals.items():
            if k in other_plurals:
                if set(items.keys()) != set(other_plurals[k].keys()):
                    plural_qty_mismatch.append(
                        (k, list(items.keys()), list(other_plurals[k].keys()))
                    )

        print(
            f"\n[{lang.upper()}] {len(other)} keys, {len(other_plurals)} plurals"
        )
        print(f"  missing keys: {len(missing)}")
        if missing[:5]:
            print(f"    sample: {missing[:5]}")
        print(f"  extra keys: {len(extra)}")
        if extra[:5]:
            print(f"    sample: {extra[:5]}")
        print(f"  format spec mismatches: {len(mismatches)}")
        for k, frs, ots in mismatches[:10]:
            print(f"    {k}: FR={frs} vs {lang.upper()}={ots}")
        print(f"  empty translations: {len(empty_translations)}")
        if empty_translations[:5]:
            print(f"    sample: {empty_translations[:5]}")
        print(f"  unchanged-from-FR (suspect untranslated): {len(same_as_fr)}")
        if same_as_fr[:10]:
            print(f"    sample: {same_as_fr[:10]}")
        print(f"  missing plurals: {len(plural_missing)}")
        print(f"  plural quantity mismatches: {len(plural_qty_mismatch)}")
        for k, fk, ok in plural_qty_mismatch[:5]:
            print(f"    {k}: FR={fk} vs {lang.upper()}={ok}")

        issues_total += (
            len(missing)
            + len(mismatches)
            + len(empty_translations)
            + len(plural_missing)
            + len(plural_qty_mismatch)
        )

    print(f"\nTotal hard issues across V2 langs: {issues_total}")
    return 0 if issues_total == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
