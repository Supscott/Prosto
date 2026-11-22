#!/usr/bin/env bash
# Rebuilds native/adblock-jni for the three Android ABIs into app/src/main/jniLibs/ and checks
# that every LOAD segment is aligned to at least 16 KB. Idempotent; fails loudly.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CRATE="$ROOT/native/adblock-jni"
OUT="$ROOT/app/src/main/jniLibs"
ABIS=(arm64-v8a armeabi-v7a x86_64)

export PATH="/opt/homebrew/opt/rustup/bin:$HOME/.cargo/bin:$PATH"

die() { echo "build_native: $*" >&2; exit 1; }

command -v cargo >/dev/null || die "cargo not found (install rustup)"
command -v cargo-ndk >/dev/null || die "cargo-ndk not found (cargo install cargo-ndk)"
installed="$(rustup target list --installed 2>/dev/null || true)"
for t in aarch64-linux-android armv7-linux-androideabi x86_64-linux-android; do
    grep -qx "$t" <<<"$installed" || die "missing Rust target $t (rustup target add $t)"
done

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
    SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
    ANDROID_NDK_HOME="$(ls -d "$SDK"/ndk/*/ 2>/dev/null | sort -V | tail -1 || true)"
    ANDROID_NDK_HOME="${ANDROID_NDK_HOME%/}"
fi
[[ -d "${ANDROID_NDK_HOME:-}" ]] || die "Android NDK not found (set ANDROID_NDK_HOME)"
export ANDROID_NDK_HOME
READELF="$(ls "$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/*/bin/llvm-readelf | head -1)"
[[ -x "$READELF" ]] || die "llvm-readelf not found in the NDK"

cd "$CRATE"
cargo test --locked

# --remap-path-prefix keeps the builder's home directory (cargo registry, checkout path) out of
# panic messages and debug info in the committed .so files.
export RUSTFLAGS="${RUSTFLAGS:-} -C link-arg=-Wl,-z,max-page-size=16384 --remap-path-prefix=$HOME=~"
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 --platform 26 -o "$OUT" build --release --locked

status=0
for abi in "${ABIS[@]}"; do
    so="$OUT/$abi/libadblock_jni.so"
    [[ -f "$so" ]] || die "missing $so"
    aligns="$("$READELF" -lW "$so" | awk '$1 == "LOAD" { print $NF }')"
    [[ -n "$aligns" ]] || die "no LOAD segments in $so"
    for a in $aligns; do
        if (( a < 0x4000 )); then
            echo "FAIL $abi: LOAD segment alignment $a < 0x4000" >&2
            status=1
        fi
    done
    printf '%-12s %8d bytes  LOAD align: %s\n' "$abi" "$(wc -c <"$so")" "$(echo $aligns | tr '\n' ' ')"
done
exit $status
