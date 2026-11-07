# Alumni UASZ

Plateforme Alumni UASZ : architecture microservices, approche **contract first**, **monorepo**.

| Dossier                          | Port | Rôle                                                 |
| -------------------------------- | ---- | ---------------------------------------------------- |
| `alumni-uasz-infra`              |      | namespace, ingress, cluster kind local, scripts      |
| `user-service`                   | 8081 | utilisateurs, authentification, rôles (émet les JWT) |
| `alumni-student-company-service` | 8082 | étudiants, CV, alumni, entreprises, promotions       |
| `job-offer-service`              | 8083 | offres, candidatures, propositions                   |
| `analytics-matching-service`     | 8084 | matching, tableau de bord, rapports                  |
| `notification-messaging-service` | 8085 | messagerie privée, groupes, notifications            |
| `contracts`                      |      | contrats OpenAPI (source de vérité)                  |

## Documentation d'architecture

Voir **[`docs/`](docs/README.md)** :

- [Justification de l'architecture](docs/architecture.md) : exigences, monolithe modulaire vs microservices, stack, composants et interfaces
- Diagrammes UML : [composants](docs/diagrams/composants.md), [packages](docs/diagrams/packages.md), [déploiement](docs/diagrams/deploiement.md), [classes par service](docs/diagrams/classes.md)
- Décisions d'architecture : [ADR](docs/adr/README.md)
- Compléments : [cas d'utilisation](docs/diagrams/cas-utilisation.md), [séquences](docs/diagrams/sequences.md)

## Première installation

```bash
(cd user-service && make setup)          # d'abord : génère le secret JWT partagé
for s in alumni-student-company job-offer analytics-matching notification-messaging; do
  (cd "$s-service" && make setup)
done
```

## Lancer un microservice

```bash
cd chemin_du_microservice
make up 
```

## Commandes SonarQube

```bash
./sonar/sonar.sh up              # démarre SonarQube + sa base (http://localhost:9000)
./sonar/sonar.sh init            # 1re fois : change le mot de passe admin, génère le token
./sonar/sonar.sh scan            # analyse les 5 services
./sonar/sonar.sh scan user-service   # un seul service
./sonar/sonar.sh open            # ouvre le tableau de bord
```

Par defaut en local pour connexion sonarqube:
Login : admin
Mot de passe : Alumni-Uasz-2026!

## CI/CD

`.github/workflows/` (racine) : CI et CD ne traitent que les microservices modifiés.

## SPIs (contract-first)

Les interfaces et modeles d'API sont generes depuis `contracts/*.yaml` et **publies dans `spis/repo/`**
(depot Maven versionne dans Git). Les microservices le lisent via `../spis/repo` : un `git pull` suffit.

### Consommer les SPIs (tous)

```bash
git pull
cd <service> && ./mvnw package
```

### Modifier un contrat (via Pull Request)

1. Modifier `contracts/<service>.yaml` et `contracts/CHANGELOG.md`.
2. Incrementer la version : `(cd spis && ./mvnw -B -q versions:set -DnewVersion=1.1.0 -DgenerateBackupPoms=false)`
3. Aligner `<alumni.spi.version>` dans le `pom.xml` des services concernes.
4. `./spis/build.sh <service>` (ou sans argument pour tous), puis committer `contracts/`, `spis/` et les `pom.xml`.

Toujours changer la version : Maven ne revalide jamais une version deja resolue (`updatePolicy=never`).
