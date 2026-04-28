#!/usr/bin/env bash
set -euo pipefail

# --- begin runfiles.bash initialization v3 ---
# Copy-pasted from the Bazel Bash runfiles library v3.
set -uo pipefail; set +e; f=bazel_tools/tools/bash/runfiles/runfiles.bash
# shellcheck disable=SC1090
source "${RUNFILES_DIR:-/dev/null}/$f" 2>/dev/null || \
  source "$(grep -sm1 "^$f " "${RUNFILES_MANIFEST_FILE:-/dev/null}" | cut -f2- -d' ')" 2>/dev/null || \
  source "$0.runfiles/$f" 2>/dev/null || \
  source "$(grep -sm1 "^$f " "$0.runfiles_manifest" | cut -f2- -d' ')" 2>/dev/null || \
  source "$(grep -sm1 "^$f " "$0.exe.runfiles_manifest" | cut -f2- -d' ')" 2>/dev/null || \
  { echo>&2 "ERROR: cannot find $f"; exit 1; }; f=; set -e
# --- end runfiles.bash initialization v3 ---

if [[ $# -ne 2 ]]; then
  echo "Usage: owlet_tv_bundle <keystore> <properties_file>" >&2
  exit 1
fi

keystore="$1"
props_file="$2"

store_password=$(grep '^storePassword=' "$props_file" | cut -d= -f2-)
key_password=$(grep '^keyPassword=' "$props_file" | cut -d= -f2-)
key_alias=$(grep '^keyAlias=' "$props_file" | cut -d= -f2-)

unsigned_aab="${SRC}"
signed_output="$(dirname "$(readlink -f "$unsigned_aab")")/$(basename $0).aab"

cp -f "$unsigned_aab" "$signed_output"

jarsigner="../${JAVABASE#external/}/bin/jarsigner"
args=(
  -keystore "$keystore"
  -storepass "$store_password"
  -sigalg SHA256withRSA
  -digestalg SHA-256
)
if [[ -n "$key_password" ]]; then
  args+=(-keypass "$key_password")
fi

"$jarsigner" "${args[@]}" "$signed_output" "$key_alias"
echo "Signed AAB: $signed_output"
