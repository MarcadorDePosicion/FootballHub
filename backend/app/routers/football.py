from fastapi import APIRouter, HTTPException, Request

from ..football_schemas import FootballData
from ..football_service import create_demo

router = APIRouter(prefix="/api/football", tags=["football"])


@router.get("", response_model=FootballData, response_model_by_alias=True)
def football(request: Request):
    if request.app.state.settings.football_data_mode != "demo":
        raise HTTPException(status_code=503, detail="Live football source is not configured yet")
    return create_demo(request.app.state.football_demo_date)
