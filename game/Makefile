.PHONY: dev test build clean help

help:
	@echo "make dev     — compile + watch + dev server on :8082"
	@echo "make test    — run unit tests"
	@echo "make build   — production JS build → public/"
	@echo "make clean   — remove build artifacts"

dev:
	sbt --batch dev

test:
	sbt --batch "core/test"

build:
	sbt --batch "js/fullLinkJS"
	@mkdir -p public
	@cp js/target/scala-3.3.3/towerdefense-js-opt/*.js js/target/scala-3.3.3/towerdefense-js-opt/*.js.map public/
	@cp index.html public/

clean:
	sbt --batch clean
