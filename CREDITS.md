# Credits

FireHorse steht unter der [MIT-Lizenz](LICENSE): forken, ändern und weitergeben ist ausdrücklich
erwünscht – bitte mit dem Hinweis auf das Ursprungsprojekt
<https://github.com/TheBigSchabowski/FireHorse>, wie es die Lizenz verlangt.

## Was in FireHorse steckt

- **Schachfiguren:** „cburnett“-Satz von Colin M.L. Burnett, über [lichess](https://github.com/lichess-org/lila),
  Lizenz GPLv2+ (nicht MIT). Aus den SVGs in `tools/pieces-svg/` als PNG gerendert
  (`shared/src/commonMain/composeResources/drawable`), das App-Icon zeigt den weißen Springer daraus.
- **Spaced Repetition:** der Algorithmus FSRS-4.5 mit seinen Standardparametern, vom Projekt
  [open-spaced-repetition](https://github.com/open-spaced-repetition) (Jarrett Ye u. a.), in FireHorse
  selbst in Kotlin umgesetzt (`shared/.../core/Fsrs.kt`).
- **Bibliotheken:** [Kotlin](https://kotlinlang.org) mit kotlinx.coroutines und kotlinx.serialization,
  [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) (JetBrains), AndroidX,
  [Gradle](https://gradle.org) – alle Apache License 2.0.

## Dienste und Programme, mit denen FireHorse spricht

- **lichess-Eröffnungsdatenbank:** die [Opening Explorer API](https://lichess.org/api#tag/Opening-Explorer)
  von [lichess.org](https://lichess.org) (Meisterpartien und lichess-Partien), abgefragt mit dem eigenen
  API-Schlüssel jedes Nutzers. Die Partiedaten stammen aus der
  [lichess-Datenbank](https://database.lichess.org) (CC0). Danke an lichess für eine freie, offene Schachplattform.
- **Partien von lichess:** die [Spielerpartien-Export-API](https://lichess.org/api#tag/Games/operation/apiGamesUser)
  von lichess, ohne oder mit dem eigenen API-Schlüssel.
- **Partien von chess.com:** die öffentliche [Published-Data API](https://www.chess.com/news/view/published-data-api)
  von chess.com (nur lesend, ohne Anmeldung).
- **openingtree-Dateien (.tree):** FireHorse liest und schreibt das Dateiformat von
  [openingtree](https://github.com/openingtree/openingtree) (Code unter GPL-3.0), damit Bäume zwischen beiden hin- und
  hergehen. Umgesetzt allein nach dem Dateiformat, ohne Code von openingtree zu übernehmen (`shared/.../core/OpeningTreeFile.kt`).
- **Engines:** FireHorse bringt keine Engine mit, sondern spricht über UCI mit einer, die schon auf dem
  Computer ist – meist [Stockfish](https://stockfishchess.org) (GPLv3, eigenständiges Programm).

## Inspiration

Kein Code übernommen, aber viele Ideen:

- **[En Croissant](https://github.com/franciscoBSalgueiro/en-croissant)** (Francisco Salgueiro) –
  Repertoire-Training mit Spaced Repetition, lichess-Datenbank und Engines nebeneinander, Einstellungen
  je Engine (Linien, Tiefe, Threads, Hash).
- **[openingtree](https://www.openingtree.com)** (Code: [github.com/openingtree/openingtree](https://github.com/openingtree/openingtree),
  GPL-3.0) – die eigenen Partien oder die eines anderen Spielers als Eröffnungsbaum mit Ergebnissen, Filtern nach Farbe, Tempo
  und Anzahl, und den Partien, die in einer Stellung herausstechen. In FireHorse selbst in Kotlin gebaut.
- **[lichess](https://lichess.org)** – Brett, Figuren, Pfeile und Kreise mit Rechtsklick, die
  Eröffnungsdatenbank mit Ergebnisbalken.
- **[Anki](https://apps.ankiweb.net)** – Spaced Repetition als Lernprinzip, Lückentext in der Theorie
  und Cmd/Strg+Shift+C für eine Lücke.
- **[tmux](https://github.com/tmux/tmux)** – `Strg+B` als Präfix für alle Tastenkürzel.
