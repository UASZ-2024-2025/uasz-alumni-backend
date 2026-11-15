# ADR-0001 : Architecture en microservices plutôt qu'un monolithe modulaire

- **Statut** : Accepté
- **Date** : 2026-11-01
- **Décideurs** : équipes back, devops
- **Ticket** : AL-2 (socle technique)

## Contexte

La plateforme couvre cinq domaines aux règles et aux cycles de vie différents : identité, profils
(étudiants, alumni, entreprises), offres et candidatures, analyse et matching, communication. Le projet
est réalisé par plusieurs équipes en parallèle (back, front, devops) sur un temps court. Le sujet GL5121
demande de mettre en œuvre des contrats d'interface, la conteneurisation, l'orchestration et la CI/CD.

La volumétrie attendue est modeste : quelques centaines à quelques milliers d'utilisateurs, ceux d'un
master universitaire et de ses diplômés.

## Options étudiées

1. **Monolithe modulaire** : une application Spring Boot, un module par domaine, une base.
   - Avantages : simple à développer, à tester et à déboguer ; transactions ACID entre domaines ; coût
     d'infrastructure minimal.
   - Inconvénients : frontières entre modules faciles à contourner ; un seul déploiement pour tout ;
     conflits entre équipes sur un même code ; met peu en pratique les sujets distribués du cours.
2. **Microservices** : un service déployable par domaine, chacun avec sa base, reliés par des contrats.
   - Avantages : autonomie des équipes ; déploiement, montée en charge et pannes isolés par service ;
     frontières imposées physiquement.
   - Inconvénients : complexité opérationnelle (N builds, N bases, orchestrateur) ; cohérence des données
     à terme ; débogage distribué.

## Décision

Nous retenons **5 microservices** découpés par domaine métier : `user`,
`alumni-student-company`, `job-offer`, `analytics-matching` et `notification-messaging`.

**Ce qui fait pencher la balance, honnêtement :** la volumétrie seule ne justifie pas des microservices.
Un monolithe modulaire tiendrait la charge sans difficulté. Le choix repose sur trois motifs :

- **organisationnel** : plusieurs équipes doivent avancer en parallèle sans se bloquer, avec des
  contrats comme frontière ;
- **pédagogique** : le sujet exige de pratiquer l'architecture distribuée ;
- **évolutif** : les domaines ont des profils de charge différents. La messagerie reçoit beaucoup de
  petites requêtes, l'analyse fait des calculs ponctuels et lourds.

## Conséquences

- **Positives**
  - Chaque équipe possède un service, son contrat et sa base.
  - Une panne de la messagerie n'empêche pas de postuler à une offre.
  - Le service d'analyse peut être dimensionné séparément.
- **Négatives / risques**, et comment on les limite
  - Coût d'outillage → monorepo ([ADR-0003](0003-monorepo.md)), Dockerfile et Makefile communs.
  - Dérive des interfaces → Contract First ([ADR-0002](0002-contract-first-openapi.md)).
  - Données réparties → références par UUID et dénormalisation des champs d'affichage
    ([ADR-0004](0004-base-de-donnees-par-service.md)).
  - Risque de « monolithe distribué » si les services s'appellent en chaîne pour chaque requête → on
    surveille le nombre d'appels inter-services ([ADR-0006](0006-communication-rest-synchrone.md)).
- **Suivi** : si l'équipe se réduit ou si le coût d'exploitation devient trop élevé, regrouper des
  services est possible, car le découpage interne en paquets est identique partout. Le chemin inverse
  (extraire des services d'un monolithe) aurait été plus coûteux.
