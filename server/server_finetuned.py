from fastapi import FastAPI, Header, HTTPException, File, Form, UploadFile
import json
import time
import io
import gc

import torch
from transformers import AutoProcessor, AutoModelForImageTextToText, BitsAndBytesConfig
from peft import PeftModel
import traceback

import base64
import cv2
import numpy as np
import easyocr
from PIL import Image

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

app = FastAPI() # create a FastAPI instance

experiment_logger = Experiment(
    file_name="results_finetuned.csv",
    output_dir=config.OUTPUT_DIR,
    experiment_name=config.EXPERIMENT_NAME,
    model_name=config.FINETUNED_MODEL_NAME,
)

######CREATE MODELS
#OCR_READER_KO = easyocr.Reader(['ko', 'en'], gpu=False)
#OCR_READER_JA = easyocr.Reader(['ja', 'en'], gpu=False)
#OCR_READER_HI = easyocr.Reader(['hi', 'en'], gpu=False)
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

    visible_uies = utils.collect_uies(request)[:30]

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
    print(f"has image: {image_bytes is not None}")
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

@app.post("/text-detection")
async def detect_text(
        image: UploadFile = File(...),
        language: str = Form("ko"),
        authorization: str | None = Header(default=None)
):
    expected = f"Bearer {config.API_TOKEN}"

    if authorization != expected:
        raise HTTPException(status_code=401, detail="Unauthorized")

    if language == "hi":
        ocr_reader = OCR_READER_HI
    elif language == "ja":
        ocr_reader = OCR_READER_JA
    else:
        ocr_reader = OCR_READER_KO

    start_time = time.perf_counter()

    try:
        img_bytes = await image.read()
        np_arr = np.frombuffer(img_bytes, np.uint8)
        cv_image = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)

        if cv_image is None:
            raise HTTPException(status_code=400, detail="Invalid image")

    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Image decode error: {str(e)}")

    try:
        results = ocr_reader.readtext(cv_image)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"OCR error: {str(e)}")

    texts = []

    for box, text, confidence in results:
        xs = [point[0] for point in box]
        ys = [point[1] for point in box]

        texts.append({
            "text": text,
            "confidence": float(confidence),
            "box": {
                "x1": int(min(xs)),
                "y1": int(min(ys)),
                "x2": int(max(xs)),
                "y2": int(max(ys))
            }
        })

    latency = round(time.perf_counter() - start_time, 2)

    print(f"OCR latency: {latency}s")
    print(f"Detected texts: {texts}")

    return {"texts": texts}