
# bibliofx
Application JavaFX de gestion de bibliothèque personnelle avec persistance locale, formulaires d’ajout/édition et panneau de détails. 
=======
# BiblioFX

Application JavaFX de gestion de bibliothèque personnelle avec persistance locale, formulaires d’ajout/édition et panneau de détails. J’ai privilégié une architecture simple et lisible avec FXML pour décrire les vues, des contrôleurs dédiés par écran/composant, et un repository JSON pour la persistance. L’objectif est d’avoir une base propre et extensible.

## Sommaire
- Contexte et objectifs
- Fonctionnalités
- Structure du projet
- Choix techniques et justifications
- Démarrage (build, run, packaging)
- Persistance des données (format JSON)
- Détails d’implémentation par composant
- Style & UX
- Dépannage (FAQ rapide)
- Limites connues & pistes d’évolution

## Contexte et objectifs
Je voulais une app desktop simple pour gérer mes livres: lister, filtrer, ajouter/éditer, consulter les détails, et organiser par bibliothèques.

## Fonctionnalités
- Liste des livres avec colonnes: Titre, Auteur, Année, Genre, Disponibilité, Ajouté le.
- Filtres rapides:
  - Recherche par titre
  - Filtre par genre
  - Filtre Disponibles seulement
  - Filtre par statut Lu, Non Lu, En Cours
- Affichage détaillé du livre sélectionné (résumé, dates, image de couverture).
- Ajout et édition via une boîte de dialogue dédiée (validation des champs).
- Suppression d’un livre.
- Organisation multi-bibliothèques (sélection, création, renommage, suppression).
- Suggestions d’auto-complétion (Google Books) lors de l’ajout/édition pour pré-remplir titre/auteur/année/genre/résumé/couverture.

## Structure du projet
Racine du repository:
- build.gradle.kts, settings.gradle.kts, gradlew(.bat), gradle/wrapper: configuration Gradle et wrapper.
- src/main/java:
  - fr/cactusstudio/bibliofx
    - Main.java: Application JavaFX principale, charge hello-view.fxml et la feuille de style globale.
    - Launcher.java: entrée alternative (Application.launch) pour compatibilité IDE/environnements.
    - Controller.java: contrôleur principal de l’écran (table + filtres + gestion bibliothèques + actions CRUD).
    - AddEditBookController.java: contrôleur du formulaire d’ajout/édition, suggestions Google Books, validation.
    - BookDetailController.java: contrôleur du panneau de détails inclus (book.fxml).
    - LibraryRepository.java: persistance JSON (~/.bibliofx.json), migration ancien format, multi-bibliothèques.
  - fr/cactusstudio/bibliofx/model
    - Book.java: modèle de données d’un livre + helpers de formatage de dates.
  - module-info.java: module Java (exports/opens et requires).
- src/main/resources/fr/cactusstudio/bibliofx
  - hello-view.fxml: vue principale (BorderPane) avec top bar, TableView au centre et panneau de détails à droite (fx:include book.fxml).
  - addBook.fxml / editBook.fxml: formulaires d’ajout et d’édition (même contrôleur).
  - book.fxml: panneau de détails (image + méta + résumé).
  - styles.css: thème clair moderne (contraste élevé, styles boutons/inputs/table).

## Choix techniques et justifications
- Java 24 (Gradle toolchain):
  - Le build utilise la Toolchain Gradle pour cibler Java 24. Gradle télécharge/emploie un JDK compatible si nécessaire.
- JavaFX 21.0.6 + FXML:
  - FXML pour séparer la vue et la logique. Contrôleurs annotés @FXML.
  - Modules utilisés: javafx.controls, javafx.fxml.
- Gradle + plugins:
  - org.openjfx.javafxplugin (gestion des modules JavaFX).
  - application (configuration mainModule/mainClass pour `gradlew run`).
  - org.beryx.jlink (packaging natif via image runtime personnalisée).
  - org.javamodularity.moduleplugin (facilite le build modulaire).
