# Vendel Android - Deployment

## Distribución

La app se distribuye por dos canales:

- **GitHub Releases** — APK firmado, publicado con goreleaser
- **F-Droid** — repositorio abierto, build reproducible

## Prerrequisitos

- Android Studio o JDK 11+
- Keystore de firma (release)
- [goreleaser](https://goreleaser.com/install/) instalado
- Cuenta de GitHub con permisos de push en `JimScope/vendel-android`

## Firma del APK

### Crear keystore (solo la primera vez)

```bash
keytool -genkey -v -keystore vendel-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias vendel
```

### Configurar credenciales

Crea `keystore.properties` en la raíz del proyecto (NO commitear):

```properties
storeFile=../vendel-release.jks
storePassword=TU_STORE_PASSWORD
keyAlias=vendel
keyPassword=TU_KEY_PASSWORD
```

O usa variables de entorno:

```bash
export KEYSTORE_FILE=path/to/vendel-release.jks
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=vendel
export KEY_PASSWORD=...
```

## Build manual

### Debug

```bash
./gradlew assembleDebug
```

APK en: `app/build/outputs/apk/debug/app-debug.apk`

### Release

```bash
./gradlew assembleRelease
```

APK en: `app/build/outputs/apk/release/app-release.apk`

## Versionado

El proyecto usa semver (`MAJOR.MINOR.PATCH`):

- `versionName` en `app/build.gradle.kts` — ej. `"1.2.0"`
- `versionCode` — entero incremental, requerido por Android
- Tags de Git — formato `v1.2.0`, deben coincidir con `versionName`

Antes de cada release:

1. Actualiza `versionCode` y `versionName` en `app/build.gradle.kts`
2. Crea el tag: `git tag v1.2.0`
3. Push: `git push origin v1.2.0`

## GitHub Releases con goreleaser

### Configuración

Crea `.goreleaser.yml` en la raíz:

```yaml
project_name: vendel-android

before:
  hooks:
    - ./gradlew clean assembleRelease

builds:
  - skip: true

release:
  github:
    owner: JimScope
    name: vendel-android
  draft: false
  prerelease: auto
  name_template: "Vendel Gateway v{{ .Version }}"

extra_files:
  - glob: app/build/outputs/apk/release/app-release.apk
    name_template: "vendel-v{{ .Version }}.apk"

changelog:
  sort: asc
  filters:
    exclude:
      - "^docs:"
      - "^ci:"
      - "^chore:"
```

### Publicar release

```bash
# Asegúrate de tener el tag creado y pusheado
export GITHUB_TOKEN=tu_token
goreleaser release
```

Esto:
1. Compila el APK release
2. Crea un GitHub Release con el tag
3. Sube el APK como asset (`vendel-v1.2.0.apk`)

### GitHub Actions (CI/CD)

Crea `.github/workflows/release.yml`:

```yaml
name: Release

on:
  push:
    tags:
      - "v*"

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - name: Decode keystore
        run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > vendel-release.jks

      - name: Build release APK
        env:
          KEYSTORE_FILE: ${{ github.workspace }}/vendel-release.jks
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew assembleRelease

      - uses: goreleaser/goreleaser-action@v6
        with:
          version: latest
          args: release
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

Secrets necesarios en el repo:
- `KEYSTORE_BASE64` — `base64 -i vendel-release.jks`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

## F-Droid

### Metadata

F-Droid requiere metadata en el repo. Crea `metadata/com.jimscope.vendel.yml`:

```yaml
Categories:
  - Connectivity
License: MIT
AuthorName: JimScope
AuthorWebSite: https://github.com/JimScope
SourceCode: https://github.com/JimScope/vendel-android
IssueTracker: https://github.com/JimScope/vendel-android/issues

AutoName: Vendel Gateway
Description: |
  SMS gateway that connects your Android device to the Vendel platform
  for sending and receiving SMS messages via API.

RepoType: git
Repo: https://github.com/JimScope/vendel-android.git

Builds:
  - versionName: 1.0.0
    versionCode: 1
    commit: v1.0.0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.0.0
CurrentVersionCode: 1
```

### Opciones de distribución en F-Droid

1. **Repositorio oficial** — Envía un merge request a [fdroiddata](https://gitlab.com/fdroid/fdroiddata) con tu metadata
2. **Repositorio propio** — Usa [fdroidserver](https://f-droid.org/docs/Setup_an_F-Droid_App_Repo/) para hostear tu propio repo

Para un repo propio:

```bash
# Instalar fdroidserver
pip install fdroidserver

# Inicializar repo
fdroid init

# Copiar APK firmado
cp app/build/outputs/apk/release/app-release.apk repo/

# Generar índice
fdroid update
```

Sube el directorio `repo/` a cualquier hosting estático (GitHub Pages, S3, etc.).

Los usuarios agregan tu repo en F-Droid: **Settings > Repositories > Add** con la URL.

## Actualización in-app

La app incluye un verificador de actualizaciones que consulta la GitHub Releases API al abrir la pantalla de Ajustes. Si hay una versión nueva:

- Muestra un banner con la versión disponible
- Enlace directo a la página de release
- El usuario puede descartar el aviso (se guarda por versión)

El verificador compara `BuildConfig.VERSION_NAME` con el `tag_name` del último release en `JimScope/vendel-android`. No usa polling — solo consulta cuando el usuario abre Ajustes.

## Checklist de release

1. [ ] Actualizar `versionCode` y `versionName` en `app/build.gradle.kts`
2. [ ] Verificar que compila: `./gradlew assembleRelease`
3. [ ] Crear commit: `git commit -am "Release v1.x.x"`
4. [ ] Crear tag: `git tag v1.x.x`
5. [ ] Push: `git push origin main --tags`
6. [ ] goreleaser publica el GitHub Release automáticamente (o manual con `goreleaser release`)
7. [ ] Actualizar metadata de F-Droid si aplica
