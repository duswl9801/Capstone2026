from pydantic import BaseModel
from typing import List, Optional

class BoundingBox(BaseModel):
    x1: int
    y1: int
    x2: int
    y2: int

class DetectedText(BaseModel):
    text: str
    box: BoundingBox
    confidence: float

class OCRResult(BaseModel):
    detectedTexts: List[DetectedText]

class UIElement(BaseModel):
    text: Optional[str] = None
    contentDescription: Optional[str] = None
    className: Optional[str] = None
    packageName: Optional[str] = None
    clickable: bool = False
    editable: bool = False
    bounds: Optional[str] = None

class OCRRequest(BaseModel):
    #imgBase64: str   -> changed to bytes
    language: str = "ko"

class screenContextRequest(BaseModel):
    uies: List[UIElement]
    texts: OCRResult
    userGoal: str
    #imgBase64: str   -> changed to bytes