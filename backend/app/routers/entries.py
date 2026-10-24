from fastapi import APIRouter, Depends, HTTPException, Response
from sqlalchemy import delete, select
from sqlalchemy.dialects.postgresql import insert as postgres_insert
from sqlalchemy.dialects.sqlite import insert as sqlite_insert
from sqlalchemy.orm import Session

from ..auth import get_current_user
from ..database import get_db
from ..models import JournalEntryModel, User
from ..schemas import JournalEntry

router = APIRouter(prefix="/api/entries", tags=["entries"])


def normalize_id(entry_id: str) -> str:
    value = entry_id.strip()
    if not value:
        raise HTTPException(status_code=422, detail="Entry ID must not be blank")
    return value


def entry_response(entry: JournalEntryModel) -> JournalEntry:
    return JournalEntry(
        id=entry.entry_id, match_id=entry.match_id, type=entry.type, text=entry.text,
        predicted_home=entry.predicted_home, predicted_away=entry.predicted_away,
        updated_at=entry.updated_at,
    )


@router.get("", response_model=list[JournalEntry])
def list_entries(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    entries = db.scalars(select(JournalEntryModel).where(
        JournalEntryModel.user_id == user.id).order_by(
        JournalEntryModel.updated_at.desc(), JournalEntryModel.entry_id.asc()))
    return [entry_response(entry) for entry in entries]


@router.put("/{entry_id}", response_model=JournalEntry)
def put_entry(entry_id: str, body: JournalEntry,
              user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    entry_id = normalize_id(entry_id)
    if body.id != entry_id:
        raise HTTPException(status_code=422, detail="Body and path entry IDs must match")
    values = body.model_dump(exclude={"id"})
    insert = sqlite_insert if db.get_bind().dialect.name == "sqlite" else postgres_insert
    statement = insert(JournalEntryModel).values(user_id=user.id, entry_id=entry_id, **values)
    db.execute(statement.on_conflict_do_update(
        index_elements=[JournalEntryModel.user_id, JournalEntryModel.entry_id],
        set_=values,
    ))
    db.commit()
    return body


@router.delete("/{entry_id}", status_code=204)
def delete_entry(entry_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    entry_id = normalize_id(entry_id)
    db.execute(delete(JournalEntryModel).where(
        JournalEntryModel.user_id == user.id, JournalEntryModel.entry_id == entry_id))
    db.commit()
    return Response(status_code=204)
