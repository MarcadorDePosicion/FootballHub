FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1
WORKDIR /srv/footballhub

COPY requirements.txt ./
RUN python -m pip install --no-cache-dir -r requirements.txt \
    && groupadd --system footballhub \
    && useradd --system --gid footballhub --home-dir /srv/footballhub footballhub \
    && chown footballhub:footballhub /srv/footballhub
COPY --chown=footballhub:footballhub app/ ./app/

USER footballhub
EXPOSE 8000
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
