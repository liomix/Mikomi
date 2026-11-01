"""离线自检：复刻标题归一化与日期锚定打分，跑真实 AniCyk 报文。

用法：python tools/verify_schedule_logic.py <cyk_schedule_response>
"""

import json
import re
import sys

from cyk_probe import decode

CHINESE_NUMERALS = {
    "一": 1, "二": 2, "三": 3, "四": 4, "五": 5,
    "六": 6, "七": 7, "八": 8, "九": 9, "十": 10, "十一": 11, "十二": 12,
}
ROMAN = {"Ⅱ": 2, "Ⅲ": 3, "Ⅳ": 4, "Ⅴ": 5, "Ⅵ": 6, "Ⅶ": 7}

BRACKET_PREFIX = re.compile(r"^[\[【(（]([^\]】)）]{1,24})[\]】)）]\s*")
FANSUB_KEYWORDS = ["字幕", "汉化", "翻译", "压制", "搬运", "组", "fansub", "sub", "raw"]
CHINESE_SEASON = re.compile(r"第\s*([一二三四五六七八九十0-9]{1,3})\s*季")
ENGLISH_SEASON = re.compile(r"(?i)(?:season|s)\s*(\d{1,2})(?:\s*(?:st|nd|rd|th))?")
SHORT_SEASON = re.compile(r"(?i)\bs(\d{1,2})\b")
ROMAN_SEASON = re.compile(r"(?<![\u4e00-\u9fff])(Ⅱ|Ⅲ|Ⅳ|Ⅴ|Ⅵ|Ⅶ)(?![\u4e00-\u9fff])")
LEFTOVER = re.compile(r"(?i)(第\s*季|season)")


def to_half_width(text):
    out = []
    for char in text:
        if char == "\u3000":
            out.append(" ")
        elif 0xFF01 <= ord(char) <= 0xFF5E:
            out.append(chr(ord(char) - 0xFEE0))
        else:
            out.append(char)
    return "".join(out)


def strip_fansub(title):
    current = title
    for _ in range(2):
        match = BRACKET_PREFIX.match(current)
        if not match:
            return current
        inner = match.group(1).lower()
        if not any(keyword in inner for keyword in FANSUB_KEYWORDS):
            return current
        current = current[: match.start()] + current[match.end():]
        current = current.strip()
    return current


def parse_season(title):
    match = CHINESE_SEASON.search(title)
    if match:
        text = match.group(1)
        return int(text) if text.isdigit() else CHINESE_NUMERALS.get(text)
    match = ENGLISH_SEASON.search(title)
    if match:
        return int(match.group(1))
    match = ROMAN_SEASON.search(title)
    if match:
        return ROMAN.get(match.group(0))
    return None


def normalize(title):
    text = strip_fansub(to_half_width(title).strip())
    season = parse_season(text)
    text = CHINESE_SEASON.sub(" ", text)
    text = ENGLISH_SEASON.sub(" ", text)
    text = SHORT_SEASON.sub(" ", text)
    text = ROMAN_SEASON.sub(" ", text)
    text = LEFTOVER.sub(" ", text)
    return "".join(ch.lower() for ch in text if ch.isalnum()), season


def overlap_ratio(left, right):
    smaller = max(1, min(len(left), len(right)))
    return len(left & right) / smaller


def main():
    path = sys.argv[1]
    with open(path, encoding="utf-8") as handle:
        days = json.loads(decode(handle.read()))

    day_titles = []
    samples = []
    for index, day in enumerate(days):
        titles = set()
        for item in day:
            raw = item.get("title", "")
            normalized, season = normalize(raw)
            if normalized:
                titles.add(normalized)
            if len(samples) < 260:
                samples.append((raw, normalized, season))
        day_titles.append(titles)
        print(f"day[{index}] items={len(day)} uniqTitles={len(titles)}")

    print("\n--- 归一化抽样（原始 → 主干 / 季数）---")
    for raw, normalized, season in samples[:18]:
        print(f"  {raw}  →  {normalized}  (season={season})")

    blanks = [raw for raw, normalized, _ in samples if not normalized]
    print(f"\n归一出空串的标题：{blanks if blanks else '无'}")

    print("\n--- 锚定打分自检（用自身数据当参考，应回到真实锚点）---")
    week = list(range(7))
    for truth in week:
        reference = {}
        for offset, titles in enumerate(day_titles):
            slot = truth + offset
            if 0 <= slot < 7:
                reference[slot] = titles

        best, best_score = -1, 0.0
        for start in week:
            score, covered = 0.0, 0
            for offset, titles in enumerate(day_titles):
                slot = start + offset
                if slot not in reference or not titles:
                    continue
                covered += 1
                score += overlap_ratio(titles, reference[slot])
            average = score / covered if covered else 0.0
            if average > best_score:
                best, best_score = start, average
        flag = "OK" if best == truth else "AMBIGUOUS"
        print(f"  truth={truth} → best={best} score={best_score:.3f} [{flag}]")


if __name__ == "__main__":
    main()
