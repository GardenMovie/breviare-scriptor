#!/usr/bin/env bash
# Pin Maven to JDK 21 for this project only, leaving the machine default alone.
#
# The project targets Java 21 (pom.xml <java.version>, CI, and the EC2 host all
# use it). Mockito's inline mock maker cannot instrument classes on JDK 26, so
# running the suite on a newer default JDK fails in LinkServiceOwnershipTest.
#
# Usage:  source .mvn/jdk21.sh    then run ./mvnw as normal
# Or:     .mvn/jdk21.sh test      to run a one-off goal

JDK21="/usr/lib/jvm/java-21-openjdk"

if [ ! -x "$JDK21/bin/java" ]; then
  echo "JDK 21 not found at $JDK21" >&2
  return 1 2>/dev/null || exit 1
fi

export JAVA_HOME="$JDK21"

# Executed rather than sourced: run mvnw with the remaining args.
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1
  exec ./mvnw "$@"
fi
