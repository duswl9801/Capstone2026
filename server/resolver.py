"""
extract_model_fields(action_json)
tokenize_for_match(text)
score_ui_match(query, ui)
find_best_ui(target_label, uies)
resolve_action(action_json, request)
"""
import re
from difflib import SequenceMatcher
from typing import Any

from schemas import UIElement, screenContextRequest
from utils import clean_text

# actions that do not need a target UI element
NO_TARGET_ACTIONS = {
    "ACTION_NONE",
    "ACTION_SCROLL_UP",
    "ACTION_SCROLL_DOWN",
    "ACTION_SCROLL_LEFT",
    "ACTION_SCROLL_RIGHT",
    "GLOBAL_ACTION_BACK",
}

# words that describe the task but are usually not the target itself
# example: "Reply to Sid" -> "reply" is task word, "sid" is target word
GOAL_STOPWORDS = {
    "reply", "message", "write", "send", "text", "call", "open", "select",
    "check", "read", "see", "view", "find", "search", "look", "filter",
    "go", "navigate", "click", "tap", "choose", "pick",
    "to", "from", "the", "a", "an", "my", "for", "with", "in", "on", "at",
    "please", "want", "wants", "need", "needs",
}

def normalize_action(action: str | None) -> str:
    action = action or ""
    action = action.strip()

    # training data sometimes used ACTION_TYPE
    if action == "ACTION_TYPE":
        return "ACTION_SET_TEXT"

    return action

"""
    read the model output.

    expected model output:
    {
        "action": "ACTION_CLICK",
        "target_label": "...",
        "input_text": ""
    }

    sometimes the model may use slightly different key names:
    - action_label instead of action
    - inputText instead of input_text
    """
def extract_model_fields(action_json: dict[str, Any]) -> tuple[str, str, str]:
    action = (
            action_json.get("action")
            or action_json.get("action_label")
            or action_json.get("action_name")
            or action_json.get("actionName")
            or ""
    )

    target_label = (
            action_json.get("target_label")
            or action_json.get("targetText")
            or action_json.get("target_text")
            or ""
    )

    input_text = (
            action_json.get("input_text")
            or action_json.get("inputText")
            or ""
    )

    action = normalize_action(action)

    return action, target_label, input_text

"""
Converts text into simple matching tokens.

    Example:
        "sid | may 26 | https://..."
        -> {"sid", "may", "26", "https"}

    This is used for fuzzy matching between the model target_label
    and the real UIElement text.
    """
def tokenize(text: str | None) -> set[str]:

    text = clean_text(text)

    if not text:
        return set()

    # keep English, Korean, and numbers
    tokens = re.findall(r"[a-zA-Z가-힣0-9]+", text)

    result = set()

    for token in tokens:
        token = token.lower().strip()

        # skip tiny noisy tokens
        if len(token) < 2:
            continue

        # simple plural normalization
        # reviews -> review
        if len(token) > 3 and token.endswith("s"):
            token = token[:-1]

        result.add(token)

    return result

def get_ui_text(ui: UIElement) -> str:
    """
    get the searchable text for a UIElement.

    right now the phone already sends merged parent/child text and contentDescription
    as ui.text, so this mostly uses ui.text.
    """
    return clean_text(ui.text or "")

"""
    score how well a UIElement matches the predicted target_label.

    higher score means better match.
    this is CPU-only string matching, so it is cheap.
"""
def score_match(query: str | None, ui: UIElement) -> float:
    query_clean = clean_text(query)
    ui_clean = get_ui_text(ui)

    if not query_clean or not ui_clean:
        return 0.0

    # exact match
    if query_clean == ui_clean:
        return 5.0

    # substring match
    if query_clean in ui_clean:
        return 4.0

    if ui_clean in query_clean:
        return 3.0

    query_tokens = tokenize(query_clean)
    ui_tokens = tokenize(ui_clean)

    if not query_tokens or not ui_tokens:
        return 0.0

    overlap = query_tokens & ui_tokens

    if not overlap:
        # small backup score for similar strings
        return SequenceMatcher(None, query_clean, ui_clean).ratio() * 0.3

    # base score: how much of the query appears in this UI element
    score = len(overlap) / len(query_tokens)

    # bonus for meaningful overlapping tokens
    # example: sid, chris, review, brand names
    for token in overlap:
        if len(token) >= 3:
            score += 0.4

    # small string similarity bonus
    score += SequenceMatcher(None, query_clean, ui_clean).ratio() * 0.2

    return score

