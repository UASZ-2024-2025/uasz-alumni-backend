# ADR-0005 : Authentification stateless par JWT signé avec un secret partagé (HS256)

- **Statut** : Accepté, à réévaluer avant la mise en production
- **Date** : 2026-11-01
- **Décideurs** : équipes back, devops
- **Ticket** : AL-2

## Contexte

Les 5 services exposent des API protégées et doivent connaître l'identité et le rôle de l'appelant
(`ETUDIANT`, `ALUMNI`, `ENSEIGNANT`, `ADMINISTRATEUR`). Ils tournent en plusieurs réplicas sans état
([ADR-0008](0008-deploiement-docker-kubernetes.md)). Le cahier des besoins (AL-62) exige des JWT, des mots
de passe hachés et un contrôle d'accès par rôle (RBAC).

## Options étudiées

1. **Sessions HTTP côté serveur** : incompatibles avec des services sans état répliqués, sauf à ajouter
   un stockage de sessions partagé (Redis).
2. **Validation centralisée** : chaque service appelle `user-service` à chaque requête. Ce service
   devient alors un point de défaillance unique et ajoute de la latence partout.
3. **JWT HS256, secret partagé** : `user-service` signe, et chaque service vérifie localement avec le
   même secret.
4. **JWT RS256/ES256 avec publication des clés (JWKS)** : `user-service` signe avec sa clé privée et
   publie la clé publique. Les services vérifient sans pouvoir signer.
5. **Fournisseur d'identité externe** (Keycloak) : très complet, mais c'est un composant lourd de plus à
   opérer.

## Décision

Pour la v1, nous retenons l'**option 3**.

- `user-service` émet un **access token** court et un **refresh token** révocable (stocké haché en base).
- Chaque service vérifie la signature, l'émetteur (`iss = alumni-uasz-user`) et l'expiration, puis
  applique les règles de rôle.
- Le secret fait au moins 32 octets (Base64). Il est généré par `make setup` et injecté en `Secret`
  Kubernetes, jamais commité.
- Les mots de passe sont hachés avec **BCrypt**.

**Pourquoi pas RS256 tout de suite** : HS256 est plus simple à mettre en place pour démarrer (pas de
gestion de paires de clés ni d'endpoint JWKS). La différence de sécurité est connue et assumée pour la v1.

## Conséquences

- **Positives** : pas d'appel réseau pour authentifier ; montée en charge horizontale sans session ;
  `user-service` n'est pas un point de défaillance à l'exécution.
- **Négatives / risques**
  - **Le secret est partagé par 5 services.** S'il fuit depuis n'importe lequel d'entre eux, un
    attaquant peut **forger** des jetons valides, y compris administrateur. N'importe quel service peut
    aussi signer.
  - Un access token ne peut pas être révoqué avant son expiration → durée de vie courte (≈ 15 min) et
    refresh token révocable.
  - Changer de secret impose de redéployer tous les services de façon coordonnée.
- **Suivi** : avant la mise en production, remplacer cette décision par un ADR « JWT RS256 + JWKS »
  (option 4). Seul `user-service` détiendra alors la clé privée, et la rotation des clés pourra se faire
  sans redéploiement coordonné.
