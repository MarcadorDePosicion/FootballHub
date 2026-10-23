# Football Hub

Football Hub to aplikacja Android przygotowana jako projekt studencki z przedmiotu **Technologie Usługowe i Mobilne**. Umożliwia przeglądanie demonstracyjnych meczów, tabel, składów i wydarzeń meczowych, wyszukiwanie drużyn i zawodników oraz prowadzenie lokalnego dziennika notatek i typów wyników.

**Dane piłkarskie są przykładowe i pochodzą z lokalnego mocka. Aplikacja nie pobiera wyników na żywo.** Nie wymaga konta, klucza API ani uruchomienia backendu.

## Funkcje

- **Start** — mecze dnia, ostatnie wyniki i informacja o demonstracyjnym źródle danych.
- **Mecze** — wybór dnia, filtrowanie według ligi i stanu spotkania (wszystkie, nadchodzące, zakończone), grupowanie według rozgrywek lub kolejność chronologiczna.
- **Szczegóły meczu** — data, wynik lub godzina, stadion, skrót strzelców oraz zakładki Szczegóły, Składy i Tabela. Nagłówek po przewinięciu pokazuje kompaktowy wynik i emblematy; tabela wyróżnia obu uczestników.
- **Ligi** — lista rozgrywek oraz zakładki Tabela i Mecze; nagłówek ligi zwija się podczas przewijania.
- **Profil drużyny** — zakładki Mecze, Tabela, Informacje i Skład, wyróżnienie drużyny w tabeli, dane o mieście, stadionie i roku założenia oraz lista zawodników i trener, jeśli jest dostępny.
- **Profil zawodnika** — klub, pozycja, numer, narodowość, data urodzenia i wiek, wzrost oraz preferowana noga w zakresie dostępnych danych. Braki nie są zastępowane zmyślonymi wartościami. Zakładka Mecze zawiera historię występów i niewykorzystanej ławki.
- **Wyszukiwanie** — podpowiedzi drużyn i zawodników z filtrem rodzaju w nagłówku głównych ekranów. Wybór otwiera profil; powrót przywraca zapytanie i filtr. Dotknięcie poza polem zamyka i czyści wyszukiwanie. Wyszukiwanie meczów nie jest zaimplementowane.
- **Ulubione** — drużyny i zawodnicy dodawani gwiazdką na profilu, z lokalnym zapisem. Oddzielne kategorie Drużyny i Zawodnicy mają karty pustego stanu. Przy całkowicie pustych ulubionych dostępny jest przycisk przeglądania lig.
- **Moje** — tworzenie, odczyt, edycja i usuwanie notatek oraz typów wyników przypisanych do meczów. Wpisy są grupowane według spotkania i można je filtrować według rodzaju.

Formularze sprawdzają m.in. pustą treść notatki, limit 2000 znaków oraz wyniki w zakresie 0–99. Usunięcie wpisu wymaga potwierdzenia. Zapisane wpisy i ulubione pozostają dostępne po ponownym uruchomieniu aplikacji.

Na karcie zakończonego meczu ukryta jest akcja dodania typu; nadal można dodawać notatki i edytować wcześniej zapisane typy w Moje. To lokalny dziennik przewidywań, bez zawierania zakładów.

### Składy, zdarzenia i historia zawodników