def find_best_ui(
    query: str | None,
    uies: list[UIElement],
    min_score: float = 0.45,
) -> tuple[UIElement | None, float]:
    """
    find the best UIElement for a query.

    example:
    model target_label:
        "sid | may not go"

    real UIElement:
        "sid | may 26 | https://depaul.zoom.us/..."

    this should align to the Sid row.
    """
    if not query:
        return None, 0.0

    best_ui = None
    best_score = 0.0

    for ui in uies:
        # only action candidates matter
        if not (ui.clickable or ui.editable):
            continue

        score = score_match(query, ui)

        if score > best_score:
            best_score = score
            best_ui = ui

    print("resolver query:", query)
    print("resolver best score:", best_score)
    print("resolver best ui:", best_ui)

    if best_score >= min_score:
        return best_ui, best_score

    return None, best_score

"""
    extract likely target words from the user goal.

    example:
    "Reply to Sid" -> "sid"
    "Check the review" -> "review"
    "Open Chris message" -> "chris"
    "Reply" -> "reply"
    """
def extract_goal_target_words(user_goal: str | None) -> str:
    tokens = tokenize(user_goal)

    useful_tokens = [
        token for token in tokens
        if token not in GOAL_STOPWORDS and len(token) >= 3
    ]

    return " ".join(useful_tokens)




def resolve_target(
    action: str,
    target_label: str,
    input_text: str,
    user_goal: str,
    uies: list[UIElement],
) -> UIElement | None:
    """
    choose the actual UIElement to return to Android.

    strategy:
    1. use the model's predicted target_label
    2. also check target words from the user goal
    3. if the user goal clearly matches a visible UI row, prefer that row
    """

    # For typing/searching/message goals, prefer editable or input-like UI.
    # Do not align the input text itself to a random suggestion/result.
    if action == "ACTION_SET_TEXT":
        if is_symbolic_input_label(target_label):
            symbolic_target = find_input_like_ui(uies)
            if symbolic_target is not None:
                return symbolic_target

        # If model target label looks like a field label, resolve normally.
        model_target, model_score = find_best_ui(
            query=target_label,
            uies=uies,
            min_score=0.45,
        )

        if model_target is not None:
            return model_target

        # fallback: use best input-like field
        input_target = find_input_like_ui(uies)
        if input_target is not None:
            return input_target

        return None

    if is_symbolic_input_label(target_label):
        symbolic_target = find_input_like_ui(uies)
        if symbolic_target is not None:
            return symbolic_target

    model_target, model_score = find_best_ui(query=target_label, uies=uies, min_score=0.45,)

    goal_query = extract_goal_target_words(user_goal)
    goal_target, goal_score = find_best_ui(
        query=goal_query,
        uies=uies,
        min_score=0.95,
    )

    # if the goal clearly names a visible target, prefer it
    # example: goal has "sid" and UI has "sid | may 26 | ..."
    if goal_target is not None and goal_score >= 0.95:
        return goal_target

    return model_target


def validate_action(
    action: str,
    target: UIElement | None,
    input_text: str,
) -> tuple[str, str]:
    """
    check whether the predicted action is executable.

    current policy:
    - mostly trust the model action
    - only fix ACTION_SET_TEXT when target is not editable
    """
    if action in NO_TARGET_ACTIONS:
        return action, ""

    if target is None:
        return action, input_text

    # important:
    # keep ACTION_SET_TEXT even when the aligned target is clickable but not editable.
    # Android will tap it first and then type into the focused editable field.
    if action == "ACTION_SET_TEXT":
        return action, input_text

    # if the model wants to type but the target is not editable,
    # click it first if possible. the next step can type.
    #if action == "ACTION_SET_TEXT" and not target.editable:
    #    if target.clickable:
    #        return "ACTION_CLICK", ""
    #    return "", ""

    return action, input_text


