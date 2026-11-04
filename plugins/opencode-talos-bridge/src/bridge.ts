/**
 * TALOS telemetry transport.
 *
 * Fire-and-forget by design. A plugin that can block an agent turn because a
 * dashboard is down is worse than no telemetry: the user would see their coding
 * agent freeze and have no idea why. Every failure here is swallowed after being
 * counted, and the counters are exposed so the health endpoint can say "we are
 * dropping events" rather than pretending everything is fine.
 *
 * Configuration comes from the environment, never from a file this repo ships:
 *   TALOS_URL        e.g. http://100.75.107.79:8010   (default: localhost:8010)
 *   TALOS_TOKEN      the host API key
 *   TALOS_ROOM_ID    defaults to "the-council"
 *   TALOS_DISABLED   set to "1" to turn the bridge off entirely
 */

export interface BridgeConfig {
  url: string;
  token: string;
  roomId: string;
  enabled: boolean;
  timeoutMs: number;
}

export interface TelemetryEvent {
  kind: "tool.start" | "tool.finish" | "session.created" | "permission.asked";
  agentId: string;
  agentName: string;
  sessionId: string;
  tool: string;
  arguments?: unknown;
  status?: string;
  resultSummary?: string;
  error?: string;
  isDelegate?: boolean;
  parentAgentId?: string;
  at: number;
}

export interface BridgeStats {
  sent: number;
  failed: number;
  dropped: number;
  lastError: string;
  lastSuccessAt: number;
}

export function readConfig(env: NodeJS.ProcessEnv = process.env): BridgeConfig {
  const token = (env.TALOS_TOKEN ?? "").trim();
  const url = (env.TALOS_URL ?? "http://127.0.0.1:8010").trim().replace(/\/+$/, "");
  const disabled = (env.TALOS_DISABLED ?? "").trim() === "1";
  return {
    url,
    token,
    roomId: (env.TALOS_ROOM_ID ?? "the-council").trim() || "the-council",
    // Without a token there is nothing to authenticate with, so the bridge stays
    // off instead of hammering the server with 401s on every tool call.
    enabled: !disabled && token.length > 0,
    timeoutMs: Number(env.TALOS_TIMEOUT_MS ?? 4000),
  };
}

export class TelemetryBridge {
  private readonly config: BridgeConfig;
  private readonly stats: BridgeStats = {
    sent: 0,
    failed: 0,
    dropped: 0,
    lastError: "",
    lastSuccessAt: 0,
  };

  constructor(config: BridgeConfig) {
    this.config = config;
  }

  getStats(): Readonly<BridgeStats> {
    return { ...this.stats };
  }

  /**
   * Send one event. Resolves immediately when the bridge is disabled so callers
   * never need to branch on configuration.
   */
  async send(event: TelemetryEvent): Promise<void> {
    if (!this.config.enabled) {
      this.stats.dropped += 1;
      return;
    }
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.config.timeoutMs);
    try {
      const response = await fetch(`${this.config.url}/api/kanban/events`, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          authorization: `Bearer ${this.config.token}`,
        },
        body: JSON.stringify({ room_id: this.config.roomId, event }),
        signal: controller.signal,
      });
      if (!response.ok) {
        // Read the body for a diagnostic but never include the token.
        const detail = await response.text().catch(() => "");
        throw new Error(`HTTP ${response.status}${detail ? `: ${detail.slice(0, 120)}` : ""}`);
      }
      this.stats.sent += 1;
      this.stats.lastSuccessAt = Date.now();
    } catch (error) {
      this.stats.failed += 1;
      this.stats.lastError = error instanceof Error ? error.message : String(error);
      // Intentionally not thrown: telemetry must never break the agent.
    } finally {
      clearTimeout(timer);
    }
  }
}
