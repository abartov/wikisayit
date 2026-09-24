#!/usr/bin/env bash
#
# Build debug and release APKs. The release APK is signed with a keystore at
# ./keystore/release.jks; if that keystore (and keystore.properties) don't
# exist yet, this script generates them on first run.
#
# A release build first bumps VERSION and VERSION_CODE (patch level by default),
# and once the build succeeds commits them as "vX.Y.Z" and tags that commit
# vX.Y.Z (F-Droid builds from these tags). Nothing is pushed. If the build
# fails, both files are restored. Write the F-Droid changelog for the new
# versionCode beforehand (fastlane/metadata/android/en-US/changelogs/<code>.txt)
# and it's included in the release commit.
#
# Usage:
#   ./build_and_sign.sh                 # bump patch, build debug + release
#   ./build_and_sign.sh --debug-only    # build debug only (no bump)
#   ./build_and_sign.sh --release-only  # bump patch, build release only (generates keystore if needed)
#   ./build_and_sign.sh --bump=minor    # bump minor (or major) instead of patch
#   ./build_and_sign.sh --no-bump       # build release without bumping, committing, or tagging
#   ./build_and_sign.sh --init-keystore # (re)generate the release keystore, then exit
#
# Env overrides for keystore generation:
#   KEY_ALIAS                 (default: wikisayit)
#   KEYSTORE_DN               (default: "CN=WikiSayIt, OU=Development, O=WikiSayIt, L=Unknown, ST=Unknown, C=US")
#   KEYSTORE_VALIDITY_DAYS    (default: 10000, ~27 years)
#   KEYSTORE_STORE_PASSWORD   (default: randomly generated)
#   KEYSTORE_KEY_PASSWORD     (default: randomly generated)

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

KEYSTORE_DIR="keystore"
KEYSTORE_FILE="${KEYSTORE_DIR}/release.jks"
KEYSTORE_PROPERTIES="keystore.properties"

BUILD_DEBUG=1
BUILD_RELEASE=1
INIT_KEYSTORE_ONLY=0
BUMP=patch

for arg in "$@"; do
    case "$arg" in
        --debug-only)
            BUILD_RELEASE=0
            ;;
        --release-only)
            BUILD_DEBUG=0
            ;;
        --init-keystore)
            INIT_KEYSTORE_ONLY=1
            ;;
        --bump=major|--bump=minor|--bump=patch)
            BUMP="${arg#--bump=}"
            ;;
        --no-bump)
            BUMP=""
            ;;
        -h|--help)
            grep -E '^#( |$)' "$0" | sed -E 's/^# ?//'
            exit 0
            ;;
        *)
            echo "Unknown argument: $arg" >&2
            exit 1
            ;;
    esac
done

generate_keystore() {
    if [[ -f "$KEYSTORE_FILE" && -f "$KEYSTORE_PROPERTIES" && "$INIT_KEYSTORE_ONLY" -eq 0 ]]; then
        return
    fi

    if ! command -v keytool >/dev/null 2>&1; then
        echo "keytool not found (should ship with the JDK). Install a JDK and retry." >&2
        exit 1
    fi

    echo "No release keystore found at ${KEYSTORE_FILE}; generating one now."
    mkdir -p "$KEYSTORE_DIR"

    local alias="${KEY_ALIAS:-wikisayit}"
    local dn="${KEYSTORE_DN:-CN=WikiSayIt, OU=Development, O=WikiSayIt, L=Unknown, ST=Unknown, C=US}"
    local validity="${KEYSTORE_VALIDITY_DAYS:-10000}"
    local store_password="${KEYSTORE_STORE_PASSWORD:-$(openssl rand -base64 24)}"
    # PKCS12 keystores (the modern keytool default) require the key password to
    # match the store password; keytool silently ignores a different -keypass.
    local key_password="${KEYSTORE_KEY_PASSWORD:-$store_password}"

    if [[ -f "$KEYSTORE_FILE" ]]; then
        rm -f "$KEYSTORE_FILE"
    fi

    keytool -genkeypair -v \
        -keystore "$KEYSTORE_FILE" \
        -storetype PKCS12 \
        -alias "$alias" \
        -keyalg RSA -keysize 2048 \
        -validity "$validity" \
        -storepass "$store_password" \
        -keypass "$key_password" \
        -dname "$dn"

    cat > "$KEYSTORE_PROPERTIES" <<EOF
storeFile=${KEYSTORE_FILE}
storePassword=${store_password}
keyAlias=${alias}
keyPassword=${key_password}
EOF
    chmod 600 "$KEYSTORE_PROPERTIES" "$KEYSTORE_FILE"

    echo
    echo "Generated ${KEYSTORE_FILE} and ${KEYSTORE_PROPERTIES}."
    echo "Both are gitignored. BACK THEM UP SOMEWHERE SAFE (e.g. a password manager):"
    echo "losing the keystore means you can never publish an update under this app's"
    echo "signature again. Passwords generated for this keystore:"
    echo "  storePassword: ${store_password}"
    echo "  keyPassword:   ${key_password}"
    echo
}

