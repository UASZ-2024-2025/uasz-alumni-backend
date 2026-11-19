# Environnement de démo (ALUM-215)

Plateforme complète (5 microservices, frontend, PostgreSQL) disponible 24h/24 pour les démos,
en attendant le serveur de l'université. **Chaque fusion dans `develop` est déployée automatiquement.**

```
fusion dans develop ─► CI déjà validée dans la PR ─► workflow « CD - Démo AWS »
   images ghcr.io (amd64 + arm64) ─► rôle AWS par OIDC ─► SSM ─► deploy.sh sur chaque machine
                                                         healthchecks OK, sinon retour arrière
```

## Architecture

| Machine | Conteneurs |
|---|---|
| **vm-a** (t4g.small, 2 Go) | Caddy (HTTPS), frontend, PostgreSQL (5 bases), Mailpit, user-service, alumni-student-company-service |
| **vm-b** (t4g.small, 2 Go) | job-offer-service, analytics-matching-service, notification-messaging-service |

- Un seul domaine : `/` pour le front, `/<service>/v1` pour l'API (chemins des contrats), `/mailpit/` pour les e-mails.
  `/actuator` n'est jamais exposé.
- JVM réglées pour une petite machine (SerialGC, tas de 160 Mo, C1 seul) : 220 à 285 Mo par service, mesurés le 18/11/2026.
- Aucun port d'administration ouvert : seuls 80 et 443 sont publics. L'accès aux machines passe par
  **AWS Systems Manager > Session Manager** (console web, sans clé SSH).
- Aucune clé AWS stockée : GitHub prend un rôle temporaire par OIDC (un rôle par dépôt, environnement `demo` uniquement).
  Ce rôle ne peut lancer que le document SSM `alumni-demo-deploy` (paramètres filtrés, aucune commande libre),
  et `deploy.sh` refuse tout commit qui n'est pas dans `develop`.
- Un rôle IAM par machine : seule vm-a peut créer les secrets et écrire dans le bucket de sauvegardes.

## Fichiers

| Fichier | Rôle | Propre à AWS ? |
|---|---|---|
| `compose.yml`, `Caddyfile`, `postgres/init-dbs.sh` | la plateforme | non |
| `scripts/bootstrap.sh` | prépare une machine Ubuntu (Docker, swap, secrets, cron de sauvegarde) | non (options AWS facultatives) |
| `scripts/deploy.sh` | déploie, vérifie, revient en arrière si besoin | non |
| `scripts/backup.sh`, `scripts/restore.sh` | sauvegarde quotidienne / restauration | non (copie S3 facultative) |
| `aws/demo-stack.yaml` | pile CloudFormation : machines, réseau, IP, rôles, bucket, budget | **oui** (jetable) |
| `.github/workflows/deploy-demo.yml` | CD depuis `develop` | déclencheur AWS (SSM) |

## Mise en place sur AWS (une seule fois)

1. Console AWS, région **eu-west-3 (Paris)**, ouvrir **CloudShell** puis :
   ```bash
   git clone -b develop https://github.com/UASZ-2024-2025/uasz-alumni-backend.git && cd uasz-alumni-backend
   VPC=$(aws ec2 describe-vpcs --filters Name=is-default,Values=true --query 'Vpcs[0].VpcId' --output text)
   SUBNET=$(aws ec2 describe-subnets --filters Name=vpc-id,Values=$VPC Name=default-for-az,Values=true \
            --query 'Subnets[0].SubnetId' --output text)
   aws cloudformation deploy --stack-name alumni-demo --template-file deploy/demo/aws/demo-stack.yaml \
     --capabilities CAPABILITY_IAM \
     --parameter-overrides VpcId=$VPC SubnetId=$SUBNET BudgetEmail=<adresse>
   aws cloudformation describe-stacks --stack-name alumni-demo --query 'Stacks[0].Outputs' --output table
   ```
   Si le fournisseur OIDC GitHub existe déjà dans le compte : ajouter `CreateGitHubOidcProvider=false`.
2. Dans **les deux dépôts** GitHub, *Settings > Environments* : environnement **demo**, limité à la
   branche `develop` (*Deployment branches > Selected branches*). C'est la seule identité acceptée par les rôles AWS.
   Puis *Settings > Secrets and variables > Actions > Variables* : `AWS_REGION` = `eu-west-3`, `DEMO_URL` (sortie `DemoUrl`),
   `AWS_DEPLOY_ROLE_ARN` = sortie `BackendDeployRoleArn` (backend) ou `FrontendDeployRoleArn` (frontend).
