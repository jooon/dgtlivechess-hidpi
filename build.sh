#!/usr/bin/env bash
set -euo pipefail

# This script prepares the local support files that ./dgtlivechess needs.
#
# Build-time inputs:
# - JDK 11+ (`javac`)
# - `python3`
# - internet access
#
# Outputs under ./build/:
# - downloaded OpenJFX runtime jars
# - downloaded JAXB jars
# - extracted application.jar for compilation
# - compiled compatibility patch classes
#
# Runtime is then offline: ./dgtlivechess only uses files already present in
# the repo's build directory plus the installed DGT LiveChess package.

ROOT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
APP_PACKAGE_JAR=${APP_PACKAGE_JAR:-/opt/DGTLiveChess/app/package.jar}
BUILD_DIR=${BUILD_DIR:-"$ROOT_DIR/build"}
OPENJFX_DIR="$BUILD_DIR/openjfx-runtime"
JAXB_DIR="$BUILD_DIR/jaxb-runtime"
CLASSES_DIR="$BUILD_DIR/classes"
APPLICATION_JAR="$BUILD_DIR/application.jar"
JAVAC=${JAVAC:-javac}

MAVEN_BASE_URL=${MAVEN_BASE_URL:-https://repo1.maven.org/maven2}
JAVAFX_VERSION=${JAVAFX_VERSION:-11.0.2}
JAVAFX_PLATFORM=${JAVAFX_PLATFORM:-linux}

TMP_DIR=$(mktemp -d)
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

if [[ ! -f "$APP_PACKAGE_JAR" ]]; then
  echo "package jar not found: $APP_PACKAGE_JAR" >&2
  exit 1
fi

if ! command -v "$JAVAC" >/dev/null 2>&1; then
  echo "javac not found: $JAVAC" >&2
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 not found" >&2
  exit 1
fi

mkdir -p "$BUILD_DIR" "$OPENJFX_DIR" "$JAXB_DIR"

download() {
  local url=$1
  local out=$2

  if [[ -f "$out" ]]; then
    return
  fi

  python3 - "$url" "$out" <<'PY'
import pathlib
import shutil
import sys
import tempfile
import urllib.request

url = sys.argv[1]
out = pathlib.Path(sys.argv[2])
out.parent.mkdir(parents=True, exist_ok=True)

with urllib.request.urlopen(url, timeout=120) as response:
    with tempfile.NamedTemporaryFile(dir=out.parent, delete=False) as tmp:
        shutil.copyfileobj(response, tmp)
        tmp_path = pathlib.Path(tmp.name)

tmp_path.replace(out)
print(out)
PY
}

extract_application_jar() {
  python3 - "$APP_PACKAGE_JAR" "$APPLICATION_JAR" <<'PY'
import pathlib
import sys
import tempfile
import zipfile

package_jar = pathlib.Path(sys.argv[1])
application_jar = pathlib.Path(sys.argv[2])
application_jar.parent.mkdir(parents=True, exist_ok=True)

with zipfile.ZipFile(package_jar) as zf:
    data = zf.read("application.jar")

with tempfile.NamedTemporaryFile(dir=application_jar.parent, delete=False) as tmp:
    tmp.write(data)
    tmp_path = pathlib.Path(tmp.name)

tmp_path.replace(application_jar)
print(application_jar)
PY
}

echo "downloading OpenJFX runtime jars into $OPENJFX_DIR"
OPENJFX_MODULES=(base controls fxml graphics media swing web)
for module in "${OPENJFX_MODULES[@]}"; do
  runtime_name="javafx-${module}-${JAVAFX_VERSION}-${JAVAFX_PLATFORM}.jar"
  download \
    "$MAVEN_BASE_URL/org/openjfx/javafx-${module}/${JAVAFX_VERSION}/${runtime_name}" \
    "$OPENJFX_DIR/$runtime_name" >/dev/null
done

echo "downloading JAXB jars into $JAXB_DIR"
download \
  "$MAVEN_BASE_URL/javax/xml/bind/jaxb-api/2.3.1/jaxb-api-2.3.1.jar" \
  "$JAXB_DIR/jaxb-api-2.3.1.jar" >/dev/null
download \
  "$MAVEN_BASE_URL/org/glassfish/jaxb/jaxb-runtime/2.3.9/jaxb-runtime-2.3.9.jar" \
  "$JAXB_DIR/jaxb-runtime-2.3.9.jar" >/dev/null
download \
  "$MAVEN_BASE_URL/org/glassfish/jaxb/txw2/2.3.9/txw2-2.3.9.jar" \
  "$JAXB_DIR/txw2-2.3.9.jar" >/dev/null
download \
  "$MAVEN_BASE_URL/com/sun/istack/istack-commons-runtime/3.0.12/istack-commons-runtime-3.0.12.jar" \
  "$JAXB_DIR/istack-commons-runtime-3.0.12.jar" >/dev/null
download \
  "$MAVEN_BASE_URL/javax/activation/javax.activation-api/1.2.0/javax.activation-api-1.2.0.jar" \
  "$JAXB_DIR/javax.activation-api-1.2.0.jar" >/dev/null

echo "extracting application.jar from $APP_PACKAGE_JAR"
extract_application_jar >/dev/null

echo "compiling compatibility classes into $CLASSES_DIR"
mkdir -p "$TMP_DIR/classes"
"$JAVAC" \
  --release 11 \
  --module-path "$OPENJFX_DIR" \
  --add-modules javafx.controls,javafx.fxml,javafx.swing,javafx.base,javafx.graphics,javafx.media,javafx.web \
  --add-exports javafx.controls/com.sun.javafx.scene.control.behavior=ALL-UNNAMED \
  --add-exports javafx.controls/com.sun.javafx.scene.control.inputmap=ALL-UNNAMED \
  -cp "$APPLICATION_JAR" \
  -d "$TMP_DIR/classes" \
  "$ROOT_DIR/compat-src/com/sun/javafx/scene/control/skin/BehaviorSkinBase.java" \
  "$ROOT_DIR/compat-src/com/sun/javafx/scene/control/skin/CellSkinBase.java" \
  "$ROOT_DIR/compat-src/com/novotea/chess/javafx/ui/ItemViewBehaviour.java" \
  "$ROOT_DIR/compat-src/com/novotea/chess/javafx/ui/ItemCellBehaviour.java" \
  "$ROOT_DIR/compat-src/com/novotea/entity/ProxyUtil.java" \
  "$ROOT_DIR/compat-src/com/novotea/entity/ProxyEntityAccess.java" \
  "$ROOT_DIR/compat-src/com/novotea/ui/core/AbstractColumnUI.java" \
  "$ROOT_DIR/compat-src/com/novotea/livechess/operations/tournament/StartRecording.java" \
  "$ROOT_DIR/compat-src/com/novotea/livechess/service/eboard/DefaultEBoardService.java"

rm -rf "$CLASSES_DIR"
mv "$TMP_DIR/classes" "$CLASSES_DIR"

echo "build artifacts are ready under $BUILD_DIR"
