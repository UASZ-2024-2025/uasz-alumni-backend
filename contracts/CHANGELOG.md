# Journal des modifications des contrats OpenAPI

## 1.25.0 (synthèse d'accueil entreprise, ALUM-249)

Évolution compatible : un chemin ajouté.

### job-offer-service
- Nouveau `GET /companies/me/home-summary` (`getMyCompanyHomeSummary`, tag `CompanyHome`), réservé au rôle ENTREPRISE
  (401 sans jeton, 403 pour un autre rôle) : `CompanyHomeSummary` (`activeOffers`, `applicationsByOffer`,
  `proposalsSent`) et `OfferApplicationsCount` (`offerId`, `title`, `applications`).
- Les données sont celles de l'utilisateur connecté : aucun paramètre d'identité.
- `proposalsSent` compte les propositions envoyées par l'utilisateur connecté ; `activeOffers` et `applicationsByOffer`
  portent sur la fiche entreprise rattachée à son compte.

### Version
- `info.version` passe de 1.10.0 à **1.25.0**.

SPI `job-offer-service-spi` publié en **1.25.0** (pom parent `alumni-spis` 1.25.0).

## 1.19.0 (recommandation d'un étudiant par un Alumni, ALUM-119)

Évolution compatible : aucune opération ni aucun champ retiré ; seule une réponse est ajoutée.

### alumni-student-company-service
- `POST /alumni/recommendations` (`recommendCandidate`) est implémenté (il renvoyait 501) : réservé au rôle ALUMNI,
  l'étudiant recommandé est notifié (type `SYSTEM_ALERT`, canal `IN_APP`) sans que la réponse l'attende.
