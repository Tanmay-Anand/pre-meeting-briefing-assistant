# LeadLens - local development.
#
#   make up        infra + backend + demo CRM
#   make down      stop everything
#   make status    what is running, and whether it answers
#
# Two things about this machine's setup are worth knowing before you start:
#
#   1. The build targets Java 25. A JDK 21 on PATH will not compile it, so JAVA_HOME is
#      pinned below. Override it if your JDK lives elsewhere:  make build JAVA_HOME=...
#
#   2. `make infra-up` needs a working Docker. If Docker will not start, you can point the
#      backend at any other Postgres instead - the datasource is env-driven:
#
#        make start-backend LEADLENS_DB_URL=jdbc:postgresql://localhost:5432/leadlens \
#                           LEADLENS_DB_USER=postgres LEADLENS_DB_PASSWORD=secret

BACKEND_DIR   := backend/leadlens
FRONTEND_DIR  := frontend

BACKEND_PORT  := 8080
DEMO_CRM_PORT := 5174

GIT_BASH := C:/Program Files/Git/bin/bash.exe

# Assigned with := rather than ?= on purpose. This machine has JAVA_HOME pointing at a JDK 21
# in the environment, and ?= would defer to it - producing "release version 25 not supported"
# only once javac actually has something new to compile, which is a confusing way to find out.
# Precedence: `make build JAVA_HOME=...` (command line) > LEADLENS_JAVA_HOME > the default.
JAVA_HOME := $(if $(LEADLENS_JAVA_HOME),$(LEADLENS_JAVA_HOME),C:/Program Files/Java/jdk-26)

export JAVA_HOME

MVNW   := ./mvnw -B --no-transfer-progress
START  := "$(GIT_BASH)" scripts/service-start.sh
STOP   := "$(GIT_BASH)" scripts/service-stop.sh
STATUS := "$(GIT_BASH)" scripts/service-status.sh

# ── Infrastructure ────────────────────────────────────────────────────────────

.PHONY: infra-up infra-down infra-restart infra-reset infra-ps

infra-up:
	docker compose up -d

infra-down:
	docker compose down

infra-restart:
	docker compose restart

# Drops the volume. The Demo CRM re-seeds itself on next boot, so this is the fastest way
# back to a clean demo - including undoing any activity you added mid-demo.
infra-reset:
	docker compose down -v

infra-ps:
	docker compose ps

# ── Backend ───────────────────────────────────────────────────────────────────

.PHONY: check-java build test verify start-backend stop-backend restart-backend run-backend

# Fails fast and legibly. Without this, a wrong JDK surfaces as "release version 25 not
# supported" from deep inside the compiler plugin, and only once something needs recompiling.
check-java:
	@test -x "$(JAVA_HOME)/bin/javac" || { \
		echo "JAVA_HOME does not point at a JDK: $(JAVA_HOME)"; \
		echo "This build needs JDK 25 or newer."; \
		echo "Override it:  make $(MAKECMDGOALS) JAVA_HOME=/path/to/jdk"; \
		exit 1; }

build: check-java
	cd $(BACKEND_DIR) && $(MVNW) -DskipTests package

test: check-java
	cd $(BACKEND_DIR) && $(MVNW) test

# Integration tests skip themselves without Docker. LEADLENS_REQUIRE_DOCKER turns that skip
# into a failure, which is what CI sets - a green build that skipped everything is not green.
verify: check-java
	cd $(BACKEND_DIR) && LEADLENS_REQUIRE_DOCKER=true $(MVNW) verify

start-backend:
	$(START) backend $(BACKEND_DIR) $(BACKEND_PORT)

stop-backend:
	$(STOP) backend

restart-backend: stop-backend start-backend

# Foreground, with live reload. Use this while working on the backend; `start-backend` is for
# when you just need it up.
run-backend: check-java
	cd $(BACKEND_DIR) && $(MVNW) spring-boot:run -Dspring-boot.run.profiles=local

# ── Frontend ──────────────────────────────────────────────────────────────────

.PHONY: install build-extension build-demo-crm build-frontend dev-extension dev-demo-crm type-check

install:
	cd $(FRONTEND_DIR) && npm install

# Emits frontend/dist - load that directory via chrome://extensions > Load unpacked.
build-extension:
	cd $(FRONTEND_DIR) && npm run build

build-demo-crm:
	cd $(FRONTEND_DIR) && npm run demo-crm:build

build-frontend: build-extension build-demo-crm

dev-extension:
	cd $(FRONTEND_DIR) && npm run dev

dev-demo-crm:
	cd $(FRONTEND_DIR) && npm run demo-crm:dev

type-check:
	cd $(FRONTEND_DIR) && npm run type-check

# ── Everything ────────────────────────────────────────────────────────────────

.PHONY: up down restart

up: infra-up build start-backend

down: stop-backend infra-down

restart: down up

# ── Status & health ───────────────────────────────────────────────────────────

.PHONY: status health

status:
	@echo "=== Services ==="
	@$(STATUS) backend $(BACKEND_PORT)
	@echo "=== Docker ==="
	@docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"

health:
	@echo [backend] && curl -sf http://localhost:$(BACKEND_PORT)/actuator/health || echo DOWN

# ── Demo data ─────────────────────────────────────────────────────────────────
# The Demo CRM seeds itself on first boot and is idempotent, so these are read-only probes.

.PHONY: demo-leads demo-rahul

demo-leads:
	@curl -sf -H "X-Demo-Tenant: t-acme" -H "X-Demo-User: u-priya" \
		http://localhost:$(BACKEND_PORT)/api/democrm/leads || echo "backend DOWN"

demo-rahul:
	@curl -sf -H "X-Demo-Tenant: t-acme" -H "X-Demo-User: u-priya" \
		http://localhost:$(BACKEND_PORT)/api/democrm/leads/12345 || echo "backend DOWN"

# ── Logs ──────────────────────────────────────────────────────────────────────
# Usage: make tail-backend N=200   (default N=100)

N ?= 100

.PHONY: logs logs-backend dump-backend tail-backend head-backend

logs: logs-backend

logs-backend:
	"$(GIT_BASH)" -c 'tail -f logs/backend.log'

dump-backend:
	"$(GIT_BASH)" -c 'cat logs/backend.log'

tail-backend:
	"$(GIT_BASH)" -c 'tail -n $(N) logs/backend.log'

head-backend:
	"$(GIT_BASH)" -c 'head -n $(N) logs/backend.log'

# ── Housekeeping ──────────────────────────────────────────────────────────────

.PHONY: clean

clean: stop-backend
	cd $(BACKEND_DIR) && $(MVNW) clean
	"$(GIT_BASH)" -c 'rm -rf $(FRONTEND_DIR)/dist $(FRONTEND_DIR)/demo-crm/dist logs/*.log logs/*.pid'
