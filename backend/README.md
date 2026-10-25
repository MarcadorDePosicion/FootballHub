# Football Hub — backend

FastAPI, Python 3.13, SQLAlchemy 2, SQLite lub PostgreSQL przez psycopg 3.
Android nadal korzysta z lokalnego demo; uruchomienie serwera nie przełącza aplikacji mobilnej na backend.

## Lokalnie, bez Dockera

PowerShell, katalog główny repozytorium (tworzenie środowiska tylko przy pierwszym uruchomieniu):

```powershell
py -3.13 -m venv backend\.venv
.\backend\.venv\Scripts\python.exe -m pip install -r backend\requirements-dev.txt
.\backend\.venv\Scripts\python.exe -m pytest -c backend\pytest.ini -v
.\backend\.venv\Scripts\python.exe -m pip check
cd backend
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload
```

Gdy `py` jest niedostępne, wskaż pełną ścieżkę do Pythona 3.13. Lokalny proces opcjonalnie czyta `backend/.env` (wzór: `backend/.env.example`). Nie nadpisuj istniejącego pliku. Domyślnie używa SQLite i demo. Testy pytest izolują konfigurację i bazę; nie korzystają z deweloperskiego pliku bazy.

## Docker Compose + PostgreSQL

Wymagany jest działający Docker Engine w trybie kontenerów Linux oraz Docker Compose. Z katalogu głównego repozytorium:

```powershell
# Tylko jeśli główny .env jeszcze nie istnieje:
Copy-Item .env.example .env
docker compose config --quiet
docker compose up --build
```

API: http://127.0.0.1:8000, Swagger: http://127.0.0.1:8000/docs,
health check: http://127.0.0.1:8000/api/health. Opcja `-d` uruchamia stos w tle.
Backend czeka na `healthy` PostgreSQL, a własny health check sprawdza zapytanie do bazy.
Obraz zawiera wyłącznie zależności produkcyjne i `app/`; proces działa jako non-root.
Testy, lokalne bazy, `.venv` i `.env` nie trafiają do obrazu.

Compose czyta **główny `.env`**, nie `backend/.env`. Nie drukuj `docker compose config` z prawdziwymi sekretami; do walidacji używaj `--quiet`. Domyślne dane dostępowe są wyłącznie placeholderami dla lokalnego dev/demo. Port backendu jest dostępny tylko na loopback hosta; port PostgreSQL nie jest publikowany.

`docker compose down` zatrzymuje stos, zachowując nazwany wolumen PostgreSQL. Nie używaj `down -v` dla danych deweloperskich, które chcesz zachować. Zmiana hasła w `.env` nie zmienia hasła istniejącego użytkownika w już zainicjalizowanym wolumenie. Tabele są tworzone przez `create_all`; migracje schematu nie są jeszcze zaimplementowane.

## Konfiguracja

| Zmienna | Domyślnie / znaczenie |
| --- | --- |
| `ENV` | `development`; `production` odrzuca domyślny lub zbyt krótki sekret JWT |
| `BACKEND_PORT` | `8000`, port hosta; kontener zawsze słucha na 8000 |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Parametry kontenerowej bazy; Compose składa `DATABASE_URL=postgresql+psycopg://…@db:5432/…` |
| `JWT_SECRET_KEY`, `JWT_ALGORITHM` | Sekret podpisu, algorytm `HS256`; do wdrożenia wygeneruj własny silny sekret |
| `JWT_ACCESS_TOKEN_EXPIRE_MINUTES` | `60` |
| `FOOTBALL_DATA_MODE` | `demo`; `live` wymaga własnego `FOOTBALL_DATA_API_KEY` |
| `FOOTBALL_DATA_COMPETITIONS` | `PL`, kody CSV np. `PL,PD` |
| `FOOTBALL_DATA_CACHE_TTL_SECONDS` | `120`, cache całego pakietu w pamięci |
| `FOOTBALL_DATA_MAX_REQUESTS_PER_MINUTE` | `10`, budżet całej instancji |
| `FOOTBALL_DATA_DAYS_BACK`, `FOOTBALL_DATA_DAYS_FORWARD` | `1` i `1`, okno meczów względem dnia w Warszawie |
| `FOOTBALL_DATA_BASE_URL` | `https://api.football-data.org/v4` |

