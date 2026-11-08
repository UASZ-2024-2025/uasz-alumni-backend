# Diagrammes de séquence des flux clés

Flux cibles décrits à partir des contrats OpenAPI. Les appels de service à service sont en REST
authentifiés par un jeton de service (ADR-0010) ; les notifications partent de façon asynchrone après le commit,
avec timeout et retry (ADR-0006).

## 1. Inscription et activation du compte

```mermaid
sequenceDiagram
    autonumber
    actor V as Visiteur
    participant F as Front
    participant I as Ingress
    participant U as user-service
    participant UDB as user_db
    participant N as notification-messaging-service

    V->>F: Remplit le formulaire (nom, email, mot de passe, rôle)
    F->>I: POST /user-service/v1/auth/register
    I->>U: POST /v1/auth/register
    U->>U: Valide la requête (Bean Validation)
    U->>UDB: Email déjà utilisé ?
    alt email déjà utilisé
        U-->>F: 409 ErrorResponse
    else nouvel utilisateur
        U->>U: Hash du mot de passe (BCrypt)
        U->>UDB: INSERT user (status = EN_ATTENTE) + token de confirmation
        U->>N: POST /notifications/send (ACCOUNT_CONFIRMATION, EMAIL)
        N-->>U: 201
        U-->>F: 201 User
    end

    V->>F: Clique sur le lien reçu par email
    F->>U: POST /auth/confirm-email {token}
    U->>UDB: Vérifie le token et passe le statut à ACTIF
    U-->>F: 200 MessageResponse

    V->>F: Se connecte
    F->>U: POST /auth/login {email, password}
    U->>UDB: Charge l'utilisateur et vérifie le hash
    U->>U: Génère l'access token JWT (rôle, iss=alumni-uasz-user) et le refresh token
    U-->>F: 200 AuthResponse {accessToken, refreshToken, expiresIn, user}
```

## 2. Candidature à une offre

```mermaid
sequenceDiagram
    autonumber
    actor E as Étudiant
    participant F as Front
    participant J as job-offer-service
    participant JDB as job_db
    participant A as alumni-student-company-service
    participant N as notification-messaging-service
    actor R as Recruteur / Alumni

    E->>F: Consulte une offre et clique sur « Postuler »
    F->>J: POST /offers/{offerId}/applications {studentId, cvId, coverLetter} + JWT
    J->>J: Vérifie le JWT (signature, rôle ETUDIANT)
    J->>JDB: Charge l'offre
    alt offre absente ou statut ≠ OPEN, ou date limite dépassée
        J-->>F: 404 / 400 ErrorResponse
    else offre ouverte
        J->>A: GET /students/{studentId} et GET /students/{studentId}/cv
        A-->>J: StudentProfile, CvResponse
        J->>JDB: Candidature déjà existante ?
        alt déjà postulé
            J-->>F: 409 ErrorResponse
        else
            J->>JDB: INSERT application (status = SUBMITTED, studentName)
            J->>N: POST /notifications/send (userId = publisherId, APPLICATION_STATUS_CHANGED)
            J-->>F: 201 Application
        end
    end

    R->>F: Examine les candidatures
    F->>J: PATCH /applications/{id}/status {status: SHORTLISTED} + JWT
    J->>J: Vérifie le JWT : publieur de l'offre ou administrateur
    J->>JDB: UPDATE application (transaction)
    J->>J: Publie ApplicationStatusChanged
    J-->>F: 200 Application (n'attend pas la notification)

    Note over J,N: Après le commit, sur un autre thread (@Async)
    J->>J: Jeton de service (iss=alumni-uasz-internal,<br/>sub=job-offer-service, aud=notification-messaging-service, role=SERVICE)
    loop jusqu'à 4 tentatives (timeouts 2 s / 3 s, délais 500 ms → 1 s → 2 s)
        J->>N: POST /notifications/send {userId = étudiant, APPLICATION_STATUS_CHANGED}
        alt 201 Created
            N-->>J: Notification
        else réseau, timeout, 5xx ou 429
            N--xJ: erreur transitoire, nouvelle tentative
        end
    end
    opt toutes les tentatives échouent, ou erreur 4xx
        J->>J: Journalise l'échec (le statut reste enregistré)
    end
    E->>N: GET /notifications + JWT
    N-->>E: Notification « Votre candidature a été mise à jour »
```

## 3. Matching étudiant → offres

```mermaid
sequenceDiagram
    autonumber
    actor E as Étudiant
    participant F as Front
    participant M as analytics-matching-service
    participant A as alumni-student-company-service
    participant J as job-offer-service

    E->>F: Ouvre « Offres recommandées »
    F->>M: GET /analytics/matchings/students/{studentId}?minScore=50 + JWT
    M->>M: Vérifie le JWT (l'étudiant lui-même, un recruteur ou un admin)
    par Données de l'étudiant
        M->>A: GET /students/{studentId}
        A-->>M: StudentProfile (skills, specialty, availabilityStatus)
    and Offres candidates
        M->>J: GET /offers?status=OPEN
        J-->>M: JobOfferPagedResponse (requiredSkills)
    end
    loop pour chaque offre
        M->>M: matching = skills ∩ requiredSkills<br/>missing = requiredSkills − skills<br/>score = |matching| / |requiredSkills| × 100<br/>(intérêts et disponibilité en critères secondaires)
    end
    M->>M: Filtre score ≥ minScore, trie par score décroissant
    M-->>F: 200 liste de OfferMatchResult
    F-->>E: Offres classées, avec compétences manquantes
```
