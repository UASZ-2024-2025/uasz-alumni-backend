# ADR-0006 : Communication entre services en REST synchrone (pas de broker en v1)

- **Statut** : Accepté, à réévaluer
- **Date** : 2026-11-07
- **Décideurs** : équipes back, devops
- **Ticket** : AL-2

## Contexte

Certains flux traversent plusieurs services :

- `job-offer-service` a besoin du profil et du CV d'un étudiant, et doit faire envoyer des notifications ;
- `analytics-matching-service` agrège des étudiants et des offres ;
- `user-service` fait envoyer l'email de confirmation.

Les contrats exposent déjà `POST /notifications/send`.

## Options étudiées

1. **REST synchrone** : un service appelle l'API d'un autre en propageant le JWT.
   - Avantages : simple ; réutilise les contrats existants ; facile à tester et à tracer.
   - Inconvénients : couplage temporel (si l'appelé est indisponible, l'appel échoue) ; latences qui
     s'additionnent.
2. **Messagerie asynchrone** (RabbitMQ ou Kafka) : les services publient des événements
   (`ApplicationSubmitted`, `OfferPublished`, `UserDeleted`…) et les autres s'y abonnent.
   - Avantages : découplage ; les notifications ne bloquent pas le flux métier ; propagation naturelle
     (RGPD, dénormalisation).
   - Inconvénients : un composant de plus à opérer ; cohérence à terme ; contrats d'événements à définir
     (AsyncAPI) ; débogage plus difficile.

## Décision

Pour la v1, nous retenons le **REST synchrone**, avec des garde-fous :

- les **délais d'attente** (timeouts) sont configurés sur chaque client HTTP ;
- l'envoi de notification est **non bloquant pour le métier** : si `notification-messaging-service`
  échoue, la candidature est quand même enregistrée et l'erreur est journalisée ;
- pas d'appels en chaîne de plus de 2 niveaux pour une requête utilisateur.

## Conséquences

- **Positives** : livraison plus rapide ; un seul style d'interface (OpenAPI) à maîtriser.
- **Négatives / risques**
  - Une notification peut être perdue si le service de notification est indisponible.
  - La disponibilité d'un flux dépend de celle de chaque service appelé.
- **Suivi**, critères qui déclencheront un ADR « messagerie asynchrone » :
  - des notifications perdues sont constatées ;
  - il faut propager la suppression d'un utilisateur (RGPD) ou synchroniser les données dénormalisées ;
  - les latences de `analytics-matching-service` deviennent excessives.
