# Commandes notification-messaging-service

Toutes les commandes se lancent depuis `alumni_uasz/notification-messaging-service/`. `make help` les liste.

| Besoin | Commande |
|---|---|
| **Première fois** (crée `.env`, secrets) | `make setup` |
| Dev : PostgreSQL en conteneur, app en local | `make run` |
| Tout en conteneurs (arrière-plan) | `make up` |
| Seulement PostgreSQL | `make db` |
| **Reset DB** (efface les données) | `make reset-db` |
| Console SQL / logs Postgres | `make psql` / `make logs-db` |
| État / logs de l'appli | `make ps` / `make logs` |
| **Arrêter** les conteneurs (données gardées) | `make down` |
| Tout supprimer (conteneurs + volumes) | `make nuke` |
| Régénérer les secrets (identifiants conservés) | `make env` puis `make reset-db` |
| Ajouter les clés manquantes de `.env.example` | `make env-complete` |
| Recopier JWT_SECRET depuis `user-service` | `make jwt-sync` |
| Construire l'image | `make image` |
| Secrets K8s (dev) depuis `.env` | `make k8s-secrets` |

Ordre conseillé : `make setup` dans `user-service` **d'abord** (il génère le secret JWT), puis dans les autres.
Après `make env` dans `user-service` : `make jwt-sync` dans chaque autre service.

## Kubernetes (kind)

```bash
cd ../alumni-uasz-infra && ./scripts/kind-up.sh          # une fois : cluster + ingress
cd ../notification-messaging-service && make k8s-secrets
cd ../alumni-uasz-infra && ./scripts/deploy-service.sh notification-messaging-service
kubectl -n alumni-uasz get pods
kubectl -n alumni-uasz logs -f deploy/notification-messaging-service
./scripts/kind-down.sh                           # supprime le cluster
```

## URLs

- Santé : `http://localhost:8085/actuator/health`
- API locale : `http://localhost:8085${API_PREFIX}/...` (ex. `http://localhost:8085/v1/...`)
- API via ingress : `http://localhost/notification-messaging-service/v1/...`
