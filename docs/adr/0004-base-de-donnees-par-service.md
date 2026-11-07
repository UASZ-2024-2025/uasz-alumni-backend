# ADR-0004 : Une base PostgreSQL par service, schéma géré par Flyway

- **Statut** : Accepté
- **Date** : 2026-11-01
- **Décideurs** : équipes back, devops
- **Ticket** : AL-2

## Contexte

Les microservices ([ADR-0001](0001-architecture-microservices.md)) n'apportent l'autonomie promise que si
chacun maîtrise ses données. Une base partagée crée un couplage caché : la modification d'une table par
une équipe casse le service d'une autre. Les données sont relationnelles (utilisateurs, offres,
candidatures, messages) et demandent de l'intégrité transactionnelle.

## Options étudiées

1. **Une base partagée par tous les services** : simple et permet les jointures, mais crée un couplage
   fort par le schéma et un point de défaillance unique.
2. **Un schéma par service dans une même instance** : isolation logique, moins de ressources, mais
   l'isolation repose sur la discipline et l'instance reste partagée.
3. **Une base par service** (*database per service*).

Pour le SGBD : PostgreSQL (open source, robuste, types riches comme `uuid`, `jsonb` et les tableaux),
MySQL, MongoDB (moins adapté à un modèle relationnel avec contraintes).

## Décision

- **Une base PostgreSQL 16 par service**. Seul le service propriétaire y accède.
- Les données d'un autre service sont **référencées par UUID** (`userId`, `companyId`, `offerId`…), sans
  clé étrangère entre bases.
- Les champs d'affichage nécessaires sont **dénormalisés** (`companyName`, `studentName`) pour éviter des
  appels réseau à chaque lecture.
- Le schéma est **versionné par Flyway** (`db/migration/V1__…`). On n'utilise jamais `ddl-auto: update` :
  le mode est `validate`. Une migration déjà appliquée n'est jamais modifiée.

## Conséquences

- **Positives** : les services évoluent indépendamment ; le schéma est reproductible dans chaque
  environnement ; les ressources sont dimensionnées par base.
- **Négatives / risques**
  - Pas de jointure ni de transaction entre services. Les rapports transverses passent par
    `analytics-matching-service`, qui agrège via les API.
  - Les données dénormalisées peuvent devenir obsolètes (une entreprise renommée, par exemple). Il faut un
    mécanisme de mise à jour : appel ou événement ([ADR-0006](0006-communication-rest-synchrone.md)).
  - La suppression d'un utilisateur (RGPD) doit être propagée à chaque service.
  - 5 bases à sauvegarder et à superviser.
- **Suivi** : en production, envisager une instance PostgreSQL managée avec une base par service, ce qui
  réduit le coût d'exploitation.
