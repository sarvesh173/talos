/**
 * The session tools, against a stub OpenCode server.
 *
 * A real server is used for the same reason as the host tests: the prompt flow
 * polls, and a mock that returns the reply immediately would pass while the
 * real one hangs.
 */

import { after, before, describe, it } from "node:test";
import assert from "node:assert/strict";
import { createServer, type Server } from "node:http";

import { chatTools } from "../dist/chat.js";
import { readOcConfig, type OcConfig } from "../dist/opencode.js";

let server: Server;
let base: string;

/** What the stub should answer, so each test states only its own case. */
let promptReplies: "text" | "error" = "text";
let pollCount = 0;

before(async () => {
  server = createServer((req, res) => {
    const auth = req.headers.authorization ?? "";
    const json = (code: number, body: unknown) => {
      res.writeHead(code, { "Content-Type": "application/json" });
      res.end(JSON.stringify(body));
    };

    if (!auth.startsWith("Basic ")) return json(401, { message: "Authentication required" });

    if (req.url === "/api/session" || req.url?.startsWith("/api/session?")) {
      return json(200, {
        data: [
          { id: "ses_1", title: "Hermes version update", time: { updated: 1794486893442 } },
          { id: "ses_2", time: { updated: 1794350842370 } },
        ],
      });
    }

    if (req.url?.startsWith("/api/session/ses_1/prompt") && req.method === "POST") {
      return json(200, { data: { id: "msg_u", type: "user", payload: { text: "hi" } } });
    }

    if (req.url?.startsWith("/api/session/ses_1/message")) {
      // Two polls: the first sees only the user turn, the second the reply.
      pollCount += 1;
      const rows =
        pollCount < 2
          ? [{ type: "user", text: "Reply with exactly: PONG" }]
          : [
              { type: "user", text: "Reply with exactly: PONG" },
              { type: "assistant", time: { completed: 1 }, content: [{ type: "text", text: "PONG" }] },
            ];
      return json(200, { data: rows });
    }

    if (req.url?.startsWith("/api/session/ses_err/prompt")) {
      return json(429, { error: { message: "Free usage limit reached" } });
    }

    return json(404, { message: "Not Found" });
  });

  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  if (address === null || typeof address === "string") throw new Error("no port");
  base = `http://127.0.0.1:${address.port}`;
});

after(() => {
  server.closeAllConnections();
  server.close();
});

const config = (overrides: Partial<OcConfig> = {}): OcConfig => ({
  ...readOcConfig({ OPENCODE_SERVER_PASSWORD: "pw", OPENCODE_URL: base }),
  timeoutMs: 2000,
  turnTimeoutMs: 5000,
  ...overrides,
});

const tool = (tools: ReturnType<typeof chatTools>, name: string) => {
  const found = tools.find((candidate) => candidate.name === name);
  assert.ok(found, `no tool named ${name}`);
  return found;
};

describe("readOcConfig", () => {
  it("stays disabled without a password", () => {
    assert.equal(readOcConfig({}).enabled, false);
  });

  it("enables with a password and strips a trailing slash", () => {
    const cfg = readOcConfig({ OPENCODE_SERVER_PASSWORD: "pw", OPENCODE_URL: "http://x:1///" });
    assert.equal(cfg.enabled, true);
    assert.equal(cfg.url, "http://x:1");
  });
});

describe("the session tools", () => {
  it("names itself sessions, prompt and messages", () => {
    assert.deepEqual(
      chatTools(config()).map((tool) => tool.name),
      ["sessions", "prompt", "messages"],
    );
  });

  it("lists a session by id and title, and marks an untitled one", async () => {
    const result = await tool(chatTools(config()), "sessions").run(config());
    assert.equal(result.isError, undefined);
    assert.match(result.content, /ses_1/);
    assert.match(result.content, /Hermes version update/);
    assert.match(result.content, /\(untitled\)/);
  });

  it("renders a user turn that carries its text outside content", async () => {
    const result = await tool(chatTools(config()), "messages").run(config(), {
      session_id: "ses_1",
      limit: 5,
    });
    assert.match(result.content, /\[user\] Reply with exactly: PONG/);
    assert.doesNotMatch(result.content, /\(no text\)/);
  });

  it("sends a prompt and returns the reply it polls for", async () => {
    pollCount = 0;
    const result = await tool(chatTools(config()), "prompt").run(config(), {
      session_id: "ses_1",
      text: "Reply with exactly: PONG",
    });
    assert.equal(result.isError, undefined);
    assert.equal(result.content, "PONG");
  });

  it("surfaces the provider error instead of hanging", async () => {
    const result = await tool(chatTools(config()), "prompt").run(config(), {
      session_id: "ses_err",
      text: "hi",
    });
    assert.equal(result.isError, true);
    assert.match(result.content, /Free usage limit reached/);
  });

  it("refuses a prompt without both arguments", async () => {
    const result = await tool(chatTools(config()), "prompt").run(config(), { session_id: "ses_1" });
    assert.equal(result.isError, true);
    assert.match(result.content, /session_id and text/);
  });
});