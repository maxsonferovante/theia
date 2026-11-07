#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

if ! command -v java >/dev/null 2>&1; then
    echo "Java não encontrado. Instale um JDK compatível e configure JAVA_HOME." >&2
    exit 1
fi

# O Gradle também aceita sdk.dir em local.properties.
if [[ ! -f local.properties && -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
    if [[ -d "$HOME/Library/Android/sdk" ]]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    elif [[ -d "$HOME/Android/Sdk" ]]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    else
        echo "Android SDK não encontrado. Configure ANDROID_HOME ou sdk.dir em local.properties." >&2
        exit 1
    fi
fi

./gradlew :androidApp:assembleDebug

apk="$project_dir/androidApp/build/outputs/apk/debug/androidApp-debug.apk"
if [[ ! -f "$apk" ]]; then
    echo "A compilação terminou, mas o APK não foi encontrado: $apk" >&2
    exit 1
fi
echo "APK de depuração gerado: $apk"
