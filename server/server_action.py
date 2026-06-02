from fastapi import FastAPI, Header, HTTPException, File, Form, UploadFile
import json
import time
import io
import gc
import re
import torch
from transformers import AutoProcessor, AutoModelForImageTextToText, BitsAndBytesConfig
from peft import PeftModel
import traceback
from PIL import Image

from Experiment import Experiment
from schemas import *
import config
import resolver
import utils
from prompt import build_prompt, build_messages

app = FastAPI()

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

    print("MODEL_DTYPE:", config.MODEL_DTYPE)
    print("torch dtype:", utils.get_torch_dtype(config.MODEL_DTYPE))

    print("Loading base model...")
    bnb_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=torch.bfloat16,
        bnb_4bit_use_double_quant=True,
    )

    base_model = AutoModelForImageTextToText.from_pretrained(
        config.BASE_MODEL_ID,
        quantization_config=bnb_config,
        device_map="auto",
        **token_kwargs,
    )

    print("Attaching fine-tuned adapter...")
    model = PeftModel.from_pretrained(base_model, config.FINETUNED_ADAPTER_DIR)
    model.eval()

    print("first param dtype:", next(model.parameters()).dtype)

    if hasattr(model.config, "use_cache"):
        model.config.use_cache = True

    print("Fine-tuned model ready.")

    return processor, model

# run the fine-tuned model and return raw generated text
def run_model(prompt: str, image_bytes: bytes | None = None) -> str:
    image = None
    inputs = None
    outputs = None
    generated = None

    try:
        if image_bytes:
            image = Image.open(io.BytesIO(image_bytes)).convert("RGB")

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
                use_cache=False,
            )

        generated = outputs[0][inputs["input_ids"].shape[1]:]

        result = PROCESSOR.decode(
            generated,
            skip_special_tokens=True
        ).strip()

        return result

    finally:
        # remove request-level tensors
        del image
        del inputs
        del outputs
        del generated

        gc.collect()

        if torch.cuda.is_available():
            torch.cuda.empty_cache()

def parse_model_response(model_response: str) -> dict:
    """
    Parse model output safely.

    If the model output is invalid JSON, do not run fallback parsing.
    Return ACTION_NONE so Android will not execute a random action.
    """
    try:
        parsed = json.loads(model_response)
    except json.JSONDecodeError:
        print("invalid model response. raw response:", model_response)
        return {
            "action": "ACTION_NONE",
            "target_label": "",
            "input_text": "",
            "rawResponse": model_response,
        }

    if not isinstance(parsed, dict):
        print("invalid model response type. parsed:", parsed)
        return {
            "action": "ACTION_NONE",
            "target_label": "",
            "input_text": "",
            "rawResponse": model_response,
        }

    action = (
        parsed.get("action")
        or parsed.get("action_label")
        or parsed.get("action_name")
        or parsed.get("actionName")
        or ""
    )

    if not str(action).strip():
        print("invalid model response: missing action. parsed:", parsed)
        return {
            "action": "ACTION_NONE",
            "target_label": "",
            "input_text": "",
            "rawResponse": model_response,
        }

    return parsed

experiment_logger = Experiment(
    file_name="server_action.csv",
    output_dir=config.OUTPUT_DIR,
    experiment_name=config.EXPERIMENT_NAME,
    model_name=config.FINETUNED_MODEL_NAME,
)

PROCESSOR, MODEL = load_model()

@app.post("/next-action")
async def ask_next_action(
        context: str = Form(...),
        image: Optional[UploadFile] = File(None),
        authorization: str | None = Header(default=None)
):
    expected = f"Bearer {config.API_TOKEN}"

    if authorization != expected:
        raise HTTPException(status_code=401, detail="Unauthorized")

    try:
        context_data = json.loads(context)
        request = screenContextRequest(**context_data)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid context JSON: {str(e)}")

    if len(request.userGoal) > 150:
        raise HTTPException(status_code=400, detail="Goal is too long")

    image_bytes = None
    if image is not None:
        image_bytes = await image.read()

    start_time = time.perf_counter()

    visible_uies = utils.collect_uies(request)

    # prompt = build_prompt(request.userGoal, visible_uies, request.imgBase64)
    prompt = build_prompt(request.userGoal, visible_uies)

    try:
        model_response = run_model(prompt=prompt, image_bytes=image_bytes)
    except Exception as e:
        traceback.print_exc()
        raise HTTPException(
            status_code=502,
            detail=f"Model inference error: {str(e)}"
        )

    action_json = parse_model_response(model_response)

    if action_json.get("action") == "ACTION_NONE":
        fallback_action, fallback_target_label, fallback_input_text, fallback_target = resolver.fallback_from_goal(
            action="ACTION_NONE",
            target_label="",
            input_text="",
            request=request,
        )

        if fallback_target is not None:
            action_json = {
                "action": fallback_action,
                "targetText": fallback_target.text or "",
                "targetContentDescription": fallback_target.contentDescription or "",
                "targetClassName": fallback_target.className or "",
                "inputText": fallback_input_text or "",
            }
        else:
            action_json = resolver.resolve_action(action_json, request)
    else:
        action_json = resolver.resolve_action(action_json, request)

    print(f"user goal: {request.userGoal}")
    print(f"has image: {image_bytes is not None}")
    print(f"raw response: {model_response}")
    print(f"alignment result: {action_json}")

    latency = round(time.perf_counter() - start_time, 2)

    target_text = action_json.get("targetText", "")
    target_desc = action_json.get("targetContentDescription", "")

    if target_desc and target_desc != target_text:
        logged_target = f"{target_text} | {target_desc}"
    else:
        logged_target = target_text

    experiment_logger.write_csv(
        latency=latency,
        user_goal=request.userGoal,
        raw_response=model_response,
        action=action_json.get("action", ""),
        target_text=logged_target,
        input_text=action_json.get("inputText", ""),
    )

    return {"response": json.dumps(action_json, ensure_ascii=False)}