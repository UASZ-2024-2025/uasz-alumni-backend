# alumni-student-company-service

Microservice **alumni-student-company** de la plateforme Alumni UASZ — Spring Boot 4.1.0, Java 21,
PostgreSQL, Flyway, Docker, Kubernetes. Approche **contract first** : l'API est définie par
`contracts/alumni-student-company-service.yaml` (OpenAPI 3.0.3) ; les endpoints implémentent les interfaces générées (SPI).

## Structure

```
src/main/java/com/uasz/alumni/alumnistudentcompany
├── config/       configuration Spring (sécurité, beans)
├── controller/   implémentations des interfaces d'API générées
├── dto/          objets de transfert propres au service
├── exceptions/   erreurs métier
├── jwt/          validation JWT
├── model/        entités JPA, enums
├── repository/   Spring Data JPA
├── security/     utilisateur courant, rôles
├── service/      logique métier
└── utils/        utilitaires
k8s/base           Deployment, Service, ConfigMap (indépendant de l'environnement)
k8s/overlays/dev   PostgreSQL + secrets de dev + image locale
k8s/overlays/prod  image GHCR, configuration de production (secrets hors dépôt)
```

## Démarrage rapide

| Besoin | Commande |
|---|---|
| Première installation | `make setup` |
| Run local (PostgreSQL docker + app) | `make run` |
| Stack complète Docker | `make up` puis `curl localhost:8082/actuator/health` |
| Kubernetes (kind) | `make k8s-secrets` puis `../alumni-uasz-infra/scripts/deploy-service.sh alumni-student-company-service` |

## Conventions

- Port : `8082` ; base PostgreSQL dédiée `alumni_student_company_db` (port hôte `5433`).
- Préfixe d'API = variable `API_PREFIX` : `/v1` en local, `/alumni-student-company-service/v1` en K8s (URL de prod des contrats).
- Schéma géré **uniquement** par Flyway (`V<n>__<desc>.sql`, jamais de modification d'une migration appliquée).
- 12-factor : aucune valeur par défaut dans les yml, aucun secret dans le dépôt (`.env` ignoré par git).
- JWT : même `JWT_SECRET` et même `JWT_ISSUER` dans tous les services.
- Probes K8s : `/actuator/health/liveness` et `/actuator/health/readiness`.
