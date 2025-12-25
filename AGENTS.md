# AGENTS.MD — ParticuleToy 🧪✨

Ce fichier décrit les règles du projet **ParticuleToy** et sert de guide de collaboration pour :
- les humains (développeurs, testeurs, chef de projet),
- et les agents IA (Codex / assistants de code / générateurs de patchs).

L’objectif est que n’importe quel agent puisse contribuer **sans casser l’architecture**, **sans introduire de dette technique inutile**, et **sans risque de violation de droits d’auteur**.

---

## 1) Vision du projet 🎮

**ParticuleToy** est un jeu/simulateur 2D de matière (type “sandbox”) inspiré *dans l’idée* par The Powder Toy :
- On place des matériaux (sable, eau, etc.)
- On simule leur comportement (gravité, interactions, combustion, etc.)
- On observe les résultats en temps réel

⚠️ Important : on s’inspire du **concept**, pas du code ni des assets. Aucune copie.

---

## 2) Contraintes légales / Copyright / Licences ⚖️🚫

### 2.1 Interdictions strictes
- ❌ Ne jamais copier/coller du code provenant de The Powder Toy (ou tout autre projet) même “juste un morceau”.
- ❌ Ne jamais importer des assets (sprites, sons, icônes) sans licence claire.
- ❌ Ne pas “traduire” ou “réécrire” du code copié : c’est pareil juridiquement.

### 2.2 Ce qui est autorisé
- ✅ Utiliser des bibliothèques open-source **avec licence compatible** (MIT / BSD / Apache-2.0, etc.).
- ✅ Lire des articles / documents pour comprendre des concepts (automates cellulaires, grid-based simulation…), puis implémenter **notre propre solution**.
- ✅ Définir un comportement similaire (ex : sable tombe) tant que l’implémentation est originale.

### 2.3 Politique de dépendances
Avant d’ajouter une dépendance :
1. Vérifier la licence (et sa compatibilité si le projet est vendu plus tard).
2. Ajouter une entrée dans `THIRD_PARTY_NOTICES.md` (à créer si absent).
3. Éviter les dépendances “lourdes” non essentielles.

---

## 3) Stack technique 🧱

- Langage : **Java**
- Build : **Maven**
- IDE : **IntelliJ IDEA**
- UI Desktop : **JavaFX**
- Tests : **JUnit 5**
- Version Java (baseline) : **Java 17 (LTS)** ✅  
  (Possible de passer à Java 21 plus tard, mais changement global et intentionnel.)

Pourquoi Java 17 :
- stable, largement supporté,
- bon compromis pour packaging et compatibilité.

---

## 4) Structure du projet (Maven multi-modules) 🗂️

Le projet doit rester modulaire pour séparer simulation et interface.

Arborescence cible :

- `pom.xml` (parent aggregator)
- `particuletoy-core/`
    - moteur de simulation (aucune dépendance UI)
- `particuletoy-desktop/`
    - application JavaFX (rendu + input), dépend de `particuletoy-core`

Règle d’or :
- Tout ce qui touche à la **physique / simulation** doit être dans `core`
- Tout ce qui touche à la **fenêtre / UI / événements souris** doit être dans `desktop`

---

## 5) Principes d’architecture 🧠

### 5.1 Objectifs techniques
- Simulation en temps réel (cible : 60 FPS sur une grille raisonnable).
- Comportements simples mais extensibles (ajout d’éléments facile).
- Performance : éviter les allocations en boucle, éviter les objets par cellule.

### 5.2 Représentation de la grille
Le modèle de base (MVP) est une **grille 2D** de cellules :
- chaque cellule contient un “type” (EMPTY, WALL, SAND, WATER…)
- indexation row-major : `index = x + y * width`
- repère : origine (0,0) en haut-gauche ; y augmente vers le bas (gravité naturelle)

