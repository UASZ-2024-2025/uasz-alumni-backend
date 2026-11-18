# notification-messaging-service

Microservice **notification-messaging** de la plateforme Alumni UASZ — Spring Boot 4.1.0, Java 21,
PostgreSQL, Flyway, Docker, Kubernetes. Approche **contract first** : l'API est définie par
`contracts/notification-messaging-service.yaml` (OpenAPI 3.0.3) ; les endpoints implémentent les interfaces générées (SPI).

## Structure

```
src/main/java/com/uasz/alumni/notificationmessaging
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
| Stack complète Docker | `make up` puis `curl localhost:8085/actuator/health` |
| Kubernetes (kind) | `make k8s-secrets` puis `../alumni-uasz-infra/scripts/deploy-service.sh notification-messaging-service` |

## Conventions

- Port : `8085` ; base PostgreSQL dédiée `notification_messaging_db` (port hôte `5436`).
- Préfixe d'API = variable `API_PREFIX` : `/v1` en local, `/notification-messaging-service/v1` en K8s (URL de prod des contrats).
- Schéma géré **uniquement** par Flyway (`V<n>__<desc>.sql`, jamais de modification d'une migration appliquée).
- 12-factor : aucune valeur par défaut dans les yml, aucun secret dans le dépôt (`.env` ignoré par git).
- JWT : même `JWT_SECRET` et même `JWT_ISSUER` dans tous les services.
- Probes K8s : `/actuator/health/liveness` et `/actuator/health/readiness`.

## Groupes de discussion (contrat 1.1.0)

Implémentés dans `service/GroupService` (règles) et `controller/GroupMessagingController` (routes du SPI).
Réservés aux rôles ETUDIANT, ALUMNI, ENSEIGNANT et ADMINISTRATEUR : ENTREPRISE reçoit un 403.
Un non-membre reçoit un 403 sur toutes les routes d'un groupe. On n'entre dans un groupe qu'en y étant ajouté.

### Qui peut faire quoi

| Action | Propriétaire (OWNER) | Admin (ADMIN) | Membre (MEMBER) |
|---|---|---|---|
| Lire le fil, marquer comme lu, préférences personnelles | oui | oui | oui |
| Envoyer un message | oui | oui | oui, sauf si `onlyAdminsCanSend` |
| Modifier nom et description | oui | oui | si `onlyAdminsCanEditInfo` est désactivé |
| Ajouter des participants | oui | oui | si `onlyAdminsCanAddMembers` est désactivé |
| Modifier les réglages du groupe | oui | oui | non |
| Nommer ou retirer un ADMIN | oui | non | non |
| Retirer un participant | tout le monde | ADMIN et MEMBER, jamais le propriétaire | non |
| Supprimer un message pour tous | oui | oui | ses propres messages |
| Quitter le groupe | oui (passation) | oui | oui |
| Supprimer le groupe | oui | non | non |

- L'ADMINISTRATEUR de la plateforme peut supprimer n'importe quel groupe.
- Réglages par défaut : envoi ouvert à tous ; modification des infos et ajout de participants réservés aux administrateurs.
- Quand le propriétaire part, la propriété passe à l'ADMIN le plus ancien, à défaut au membre le plus ancien.
  Le groupe est supprimé avec son dernier membre.
- Chaque événement (création, ajout, départ, retrait, rôle, réglage, renommage, passation) ajoute au fil un message `SYSTEM`
  en français. Ces messages ne comptent pas dans les non-lus et ne peuvent pas être supprimés.
- Un message supprimé reste dans le fil avec `deleted: true` et sans contenu.
- Messages : contenu non vide, 4000 caractères au plus (sinon 400 `INVALID_MESSAGE`).
  Pagination : page 0 = messages les plus récents, et chaque page est rendue dans l'ordre chronologique (`size` de 1 à 100).
- Préférences personnelles (`pinned`, `archived`, sourdine de 8 h, 1 semaine ou sans limite) : propres à chaque participant.
  `listGroups` renvoie les groupes épinglés d'abord, puis les plus récents.

### Données (migration `V2__groupes_de_discussion.sql`)

| Table | Contenu |
|---|---|
| `chat_users` | Annuaire local des noms et rôles, recopiés du JWT (claim `name`), voir [ADR-0010](../docs/adr/0010-annuaire-local-noms-messagerie.md) |
| `chat_groups` | Groupe, propriétaire, les trois réglages, dernière activité |
| `chat_group_members` | Rôle dans le groupe, dernière lecture, préférences personnelles ; un seul OWNER par groupe (index unique partiel) |
| `chat_group_messages` | Messages TEXT et SYSTEM ; `seq` départage deux messages de même horodatage |

Les modifications d'un groupe verrouillent sa ligne (`SELECT … FOR UPDATE`). Deux actions simultanées, comme deux départs,
ne peuvent donc pas laisser un groupe sans propriétaire.

### Tests

- `GroupServiceIntegrationTest` : règles des groupes contre un vrai PostgreSQL embarqué (`io.zonky.test:embedded-postgres`,
  sans Docker). Les migrations Flyway et la validation du schéma JPA sont incluses.
- `GroupMessagingControllerTest` : sécurité des routes (401, 403 pour ENTREPRISE), validation du contrat et format des erreurs.
