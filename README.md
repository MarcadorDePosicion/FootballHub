# Football Hub — mobilne MVP

Projekt studencki: Technologie Usługowe i Mobilne. Jeden istniejący moduł Android `app`, Kotlin, Jetpack Compose i Material 3.

## Dane demonstracyjne

**Wszystkie mecze, wyniki i tabele są przykładowe. Nie są danymi na żywo.** Pasek DEMO jest widoczny na każdym ekranie. Nazwy klubów są rzeczywiste, ale terminarz, wyniki i tabela są fikcyjne. Tabele pokazują fragmenty po cztery drużyny. Daty meczów są generowane względem dnia uruchomienia; identyfikatory pozostają stałe, więc wpisy nadal wskazują ten sam demonstracyjny mecz.

Retrofit wywołuje `GET /api/football`. `MockFootballInterceptor` zwraca JSON lokalnie i nigdy nie wywołuje `chain.proceed()`. Domeny `.example` nie trzeba konfigurować. Aplikacja działa bez backendu, klucza API i logowania. Notatki, typy i ulubione są danymi lokalnymi, bez synchronizacji z serwerem.

## Uruchomienie na istniejącym Pixel 8

1. Otwórz w Android Studio katalog `C:\Users\Puma\Documents\FootballHub`.
2. Wykonaj **File → Sync Project with Gradle Files**. Pierwsza synchronizacja wymaga internetu do pobrania bibliotek.
3. W ustawieniach Gradle użyj JDK dostarczonego z Android Studio (`C:\Program Files\Android\Android Studio\jbr`). Projekt ma kryterium JVM 25 w `gradle/gradle-daemon-jvm.properties`.
4. Otwórz **Tools → Device Manager** i uruchom istniejący **Pixel 8** (AVD `Pixel_8`). Nie twórz drugiego emulatora.
5. Wybierz konfigurację **app** i urządzenie **Pixel 8** na pasku narzędzi.
6. Kliknij **Run ▶**. Poczekaj na ekran Football Hub i informację DEMO.
7. Do nagrania użyj funkcji nagrywania w oknie emulatora / Running Devices. Jeśli jest dostępna przez menu `…`, wybierz **Record and Playback → Start Recording**.

Alternatywnie w terminalu PowerShell w katalogu projektu:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n pl.stasgroch.footballhub/.MainActivity
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. `installDebug` wymaga uruchomionego emulatora.

## Scenariusz przed nagraniem

- Start: sprawdź mecze dnia i ostatnie wyniki; pokaż oznaczenie DEMO.
- Mecze: wyszukaj Liverpool, przełącz ligę i status, sprawdź pusty wynik wyszukiwania.
- Szczegóły meczu: sprawdź datę, wynik, stadion, kolejkę i przejście do tabeli.
- Ligi: otwórz każdą z trzech lig; kliknij drużynę w tabeli.
- Drużyna: dodaj do ulubionych, sprawdź listę Ulubione, usuń i dodaj ponownie.
- Notatka: dodaj, odczytaj w szczegółach i w Moje, edytuj; sprawdź walidację pustej treści.
- Typ: dodaj wynik 2:1, edytuj na 3:1; sprawdź brak wyniku, wartości ujemne i powyżej 99.
- Moje: przełącz filtry Wszystkie / Notatki / Typy; przejdź do meczu z wpisu.
- Nawigacja: użyj dolnych zakładek, przycisku Wstecz oraz systemowego Wstecz.
- Obróć ekran podczas wypełniania formularza — pola powinny pozostać.
- Zamknij aplikację (Force stop) i uruchom ponownie: wpisy i ulubione powinny pozostać.
- Wyłącz sieć emulatora: mecze powinny nadal się wczytywać.
- Usuń wpis, anuluj jedno potwierdzenie usunięcia, następnie potwierdź; sprawdź oba ekrany i pustą listę.

Nie używaj Clear storage ani odinstalowania w teście trwałości — te operacje celowo usuwają bazę aplikacji.