### 5.3 Données et performance (règles)
- ✅ Préférer `byte[]` / `short[]` / `int[]` plutôt que des objets.
- ✅ Préférer “SoA” (Structure of Arrays) si on ajoute des propriétés (température, vx, vy…).
- ✅ Aucune allocation dans `step()` (ou quasi zéro).
- ✅ Pas de streams Java dans les hot paths (trop coûteux).
- ✅ Les couleurs peuvent être pré-calculées dans un tableau `int[]`.

---

## 6) Règles de simulation (MVP) 🧪

### 6.1 Boucle de simulation
La simulation se fait en “ticks” (pas de temps fixe).
- On fait typiquement 60 ticks/s.
- Si le rendu est plus lent, on peut limiter le catch-up (ex : max 5 ticks par frame).

### 6.2 Ordre d’itération (gravité)
Pour les matériaux soumis à la gravité (sable, eau) :
- Itérer de bas en haut (y = height-2 vers 0) pour éviter qu’une particule retombe plusieurs fois par tick.
- Alterner le sens gauche→droite / droite→gauche aléatoirement par ligne ou par frame pour éviter les biais.

### 6.3 Anti double-mouvement
Les particules ne doivent pas “bouger deux fois” dans le même tick.
Méthode recommandée :
- `int[] movedStamp` + `int frameId` (stamp technique)
- Une cellule est “déjà traitée/mue” si `movedStamp[idx] == frameId`
- On marque source et destination lors d’un swap

### 6.4 Interactions MVP (exemples)
- **SAND** :
    1) tombe si dessous est EMPTY
    2) sinon, tente bas-gauche / bas-droite si EMPTY
    3) si dessous est WATER, peut échanger (sable coule)
- **WATER** :
    1) tombe si dessous EMPTY
    2) sinon, tente bas-gauche / bas-droite
    3) sinon, s’étale à gauche/droite (aléatoire) si EMPTY
- **WALL** :
    - immobile
- **EMPTY** :
    - rien

⚠️ Le comportement précis pourra évoluer, mais l’implémentation doit rester claire et testable.

### 6.5 Déterminisme (important pour tests)
Le moteur `core` doit être testable :
- permettre l’injection d’une seed RNG (ex : `new SplittableRandom(seed)`)
- éviter de dépendre directement du temps système dans `core` (sauf par défaut)

---

## 7) UI Desktop (JavaFX) 🖥️🖱️

### 7.1 Rendu
Objectif : rendu pixel-perfect rapide.
Recommandation :
- Utiliser `WritableImage` + `PixelBuffer<IntBuffer>` (ou `PixelWriter` si nécessaire)
- Désactiver le smoothing (pas de flou) :
    - `imageView.setSmooth(false)`
- Utiliser un facteur de scale (ex : x2, x3) pour zoom simple.

### 7.2 Input utilisateur
- Clic gauche : placer le matériau sélectionné
- Clic droit : effacer (mettre EMPTY) ou placer WALL (à définir par UI)
- Drag : peinture continue
- Molette : changer le rayon du pinceau
- Boutons UI : choisir matériau, Pause/Play, Clear, Step

Toutes les coordonnées UI doivent être converties en coordonnées grille :
- `(gridX, gridY) = (mouseX / scale, mouseY / scale)` si on maîtrise le scaling.

### 7.3 Séparation stricte
- `desktop` gère la souris/clavier et appelle des méthodes “propres” du `core` :
    - `paintCircle(x, y, radius, elementType)`
    - `step()`
    - `clear()`
    - `renderTo(buffer)`

Le `core` ne doit jamais importer JavaFX.

---

## 8) Conventions de code ✍️

### 8.1 Style général
- Indentation : 4 espaces
- Accolades : style Java standard
- Nommage :
    - packages : `com.particuletoy...`
    - classes : `PascalCase`
    - méthodes/variables : `camelCase`
    - constantes : `UPPER_SNAKE_CASE`

### 8.2 API “propre”
- Préférer des méthodes explicites :
    - `setCell(x,y,type)` plutôt que d’exposer les tableaux
