# ADR-0008 : Conteneurisation Docker et déploiement Kubernetes (kustomize, kind en local)

- **Statut** : Accepté
- **Date** : 2026-11-01
- **Décideurs** : équipe devops
- **Ticket** : AL-2

## Contexte

Cinq services et leurs bases doivent tourner de façon identique sur les postes des développeurs, en CI
et en production. Le cahier des besoins exige de la disponibilité et de l'évolutivité, et le sujet impose
Docker et la CI/CD.

## Options étudiées

1. **docker-compose seul**, y compris en production : simple, mais pas de rolling update, pas de
   redémarrage sur sonde ni de répartition sur plusieurs nœuds.
2. **Kubernetes** : réplicas, rolling updates, sondes de santé, gestion des secrets, ingress. Plus
   complexe, mais c'est le standard du marché.
3. **PaaS** (Heroku, Render…) : très simple, mais dépendance à un fournisseur, coût, et peu de valeur
   pédagogique.

Pour gérer les manifestes : Helm (moteur de templates puissant mais verbeux) ou kustomize (intégré à
`kubectl`, overlays simples).

## Décision

- **Docker** : un `docker/service.Dockerfile` unique, multi-étapes, paramétré par `SERVICE`, avec un
  JRE 21 et un utilisateur non root (UID 10001).
- **Développement local** : `docker-compose` par service (le service et son PostgreSQL), piloté par
  `make`.
- **Orchestration** : Kubernetes avec **kustomize**. Chaque service a une `base` et des overlays `dev` et
  `prod`. En local, on utilise **kind**, avec un ingress NGINX qui route par préfixe.
- **Par défaut** : 2 réplicas, `RollingUpdate` avec `maxUnavailable: 0`, sondes liveness/readiness
  Actuator, requêtes et limites de ressources.
- **CD** : les images sont publiées sur GHCR, puis on fait `kubectl apply -k`.

## Conséquences

- **Positives** : environnements identiques du poste de développement à la production ; déploiement sans
  interruption ; une seule image à maintenir.
- **Négatives / risques**
  - Il faut apprendre Kubernetes, et kind consomme des ressources sur le poste.
  - En dev, PostgreSQL tourne en `StatefulSet` dans le cluster, ce qui ne convient pas tel quel à la
    production (sauvegardes, haute disponibilité).
- **Suivi** : choisir l'hébergement de production (cluster managé et base managée) dans un ADR dédié.
