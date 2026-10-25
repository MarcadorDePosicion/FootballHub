# Źródło piłkarskie — Etap 2D.2

`GET /api/football` zachowuje osiem kolekcji i nazwy JSON z Etapu 2D.1.
Domyślny tryb to `demo`; samo ustawienie klucza nie uruchamia trybu `live`.
Konfiguracja `live` wymaga niepustego `FOOTBALL_DATA_API_KEY`. Klucz jest odczytywany
z konfiguracji i przesyłany wyłącznie w nagłówku `X-Auth-Token`.

## Pobieranie i limit

Na każde odświeżenie przypada maksymalnie `3 × liczba rozgrywek` żądań, sekwencyjnie:
`teams`, `matches`, `standings`. Nie ma pobierania profilu każdego zawodnika,
paginowania całych sezonów ani automatycznych retry. Lista rozgrywek to CSV,
np. `PL,PD,SA,BL1`. Okno meczów domyślnie obejmuje jeden dzień wstecz i jeden
w przód względem bieżącej daty w `Europe/Warsaw`.

Wszystkie rozgrywki korzystają z jednego przesuwnego okna 60 sekund. Domyślnie
wolno wykonać 10 żądań; każde rozpoczęte żądanie (również zakończone błędem)
zużywa budżet. Nadmiarowe żądanie nie jest wysyłane. Dla czterech lig potrzeba
co najmniej 12 dostępnych żądań na pełny cykl — przy limicie 10 taki cykl nie
zakończy się sukcesem. Limiter i cache są lokalne dla instancji aplikacji;
uruchomienie wielu procesów nie daje współdzielonego limitu dla jednego klucza.
W tym etapie należy używać jednej instancji/procesu dla danego budżetu.

## Cache i błędy

Cache w pamięci przechowuje wyłącznie cały poprawnie pobrany i zmapowany pakiet.
TTL domyślnie wynosi 120 s, liczonych od ukończenia pobierania. `asyncio.Lock`
chroni odświeżanie; oczekujące żądania wykorzystują nowo zapisany ważny pakiet.
Każda aplikacja ma własny cache, limiter, klienta i zamyka klienta przy wyłączeniu.

| Sytuacja | Wynik |
| --- | --- |
| Ważny cache | 200, `X-Football-Data-Cache: hit` |
| Poprawne pobranie | 200, `X-Football-Data-Cache: miss` |
| 429, 5xx, timeout, błąd transportu, lokalny limit | 200 `stale`, jeśli istnieje poprzedni pakiet; w przeciwnym razie 503 |
| 401/403 dostawcy | 503, również jeśli istnieje stary cache |
| Niepoprawny JSON/struktura lub inny błąd HTTP | 502, również jeśli istnieje stary cache |
| 404 dla `standings` przy rozgrywce potwierdzonej wcześniejszymi odpowiedziami | Pusta tabela; pozostałe dane mogą być zapisane w cache |

Sam status 404 nie rozróżnia niepoprawnego kodu od brakującej tabeli. Opcjonalny
brak tabeli wymaga zgodnego kodu i dodatniego ID w metadanych `teams`, obecności
list `teams`/`matches` oraz zgodności kodu i ID rozgrywki we wszystkich zwróconych
meczach. Bez tego potwierdzenia 404 daje 502. Nie analizujemy tekstu komunikatu
dostawcy i nie wykonujemy dodatkowego żądania weryfikacyjnego.

Błędy nie nadpisują ani nie przedłużają TTL poprzedniego pakietu. Odpowiedzi błędów
nie zawierają ciała odpowiedzi dostawcy ani tekstu wyjątków HTTP. Nie ma przejścia
z live do demo. `stale` nie ma dodatkowego limitu wieku w tym etapie.

## Mapowanie i ograniczenia niezmienionego kontraktu Androida

- ID lig, drużyn, zawodników i meczów pochodzą od dostawcy. Nie ma przemapowania
  na ID demo ani ingerencji w zapisy użytkownika. ID live i demo należą do różnych
  przestrzeni znaczeniowych: istniejących ulubionych/notatek demo nie należy
  automatycznie traktować jako referencji live tylko na podstawie równej liczby.