# Must match the formula app/build.gradle.kts enforces.
version_code_for() {
    local major minor patch
    IFS=. read -r major minor patch <<< "$1"
    echo $((2000000 + major * 10000 + minor * 100 + patch))
}

bump_version() {
    local old_version major minor patch
    old_version="$(tr -d '[:space:]' < VERSION)"
    IFS=. read -r major minor patch <<< "$old_version"
    case "$BUMP" in
        major) major=$((major + 1)); minor=0; patch=0 ;;
        minor) minor=$((minor + 1)); patch=0 ;;
        patch) patch=$((patch + 1)) ;;
    esac
    NEW_VERSION="${major}.${minor}.${patch}"
    NEW_VERSION_CODE="$(version_code_for "$NEW_VERSION")"

    if git rev-parse -q --verify "refs/tags/v${NEW_VERSION}" >/dev/null; then
        echo "Tag v${NEW_VERSION} already exists; refusing to bump." >&2
        exit 1
    fi

    OLD_VERSION_FILE="$(cat VERSION)"
    OLD_VERSION_CODE_FILE="$(cat VERSION_CODE)"
    echo "$NEW_VERSION" > VERSION
    echo "$NEW_VERSION_CODE" > VERSION_CODE
    trap restore_version EXIT
    echo "==> Bumped version ${old_version} -> ${NEW_VERSION} (versionCode ${NEW_VERSION_CODE})"

    # F-Droid reads the changelog from the tagged commit, so it has to exist before tagging.
    CHANGELOG="fastlane/metadata/android/en-US/changelogs/${NEW_VERSION_CODE}.txt"
    if [[ ! -f "$CHANGELOG" ]]; then
        echo "Note: no F-Droid changelog at ${CHANGELOG}; v${NEW_VERSION} will be tagged without one."
    fi
}

restore_version() {
    [[ "$?" -eq 0 ]] && return
    echo "$OLD_VERSION_FILE" > VERSION
    echo "$OLD_VERSION_CODE_FILE" > VERSION_CODE
    echo "Build failed; restored VERSION and VERSION_CODE." >&2
}

commit_and_tag_version() {
    trap - EXIT
    local files=(VERSION VERSION_CODE)
    if [[ -f "$CHANGELOG" ]]; then
        git add -- "$CHANGELOG"
        files+=("$CHANGELOG")
    fi
    git commit -q -m "v${NEW_VERSION}" -- "${files[@]}"
    git tag "v${NEW_VERSION}"
    echo "==> Committed and tagged v${NEW_VERSION} (not pushed)"
}

if [[ "$INIT_KEYSTORE_ONLY" -eq 1 ]]; then
    generate_keystore
    exit 0
fi

if [[ "$BUILD_RELEASE" -eq 0 ]]; then
    BUMP=""
fi

if [[ -n "$BUMP" ]]; then
    bump_version
fi

if [[ "$BUILD_DEBUG" -eq 1 ]]; then
    echo "==> Building debug APK"
    ./gradlew assembleDebug
fi

if [[ "$BUILD_RELEASE" -eq 1 ]]; then
    generate_keystore
    echo "==> Building signed release APK"
    ./gradlew assembleRelease
fi

if [[ -n "$BUMP" ]]; then
    commit_and_tag_version
fi

echo
echo "Done. Build outputs:"
if [[ "$BUILD_DEBUG" -eq 1 ]]; then
    find app/build/outputs/apk/debug -name '*.apk' 2>/dev/null
fi
if [[ "$BUILD_RELEASE" -eq 1 ]]; then
    find app/build/outputs/apk/release -name '*.apk' 2>/dev/null
fi
