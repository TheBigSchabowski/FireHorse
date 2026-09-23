# Eröffnungs-Puzzle

Eröffnungen als Puzzle lernen, mit Spaced Repetition, für macOS und Android.

Du legst Varianten selbst an: Züge vom Start bis zur Stellung spielen, Eröffnung und Namen
eintragen („Sizilianisch“ / „Scheveningen“). Beim Üben steht nur der Name auf einer schwarzen
Tafel, und du spielst beide Seiten vom Start, bis die Stellung auf dem Brett steht. Jede
Zugfolge zählt, die in deinen gespeicherten Varianten vorkommt, also auch Zugumstellungen.

## Download

Unter [Releases](../../releases/latest):

| Plattform | Datei | Hinweis |
|---|---|---|
| macOS | `EroeffnungsPuzzle_<version>_macos-arm64.dmg` | Apple Silicon |
| Android | `EroeffnungsPuzzle_<version>_android.apk` | Android 8.0+ |

**macOS:** DMG öffnen und die App in „Programme“ ziehen. Die App ist nicht mit einem
Apple-Entwicklerzertifikat signiert, deshalb beim ersten Start: Rechtsklick auf die App →
**Öffnen** → **Öffnen** bestätigen.

**Android:** APK im Browser auf dem Handy herunterladen und öffnen, dann „Aus dieser Quelle
installieren“ erlauben.

## Bedienung

- **+ Neue Variante:** Züge spielen, Namen geben, speichern. „Weiterführen“ bei einer vorhandenen
  Variante übernimmt ihre Züge, dann hängst du nur noch an, was anders ist.
- **Lernen:** fällige und neue Varianten (Spaced Repetition, FSRS). **Frei üben:** alle gemischt,
  ohne Wertung. **Üben** an einer Variante: nur diese.
- Während eines Puzzles: **Zielstellung zeigen** als Hilfe, **↺ Nochmal von vorn**. Ein Zug aus
  einer anderen gespeicherten Variante gibt nur einen Hinweis, ein falscher Zug wird korrigiert
  und die Karte kommt bald wieder.
- Desktop: `Enter` / `Leertaste` = „Gut“ bzw. „Weiter“.

Gespeichert wird auf dem Mac in `~/Documents/EroeffnungsPuzzle`, auf Android im App-Speicher.

## Bauen

```sh
./gradlew :desktopApp:run            # Desktop-App aus dem Quellcode starten
./gradlew :shared:desktopTest        # Tests (Zuggenerator mit Perft, Puzzle-Logik)
./gradlew :desktopApp:renderScreens  # Bildschirme in Handy- und Desktopgröße nach desktopApp/build/screens
./tools/build-release.sh             # DMG + signiertes APK nach dist/releases
./tools/android-emulator.sh          # APK auf einem unsichtbaren Emulator starten, Screenshot nach build/emulator.png
```

Der Emulator (Pixel 6, Android 16) läuft ohne Fenster und wird über `adb` bedient, z. B.
`adb shell input tap X Y`. `./tools/android-emulator.sh shot name` macht ein weiteres
Bildschirmfoto, `./tools/android-emulator.sh stop` beendet ihn.

Die Figuren-PNGs und das Icon entstehen aus `tools/pieces-svg/` mit `tools/RenderPieces.java`
(Skia aus Compose Desktop). Ein Test prüft, dass keine Figur leer ist.

Voraussetzungen: Android SDK unter `~/Library/Android/sdk`, ein JDK (Gradle besorgt sich JDK 21
selbst). `-PdataDir=/ordner` startet die Desktop-App mit einem anderen Datenordner,
`-PwindowSize=393x852` in Handygröße.

**APK-Schlüssel:** `tools/android-sign.sh` erzeugt ihn einmalig unter
`~/.android/eroeffnungspuzzle-release.jks` (Passwort daneben in `.pass`), bewusst außerhalb des
Projekts. **Beide Dateien sichern.** Ohne denselben Schlüssel lässt sich ein Update nicht über
die installierte App spielen, und Deinstallieren löscht den Lernfortschritt.

## Aufbau

| Modul | Inhalt |
|---|---|
| `shared` | Kotlin Multiplatform: Zuggenerator/SAN, Varianten und Puzzle-Logik, FSRS, gesamte Compose-Oberfläche |
| `desktopApp` | Fenster, Tastatur, DMG-Paket |
| `androidApp` | Activity, Zurück-Taste, APK |
