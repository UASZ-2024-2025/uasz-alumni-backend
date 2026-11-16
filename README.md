# Alumni UASZ

Plateforme Alumni UASZ : architecture microservices et approche **Contract First**. Ce dépôt regroupe tout le backend (services, contrats, infrastructure) ; le frontend est dans un dépôt séparé ([Alumni-UASZ-frontend](https://github.com/UASZ-2024-2025/Alumni-UASZ-frontend)).

## Architecture du projet

| Dossier | Port | Rôle |
|---|---:|---|
| `alumni-uasz-infra` | — | Namespace, Ingress, cluster Kind local et scripts |
| `user-service` | 8081 | Utilisateurs, authentification et rôles (émet les JWT) |
| `alumni-student-company-service` | 8082 | Étudiants, CV, alumni, entreprises et promotions |
| `job-offer-service` | 8083 | Offres, candidatures et propositions |
| `analytics-matching-service` | 8084 | Matching, tableau de bord et rapports |
| `notification-messaging-service` | 8085 | Messagerie privée, groupes et notifications |
| `contracts` | — | Contrats OpenAPI (source de vérité) |
| `spis` | — | Interfaces et modèles d'API générés depuis les contrats |

## Documentation d'architecture

Voir **[`docs/`](docs/README.md)** :

- [Justification de l'architecture](docs/architecture.md) : exigences, monolithe modulaire vs microservices, stack, composants et interfaces
- Diagrammes UML : [composants](docs/diagrams/composants.md), [packages](docs/diagrams/packages.md), [déploiement](docs/diagrams/deploiement.md), [classes par service](docs/diagrams/classes.md)
- Décisions d'architecture : [ADR](docs/adr/README.md)
- Compléments : [cas d'utilisation](docs/diagrams/cas-utilisation.md), [séquences](docs/diagrams/sequences.md)

## Première installation

Avant de démarrer les services, il faut installer les dépendances et générer le secret JWT partagé.

```bash
# 1. Configurer le user-service et générer le secret JWT partagé
cd user-service
make setup
cd ..

# 2. Configurer les autres microservices
for s in alumni-student-company job-offer analytics-matching notification-messaging; do
  (cd "$s-service" && make setup)
done
```

## Lancer un microservice

Depuis le dossier du microservice concerné :

```bash
# si vous etes encore en possession de l'ancien contenu de .env sans mailpit et les autres variables
# il faudra vous en assurer en verifiant votre fichier .env dans user-service
# si le fichier est complet alors vous pouvez sauter cette etape, sinon faites-le.
cd user-service
make env-complete


cd chemin_du_microservice
make env-complete  # si vous etes encore en possession de l'ancien contenu de .env sans mailpit et les autres variables
make up
```

Pour arrêter le microservice :

```bash
make down
```

## Ports des microservices

| Microservice | URL locale |
|---|---|
| User Service | `http://localhost:8081` |
| Alumni Student Company Service | `http://localhost:8082` |
| Job Offer Service | `http://localhost:8083` |
| Analytics Matching Service | `http://localhost:8084` |
| Notification Messaging Service | `http://localhost:8085` |

## Commandes SonarQube

Le projet fournit un script pour gérer SonarQube et analyser la qualité du code.

```bash
# Démarrer SonarQube et sa base de données
./sonar/sonar.sh up

# Première initialisation : configurer le mot de passe administrateur
# et générer le token d'analyse
./sonar/sonar.sh init

# Analyser les cinq microservices
./sonar/sonar.sh scan

# Analyser un seul microservice
./sonar/sonar.sh scan user-service

# Ouvrir le tableau de bord SonarQube
./sonar/sonar.sh open
```

Tableau de bord local : `http://localhost:9000`

**Connexion SonarQube :**
- Login par défaut : `admin`
- Mot de passe : à définir lors de la première initialisation.

Ne jamais enregistrer de mot de passe ou de token directement dans le dépôt Git.

## CI/CD

Les workflows GitHub Actions se trouvent dans :

```text
.github/workflows/
```

La CI/CD est configurée pour traiter uniquement les microservices concernés par les modifications.

### CI des Pull Requests (`ci.yml`, `pr-title.yml`)

Déclenchée sur chaque PR vers `develop` et `main` (et sur chaque push sur ces branches) :

| Job | Rôle |
|---|---|
| Lint des contrats OpenAPI | `redocly lint contracts/*.yaml` à chaque run ; une erreur bloque, les avertissements non |
| Generation des SPIs | régénère les SPIs (sans publier) si un contrat ou un `pom.xml` de `spis/` change |
| Build et tests (`<service>`) | `./mvnw verify` + build de l'image Docker, uniquement pour les services modifiés |
| CI success | récapitulatif : échoue si l'un des jobs précédents a échoué |
| Titre de PR (Conventional Commits) | le titre doit suivre `type(scope): description` |

Types de titre acceptés : `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.

Vérifier les contrats en local avant de pousser :

```bash
npx --yes @redocly/cli@1 lint contracts/*.yaml
```

Un endpoint public (sans jeton) doit le déclarer explicitement avec `security: []` ; sinon le lint échoue.

### Règles de contribution (actives sur `develop` et `main`)

Ces règles sont imposées par GitHub (ruleset `protect-main-develop`) : le bouton de fusion reste bloqué tant qu'elles ne sont pas toutes respectées.

| Règle | Détail |
|---|---|
| Pull Request obligatoire | Jamais de push direct sur `develop` ni `main` ; une branche par tâche (`feature/AL-XX-...`, `fix/...`, `docs/...`) |
| 1 approbation | Par un autre membre de l'équipe ; un nouveau push annule l'approbation |
| Conversations résolues | Tous les commentaires de relecture doivent être résolus |
| Checks obligatoires | `CI success` (lint, build, tests) et `Titre de PR (Conventional Commits)` doivent être verts |
| Branche à jour | La PR doit contenir le dernier état de `develop` (bouton *Update branch*) |
| Squash merge uniquement | Toute la PR devient un seul commit, dont le message est le **titre de la PR** |

### Format du titre de PR

```text
type(scope): description courte (AL-XX)
```

- `type` : `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert` ;
- `scope` (facultatif) : le service ou la partie concernée, ex. `user-service`, `job-offer`, `contracts`, `deploy` ;
- la référence du ticket Jira à la fin, entre parenthèses, quand il y en a une.

| Correct | Refusé (et pourquoi) |
|---|---|
| `feat(user-service): inscription et confirmation d'email (AL-18)` | `Feature/al 18 inscription` (nom de branche, pas de type) |
| `fix(job-offer): statut 409 sur une candidature en double` | `AL-48: écrans profils` (le ticket n'est pas un type) |
| `docs: guide de démarrage` | `Expériences des Alumni` (pas de type) |

Si le check du titre est rouge, il suffit de **modifier le titre de la PR** : le contrôle se relance tout seul, sans nouveau commit.

## SPIs — Contract First

Les interfaces et modèles d'API sont générés à partir des contrats OpenAPI présents dans :

```text
contracts/*.yaml
```

Ces contrats constituent la source de vérité de l'architecture.

Les SPIs générés sont publiés dans :

```text
spis/repo/
```

Les microservices utilisent ce dépôt Maven versionné dans Git.

### Consommer les SPIs

Pour récupérer les dernières modifications :

```bash
git pull
```

Puis compiler le microservice concerné :

```bash
cd <service>
./mvnw package
```

### Modifier un contrat via Pull Request

Pour modifier un contrat OpenAPI, suivre les étapes suivantes :

1. Modifier le fichier `contracts/<service>.yaml`.
2. Mettre à jour `contracts/CHANGELOG.md`.
3. Incrémenter la version Maven des SPIs :

```bash
cd spis
./mvnw -B -q versions:set -DnewVersion=1.1.0 -DgenerateBackupPoms=false
cd ..
```

4. Aligner la propriété `<alumni.spi.version>` dans le `pom.xml` des microservices concernés.
5. Générer et publier les SPIs :

```bash
./spis/build.sh <service>
```

Pour générer tous les SPIs :

```bash
./spis/build.sh
```

6. Commiter les modifications des dossiers `contracts/`, `spis/` et des fichiers `pom.xml` concernés.
7. Ouvrir une Pull Request pour faire valider les changements.

**Important :** toujours incrémenter la version des SPIs. Maven ne revalide pas automatiquement une version déjà résolue lorsque la politique de mise à jour est configurée sur `never`.

## Bonnes pratiques

- Respecter l'approche **Contract First**.
- Mettre à jour les contrats OpenAPI avant de modifier les implémentations concernées.
- Garder les versions des SPIs synchronisées entre les services.
- Ne jamais commiter de secrets, mots de passe ou tokens.
- Tester les microservices avant chaque Pull Request.
- Utiliser des branches dédiées pour chaque fonctionnalité.
- Faire valider les changements via Pull Request avant leur intégration dans `develop`.
