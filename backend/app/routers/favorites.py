from typing import Annotated

from fastapi import APIRouter, Depends, Path
from sqlalchemy import delete, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from ..auth import get_current_user
from ..database import get_db
from ..models import FavoritePlayerModel, FavoriteTeamModel, User
from ..schemas import Favorites

router = APIRouter(prefix="/api/favorites", tags=["favorites"])
PositiveId = Annotated[int, Path(gt=0, le=2147483647)]


def snapshot(db: Session, user_id: int) -> Favorites:
    return Favorites(
        team_ids=list(db.scalars(select(FavoriteTeamModel.team_id).where(
            FavoriteTeamModel.user_id == user_id).order_by(FavoriteTeamModel.team_id))),
        player_ids=list(db.scalars(select(FavoritePlayerModel.player_id).where(
            FavoritePlayerModel.user_id == user_id).order_by(FavoritePlayerModel.player_id))),
    )


def add_favorite(db: Session, model, column, user_id: int, item_id: int) -> Favorites:
    db.add(model(user_id=user_id, **{column.key: item_id}))
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        # Only a duplicate of this user's exact record is an idempotent success.
        if db.get(model, (user_id, item_id)) is None:
            raise
    return snapshot(db, user_id)


def remove_favorite(db: Session, model, column, user_id: int, item_id: int) -> Favorites:
    db.execute(delete(model).where(model.user_id == user_id, column == item_id))
    db.commit()
    return snapshot(db, user_id)


@router.get("", response_model=Favorites)
def get_favorites(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return snapshot(db, user.id)


@router.put("/teams/{team_id}", response_model=Favorites)
def add_team(team_id: PositiveId, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return add_favorite(db, FavoriteTeamModel, FavoriteTeamModel.team_id, user.id, team_id)


@router.delete("/teams/{team_id}", response_model=Favorites)
def remove_team(team_id: PositiveId, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return remove_favorite(db, FavoriteTeamModel, FavoriteTeamModel.team_id, user.id, team_id)


@router.put("/players/{player_id}", response_model=Favorites)
def add_player(player_id: PositiveId, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return add_favorite(db, FavoritePlayerModel, FavoritePlayerModel.player_id, user.id, player_id)


@router.delete("/players/{player_id}", response_model=Favorites)
def remove_player(player_id: PositiveId, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return remove_favorite(db, FavoritePlayerModel, FavoritePlayerModel.player_id, user.id, player_id)
