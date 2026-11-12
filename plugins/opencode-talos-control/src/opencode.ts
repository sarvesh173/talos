/**
 * OpenCode server client — the session half of this plugin.
 *
 * The host tools answer "what is on the machine". These answer "what did the
 * agent say", which is a different server: `opencode serve` on its own port,
 * with basic auth rather than a bearer token.
 *
 * Configuration comes from the environment the server already has, so nothing
 * new needs provisioning:
 *   OPENCODE_URL                default http://127.0.0.1:4090
 *   OPENCODE_SERVER_PASSWORD    the server's password (username is "opencode")
 */

const BASIC_USERNAME = "opencode";

export interface OcConfig {
  url: string;
  password: string;
  enabled: boolean;
  /** Reading is fast; a model turn is not, so the two use different budgets. */
  timeoutMs: number;
  turnTimeoutMs: number;
}

export interface OcResponse<T = unknown> {
  ok: boolean;
  status: number;
  data?: T;
  error?: string;
}

export function readOcConfig(env: NodeJS.ProcessEnv = process.env): OcConfig {
  const url = (env.OPENCODE_URL ?? "http://127.0.0.1:4090").trim().replace(/\/+$/, "");
  const password = (env.OPENCODE_SERVER_PASSWORD ?? "").trim();
  return {
    url,
    password,
    // Without a password every call is a 401; say so once instead of per tool.
    enabled: password.length > 0,
    timeoutMs: Number(env.OPENCODE_TIMEOUT_MS ?? 8000),
    turnTimeoutMs: Number(env.OPENCODE_TURN_TIMEOUT_MS ?? 120000),
  };
}

/** Every call carries basic auth; the server rejects everything else. */
export async function ocCall<T>(
  config: OcConfig,
  path: string,
  init?: RequestInit,
  timeoutMs?: number,
): Promise<OcResponse<T>> {
  if (!config.enabled) {
    return {
      ok: false,
      status: 0,
      error:
        "OPENCODE_SERVER_PASSWORD is not set in this process, so the OpenCode server cannot be reached.",
    };
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs ?? config.timeoutMs);
  const auth = btoa(`${BASIC_USERNAME}:${config.password}`);

  try {
    const response = await fetch(`${config.url}${path}`, {
      ...init,
      signal: controller.signal,
      headers: {
        Authorization: `Basic ${auth}`,
        Accept: "application/json",
        ...(init?.body ? { "Content-Type": "application/json" } : {}),
        ...(init?.headers ?? {}),
      },
    });

    const text = await response.text();
    let data: unknown;
    try {
      data = text.length > 0 ? JSON.parse(text) : undefined;
    } catch {
      return {
        ok: false,
        status: response.status,
        error: `OpenCode returned non-JSON (${response.status}): ${text.slice(0, 200)}`,
      };
    }

    if (!response.ok) {
      const detail =
        (data as { message?: string; error?: { message?: string } })?.error?.message ??
        (data as { message?: string })?.message ??
        `HTTP ${response.status}`;
      return { ok: false, status: response.status, data: data as T, error: detail };
    }

    return { ok: true, status: response.status, data: data as T };
  } catch (error) {
    const aborted = (error as Error)?.name === "AbortError";
    return {
      ok: false,
      status: 0,
      error: aborted
        ? `OpenCode did not answer within ${timeoutMs ?? config.timeoutMs}ms at ${config.url}.`
        : `Cannot reach OpenCode at ${config.url}: ${(error as Error)?.message ?? "unknown error"}`,
    };
  } finally {
    clearTimeout(timer);
  }
}

export function describeOcFailure(path: string, status: number, error?: string): string {
  if (status === 401) {
    return "OpenCode rejected the password (401). OPENCODE_SERVER_PASSWORD is wrong or the server was started with a different one.";
  }
  if (status === 404) {
    return `OpenCode has no ${path} endpoint (404). It is older than this plugin expects.`;
  }
  return `${path} failed: ${error ?? "unknown error"}${status ? ` (HTTP ${status})` : ""}`;
}