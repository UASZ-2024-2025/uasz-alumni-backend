# ADR-0010 : Noms des participants de la messagerie recopiés depuis le jeton

- **Statut** : Proposé
- **Date** : 2026-11-18
- **Décideurs** : équipe back (messagerie, utilisateurs)
- **Ticket** : ALUM-XX (groupes de discussion : réglages et administration)

## Contexte

Les groupes de discussion affichent le nom de chaque participant et de l'auteur de chaque message
(`GroupMember.userName`, `GroupMessage.senderName`). Les comptes vivent dans user-service, avec sa propre base
(ADR-0004). `GET /users/{id}` est réservé à l'utilisateur lui-même ou à l'ADMINISTRATEUR : notification-messaging-service
ne peut donc pas lire le nom des autres. Enfin, la v1 n'a ni broker ni événements entre services (ADR-0006).

## Options étudiées

1. **Appel REST à user-service à chaque affichage** : les noms sont toujours à jour. En revanche, il faut un compte de service
   ou une route interne qui n'existe pas dans le contrat. La messagerie tombe dès que user-service est indisponible,
   et il y a N appels par page de messages.
2. **Événements « utilisateur créé / modifié »** : c'est découplé, mais cela demande un broker, que la v1 n'a pas (ADR-0006).
3. **Annuaire local alimenté par le jeton** : user-service ajoute le claim `name` (prénom et nom) au JWT.
   À chaque requête, la messagerie enregistre en `upsert` le nom et le rôle de l'appelant dans `chat_users`.

## Décision

Nous retenons l'option 3. Elle ne modifie aucun contrat d'API (le claim est optionnel), n'ajoute aucun appel synchrone
entre services et suffit à l'affichage. Le nom d'un utilisateur est connu dès sa première requête vers la messagerie.

## Conséquences

- **Positives** : aucune dépendance de la messagerie envers user-service à l'exécution ; une seule écriture par
  utilisateur tant que son nom ne change pas (cache mémoire) ; l'upsert natif (`ON CONFLICT`) tolère les requêtes
  simultanées.
- **Négatives / risques** : une personne ajoutée à un groupe sans s'être encore connectée à la messagerie apparaît sans nom
  (`userName` absent, comme l'autorise le contrat 1.1.0). Un changement de nom n'est vu qu'à la requête suivante de la
  personne concernée. Les jetons émis avant ce changement n'ont pas de claim `name` : le nom déjà connu est conservé.
- **Suivi** : si un broker est introduit, remplacer l'alimentation par jeton par des événements de user-service.
