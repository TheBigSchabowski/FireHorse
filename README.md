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
  dazu. Die Auswahl lässt sich als Playlist speichern, abspielen, lernen, archivieren oder auf einmal löschen.
  **Löschen** bei einer Eröffnung löscht sie samt allen Varianten (mit Rückfrage).
- **Suchen:** das Feld unter den Zahlen auf der Startseite zeigt nur, worauf alle Begriffe passen (auch
  im Archiv). Züge gelten ab dem Anfang: `d4` findet die 1.d4-Eröffnungen, `e4 c5` findet 1.e4 c5,
  ein Schwarzzug allein die erste Antwort (`c5` = 1…c5); mit Nummer genau dort (`6.f3`, `3...e6`).
  `weiß`/`schwarz` filtert nach der gespielten Seite, alles andere sucht im Namen von Eröffnung und
  Variante (`najdorf`). Deutsche Figuren gehen (`Sf3`), `x`/`+`/`=` darf man weglassen. Die Treffer
  lassen sich abspielen, lernen oder auswählen; ✕ oder `Esc` leert das Feld. `Strg+B s` blendet das
  Feld aus (die Suche ist dann weg) und wieder ein, mit dem Cursor darin.
- **Archivieren:** bei einer Eröffnung, einer Variante oder einer Auswahl – zum Beiseitelegen statt
  Löschen, z. B. für ein neues Repertoire. Archiviertes steht ganz unten auf der Startseite unter
  „Archiv“, wird nicht abgefragt, zählt nicht bei fällig/neu, fehlt in Playlists, und seine Züge
  gelten beim Üben nicht als „andere Variante“. Von dort: **Durchgehen**, **Als Vorlage** (neue
  Variante mit diesen Zügen), **Zurückholen** (mit altem Lernstand) oder endgültig **Löschen**.
  „Alle exportieren“ nimmt das Archiv mit.
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
  Unten auf dem Brett liegt dabei eine Leiste mit sechs Stiften und der Wahl zwischen Kreis und
  ausgefülltem Feld. Am Computer wechseln ← → den Stift und ↑ ↓ zwischen Kreis und Feld; Shift malt
  mit Stift 2, Alt mit 3, beide mit 4. Die Farbe jedes Stifts ist frei wählbar (`Strg+B k` oder
  Einstellungen → Konfiguration → Brett → Zeichenfarben): aus 16 Farben oder als eigener Code mit
  Namen, z. B. `#7FFFD4` „Aquamarin“. Schon Gezeichnetes nimmt die neue Farbe an.
- **Durchgehen, Bearbeiten, Üben** sind drei Modi: „Durchgehen“ (und Abspielen, Playlists) lässt die Züge der Variante,
  wie sie sind – ein anderer Zug auf dem Brett öffnet einen Nebenstrang nur zum Anschauen, `Strg+B Enter` (oder
  „↩ Zurück zur Variante“) führt zurück, „Züge bearbeiten“ wechselt ins Bearbeiten. „Bearbeiten“ ändert die Züge:
  ein anderer Zug ersetzt ab dort den Rest. „Üben“ fragt die Variante als Puzzle ab.
- **Zeichnungen zum Abfragen:** im Editor legt „+ Zeichnung hier“ (oder `Strg+B n`) an der Stellung auf dem Brett eine
  Zeichnung an, die mit der Variante gespeichert wird (blauer Rahmen: Antippen kreist ein, Ziehen malt
  einen Pfeil), dazu optional eine Frage oder ein Name. Beim Durchgehen und Abspielen ist sie blau zu
  sehen. Nach dem Puzzle und den Theorie-Lücken kommt jede Zeichnung dran: das Brett zeigt ihre
  Stellung, du zeichnest aus dem Gedächtnis, „Prüfen“ vergleicht (grün richtig, orange fehlte, rot zu
  viel). Der Stift (die Farbe) zählt mit. Fehler zählen wie ein falscher Zug. Ändern sich die Züge davor, fällt die Zeichnung weg.
