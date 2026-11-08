# ADR-0006 : Communication entre services en REST (pas de broker en v1), avec timeouts et retry

- **Statut** : Accepté, à réévaluer
- **Date** : 2026-11-07
- **Décideurs** : équipes back, devops
- **Ticket** : AL-2, ticket « Communication inter-services »

## Contexte

Plusieurs flux traversent plusieurs services :

- `job-offer-service` et `alumni-student-company-service` déclenchent des notifications
  (`POST /notifications/send` de `notification-messaging-service`) ;
- `analytics-matching-service` lit les profils étudiants et les offres ;
- les profils étudiant et alumni sont liés à un `userId` de `user-service`.

Il faut aussi décider comment ces appels sont sécurisés, voir l'[ADR-0010](0010-jeton-de-service.md), et
comment le système se comporte quand un service est en panne.

## Options étudiées

1. **REST synchrone** : un service appelle l'API d'un autre.
   - Avantages : simple ; réutilise les contrats OpenAPI existants (Contract First) ; facile à tester et à
     tracer ; aucun composant supplémentaire.
   - Inconvénients : couplage temporel (si l'appelé est indisponible, l'appel échoue) ; les latences
     s'additionnent.
2. **Broker de messages** (RabbitMQ ou Kafka) : les services publient des événements
   (`ApplicationStatusChanged`, `OfferPublished`, `UserDeleted`…) et les autres s'y abonnent.
   - Avantages : découplage fort ; aucune perte si le consommateur est arrêté (le message attend dans la
     file) ; propagation naturelle (RGPD, données dénormalisées).
   - Inconvénients : un composant de plus à déployer, superviser et sauvegarder ; cohérence à terme ; contrats
     d'événements à définir (AsyncAPI) ; débogage plus difficile ; courbe d'apprentissage pour l'équipe.
3. **Les deux** : REST pour les lectures, broker pour les événements.
   - C'est la cible naturelle, mais elle combine le coût d'exploitation des deux pour la v1.

## Décision

Pour la v1, nous retenons le **REST** pour tous les échanges, avec deux usages distincts :

| Usage | Exemple | Mode |
| --- | --- | --- |
| **Lecture** (l'appelant a besoin de la réponse) | matching : profils et offres | REST synchrone, timeout court, erreur remontée à l'appelant |
| **Événement** (l'appelant n'a pas besoin de réponse) | changement de statut d'une candidature → notification | REST **asynchrone** : après le commit, sur un autre thread, avec retry ; ne bloque jamais le flux métier |

**Politique de résilience** (implémentée dans `job-offer-service`, à reprendre dans les autres services) :

| Mécanisme | Valeur | Rôle |
| --- | --- | --- |
| Timeout de connexion | 2 s | Un service arrêté est détecté vite |
| Timeout de lecture | 3 s | Un service lent ne bloque pas un thread indéfiniment |
| Retry | 3 retries (4 tentatives), délais 500 ms, 1 s, 2 s (plafond 5 s) | Absorbe une coupure brève (redémarrage d'un pod) |
| Erreurs rejouées | réseau / timeout, HTTP 5xx, HTTP 429 | Erreurs **transitoires** uniquement |
| Erreurs non rejouées | autres HTTP 4xx (400, 401, 403…) | Rejouer une requête invalide ne la rendra pas valide |
| Publication après commit | `@TransactionalEventListener(AFTER_COMMIT)` | Aucune notification pour une modification annulée |
| Exécution asynchrone | `@Async` | La réponse au recruteur n'attend ni la notification ni les retries |
| Échec définitif | journalisé en `ERROR` | Le changement de statut n'est jamais annulé |

Outils : `RestClient` de Spring avec le client HTTP du JDK pour les timeouts, et `@Retryable` de
Spring Framework 7 (`@EnableResilientMethods`), sans dépendance supplémentaire.

Règle complémentaire : pas d'appels en chaîne de plus de 2 niveaux pour une requête utilisateur.

## Conséquences

- **Positives**
  - Livraison rapide ; un seul style d'interface (OpenAPI) à maîtriser.
  - Une panne de `notification-messaging-service` n'empêche ni de postuler ni de traiter une candidature.
  - Les coupures courtes (redémarrage, rolling update) sont absorbées par le retry.
- **Négatives / risques**
  - **Une notification peut être perdue** si le service de notification reste indisponible plus longtemps
    que les retries (environ 4 s), ou si `job-offer-service` redémarre pendant les retries : l'événement
    n'est gardé qu'en mémoire.
  - Les retries multiplient la charge sur un service déjà en difficulté (limité par le plafond et le petit
    nombre de tentatives).
  - Une lecture synchrone échoue si le service lu est indisponible.
- **Suivi** : critères qui déclencheront un ADR « broker de messages » (RabbitMQ, ou le pattern
  *transactional outbox* comme étape intermédiaire) :
  - des notifications perdues sont constatées en production ;
  - il faut propager la suppression d'un utilisateur (RGPD) ou synchroniser les données dénormalisées ;
  - un nouvel événement doit être consommé par plusieurs services.
