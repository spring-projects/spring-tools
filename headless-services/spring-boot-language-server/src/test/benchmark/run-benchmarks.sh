#!/bin/bash
# Runs the benchmarks of the Kotlin parsers, see README.md.
#
#   run-benchmarks.sh [quick|full]
#
# quick (the default) leaves out the corpus of 5000 files.
set -e

MODE="${1:-quick}"
MODULE="$(cd "$(dirname "$0")/../../.." && pwd)"
BUILD="$MODULE/target/benchmark"
CORPORA="$BUILD/corpora"
HEAP="${BENCHMARK_HEAP:-2g}"

cd "$MODULE"
mkdir -p "$BUILD/classes"

echo "== building"
../mvnw -q compile
../mvnw -q dependency:build-classpath -Dmdep.outputFile="$BUILD/classpath.txt"
CP="$(cat "$BUILD/classpath.txt"):$MODULE/target/classes"
javac -nowarn -cp "$CP" -d "$BUILD/classes" src/test/benchmark/Bench.java src/test/benchmark/BenchPsiReader.java 2>&1 | grep -v "^Note:" || true

if [ ! -d "$CORPORA/bodieshuge" ] || [ "$2" = "regenerate" ]; then
	echo "== generating the corpora"
	python3 src/test/benchmark/generate_corpora.py "$CORPORA" "$MODULE/src/test/resources/test-projects"
fi

java_run() { java -Xmx"$HEAP" -cp "$CP:$BUILD/classes" "$@" 2>&1 | grep -E "^(RESULT|MEMORY|VERIFY|DIFFERENT)|Exception" || true; }

echo "== checks: every parser reads the same, skipping bodies changes nothing"
for corpus in small medium bodies hugefile bodieshuge $( [ "$MODE" = full ] && echo large ); do
	java_run Bench verify "$CORPORA/$corpus"
done
java_run Bench verify-skip "$CORPORA/small" "$CORPORA/medium" "$CORPORA/bodies" "$CORPORA/hugefile" "$CORPORA/bodieshuge"

echo "== benchmarks (one JVM per parser and corpus, $HEAP heap)"
# corpus passes threads
CASES="small:40:4 medium:8:8 bodies:4:8 hugefile:40:1 bodieshuge:30:1"
if [ "$MODE" = full ]; then
	CASES="$CASES large:3:8"
fi
for case in $CASES; do
	IFS=: read -r corpus passes threads <<< "$case"
	echo "## $corpus"
	for parser in psi tree-sitter tree-sitter-bodies syntax-api syntax-api-bodies; do
		java_run Bench run "$parser" "$CORPORA/$corpus" "$passes" "$threads"
	done
	for mode in lazy full; do
		java_run BenchPsiReader "$mode" "$CORPORA/$corpus" "$passes"
	done
done
