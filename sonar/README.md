# SonarQube : configuration partagée

Tout est versionné dans ce dossier : un `git pull` donne à chacun la même configuration.

| Fichier | Rôle |
|---|---|
| `docker-compose.yml` | SonarQube Community + PostgreSQL en local |
| `sonar.properties` | Exclusions et paramètres communs aux 5 services |
| `sonar.sh` | Commande unique (up, init, scan, down...) |
| `.sonar-token` | Token personnel généré par `init` (**ignoré par git**) |

## Démarrage

```bash
git pull
./sonar/sonar.sh up       # 1 à 2 min au premier lancement
./sonar/sonar.sh init     # une seule fois : mot de passe admin + token
./sonar/sonar.sh scan     # les 5 services (ou : scan user-service)
./sonar/sonar.sh open     # http://localhost:9000
```

Chaque service apparaît comme un projet `alumni-uasz_<service>`.

## Modifier la configuration pour toute l'équipe

Éditer `sonar/sonar.properties`, committer, pousser. Les autres récupèrent le changement au prochain `git pull`.

## Serveur SonarQube commun (optionnel)

Pas besoin de `up` ni `init` : exporter simplement

```bash
export SONAR_HOST_URL=https://sonar.exemple.sn
export SONAR_TOKEN=<token personnel>
./sonar/sonar.sh scan
```

Pour la CI, définir la variable de dépôt `SONAR_HOST_URL` et le secret `SONAR_TOKEN` : le workflow `.github/workflows/sonar.yml` s'active alors tout seul.

## Couverture de tests

JaCoCo est appelé automatiquement. Tant qu'un service n'a pas de tests, la couverture reste à 0 %.
Pour écrire des tests, ajouter `spring-boot-starter-test` (scope test) dans le `pom.xml` du service.

## Dépannage

- **Le conteneur redémarre en boucle (Linux)** : `sudo sysctl -w vm.max_map_count=262144`.
- **Mot de passe admin déjà changé** : exporter `SONAR_ADMIN_PASSWORD`, ou `./sonar/sonar.sh reset`.
- **Port 9000 occupé** : `SONAR_PORT=9001 ./sonar/sonar.sh up` (réutiliser la même variable pour `init` et `scan`).
