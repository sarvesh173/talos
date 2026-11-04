"""Tests for the TALOS agent identity seed/form/palette maths.

Python mirror of AgentIdentityResolver.kt (FNV-1a 64-bit, form rule,
palette rule). Guards determinism and the group/member distinction
without a Gradle build.
"""

MASK64 = (1 << 64) - 1
FNV_OFFSET_BASIS = 14695981039346656037  # 0xCBF29CE484222325
FNV_PRIME = 1099511628211  # 0x100000001B3

PALETTE_IDS = ["amber", "teal", "coral", "violet", "mint", "rose", "sky", "slate"]

KNOWN_IDS = ["opencode", "hermes", "claude-code", "delegate-7f3a", "room-1"]


def seed_for(agent_id: str) -> int:
    h = FNV_OFFSET_BASIS
    for b in agent_id.encode("utf-8"):
        h ^= b
        h = (h * FNV_PRIME) & MASK64
    return h


def preferred_form(agent_id: str, is_group: bool) -> str:
    u = seed_for(agent_id) % 1000
    if is_group:
        if u < 600:
            return "Blob"
        if u < 800:
            return "Orb"
        return "Character"
    if u < 600:
        return "Character"
    if u < 800:
        return "Blob"
    return "Orb"


def palette_for(agent_id: str, is_group: bool, taken=()) -> str:
    """Palette for an identity; a group must not reuse any member's palette.

    `taken` is the set of palettes already spoken for. Walking forward from the
    hash-derived base keeps the result deterministic (same id, same answer) while
    still guaranteeing a group never looks like one of its own members — which is
    the whole point of the group/member distinction.
    """
    base = seed_for(agent_id) % len(PALETTE_IDS)
    if not is_group:
        return PALETTE_IDS[base]
    for i in range(len(PALETTE_IDS)):
        candidate = PALETTE_IDS[(base + i) % len(PALETTE_IDS)]
        if candidate not in taken:
            return candidate
    # Every palette is spoken for: fall back to a derived variant rather than
    # silently colliding with a member.
    return PALETTE_IDS[base] + "-g"


# --- FNV-1a reference vectors (must match Kotlin exactly) ---
def test_fnv1a_reference_vectors():
    assert seed_for("") == 14695981039346656037
    assert seed_for("a") == 12638187200555641996
    assert seed_for("opencode") == 12888187682890144000


def test_fnv1a_reference_vectors_hex():
    assert hex(seed_for("")) == "0xcbf29ce484222325"
    assert hex(seed_for("hermes")) == "0xc9566c140522e003"
    assert hex(seed_for("claude-code")) == "0xd577dee0b3892133"


# --- Determinism ---
def test_same_id_same_seed_across_runs():
    for agent_id in KNOWN_IDS:
        first = seed_for(agent_id)
        assert seed_for(agent_id) == first  # second evaluation, same process
        assert seed_for(agent_id) == first  # third evaluation


def test_same_id_same_form_and_palette():
    for agent_id in KNOWN_IDS:
        assert preferred_form(agent_id, is_group=True) == preferred_form(agent_id, is_group=True)
        assert palette_for(agent_id, is_group=False) == palette_for(agent_id, is_group=False)


def test_different_ids_different_seeds():
    seeds = {seed_for(agent_id) for agent_id in KNOWN_IDS}
    assert len(seeds) == len(KNOWN_IDS)


# --- Group/member distinction ---
def test_group_never_shares_palette_with_members():
    member_palettes = [palette_for(m, is_group=False) for m in KNOWN_IDS]
    group_palette = palette_for("room-1", is_group=True, taken=member_palettes)
    assert group_palette not in member_palettes


def test_group_palette_distinct_even_when_all_palettes_taken():
    taken = list(PALETTE_IDS)  # every palette used by some member
    group_palette = palette_for("room-1", is_group=True, taken=taken)
    assert group_palette not in taken
    assert group_palette.endswith("-g")


def test_form_bias_groups_prefer_blob_members_prefer_character():
    ids = [f"agent-{i}" for i in range(500)]
    group_forms = [preferred_form(a, is_group=True) for a in ids]
    member_forms = [preferred_form(a, is_group=False) for a in ids]
    assert group_forms.count("Blob") > group_forms.count("Character")
    assert member_forms.count("Character") > member_forms.count("Blob")
    # Photo is never auto-assigned
    assert "Photo" not in group_forms + member_forms


def test_group_member_form_differs_for_same_id():
    """Same id resolves to a different form as group vs member (distinct identity)."""
    differing = sum(
        1 for a in KNOWN_IDS if preferred_form(a, True) != preferred_form(a, False)
    )
    assert differing >= 1
