#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

for variable in THEIA_RELEASE_STORE_FILE THEIA_RELEASE_STORE_PASSWORD THEIA_RELEASE_KEY_ALIAS THEIA_RELEASE_KEY_PASSWORD; do
    if [[ -z "${!variable:-}" ]]; then
        echo "Configure a variável $variable antes de gerar o APK de release." >&2
        exit 1
    fi
done

if [[ ! -f "$THEIA_RELEASE_STORE_FILE" ]]; then
    echo "Arquivo de assinatura não encontrado: $THEIA_RELEASE_STORE_FILE" >&2
    exit 1
fi
# O Gradle resolve caminhos relativos ao módulo; use um caminho absoluto.
export THEIA_RELEASE_STORE_FILE="$(cd -- "$(dirname -- "$THEIA_RELEASE_STORE_FILE")" && pwd)/$(basename -- "$THEIA_RELEASE_STORE_FILE")"

if ! command -v java >/dev/null 2>&1; then
    echo "Java não encontrado. Instale um JDK compatível e configure JAVA_HOME." >&2
    exit 1
fi
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

./gradlew :androidApp:assembleRelease

apk="$project_dir/androidApp/build/outputs/apk/release/androidApp-release.apk"
if [[ ! -f "$apk" ]]; then
    echo "A compilação terminou, mas o APK assinado não foi encontrado: $apk" >&2
    exit 1
fi
echo "APK de release gerado: $apk"
