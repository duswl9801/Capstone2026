from fastapi import FastAPI, Header, HTTPException
import json
import time

import torch
from transformers import AutoProcessor, AutoModelForImageTextToText
from peft import PeftModel
import traceback

from Experiment import Experiment
from schemas import *
import config
import utils
from prompt import build_prompt, build_messages

def load_model():
    if config.HF_TOKEN:
        token_kwargs = {"token": config.HF_TOKEN}
    else:
        token_kwargs = {}
        print("There is no Hugging Face token...")

    print("Loading processor...")
    # fine-tuned output directory usually contains tokenizer/processor files.
    # if it does not, fall back to the base model processor.
    try:
        processor = AutoProcessor.from_pretrained(config.FINETUNED_ADAPTER_DIR, **token_kwargs)
    except Exception:
        processor = AutoProcessor.from_pretrained(config.BASE_MODEL_ID, **token_kwargs)

    print("Loading base model...")
    base_model = AutoModelForImageTextToText.from_pretrained(
        config.BASE_MODEL_ID,
        torch_dtype=utils.get_torch_dtype(config.MODEL_DTYPE),
        device_map="auto",
        **token_kwargs,
    )

    print("Attaching fine-tuned adapter...")
    model = PeftModel.from_pretrained(base_model, config.FINETUNED_ADAPTER_DIR)
    model.eval()

    if hasattr(model.config, "use_cache"):
        model.config.use_cache = True

    print("Fine-tuned model ready.")

    return processor, model

# run the fine-tuned model and return raw generated text
def run_model(prompt: str, img_base64: str | None = None) -> str:
    image = None

    if img_base64 and img_base64.strip():
        image = utils.decode_base64_image(img_base64)

    messages = build_messages(prompt, image)

    text = PROCESSOR.apply_chat_template(
        messages,
        tokenize=False,
        add_generation_prompt=True,
    )

    if image is not None:
        inputs = PROCESSOR(text=text, images=image, return_tensors="pt").to(MODEL.device)
    else:
        inputs = PROCESSOR(text=text, return_tensors="pt").to(MODEL.device)

    with torch.inference_mode():
        outputs = MODEL.generate(
            **inputs,
            max_new_tokens=config.MAX_NEW_TOKENS,
            do_sample=False,
            pad_token_id=PROCESSOR.tokenizer.eos_token_id,
        )

    generated = outputs[0][inputs["input_ids"].shape[1]:]

    return PROCESSOR.decode(
        generated,
        skip_special_tokens=True
    ).strip()


app = FastAPI() # create a FastAPI instance

experiment_logger = Experiment(
    file_name="results_finetuned.csv",
    output_dir=config.OUTPUT_DIR,
    experiment_name=config.EXPERIMENT_NAME,
    model_name=config.MODEL_NAME,
)

PROCESSOR, MODEL = load_model()

@app.post("/next-action")
def ask_next_action(request:screenContextRequest, authorization: str | None = Header(default=None)):
    expected = f"Bearer {config.API_TOKEN}"

    if authorization != expected:
        raise HTTPException(status_code=401, detail="Unauthorized")

    if len(request.userGoal) > 150:
        raise HTTPException(status_code=400, detail="Goal is too long")

    start_time = time.perf_counter()

    visible_uies = utils.collect_uies(request)

    # prompt = build_prompt(request.userGoal, visible_uies, request.imgBase64)
    prompt = build_prompt(request.userGoal, visible_uies)

    try:
        model_response = run_model(prompt=prompt, img_base64=request.imgBase64)
    except Exception as e:
        traceback.print_exc()
        raise HTTPException(
            status_code=502,
            detail=f"Model inference error: {str(e)}"
        )

    try:
        action_json = json.loads(model_response)
    except json.JSONDecodeError:
        print("json decode error...")
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
    target = utils.find_ui(target_label, request.uies)

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
        input_text=action_json.get("inputText", ""),
    )

    return {"response": json.dumps(action_json, ensure_ascii=False)}