# Alumni UASZ

Plateforme Alumni UASZ : architecture microservices, approche **contract first**, **monorepo**.

| Dossier | Port | Rôle |
|---|---|---|
| `alumni-uasz-infra` | | namespace, ingress, cluster kind local, scripts |
| `user-service` | 8081 | utilisateurs, authentification, rôles (émet les JWT) |
| `alumni-student-company-service` | 8082 | étudiants, CV, alumni, entreprises, promotions |
| `job-offer-service` | 8083 | offres, candidatures, propositions |
| `analytics-matching-service` | 8084 | matching, tableau de bord, rapports |
| `notification-messaging-service` | 8085 | messagerie privée, groupes, notifications |
| `contracts` | | contrats OpenAPI (source de vérité) |

## Première installation

```bash
(cd user-service && make setup)          # d'abord : génère le secret JWT partagé
for s in alumni-student-company job-offer analytics-matching notification-messaging; do
  (cd "$s-service" && make setup)
done
```

## CI/CD

`.github/workflows/` (racine) : CI et CD ne traitent que les microservices modifiés.
