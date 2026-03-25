from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse
from app.models.schemas import (
    GenerateRequest, GenerateResponse,
    EmbeddingRequest, EmbeddingResponse,
    SingleEmbeddingResponse, SingleEmbeddingRequest
)
from app.services.llm_service import generate_response, stream_response, CHAT_MODEL
from app.services.embedding_service import generate_embeddings, generate_single_embeddings

router = APIRouter()


@router.post("/generate", response_model=GenerateResponse)
async def generate(request: GenerateRequest):
    try:
        if request.stream:
            return StreamingResponse(
                stream_response(request),
                media_type="text/event-stream",
            )
        content = generate_response(request)
        return GenerateResponse(
            content=content,
            model=CHAT_MODEL,
            # === VECCHIO ===
            # provider=request.provider,
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/embeddings", response_model=EmbeddingResponse)
async def embeddings(request: EmbeddingRequest):
    try:
        result = generate_embeddings(request)
        return EmbeddingResponse(embeddings=result)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/embedding", response_model=SingleEmbeddingResponse)
async def embedding(request: SingleEmbeddingRequest):
    try:
        result = generate_single_embeddings(request)
        return SingleEmbeddingResponse(embedding=result)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))