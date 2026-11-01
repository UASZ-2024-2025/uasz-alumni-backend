# alumni-uasz-infra

Dépôt d'infrastructure partagée de la plateforme **Alumni UASZ** : namespace, ingress,
cluster Kubernetes local (kind) et scripts de déploiement.
Chaque microservice possède son propre dépôt et ses propres manifestes (`k8s/`).

## Structure attendue (dépôts frères dans le même dossier)

```
alumni_uasz/
├── alumni-uasz-infra/                       <- ce dépôt
├── user-service/
├── alumni-student-company-service/
├── job-offer-service/
├── analytics-matching-service/
└── notification-messaging-service/
```

## Démarrage local (kind)

Pré-requis : docker, kind, kubectl.

```bash
./scripts/kind-up.sh                         # cluster + ingress-nginx + namespace + ingress
(cd ../user-service && make k8s-secrets)     # secrets dev depuis .env (à faire pour chaque service)
./scripts/deploy-service.sh user-service
./scripts/kind-down.sh                       # suppression du cluster
```

## Routage

Un chemin par microservice, identique à l'URL de production des contrats
(`https://api.alumni-uasz.sn/<service>/v1`) : `/user-service/v1`, `/job-offer-service/v1`, ...
Le préfixe est porté par la variable `API_PREFIX` de chaque service (ConfigMap en K8s).