- Zdarzenia v4 nie mają gwarantowanego ID. Ich lokalne ID to deterministyczny
  31-bitowy hash tożsamości zdarzenia, z rozróżnieniem identycznych wystąpień.
  Wykryta kolizja kończy mapowanie błędem, zamiast scalać różne zdarzenia.
- W tabeli zachowywane są oficjalne `position` i `points` oraz kolejność dostawcy.
  Używana jest pierwsza tabela `TOTAL`. Kontrakt nie reprezentuje grup tabel ani
  kilku lig jednego klubu: `Team.leagueId` wskazuje pierwszą skonfigurowaną rozgrywkę,
  a mecze i wiersze tabel zachowują właściwe własne `leagueId`.
- `utcDate` jest konwertowane przez `ZoneInfo("Europe/Warsaw")`, także przy zmianach
  czasu. Wynik null pozostaje null, jawne 0:0 pozostaje 0:0. `Match.venue` nie jest
  uzupełniane stadionem gospodarza. Status jest zachowywany, także gdy jest nieznany.
- Dostawca nie gwarantuje miasta. `Team.city` pozostaje pustym tekstem; brak stadionu
  drużyny również daje pusty tekst. Kontrakt wymaga liczbowych `founded` i `round`:
  brak tych danych jest reprezentowany przez 0 (wartość techniczna, nie rzeczywisty rok/kolejka).
  Obsługa tych braków bez wartości technicznych wymagałaby przyszłej zmiany kontraktu/UI.
- Kadry i uczestnicy meczów są scalani po ID. Aktualna przynależność z kadry ma
  pierwszeństwo przed drużyną historycznego występu. `heightCm` i `preferredFoot`
  pozostają null. Brak nazwy nowego zawodnika przy znanym ID jest błędem struktury,
  nie powodem do wymyślania nazwiska.
- Lista wyjściowa API nie gwarantuje współrzędnych taktycznych. `starterRows`
  zawiera jeden płaski wiersz ID; zachowana jest formacja tekstowa. Android rozpoznaje
  brak prawidłowej geometrii i korzysta z listy zamiast zmyślonego ustawienia na boisku.
- `OWN` jest mapowane na `OWN_GOAL`: `teamId` oznacza drużynę zdobywającą gola,
  a zawodnik należy do przeciwnika. `YELLOW_RED` daje `SECOND_YELLOW`.
- `coverage` (nie nowe pole `completeness`) ma wartości Androida. Brak wszystkich
  sekcji zdarzeń daje `UNAVAILABLE`; częściowe dane dają `PARTIAL`. `COMPLETE`
  wymaga wszystkich list `goals`, `bookings`, `substitutions`, rozpoznanych zdarzeń,
  zakończenia `FINISHED`, czasu `REGULAR` i liczby goli zgodnej z wynikiem.
  Jest to konserwatywna reguła mappera, nie gwarancja kompletności udzielana przez API.
- `minutesPlayed` nie jest odgadywane. Pozostaje null do obliczenia w Androidzie;
  jedynie niewykorzystany rezerwowy w kompletnym zakończonym meczu ma potwierdzone 0.
  Dogrywka/karne pozostają `PARTIAL`, by nie uruchamiać androidowego założenia 90 minut.
  Zaplanowane mecze i walkowery nie tworzą fikcyjnych występów.

## Weryfikacja

Testy live używają wyłącznie `httpx.MockTransport`, zegara sterowanego przez test
i bazy SQLite w pamięci. Blokują prawdziwy transport HTTP. Dane testowe są fikcyjnymi
przykładami struktury v4; tokeny testowe powstają w pamięci i nie są zapisywane w plikach.
Nie przeprowadzono testu z prawdziwym kontem dostawcy ani nie potwierdzono dostępności
szczegółów meczów w konkretnym planie abonamentowym.

Oficjalne punkty odniesienia:
- https://docs.football-data.org/general/v4/match.html
- https://docs.football-data.org/general/v4/competition.html
- https://docs.football-data.org/general/v4/lookup_tables.html