def resolve_action(
    action_json: dict[str, Any],
    request: screenContextRequest,
) -> dict[str, str]:
    """
    main resolver function.

    input from model:
    {
        "action": "ACTION_CLICK",
        "target_label": "sid | may not go",
        "input_text": ""
    }

    output to Android:
    {
        "action": "ACTION_CLICK",
        "targetText": "sid | may 26 | https://...",
        "targetContentDescription": "",
        "targetClassName": "android.widget.RelativeLayout",
        "inputText": ""
    }
    """
    action, target_label, input_text = extract_model_fields(action_json)

    if action == "ACTION_CLICK" and input_text.strip():
        goal = clean_text(request.userGoal or "")
        if any(word in goal for word in ["write", "type", "text", "message", "reply", "search", "find", "look up"]):
            print("resolver override: ACTION_CLICK with input_text -> ACTION_SET_TEXT")
            action = "ACTION_SET_TEXT"

    if action == "ACTION_NONE":
        return {
            "action": "ACTION_NONE",
            "targetText": "",
            "targetContentDescription": "",
            "targetClassName": "",
            "inputText": "",
        }

    target = resolve_target(
        action=action,
        target_label=target_label,
        input_text=input_text,
        user_goal=request.userGoal,
        uies=request.uies,
    )

    action, input_text = validate_action(
        action=action,
        target=target,
        input_text=input_text,
    )

    if target:
        return {
            "action": action,
            "targetText": target.text or "",
            "targetContentDescription": target.contentDescription or "",
            "targetClassName": target.className or "",
            "inputText": input_text or "",
        }

    # fallback when no matching UIElement was found
    return {
        "action": action,
        "targetText": target_label or "",
        "targetContentDescription": "",
        "targetClassName": "",
        "inputText": input_text or "",
    }

def is_symbolic_input_label(label: str | None) -> bool:
    label = clean_text(label)

    return label in {
        "search_text_field",
        "message_text_field",
        "input_field",
        "text_field",
        "search_field",
    }


def find_input_like_ui(uies: list[UIElement]) -> UIElement | None:
    # first, prefer real editable fields
    for ui in uies:
        if ui.editable:
            return ui

    # if no editable field is visible yet, click a search/message/input-like field first
    input_keywords = {
        "search",
        "search or ask",
        "search or url",
        "ask a question",
        "message",
        "text message",
        "enter message",
        "type",
        "compose",
        "write",
        "reply",
    }
    for ui in uies:
        if not ui.clickable:
            continue

        label = get_ui_text(ui)

        if any(keyword in label for keyword in input_keywords):
            return ui

    return None

def extract_search_query_from_goal(user_goal: str | None) -> str:
    text = clean_text(user_goal)

    if not text:
        return ""

    search_words = ["search", "find", "look up", "filter"]

    for word in search_words:
        if text.startswith(word + " "):
            return text[len(word):].strip()

    return ""

def extract_write_text_from_goal(user_goal: str | None) -> str:
    text = clean_text(user_goal)

    if not text:
        return ""

    write_words = ["write", "type", "text", "message", "reply"]

    for word in write_words:
        if text.startswith(word + " "):
            return text[len(word):].strip()

    return ""


def fallback_from_goal(
    action: str,
    target_label: str,
    input_text: str,
    request: screenContextRequest,
) -> tuple[str, str, str, UIElement | None]:

    # fallback for writing / typing / replying
    write_text = extract_write_text_from_goal(request.userGoal)

    if write_text:
        target = find_input_like_ui(request.uies)

        if target is not None:
            return "ACTION_SET_TEXT", "", write_text, target

    # fallback for search
    search_query = extract_search_query_from_goal(request.userGoal)

    if search_query:
        target = find_input_like_ui(request.uies)

        if target is not None:
            return "ACTION_SET_TEXT", "", search_query, target

    return action, target_label, input_text, None