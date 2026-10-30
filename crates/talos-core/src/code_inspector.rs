//! Git Working Tree & Code Diff Inspector for TALOS Core.
//!
//! Provides real-time git diff parsing, status inspection, and file content viewing
//! matching ChatGPT Canvas and GitHub code review capabilities.

use serde::{Deserialize, Serialize};
use std::process::Command;
use tracing::info;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GitDiffSummary {
    pub branch: String,
    pub total_additions: usize,
    pub total_deletions: usize,
    pub files: Vec<GitFileDiff>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GitFileDiff {
    pub filename: String,
    pub path: String,
    pub additions: usize,
    pub deletions: usize,
    pub lines: Vec<GitDiffLine>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GitDiffLine {
    pub line_type: String, // "header", "added", "deleted", "context"
    pub old_num: Option<usize>,
    pub new_num: Option<usize>,
    pub content: String,
}

#[derive(Debug, Clone, Default)]
pub struct CodeInspector;

impl CodeInspector {
    pub fn new() -> Self {
        Self
    }

    /// Retrieve the current git branch name.
    pub fn get_branch(&self) -> String {
        Command::new("git")
            .args(["rev-parse", "--abbrev-ref", "HEAD"])
            .output()
            .ok()
            .and_then(|out| {
                if out.status.success() {
                    Some(String::from_utf8_lossy(&out.stdout).trim().to_string())
                } else {
                    None
                }
            })
            .unwrap_or_else(|| "main".to_string())
    }

    /// Retrieve and parse unified git diff of working tree.
    pub fn get_diff(&self) -> GitDiffSummary {
        let branch = self.get_branch();

        // Run git diff (staged + unstaged vs HEAD)
        let output = Command::new("git")
            .args(["diff", "HEAD", "--unified=3"])
            .output()
            .or_else(|_| Command::new("git").args(["diff", "--unified=3"]).output())
            .ok();

        let raw_diff = output
            .filter(|o| o.status.success())
            .map(|o| String::from_utf8_lossy(&o.stdout).to_string())
            .unwrap_or_default();

        let (files, total_additions, total_deletions) = self.parse_unified_diff(&raw_diff);

        info!(
            "Git diff inspected: {} files changed, +{} -{}",
            files.len(),
            total_additions,
            total_deletions
        );

        GitDiffSummary {
            branch,
            total_additions,
            total_deletions,
            files,
        }
    }

    fn parse_unified_diff(&self, raw: &str) -> (Vec<GitFileDiff>, usize, usize) {
        let mut files = Vec::new();
        let mut total_additions = 0;
        let mut total_deletions = 0;

        let mut current_file: Option<GitFileDiff> = None;
        let mut old_line_counter = 0;
        let mut new_line_counter = 0;

        for line in raw.lines() {
            if line.starts_with("diff --git ") {
                if let Some(f) = current_file.take() {
                    total_additions += f.additions;
                    total_deletions += f.deletions;
                    files.push(f);
                }

                // Extract path from "diff --git a/path b/path"
                let parts: Vec<&str> = line.split_whitespace().collect();
                let path = parts
                    .get(3)
                    .map(|p| p.trim_start_matches("b/"))
                    .unwrap_or("unknown")
                    .to_string();
                let filename = path.split('/').last().unwrap_or(&path).to_string();

                current_file = Some(GitFileDiff {
                    filename,
                    path,
                    additions: 0,
                    deletions: 0,
                    lines: Vec::new(),
                });
            } else if line.starts_with("@@ ") {
                // Hunk header: @@ -old_start,old_count +new_start,new_count @@
                if let Some(ref mut f) = current_file {
                    f.lines.push(GitDiffLine {
                        line_type: "header".to_string(),
                        old_num: None,
                        new_num: None,
                        content: line.to_string(),
                    });

                    // Parse starting line numbers
                    if let Some(plus_idx) = line.find('+') {
                        let after_plus = &line[plus_idx + 1..];
                        let comma_idx = after_plus.find(',').or_else(|| after_plus.find(' '));
                        if let Some(end) = comma_idx {
                            new_line_counter = after_plus[..end].parse().unwrap_or(1);
                        }
                    }
                    if let Some(minus_idx) = line.find('-') {
                        let after_minus = &line[minus_idx + 1..];
                        let comma_idx = after_minus.find(',').or_else(|| after_minus.find(' '));
                        if let Some(end) = comma_idx {
                            old_line_counter = after_minus[..end].parse().unwrap_or(1);
                        }
                    }
                }
            } else if line.starts_with('+') && !line.starts_with("+++ ") {
                if let Some(ref mut f) = current_file {
                    f.additions += 1;
                    f.lines.push(GitDiffLine {
                        line_type: "added".to_string(),
                        old_num: None,
                        new_num: Some(new_line_counter),
                        content: line[1..].to_string(),
                    });
                    new_line_counter += 1;
                }
            } else if line.starts_with('-') && !line.starts_with("--- ") {
                if let Some(ref mut f) = current_file {
                    f.deletions += 1;
                    f.lines.push(GitDiffLine {
                        line_type: "deleted".to_string(),
                        old_num: Some(old_line_counter),
                        new_num: None,
                        content: line[1..].to_string(),
                    });
                    old_line_counter += 1;
                }
            } else if !line.starts_with("index ")
                && !line.starts_with("--- ")
                && !line.starts_with("+++ ")
            {
                if let Some(ref mut f) = current_file {
                    f.lines.push(GitDiffLine {
                        line_type: "context".to_string(),
                        old_num: Some(old_line_counter),
                        new_num: Some(new_line_counter),
                        content: if line.is_empty() {
                            String::new()
                        } else {
                            line[1.min(line.len())..].to_string()
                        },
                    });
                    old_line_counter += 1;
                    new_line_counter += 1;
                }
            }
        }

        if let Some(f) = current_file {
            total_additions += f.additions;
            total_deletions += f.deletions;
            files.push(f);
        }

        (files, total_additions, total_deletions)
    }

    /// Read raw file content from the working directory safely.
    pub fn read_file_content(&self, rel_path: &str) -> Result<String, String> {
        let clean_path = rel_path.trim_start_matches('/');
        if clean_path.contains("..") {
            return Err("Path traversal not permitted".to_string());
        }

        std::fs::read_to_string(clean_path).map_err(|e| format!("Failed to read file: {}", e))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_unified_diff_parsing() {
        let inspector = CodeInspector::new();
        let sample_diff = r#"diff --git a/src/main.rs b/src/main.rs
index abc..def 100644
--- a/src/main.rs
+++ b/src/main.rs
@@ -10,3 +10,4 @@
 fn main() {
-    println!("hello");
+    println!("hello world");
+    println!("talos online");
 }
"#;
        let (files, add, del) = inspector.parse_unified_diff(sample_diff);
        assert_eq!(files.len(), 1);
        assert_eq!(files[0].filename, "main.rs");
        assert_eq!(add, 2);
        assert_eq!(del, 1);
        assert_eq!(files[0].lines.len(), 6);
    }
}
