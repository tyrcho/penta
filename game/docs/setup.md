# Environment setup — sbt/coursier in a network-restricted sandbox

`sbt` isn't preinstalled in some sandboxed/remote execution environments (e.g. Claude
Code's own cloud sessions), and the usual install paths are blocked there:
`repo.scala-sbt.org` (the apt repo) and plain `github.com` download/release URLs both
get a 403 from the outbound network policy. `repo1.maven.org` (Maven Central) and
`raw.githubusercontent.com` are reachable, so use those instead.

## Fastest path: sbt-launch.jar straight from Maven Central

No install step, no coursier needed — the bare launcher jar is itself a published
artifact:

```bash
curl -fsSL -o /tmp/sbt-launch.jar \
  "https://repo1.maven.org/maven2/org/scala-sbt/sbt-launch/1.10.1/sbt-launch-1.10.1.jar"

mkdir -p /tmp/bin
cat > /tmp/bin/sbt <<'EOF'
#!/bin/bash
exec java -jar /tmp/sbt-launch.jar "$@"
EOF
chmod +x /tmp/bin/sbt
export PATH="/tmp/bin:$PATH"
```

Then run tasks exactly as in the `Makefile` (`make test` → `sbt "coreJVM/test" "sim/test"`),
**but drop `--batch`**: that flag belongs to the `sbt` shell-script wrapper's arg parsing,
not the bare launcher jar, which mis-parses it as a command (`Not a valid command: --`).
Pass task names as plain trailing arguments instead:

```bash
sbt "coreJVM/test"
sbt "coreJVM/test" "sim/test"
```

This setup is session-scoped (lives under `/tmp`) and needs to be redone in a fresh
sandbox — it isn't meant to replace a real local sbt install on a normal dev machine.

## Coursier (`cs`) — works for fetching, not for `cs install sbt`

The coursier native launcher can be fetched the same way:

```bash
curl -fsSL -o /tmp/cs.gz \
  "https://raw.githubusercontent.com/coursier/launchers/master/cs-x86_64-pc-linux.gz"
gunzip -f /tmp/cs.gz && chmod +x /tmp/cs
```

`cs` itself works (e.g. `cs java`), but `cs install sbt` fails with a TLS handshake
error against `repo1.maven.org`: the native GraalVM binary doesn't pick up the
sandbox's injected trust store the way a plain `java` process does via
`JAVA_TOOL_OPTIONS` (see `/root/.ccr/README.md` for how that trust store is wired up
for JVM tools). The sbt-launch.jar route above sidesteps this entirely since it's
just `java -jar`, so prefer that over `cs install sbt` in this kind of environment.
