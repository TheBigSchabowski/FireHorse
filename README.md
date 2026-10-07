# FireHorse

Eröffnungen als Puzzle lernen, mit Spaced Repetition, für macOS, Linux und Android.

Du legst Varianten selbst an: Züge vom Start bis zur Stellung spielen, Eröffnung und Namen
eintragen („Sizilianisch“ / „Scheveningen“). Beim Üben steht nur der Name auf einer schwarzen
Tafel, und du spielst beide Seiten vom Start, bis die Stellung auf dem Brett steht. Jede
Zugfolge zählt, die in deinen gespeicherten Varianten vorkommt, also auch Zugumstellungen.

## Download

Unter [Releases](../../releases/latest):

| Plattform | Datei | Hinweis |
|---|---|---|
| macOS | `FireHorse_<version>_macos-arm64.dmg` | Apple Silicon |
| Linux | `FireHorse_<version>_linux-amd64.deb` | Debian, Ubuntu, Mint u. a. (x86-64) |
| Linux | `FireHorse_<version>_linux-amd64.tar.gz` | jede andere Distribution, ohne Installation |
| Android | `FireHorse_<version>_android.apk` | Android 8.0+ |

**macOS:** DMG öffnen und die App in „Programme“ ziehen. Die App ist nicht mit einem
Apple-Entwicklerzertifikat signiert, deshalb beim ersten Start: Rechtsklick auf die App →
**Öffnen** → **Öffnen** bestätigen.

**Linux:** Das DEB installiert die App samt Java mit `sudo apt install ./FireHorse_<version>_linux-amd64.deb`
und legt „FireHorse“ ins Anwendungsmenü (unter „Spiele“); gestartet wird auch mit `/opt/firehorse/bin/FireHorse`.
Das tar.gz einfach entpacken und `FireHorse/bin/FireHorse` starten. Gebraucht wird eine Desktop-Umgebung
mit X11 oder Wayland (XWayland) und OpenGL (`libgl1`), wie sie jede Desktop-Installation mitbringt.

**Arch Linux:** Das DEB ist ein Debian-Paket und geht dort nicht direkt (nur über `debtap`). Nimm das tar.gz:
`tar xzf FireHorse_<version>_linux-amd64.tar.gz`, dann `./FireHorse/bin/FireHorse`. Java ist schon dabei.
Bricht der Start mit Grafikfehlern ab, fehlt meist `mesa` bzw. der Grafiktreiber. Einen Menüeintrag gibt es
dabei nicht, dafür müsstest du selbst eine `.desktop`-Datei anlegen.

**Android:** APK im Browser auf dem Handy herunterladen und öffnen, dann „Aus dieser Quelle
installieren“ erlauben.

## Bedienung

- **+ Neue Variante:** Züge spielen, Namen geben, speichern. „Weiterführen“ bei einer vorhandenen
  Variante übernimmt ihre Züge, dann hängst du nur noch an, was anders ist.
- **PGN/FEN einfügen:** eine Zugfolge oder Stellung, die du irgendwo gefunden hast, ins Feld
  einfügen (Cmd/Strg+V, am Handy lange drücken). Aus einer **PGN** wird die Hauptvariante –
  Kopfzeilen, Kommentare `{…}`, Nebenvarianten `(…)`, `!?`/`$1` und das Ergebnis fallen weg,
  `Eröffnung`/`Variation` aus dem Kopf füllen die Namen, und wer den letzten Zug gemacht hat, ist
  die Seite, die du spielst. Aus einer **FEN** wird die Stellung, ab der die Variante beginnt –
  du spielst von dort die Züge, die du üben willst, und trainierst sie später auch ab dort. Das
  Eingefügte landet erst im Editor: durchgehen, mit „Ab hier kürzen“ zuschneiden, benennen,
  speichern. Eine bloße Zugliste („e4 c5 Nf3“) geht genauso.