- Modularisation (module-info.java):
  - `requires javafx.controls, javafx.fxml, com.google.gson, java.net.http`.
  - `opens fr.cactusstudio.bibliofx to javafx.fxml, com.google.gson` (FXML et sérialisation).
  - `opens fr.cactusstudio.bibliofx.model to javafx.base, com.google.gson` (bindings TableView et JSON).
- Persistance locale JSON avec Gson:
  - Fichier `~/.bibliofx.json` (lisible/modifiable si besoin).
  - Support multi-bibliothèques + champ `current`.
  - Migration depuis un ancien format (tableau simple) gérée au démarrage du repository.
- Suggestions via Google Books API:
  - Requêtes HTTP en asynchrone (java.net.http + CompletableFuture).
  - Parsing JSON avec Gson (JsonParser), mapping minimal vers un objet Suggestion.
  - Choix de la meilleure image disponible (extraLarge → smallThumbnail) et sécurisation http→https.
  - Mapping des catégories Google vers nos genres (heuristiques FR/EN basiques).
- UI/UX: TableView + filtres instantanés, panneau de détail via fx:include, feuille de style globale pour homogénéité.

## Démarrage (build, run, packaging)
Prérequis: rien de spécifique si vous utilisez le wrapper Gradle. Une connexion internet est utile pour les suggestions Google Books (facultatif).

- Lancer l’application (Linux/macOS):
  - `./gradlew run`
- Lancer sur Windows:
  - `gradlew.bat run`
- Construire une image exécutable (jlink):
  - `./gradlew jlinkZip`
  - Archive générée: `build/distributions/app-<classifier>.zip` (classifier selon OS/arch OpenJFX).
  - Dézipper puis lancer `bin/BiblioFX` (ou `bin/BiblioFX.exe` sous Windows si image jpackage).
- Générer un installateur Windows (.exe) avec jpackage (Windows uniquement):
  - Pré-requis: JDK 17+ avec jpackage disponible dans le PATH. (WiX n’est pas nécessaire pour .exe, uniquement pour .msi.)
  - Commande rapide (alias): `gradlew makeInstaller`
  - Alternative directe: `gradlew jpackage`
  - Portable (app image): `gradlew jpackageImage` → ouvreur: `build/jpackage/BiblioFX/bin/BiblioFX.exe`.
  - Installateur .exe généré: `build/jpackage/BiblioFX-setup-<version>.exe` (ex: `BiblioFX-setup-1.0-SNAPSHOT.exe`).
  - Options: un raccourci et une entrée de menu Démarrer sont générés via `--win-shortcut` et `--win-menu`.

Notes:
- Le plugin OpenJFX gère les modules JavaFX au runtime, `application` définit `mainModule` et `mainClass`.
- La Toolchain Gradle cible Java 24; Gradle sélectionne un JDK compatible si configuré pour le faire.

## Persistance des données (format JSON)
Fichier: `~/.bibliofx.json`

Structure (simplifiée):
```
{
  "current": "Bibliothèque",
  "libraries": {
    "Bibliothèque": [
      {
        "title": "...",
        "author": "...",
        "year": 2023,
        "genre": "Roman",
        "available": true,
        "summary": "...",
        "coverUrl": "https://...",
        "addedAt": 1714068890000,
        "borrowedAt": null
      }
    ],
    "Pro": []
  }
}
```

Remise à zéro: vous pouvez supprimer `~/.bibliofx.json` pour repartir d’un état vierge (l’app le recréera).

## Détails d’implémentation par composant
- Main.java
  - Charge `hello-view.fxml`, applique `styles.css` globalement, instancie la scène 900x500.
- Launcher.java
  - Point d’entrée alternatif: `Application.launch(Main.class, args)`.
- Controller.java (écran principal)
  - Initialise la combo de bibliothèques (chargée depuis `LibraryRepository`), sélectionne la bibliothèque courante et écoute les changements.
  - Charge les livres de la bibliothèque courante. Si `addedAt` absent, il est renseigné à l’initialisation pour normaliser les données.
  - Configure les colonnes de la TableView via `PropertyValueFactory`.
  - Colonne Disponibilité rendue textuellement: "Disponible" ou "Emprunté le <date>" si la date d’emprunt est connue.
  - Colonne "Ajouté le": affichage au format `dd/MM/yyyy`, avec comparateur pour trier proprement.
  - Filtres: `FilteredList<Book>` avec prédicats combinant recherche par titre, genre sélectionné et disponibilité.
  - Handlers principaux: `onAdd`, `onEdit`, `onDelete`, `onNewLibrary`, `onRenameLibrary`, `onDeleteLibrary`, `onSwitchLibrary`.
  - Ouverture des boîtes de dialogue d’ajout/édition via `FXMLLoader` et `Stage` modal; récupération du `Book` résultant en sortie du contrôleur enfant (`getResult()`).
  - Synchronise le panneau de détails en fonction de la sélection courante.
