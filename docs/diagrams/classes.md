# Diagrammes de classes (par service)

**Modèle de domaine cible** de chaque service. Il est dérivé des contrats OpenAPI v1.0.0 et servira de base
aux entités JPA (`model/`) et aux migrations Flyway.
Les références vers les données d'un **autre service** sont de simples UUID (pas de clé étrangère entre
bases). Elles sont marquées `«ref» Service`.

## 1. user-service

```mermaid
classDiagram
    direction LR
    class User {
        UUID id
        String lastName
        String firstName
        String email
        String passwordHash
        UserStatus status
        String phone
        String gender
        LocalDate birthDate
        Instant createdAt
    }
    class Role {
        UUID id
        RoleName name
        List~String~ permissions
    }
    class RefreshToken {
        UUID id
        String tokenHash
        Instant expiresAt
        boolean revoked
    }
    class VerificationToken {
        UUID id
        String tokenHash
        TokenPurpose purpose
        Instant expiresAt
        boolean used
    }
    class RoleName {
        <<enumeration>>
        ETUDIANT
        ALUMNI
        ENSEIGNANT
        ADMINISTRATEUR
    }
    class UserStatus {
        <<enumeration>>
        EN_ATTENTE
        ACTIF
        INACTIF
        SUSPENDU
    }
    class TokenPurpose {
        <<enumeration>>
        EMAIL_CONFIRMATION
        PASSWORD_RESET
    }
    User "*" --> "1" Role : role
    User "1" *-- "*" RefreshToken
    User "1" *-- "*" VerificationToken
    Role ..> RoleName
    User ..> UserStatus
    VerificationToken ..> TokenPurpose
```

> `RefreshToken`, `VerificationToken` et `passwordHash` ne figurent pas dans le contrat. Ce sont des classes
> internes, nécessaires aux opérations `/auth/refresh-token`, `/auth/logout`, `/auth/confirm-email` et
> `/auth/reset-password`.

## 2. alumni-student-company-service

```mermaid
classDiagram
    direction TB
    class StudentProfile {
        UUID id
        UUID userId «ref» user-service
        String firstName
        String lastName
        String email
        String specialty
        int academicYear
        String bio
        List~String~ interests
        List~String~ skills
        AvailabilityStatus availabilityStatus
        LocalDate availabilityDate
    }
    class Cv {
        UUID id
        String title
        String template
        String language
        String summary
        List~String~ skills
        String fileUrl
    }
    class CvExperience {
        String companyName
        String position
        LocalDate startDate
        LocalDate endDate
        String description
    }
    class CvEducation {
        String institution
        String degree
        String fieldOfStudy
        LocalDate startDate
        LocalDate endDate
    }
    class CvLanguage {
        String language
        String proficiency
    }
    class AlumniProfile {
        UUID id
        UUID userId «ref» user-service
        String firstName
        String lastName
        String email
        int graduationYear
        String specialty
        String currentCompany
        String currentPosition
        String linkedinUrl
    }
    class WorkExperience {
        UUID id
        String companyName
        String position
        LocalDate startDate
        LocalDate endDate
        boolean isCurrent
        String description
    }
    class Recommendation {
        UUID id
        UUID recommenderId «ref» user-service
        String comments
        Instant createdAt
    }
    class Company {
        UUID id
        String name
        String sector
        String description
        String website
        String address
        String contactEmail
        String contactPhone
        String logoUrl
        boolean isPartner
        Instant createdAt
    }
    class Promotion {
        UUID id
        int academicYear
        String specialty
        int graduateCount
    }
    class AvailabilityStatus {
        <<enumeration>>
        AVAILABLE_FOR_INTERNSHIP
        AVAILABLE_FOR_JOB
        AVAILABLE_IMMEDIATELY
        NOT_AVAILABLE
    }
    StudentProfile "1" *-- "0..1" Cv
    Cv "1" *-- "*" CvExperience
    Cv "1" *-- "*" CvEducation
    Cv "1" *-- "*" CvLanguage
    AlumniProfile "1" *-- "*" WorkExperience
    Recommendation "*" --> "1" StudentProfile : student
    Recommendation "*" --> "1" Company : company
    Promotion "1" o-- "*" StudentProfile : graduates
    StudentProfile ..> AvailabilityStatus
```

## 3. job-offer-service

```mermaid
classDiagram
    direction LR
    class JobOffer {
        UUID id
        String title
        OfferType type
        String description
        UUID companyId «ref» asc-service
        String companyName
        UUID publisherId «ref» user-service
        String publisherName
        String location
        RemoteType remoteType
        List~String~ requiredSkills
        Integer durationMonths
        String salaryRange
        LocalDate deadline
        OfferStatus status
        Instant createdAt
    }
    class Application {
        UUID id
        UUID studentId «ref» asc-service
        String studentName
        UUID cvId «ref» asc-service
        String coverLetter
        ApplicationStatus status
        Instant createdAt
    }
    class Proposal {
        UUID id
        UUID studentId «ref» asc-service
        String studentName
        UUID recruiterId «ref» user-service
        String recruiterName
        String message
        ProposalStatus status
        Instant createdAt
    }
    class OfferType {
        <<enumeration>>
        INTERNSHIP
        JOB
    }
    class RemoteType {
        <<enumeration>>
        ON_SITE
        HYBRID
        REMOTE
    }
    class OfferStatus {
        <<enumeration>>
        OPEN
        CLOSED
        ARCHIVED
    }
    class ApplicationStatus {
        <<enumeration>>
        SUBMITTED
        UNDER_REVIEW
        SHORTLISTED
        REJECTED
        ACCEPTED
    }
    class ProposalStatus {
        <<enumeration>>
        PENDING
        ACCEPTED
        REJECTED
        EXPIRED
    }
    JobOffer "1" *-- "*" Application : applications
    JobOffer "1" <-- "*" Proposal : offer
    JobOffer ..> OfferType
    JobOffer ..> RemoteType
    JobOffer ..> OfferStatus
    Application ..> ApplicationStatus
    Proposal ..> ProposalStatus
```

