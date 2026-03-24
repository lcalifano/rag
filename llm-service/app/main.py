from fastapi import FastAPI
from app.routers import generate

app = FastAPI(title="LLM Service", version="1.0.0")

app.include_router(generate.router, tags=["LLM"])


@app.get("/health")
async def health():
    return {"status": "UP"}
