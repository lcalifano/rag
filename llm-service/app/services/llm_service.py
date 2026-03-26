import os
from langchain_core.messages import HumanMessage, SystemMessage
from app.models.schemas import GenerateRequest

# === VECCHIO: factory pattern multi-provider ===
# def get_chat_model(request: GenerateRequest):
#     match request.provider.upper():
#         case "OPENAI":
#             from langchain_openai import ChatOpenAI
#             return ChatOpenAI(
#                 model=request.model,
#                 api_key=request.api_key,
#                 temperature=request.temperature,
#             )
#         case "ANTHROPIC":
#             from langchain_anthropic import ChatAnthropic
#             return ChatAnthropic(
#                 model=request.model,
#                 api_key=request.api_key,
#                 temperature=request.temperature,
#             )
#         case "OLLAMA":
#             from langchain_ollama import ChatOllama
#             return ChatOllama(
#                 model=request.model,
#                 base_url=request.base_url,
#                 temperature=request.temperature,
#             )
#         case _:
#             raise ValueError(f"Provider non supportato: {request.provider}")

# === NUOVO: Ollama fisso con modello configurato via env ===
OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://ollama:11434")
CHAT_MODEL = os.getenv("CHAT_MODEL", "llama3")


def get_chat_model(temperature: float = 0.7):
    from langchain_ollama import ChatOllama
    return ChatOllama(
        model=CHAT_MODEL,
        base_url=OLLAMA_BASE_URL,
        temperature=temperature,
    )


def generate_response(request: GenerateRequest) -> str:
    model = get_chat_model(request.temperature)

    messages = []
    if request.context:
        messages.append(SystemMessage(content=(
            "Usa il seguente contesto per rispondere alla domanda dell'utente, "
            "dai più peso ai documenti rilevanti che alla chat. "
            "Rispondi sempre nella lingua in cui è stato formulato il prompt"
            "Se il contesto non contiene informazioni rilevanti, rispondi basandoti "
            "sulle tue conoscenze.\n\n"
            f"Contesto:\n{request.context}"
        )))
    messages.append(HumanMessage(content=request.prompt))

    response = model.invoke(messages)
    return response.content


async def stream_response(request: GenerateRequest):
    model = get_chat_model(request.temperature)

    messages = []
    if request.context:
        messages.append(SystemMessage(content=(
            "Usa il seguente contesto per rispondere alla domanda dell'utente. "
            "Se il contesto non contiene informazioni rilevanti, rispondi basandoti "
            "sulle tue conoscenze.\n\n"
            f"Contesto:\n{request.context}"
        )))
    messages.append(HumanMessage(content=request.prompt))

    async for chunk in model.astream(messages):
        if chunk.content:
            yield chunk.content
