# Documentation technique --- Alumni UASZ

**Version : 1.0 --- document de travail établi à partir des dépôts
backend et frontend actuels**

> Cette documentation décrit l'état visible dans les sources transmises.
> Les deux dépôts sont séparés : `uasz-alumni-backend` et
> `Alumni-UASZ-frontend`. Les commandes ci-dessous supposent que les
> dépôts ont été clonés dans un même répertoire parent lorsque des
> appels inter-services sont nécessaires.

## 1. Présentation et architecture

Alumni UASZ est une plateforme web construite selon une architecture
microservices et une approche **Contract First**. Les contrats OpenAPI
du backend constituent la source de vérité des API. Les interfaces et
modèles Java sont générés dans `spis/`; les clients TypeScript du
frontend sont générés dans `src/app/api/generes/`.

  -------------------------------------------------------------------------------------
  Composant                                            Port local Responsabilité
  ---------------------------------- ---------------------------- ---------------------
  `user-service`                                             8081 Comptes,
                                                                  authentification,
                                                                  rôles ; émet les JWT

  `alumni-student-company-service`                           8082 Étudiants, CV,
                                                                  Alumni, entreprises
                                                                  et promotions

  `job-offer-service`                                        8083 Offres, candidatures
                                                                  et propositions

  `analytics-matching-service`                               8084 Matching, tableaux de
                                                                  bord et rapports

  `notification-messaging-service`                           8085 Messagerie privée,
                                                                  groupes et
                                                                  notifications

  Frontend Angular                                           4200 Interface web

  PostgreSQL                               variable selon service Une base isolée par
                                                                  microservice

  Mailpit                                    8025 (interface web) Consultation des
                                                                  courriels de
                                                                  développement
  -------------------------------------------------------------------------------------

**Vue d'ensemble**

``` text
Navigateur
   |
   v
Frontend Angular (localhost:4200)
   |-- user-service (8081)
   |-- alumni-student-company-service (8082)
   |-- job-offer-service (8083)
   |-- analytics-matching-service (8084)
   `-- notification-messaging-service (8085)

Les services communiquent également entre eux selon les cas d'usage.
Chaque microservice possède sa base PostgreSQL.
```

\[EMPLACEMENT CAPTURE 1 --- Schéma d'architecture global de la
plateforme\]

### 1.1 Organisation des dépôts

Backend : - `user-service/`, `alumni-student-company-service/`,
`job-offer-service/`, `analytics-matching-service/`,
`notification-messaging-service/` : cinq applications Spring Boot
indépendantes. - `contracts/` : contrats OpenAPI. - `spis/` : SPIs Java
générées à partir des contrats. - `alumni-uasz-infra/` : manifests
Kubernetes, cluster kind et scripts de déploiement local. -
`deploy/demo/` : configuration de l'environnement de démonstration. -
`docs/` : documentation d'architecture, ADR, diagrammes et plan de
tests.

Frontend : - `src/app/socle/` : authentification, intercepteurs HTTP,
gardes de routes et mise en page. - `src/app/commun/` : composants,
modèles et utilitaires partagés. - `src/app/fonctionnalites/` :
fonctionnalités métier. - `src/app/api/generes/` : clients générés, à ne
pas modifier manuellement. - `src/environments/` : adresses des API par
environnement. - `scripts/mock-api.js` : faux backend de développement.

## 2. Prérequis

### Backend

-   Git
-   Java 21 (JDK)
-   Docker avec Docker Compose
-   Bash pour les scripts shell (sur Windows, utiliser WSL ou un
    environnement compatible)
-   Accès réseau à Maven Central lors du premier téléchargement des
    dépendances

Les services disposent d'un Maven Wrapper (`mvnw`), ce qui évite
d'installer une version globale de Maven.

### Frontend

-   Node.js : `^20.19.0 || ^22.12.0 || ^24.0.0`
-   npm 11
-   Git
-   Java uniquement si les clients API doivent être régénérés
-   Docker Desktop uniquement pour SonarQube local

## 3. Récupération des dépôts

Créer un répertoire de travail et cloner les deux dépôts :

``` bash
mkdir alumni_uasz
cd alumni_uasz

