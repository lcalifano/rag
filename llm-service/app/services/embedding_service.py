from app.models.schemas import EmbeddingRequest


def get_embeddings_model(request: EmbeddingRequest):
    match request.provider.upper():
        case "OPENAI":
            from langchain_openai import OpenAIEmbeddings
            return OpenAIEmbeddings(
                model=request.model,
                api_key=request.api_key,
            )
        case "OLLAMA":
            from langchain_ollama import OllamaEmbeddings
            return OllamaEmbeddings(
                model=request.model,
                base_url=request.base_url,
            )
        case _:
            raise ValueError(f"Provider embedding non supportato: {request.provider}")


def generate_embeddings(request: EmbeddingRequest) -> list[list[float]]:
    model = get_embeddings_model(request)
    return model.embed_documents(request.texts)
