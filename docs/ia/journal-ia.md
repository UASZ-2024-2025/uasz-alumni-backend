# Journal d'utilisation de l'IA

> Ticket : ALUM-194 (livrable n°10). Ce journal alimente le
> [rapport d'utilisation de l'IA](rapport-utilisation-ia.md).
> Chaque équipe (back, front, devops) y ajoute ses entrées par PR, au fil du projet.

## Comment ajouter une entrée

Une entrée par tâche où l'IA a réellement servi, dans la section de votre équipe, de la plus ancienne à la plus
récente. Recopiez le modèle :

```markdown
### AAAA-MM-JJ · ALUM-XXX · titre court

- **Outil** : nom de l'outil (assistant de code, agent, chat…)
- **Tâche confiée à l'IA** : ce qui a été demandé, en une ou deux phrases
- **Résultat** : ce qui a été produit (code, tests, doc, revue…), lien vers la PR
- **Vérifié / corrigé par l'humain** : ce que vous avez relu, testé, refusé ou réécrit
- **Gain ou problème** : temps gagné, erreur évitée, erreur introduite…
```

Règles :

- Pas de secret, de jeton, de mot de passe ni de donnée personnelle dans les entrées, ni dans ce que vous donnez à
  l'outil.
- Les erreurs de l'IA ont autant d'intérêt que ses réussites : notez-les.
- Restez factuel : un lien vers la PR ou le ticket vaut mieux qu'un long récit.

---

## Équipe back

Outil : **Claude Code** (agent de développement en ligne de commande, Anthropic), piloté depuis le dépôt. Il lit le
code, lance Maven et les scripts, et passe par les connecteurs Jira et GitHub (navigateur) pour les tickets et les
PR. Les fusions de PR et certains changements d'état Jira lui sont bloqués par son garde-fou : le développeur les
fait lui-même.

### 2026-11-07 · ALUM-13 · Documentation d'architecture

- **Outil** : Claude Code
- **Tâche confiée à l'IA** : rédiger `docs/architecture.md`, les diagrammes Mermaid et les ADR 0001 à 0009 à partir
  du code et des contrats, puis générer le PDF de remise.
- **Résultat** : `docs/`, ADR, diagrammes de composants, packages, déploiement, classes, séquences ; PDF construit
  depuis les `.md` (HTML puis Chrome sans interface).
- **Vérifié / corrigé par l'humain** : section « Exigences » réalignée sur le ticket AL-62 ; relecture des ADR.
- **Gain ou problème** : rédaction rapide et cohérente ; PlantUML n'a pas pu être installé, Mermaid a été retenu.

### 2026-11-15 → 18 · ALUM-83 · Communication inter-services

- **Outil** : Claude Code
- **Tâche confiée à l'IA** : jeton de service, timeouts, retry et notification asynchrone entre job-offer,
  notification-messaging et user-service ; tests et ADR-0010.
- **Résultat** : PR #18, puis découpée en PR #26 et #30 (fusionnées).
- **Vérifié / corrigé par l'humain** : le relecteur a demandé le découpage de la PR #18, écrite sur une branche
  ancienne qui ignorait la validation JWT fusionnée entre-temps (PR #22).
- **Gain ou problème** : code et doc complets ; travail parallèle non vu par l'agent, repris à partir de `develop`.

### 2026-11-18 · ALUM-171 · Agents et contrats OpenAPI

- **Outil** : Claude Code
- **Tâche confiée à l'IA** : trois expériences d'agents sur les contrats (revue de conformité, tests générés depuis
  le contrat, CHANGELOG et ADR).
- **Résultat** : PR #27, `scripts/agents/contract-conformity.py`, tests de contrat, rapport
  [`docs/agents/rapport-agents-contrats.md`](../agents/rapport-agents-contrats.md).
- **Vérifié / corrigé par l'humain** : choix des écarts à traiter ; deux tickets de contrat abandonnés en équipe.
- **Gain ou problème** : 4 écarts réels trouvés ; un chiffre faux dans un message de commit (21 tests au lieu de 24).

### 2026-11-19 · ALUM-88 · Promotions et diplômés

- **Outil** : Claude Code
- **Tâche confiée à l'IA** : endpoints des promotions, migration V8, contrat 1.4.0, tests ; puis corrections après
  revue.
- **Résultat** : PR #37 (fusionnée), puis PR #41 (suites de revue).
- **Vérifié / corrigé par l'humain** : relecteurs (index non utilisé, ordre des migrations avec la PR #10).
- **Gain ou problème** : les tests MockMvc écrits par l'agent étaient verts, mais le service réel lancé sur
  PostgreSQL répondait **401 au lieu de 403** ; bug trouvé et corrigé seulement en testant le vrai serveur.

### 2026-11-25 · ALUM-179 · Score de matching

- **Outil** : Claude Code
- **Tâche confiée à l'IA** : analyser la branche de matching d'un autre membre, puis écrire le calcul du score, sa
  normalisation, ses tests et sa documentation.
- **Résultat** : PR #65, [`docs/matching.md`](../matching.md), 37 tests.
- **Vérifié / corrigé par l'humain** : poids de la formule (70 / 20 / 10) à valider ; répartition du travail avec
  l'auteur de la branche de matching.
- **Gain ou problème** : l'analyse a relevé un appel inter-services sans jeton qui aurait échoué une fois déployé.

## Équipe front

*À compléter par l'équipe front (outils utilisés, entrées au format ci-dessus).*

## Équipe devops

*À compléter par l'équipe devops (outils utilisés, entrées au format ci-dessus).*
