# Alumni UASZ

Plateforme Alumni UASZ : architecture microservices, approche **Contract First** et organisation en **monorepo**.

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

## Lancer le projet

Prérequis : Docker (Compose v2.17+) et Git Bash sous Windows.

```bash
cd deploy
./setup.sh                  # 1re fois : crée .env (mots de passe, secret JWT, Brevo facultatif)
docker compose up -d --build
```

| Service                    | URL                                 |
| -------------------------- | ----------------------------------- |
| Frontend                   | http://localhost:4200               |
| Microservices              | http://localhost:8081 à 8085 (`/v1`) |
| Emails de test (Mailpit)   | http://localhost:8025               |

- Une base PostgreSQL par service (non exposée sur l'hôte, volumes conservés entre deux lancements).
- Emails : Mailpit par défaut. Pour de vrais envois via **Brevo**, renseigner `MAIL_*` dans `deploy/.env` (voir `.env.example`), puis `docker compose up -d user-service`.
- Le frontend est construit depuis son dépôt Git (`FRONTEND_CONTEXT`, branche `develop`), ou depuis un chemin local.
- `docker compose logs -f <service>` : logs ; `docker compose down` : arrêt ; `docker compose down -v` : arrêt + suppression des bases.
- Ne pas lancer en même temps les `make up` des services : ils utilisent les mêmes ports.

## Développer un microservice

### Première installation

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

### Lancer un microservice

Depuis le dossier du microservice concerné :

```bash
cd chemin_du_microservice
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
