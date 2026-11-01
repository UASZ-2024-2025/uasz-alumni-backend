#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

INGRESS_URL="https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.12.1/deploy/static/provider/kind/deploy.yaml"

if ! kind get clusters | grep -qx alumni-uasz; then
  kind create cluster --config kind/kind-config.yaml
fi
kubectl config use-context kind-alumni-uasz

kubectl apply -f "$INGRESS_URL"
kubectl -n ingress-nginx rollout status deploy/ingress-nginx-controller --timeout=180s

# Le namespace d'abord ; l'ingress ensuite (le webhook ingress-nginx doit être prêt)
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/ingress.yaml
echo "Cluster prêt. Déployer un service : ./scripts/deploy-service.sh user-service"
