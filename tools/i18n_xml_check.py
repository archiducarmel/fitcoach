"""Quick XML well-formedness + element count for all locale strings.xml."""
import xml.etree.ElementTree as ET
from pathlib import Path

ok = 0
fail = 0
for d in ["values", "values-en", "values-es", "values-it", "values-pt", "values-de"]:
    p = Path("app/src/main/res") / d / "strings.xml"
    try:
        tree = ET.parse(p)
        root = tree.getroot()
        n_str = len(root.findall("string"))
        n_plr = len(root.findall("plurals"))
        n_arr = len(root.findall("string-array"))
        print(f"  [OK] {d}: {n_str} strings, {n_plr} plurals, {n_arr} arrays")
        ok += 1
    except Exception as e:
        print(f"  [FAIL] {d}: {e}")
        fail += 1
print(f"XML well-formedness: {ok} OK, {fail} FAIL")
