from datetime import datetime, timedelta, timezone
import re

import bcrypt
from fastapi import Depends, HTTPException, Request
import jwt
from sqlalchemy.orm import Session

from .config import Settings
from .database import get_db
from .models import User


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("ascii")


def verify_password(password: str, password_hash: str) -> bool:
    try:
        return bcrypt.checkpw(password.encode("utf-8"), password_hash.encode("ascii"))
    except (ValueError, UnicodeError):
        return False


def create_access_token(user_id: int, settings: Settings) -> str:
    now = datetime.now(timezone.utc)
    return jwt.encode(
        {"sub": str(user_id), "iat": now,
         "exp": now + timedelta(minutes=settings.jwt_access_token_expire_minutes)},
        settings.jwt_secret_key.get_secret_value(), algorithm=settings.jwt_algorithm,
    )


def unauthorized() -> HTTPException:
    return HTTPException(status_code=401, detail="Invalid authentication credentials",
                         headers={"WWW-Authenticate": "Bearer"})


def get_current_user(request: Request, db: Session = Depends(get_db)) -> User:
    headers = request.headers.getlist("authorization")
    if len(headers) != 1:
        raise unauthorized()
    parts = headers[0].split()
    if len(parts) != 2 or parts[0].lower() != "bearer":
        raise unauthorized()
    settings = request.app.state.settings
    try:
        claims = jwt.decode(parts[1], settings.jwt_secret_key.get_secret_value(),
                            algorithms=[settings.jwt_algorithm],
                            options={"require": ["sub", "iat", "exp"]})
    except (jwt.InvalidTokenError, TypeError, ValueError, OverflowError):
        raise unauthorized() from None
    subject = claims["sub"]
    # Bound IDs to the SQL Integer domain before querying PostgreSQL or SQLite.
    if not isinstance(subject, str) or not re.fullmatch(r"[1-9][0-9]{0,9}", subject):
        raise unauthorized()
    user_id = int(subject)
    if user_id > 2147483647:
        raise unauthorized()
    user = db.get(User, user_id)
    if user is None:
        raise unauthorized()
    return user
