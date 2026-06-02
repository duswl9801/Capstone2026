import torch
import io
from typing import List
from schemas import UIElement, screenContextRequest
from PIL import Image
import base64
import re


def clean_text(text: str | None) -> str:
    if not text:
        return ""

    text = (
        text.replace("\u2068", "")
        .replace("\u2069", "")
        .replace("\u200e", "")
        .replace("\u200f", "")
        .replace("\u200b", "")
        .replace("\u200c", "")
        .replace("\u200d", "")
        .lower()
    )

    # collapse newlines, tabs, and repeated spaces into one space
    text = re.sub(r"\s+", " ", text)

    # collapse repeated comma separators
    text = re.sub(r"(,\s*){2,}", ", ", text)

    # remove leading/trailing comma and spaces
    text = text.strip(" ,")

    return text.strip()

def collect_uies(request: screenContextRequest) -> str:
    lines = []
    UI_caches = []

    for index, ui in enumerate(request.uies):
        text = clean_text(ui.text)
        desc = clean_text(ui.contentDescription)

        #text = text[:200]
        #desc = desc[:120]

        kind = "editable" if ui.editable else "clickable"

        lines.append(
            f"[{index}] "
            f"text='{text}' "
            #f"contentDescription='{desc}' "
            f"type='{kind}' "
        )

        UI_caches.append(
            f"[{index}] "
            f"text='{text}', "
            f"contentDescription='{desc}', "
            f"className='{ui.className}', "
            f"type='{kind}', "
            f"bounds='{ui.bounds}'"
        )

    """
        for index, detected in enumerate(request.texts.detectedTexts):
        text = clean_text(detected.text)

        if text:
            lines.append(f"OCRText {index}: text='{text}'")
    """

    visible_uies = "\n".join(lines)
    print(visible_uies)

    return visible_uies

def normalize_text(s: str | None) -> str:
    return (s or "").strip().lower()

def find_ui(target_label: str | None, uies: List[UIElement]) -> UIElement | None:
    target = normalize_text(target_label)

    if not target:
        return None

    # 1. exact match: target == text or target == contentDescription
    for ui in uies:
        ui_text = normalize_text(ui.text)
        ui_desc = normalize_text(ui.contentDescription)

        if target == ui_text or target == ui_desc:
            return ui

    # 2. contains match
    for ui in uies:
        ui_text = normalize_text(ui.text)
        ui_desc = normalize_text(ui.contentDescription)

        text_matches = ui_text and (target in ui_text or ui_text in target)
        desc_matches = ui_desc and (target in ui_desc or ui_desc in target)

        if text_matches or desc_matches:
            return ui

    print("TARGET NOT FOUND:", target_label)

    debug_key = target[:5] if len(target) >= 5 else target

    for ui in uies:
        ui_text = normalize_text(ui.text)
        ui_desc = normalize_text(ui.contentDescription)

        if debug_key and (debug_key in ui_text or debug_key in ui_desc):
            print("POSSIBLE UI:", ui)

    return None

def get_torch_dtype(model_dtype):
    if model_dtype == "float16":
        return torch.float16
    if model_dtype == "float32":
        return torch.float32
    return torch.bfloat16

"""
def decode_base64_image(img_base64: str) -> Image.Image:
    #Convert request.imgBase64 to a PIL RGB image.
    if not img_base64:
        raise ValueError("imgBase64 is empty")

    # Support both pure base64 and data URL format.
    if "," in img_base64 and img_base64.strip().startswith("data:"):
        img_base64 = img_base64.split(",", 1)[1]

    image_bytes = base64.b64decode(img_base64)
    return Image.open(io.BytesIO(image_bytes)).convert("RGB")
"""


def decode_image_bytes(image_bytes: bytes) -> Image.Image:
    """Convert uploaded image bytes to a PIL RGB image."""
    if not image_bytes:
        raise ValueError("image_bytes is empty")

    return Image.open(io.BytesIO(image_bytes)).convert("RGB")