- Mais on autorise des getters “low-level” si nécessaire pour performance, avec doc.

### 8.3 Gestion d’erreurs
- `core` : validation des paramètres (clamp si besoin pour peinture)
- pas d’exceptions en boucle de simulation (ça doit rester robuste)
- logs : minimal, pas dans le hot path

### 8.4 Documentation
Chaque classe “publique” du `core` doit avoir un JavaDoc court :
- rôle
- invariants (ex : width/height immuables)
- complexité si important

---

## 9) Tests & Qualité ✅

### 9.1 Tests unitaires (core)
Le module `core` doit avoir des tests JUnit 5 :
- tests de `paintCircle` (ne sort pas de la grille, remplit correctement)
- tests de mouvement simple (sable tombe)
- tests déterministes avec seed fixe

### 9.2 Tests manuels (desktop)
Checklist manuelle minimale :
- l’app démarre
- on peut placer sable/eau/mur
- pause/play marche
- clear marche
- pas de lag extrême sur une grille MVP

---

## 10) Build & commandes Maven 🛠️

Rappels (exemples) :
- Build complet :
    - `mvn clean package`
- Tests :
    - `mvn test`
- Lancer l’app JavaFX (module desktop) :
    - `mvn -pl particuletoy-desktop javafx:run`

Les agents doivent maintenir les POM propres :
- versions en properties
- pas de plugins inutiles
- pas de repo Maven custom douteux

---

## 11) Process de contribution (humains + agents IA) 🔄

### 11.1 Règle “petits changements”
- PR petites et ciblées (1 feature = 1 PR si possible)
- un refactor massif doit être justifié et découpé

### 11.2 Définition of Done (DoD)
Un changement est “Done” si :
- compile ✅
- tests passent ✅
- pas d’allocation évidente dans `step()` (si modif simulation) ✅
- pas de dépendance non validée ✅
- doc minimale mise à jour si nécessaire ✅

### 11.3 Convention de commits (recommandée)
Conventional Commits :
- `feat(core): ...`
- `feat(desktop): ...`
- `fix(core): ...`
- `chore: ...`

---

## 12) Rôles des agents IA 🤖

Quand un agent IA intervient, il doit se positionner implicitement dans un rôle :

1) Agent “Build/Infra”
- POM, structure modules, plugins, packaging

2) Agent “Simulation”
- data structures, rules, performance, déterminisme, tests

3) Agent “UI Desktop”
- JavaFX, rendu pixel, input, UX minimale

4) Agent “QA”
- tests unitaires, scénarios manuels, checks perf simples

Règle de coordination :
- si une modification touche à la fois `core` et `desktop`, faire le changement `core` d’abord, puis adapter `desktop`.

---

## 13) Règles anti-dette technique 🧯

- Pas de “quick hack” dans `core` : tout comportement doit être encapsulé.
- Pas de duplication de logique de mouvement : factoriser proprement.
- Pas de dépendances ajoutées “pour gagner du temps” sans justification.

---

## 14) Roadmap MVP (référence) 🗺️

MVP = “ça tourne et c’est fun à toucher” :
- grille + rendu pixel ✅
- brush + placement ✅
- 4 éléments : EMPTY/WALL/SAND/WATER ✅
- pause/play/clear ✅
- step stable, pas de crash ✅

Ensuite seulement :
- feu/fumée/gaz
- température/pression
- matériaux plus complexes
- sauvegarde/chargement
- optimisations chunk/multi-thread (avec précautions)

---

## 15) Checklist avant d’ajouter un nouvel élément 🔥🌊🧱

Avant d’ajouter “OIL”, “FIRE”, etc. :
- définir ses propriétés (couleur, densité, règles)
- ajouter la logique dans le `core` (pas dans UI)
- ajouter un test minimal si possible
- exposer le bouton/choix dans UI
- vérifier performance (pas d’alloc)

---

Fin du document.  
Si un agent a un doute : il choisit la solution la plus simple, maintenable, testable, et conforme à ce fichier ✅🙂
