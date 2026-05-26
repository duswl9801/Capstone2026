import json
import config

def build_prompt(user_goal, visible_text, image_base64=None):
    allowed_actions = "\n".join(f"- {a}" for a in config.ALLOWED_ACTIONS)
    output_format = json.dumps(config.OUTPUT_FORMAT, indent=2)

    prompt = f"""
You are helping a user navigate a screen.

User goal:
{user_goal}

Visible UI/OCR elements:
{visible_text}

Allowed actions: 
{allowed_actions}

Output format: 
{output_format}

Based on the visible UI/OCR elements, predict the correct next action for the user's goal.
Choose the target only from the given UI/OCR elements. 
When you choose a target UI element, copy its fields exactly from the provided UI element. 
If a selected field is empty, keep it empty. 
Do not choose generic targets such as "View", "Button", "TextView", or empty text. 
If the target is not visible, use a scroll action and leave the target fields and inputText empty. 
Do not invent or assume any button, link, or text that is not visible.
If the user goal asks to search, find, look up, or filter with a specific query to type, consider ACTION_SET_TEXT when editable field is visible. 
Put the query in inputText.
If the user goal asks to find another existing items in a list and no specific text needs to be typed, consider ACTION_SCROLL_ also.
Use ACTION_CLICK only when the target is clickable.
""".strip()

    return prompt

def build_messages(prompt_text, image=None):
    content = []

    if image is not None:
        content.append({"type": "image", "image": image})

    content.append({"type": "text", "text": prompt_text})

    return [
        {
            "role": "user",
            "content": content,
        }
    ]