- **▶ Abspielen:** bei einer Variante auf der Startseite oder im Editor direkt unter dem Brett. Die
  Variante läuft von allein Zug für Zug ab, z. B. wenn du eine Zugfolge vergessen hast. ⏸ hält an,
  `−`/`+` ändern die Zeit pro Zug (0,5 bis 5 s), „Wiederholen“ beginnt am Ende von vorn. Jeder Klick
  aufs Brett, auf einen Pfeil oder einen Zug hält den Player ebenfalls an.
- **Playlists:** jede Eröffnung ist eine Playlist aus all ihren Varianten, dazu kommen eigene unter
  „Meine Playlists“ (**+ Neue Playlist**: Varianten aus beliebigen Eröffnungen antippen, speichern,
  Reihenfolge mit ↑/↓ ändern). Jede Playlist hat zwei Modi: **▶ Abspielen** zeigt die Stellungen,
  die Varianten laufen nacheinander mit dem Player ab (die Endstellung bleibt kurz stehen;
  „← Vorherige“ / „Nächste →“ springen). **Lernen** fragt sie nacheinander als Puzzle ab, und zum
  nächsten geht es erst, wenn du das aktuelle ohne Fehler gelöst hast. Lernen mit Spaced Repetition
  bleibt davon unberührt.
- **Anpinnen:** holt eine Eröffnung ganz nach oben auf der Startseite, bis zu drei gleichzeitig.
  „Lösen“ stellt sie wieder an ihren Platz.
- **Auswählen:** zeigt an jeder Variante ein Kästchen; „Alle auswählen“ nimmt eine ganze Eröffnung
  dazu. Die Auswahl lässt sich als Playlist speichern, abspielen, lernen oder auf einmal löschen.
  **Löschen** bei einer Eröffnung löscht sie samt allen Varianten (mit Rückfrage).
- **Durchgehen:** öffnet die Variante am Start. Mit |◀ ◀ ▶ ▶|, den Pfeiltasten ← → oder einem Klick
  auf einen Zug in der Liste gehst du die Züge durch — nichts wird dabei gelöscht.
- **Bearbeiten:** dasselbe Brett, nur gleich bei der Endstellung. Ein Zug mitten in der Variante
  ersetzt ab dort den Rest, „Ab hier kürzen“ wirft ihn ohne Ersatz weg, „Zug zurück“ nimmt den
  letzten zurück. Geänderte Züge sind ein neues Puzzle: der Lernstand fängt von vorn an.
- **Lernen:** fällige und neue Varianten (Spaced Repetition, FSRS). **Frei üben:** alle gemischt,
  ohne Wertung. **Üben** an einer Variante: nur diese. **Züge üben:** die Stellungen der
  Variante gemischt, jeweils nur den nächsten Zug finden.
- **Theorie** (im Editor, Cmd/Strg+T): `((e6))` wird nach dem Lösen als Lücke abgefragt. `@8. Ng4`
  wird ein Link, der das Brett nach diesem Zug zeigt. Tipp `8` und Tab für den weißen Zug, nochmal
  Tab für den schwarzen.
- **Auf dem Brett zeichnen (wie bei lichess):** ein Feld lange gedrückt halten und auf ein anderes
  ziehen malt einen Pfeil, loslassen auf demselben Feld kreist es ein; am Computer geht auch die rechte
  Maustaste. Derselbe Pfeil nochmal nimmt ihn wieder weg, und mit dem nächsten Zug oder der
  nächsten Berührung des Bretts ist alles wieder sauber. Gilt im Editor wie beim Üben.
- **Zeichenmodus (vor allem fürs Handy):** dreimal schnell aufs Brett tippen. Ein grüner Rahmen
  zeigt den Modus an; jetzt kreist Antippen ein Feld ein (nochmal tippen nimmt es weg), Ziehen von
  Feld zu Feld malt einen Pfeil, und Züge sind gesperrt. „Löschen“ in der Leiste oben wischt alles
  weg, „Fertig“ oder nochmal dreimal tippen beendet den Modus. Gezeichnetes wird nicht gespeichert.
  Unten auf dem Brett liegt dabei eine Leiste mit vier Farben (grün, rot, blau, gelb) und der Wahl
  zwischen Kreis und ausgefülltem Feld. Am Computer malt Shift rot, Alt blau, beide gelb.