Dane dostępowe bazy w Compose powinny używać znaków bezpiecznych w URL (np. losowego hasła szesnastkowego), ponieważ są wstawiane bez kodowania do URL. Poza Compose można przekazać kompletny `DATABASE_URL`. Pliki `.env` są ignorowane przez Git; nie zapisuj kluczy w przykładach ani obrazie.

Live wykonuje do trzech żądań na ligę. Przy budżecie 10 cztery ligi (12 żądań) nie zakończą pełnego pobrania: limiter nie czeka i nie ponawia HTTP 429. Ogranicz liczbę lig lub ustaw budżet zgodny z rzeczywistym limitem konta dostawcy, nie zwiększaj go arbitralnie. Cache i limiter są lokalne dla procesu — uruchamiaj jeden proces na budżet klucza. Szczegóły mapowania, błędów i danych stale: [FOOTBALL_LIVE.md](FOOTBALL_LIVE.md).

## Endpointy

| Metoda i ścieżka | Dostęp / funkcja |
| --- | --- |
| `GET /api/health` | Publiczny; 200 przy poprawnej bazie, 503 przy jej niedostępności |
| `POST /api/auth/register` | Rejestracja (JSON `email`, `password`) |
| `POST /api/auth/login` | Logowanie; zwraca `accessToken`, `tokenType`, `user` |
| `GET /api/auth/me` | Profil bieżącego użytkownika |
| `GET /api/entries` | Własny dziennik |
| `PUT /api/entries/{id}` | Utworzenie/aktualizacja własnego wpisu, ID body zgodne ze ścieżką |
| `DELETE /api/entries/{id}` | Usunięcie własnego wpisu |
| `GET /api/favorites` | Własne `teamIds` i `playerIds` |
| `PUT/DELETE /api/favorites/teams/{id}` | Dodanie/usunięcie ulubionej drużyny |
| `PUT/DELETE /api/favorites/players/{id}` | Dodanie/usunięcie ulubionego zawodnika |
| `GET /api/football` | Publiczny pakiet ośmiu kolekcji, demo lub live |

Profil, dziennik i ulubione wymagają `Authorization: Bearer <accessToken>`.
Pełne schematy są dostępne w `/docs`. Komentarz predykcji jest zachowywany; limit tekstu to 2000 jednostek UTF-16 przed przycięciem.

## Izolowany test PostgreSQL i HTTP

Nie uruchamiaj skryptu smoke przeciwko bazie deweloperskiej: tworzy tymczasowe konta. Upewnij się, że projekt `footballhub-2e-test` nie istnieje i port 18000 jest wolny. Polecenia z katalogu głównego (PowerShell):

```powershell
$env:BACKEND_PORT = '18000'
$env:FOOTBALL_DATA_MODE = 'demo'
docker compose --env-file .env.example -p footballhub-2e-test config --quiet
docker compose --env-file .env.example -p footballhub-2e-test up -d --build --wait --wait-timeout 180
docker compose --env-file .env.example -p footballhub-2e-test ps
Get-Content -Raw backend/scripts/smoke_test.py | docker compose --env-file .env.example -p footballhub-2e-test exec -T backend python -
Invoke-RestMethod http://127.0.0.1:18000/api/health
# Sprzątaj również po nieudanym teście; wyłącznie ten projekt testowy:
docker compose --env-file .env.example -p footballhub-2e-test down -v
Remove-Item Env:BACKEND_PORT, Env:FOOTBALL_DATA_MODE
```

Skrypt używa rzeczywistego HTTP i PostgreSQL. Sprawdza JWT, CRUD, izolację użytkowników, idempotencję, komentarz predykcji, 64-bitowy timestamp i publiczne demo. Dodatkowo sprawdza typy kolumn, strefę czasową, klucz złożony, FK i możliwość dalszego użycia sesji po rollback. To oddzielny test dymny, nie zastępuje 221 testów pytest na SQLite. Nie wykonuje zapytań do dostawcy piłkarskiego.
