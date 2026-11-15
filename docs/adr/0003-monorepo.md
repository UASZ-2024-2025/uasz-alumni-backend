# ADR-0003 : Monorepo unique pour les services, les contrats et l'infrastructure

- **Statut** : Accepté
- **Date** : 2026-11-01
- **Décideurs** : équipe devops
- **Ticket** : AL-2

## Contexte

Le projet comprend 5 services, des contrats partagés, des SPIs générés, un Dockerfile commun, une
infrastructure Kubernetes et des workflows CI/CD. Au départ, l'infrastructure était pensée en dépôts
séparés (voir `alumni-uasz-infra/README.md`, « dépôts frères »). L'équipe est petite et les services
évoluent souvent en même temps, par exemple quand un contrat change.

## Options étudiées

1. **Un dépôt par service** (polyrepo)
   - Avantages : droits et historique séparés ; CI simple par dépôt.
   - Inconvénients : une modification transverse (contrat + service + infra) demande plusieurs PR
     synchronisées ; les SPIs doivent être publiés sur un registre externe ; la configuration est
     dupliquée.
2. **Monorepo**
   - Avantages : une modification transverse tient dans **une seule PR, atomique** ; outillage partagé
     (Dockerfile, Sonar, workflows) ; les SPIs sont accessibles par un chemin relatif ; une seule
     source de vérité.
   - Inconvénients : la CI doit éviter de tout reconstruire à chaque commit ; le dépôt grossit.

## Décision

Nous retenons un **monorepo**. L'indépendance des services est préservée par :

- un dossier autonome par service (son `pom.xml`, son `Makefile`, son `docker-compose.yml`, ses manifestes
  `k8s/`) ;
- une **CI sélective** (`dorny/paths-filter`) : seuls les services modifiés sont construits et déployés.

## Conséquences

- **Positives** : un changement de contrat, sa régénération et son adoption par les services passent en
  une seule revue. L'outillage n'est écrit qu'une fois.
- **Négatives / risques**
  - Tout le monde a accès à tout le code → la discipline de revue par PR remplace la séparation des
    droits.
  - Le README d'`alumni-uasz-infra` parle encore de dépôts séparés → à mettre à jour.
- **Suivi** : si le nombre de services ou d'équipes augmente fortement, réévaluer (CODEOWNERS par
  dossier dans un premier temps).
