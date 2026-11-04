/**
 * opencode-talos-bridge
 *
 * Streams OpenCode's tool activity to TALOS so the Kanban board can show what
 * the agent is actually doing, rather than a spinner and a guess.
 *
 * Install:
 *     opencode plugin add opencode-talos-bridge
 *
 * or from a local checkout:
 *     opencode plugin add ./path/to/opencode-talos-bridge
 *
 * Configure with environment variables (never committed):
 *     TALOS_URL    default http://127.0.0.1:8010
 *     TALOS_TOKEN  host API key — without this the bridge stays disabled
 *     TALOS_ROOM_ID default "the-council"
 *     TALOS_DISABLED=1 to turn it off
 */

import { Plugin } from "@opencode/plugin";
import { TelemetryBridge, readConfig, type TelemetryEvent } from "./bridge.js";
import { preview, redact } from "./redact.js";

const PLUGIN_ID = "talos.bridge";

/** Event/agent identity that survives across a tool call's before/after phases. */
interface PendingTool {
  startedAt: number;
  tool: string;
}

export default Plugin.define({
  id: PLUGIN_ID,

  async setup(ctx) {
    const config = readConfig();
    const bridge = new TelemetryBridge(config);

    // A silent no-op plugin is indistinguishable from a broken one, so say which
    // state we are in. The message never includes the token.
    if (config.enabled) {
      await ctx.storage.set("bridge.enabled", true);
      console.log(
        `[${PLUGIN_ID}] enabled -> ${config.url} room=${config.roomId}`
      );
    } else {
      await ctx.storage.set("bridge.enabled", false);
      console.log(
        `[${PLUGIN_ID}] disabled (set TALOS_TOKEN to enable; TALOS_DISABLED=1 opts out)`
      );
    }

    /** In-flight calls keyed by tool + session, so start/finish pair up. */
    const pending = new Map<string, PendingTool>();
    const keyFor = (sessionId: string, tool: string) => `${sessionId}::${tool}`;

    const emit = (event: TelemetryEvent): void => {
      // Deliberately not awaited. A slow dashboard must never delay the agent.
      void bridge.send(event);
    };

    const identity = () => ({
      agentId: (ctx.location as { agent?: string }).agent ?? "opencode",
      agentName: "OpenCode",
      sessionId: (ctx.location as { sessionID?: string }).sessionID ?? "unknown",
    });

    // --- tool execution -----------------------------------------------------
    await ctx.tool.hook("execute.before", (event) => {
      const base = identity();
      // Redact here, before the payload leaves this process. The server redacts
      // again; one layer is not enough when the payload crosses a network.
      const safeArgs = redact(event.input);
      pending.set(keyFor(base.sessionId, event.tool), {
        startedAt: Date.now(),
        tool: event.tool,
      });
      emit({
        kind: "tool.start",
        ...base,
        tool: event.tool,
        arguments: safeArgs,
        status: "running",
        at: Date.now(),
      });
    });

    await ctx.tool.hook("execute.after", (event) => {
      const base = identity();
      const key = keyFor(base.sessionId, event.tool);
      const started = pending.get(key);
      pending.delete(key);
      const failed = event.status === "error";
      emit({
        kind: "tool.finish",
        ...base,
        tool: event.tool,
        status: failed ? "error" : "ok",
        resultSummary: failed ? "" : preview((event as { result?: unknown }).result),
        error: failed ? preview((event as { error?: { message?: string } }).error?.message) : "",
        at: Date.now(),
      });

      // Surface a stall hint when a single tool call ran unusually long; the
      // board uses this to explain a quiet agent instead of showing "working".
      if (started && Date.now() - started.startedAt > 120_000) {
        console.warn(
          `[${PLUGIN_ID}] ${event.tool} ran ${Math.round((Date.now() - started.startedAt) / 1000)}s`
        );
      }
    });

    // --- permissions --------------------------------------------------------
    // Reported so the phone can show the same prompt, with the same four
    // options, instead of the user having to walk back to the laptop.
    await ctx.permission.hook("evaluate", (event) => {
      emit({
        kind: "permission.asked",
        ...identity(),
        tool: event.action,
        arguments: redact(event.resources),
        status: "pending",
        at: Date.now(),
      });
    });

    // --- cleanup ------------------------------------------------------------
    return () => {
      pending.clear();
      void ctx.storage.set("bridge.enabled", false);
    };
  },
});