- **Précision** : `recommendCandidate` déclare la réponse 403 (appelant qui n'a pas le rôle ALUMNI).
- `info.version` passe de 1.16.0 à **1.19.0**.

SPI `alumni-student-company-service-spi` publié en **1.19.0** (pom parent `alumni-spis` 1.19.0).

## 1.16.0 (synthèse d'accueil Alumni, ALUM-246)

Évolution compatible : une opération et un schéma ajoutés, rien de retiré.

### alumni-student-company-service
- **Ajout** de `GET /alumni/me/home-summary` (`getMyAlumniHomeSummary`, rôle ALUMNI) : compteurs de la page d'accueil
  de l'Alumni connecté, identifié par son jeton uniquement. Réponses 200, 401 et 403.
- Nouveau schéma `AlumniHomeSummary` : `recommendationsGiven`, `pendingRequests`, `unreadMessages` (entiers >= 0,
  obligatoires).
- `info.version` passe de 1.9.0 à **1.16.0** (alignée sur la version du SPI).

## 1.15.0 (pièces jointes : envoi sans remplacement, ALUM-261)

Évolution compatible : un champ ajouté en réponse.

### notification-messaging-service
- `AttachmentUploadTicket.overwrite` (toujours false) : à renvoyer tel quel à Cloudinary ; signé, il empêche de
  remplacer un fichier déjà déposé avec la même autorisation.
- `AttachmentUpload` : seuls `publicId` et `version` sont signés ; taille, format, durée et dimensions sont relus auprès
  du stockage (fichier refusé = effacé ; version différente = 400 `INVALID_ATTACHMENT`).
- `ServiceUnavailable` couvre aussi un stockage momentanément injoignable (503 `STORAGE_UNAVAILABLE`).
- Un fichier n'est joint qu'à un seul message (sinon 400 `INVALID_ATTACHMENT`) ; `MessageAttachment.fileName` prend
  l'extension du fichier stocké.
- Modification : refusée pour un sticker ou un message vocal (400 `INVALID_MESSAGE`) ; un texte vide retire la légende
  d'une pièce jointe.

### Version
- `info.version` passe de 1.14.0 à **1.15.0**.

## 1.14.0 (pièces jointes, stickers et messages vocaux, ALUM-261)

Évolution compatible : aucune opération ni aucun champ retiré ; `SendMessageRequest.content` devient facultatif.

### notification-messaging-service
- Nouveau tag `MessageAttachments` : `GET /messages/attachments/settings` (`getAttachmentSettings`) et
  `POST /messages/attachments/uploads` (`createAttachmentUpload`, autorisation d'envoi direct vers Cloudinary,
  503 `ATTACHMENTS_DISABLED` sans stockage configuré). Nouvelle réponse `ServiceUnavailable`.
- `GroupMessageType` : valeurs `STICKER` et `ATTACHMENT` ; le type sert aussi à la messagerie privée.
- `SendMessageRequest` : `sticker` et `attachment` (`AttachmentUpload`) ; `content` n'est plus obligatoire (légende
  d'une pièce jointe). Envoi privé et de groupe : réponse 503.
- `PrivateMessage` et `GroupMessage` : champs `sticker` et `attachment` (`MessageAttachment`, lien signé) ;
  `PrivateMessage` : champ `type`.
- `ConversationSummary` : `lastMessageType` et `lastMessageAttachmentKind` ; `GroupSummary` : `lastMessageAttachmentKind`.
- Nouveaux schémas : `AttachmentKind`, `MessageAttachment`, `AttachmentUpload`, `AttachmentSettings`,
  `AttachmentUploadRequest`, `AttachmentUploadTicket`.
- `info.version` passe de 1.13.0 à **1.14.0**.

SPI `notification-messaging-service-spi` publié en **1.14.0** (pom parent `alumni-spis` 1.14.0).

## 1.13.0 (messagerie privée alignée sur les groupes, modification d'un message, ALUM-258)

Évolution compatible : aucune opération ni aucun champ retiré ; seuls des opérations et des champs sont ajoutés.

### notification-messaging-service
- `PATCH /messages/{messageId}` (`editPrivateMessage`) et `PATCH /messages/groups/{groupId}/messages/{messageId}`
  (`editGroupMessage`) : l'auteur modifie son message dans les 15 minutes qui suivent l'envoi (400
  `EDIT_WINDOW_EXPIRED` au-delà, `MESSAGE_DELETED` pour un message supprimé). Nouveau schéma `EditMessageRequest`.
- `PrivateMessage` et `GroupMessage` : champ `editedAt`. `PrivateMessage` : champ `deleted`.
- **Changement de comportement** : `DELETE /messages/{messageId}` laisse le message dans le fil avec `deleted` à
  true et un contenu vide, comme dans les groupes (il était effacé).
- `PATCH /messages/conversations/{recipientId}/preferences` (`updateConversationPreferences`) : épingler, archiver,
  mettre en sourdine une conversation privée (mêmes schémas que les groupes). `ConversationSummary` : champs
  `preferences`, `lastMessageSenderId` et `lastMessageDeleted` ; liste triée épinglées d'abord.
- `info.version` passe de 1.11.0 à **1.13.0** (1.12.0 est prise par user-service, ALUM-74).

SPI `notification-messaging-service-spi` publié en **1.13.0** (pom parent `alumni-spis` 1.13.0).

## 1.11.0 (émission des notifications, ALUM-135)

Évolution compatible : aucune opération ni aucun champ retiré ; seule une réponse est ajoutée.

### notification-messaging-service
- `POST /notifications/send` est implémenté (il renvoyait 501) : la notification est enregistrée et apparaît dans
  le centre de notifications du destinataire ; le canal `EMAIL` l'envoie en plus par e-mail. `channel` absent :
  `IN_APP`. `PUSH` : hors périmètre, la notification reste visible dans l'application.
- **Précision** : `POST /notifications/send` (`triggerNotification`) déclare la réponse 403, déjà renvoyée par la
  sécurité à un utilisateur qui n'est pas administrateur (ALUM-83).
- `info.version` passe de 1.1.0 à **1.11.0** (alignée sur la version du SPI).

SPI `notification-messaging-service-spi` publié en **1.11.0** (pom parent `alumni-spis` 1.11.0).

## 1.10.0 (dépôt et suivi des candidatures, ALUM-172)

Évolution compatible : aucune opération ni aucun champ retiré ; seules des réponses sont ajoutées.

### job-offer-service
- `POST /offers/{offerId}/applications` et `GET /applications` sont implémentés (ils renvoyaient 501).
- **Précision** : `POST /offers/{offerId}/applications` (`submitApplication`) déclare 403 (appelant qui n'est pas
  étudiant, ou `studentId` différent de son compte) et 404 (offre introuvable). Une offre qui n'est pas OPEN, ou
  dont l'échéance est passée, est refusée en 400 `OFFER_NOT_OPEN`.
- **Précision** : `GET /applications` (`listMyApplications`) déclare 403 : un étudiant ne lit que ses propres
  candidatures, l'administrateur celles de tous ; les autres rôles sont refusés.
- `info.version` passe de 1.6.0 à **1.10.0** (alignée sur la version du SPI).

SPI `job-offer-service-spi` publié en **1.10.0** (pom parent `alumni-spis` 1.10.0).

## 1.9.0 (CV de l'étudiant, ALUM-109)

Évolution compatible : aucune opération ni aucun champ retiré ; seule une réponse est ajoutée.

### alumni-student-company-service
- `GET /students/{id}/cv` et `PUT /students/{id}/cv` sont implémentés (ils renvoyaient 501). `{id}` accepte
  l'identifiant du profil étudiant ou celui de son compte ; 404 `CV_NOT_FOUND` tant que l'étudiant n'a pas de CV.
- **Précision** : `PUT /students/{id}/cv` (`updateStudentCv`) déclare la réponse 403 (modification par un
  utilisateur qui n'est ni l'étudiant concerné ni un administrateur).
- `info.version` passe de 1.8.0 à **1.9.0**.

SPI `alumni-student-company-service-spi` publié en **1.9.0** (pom parent `alumni-spis` 1.9.0).

## 1.8.0 (fiche entreprise du compte connecté, ALUM-224)

Évolution compatible : aucune opération ni aucun champ retiré ; seule une opération est ajoutée.

### alumni-student-company-service
- **Ajout** de `GET /companies/me` (`getMyCompany`, rôle ENTREPRISE) : fiche dont le compte connecté est le
  gestionnaire, 404 s'il n'en a pas encore. Relance le rattachement du compte dans user-service si le jeton n'a pas
  encore de `companyId`.
- `info.version` passe de 1.7.0 à **1.8.0**.

SPI `alumni-student-company-service-spi` publié en **1.8.0** (pom parent `alumni-spis` 1.8.0).

## 1.7.0 (recherche d'Alumni par spécialité, ALUM-105)

Évolution compatible : aucune opération ni aucun champ retiré ; seuls un paramètre et une réponse sont ajoutés.

### alumni-student-company-service
- **Ajout** du paramètre de requête `specialty` (string, facultatif) sur `GET /alumni` (`searchAlumni`) :
  filtre de recherche par spécialité, le « secteur » de l'écran (ALUM-105).
- **Précision** : `PUT /alumni/{id}` (`updateAlumniProfile`) déclare la réponse 403 (modification par un
  utilisateur qui n'est pas le propriétaire du profil) (ALUM-100).
- `info.version` passe de 1.5.0 à **1.7.0**.

SPI `alumni-student-company-service-spi` publié en **1.7.0** (pom parent `alumni-spis` 1.7.0).

## 1.6.0 (publication et gestion des offres, ALUM-148)

Évolution compatible : aucune opération ni aucun champ retiré ; seules des réponses et des descriptions sont ajoutées.

### job-offer-service
- **Ajout** des réponses `403` sur `updateOffer` et `updateOfferStatus` (auteur de l'offre ou ADMINISTRATEUR), `400` sur
  `updateOfferStatus` (transition interdite), `503` sur `createOffer` (fiche entreprise indisponible).
- **Précisions** : règles de publication (gestionnaire de la fiche, entreprise validée), visibilité des offres selon
  leur statut (`searchOffers`, `getOfferById`), mise à jour partielle de `updateOffer`, archivage par `deleteOffer`,
  transitions de statut, fermeture automatique à la date limite.
- `info.version` passe de 1.0.0 à **1.6.0** pour s'aligner sur le SPI.

SPI `job-offer-service-spi` publié en **1.6.0** (pom parent `alumni-spis` 1.6.0).

## 1.5.0 (rattachement d'un compte ENTREPRISE à sa fiche, ALUM-168)

Évolution compatible : aucune opération ni aucun champ retiré ; seuls des champs, des opérations et des réponses sont ajoutés.

### user-service
- **Ajout** du champ `User.companyId` (uuid, facultatif) : fiche entreprise rattachée au compte ENTREPRISE. Également
  présent dans le jeton d'accès (claim `companyId`).
- **Ajout** du tag `Internal` et de `PUT /internal/users/{id}/company` (`linkCompanyAccount`, `UserCompanyLinkRequest`) :
  rattachement idempotent, jeton de service uniquement (ADR-0010). 409 si le compte n'est pas ENTREPRISE ou déjà rattaché
  à une autre fiche.
- `info.version` passe de 1.0.0 à **1.5.0** pour s'aligner sur les SPI.

### alumni-student-company-service
- **Ajout** de la réponse `409` sur `createCompany` (un compte ENTREPRISE ne gère qu'une seule fiche).
- **Ajout** de `PUT /companies/{id}/owner` (`assignCompanyOwner`, `CompanyOwnerRequest`) : rattachement d'une fiche à un
  compte ENTREPRISE par un administrateur (409, 503).
- **Ajout** de `GET /internal/companies/{id}` (`getCompanyForService`) : lecture de la fiche par un autre service.
- `info.version` passe de 1.4.1 à **1.5.0**.

SPI `user-service-spi` et `alumni-student-company-service-spi` publiés en **1.5.0** (pom parent `alumni-spis` 1.5.0).

## alumni-student-company-service 1.4.1 (précision sur les diplômés, ALUM-88)

Évolution compatible : seule une description change.

- **Précision** sur `getPromotionGraduates` : l'`id` de chaque diplômé est celui du profil Alumni (à utiliser avec
  `GET /alumni/{id}`, pas `GET /students/{id}`). Remarque de revue sur la #37.
- SPI `alumni-student-company-service-spi` publié en **1.4.1** (pom parent `alumni-spis` 1.4.1) ;
  alumni-student-company-service passe à `alumni.spi.version` 1.4.1.

## alumni-student-company-service 1.4.0 (promotions académiques, ALUM-88)

Évolution compatible : aucune opération ni aucun champ retiré ; seules des réponses et des descriptions sont ajoutées.

- **Ajout** des réponses `403` (création réservée à `ADMINISTRATEUR`) et `409` (promotion déjà existante pour la même
  année et la même spécialité, sans tenir compte de la casse) sur `createPromotion`.
- **Précisions** : `academicYear` entre 1990 et 2100, `specialty` non vide ; tri de `listPromotions` ;
  `graduateCount` calculé ; les diplômés d'une promotion (`getPromotionGraduates`) sont les Alumni de même année de
  diplôme et de même spécialité, triés par nom puis prénom.
- `info.version` passe de 1.2.0 à **1.4.0** pour s'aligner sur le SPI : le SPI 1.3.0 (ALUM-96) avait été publié sans
  changer la version du contrat.
- SPI `alumni-student-company-service-spi` publié en **1.4.0** (pom parent `alumni-spis` 1.4.0) ;
  alumni-student-company-service passe à `alumni.spi.version` 1.4.0.

## notification-messaging-service 1.1.0 (groupes de discussion : réglages et administration)

Évolution compatible : aucun champ ni opération existants ne sont retirés ; les nouveaux champs de réponse sont facultatifs.

- **Ajout** du rôle `ADMIN` dans `GroupMemberRole` (OWNER et ADMIN = administrateurs du groupe).
- **Ajout** des réglages de groupe `GroupSettings` (`onlyAdminsCanSend`, `onlyAdminsCanEditInfo`, `onlyAdminsCanAddMembers`)
  et de `PATCH /messages/groups/{groupId}/settings` (`updateGroupSettings`).
- **Ajout** des préférences personnelles `GroupPreferences` (épinglé, archivé, sourdine via `MuteDuration`)
  et de `PATCH /messages/groups/{groupId}/preferences` (`updateGroupPreferences`).
- **Ajout** de `PATCH /messages/groups/{groupId}/members/{userId}` (`updateGroupMemberRole`) : nommer ou retirer un ADMIN.
- **Ajout** de `DELETE /messages/groups/{groupId}/messages/{messageId}` (`deleteGroupMessage`) : suppression pour tous.
- **Ajout** de `GroupMessageType` (TEXT, SYSTEM) et des champs `GroupMessage.type`, `GroupMessage.deleted`,
  `Group.settings`, `Group.preferences`, `GroupSummary.preferences`, `GroupSummary.lastMessageSenderName`,
  `GroupSummary.lastMessageType`.
- **Précisions** : règles d'accès de chaque opération (membres, administrateurs du groupe, réglages) ; 403 pour ENTREPRISE ;
  ordre des pages de `getGroupMessages` ; passation de propriété quand le propriétaire quitte ; contenu des messages
  non vide et limité à 4000 caractères ; tri de `listGroups`.
- **Précisions** sur `getConversationMessages` : messages du plus récent au plus ancien (`createdAt` décroissant),
  paginés ; seuls les échanges entre l'appelant et `recipientId` sont renvoyés (pas de 403).
  `PrivateMessage.senderName` et `GroupMessage.senderName` peuvent être absents (jeton émis sans le nom de l'utilisateur).
- SPI `notification-messaging-service-spi` publié en **1.2.0** (version du pom parent `alumni-spis`, déjà passée à 1.2.0
  par alumni-student-company-service).

## 1.3.0

### alumni-student-company-service
- **Ajout** du champ `Company.ownerId` (uuid) : gestionnaire de la fiche entreprise (ALUM-96).
- **Ajout** de la réponse `403` sur `createCompany` et `updateCompany`.
- **Précision** : seul l'administrateur peut modifier `isPartner` (`CompanyUpdateRequest`).
- SPI `alumni-student-company-service-spi` publié en **1.3.0** : réunit les changements de la 1.2.0 (profils internes, ALUM-83)
  et `Company.ownerId`. La 1.1.0 de la branche AL-28 est abandonnée.

## 1.2.0

### alumni-student-company-service
- **Ajout** du tag `Internal` et de `POST /internal/profiles` (`provisionProfile`) : création du profil
  d'un compte ETUDIANT ou ALUMNI après la confirmation de son email, appelée par user-service avec un
  jeton de service (ADR-0010). Idempotent sur `userId` (201 si créé, 200 si déjà présent) ; 403 pour un
  JWT utilisateur (ALUM-83).
- **Ajout** des schémas `ProvisionedProfileType`, `ProfileProvisioningRequest`, `ProfileProvisioningResponse`.
- SPI `alumni-student-company-service-spi` publié en **1.2.0** (version du pom parent `alumni-spis` passée à 1.2.0).
  La branche AL-28 publie de son côté une 1.1.0 (`Company.ownerId`) : la seconde PR fusionnée devra
  régénérer le SPI pour réunir les deux changements.

## 1.0.0 (en attente de revue croisée puis tag `contracts-v1.0.0`)

### notification-messaging-service
- **Ajout** du tag `GroupMessaging` et de 10 opérations pour les groupes de discussion
  (exigence du sujet, annoncée dans la description du service mais absente des endpoints) :
  `listGroups`, `createGroup`, `getGroupById`, `updateGroup`, `deleteGroup`,
  `addGroupMembers`, `removeGroupMember`, `getGroupMessages`, `sendGroupMessage`, `markGroupAsRead`.
- **Ajout** des schémas `Group`, `GroupSummary`, `GroupMember`, `GroupMemberRole`,
  `GroupCreateRequest`, `GroupUpdateRequest`, `GroupMembersRequest`, `GroupMessage`,
  `GroupMessagePagedResponse`.
- **Ajout** de `GROUP_INVITATION` et `GROUP_MESSAGE_RECEIVED` dans `NotificationType`.

### user-service
- **Ajout** du rôle `ENTREPRISE` dans `RoleName`.
- **Ajout** du schéma partagé `Gender` (remplace les enums inline de `User` et `UserUpdateRequest`).
- **Ajout** de `POST /auth/change-password` (`changePassword`, `ChangePasswordRequest`) et de `POST /users` (`createUser`, `UserCreateRequest`).
- **Précisions** : 423 sur `/auth/login` ; 403 sur `GET /users/{id}` (soi-même ou ADMINISTRATEUR) ; 400/409 sur `PATCH /users/{id}/status` ; 409 sur `PUT`/`DELETE /users/{id}` ; changement d'email réservé à ADMINISTRATEUR ; réponse `Conflict`.
- SPI `user-service-spi` passé en **1.1.0**.
- **Suppression** du serveur `virtserver.swaggerhub.com` (mock automatique non pertinent).
- **Renommage** des schémas auto-générés, afin d'obtenir des classes lisibles à la génération :

| Ancien nom | Nouveau nom |
|---|---|
| `auth_refreshtoken_body` | `RefreshTokenRequest` |
| `auth_logout_body` | `LogoutRequest` |
| `auth_forgotpassword_body` | `ForgotPasswordRequest` |
| `auth_resetpassword_body` | `ResetPasswordRequest` |
| `auth_confirmemail_body` | `ConfirmEmailRequest` |
| `auth_resendconfirmation_body` | `ResendConfirmationRequest` |
| `inline_response_200` | `UserPagedResponse` |
| `id_status_body` | `UserStatusUpdateRequest` |

### Tous les contrats
- Fins de ligne normalisées en LF.
- **Précision** (AL-13) : `security: []` sur les opérations publiques, pour que le lint Redocly de la CI passe
  (règle `security-defined`). Aucun changement de comportement ni de code généré.
  - user-service : `registerUser`, `loginUser`, `refreshToken`, `forgotPassword`, `resetPassword`, `confirmEmail`, `resendConfirmation` ;
  - job-offer-service : `searchOffers`, `getOfferById`.