## Organizacja kodu

Ścieżki poniżej są względem `app/src/main/java/pl/stasgroch/footballhub`:

- `MainActivity.kt`: uruchomienie Compose.
- `data/model`: modele domenowe i walidacja formularzy.
- `data/remote`: Retrofit API, interceptor i generator przykładowych danych.
- `data/repository`: repozytorium danych piłkarskich i interfejs danych użytkownika z implementacją Room.
- `data/local`: encje, DAO, baza Room; schemat wersji 1 zapisany w `app/schemas`.
- `ui/FootballViewModel.kt`: ładowanie, stan, zapis i obsługa błędów.
- `ui/navigation/FootballApp.kt`: NavHost i dolna nawigacja.
- `ui/screens`: ekrany meczów, lig, drużyn, wpisów i współdzielone komponenty.
- `ui/theme`: ciemna identyfikacja Football Hub z limonkowym i turkusowym akcentem.

Przepływ danych: Compose → ViewModel → repozytorium → Retrofit/OkHttp lub Room. Room publikuje zmiany przez Flow. Formularz wraca dopiero po zakończeniu zapisu. Notatki i typy mają stabilne identyfikatory UUID oraz identyfikator meczu. Ulubione są unikalne po identyfikatorze drużyny. Wersja 1 bazy nie korzysta z destrukcyjnych migracji; kolejne zmiany schematu wymagają migracji.

Aby podłączyć backend, zmień fabrykę `FootballRepository.demo()`: usuń interceptor i ustaw adres REST, zachowując kontrakt `FootballData` albo dodając mapowanie DTO. Ekrany nie wymagają zmiany. Synchronizacja wpisów użytkownika to osobny przyszły etap implementacji interfejsu `UserRepository`.

## Biblioteki i zgodność

Zachowano Gradle 9.6.0, AGP 9.4.1, ustawienie pluginu Compose/Kotlin 2.2.10, Compose BOM 2026.02.01 oraz minSdk 26 / compileSdk i targetSdk 37. AGP używa wbudowanej obsługi Kotlin.

Dodano Navigation Compose 2.9.7, Lifecycle Compose 2.9.4, Retrofit i converter Gson 2.11.0, OkHttp 4.12.0, Room 2.8.4 oraz KSP 2.3.6. Nie dodano drugiego modułu.

Biblioteki testowe z szablonu wymagały aktualizacji do Espresso 3.7.0 i AndroidX JUnit 1.3.0: Espresso 3.5.1 wywoływało nieistniejącą na Androidzie 16 metodę `InputManager.getInstance`. Opis poprawki: https://developer.android.com/jetpack/androidx/releases/test

## Weryfikacja

Po każdym etapie (nawigacja, HTTP/mock, CRUD, Room) wykonano udaną kompilację debug.

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

`FootballDataTest` sprawdza JSON interceptora, spójność danych i walidację. `RoomPersistenceTest` używa oddzielnej testowej bazy, sprawdza CRUD oraz ponowne otwarcie pliku. `FootballUiTest` sprawdza ścieżkę przez nawigację, notatki, typy i ulubione na emulatorze; tworzy i usuwa własne wpisy testowe.

Wynik końcowy (21.10.2026): `assembleDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest` i `lintDebug` — BUILD SUCCESSFUL. 3 testy JVM (w tym test szablonu) i 3 testy na Pixel_8 / Android 16 (w tym test szablonu), bez błędów. Sprawdzono cały scenariusz UI, odtworzenie aktywności i zapis po ponownym otwarciu bazy. Lint nie zgłasza błędów; pozostają ostrzeżenia o dostępności nowszych bibliotek i nieużywanych zasobach szablonu. Celowo nie aktualizowano narzędzi projektu. Po testach APK ponownie zainstalowano i uruchomiono na Pixel 8.

## Dopracowanie UI/UX — 21.10.2026