- Składy gospodarzy i gości prezentują ustawienie na boisku, rezerwowych, dostępnego trenera i legendę. Brak ustawienia lub składu ma osobny komunikat albo listę dostępnych zawodników.
- Szczegóły pokazują zdarzenia po stronie odpowiedniej drużyny: minutę, gola i wynik po golu, strzelca, asystenta, kartki oraz zmiany. Symbole oznaczają gola, asystę, samobójczą bramkę (czerwona piłka), żółtą/czerwoną kartkę oraz drugą żółtą skutkującą wykluczeniem (żółta z czerwoną).
- Na boisku osiągnięcia nie przesuwają linii zawodników. Na ławce są obok nazwiska, a zmiany zawierają kolorową strzałkę, minutę i nazwisko zmienianego zawodnika; zejście jest czerwone.
- Historia zawodnika ma kompaktowe wiersze drużyn i wyniku, wyróżnienie zwycięzcy, osiągnięcia oraz osobny znacznik minut gry lub niewykorzystanej ławki. Minuty są wyliczane z dostępnych danych występu i kompletnych zdarzeń; standardowy pełny mecz to 90 minut, bez doliczonego czasu. Nieznany czas nie jest udawany jako zero.
- Zawodnika można otworzyć m.in. z wyszukiwarki, składu, strzelców i zdarzeń meczu. Wszystkie te dane są demonstracyjne.

Interfejs wykorzystuje ciemny motyw, limonkowe i turkusowe akcenty, kompaktowe listy oraz pływającą dolną nawigację reagującą na przewijanie. Formularze ukrywają dolny pasek, pozostawiając miejsce na edycję. Efekt szkła opiera się na gradientach i półprzezroczystości, bez kosztownego rozmywania tła.

Dni w Meczach i zakładki szczegółów można przesuwać poziomo. Ponowne dotknięcie aktywnej głównej zakładki przewija listę do góry, zachowując datę i filtry. Ekrany szczegółowe mają własne nagłówki i nie pokazują dolnej nawigacji. Pod przenikającymi ekranami pozostaje ciemne tło. Zakres sprawdzenia przejść i ograniczenia pomiarów opisuje [audyt animacji](docs/animation-audit-2026-10-23.md); nie jest to deklaracja płynności na każdym urządzeniu.

## Technologie i konfiguracja

Projekt zawiera jeden moduł aplikacji: `app`.

| Obszar | Technologia / wersja w konfiguracji |
| --- | --- |
| Język i UI | Kotlin, Jetpack Compose, Material 3 |
| Plugin Compose / Compose BOM | 2.2.10 / 2026.02.01 |
| Nawigacja | Navigation Compose 2.9.7 |
| Stan i cykl życia | ViewModel, Lifecycle Compose 2.9.4, Coroutines i Flow |
| Warstwa HTTP i JSON | Retrofit 2.11.0, Gson, OkHttp 4.12.0 |
| Dane lokalne | Room 2.8.4, KSP 2.3.6 |
| Narzędzia budowania | Gradle Wrapper 9.6.0, Android Gradle Plugin 9.4.1 |
| Android SDK | minSdk 26, compileSdk 37, targetSdk 37 |
| Testy | JUnit 4, Compose UI Test, AndroidX JUnit 1.3.0, Espresso 3.7.0 |

AGP korzysta z wbudowanej obsługi Kotlin. Wersje zależności znajdują się w `gradle/libs.versions.toml`, a ustawienia Androida w `app/build.gradle.kts`. Kryterium JVM demona Gradle to **25**, zgodnie z `gradle/gradle-daemon-jvm.properties`. Ustawienie zgodności źródeł Java 11 w module aplikacji nie oznacza, że Gradle należy uruchamiać na JDK 11.

## Uruchomienie

### Wymagania

- Android Studio obsługujące wersję AGP używaną w projekcie.
- Android SDK Platform 37 oraz narzędzia SDK wymagane podczas synchronizacji i budowania.
- JDK zgodny z konfiguracją projektu, w tym kryterium JVM 25 dla demona Gradle.
- Dowolny zgodny emulator lub urządzenie z **Androidem 8.0 (API 26) albo nowszym**.
- Połączenie z internetem przy pierwszej konfiguracji, aby pobrać Gradle, zależności, SDK, obraz emulatora i ewentualnie JDK. Działanie demonstracyjnej aplikacji nie wymaga sieci.

### Android Studio

