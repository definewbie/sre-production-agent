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
