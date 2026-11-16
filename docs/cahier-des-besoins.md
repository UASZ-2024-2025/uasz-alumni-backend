# Cahier des besoins : plateforme Alumni UASZ

> Livrable n°1 du sujet du projet intégrateur. Base de l'architecture (GL5121 : « identifier les exigences fonctionnelles et non fonctionnelles »).
> Ticket Jira : *Rédiger le cahier des besoins (exigences fonctionnelles et non fonctionnelles)*, epic Livrables.
> Les identifiants `EF-xx` et `ENF-xx` sont les mêmes que dans [`architecture.md`](architecture.md), qui indique comment l'architecture répond à chaque exigence.

| | |
|---|---|
| Projet | Plateforme Alumni de l'Université Assane Seck de Ziguinchor (UASZ) |
| Version | 1.0, à valider par l'équipe |
| Statut | Brouillon pour relecture |

## Sommaire

1. [Contexte et problème](#1-contexte-et-problème)
2. [Objectifs et indicateurs de succès](#2-objectifs-et-indicateurs-de-succès)
3. [Périmètre](#3-périmètre)
4. [Acteurs](#4-acteurs)
5. [Exigences fonctionnelles](#5-exigences-fonctionnelles)
6. [Règles de gestion](#6-règles-de-gestion)
7. [Matrice rôles et droits](#7-matrice-rôles-et-droits)
8. [Exigences non fonctionnelles](#8-exigences-non-fonctionnelles)
9. [Contraintes](#9-contraintes)
10. [Hypothèses, risques et points ouverts](#10-hypothèses-risques-et-points-ouverts)
11. [Traçabilité avec le backlog](#11-traçabilité-avec-le-backlog)
12. [Glossaire](#12-glossaire)

---

## 1. Contexte et problème

L'UASZ forme des étudiants en informatique (Master) dont les anciens diplômés, les enseignants et les entreprises
partenaires n'ont aujourd'hui aucun espace commun. Les échanges passent par des **groupes WhatsApp et les réseaux
sociaux**, ce qui provoque :

- **l'absence d'annuaire fiable** : on ne sait pas où travaillent les diplômés ni comment les contacter ;
- **la dispersion des offres** : stages et emplois circulent par messages, sans historique ni recherche ;
- **l'absence de suivi** : un étudiant ne sait pas où en est sa candidature, une entreprise ne reçoit pas de profils qualifiés ;
- **l'absence d'indicateurs** : l'administration ne peut pas mesurer l'insertion professionnelle ni l'écart entre les
  compétences enseignées et celles demandées par le marché.

La plateforme Alumni UASZ centralise ces échanges dans une application web unique, sécurisée et adaptée à chaque profil d'utilisateur.

## 2. Objectifs et indicateurs de succès

| # | Objectif | Indicateur de succès (cible à valider) |
|---|---|---|
| O1 | Centraliser le réseau des diplômés | Nombre d'Alumni inscrits et ayant renseigné leur parcours |
| O2 | Faciliter l'accès aux stages et aux emplois | Nombre d'offres publiées, nombre de candidatures déposées |
| O3 | Mettre en relation étudiants, Alumni et entreprises | Nombre de propositions directes et de conversations ouvertes |
| O4 | Mesurer l'insertion professionnelle | Taux d'insertion par promotion et par spécialité, délai moyen d'accès au premier emploi |
| O5 | Adapter la formation au marché | Rapport d'écart de compétences disponible pour les enseignants et l'administration |

## 3. Périmètre

**Dans le périmètre (version 1.0)**
- Comptes, authentification, rôles et administration des utilisateurs.
- Profils étudiants et CV en ligne (avec PDF), profils Alumni et parcours professionnel, entreprises partenaires, promotions.
- Offres d'emploi et de stage, candidatures, propositions directes.
- Matching étudiant / offre, tableau de bord et rapports pour l'administration.
- Messagerie privée, groupes de discussion, notifications.
- Application web (ordinateur et mobile en responsive).

**Hors périmètre (version 1.0)**
- Application mobile native.
- Paiement, recrutement contractuel, signature électronique.
- Visioconférence.
- Notifications push natives : le canal est prévu dans le contrat mais son implémentation est à cadrer.
- Import automatique des données depuis d'autres systèmes de l'université.

## 4. Acteurs

| Acteur | Rôle technique | Besoin principal |
|---|---|---|
| **Visiteur** | aucun (non connecté) | Créer un compte, se connecter |
| **Étudiant** | `ETUDIANT` | Valoriser son profil et son CV, trouver un stage ou un emploi, échanger avec les Alumni |
| **Alumni** | `ALUMNI` | Rester visible du réseau, publier des offres, recommander des étudiants |
| **Enseignant** | `ENSEIGNANT` | Suivre les promotions, consulter les statistiques pour adapter la formation |
| **Entreprise partenaire** | `ENTREPRISE` | Publier des offres, consulter les profils, traiter les candidatures, contacter des étudiants |
| **Administrateur** | `ADMINISTRATEUR` | Gérer les comptes, les promotions et les entreprises partenaires, piloter la plateforme |

## 5. Exigences fonctionnelles

Priorités selon la méthode **MoSCoW** : **M** = indispensable, **S** = important, **C** = souhaitable.

### 5.1 Utilisateurs et accès (EF-01, EF-02)

| ID | Exigence | Acteurs | Priorité |
|---|---|---|---|
| EF-01a | Créer un compte (Alumni, étudiant, enseignant, entreprise) avec email et mot de passe | Visiteur | M |
| EF-01b | Activer son compte par un lien reçu par email, renvoyer ce lien si besoin | Visiteur | M |
| EF-01c | Se connecter, rester connecté (rafraîchissement automatique de la session) et se déconnecter | Tous | M |
| EF-01d | Réinitialiser un mot de passe oublié par email, changer son mot de passe | Tous | M |
| EF-02a | Consulter, filtrer (rôle, statut), créer, modifier et supprimer des comptes | Administrateur | M |
| EF-02b | Activer, désactiver ou suspendre un compte | Administrateur | M |
| EF-02c | Modifier ses propres informations (nom, téléphone, genre, date de naissance) | Tous | S |
| EF-02d | Consulter les rôles et leurs permissions | Administrateur | S |

### 5.2 Promotions, profils, CV et entreprises (EF-03 à EF-07)

| ID | Exigence | Acteurs | Priorité |
|---|---|---|---|
| EF-03 | Créer des promotions (année, spécialité) et consulter la liste de leurs diplômés | Administrateur, Enseignant | M |
| EF-04a | Renseigner son profil (spécialité, compétences, intérêts, disponibilité) et le rendre visible | Étudiant | M |
| EF-04b | Créer son CV en ligne (résumé, compétences, expériences, formations, langues) et le télécharger en PDF | Étudiant | M |
| EF-04c | Rechercher des étudiants (nom, spécialité, année, disponibilité) | Alumni, Enseignant, Entreprise, Administrateur | S |
| EF-05a | Tenir à jour son profil Alumni (promotion, entreprise, poste, LinkedIn) et son parcours professionnel | Alumni | M |
| EF-05b | Rechercher des Alumni (nom, entreprise, poste) | Étudiant et autres rôles | S |
| EF-06 | Recommander un étudiant, la recommandation apparaît sur son profil | Alumni | C |
| EF-07a | Créer et gérer la fiche de son entreprise | Alumni, Entreprise | M |
| EF-07b | Valider une entreprise comme partenaire officiel | Administrateur | M |
| EF-07c | Consulter l'annuaire des entreprises | Tous | S |

### 5.3 Offres, candidatures et propositions (EF-08 à EF-11)

| ID | Exigence | Acteurs | Priorité |
|---|---|---|---|
| EF-08a | Publier, modifier, clôturer et archiver une offre d'emploi ou de stage | Alumni, Entreprise, Administrateur | M |
| EF-08b | Rechercher des offres avec filtres (mots-clés, type, entreprise, lieu, télétravail) | Étudiant et autres rôles | M |
| EF-09a | Postuler à une offre avec son CV et une lettre de motivation, suivre ses candidatures | Étudiant | M |
| EF-09b | Consulter les candidatures reçues et en changer le statut, l'étudiant est notifié | Alumni, Entreprise | M |
| EF-10 | Proposer directement une offre à un étudiant, qui l'accepte ou la refuse | Alumni, Entreprise ; Étudiant | S |
| EF-11a | Recommander à un étudiant les offres les plus adaptées, avec score et compétences manquantes | Étudiant | S |
| EF-11b | Suggérer à un recruteur les étudiants les plus qualifiés pour son offre | Alumni, Entreprise | S |

### 5.4 Messagerie et notifications (EF-12 à EF-14)

| ID | Exigence | Acteurs | Priorité |
|---|---|---|---|
| EF-12 | Échanger en privé avec un autre utilisateur, voir l'historique et les messages non lus | Tous | M |
| EF-13 | Créer un groupe de discussion, gérer ses membres, échanger des messages | Tous | S |
| EF-14a | Recevoir des notifications dans l'application (nouveau message, changement de candidature, nouvelle proposition) | Tous | M |
| EF-14b | Recevoir certaines notifications par email | Tous | S |
| EF-14c | Marquer une notification ou toutes comme lues, voir le compteur de non-lues | Tous | M |
| EF-14d | Notifications push | Tous | C |

### 5.5 Tableau de bord et rapports (EF-15)

| ID | Exigence | Acteurs | Priorité |
|---|---|---|---|
| EF-15a | Consulter les indicateurs clés (Alumni, étudiants, entreprises, taux d'insertion, offres) | Administrateur | M |
| EF-15b | Consulter le rapport d'insertion par promotion et par spécialité | Administrateur | S |
| EF-15c | Consulter le rapport d'écart de compétences (marché / diplômés) | Administrateur, Enseignant | S |

## 6. Règles de gestion

| ID | Règle |
|---|---|
| RG-01 | Un compte nouvellement créé est au statut `EN_ATTENTE` et ne peut pas se connecter avant la confirmation de l'email. |
| RG-02 | Une adresse email ne peut être utilisée que par un seul compte. |
| RG-03 | Le mot de passe contient au moins 8 caractères et est stocké haché, jamais en clair. |
| RG-04 | Un compte `INACTIF` ou `SUSPENDU` ne peut pas se connecter. |
| RG-05 | Seul un administrateur change le statut d'un compte, et il ne peut pas modifier le sien. |
| RG-06 | Le lien de confirmation d'email et le lien de réinitialisation de mot de passe expirent après une durée limitée, et un lien de réinitialisation ne sert qu'une fois. |
| RG-07 | Un utilisateur ne modifie que ses propres informations, sauf l'administrateur. L'email n'est modifiable que par un administrateur. |
| RG-08 | Un étudiant ne peut pas postuler deux fois à la même offre, ni à une offre non ouverte. |
| RG-09 | Seul l'auteur d'une offre, ou un administrateur, peut la modifier, la clôturer ou voir les candidatures qu'elle reçoit. |
| RG-10 | Seul l'étudiant destinataire d'une proposition peut l'accepter ou la refuser. |
| RG-11 | Seul un administrateur peut déclarer une entreprise « partenaire officiel ». |
| RG-12 | Un utilisateur ne voit que ses propres notifications et ses propres candidatures. |
| RG-13 | Seuls les membres d'un groupe lisent et écrivent dans ses messages ; seul son créateur (OWNER) le modifie et y ajoute des membres. |
| RG-14 | Seul l'expéditeur d'un message privé peut le supprimer. |
| RG-15 | Une offre est clôturée automatiquement à sa date limite. |
| RG-16 | Les scores de matching vont de 0 à 100 et ne tiennent compte que des offres ouvertes et des étudiants disponibles. |

## 7. Matrice rôles et droits

Matrice proposée, à valider par l'équipe (elle prolonge les permissions déjà codées dans le `user-service`).
**L** = lire, **E** = écrire (créer / modifier), **—** = aucun accès. Les nuances sont dans les règles de gestion.

| Fonction | Étudiant | Alumni | Enseignant | Entreprise | Administrateur |
|---|---|---|---|---|---|
| Son propre profil et son compte | L E | L E | L E | L E | L E |
| Gérer les comptes utilisateurs | — | — | — | — | L E |
| Promotions et diplômés | — | — | L | — | L E |
| Profils étudiants et CV | L E (le sien) | L | L | L | L |
| Profils Alumni | L | L E (le sien) | L | — | L |
| Entreprises | L | L E (la sienne) | L | L E (la sienne) | L E, validation partenaire |
| Offres | L | L E | — | L E | L E |
| Candidatures | L E (les siennes) | L E (sur ses offres) | — | L E (sur ses offres) | L |
| Propositions directes | L E (répondre) | L E (envoyer) | — | L E (envoyer) | L |
| Matching | L (ses offres) | L (ses candidats) | — | L (ses candidats) | L |
| Messagerie et notifications | L E | L E | L E | L E | L E |
| Tableau de bord et rapport d'insertion | — | — | — | — | L |
| Rapport d'écart de compétences | — | — | L (à valider) | — | L |

## 8. Exigences non fonctionnelles

Les cibles chiffrées sont des **propositions** à confirmer avec l'équipe et l'encadrant.

| ID | Catégorie | Exigence | Critère de vérification |
|---|---|---|---|
| ENF-01 | **Sécurité** | Authentification par JWT, mots de passe hachés (BCrypt), contrôle d'accès par rôle (RBAC) sur chaque service, secrets hors du dépôt, protection contre l'énumération de comptes et limitation du débit sur la connexion | Une requête sans jeton reçoit 401, avec un rôle insuffisant reçoit 403 ; aucun mot de passe en clair dans les bases ni les journaux |
| ENF-02 | **Performance** | Listes paginées ; temps de réponse des API courantes inférieur à 1 seconde pour 95 % des requêtes avec 100 utilisateurs simultanés (cible proposée) | Test de charge simple sur les endpoints de liste et de recherche |
| ENF-03 | **Disponibilité** | Une panne d'un service ne rend pas les autres indisponibles ; déploiement sans coupure ; sondes de santé sur chaque service | Arrêt volontaire d'un service : les autres répondent toujours |
| ENF-04 | **Évolutivité** | Services sans état, déployables et dimensionnables indépendamment ; ajout d'un domaine sans modifier les autres services | Revue d'architecture |
| ENF-05 | **Maintenabilité** | Code structuré en couches, contrats OpenAPI versionnés, code d'interface généré, couverture de tests et quality gate SonarQube | Rapport SonarQube sans bug bloquant ni vulnérabilité critique ; couverture cible fixée dans le plan de tests |
| ENF-06 | **Ergonomie** | Interface en français, parcours clairs par rôle, messages d'erreur explicites, retour visuel (chargement, succès, erreur), accessibilité de base | Test des parcours clés avec un utilisateur extérieur à l'équipe |
| ENF-07 | **Compatibilité** | Interface web responsive (ordinateur, tablette, mobile) sur les navigateurs récents ; API indépendantes du client | Vérification sur mobile des écrans clés |
| ENF-08 | **RGPD / données personnelles** | Collecte limitée au nécessaire, accès filtré par rôle, suppression d'un compte possible, données personnelles cantonnées à leur service. À compléter : durée de conservation, export des données personnelles, effet de la suppression sur les autres services | Décisions consignées dans la matrice et dans le rapport final |
| ENF-09 | **Intégrité des données** | Base transactionnelle, schéma versionné par migrations, une base par service | Migrations rejouables sur une base vide |
| ENF-10 | **Observabilité** | Point de santé sur chaque service ; à compléter : journaux et métriques | `/actuator/health` répond sur chaque service |
| ENF-11 | **Portabilité** | Toute la plateforme démarre avec une seule commande sur un poste neuf | `docker compose up` fonctionne sur un poste neuf |
| ENF-12 | **Langue** | Interface et messages utilisateur en français | Revue des écrans |

## 9. Contraintes

| Contrainte | Description |
|---|---|
| **Contract First** | Les contrats OpenAPI font foi ; interfaces serveur et client générés ; toute évolution passe par une PR avec nouvelle version |
| **Architecture** | Microservices : `user-service`, `alumni-student-company-service`, `job-offer-service`, `analytics-matching-service`, `notification-messaging-service` ; une base par service |
| **Technologies** | Spring Boot (Java) côté back, Angular côté front, PostgreSQL |
| **Git Flow** | Branches `main` et `develop`, branches par ticket, fusion par Pull Request relue |
| **Conteneurisation** | Docker pour chaque service ; déploiement Kubernetes |
| **CI/CD** | GitHub Actions : build, tests, validation des contrats, images publiées |
| **Qualité** | SonarQube, tests unitaires, d'intégration, de contrat et de bout en bout |
| **Organisation** | 3 équipes (back, front, devops), sprints avec livrable démontrable à chaque revue |
| **Rendu académique** | Livrables du sujet : documentation, rapports, démonstration (voir l'epic Livrables) |

## 10. Hypothèses, risques et points ouverts

**Hypothèses**
- Les étudiants et Alumni utilisent leur adresse email personnelle ou universitaire valide pour activer leur compte.
- Un serveur d'email (SMTP) est disponible en production ; un serveur de test est utilisé en développement.
- Les données de démonstration sont fictives.

**Risques**
| Risque | Effet | Parade |
|---|---|---|
| Contrats modifiés après le début du développement | Conflits entre back et front | Tag `contracts-v1.0.0` après la revue croisée, puis versionnement strict |
| Dépendances entre services mal ordonnées | Tickets bloqués, sprint review impossible | Backlog ordonné par dépendances, un livrable par sprint |
| Validation JWT recopiée dans chaque service | Incohérences, conflits de fusion | Une seule bibliothèque commune |
| Charge inégale entre membres | Retards sur le chemin critique | Répartition revue à chaque planning |

**Points ouverts à trancher**
1. La recherche d'offres est-elle publique ou réservée aux utilisateurs connectés ?
2. Lien entre un compte `ENTREPRISE` et sa fiche entreprise (champ `companyId` sur l'utilisateur).
3. Effet de la suppression d'un utilisateur sur ses profils, candidatures et messages.
4. Mise en œuvre du temps réel de la messagerie (WebSocket ou interrogation périodique).
5. Périmètre du canal de notification push.
6. Durée de conservation des données personnelles et export à la demande (RGPD).
7. Ouverture du rapport d'écart de compétences aux enseignants.

## 11. Traçabilité avec le backlog

| Domaine d'exigences | Sprint | Tickets du backlog |
|---|---|---|
| Cadrage, architecture, contrats, socle technique | 1 | T-01 à T-16 |
| EF-01, EF-02 : comptes et accès | 2 | T-17 à T-25 |
| EF-03 à EF-07 : promotions, profils, CV, entreprises | 3 | T-28 à T-39 |
| EF-08 à EF-10, EF-14 : offres, candidatures, propositions, notifications | 4 | T-42 à T-52 |
| EF-11 à EF-13, EF-15 : messagerie, matching, rapports | 5 | T-53 à T-65 |
| ENF et livrables : tests, qualité, déploiement, rapports | 1, 2, 6 | T-12, T-13, T-26, T-27, T-66 à T-74 |

## 12. Glossaire

| Terme | Définition |
|---|---|
| **Alumni** | Ancien étudiant diplômé de l'UASZ |
| **Contract First** | Approche où le contrat d'API est écrit avant le code, qui en est généré |
| **JWT** | Jeton signé qui prouve l'identité et le rôle d'un utilisateur |
| **RBAC** | Contrôle d'accès fondé sur le rôle de l'utilisateur |
| **Matching** | Calcul automatique d'un score d'adéquation entre un étudiant et une offre |
| **Taux d'insertion** | Part des diplômés d'une promotion qui occupent un emploi |
| **MoSCoW** | Méthode de priorisation : Must, Should, Could, Won't |
| **SPI** | Interfaces et modèles d'API générés à partir des contrats |