git clone https://github.com/UASZ-2024-2025/uasz-alumni-backend.git
git clone https://github.com/UASZ-2024-2025/Alumni-UASZ-frontend.git
```

Structure attendue :

``` text
alumni_uasz/
├── uasz-alumni-backend/
└── Alumni-UASZ-frontend/
```

Se placer sur `develop` à jour dans chaque dépôt avant de commencer :

``` bash
git -C uasz-alumni-backend fetch
git -C uasz-alumni-backend switch develop
git -C uasz-alumni-backend pull

git -C Alumni-UASZ-frontend fetch
git -C Alumni-UASZ-frontend switch develop
git -C Alumni-UASZ-frontend pull
```

## 4. Configuration et variables d'environnement du backend

Chaque microservice possède son propre `.env.example`, son `.env` local
(non versionné), un `Makefile` et des scripts dans `scripts/`.

### 4.1 Initialisation

**Important : configurer `user-service` en premier**, car il génère le
`JWT_SECRET` partagé par les cinq services.

``` bash
cd uasz-alumni-backend/user-service
make setup
make env-complete
```

Configurer ensuite les autres services :

``` bash
cd ../alumni-student-company-service && make setup && make env-complete
cd ../job-offer-service && make setup && make env-complete
cd ../analytics-matching-service && make setup && make env-complete
cd ../notification-messaging-service && make setup && make env-complete
```

Les scripts créent les fichiers `.env` à partir de `.env.example`,
génèrent les mots de passe PostgreSQL et recopient le secret JWT depuis
`user-service` pour les services qui valident les jetons.

Si le secret JWT est régénéré dans `user-service`, synchroniser les
autres services :

``` bash
# À exécuter depuis chacun des quatre autres répertoires de service
make jwt-sync
```

Ne jamais commiter les fichiers `.env`, les mots de passe, les tokens ou
les secrets.

### 4.2 Variables principales

Les noms exacts et les valeurs par défaut sont définis dans le
`.env.example` propre à chaque service. Les familles de variables
communes sont :

  -----------------------------------------------------------------------
  Variable                            Rôle
  ----------------------------------- -----------------------------------
  `POSTGRES_DB`                       Nom de la base du service

  `POSTGRES_USER`                     Utilisateur PostgreSQL du service

  `POSTGRES_PASSWORD`                 Mot de passe PostgreSQL

  `DB_PORT`                           Port local publié par le conteneur
                                      PostgreSQL

  `JWT_SECRET`                        Secret Base64 partagé, généré par
                                      `user-service`

  `JWT_ISSUER`                        Émetteur attendu des JWT
                                      (`alumni-uasz-user`)

  `SERVER_PORT`                       Port HTTP du microservice

  `API_PREFIX`                        Préfixe de l'API ; `/v1` en local

  `CORS_ALLOWED_ORIGINS`              Origines frontend autorisées,
                                      normalement `http://localhost:4200`
                                      en local

  `FORWARD_HEADERS_STRATEGY`          `none` en développement direct ;
                                      `native` derrière un proxy de
                                      confiance
  -----------------------------------------------------------------------

Variables propres à certains services : - `NOTIFICATION_SERVICE_URL` et
`NOTIFICATION_SERVICE_URL_DOCKER` : adresse du service de notification
depuis l'hôte ou depuis un conteneur. - `STUDENT_COMPANY_SERVICE_URL` et
sa variante `_DOCKER` : adresse du service de gestion des
profils/entreprises. - `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`,
`MAIL_PASSWORD`, `MAIL_FROM` : configuration SMTP. Le développement du
`user-service` utilise Mailpit. - `FRONTEND_URL` : URL utilisée
notamment dans les liens de confirmation. - `BOOTSTRAP_ADMIN_EMAIL`,
`BOOTSTRAP_ADMIN_PASSWORD` et les variables de nom : compte
administrateur initial. - `JWT_ACCESS_TTL`, `REFRESH_TOKEN_TTL`,
`EMAIL_CONFIRMATION_TTL`, `PASSWORD_RESET_TTL`, `MAX_FAILED_ATTEMPTS`,
`LOCK_DURATION` : paramètres de sécurité du `user-service`. -
`CLOUDINARY_URL` : configuration facultative des pièces jointes de
messagerie.