Zmiany dotyczą warstwy prezentacji. Zachowano modele, ViewModel, repozytoria, mock JSON, bazę Room, trasy nawigacji i operacje CRUD. Konfiguracja Gradle i wersje bibliotek nie zostały zmienione w tym etapie.

- Centralna paleta `HubColors`, typografia i czteropunktowa skala `HubSpace`.
- Kompaktowy Start bez dużego banera; małe nagłówki lig, emblematy z inicjałami, stała kolumna wyników i dyskretne oznaczenie DEMO.
- Rozróżnienie prezentacji spotkania planowanego, zakończonego oraz statusów trwającego meczu. Nie zmieniano danych mocka, żeby sztucznie dodawać mecz na żywo.
- Panel szczegółów z drużynami i wynikiem/godziną, czytelnymi profilami klubów oraz hierarchią akcji notatki i typu.
- Jedna tabela z wyrównanymi kolumnami M/B/Pkt, ograniczoną szerokością nazw, wyróżnieniem ulubionych i przewijaniem poziomym przy małej szerokości lub dużej czcionce.
- Lekkie własne ikony wektorowe bez nowych bibliotek, aktywna zakładka dolnego paska i obsługa odstępów systemowych.
- Formularze z kontekstem meczu i widoczną walidacją, jednoznacznym zapisem, anulowaniem i usuwaniem. W formularzu dolny pasek ustępuje miejsca edycji; powrót jest dostępny przez Wstecz lub Anuluj.
- Filtry meczów i dziennika są zapamiętywane jako stan UI ponad stosem nawigacji. Nie zmieniono logiki pobierania ani zapisywania danych.

Test regresji obejmuje CRUD i ulubione, powrót do wyszukiwania po przełączeniu zakładki oraz odtworzenie aktywności. Osobny test tabeli sprawdza szerokość 320 dp, czcionkę 150%, przewijanie i kliknięcie długiej nazwy drużyny. Do oceny wyglądu służą zrzuty Start, szczegółów, tabeli i formularza zapisane przez test w katalogu `/sdcard/Download/footballhub-ui` emulatora.

Przed nagraniem warto ręcznie sprawdzić: wszystkie trzy ligi, długie nazwy klubów, klawiaturę i przewijanie formularzy, obrót telefonu podczas edycji, filtry po zmianie zakładek, usuwanie/anulowanie usunięcia wpisu oraz ponowne uruchomienie aplikacji z zapisanymi wpisami. Przykładowe dane są lokalne; nie dodano reklam, kursów, logowania ani funkcji spoza zakresu Football Hub.

Wynik weryfikacji etapu UI/UX: `assembleDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest`, `lintDebug` — BUILD SUCCESSFUL. 3 testy JVM i 4 testy urządzeniowe bez błędów. Wizualnie obejrzano Start, szczegóły meczu, tabelę i formularz notatki na Pixel 8 (Android 16, ustawiona czcionka 130%). Skorygowano tabelę tak, aby wszystkie kolumny mieściły się przy tym ustawieniu, oraz dopasowano inicjały emblematów. Test na 320 dp przy czcionce 150% potwierdził przewijanie do Pkt i działanie linków do drużyn. Zrzuty końcowe: `app/build/ui-review/footballhub-ui/`. Lint pozostawia ostrzeżenia o aktualizacjach zależności i zasobach szablonu; bez błędów.

## Etap 1 — grupowane mecze i pływająca nawigacja

Start i Mecze używają wspólnej listy `GroupedMatches`: nagłówek rozgrywek, osobne leniwie renderowane wiersze, separator wewnątrz grupy, wyróżniony zwycięzca i spokojniejszy kolor przegranego. Remis prezentuje obie drużyny jednakowo, a spotkania zaplanowane pokazują godzinę bez fikcyjnego wyniku. Daty pozostają widoczne przy meczach; filtry ligi, statusu i nazwy drużyny zachowano.

