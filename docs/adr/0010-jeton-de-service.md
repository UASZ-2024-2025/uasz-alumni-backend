# ADR-0010 : Authentification des appels inter-services par jeton de service

- **Statut** : Accepté
- **Date** : 2026-11-07
- **Décideurs** : équipes back, devops
- **Ticket** : « Communication inter-services »

## Contexte

Certains endpoints sont destinés aux **services** et non aux utilisateurs. Par exemple,
`POST /notifications/send` est décrit dans le contrat comme « usage interne inter-services ou Admin ».
Sans protection, n'importe quel utilisateur connecté pourrait envoyer une notification à n'importe qui.

Un appel peut aussi naître **sans utilisateur** : une tâche planifiée, ou une notification envoyée après
le commit sur un autre thread ([ADR-0006](0006-communication-rest-synchrone.md)), quand la requête
d'origine est déjà terminée.

## Options étudiées

1. **Relayer le JWT de l'utilisateur** : l'appelant transmet le jeton reçu.
   - Simple, mais le service appelé ne peut pas distinguer un appel interne d'un appel direct de
     l'utilisateur, et il n'y a parfois pas d'utilisateur (tâche planifiée, traitement asynchrone). Le jeton
     peut aussi expirer pendant les retries.
2. **Clé d'API statique partagée** (en-tête `X-Api-Key`) : simple, mais sans expiration, sans identité de
   l'appelant et sans audience. Une clé qui fuit reste valable indéfiniment.
3. **mTLS** (certificats clients, ou maillage de services type Istio/Linkerd) : très robuste, mais lourd à
   mettre en place pour la v1.
4. **Jeton de service JWT** : chaque service signe un jeton court qui l'identifie.
5. **OAuth2 Client Credentials** (Keycloak) : le standard, mais il faut un serveur d'autorisation à
   opérer (voir l'ADR-0005).

## Décision

Nous retenons l'**option 4**, avec le format suivant :

| Claim | Valeur | Rôle |
| --- | --- | --- |
| `iss` | `alumni-uasz-internal` | Émetteur interne, **distinct** de user-service (`alumni-uasz-user`) |
| `sub` | nom du service appelant (ex. `job-offer-service`) | Identité de l'appelant (audit : colonne `emitted_by`) |
| `aud` | nom du service appelé (ex. `notification-messaging-service`) | Un jeton n'est valable que pour **un** destinataire |
| `role` | `SERVICE` | Autorisation par rôle |
| `exp` | émission + 5 min | Jeton court ; mis en cache et renouvelé 30 s avant expiration |

Signature **HS256 avec le même `JWT_SECRET`** que les jetons utilisateurs (pas de nouveau secret à
distribuer).

**Côté service appelé** (ex. `notification-messaging-service`) :

- le validateur est choisi selon l'émetteur. Les jetons utilisateurs sont vérifiés avec l'émetteur
  `alumni-uasz-user`. Les jetons de service sont vérifiés avec l'émetteur `alumni-uasz-internal` **et** une
  audience égale au nom du service. Tout autre émetteur est refusé (401) ;
- les endpoints internes exigent le rôle `SERVICE` (ou `ADMINISTRATEUR` quand le contrat le prévoit) ;
- les endpoints utilisateurs refusent le rôle `SERVICE` (403).

**Côté appelant** : `ServiceTokenProvider` produit le jeton, et le client HTTP l'ajoute à chaque appel.
Le JWT de l'utilisateur n'est **jamais** relayé à un autre service.

## Conséquences

- **Positives**
  - Un utilisateur ne peut plus appeler un endpoint interne.
  - L'appelant est identifié, ce qui permet l'audit.
  - Un jeton intercepté ne sert que pour un seul service et pendant 5 minutes.
  - Les appels sans utilisateur (asynchrones, planifiés) sont possibles.
  - Aucun composant ni secret supplémentaire.
- **Négatives / risques**
  - **Même secret que les jetons utilisateurs** : quiconque possède `JWT_SECRET` peut forger un jeton de
    service, et inversement. C'est le même risque que celui déjà accepté dans l'ADR-0005. La séparation par
    émetteur, audience et rôle protège contre les **erreurs d'usage** (un jeton utilisateur présenté à un
    endpoint interne), pas contre la **fuite du secret**.
  - Chaque service dupliquait jusqu'ici sa configuration JWT. Une bibliothèque commune deviendra utile
    quand les 5 services l'utiliseront.
- **Suivi** : lors du passage à RS256 avec publication des clés (JWKS), prévu dans l'ADR-0005, chaque
  service signera ses jetons de service avec **sa propre clé privée**. Un service compromis ne pourra plus
  se faire passer pour un autre. Il faudra alors remplacer cet ADR.
