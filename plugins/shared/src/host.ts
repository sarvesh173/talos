/**
 * TALOS host client.
 *
 * One place that knows how to talk to the host, so the tools stay thin. Every
 * call is bounded by a timeout: a host that stops answering must surface as an
 * error the model can read, not as a hanging turn.
 *
 * Configuration comes from the environment, never from a committed file:
 *   TALOS_URL    e.g. http://100.75.107.79:8000   (default: localhost:8000)
 *   TALOS_TOKEN  the host API key
 *
 * Environment is not portable: a plugin loaded by a service manager gets the
 * unit's environment, not the user's shell. So the token is also read from a
 * file when the variable is absent — same fallback chain on every OS.
 */
import { readFileSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

/** Where a host may drop the token so no environment plumbing is needed. */
const TOKEN_FILES = [
  () => join(homedir(), ".talos", "token"),
  () => join(homedir(), ".hermes", "talos_token.txt"),
];

function readTokenFromFile(env: NodeJS.ProcessEnv): string {
  const explicit = (env.TALOS_TOKEN_FILE ?? "").trim();
  const candidates = explicit ? [explicit, ...TOKEN_FILES.map((f) => f())] : TOKEN_FILES.map((f) => f());
  for (const path of candidates) {
    try {
      const value = readFileSync(path, "utf8").trim();
      if (value) return value;
    } catch {
      // Missing file is normal; try the next candidate.
    }
  }
  return "";
}

export interface HostConfig {
  url: string;
  token: string;
  enabled: boolean;
  timeoutMs: number;
}

export interface HostResponse<T = unknown> {
  ok: boolean;
  status: number;
  data?: T;
  error?: string;
}

export function readConfig(env: NodeJS.ProcessEnv = process.env): HostConfig {
  const token = (env.TALOS_TOKEN ?? "").trim() || readTokenFromFile(env);
  const url = (env.TALOS_URL ?? "http://127.0.0.1:8000").trim().replace(/\/+$/, "");
  const disabled = (env.TALOS_DISABLED ?? "").trim() === "1";
  return {
    url,
    token,
    // Without a token every call is a 401; better to say so once than to fail
    // every tool with the same error.
    enabled: !disabled && token.length > 0,
    timeoutMs: Number(env.TALOS_TIMEOUT_MS ?? 8000),
  };
}

export async function callHost<T>(
  config: HostConfig,
  path: string,
  init?: RequestInit,
): Promise<HostResponse<T>> {
  if (!config.enabled) {
    return {
      ok: false,
      status: 0,
      error: "TALOS_TOKEN is not set, so the host cannot be reached. Set it in the environment.",
    };
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), config.timeoutMs);
  try {
    const response = await fetch(`${config.url}${path}`, {
      ...init,
      signal: controller.signal,
      headers: {
        Authorization: `Bearer ${config.token}`,
        Accept: "application/json",
        ...(init?.headers ?? {}),
      },
    });

    const text = await response.text();
    let data: unknown;
    try {
      data = text.length > 0 ? JSON.parse(text) : undefined;
    } catch {
      // The host answered with something that is not JSON. Keep the body so the
      // error says what actually came back instead of "unexpected token <".
      return {
        ok: false,
        status: response.status,
        error: `Host returned non-JSON (${response.status}): ${text.slice(0, 200)}`,
      };
    }

    if (!response.ok) {
      const detail =
        (data as { detail?: string })?.detail ?? `HTTP ${response.status}`;
      return { ok: false, status: response.status, data: data as T, error: detail };
    }

    return { ok: true, status: response.status, data: data as T };
  } catch (error) {
    const aborted = (error as Error)?.name === "AbortError";
    return {
      ok: false,
      status: 0,
      error: aborted
        ? `Host did not answer within ${config.timeoutMs}ms at ${config.url}.`
        : `Cannot reach host at ${config.url}: ${(error as Error)?.message ?? "unknown error"}`,
    };
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Turns a failed host call into something the model can act on.
 *
 * The two cases worth naming are a rejected key and a missing endpoint: the
 * first is the user's credential, the second means the host is older than the
 * plugin expects. Both are otherwise just numbers.
 */
export function describeFailure(path: string, status: number, error?: string): string {
  if (status === 401) {
    return `TALOS rejected the API key (401 from ${path}). The key in TALOS_TOKEN is wrong or revoked.`;
  }
  if (status === 404) {
    return `The host has no ${path} endpoint (404). It is running an older build than this plugin expects.`;
  }
  return `${path} failed: ${error ?? "unknown error"}${status ? ` (HTTP ${status})` : ""}`;
}
