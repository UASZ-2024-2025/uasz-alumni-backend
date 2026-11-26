# Cahier de recette – Sprint 3

Ce cahier donne, pour chaque démo du Sprint 3 (23 – 30 nov., 31 tickets), un scénario pas à pas avec son résultat attendu, à jouer devant l'équipe pour valider la livraison.

## 1. Environnement de recette

- **Back :** dépôt `uasz-alumni-backend`, démarré avec `docker compose up` ou, service par service, make setup une fois puis make up dans chaque dossier (cinq services, ports 8081 à 8085 ; e-mails de test via Mailpit). À confirmer avec l'équipe : la commande exacte du seed (docker compose ou make). Le seed (ALUM-78) charge les données de démonstration au démarrage.
- **Front :** dépôt `Alumni-UASZ-frontend`, `npm install` puis `npm start`, application sur `http://localhost:4200`. En recette finale, utiliser l'environnement de démonstration déployé (ALUM-190).
- **Swagger UI :** une par service (`http://localhost:808X`), utile pour les scénarios « serveur » (appels directs, codes 401/403/404).
- **Navigateur :** Chrome récent, plus un second profil ou une fenêtre privée pour jouer deux rôles en parallèle.

## 2. Comptes de test

Mot de passe commun : `password123`. Ces comptes sont documentés dans le README du front (utilisés avec le faux back) ; leur présence dans le seed du vrai back (ALUM-78) est à vérifier au début de la recette.

| Rôle | Compte |
| --- | --- |
| ÉTUDIANT | `etudiant@uasz.sn` |
| ALUMNI | `alumni@uasz.sn` |
| ENTREPRISE (recruteur) | `entreprise@uasz.sn` |
| ENSEIGNANT | `enseignant@uasz.sn` |
| ADMINISTRATEUR | `admin@uasz.sn` |

## 3. Conventions du cahier

- Chaque scénario indique : tickets couverts, rôle, prérequis, étapes numérotées, résultat attendu, et une case de verdict (OK / KO / Non joué) dans le tableau final.
- Les scénarios « serveur » se jouent dans Swagger UI ou avec un client HTTP. Les scénarios « écran » se jouent dans l'application.
- **Réserve de statut :** au 26 nov., plusieurs tickets ne sont pas encore terminés (En revue : ALUM-243, 256, 179, 180, 181 ; En cours : ALUM-183, 257, 264 ; À faire : ALUM-268, 269, 188). Leur scénario est écrit, mais il n'est joué que si le ticket est livré à la date de la recette.

## Scénario 1 – Jeu de données de démonstration (ALUM-78)

**Tickets :** ALUM-78 (Terminé), sous-tâches ALUM-79 à 82 à faire · **Type :** serveur + connexion par rôle · **Rôles :** tous

**Prérequis :** dépôt `uasz-alumni-backend` à jour sur `develop`, Docker lancé.

1. Repartir d'un environnement vide : `docker compose down -v`, puis `docker compose up`.
2. Attendre que les cinq services répondent (ports 8081 à 8085, logs sans erreur).
3. Dans le front, se connecter successivement avec les cinq comptes de test et ouvrir la page d'accueil de chaque rôle.
4. En tant qu'étudiant, ouvrir la liste des offres ; en tant que recruteur, ouvrir ses offres ; en tant qu'administrateur, ouvrir la liste des utilisateurs et des entreprises.
5. Noter les effectifs affichés (promotions, Alumni, étudiants, entreprises, offres).
6. Relancer le seed sans vider les volumes (redémarrer les services ou rejouer le script), puis comparer les effectifs.
7. Vérifier un lien entre services : un étudiant du seed a le même identifiant dans le service des profils et dans le service des offres (candidatures, propositions).

**Résultat attendu**

- Après `docker compose up`, des étudiants, Alumni, entreprises et offres de démo existent.
- Chaque rôle se connecte et voit des données réalistes.
- Après la relance du seed (étape 6), les effectifs sont identiques : aucun doublon.
- Les identifiants sont cohérents d'un service à l'autre.

## Scénario 2 – Recherche d'offres avec filtres (ALUM-152)

**Ticket :** ALUM-152 (Terminé), tests ALUM-153 à 155 à faire · **Type :** serveur · **Rôle :** étudiant