Les valeurs obligatoires doivent être présentes et ne doivent pas rester
à `CHANGE_ME`. Pour compléter un ancien `.env` après évolution du modèle
: `make env-complete`.

### 4.3 Bases de données

Chaque service dispose de sa propre base PostgreSQL et de son propre
utilisateur. Les migrations sont gérées par Flyway ; Hibernate utilise
`ddl-auto: validate`. Ne pas modifier une migration déjà appliquée :
ajouter une nouvelle migration `Vn__description.sql`.

## 5. Démarrer le backend

### 5.1 Démarrage d'un service

Depuis le répertoire du service :

``` bash
make up
```

Cette commande démarre la stack du service en conteneurs et reconstruit
l'application. Pour démarrer l'application Java sur la machine hôte avec
la base PostgreSQL en conteneur :

``` bash
make db
make run
```

Répéter pour chaque service requis par le scénario testé. Les appels
inter-services doivent être configurés dans les `.env` correspondants.
Le `user-service` doit être initialisé et disponible pour les parcours
d'authentification.

Commandes utiles disponibles dans les Makefiles :

  Commande              Description
  --------------------- -----------------------------------------------------
  `make help`           Liste les commandes du service
  `make setup`          Crée le `.env` initial
  `make env-complete`   Ajoute les variables manquantes
  `make jwt-sync`       Synchronise le secret JWT depuis `user-service`
  `make db`             Démarre PostgreSQL (et Mailpit pour `user-service`)
  `make run`            Démarre PostgreSQL puis l'application en local
  `make up`             Démarre/reconstruit la stack en conteneurs
  `make down`           Arrête les conteneurs sans supprimer les données
  `make ps`             État des conteneurs
  `make logs`           Logs de l'application
  `make logs-db`        Logs PostgreSQL
  `make psql`           Ouvre une console SQL
  `make reset-db`       Réinitialise la base --- **destructif**
  `make nuke`           Supprime conteneurs et volumes --- **destructif**
  `make k8s-secrets`    Génère les secrets Kubernetes de développement

### 5.2 Vérification de santé

  Service                      URL
  ---------------------------- -----------------------------------------
  Utilisateurs                 `http://localhost:8081/actuator/health`
  Profils / entreprises        `http://localhost:8082/actuator/health`
  Offres                       `http://localhost:8083/actuator/health`
  Analytics / matching         `http://localhost:8084/actuator/health`
  Notifications / messagerie   `http://localhost:8085/actuator/health`

La réponse attendue lorsque le service est prêt est un statut `UP`.

\[EMPLACEMENT CAPTURE 2 --- Vérification health UP des cinq
microservices\]

## 6. Contrats API et documentation interactive

Les contrats OpenAPI sont dans `uasz-alumni-backend/contracts/` et sont
également présents dans le dépôt frontend sous `contrat/`. Ils décrivent
les chemins, schémas, paramètres, réponses et rôles autorisés. Le
backend suit une approche Contract First ; les SPIs Java sont générées
depuis ces contrats.

**Point important constaté dans les fichiers fournis :** aucun mécanisme
`springdoc-openapi` ni dépendance `springdoc-openapi` n'a été repéré
dans les configurations backend consultées. Il ne faut donc pas annoncer
une URL Swagger UI fonctionnelle sans ajouter/configurer explicitement
ce composant. À ce stade, la référence vérifiable est le fichier YAML
OpenAPI de chaque service. Une interface Swagger UI peut être ajoutée
ultérieurement si le projet le décide.

