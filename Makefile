.PHONY: build test clean verify-json server-up
.PHONY: cluster-up cluster-down kube-context cluster-status namespaces
.PHONY: deploy-smoke smoke-test clean-smoke
.PHONY: deploy-crashloop-demo wait-crashloop collect-k8s-evidence-live investigate-k8s-live clean-crashloop-demo live-k8s-demo
.PHONY: observability-install observability-uninstall observability-status observability-port-forward observability-check
.PHONY: demo-build-images demo-load-images demo-services-install demo-services-uninstall
.PHONY: demo-services-status demo-services-port-forward demo-services-check
.PHONY: demo-traffic-start demo-fault-normal demo-fault-payment-latency
.PHONY: demo-fault-payment-error demo-fault-payment-timeout

# ─── Build & Test ───────────────────────────────────────────

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

# ─── kind Cluster Lifecycle ────────────────────────────────

KIND_CLUSTER_NAME := sre-agent
KIND_CONFIG := k8s/kind-sre-agent.yaml

cluster-up:
	@echo "Creating kind cluster '$(KIND_CLUSTER_NAME)'..."
	kind create cluster --config $(KIND_CONFIG) || \
		(echo "Cluster may already exist. Use 'make cluster-down' first to recreate." && exit 1)
	@echo "Switching kube context..."
	kubectl config use-context kind-$(KIND_CLUSTER_NAME)
	@echo "Cluster ready."
	make cluster-status

cluster-down:
	@echo "Deleting kind cluster '$(KIND_CLUSTER_NAME)'..."
	kind delete cluster --name $(KIND_CLUSTER_NAME)
	@echo "Cluster deleted."

kube-context:
	kubectl config use-context kind-$(KIND_CLUSTER_NAME)

cluster-status:
	@echo "=== Cluster Info ==="
	kubectl cluster-info --context kind-$(KIND_CLUSTER_NAME)
	@echo "=== Nodes ==="
	kubectl get nodes -o wide
	@echo "=== All Pods ==="
	kubectl get pods -A

namespaces:
	kubectl apply -f k8s/namespaces/demo.yaml
	kubectl apply -f k8s/namespaces/observability.yaml
	kubectl apply -f k8s/namespaces/sre-agent.yaml
	@echo "Namespaces created."
	kubectl get ns

# ─── Smoke Test (nginx, Step H) ───────────────────────────

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

# ─── CrashLoopBackOff Live Demo (Step K) ──────────────────

# Find the CLI jar dynamically
CLI_JAR := $(shell ls sre-agent-cli/target/sre-agent-cli-*.jar 2>/dev/null | head -1)

deploy-crashloop-demo:
	@echo "Deploying recommend-service CrashLoopBackOff demo..."
	kubectl apply -f k8s/demo-services/recommend-crashloop-demo.yaml
	@echo "Deployment created. Waiting for CrashLoopBackOff..."
	@echo "Run 'make wait-crashloop' to wait for the pod to enter CrashLoopBackOff state."

wait-crashloop:
	@echo "Waiting for recommend-service pod to enter CrashLoopBackOff..."
	@timeout=90; \
	elapsed=0; \
	while [ $$elapsed -lt $$timeout ]; do \
		status=$$(kubectl -n demo get pods -l app=recommend-service -o jsonpath='{.items[0].status.containerStatuses[0].state.waiting.reason}' 2>/dev/null || echo ""); \
		restarts=$$(kubectl -n demo get pods -l app=recommend-service -o jsonpath='{.items[0].status.containerStatuses[0].restartCount}' 2>/dev/null || echo "0"); \
		if [ "$$status" = "CrashLoopBackOff" ] || [ "$$restarts" -ge 2 ]; then \
			echo ""; \
			echo "✓ Pod is in CrashLoopBackOff (restarts: $$restarts)"; \
			kubectl -n demo get pods -l app=recommend-service -o wide; \
			exit 0; \
		fi; \
		echo "  Waiting... (status=$$status, restarts=$$restarts, elapsed=$${elapsed}s)"; \
		sleep 5; \
		elapsed=$$((elapsed + 5)); \
	done; \
	echo "⚠ Timeout waiting for CrashLoopBackOff. Current state:"; \
	kubectl -n demo get pods -l app=recommend-service -o wide; \
	kubectl -n demo describe pod -l app=recommend-service | tail -20; \
	exit 1

collect-k8s-evidence-live:
	@if [ -z "$(CLI_JAR)" ]; then echo "Error: CLI jar not found. Run 'make build' first."; exit 1; fi
	@echo "Collecting live K8s evidence from kind cluster..."
	java -jar $(CLI_JAR) collect-k8s-evidence \
		--namespace demo \
		--service recommend-service \
		--output examples/evidence/k8s_crashloop_live_evidence.json \
		--reader kubectl
	@echo "Live evidence collected."
	@echo "=== Evidence Summary ==="
	@cat examples/evidence/k8s_crashloop_live_evidence.json | python3 -c "import json,sys; items=json.load(sys.stdin); print('Evidence count:', len(items)); [print('  -', i.get('evidence_type','?')) for i in items]"

