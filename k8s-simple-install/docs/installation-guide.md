# Guide d'installation Kubernetes (k8s-simple-install)

## Étapes

1. Exécuter `install-master.sh` sur la machine master.
2. Copier `/tmp/join.sh` depuis le master vers les workers.
3. Exécuter `install-worker.sh` sur chaque worker.
4. Vérifier avec `kubectl get nodes`.
5. Déployer l'app avec `deploy-test-app.sh`.
6. (Optionnel) Appliquer `dashboard.yaml`.

## Vérifications
- `kubectl get pods -A`
- `kubectl get svc`
- `kubectl describe node`
