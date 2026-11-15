# Documentation d'architecture : Alumni UASZ

Livrables n°5 et n°6 du sujet **GL5121 Architecture Logicielle**.

## Justification de l'architecture

**[architecture.md](architecture.md)** :
1. Acteurs, exigences fonctionnelles et non fonctionnelles, contraintes (alignés sur le cahier des besoins, AL-62)
2. Comparaison argumentée monolithe modulaire / microservices, et choix retenu
3. Stack technique retenue et justification
4. Composants logiciels et interfaces (contrats OpenAPI)
5. Sécurité, déploiement, CI/CD, points ouverts

## Décisions d'architecture (ADR)

**[adr/](adr/README.md)** : une fiche par décision importante, avec son contexte, les options étudiées, la
décision et ses conséquences (microservices, Contract First, monorepo, une base par service, JWT,
communication REST, distribution des SPIs, Docker/Kubernetes, stack Java/Spring).

## Diagrammes UML exigés

| Diagramme | Fichier |
| --- | --- |
| Diagramme de composants | [diagrams/composants.md](diagrams/composants.md) |
| Diagramme de packages | [diagrams/packages.md](diagrams/packages.md) |
| Diagramme de déploiement | [diagrams/deploiement.md](diagrams/deploiement.md) |
| Diagrammes de classes (par service) | [diagrams/classes.md](diagrams/classes.md) |

## Compléments

| Diagramme | Fichier |
| --- | --- |
| Cas d'utilisation par rôle | [diagrams/cas-utilisation.md](diagrams/cas-utilisation.md) |
| Séquences : inscription, candidature, matching | [diagrams/sequences.md](diagrams/sequences.md) |

## Format des diagrammes

Les diagrammes sont écrits en **[Mermaid](https://mermaid.js.org/)** dans les fichiers Markdown :
- **GitHub** et **GitLab** les affichent directement ;
- **VS Code** et **IntelliJ** les affichent dans l'aperçu Markdown ;
- pour obtenir une image (PNG ou SVG) à mettre dans un rapport, coller le bloc dans
  <https://mermaid.live> puis exporter.

Les contrats d'interface, source de vérité des API, sont dans [`../contracts/`](../contracts/).
