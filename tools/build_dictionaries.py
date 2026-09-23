#!/usr/bin/env python3
"""
Builds the two offline dictionaries the "Мои слова" editor uses:

  app/src/main/assets/dict/en_ipa.txt   word <TAB> IPA transcription
  app/src/main/assets/dict/en_ru.txt    word <TAB> перевод|перевод|перевод

Sources (downloaded when not already present in --src):
  * CMU Pronouncing Dictionary (BSD-style licence) — ARPAbet, converted to IPA.
  * OpenRussian dictionary data (CC BY-SA 4.0) — Russian→English, inverted here.
    Rows are ordered by frequency, which is what ranks the translations.

Run from the repository root:  python3 tools/build_dictionaries.py
"""
import argparse, csv, os, re, sys, urllib.request
from collections import defaultdict

CMU_URL = "https://raw.githubusercontent.com/cmusphinx/cmudict/master/cmudict.dict"
OR_BASE = "https://raw.githubusercontent.com/Badestrand/russian-dictionary/master/"
OR_FILES = ["nouns.csv", "verbs.csv", "adjectives.csv", "others.csv"]

# Children mostly add nouns, so at equal frequency a noun wins; and a rare
# verb ("замусолить" for "thumb") is a worse suggestion than none at all, so
# verbs, adjectives and the rest must also be reasonably common to count.
FILE_PENALTY = {"nouns.csv": 0.0, "others.csv": 0.05, "verbs.csv": 0.08, "adjectives.csv": 0.10}
MAX_PERCENTILE = {"nouns.csv": 1.0, "others.csv": 0.6, "verbs.csv": 0.4, "adjectives.csv": 0.6}
SENSE_PENALTY = 0.08

# This is a children's app: these are never offered as a translation, even
# when the dictionary lists them ("dog" -> "кобель").
NEVER_SUGGEST = {
    "кобель", "сука", "сучка", "шлюха", "задница", "жопа", "сиська", "титька",
    "трах", "блядь", "бля", "дерьмо", "говно", "херня", "хрен", "мудак",
    "срать", "ссать", "пердеть", "сраный", "хер", "потаскуха",
}

# ---------------------------------------------------------------- ARPAbet → IPA

VOWELS = {
    "AA": "ɑː", "AE": "æ", "AO": "ɔː", "AW": "aʊ", "AY": "aɪ", "EH": "e",
    "EY": "eɪ", "IH": "ɪ", "IY": "iː", "OW": "oʊ", "OY": "ɔɪ", "UH": "ʊ", "UW": "uː",
}
CONSONANTS = {
    "B": "b", "CH": "tʃ", "D": "d", "DH": "ð", "F": "f", "G": "ɡ", "HH": "h",
    "JH": "dʒ", "K": "k", "L": "l", "M": "m", "N": "n", "NG": "ŋ", "P": "p",
    "R": "r", "S": "s", "SH": "ʃ", "T": "t", "TH": "θ", "V": "v", "W": "w",
    "Y": "j", "Z": "z", "ZH": "ʒ",
}
# Clusters English allows at the start of a syllable; the stress mark goes
# before the longest of these that ends right before the stressed vowel.
ONSETS = {
    ("S", "T", "R"), ("S", "P", "R"), ("S", "P", "L"), ("S", "K", "R"), ("S", "K", "W"),
    ("P", "R"), ("P", "L"), ("B", "R"), ("B", "L"), ("T", "R"), ("T", "W"), ("D", "R"),
    ("D", "W"), ("K", "R"), ("K", "L"), ("K", "W"), ("G", "R"), ("G", "L"), ("G", "W"),
    ("F", "R"), ("F", "L"), ("TH", "R"), ("SH", "R"), ("S", "T"), ("S", "P"), ("S", "K"),
    ("S", "M"), ("S", "N"), ("S", "L"), ("S", "W"), ("P", "Y"), ("B", "Y"), ("K", "Y"),
    ("F", "Y"), ("M", "Y"), ("V", "Y"), ("HH", "Y"),
}


def vowel_ipa(base, stress):
    if base == "AH":
        return "ʌ" if stress in "12" else "ə"
    if base == "ER":
        return "ɜːr" if stress in "12" else "ər"
    return VOWELS[base]


