from fastapi import APIRouter, HTTPException, Request, Response

from ..football_schemas import FootballData
from ..football_service import create_demo
from ..football_client import FootballError

router = APIRouter(prefix="/api/football", tags=["football"])


@router.get("", response_model=FootballData, response_model_by_alias=True)
async def football(request: Request, response: Response):
    if request.app.state.settings.football_data_mode == "demo":
        return create_demo(request.app.state.football_demo_date)
    service = request.app.state.football_service
    if service is None:
        raise HTTPException(status_code=503, detail="Live football source is not configured yet")
    try:
        data, cache_status = await service.get()
    except FootballError as error:
        raise HTTPException(status_code=error.status, detail=error.detail) from None
    response.headers["X-Football-Data-Cache"] = cache_status
    return data