- Während eines Puzzles: **Zielstellung zeigen** als Hilfe, **↺ Nochmal von vorn**. Ein Zug aus
  einer anderen gespeicherten Variante gibt nur einen Hinweis, ein falscher Zug wird korrigiert
  und die Karte kommt bald wieder.
- Desktop: `Enter` / `Leertaste` = „Gut“ bzw. „Weiter“.
- **Einstellungen** (oben auf der Startseite) haben zwei Reiter: **Konfiguration** (neue Varianten
  pro Tag, Tempo des Players, Brettfarben, am Computer die Tastenbelegung samt Präfix, am Handy das Pferd
  (an/aus, links/rechts, Haltezeit) und das Raster fürs Doppeltippen, Export/Import), alles jederzeit änderbar, und **Hilfe**, die alles hier Beschriebene in der App
  erklärt. „Hilfe“ neben jedem Bereich der Konfiguration öffnet direkt die passende Seite.

### Kürzel

Am Computer erst `Strg+B`, dann eine Taste (wie bei tmux). `Strg+B ?` oder einfach `Shift+S` zeigt die Liste
aller Kürzel; darin blättern ↑ ↓ (←/→ seitenweise), `Shift+S` oder `Esc` schließt sie wieder.
`Strg+N` und dann eine Ziffer startet „Playlist lernen“ der Eröffnung an dieser Stelle der Startseite
(`1` die erste, angepinnte zuerst, bis `9`); unten steht dabei, welche Nummer welche Eröffnung ist.
`8` im Editor schaltet die Seite der Variante um (Weiß ↔ Schwarz, wie „Ich spiele“), mit „Speichern“ übernommen; die
Züge bleiben, also auch der Lernstand. So kann eine Eröffnung wie Sizilianisch Varianten für beide Seiten haben, und die
Suche (`weiß`/`schwarz`) findet sie danach. Beim Üben dreht `8` nur das Brett, bis zur nächsten Variante.
`Shift+S`, `Shift+A`, `Strg+N` und `8` gelten nicht, solange der Cursor in einem Textfeld steht; dort wird getippt.
Passt ein Buchstabe nicht, lässt er sich unter Einstellungen → Konfiguration → Tastenkürzel ändern
(auf die Taste klicken, die neue drücken; eine schon vergebene tauscht mit), ebenso das B nach Strg.
Die Liste kommt immer auch mit `?` oder `h`. `Strg+B` ist der Navigator: ein Buchstabe, der noch
keinen Befehl hat, öffnet die Konfiguration bei den Tastenkürzeln, wo du ihn belegen kannst.
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
| `Strg+B c` | 2 Schritte halten | Neue Variante |
| `Strg+B x` | 3 Schritte halten | PGN einfügen |
| `Strg+B v` | Kürzel-Liste antippen | PGN bis zur Stellung auf dem Brett kopieren |
| `Strg+B y` | Kürzel-Liste antippen | FEN der Stellung auf dem Brett kopieren (auch mitten im Halbzug-Durchgehen) |
| `Strg+B p` | Kürzel-Liste antippen | Alles kopieren: die ganze Variante als PGN mit Eröffnung, Name, Theorie und Zeichnungen (als `[%cal]`/`[%csl]` wie bei lichess) |
| `Strg+B o` | Kürzel-Liste antippen | Alles bis zur Stellung kopieren: Züge und Zeichnungen bis zum Brett, die Theorie ganz |
| `Strg+B d` | Kürzel-Liste antippen | PGN mit allem einfügen: Züge, Namen, Theorie und Zeichnungen kommen zurück (auch aus lichess) |
| `Strg+B l` | Kürzel-Liste antippen | Eröffnungsdatenbank zur Stellung auf dem Brett: Meister, lichess oder eigene Partien (wie openingtree); ein Klick auf einen Zug spielt ihn im Editor |
| `Strg+B e` | Kürzel-Liste antippen | Eröffnungsnotizen: eigene Notizen zur offenen Eröffnung, oder „Allgemein“ |
| `Strg+B s` | Kürzel-Liste antippen | Suchfeld auf der Startseite aus- und wieder einblenden (eingeblendet mit dem Cursor darin) |
| `Strg+B r` | – | Engine rechnet die Stellung auf dem Brett: beste Linien mit Bewertung; Klick spielt den ersten Zug einer Linie, Shift+Klick die ganze Linie (nur am Computer) |
| `Strg+B Shift+R` | – | Engine als Gegner im Editor: Seite (Weiß/Schwarz), Tiefe pro Zug und bester/zweit-/drittbester Zug wählen, dann zieht sie für diese Seite, du spielst die andere (nur am Computer) |
| `Strg+B f` | 4 Schritte halten | FEN einfügen |
| `Strg+B m` | 1 Schritt halten | Zeichenmodus an/aus (kurze Zeichnung, nur für den Moment) |
| `Strg+B n` | Kürzel-Liste antippen | Zeichnung zum Abfragen an dieser Stellung an/aus (im Editor) |
| `Strg+B w` | Kürzel-Liste antippen | Gezeichnetes auf dem Brett sofort wegwischen |
| `Strg+B k` | Kürzel-Liste antippen | Zeichenfarben einstellen (auch eigene wie `#7FFFD4`) |
| `Strg+B a` | Kürzel-Liste antippen | Variante abspielen / anhalten (im Editor) |
| `Strg+B b` | Doppeltippen Mitte rechts | Einstellungen, am Computer gleich bei den Tastenkürzeln |

