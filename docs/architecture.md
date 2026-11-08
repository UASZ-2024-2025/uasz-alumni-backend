# Architecture logicielle : choix et justification

> Livrables n°5 et n°6 du sujet GL5121 (Architecture Logicielle).
> Ticket Jira : *Documentation d'architecture : stack, diagrammes UML et justification des choix* (epic AL-2).
> Les diagrammes UML sont dans [`diagrams/`](diagrams/) (index : [`README.md`](README.md)).
> Chaque décision est détaillée dans un ADR : [`adr/`](adr/README.md).

## Sommaire

1. [Contexte](#1-contexte)
2. [Exigences prises en compte](#2-exigences-prises-en-compte)
3. [Monolithe modulaire ou microservices](#3-monolithe-modulaire-ou-microservices)
4. [Stack technique retenue](#4-stack-technique-retenue)
5. [Composants logiciels et interfaces](#5-composants-logiciels-et-interfaces)
6. [Sécurité](#6-sécurité)
7. [Déploiement, CI/CD et qualité](#7-déploiement-cicd-et-qualité)
8. [Points ouverts](#8-points-ouverts)

---

## 1. Contexte

La plateforme **Alumni UASZ** met en relation les étudiants du Master Informatique de l'UASZ, les anciens
diplômés (alumni), les enseignants, les entreprises partenaires et l'administration. Elle doit permettre :

- la gestion des comptes et l'authentification ;
- la gestion des profils (étudiant, alumni), des CV, des entreprises et des promotions ;
- la publication d'offres d'emploi ou de stage, les candidatures et les propositions directes ;
- le matching automatique entre étudiants et offres, des statistiques et des rapports pour l'administration ;
- la messagerie privée, les groupes de discussion et les notifications.

Le backend sert une application front (équipe front) via des API REST.

## 2. Exigences prises en compte

> Le **cahier des besoins** (livrable n°1, ticket **AL-62**) fait référence pour le détail des exigences.
> Il sera déposé dans `docs/`. Cette section reprend sa structure (acteurs, exigences fonctionnelles par
> domaine, exigences non fonctionnelles, contraintes) et indique, pour chaque exigence, comment
> l'architecture y répond.

**Problème à résoudre** : aujourd'hui, les échanges entre étudiants, alumni, enseignants et entreprises
sont dispersés sur WhatsApp et les réseaux sociaux. Il n'existe ni annuaire fiable, ni suivi des offres
et des candidatures, ni indicateur d'insertion professionnelle.

### 2.1 Acteurs

| Acteur | Rôle technique (`RoleName`) | Remarque |
| --- | --- | --- |
| Étudiant | `ETUDIANT` | |
| Alumni | `ALUMNI` | |
| Enseignant | `ENSEIGNANT` | |
| Administrateur | `ADMINISTRATEUR` | |
| Entreprise partenaire | **absent** | Acteur du cahier des besoins, mais sans rôle dans le contrat `user-service` v1.0.0 (voir §8) |

### 2.2 Exigences fonctionnelles (par domaine)

| ID | Domaine | Exigence | Acteurs | Service |
| --- | --- | --- | --- | --- |
| EF-01 | Utilisateurs | Inscription, connexion, refresh/logout, confirmation email, mot de passe oublié | Tous | `user-service` |
| EF-02 | Utilisateurs | Gestion des utilisateurs, de leur statut (activation, suspension) et des rôles | Administrateur | `user-service` |
| EF-03 | Promotions | Promotions académiques et liste des diplômés | Administrateur, Enseignant | `alumni-student-company-service` |
| EF-04 | CV | Profil étudiant (spécialité, compétences, disponibilité) et CV structuré | Étudiant | `alumni-student-company-service` |
| EF-05 | Alumni | Profil alumni et parcours professionnel (expériences) | Alumni | `alumni-student-company-service` |
| EF-06 | Alumni | Recommandation d'un étudiant auprès d'une entreprise | Alumni, Enseignant | `alumni-student-company-service` |
| EF-07 | Entreprises | Annuaire et fiches des entreprises partenaires | Tous ; Administrateur et Entreprise en écriture | `alumni-student-company-service` |
| EF-08 | Offres | Publication, recherche, mise à jour et clôture d'offres d'emploi ou de stage | Alumni, Entreprise, Administrateur | `job-offer-service` |
| EF-09 | Offres | Candidature à une offre et suivi de son état | Étudiant ; recruteur pour le changement d'état | `job-offer-service` |
| EF-10 | Offres | Proposition directe d'une offre à un étudiant, puis acceptation ou refus | Alumni, Entreprise ; Étudiant | `job-offer-service` |
| EF-11 | Offres | Matching étudiant ↔ offre, avec score et compétences manquantes | Étudiant, recruteur | `analytics-matching-service` |
| EF-12 | Messagerie | Messagerie privée 1-à-1 | Tous | `notification-messaging-service` |
| EF-13 | Messagerie | Groupes de discussion (création, membres, messages) | Tous | `notification-messaging-service` |
| EF-14 | Messagerie | Notifications in-app, email et push (lues / non lues) | Tous | `notification-messaging-service` |
| EF-15 | Tableau de bord | Statistiques globales, rapport d'insertion, écart de compétences | Administrateur | `analytics-matching-service` |

### 2.3 Exigences non fonctionnelles

| ID | Exigence | Réponse architecturale |
| --- | --- | --- |
| ENF-01 | **Sécurité** : JWT, mots de passe hachés, RBAC | JWT signé (HMAC) émis par `user-service` et vérifié localement par chaque service ; mots de passe hachés avec **BCrypt**, jamais stockés ni journalisés en clair ; contrôle d'accès par rôle (`ETUDIANT`, `ALUMNI`, `ENSEIGNANT`, `ADMINISTRATEUR`) dans chaque service ; API stateless ; secrets hors du dépôt (voir §6) |
| ENF-02 | **Performance** | Pagination de toutes les listes (`page`, `size`) ; vérification du JWT sans appel réseau ; index sur les colonnes de recherche ; champs d'affichage dénormalisés (`companyName`, `studentName`) pour éviter des appels en cascade ; calculs lourds (matching, rapports) isolés dans un service dédié |
| ENF-03 | **Disponibilité** | 2 réplicas par service en Kubernetes, rolling update sans indisponibilité (`maxUnavailable: 0`), sondes liveness/readiness, arrêt gracieux ; une panne reste limitée à un service |
| ENF-04 | **Évolutivité** (montée en charge et fonctionnelle) | Services sans état, scalables indépendamment ; une base par service ; nouveaux domaines ajoutés comme nouveaux services, sans toucher aux autres |
| ENF-05 | **Maintenabilité** | Découpage par domaine métier ; même structure en couches dans chaque service ; contrats OpenAPI versionnés et code d'interface généré (SPI) ; SonarQube |
| ENF-06 | **Ergonomie** | Côté backend : API REST cohérentes (mêmes conventions, pagination et format d'erreur `ErrorResponse` uniques) et messages d'erreur explicites, pour que le front puisse offrir une interface simple. La conception de l'interface relève de l'équipe front |
| ENF-07 | **Compatibilité mobile** | API REST/JSON indépendantes du client, donc utilisables par le web responsive comme par une future application mobile ; JWT adapté aux clients mobiles (pas de cookie de session) ; canal de notification `PUSH` prévu dans le contrat ; réponses paginées et légères |
| ENF-08 | **RGPD / données personnelles** | Données personnelles cantonnées à leur service propriétaire et référencées ailleurs par UUID ; accès authentifié et filtré par rôle ; mots de passe hachés ; consentement via la confirmation de l'email ; droit de suppression avec `DELETE /users/{id}` ; HTTPS de bout en bout via l'ingress. **À compléter** : durée de conservation, export des données personnelles, suppression propagée aux autres services |
| ENF-09 | **Intégrité des données** | PostgreSQL transactionnel ; schéma versionné par Flyway ; `ddl-auto: validate` |
| ENF-10 | **Observabilité** (minimum) | Spring Boot Actuator (`health`, `info`) ; à compléter (logs centralisés, métriques) |

### 2.4 Contraintes

| Contrainte | Mise en œuvre dans le projet |
| --- | --- |
| **Contract First** | Contrats OpenAPI dans `contracts/` ; interfaces et modèles générés (`spis/`) et implémentés par les contrôleurs ; toute évolution passe par une PR avec un incrément de version |
| **Git Flow** | Branche d'intégration `develop`, branche de production `main`, branches `feature/AL-xx-...` par ticket Jira, fusion par Pull Request |
| **Docker** | `docker/service.Dockerfile` partagé, docker-compose par service pour le développement local |
| **CI/CD** | GitHub Actions : `ci.yml` (vérification des SPIs, build des services modifiés), `deploy.yml` (images GHCR, déploiement Kubernetes) |
| **SonarQube** | `sonar/` en local et `sonar.yml` en CI pour les 5 services |

## 3. Monolithe modulaire ou microservices

### 3.1 Les deux options

**Monolithe modulaire** : une seule application déployable, découpée en modules internes aux frontières
nettes (un module par domaine), qui partagent un processus et en général une base de données.

**Microservices** : plusieurs applications déployées indépendamment, chacune propriétaire d'un domaine et de
ses données. Elles communiquent par le réseau à travers des contrats d'API.

### 3.2 Comparaison

| Critère | Monolithe modulaire | Microservices | Avantage |
| --- | --- | --- | --- |
| Simplicité de développement initial | Un seul projet, appels de méthode, une transaction | Plusieurs projets, appels réseau, cohérence à gérer entre services | Monolithe |
| Déploiement | Un seul artefact : simple, mais tout est redéployé à chaque changement | Déploiement indépendant de chaque service | Microservices |
| Scalabilité | Toute l'application est dupliquée | Seul le service chargé est dupliqué (ex. messagerie) | Microservices |
| Isolation des pannes | Une fuite mémoire ou un crash touche tout le système | Une panne reste contenue dans un service | Microservices |
| Autonomie des équipes | Code partagé, conflits fréquents, release commune | Chaque équipe possède un service et son contrat | Microservices |
| Cohérence des données | Transactions ACID entre modules | Cohérence à terme entre services, données dupliquées ou référencées par ID | Monolithe |
| Coût d'infrastructure | Faible (1 application + 1 base) | Plus élevé (N applications, N bases, orchestrateur) | Monolithe |
| Observabilité et débogage | Pile d'appels locale | Traçage distribué nécessaire | Monolithe |
| Frontières entre domaines | Respectées par discipline seulement, faciles à contourner | Imposées physiquement par le réseau et les contrats | Microservices |
| Valeur pédagogique (sujet GL5121) | Faible pour les sujets DevOps et distribués | Contrats, conteneurs, orchestration, CI/CD | Microservices |

### 3.3 Choix retenu : microservices

> Décision formalisée dans [ADR-0001](adr/0001-architecture-microservices.md).

Nous retenons une architecture en **5 microservices**. Raisons :

1. **Des domaines métier bien séparés.** Identité, profils, offres, analyse et communication ont peu de
   règles en commun et des cycles de vie différents. Le découpage suit ces domaines (*bounded contexts*).
2. **Des charges différentes.** La messagerie et les notifications reçoivent beaucoup de petites requêtes.
   Le matching et les rapports font des calculs ponctuels et coûteux. Les microservices permettent de
   dimensionner chacun séparément.
3. **Le travail en équipes parallèles.** Le projet est réalisé par plusieurs équipes (back, front, devops).
   Les contrats OpenAPI servent de frontière stable : chaque équipe avance sur son service, et le front
   peut développer avec des mocks générés depuis les contrats.
4. **Des pannes isolées.** Une panne de la messagerie ne doit pas empêcher un étudiant de postuler.
5. **Les attentes du sujet.** Le sujet demande de mettre en œuvre contrats d'interface, conteneurisation,
   orchestration et CI/CD. Ces sujets prennent tout leur sens dans une architecture distribuée.

**Coûts acceptés, et comment nous les limitons :**

| Coût | Mesure |
| --- | --- |
| Complexité de l'outillage | **Monorepo**, `Makefile` et scripts identiques dans chaque service, Dockerfile unique partagé |
| Dérive des interfaces | Approche **contract first** : SPIs générés et versionnés ; une modification de contrat passe par une PR |
| Données réparties | Chaque service est propriétaire de ses données ; les autres y font référence par UUID, et les champs d'affichage (`companyName`, `studentName`…) sont dénormalisés |
| Authentification distribuée | JWT vérifié localement par chaque service, sans appel à `user-service` à chaque requête |
| Coût d'exploitation | Kubernetes local avec **kind** ; une seule image paramétrée par `SERVICE` |

> Le **monolithe modulaire** restait une option défendable pour une petite équipe. Le découpage par paquets
> et par domaine utilisé ici permettrait d'ailleurs de regrouper les services en un monolithe si le coût
> d'exploitation devenait trop élevé. L'inverse, extraire des services d'un monolithe, est plus difficile.

## 4. Stack technique retenue

> Voir les ADR [0002](adr/0002-contract-first-openapi.md) à [0010](adr/0010-jeton-de-service.md).

| Couche | Choix | Justification |
| --- | --- | --- |
| Langage | **Java 21 (LTS)** | Support long terme, *records*, *virtual threads* ; langage maîtrisé par l'équipe et enseigné dans le cursus |
| Framework | **Spring Boot 4.1** (Web MVC, Validation, Security, Data JPA, Actuator) | Standard de l'industrie pour les microservices Java ; écosystème complet (sécurité, persistance, santé) ; configuration externalisée |
| Contrats d'API | **OpenAPI 3** + **openapi-generator** | Contract first : le contrat est la source de vérité ; interfaces et DTO générés (SPI), donc pas d'écart entre la documentation et le code |
| Distribution des SPIs | Dépôt Maven versionné dans Git (`spis/repo/`) | Aucun registre à héberger ; un `git pull` suffit ; versions immuables |
| Base de données | **PostgreSQL 16**, une base par service | SGBD relationnel open source, fiable et transactionnel ; *database per service* pour l'autonomie des services |
| Migrations | **Flyway** | Schéma versionné et rejoué à l'identique dans chaque environnement ; JPA en mode `validate` |
| Sécurité | **Spring Security** + **JWT** (secret HMAC partagé, émetteur `alumni-uasz-user`) | Stateless, donc compatible avec la mise à l'échelle horizontale ; vérification locale sans appel réseau |
| Build | **Maven** (wrapper `mvnw`) | Standard Spring ; build reproductible sans installation préalable |
| Conteneurisation | **Docker** (build multi-étapes, image JRE, utilisateur non root) | Image légère et sûre ; un seul `service.Dockerfile` paramétré par `SERVICE` |
| Dev local | **docker-compose** par service, `make setup / up` | Démarrer un service et sa base en une commande |
| Orchestration | **Kubernetes** + **kustomize** (overlays dev/prod), **kind** en local, ingress **NGINX** | Rolling updates, sondes de santé, réplicas ; même manifeste du poste de développement à la production |
| CI/CD | **GitHub Actions** (CI sélective, CD vers GHCR et K8s) | Intégré au dépôt ; seuls les services modifiés sont construits |
| Qualité | **SonarQube** | Bugs, vulnérabilités, *code smells* et couverture pour les 5 services |

## 5. Composants logiciels et interfaces

Voir le [diagramme de composants](diagrams/composants.md) et le [diagramme de packages](diagrams/packages.md).

### 5.1 Composants

| Composant | Port | Responsabilité | Interface fournie (contrat) | Préfixe exposé (ingress) |
| --- | --- | --- | --- | --- |
| `user-service` | 8081 | Comptes, authentification, rôles ; **émet les JWT** | [`contracts/user-service.yaml`](../contracts/user-service.yaml) (14 opérations) | `/user-service/v1` |
| `alumni-student-company-service` | 8082 | Étudiants, CV, alumni, expériences, recommandations, entreprises, promotions | [`contracts/alumni-student-company-service.yaml`](../contracts/alumni-student-company-service.yaml) (18) | `/alumni-student-company-service/v1` |
| `job-offer-service` | 8083 | Offres, candidatures, propositions | [`contracts/job-offer-service.yaml`](../contracts/job-offer-service.yaml) (13) | `/job-offer-service/v1` |
| `analytics-matching-service` | 8084 | Matching, statistiques, rapports | [`contracts/analytics-matching-service.yaml`](../contracts/analytics-matching-service.yaml) (5) | `/analytics-matching-service/v1` |
| `notification-messaging-service` | 8085 | Messagerie privée, groupes, notifications | [`contracts/notification-messaging-service.yaml`](../contracts/notification-messaging-service.yaml) (20) | `/notification-messaging-service/v1` |
| SPIs (`spis/`) | | Interfaces `*Api` et modèles générés depuis les contrats | Artefacts Maven `com.uasz.alumni:*-spi:1.0.0` | |
| Ingress NGINX | 80/443 | Point d'entrée unique, routage par préfixe | | `https://api.alumni-uasz.sn/<service>/v1` |

### 5.2 Interfaces entre composants

- **Front → services** : REST/JSON via l'ingress, avec un en-tête `Authorization: Bearer <JWT>` (sauf pour les
  endpoints publics : `/auth/*`, consultation et recherche d'offres).
- **Services → services** : REST/JSON, authentifié par un **jeton de service** propre à l'appelant (le JWT de
  l'utilisateur n'est jamais relayé), voir l'[ADR-0010](adr/0010-jeton-de-service.md). Les lectures sont
  synchrones. Les événements (notifications) partent de façon asynchrone après le commit, avec timeout et retry, et
  ne bloquent jamais le flux métier, voir l'[ADR-0006](adr/0006-communication-rest-synchrone.md). Les dépendances
  prévues sont les suivantes :

| Consommateur | Fournisseur | Usage |
| --- | --- | --- |
| `analytics-matching-service` | `alumni-student-company-service` | Compétences des étudiants, diplômés, promotions |
| `analytics-matching-service` | `job-offer-service` | Offres ouvertes et compétences requises |
| `job-offer-service` | `alumni-student-company-service` | Nom de l'entreprise, profil et CV du candidat |
| `job-offer-service`, `analytics-matching-service`, `user-service` | `notification-messaging-service` | `POST /notifications/send` (changement d'état d'une candidature, proposition, nouvelle offre adaptée, confirmation de compte) |
| tous | `user-service` | Aucun appel à l'exécution : le JWT est vérifié localement avec le secret partagé |

- **Données** : chaque service est le **seul** à accéder à sa base. Les autres données sont référencées par
  **UUID** (`userId`, `companyId`, `offerId`, `studentId`…), jamais par clé étrangère entre bases.
- **Évolution des contrats** : modifier `contracts/*.yaml` et `CHANGELOG.md`, incrémenter la version des SPIs,
  régénérer, aligner `alumni.spi.version` dans les services concernés, le tout dans une Pull Request.

### 5.3 Structure interne d'un service

Chaque service suit la même organisation en couches (package racine `com.uasz.alumni.<service>`) :

```
controller   -> implémente les interfaces SPI générées (spi.api.*Api)
service      -> logique métier, transactions
repository   -> Spring Data JPA
model        -> entités JPA
dto          -> mapping entités <-> modèles SPI (spi.model.*)
security/jwt -> filtre et validation du JWT
config       -> sécurité, préfixe API
exceptions   -> ApiException, GlobalExceptionHandler -> ErrorResponse
utils
```

## 6. Sécurité

- **Authentification** : `user-service` vérifie les identifiants et émet un *access token* JWT (court) et un
  *refresh token*. Les 4 autres services vérifient la signature et l'émetteur avec le même secret
  (`JWT_SECRET`, Base64 de 32 octets minimum).
- **Autorisation** : le rôle (`ETUDIANT`, `ALUMNI`, `ENSEIGNANT`, `ADMINISTRATEUR`) est porté par le JWT et
  contrôlé dans chaque service.
- **Appels inter-services** : jeton de service signé (émetteur `alumni-uasz-internal`, `sub` = service
  appelant, `aud` = service appelé, rôle `SERVICE`, durée de vie de 5 min). Les endpoints internes comme
  `POST /notifications/send` exigent ce rôle, et les endpoints utilisateurs le refusent
  ([ADR-0010](adr/0010-jeton-de-service.md)).
- **Stateless** : pas de session HTTP ; CSRF, formulaire de connexion et HTTP Basic désactivés.
- **Secrets** : fichier `.env` local non versionné et généré par `make setup` ; *Secrets* Kubernetes en
  cluster.
- **Conteneurs** : utilisateur non root (UID 10001), profil seccomp `RuntimeDefault`, limites CPU et mémoire.
- **Exposition** : seules les sondes `health` et `info` d'Actuator sont exposées, sans détails.

## 7. Déploiement, CI/CD et qualité

Voir le [diagramme de déploiement](diagrams/deploiement.md).

- **Kubernetes** : namespace `alumni-uasz`. Par service : un `Deployment` à 2 réplicas, un `Service`, une
  `ConfigMap` et des `Secrets`, plus un `StatefulSet` PostgreSQL en dev (stockage de 1 Gi). L'ingress NGINX
  route chaque préfixe vers son service.
- **CI** (`.github/workflows/ci.yml`) : vérifie que les contrats génèrent des SPIs valides, détecte les
  services modifiés et ne construit qu'eux.
- **CD** (`deploy.yml`) : build Maven, image poussée sur **GHCR**, déploiement kustomize.
- **Qualité** (`sonar.yml` et `sonar/`) : analyse SonarQube des 5 services sur `main` et `develop`.

## 8. Points ouverts

| Sujet | Constat | Piste |
| --- | --- | --- |
| Rôle « Entreprise partenaire » | Acteur du cahier des besoins (AL-62), et les contrats parlent de propositions « par un Alumni ou Entreprise », mais `RoleName` ne contient pas `ENTREPRISE` | Ajouter `ENTREPRISE` à `RoleName` et relier le compte à une `Company` (contrat `user-service` 1.1.0, à décider en équipe) |
| RGPD | Pas encore d'export des données personnelles ni de règles de conservation ; la suppression d'un compte n'est pas propagée aux autres services | Endpoint d'export, durée de conservation, événement `UserDeleted` traité par chaque service |
| Communication asynchrone | Les notifications passent par un appel REST (asynchrone après commit, avec retry) : une notification peut être perdue si le service reste longtemps indisponible | Introduire un broker (RabbitMQ ou Kafka) pour les événements (`ApplicationStatusChanged`, `OfferPublished`…) |
| API Gateway | L'ingress fait seulement du routage | Gateway (Spring Cloud Gateway) pour la limitation de débit et la vérification centralisée du JWT, si besoin |
| Observabilité | Seul Actuator `health/info` est exposé | Prometheus et Grafana, logs centralisés, traçage (OpenTelemetry) |
| Stockage des fichiers | `CvResponse.fileUrl`, `logoUrl` | Stockage objet (MinIO ou S3) |