def arpabet_to_ipa(phones):
    parsed = []
    for p in phones:
        m = re.fullmatch(r"([A-Z]+)([012]?)", p)
        if not m:
            return None
        base, stress = m.groups()
        if base in VOWELS or base in ("AH", "ER"):
            parsed.append(("V", base, stress or "0"))
        elif base in CONSONANTS:
            parsed.append(("C", base, ""))
        else:
            return None
    syllables = sum(1 for kind, _, _ in parsed if kind == "V")

    # CMU sometimes gives two primary stresses ("lemonade"); IPA wants one,
    # so every primary but the last becomes secondary.
    primaries = [i for i, (k, _, st) in enumerate(parsed) if k == "V" and st == "1"]
    for i in primaries[:-1]:
        kind, base, _ = parsed[i]
        parsed[i] = (kind, base, "2")

    marks = {}  # index in parsed -> mark to insert before it
    split_r = set()  # unstressed ER right before a stressed vowel: "ə" + mark + "r"
    if syllables > 1:
        for i, (kind, base, stress) in enumerate(parsed):
            if kind != "V" or stress not in "12":
                continue
            if i > 0 and parsed[i - 1][1] == "ER" and parsed[i - 1][2] == "0":
                split_r.add(i - 1)           # giraffe: dʒəˈræf, not dʒərˈæf
                marks[i] = "ˈ" if stress == "1" else "ˌ"
                continue
            j = i
            while j > 0 and parsed[j - 1][0] == "C":
                j -= 1
            run = tuple(b for _, b, _ in parsed[j:i])
            start = i - 1 if run else i
            for size in range(len(run), 1, -1):
                if run[-size:] in ONSETS:
                    start = i - size
                    break
            marks[start] = "ˈ" if stress == "1" else "ˌ"

    out = []
    for i, (kind, base, stress) in enumerate(parsed):
        if i in marks:
            out.append(marks[i])
            if i - 1 in split_r:
                out.append("r")
        if i in split_r:
            out.append("ə")
        else:
            out.append(vowel_ipa(base, stress) if kind == "V" else CONSONANTS[base])
    return "".join(out)


# --------------------------------------------------------------------- sources

def fetch(src, name, url):
    path = os.path.join(src, name)
    if not os.path.exists(path):
        print("downloading", url, file=sys.stderr)
        urllib.request.urlretrieve(url, path)
    return path


WORD_RE = re.compile(r"^[a-z][a-z' -]*[a-z]$|^[a-z]$")


def build_ipa(cmu_path):
    ipa = {}
    with open(cmu_path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.split("#", 1)[0].strip()
            if not line:
                continue
            head, *phones = line.split()
            if "(" in head:          # alternate pronunciations: keep the first
                continue
            word = head.lower()
            if not WORD_RE.match(word) or word in ipa:
                continue
            converted = arpabet_to_ipa(phones)
            if converted:
                ipa[word] = converted
    return ipa


def english_terms(field):
    """'person, people; man' -> ['person', 'people', 'man'], best first."""
    field = re.sub(r"\([^)]*\)", " ", field)       # drop "(of time)" notes
    field = re.sub(r"\[[^\]]*\]", " ", field)
    terms = []
    for part in re.split(r"[;,/]", field):
        t = part.strip().lower()
        t = re.sub(r"^to ", "", t)                 # verbs: "to go" -> "go"
        t = re.sub(r"^(a|an|the) ", "", t)
        t = re.sub(r"\s+", " ", t).strip(" .!?*'\"")
        if t and WORD_RE.match(t) and len(t.split()) <= 3:
            terms.append(t)
    return terms


def build_translations(src):
    # A candidate's score is its frequency percentile within its own file plus
    # the penalties above; lower is better. Each English word keeps its best 3.
    candidates = defaultdict(dict)
    csv.field_size_limit(10 ** 9)
    for name in OR_FILES:
        path = fetch(src, "ru_" + name, OR_BASE + name)
        with open(path, encoding="utf-8") as fh:
            rows = list(csv.DictReader(fh, delimiter="\t"))
        for rank, row in enumerate(rows):
            percentile = rank / max(len(rows), 1)
            if percentile > MAX_PERCENTILE[name]:
                break
            ru = (row.get("bare") or "").strip()
            if not ru or not re.fullmatch(r"[а-яё -]+", ru.lower()) or ru.lower() in NEVER_SUGGEST:
                continue
            for pos, term in enumerate(english_terms(row.get("translations_en") or "")):
                score = percentile + FILE_PENALTY[name] + pos * SENSE_PENALTY
                best = candidates[term].get(ru)
                if best is None or score < best:
                    candidates[term][ru] = score
    table = {}
    for term, options in candidates.items():
        ranked = sorted(options.items(), key=lambda kv: kv[1])
        table[term] = [ru for ru, _ in ranked[:3]]
    return table


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default="build/dict-src")
    ap.add_argument("--out", default="app/src/main/assets/dict")
    args = ap.parse_args()
    os.makedirs(args.src, exist_ok=True)
    os.makedirs(args.out, exist_ok=True)

    ipa = build_ipa(fetch(args.src, "cmudict.dict", CMU_URL))
    translations = build_translations(args.src)

    with open(os.path.join(args.out, "en_ipa.txt"), "w", encoding="utf-8") as fh:
        for word in sorted(ipa):
            fh.write(f"{word}\t{ipa[word]}\n")
    with open(os.path.join(args.out, "en_ru.txt"), "w", encoding="utf-8") as fh:
        for word in sorted(translations):
            fh.write(f"{word}\t{'|'.join(translations[word])}\n")
    print(f"en_ipa: {len(ipa)} words, en_ru: {len(translations)} words", file=sys.stderr)


if __name__ == "__main__":
    main()
