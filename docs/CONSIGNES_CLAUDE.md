# Consignes pour Claude : préparer la liste de citations du mois

Copie ce texte dans ta conversation avec Claude (ou dans les instructions d'un projet Claude), puis colle l'export de tes avis depuis l'appli.

---

Tu prépares chaque mois la liste de citations affichée sur l'écran de verrouillage de mon téléphone. Je te colle l'export de mes avis (citations aimées, pas aimées, vues sans avis). Ajuste la nouvelle liste selon mes goûts : davantage d'auteurs, de thèmes et de tons proches de ce que j'ai aimé, moins de ce que je n'ai pas aimé, tout en gardant un peu de découverte.

Règles :

1. Environ 30 citations, en français, de nature philosophique (stoïcisme, existentialisme, moralistes, Antiquité, etc.).
2. Uniquement des citations vérifiées, avec l'auteur réel et la source précise (œuvre, et livre, chapitre ou paragraphe quand c'est possible). Pas de citations apocryphes ou attribuées à tort. Pour un auteur étranger, utilise une traduction française reconnue.
3. Chaque texte fait au maximum 220 caractères, pour rester lisible sur l'écran de verrouillage.
4. N'utilise jamais de tiret long ni de tiret moyen (ni dans le texte, ni dans l'auteur, ni dans la source). Le trait d'union des mots composés est autorisé.
5. N'ajoute pas les guillemets « » autour du texte : l'appli les ajoute elle-même.
6. Tu peux garder les citations que j'ai aimées. Retire celles que je n'ai pas aimées.
7. Chaque citation a un identifiant unique (`id`) en minuscules, sans espaces ni accents, par exemple `auteur-mot-cle`. Une citation conservée garde le même identifiant (sinon mon avis sur elle est perdu).
8. Réponds avec le fichier JSON complet, dans un seul bloc de code, et rien d'autre dans le bloc.

Format exact attendu :

```json
{
  "version": 1,
  "updated": "AAAA-MM-JJ",
  "quotes": [
    {
      "id": "seneque-temps",
      "text": "Ce n'est pas que nous ayons peu de temps, c'est que nous en perdons beaucoup.",
      "author": "Sénèque",
      "source": "De la brièveté de la vie, I",
      "theme": "stoïcisme"
    }
  ]
}
```

Champs obligatoires : `id`, `text`, `author`. Champs conseillés : `source`, `theme`.
