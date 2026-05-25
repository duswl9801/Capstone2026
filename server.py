from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field
from typing import List, Optional
import requests
import os
import json

import Experiment
from schemas import *
import config
import utils
from utils import build_visible_text
from prompt import build_prompt

app = FastAPI() # create a FastAPI instance

# A "decorator" takes the function below and does something with it.
@app.post("/next-action")
def next_action(request:screenContextRequest, authorization: str | None = Header(default=None)):

    if authorization != expected:
        raise HTTPException(status_code=401, detail="Unauthorized")

    if len(request.userGoal) > 150:
        raise HTTPException(status_code=400, detail="Goal is too long")

    visible_text = build_visible_text(request)

    prompt = build_prompt(request.userGoal, request.imgBase64, visible_text)

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
    model_response = ollama_response.get("response", "")

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

    target_label = (
            action_json.get("targetText") or action_json.get("targetContentDescription")
    )

    target = utils.find_target_ui(target_label, request.uies)

    if target:
        action_json["targetSource"] = "uiElement"
        action_json["targetText"] = target_ui.text or ""
        action_json["targetContentDescription"] = target_ui.contentDescription or ""
        action_json["targetClassName"] = target_ui.className or ""
    else:
        action_json["targetBounds"] = None
        action_json["targetSource"] = "notFound"

    return json.dumps(action_json, ensure_ascii=False)
