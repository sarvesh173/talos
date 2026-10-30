//! Image Generation & Gallery Studio for TALOS Core.
//!
//! Provides generation routing, SVG canvas asset synthesis, and persistent SQLite gallery storage.

use crate::db::Database;
use crate::models::{GenerateImageRequest, GeneratedImage};
use std::time::{SystemTime, UNIX_EPOCH};
use tracing::info;

fn now_ts() -> f64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs_f64()
}

#[derive(Debug, Clone, Default)]
pub struct ImageStudio;

impl ImageStudio {
    pub fn new() -> Self {
        Self
    }

    /// Generate an image asset from a text prompt and style specification.
    pub fn generate(
        &self,
        req: GenerateImageRequest,
        db: &Database,
    ) -> Result<GeneratedImage, String> {
        let prompt_trimmed = req.prompt.trim();
        if prompt_trimmed.is_empty() {
            return Err("Prompt cannot be empty".to_string());
        }

        let id = format!("img_{}", &uuid::Uuid::new_v4().to_string()[..8]);
        let style = req.style.to_lowercase();
        let aspect_ratio = req.aspect_ratio;

        // Determine dimension ratios
        let (width, height) = match aspect_ratio.as_str() {
            "16:9" => (1280, 720),
            "9:16" => (720, 1280),
            "4:3" => (1024, 768),
            "3:4" => (768, 1024),
            _ => (1024, 1024),
        };

        // Determine theme colors based on style (Strict M3 Expressive & Signature Purple Authority)
        let (bg_start, bg_end, accent_color, secondary_accent) = match style.as_str() {
            "anime" => ("#1E1B4B", "#4A044E", "#F472B6", "#E879F9"),
            "cyberpunk" => ("#050505", "#18181B", "#2DD4BF", "#38BDF8"),
            "watercolor" => ("#0F172A", "#1E293B", "#818CF8", "#A78BFA"),
            "sketch" => ("#141218", "#211F26", "#D0BCFF", "#E6E0E9"),
            "flux" | "photorealistic" => ("#090D16", "#141C2E", "#38BDF8", "#818CF8"),
            "wan" | "digital_art" => ("#181024", "#2D1B4E", "#C084FC", "#F43F5E"),
            _ => ("#141218", "#2B2338", "#D0BCFF", "#6750A4"), // Google M3 Signature Purple
        };

        // Clean text escape
        let encoded_prompt = prompt_trimmed
            .replace('&', "&amp;")
            .replace('<', "&lt;")
            .replace('>', "&gt;")
            .replace('"', "&quot;")
            .replace('\'', "&apos;");

        let style_upper = style.to_uppercase();
        let prompt_y = height - 120;
        let cx = width / 2;
        let cy = height / 2;
        let frame_inset = 32;
        let frame_w = width - (frame_inset * 2);
        let frame_h = height - (frame_inset * 2);

        let svg_data = format!(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"{width}\" height=\"{height}\" viewBox=\"0 0 {width} {height}\">\
            <defs>\
            <linearGradient id=\"bg\" x1=\"0%\" y1=\"0%\" x2=\"100%\" y2=\"100%\">\
            <stop offset=\"0%\" stop-color=\"{bg_start}\"/>\
            <stop offset=\"100%\" stop-color=\"{bg_end}\"/>\
            </linearGradient>\
            <radialGradient id=\"acc\" cx=\"50%\" cy=\"50%\" r=\"50%\">\
            <stop offset=\"0%\" stop-color=\"{accent_color}\" stop-opacity=\"0.40\"/>\
            <stop offset=\"100%\" stop-color=\"{accent_color}\" stop-opacity=\"0\"/>\
            </radialGradient>\
            <linearGradient id=\"borderGrad\" x1=\"0%\" y1=\"0%\" x2=\"100%\" y2=\"100%\">\
            <stop offset=\"0%\" stop-color=\"{accent_color}\" stop-opacity=\"0.7\"/>\
            <stop offset=\"100%\" stop-color=\"{secondary_accent}\" stop-opacity=\"0.2\"/>\
            </linearGradient>\
            <pattern id=\"grid\" width=\"40\" height=\"40\" patternUnits=\"userSpaceOnUse\">\
            <path d=\"M 40 0 L 0 0 0 40\" fill=\"none\" stroke=\"{accent_color}\" stroke-width=\"0.5\" stroke-opacity=\"0.15\"/>\
            </pattern>\
            </defs>\
            <rect width=\"{width}\" height=\"{height}\" fill=\"url(#bg)\"/>\
            <rect width=\"{width}\" height=\"{height}\" fill=\"url(#grid)\"/>\
            <circle cx=\"{cx}\" cy=\"{cy}\" r=\"380\" fill=\"url(#acc)\"/>\
            <rect x=\"{frame_inset}\" y=\"{frame_inset}\" width=\"{frame_w}\" height=\"{frame_h}\" rx=\"24\" fill=\"none\" stroke=\"url(#borderGrad)\" stroke-width=\"2\"/>\
            <rect x=\"60\" y=\"60\" width=\"280\" height=\"36\" rx=\"18\" fill=\"{accent_color}\" fill-opacity=\"0.18\" stroke=\"{accent_color}\" stroke-width=\"1\"/>\
            <text x=\"80\" y=\"84\" font-family=\"sans-serif\" font-size=\"14\" font-weight=\"bold\" fill=\"{accent_color}\" letter-spacing=\"1.5\">HERMES STUDIO • {style_upper}</text>\
            <rect x=\"{frame_w}\" y=\"60\" width=\"80\" height=\"36\" rx=\"18\" fill=\"#141218\" fill-opacity=\"0.8\" stroke=\"{secondary_accent}\" stroke-width=\"1\"/>\
            <text x=\"{}\" y=\"84\" font-family=\"sans-serif\" font-size=\"14\" font-weight=\"600\" fill=\"{secondary_accent}\">{aspect_ratio}</text>\
            <rect x=\"60\" y=\"{}\" width=\"{}\" height=\"90\" rx=\"16\" fill=\"#141218\" fill-opacity=\"0.7\" stroke=\"{accent_color}\" stroke-opacity=\"0.3\" stroke-width=\"1\"/>\
            <text x=\"84\" y=\"{}\" font-family=\"sans-serif\" font-size=\"24\" font-weight=\"600\" fill=\"#E6E0E9\">{encoded_prompt}</text>\
            </svg>",
            frame_w + 20,
            prompt_y - 30,
            width - 120,
            prompt_y + 24
        );

        let image_url = format!(
            "data:image/svg+xml;utf8,{}",
            svg_data.replace('#', "%23").replace(' ', "%20")
        );

        let img = GeneratedImage {
            id,
            prompt: prompt_trimmed.to_string(),
            style,
            aspect_ratio,
            image_url,
            created_at: now_ts(),
        };

        db.insert_generated_image(&img)
            .map_err(|e| format!("Database error: {}", e))?;

        info!("Generated image {} for prompt '{}'", img.id, img.prompt);
        Ok(img)
    }

