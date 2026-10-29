//! Theme configuration: the flexible color/motion/shape token store.
//!
//! The Android client fetches `GET /api/theme` at startup and applies the
//! returned tokens as Material 3 color-scheme overrides, so palette changes
//! ship without an APK rebuild. `PUT /api/theme` persists a new config.

use serde::{Deserialize, Serialize};

/// One tonal color role as an sRGB hex string ("#RRGGBB").
pub type Hex = String;

/// M3-style tonal roles the client maps onto its ColorScheme.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct ColorRoles {
    pub primary: Hex,
    pub on_primary: Hex,
    pub primary_container: Hex,
    pub on_primary_container: Hex,
    pub secondary: Hex,
    pub on_secondary: Hex,
    pub secondary_container: Hex,
    pub on_secondary_container: Hex,
    pub tertiary: Hex,
    pub on_tertiary: Hex,
    pub tertiary_container: Hex,
    pub on_tertiary_container: Hex,
    pub error: Hex,
    pub on_error: Hex,
    pub surface: Hex,
    pub on_surface: Hex,
    pub on_surface_variant: Hex,
    pub surface_container_lowest: Hex,
    pub surface_container_low: Hex,
    pub surface_container: Hex,
    pub surface_container_high: Hex,
    pub surface_container_highest: Hex,
    pub outline: Hex,
    pub outline_variant: Hex,
}

/// M3 Expressive spring tokens (damping ratio + stiffness per family).
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct MotionTokens {
    pub spatial_fast: (f32, f32),
    pub spatial_default: (f32, f32),
    pub spatial_slow: (f32, f32),
    pub effects_fast: (f32, f32),
    pub effects_default: (f32, f32),
    pub effects_slow: (f32, f32),
}

/// Shape language: scallop parameters used by graphics-shapes on the client.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct ShapeTokens {
    pub scallop_vertices: u32,
    pub scallop_rounding: f32,
    pub scallop_smoothing: f32,
}

/// Full theme payload served to clients.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct ThemeConfig {
    pub name: String,
    pub seed: Hex,
    pub dark: ColorRoles,
    pub light: ColorRoles,
    pub motion: MotionTokens,
    pub shapes: ShapeTokens,
    pub updated_at: f64,
}

/// Name of the retired baseline-violet theme. Any stored config still
/// carrying it is migrated to the current default on read (see db::get_theme).
pub const LEGACY_THEME_NAME: &str = "Talos Expressive Violet";

fn hx(v: u32) -> Hex {
    format!("#{:06X}", v)
}

impl Default for ThemeConfig {
    fn default() -> Self {
        // TALOS "Molten Forge": copper primary, steel secondary, verdigris
        // patina tertiary on graphite. Mirrors the APK's built-in scheme so
        // the backend-served tokens never fight the bundled palette.
        let dark = ColorRoles {
            primary: hx(0xE0A261),
            on_primary: hx(0x4A2A08),
            primary_container: hx(0x6B4116),
            on_primary_container: hx(0xFFDCC0),
            secondary: hx(0xB9C0C8),
            on_secondary: hx(0x242A31),
            secondary_container: hx(0x3A4149),
            on_secondary_container: hx(0xDCE2EA),
            tertiary: hx(0x7FC8BB),
            on_tertiary: hx(0x00382F),
            tertiary_container: hx(0x1E4E45),
            on_tertiary_container: hx(0x9CE8D9),
            error: hx(0xFFB4AB),
            on_error: hx(0x690005),
            surface: hx(0x121316),
            on_surface: hx(0xE8E4DE),
            on_surface_variant: hx(0xB8B2AA),
            surface_container_lowest: hx(0x0B0C0E),
            surface_container_low: hx(0x191A1E),
            surface_container: hx(0x1E2024),
            surface_container_high: hx(0x282A2F),
            surface_container_highest: hx(0x33353B),
            outline: hx(0x8E8A84),
            outline_variant: hx(0x70747A),
        };
        let light = ColorRoles {
            primary: hx(0x8A5524),
            on_primary: hx(0xFFFFFF),
            primary_container: hx(0xFFDCC0),
            on_primary_container: hx(0x2E1804),
            secondary: hx(0x4E5A66),
            on_secondary: hx(0xFFFFFF),
            secondary_container: hx(0xDCE2EA),
            on_secondary_container: hx(0x222930),
            tertiary: hx(0x2F6A60),
            on_tertiary: hx(0xFFFFFF),
            tertiary_container: hx(0x9CE8D9),
            on_tertiary_container: hx(0x002019),
            error: hx(0xB3261E),
            on_error: hx(0xFFFFFF),
            surface: hx(0xFBF8F3),
            on_surface: hx(0x1B1A17),
            on_surface_variant: hx(0x4C4842),
            surface_container_lowest: hx(0xFFFFFF),
            surface_container_low: hx(0xF7F2EB),
            surface_container: hx(0xF1EBE3),
            surface_container_high: hx(0xEBE5DC),
            surface_container_highest: hx(0xE5DFD6),
            outline: hx(0x7E7970),
            outline_variant: hx(0x8C8377),
        };
        Self {
            name: "Molten Forge".into(),
            seed: hx(0xE0A261),
            dark,
            light,
            motion: MotionTokens {
                spatial_fast: (0.6, 800.0),
                spatial_default: (0.8, 380.0),
                spatial_slow: (0.8, 200.0),
                effects_fast: (1.0, 3800.0),
                effects_default: (1.0, 1600.0),
                effects_slow: (1.0, 800.0),
            },
            shapes: ShapeTokens {
                scallop_vertices: 12,
                scallop_rounding: 0.18,
                scallop_smoothing: 0.55,
            },
            updated_at: 0.0,
        }
    }
}

impl ThemeConfig {
    /// Validate hex fields are well-formed; return Err listing offenders.
    pub fn validate(&self) -> Result<(), String> {
        let check = |label: &str, v: &str| -> Result<(), String> {
            let body = v.strip_prefix('#').unwrap_or(v);
            if body.len() == 6 && body.chars().all(|c| c.is_ascii_hexdigit()) {
                Ok(())
            } else {
                Err(format!("invalid hex for {}: {}", label, v))
            }
        };
        check("seed", &self.seed)?;
        let d = &self.dark;
        check("dark.primary", &d.primary)?;
        check("dark.surface", &d.surface)?;
        check("dark.on_surface", &d.on_surface)?;
        let l = &self.light;
        check("light.primary", &l.primary)?;
        check("light.surface", &l.surface)?;
        check("light.on_surface", &l.on_surface)?;
        Ok(())
    }
}
