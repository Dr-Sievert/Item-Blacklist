#!/usr/bin/env sh
# Gradle, with JAVA_HOME resolved for you.
#
# ./gradlew needs a JVM on PATH or JAVA_HOME before Gradle's toolchain support
# can do anything. This wrapper points it at the JDKs paper-scaffold installed,
# so the project builds on a machine with no system-wide Java.
#
# Use ./gradlew directly instead if Gradle finds every JDK the build asks for by
# itself: Java 25 or newer to run Gradle on (CLAUDE.md, "Toolchain"), as
# JAVA_HOME or on PATH, and each line's Java (java_version in gradle.properties)
# for its modules.
set -e
cd "$(dirname "$0")"

# The build JDK's version, read from gradle.properties:
# the last buildJdk line, blanks dropped.
build_jdk="$(sed -n 's/^[[:space:]]*buildJdk[[:space:]]*=//p' gradle.properties | tail -n 1 | tr -d '[:space:]')"
if [ -z "$build_jdk" ]; then
    echo "gradle.properties has no buildJdk line." >&2
    exit 1
fi

# The paper-scaffold home: PAPER_SCAFFOLD_HOME when set, the same default
# otherwise as the generator's, which on Windows is in the user profile whatever
# Git Bash's HOME is. There it is spelt C:/..., as Java reads the list below.
case "$(uname -s)" in
    MINGW* | MSYS* | CYGWIN*)
        scaffold_home="$(cygpath -m "${PAPER_SCAFFOLD_HOME:-${USERPROFILE}/.paper-scaffold}")"
        ;;
    *) scaffold_home="${PAPER_SCAFFOLD_HOME:-${HOME}/.paper-scaffold}" ;;
esac

# The home of a JDK paper-scaffold installed, from the marker in its folder, or
# nothing. The marker holds the home relative to the folder; markers from
# earlier versions hold an absolute path.
jdk_home() {
    if [ -f "$1/.complete" ]; then
        home="$(cat "$1/.complete")"
        case "$home" in
            /* | [A-Za-z]:*) ;;
            *) home="$1/$home" ;;
        esac
        if [ -x "${home}/bin/java" ]; then
            printf '%s\n' "$home"
        fi
    fi
}

cached="$(jdk_home "${scaffold_home}/jdks/temurin-${build_jdk}")"

# Every JDK paper-scaffold installed, for Gradle's toolchains, since Gradle looks
# in the paper-scaffold home only when told to: the build JDK, which
# gradle/gradle-daemon-jvm.properties asks for when another release runs the
# launcher, and each line's Java, which its modules ask for.
installations=""
for folder in "${scaffold_home}"/jdks/temurin-*; do
    home="$(jdk_home "$folder")"
    if [ -n "$home" ]; then
        installations="${installations:+${installations},}${home}"
    fi
done

if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    # Its major version: 11 from "11.0.2", 8 from "1.8.0_392".
    release="$(sed -n 's/^JAVA_VERSION="\(1\.\)\{0,1\}\([0-9]*\).*/\2/p' "${JAVA_HOME}/release" 2>/dev/null || true)"
    if [ -n "$release" ] && [ "$release" -lt 25 ]; then
        # Gradle itself runs on Java 25 or newer here (CLAUDE.md, "Toolchain"), so
        # the cached JDK replaces a JAVA_HOME older than that.
        if [ -z "$cached" ]; then
            echo "JAVA_HOME is Java ${release}; this build needs 25 or newer to run Gradle." >&2
            echo "Point JAVA_HOME at a JDK ${build_jdk}, from" >&2
            echo "  https://adoptium.net/temurin/releases/?version=${build_jdk}" >&2
            exit 1
        fi
        JAVA_HOME="$cached"
        export JAVA_HOME
    fi
elif [ -n "$cached" ]; then
    JAVA_HOME="$cached"
    export JAVA_HOME
elif ! command -v java >/dev/null 2>&1; then
    echo "No JDK ${build_jdk} found. Install one from" >&2
    echo "  https://adoptium.net/temurin/releases/?version=${build_jdk}" >&2
    echo "and point JAVA_HOME at it." >&2
    exit 1
else
    # A JAVA_HOME with no java in it makes gradlew refuse to start, even with
    # java on PATH.
    unset JAVA_HOME
fi

if [ -n "$installations" ]; then
    set -- "-Dorg.gradle.java.installations.paths=$installations" "$@"
fi

# Through sh, so a gradlew that lost its execute bit still runs.
exec sh ./gradlew "$@"
