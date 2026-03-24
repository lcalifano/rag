from langchain_core.messages import HumanMessage, SystemMessage
from app.models.schemas import GenerateRequest


def get_chat_model(request: GenerateRequest):
    match request.provider.upper():
        case "OPENAI":
            from langchain_openai import ChatOpenAI
            return ChatOpenAI(
                model=request.model,
                api_key=request.api_key,
                temperature=request.temperature,
            )
        case "ANTHROPIC":
            from langchain_anthropic import ChatAnthropic
            return ChatAnthropic(
                model=request.model,
                api_key=request.api_key,
                temperature=request.temperature,
            )
        case "OLLAMA":
            from langchain_ollama import ChatOllama
            return ChatOllama(
                model=request.model,
                base_url=request.base_url,
                temperature=request.temperature,
            )
        case _:
            raise ValueError(f"Provider non supportato: {request.provider}")


def generate_response(request: GenerateRequest) -> str:
    model = get_chat_model(request)

    messages = []
    if request.context:
        messages.append(SystemMessage(content=(
            "Usa il seguente contesto per rispondere alla domanda dell'utente. "
            "Se il contesto non contiene informazioni rilevanti, rispondi basandoti "
            "sulle tue conoscenze.\n\n"
            f"Contesto:\n{request.context}"
        )))
    messages.append(HumanMessage(content=request.prompt))

    response = model.invoke(messages)
    return response.content


async def stream_response(request: GenerateRequest):
    model = get_chat_model(request)

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