Contrats principaux : - `contracts/user-service.yaml` -
`contracts/alumni-student-company-service.yaml` -
`contracts/job-offer-service.yaml` -
`contracts/analytics-matching-service.yaml` -
`contracts/notification-messaging-service.yaml`

\[EMPLACEMENT CAPTURE 3 --- Contrat OpenAPI ouvert dans l'éditeur ou
interface Swagger UI si elle est ajoutée ultérieurement\]

## 7. Tests backend

Depuis le répertoire du microservice :

``` bash
./mvnw test
```

Pour compiler et empaqueter sans exécuter les tests :

``` bash
./mvnw -B package -DskipTests
```

Pour vérifier la conformité d'un service au contrat OpenAPI, certains
dépôts contiennent `scripts/contract-test.sh`. Le script du
`user-service` indique qu'il nécessite un service déjà démarré et
Schemathesis installé. Vérifier le README/script du service concerné
avant exécution.

Les tests d'intégration nécessitant PostgreSQL ou des services voisins
doivent être lancés avec les dépendances correspondantes démarrées. Les
tests unitaires ne doivent pas être confondus avec un test complet de
recette multi-services.

## 8. Démarrer le frontend

Depuis la racine du dépôt frontend :

``` bash
cd Alumni-UASZ-frontend
npm ci
```

Dans un premier terminal :

``` bash
npm run mock
```

Dans un second terminal :

``` bash
npm start
```

L'application est disponible à `http://localhost:4200`.

Le mock est un serveur de test en mémoire qui écoute sur les ports
**8081 à 8085**. Le code `scripts/mock-api.js` démarre cinq serveurs (un
par port).

### 8.1 Comptes de démonstration du mock

Mot de passe commun du mock : `password123`.

  Adresse                        Rôle
  ------------------------------ --------------------------
  `alumni@uasz.sn`               `ALUMNI`
  `etudiant@uasz.sn`             `ETUDIANT`
  `enseignant@uasz.sn`           `ENSEIGNANT`
  `entreprise@uasz.sn`           `ENTREPRISE`
  `entreprise-attente@uasz.sn`   `ENTREPRISE` non validée
  `admin@uasz.sn`                `ADMINISTRATEUR`

Ces identifiants sont réservés au mock local. Ne pas les utiliser comme
identifiants d'un environnement réel.

### 8.2 Commandes frontend

  Commande                                 Description
  ---------------------------------------- ---------------------------------------------------
  `npm start`                              Serveur Angular de développement sur le port 4200
  `npm run mock`                           Démarre le faux backend
  `npm run build`                          Build de production
  `npm test`                               Lance les tests Vitest
  `npx ng test --watch=false`              Lance les tests une fois
  `npx ng test --coverage --watch=false`   Tests avec couverture
  `npm run api:generate`                   Régénère les clients depuis les contrats

L'URL des services est configurée dans `src/environments/`.
`environment.development.ts` est utilisé en développement et
`environment.ts` en production, selon les remplacements déclarés dans
`angular.json`.

## 9. Architecture et règles de développement frontend

-   Les appels API métier passent par les clients générés, pas par des
    appels `HttpClient` écrits directement dans les composants.
-   Ne pas modifier manuellement `src/app/api/generes/**`. Modifier le
    contrat puis régénérer les clients.
-   `fonctionnalites` peut dépendre de `socle`, `commun` et
    `api/generes`, mais pas l'inverse.
-   Une fonctionnalité ne doit pas importer directement le code d'une
    autre fonctionnalité ; le code partagé doit être placé dans
    `commun`.
-   Le stockage de session passe uniquement par `JetonService`.
-   Les composants sont standalone ; les signaux servent à l'état local
    ; les templates utilisent `@if` et `@for`.
-   Les erreurs API sont normalisées en `ErreurApi` par l'intercepteur
    d'erreurs.
-   Les fichiers du socle d'authentification sont sensibles et réservés
    au référent indiqué dans le README frontend.

## 10. Authentification et autorisations

