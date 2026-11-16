# ADR-0003 : Organisation des dépôts (backend et frontend séparés)

- **Statut** : Accepté
- **Date** : 2026-11-01 (révisé le 2026-11-15)
- **Décideurs** : équipe devops
- **Ticket** : AL-2

## Contexte

Le projet comprend 5 services, des contrats partagés, des SPIs générés, un Dockerfile commun, une
infrastructure Kubernetes, des workflows CI/CD et une application frontend. Au départ, l'infrastructure
était pensée en dépôts séparés (voir `alumni-uasz-infra/README.md`, « dépôts frères »). L'équipe est
petite et les services backend évoluent souvent en même temps, par exemple quand un contrat change.

Le projet n'est **pas un monorepo** : le frontend vit dans son propre dépôt, avec son propre cycle de
développement, et ne partage avec le backend que les contrats d'API.

## Options étudiées

1. **Un dépôt par service**
   - Avantages : droits et historique séparés ; CI simple par dépôt.
   - Inconvénients : une modification transverse (contrat + service + infra) demande plusieurs PR
     synchronisées ; les SPIs doivent être publiés sur un registre externe ; la configuration est
     dupliquée.
2. **Un dépôt unique pour tout le projet (frontend compris)**
   - Avantages : une seule source de vérité.
   - Inconvénients : mélange deux piles techniques (Java/Maven et TypeScript) et deux équipes dans la
     même CI et le même historique, alors que le frontend ne dépend du backend que par les contrats.
3. **Un dépôt backend et un dépôt frontend**
   - Avantages : côté backend, une modification transverse (contrat, services, infra) tient dans
     **une seule PR** ; outillage partagé (Dockerfile, Sonar, workflows) ; les SPIs sont accessibles par
     un chemin relatif. Le frontend garde son outillage et son rythme propres.
   - Inconvénients : un changement de contrat qui touche le frontend demande une PR dans chaque dépôt ;
     la CI backend doit éviter de tout reconstruire à chaque commit.

## Décision

Nous retenons **deux dépôts** dans l'organisation GitHub `UASZ-2024-2025` :

- **`uasz-alumni-backend`** : les 5 microservices, les contrats OpenAPI, les SPIs, le Dockerfile commun,
  l'infrastructure (`alumni-uasz-infra`), Sonar et les workflows CI/CD ;
- **`Alumni-UASZ-frontend`** : l'application frontend, qui consomme les API décrites par les contrats.

Dans le dépôt backend, l'indépendance des services est préservée par :

- un dossier autonome par service (son `pom.xml`, son `Makefile`, son `docker-compose.yml`, ses manifestes
  `k8s/`) ;
- une **CI sélective** (`dorny/paths-filter`) : seuls les services modifiés sont construits et déployés.

## Conséquences

- **Positives** : côté backend, un changement de contrat, sa régénération et son adoption par les
  services passent en une seule revue, et l'outillage n'est écrit qu'une fois. Le frontend évolue sans
  être freiné par la CI backend.
- **Négatives / risques**
  - Tous les développeurs backend ont accès à tout le code backend → la discipline de revue par PR
    remplace la séparation des droits.
  - Un changement de contrat doit être répercuté à la main dans le dépôt frontend → le `CHANGELOG` des
    contrats sert de point de synchronisation.
  - Le README d'`alumni-uasz-infra` parle encore de dépôts séparés pour l'infrastructure → à mettre à
    jour.
- **Suivi** : si le nombre de services ou d'équipes augmente fortement, réévaluer (CODEOWNERS par
  dossier dans un premier temps).
