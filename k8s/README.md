# Kubernetes manifests for enterprise-devsecops-observability-platform

This folder contains example Kubernetes manifests to run the services and a MySQL StatefulSet for persistent storage.

Important notes before applying:
- Edit the `image:` fields in each deployment (replace `your-registry/<service>:latest`) with your actual image names and tags (for example `nikitamathe/auth-service:7`).
- Ensure your cluster has a default StorageClass for dynamic PVC provisioning, or create PersistentVolumes for the MySQL PVC.
- The manifests are created in the `banking` namespace.
- Update secrets in `mysql-secret.yaml` with secure values (they are base64-encoded). Example provided uses `changeme` and `userpass`.

Recommended: professional Kustomize workflow (single command, but modular files)

1) Edit images in Kustomization (or run `kustomize edit set image`):

```bash
# example: replace placeholder with your real image
kustomize edit set image your-registry/account-service=nikitamathe/account-service:7
```

2) Apply everything from the `k8s/` directory in one command:

```bash
kubectl apply -k .
```

Notes:
- Files are modular and minimal for easier reviews and upgrades.
- Use `kubectl apply -k .` from inside `k8s/`.
- If you prefer, `kubectl apply -f .` also works because the Kustomization file is no longer treated as a manifest.

Verify:
- `kubectl -n banking get pods,svc,sts,pvc`
- `kubectl -n banking logs deploy/<service>` for app logs

If your cluster doesn't provide LoadBalancer services (e.g., Minikube), change the `frontend` service type to `NodePort` or use an Ingress controller.

If you want, I can:
- Replace `your-registry/...` placeholders with exact image names from your `docker-compose.yml` or CI pipeline.
- Add an Ingress manifest and TLS example.
