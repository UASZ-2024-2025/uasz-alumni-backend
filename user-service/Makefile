.RECIPEPREFIX = >
.DEFAULT_GOAL := help
IMAGE ?= user-service:dev
SERVICE := $(notdir $(CURDIR))

help: ## Liste des commandes
> @grep -E '^[a-zA-Z0-9_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf " %-14s %s\n",$$1,$$2}'

setup: ## 1re installation : crée .env (mot de passe aléatoire, secret JWT)
> ./scripts/setup.sh

env: ## Régénère POSTGRES_PASSWORD + JWT_SECRET (identifiants conservés) puis make reset-db
> ./scripts/gen-env.sh --force

env-complete: ## Ajoute à .env les clés manquantes de .env.example
> ./scripts/gen-env.sh --complete

jwt-sync: ## Recopie JWT_SECRET depuis ../user-service/.env (services non émetteurs)
> ./scripts/gen-env.sh --jwt-only

db: ## Démarre PostgreSQL + Mailpit (conteneurs)
> docker compose up -d --wait postgres mailpit

run: ## PostgreSQL + app en local (profil local, lit .env)
> ./scripts/start.sh

up: ## Stack complète en conteneurs (arrière-plan, rebuild)
> ./scripts/rebuild.sh

down: ## Arrête les conteneurs du service (données conservées)
> ./scripts/stop.sh

reset-db: ## DESTRUCTIF : efface la base Postgres et la relance
> ./scripts/reset.sh

nuke: ## DESTRUCTIF : arrête tout et supprime conteneurs + volumes
> docker compose down -v --remove-orphans

ps: ## État des conteneurs du service
> docker compose ps

logs: ## Logs de l'application (conteneur)
> docker compose logs -f user-service

logs-db: ## Logs de PostgreSQL
> docker compose logs -f postgres

psql: ## Console SQL dans le conteneur Postgres
> docker compose exec postgres sh -c 'psql -U "$$POSTGRES_USER" -d "$$POSTGRES_DB"'

image: ## Construit l'image Docker
> docker build -t $(IMAGE) .

k8s-secrets: ## Génère les secrets K8s (dev) à partir de .env
> ./scripts/k8s-secrets.sh

spi: ## (Contrat modifie) regenere le SPI de ce service dans ../spis/repo
> ../spis/build.sh $(SERVICE)