Le `user-service` émet un access token et un refresh token. Les autres
services vérifient le JWT localement avec le secret partagé et
contrôlent le rôle. Le frontend : 1. appelle le client généré pour la
connexion ; 2. conserve les jetons via `JetonService` (stockage
localStorage dans l'état du code fourni) ; 3. ajoute le Bearer token aux
requêtes API ; 4. tente un renouvellement partagé lorsque plusieurs
requêtes rencontrent un `401` ; 5. efface la session et renvoie vers
`/connexion` si le renouvellement échoue.

Les routes frontend utilisent un garde d'authentification et des gardes
de rôle. Un accès non autorisé mène à la page `/acces-refuse` (403).

## 11. Exploitation locale et Kubernetes

### 11.1 Kubernetes local (kind)

Le dépôt d'infrastructure fournit les scripts suivants. Prérequis :
Docker, kind et kubectl.

``` bash
cd alumni-uasz-infra
./scripts/kind-up.sh
```

Pour chaque microservice, générer d'abord les secrets puis déployer
depuis le dépôt infra :

``` bash
cd ../user-service
make k8s-secrets

cd ../alumni-uasz-infra
./scripts/deploy-service.sh user-service
kubectl -n alumni-uasz get pods
kubectl -n alumni-uasz logs -f deploy/user-service
```

Le cluster s'appelle `alumni-uasz`. L'Ingress NGINX route les préfixes
`/user-service/v1`, `/alumni-student-company-service/v1`,
`/job-offer-service/v1`, `/analytics-matching-service/v1` et
`/notification-messaging-service/v1`. Le script `kind-down.sh` supprime
le cluster.

### 11.2 Démonstration AWS

Le dépôt contient aussi une configuration de démonstration AWS avec deux
machines, Docker Compose, Caddy/HTTPS, déploiement GitHub Actions via
OIDC/SSM et sauvegardes. Cette procédure est distincte du démarrage
local ; consulter `deploy/demo/README.md` et les scripts associés avant
toute opération. Ne pas appliquer ces opérations sur un environnement
partagé sans autorisation.

### 11.3 Logs et diagnostic

-   `make ps` : état des conteneurs.
-   `make logs` : logs du service.
-   `make logs-db` : logs PostgreSQL.
-   `docker compose ps` et `docker compose logs -f <service>` :
    diagnostic Compose.
-   `curl http://localhost:<port>/actuator/health` : disponibilité du
    service.

Erreurs fréquentes : - variable absente : exécuter `make env-complete`,
puis vérifier `.env` ; - `JWT_SECRET` incohérent : exécuter
`make jwt-sync` dans les services validateurs ; - base inaccessible :
vérifier le conteneur PostgreSQL, `DB_PORT` et les variables datasource
; - port occupé : arrêter le mock ou le service qui utilise déjà le port
; - appel inter-service impossible depuis Docker : utiliser les
variables d'URL prévues pour le contexte conteneur (`*_DOCKER`) ; -
erreur CORS : vérifier `CORS_ALLOWED_ORIGINS` et l'URL frontend
configurée.

## 12. Qualité, CI et Pull Request

Avant de pousser : - backend : exécuter `./mvnw test` dans chaque
service touché ; - frontend : `npm run build` puis
`npx ng test --watch=false`.

Le dépôt frontend documente les checks CI `Build et tests frontend`,
`CI success` et le contrôle du titre Conventional Commits. Le backend
possède ses propres workflows CI et contrôle de titre de PR.

Pour les tickets ALUM-xxx, respecter la consigne de branche
communiquée :

``` bash
git fetch
git switch develop
git pull
git switch -c feature/alum-xxx-titre-du-ticket-jira
```

Ne jamais travailler directement sur `main` ou `develop`. Ouvrir une
seule PR vers `develop`, demander une relecture par un autre membre,
résoudre les conversations et attendre les checks verts avant fusion.

------------------------------------------------------------------------

**Fin --- documentation technique ALUM-192**
