# ADR-0009 : Java 21 et Spring Boot comme stack backend commune

- **Statut** : Accepté
- **Date** : 2026-11-01
- **Décideurs** : équipe back
- **Ticket** : AL-2

## Contexte

Les microservices permettent techniquement d'utiliser un langage différent par service. Mais l'équipe
est petite, les membres passent d'un service à l'autre, et l'outillage (Dockerfile, Sonar, CI, SPIs)
doit rester commun.

## Options étudiées

1. **Java 21 + Spring Boot** : écosystème complet (Web, Security, Data JPA, Validation, Actuator) ;
   enseigné dans le cursus ; très demandé en entreprise ; openapi-generator le supporte bien.
2. **Node.js (NestJS)** : rapide à démarrer, même langage que le front, mais typage et ORM moins mûrs pour
   un domaine transactionnel, et moins maîtrisé par l'équipe back.
3. **Plusieurs langages selon le service** (ex. Python pour le matching) : chacun utilise l'outil le plus
   adapté, mais l'outillage est multiplié et les membres deviennent moins interchangeables.

## Décision

**Java 21 (LTS) et Spring Boot 4.1** pour les 5 services, avec Maven (wrapper `mvnw`), Spring Security,
Data JPA, Validation, Actuator, Flyway et PostgreSQL. La structure de paquets est la même partout
(`controller`, `service`, `repository`, `model`, `dto`, `security`, `config`, `exceptions`).

## Conséquences

- **Positives** : un développeur passe d'un service à l'autre sans changer de repères ; outillage unique
  (un Dockerfile, une config Sonar) ; Java 21 est supporté longtemps.
- **Négatives / risques**
  - Une JVM consomme plus de mémoire par service (≈ 384–512 Mi par pod).
  - Spring Boot 4 est récent : certaines bibliothèques tierces peuvent ne pas encore être compatibles.
- **Suivi** : si le matching demande des traitements de données avancés (ML), un service Python dédié
  pourra être ajouté derrière le même mécanisme de contrat.
