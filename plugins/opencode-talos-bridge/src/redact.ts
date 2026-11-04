/**
 * Credential redaction.
 *
 * This plugin forwards tool arguments to another process. That makes it a
 * credential exfiltration path unless it scrubs first: an agent running
 * `curl -H "Authorization: Bearer sk-or-v1-..."` would otherwise ship a live key
 * to the TALOS server, into SQLite, and onto a websocket to a phone.
 *
 * The approach is deliberately conservative and duplicated on purpose: scrub in
 * the plugin (before the value leaves this process) *and* again on the server
 * (server/tool_ledger.py). Defence in depth, because a single missed pattern on
 * either side should not be enough to leak a secret.
 *
 * Only patterns with high specificity are matched. A generic "any long string"
 * rule would redact file paths, commit SHAs and base64 images, which makes the
 * Kanban board useless and trains people to ignore it.
 */

const REDACTED = "[REDACTED]";

const PATTERNS: ReadonlyArray<{ name: string; re: RegExp; keepGroup?: boolean }> = [
  // Provider keys with recognisable prefixes.
  { name: "openai", re: /\bsk-[A-Za-z0-9_-]{16,}/g },
  { name: "openrouter", re: /\bsk-or-v1-[A-Za-z0-9_-]{16,}/g },
  { name: "anthropic", re: /\bsk-ant-[A-Za-z0-9_-]{16,}/g },
  { name: "github_pat", re: /\bgithub_pat_[A-Za-z0-9_]{20,}/g },
  { name: "github", re: /\bgh[pousr]_[A-Za-z0-9]{20,}/g },
  { name: "slack", re: /\bxox[baprs]-[A-Za-z0-9-]{10,}/g },
  { name: "google", re: /\bAIza[0-9A-Za-z_-]{30,}/g },
  { name: "aws", re: /\bAKIA[0-9A-Z]{16}\b/g },
  { name: "stripe", re: /\b[sr]k_(live|test)_[A-Za-z0-9]{16,}/g },
  { name: "huggingface", re: /\bhf_[A-Za-z0-9]{30,}/g },
  { name: "jwt", re: /\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}/g },

  // Assignments: keep the label so the log still says *what* was stripped.
  { name: "assignment", re: /((?:api[_-]?key|token|secret|password|passwd|authorization|bearer)\s*[=:]\s*)(["']?)([^\s"',;}]{8,})\2/gi, keepGroup: true },

  // Bearer/basic credentials inside a header string.
  { name: "bearer", re: /(Bearer\s+)([A-Za-z0-9._~+/-]{16,}=*)/gi, keepGroup: true },
  { name: "basic", re: /(Basic\s+)([A-Za-z0-9+/]{16,}=*)/gi, keepGroup: true },
];

/** Replace credential-shaped substrings in a string. */
export function redactString(value: string): string {
  let out = value;
  for (const { re, keepGroup } of PATTERNS) {
    // Reset lastIndex: the regexes are module-level and carry state across calls.
    re.lastIndex = 0;
    out = out.replace(re, (match, ...groups) => {
      if (keepGroup && groups.length >= 3) {
        // group1 = label, group2 = optional quote, group3 = the secret
        const [label, quote] = groups as string[];
        return `${label}${quote ?? ""}${REDACTED}${quote ?? ""}`;
      }
      if (keepGroup && groups.length >= 2) {
        const [label] = groups as string[];
        return `${label}${REDACTED}`;
      }
      return REDACTED;
    });
  }
  return out;
}

/**
 * Redact recursively.
 *
 * Tool arguments are nested JSON far more often than they are flat strings, and
 * a shallow scrub is the classic way this leaks. Depth is bounded so a cyclic or
 * pathological payload cannot hang the agent.
 */
export function redact(value: unknown, depth = 0): unknown {
  if (depth > 8) return value;
  if (typeof value === "string") return redactString(value);
  if (Array.isArray(value)) return value.slice(0, 200).map((item) => redact(item, depth + 1));
  if (value && typeof value === "object") {
    const out: Record<string, unknown> = {};
    for (const [key, item] of Object.entries(value as Record<string, unknown>).slice(0, 200)) {
      // A key named like a credential is redacted by name, whatever its value.
      if (/^(authorization|api[-_]?key|token|secret|password|passwd)$/i.test(key)) {
        out[key] = REDACTED;
      } else {
        out[key] = redact(item, depth + 1);
      }
    }
    return out;
  }
  return value;
}

/** Short one-line preview for the board, measured after redaction. */
export function preview(value: unknown, limit = 160): string {
  if (value === undefined || value === null) return "";
  let text: string;
  if (typeof value === "string") {
    text = value;
  } else {
    try {
      text = JSON.stringify(value);
    } catch {
      text = String(value);
    }
  }
  text = text.replace(/\s+/g, " ").trim();
  return text.length <= limit ? text : `${text.slice(0, limit - 1)}…`;
}

export { REDACTED };
