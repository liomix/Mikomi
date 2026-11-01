"""AniCyk 报文解密探针：把加密响应还原成 JSON，便于开发期核对字段。

用法：curl 一份响应存文件后 `python tools/cyk_probe.py <file>`（需 pycryptodome）。
"""

import base64
import hashlib
import json
import sys

from Crypto.Cipher import AES

KEY = hashlib.sha256(b"%ZZJ_ciyuan_2025!@").digest()


def decode(raw: str) -> str:
    raw = raw.strip().lstrip("\ufeff")
    if raw.startswith("Dddm"):
        return base64.b64decode(raw[len("Dddm"):]).decode("utf-8")
    if not raw.startswith("CYK2026"):
        return raw
    payload = raw[len("CYK2026"):]
    iv_b64, _, data_b64 = payload.partition(":")
    iv = base64.b64decode(iv_b64)
    data = base64.b64decode(data_b64)
    cipher = AES.new(KEY, AES.MODE_CBC, iv)
    padded = cipher.decrypt(data)
    pad = padded[-1]
    return padded[:-pad].decode("utf-8")


def main() -> None:
    with open(sys.argv[1], encoding="utf-8") as handle:
        text = decode(handle.read())
    data = json.loads(text)
    if isinstance(data, list):
        print(f"days={len(data)}")
        for index, day in enumerate(data):
            print(f"--- day[{index}] items={len(day) if isinstance(day, list) else '?'}")
            if isinstance(day, list) and day:
                print(json.dumps(day[0], ensure_ascii=False)[:600])
                keys = sorted({k for item in day if isinstance(item, dict) for k in item})
                print("keys:", keys)
    else:
        print(json.dumps(data, ensure_ascii=False)[:1500])


if __name__ == "__main__":
    main()
