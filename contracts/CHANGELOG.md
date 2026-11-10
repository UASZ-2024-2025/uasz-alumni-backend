# Journal des modifications des contrats OpenAPI

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
