/**
 * The session tools: talk to an agent, and move between its sessions.
 *
 * Deliberately small. Everything else about OpenCode — token accounting,
 * approvals, permission rules, model choice — is a later pass; this file is
 * the minimum that lets a person hold a conversation.
 */

import { type Tool } from "talos-plugin-core";
import { describeOcFailure, ocCall, type OcConfig } from "./opencode.js";

interface SessionRow {
  id: string;
  title?: string;
  time?: { created?: number; updated?: number };
}

interface MessageRow {
  info?: { id?: string; type?: string; role?: string; error?: { message?: string } };
  type?: string;
  content?: { type: string; text?: string }[];
  error?: { message?: string };
  outcome?: string;
  /** User turns carry their text here; assistant turns use content[]. */
  text?: string;
  payload?: { text?: string };
  time?: { created?: number; completed?: number };
}

const asList = (data: unknown): Record<string, unknown>[] =>
  Array.isArray(data) ? (data as Record<string, unknown>[]) : [];

const messageList = (data: unknown): MessageRow[] =>
  asList((data as { data?: unknown })?.data ?? data) as MessageRow[];

/** Text a person reads: reasoning is skipped, text parts are joined. */
const textOf = (message: MessageRow): string =>
  (message.content ?? [])
    .filter((part) => part.type === "text" && typeof part.text === "string")
    .map((part) => part.text as string)
    .join("\n")
    .trim();

const iso = (ms?: number): string => (ms ? new Date(ms).toISOString() : "unknown time");

export function renderSessions(data: unknown): string {
  const rows = asList((data as { data?: unknown })?.data ?? data) as unknown as SessionRow[];
  if (rows.length === 0) return "No sessions on this OpenCode server.";
  return rows
    .map((row) => {
      const title = row.title?.trim() || "(untitled)";
      return `${row.id}\n  ${title}\n  updated ${iso(row.time?.updated)}`;
    })
    .join("\n");
}

export function renderMessages(data: unknown, limit: number): string {
  const rows = messageList(data).slice(-limit);
  if (rows.length === 0) return "No messages in this session yet.";
  return rows
    .map((message) => {
      const role = message.info?.type ?? message.type ?? "unknown";
      const failed = message.error?.message ?? message.info?.error?.message;
      if (failed) return `[${role}] failed: ${failed}`;
      // Three shapes, one per endpoint: the message list puts user text in
      // `text`, the prompt response in `payload.text`, assistants in content[].
      const text =
        textOf(message) ||
        (message.text ?? "").trim() ||
        (message.payload?.text ?? "").trim();
      return `[${role}] ${text || "(no text)"}`;
    })
    .join("\n\n");
}

/**
 * A prompt is accepted immediately and answered later, so the reply is read by
 * polling: the turn is done when an assistant message carries a completion time
 * or an error, or when an idle message reports the outcome.
 */
async function waitForReply(
  config: OcConfig,
  sessionId: string,
  deadline: number,
): Promise<{ text: string; error?: string }> {
  let lastError: string | undefined;
  while (Date.now() < deadline) {
    await new Promise((resolve) => setTimeout(resolve, 1500));
    const response = await ocCall<{ data?: MessageRow[] }>(
      config,
      `/api/session/${sessionId}/message?limit=3`,
    );
    if (!response.ok) {
      lastError = describeOcFailure(`/api/session/${sessionId}/message`, response.status, response.error);
      continue;
    }
    const rows = messageList(response.data).slice(-3);
    for (const row of rows) {
      const kind = row.info?.type ?? row.type;
      const failure = row.error?.message ?? row.info?.error?.message;
      if (failure) return { text: "", error: failure };
      if (kind === "idle") {
        if (row.outcome === "failed") continue;
        // The assistant row is the one before the idle marker.
        const assistant = [...rows].reverse().find((r) => (r.info?.type ?? r.type) === "assistant");
        if (assistant) {
          const text = textOf(assistant);
          if (text) return { text };
        }
        continue;
      }
      if (kind === "assistant" && row.time?.completed) {
        const text = textOf(row);
        if (text) return { text };
      }
    }
  }
  return { text: "", error: lastError ?? "The agent did not finish before the timeout." };
}

export function chatTools(config: OcConfig): Tool[] {
  const fail = (path: string, status: number, error?: string) => ({
    content: describeOcFailure(path, status, error),
    isError: true,
  });

  return [
    {
      name: "sessions",
      description:
        "List the OpenCode sessions (conversations) on this server, newest first, with their id and title. Use the id with the prompt and messages tools.",
      input: { type: "object", properties: {}, additionalProperties: false },
      run: async () => {
        const response = await ocCall<unknown>(config, "/api/session?limit=20");
        if (!response.ok) return fail("/api/session", response.status, response.error);
        return { content: renderSessions(response.data) };
      },
    },
    {
      name: "prompt",
      description:
        "Send a message to an OpenCode session and return the agent's reply. Requires the session id from the sessions tool. This is how you talk to the agent.",
      input: {
        type: "object",
        properties: {
          session_id: { type: "string", description: "Session id, e.g. ses_..." },
          text: { type: "string", description: "Message to send" },
        },
        required: ["session_id", "text"],
        additionalProperties: false,
      },
      run: async (_config, args) => {
        const sessionId = String(args?.session_id ?? "").trim();
        const text = String(args?.text ?? "").trim();
        if (!sessionId || !text) {
          return { content: "prompt needs session_id and text.", isError: true };
        }
        const sent = await ocCall<unknown>(
          config,
          `/api/session/${encodeURIComponent(sessionId)}/prompt`,
          { method: "POST", body: JSON.stringify({ text }) },
        );
        if (!sent.ok) {
          return fail(`/api/session/${sessionId}/prompt`, sent.status, sent.error);
        }
        const reply = await waitForReply(config, sessionId, Date.now() + config.turnTimeoutMs);
        if (reply.error) return { content: reply.error, isError: true };
        return { content: reply.text };
      },
    },
    {
      name: "messages",
      description:
        "Read the recent messages of an OpenCode session — use it to catch up on a session before replying in it.",
      input: {
        type: "object",
        properties: {
          session_id: { type: "string", description: "Session id, e.g. ses_..." },
          limit: { type: "number", description: "How many recent messages (default 10)" },
        },
        required: ["session_id"],
        additionalProperties: false,
      },
      run: async (_config, args) => {
        const sessionId = String(args?.session_id ?? "").trim();
        if (!sessionId) return { content: "messages needs session_id.", isError: true };
        const limit = Math.max(1, Math.min(50, Number(args?.limit ?? 10) || 10));
        const path = `/api/session/${encodeURIComponent(sessionId)}/message?limit=${limit}`;
        const response = await ocCall<unknown>(config, path);
        if (!response.ok) return fail(path, response.status, response.error);
        return { content: renderMessages(response.data, limit) };
      },
    },
  ];
}