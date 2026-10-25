use sha2::{Digest, Sha256};
use uuid::Uuid;

pub struct AuthEngine;

impl AuthEngine {
    /// Generates a new API token with the standard `talos_sk_` prefix and returns (plain_secret, sha256_hash, masked_preview).
    pub fn generate_token(_label: &str) -> (String, String, String) {
        let raw_uuid = Uuid::new_v4().to_string().replace('-', "");
        let token = format!("talos_sk_{}", raw_uuid);
        let hash = Self::hash_token(&token);
        let masked = Self::mask_token(&token);
        (token, hash, masked)
    }

    /// Computes the hex-encoded SHA-256 hash of a bearer token.
    pub fn hash_token(token: &str) -> String {
        let mut hasher = Sha256::new();
        hasher.update(token.trim().as_bytes());
        hex::encode(hasher.finalize())
    }

    /// Creates a safe preview of the token for audit logs and UI cards.
    pub fn mask_token(token: &str) -> String {
        let trimmed = token.trim();
        if trimmed.len() <= 16 {
            return "talos_sk_...".to_string();
        }
        format!("{}...{}", &trimmed[..12], &trimmed[trimmed.len() - 4..])
    }
}
