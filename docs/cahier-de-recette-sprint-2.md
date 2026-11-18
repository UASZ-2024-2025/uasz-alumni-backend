# Cahier de recette : Sprint 2

> Ticket Jira : **ALUM-70** *Cahier de recette du Sprint 2*.
> Objectif : disposer d'un scénario de test pas à pas pour chaque démo du Sprint 2, afin de valider la livraison avec toute l'équipe.
> Exigences couvertes : EF-01 et EF-02 (comptes et accès), validation du jeton JWT dans les autres services (ALUM-28), ENF-01 (sécurité).
> Références : [`cahier-des-besoins.md`](cahier-des-besoins.md) (EF, RG, ENF), `contracts/user-service.yaml`, `user-service/README.md`.

| | |
|---|---|
| Version | 1.0, brouillon à valider par l'équipe |
| Périmètre testé | `user-service` (comptes, authentification, rôles, administration) et validation JWT dans `job-offer-service`, `alumni-student-company-service`, `analytics-matching-service`, `notification-messaging-service` |
| Rédigé à partir de | contrat OpenAPI `user-service`, code du `user-service`, `.env.example`, README, commit ALUM-28 (état de `develop` au 18/11/2026) |

## Sommaire

1. [Préparation de l'environnement](#1-préparation-de-lenvironnement)
2. [Jeu de données et conventions](#2-jeu-de-données-et-conventions)
3. [Scénarios](#3-scénarios)
4. [Synthèse d'exécution](#4-synthèse-dexécution)
5. [Registre des anomalies](#5-registre-des-anomalies)
6. [Points à confirmer avant la recette](#6-points-à-confirmer-avant-la-recette)

---

## 1. Préparation de l'environnement

À faire une fois, sur un poste avec Docker, avant la séance de recette.

```bash
git fetch && git switch develop && git pull

# 1. user-service d'abord (il génère le secret JWT partagé)
cd user-service && make setup && make up
curl http://localhost:8081/actuator/health          # attendu : {"status":"UP"}

# 2. autres services (secret JWT recopié depuis user-service)
cd ../job-offer-service && make setup && make up
curl http://localhost:8083/actuator/health          # attendu : {"status":"UP"}
```

| Élément | Valeur |
|---|---|
| API `user-service` | `http://localhost:8081/v1` |
| API `job-offer-service` | `http://localhost:8083/v1` (préfixe d'API local, voir `API_PREFIX`) |
| Boîte mail de test (Mailpit) | `http://localhost:8025` |
| Administrateur initial | `BOOTSTRAP_ADMIN_EMAIL` (défaut `admin@alumni-uasz.sn`), mot de passe = `BOOTSTRAP_ADMIN_PASSWORD` dans `user-service/.env` |
| Verrouillage de compte | `MAX_FAILED_ATTEMPTS=5`, `LOCK_DURATION=15m` |
| Durées des jetons | accès `JWT_ACCESS_TTL=15m`, rafraîchissement `REFRESH_TOKEN_TTL=7d`, confirmation d'email `EMAIL_CONFIRMATION_TTL=24h`, réinitialisation `PASSWORD_RESET_TTL=1h` |
| Limitation de débit (par IP et par route) | 10 requêtes/minute sur `register`, `resend-confirmation`, `forgot-password`, `reset-password` ; 30 requêtes/minute sur `login` et `refresh-token` |

> Si les valeurs de votre `.env` diffèrent (durées raccourcies pour la démo, par exemple), adaptez les attentes des scénarios concernés.

**Remise à zéro entre deux passages de recette** : `make reset-db` dans `user-service` (destructif : efface la base).

## 2. Jeu de données et conventions

Comptes utilisés (adresses fictives, boîte de réception visible dans Mailpit) :

| Alias | Email | Rôle | Mot de passe de test |
|---|---|---|---|
| ADMIN | `admin@alumni-uasz.sn` | ADMINISTRATEUR | voir `.env` |
| ETU | `etudiant.test@uasz.sn` | ETUDIANT | `Recette2026` |
| ALU | `alumni.test@uasz.sn` | ALUMNI | `Recette2026` |
| ENS | `enseignant.test@uasz.sn` | ENSEIGNANT | `Recette2026` |
| ENT | `entreprise.test@uasz.sn` | ENTREPRISE | `Recette2026` |

Conventions de lecture :

- Chaque scénario porte un identifiant `RC-S2-xx`, l'exigence couverte (EF, RG, ENF) et le profil qui joue la démo.
- Les requêtes sont données en `curl` (ou Postman/Swagger) ; `$ACCESS` est le jeton d'accès renvoyé par `/auth/login`, `$REFRESH` le jeton de rafraîchissement.
- Un message d'erreur est un `ErrorResponse` : `{ "code": "...", "message": "...", "timestamp": "..." }`. Le **code** fait foi, le message peut évoluer.
- Politique de mot de passe : 8 à 72 octets, au moins une lettre et un chiffre.
- Résultat : **OK**, **KO** (anomalie, voir registre) ou **NT** (non testé).

---

## 3. Scénarios

### Démo 1 : Inscription et activation du compte (EF-01a, EF-01b)

#### RC-S2-01 : Inscription d'un étudiant (cas nominal)
*Exigences : EF-01a, RG-01, RG-03. Profil : Visiteur.*

**Préconditions** : base vide de `etudiant.test@uasz.sn`.

| # | Action | Résultat attendu |
|---|---|---|
| 1 | `POST /auth/register` avec `{"firstName":"Awa","lastName":"Diop","email":"etudiant.test@uasz.sn","password":"Recette2026","role":"ETUDIANT"}` | **201**, corps `User` avec `status = EN_ATTENTE`, rôle `ETUDIANT`, sans mot de passe dans la réponse |
| 2 | Ouvrir Mailpit (`localhost:8025`) | Un email de confirmation est arrivé pour `etudiant.test@uasz.sn`, avec un lien `…/confirmation-email?token=…` |
| 3 | `POST /auth/login` avec ce compte, sans avoir confirmé | **403**, code `ACCOUNT_NOT_ACTIVATED` (RG-01) |

#### RC-S2-02 : Inscription refusée
*Exigences : EF-01a, RG-02, RG-03. Profil : Visiteur.*

| # | Action | Résultat attendu |
|---|---|---|
| 1 | Rejouer l'inscription de RC-S2-01 avec la même adresse | **409**, code `EMAIL_ALREADY_USED` (RG-02) |
| 2 | `POST /auth/register` avec le rôle `ADMINISTRATEUR` | **400**, code `ROLE_NOT_ALLOWED` |
| 3 | `POST /auth/register` avec le mot de passe `abcdefgh` (pas de chiffre) | **400**, code `WEAK_PASSWORD` |
| 4 | `POST /auth/register` avec le mot de passe `Ab1` (trop court) | **400** (erreur de validation ou `WEAK_PASSWORD`) |
| 5 | `POST /auth/register` avec un email mal formé (`pas-un-email`) | **400**, code `VALIDATION_ERROR` |
| 6 | `POST /auth/register` sans le champ `role` | **400**, code `VALIDATION_ERROR` |

#### RC-S2-03 : Confirmation de l'email et première connexion
*Exigences : EF-01b, RG-01, RG-06. Profil : Visiteur.*

**Préconditions** : compte ETU créé (RC-S2-01), non confirmé.

| # | Action | Résultat attendu |
|---|---|---|
| 1 | Copier le `token` du lien reçu dans Mailpit, puis `POST /auth/confirm-email` avec `{"token":"<token>"}` | **200**, message de succès |
| 2 | `POST /auth/login` avec `etudiant.test@uasz.sn` / `Recette2026` | **200**, `AuthResponse` avec `accessToken`, `refreshToken`, `expiresIn`, `user.status = ACTIF` |
| 3 | Rejouer `POST /auth/confirm-email` avec le même jeton | **400**, code `INVALID_OR_EXPIRED_TOKEN` (jeton à usage unique) |
| 4 | `POST /auth/confirm-email` avec un jeton inventé | **400**, code `INVALID_OR_EXPIRED_TOKEN` |
| 5 | `POST /auth/resend-confirmation` pour ETU (déjà activé) | **409**, code `ACCOUNT_ALREADY_ACTIVE` |

#### RC-S2-04 : Renvoi du lien de confirmation
*Exigences : EF-01b, RG-06. Profil : Visiteur.*

**Préconditions** : nouveau compte ALU créé via `register` (rôle `ALUMNI`), non confirmé.

| # | Action | Résultat attendu |
|---|---|---|
| 1 | `POST /auth/resend-confirmation` avec l'email d'ALU | **200**, message générique ; un second email arrive dans Mailpit |
| 2 | Utiliser le **premier** lien (ancien jeton) avec `POST /auth/confirm-email` | **400**, code `INVALID_OR_EXPIRED_TOKEN` (l'ancien lien est invalidé) |
| 3 | Utiliser le **second** lien | **200**, compte ALU passe à `ACTIF` |
| 4 | `POST /auth/resend-confirmation` avec une adresse inconnue | **200**, même message qu'à l'étape 1 (pas d'énumération de comptes, ENF-01) |

> Créer de la même façon les comptes ENS (`ENSEIGNANT`) et ENT (`ENTREPRISE`) et les activer ; ils servent aux scénarios suivants.

---

### Démo 2 : Connexion, session et déconnexion (EF-01c)

#### RC-S2-05 : Connexion et refus de connexion
*Exigences : EF-01c, ENF-01, RG-04. Profil : tous.*

| # | Action | Résultat attendu |
|---|---|---|
| 1 | `POST /auth/login` avec ETU et le bon mot de passe | **200**, `AuthResponse` ; `expiresIn` cohérent avec `JWT_ACCESS_TTL` (900 s si 15 min) |
| 2 | `POST /auth/login` avec ETU et un mauvais mot de passe | **401**, code `INVALID_CREDENTIALS` |
| 3 | `POST /auth/login` avec une adresse inconnue | **401**, **même** code et même message qu'à l'étape 2 (pas d'indice sur l'existence du compte) |
| 4 | `GET /users/{id de ETU}` avec `Authorization: Bearer $ACCESS` | **200**, ETU voit son propre compte |

#### RC-S2-06 : Verrouillage après échecs répétés
*Exigences : ENF-01. Profil : Visiteur. Paramètres : 5 échecs, 15 minutes.*

**Préconditions** : compte ENS actif.

| # | Action | Résultat attendu |
|---|---|---|
| 1 | Envoyer 5 `POST /auth/login` avec un mauvais mot de passe pour ENS | Les 5 premières réponses sont **401** `INVALID_CREDENTIALS` |
| 2 | Envoyer un 6ᵉ login, cette fois avec le **bon** mot de passe | **423**, code `ACCOUNT_LOCKED` |
| 3 | Attendre la fin du verrouillage (ou, en séance, l'administrateur passe ENS à `ACTIF` via RC-S2-12) puis se connecter | **200** |

> En séance, ne pas attendre 15 minutes : utiliser la levée par l'administrateur (« Passer à ACTIF lève aussi un éventuel verrouillage », contrat `updateUserStatus`).

#### RC-S2-07 : Rafraîchissement de session et rotation des jetons
*Exigences : EF-01c. Profil : ETU.*

| # | Action | Résultat attendu |
|---|---|---|
| 1 | Se connecter, noter `$REFRESH` (appelé R1) | **200** |
| 2 | `POST /auth/refresh-token` avec R1 | **200**, nouveau `accessToken` et nouveau `refreshToken` R2 |
| 3 | Rejouer `POST /auth/refresh-token` avec R1 (déjà consommé) | **401**, code `INVALID_REFRESH_TOKEN` (réutilisation détectée) |
| 4 | `POST /auth/refresh-token` avec R2 | **401** `INVALID_REFRESH_TOKEN` (la détection de réutilisation révoque la chaîne ; voir point 6.2) |
| 5 | `POST /auth/refresh-token` avec une valeur inventée | **401**, code `INVALID_REFRESH_TOKEN` |

#### RC-S2-08 : Déconnexion
*Exigences : EF-01c. Profil : ETU.*

| # | Action | Résultat attendu |
|---|---|---|
| 1 | Se connecter (jetons A et R) | **200** |
| 2 | `POST /auth/logout` avec `Authorization: Bearer A` et `{"refreshToken":"R"}` | **204** |
| 3 | `POST /auth/refresh-token` avec R | **401**, code `INVALID_REFRESH_TOKEN` |
| 4 | `POST /auth/logout` **sans** en-tête `Authorization` | **401** |

---

### Démo 3 : Mot de passe oublié, réinitialisation et changement (EF-01d)

#### RC-S2-09 : Réinitialisation du mot de passe oublié
*Exigences : EF-01d, RG-03, RG-06, ENF-01. Profil : Visiteur (ETU).*

| # | Action | Résultat attendu |
|---|---|---|
| 1 | `POST /auth/forgot-password` avec l'email d'ETU | **200**, message générique ; un email de réinitialisation arrive dans Mailpit |
| 2 | `POST /auth/forgot-password` avec une adresse inconnue | **200**, **même** message (pas d'énumération) ; aucun email dans Mailpit |
| 3 | `POST /auth/reset-password` avec le jeton reçu et un nouveau mot de passe `abcdefgh` | **422**, code `WEAK_PASSWORD` ; le jeton n'est pas consommé (à vérifier avec l'étape 4) |
| 4 | `POST /auth/reset-password` avec le même jeton et `NouveauMdp2026` | **200** |
| 5 | `POST /auth/login` avec l'ancien mot de passe `Recette2026` | **401** `INVALID_CREDENTIALS` |
| 6 | `POST /auth/login` avec `NouveauMdp2026` | **200** |
| 7 | Rejouer `POST /auth/reset-password` avec le même jeton | **400**, code `INVALID_OR_EXPIRED_TOKEN` (usage unique, RG-06) |

#### RC-S2-10 : Changement du mot de passe (utilisateur connecté)
*Exigences : EF-01d, RG-03. Profil : ETU connecté.*

| # | Action | Résultat attendu |
|---|---|---|
| 1 | `POST /auth/change-password` avec le mauvais mot de passe actuel | **400**, code `INVALID_CURRENT_PASSWORD` |
| 2 | `POST /auth/change-password` avec un nouveau mot de passe identique à l'actuel | **400** |
| 3 | `POST /auth/change-password` avec un nouveau mot de passe faible (`abcdefgh`) | **422**, code `WEAK_PASSWORD` |
| 4 | `POST /auth/change-password` avec les bonnes valeurs (nouveau : `Recette2027`) | **200** |
| 5 | `POST /auth/refresh-token` avec le refresh token d'avant le changement | **401** (tous les refresh tokens sont révoqués : il faut se reconnecter) |
| 6 | `POST /auth/login` avec `Recette2027` | **200** |
| 7 | `POST /auth/change-password` sans jeton | **401** |

---

### Démo 4 : Administration des comptes (EF-02a, EF-02b)

**Préconditions communes** : ADMIN connecté (`$ADMIN_ACCESS`), comptes ETU, ALU, ENS, ENT actifs.

#### RC-S2-11 : Consulter, filtrer, créer et modifier des comptes
*Exigences : EF-02a, RG-07. Profil : ADMIN.*

| # | Action | Résultat attendu |
|---|---|---|
| 1 | `GET /users` avec `$ADMIN_ACCESS` | **200**, `UserPagedResponse` paginée (20 par page par défaut) listant au moins ADMIN, ETU, ALU, ENS, ENT |
| 2 | `GET /users?role=ALUMNI` | **200**, uniquement des comptes `ALUMNI` |
| 3 | `GET /users?status=EN_ATTENTE` | **200**, uniquement des comptes `EN_ATTENTE` (vide si tous sont confirmés) |
| 4 | `GET /users?page=0&size=2` | **200**, 2 éléments au plus, informations de pagination cohérentes |
| 5 | `POST /users` (création par l'administrateur) avec un nouvel utilisateur valide | **201**, compte créé ; vérifier le statut renvoyé (voir point 6.3) |
| 6 | `POST /users` avec une adresse déjà utilisée | **409**, code `EMAIL_ALREADY_USED` |
| 7 | `PUT /users/{id de ETU}` avec un nouveau téléphone ou un nouveau nom | **200**, seuls les champs envoyés changent (mise à jour partielle) |
| 8 | `PUT /users/{id de ETU}` avec un nouvel email, en tant qu'ADMIN | **200** (seul l'administrateur peut changer l'email, RG-07) |
| 9 | `GET /users/{id de ETU}` | **200** |

#### RC-S2-12 : Activer, désactiver, suspendre un compte
*Exigences : EF-02b, RG-04, RG-05. Profil : ADMIN.*

| # | Action | Résultat attendu |
|---|---|---|
| 1 | Connecter ENT (jetons AE, RE), puis `PATCH /users/{id de ENT}/status` avec `{"status":"SUSPENDU"}` en tant qu'ADMIN | **200**, `status = SUSPENDU` |
| 2 | `POST /auth/login` avec ENT | **403**, code `ACCOUNT_DISABLED` (RG-04) |
| 3 | `POST /auth/refresh-token` avec RE (session ouverte avant la suspension) | **401** (les sessions sont révoquées) |
| 4 | `PATCH` avec `{"status":"INACTIF"}` sur ENT | **200**, la connexion reste refusée (`ACCOUNT_DISABLED`) |
| 5 | `PATCH` avec `{"status":"ACTIF"}` sur ENT | **200** ; ENT peut se connecter de nouveau |
| 6 | `PATCH` sur le compte d'ADMIN lui-même (`SUSPENDU`) | **409**, code `CANNOT_MODIFY_SELF` (RG-05) |
| 7 | `PATCH` avec un statut inconnu (`{"status":"BIDON"}`) | **400** |
| 8 | `PATCH` sur un identifiant inexistant (UUID aléatoire) | **404** |

#### RC-S2-13 : Supprimer un compte et protection du dernier administrateur
*Exigences : EF-02a, RG-05. Profil : ADMIN.*

| # | Action | Résultat attendu |
|---|---|---|
| 1 | `DELETE /users/{id d'un compte de test créé en RC-S2-11 étape 5}` | **204** |
| 2 | `GET /users/{id supprimé}` | **404** |
| 3 | `DELETE /users/{id d'ADMIN}` (son propre compte) | **409** : `CANNOT_MODIFY_SELF` (ou `LAST_ADMIN` s'il est le seul administrateur actif) |
| 4 | `DELETE /users/{id inexistant}` | **404** |

---

### Démo 5 : Contrôle d'accès par rôle (EF-02, ENF-01, RG-07)

#### RC-S2-14 : Accès refusés aux non-administrateurs
*Exigences : ENF-01, RG-07. Profil : ETU (puis ALU, ENS, ENT).*

| # | Action | Résultat attendu |
|---|---|---|
| 1 | `GET /users` **sans** jeton | **401** |
| 2 | `GET /users` avec le jeton d'ETU | **403** |
| 3 | `POST /users` avec le jeton d'ETU | **403** |
| 4 | `PATCH /users/{id}/status` avec le jeton d'ETU | **403** |
| 5 | `DELETE /users/{id d'ALU}` avec le jeton d'ETU | **403** |
| 6 | `GET /users/{id d'ALU}` avec le jeton d'ETU (compte d'un autre) | **403** |
| 7 | `GET /users/{id d'ETU}` avec le jeton d'ETU | **200** |
| 8 | `GET /users/{id d'ETU}` avec le jeton d'ADMIN | **200** |
| 9 | Répéter les étapes 2 à 5 avec ALU, ENS et ENT | Mêmes résultats : **403** |

#### RC-S2-15 : Modification de ses propres informations
*Exigences : EF-02c, RG-07. Profil : ETU.*

| # | Action | Résultat attendu |
|---|---|---|
| 1 | `PUT /users/{id d'ETU}` avec un nouveau nom et un nouveau téléphone, jeton d'ETU | **200**, valeurs mises à jour |
| 2 | `PUT /users/{id d'ETU}` en changeant l'email, jeton d'ETU | **403** (email réservé à l'administrateur) |
| 3 | `PUT /users/{id d'ALU}`, jeton d'ETU | **403** |
| 4 | `PUT /users/{id d'ETU}` avec un jeton expiré ou falsifié | **401** |

#### RC-S2-16 : Consultation des rôles et de leurs permissions
*Exigences : EF-02d. Profil : ADMIN.*

| # | Action | Résultat attendu |
|---|---|---|
| 1 | `GET /roles` avec `$ADMIN_ACCESS` | **200**, liste des 5 rôles : `ALUMNI`, `ETUDIANT`, `ENSEIGNANT`, `ENTREPRISE`, `ADMINISTRATEUR`, chacun avec ses permissions |
| 2 | `GET /roles` sans jeton | **401** |

---

### Démo 6 : Validation du jeton JWT dans les autres services (ALUM-28, ENF-01)

Le jeton émis par `user-service` doit être reconnu par `job-offer-service` (même `JWT_SECRET`, même `JWT_ISSUER`).
Jeton absent ou invalide = non authentifié (401) sur les routes protégées ; les routes publiques restent accessibles.

#### RC-S2-17 : Routes publiques et protégées de `job-offer-service`
*Exigences : ENF-01. Profil : ETU.*

| # | Action | Résultat attendu |
|---|---|---|
| 1 | `GET http://localhost:8083/v1/offers` **sans** jeton | **200** (route publique) |
| 2 | Appeler une route protégée de `job-offer-service` (par exemple `GET /v1/applications` ou toute route hors `GET /offers`) **sans** jeton | **401**, corps au format `ErrorResponse` (JSON) |
| 3 | Même route avec `Authorization: Bearer $ACCESS` d'ETU | Réponse **différente de 401** (200, 403 ou 404 selon la règle métier de la route : l'important est que le jeton soit accepté) |
| 4 | Même route avec un jeton dont on a modifié un caractère | **401** |
| 5 | Même route avec un jeton **expiré** (attendre `JWT_ACCESS_TTL`, ou configurer un TTL court en environnement de recette) | **401** |
| 6 | Même route avec un jeton signé avec une autre clé ou un autre émetteur | **401** |

> Les tests unitaires (jeton valide, mauvaise clé, mauvais émetteur, expiré, filtre) sont déjà dans chaque service ; cette démo valide le comportement bout en bout.

#### RC-S2-18 : Cohérence entre services
*Exigences : ENF-01, ENF-03. Profil : ETU.*

| # | Action | Résultat attendu |
|---|---|---|
| 1 | Répéter RC-S2-17 étapes 2 et 3 sur un second service (par exemple `alumni-student-company-service`, port 8082) | Mêmes résultats : 401 sans jeton, jeton d'ETU accepté |
| 2 | Arrêter `user-service` (`make down`) puis rappeler la route protégée de `job-offer-service` avec le jeton déjà émis | Jeton toujours accepté tant qu'il n'a pas expiré (la validation est locale, ENF-03) |
| 3 | Redémarrer `user-service` (`make up`) | Santé `UP` |

---

### Démo 7 : Qualité et exploitation (ENF-05, ENF-10, ENF-11)

#### RC-S2-19 : Santé des services, tests et CI
*Exigences : ENF-05, ENF-10, ENF-11. Profil : équipe.*

| # | Action | Résultat attendu |
|---|---|---|
| 1 | `curl localhost:<port>/actuator/health` pour chaque service démarré | `{"status":"UP"}` |
| 2 | `cd user-service && ./mvnw test` | Build réussi, 0 test en échec |
| 3 | Dans chacun des 4 autres services : `./mvnw test` | Build réussi, 0 test en échec |
| 4 | Ouvrir l'onglet Actions de la dernière PR fusionnée sur `develop` | Job **CI success** vert |
| 5 | Dans `user-service` : `make reset-db` puis `make run` | Les migrations Flyway se rejouent sur une base vide, le service démarre, l'administrateur initial est recréé |

---

## 4. Synthèse d'exécution

À remplir pendant la séance de recette. Un exemplaire par passage.

| Champ | Valeur |
|---|---|
| Date et lieu | |
| Version testée (commit de `develop`) | |
| Testeur(s) | |
| Observateurs | |

| ID | Démo | Exigence(s) | Résultat (OK / KO / NT) | Anomalie liée | Commentaire |
|---|---|---|---|---|---|
| RC-S2-01 | Inscription | EF-01a | | | |
| RC-S2-02 | Inscription | EF-01a | | | |
| RC-S2-03 | Inscription | EF-01b | | | |
| RC-S2-04 | Inscription | EF-01b | | | |
| RC-S2-05 | Connexion | EF-01c | | | |
| RC-S2-06 | Connexion | ENF-01 | | | |
| RC-S2-07 | Connexion | EF-01c | | | |
| RC-S2-08 | Connexion | EF-01c | | | |
| RC-S2-09 | Mot de passe | EF-01d | | | |
| RC-S2-10 | Mot de passe | EF-01d | | | |
| RC-S2-11 | Administration | EF-02a | | | |
| RC-S2-12 | Administration | EF-02b | | | |
| RC-S2-13 | Administration | EF-02a | | | |
| RC-S2-14 | Contrôle d'accès | ENF-01 | | | |
| RC-S2-15 | Contrôle d'accès | EF-02c | | | |
| RC-S2-16 | Contrôle d'accès | EF-02d | | | |
| RC-S2-17 | JWT inter-services | ENF-01 | | | |
| RC-S2-18 | JWT inter-services | ENF-03 | | | |
| RC-S2-19 | Qualité | ENF-05, 10, 11 | | | |

**Critère de validation du sprint** : tous les scénarios en **OK**, ou les **KO** consignés dans le registre ci-dessous avec une décision (corriger avant la revue, reporter au Sprint 3, ou accepter).

## 5. Registre des anomalies

| N° | Scénario | Étape | Constat (attendu / obtenu) | Gravité (bloquante, majeure, mineure) | Ticket Jira | Décision | Statut |
|---|---|---|---|---|---|---|---|
| A-01 | | | | | | | |

## 6. Points à confirmer avant la recette

Ce cahier a été rédigé à partir du contrat et du code, sans exécuter la plateforme. Quelques points doivent être vérifiés par l'équipe avant la séance :

1. **Périmètre du Sprint 2.** Le tableau de traçabilité du cahier des besoins associe le Sprint 2 aux exigences EF-01 et EF-02 (tickets T-17 à T-25). Les tickets du backlog Jira portent des numéros ALUM-xx différents ; le dernier commit sur `develop` (ALUM-28, validation JWT) a été ajouté comme démo 6. Si le Sprint 2 contient d'autres stories, ajouter leurs scénarios au même format.
2. **Détection de réutilisation du refresh token (RC-S2-07, étape 4).** Le README annonce une « rotation avec détection de réutilisation ». Confirmer si le rejeu de R1 révoque aussi R2 ; sinon, adapter ou retirer l'étape 4.
3. **Statut d'un compte créé par l'administrateur (RC-S2-11, étape 5).** Le contrat ne précise pas si `POST /users` crée un compte directement `ACTIF` ou `EN_ATTENTE`. Noter le comportement observé et le faire valider.
4. **Route protégée de `job-offer-service` (RC-S2-17).** Choisir avec l'équipe la route et le rôle utilisés pour la démo, selon ce qui est implémenté au moment de la recette.
5. **Mot de passe trop court à l'inscription (RC-S2-02, étape 4).** Selon la validation, la réponse est un `VALIDATION_ERROR` ou un `WEAK_PASSWORD` ; dans les deux cas le statut doit être 400.
6. **Valeurs `.env` de recette.** Si les durées des jetons sont raccourcies pour la démonstration, les consigner en tête de la synthèse d'exécution.