1. Pobierz lub sklonuj repozytorium do wybranego katalogu. W Android Studio wybierz **Open** i otwórz katalog główny zawierający `settings.gradle.kts` oraz pliki `gradlew` i `gradlew.bat`.
2. W **Settings → Build, Execution, Deployment → Build Tools → Gradle** sprawdź ustawienie **Gradle JDK**. Można użyć JDK dostarczonego z Android Studio, jeśli spełnia wymagania projektu, albo wskazać odpowiednią instalację. Projekt definiuje JVM 25 i resolver toolchainów; automatyczne pobranie brakującego JDK może wymagać dostępu do sieci.
3. W **Tools → SDK Manager** sprawdź lokalizację Android SDK oraz zainstaluj wymaganą platformę SDK. Nie kopiuj lokalizacji SDK z innego komputera. Jeśli plik `local.properties` zawiera nieaktualne `sdk.dir`, dostosuj je do lokalnego SDK wskazanego przez Android Studio.
4. Wykonaj **File → Sync Project with Gradle Files** i poczekaj na zakończenie synchronizacji. W razie błędu sprawdź komunikat dotyczący JDK, SDK lub pobierania zależności przed zmianą wersji projektu.
5. Otwórz **Tools → Device Manager**. Wybierz istniejący zgodny emulator albo utwórz nowy przez **Create Device**, dobierając profil sprzętowy i obraz systemu Android API 26 lub nowszego. Nie jest wymagany konkretny model telefonu ani nazwa AVD.
6. Uruchom emulator, wybierz konfigurację **app** oraz docelowe urządzenie na pasku narzędzi i kliknij **Run ▶**.

Można również użyć fizycznego urządzenia z włączonym debugowaniem USB i zaakceptowaną autoryzacją komputera. Nazwy lub położenie opcji Android Studio mogą różnić się między wersjami i systemami operacyjnymi.

### Opcjonalne budowanie APK w terminalu

Polecenia wykonuj w katalogu głównym projektu. Korzystaj z dołączonego Gradle Wrappera — nie trzeba instalować osobnej wersji Gradle.

**Windows / PowerShell:**

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

**macOS / Linux:**

```sh
sh gradlew :app:assembleDebug
sh gradlew :app:installDebug
```

APK debug powstaje w `app/build/outputs/apk/debug/app-debug.apk`. Zadanie `installDebug` jest opcjonalne i wymaga dostępnego emulatora lub podłączonego urządzenia. Po instalacji aplikację można otworzyć z listy aplikacji urządzenia.

Jeśli terminal nie znajduje Javy, sprawdź lokalizację wybranego JDK w ustawieniach Gradle w Android Studio i skonfiguruj `JAVA_HOME` dla swojej instalacji. Lokalizację SDK sprawdzisz w SDK Manager. Nie zmieniaj wersji Gradle, Kotlin ani SDK wyłącznie po to, aby obejść błędną ścieżkę środowiska.

## Architektura

Kod aplikacji znajduje się w `app/src/main/java/pl/stasgroch/footballhub`:

| Plik / katalog | Odpowiedzialność |
| --- | --- |
| `MainActivity.kt` | Punkt wejścia aktywności i uruchomienie Compose |
| `data/model` | Modele danych i walidacja wpisów |
| `data/remote` | Kontrakt Retrofit, interceptor i generator danych demonstracyjnych |
| `data/repository` | Repozytorium danych piłkarskich oraz interfejs i implementacja danych użytkownika |
| `data/local` | Encje, DAO i baza Room |
| `ui/FootballViewModel.kt` | Ładowanie danych, stan UI, zapis i obsługa błędów |
| `ui/navigation` | Trasy Navigation Compose oraz pływająca nawigacja |
| `ui/screens` | Ekrany i współdzielone komponenty interfejsu |
| `ui/theme` | Kolory, typografia, odstępy i materiały wizualne |

Przepływ danych: **Compose → ViewModel → repozytorium → Retrofit/OkHttp lub Room**. Room publikuje zmiany przez Flow, dzięki czemu wpisy i ulubione są spójne między ekranami. Formularz wraca do poprzedniego widoku po zakończeniu zapisu.

