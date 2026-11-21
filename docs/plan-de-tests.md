# Stratégie et plan de tests : Alumni UASZ

| | |
|---|---|
| **Ticket** | ALUM-66 (parent : ALUM-7 Qualité) |
| **Statut** | Brouillon pour relecture de l'équipe |
| **Version** | 0.1 du 17 novembre 2026 |
| **Auteur** | Ernest Urbain Diatta |
| **Périmètre** | 5 microservices back, front Angular, contrats OpenAPI |

> Ce document est le plan de référence pour tester toute la plateforme. Les valeurs marquées
> **(proposé)** sont des propositions à discuter et à valider par l'équipe avant publication.

---

## 1. Objectif

Se mettre d'accord sur **ce que l'on teste, avec quels outils, à quel niveau d'exigence, et qui s'en
occupe**, pour que chaque Pull Request apporte la preuve que la plateforme fonctionne et respecte
ses contrats.

Ce plan répond à quatre questions :

1. Quels **types de tests** ? (section 3)
2. Avec quels **outils** ? (section 3)
3. Quels **seuils de couverture** ? (section 5)
4. Qui est **responsable** de quoi ? (section 9)

## 2. État des lieux (constaté dans le dépôt)

| Élément | Constat |
|---|---|
| Back | 5 microservices **Java 21 / Spring Boot 4.1**, Maven (`mvnw`), Flyway, PostgreSQL, JWT (secret HMAC partagé, émis par `user-service`) |
| Front | **Angular 21**, tests avec **Vitest 4** (`@vitest/coverage-v8`, jsdom), 43 fichiers `.spec.ts` |
| Contrats | 5 fichiers OpenAPI dans `contracts/` (**72 opérations** au total), copiés dans `contrat/` côté front |
| Tests back existants | **`user-service` seulement** : environ 230 méthodes `@Test` (services, contrôleurs, JWT, sécurité, migrations). Les 4 autres services n'ont **aucun test** |
| Tests d'intégration | Un seul test de migration PostgreSQL, qui dépend d'une base de dev (variable `SPRING_DATASOURCE_URL`) et qui est **ignoré sinon**. **Testcontainers n'est pas encore utilisé** |
| Tests de bout en bout | Aucun outil en place (ni Selenium, ni Playwright, ni Cypress) |
| Qualité | SonarQube (local et workflow `sonar.yml`), CI GitHub Actions avec contrôle `spis-check` des contrats |
| Contrats | Les 5 YAML du back et du front sont **identiques** aujourd'hui. Le `CHANGELOG.md` du front est déjà en retard sur celui du back (entrées `user-service` absentes) |

**Conséquence** : l'effort principal porte sur les 4 services sans tests, l'intégration réelle avec
PostgreSQL, la vérification des contrats et un petit nombre de parcours de bout en bout.

## 3. Pyramide de tests

| # | Niveau | Ce qu'il vérifie | Outils | Quand il tourne |
|---|---|---|---|---|
| 1 | **Unitaire back** | Une classe isolée : services, règles métier, mappers, utilitaires, JWT | JUnit 5, Mockito, AssertJ (inclus dans `spring-boot-starter-test`) | Chaque `mvn test`, chaque PR |
| 2 | **Contrôleur / sécurité back** | Les routes d'un contrôleur : codes HTTP, validation, rôles (401 / 403), format d'erreur | `@WebMvcTest`, MockMvc, `spring-security-test` (déjà utilisés dans `user-service`) | Chaque PR |
| 3 | **Intégration back** | Le service avec une **vraie base PostgreSQL** : migrations Flyway, requêtes JPA, contraintes (ex. `uq_users_email`) | Spring Boot Test + **Testcontainers** PostgreSQL (`@ServiceConnection`) | Chaque PR (Docker disponible sur le runner CI) |
| 4 | **Contrat** | Les réponses réelles d'un service sont **conformes à son fichier OpenAPI** ; les contrats ne changent pas sans que la rupture soit visible | Validation OpenAPI dans MockMvc (ex. `swagger-request-validator`), `openapi-diff`, job `spis-check` existant | Chaque PR touchant `contracts/` ou un contrôleur |
| 5 | **Unitaire / composant front** | Services, composants, garde de routes, intercepteurs (rafraîchissement du jeton) | Vitest, jsdom, Angular TestBed | Chaque PR front |
| 6 | **Bout en bout** | Un parcours complet utilisateur, navigateur → front → back → base | Selenium (WebDriver) + JUnit 5 sur la stack lancée en Docker **(proposé)** | Avant livraison de sprint et sur la branche `develop` |
| 7 | **Non fonctionnel ciblé** | Sécurité par rôle, pagination, limitation de débit | Réutilise les niveaux 2 et 3 (pas d'outil dédié) | Chaque PR |

### Règles de la pyramide

- **Beaucoup** de tests unitaires (niveaux 1 et 5), **peu** de tests de bout en bout (niveau 6) : ils sont lents et fragiles.
- Un bug trouvé en bout en bout doit donner **un test de niveau inférieur** qui le reproduit.
- On **ne teste pas le code généré** (`spis/`, `src/app/api/generes/`) : on teste le code qui l'utilise.
- Nommage **(proposé)** : `*Test` pour les tests unitaires et de contrôleur (Surefire), `*IT` pour l'intégration avec Docker (Failsafe), afin de pouvoir lancer les uns sans les autres.

### Ce qui est hors périmètre pour l'instant

- Tests de charge et de performance (à planifier plus tard, une fois les services stabilisés).
- Tests d'accessibilité et de compatibilité multi-navigateurs au-delà de Chrome.
- Tests RGPD de bout en bout (export, suppression propagée) : les fonctions ne sont pas encore implémentées (voir `docs/architecture.md`).

## 4. Tests de contrat (Contract First)

Les contrôleurs implémentent des interfaces générées depuis les contrats : une **signature** qui
s'écarte du contrat ne compile plus. Mais la compilation ne vérifie ni les codes HTTP, ni la forme des
réponses, ni les droits d'accès. Les tests de contrat comblent ce vide :

1. **Conformité des réponses** : pour chaque opération, un test appelle le contrôleur et valide la
   réponse (statut, schéma) contre le YAML.
2. **Couverture du contrat** : pour chaque opération, au minimum **un cas passant et un cas d'erreur**
   (400, 401, 403, 404 ou 409 selon le contrat). Cible : **100 % des 72 opérations**.
3. **Parité des deux copies** : un contrôle en CI vérifie que `contracts/*.yaml` (back) et
   `contrat/*.yaml` (front) restent identiques. Aujourd'hui elles le sont, mais rien ne le garantit.
4. **Ruptures de compatibilité** : ajout d'`openapi-diff` en CI (déjà prévu comme suivi dans l'ADR-0002).
5. **Mock du front** : `scripts/mock-api.js` est écrit à la main. Il doit être revérifié à chaque
   changement de contrat, ou remplacé par un mock généré.

