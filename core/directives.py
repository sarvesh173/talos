"""Parser for the SYSTEM.md ``:::talos`` rich-directive protocol.

A model reply may contain at most three fenced directive blocks, each a
single JSON object. This module extracts those blocks, validates them
against the contract in SYSTEM.md, and returns typed directives in order
with the surrounding prose preserved. Malformed or invalid blocks are
dropped silently (SYSTEM.md section 1) — they never break the prose
around them, and an unclosed fence is treated as plain prose so no text
is ever lost.

Pure Python, standard library only, no I/O.
"""

from __future__ import annotations

import json
import re
from dataclasses import asdict, dataclass, field
from typing import Any, Dict, List, Optional, Union

#: Maximum directive blocks per reply (SYSTEM.md section 5).
MAX_BLOCKS_PER_REPLY = 3

#: Maximum permission blocks per reply (SYSTEM.md section 5).
MAX_PERMISSION_BLOCKS_PER_REPLY = 1

DIRECTIVE_TYPES = ("chart", "select", "confirm", "permission", "game", "emoji", "cards")

CHART_KINDS = ("bar", "line", "area")
GAME_KINDS = ("rock-paper-scissors", "tic-tac-toe", "guess-number")
PERMISSION_SCOPES = ("once", "session", "full")
CARD_ACCENTS = ("blue", "green", "orange", "red", "purple", "gray")
CONFIRM_DEFAULTS = ("confirm", "deny")

#: deadline_seconds bounds for ``confirm`` (SYSTEM.md section 2.3).
CONFIRM_MIN_DEADLINE = 15
CONFIRM_MAX_DEADLINE = 600

_FENCE_RE = re.compile(r":::talos[ \t]*\r?\n?(.*?)\r?\n?:::", re.DOTALL)


# ---------------------------------------------------------------------------
# Typed directives
# ---------------------------------------------------------------------------


@dataclass
class ChartSeries:
    name: str
    values: List[Optional[float]]

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class ChartDirective:
    type: str = "chart"
    kind: str = "bar"
    title: str = ""
    x: List[str] = field(default_factory=list)
    series: List[ChartSeries] = field(default_factory=list)
    unit: str = ""

    def to_dict(self) -> Dict[str, Any]:
        data = asdict(self)
        data["series"] = [s.to_dict() for s in self.series]
        return data


@dataclass
class SelectOption:
    id: str
    label: str
    hint: str = ""
    checked: bool = False

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class SelectDirective:
    type: str = "select"
    multi: bool = False
    title: str = ""
    options: List[SelectOption] = field(default_factory=list)
    submit_label: str = "Submit"

    def to_dict(self) -> Dict[str, Any]:
        data = asdict(self)
        data["options"] = [o.to_dict() for o in self.options]
        return data


@dataclass
class ConfirmDirective:
    type: str = "confirm"
    title: str = ""
    body: str = ""
    confirm_label: str = "Confirm"
    deny_label: str = "Deny"
    deadline_seconds: Optional[int] = None
    default_on_timeout: str = "deny"

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class PermissionDirective:
    type: str = "permission"
    tool: str = ""
    command: str = ""
    reason: str = ""
    scopes: List[str] = field(default_factory=lambda: ["once", "session"])

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class GameDirective:
    type: str = "game"
    game: str = ""
    title: str = ""
    prompt: str = ""
    state: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class EmojiDirective:
    type: str = "emoji"
    title: str = ""
    options: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class CardItem:
    title: str
    subtitle: str = ""
    badge: str = ""
    accent: str = "gray"

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class CardsDirective:
    type: str = "cards"
    title: str = ""
    items: List[CardItem] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        data = asdict(self)
        data["items"] = [i.to_dict() for i in self.items]
        return data


Directive = Union[
    ChartDirective,
    SelectDirective,
    ConfirmDirective,
    PermissionDirective,
    GameDirective,
    EmojiDirective,
    CardsDirective,
]


@dataclass
class Segment:
    """One piece of a parsed reply: prose or a typed directive, in order."""

    kind: str  # "prose" | "directive"
    text: str = ""
    directive: Optional[Directive] = None

    def to_dict(self) -> Dict[str, Any]:
        data: Dict[str, Any] = {"kind": self.kind}
        if self.kind == "prose":
            data["text"] = self.text
        else:
            data["directive"] = self.directive.to_dict() if self.directive else None
        return data