Die oft gebrauchten Befehle liegen in der unteren Tastenreihe (`y x c v b n m`), nah an Strg und B.
Strg darf dabei gedrückt bleiben: `Strg+B Strg+V` ist dasselbe wie `Strg+B v`.

`Shift+A` (ohne `Strg+B`) blendet alles Gezeichnete auf dem Brett aus und wieder ein, auch die
Zeichnungen zum Abfragen; verloren geht dabei nichts. In einem Textfeld bleibt es ein großes A.

**Beim Zeichnen** (Zeichenmodus, Zeichnung zum Abfragen, Abfrage aus dem Gedächtnis) ist jede Taste
allein ein Befehl, ohne `Strg+B`: `1`–`6` Stift wählen, `o` Kreis oder ausgefülltes Feld, `x` alles
wegwischen, `k` Zeichenfarben, `u` oder `Esc` Zeichnen beenden, dazu ← → Stift und ↑ ↓ Kreis/Feld.
Steht der Cursor in einem Textfeld, landen Buchstaben dort; ein Tipp aufs Brett holt die Tasten
zurück. Auch diese Tasten lassen sich unter Konfiguration → Tastenkürzel umbelegen.

Das geht auch mitten in einem Puzzle: die neue Variante legt sich darüber, nach Speichern oder
Abbrechen geht es mit dem Puzzle weiter. Eine angefangene Variante im Editor wird dabei nie
überschrieben.

**lichess-Datenbank:** lichess gibt sie nur mit einem persönlichen API-Schlüssel heraus. Auf
<https://lichess.org/account/oauth/token> einen neuen anlegen (kein Häkchen nötig) und unter
Einstellungen → Konfiguration → lichess einfügen. Jeder nimmt seinen eigenen; er bleibt auf dem Gerät
und kommt nicht mit in Exporte.