- **Zeichnungen zum Abfragen:** im Editor legt „+ Zeichnung hier“ (oder `Strg+B n`) an der Stellung auf dem Brett eine
  Zeichnung an, die mit der Variante gespeichert wird (blauer Rahmen: Antippen kreist ein, Ziehen malt
  einen Pfeil), dazu optional eine Frage oder ein Name. Beim Durchgehen und Abspielen ist sie blau zu
  sehen. Nach dem Puzzle und den Theorie-Lücken kommt jede Zeichnung dran: das Brett zeigt ihre
  Stellung, du zeichnest aus dem Gedächtnis, „Prüfen“ vergleicht (grün richtig, orange fehlte, rot zu
  viel). Die Farbe zählt mit. Fehler zählen wie ein falscher Zug. Ändern sich die Züge davor, fällt die Zeichnung weg.
- Während eines Puzzles: **Zielstellung zeigen** als Hilfe, **↺ Nochmal von vorn**. Ein Zug aus
  einer anderen gespeicherten Variante gibt nur einen Hinweis, ein falscher Zug wird korrigiert
  und die Karte kommt bald wieder.
- Desktop: `Enter` / `Leertaste` = „Gut“ bzw. „Weiter“.
- **Einstellungen** (oben auf der Startseite) haben zwei Reiter: **Konfiguration** (neue Varianten
  pro Tag, Tempo des Players, Brettfarben, am Computer die Tastenbelegung samt Präfix, am Handy das Pferd
  (an/aus, links/rechts, Haltezeit) und das Raster fürs Doppeltippen, Export/Import), alles jederzeit änderbar, und **Hilfe**, die alles hier Beschriebene in der App
  erklärt. „Hilfe“ neben jedem Bereich der Konfiguration öffnet direkt die passende Seite.

### Kürzel

Am Computer erst `Strg+B`, dann eine Taste (wie bei tmux). `Strg+B ?` zeigt die Liste aller Kürzel.
Passt ein Buchstabe nicht, lässt er sich unter Einstellungen → Konfiguration → Tastenkürzel ändern
(auf die Taste klicken, die neue drücken; eine schon vergebene tauscht mit), ebenso das B nach Strg.
Die Liste kommt immer auch mit `?` oder `h`.
Am Handy übernimmt das **Pferd** unten rechts (lässt sich nach links legen oder ausblenden): kurz antippen zeigt die Liste, gedrückt halten wählt
nach der Zeit – daneben steht, was beim Loslassen passiert, wegziehen bricht ab. Wie lang ein Schritt
dauert (0,5 bis 3 s, Standard 1 s), steht in den Einstellungen.

**Doppeltippen (Handy):** Der Bildschirm ist in Bereiche geteilt, standardmäßig sechs (zwei Spalten,
drei Reihen). Das Raster ist frei wählbar, 1–3 Spalten und 1–4 Reihen, z. B. nur „oben“ und „unten“.
Zweimal schnell auf eine freie Stelle tippen – kein Knopf, kein Textfeld, nicht das Brett – führt
das Kürzel dieses Bereichs aus. Welcher Bereich was macht, lässt sich in den Einstellungen festlegen
oder ganz ausschalten.