**Prérequis :** seed chargé, jeton JWT de `etudiant@uasz.sn` (connexion puis « Authorize » dans Swagger UI du `job-offer-service`, port 8083).

1. Appeler `GET /offers` sans filtre.
2. Appeler `GET /offers` avec un mot-clé dans `query` (un mot présent dans le titre d'une offre du seed).
3. Ajouter `type`, puis `location`, puis `remoteType`, un filtre à la fois, en notant le nombre de résultats.
4. Filtrer par `companyId` (identifiant d'une entreprise du seed).
5. Filtrer par `status` fermé ou équivalent pour voir les offres non ouvertes.
6. Combiner trois filtres (par exemple `type` + `location` + `remoteType`).
7. Utiliser `page` et la taille de page pour demander la deuxième page d'une recherche qui a plus de résultats que la taille de page.
8. Appeler `GET /offers` sans jeton (accès selon la décision de la revue des contrats, ALUM-14).

**Résultat attendu**

- Sans filtre, seules les offres OPEN sont renvoyées, paginées.
- Chaque filtre réduit la liste de manière cohérente avec les données du seed.
- La combinaison de filtres renvoie exactement l'intersection, toujours paginée (total, page, taille).
- La page 2 contient les résultats suivants, sans doublon avec la page 1.
- L'appel sans jeton est refusé ou accepté conformément au contrat.

## Scénario 3 – Propositions directes d'offres (ALUM-174)

**Ticket :** ALUM-174 (Terminé) · **Type :** serveur + notification · **Rôles :** entreprise (recruteur), étudiant

**Prérequis :** seed chargé, une offre de `entreprise@uasz.sn`, deux sessions (recruteur et étudiant), `notification-messaging-service` démarré.

1. Connecté en recruteur, appeler `POST /proposals` pour envoyer une offre à l'étudiant de test.
2. Appeler `GET /proposals` en recruteur : la proposition est listée au statut PENDING.
3. Connecté en étudiant, ouvrir ses notifications : une notification NEW_JOB_PROPOSAL est présente.
4. Toujours en étudiant, appeler `GET /proposals` : la proposition reçue est visible.
5. Appeler `PATCH /proposals/{id}/status` avec ACCEPTED (répéter avec une seconde proposition et REJECTED).
6. Revenir en recruteur, `GET /proposals` : le nouveau statut est visible.
7. Se connecter avec un autre étudiant ou un autre compte et tenter le même `PATCH` sur cette proposition.
8. Expiration : vérifier une proposition dont la date est dépassée (fournie par le seed ou créée avec une échéance courte, à confirmer avec l'équipe back).

**Résultat attendu**

- L'étudiant est notifié (NEW_JOB_PROPOSAL) et peut répondre.
- Le recruteur voit la réponse (ACCEPTED ou REJECTED).
- Seul l'étudiant destinataire peut répondre : l'autre compte est refusé (403).
- Une proposition périmée passe au statut EXPIRED automatiquement.

*L'écran associé (ALUM-175) n'est pas dans le Sprint 3 : ce scénario se joue côté API.*

## Scénario 4 – Recommandation d'un étudiant par un Alumni (ALUM-119)

**Ticket :** ALUM-119 (Terminé, PR #1), tests ALUM-121 et fusion ALUM-122 à faire · **Type :** serveur · **Rôles :** ALUMNI, étudiant

**Prérequis :** seed chargé, Swagger UI de `alumni-student-company-service` (port 8082).

1. Appeler `POST /alumni/recommendations` sans jeton.
2. Se connecter en `etudiant@uasz.sn`, rappeler le même endpoint avec ce jeton.
3. Se connecter en `alumni@uasz.sn`, rappeler le endpoint avec un corps conforme à `RecommendationRequest` (identifiant d'un étudiant du seed, texte de recommandation).
4. Consulter le profil de l'étudiant recommandé (connecté en étudiant puis en administrateur).
5. Rappeler le endpoint avec un corps invalide (champ obligatoire absent) puis avec un étudiant inexistant.

**Résultat attendu**

- Étape 1 : refus 401.
- Étape 2 : un utilisateur qui n'est pas Alumni reçoit 403.
- Étape 3 : la recommandation est enregistrée et renvoyée (`RecommendationResponse`).
- Étape 4 : la recommandation apparaît sur le profil de l'étudiant.
- Étape 5 : erreurs claires (400 pour le corps invalide, 404 pour l'étudiant inconnu).

*L'écran correspondant (ALUM-123) n'est pas dans le Sprint 3.*

## Scénario 5 – CV étudiant : photo, modèles et PDF (ALUM-255, 256, 257, 197)

**Tickets :** ALUM-255 photo serveur (Terminé), ALUM-256 photo écran (En revue), ALUM-257 plusieurs modèles écran (En cours), ALUM-197 PDF serveur (Terminé) · **Rôle :** étudiant

**Prérequis :** seed chargé, compte `etudiant@uasz.sn` avec un CV déjà saisi (ALUM-109), une image JPEG de moins de 2 Mo, une image PNG, une image de plus de 2 Mo, un fichier d'un autre format (GIF ou PDF).

### A. Photo sur le CV

1. Ouvrir l'éditeur de CV et ajouter la photo JPEG.
2. Vérifier l'aperçu immédiat et la photo dans l'aperçu du CV (recadrage carré ou rond, sans déformation).
3. Remplacer la photo par le PNG, puis la supprimer.
4. Tenter d'envoyer l'image de plus de 2 Mo, puis le fichier GIF ou PDF.
5. Ouvrir un CV sans photo (autre compte du seed).
6. Côté API, lire la photo d'un autre étudiant avec le jeton de l'étudiant de test, puis avec celui de l'administrateur.

### B. Modèles de CV

7. Dans l'éditeur, ouvrir le sélecteur de modèle et compter les modèles proposés.
8. Choisir successivement chaque modèle et observer l'aperçu en direct.
9. Choisir un modèle, quitter l'éditeur, se déconnecter, se reconnecter, rouvrir l'éditeur.

### C. PDF du CV

10. Cliquer sur le téléchargement du PDF avec la photo et un modèle choisi.
11. Ouvrir le PDF : vérifier le modèle, la photo, le nombre de pages et l'absence de coupure au milieu d'une rubrique, pour chaque modèle.
12. Modifier le CV (ajouter une compétence), retélécharger le PDF.

**Résultat attendu**

- La photo JPEG ou PNG de 2 Mo maximum est acceptée, s'affiche immédiatement, peut être remplacée et supprimée ; un CV sans photo reste inchangé.
- Une image trop lourde ou un autre format est refusé avec un message clair (format ou taille).
- Seul le propriétaire (et l'administration) peut lire la photo ; l'autre étudiant est refusé.
- Au moins trois modèles sont proposés, l'aperçu change en direct, le choix est mémorisé à la visite suivante.
- Le PDF se télécharge, reprend le modèle et la photo choisis, tient sur une ou deux pages sans coupure au milieu d'une rubrique.
- Après la modification, `fileUrl` pointe vers un PDF à jour (la nouvelle compétence y figure).

## Scénario 6 – Synthèses et pages d'accueil par rôle (ALUM-243, 246, 249, 264, 240)

**Tickets :** ALUM-246 Alumni (Terminé), ALUM-249 entreprise (Terminé), ALUM-243 étudiant (En revue), ALUM-264 enseignant (En cours), ALUM-240 page d'accueil enseignant, écran (Terminé) · **Type :** serveur puis écran

**Prérequis :** seed chargé, les cinq comptes de test. Les chemins exacts des endpoints sont dans les contrats OpenAPI (`contracts/`) et dans Swagger UI.

Contenu attendu de la synthèse, par rôle :

| Rôle | Contenu de la synthèse | Ticket |
| --- | --- | --- |
| ÉTUDIANT | candidatures par statut, propositions reçues, notifications et messages non lus | ALUM-243 |
| ALUMNI | recommandations données, demandes, messages non lus | ALUM-246 |
| ENTREPRISE | offres actives, candidatures reçues par offre, propositions envoyées | ALUM-249 |
| ENSEIGNANT | effectifs par promotion, offres ouvertes, notifications et messages non lus | ALUM-264 |

### A. Endpoints de synthèse (répéter pour chaque rôle)

1. Appeler l'endpoint de synthèse du rôle sans jeton.
2. L'appeler avec le jeton du bon rôle.
3. L'appeler avec le jeton d'un autre rôle (par exemple l'administrateur ou l'étudiant pour la synthèse enseignant).
4. Avec deux comptes du même rôle (si le seed en contient deux), comparer les résultats.

### B. Page d'accueil enseignant (écran)

5. Se connecter en `enseignant@uasz.sn` : la page d'accueil s'affiche.
6. Vérifier les chiffres et les raccourcis, et les comparer à la réponse de l'endpoint de synthèse (ou des API statistiques, ALUM-182).
7. Provoquer les trois états : chargement (réseau ralenti dans les outils du navigateur), erreur (service arrêté), vide (compte sans données).
8. Se connecter avec un autre rôle et vérifier qu'il ne voit pas cette page d'accueil.

**Résultat attendu**

- Sans jeton : 401. Mauvais rôle : 403. Bon rôle : 200 avec les données du tableau ci-dessus.
- Les données sont celles de l'utilisateur connecté uniquement.
- La page d'accueil enseignant remplace le squelette technique, affiche des chiffres cohérents avec l'API et se compose de composants communs.
- Les états chargement, erreur et vide s'affichent correctement avec un message clair.
- Seul le rôle ENSEIGNANT voit cette page.

## Scénario 7 – Matching étudiant / offre (ALUM-179, 180, 181, 186)

**Tickets :** ALUM-179 algorithme (En revue), ALUM-180 offres pour un étudiant (En revue), ALUM-181 candidats pour une offre (En revue), ALUM-186 écrans (Terminé) · **Rôles :** étudiant, recruteur, administrateur

**Prérequis :** seed chargé avec des étudiants ayant des compétences variées et des offres OPEN ; Swagger UI de `analytics-matching-service` (port 8084).

1. Lancer les tests unitaires du service (`./mvnw test` dans `analytics-matching-service`) et vérifier les cas 100 %, 0 % et partiel.
2. Ouvrir la formule de score documentée dans `docs/`.
3. Appeler `GET /analytics/matchings/students/{studentId}` avec `minScore=50` pour un étudiant du seed.
4. Rappeler avec `minScore=0`, puis `minScore=100`.
5. Rappeler avec un `studentId` inexistant.
6. Appeler `GET /analytics/matchings/offers/{offerId}` avec `minScore=50`, avec le jeton du recruteur auteur de l'offre.
7. Rappeler avec le jeton d'un autre recruteur, puis de l'administrateur, puis avec un `offerId` inexistant.
8. Écran étudiant : ouvrir « Offres recommandées pour vous ».
9. Écran recruteur : ouvrir une de ses offres et consulter « Candidats suggérés ».

**Résultat attendu**

- Les tests couvrent 100 %, 0 % et partiel, et passent ; la formule (score de 0 à 100 selon compétences, intérêts, disponibilité) est documentée.
- Chaque résultat contient `matchScore`, `matchingSkills` et `missingSkills`.
- Seules les offres OPEN dont le score est au moins égal à `minScore` sont renvoyées, triées par score décroissant ; `minScore=0` renvoie toutes les offres OPEN.
- Un étudiant inconnu ou une offre inconnue renvoie 404.
- Les candidats renvoyés sont disponibles, triés par score, tous au-dessus de `minScore` ; l'accès est réservé à l'auteur de l'offre et à l'administrateur (autre recruteur refusé).
- Les écrans affichent le score et les compétences manquantes.

## Scénario 8 – Administration : statistiques et taux d'insertion (ALUM-182, 183)

**Tickets :** ALUM-182 statistiques du tableau de bord (Terminé), ALUM-183 rapport du taux d'insertion (En cours) · **Type :** serveur (+ tableau de bord admin existant côté front) · **Rôle :** administrateur

**Prérequis :** seed chargé ; jeton de `admin@uasz.sn` ; Swagger UI de `analytics-matching-service` (port 8084) ; un jeu de test dont on connaît le résultat (par exemple une promotion du seed dont on a compté les diplômés et ceux en emploi).

1. Appeler `GET /analytics/dashboard/stats` avec le jeton administrateur.
2. Compter dans l'application, en administrateur, les Alumni, les étudiants et les entreprises (listes d'administration) et comparer aux chiffres de la réponse.
3. Ouvrir le tableau de bord d'administration du front et comparer ses chiffres à la réponse de l'API.
4. Appeler le même endpoint avec le jeton de l'enseignant, puis de l'étudiant, puis sans jeton.
5. Appeler `GET /analytics/reports/insertion` avec `graduationYear` et `specialty` correspondant au jeu de test.
6. Calculer à la main le taux attendu (diplômés en emploi ÷ diplômés) et le comparer à la réponse.
7. Appeler le rapport sans filtre pour obtenir le taux global, puis avec le jeton de l'enseignant.

**Résultat attendu**

- La réponse du tableau de bord contient Alumni, étudiants, entreprises, taux d'insertion, stages, emplois et `updatedAt`.
- Les chiffres sont cohérents avec ceux des autres services et avec l'écran d'administration.
- Un non-administrateur reçoit 403 ; sans jeton, 401.
- Le rapport d'insertion renvoie diplômés, en emploi, taux, délai moyen et taux global ; le taux est exact sur le jeu de test connu.
- Le rapport d'insertion reste réservé à l'administrateur (l'enseignant est refusé).

*Le mode de calcul (à la demande ou avec cache) était à décider : le préciser à l'oral pendant la démo, `updatedAt` le rend visible.*

## Scénario 9 – Espace enseignant (ALUM-268, 269)

**Tickets :** ALUM-268 rapport d'écart de compétences ouvert à l'enseignant, serveur + contrat (À faire), ALUM-269 menu Statistiques et Promotions, écran (À faire) · **Rôles :** enseignant, administrateur, autres rôles

**Prérequis :** seed chargé avec des promotions et des diplômés ; front et back lancés ; ALUM-268 livré avant ALUM-269 pour l'écran Statistiques.

### A. Serveur et contrat

1. Appeler `GET /analytics/reports/skills-gap` avec le jeton de `enseignant@uasz.sn`.
2. Rappeler avec le jeton de l'administrateur.
3. Rappeler avec le jeton d'un étudiant, d'un Alumni et d'une entreprise.
4. Avec le jeton de l'enseignant, appeler `GET /analytics/dashboard/stats` et `GET /analytics/reports/insertion`.
5. Ouvrir `contracts/analytics-matching-service.yaml` (opération `getSkillsGapReport`) et `contracts/CHANGELOG.md`.

### B. Écran enseignant

6. Se connecter en `enseignant@uasz.sn` et lire le menu.
7. Ouvrir « Statistiques ».
8. Ouvrir « Promotions », puis la fiche d'une promotion et ses diplômés.
9. Saisir à la main l'adresse d'une page d'administration (utilisateurs ou entreprises).
10. Lancer `npm test` dans le front et repérer les tests de composants des deux écrans.

**Résultat attendu**

- L'enseignant et l'administrateur reçoivent 200 et le rapport ; étudiant, Alumni et entreprise reçoivent 403.
- Le tableau de bord et le rapport d'insertion restent refusés à l'enseignant (403).
- Le contrat indique les rôles ADMINISTRATEUR et ENSEIGNANT pour `getSkillsGapReport`, avec une entrée dans le `CHANGELOG.md` (version mineure).
- Le menu enseignant affiche Annuaire, Groupes, Messagerie, Notifications, plus « Statistiques » et « Promotions ».
- « Statistiques » affiche uniquement le rapport d'écart de compétences.
- « Promotions » liste les promotions et, pour chacune, ses diplômés, sans bouton de création ni de modification.
- La page d'administration est refusée (page 403) pour l'enseignant.
- Les tests de composants des deux écrans passent.

## Scénario 10 – Transverse : finitions, tests de bout en bout, livraison continue (ALUM-188, 189, 190)

**Tickets :** ALUM-188 finitions responsive, accessibilité, états (À faire), ALUM-189 tests de bout en bout (En cours), ALUM-190 livraison continue (Terminé) · **Rôles :** tous

### A. Finitions (ALUM-188)

1. Ouvrir l'application dans les outils du navigateur en mode appareil (largeur 375 px), ou sur un téléphone.
2. Parcourir les parcours clés : connexion, page d'accueil, liste des offres, éditeur de CV.
3. Provoquer une erreur (service arrêté), un chargement lent et une liste vide.
4. Naviguer au clavier (Tab, Entrée) sur la connexion et la liste des offres ; vérifier libellés et contrastes.

### B. Tests de bout en bout (ALUM-189)

5. Lancer la commande de tests de bout en bout du dépôt (Playwright ou Cypress, selon l'outil retenu) sur l'environnement Docker Compose.
6. Ouvrir la dernière exécution GitHub Actions d'une PR vers `main`.

### C. Livraison continue (ALUM-190)

7. Fusionner une PR sur `main` (ou ouvrir la dernière exécution qui l'a fait).
8. Dans GitHub Actions, vérifier la publication d'une image par service et pour le front, et ses tags (version et commit).
9. Ouvrir l'environnement de démonstration et vérifier qu'il tourne sur la nouvelle version.
10. Rechercher des secrets en clair dans les dépôts et workflows.

**Résultat attendu**

- Les parcours clés sont utilisables sur mobile ; les messages d'erreur, de chargement et de liste vide sont homogènes et clairs ; la navigation au clavier fonctionne.
- Les parcours passés en bout en bout : inscription puis connexion ; étudiant (profil, CV, candidature) ; recruteur (publier, traiter, notification) ; administrateur (tableau de bord). Ils passent en CI avant chaque livraison sur `main`.
- Un merge sur `main` publie les images (tag de version et de commit) et met à jour l'environnement de démonstration automatiquement.
- Aucun secret n'est présent dans les dépôts (ils viennent de GitHub Secrets).

## Livrables documentaires (ALUM-191 à 196)

Ces tickets ne se jouent pas comme des démos : on vérifie le livrable et son critère d'acceptation. Cocher quand c'est constaté devant l'équipe.

- [ ] **ALUM-191 Product Backlog et User Stories (À faire) :** le document final est dans `docs/`, au format « En tant que… je veux… afin de… », avec priorités MoSCoW, estimations et découpage en sprints.
- [ ] **ALUM-192 Documentation technique (En revue) :** un nouveau développeur lance le projet et ses tests en suivant uniquement ce document (prérequis, variables d'environnement, ports, Swagger UI).
- [ ] **ALUM-193 Rapport qualité (À faire) :** le rapport final est dans `docs/` avec chiffres Sonar par service, couverture et évolution par sprint.
- [ ] **ALUM-194 Rapport d'utilisation de l'IA (En cours) :** le journal est à jour et le rapport est dans `docs/`.
- [ ] **ALUM-195 Manuel utilisateur par rôle (En revue) :** un chapitre par rôle avec captures d'écran, une FAQ, relu par une personne extérieure à l'équipe.
- [ ] **ALUM-196 Rapport final, soutenance et démo répétée (À faire) :** la démo est répétée de bout en bout sur l'environnement déployé, avec un plan B en vidéo.

## Synthèse de la recette

Dix scénarios couvrent les 24 tickets fonctionnels et techniques du sprint ; les six livrables documentaires sont en checklist. Renseigner le verdict pendant la recette.

| N° | Démo | Tickets | Statut Jira au 26 nov. | Rôle | Verdict | Remarques |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Jeu de données de démo | ALUM-78 | Terminé (sous-tâches à faire) | Tous | Non joué | |
| 2 | Recherche d'offres avec filtres | ALUM-152 | Terminé | Étudiant | Non joué | |
| 3 | Propositions directes d'offres | ALUM-174 | Terminé | Entreprise, étudiant | Non joué | |
| 4 | Recommandation par un Alumni | ALUM-119 | Terminé | Alumni, étudiant | Non joué | |
| 5 | CV : photo, modèles, PDF | ALUM-255, 256, 257, 197 | Terminé, En revue, En cours, Terminé | Étudiant | Non joué | |
| 6 | Synthèses et accueils par rôle | ALUM-243, 246, 249, 264, 240 | En revue, Terminé, Terminé, En cours, Terminé | Tous | Non joué | |
| 7 | Matching étudiant / offre | ALUM-179, 180, 181, 186 | En revue (3), Terminé | Étudiant, recruteur | Non joué | |
| 8 | Statistiques et taux d'insertion | ALUM-182, 183 | Terminé, En cours | Administrateur | Non joué | |
| 9 | Espace enseignant | ALUM-268, 269 | À faire | Enseignant | Non joué | |
| 10 | Finitions, E2E, livraison continue | ALUM-188, 189, 190 | À faire, En cours, Terminé | Tous | Non joué | |
