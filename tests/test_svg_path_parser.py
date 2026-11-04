"""Geometry proofs for the TALOS arbitrary-SVG path parser.

The Python reference (svg_path_parser_ref.py) mirrors SvgPathParser.kt
algorithm-for-algorithm. These tests pin the exact expected outputs for
the shared fixtures so the maths is verifiable without an Android build.
"""
import math
import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from svg_path_parser_ref import (  # noqa: E402
    MAX_POINTS,
    parse_path_data,
    parse_svg,
    point_in_fill,
)

EPS = 1e-6


def bbox(contour):
    xs = [p[0] for p in contour]
    ys = [p[1] for p in contour]
    return min(xs), max(xs), min(ys), max(ys)


def close(a, b, tol=1e-6):
    return abs(a - b) <= tol


# --- 1. triangle -------------------------------------------------------------

def test_triangle_full_svg():
    r = parse_svg(
        '<svg viewBox="0 0 100 100">'
        '<path d="M10 90 L50 10 L90 90 Z"/></svg>'
    )
    assert r.ok and not r.truncated
    assert len(r.contours) == 1
    c = r.contours[0]
    assert c[0] == (10.0, 90.0)
    assert (50.0, 10.0) in c
    assert (90.0, 90.0) in c
    assert c[-1] == c[0]  # Z closes back to the subpath start
    assert bbox(c) == (10.0, 90.0, 10.0, 90.0)
    assert point_in_fill(50.0, 70.0, r.contours, r.fill_type)
    assert not point_in_fill(15.0, 15.0, r.contours, r.fill_type)


# --- 2. rounded rect via arcs -------------------------------------------------

def test_rounded_rect_via_arcs():
    # 80x80 rect at (10,10) with r=10 corners, built from H/V/A only.
    d = ("M20 10 H80 A10 10 0 0 1 90 20 V80 A10 10 0 0 1 80 90 "
         "H20 A10 10 0 0 1 10 80 V20 A10 10 0 0 1 20 10 Z")
    r = parse_svg(f'<svg viewBox="0 0 100 100"><path d="{d}"/></svg>')
    assert r.ok
    assert len(r.contours) == 1
    c = r.contours[0]
    assert bbox(c) == (10.0, 90.0, 10.0, 90.0)
    assert len(c) > 12  # arcs are flattened, not just corner points
    # corner arc midpoint of the top-right arc sits on the quarter circle:
    # centre (80,20), r=10 -> point at 45deg is (80+10/sqrt2, 20-10/sqrt2)
    diag = (80 + 10 / math.sqrt(2), 20 - 10 / math.sqrt(2))
    assert any(close(p[0], diag[0], 0.6) and close(p[1], diag[1], 0.6) for p in c)
    # inside the rect but outside the rounded corner is NOT filled
    assert point_in_fill(50.0, 50.0, r.contours, r.fill_type)
    assert not point_in_fill(11.0, 11.0, r.contours, r.fill_type)


# --- 3. cubic circle approximation --------------------------------------------

def test_cubic_circle_approximation():
    k = 0.5522847498 * 40.0  # kappa * r
    d = (f"M90 50 C90 {50 + k} {50 + k} 90 50 90 "
         f"C{50 - k} 90 10 {50 + k} 10 50 "
         f"C10 {50 - k} {50 - k} 10 50 10 "
         f"C{50 + k} 10 90 {50 - k} 90 50 Z")
    r = parse_svg(d)
    assert r.ok
    c = r.contours[0]
    # adaptive: a circle costs far fewer points than 4x10 fixed samples
    assert 8 <= len(c) <= 60, len(c)
    minx, maxx, miny, maxy = bbox(c)
    assert close(minx, 10.0, 0.5) and close(maxx, 90.0, 0.5)
    assert close(miny, 10.0, 0.5) and close(maxy, 90.0, 0.5)
    radii = [math.hypot(p[0] - 50.0, p[1] - 50.0) for p in c]
    assert all(38.5 <= rad <= 41.0 for rad in radii), (min(radii), max(radii))
    assert point_in_fill(50.0, 50.0, r.contours, r.fill_type)
    assert not point_in_fill(5.0, 5.0, r.contours, r.fill_type)


def test_straight_cubic_is_one_segment():
    # adaptive proof: a collinear cubic flattens to a single segment
    contours = parse_path_data("M0 0 C3 0 7 0 10 0 L10 10")
    c = contours[0]
    assert c == [(0.0, 0.0), (10.0, 0.0), (10.0, 10.0)]


# --- 4. rotate transform -------------------------------------------------------

def test_rotate_transform_with_centre():
    r = parse_svg(
        '<svg><path transform="rotate(90 50 50)" '
        'd="M10 10 L30 10 L30 30 Z"/></svg>'
    )
    assert r.ok
    c = r.contours[0]
    # rotate90 about (50,50): (10,10) -> (90,10)
    assert close(c[0][0], 90.0, 1e-9) and close(c[0][1], 10.0, 1e-9)
    # (30,10) -> (90,30)
    assert close(c[1][0], 90.0, 1e-9) and close(c[1][1], 30.0, 1e-9)