## 5. Seuils de couverture (proposé)

Les chiffres actuels de couverture sont à mesurer avec SonarQube avant de figer ces seuils.

| Périmètre | Seuil | Remarque |
|---|---|---|
| **Code neuf** (toutes les PR, back et front) | **≥ 80 %** lignes | Porté par le Quality Gate Sonar |
| Back : `user-service` | **≥ 80 %** global | Base de tests déjà solide |
| Back : 4 autres services | Palier 1 : **≥ 50 %**, puis palier 2 : **≥ 70 %** | Partent de 0 % |
| Back : couches `service`, `security`, `jwt`, `utils` | **≥ 80 %** | Là où se trouve la logique métier |
| Front | **≥ 60 %** global, **≥ 80 %** sur `services/` et `utilitaires/` | À ajuster après mesure |
| Contrat | **100 %** des opérations testées (cas passant + cas d'erreur) | Voir section 4 |

**Exclusions de couverture** : code généré (`spis/`, `src/app/api/generes/`), classes
`*Application`, `package-info.java`, `app.config.ts`, fichiers `*.routes.ts` (déjà exclus côté front).

**Quality Gate Sonar sur le code neuf (proposé)** : couverture ≥ 80 %, aucune faille ni bug bloquant ou
critique, duplication ≤ 3 %. Une PR qui ne passe pas le gate n'est pas fusionnée.

Les seuils sont des **garde-fous, pas des objectifs en soi** : un test écrit uniquement pour monter le
pourcentage n'apporte rien. En revue, on regarde ce que le test vérifie vraiment.

## 6. Jeux de données partagés

| Besoin | Approche |
|---|---|
| Tests unitaires back | Une classe `*TestData` par service, sur le modèle de `UserTestData` (déjà dans `user-service`) : fabriques d'objets valides, modifiables test par test |
| Tests d'intégration | Les migrations Flyway créent le schéma ; les données de test sont insérées par le test lui-même, dans une base jetable (Testcontainers) |
| Tests de bout en bout | Un script de données de départ, commun, avec **un compte par rôle** : `ETUDIANT`, `ALUMNI`, `ENSEIGNANT`, `ADMINISTRATEUR` et `ENTREPRISE`, plus quelques offres, entreprises et promotions |
| Front | Des fixtures typées avec les modèles générés, alignées avec `mock-api.js` |

Règles : identifiants (UUID) **fixes et documentés** pour les données communes ; **aucune donnée
personnelle réelle** ; **aucun secret** ni mot de passe réel dans le dépôt.

## 7. Parcours de bout en bout retenus (proposé)

Tirés de `docs/diagrams/sequences.md`. On en garde peu, mais ils couvrent les chemins critiques.

| # | Parcours | Services touchés |
|---|---|---|
| E2E-1 | Inscription, confirmation par email (Mailpit en local), connexion | `user-service`, front |
| E2E-2 | Candidature d'un étudiant à une offre | `job-offer-service`, `alumni-student-company-service`, `user-service`, front |
| E2E-3 | Affichage du matching et du tableau de bord | `analytics-matching-service`, front |

**Point à trancher** : le front n'a pas d'outil de bout en bout. **Selenium** est proposé car il est
déjà maîtrisé dans l'équipe ; **Playwright** est une alternative plus simple à installer pour Angular.

## 8. Plan par service et ordre de mise en œuvre

| Service | Opérations du contrat | Tests existants | Priorité |
|---|---:|---|---|
| `user-service` | 16 | Nombreux (unitaires, contrôleurs, sécurité, migration) | Ajouter Testcontainers et les tests de contrat |
| `alumni-student-company-service` | 18 | Aucun | **1** : cœur des données (étudiants, CV, entreprises) |
| `job-offer-service` | 13 | Aucun | **2** : parcours de candidature |
| `notification-messaging-service` | 20 | Aucun | **3** : messagerie et groupes (le plus d'opérations) |
| `analytics-matching-service` | 5 | Aucun | **4** : peu de routes, mais le calcul de matching demande des tests unitaires précis |
| Front | : | 43 fichiers `.spec.ts` | Mesurer la couverture, compléter pages et services |

**Ordre proposé** (chaque étape devient un ticket) :

1. Valider ce plan et configurer le Quality Gate Sonar.
2. Socle commun : dépendance Testcontainers, `*TestData`, validation de contrat dans MockMvc.
3. Tests des services par priorité (tableau ci-dessus), un ticket par service.
4. Contrôles de contrat en CI : parité des copies, `openapi-diff`.
5. Mise en place du bout en bout et des 3 parcours.

## 9. Responsables (à compléter en réunion)

| Niveau | Responsable | Rôle |
|---|---|---|
| Unitaire et contrôleur d'un service | **[ Ernest Urbain DIATTA ]** (un par service) | Écrit et maintient les tests de son service |
| Intégration (Testcontainers) | **[ Ernest Urbain DIATTA ]** | Socle commun, aide les autres services |
| Contrat | **[ Ernest Urbain DIATTA ]** | Validation OpenAPI, `openapi-diff`, parité des copies |
| Front | **[ Ernest Urbain DIATTA ]** | Tests Vitest, mock |
| Bout en bout | **[ Ernest Urbain DIATTA ]** | Parcours E2E, données de départ |
| CI et SonarQube | **[ Ernest Urbain DIATTA ]** | Workflows, Quality Gate |
| Relecture du plan | Toute l'équipe | Validation avant publication |

Règle générale : **l'auteur d'une modification écrit ses tests**, le relecteur vérifie qu'ils existent
et qu'ils sont utiles.

## 10. Exécution et intégration continue

- **En local** : `./mvnw test` (tests rapides) et `./mvnw verify` (avec intégration, Docker requis) dans
  le dossier du service ; `npm test` pour le front ; `./sonar/sonar.sh scan` pour la qualité.
- **En CI** (GitHub Actions, déclenchée sur les microservices modifiés) : compilation, tests
  unitaires et de contrôleur, intégration, `spis-check`, couverture envoyée à Sonar. La PR ne peut être
  fusionnée que si la CI est verte.
- **Avant livraison de sprint** : tests de bout en bout sur la stack complète.

## 11. Definition of Done

Une story est terminée quand :

- [ ] le code est écrit **et ses tests aussi**, au niveau adapté de la pyramide ;
- [ ] les tests passent en local **et** en CI ;
- [ ] le **contrat OpenAPI est respecté** (et mis à jour dans les deux copies s'il change) ;
- [ ] le Quality Gate Sonar est vert (couverture du code neuf ≥ 80 %) ;
- [ ] la branche est **à jour avec `develop`**, sans conflit ;
- [ ] la PR vise `develop`, référence le ticket (ex. `ALUM-66`) et a été **relue par un autre membre** ;
- [ ] la documentation concernée est à jour.

## 12. Risques et points ouverts

| # | Sujet | Proposition |
|---|---|---|
| 1 | Docker est nécessaire en local pour Testcontainers | Séparer `*Test` et `*IT` pour pouvoir travailler sans Docker |
| 2 | Spring Boot 4 est récent : certaines bibliothèques de test peuvent ne pas être compatibles | Vérifier chaque dépendance avant de l'adopter |
| 3 | Deux copies des contrats qui peuvent diverger | Contrôle de parité en CI (section 4) |
| 4 | Le `CHANGELOG` du front est en retard sur celui du back | Le synchroniser, ou n'en garder qu'un seul |
| 5 | Le rôle `ENTREPRISE` est dans les contrats, mais `docs/architecture.md` ne parle que de 4 rôles | Mettre à jour la documentation et prévoir un compte de test `ENTREPRISE` |
| 6 | Pas encore de mesure de couverture de référence | Lancer Sonar et ajuster les seuils de la section 5 |
| 7 | Outil de bout en bout non tranché | Décision d'équipe (Selenium ou Playwright) |

## 13. Historique

| Version | Date | Changement |
|---|---|---|
| 0.1 | 2026-11-17 | Premier jet pour relecture de l'équipe |
