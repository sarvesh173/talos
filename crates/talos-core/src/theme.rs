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

fn hx(v: u32) -> Hex {
    format!("#{:06X}", v)
}

impl Default for ThemeConfig {
    fn default() -> Self {
        // TALOS M3 Expressive baseline: seed #6750A4, brand dark #6366F1 family.
        let dark = ColorRoles {
            primary: hx(0xD0BCFF),
            on_primary: hx(0x381E72),
            primary_container: hx(0x4F378B),
            on_primary_container: hx(0xEADDFF),
            secondary: hx(0xCCC2DC),
            on_secondary: hx(0x332D41),
            secondary_container: hx(0x4A4458),
            on_secondary_container: hx(0xE8DEF8),
            tertiary: hx(0xEFB8C8),
            on_tertiary: hx(0x492532),
            tertiary_container: hx(0x633B48),
            on_tertiary_container: hx(0xFFD8E4),
            error: hx(0xF2B8B5),
            on_error: hx(0x601410),
            surface: hx(0x141218),
            on_surface: hx(0xE5E1E6),
            on_surface_variant: hx(0xCAC4D0),
            surface_container_lowest: hx(0x0F0D13),
            surface_container_low: hx(0x1D1B20),
            surface_container: hx(0x211F26),
            surface_container_high: hx(0x2B2930),
            surface_container_highest: hx(0x36343B),
            outline: hx(0x938F99),
            outline_variant: hx(0x49454F),
        };
        let light = ColorRoles {
            primary: hx(0x6750A4),
            on_primary: hx(0xFFFFFF),
            primary_container: hx(0xEADDFF),
            on_primary_container: hx(0x21005D),
            secondary: hx(0x625B71),
            on_secondary: hx(0xFFFFFF),
            secondary_container: hx(0xE8DEF8),
            on_secondary_container: hx(0x1D192B),
            tertiary: hx(0x7D5260),
            on_tertiary: hx(0xFFFFFF),
            tertiary_container: hx(0xFFD8E4),
            on_tertiary_container: hx(0x31111D),
            error: hx(0xB3261E),
            on_error: hx(0xFFFFFF),
            surface: hx(0xFEF7FF),
            on_surface: hx(0x1D1B20),
            on_surface_variant: hx(0x49454F),
            surface_container_lowest: hx(0xFFFFFF),
            surface_container_low: hx(0xF7F2FA),
            surface_container: hx(0xF3EDF7),
            surface_container_high: hx(0xECE6F0),
            surface_container_highest: hx(0xE6E0E9),
            outline: hx(0x79747E),
            outline_variant: hx(0xCAC4D0),
        };
        Self {
            name: "Talos Expressive Violet".into(),
            seed: hx(0x6750A4),
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