- AddEditBookController.java (formulaire)
  - Initialise la liste de genres, gère les champs, et un menu contextuel de suggestions.
  - `fetchSuggestionsAsync(query)`: requête Google Books (maxResults=5), parsing JSON, construction d’une liste minimaliste (title, author, year, genre mappé, summary, coverUrl), puis affichage d’un menu contextuel pour appliquer une suggestion.
  - `mapCategoryToGenre`: heuristique simple FR/EN pour convertir des catégories Google vers nos genres.
  - Validation: titre/auteur requis, année numérique 0..9999; messages d’alerte via `Alert`.
  - Génère un `Book` avec `addedAt` (et `borrowedAt` si indisponible à la création), renseigne les champs optionnels (résumé, couverture).
- BookDetailController.java (panneau de détails)
  - Méthode `setBook(Book)`: gère le cas null (efface l’affichage) et le cas renseigné (met à jour labels, résumé, image).
  - Chargement d’image: accepte URL http/https/file: ou un chemin local transformé en URI si le fichier existe.
- LibraryRepository.java (persistance)
  - Fichier `~/.bibliofx.json` avec structure `{ current, libraries: { name -> [Book] } }`.
  - Méthodes: `listLibraries`, `getCurrentLibrary`, `setCurrentLibrary`, `load(name)`, `save(name, books)`, `createLibrary`, `renameLibrary`, `deleteLibrary`.
  - `ensureInitialized()`: crée le fichier si absent et migre depuis l’ancien format (un simple tableau JSON de livres).
- Book.java (modèle)
  - Champs principaux (title, author, year, genre, available), métadonnées (summary, coverUrl), timestamps (addedAt, borrowedAt).
  - Helpers d’affichage: `getAddedAtFormatted()`, `getBorrowedAtFormatted()`, variantes "date seule".

## Style & UX
- `styles.css` définit une charte claire/contrastée, avec composants cohérents (boutons primaires, inputs, TableView stylée, cartes/panneaux).
- La vue principale `hello-view.fxml` isole la top bar (filtres/actions), la table centrale et le panneau de détails à droite (scrollable).
- Le menu de suggestions ajuste sa largeur pour rester lisible sans dépasser la largeur de la scène.

## Dépannage (FAQ rapide)
- Erreur de modules JavaFX au lancement: utilisez `./gradlew run` (le plugin OpenJFX gère le classpath/modulepath).
- Pas de suggestions: l’app fonctionne hors-ligne; les suggestions Google Books nécessitent internet. Les erreurs réseau sont silencieusement ignorées.
- Images de couverture qui ne s’affichent pas: vérifiez l’URL (https recommandé) ou le chemin fichier local.
- Données incohérentes après mise à jour: supprimez `~/.bibliofx.json` pour repartir sur une base propre (attention, perte de données).

## Limites connues & pistes d’évolution
- Pas de tests unitaires pour l’instant (JUnit 5 déjà configuré dans le build).
- Suggestions Google Books sans clé API (quota public et réponses variables).
- Pas d’annulation (undo/redo) sur les opérations CRUD.
- Internationalisation (i18n) minimale: libellés en français codés en dur.
- Évolutions possibles:
  - Ajouter un bouton Emprunter/Restituer qui gère `available` et `borrowedAt`.
  - Export/Import (JSON/CSV).
  - Champs supplémentaires (éditeur, ISBN), et filtres avancés (auteur, année).
  - Tests unitaires pour le mapping des catégories, la persistance, et les filtres.
>>>>>>> 1fdb5d9 (Bibliofx v1)