`FloatingNavigation` na tych dwóch ekranach reaguje na rzeczywisty dystans przewinięty przez listę (`NestedScrollConnection`). Próg 24 dp ogranicza migotanie przy małych ruchach. Etykiety animują wysokość i przezroczystość przez około 220 ms; powrót na początek rozwija pasek. Nakładka nie zmienia rozmiaru listy. Stały dolny zapas 112 dp pozwala przewinąć ostatni mecz ponad pasek. Osobne stany list zachowują pozycję przy przełączaniu zakładek. Pozostałe ekrany zachowują dotychczasowy pasek i układ.

Testy obejmują gesty w obu kierunkach na Start/Mecze, powrót do początku, stałą wysokość widoku, dostępność ostatniego meczu i przejście do Lig ze zwiniętego paska. Test jednostkowy sprawdza próg, drobne zmiany kierunku oraz ignorowanie ruchu poza granicą listy. Test CRUD wskazuje wpisy po ich identyfikatorach, aby istniejące notatki użytkownika nie powodowały niejednoznaczności przycisków. Dodano wyłącznie techniczny znacznik testowy kart wpisów, bez zmian ich wyglądu lub logiki.

Do ręcznego sprawdzenia przed nagraniem: płynność animacji podczas szybkich zmian kierunku, długie nazwy po zmianie rozmiaru czcionki, obrót urządzenia oraz wygoda dotykania wszystkich pięciu ikon. Dane nadal pochodzą wyłącznie z lokalnego mocka.
Weryfikacja etapu 1: assembleDebug, testDebugUnitTest, connectedDebugAndroidTest i lintDebug — BUILD SUCCESSFUL; 4 testy JVM oraz 5 testów urządzeniowych bez błędów. Obejrzano zrzuty obu wariantów paska na Start i Mecze z Pixel 8 / Android 16 przy czcionce 130%. Testy potwierdziły zmianę stanu po gestach, brak zmiany wysokości listy, dostępność ostatniego meczu, nawigację oraz regresję CRUD i Room. Animacja wymaga jeszcze subiektywnej oceny płynności przed nagraniem; nie przeprowadzono pomiaru liczby klatek.

## Nagłówek ligi i wykończenie Glass

Ekran ligi wykorzystuje istniejący `StandingsScreen` i Material 3 `MediumTopAppBar` z `enterAlwaysScrollBehavior` oraz `nestedScroll`. Nagłówek zastępuje ogólny pasek Football Hub na tej trasie. Widoki Tabela/Mecze są rzeczywistymi zakładkami, korzystającymi z tych samych danych i osobnych stanów list. Kliknięcie meczu otwiera dotychczasowe szczegóły. Cztery drużyny w tabeli nadal są fragmentem danych demonstracyjnych; nie dodawano sztucznych danych dla uzyskania przewijania.

`GlassMaterial.kt` definiuje lekki materiał: ciemny półprzezroczysty gradient, cienka krawędź z refleksami i miękki cień. Używają go pływający pasek, nagłówek ligi i filtry Meczy. Bez rozmywania tła, przechwytywania klatek i nowych bibliotek. Karty meczów i tabela pozostają nieprzezroczyste. Zmniejszono odstępy grup i wierszy; zakończone mecze pokazują status i datę, a planowane godzinę i datę. Dokładna godzina zakończonego spotkania pozostaje w szczegółach.

