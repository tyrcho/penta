.PHONY: dev test test-js build clean help

help:
	@echo "make dev      — compile + watch + dev server on :8082"
	@echo "make test     — run unit tests on the JVM (fast — for iterating on AI strategies)"
	@echo "make test-js  — run unit tests under Node.js (parity check for the browser build)"
	@echo "make build    — production JS build → public/"
	@echo "make clean    — remove build artifacts"

dev:
	sbt --batch dev

test:
	sbt --batch "coreJVM/test"

test-js:
	sbt --batch "coreJS/test"

build:
	sbt --batch "js/fullLinkJS"
	@mkdir -p public
	@cp js/target/scala-3.3.3/towerdefense-js-opt/*.js js/target/scala-3.3.3/towerdefense-js-opt/*.js.map public/
	@cp index.html public/
	@cp -r assets public/

clean:
	sbt --batch clean
