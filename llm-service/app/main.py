import os
import httpx
import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.responses import JSONResponse
from app.routers import generate

logger = logging.getLogger("llm-service")

OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://ollama:11434")
CHAT_MODEL = os.getenv("CHAT_MODEL", "llama3")
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "nomic-embed-text")

models_ready = False


async def pull_model(client: httpx.AsyncClient, model: str):
    """Scarica un modello su Ollama se non presente."""
    logger.info(f"Pulling modello '{model}' su Ollama...")
    try:
        response = await client.post(
            f"{OLLAMA_BASE_URL}/api/pull",
            json={"name": model},
            timeout=600.0,  # I modelli possono essere grandi, timeout 10 minuti
        )
        if response.status_code == 200:
            logger.info(f"Modello '{model}' pronto.")
        else:
            logger.warning(f"Pull '{model}' status: {response.status_code}")
    except Exception as e:
        logger.error(f"Errore nel pull del modello '{model}': {e}")


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup: scarica i modelli necessari su Ollama."""
    global models_ready
    async with httpx.AsyncClient() as client:
        await pull_model(client, CHAT_MODEL)
        await pull_model(client, EMBEDDING_MODEL)
    models_ready = True
    logger.info("Tutti i modelli pronti. Servizio operativo.")
    yield


app = FastAPI(title="LLM Service", version="1.0.0", lifespan=lifespan)

app.include_router(generate.router, tags=["LLM"])


@app.get("/health")
async def health():
    if not models_ready:
        return JSONResponse(status_code=503, content={"status": "LOADING_MODELS"})
    return {"status": "UP"}
