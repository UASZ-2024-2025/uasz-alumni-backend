# Manuel utilisateur par rôle --- Alumni UASZ

**Version : 1.0 --- guide de prise en main à faire relire par une
personne extérieure à l'équipe**

> Ce manuel est fondé sur les routes, menus, composants et scénarios de
> test présents dans le frontend fourni. L'interface peut évoluer. Les
> comptes et le mot de passe de démonstration présentés ici
> appartiennent uniquement au mock local.

## 1. Découvrir la plateforme

Alumni UASZ est une plateforme de mise en relation de la communauté
universitaire : étudiants, Alumni, enseignants, entreprises et
administrateurs. Après connexion, l'utilisateur accède à un espace dont
les entrées de navigation dépendent de son rôle.

Les cinq rôles applicatifs présents dans le frontend sont : - **Étudiant
(`ETUDIANT`)** - **Alumni (`ALUMNI`)** - **Enseignant (`ENSEIGNANT`)** -
**Entreprise (`ENTREPRISE`)** - **Administrateur (`ADMINISTRATEUR`)**

\[EMPLACEMENT CAPTURE 1 --- Page de connexion\]

## 2. Se connecter et naviguer

1.  Ouvrir l'adresse de la plateforme communiquée par l'établissement
    ou, en local, `http://localhost:4200`.
2.  Saisir l'adresse e-mail et le mot de passe fournis.
3.  Valider le formulaire de connexion.
4.  Une fois connecté, consulter la page d'accueil et le menu
    correspondant à son rôle.
5.  Pour quitter son espace, utiliser l'action de déconnexion dans la
    barre de navigation.

En cas de session expirée, l'application tente automatiquement de
renouveler le jeton. Si le renouvellement échoue, l'utilisateur est
redirigé vers la page de connexion.

\[EMPLACEMENT CAPTURE 2 --- Page d'accueil après connexion avec le menu
latéral ou supérieur\]

## 3. Manuel de l'étudiant

### 3.1 Accès et menu

L'étudiant voit les entrées suivantes dans le menu : - Profil étudiant -
Mon CV - Annuaire - Offres - Mes candidatures - Correspondances -
Groupes - Messagerie - Notifications

