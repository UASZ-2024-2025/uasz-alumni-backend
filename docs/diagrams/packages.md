# Diagramme de packages

## Vue d'ensemble du monorepo

```mermaid
flowchart TB
    subgraph repo["«repository» uasz-alumni-backend"]
        contracts["«package» contracts<br/>*.yaml OpenAPI + CHANGELOG"]
        spis["«package» spis<br/>génération + dépôt Maven spis/repo"]
        subgraph services["«package» microservices"]
            u["user-service"]
            a["alumni-student-company-service"]
            j["job-offer-service"]
            an["analytics-matching-service"]
            n["notification-messaging-service"]
        end
        docker["«package» docker<br/>service.Dockerfile"]
        infra["«package» alumni-uasz-infra<br/>namespace, ingress, kind"]
        sonar["«package» sonar"]
        gh["«package» .github/workflows<br/>ci, deploy, sonar"]
    end

    spis -. "«generate» depuis" .-> contracts
    services -. "«import» *-spi" .-> spis
    docker -. "«build»" .-> services
    infra -. "«deploy»" .-> services
    gh -. "«use»" .-> docker
    sonar -. "«analyse»" .-> services
```

## Structure interne d'un microservice

Tous les services ont la même structure. Exemple : `job-offer-service`
(`com.uasz.alumni.joboffer`). Les autres racines sont `...user`, `...alumnistudentcompany`,
`...analyticsmatching` et `...notificationmessaging`.

```mermaid
flowchart TB
    subgraph root["«package» com.uasz.alumni.joboffer"]
        controller["«package» controller<br/>OffersController<br/>ApplicationsController<br/>ProposalsController"]
        service["«package» service<br/>logique métier"]
        repository["«package» repository<br/>Spring Data JPA"]
        model["«package» model<br/>entités JPA"]
        dto["«package» dto<br/>mappers entité ↔ SPI"]
        security["«package» security"]
        jwt["«package» jwt"]
        config["«package» config<br/>SecurityConfig, ApiPrefixConfig"]
        exceptions["«package» exceptions<br/>ApiException, GlobalExceptionHandler<br/>NotImplementedYetException"]
        utils["«package» utils"]
    end

    subgraph spi["«package» com.uasz.alumni.joboffer.spi (généré)"]
        api["«package» spi.api<br/>OffersApi, ApplicationsApi, ProposalsApi"]
        spimodel["«package» spi.model<br/>JobOffer, Application, Proposal…"]
    end

    controller -. "«realize»" .-> api
    controller --> service
    controller --> dto
    dto --> spimodel
    dto --> model
    service --> repository
    service --> model
    repository --> model
    config --> security
    security --> jwt
    exceptions --> spimodel
    controller --> exceptions
```

## Règles de dépendance

- `controller` ne dépend que de `service`, de `dto` et des interfaces SPI. Il n'accède jamais au `repository`.
- Les entités (`model`) ne sortent jamais du service. On expose uniquement les modèles `spi.model` via `dto`.
- Le package `spi` est **généré**. On ne le modifie pas à la main : on modifie le contrat.
