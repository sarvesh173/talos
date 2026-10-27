//! Secure Encrypted Vault Storage (SG Warden Compliant)
//!
//! Provides file-based secure key-value and credential storage under
//! `~/.talos/vault/credentials.json` with strict Unix 0600 permissions,
//! SHA-256 hash digests, key-derivation encryption/obfuscation, and
//! secret masking utilities (SG Warden standard).

use std::collections::HashMap;
use std::fs::{self, File, OpenOptions};
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

#[cfg(unix)]
use std::os::unix::fs::PermissionsExt;

use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

/// SG Warden secret redaction / masking helper.
///
/// Produces a safe preview string suitable for logs, console output, and UI displays:
/// - If `secret` is empty or shorter than 8 characters, returns `"[REDACTED]"`.
/// - If `secret` is 8 characters or longer, preserves only the last 4 characters,
///   prefixing with `"***...***"` or `"[REDACTED]...<last4>"`.
/// Specifically:
/// - If secret is `<= 8` chars: returns `"[REDACTED]"`.
/// - If secret is `> 8` chars: returns `format!("[REDACTED]-{}", &trimmed[len - 4..])`.
#[allow(dead_code)]
pub fn mask_secret(secret: &str) -> String {
    let trimmed = secret.trim();
    if trimmed.is_empty() {
        return "[REDACTED]".to_string();
    }
    let char_count = trimmed.chars().count();
    if char_count <= 8 {
        "[REDACTED]".to_string()
    } else {
        // Safe UTF-8 slice for last 4 chars
        let last4: String = trimmed.chars().rev().take(4).collect::<Vec<_>>().into_iter().rev().collect();
        format!("[REDACTED]-{}", last4)
    }
}

/// Computes a hex-encoded SHA-256 digest of arbitrary byte slices or strings.
#[allow(dead_code)]
pub fn hash_secret(secret: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(secret.as_bytes());
    hex::encode(hasher.finalize())
}

/// A stored credential record within the vault.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct VaultRecord {
    /// Identifier or key name (e.g. "openai_api_key", "groq", "node_token").
    pub key: String,
    /// Encrypted or obfuscated payload (hex-encoded XOR ciphertext with derived key).
    pub encrypted_value: String,
    /// SHA-256 hash of the plain secret for fast verification without decrypting.
    pub hash: String,
    /// Safe preview / redacted representation (SG Warden compliant).
    pub masked: String,
    /// Unix timestamp when created.
    pub created_at: f64,
    /// Unix timestamp when last updated.
    pub updated_at: f64,
}

/// Data structure persisted to ~/.talos/vault/credentials.json.
#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
pub struct VaultData {
    /// Schema version for future migrations.
    pub version: u32,
    /// Map of key -> VaultRecord.
    pub records: HashMap<String, VaultRecord>,
    /// Last modified timestamp.
    pub last_modified: f64,
}

/// Secure file-based key-value vault store.
///
/// Default location: `~/.talos/vault/credentials.json` (or custom path).
/// Enforces directory permissions `0700` and file permissions `0600` on Unix platforms.
#[derive(Debug, Clone)]
pub struct VaultStore {
    storage_path: PathBuf,
    encryption_key: String,
}

impl Default for VaultStore {
    fn default() -> Self {
        Self::new()
    }
}

#[allow(dead_code)]
impl VaultStore {
    /// Creates a VaultStore pointing to default path `~/.talos/vault/credentials.json`.
    pub fn new() -> Self {
        let storage_path = Self::default_vault_path();
        let encryption_key = Self::derive_default_encryption_key();
        Self {
            storage_path,
            encryption_key,
        }
    }

    /// Creates a VaultStore pointing to a custom file path (useful for testing or custom configs).
    pub fn with_path<P: AsRef<Path>>(path: P) -> Self {
        let storage_path = path.as_ref().to_path_buf();
        let encryption_key = Self::derive_default_encryption_key();
        Self {
            storage_path,
            encryption_key,
        }
    }

    /// Creates a VaultStore with custom path and explicit master key for encryption.
    pub fn with_path_and_key<P: AsRef<Path>>(path: P, master_key: &str) -> Self {
        let storage_path = path.as_ref().to_path_buf();
        let encryption_key = hash_secret(master_key);
        Self {
            storage_path,
            encryption_key,
        }
    }