def test_matrix_and_skew_transforms():
    r = parse_svg(
        '<svg><path transform="matrix(2 0 0 3 5 7)" d="M1 1 L2 1 L2 2 Z"/></svg>'
    )
    assert r.ok
    assert r.contours[0][0] == (7.0, 10.0)  # (2*1+5, 3*1+7)
    r = parse_svg(
        '<svg><path transform="skewX(45)" d="M0 0 L10 0 L10 10 Z"/></svg>'
    )
    assert r.ok
    # skewX(45): x' = x + tan(45)*y -> (10,10) -> (20,10)
    assert (20.0, 10.0) in r.contours[0]


# --- 5. nested <g transform> ---------------------------------------------------

def test_nested_group_transforms():
    r = parse_svg(
        '<svg><g transform="translate(10 20)"><g transform="scale(2)">'
        '<path d="M0 0 L10 0 L10 10 Z"/></g></g></svg>'
    )
    assert r.ok
    c = r.contours[0]
    assert c[0] == (10.0, 20.0)
    assert c[1] == (30.0, 20.0)
    assert c[2] == (30.0, 40.0)


def test_viewbox_normalisation():
    r = parse_svg(
        '<svg viewBox="10 20 80 80"><path d="M10 20 L90 20 L90 100 Z"/></svg>'
    )
    assert r.ok
    assert r.contours[0][0] == (0.0, 0.0)


# --- 6. evenodd donut ------------------------------------------------------------

def test_evenodd_donut_two_contours_hole_stays_hole():
    d = "M10 10 H90 V90 H10 Z M30 30 H70 V70 H30 Z"
    r = parse_svg(
        f'<svg><path fill-rule="evenodd" d="{d}"/></svg>'
    )
    assert r.ok
    assert r.fill_type == "evenOdd"
    assert len(r.contours) == 2
    assert point_in_fill(20.0, 20.0, r.contours, r.fill_type)      # ring
    assert not point_in_fill(50.0, 50.0, r.contours, r.fill_type)  # hole
    assert not point_in_fill(95.0, 95.0, r.contours, r.fill_type)  # outside


# --- 7. raw d string ---------------------------------------------------------------

def test_raw_d_string():
    r = parse_svg("M0 0 L10 0 L0 10 Z")
    assert r.ok
    assert len(r.contours) == 1
    c = r.contours[0]
    assert len(c) == 4  # 3 corners + Z close
    assert c[0] == (0.0, 0.0)
    assert c[-1] == c[0]


def test_bare_path_element_and_relative_commands():
    r = parse_svg('<path d="m5 5 l10 0 l0 10 z"/>')
    assert r.ok
    assert r.contours[0][0] == (5.0, 5.0)
    assert r.contours[0][1] == (15.0, 5.0)
    assert r.contours[0][2] == (15.0, 15.0)


def test_quad_smooth_commands():
    # Q then T (T reflects the control point) then S after C
    r = parse_svg("M0 0 Q5 10 10 0 T20 0 L20 10 Z")
    assert r.ok
    c = r.contours[0]
    assert c[0] == (0.0, 0.0)
    assert any(close(p[0], 5.0, 0.5) and close(p[1], 5.0, 0.5) for p in c)
    r2 = parse_svg("M0 0 C0 10 10 10 10 0 S20 -10 20 0 L20 5 Z")
    assert r2.ok


# --- stroke-only ------------------------------------------------------------------

def test_stroke_only_path_ignored():
    r = parse_svg(
        '<svg><path fill="none" stroke="#000" d="M0 0 L10 0 L10 10 Z"/></svg>'
    )
    assert not r.ok  # no fill contours; an error result, not a crash
    assert r.error is not None
    assert r.contours == []


# --- 8. malformed -------------------------------------------------------------------

@pytest.mark.parametrize("bad", [
    "",
    "M0",                 # truncated pair
    "M0 0 Lx y",          # non-numeric
    "X10 10",             # unknown command
    "garbage",
    "<svg></svg>",        # no path elements
    "<svg><path d=\"M0 0 L10\"/></svg>",  # truncated L
])
def test_malformed_returns_error_not_throw(bad):
    r = parse_svg(bad)
    assert r.error is not None
    assert r.contours == []


# --- bounded work ----------------------------------------------------------------------

def test_pathological_path_truncated_not_hung():
    # 100k line segments >> MAX_POINTS budget
    d = "M0 0 " + " ".join(f"L{i} {i % 7}" for i in range(1, 100_001))
    r = parse_svg(d)
    assert r.truncated
    assert r.contours, "partial geometry should survive truncation"
    total = sum(len(c) for c in r.contours)
    assert total <= MAX_POINTS + 2, total
