# Journal des modifications des contrats OpenAPI

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