Notatki i typy mają identyfikatory UUID oraz identyfikator meczu. Baza Room `football-hub.db` w wersji **2** zawiera tabele `entries`, `favorites` i `favorite_players`; ulubione przechowują identyfikatory drużyn lub zawodników. Schematy znajdują się w `app/schemas`. Migracja 1 → 2 dodaje ulubionych zawodników, zachowując wcześniejsze wpisy i drużyny. Baza nie korzysta z destrukcyjnych migracji. Jest lokalna: ponowne uruchomienie nie usuwa danych, natomiast wyczyszczenie danych aplikacji lub odinstalowanie je usuwa.

### Dane demonstracyjne i przyszły backend

Retrofit wywołuje `GET /api/football`. `MockFootballInterceptor` przechwytuje żądanie i zwraca lokalną odpowiedź JSON. Nie wywołuje `chain.proceed()`, więc żądanie nie trafia do serwera. Użyty adres w domenie `.example` jest placeholderem i nie wymaga konfiguracji DNS ani klucza API.

Przykładowe dane obejmują Premier League, La Ligę i Ekstraklasę: 12 drużyn, 6 meczów i 168 fikcyjnych zawodników. Nazwy klubów są rzeczywiste, lecz terminarz, wyniki, tabele, kadry i zdarzenia są fikcyjne. Tabele zawierają fragmenty po cztery drużyny; nie są pełnymi tabelami rozgrywek. Część pól zawodników i skład następnego dnia są celowo niedostępne, aby pokazywać obsługę braków. Daty są generowane względem dnia utworzenia danych demonstracyjnych; identyfikatory pozostają stałe, dlatego zapisany wpis nadal odnosi się do tego samego demonstracyjnego meczu, nawet gdy zmieni się jego prezentowana data. Dane piłkarskie są tworzone ponownie przy ładowaniu demo; nie stanowią trwałego archiwum sezonu.

Przyszłe podłączenie REST wymaga zastąpienia konfiguracji `FootballRepository.demo()`: usunięcia mock interceptora, ustawienia adresu backendu i zachowania kontraktu danych lub dodania mapowania DTO. Synchronizacja notatek, typów i ulubionych z serwerem wymaga osobnej implementacji w warstwie `UserRepository`; obecnie jej nie ma.

## Testy i weryfikacja

### Uruchamianie testów

W katalogu głównym projektu wykonaj:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

Na macOS i Linux zastąp `.\gradlew.bat` poleceniem `sh gradlew`. Testy jednostkowe i lint nie wymagają urządzenia. `connectedDebugAndroidTest` wymaga uruchomionego emulatora lub podłączonego urządzenia z debugowaniem.

| Zestaw | Zakres |
| --- | --- |
| `FootballDataTest` | JSON interceptora, spójność danych i walidacja formularzy |
| `NavigationScrollStateTest` | Stan i próg zmiany wariantu dolnej nawigacji |
| `PlayerEventsTest`, `PlayedMinutesTest` | Spójność zdarzeń, powiązania z zawodnikami i wyliczanie minut gry |
| `RoomPersistenceTest` | Operacje CRUD, odtworzenie danych i migracja schematu 1 → 2 |
| `FootballUiTest` | Nawigacja, wyszukiwanie i powroty, daty, filtry, profile, gesty, CRUD, anulowanie edycji i ulubione; wybrane klatki przejść i geometria |
| `LineupLayoutTest` | Stałe pozycje linii i wysokość przy przełączaniu stron, brak formacji i pusty skład |
| `FavoritesLayoutTest` | Puste kategorie przy posiadaniu tylko drużyny albo tylko zawodnika |
| `StandingsLayoutTest` | Układ tabeli przy szerokości 320 dp i czcionce 150%, przewijanie oraz przejścia do drużyn |

Repozytorium zawiera także podstawowe testy z szablonu Android Studio. Testy UI tworzą i sprzątają własne wpisy; test Room korzysta z oddzielnej bazy testowej.