**Eigene Partien (wie [openingtree](https://www.openingtree.com)):** Der Reiter „Partien“ im Datenbank-Fenster
(`Strg+B l`) zeigt die Partien eines Spielers als Baum: welche Züge in jeder Stellung gespielt wurden, wie oft, wie sie
ausgingen (Ergebnisbalken, Punkte in %, Ø Gegner), dazu die zuletzt gespielte Partie, den besten Sieg und die schlimmste
Niederlage (ein Klick öffnet sie auf lichess/chess.com). Die Partien lädt FireHorse selbst, ohne Umweg über openingtree:
von **lichess** oder **chess.com** (Kontoname, Farbe, Tempo, nur gewertete, höchstens wie viele – die neuesten zuerst;
beide geben sie ohne Anmeldung heraus, mit lichess-Schlüssel geht es schneller) oder aus einer **PGN-Datei** (optional nur die
Partien eines Spielers). In den Baum kommen die ersten 30 Züge jeder Partie; er bleibt im Datenordner (`partien.json`), bis
andere Partien geladen werden. **Als .tree speichern** schreibt eine Datei, die openingtree.com mit „Load tree“ öffnet,
und **openingtree-Datei öffnen** liest einen dort gespeicherten Baum – so geht ein Baum in beide Richtungen.

**Engines:** FireHorse lädt keine Engine herunter und fragt im Netz nur lichess (Datenbank, Partien) und chess.com (Partien).
Unter Einstellungen → Konfiguration → Engines den Pfad zu einer UCI-Engine angeben, die schon auf dem
Computer ist (z. B. Stockfish; die von En Croissant liegt am Mac unter
`~/Library/Application Support/org.encroissant.app/engines/stockfish/stockfish-macos-universal`), oder die
Datei in den Ordner `Engines` im Datenordner legen – dann steht sie dort zum Hinzufügen. Mehrere Engines
lassen sich einrichten und wählen; jede hat ihre eigenen Linien, Tiefe (unbegrenzt oder fest), Threads und Hash. Alle vier
lassen sich mit − und + in Schritten ändern oder direkt eintippen (Tiefe bis 99, leer = unbegrenzt; Threads bis zur Zahl der
Kerne; Hash 16 MB bis 64 GB). Test gegen eine echte Engine:
`FIREHORSE_TEST_ENGINE=/pfad/zu/stockfish ./gradlew :shared:desktopTest`.

**Engine als Gegner:** Im Editor die ersten Züge beidseitig spielen, dann `Strg+B Shift+R`: wählen, welche
Seite die Engine spielt (W/S oder ← →), wie tief sie für jeden Zug rechnet (↑ ↓) und ob sie den besten,
zweit- oder drittbesten Zug spielt (1 2 3 – gut, um auch auf Nebenvarianten vorbereitet zu sein), Enter startet. Ab jetzt
zieht sie für ihre Seite, sobald sie am Ende der Variante am Zug ist, und du spielst nur noch die andere Farbe.
Zurückgehen in der Variante lässt sie in Ruhe; spielst du dort einen anderen Zug, antwortet sie darauf. Unter
dem Brett steht, was sie gerade tut, mit „Ändern“ und „Aus“. Tiefe und Zugwahl merkt sich FireHorse fürs nächste Mal.

**Importieren** (Einstellungen → Konfiguration → Daten) fragt nach dem Öffnen der Datei: **Dazunehmen**
– die Varianten der Datei kommen dazu, vorhandene mit gleicher Id werden aktualisiert, alles andere bleibt –
oder **Ersetzen** – alle eigenen Varianten werden mit Lernstand gelöscht und nur die aus der Datei geladen
(Notizen, Einstellungen und Playlists bleiben). Eine Datei ohne brauchbare Variante ersetzt nichts.

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

## Lizenz und Dank

FireHorse steht unter der [MIT-Lizenz](LICENSE): Jeder darf es forken, ändern und weitergeben,
solange der Hinweis auf das Ursprungsprojekt erhalten bleibt. Ausgenommen sind die Schachfiguren
(„cburnett“, GPLv2+).

Ideen kamen von [En Croissant](https://github.com/franciscoBSalgueiro/en-croissant),
[lichess](https://lichess.org), [openingtree](https://github.com/openingtree/openingtree) und [Anki](https://apps.ankiweb.net); die Eröffnungsdatenbank ist die
[Opening Explorer API](https://lichess.org/api#tag/Opening-Explorer) von lichess, die Wiederholungen
plant [FSRS](https://github.com/open-spaced-repetition). Alles im Einzelnen steht in [CREDITS.md](CREDITS.md).
