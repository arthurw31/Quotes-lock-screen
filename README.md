# Citations sur l'écran de verrouillage

Appli Android personnelle qui affiche une citation par-dessus ta photo de fond, directement sur l'écran de verrouillage. La citation change toute seule chaque jour, et la liste se met à jour depuis ce dépôt GitHub, sans réinstaller l'appli.

![Aperçu du rendu](docs/apercu.png)

## Comment ça marche

Android ne permet pas à une appli d'écrire directement sur l'écran de verrouillage. L'appli fabrique donc elle-même l'image : elle prend ta photo, dessine la citation dessus, et l'installe **uniquement** comme fond de l'écran de verrouillage. Ton écran d'accueil ne change pas.

* Une image fixe ne consomme rien : l'appli ne travaille que quelques secondes par jour.
* Elle vérifie la liste de citations sur GitHub deux fois par jour quand il y a du réseau. Sans réseau, elle garde la dernière liste téléchargée (et une liste de secours est intégrée à l'appli).
* Les citations ne se répètent pas tant que toutes n'ont pas été vues, et celles marquées « je n'aime pas » ne reviennent plus.

## 1. Installer l'appli (une seule fois)

Avant la toute première installation, fais l'étape « Clé de signature » plus bas (une seule fois).

1. Sur ton Pixel, ouvre ce lien dans Chrome :
   **https://github.com/arthurw31/Quotes-lock-screen/releases/latest/download/citations.apk**
2. Chrome télécharge `citations.apk`. Touche la notification de téléchargement (ou ouvre l'appli Fichiers, dossier Téléchargements) et ouvre le fichier.
3. Android affiche « Pour votre sécurité, votre téléphone n'est pas autorisé à installer des applis inconnues ». Touche **Paramètres**, active **Autoriser cette source**, puis reviens en arrière.
4. Touche **Installer**. Si Google Play Protect affiche un avertissement (appli inconnue), choisis **Plus de détails** puis **Installer quand même**. C'est normal pour une appli qui ne vient pas du Play Store.

## 2. Régler l'appli (une seule fois)

1. Ouvre **Citations**.
2. Onglet **Apparence** : touche **Choisir ma photo** et sélectionne la photo que tu utilises comme fond d'écran.
3. Ajuste la position, la taille, la police, la couleur et le voile de lisibilité. L'aperçu montre aussi l'horloge pour vérifier que rien ne se chevauche.
4. Touche **Appliquer**, puis verrouille ton téléphone pour voir le résultat.
5. Onglet **Citations**, carte « Fonctionnement en arrière-plan » :
   * touche **Autoriser** pour que l'appli fonctionne sans restriction de batterie (sinon Android peut retarder le changement de citation d'un jour ou plus si tu n'ouvres jamais l'appli) ;
   * touche **Ouvrir les infos de l'appli** et désactive **Suspendre l'activité dans l'appli si elle n'est pas utilisée**. Sans ça, Android peut mettre l'appli en veille au bout de quelques mois.

C'est tout. La citation change ensuite toute seule, peu après minuit.

## 3. Mettre à jour les citations (chaque mois)

1. Dans l'appli, onglet **Citations**, touche **Exporter mes avis** et envoie le texte dans ta conversation avec Claude (copier, ou partager vers l'appli Claude).
2. Donne aussi à Claude les consignes du fichier [`docs/CONSIGNES_CLAUDE.md`](docs/CONSIGNES_CLAUDE.md) (il suffit de les copier une fois dans la conversation ou dans un projet Claude). Claude te renvoie un fichier JSON complet.
3. Sur GitHub (navigateur ou appli), ouvre le fichier [`citations/quotes.json`](citations/quotes.json), touche le **crayon** (Edit), sélectionne tout le contenu, remplace-le par celui donné par Claude, puis touche **Commit changes**.
4. GitHub vérifie automatiquement le fichier (onglet **Actions**, contrôle « Vérifier les citations »). Une coche verte veut dire que c'est bon. Une croix rouge indique une erreur de format : ouvre le détail, l'erreur est expliquée en français. Même en cas d'erreur, l'appli ne casse pas : elle garde l'ancienne liste.
5. Le téléphone récupère la nouvelle liste dans les 12 heures. Pour le faire tout de suite : onglet **Citations**, **Vérifier maintenant**.

La citation affichée ne change pas au moment de la mise à jour : la nouvelle liste est utilisée à partir du prochain changement.

## 4. Installer une nouvelle version de l'appli

Quand le code de l'appli est modifié, GitHub compile et publie automatiquement une nouvelle version dans l'onglet **Releases**. Pour l'installer, refais simplement l'étape 1 avec le même lien : la nouvelle version s'installe par-dessus l'ancienne, et ta photo, tes réglages et tes avis sont conservés.

Option pratique : l'appli gratuite **Obtainium** peut surveiller ce dépôt et te prévenir des nouvelles versions. Il suffit d'y ajouter l'adresse `https://github.com/arthurw31/Quotes-lock-screen`.

Modifier seulement les citations ne crée pas de nouvelle version de l'appli : ce n'est pas nécessaire.

## Limites à connaître

* **Fonds spéciaux** : si tu utilisais un fond animé, un effet de profondeur (horloge derrière le sujet) ou un fond généré par l'IA sur l'écran de verrouillage, il est remplacé par une image fixe.
* **Notifications** : quand tu as beaucoup de notifications, elles peuvent recouvrir la citation. Place-la plus bas si ça te gêne.
* **Heure du changement** : Android regroupe les tâches de fond pour économiser la batterie. Le changement a lieu en général dans l'heure qui suit minuit, parfois un peu plus tard.
* **Changer de fond d'écran** : si tu changes de photo dans les réglages du Pixel, l'écran de verrouillage sera écrasé jusqu'au prochain changement de citation. Change plutôt la photo dans l'appli Citations.
* **Vérification des développeurs** : Google déploie progressivement l'obligation de vérifier l'identité des développeurs pour les applis installées hors Play Store. En France, rien ne bloque pour l'instant. Si un jour l'installation est refusée, il restera l'installation par câble (adb) ou un compte développeur gratuit « amateur ».

## Clé de signature (à faire une seule fois)

Pour que chaque nouvelle version s'installe par-dessus l'ancienne, toutes les versions doivent être signées avec la même clé. Comme le dépôt est public, cette clé n'y est pas : elle est rangée dans les **Secrets** du dépôt, que personne d'autre ne peut lire.

1. Sur github.com, ouvre le dépôt, puis **Settings** > **Secrets and variables** > **Actions**.
2. Touche **New repository secret** et crée ces deux secrets (les valeurs sont dans le fichier `secrets-github.txt` que Claude t'a envoyé) :
   * `SIGNING_PASSWORD`
   * `SIGNING_KEYSTORE_BASE64`
3. Onglet **Actions**, choisis **Construire l'appli**, puis **Run workflow**. Au bout de quelques minutes, l'APK apparaît dans **Releases**.

Garde une copie de `secrets-github.txt` en lieu sûr (gestionnaire de mots de passe, par exemple). Si la clé était perdue, il faudrait désinstaller l'appli une fois pour installer une version signée avec une nouvelle clé.

## Organisation du dépôt

| Emplacement | Contenu |
| --- | --- |
| `citations/quotes.json` | La liste de citations (le seul fichier à modifier chaque mois) |
| `docs/CONSIGNES_CLAUDE.md` | Les consignes à donner à Claude pour préparer une nouvelle liste |
| `app/` | Le code de l'appli Android (Kotlin) |
| `.github/workflows/` | Compilation automatique de l'APK et vérification des citations |
