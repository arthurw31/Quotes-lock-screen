"""Refuse les tirets longs et moyens dans les fichiers texte du dépôt."""
import pathlib
import sys

BAD = ("\u2014", "\u2013")
SKIP_DIRS = {".git", "build", ".gradle", ".kotlin"}
found = 0
for path in pathlib.Path(".").rglob("*"):
    if not path.is_file() or SKIP_DIRS.intersection(path.parts):
        continue
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except (UnicodeDecodeError, OSError):
        continue
    for n, line in enumerate(lines, start=1):
        if any(ch in line for ch in BAD):
            print(f"{path}:{n}: {line.strip()}")
            found += 1
if found:
    print(f"{found} ligne(s) avec un tiret long ou moyen, à remplacer.")
    sys.exit(1)
print("Aucun tiret long ou moyen.")