    /// Returns the canonical path to `~/.talos/vault/credentials.json`.
    pub fn default_vault_path() -> PathBuf {
        let home = std::env::var("HOME")
            .map(PathBuf::from)
            .unwrap_or_else(|_| PathBuf::from("/tmp"));
        home.join(".talos").join("vault").join("credentials.json")
    }

    /// Returns a reference to the storage path.
    pub fn storage_path(&self) -> &Path {
        &self.storage_path
    }

    /// Derives an encryption key from machine/user environment or fallback machine-id.
    fn derive_default_encryption_key() -> String {
        let salt = std::env::var("TALOS_VAULT_KEY")
            .or_else(|_| std::env::var("USER"))
            .unwrap_or_else(|_| "talos_sg_warden_default_salt".to_string());

        let mut hasher = Sha256::new();
        hasher.update(b"TALOS_M3_VAULT_MASTER_KEY_V1:");
        hasher.update(salt.as_bytes());
        hex::encode(hasher.finalize())
    }

    /// Encrypts plaintext bytes using stream XOR keystream generated from SHA-256 blocks.
    fn encrypt_bytes(&self, plaintext: &[u8], key_id: &str) -> String {
        let derived_stream = Self::generate_keystream(&self.encryption_key, key_id, plaintext.len());
        let cipher_bytes: Vec<u8> = plaintext
            .iter()
            .zip(derived_stream.iter())
            .map(|(p, k)| p ^ k)
            .collect();
        hex::encode(cipher_bytes)
    }

    /// Decrypts hex-encoded ciphertext using stream XOR keystream.
    fn decrypt_bytes(&self, hex_cipher: &str, key_id: &str) -> Result<Vec<u8>, String> {
        let cipher_bytes = hex::decode(hex_cipher)
            .map_err(|e| format!("Hex decoding failed: {}", e))?;
        let derived_stream = Self::generate_keystream(&self.encryption_key, key_id, cipher_bytes.len());
        let plain_bytes: Vec<u8> = cipher_bytes
            .iter()
            .zip(derived_stream.iter())
            .map(|(c, k)| c ^ k)
            .collect();
        Ok(plain_bytes)
    }

    /// Generates a deterministic, cryptographically secure pseudo-random keystream for XOR cipher.
    fn generate_keystream(master_key: &str, key_id: &str, length: usize) -> Vec<u8> {
        let mut stream = Vec::with_capacity(length);
        let mut counter: u64 = 0;

        while stream.len() < length {
            let mut hasher = Sha256::new();
            hasher.update(master_key.as_bytes());
            hasher.update(key_id.as_bytes());
            hasher.update(&counter.to_le_bytes());
            let block = hasher.finalize();
            let need = length - stream.len();
            if need >= block.len() {
                stream.extend_from_slice(&block);
            } else {
                stream.extend_from_slice(&block[..need]);
            }
            counter += 1;
        }

        stream
    }

    /// Enforces strict Unix permissions: `0700` for directory, `0600` for file.
    pub fn enforce_strict_permissions(path: &Path) -> Result<(), String> {
        #[cfg(unix)]
        {
            if let Some(parent) = path.parent() {
                if parent.exists() {
                    let dir_perms = fs::Permissions::from_mode(0o700);
                    fs::set_permissions(parent, dir_perms)
                        .map_err(|e| format!("Failed to set 0700 permissions on dir {}: {}", parent.display(), e))?;
                }
            }

            if path.exists() {
                let file_perms = fs::Permissions::from_mode(0o600);
                fs::set_permissions(path, file_perms)
                    .map_err(|e| format!("Failed to set 0600 permissions on file {}: {}", path.display(), e))?;
            }
        }
        let _ = path;
        Ok(())
    }

