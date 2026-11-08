# Diagramme de composants

Composants logiciels de la plateforme et interfaces (fournies ●, requises ◐) entre eux.
Chaque interface fournie correspond à un contrat OpenAPI de [`contracts/`](../../contracts/).

```mermaid
flowchart TB
    front["«component»<br/><b>Application front</b> (web)"]
    ingress["«component»<br/><b>Ingress NGINX</b><br/>routage par préfixe /{service}/v1"]

    subgraph platform["«subsystem» Backend Alumni UASZ"]
        direction LR
        user["«component»<br/><b>user-service</b><br/>────────<br/>● AuthApi<br/>● UsersApi<br/>● RolesApi"]
        asc["«component»<br/><b>alumni-student-company-service</b><br/>────────<br/>● StudentsApi · AlumniApi<br/>● CompaniesApi · PromotionsApi"]
        job["«component»<br/><b>job-offer-service</b><br/>────────<br/>● OffersApi<br/>● ApplicationsApi<br/>● ProposalsApi"]
        ana["«component»<br/><b>analytics-matching-service</b><br/>────────<br/>● MatchingsApi<br/>● DashboardApi<br/>● AnalyticsReportsApi"]
        notif["«component»<br/><b>notification-messaging-service</b><br/>────────<br/>● PrivateMessagingApi<br/>● GroupMessagingApi<br/>● NotificationsApi"]
    end

    subgraph data["«subsystem» Persistance : une base PostgreSQL par service"]
        direction LR
        dbU[("user_db")]
        dbA[("asc_db")]
        dbJ[("job_db")]
        dbN[("analytics_db")]
        dbM[("notif_db")]
    end

    front -->|"HTTPS + JWT"| ingress
    ingress --> user & asc & job & ana & notif

    ana -. "◐ profils, compétences" .-> asc
    ana -. "◐ offres ouvertes" .-> job
    job -. "◐ entreprise, CV" .-> asc
    job -. "◐ notifications" .-> notif
    ana -. "◐ notifications" .-> notif
    user -. "◐ notifications" .-> notif

    user -->|JDBC| dbU
    asc -->|JDBC| dbA
    job -->|JDBC| dbJ
    ana -->|JDBC| dbN
    notif -->|JDBC| dbM

```

## Lecture

| Élément | Signification |
| --- | --- |
| ● dans un composant | Interface **fournie** par le composant : interfaces Java `*Api` générées depuis le contrat |
| ◐ « notifications » | Appel de `POST /notifications/send` |
| Flèche pleine | Appel REST entrant via l'ingress, ou accès JDBC à la base du service |
| Flèche pointillée ◐ | Interface **requise** : appel REST de service à service, authentifié par un jeton de service (ADR-0010) |
| Interfaces `*Api` | Générées depuis `contracts/*.yaml` dans les artefacts Maven `*-spi:1.0.0` et implémentées par les contrôleurs de chaque composant (voir le [diagramme de packages](packages.md)) |

Le JWT émis par `user-service` est vérifié **localement** par chaque service avec un secret partagé.
Il n'y a donc pas de dépendance à `user-service` à l'exécution pour l'authentification.
