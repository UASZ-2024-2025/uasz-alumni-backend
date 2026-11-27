# Rapport d'utilisation de l'IA dans le développement

> Livrable n°10 · Ticket ALUM-194 · Sources : [journal d'utilisation de l'IA](journal-ia.md), PR et tickets Jira
> du projet, [rapport des agents sur les contrats](../agents/rapport-agents-contrats.md) (ALUM-171).
>
> **État** : la partie back est rédigée ; les parties front et devops attendent les entrées de leurs équipes
> dans le journal (sections marquées *À compléter*).

## 1. Objet et méthode

Ce rapport fait le bilan de l'usage d'outils d'intelligence artificielle pendant le projet Alumni UASZ : à quoi ils
ont servi, ce qu'ils ont apporté, les erreurs qu'ils ont faites, et les règles que l'équipe en tire.

Il s'appuie sur des faits vérifiables : chaque usage cité renvoie à un ticket, une PR ou un fichier du dépôt. Le
[journal](journal-ia.md) recense les usages au fil du projet ; ce rapport en fait la synthèse.

## 2. Outils utilisés

| Équipe | Outil | Usage principal |
| --- | --- | --- |
| Back | **Claude Code** (Anthropic), agent de développement en ligne de commande | Code, tests, documentation, revue de branches, suivi Jira et GitHub |
| Front | *À compléter* | *À compléter* |
| Devops | *À compléter* | *À compléter* |

Côté back, l'agent travaille dans le dépôt : il lit le code et les contrats, lance Maven, les tests et les scripts,
démarre au besoin le service sur une base PostgreSQL jetable (Docker), et passe par Jira et GitHub pour les tickets
et les PR. Un garde-fou lui interdit certaines actions, comme fusionner une PR ou changer l'état de certains
tickets : le développeur les fait lui-même.

## 3. Usages par activité (équipe back)

| Activité | Ce que l'IA a fait | Ce que l'humain a fait | Exemple |
| --- | --- | --- | --- |
| Documentation d'architecture | Rédaction de `architecture.md`, des ADR et des diagrammes Mermaid ; génération du PDF | Alignement sur le cahier des besoins, relecture des ADR | ALUM-13, `docs/` |
| Contrats et SPIs | Modification des contrats, incrément de version, publication des SPIs, CHANGELOG | Décision des changements de contrat en équipe | PR #26, #37, #41 |
| Code des services | Endpoints, services, migrations Flyway, sécurité | Choix de conception, découpage des PR | ALUM-83, ALUM-88 |
| Tests | Tests unitaires et web, tests générés depuis le contrat | Choix des cas métier à couvrir | ALUM-171, ALUM-179 |
| Vérification réelle | Lancement du service sur PostgreSQL, appels HTTP avec un vrai jeton, `EXPLAIN` | Décision de tester contre la vraie base | PR #41 |
| Revue | Analyse d'une branche d'un autre membre ; prise en compte des remarques des relecteurs | Revue humaine obligatoire avant fusion | ALUM-179, PR #35, #36, #37 |
| Suivi de projet | Commentaires Jira, descriptions de PR, journal d'avancement | Fusions, priorités, échanges avec l'équipe | ALUM-88, ALUM-179 |

## 4. Exemples avant / après

### 4.1 Un 403 qui sortait en 401 (ALUM-88, PR #37 puis #41)

- **Avant** : l'agent avait écrit les endpoints des promotions et leurs tests web. Les tests vérifiaient qu'un
  étudiant reçoit 403 en créant une promotion : ils étaient verts, la PR a été fusionnée.
