from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from ..auth import create_access_token, get_current_user, hash_password, unauthorized, verify_password
from ..database import get_db
from ..models import User
from ..schemas import Credentials, LoginResponse, UserProfile

router = APIRouter(prefix="/api/auth", tags=["auth"])


@router.post("/register", status_code=201, response_model=UserProfile)
def register(credentials: Credentials, db: Session = Depends(get_db)) -> UserProfile:
    user = User(email=str(credentials.email), password_hash=hash_password(credentials.password))
    db.add(user)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(status_code=409, detail="Email already registered") from None
    db.refresh(user)
    return UserProfile.model_validate(user)


@router.post("/login", response_model=LoginResponse)
def login(credentials: Credentials, request: Request, db: Session = Depends(get_db)) -> LoginResponse:
    user = db.scalar(select(User).where(User.email == str(credentials.email)))
    if user is None or not verify_password(credentials.password, user.password_hash):
        raise unauthorized()
    return LoginResponse(access_token=create_access_token(user.id, request.app.state.settings),
                         user=UserProfile.model_validate(user))


@router.get("/me", response_model=UserProfile)
def me(user: User = Depends(get_current_user)) -> UserProfile:
    return UserProfile.model_validate(user)
