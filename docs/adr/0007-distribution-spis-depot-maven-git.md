# ADR-0007 : Distribution des SPIs par un dépôt Maven versionné dans Git

- **Statut** : Accepté
- **Date** : 2026-11-01
- **Décideurs** : équipe devops
- **Ticket** : AL-2

## Contexte

Les SPIs générés depuis les contrats ([ADR-0002](0002-contract-first-openapi.md)) doivent être
consommables par chaque service, en local comme en CI et lors du build Docker. On veut éviter qu'un
développeur ait à régénérer les SPIs lui-même, et garantir que tout le monde compile contre la même
version.

## Options étudiées

1. **Génération dans chaque service** (plugin openapi-generator dans chaque `pom.xml`) : pas
   d'artefact à publier, mais la configuration est dupliquée 5 fois, chaque build régénère, et rien ne
   garantit que deux services utilisent la même version du contrat.
2. **Registre Maven externe** (GitHub Packages, Nexus) : c'est la solution standard en entreprise, mais
   chaque développeur et chaque build Docker doivent s'authentifier, et il faut un compte ou un serveur à
   administrer.
3. **Dépôt Maven fichier versionné dans Git** (`spis/repo/`) : les artefacts publiés sont commités, et
   les services les lisent via `../spis/repo`.

## Décision

Nous retenons l'**option 3**.

- `spis/build.sh` génère et publie dans `spis/repo/`. Le résultat est commité avec le contrat.
- Les versions sont immuables : toute modification impose une nouvelle version
  (`updatePolicy=never` côté consommateurs).
- Pour consommer une nouvelle version : `git pull`, puis aligner `alumni.spi.version`.

## Conséquences

- **Positives** : aucune authentification ni serveur ; build Docker hors ligne (le Dockerfile copie
  `spis/repo/`) ; la version du contrat et le code sont dans le même commit.
- **Négatives / risques**
  - Des binaires (`.jar`) sont versionnés dans Git, ce qui fait grossir le dépôt à chaque version.
  - On peut oublier de régénérer après une modification du contrat → le job CI `spis-check` le
    détecte.
- **Suivi** : passer sur GitHub Packages si le nombre de versions fait trop grossir le dépôt, ou si des
  consommateurs extérieurs au monorepo apparaissent.
