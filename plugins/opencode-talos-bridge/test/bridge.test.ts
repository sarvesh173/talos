import test from "node:test";
import assert from "node:assert/strict";
import { readConfig, TelemetryBridge } from "../src/bridge.ts";

test("disabled when no token is present", () => {
  const config = readConfig({} as NodeJS.ProcessEnv);
  assert.equal(config.enabled, false);
  assert.equal(config.url, "http://127.0.0.1:8010");
  assert.equal(config.roomId, "the-council");
});

test("enabled when a token is present", () => {
  const config = readConfig({ TALOS_TOKEN: "abc123" } as unknown as NodeJS.ProcessEnv);
  assert.equal(config.enabled, true);
});

test("TALOS_DISABLED wins over a token", () => {
  const config = readConfig({
    TALOS_TOKEN: "abc123",
    TALOS_DISABLED: "1",
  } as unknown as NodeJS.ProcessEnv);
  assert.equal(config.enabled, false);
});

test("trailing slashes are stripped from the url", () => {
  const config = readConfig({
    TALOS_TOKEN: "t",
    TALOS_URL: "http://host:8010///",
  } as unknown as NodeJS.ProcessEnv);
  assert.equal(config.url, "http://host:8010");
});

test("a whitespace token is not a token", () => {
  const config = readConfig({ TALOS_TOKEN: "   " } as unknown as NodeJS.ProcessEnv);
  assert.equal(config.enabled, false);
});

test("disabled bridge drops instead of sending", async () => {
  const bridge = new TelemetryBridge(readConfig({} as NodeJS.ProcessEnv));
  await bridge.send({
    kind: "tool.start",
    agentId: "a",
    agentName: "OpenCode",
    sessionId: "s",
    tool: "read",
    at: Date.now(),
  });
  const stats = bridge.getStats();
  assert.equal(stats.sent, 0);
  assert.equal(stats.failed, 0);
  assert.equal(stats.dropped, 1);
});

test("a transport failure is counted, never thrown", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async () => {
    throw new Error("ECONNREFUSED");
  }) as unknown as typeof fetch;

  try {
    const bridge = new TelemetryBridge(
      readConfig({ TALOS_TOKEN: "t" } as unknown as NodeJS.ProcessEnv)
    );
    await assert.doesNotReject(() =>
      bridge.send({
        kind: "tool.start",
        agentId: "a",
        agentName: "OpenCode",
        sessionId: "s",
        tool: "read",
        at: Date.now(),
      })
    );
    const stats = bridge.getStats();
    assert.equal(stats.failed, 1);
    assert.match(stats.lastError, /ECONNREFUSED/);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("an HTTP error keeps the status and never the token", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async () =>
    new Response("nope", { status: 401 })) as unknown as typeof fetch;

  try {
    const bridge = new TelemetryBridge(
      readConfig({ TALOS_TOKEN: "supersecret" } as unknown as NodeJS.ProcessEnv)
    );
    await bridge.send({
      kind: "tool.start",
      agentId: "a",
      agentName: "OpenCode",
      sessionId: "s",
      tool: "read",
      at: Date.now(),
    });
    const stats = bridge.getStats();
    assert.equal(stats.failed, 1);
    assert.match(stats.lastError, /401/);
    assert.ok(!stats.lastError.includes("supersecret"));
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("a successful send increments sent", async () => {
  const originalFetch = globalThis.fetch;
  let seenUrl = "";
  let seenAuth = "";
  globalThis.fetch = (async (url: string, init: RequestInit) => {
    seenUrl = String(url);
    seenAuth = String((init.headers as Record<string, string>).authorization);
    return new Response("{}", { status: 200 });
  }) as unknown as typeof fetch;

  try {
    const bridge = new TelemetryBridge(
      readConfig({ TALOS_TOKEN: "tok", TALOS_URL: "http://h:1" } as unknown as NodeJS.ProcessEnv)
    );
    await bridge.send({
      kind: "tool.start",
      agentId: "a",
      agentName: "OpenCode",
      sessionId: "s",
      tool: "read",
      at: Date.now(),
    });
    assert.equal(bridge.getStats().sent, 1);
    assert.equal(seenUrl, "http://h:1/api/kanban/events");
    assert.equal(seenAuth, "Bearer tok");
  } finally {
    globalThis.fetch = originalFetch;
  }
});
