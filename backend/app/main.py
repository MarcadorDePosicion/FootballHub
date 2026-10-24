from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI
from fastapi.responses import JSONResponse
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from .config import Settings
from .database import Base, Database, get_db
from . import models  # Register tables before lifespan calls create_all.
from .routers.auth import router as auth_router
from .routers.entries import router as entries_router
from .routers.favorites import router as favorites_router


def create_app(settings: Settings | None = None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(application: FastAPI):
        # No settings, .env reads or database side effects at import time.
        configuration = settings if settings is not None else Settings()
        database = Database(configuration.database_url)
        application.state.settings = configuration
        application.state.database = database
        try:
            Base.metadata.create_all(bind=database.engine)
            yield
        finally:
            database.engine.dispose()

    application = FastAPI(title="Football Hub API", lifespan=lifespan)
    application.include_router(auth_router)
    application.include_router(entries_router)
    application.include_router(favorites_router)

    @application.get("/api/health", responses={503: {"description": "Database unavailable"}})
    def health(db: Session = Depends(get_db)):
        try:
            db.execute(text("SELECT 1")).scalar_one()
        except SQLAlchemyError:
            return JSONResponse(
                status_code=503, content={"status": "unavailable", "database": "unavailable"}
            )
        return {"status": "ok", "database": "ok"}

    return application


app = create_app()