- **Après** : en lançant le vrai service sur PostgreSQL pour traiter les remarques de revue, l'agent a constaté une
  réponse **401**. En cause : le refus passait par la page d'erreur interne, où le filtre JWT ne s'exécute pas. Les
  tests MockMvc ne reproduisaient pas ce chemin. Le correctif (PR #41) concerne aussi `/companies` et `/alumni`.
- **Leçon** : des tests verts écrits par l'IA ne prouvent pas le comportement réel. Il faut au moins un essai sur le
  service démarré.

### 4.2 Écarts entre contrat et code (ALUM-171)

- **Avant** : les écarts entre contrats OpenAPI et code n'étaient repérés qu'en relecture, au hasard.
- **Après** : un script écrit par l'agent (`scripts/agents/contract-conformity.py`) et des tests générés depuis le
  contrat ont trouvé 4 écarts réels (version de contrat non incrémentée, codes 403 et 404 non déclarés). Le script
  reste dans le dépôt et resservira à chaque PR.

### 4.3 Revue d'une branche avant de coder (ALUM-179)

- **Avant** : une branche de matching existait déjà, compilait et passait ses tests.
- **Après** : l'analyse a montré que l'appel au service des étudiants partait sans jeton. Une fois déployé, il aurait
  renvoyé 401 et le service aurait répondu « étudiant introuvable » pour tout le monde. Elle a aussi montré que le
  score ne tenait compte que des compétences. Le travail a été réparti pour éviter un doublon : le calcul du score
  est devenu une classe à part (PR #65), réutilisable par l'autre branche.

### 4.4 Documentation tenue à jour (ALUM-83, ALUM-13)

- **Avant** : un changement de contrat oubliait facilement le CHANGELOG, les ADR ou le PDF de remise.
- **Après** : dans le même lot de travail, l'agent a mis à jour contrat, version, SPI, CHANGELOG, ADR-0006 et
  ADR-0010, diagramme de séquence et PDF. La PR #26 est arrivée complète en revue.

## 5. Erreurs et limites constatées

| Problème | Exemple | Parade adoptée |
| --- | --- | --- |
| Travail parallèle non vu | PR #18 écrite sur une branche ancienne, ignorant une validation JWT déjà fusionnée | Partir d'un `develop` à jour et regarder les PR ouvertes avant de commencer |
| Faux sentiment de sécurité des tests | 403 vert en test, 401 en réel (§ 4.1) | Essai sur le service démarré pour toute règle de sécurité |
| Chiffres de synthèse faux | « 21 nouveaux tests » au lieu de 24 dans un message de commit | Relire les résumés de l'IA comme ceux d'un humain |
| Hypothèses non vérifiées | Migration V8 non testée sur PostgreSQL à la première PR | L'écrire dans la PR, puis la vérifier dès que l'environnement le permet |
| Environnement local | Chemins réécrits par Git Bash sous Windows (`/v1`), JDK 17 par défaut au lieu de 21 | Notes dans le suivi du projet, commandes documentées |
| Décisions d'équipe | Ordre de fusion des PR, répartition des tickets | Restent humaines : l'IA propose, l'équipe décide |

## 6. Règles d'usage retenues

1. **L'humain reste responsable** de ce qui est fusionné : revue d'un autre membre obligatoire, fusion faite par une
   personne.
2. **Vérifier sur le réel** ce qui touche à la sécurité, aux migrations et aux appels entre services, pas seulement
   avec des tests simulés.
3. **Partir d'un état à jour** (`git fetch`, PR ouvertes, tickets en cours) avant de confier une tâche à l'IA.
4. **Ne jamais donner de secret** à l'outil ; utiliser des valeurs de test générées pour les essais locaux.
5. **Garder les traces** : scripts, tests et documentation produits restent dans le dépôt, et le journal est tenu au
   fil de l'eau.

## 7. Bilan

| | Back | Front | Devops |
| --- | --- | --- | --- |
| Gains | Documentation complète et à jour ; écarts contrat / code détectés ; tests plus nombreux ; revue de branche avant de coder | *À compléter* | *À compléter* |
| Risques | Tests trompeurs, travail parallèle non vu, chiffres de synthèse à vérifier | *À compléter* | *À compléter* |

Côté back, l'IA a surtout accéléré le travail répétitif (documentation, contrats, tests) et aidé à trouver des
défauts difficiles à voir en relecture. Ses erreurs ont toutes été rattrapées par la revue humaine ou par un essai
sur le service réel : ces deux garde-fous sont la condition de son usage.

## Annexe : sources

- [Journal d'utilisation de l'IA](journal-ia.md)
- [Rapport des agents sur les contrats OpenAPI](../agents/rapport-agents-contrats.md) (ALUM-171)
- [Score de matching](../matching.md) (ALUM-179)
- PR citées : #18, #26, #27, #30, #35, #36, #37, #41, #65