## 4. analytics-matching-service

Ce service calcule ses résultats à partir des données des autres services. Ses classes sont surtout des
**objets de calcul** (non persistés) et un éventuel cache de statistiques.

```mermaid
classDiagram
    direction LR
    class MatchingEngine {
        <<service>>
        +matchOffersForStudent(UUID studentId, int minScore) List~OfferMatchResult~
        +matchCandidatesForOffer(UUID offerId, int minScore) List~CandidateMatchResult~
        -score(Set~String~ studentSkills, Set~String~ requiredSkills) int
    }
    class AnalyticsService {
        <<service>>
        +dashboardStats() DashboardStats
        +insertionReport(Integer year, String specialty) InsertionReportResponse
        +skillsGap() SkillsGapResponse
    }
    class OfferMatchResult {
        UUID offerId
        String offerTitle
        String companyName
        int matchScore
        List~String~ matchingSkills
        List~String~ missingSkills
    }
    class CandidateMatchResult {
        UUID studentId
        String studentName
        String specialty
        int matchScore
        List~String~ matchingSkills
    }
    class DashboardStats {
        int alumniCount
        int studentCount
        int companyCount
        float insertionRate
        int internshipCount
        int jobOfferCount
        Instant updatedAt
    }
    class InsertionReportResponse {
        float overallInsertionRate
    }
    class InsertionReportItem {
        int graduationYear
        String specialty
        int totalGraduates
        int employedCount
        float insertionRate
        float averageMonthsToFirstJob
    }
    class SkillsGapResponse {
        List~String~ topRequiredSkills
    }
    class SkillsGapItem {
        String skillName
        float demandPercentage
        float supplyPercentage
        float gap
    }
    class StudentDirectoryClient {
        <<interface>>
        +findStudent(UUID id)
        +findGraduates(...)
    }
    class JobOfferClient {
        <<interface>>
        +findOpenOffers()
        +findOffer(UUID id)
    }
    MatchingEngine ..> OfferMatchResult : produit
    MatchingEngine ..> CandidateMatchResult : produit
    MatchingEngine --> StudentDirectoryClient
    MatchingEngine --> JobOfferClient
    AnalyticsService ..> DashboardStats : produit
    AnalyticsService ..> InsertionReportResponse : produit
    AnalyticsService ..> SkillsGapResponse : produit
    AnalyticsService --> StudentDirectoryClient
    AnalyticsService --> JobOfferClient
    InsertionReportResponse "1" *-- "*" InsertionReportItem
    SkillsGapResponse "1" *-- "*" SkillsGapItem
```

## 5. notification-messaging-service

```mermaid
classDiagram
    direction LR
    class PrivateMessage {
        UUID id
        UUID senderId «ref» user-service
        UUID recipientId «ref» user-service
        String content
        boolean isRead
        Instant createdAt
    }
    class Group {
        UUID id
        String name
        String description
        UUID ownerId «ref» user-service
        Instant createdAt
    }
    class GroupMember {
        UUID userId «ref» user-service
        GroupMemberRole groupRole
        Instant joinedAt
        Instant lastReadAt
    }
    class GroupMessage {
        UUID id
        UUID senderId «ref» user-service
        String content
        Instant createdAt
    }
    class Notification {
        UUID id
        UUID userId «ref» user-service
        NotificationType type
        NotificationChannel channel
        String title
        String content
        String referenceId
        boolean isRead
        Instant createdAt
    }
    class GroupMemberRole {
        <<enumeration>>
        OWNER
        MEMBER
    }
    class NotificationChannel {
        <<enumeration>>
        IN_APP
        EMAIL
        PUSH
    }
    class NotificationType {
        <<enumeration>>
        MESSAGE_RECEIVED
        APPLICATION_STATUS_CHANGED
        NEW_JOB_PROPOSAL
        NEW_MATCHING_OFFER
        ACCOUNT_CONFIRMATION
        SYSTEM_ALERT
        GROUP_INVITATION
        GROUP_MESSAGE_RECEIVED
    }
    Group "1" *-- "1..*" GroupMember : members
    Group "1" *-- "*" GroupMessage : messages
    GroupMember ..> GroupMemberRole
    Notification ..> NotificationType
    Notification ..> NotificationChannel
```

> `GroupMember.lastReadAt` est interne. Il sert à calculer `GroupSummary.unreadCount` et
> `PATCH /messages/groups/{groupId}/read`. `ConversationSummary` est une vue calculée sur `PrivateMessage`.
