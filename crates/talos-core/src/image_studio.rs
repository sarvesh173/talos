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

        // Determine theme colors based on style
        let (bg_start, bg_end, accent_color) = match style.as_str() {
            "anime" => ("#1E1B4B", "#4A044E", "#F472B6"),
            "cyberpunk" => ("#050505", "#18181B", "#2DD4BF"),
            "watercolor" => ("#FDF2F8", "#E0F2FE", "#38BDF8"),
            "sketch" => ("#1C1917", "#292524", "#F59E0B"),
            _ => ("#0A0C10", "#151921", "#F59E0B"), // Cinematic Molten Forge
        };

        // Clean text escape
        let encoded_prompt = prompt_trimmed
            .replace('&', "&amp;")
            .replace('<', "&lt;")
            .replace('>', "&gt;")
            .replace('"', "&quot;")
            .replace('\'', "&apos;");

        let style_upper = style.to_uppercase();
        let prompt_y = height - 100;
        let cx = width / 2;
        let cy = height / 2;

        let svg_data = format!(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"{width}\" height=\"{height}\" viewBox=\"0 0 {width} {height}\">\
            <defs>\
            <linearGradient id=\"bg\" x1=\"0%\" y1=\"0%\" x2=\"100%\" y2=\"100%\">\
            <stop offset=\"0%\" stop-color=\"{bg_start}\"/>\
            <stop offset=\"100%\" stop-color=\"{bg_end}\"/>\
            </linearGradient>\
            <radialGradient id=\"acc\" cx=\"50%\" cy=\"50%\" r=\"50%\">\
            <stop offset=\"0%\" stop-color=\"{accent_color}\" stop-opacity=\"0.35\"/>\
            <stop offset=\"100%\" stop-color=\"{accent_color}\" stop-opacity=\"0\"/>\
            </radialGradient>\
            </defs>\
            <rect width=\"{width}\" height=\"{height}\" fill=\"url(#bg)\"/>\
            <circle cx=\"{cx}\" cy=\"{cy}\" r=\"350\" fill=\"url(#acc)\"/>\
            <text x=\"50\" y=\"80\" font-family=\"sans-serif\" font-size=\"28\" font-weight=\"bold\" fill=\"{accent_color}\">TALOS IMAGE STUDIO • {style_upper}</text>\
            <text x=\"50\" y=\"{prompt_y}\" font-family=\"sans-serif\" font-size=\"36\" font-weight=\"600\" fill=\"#F1F5F9\">{encoded_prompt}</text>\
            </svg>"
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
