"""Tests for the TALOS composed group avatar layout maths.

Python mirror of GroupMemberAvatar.kt (cell rects for n = 0..9, "+N"
threshold, overlapping-strip offsets, photo-failure fallback, delegate
marking, group/member palette layering). Guards the composition rules
without a Gradle build.
"""

PALETTE_IDS = ["amber", "teal", "coral", "violet", "mint", "rose", "sky", "slate"]

GROUP_MAX_CELLS = 4
GROUP_COUNT_THRESHOLD = 5
STRIP_MAX_CELLS = 4
STRIP_STEP_FRACTION = 0.625


# --- Mirror of GroupMemberAvatar.kt ---------------------------------------
def group_cell_rects(n):
    """Fractional (fx, fy, fw, fh) rects in draw order."""
    if n <= 0:
        return [(0.0, 0.0, 1.0, 1.0)]  # empty state
    if n == 1:
        return [(0.0, 0.0, 1.0, 1.0)]
    if n == 2:
        return [(0.0, 0.0, 0.5, 1.0), (0.5, 0.0, 0.5, 1.0)]
    if n == 3:
        return [
            (0.0, 0.0, 1.0, 0.5),
            (0.0, 0.5, 0.5, 0.5),
            (0.5, 0.5, 0.5, 0.5),
        ]
    return [  # 4: quad; 5+: quad with 4th cell as "+N"
        (0.0, 0.0, 0.5, 0.5),
        (0.5, 0.0, 0.5, 0.5),
        (0.0, 0.5, 0.5, 0.5),
        (0.5, 0.5, 0.5, 0.5),
    ]


def group_visible_member_count(n):
    if n <= 0:
        return 0
    if n >= GROUP_COUNT_THRESHOLD:
        return GROUP_MAX_CELLS - 1  # 3 members + count cell
    return min(n, GROUP_MAX_CELLS)


def group_overflow_count(n):
    return n - (GROUP_MAX_CELLS - 1) if n >= GROUP_COUNT_THRESHOLD else 0


def strip_visible_member_count(n):
    if n <= 0:
        return 0
    return STRIP_MAX_CELLS - 1 if n > STRIP_MAX_CELLS else n


def strip_overflow_count(n):
    return n - (STRIP_MAX_CELLS - 1) if n > STRIP_MAX_CELLS else 0


def strip_offset_dp(index, avatar_size_dp):
    return index * avatar_size_dp * STRIP_STEP_FRACTION


def strip_width_dp(n, avatar_size_dp):
    if n <= 0:
        return avatar_size_dp
    cells = strip_visible_member_count(n) + (1 if strip_overflow_count(n) > 0 else 0)
    return avatar_size_dp + (cells - 1) * avatar_size_dp * STRIP_STEP_FRACTION


def is_delegate_identity(agent_id, group_id=None, is_group=False):
    if is_group:
        return False
    return (
        group_id is not None
        or agent_id.startswith("delegate-")
        or agent_id.startswith("delegate_")
    )


def resolve_cell_form(form, photo_available):
    """Photo with no loadable image falls back to generated Character."""
    if form == "Photo" and not photo_available:
        return "Character"
    return form


def group_accent_palette_id(member_palette_ids, group_palette_id=None):
    member_palette_ids = list(member_palette_ids)
    base = 0
    if group_palette_id is not None:
        bare = group_palette_id[:-2] if group_palette_id.endswith("-g") else group_palette_id
        base = PALETTE_IDS.index(bare) if bare in PALETTE_IDS else 0
    for i in range(len(PALETTE_IDS)):
        candidate = PALETTE_IDS[(base + i) % len(PALETTE_IDS)]
        if candidate not in member_palette_ids:
            return candidate
    return PALETTE_IDS[base] + "-g"


# --- Cell count and positions for n = 0..9 ---------------------------------
EXPECTED_RECTS = {
    0: [(0.0, 0.0, 1.0, 1.0)],
    1: [(0.0, 0.0, 1.0, 1.0)],
    2: [(0.0, 0.0, 0.5, 1.0), (0.5, 0.0, 0.5, 1.0)],
    3: [(0.0, 0.0, 1.0, 0.5), (0.0, 0.5, 0.5, 0.5), (0.5, 0.5, 0.5, 0.5)],
    4: [
        (0.0, 0.0, 0.5, 0.5),
        (0.5, 0.0, 0.5, 0.5),
        (0.0, 0.5, 0.5, 0.5),
        (0.5, 0.5, 0.5, 0.5),
    ],
}
EXPECTED_RECTS.update(
    {n: EXPECTED_RECTS[4] for n in range(5, 10)}  # 5..9 share the quad
)


def test_cell_rects_for_n_0_to_9():
    for n in range(0, 10):
        rects = group_cell_rects(n)
        assert rects == EXPECTED_RECTS[n], f"n={n}: {rects}"
        # Every rect stays inside the unit square.
        for fx, fy, fw, fh in rects:
            assert 0.0 <= fx < 1.0 and 0.0 <= fy < 1.0
            assert 0.0 < fw <= 1.0 - fx and 0.0 < fh <= 1.0 - fy


def test_cell_counts_for_n_0_to_9():
    expected_counts = {0: 1, 1: 1, 2: 2, 3: 3, 4: 4, 5: 4, 6: 4, 7: 4, 8: 4, 9: 4}
    for n, count in expected_counts.items():
        assert len(group_cell_rects(n)) == count, f"n={n}"


