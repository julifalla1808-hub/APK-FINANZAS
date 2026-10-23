#!/usr/bin/env bash
# Compila la APK sin Gradle, usando las herramientas de Android de Ubuntu:
#   sudo apt-get install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
OUT=build
KEYSTORE=${KEYSTORE:-keystore/finanzas.jks}
rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "1/6 Pruebas del lector de notificaciones"
mkdir -p "$OUT/test"
javac -nowarn -d "$OUT/test" app/src/com/julifalla/finanzas/NotificationParser.java app/test/ParserTest.java
java -cp "$OUT/test" ParserTest > "$OUT/test/result.txt" || { cat "$OUT/test/result.txt"; exit 1; }
tail -1 "$OUT/test/result.txt"

echo "2/6 Recursos (aapt)"
aapt package -f -m -J "$OUT/gen" -M app/AndroidManifest.xml -S app/res -I "$ANDROID_JAR" \
    --min-sdk-version 24 --target-sdk-version 34 -F "$OUT/app.unaligned.apk"

echo "3/6 Java (javac)"
javac -nowarn -source 8 -target 8 -encoding UTF-8 -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
    -d "$OUT/classes" $(find app/src "$OUT/gen" -name '*.java')

echo "4/6 Dex (dx)"
dalvik-exchange --dex --min-sdk-version=24 --output="$OUT/dex/classes.dex" "$OUT/classes"
(cd "$OUT/dex" && aapt add ../app.unaligned.apk classes.dex > /dev/null)

echo "5/6 Alineado (zipalign)"
zipalign -f -p 4 "$OUT/app.unaligned.apk" "$OUT/app.aligned.apk"

echo "6/6 Firma (apksigner)"
if [ ! -f "$KEYSTORE" ]; then
    mkdir -p "$(dirname "$KEYSTORE")"
    keytool -genkeypair -keystore "$KEYSTORE" -storepass finanzas -keypass finanzas -alias finanzas \
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Mis Finanzas, O=Personal, C=CO" > /dev/null 2>&1
fi
apksigner sign --ks "$KEYSTORE" --ks-pass pass:finanzas --key-pass pass:finanzas --ks-key-alias finanzas \
    --out MisFinanzas.apk "$OUT/app.aligned.apk"
apksigner verify MisFinanzas.apk
echo "Listo: $(pwd)/MisFinanzas.apk"
