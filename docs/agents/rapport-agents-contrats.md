# Rapport d'expérience : agents logiciels et contrats OpenAPI (ALUM-171)

**Objectif du ticket** : mesurer ce que des agents logiciels apportent au projet en s'appuyant sur les
contrats OpenAPI (`contracts/*.yaml`), source de vérité de l'approche Contract First (ADR-0002).

**Agent utilisé** : Claude Code (agent de développement en ligne de commande, Anthropic), piloté depuis le
dépôt. Il lit les contrats et le code, écrit des scripts et des tests, les exécute et corrige.

Trois expériences ont été menées sur l'état de `develop` du 18/11/2026 :

| # | Expérience | Livrable rejouable | Résultat principal |
| --- | --- | --- | --- |
| 1 | Revue de conformité contrat <-> code | `scripts/agents/contract-conformity.py` | 2 écarts réels trouvés dès le premier passage |
| 2 | Tests générés à partir du contrat | `alumni-student-company-service/src/test/java/.../contract/` | 8 tests verts, 3 écarts au contrat révélés |
| 3 | Mise à jour du CHANGELOG et des ADR lors d'un changement de contrat | PR #26 (ALUM-83) | Documentation complète, mais erreurs humaines à relire |

---

## Expérience 1 : revue de conformité contrat <-> code

**Objectif.** Savoir, à chaque PR, où en est chaque service par rapport à son contrat, et repérer les
dérives (version, codes HTTP) sans relire tout le code.

**Outil et mise en place.** L'agent a écrit un script Python (PyYAML seulement) qui, pour chaque service :

1. liste les `operationId` du contrat et cherche la méthode de contrôleur correspondante ; il la classe en
   *implémentée*, *squelette (501)* (`NotImplementedYetException`) ou *absente* ;
2. compare la version du contrat (`info.version`) à la version du SPI utilisée par le `pom.xml` du
   service, et vérifie que ce SPI est publié dans `spis/repo` ;
3. relève les `HttpStatus.X` utilisés dans le code qui ne sont déclarés nulle part dans le contrat.

```bash
python scripts/agents/contract-conformity.py            # rapport Markdown
python scripts/agents/contract-conformity.py --strict   # code de sortie 1 si dérive de version (CI)
```

**Résultat** (sur `develop`) :

| Service | Contrat | SPI du pom | Opérations | Implémentées | Squelettes (501) | Dérive |
| --- | --- | --- | ---: | ---: | ---: | --- |
| user-service | 1.0.0 | 1.1.0 | 16 | 16 | 0 | contrat en 1.0.0, service compilé contre le SPI 1.1.0 |
| alumni-student-company-service | 1.0.0 | 1.0.0 | 18 | 2 | 16 | aucune |
| job-offer-service | 1.0.0 | 1.0.0 | 13 | 0 | 13 | aucune |
| analytics-matching-service | 1.0.0 | 1.0.0 | 5 | 0 | 5 | aucune |
| notification-messaging-service | 1.0.0 | 1.0.0 | 20 | 0 | 20 | aucune |

Écarts trouvés :

- **Version du contrat user-service non incrémentée.** Le `CHANGELOG` annonce le SPI `user-service-spi`
  en 1.1.0 et le service l'utilise, mais `info.version` du contrat est resté à `1.0.0`.
