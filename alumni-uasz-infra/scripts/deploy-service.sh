#!/usr/bin/env bash
# Usage : ./scripts/deploy-service.sh <dossier-du-service>   (ex: user-service)
# Pré-requis : `make k8s-secrets` déjà lancé dans le service (génère db.env / app.env).
set -euo pipefail
SVC="${1:?Usage: $0 <service-dir>}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SVC_DIR="$ROOT/$SVC"
[[ -d "$SVC_DIR" ]] || { echo "Dépôt introuvable : $SVC_DIR" >&2; exit 1; }
[[ -f "$SVC_DIR/k8s/overlays/dev/db.env" ]] || { echo "db.env absent : lancez 'make k8s-secrets' dans $SVC" >&2; exit 1; }

docker build -t "$SVC:dev" "$SVC_DIR"
kind load docker-image "$SVC:dev" --name alumni-uasz
kubectl apply -k "$SVC_DIR/k8s/overlays/dev"
# redémarre pour prendre en compte une image :dev reconstruite
kubectl -n alumni-uasz rollout restart deploy/"$SVC"
kubectl -n alumni-uasz rollout status deploy/"$SVC" --timeout=240s
kubectl -n alumni-uasz get pods -l app.kubernetes.io/name="$SVC"
