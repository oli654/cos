# Sky Client (Fabric, Minecraft 1.21.1)

Klient PvP w formie moda (tylko po stronie klienta): ładne menu, moduły HUD, zoom, always-sprint
oraz napisy piosenek wiszące na niebie. **Bez cheatów** – mod zmienia tylko to, co sam widzisz
(brak reach/aim/auto-click), więc jest bezpieczny na większości serwerów.

## Budowanie
Najprościej: wygeneruj szablon na https://fabricmc.net/develop/template (1.21.1, pakiet `com.skyclient`)
i podmień jego `src/`, `build.gradle`, `gradle.properties` plikami z tego projektu.
Albo tutaj: `gradle wrapper --gradle-version 8.10.2`, potem `./gradlew build` (JDK 21).
Wynik: `build/libs/skyclient-1.0.0.jar` -> folder `mods` (+ Fabric API 0.115.0+1.21.1).

## Sterowanie
- **Prawy Shift** – menu (zakładki: HUD, PvP, Lyrics, Music, Theme)
- **C** – zoom (przytrzymaj), **]** – pokaż/ukryj napisy, **[** – pauza/wznów
- Klawisze zmienisz w Opcje > Sterowanie > Sky Client
- Menu > **Edit HUD**: przeciągaj moduły myszą, kółko myszy nad modułem = rozmiar

## Moduły HUD
Watermark, FPS, CPS, Ping, Coordinates, Speed, Memory, Clock, Combo, Reach (twój ostatni cios),
Item counter (gapple/perły/splash/strzały/totemy), Potion effects, Keystrokes (WASD, LMB/RMB, spacja, CPS),
Armor status (z wytrzymałością), Now playing. Każdy ma: skalę, tło, kolor, cień, pasek akcentu + opcje własne.

## Piosenki (zakładka Music)
Folder `.minecraft/skyclient/songs/`:
- `nazwa.lrc` – tekst z czasami, np. `[00:12.50]linijka tekstu` (własne teksty; mod nie zawiera gotowych)
- `nazwa.wav` – opcjonalnie muzyka (pauza i przewijanie tylko z WAV)
- albo dźwięk z resource packa: włącz "Use resource pack audio", w paczce
  `assets/skyclient/sounds.json`: `{ "nazwa": { "sounds": [ { "name": "skyclient:nazwa", "stream": true } ] } }`
  i plik `assets/skyclient/sounds/nazwa.ogg` (nazwa małymi literami, bez spacji)
- bez dźwięku: puszczasz muzykę gdzie indziej i dopasowujesz "Sync offset"

Komendy: `/skyclient`, `/lyrics play|stop|pause|resume|seek|anchor|list|folder|config|set|menu`.
Konfiguracja: `config/skyclient.json` i `config/skyclient-lyrics.json`.

## Gdy gra się wywala
- Sprawdź: Minecraft **1.21.1**, loader **Fabric**, w `mods` jest **Fabric API 0.115.0+1.21.1**, nie ma starego `skylyrics-*.jar`.
- Mod łapie własne błędy: pojedyncza funkcja, która się wysypie, wyłącza się sama po 3 próbach
  i zostawia w `logs/latest.log` wpis zaczynający się od `Sky Client:` (razem ze stacktrace).
- Jeśli crash i tak następuje, wklej tekst z `crash-reports/*.txt` (od `Description:` do pierwszego `Caused by:`).

## Nowości w 1.1
- Wyszukiwarka modułów w zakładce HUD, animacja otwierania menu
- Zakładka Theme: presety HUD (Minimal / PvP / Everything) i 3 sloty profili (Save / Load)
- Moduł **Target info** (nazwa i HP przeciwnika, na którego patrzysz)
- Music: przycisk **Next**, opcja "Auto-play next song"
- `/skyclient doctor` – pokazuje wersję Minecrafta i Fabric API oraz ewentualne błędy funkcji