@dataclass
class ParsedReply:
    """Result of parsing one model reply."""

    segments: List[Segment] = field(default_factory=list)

    @property
    def directives(self) -> List[Directive]:
        return [s.directive for s in self.segments if s.kind == "directive" and s.directive]

    @property
    def prose(self) -> str:
        """All prose segments joined, fences removed."""
        return "\n".join(s.text for s in self.segments if s.kind == "prose").strip()

    def to_dict(self) -> Dict[str, Any]:
        return {
            "segments": [s.to_dict() for s in self.segments],
            "directives": [d.to_dict() for d in self.directives],
            "prose": self.prose,
        }


# ---------------------------------------------------------------------------
# Validation helpers — each returns a typed directive or None (drop the block)
# ---------------------------------------------------------------------------


def _is_number(value: Any) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def _opt_str(data: Dict[str, Any], key: str, default: str = "") -> Optional[str]:
    value = data.get(key, default)
    if value is None:
        return default
    return value if isinstance(value, str) else None


def _validate_chart(data: Dict[str, Any]) -> Optional[ChartDirective]:
    kind = data.get("kind")
    if kind not in CHART_KINDS:
        return None
    x = data.get("x")
    if not isinstance(x, list) or not (1 <= len(x) <= 12):
        return None
    if not all(isinstance(label, str) for label in x):
        return None
    series_raw = data.get("series")
    if not isinstance(series_raw, list) or not (1 <= len(series_raw) <= 4):
        return None
    series: List[ChartSeries] = []
    for entry in series_raw:
        if not isinstance(entry, dict):
            return None
        values = entry.get("values")
        if not isinstance(values, list) or len(values) != len(x):
            return None
        if not all(v is None or _is_number(v) for v in values):
            return None
        name = entry.get("name", "")
        if not isinstance(name, str):
            return None
        series.append(ChartSeries(name=name, values=list(values)))
    title = _opt_str(data, "title")
    unit = _opt_str(data, "unit")
    if title is None or unit is None:
        return None
    return ChartDirective(kind=kind, title=title, x=list(x), series=series, unit=unit)


def _validate_select(data: Dict[str, Any]) -> Optional[SelectDirective]:
    options_raw = data.get("options")
    if not isinstance(options_raw, list) or not (2 <= len(options_raw) <= 8):
        return None
    options: List[SelectOption] = []
    for entry in options_raw:
        if not isinstance(entry, dict):
            return None
        opt_id = entry.get("id")
        label = entry.get("label")
        if not isinstance(opt_id, str) or not opt_id:
            return None
        if not isinstance(label, str) or not label:
            return None
        hint = entry.get("hint", "")
        if not isinstance(hint, str):
            return None
        checked = entry.get("checked", False)
        if not isinstance(checked, bool):
            return None
        options.append(SelectOption(id=opt_id, label=label, hint=hint, checked=checked))
    multi = data.get("multi", False)
    if not isinstance(multi, bool):
        return None
    title = _opt_str(data, "title")
    submit = _opt_str(data, "submit_label", "Submit")
    if title is None or submit is None:
        return None
    return SelectDirective(multi=multi, title=title, options=options, submit_label=submit)


def _validate_confirm(data: Dict[str, Any]) -> Optional[ConfirmDirective]:
    title = data.get("title")
    if not isinstance(title, str) or not title.strip():
        return None
    default = data.get("default_on_timeout")
    if default not in CONFIRM_DEFAULTS:
        return None
    deadline = data.get("deadline_seconds")
    if deadline is not None:
        if not isinstance(deadline, int) or isinstance(deadline, bool):
            return None
        if not (CONFIRM_MIN_DEADLINE <= deadline <= CONFIRM_MAX_DEADLINE):
            return None
    body = _opt_str(data, "body")
    confirm_label = _opt_str(data, "confirm_label", "Confirm")
    deny_label = _opt_str(data, "deny_label", "Deny")
    if body is None or confirm_label is None or deny_label is None:
        return None
    return ConfirmDirective(
        title=title,
        body=body,
        confirm_label=confirm_label,
        deny_label=deny_label,
        deadline_seconds=deadline,
        default_on_timeout=default,
    )


def _validate_permission(data: Dict[str, Any]) -> Optional[PermissionDirective]:
    tool = data.get("tool")
    if not isinstance(tool, str) or not tool.strip():
        return None
    scopes_raw = data.get("scopes", ["once", "session"])
    if not isinstance(scopes_raw, list) or not scopes_raw:
        return None
    if not all(isinstance(s, str) and s in PERMISSION_SCOPES for s in scopes_raw):
        return None
    command = _opt_str(data, "command")
    reason = _opt_str(data, "reason")
    if command is None or reason is None:
        return None
    return PermissionDirective(
        tool=tool, command=command, reason=reason, scopes=list(scopes_raw)
    )