Do sprawdzenia: Ligi → La Liga → gest w górę i w dół (nazwa i kraj nadal widoczne), zakładka Mecze → spotkanie, powrót do tabeli; Start/Mecze → zwijanie i rozwijanie dolnego paska oraz ostatni mecz. Warto ocenić płynność na nieobciążonym emulatorze: podczas testów ten emulator logował pominięte klatki, więc nie potwierdzono stałych 60 FPS.
Wynik weryfikacji: 4 testy JVM i 6 testów na Pixel 8 / Android 16 bez błędów, w tym zwijanie i rozwijanie nagłówka ligi, działające zakładki, nawigacja, CRUD i Room. Obejrzano zrzuty Startu oraz ligi przed/po przewinięciu i widoku meczów. Po ostatniej korekcie odstępów wykonano ponownie assembleDebug i lintDebug.
Poprawka spójności nawigacji: usunięto alternatywny NavigationBar z Scaffold. Wszystkie zakładki i szczegóły meczu/ligi/drużyny korzystają z FloatingNavigation. Listy mają stały dolny zapas 112 dp; formularze nadal ukrywają pasek. Zwijanie podczas przewijania zachowano na Start/Mecze. Debug i lint przeszły, podobnie 4 testy JVM i 7 urządzeniowych, w tym kontrola marginesów pływającego paska na wszystkich trasach. Obejrzano zrzuty Lig i szczegółów meczu.
Zwijanie na wszystkich trasach: obserwator nestedScroll przeniesiono nad NavHost. Gest pionowy jest obserwowany w pre-scroll, zanim przejmie go nagłówek ligi; nic nie jest konsumowane przez dolny pasek. Próg 24 dp i animacje zachowano. Dzięki temu również krótkie listy i ekran ligi reagują na gesty kierunkowe; przewijanie poziome nie zmienia stanu. Zmiana trasy rozwija pasek. Testy potwierdziły oba kierunki w szczegółach meczu i ligi, wspólnie ze zwijaniem górnego nagłówka. assembleDebug, lintDebug, 4 testy JVM i 7 testów urządzeniowych: sukces.
Profil drużyny ma zakładki Mecze/Tabela. Tabela wykorzystuje team.leagueId oraz istniejący StandingsTable; aktualna drużyna jest wyróżniona tłem, limonkowym tekstem i stanem selected dla dostępności. Mecze zachowują dotychczasowy układ. Centrum meczowe ma kompaktowy przycisk Filtry, licznik i opis aktywnych ustawień. Panel oferuje ligę i status (w tym Trwające), Zastosuj/Anuluj oraz Wyczyść na liście. Wyszukiwanie pozostaje niezależne. Debug i lint oraz 4 testy JVM i 8 testów urządzeniowych przeszły. Sprawdzono łączenie filtrów, anulowanie, zachowanie po zmianie zakładek, wyczyszczenie i właściwe wyróżnienie drużyny. Zrzuty filter-panel, compact-filters i team-table obejrzano na Pixel 8.
Szczegóły meczu: herb i nazwa prowadzą do profilu drużyny; usunięto osobne skróty Profil i nagłówek Twoja perspektywa. Przyciski dodawania notatki i typu są obok siebie. Wspólna tabela ligi wyróżnia obu uczestników meczu, zachowując pozycje oraz kliknięcia drużyn. Weryfikacja: assembleDebug, lintDebug, 4 testy JVM i 9 testów na Pixel 8 / Android 16 bez błędów. Test sprawdził oba przejścia do profili, wyrównanie przycisków oraz wyróżnienie dwóch wierszy; regresja CRUD nadal przechodzi. Obejrzano zrzuty match-compact-actions i match-table przy czcionce 130%.
Minimalistyczny profil drużyny: kompaktowa gwiazdka przełączająca ulubione (kontur/wypełnienie), przypięte zakładki Mecze/Tabela/Informacje bezpośrednio pod nazwą i miękki gradient nagłówka bez dodatkowych bibliotek. Informacje obejmują miasto, rok założenia, stadion i ligę z istniejących danych. Ekrany szczegółów i formularze nie wyświetlają już marki Football Hub ani opisu mocka w nagłówku; zachowano przycisk powrotu. Pięć głównych ekranów zachowuje oznaczenie demo. assembleDebug, lintDebug, 4 testy JVM i 9 testów urządzeniowych przeszły. Obejrzano team-information na Pixel 8 przy czcionce 130%; testy obejmują zakładkę Informacje, ulubione, przejścia do profili oraz CRUD.
