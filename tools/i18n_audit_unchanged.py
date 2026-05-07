"""V2.6 audit — sample unchanged-from-FR strings to surface real untranslated UI text."""
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path("app/src/main/res")


def parse_strings(path):
    tree = ET.parse(path)
    root = tree.getroot()
    return {s.get("name"): (s.text or "") for s in root.findall("string")}


# Strings that are LEGITIMATELY identical between FR and translation:
# - brand names, app technical identifiers
# - acronyms (LLM, AI, API, kcal, g, ml, etc.)
# - format-only strings ("%1$s", "%1$d g", etc.)
# - English aesthetic strings (cyberpunk HUD)
LEGIT_PATTERNS = [
    re.compile(r"^[A-Z]{2,}$"),  # all-caps acronym
    re.compile(r"^\d+\s*(g|ml|kcal|kJ|kg|lb|cm|m|mm|s|h|j|min|d)?$"),  # number+unit
    re.compile(r"^[%@\$\d\s\.,\-:]+$"),  # format/punctuation only
    re.compile(r"^(ShredCoach|Shreddy|Marcus|Léa|Hugo|Sophie|Charon|Aoede|Puck|Kore|GymScan|MealScanner|BodyScanner|Free Exercise DB|ExerciseDB|Gemini|Groq|Mistral|OpenAI|Claude|Anthropic|Llama)$"),
    re.compile(r"^(BIOMETRIC|SYSTEM|LOADING|ONLINE|READOUT|HUD|DB|API|AI|LLM|TTS|CDN|PDF|CSV|JSON|TXT|JPG|PNG|MP3|MP4|GIF|URL)\b"),
    re.compile(r"^(OK|GO|YES|NO)$", re.I),
]


def is_legitimate_unchanged(text):
    t = text.strip()
    if not t or len(t) < 3:
        return True  # too short to flag
    if not re.search(r"[A-Za-zÀ-ÿ]", t):
        return True
    for pat in LEGIT_PATTERNS:
        if pat.match(t):
            return True
    return False


def main():
    fr_path = ROOT / "values" / "strings.xml"
    fr = parse_strings(fr_path)
    print(f"FR canonical: {len(fr)} keys\n")

    for lang in ["es", "it", "pt", "de"]:
        path = ROOT / f"values-{lang}" / "strings.xml"
        if not path.exists():
            continue
        other = parse_strings(path)
        suspect = []
        for k, fr_text in fr.items():
            if k not in other:
                continue
            other_text = other[k]
            if other_text == fr_text and len(fr_text) > 5 and re.search(r"[A-Za-zÀ-ÿ]", fr_text):
                if not is_legitimate_unchanged(fr_text):
                    suspect.append((k, fr_text))

        print(f"[{lang.upper()}] {len(suspect)} suspect untranslated UI strings:")
        for k, t in suspect[:30]:
            preview = t[:80].replace("\n", "\\n")
            print(f"  {k}: \"{preview}\"")
        if len(suspect) > 30:
            print(f"  ... +{len(suspect) - 30} more")
        print()


if __name__ == "__main__":
    main()
