# ADR-0002 : Approche Contract First avec OpenAPI et SPIs générés

- **Statut** : Accepté
- **Date** : 2026-11-01
- **Décideurs** : équipes back, front
- **Ticket** : AL-2

## Contexte

Cinq services et un front sont développés en parallèle par des équipes différentes. Sans contrat
stable, chaque équipe interprète les API à sa façon. Les écarts apparaissent alors tard, à
l'intégration. Le sujet impose une démarche Contract First.

## Options étudiées

1. **Code First** : on écrit les contrôleurs, puis on génère la documentation (springdoc).
   - Avantage : rapide pour un service isolé.
   - Inconvénients : le contrat change à chaque modification de code ; le front attend que le back soit
     écrit ; rien n'empêche une rupture de compatibilité.
2. **Contract First, contrat seulement documentaire** : on écrit l'OpenAPI, puis on code à la main.
   - Inconvénient : le code et le contrat finissent par diverger, car rien ne les relie.
3. **Contract First avec génération** : l'OpenAPI est la source de vérité. Les interfaces Java (`*Api`) et
   les modèles sont **générés** (openapi-generator) et les contrôleurs les implémentent.

## Décision

Nous retenons l'**option 3**.

- Les contrats sont dans `contracts/*.yaml`, avec un `CHANGELOG.md`.
- Ils sont générés en artefacts Maven `*-spi` versionnés ([ADR-0007](0007-distribution-spis-depot-maven-git.md)).
- Chaque contrôleur `implements` l'interface générée. Si le code s'écarte du contrat, **la compilation
  échoue**.
- Toute modification de contrat passe par une Pull Request : on modifie le contrat, on incrémente la
  version, on régénère et on aligne les `pom.xml`.
- La CI vérifie que les contrats génèrent des SPIs valides (job `spis-check`).

## Conséquences

- **Positives**
  - Le front peut développer contre des mocks générés depuis le contrat, avant que le back existe.
  - La documentation d'API est toujours exacte, puisque c'est elle qui produit le code.
  - Les ruptures de compatibilité sont visibles en revue de code, sur le diff du YAML.
- **Négatives / risques**
  - Il faut connaître OpenAPI et les options du générateur.
  - Une modification de contrat demande plus de cérémonie : version, régénération, PR.
  - Le nommage des classes dépend du YAML. Les schémas inline mal nommés (`inline_response_200`) ont dû
    être renommés (voir `contracts/CHANGELOG.md`).
- **Suivi** : ajouter un contrôle automatique des ruptures de compatibilité en CI (ex. `openapi-diff`)
  quand les contrats évolueront après la v1.0.0.
