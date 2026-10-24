from sqlalchemy import event
from sqlalchemy.exc import OperationalError

from app.database import get_db


def test_health_executes_select_on_isolated_database(client, application):
    statements = []
    engine = application.state.database.engine
    assert engine.url.database == ":memory:"

    def record(connection, cursor, statement, parameters, context, executemany):
        statements.append(statement)

    event.listen(engine, "before_cursor_execute", record)
    try:
        response = client.get("/api/health")
    finally:
        event.remove(engine, "before_cursor_execute", record)
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "database": "ok"}
    assert statements == ["SELECT 1"]


def test_health_masks_database_query_failure(client, application):
    statements = []

    class FailingSession:
        def execute(self, statement):
            statements.append(str(statement))
            raise OperationalError(str(statement), {}, Exception("private-host password=not-public"))

    def failing_db():
        yield FailingSession()

    application.dependency_overrides[get_db] = failing_db
    response = client.get("/api/health")
    assert statements == ["SELECT 1"]
    assert response.status_code == 503
    assert response.json() == {"status": "unavailable", "database": "unavailable"}
    assert "private-host" not in response.text
    assert "password" not in response.text
    assert "SELECT" not in response.text