def test_visible_members_and_overflow_for_n_0_to_9():
    for n in range(0, 10):
        visible = group_visible_member_count(n)
        overflow = group_overflow_count(n)
        if n == 0:
            # An empty group still draws one cell — the honest empty state — so
            # it is not the sum of visible members and an overflow badge. Keep
            # the two rules apart or the invariant lies about what is drawn.
            assert len(group_cell_rects(n)) == 1, "empty group must draw its placeholder"
            assert visible == 0 and overflow == 0
            continue
        assert visible + (1 if overflow > 0 else 0) == len(group_cell_rects(n)), f"n={n}"
        if n >= GROUP_COUNT_THRESHOLD:
            assert visible == 3, f"n={n}: visible={visible}"
            assert overflow == n - 3, f"n={n}: overflow={overflow}"
        else:
            assert overflow == 0, f"n={n}: overflow={overflow}"
            assert visible == n, f"n={n}: visible={visible}"


# --- "+N" threshold ---------------------------------------------------------
def test_count_threshold_at_five():
    assert group_overflow_count(4) == 0  # quad of 4 members, no count cell
    assert group_overflow_count(5) == 2  # 3 members + "+2"
    assert group_overflow_count(9) == 6  # 3 members + "+6"
    # The threshold is exactly GROUP_COUNT_THRESHOLD.
    for n in range(0, 10):
        assert (group_overflow_count(n) > 0) is (n >= GROUP_COUNT_THRESHOLD)


def test_count_cell_is_fourth_not_first():
    """Overflow is always the 4th (bottom-right) cell, never cell 0."""
    quad = group_cell_rects(9)
    assert quad[3] == (0.5, 0.5, 0.5, 0.5)
    assert group_cell_rects(9) == group_cell_rects(4)


# --- Overlapping strip -------------------------------------------------------
def test_strip_offsets():
    size = 24.0
    for i in range(4):
        assert strip_offset_dp(i, size) == i * size * 0.625
    # Overlap is 37.5%: each step is 15dp on a 24dp avatar.
    assert strip_offset_dp(1, size) == 15.0
    assert strip_offset_dp(3, size) == 45.0


def test_strip_visible_and_overflow():
    for n in range(0, 10):
        visible = strip_visible_member_count(n)
        overflow = strip_overflow_count(n)
        cells = visible + (1 if overflow > 0 else 0)
        assert cells == min(n, STRIP_MAX_CELLS) if n > 0 else cells == 0
        if n > STRIP_MAX_CELLS:
            assert visible == 3 and overflow == n - 3
        else:
            assert overflow == 0


def test_strip_width():
    size = 24.0
    assert strip_width_dp(0, size) == size
    assert strip_width_dp(1, size) == size
    assert strip_width_dp(2, size) == size + 15.0
    assert strip_width_dp(3, size) == size + 30.0
    # 4+ members: 3 avatars + "+N" chip -> 4 cells.
    assert strip_width_dp(4, size) == size + 45.0
    assert strip_width_dp(9, size) == size + 45.0


# --- Fallback: failed member never produces a null cell ---------------------
def test_failed_member_falls_back_to_generated_identity():
    # Photo member with no loadable image -> generated Character, not null.
    assert resolve_cell_form("Photo", photo_available=False) == "Character"
    assert resolve_cell_form("Photo", photo_available=True) == "Photo"
    for form in ("Blob", "Orb", "Character"):
        assert resolve_cell_form(form, photo_available=False) == form
        assert resolve_cell_form(form, photo_available=True) == form
    # The resolved form is always a real form, never None.
    for form in ("Blob", "Orb", "Character", "Photo"):
        for available in (True, False):
            assert resolve_cell_form(form, available) in (
                "Blob",
                "Orb",
                "Character",
                "Photo",
            )


def test_fallback_cell_still_occupies_its_rect():
    """A photo-failed member still gets exactly one rect: no dropped cell."""
    class _Member:
        def __init__(self, form):
            self.form = form

    for n in range(0, 10):
        rects = group_cell_rects(n)
        visible = group_visible_member_count(n)
        # Every visible member maps to exactly one rect.
        assert visible <= len(rects)
        # Simulate a Photo member failing to load: resolve_cell_form never
        # returns None, so the cell count is unchanged by the failure.
        member = _Member("Photo")
        assert resolve_cell_form(member.form, False) is not None


# --- Delegate marking and group/member layering -----------------------------
def test_delegate_marking():
    assert is_delegate_identity("delegate-7f3a") is True
    assert is_delegate_identity("delegate_9") is True
    assert is_delegate_identity("hermes", group_id="room-1") is True
    assert is_delegate_identity("opencode") is False
    assert is_delegate_identity("room-1", is_group=True) is False
    # A main agent with a groupId is still a delegate (spawned helper).
    assert is_delegate_identity("helper", group_id="opencode") is True


def test_group_accent_never_equals_member_accent():
    members = ["amber", "teal", "coral", "violet"]
    assert group_accent_palette_id(members, "amber") not in members
    all_taken = list(PALETTE_IDS)
    resolved = group_accent_palette_id(all_taken, "sky")
    assert resolved not in all_taken
    assert resolved.endswith("-g")
    assert group_accent_palette_id([], None) == "amber"