Raporty są generowane w `app/build/reports/`, m.in. `tests/testDebugUnitTest/`, `androidTests/connected/debug/` oraz `lint-results-debug.html`. Wybrane testy UI zapisują zrzuty na urządzeniu w `/sdcard/Download/footballhub-ui`. Raporty i zrzuty są wynikami lokalnego uruchomienia, a nie warunkiem pobrania projektu.

### Ostatni udokumentowany wynik — 23.10.2026

Po poprawce zachowania wyszukiwarki wykonano kompilację, testy i lint:

- `assembleDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest` i `lintDebug`: **BUILD SUCCESSFUL**;
- **11 testów JVM i 27 testów Android**, bez błędów i pominięć;
- emulator Pixel 8 z Androidem 16 — środowisko weryfikacji, nie wymaganie aplikacji;
- lint: **0 błędów, 22 ostrzeżenia** dotyczące nowszych zależności, nieużywanych zasobów szablonu i nadmiarowej etykiety manifestu;
- oględziny zapisanych klatek przejścia Ligi ↔ liga oraz testy geometrii i ciemnego tła w trakcie wybranych przejść.

README zaktualizowano po zakończeniu implementacji i testów. Wyniki nie potwierdzają działania na każdym urządzeniu ani poprawności każdej klatki wszystkich tras. Nie wykonano benchmarku czasu klatek, pomiaru rekompozycji ani testów wydajności na fizycznym telefonie. Szczegółowy zakres oraz odtworzony przed poprawką błąd wyszukiwarki opisuje [raport audytu](docs/animation-audit-2026-10-23.md).

### Krótki scenariusz demonstracji

1. Pokaż Start i oznaczenie danych demonstracyjnych; otwórz szczegóły meczu oraz profil drużyny.
2. W Meczach zmień dzień, ligę, stan i kolejność. Sprawdź również dzień bez spotkań.
3. Wyszukaj drużynę i zawodnika, użyj filtrów, otwórz profile i wróć do zapytania. Sprawdź zamknięcie wyników i klawiatury po dotknięciu poza polem.
4. Przełącz zakładki ligi, drużyny, zawodnika i meczu. Obejrzyj zdarzenia, boisko, obie ławki i historię występów; sprawdź wyróżnienia w tabeli oraz zwijanie nagłówka i dolnej nawigacji.
5. Dodaj drużynę i zawodnika do Ulubionych; przełącz kategorie, usuń elementy gwiazdką i sprawdź puste stany.
6. Dodaj notatkę i typ do jednego meczu, sprawdź wspólny nagłówek w Moje, edycję, walidację oraz anulowanie i potwierdzenie usunięcia.
7. Obróć ekran podczas edycji, a następnie zamknij i uruchom aplikację ponownie, aby sprawdzić trwałość zapisanych danych. Sprawdź też działanie mocka bez sieci.

Przed nagraniem warto ocenić płynność animacji, długie nazwy, większą czcionkę i dostępność ostatnich elementów list nad pływającym paskiem. **Nie używaj czyszczenia danych aplikacji ani odinstalowania do sprawdzania trwałości** — te operacje usuwają lokalną bazę.

## Ograniczenia

- Brak prawdziwego backendu, wyników na żywo, logowania i synchronizacji między urządzeniami.
- Dane piłkarskie mają wyłącznie charakter demonstracyjny; puste dni oraz niepełne tabele są oczekiwane dla tego zestawu danych.
- Notatki, typy i ulubione są zapisywane lokalnie. Wyczyszczenie danych lub odinstalowanie aplikacji powoduje ich utratę.
- Emblematy klubów są lekkimi oznaczeniami z inicjałami, a nie oficjalnymi herbami.
- Projekt nie obejmuje reklam, kursów bukmacherskich, zakładów, czatu, fantasy football ani funkcji płatnych.
- Opisane budowanie APK dotyczy wersji debug; nie jest instrukcją wydania aplikacji w sklepie.
