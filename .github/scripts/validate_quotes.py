"""Vérifie que le fichier de citations est valide avant que l'appli ne le télécharge."""
import json
import sys

FORBIDDEN = {"\u2014": "tiret long", "\u2013": "tiret moyen"}
MAX_LEN = 260

path = sys.argv[1] if len(sys.argv) > 1 else "citations/quotes.json"
errors = []

try:
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
except json.JSONDecodeError as e:
    print(f"ERREUR : le fichier n'est pas du JSON valide (ligne {e.lineno}, colonne {e.colno}) : {e.msg}")
    sys.exit(1)

quotes = data.get("quotes") if isinstance(data, dict) else None
if not isinstance(quotes, list) or not quotes:
    print("ERREUR : il faut une liste \"quotes\" non vide.")
    sys.exit(1)

ids = set()
for n, q in enumerate(quotes, start=1):
    where = f"citation n°{n}"
    if not isinstance(q, dict):
        errors.append(f"{where} : ce n'est pas un objet {{...}}")
        continue
    qid = str(q.get("id", "")).strip()
    text = str(q.get("text", "")).strip()
    author = str(q.get("author", "")).strip()
    if qid:
        where += f" ({qid})"
        if qid in ids:
            errors.append(f"{where} : identifiant en double")
        ids.add(qid)
    else:
        errors.append(f"{where} : champ \"id\" manquant")
    if not text:
        errors.append(f"{where} : champ \"text\" vide")
    is_word = q.get("kind") == "word"
    if is_word:
        if not str(q.get("translation", "")).strip():
            errors.append(f"{where} : champ \"translation\" vide (mot anglais)")
    elif not author:
        errors.append(f"{where} : champ \"author\" vide")
    if len(text) > MAX_LEN:
        errors.append(f"{where} : texte trop long ({len(text)} caractères, maximum {MAX_LEN})")
    for field in ("text", "author", "source", "theme", "explanation", "example", "translation", "definition"):
        value = str(q.get(field) or "")
        for ch, name in FORBIDDEN.items():
            if ch in value:
                errors.append(f"{where} : {name} interdit dans \"{field}\"")

if errors:
    print("Le fichier contient des erreurs :")
    for e in errors:
        print(" * " + e)
    sys.exit(1)

words = sum(1 for q in quotes if isinstance(q, dict) and q.get("kind") == "word")
print(f"OK : {len(quotes) - words} citations et {words} mots valides.")