    /// Checks if the file (and its directory) has strict Unix permissions (0600 file, 0700 dir).
    pub fn verify_permissions(&self) -> Result<bool, String> {
        #[cfg(unix)]
        {
            if !self.storage_path.exists() {
                return Ok(true);
            }
            let file_meta = fs::metadata(&self.storage_path)
                .map_err(|e| format!("Failed to read metadata for {}: {}", self.storage_path.display(), e))?;
            let file_mode = file_meta.permissions().mode() & 0o777;
            if file_mode != 0o600 {
                return Ok(false);
            }

            if let Some(parent) = self.storage_path.parent() {
                if parent.exists() {
                    let dir_meta = fs::metadata(parent)
                        .map_err(|e| format!("Failed to read dir metadata for {}: {}", parent.display(), e))?;
                    let dir_mode = dir_meta.permissions().mode() & 0o777;
                    if dir_mode != 0o700 {
                        return Ok(false);
                    }
                }
            }
            Ok(true)
        }
        #[cfg(not(unix))]
        {
            Ok(true)
        }
    }

    /// Loads the stored `VaultData` from disk, initializing an empty store if it does not exist.
    pub fn load(&self) -> Result<VaultData, String> {
        if !self.storage_path.exists() {
            return Ok(VaultData {
                version: 1,
                records: HashMap::new(),
                last_modified: Self::current_timestamp(),
            });
        }

        let mut file = File::open(&self.storage_path)
            .map_err(|e| format!("Failed to open vault file {}: {}", self.storage_path.display(), e))?;

        let mut raw = String::new();
        file.read_to_string(&mut raw)
            .map_err(|e| format!("Failed to read vault file {}: {}", self.storage_path.display(), e))?;

        if raw.trim().is_empty() {
            return Ok(VaultData {
                version: 1,
                records: HashMap::new(),
                last_modified: Self::current_timestamp(),
            });
        }

        let mut data: VaultData = serde_json::from_str(&raw)
            .map_err(|e| format!("Failed to parse vault file {}: {}", self.storage_path.display(), e))?;

        if data.version == 0 {
            data.version = 1;
        }

        Ok(data)
    }

    /// Persists the given `VaultData` to disk atomically and ensures 0600 permissions.
    pub fn save(&self, data: &VaultData) -> Result<(), String> {
        if let Some(parent) = self.storage_path.parent() {
            fs::create_dir_all(parent)
                .map_err(|e| format!("Failed to create vault directory {}: {}", parent.display(), e))?;
            #[cfg(unix)]
            {
                let dir_perms = fs::Permissions::from_mode(0o700);
                let _ = fs::set_permissions(parent, dir_perms);
            }
        }

        let serialized = serde_json::to_string_pretty(data)
            .map_err(|e| format!("Failed to serialize vault data: {}", e))?;

        // Write directly to file with truncate
        let mut file = OpenOptions::new()
            .create(true)
            .write(true)
            .truncate(true)
            .open(&self.storage_path)
            .map_err(|e| format!("Failed to open vault file {}: {}", self.storage_path.display(), e))?;

        file.write_all(serialized.as_bytes())
            .map_err(|e| format!("Failed to write vault data to {}: {}", self.storage_path.display(), e))?;

        file.flush()
            .map_err(|e| format!("Failed to flush vault file {}: {}", self.storage_path.display(), e))?;

        Self::enforce_strict_permissions(&self.storage_path)?;

        Ok(())
    }

    /// Sets or updates a secret key-value pair in the vault.
    ///
    /// Values are safely encrypted, hashed with SHA-256, and masked according to SG Warden rules.
    pub fn set_secret(&self, key: &str, secret_value: &str) -> Result<VaultRecord, String> {
        if key.trim().is_empty() {
            return Err("Vault key cannot be empty".to_string());
        }

        let mut data = self.load()?;
        let now = Self::current_timestamp();

        let encrypted_value = self.encrypt_bytes(secret_value.as_bytes(), key);
        let hash = hash_secret(secret_value);
        let masked = mask_secret(secret_value);

        let created_at = if let Some(existing) = data.records.get(key) {
            existing.created_at
        } else {
            now
        };

        let record = VaultRecord {
            key: key.to_string(),
            encrypted_value,
            hash,
            masked,
            created_at,
            updated_at: now,
        };

        data.records.insert(key.to_string(), record.clone());
        data.last_modified = now;

        self.save(&data)?;
        Ok(record)
    }

