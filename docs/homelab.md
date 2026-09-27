# Homelab deployment

On the [homelab], Argo CD deploys the Helm chart in
[`chart/flipfinder`](../chart/flipfinder) from this repository's `master`
branch. The homelab version keeps its price database on a persistent volume,
and features that rely on the homelab are enabled by its `homelab` Spring
profile.

## The chart

The chart follows the homelab's application contract:

- Argo CD syncs the chart directly from this repository.
- The namespace receives `gateway.homelab/access: public`.
- An `HTTPRoute` for `flipfinder.localhost` attaches to
  `gateway-system/homelab` on its `http` listener.
- The service stays internal on port 80; the container listens on port 8080.
- The `homelab` Spring profile stores SQLite on a 1 Gi `ReadWriteOnce`
  volume.
- One replica runs, because a SQLite database has one writer.

Set `persistence.enabled=false` to run the in-memory `local` profile with no
volume. Check the chart without deploying it:

```bash
helm lint chart/flipfinder
helm template flipfinder chart/flipfinder --namespace flipfinder
```

## Registering with Argo CD

[`deploy/argocd-application.yaml`](../deploy/argocd-application.yaml)
registers the chart the same way as the homelab's other applications: the
`default` project, the `flipfinder` namespace, and automated sync with pruning
and self-healing. Apply it once:

```bash
kubectl --context kind-homelab-dev apply -f deploy/argocd-application.yaml
```

The registration lives in the cluster, not in Git; the homelab's backup
exports it. Once it syncs, the app is at:

- Web app: <http://flipfinder.localhost:8080>
- API: <http://flipfinder.localhost:8080/api/flips>
- Swagger UI: <http://flipfinder.localhost:8080/swagger-ui.html>

To check on it:

```bash
kubectl --context kind-homelab-dev -n argocd get application flipfinder
kubectl --context kind-homelab-dev -n flipfinder logs deploy/flipfinder
```

## How changes reach the homelab

Argo CD applies whatever the chart on `master` describes, and the chart names
one image version. A merged chart change is applied within a few minutes; new
code ships when a [release](development.md#ci-and-releases) bumps that version.

### Running a master build

Every push to `master` publishes an image tagged `sha-<commit>`. To run one
before it is released, override the chart's image tag on the Application:

```bash
kubectl --context kind-homelab-dev -n argocd patch application flipfinder --type merge \
  -p '{"spec":{"source":{"helm":{"parameters":[{"name":"image.tag","value":"sha-5ba07f3"}]}}}}'
```

Go back to the chart's version by removing the override:

```bash
kubectl --context kind-homelab-dev -n argocd patch application flipfinder --type merge \
  -p '{"spec":{"source":{"helm":null}}}'
```

### Running a local build

To try uncommitted work, build the image, load it into the Kind nodes and
point the Application at it the same way:

```bash
tag=dev-$(date +%s)
docker build --tag "flipfinder:$tag" .
kind load docker-image "flipfinder:$tag" --name homelab-dev
kubectl --context kind-homelab-dev -n argocd patch application flipfinder --type merge \
  -p "{\"spec\":{\"source\":{\"helm\":{\"parameters\":[{\"name\":\"image.repository\",\"value\":\"flipfinder\"},{\"name\":\"image.tag\",\"value\":\"$tag\"}]}}}}"
```

Remove the override as above when you are done.

[homelab]: https://github.com/griffinseibold/Homelab