investigate-k8s-live:
	@if [ -z "$(CLI_JAR)" ]; then echo "Error: CLI jar not found. Run 'make build' first."; exit 1; fi
	@echo "Running RCA investigation with live K8s evidence..."
	java -jar $(CLI_JAR) investigate \
		--alert examples/alerts/k8s_crashloop.json \
		--evidence examples/evidence/k8s_crashloop_live_evidence.json \
		--output examples/reports/k8s_crashloop_live_report.md \
		--show-trace
	@echo "=== Report Generated ==="
	@head -30 examples/reports/k8s_crashloop_live_report.md

clean-crashloop-demo:
	@echo "Cleaning up CrashLoopBackOff demo..."
	kubectl delete -f k8s/demo-services/recommend-crashloop-demo.yaml --ignore-not-found=true
	@echo "Demo resources deleted."

live-k8s-demo: cluster-status namespaces deploy-crashloop-demo wait-crashloop collect-k8s-evidence-live investigate-k8s-live
	@echo ""
	@echo "═══════════════════════════════════════════════════"
	@echo "  Live K8s Demo Complete!"
	@echo "═══════════════════════════════════════════════════"
	@echo ""
	@echo "Evidence: examples/evidence/k8s_crashloop_live_evidence.json"
	@echo "Report:   examples/reports/k8s_crashloop_live_report.md"
	@echo ""
	@echo "Cleanup:  make clean-crashloop-demo"
	@echo "Tear down: make cluster-down"

# ─── Observability Stack (Step T) ─────────────────────────

observability-install:
	@scripts/observability/install-observability.sh

observability-uninstall:
	@scripts/observability/uninstall-observability.sh

observability-status:
	@echo "=== Observability Stack Status ==="
	@kubectl -n observability get pods -o wide 2>/dev/null || echo "  (kind cluster not reachable)"
	@echo ""
	@echo "=== Helm Releases ==="
	@helm list -n observability 2>/dev/null || echo "  (no releases found)"

observability-port-forward:
	@scripts/observability/port-forward-observability.sh

observability-check:
	@scripts/observability/check-observability.sh

# ─── Demo Services (3-service demo) ───────────────────────

# Short aliases (recommended)
demo-build: demo-build-images demo-load-images
demo-deploy: demo-services-install
demo-check: demo-services-check
demo-status: demo-services-status
demo-port-forward: demo-services-port-forward
demo-uninstall: demo-services-uninstall
demo-traffic: demo-traffic-start

# Full targets
demo-build-images:
	@scripts/demo-services/build-demo-images.sh

demo-load-images:
	@scripts/demo-services/load-demo-images-kind.sh

demo-services-install:
	@scripts/demo-services/deploy-demo-services.sh

demo-services-uninstall:
	@scripts/demo-services/uninstall-demo-services.sh

demo-services-status:
	@echo "=== Demo Services Pods ==="
	@kubectl -n demo get pods -o wide
	@echo ""
	@echo "=== Demo Services ==="
	@kubectl -n demo get svc

demo-services-port-forward:
	@scripts/demo-services/port-forward-demo-services.sh

demo-services-check:
	@scripts/demo-services/check-demo-services.sh

demo-traffic-start:
	@scripts/demo-services/generate-traffic.sh

# Fault injection (requires port-forward active)
demo-fault-normal:
	@echo "Clearing all faults on payment-service..."
	@curl -s -X POST http://localhost:18082/fault-config -H 'Content-Type: application/json' -d '{"mode":"normal","latencyMs":0,"errorRate":0.0,"timeoutRate":0.0}' && echo ""

demo-fault-payment-latency:
	@echo "Injecting latency (2000ms) on payment-service..."
	@curl -s -X POST http://localhost:18082/fault-config -H 'Content-Type: application/json' -d '{"mode":"latency","latencyMs":2000,"errorRate":0.0,"timeoutRate":0.0}' && echo ""

demo-fault-payment-error:
	@echo "Injecting 50%% error rate on payment-service..."
	@curl -s -X POST http://localhost:18082/fault-config -H 'Content-Type: application/json' -d '{"mode":"error","latencyMs":0,"errorRate":0.5,"timeoutRate":0.0}' && echo ""

demo-fault-inventory-latency:
	@echo "Injecting latency (3000ms) on inventory-service..."
	@curl -s -X POST http://localhost:18083/fault-config -H 'Content-Type: application/json' -d '{"mode":"latency","latencyMs":3000,"errorRate":0.0,"timeoutRate":0.0}' && echo ""

demo-fault-inventory-error:
	@echo "Injecting 80%% error rate on inventory-service..."
	@curl -s -X POST http://localhost:18083/fault-config -H 'Content-Type: application/json' -d '{"mode":"error","latencyMs":0,"errorRate":0.8,"timeoutRate":0.0}' && echo ""
