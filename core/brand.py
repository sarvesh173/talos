"""Brand configuration loader.
Decouples application brand identity from the underlying core code.
"""

import json
from pathlib import Path
from typing import Any, Dict

_ROOT_DIR = Path(__file__).resolve().parent.parent
_BRAND_FILE = _ROOT_DIR / "brand.json"

_DEFAULT_BRAND = {
    "name": "TALOS",
    "codename": "talos",
    "tagline": "AI-Native Multi-Agent Collaboration Hub & Realtime Communication Engine",
    "version": "0.1.0",
    "author": "sarvesh173",
    "branding": {
        "primaryColor": "#6366f1",
        "surfaceDark": "#0b0f19",
        "surfaceCard": "#131b2e",
        "statusIndicator": "#10b981",
    },
}


def load_brand_config() -> Dict[str, Any]:
    """Load brand configuration from root brand.json."""
    if _BRAND_FILE.exists():
        try:
            with open(_BRAND_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return _DEFAULT_BRAND
    return _DEFAULT_BRAND


BRAND = load_brand_config()
