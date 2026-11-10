# Diagramme de déploiement

## Cible Kubernetes (dev avec kind, prod avec le même manifeste)

```mermaid
flowchart TB
    client["«device»<br/>Navigateur / mobile"]
    gh["«execution environment»<br/>GitHub Actions"]
    ghcr[("«artifact repository»<br/>GHCR<br/>ghcr.io/…/service:tag")]

    subgraph k8s["«node» Cluster Kubernetes"]
        subgraph ingressns["«execution environment» namespace ingress-nginx"]
            ing["«artifact» Ingress NGINX<br/>api.alumni-uasz.sn"]
        end
        subgraph ns["«execution environment» namespace alumni-uasz"]
            subgraph pu["«deployment» user-service ×2"]
                pu1["«pod» JRE 21<br/>user-service.jar :8081"]
            end
            subgraph pa["«deployment» alumni-student-company-service ×2"]
                pa1["«pod» :8082"]
            end
            subgraph pj["«deployment» job-offer-service ×2"]
                pj1["«pod» :8083"]
            end
            subgraph pan["«deployment» analytics-matching-service ×2"]
                pan1["«pod» :8084"]
            end
            subgraph pn["«deployment» notification-messaging-service ×2"]
                pn1["«pod» :8085"]
            end
            dbu[("«statefulset» postgres:16<br/>user-service-db")]
            dba[("«statefulset» postgres:16<br/>asc-db")]
            dbj[("«statefulset» postgres:16<br/>job-offer-db")]
            dban[("«statefulset» postgres:16<br/>analytics-db")]
            dbn[("«statefulset» postgres:16<br/>notification-db")]
            cfg["«artifact» ConfigMap + Secrets<br/>API_PREFIX, JWT_SECRET, DB…"]
        end
    end

    client -->|HTTPS| ing
    ing -->|/user-service/v1| pu1
    ing -->|/alumni-student-company-service/v1| pa1
    ing -->|/job-offer-service/v1| pj1
    ing -->|/analytics-matching-service/v1| pan1
    ing -->|/notification-messaging-service/v1| pn1

    pu1 -->|TCP 5432| dbu
    pa1 -->|TCP 5432| dba
    pj1 -->|TCP 5432| dbj
    pan1 -->|TCP 5432| dban
    pn1 -->|TCP 5432| dbn

    gh -->|docker push| ghcr
    ghcr -->|image pull| ns
    gh -->|kubectl apply -k| ns
```

| Élément | Détail (manifestes `*/k8s/`) |
| --- | --- |
| Deployment | 2 réplicas, `RollingUpdate` (`maxUnavailable: 0`, `maxSurge: 1`), arrêt gracieux de 30 s |
| Pod | Image `eclipse-temurin` JRE 21, non root (UID 10001), seccomp `RuntimeDefault`, 200m–1 CPU, 384–512 Mi |
| Sondes | `/actuator/health/liveness` et `/actuator/health/readiness` |
| Configuration | `ConfigMap` (port, préfixe, CORS, émetteur JWT) ; `Secrets` (`JWT_SECRET`, identifiants de la base) |
| Base de données | Overlay `dev` : `StatefulSet` `postgres:16-alpine` avec un PVC de 1 Gi par service. En prod : base gérée ou StatefulSet dédié |
| Ingress | Classe `nginx`, un chemin `Prefix` par service |

## Poste de développement (docker-compose)

```mermaid
flowchart LR
    dev["«device» Poste développeur"]
    subgraph compose["«execution environment» Docker Engine (docker-compose d'un service)"]
        app["«container» <service><br/>127.0.0.1:808x"]
        pg[("«container» postgres:16-alpine<br/>127.0.0.1:DB_PORT<br/>volume pgdata")]
    end
    dev -->|make up| compose
    app -->|jdbc:postgresql://postgres:5432| pg
```
