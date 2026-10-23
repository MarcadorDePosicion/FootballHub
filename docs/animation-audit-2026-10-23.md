# Audyt nawigacji i animacji — 23.10.2026

## Metoda i zakres

Sprawdzono aktualny kod oraz aplikację debug na emulatorze Pixel 8 / Android 16. Starsze nagrania nie były podstawą oceny obecnego zachowania. Testy Compose obejmują nawigację, stan i geometrię; wybrane przejścia zatrzymano w klatkach pośrednich. Nie wykonano benchmarku czasu klatek, profilowania rekompozycji ani pomiarów na fizycznym urządzeniu. Wynik nie jest gwarancją 60 FPS ani oceną każdej klatki wszystkich tras.

## Przyczyny i decyzje

- `FootballApp.kt`: każdy cel `NavHost` otrzymuje własny `DestinationFrame`, nagłówek, insets i dolną nawigację. Zmiana bieżącej trasy nie przebudowuje nagłówka nadal widocznego ekranu wychodzącego. Zachowano to rozwiązanie.
- Nieprzezroczyste ciemne tło pod `NavHost` i ciemne tło okna Androida nadal są obecne. Nie zmieniano domyślnego przenikania Navigation Compose ani jego czasu. Na klatkach pośrednich widać oba przenikające ekrany; nie należy mylić tego z drugim nagłówkiem dołożonym przez wspólny kontener.
- Dolny pasek jest nakładką, a nie elementem zmieniającym wysokość listy. Animacje rozmiaru trwają 200 ms, przezroczystości etykiet 160 ms. Obserwator nested scroll nie konsumuje gestu. Brak potwierdzonej podstawy do zmiany.
- `MeasuredContentPager` pamięta wysokości stron i używa ich maksimum, z odstępem 16 dp między stronami. Składy obu drużyn są mierzone razem, a umieszczana jest tylko wybrana strona. To zapobiega skracaniu kontenera i ograniczaniu zapamiętanego przewinięcia podczas przełączania.
- `FootballViewModel` ładuje demo przy inicjalizacji lub jawnym odświeżeniu, nie przy każdym wejściu na trasę. Dane użytkownika obserwuje przez Flow. Nie znaleziono ładowania danych wywoływanego na każdej klatce. Nie zastępuje to pomiaru wydajności przy dużym rzeczywistym zbiorze danych.

## Potwierdzony błąd i poprawka

Wyszukiwanie drużyny wywoływało `dismiss()`, które kasowało zapytanie i filtr przed nawigacją. Wyszukiwanie zawodnika już korzystało z mechanizmu wznowienia. Nowy test `teamSearchRestoresQueryAndFilterOnReturn` przed zmianą zakończył się błędem: po powrocie pole miało `EditableText = ''`, mimo wcześniejszego zapytania `barcelona`.

W `HeaderSearch.kt` wybór drużyny korzysta teraz z istniejącego mechanizmu `search.resume`, tak samo jak wybór zawodnika. Ukrywa podpowiedzi i klawiaturę, zachowując dane do powrotu. Zamknięcie wyszukiwania przez dotknięcie poza polem nadal czyści zapytanie. To jedyna zmiana kodu produkcyjnego tego audytu.

## Macierz weryfikacji

| Obszar | Dowód / zakres kontroli | Wynik i ograniczenie |
| --- | --- | --- |
| Start → mecz → Start | `outgoingMainScreenKeepsItsBoundsDuringDetailTransition`: geometria po 96 ms, ciemny piksel bocznego tła, położenie po powrocie | Bez regresji w badanej klatce; nie jest pomiarem FPS |
| Ligi → liga → Ligi | Nowy test `leagueTransitionFramesKeepGeometryAndDarkBackgroundBothWays`, po trzy próbki co 64 ms w obu kierunkach | Stałe położenie wychodzącego tytułu i ciemne tło; zapisane klatki wejścia i powrotu obejrzano |
| Liga → drużyna, mecz → drużyna | Istniejące testy tabel, profili i nawigacji | Poprawne cele i układy końcowe; brak osobnego pomiaru każdej klatki tych przejść |
| Ulubione → drużyna / zawodnik → powrót | Testy CRUD i ulubionych; dodatkowa asercja zachowania kategorii Zawodnicy po powrocie | Przejścia funkcjonalne i stan kategorii sprawdzone; nie profilowano klatek |
| Puste kategorie ulubionych | `FavoritesLayoutTest`, osobno tylko drużyna i tylko zawodnik | Komunikaty dostępne, przełączenie na zapełnioną kategorię działa; izolowane dane bez zmiany bazy użytkownika |
| Moje → edycja notatki / typu | `navigationCrudAndFavorites`: zapis, anulowanie notatki, powrót strzałką bez zapisu typu, usuwanie i odtworzenie aktywności | Anulowane dane nie trafiają do listy; zapisane zmiany są widoczne |
| Mecz → nowa notatka / typ | Test CRUD, kompaktowych akcji i nagłówków | Formularze działają; zakończony mecz nie pokazuje akcji dodania typu |
| Wyszukiwarka → drużyna / zawodnik | Testy podpowiedzi, zamykania i powrotów | Błąd drużyny naprawiony; zapytanie i filtr sprawdzane testem. ScrollState jest współdzielony w kodzie, ale nie dodano pomiaru długiej listy wyników po powrocie |
| Wyszukiwarka → mecz | Przegląd implementacji | Nie dotyczy: wyszukiwarka obsługuje drużyny i zawodników, nie mecze |
| Pięć ekranów głównych i ponowne kliknięcie zakładki | Testy reselect, filtrów, dat i floating navigation | Zachowane przewinięcie do góry na ponowne kliknięcie, filtry i data; pasek nie zmienia rozmiaru listy |
| Dni i zakładki szczegółów | Testy `dayPagesFollowFingerBeforeRelease` oraz gestów poziomych | Kontrola również przed puszczeniem palca; gesty nie zmieniają głównej zakładki |
| Składy gospodarzy i gości | `LineupLayoutTest`, powtarzane przełączenia oraz geometria zawodników | Równa wysokość kontenera i stałe pozycje linii; brak potrzeby zmiany mechanizmu |

## Ograniczenia

Ocena klatek pośrednich jest celowa, lecz próbkowana. Kontrola ciemnego piksela przy krawędzi nie dowodzi poprawności wszystkich pikseli ekranu; dlatego dodatkowo obejrzano zrzuty ligi. Nie deklaruje się braku wszelkich przycięć ani niepotrzebnych rekompozycji. Dłuższe listy rzeczywistego API, wolniejsze urządzenia i wszystkie warianty klawiatury wymagają oddzielnych pomiarów.

Nie zmieniano czasów przejść, nawigacji, gestów, tła, układu składów ani wcześniejszych poprawek interfejsu. Aktualne liczby testów i wynik kompilacji/lint znajdują się w sekcji „Testy i weryfikacja” głównego README, uzupełnionej po zakończeniu przebiegów.