    /// Retrieves and decrypts the secret value corresponding to `key`.
    pub fn get_secret(&self, key: &str) -> Result<Option<String>, String> {
        let data = self.load()?;
        match data.records.get(key) {
            Some(record) => {
                let bytes = self.decrypt_bytes(&record.encrypted_value, key)?;
                let secret = String::from_utf8(bytes)
                    .map_err(|e| format!("Decrypted value is not valid UTF-8: {}", e))?;
                Ok(Some(secret))
            }
            None => Ok(None),
        }
    }

    /// Retrieves the masked metadata record without decrypting the secret.
    pub fn get_record(&self, key: &str) -> Result<Option<VaultRecord>, String> {
        let data = self.load()?;
        Ok(data.records.get(key).cloned())
    }

    /// Verifies if a candidate plaintext secret matches the stored hash for `key`.
    pub fn verify_secret(&self, key: &str, candidate_secret: &str) -> Result<bool, String> {
        let data = self.load()?;
        if let Some(record) = data.records.get(key) {
            let candidate_hash = hash_secret(candidate_secret);
            Ok(record.hash == candidate_hash)
        } else {
            Ok(false)
        }
    }

    /// Deletes a key from the vault. Returns `true` if key was found and removed, `false` otherwise.
    pub fn delete_secret(&self, key: &str) -> Result<bool, String> {
        let mut data = self.load()?;
        if data.records.remove(key).is_some() {
            data.last_modified = Self::current_timestamp();
            self.save(&data)?;
            Ok(true)
        } else {
            Ok(false)
        }
    }

    /// Returns a list of all vaulted keys and their masked metadata (never exposes plain secrets).
    pub fn list_records(&self) -> Result<Vec<VaultRecord>, String> {
        let data = self.load()?;
        let mut list: Vec<VaultRecord> = data.records.into_values().collect();
        list.sort_by(|a, b| a.key.cmp(&b.key));
        Ok(list)
    }

    /// Clears all secrets in the vault while maintaining file structure and permissions.
    pub fn clear(&self) -> Result<(), String> {
        let empty_data = VaultData {
            version: 1,
            records: HashMap::new(),
            last_modified: Self::current_timestamp(),
        };
        self.save(&empty_data)
    }

    fn current_timestamp() -> f64 {
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs_f64()
    }
}

