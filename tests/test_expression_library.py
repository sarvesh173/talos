"""Mirror of ExpressionLibrary.kt generation + selection maths.

Scheme: 10 families x 10 eyes x 5 mouths = 500, id = fam*50 + eye*5 + mouth.
Selection: window = tick // 8; base = fnv1a(agentId) % 50;
offset = (base + window*3) % 50; id = family_ordinal*50 + offset.
"""
import pytest

FAMILIES = ["Calm", "Focused", "Curious", "Delighted", "Confused",
            "Struggling", "Relieved", "Alert", "Sleepy", "Celebrating"]
EYE_COUNT, MOUTH_COUNT, PER_FAMILY, TOTAL = 10, 5, 50, 500
TICK_WINDOW, STEP = 8, 3

# Mirror of ExpressionLibrary.familyForState for every BotState.
FAMILY_FOR_STATE = {
    "Idle": "Calm", "Orbit": "Calm", "Radar": "Calm", "Humming": "Calm",
    "Thinking": "Focused", "Searching": "Focused", "Working": "Focused",
    "Writing": "Focused", "Loading": "Focused", "Progress": "Focused",
    "Curious": "Curious", "Listening": "Curious", "Dictating": "Curious",
    "Spawning": "Curious",
    "Happy": "Delighted", "Excited": "Delighted", "Laughing": "Delighted",
    "Playful": "Delighted", "Proud": "Delighted", "Bouncing": "Delighted",
    "Shy": "Delighted",
    "Confused": "Confused", "Suspicious": "Confused", "Bored": "Confused",
    "Angry": "Struggling", "Dragging": "Struggling", "Sad": "Struggling",
    "Waking": "Relieved", "Receiving": "Relieved", "Sending": "Relieved",
    "Uploading": "Relieved", "Notifying": "Relieved",
    "Alerting": "Alert", "Scared": "Alert", "Surprised": "Alert",
    "Sleeping": "Sleepy", "Drowsy": "Sleepy", "PoweringDown": "Sleepy",
    "Celebrate": "Celebrating",
}

FAMILY_FOR_TRIGGER = {
    "ThinkingStarted": "Focused", "ToolCall": "Curious", "Success": "Delighted",
    "Error": "Struggling", "DelegateSpawned": "Alert", "IdleTooLong": "Sleepy",
}

# Distinct per-state param tuples mirrored from the Kotlin tables (injectivity
# witness: eye tuple depends only on eyeIndex, mouth only on mouthIndex,
# motion/aura only on family ordinal).
EYE_STATES = [
    (100, 120, 34, 56, 160, 120, 34, 56, 1.0),
    (100, 124, 36, 40, 160, 124, 36, 40, 0.72),
    (100, 128, 38, 22, 160, 128, 38, 22, 0.38),
    (100, 116, 30, 66, 160, 116, 30, 66, 1.0),
    (100, 132, 40, 16, 160, 132, 40, 16, 0.24),
    (102, 120, 32, 50, 158, 118, 32, 50, 0.92),
    (96, 116, 30, 52, 164, 120, 30, 52, 0.96),
    (100, 126, 40, 28, 160, 126, 40, 28, 0.5),
    (100, 114, 28, 60, 160, 114, 28, 60, 1.0),
    (100, 122, 34, 48, 160, 122, 34, 48, 0.84),
]
MOUTH_STATES = [
    (44, 176, 0.2, 0.55, 0), (36, 178, 0.85, 0.1, 14), (40, 180, 0.08, 0.0, 0),
    (42, 182, 0.15, -0.5, 0), (20, 180, 0.45, 0.2, 6),
]
FAMILY_MOTION = [
    (1.2, 0.4, 1.5, 0.5, 1.0), (0.6, 0.3, -6, 0.8, 0.7), (1.8, 0.9, 8, 1.2, 1.1),
    (3.2, 0.7, 3, 1.6, 1.3), (1.0, 1.2, 12, 0.9, 0.9), (2.4, 0.5, 4, 2.4, 1.2),
    (1.4, 0.5, 0, 0.7, 0.9), (2.8, 0.6, -3, 2.8, 1.4), (0.4, 0.2, 2.5, 0.3, 0.8),
    (4.2, 1.0, 5, 2.2, 1.5),
]
FAMILY_AURA = [
    (0.1, 0.05, 0, 0.0), (0.25, 0.15, 1, 0.35), (0.3, 0.2, 1, 0.4),
    (0.45, 0.3, 1, 0.55), (0.35, 0.25, 1, 0.45), (0.5, 0.6, 1, 0.6),
    (0.2, 0.1, 0, 0.25), (0.6, 0.7, 2, 0.7), (0.08, 0.04, 0, 0.15),
    (0.7, 0.5, 2, 0.8),
]