\[EMPLACEMENT CAPTURE 3 --- Menu de l'étudiant\]

### 3.2 Consulter et compléter son profil

1.  Ouvrir **Profil étudiant** (`/mon-espace/profil`).
2.  Consulter les informations affichées.
3.  Utiliser les actions proposées par la page pour compléter ou mettre
    à jour les informations disponibles.
4.  Enregistrer les modifications si le formulaire le propose.

\[EMPLACEMENT CAPTURE 4 --- Profil étudiant\]

### 3.3 Gérer son CV

1.  Ouvrir **Mon CV** (`/mon-espace/cv`).
2.  Consulter les informations du CV.
3.  Ajouter ou mettre à jour les éléments proposés par l'écran
    (formations, compétences ou informations professionnelles selon les
    champs disponibles).
4.  Vérifier les informations enregistrées avant de quitter la page.

\[EMPLACEMENT CAPTURE 5 --- Espace Mon CV\]

### 3.4 Consulter l'annuaire

1.  Ouvrir **Annuaire**.
2.  Parcourir les profils accessibles.
3.  Utiliser les filtres ou la recherche lorsqu'ils sont disponibles.
4.  Ouvrir un profil pour consulter les informations publiques
    proposées.

\[EMPLACEMENT CAPTURE 6 --- Annuaire\]

### 3.5 Consulter les offres et suivre ses candidatures

1.  Ouvrir **Offres** (`/offres-emploi`).
2.  Sélectionner une offre pour consulter son détail.
3.  Si l'offre est ouverte et que le profil le permet, utiliser l'action
    de candidature.
4.  Ouvrir **Mes candidatures** (`/candidatures`) pour retrouver les
    candidatures envoyées et leur statut.

Le backend vérifie notamment que l'offre est ouverte, que la date limite
n'est pas dépassée et qu'une candidature n'existe pas déjà. Les statuts
de candidature peuvent évoluer au fil du traitement par le recruteur.

\[EMPLACEMENT CAPTURE 7 --- Liste des offres\] \[EMPLACEMENT CAPTURE 8
--- Détail d'une offre et action pour postuler\] \[EMPLACEMENT CAPTURE 9
--- Mes candidatures et statuts\]

### 3.6 Correspondances

L'entrée **Correspondances** (`/correspondances`) est présente dans le
menu étudiant, mais la route frontend fournie est explicitement déclarée
« en construction ». Cette rubrique ne doit donc pas être présentée
comme une fonctionnalité opérationnelle dans cette version.

\[EMPLACEMENT CAPTURE 10 --- Correspondances (si l'écran est activé dans
la version utilisée)\]

### 3.7 Groupes, messagerie et notifications

-   **Groupes** : consulter les groupes auxquels l'étudiant appartient
    et accéder à leurs conversations. Les droits dépendent du rôle de
    membre dans le groupe. Le jeu de test montre notamment un groupe en
    lecture seule et un groupe auquel un étudiant non membre ne peut pas
    accéder.
-   **Messagerie** : accéder aux conversations privées disponibles.
-   **Notifications** : consulter les notifications reçues.

\[EMPLACEMENT CAPTURE 11 --- Liste des groupes\] \[EMPLACEMENT CAPTURE
12 --- Conversation de groupe\] \[EMPLACEMENT CAPTURE 13 --- Messagerie
privée et notifications\]

## 4. Manuel de l'Alumni

### 4.1 Accès et menu

L'Alumni dispose des entrées : - Annuaire - Profil Alumni - Offres -
Propositions envoyées - Groupes - Messagerie - Notifications

\[EMPLACEMENT CAPTURE 14 --- Menu de l'Alumni\]

### 4.2 Consulter et gérer son profil Alumni

1.  Ouvrir **Profil Alumni** (`/espace-alumni/profil`).
2.  Consulter les informations de son profil.
3.  Mettre à jour les informations de parcours proposées par l'écran.
4.  Vérifier les informations affichées après enregistrement.

Le service de gestion des profils est responsable du parcours
professionnel des Alumni.

\[EMPLACEMENT CAPTURE 15 --- Profil Alumni\]

### 4.3 Utiliser l'annuaire

1.  Ouvrir **Annuaire**.
2.  Rechercher ou parcourir les profils accessibles.
3.  Ouvrir un profil pour consulter les informations disponibles.

\[EMPLACEMENT CAPTURE 16 --- Annuaire côté Alumni\]

### 4.4 Consulter les offres et les propositions envoyées

Le menu expose **Offres** et **Propositions envoyées**. La route
propositions est autorisée aux rôles `ALUMNI` et `ENTREPRISE`. Consulter
la liste pour retrouver les propositions déjà transmises et leurs
informations.

\[EMPLACEMENT CAPTURE 17 --- Offres accessibles à l'Alumni\]
\[EMPLACEMENT CAPTURE 18 --- Propositions envoyées\]

### 4.5 Participer aux groupes et communiquer

L'Alumni peut accéder aux groupes, à la messagerie et aux notifications.
Le scénario de test fourni donne à `alumni@uasz.sn` le rôle
d'administrateur du groupe « Promo GL 2025 » : il peut retirer des
membres, mais pas la propriétaire, et ne gère pas les rôles dans ce
scénario.

\[EMPLACEMENT CAPTURE 19 --- Gestion d'un groupe par un Alumni\]

## 5. Manuel de l'enseignant

### 5.1 Accès et menu

Le menu enseignant contient : - Annuaire - Groupes - Messagerie -
Notifications

\[EMPLACEMENT CAPTURE 20 --- Menu de l'enseignant\]

### 5.2 Consulter l'annuaire

1.  Ouvrir **Annuaire**.
2.  Parcourir les profils accessibles.
3.  Ouvrir un profil pour consulter les informations proposées.

\[EMPLACEMENT CAPTURE 21 --- Annuaire côté enseignant\]

### 5.3 Participer aux groupes

L'enseignant peut accéder aux groupes de discussion. Dans les données de
démonstration, `enseignant@uasz.sn` est propriétaire de « Promo GL 2025
» et « Annonces UFR SET ». Le scénario couvre les réglages, les rôles,
le retrait de membres et la suppression de groupe. Les actions
réellement disponibles dépendent des droits affichés dans l'interface.

\[EMPLACEMENT CAPTURE 22 --- Groupes de l'enseignant\] \[EMPLACEMENT
CAPTURE 23 --- Paramètres et membres d'un groupe\]

### 5.4 Statistiques : limite connue

La route `/statistiques` est autorisée aux rôles `ENSEIGNANT` et
`ADMINISTRATEUR`. Toutefois, le code frontend précise que le tableau de
bord complet est destiné à l'administrateur et que l'enseignant conserve
l'accès sans appel aux API tant que le backend ne lui ouvre pas ces API.
Ne pas interpréter l'accès à la route comme la garantie de statistiques
complètes pour l'enseignant.

\[EMPLACEMENT CAPTURE 24 --- Statistiques si l'écran est disponible pour
ce rôle\]

## 6. Manuel de l'entreprise

### 6.1 Accès et menu

Le menu entreprise contient : - Ma fiche entreprise - Annuaire - Mes
offres - Publier une offre - Propositions envoyées - Messagerie -
Notifications

Les entreprises n'ont pas accès à la rubrique Groupes dans le menu et le
scénario mock renvoie un refus 403 pour ce rôle.

\[EMPLACEMENT CAPTURE 25 --- Menu entreprise\]

### 6.2 Consulter sa fiche entreprise

1.  Ouvrir **Ma fiche entreprise** (`/entreprise/ma-fiche`).
2.  Consulter les informations de l'entreprise.
3.  Mettre à jour les champs proposés par l'écran.
4.  Vérifier les informations enregistrées.

\[EMPLACEMENT CAPTURE 26 --- Fiche entreprise\]

### 6.3 Publier une offre

1.  Ouvrir **Publier une offre** (`/offres-emploi/publier`).
2.  Renseigner les champs obligatoires du formulaire.
3.  Vérifier les informations et soumettre le formulaire.
4.  Retrouver ensuite l'offre dans **Mes offres**.

Le backend vérifie que l'entreprise est autorisée à publier. Une
entreprise non validée reçoit une erreur `403 COMPANY_NOT_VALIDATED`.

\[EMPLACEMENT CAPTURE 27 --- Formulaire de publication d'une offre\]

### 6.4 Gérer ses offres

1.  Ouvrir **Mes offres** (`/offres-emploi/mes-offres`).
2.  Consulter les offres rattachées à son entreprise.
3.  Ouvrir une offre pour consulter les candidatures reçues.
4.  Utiliser les actions disponibles pour examiner les candidatures et
    faire évoluer leur statut.

Les transitions de statut couvertes par le mock sont : - `SUBMITTED`
vers `UNDER_REVIEW` ou `REJECTED` - `UNDER_REVIEW` vers `SHORTLISTED` ou
`REJECTED` - `SHORTLISTED` vers `ACCEPTED` ou `REJECTED`

Toute autre transition est rejetée par le backend avec
`400 INVALID_STATUS_TRANSITION`.

\[EMPLACEMENT CAPTURE 28 --- Mes offres\] \[EMPLACEMENT CAPTURE 29 ---
Candidatures reçues pour une offre\] \[EMPLACEMENT CAPTURE 30 ---
Modification du statut d'une candidature\]

### 6.5 Propositions, messagerie et notifications

L'entreprise peut consulter **Propositions envoyées**, utiliser la
messagerie privée et consulter ses notifications. Les droits d'accès
sont déterminés par les règles de l'application et du backend.

\[EMPLACEMENT CAPTURE 31 --- Propositions envoyées\] \[EMPLACEMENT
CAPTURE 32 --- Messagerie et notifications entreprise\]

### 6.6 Entreprise en attente de validation

Le compte de démonstration `entreprise-attente@uasz.sn` représente une
entreprise non validée. Il sert à vérifier le refus de publication
d'offre (`403 COMPANY_NOT_VALIDATED`). Si le compte est dans cet état,
contacter l'administrateur ou le responsable de la plateforme pour
connaître la procédure de validation ; le présent code ne documente pas
une procédure utilisateur autonome de demande de validation.

## 7. Manuel de l'administrateur

### 7.1 Accès et menu

Le menu administrateur contient : - Administration - Annuaire -
Entreprises - Offres - Groupes - Messagerie - Notifications

\[EMPLACEMENT CAPTURE 33 --- Menu administrateur\]

### 7.2 Tableau de bord d'administration

1.  Ouvrir **Administration** (`/administration`).
2.  Consulter le tableau de bord.
3.  Utiliser les rubriques de gestion proposées.

Les routes d'administration repérées comprennent : - Tableau de bord -
Gestion des utilisateurs (`/administration/utilisateurs`) - Promotions
(`/administration/promotions`) - Entreprises partenaires
(`/administration/entreprises`)

\[EMPLACEMENT CAPTURE 34 --- Tableau de bord administrateur\]

### 7.3 Gérer les utilisateurs

1.  Ouvrir **Gestion des utilisateurs**.
2.  Rechercher ou parcourir les comptes affichés.
3.  Utiliser les actions proposées par l'interface pour administrer les
    comptes.
4.  Vérifier le résultat de l'action.

\[EMPLACEMENT CAPTURE 35 --- Gestion des utilisateurs\]

### 7.4 Gérer les promotions

1.  Ouvrir **Promotions**.
2.  Consulter les promotions existantes.
3.  Utiliser les actions disponibles pour gérer les promotions et les
    informations associées.

\[EMPLACEMENT CAPTURE 36 --- Gestion des promotions\]

### 7.5 Gérer les entreprises partenaires

1.  Ouvrir **Entreprises** ou **Entreprises partenaires**.
2.  Consulter les fiches présentées.
3.  Utiliser les actions proposées pour administrer les entreprises.

\[EMPLACEMENT CAPTURE 37 --- Gestion des entreprises partenaires\]

### 7.6 Statistiques et suivi

La page **Statistiques** est accessible à l'administrateur et le code la
décrit comme le tableau de bord complet destiné à ce rôle.

\[EMPLACEMENT CAPTURE 38 --- Tableau de bord statistiques\]

## 8. Comptes de démonstration pour la relecture

Pour tester les écrans localement, démarrer le mock frontend (voir le
guide technique), puis utiliser le mot de passe `password123`.

  -----------------------------------------------------------------------
  Compte                              Rôle / cas de test
  ----------------------------------- -----------------------------------
  `alumni@uasz.sn`                    Alumni ; administrateur du groupe «
                                      Promo GL 2025 »

  `etudiant@uasz.sn`                  Étudiant ; candidatures et groupes

  `enseignant@uasz.sn`                Enseignant ; propriétaire de deux
                                      groupes

  `entreprise@uasz.sn`                Entreprise validée ; offres et
                                      candidatures reçues

  `entreprise-attente@uasz.sn`        Entreprise non validée ; refus de
                                      publication

  `admin@uasz.sn`                     Administrateur
  -----------------------------------------------------------------------

Le mock conserve ses données en mémoire. Redémarrer `npm run mock`
réinitialise le jeu de données. Ces comptes ne sont pas des comptes de
production.

## 9. FAQ et résolution des problèmes

**Je n'arrive pas à me connecter.**\
Vérifier l'adresse e-mail, le mot de passe et la disponibilité du
backend ou du mock. En local, vérifier que `npm run mock` tourne et que
le port 8081 est disponible.

**Je suis redirigé vers la page de connexion.**\
La session peut être absente ou le renouvellement du jeton a échoué. Se
reconnecter.

**La page affiche « Accès refusé » (403).**\
La route est réservée à un autre rôle ou l'action n'est pas autorisée
pour ce compte. Vérifier le rôle connecté et les droits de
l'utilisateur.

**Une entreprise ne peut pas publier d'offre.**\
Le compte peut être non validé. Le cas `entreprise-attente@uasz.sn` est
prévu pour tester le code `COMPANY_NOT_VALIDATED`.

**Je ne vois pas les candidatures d'une offre.**\
Vérifier que l'offre appartient bien à l'entreprise connectée et ouvrir
les candidatures depuis **Mes offres**. Le mock renvoie un 403 si
l'entreprise tente d'accéder aux candidatures d'une offre appartenant à
une autre entreprise.

**La rubrique Correspondances ne fonctionne pas.**\
Dans les sources fournies, cette route est encore déclarée « en
construction ». Elle n'est pas à considérer comme une fonctionnalité
finalisée.

**Les groupes sont inaccessibles pour mon compte entreprise.**\
C'est le comportement prévu par le menu et les scénarios de test actuels
: les entreprises n'ont pas accès aux groupes.

**Les données ont disparu après redémarrage du mock.**\
C'est normal : les données du mock sont en mémoire et sont
réinitialisées à son redémarrage.

**Une notification ou un e-mail n'arrive pas.**\
Vérifier que le service de notification est démarré et que le canal
correspondant est configuré. En développement, les e-mails du
`user-service` sont consultables via Mailpit lorsqu'il est lancé.

## 10. Sécurité et bonnes pratiques

-   Ne jamais partager son mot de passe.
-   Se déconnecter après utilisation d'un poste partagé.
-   Ne pas utiliser les comptes du mock dans un environnement réel.
-   Ne pas transmettre de données personnelles ou de documents sensibles
    dans un environnement de démonstration.
-   Signaler à l'administrateur toute anomalie ou tout accès inattendu.

------------------------------------------------------------------------

**Fin --- manuel utilisateur ALUM-195**
