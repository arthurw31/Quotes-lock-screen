# Consignes pour Claude : préparer la liste de citations du mois

Copie ce texte dans ta conversation avec Claude (ou dans les instructions d'un projet Claude), puis colle l'export de tes avis depuis l'appli.

---

Tu prépares chaque mois la liste de citations affichée sur l'écran de verrouillage de mon téléphone. Je te colle l'export de mes avis (citations aimées, pas aimées, vues sans avis). Ajuste la nouvelle liste selon mes goûts : davantage d'auteurs, de thèmes et de tons proches de ce que j'ai aimé, moins de ce que je n'ai pas aimé, tout en gardant un peu de découverte.

Règles :

1. Environ 30 citations, en français, de nature philosophique (stoïcisme, existentialisme, moralistes, Antiquité, etc.).
2. Uniquement des citations vérifiées, avec l'auteur réel et la source précise (œuvre, et livre, chapitre ou paragraphe quand c'est possible). Pas de citations apocryphes ou attribuées à tort. Pour un auteur étranger, utilise une traduction française reconnue.
3. Chaque citation doit se comprendre seule, sans connaître le livre, le personnage ou la situation d'origine. Elle doit faire réfléchir et changer un peu la façon de voir la vie (le temps, la liberté, le bonheur, les autres, les épreuves...). Évite les phrases qui n'ont de sens que dans leur contexte, les références obscures et les tournures trop anciennes. Privilégie les citations profondes : paradoxes, vérités dérangeantes sur nous-mêmes, réflexions sur le temps, la mort, le désir, la liberté. Évite les clichés de développement personnel et les citations trop connues ou trop simples (« un voyage de mille lieues… », « carpe diem »…). Pas de citations d'entrepreneurs ni de rap.
4. Chaque texte fait au maximum 220 caractères, pour rester lisible sur l'écran de verrouillage.
5. N'utilise jamais de tiret long ni de tiret moyen (ni dans le texte, ni dans l'auteur, ni dans la source). Le trait d'union des mots composés est autorisé.
6. N'ajoute pas les guillemets « » autour du texte : l'appli les ajoute elle-même.
7. Tu peux garder les citations que j'ai aimées. Retire celles que je n'ai pas aimées.
8. Chaque citation a un identifiant unique (`id`) en minuscules, sans espaces ni accents, par exemple `auteur-mot-cle`. Une citation conservée garde le même identifiant (sinon mon avis sur elle est perdu).
9. Pour chaque citation, ajoute une explication simple (1 à 3 phrases, en tutoyant) dans `explanation`, et un exemple concret de la vie quotidienne dans `example`. Maximum 250 caractères chacun.
10. Réponds avec le fichier JSON complet, dans un seul bloc de code, et rien d'autre dans le bloc.

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
      "theme": "stoïcisme",
      "explanation": "La vie n'est pas trop courte en soi : c'est nous qui en gaspillons une grande partie en distractions et en attente.",
      "example": "Fais le compte des heures passées chaque semaine à faire défiler des contenus qui ne t'apportent rien."
    }
  ]
}
```

Champs obligatoires : `id`, `text`, `author`. Champs conseillés : `source`, `theme`, `explanation`, `example`.

## Mots anglais

La liste contient aussi des mots anglais (environ 100), mélangés aux citations. Pour en ajouter ou en remplacer :

* Des mots ou expressions de la vie courante, mais un peu plus rares : ceux qu'on entend dans les séries, les conversations ou les articles, sans les apprendre en cours. Pas de mots basiques, ni de mots presque identiques en français.
* Champs : `id` (commence par `en-`), `kind` égal à `"word"`, `text` (le mot, avec « to » pour un verbe), `translation` (traduction française courte), `definition` (facultative, une phrase courte quand la traduction ne suffit pas), `example` (une phrase en anglais suivie de sa traduction entre parenthèses), `theme` égal à `"anglais"`.

```json
{
  "id": "en-linger",
  "kind": "word",
  "text": "to linger",
  "translation": "s'attarder",
  "definition": "Rester plus longtemps que prévu, avoir du mal à partir.",
  "example": "We lingered over coffee long after the meal was finished. (On s'est attardés autour d'un café bien après la fin du repas.)",
  "theme": "anglais"
}
```
