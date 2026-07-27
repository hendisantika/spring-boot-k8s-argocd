# spring-boot-k8s-argocd

A minimal Spring Boot REST service used to demonstrate GitOps continuous delivery to Kubernetes with
[Argo CD](https://argo-cd.readthedocs.io/). Argo CD watches this repository's `k8s/` directory and keeps the cluster in
sync with whatever is committed there.

## Tech stack

| Component     | Version                  |
|---------------|--------------------------|
| Java          | 25                       |
| Spring Boot   | 4.1.0                    |
| Build tool    | Maven (wrapper included) |
| Container     | Docker (multi-stage)     |
| Orchestration | Kubernetes               |
| CD            | Argo CD                  |

Dependencies: `spring-boot-starter-web`, `spring-boot-devtools`, `spring-boot-configuration-processor`, Lombok, and
`spring-boot-starter-test`.

## Project layout

```
├── Dockerfile                     # Multi-stage build (Maven → Amazon Corretto 25 Alpine)
├── application.yaml               # Argo CD Application manifest (apply into the argocd namespace)
├── install.yaml                   # Vendored Argo CD install manifest
├── build.sh                       # Local docker build + push (build-v2.sh is a legacy shim)
├── k8s/
│   └── spring-app-deployment.yaml # Deployment + NodePort Service watched by Argo CD
└── src/main/java/id/my/hendisantika/k8sargocd/
    ├── SpringBootK8sArgocdApplication.java  # Entry point; logs the running app version
    └── HelloController.java                 # GET /api/v1/hello
```

## API

| Method | Path            | Response                        |
|--------|-----------------|---------------------------------|
| `GET`  | `/api/v1/hello` | `hello from spring argo cd app` |

## Running locally

Requires JDK 25 on the `PATH` (or `JAVA_HOME`):

```bash
./mvnw spring-boot:run
curl http://localhost:8080/api/v1/hello
```

Run the tests:

```bash
./mvnw test
```

## Container image

Images are published as **`hendisantika/spring-boot-k8s-argocd:<github_run_number>`** — the tag is the GitHub Actions
workflow run number, so every push to `main` produces a new, immutable tag. Nothing uses `latest`.

CI does this automatically (see below). To build by hand, pass the tag explicitly:

```bash
./build.sh 42        # → hendisantika/spring-boot-k8s-argocd:42
```

`build.sh` also picks the tag up from `$GITHUB_RUN_NUMBER` when it is set. `build-v2.sh` is kept only as a shim that
forwards to `build.sh`; the old `v1`/`v2` tagging scheme is gone.

The `Dockerfile` builds in two stages — `maven:3.9-amazoncorretto-25` compiles the JAR, and the runtime image is
`amazoncorretto:25-alpine` carrying just that JAR.

## Deploying with Argo CD

1. **Install Argo CD** into the cluster:

   ```bash
   kubectl create namespace argocd
   kubectl apply -n argocd -f install.yaml
   ```

2. **Get the initial admin password** and open the UI:

   ```bash
   kubectl -n argocd get secret argocd-initial-admin-secret \
     -o jsonpath="{.data.password}" | base64 -d; echo
   kubectl port-forward svc/argocd-server -n argocd 8080:443
   ```

   Then browse to <https://localhost:8080> and log in as `admin`.

3. **Register the application**:

   ```bash
   kubectl apply -f application.yaml
   ```

   This creates an Argo CD `Application` that tracks `HEAD` of this repo, reads the `k8s/` path, and deploys into the
   `spring-argocd-app` namespace. `syncPolicy.automated` is enabled with `selfHeal` and `prune`, and
   `CreateNamespace=true` creates the target namespace on first sync — so no manual `kubectl apply` of the workload is
   needed.

4. **Reach the service** — see [Accessing the API from the cluster](#accessing-the-api-from-the-cluster) below.

## Accessing the API from the cluster

The workload is exposed by a `NodePort` Service named `spring-argocd-app` in the `spring-argocd-app` namespace, mapping
node port `30009` → service port `8080` → container port `8080`. The only route is `GET /api/v1/hello`.

### 0. Confirm the workload is up

```bash
kubectl get all -n spring-argocd-app
```

You want the pod `READY 1/1` and the service listing `8080:30009/TCP`:

```
NAME                                     READY   STATUS    RESTARTS   AGE
pod/spring-argocd-app-5d75dd6967-7nb4q   1/1     Running   0          10m

NAME                        TYPE       CLUSTER-IP      EXTERNAL-IP   PORT(S)          AGE
service/spring-argocd-app   NodePort   10.96.154.200   <none>        8080:30009/TCP   10m
```

If the pod is stuck in `ImagePullBackOff`, the tag in `k8s/spring-app-deployment.yaml` has not been published yet —
check which tag it wants with:

```bash
kubectl get deploy spring-argocd-app -n spring-argocd-app \
  -o jsonpath='{.spec.template.spec.containers[0].image}'; echo
```

### 1. `kubectl port-forward` (works on every platform)

The most reliable option, and the one to reach for on Docker Desktop or minikube's docker driver, where the node IP is
not routable from the host:

```bash
kubectl port-forward -n spring-argocd-app svc/spring-argocd-app 18080:8080
```

Leave that running, and in another terminal:

```bash
curl http://localhost:18080/api/v1/hello
# hello from spring argo cd app
```

Port `18080` is arbitrary — any free local port works. Forwarding to `svc/` load-balances across replicas; use
`pod/<name>` to pin one.

### 2. minikube

```bash
minikube service spring-argocd-app -n spring-argocd-app --url
```

On **Linux** this prints `http://<node-ip>:30009` and you can curl it directly.

On **macOS and Windows with the docker driver**, minikube prints a tunnelled `http://127.0.0.1:<random-port>` URL plus
the warning *"the terminal needs to be open to run it"* — the tunnel dies when you close it. Curl the printed URL from
a second terminal:

```bash
curl http://127.0.0.1:<printed-port>/api/v1/hello
# hello from spring argo cd app
```

> On macOS with the docker driver, `curl http://$(minikube ip):30009/...` **will not work** — it hangs and times out.
> The node lives inside a Docker network that the host cannot route to. Use the tunnel above or `port-forward`.
>
> The NodePort is still genuinely open on the node itself, which you can prove from inside it:
>
> ```bash
> minikube ssh "curl -s http://localhost:30009/api/v1/hello"
> ```

### 3. Directly by node IP (real clusters)

Where nodes are reachable — bare metal, a VM cluster, a cloud node pool with the port allowed by the firewall or
security group:

```bash
kubectl get nodes -o wide          # take EXTERNAL-IP, or INTERNAL-IP on a private network
curl http://<node-ip>:30009/api/v1/hello
```

### 4. From inside the cluster

Other pods reach it through the cluster DNS name `<service>.<namespace>.svc.cluster.local` on the **service** port
`8080`, not the node port:

```bash
kubectl run curltest --rm -i --restart=Never --image=curlimages/curl -- \
  curl -s http://spring-argocd-app.spring-argocd-app.svc.cluster.local:8080/api/v1/hello
# hello from spring argo cd app
```

Inside the `spring-argocd-app` namespace the short name `http://spring-argocd-app:8080` is enough.

### Which one to use

| Situation                                   | Use                                      |
|---------------------------------------------|------------------------------------------|
| Local dev on macOS/Windows, or anything CI   | `kubectl port-forward` (§1)              |
| minikube, want the NodePort semantics        | `minikube service --url` (§2)            |
| Real cluster with routable nodes             | `http://<node-ip>:30009` (§3)            |
| Another pod calling this service             | Cluster DNS on port 8080 (§4)            |

## CI/CD pipeline

`.github/workflows/maven.yml` runs on every push and pull request against `main`:

| Job       | When                | What it does                                                                                 |
|-----------|---------------------|----------------------------------------------------------------------------------------------|
| `build`   | pushes and PRs      | `mvn -B package` on JDK 25 (Temurin), then submits the dependency graph for Dependabot alerts |
| `publish` | pushes to `main`    | Builds and pushes `hendisantika/spring-boot-k8s-argocd:${{ github.run_number }}`, then rewrites the `image:` line in `k8s/spring-app-deployment.yaml` and commits it back |

That commit is what closes the GitOps loop: Argo CD sees the new tag in `k8s/` and rolls out the revision. The commit
message carries `[skip ci]` so it does not retrigger the workflow.

### Required repository setup

- Secrets `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` for the registry push.
- The repository default workflow permission is read-only, so both jobs declare `permissions: contents: write`
  explicitly — `build` needs it to submit the dependency graph, `publish` to push the tag-bump commit. An explicit
  `permissions` block overrides the read-only default, so no repository setting change is needed.

Dependabot is configured in `.github/dependabot.yml`.

## GitOps workflow

1. Push to `main`.
2. CI builds the image and pushes it tagged with the run number.
3. CI commits the new tag into `k8s/spring-app-deployment.yaml`.
4. Argo CD detects the drift and rolls out the new revision. Because `selfHeal` is on, manual edits made directly
   against the cluster are reverted back to what is in Git.

## Author

Hendi Santika — [s.id/hendisantika](https://s.id/hendisantika) · hendisantika@yahoo.co.id · Telegram
[@hendisantika34](https://t.me/hendisantika34)