    /// List gallery images from database.
    pub fn list(&self, db: &Database, limit: usize) -> Result<Vec<GeneratedImage>, String> {
        db.list_generated_images(limit)
            .map_err(|e| format!("Database error: {}", e))
    }

    /// Delete an image from gallery.
    pub fn delete(&self, id: &str, db: &Database) -> Result<bool, String> {
        db.delete_generated_image(id)
            .map_err(|e| format!("Database error: {}", e))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_image_generation_and_gallery_lifecycle() {
        let db = Database::new_in_memory().expect("db in memory");
        let studio = ImageStudio::new();

        let req = GenerateImageRequest {
            prompt: "Cyberpunk mechanical terminal in copper and obsidian".to_string(),
            style: "cyberpunk".to_string(),
            aspect_ratio: "16:9".to_string(),
        };

        let generated = studio.generate(req, &db).expect("generation should succeed");
        assert!(generated.id.starts_with("img_"));
        assert_eq!(generated.style, "cyberpunk");
        assert!(generated.image_url.starts_with("data:image/svg+xml"));

        let gallery = studio.list(&db, 10).expect("gallery list should succeed");
        assert_eq!(gallery.len(), 1);
        assert_eq!(gallery[0].id, generated.id);

        let deleted = studio.delete(&generated.id, &db).expect("delete should succeed");
        assert!(deleted);

        let gallery_after = studio.list(&db, 10).expect("gallery list should succeed");
        assert_eq!(gallery_after.len(), 0);
    }
}