- **403 non déclaré** dans le contrat d'alumni-student-company-service, alors que
  `AlumniExperienceService` le renvoie (ajout d'une expérience par un autre Alumni).

Le tableau donne aussi une vue d'avancement utile au Scrum : 18 opérations sur 72 sont implémentées.

**Limites.**

- Le contrôle des codes HTTP est fait **au niveau du service**, pas de l'opération : un 404 déclaré sur
  une opération masque un 404 non déclaré sur une autre. L'expérience 2 a trouvé deux 404 que ce script
  ne voit pas.
- La détection repose sur des conventions du projet (contrôleurs dans `controller/`, squelettes qui lèvent
  `NotImplementedYetException`). Une implémentation qui s'en écarte serait mal classée.
- Le script ne vérifie ni les schémas des corps, ni la sécurité déclarée.

---

## Expérience 2 : tests générés à partir du contrat

**Objectif.** Écrire des tests à partir du contrat **seul**, sans s'inspirer du code, pour vérifier que
l'implémentation respecte ce qui est promis au frontend. Cible : `GET` et `POST /alumni/{id}/experiences`
(ALUM-61), seule fonctionnalité déjà codée d'alumni-student-company-service.

**Outil et mise en place.** L'agent a écrit :

- `OpenApiContract` : lit le YAML du contrat pendant les tests, donne les codes déclarés d'une opération,
  les champs obligatoires et les formats d'un schéma, fabrique un exemple valide, et vérifie qu'un corps
  JSON est conforme à un schéma (types, `required`, formats `uuid` / `date` / `date-time`, `enum`,
  propriétés non déclarées) ;
- `AlumniExperiencesContractTest` : contrôleur et service réels, seuls les dépôts et le décodage du jeton
  sont simulés. Les cas sont **générés depuis le contrat** : un test par champ obligatoire de
  `WorkExperienceRequest` (3), un test par champ au format `date` (2). Chaque réponse est contrôlée :
  code déclaré pour l'opération, corps conforme à `WorkExperience` ou `ErrorResponse`.

Si le contrat change (nouveau champ obligatoire, nouveau champ date), les tests suivent sans être modifiés.

**Résultat** : 11 tests.

- **8 verts** : 201 avec un `WorkExperience` conforme, 200 avec un tableau conforme, 400 au format
  `ErrorResponse` pour chacun des 3 champs obligatoires et des 2 dates mal formées, 401 sans jeton.
- **3 écarts au contrat** (le code est correct, c'est le contrat qui est incomplet) :

| Opération | Situation | Code renvoyé | Codes déclarés par le contrat |
| --- | --- | --- | --- |
| `POST /alumni/{id}/experiences` | un autre Alumni ajoute une expérience | **403** | 201, 400, 401 |
| `POST /alumni/{id}/experiences` | Alumni inconnu | **404** | 201, 400, 401 |
| `GET /alumni/{id}/experiences` | Alumni inconnu | **404** | 200, 401 |

Ces 3 tests sont laissés dans le code, désactivés (`@Disabled`) avec la correction à faire, pour ne pas
bloquer la CI. Ils seront réactivés quand le contrat sera complété.

**Limites.**

- Il faut quand même connaître le code pour simuler les dépôts (quelles méthodes appeler) : la génération
  porte sur les cas et les vérifications, pas sur la mise en place.
- Le validateur de schéma est minimal (pas de `oneOf` / `allOf`, pas de `minLength`, pas de `pattern`).
- Les règles métier absentes du contrat (une seule expérience courante, date de fin après la date de
  début) ne peuvent pas être générées : elles restent couvertes par les tests écrits à la main
  (`AlumniExperienceServiceTest`).

---

## Expérience 3 : CHANGELOG et ADR mis à jour lors d'un changement de contrat

**Objectif.** Quand un contrat change, ne rien oublier : contrat, numéro de version, SPI, CHANGELOG, ADR,
diagrammes, configuration.

**Outil et mise en place.** Pendant ALUM-83 (PR #26), l'agent a ajouté `POST /internal/profiles` au contrat
d'alumni-student-company-service, puis a enchaîné sans consigne supplémentaire :

- `info.version` passé à 1.2.0 et SPI régénéré et publié (`spis/build.sh`) ;
- entrée 1.2.0 dans `contracts/CHANGELOG.md`, avec la collision de version repérée sur la branche AL-28
  (qui publie aussi un SPI 1.1.0) et la consigne de régénération pour la seconde PR fusionnée ;
- ADR-0010 (jeton de service) et ADR-0006 (politique de résilience) mis à jour, diagramme de séquence de
  l'inscription complété ;
- filtres de la CI mis à jour pour relancer les tests de user-service quand ce SPI change.

**Résultat.** La PR #26 est arrivée complète en revue, CI verte (10 checks).

**Limites constatées.**

- **Travail parallèle non vu.** La première version (PR #18) a été écrite sur une branche ancienne ;
  l'agent n'a pas vu que la PR #22 (ALUM-28) avait entre-temps fusionné sa propre validation JWT. Le
  relecteur l'a signalé et il a fallu tout reprendre à partir de `develop`. Un `git fetch` et un regard
  sur les PR ouvertes avant de commencer auraient évité ce travail.
- **Chiffres faux dans un message de commit** : « 21 nouveaux tests » au lieu de 24. Les résumés produits
  par l'agent doivent être relus comme ceux d'un humain.
- Les décisions de découpage (deux PR, ordre de fusion avec la PR #21) ont été prises par le développeur
  et le relecteur, pas par l'agent.

---

## Bilan

| Apport | Constat |
| --- | --- |
| Détection d'écarts | Les expériences 1 et 2 ont trouvé 4 écarts réels en quelques minutes, invisibles en relecture classique. |
| Rejouabilité | Le script et les tests restent dans le dépôt ; ils resservent à chaque PR. |
| Documentation | L'agent tient CHANGELOG, ADR et configuration à jour sans oubli, si on le lui demande dans le même lot de travail. |
| Limites | Il ne voit que ce qu'il lit : branches parallèles, décisions d'équipe et chiffres de synthèse restent à vérifier par un humain. |

## Recommandations

1. **Compléter le contrat d'alumni-student-company-service** : ajouter 403 et 404 à `addAlumniExperience`,
   404 à `getAlumniExperiences`, puis réactiver les 3 tests désactivés.
2. **Passer le contrat user-service en `1.1.0`** pour l'aligner sur le SPI publié.
3. **Ajouter le script à la CI** (`--strict`), au moins sur les PR qui touchent `contracts/` ou un `pom.xml`.
4. **Réutiliser `OpenApiContract`** dans chaque service quand ses opérations seront implémentées.