3. Paquets ghcr.io (`uasz-alumni-backend/*`, `alumni-uasz-frontend/frontend`) : visibilité **publique**
   pour que les machines les téléchargent sans identifiants (le code est déjà public).
4. Lancer une première fois *Actions > CD - Démo AWS > Run workflow* (`all`) dans le backend, puis le workflow du frontend.

Le démarrage d'une machine est journalisé dans `/var/log/alumni-bootstrap.log`.

## Exploitation courante

Commandes à lancer dans Session Manager (`sudo -i`) :

| Besoin | Commande |
|---|---|
| état des conteneurs | `cd /opt/alumni/app/deploy/demo && docker compose --env-file /opt/alumni/demo.env --env-file /opt/alumni/tags.env ps` |
| journaux d'un service | `... logs -f --tail 200 user-service` |
| redéployer une version précise | `/opt/alumni/app/deploy/demo/scripts/deploy.sh user-service=<sha>` |
| sauvegarde immédiate | `/opt/alumni/app/deploy/demo/scripts/backup.sh` |
| restauration | `/opt/alumni/app/deploy/demo/scripts/restore.sh s3://<bucket>/<fichier>` (arrêter d'abord les services de vm-b) |
| versions déployées | `cat /opt/alumni/tags.env` |

- **Compte admin initial** et accès Mailpit : *Systems Manager > Parameter Store > `/alumni-demo/secrets`*
  (`BOOTSTRAP_ADMIN_PASSWORD`, `MAILPIT_UI_AUTH`). L'admin est `admin@alumni-uasz.sn`.
- **Vrais e-mails (Brevo)** : par défaut, les e-mails partent dans Mailpit. Pour envoyer de vrais e-mails, créer dans
  *Parameter Store* le paramètre **SecureString** `/alumni-demo/mail` :
  ```
  MAIL_HOST=smtp-relay.brevo.com
  MAIL_PORT=587
  MAIL_STARTTLS=true
  MAIL_USERNAME=<identifiant SMTP Brevo>
  MAIL_PASSWORD=<clé SMTP Brevo>
  MAIL_FROM=<expéditeur validé dans Brevo>
  ```
  puis relancer le workflow « CD - Démo AWS » (`service` = `none`). `deploy.sh` relit ce paramètre à chaque
  déploiement : le modifier change les réglages, le supprimer revient à Mailpit. Hors AWS, mettre ces lignes
  dans `/opt/alumni/demo.env`.
- **Sauvegardes** : chaque nuit à 02:30 UTC, 7 jours sur la machine, 30 jours dans S3.
- **Règle d'équipe** : une migration Flyway doit rester compatible avec la version précédente
  (ajouter d'abord, supprimer dans une PR suivante), car le retour arrière ne défait pas une migration.

## Budget

Free plan : 200 $ de crédits, compte fermé au bout de 6 mois. Coût prévu ≈ 35 $/mois
(2 × t4g.small + disques + 2 IPv4 publiques). Le budget `alumni-demo-mensuel` envoie une alerte à 50 % et à 80 %
de la consommation réelle (crédits non déduits), ainsi qu'une alerte quand la prévision dépasse 100 %.

## Migration vers le serveur de l'université

Rien dans la plateforme ne dépend d'AWS, la migration ne prend donc que quelques commandes :

```bash
# 1. sur le nouveau serveur (Ubuntu)
git clone -b develop https://github.com/UASZ-2024-2025/uasz-alumni-backend.git /opt/alumni/app
sudo /opt/alumni/app/deploy/demo/scripts/bootstrap.sh --role all --domain <domaine-du-serveur>
# 2. sur vm-a (AWS) : sauvegarde fraîche, puis la copier sur le nouveau serveur
sudo /opt/alumni/app/deploy/demo/scripts/backup.sh
# 3. sur le nouveau serveur
sudo /opt/alumni/app/deploy/demo/scripts/restore.sh /chemin/alumni-AAAAMMJJ-HHMMSS.sql.gz
```

4. Remplacer, dans `deploy-demo.yml` (et dans le workflow du frontend), l'étape SSM par un runner auto-hébergé
   sur le serveur, qui lance `deploy.sh`. Le reste du workflow ne change pas.
5. DNS du domaine vers le serveur (Caddy obtient le certificat tout seul), puis supprimer la pile `alumni-demo` dans CloudFormation.

Le paramètre `--role all` place tout sur une seule machine (`COMPOSE_PROFILES=vm-a,vm-b`).
