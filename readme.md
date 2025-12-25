# ParticuleToy

ParticuleToy est un simulateur de matière / particules 2D (style "sandbox") inspiré du genre popularisé par The Powder Toy, mais **implémenté entièrement de zéro** en Java (sans réutiliser de code externe soumis au copyright).

Objectif MVP 🎯
- Une grille 2D (cellules) qui simule des éléments simples (mur, sable, eau).
- Une UI desktop (JavaFX) pour peindre des particules et voir la simulation en temps réel.
- Un socle propre (architecture + tests) pour itérer rapidement.

## Architecture (multi-module Maven)

- `particuletoy-core` : moteur de simulation (aucune dépendance UI)
- `particuletoy-desktop` : application JavaFX (rendu + interactions utilisateur)

## Prérequis

- JDK 17 (projet compilé en `--release 17`)
- Maven 3.9+ recommandé

Note JavaFX ⚠️
Le projet utilise une version JavaFX compatible JDK 17+. Si vous changez la version JavaFX, vérifiez la compatibilité minimale JDK avant de mettre à jour.

## Lancer l'app

Depuis la racine :

- Tests core :
  `mvn -pl particuletoy-core test`

- Lancer l'app JavaFX :
  `mvn -pl particuletoy-desktop javafx:run`

Maven télécharge automatiquement les modules JavaFX (dont les libs natives) si les dépendances sont bien déclarées.

## Contrôles (MVP)

- Clic gauche : peindre l'élément sélectionné
- Clic droit : effacer (EMPTY)
- Le bord est un mur de confinement (non "paintable") pour éviter les sorties.

## Roadmap (à venir)

- Plus d'éléments (poudre, feu, fumée, huile, etc.)
- "Velocity fields" simplifiés pour fluides
- Optimisation du moteur (chunking, dirty rectangles, rendu plus rapide)
- Sauvegarde/chargement des scènes
- Packaging (jlink / jpackage) pour distribuer l'app
