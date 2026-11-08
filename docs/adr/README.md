# Architecture Decision Records (ADR)

Un ADR consigne **une** décision d'architecture importante : son contexte, les options étudiées, la
décision et ses conséquences. Il permet à un nouveau membre (ou au jury) de comprendre **pourquoi** le
système est ainsi, pas seulement **comment** il est fait.

## Règles

- Un fichier par décision : `NNNN-titre-court.md`, numéroté dans l'ordre chronologique.
- Un ADR **accepté n'est jamais réécrit**. Si la décision change, on crée un nouvel ADR qui le
  **remplace** (`Remplacé par ADR-00XX`), et on met à jour le statut de l'ancien.
- Statuts : `Proposé` → `Accepté` → (`Déprécié` | `Remplacé par ADR-XXXX`).
- Toute création ou modification passe par une Pull Request relue par l'équipe.
- Modèle : [`0000-modele.md`](0000-modele.md).

## Index

| N° | Décision | Statut | Date |
| --- | --- | --- | --- |
| [0001](0001-architecture-microservices.md) | Architecture en microservices plutôt qu'un monolithe modulaire | Accepté | 2026-11-01 |
| [0002](0002-contract-first-openapi.md) | Approche Contract First avec OpenAPI et SPIs générés | Accepté | 2026-11-01 |
| [0003](0003-monorepo.md) | Monorepo unique pour les services, les contrats et l'infrastructure | Accepté | 2026-11-01 |
| [0004](0004-base-de-donnees-par-service.md) | Une base PostgreSQL par service, schéma géré par Flyway | Accepté | 2026-11-01 |
| [0005](0005-authentification-jwt.md) | Authentification stateless par JWT signé avec un secret partagé (HS256) | Accepté, à réévaluer | 2026-11-01 |
| [0006](0006-communication-rest-synchrone.md) | Communication entre services en REST (pas de broker en v1), avec timeouts et retry | Accepté, à réévaluer | 2026-11-07 |
| [0007](0007-distribution-spis-depot-maven-git.md) | Distribution des SPIs par un dépôt Maven versionné dans Git | Accepté | 2026-11-01 |
| [0008](0008-deploiement-docker-kubernetes.md) | Conteneurisation Docker et déploiement Kubernetes (kustomize, kind en local) | Accepté | 2026-11-01 |
| [0009](0009-stack-java-spring-boot.md) | Java 21 et Spring Boot comme stack backend commune | Accepté | 2026-11-01 |
| [0010](0010-jeton-de-service.md) | Authentification des appels inter-services par jeton de service | Accepté | 2026-11-07 |