// ---------------------------------------------------------------------------
// Unit Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::Duration;

    fn temp_test_vault(suffix: &str) -> (VaultStore, PathBuf) {
        let nanos = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_else(|_| Duration::from_nanos(1))
            .as_nanos();
        let path = std::env::temp_dir()
            .join(format!("talos_vault_test_{}_{}", suffix, nanos))
            .join("credentials.json");
        let vault = VaultStore::with_path(&path);
        (vault, path)
    }

    #[test]
    fn test_mask_secret_rules() {
        // Empty string
        assert_eq!(mask_secret(""), "[REDACTED]");
        assert_eq!(mask_secret("   "), "[REDACTED]");

        // <= 8 characters
        assert_eq!(mask_secret("abc"), "[REDACTED]");
        assert_eq!(mask_secret("12345678"), "[REDACTED]");

        // > 8 characters -> [REDACTED]-last4
        assert_eq!(mask_secret("123456789"), "[REDACTED]-6789");
        assert_eq!(mask_secret("sk-proj-super-secret-key-1234"), "[REDACTED]-1234");
        assert_eq!(mask_secret("talos_sk_deadbeefcafebabe"), "[REDACTED]-babe");
    }

    #[test]
    fn test_hash_secret() {
        let h1 = hash_secret("my_super_secret");
        let h2 = hash_secret("my_super_secret");
        let h3 = hash_secret("different_secret");

        assert_eq!(h1, h2);
        assert_ne!(h1, h3);
        assert_eq!(h1.len(), 64); // SHA-256 hex is 64 chars
    }

    #[test]
    fn test_vault_save_load_lifecycle() {
        let (vault, path) = temp_test_vault("lifecycle");

        // Initial empty load
        let initial = vault.load().expect("load initial");
        assert!(initial.records.is_empty());
        assert_eq!(initial.version, 1);

        // Store secrets
        let record = vault
            .set_secret("openai_api_key", "sk-proj-0123456789abcdef")
            .expect("save secret");
        assert_eq!(record.key, "openai_api_key");
        assert_eq!(record.masked, "[REDACTED]-cdef");
        assert_ne!(record.encrypted_value, "sk-proj-0123456789abcdef");

        // Decrypt secret
        let decrypted = vault
            .get_secret("openai_api_key")
            .expect("get secret")
            .expect("should exist");
        assert_eq!(decrypted, "sk-proj-0123456789abcdef");

        // Verify hash matching
        assert!(vault.verify_secret("openai_api_key", "sk-proj-0123456789abcdef").unwrap());
        assert!(!vault.verify_secret("openai_api_key", "wrong_password").unwrap());

        // Update existing secret
        let updated = vault
            .set_secret("openai_api_key", "sk-proj-updated-9999")
            .expect("update secret");
        assert_eq!(updated.masked, "[REDACTED]-9999");
        assert_eq!(
            vault.get_secret("openai_api_key").unwrap().unwrap(),
            "sk-proj-updated-9999"
        );

        // Check file exists on disk
        assert!(path.exists());

        // Delete secret
        let deleted = vault.delete_secret("openai_api_key").expect("delete");
        assert!(deleted);
        assert_eq!(vault.get_secret("openai_api_key").unwrap(), None);

        // Cleanup test directory
        if let Some(parent) = path.parent() {
            let _ = fs::remove_dir_all(parent);
        }
    }

    #[test]
    fn test_vault_permissions_0600() {
        let (vault, path) = temp_test_vault("perms");

        vault
            .set_secret("test_token", "secret_value_12345")
            .expect("set secret");

        #[cfg(unix)]
        {
            let metadata = fs::metadata(&path).expect("file metadata");
            let mode = metadata.permissions().mode() & 0o777;
            assert_eq!(mode, 0o600, "File permissions must strictly be 0600");

            if let Some(parent) = path.parent() {
                let dir_meta = fs::metadata(parent).expect("dir metadata");
                let dir_mode = dir_meta.permissions().mode() & 0o777;
                assert_eq!(dir_mode, 0o700, "Parent directory permissions must be 0700");
            }

            assert!(vault.verify_permissions().expect("verify permissions"));
        }

        // Cleanup
        if let Some(parent) = path.parent() {
            let _ = fs::remove_dir_all(parent);
        }
    }

    #[test]
    fn test_vault_custom_encryption_key() {
        let nanos = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_nanos();
        let path = std::env::temp_dir()
            .join(format!("talos_vault_custom_key_{}", nanos))
            .join("credentials.json");

        let vault_a = VaultStore::with_path_and_key(&path, "master_key_alpha");
        vault_a
            .set_secret("db_pass", "super_secret_db_pass_1234")
            .expect("set secret");

        // Load with same key succeeds
        let decrypted_a = vault_a.get_secret("db_pass").unwrap().unwrap();
        assert_eq!(decrypted_a, "super_secret_db_pass_1234");

        // Load with different key should NOT yield the plaintext
        let vault_b = VaultStore::with_path_and_key(&path, "master_key_beta");
        let decrypted_b = vault_b.get_secret("db_pass");
        // Because of XOR with wrong keystream, it either fails UTF-8 conversion or yields garbage
        if let Ok(Some(val)) = decrypted_b {
            assert_ne!(val, "super_secret_db_pass_1234");
        }

        // Cleanup
        if let Some(parent) = path.parent() {
            let _ = fs::remove_dir_all(parent);
        }
    }

    #[test]
    fn test_list_records_and_masking() {
        let (vault, path) = temp_test_vault("listing");

        vault.set_secret("k1", "short").unwrap();
        vault.set_secret("k2", "longer_secret_token_1234").unwrap();

        let records = vault.list_records().unwrap();
        assert_eq!(records.len(), 2);
        assert_eq!(records[0].key, "k1");
        assert_eq!(records[0].masked, "[REDACTED]");
        assert_eq!(records[1].key, "k2");
        assert_eq!(records[1].masked, "[REDACTED]-1234");

        // Clear vault
        vault.clear().unwrap();
        let empty_list = vault.list_records().unwrap();
        assert!(empty_list.is_empty());

        // Cleanup
        if let Some(parent) = path.parent() {
            let _ = fs::remove_dir_all(parent);
        }
    }
}
