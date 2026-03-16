# Vendel Android - Deployment

## Distribución

La app se distribuye por dos canales:

- **GitHub Releases** — APK firmado, publicado automáticamente con GitHub Actions + goreleaser
- **F-Droid** — repositorio abierto, build reproducible (futuro)

## Prerrequisitos

- Android Studio o JDK 17+
- Keystore de firma (release)
- Cuenta de GitHub con permisos de push en `JimScope/vendel-android`

## Firma del APK

### 1. Crear keystore (solo la primera vez)

```bash
keytool -genkey -v -keystore vendel-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias vendel
```

Te pedirá contraseñas y datos del certificado. Guarda el archivo `vendel-release.jks` en un lugar seguro fuera del repo.

### 2. Configurar credenciales locales

Crea `keystore.properties` en la raíz del proyecto (ya está en `.gitignore`):

```properties
storeFile=../vendel-release.jks
storePassword=TU_STORE_PASSWORD
keyAlias=vendel
keyPassword=TU_KEY_PASSWORD
```

## Build local

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

- `versionName` en `app/build.gradle.kts` — ej. `"0.1.0"`
- `versionCode` — entero incremental, requerido por Android
- Tags de Git — formato `v0.1.0`, deben coincidir con `versionName`

## GitHub Releases (CI/CD)

El release se automatiza con GitHub Actions + goreleaser. Al pushear un tag `v*`, el workflow:

1. Compila el APK release firmado
2. Crea un GitHub Release con changelog automático
3. Sube el APK como asset (`vendel-v0.1.0.apk`)

### Configuración paso a paso

#### Paso 1: Codificar el keystore en base64

Desde la terminal, en el directorio donde tienes tu `vendel-release.jks`:

```bash
base64 -i vendel-release.jks | pbcopy
```

Esto copia el contenido codificado al portapapeles. En Linux usa `base64 vendel-release.jks | xclip -selection clipboard`.

#### Paso 2: Crear los secrets en GitHub

1. Ve a tu repositorio en GitHub: `github.com/JimScope/vendel-android`
2. Click en **Settings** (pestaña superior)
3. En el menú lateral, bajo **Security**, click en **Secrets and variables** → **Actions**
4. Click en **New repository secret** y crea estos 4 secrets uno por uno:

| Secret name          | Valor                                                        |
|----------------------|--------------------------------------------------------------|
| `KEYSTORE_BASE64`    | El contenido base64 del keystore (lo copiaste en el paso 1)  |
| `KEYSTORE_PASSWORD`  | La contraseña del keystore (storePassword)                   |
| `KEY_ALIAS`          | El alias de la key, ej. `vendel`                             |
| `KEY_PASSWORD`       | La contraseña de la key (keyPassword)                        |

> `GITHUB_TOKEN` no necesita configuración — GitHub lo provee automáticamente a los workflows.

#### Paso 3: Verificar que todo compila localmente

```bash
./gradlew assembleRelease
```

Si compila sin errores, estás listo.

#### Paso 4: Crear el tag y publicar

```bash
# 1. Asegúrate de que versionName en build.gradle.kts coincide
# 2. Commit cualquier cambio pendiente
# 3. Crea el tag
git tag v0.1.0

# 4. Push del código y el tag
git push origin main --tags
```

#### Paso 5: Verificar el release

1. Ve a **Actions** en tu repositorio de GitHub
2. Verás el workflow "Release" ejecutándose
3. Cuando termine (~2-3 min), ve a **Releases** en la página principal del repo
4. Debería aparecer **Vendel v0.1.0** con el APK descargable

### Qué hacer si falla el workflow

- **"KEYSTORE_BASE64 is not set"** → Verifica que el secret se creó correctamente en el paso 2
- **"Could not resolve signing config"** → El keystore decodificado está corrupto. Repite el paso 1 asegurándote de no incluir saltos de línea extra
- **Build falla** → Revisa que el build pasa localmente primero con `./gradlew assembleRelease`
- **goreleaser falla** → Verifica que el tag tiene formato `v*` y que no existe ya un release con ese tag

### Releases posteriores

Para cada nueva versión:

1. Actualiza `versionCode` (incrementar) y `versionName` en `app/build.gradle.kts`
2. Commit: `git commit -am "Bump version to 0.2.0"`
3. Tag: `git tag v0.2.0`
4. Push: `git push origin main --tags`

El workflow se encarga del resto.

## Archivos de configuración

### `.goreleaser.yml`

```yaml
project_name: vendel-android

before:
  hooks:
    - ./gradlew clean assembleRelease  # goreleaser rebuild (redundante en CI pero útil local)

builds:
  - skip: true  # No es un proyecto Go, solo distribuye el APK

release:
  github:
    owner: JimScope
    name: vendel-android
  draft: false
  prerelease: auto  # Tags con -rc, -beta, etc. se marcan como prerelease
  name_template: "Vendel v{{ .Version }}"

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

### `.github/workflows/release.yml`

```yaml
name: Release

on:
  push:
    tags:
      - "v*"

permissions:
  contents: write  # Necesario para crear releases

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0  # goreleaser necesita historial completo para el changelog

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Decode keystore
        run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > vendel-release.jks

      - name: Build release APK
        env:
          KEYSTORE_FILE: ${{ github.workspace }}/vendel-release.jks
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew assembleRelease

      - name: Release with goreleaser
        uses: goreleaser/goreleaser-action@v6
        with:
          version: latest
          args: release
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

## Actualización in-app

La app incluye un verificador de actualizaciones que consulta la GitHub Releases API al abrir la pantalla de Ajustes. Si hay una versión nueva:

- Muestra un banner con la versión disponible
- Enlace directo a la página de release
- El usuario puede descartar el aviso (se guarda por versión)

El verificador compara `BuildConfig.VERSION_NAME` con el `tag_name` del último release en `JimScope/vendel-android`. No usa polling — solo consulta cuando el usuario abre Ajustes.

## Checklist de release

1. [ ] Actualizar `versionCode` y `versionName` en `app/build.gradle.kts`
2. [ ] Verificar que compila: `./gradlew assembleRelease`
3. [ ] Commit y push a main
4. [ ] Crear tag: `git tag vX.Y.Z`
5. [ ] Push tag: `git push origin vX.Y.Z`
6. [ ] Verificar en GitHub Actions que el workflow completa
7. [ ] Verificar en Releases que el APK está disponible
