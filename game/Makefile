.PHONY: dev test test-js sim sim-tune docs build clean help

help:
	@echo "make dev      — compile + watch + dev server on :8082"
	@echo "make test     — run unit tests on the JVM (fast — for iterating on AI strategies)"
	@echo "make test-js  — run unit tests under Node.js (parity check for the browser build)"
	@echo "make sim      — headless AI-vs-AI battles: make sim ARGS='linear balanced 200'"
	@echo "                add --log <path> for a one-match transcript: ARGS='linear comb 1 --log /tmp/match.log'"
	@echo "make sim-tune — sweep CompositeStrategy weights: make sim-tune ARGS='linear 50 0.25'"
	@echo "make docs     — regenerate the vault's building/unit/resource/research pages (FR in place, EN under ../Resources-en/)"
	@echo "make build    — production JS build → public/"
	@echo "make clean    — remove build artifacts"

dev:
	sbt --batch dev

test:
	sbt --batch "coreJVM/test" "sim/test"

test-js:
	sbt --batch "coreJS/test"

sim:
	sbt --batch "sim/runMain towerdefense.sim.run $(ARGS)"

sim-tune:
	sbt --batch "sim/runMain towerdefense.sim.tune $(ARGS)"

docs:
	sbt --batch "sim/runMain towerdefense.docgen.generate"

build:
	sbt --batch "js/fullLinkJS"
	@mkdir -p public
	@cp js/target/scala-3.3.3/towerdefense-js-opt/*.js js/target/scala-3.3.3/towerdefense-js-opt/*.js.map public/
	@cp index.html public/
	@cp -r assets public/

clean:
	sbt --batch clean
