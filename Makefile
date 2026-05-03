.PHONY: build test clean verify-json server-up

build:
	mvn package -DskipTests

test:
	mvn test

clean:
	mvn clean

verify-json:
	python3 -m json.tool examples/alerts/competing_hypotheses.json > /dev/null
	python3 -m json.tool examples/evidence/competing_hypotheses.json > /dev/null
	@echo "JSON validation passed"

server-up:
	mvn -pl sre-agent-server spring-boot:run

health:
	curl -s http://localhost:8080/health

KIND_CLUSTER_NAME := sre-agent
KIND_CONFIG := k8s/kind-sre-agent.yaml
.PHONY: cluster-up cluster-down cluster-status namespaces deploy-smoke smoke-test clean-smoke kube-context

cluster-up:
	kind create cluster --config $(KIND_CONFIG)

cluster-down:
	kind delete cluster --name $(KIND_CLUSTER_NAME)

kube-context:
	kubectl config use-context kind-$(KIND_CLUSTER_NAME)

cluster-status:
	kubectl cluster-info --context kind-$(KIND_CLUSTER_NAME)
	kubectl get nodes -o wide
	kubectl get pods -A

namespaces:
	kubectl apply -f k8s/namespaces/demo.yaml
	kubectl apply -f k8s/namespaces/observability.yaml
	kubectl apply -f k8s/namespaces/sre-agent.yaml
	kubectl get ns

deploy-smoke:
	kubectl apply -f k8s/demo-services/nginx-smoke.yaml
	kubectl -n demo rollout status deployment/nginx-smoke
	kubectl -n demo get pods -o wide
	kubectl -n demo get svc

smoke-test:
	@echo "Starting port-forward on localhost:8088..."
	@kubectl -n demo port-forward svc/nginx-smoke 8088:80 >/tmp/sre-agent-nginx-smoke.log 2>&1 & echo $$! > /tmp/sre-agent-nginx-smoke.pid
	@sleep 2
	@curl -I http://localhost:8088
	@kill $$(cat /tmp/sre-agent-nginx-smoke.pid) || true
	@rm -f /tmp/sre-agent-nginx-smoke.pid

clean-smoke:
	kubectl delete -f k8s/demo-services/nginx-smoke.yaml --ignore-not-found=true