def _validate_game(data: Dict[str, Any]) -> Optional[GameDirective]:
    game = data.get("game")
    if game not in GAME_KINDS:
        return None
    state = data.get("state", {})
    if not isinstance(state, dict):
        return None
    title = _opt_str(data, "title")
    prompt = _opt_str(data, "prompt")
    if title is None or prompt is None:
        return None
    return GameDirective(game=game, title=title, prompt=prompt, state=dict(state))


def _validate_emoji(data: Dict[str, Any]) -> Optional[EmojiDirective]:
    options = data.get("options")
    if not isinstance(options, list) or not (4 <= len(options) <= 8):
        return None
    if not all(isinstance(o, str) and o.strip() for o in options):
        return None
    title = _opt_str(data, "title")
    if title is None:
        return None
    return EmojiDirective(title=title, options=list(options))


def _validate_cards(data: Dict[str, Any]) -> Optional[CardsDirective]:
    items_raw = data.get("items")
    if not isinstance(items_raw, list) or not (2 <= len(items_raw) <= 6):
        return None
    items: List[CardItem] = []
    for entry in items_raw:
        if not isinstance(entry, dict):
            return None
        title = entry.get("title")
        if not isinstance(title, str) or not title.strip():
            return None
        subtitle = entry.get("subtitle", "")
        badge = entry.get("badge", "")
        accent = entry.get("accent", "gray")
        if not isinstance(subtitle, str) or not isinstance(badge, str):
            return None
        if badge and len(badge.split()) != 1:
            return None
        if accent not in CARD_ACCENTS:
            return None
        items.append(CardItem(title=title, subtitle=subtitle, badge=badge, accent=accent))
    title = _opt_str(data, "title")
    if title is None:
        return None
    return CardsDirective(title=title, items=items)


_VALIDATORS = {
    "chart": _validate_chart,
    "select": _validate_select,
    "confirm": _validate_confirm,
    "permission": _validate_permission,
    "game": _validate_game,
    "emoji": _validate_emoji,
    "cards": _validate_cards,
}


def validate_directive(data: Any) -> Optional[Directive]:
    """Validate one decoded JSON value into a typed directive, or None."""
    if not isinstance(data, dict):
        return None
    dtype = data.get("type")
    validator = _VALIDATORS.get(dtype) if isinstance(dtype, str) else None
    if validator is None:
        return None
    try:
        return validator(data)
    except Exception:
        return None


# ---------------------------------------------------------------------------
# Parsing
# ---------------------------------------------------------------------------


def parse_directives(text: Optional[str]) -> ParsedReply:
    """Parse ``:::talos`` blocks out of a model reply.

    Valid blocks become typed directives in order; malformed JSON, unknown
    types, and contract violations are dropped without disturbing the
    surrounding prose. At most three blocks (and one ``permission``) are
    kept per reply. An unclosed fence is left as prose.
    """
    if not text:
        return ParsedReply(segments=[Segment(kind="prose", text="")] if text == "" else [])
    parsed = ParsedReply()
    pos = 0
    kept = 0
    permissions = 0
    for match in _FENCE_RE.finditer(text):
        head = text[pos : match.start()]
        if head:
            parsed.segments.append(Segment(kind="prose", text=head))
        body = match.group(1).strip()
        pos = match.end()
        if kept >= MAX_BLOCKS_PER_REPLY:
            continue
        try:
            data = json.loads(body) if body else None
        except (ValueError, TypeError):
            continue  # malformed JSON: drop the block, keep the prose
        directive = validate_directive(data)
        if directive is None:
            continue
        if isinstance(directive, PermissionDirective):
            if permissions >= MAX_PERMISSION_BLOCKS_PER_REPLY:
                continue
            permissions += 1
        parsed.segments.append(Segment(kind="directive", directive=directive))
        kept += 1
    tail = text[pos:]
    if tail:
        parsed.segments.append(Segment(kind="prose", text=tail))
    if not parsed.segments:
        parsed.segments.append(Segment(kind="prose", text=text))
    return parsed


def parse_reply(text: Optional[str]) -> ParsedReply:
    """Alias for :func:`parse_directives` (readability at call sites)."""
    return parse_directives(text)


def extract_directives(text: Optional[str]) -> List[Directive]:
    """Convenience: just the typed directives from a reply, in order."""
    return parse_directives(text).directives
