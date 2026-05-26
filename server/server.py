from fastapi import FastAPI, Header, HTTPException
import requests
import json
import time

from Experiment import Experiment
from schemas import *
import config
from utils import collect_uies, find_ui
from prompt import build_prompt

app = FastAPI() # create a FastAPI instance

experiment_logger = Experiment(
    output_dir=config.OUTPUT_DIR,
    experiment_name=config.EXPERIMENT_NAME,
    model_name=config.MODEL_NAME
)

# A "decorator" takes the function below and does something with it.
@app.post("/next-action")
def ask_next_action(request:screenContextRequest, authorization: str | None = Header(default=None)):
    expected = f"Bearer {config.API_TOKEN}"

    if authorization != expected:
        raise HTTPException(status_code=401, detail="Unauthorized")

    if len(request.userGoal) > 150:
        raise HTTPException(status_code=400, detail="Goal is too long")

    start_time = time.perf_counter()

    visible_uies = collect_uies(request)

    #prompt = build_prompt(request.userGoal, visible_uies, request.imgBase64)
    prompt = build_prompt(request.userGoal, visible_uies)

    payload = {
        "model": config.MODEL_NAME,
        "prompt": prompt,
        "stream": False,
        "format": "json"
    }

    try:
        r = requests.post(config.OLLAMA_URL, json=payload, timeout=100)
        r.raise_for_status()
    except requests.RequestException as e:
        raise HTTPException(status_code=502, detail=f"Ollama error: {str(e)}")

    ollama_response = r.json()
    model_response = ollama_response.get("response", "") # raw answer

    try:
        action_json = json.loads(model_response)
    except json.JSONDecodeError:
        action_json = {
            "action": "",
            "targetText": "",
            "targetContentDescription": "",
            "targetClassName": "",
            "inputText": "",
            "rawResponse": model_response
        }

    # align the model output with the actual UI elements values
    target_label = (action_json.get("targetText") or action_json.get("targetContentDescription"))
    target = find_ui(target_label, request.uies)

    if target:
        action_json = {
            "action": action_json.get("action", ""),
            "targetText": target.text or "",
            "targetContentDescription": target.contentDescription or "",
            "targetClassName": target.className or "",
            "inputText": action_json.get("inputText", "")
        }
    else:
        action_json = {
            "action": action_json.get("action", ""),
            "targetText": action_json.get("targetText", ""),
            "targetContentDescription": action_json.get("targetContentDescription", ""),
            "targetClassName": action_json.get("targetClassName", ""),
            "inputText": action_json.get("inputText", "")
        }

    print(f"user goal: {request.userGoal}")
    print(f"raw response: {model_response}")
    print(f"alignment result: {action_json}")

    latency = round(time.perf_counter() - start_time, 2)

    experiment_logger.write_csv(
        latency=latency,
        user_goal=request.userGoal,
        raw_response=model_response,
        action=action_json.get("action", ""),
        target_text=action_json.get("targetText", ""),
        input_text=action_json.get("inputText", "")
    )

    return {"response": json.dumps(action_json, ensure_ascii=False)}