| Computer | Handy | |
|---|---|---|
| `Strg+B ?` | antippen | Liste aller Kürzel (Einträge sind anklickbar) |
| `Strg+B m` | 1 Schritt halten | Zeichenmodus an/aus (kurze Zeichnung, nur für den Moment) |
| `Strg+B n` | Kürzel-Liste antippen | Zeichnung zum Abfragen an dieser Stellung an/aus (im Editor) |
| `Strg+B u` | Kürzel-Liste antippen | Gezeichnetes auf dem Brett sofort wegwischen |
| `Strg+B c` | 2 Schritte halten | Neue Variante |
| `Strg+B p` | 3 Schritte halten | PGN einfügen |
| `Strg+B f` | 4 Schritte halten | FEN einfügen |
| `Strg+B a` | Kürzel-Liste antippen | Variante abspielen / anhalten (im Editor) |
| `Strg+B s` | Doppeltippen Mitte rechts | Einstellungen und Hilfe |
| `Strg+B 2` | Kürzel-Liste antippen | FEN der Stellung auf dem Brett kopieren (auch mitten im Halbzug-Durchgehen) |
| `Strg+B 3` | Kürzel-Liste antippen | PGN bis zur Stellung auf dem Brett kopieren |

Das geht auch mitten in einem Puzzle: die neue Variante legt sich darüber, nach Speichern oder
Abbrechen geht es mit dem Puzzle weiter. Eine angefangene Variante im Editor wird dabei nie
überschrieben.

Gespeichert wird am Mac in `~/Documents/EroeffnungsPuzzle`, unter Linux im Dokumente-Ordner des Benutzers
(`~/Dokumente/EroeffnungsPuzzle`, je nach Sprache `~/Documents/...`; maßgeblich ist `XDG_DOCUMENTS_DIR`
in `~/.config/user-dirs.dirs`), auf Android im App-Speicher.

## Bauen

```sh
./gradlew :desktopApp:run            # Desktop-App aus dem Quellcode starten
./gradlew :shared:desktopTest        # Tests (Zuggenerator mit Perft, Puzzle-Logik)
./gradlew :desktopApp:renderScreens  # Bildschirme in Handy- und Desktopgröße nach desktopApp/build/screens
./tools/build-release.sh             # DMG + signiertes APK nach dist/releases (am Mac)
./tools/build-linux.sh               # DEB + tar.gz nach dist/linux (unter Linux, braucht fakeroot)
./tools/android-emulator.sh          # APK auf einem unsichtbaren Emulator starten, Screenshot nach build/emulator.png
```

Der Emulator (Pixel 6, Android 16) läuft ohne Fenster und wird über `adb` bedient, z. B.
`adb shell input tap X Y`. `./tools/android-emulator.sh shot name` macht ein weiteres
Bildschirmfoto, `./tools/android-emulator.sh stop` beendet ihn.

Die Figuren-PNGs und das Icon entstehen aus `tools/pieces-svg/` mit `tools/RenderPieces.java`
(Skia aus Compose Desktop). Ein Test prüft, dass keine Figur leer ist.

Voraussetzungen: Android SDK unter `~/Library/Android/sdk` (Linux: `~/Android/Sdk`, sonst
`sdk.dir` in `local.properties`), ein JDK (Gradle besorgt sich JDK 21 selbst; für das DEB muss es ein
volles JDK mit `jpackage` sein, unter Ubuntu `openjdk-21-jdk`). `-PdataDir=/ordner` startet die Desktop-App mit einem anderen Datenordner,
`-PwindowSize=393x852` in Handygröße.

**APK-Schlüssel:** `tools/android-sign.sh` erzeugt ihn einmalig unter
`~/.android/eroeffnungspuzzle-release.jks` (Passwort daneben in `.pass`), bewusst außerhalb des
Projekts. **Beide Dateien sichern.** Ohne denselben Schlüssel lässt sich ein Update nicht über
die installierte App spielen, und Deinstallieren löscht den Lernfortschritt.

## Aufbau

| Modul | Inhalt |
|---|---|
| `shared` | Kotlin Multiplatform: Zuggenerator/SAN, Varianten und Puzzle-Logik, FSRS, gesamte Compose-Oberfläche |
| `desktopApp` | Fenster, Tastatur, DMG- und DEB-Paket |
| `androidApp` | Activity, Zurück-Taste, APK |
