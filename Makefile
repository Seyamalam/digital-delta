SHELL := /bin/bash

.PHONY: setup verify connected test build lint format seed reset demo

setup:
	scripts/setup-local.sh

verify test build:
	scripts/verify-local.sh

connected:
	scripts/verify-local.sh --connected

lint:
	cd packages/proto && buf lint && buf format --diff --exit-code
	cd services/node && test -z "$$(gofmt -l .)" && go vet ./...
	cd apps/field-android && env JAVA_HOME="$${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}" ./gradlew lintDebug
	cd apps/command && pnpm typecheck
	cd services/headquarters-archive && pnpm typecheck

format:
	cd packages/proto && buf format -w
	cd services/node && gofmt -w $$(rg --files -g '*.go')

seed:
	node scripts/observer-local.mjs seed

reset:
	scripts/reset-demo-local.sh

demo:
	scripts/run-demo-local.sh