def all_expressions():
    out = []
    for fi, fam in enumerate(FAMILIES):
        for e in range(EYE_COUNT):
            for m in range(MOUTH_COUNT):
                eid = fi * PER_FAMILY + e * MOUTH_COUNT + m
                tup = (FAMILY_MOTION[fi], FAMILY_AURA[fi], EYE_STATES[e], MOUTH_STATES[m])
                out.append((eid, fam, tup))
    return out


def agent_hash(agent_id: str) -> int:
    h = 0x811C9DC5
    for ch in agent_id:
        h = ((h ^ ord(ch)) * 0x01000193) & 0xFFFFFFFF
    # Mirror Java/Kotlin Int 32-bit wrap into signed range.
    return h - 0x100000000 if h >= 0x80000000 else h


def for_agent(agent_id: str, state: str, tick: int) -> int:
    fam = FAMILIES.index(FAMILY_FOR_STATE[state])
    base = agent_hash(agent_id) % PER_FAMILY  # Python % == Math.floorMod for positive modulus
    window = tick // TICK_WINDOW  # floor division matches Math.floorDiv
    offset = (base + window * STEP) % PER_FAMILY
    return fam * PER_FAMILY + offset


def for_trigger(trigger: str, agent_id: str) -> int:
    fam = FAMILIES.index(FAMILY_FOR_TRIGGER[trigger])
    return fam * PER_FAMILY + (agent_hash(agent_id) % PER_FAMILY)


# (a) exactly 500 unique ids
def test_exactly_500_unique_ids():
    exprs = all_expressions()
    assert len(exprs) == 500
    assert len({e[0] for e in exprs}) == 500
    assert sorted(e[0] for e in exprs) == list(range(500))


# (b) no duplicate parameter tuples
def test_no_duplicate_parameter_tuples():
    exprs = all_expressions()
    assert len({e[2] for e in exprs}) == 500


# (c) every family represented, 50 each
def test_every_family_represented():
    exprs = all_expressions()
    from collections import Counter
    counts = Counter(e[1] for e in exprs)
    assert set(counts) == set(FAMILIES)
    assert all(v == 50 for v in counts.values())
    # Every BotState maps into the family set and all families reachable.
    assert set(FAMILY_FOR_STATE.values()) == set(FAMILIES)
    assert len(FAMILY_FOR_STATE) == 39  # all BotState entries mapped


# (d) selection deterministic: same inputs, same expression
def test_selection_deterministic():
    assert for_agent("agent-1", "Thinking", 100) == for_agent("agent-1", "Thinking", 100)
    assert for_agent("agent-1", "Thinking", 100) == for_agent("agent-1", "Thinking", 103)
    assert for_trigger("ToolCall", "agent-1") == for_trigger("ToolCall", "agent-1")


# Spread: selection covers the whole library, not one family cluster.
def test_selection_spread_across_library():
    seen = set()
    for state in FAMILY_FOR_STATE:
        for agent in ("a", "b", "c", "d", "e", "f", "g", "h"):
            for tick in range(0, 400, 8):
                seen.add(for_agent(agent, state, tick))
    fams_seen = {FAMILIES[i // 50] for i in seen}
    assert len(fams_seen) >= 10          # all families reachable
    assert len(seen) >= 400              # majority of the 500 actually selected


# (e) single agent over 200 ticks: >= 20 distinct, bounded adjacent change
def test_single_agent_diversity_and_bounded_change():
    ids = [for_agent("solo", "Thinking", t) for t in range(200)]
    assert len(set(ids)) >= 20
    for a, b in zip(ids, ids[1:]):
        if a != b:
            d = (b - a) % PER_FAMILY  # cyclic distance within the family block
            assert d == STEP or d == PER_FAMILY - STEP or abs(b - a) == STEP or abs(b - a) == PER_FAMILY - STEP


# (f) six simultaneous agents are not all the same expression
def test_six_agents_distinct():
    ids = {for_agent(f"agent-{i}", "Thinking", 42) for i in range(6)}
    assert len(ids) > 1
    trig = {for_trigger("DelegateSpawned", f"agent-{i}") for i in range(6)}
    assert len(trig) > 1
