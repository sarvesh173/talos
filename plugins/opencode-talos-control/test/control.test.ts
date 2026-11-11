/**
 * Tests run against a real HTTP server on a loopback port, so the timeout,
 * status mapping and JSON handling all exercise real fetch behaviour rather
 * than a mock that cannot disagree with it.
 */

import { after, before, describe, it } from "node:test";
import assert from "node:assert/strict";
import { createServer, type Server } from "node:http";

import { callHost, readConfig, type HostConfig } from "../dist/host.js";
import {
  describeFailure,
  formatDiff,
  gitDiff,
  hostHealth,
  json,
  listAgents,
  meshStatus,
} from "../dist/tools.js";

let server: Server;
let base: string;

before(async () => {
  server = createServer((req, res) => {
    const token = req.headers.authorization;

    if (req.url === "/health") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ status: "ok" }));
      return;
    }

    if (!token || token !== "Bearer test-key") {
      res.writeHead(401, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ detail: "Invalid or missing TALOS API Token" }));
      return;
    }

    if (req.url === "/api/agents/roster") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(
        JSON.stringify({
          agents: [{ id: "opencode", installed: true, sessions: 20 }],
        }),
      );
      return;
    }

    if (req.url === "/api/processes") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ processes: [] }));
      return;
    }

    if (req.url === "/api/git/diff") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(
        JSON.stringify({
          branch: "dev",
          files: [{ path: "src/a.ts", additions: 3, deletions: 1 }],
        }),
      );
      return;
    }

    if (req.url === "/api/missing") {
      res.writeHead(404, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ detail: "Not Found" }));
      return;
    }

    if (req.url === "/api/git/diff?clean=1") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ branch: "dev", files: [] }));
      return;
    }

    if (req.url === "/api/mesh/status") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ peers: [{ name: "redmi-note-4", online: true }] }));
      return;
    }

    if (req.url === "/api/not-json") {
      res.writeHead(200, { "Content-Type": "text/plain" });
      res.end("this is not json");
      return;
    }

    if (req.url === "/api/slow") {
      // Never answers; the caller's timeout has to fire.
      return;
    }

    res.writeHead(404, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ detail: "Not Found" }));
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

const config = (overrides: Partial<HostConfig> = {}): HostConfig => ({
  url: base,
  token: "test-key",
  enabled: true,
  timeoutMs: 2000,
  ...overrides,
});

describe("readConfig", () => {
  it("stays disabled without a token", () => {
    assert.equal(readConfig({}).enabled, false);
  });

  it("enables when a token is present and strips trailing slashes", () => {
    const cfg = readConfig({ TALOS_TOKEN: "abc", TALOS_URL: "http://x:1///" });
    assert.equal(cfg.enabled, true);
    assert.equal(cfg.url, "http://x:1");
  });

  it("TALOS_DISABLED wins over a token", () => {
    assert.equal(readConfig({ TALOS_TOKEN: "abc", TALOS_DISABLED: "1" }).enabled, false);
  });
});

describe("callHost", () => {
  it("refuses to call when disabled, and says why", async () => {
    const result = await callHost(config({ enabled: false }), "/health");
    assert.equal(result.ok, false);
    assert.match(result.error ?? "", /TALOS_TOKEN is not set/);
  });

  it("maps a 401 to the host's own detail", async () => {
    const result = await callHost(config({ token: "wrong" }), "/api/agents/roster");
    assert.equal(result.ok, false);
    assert.equal(result.status, 401);
    assert.match(result.error ?? "", /Invalid or missing/);
  });

  it("reports a non-JSON body instead of throwing", async () => {
    const result = await callHost(config(), "/api/not-json");
    assert.equal(result.ok, false);
    assert.match(result.error ?? "", /non-JSON/);
  });

  it("times out rather than hanging", async () => {
    const result = await callHost(config({ timeoutMs: 150 }), "/api/slow");
    assert.equal(result.ok, false);
    assert.match(result.error ?? "", /did not answer within/);
  });

  it("reports an unreachable host", async () => {
    const result = await callHost(config({ url: "http://127.0.0.1:1" }), "/health");
    assert.equal(result.ok, false);
    assert.match(result.error ?? "", /Cannot reach host/);
  });
});

describe("tools", () => {
  it("lists agents", async () => {
    const result = await listAgents(config());
    assert.equal(result.isError, undefined);
    assert.match(result.content, /opencode/);
  });

  it("summarises a diff by file", async () => {
    const result = await gitDiff(config());
    assert.match(result.content, /dev: 1 file/);
    assert.match(result.content, /src\/a\.ts {2}\+3 -1/);
  });

  it("says a clean tree is clean instead of returning {}", () => {
    assert.equal(formatDiff({ branch: "dev", files: [] }), "No changes on dev.");
  });

  it("formats a multi-file diff with counts", () => {
    const text = formatDiff({
      branch: "dev",
      files: [
        { path: "a.ts", additions: 2, deletions: 0 },
        { path: "b.ts", additions: 0, deletions: 5 },
      ],
    });
    assert.match(text, /2 file\(s\)/);
    assert.match(text, /a\.ts {2}\+2 -0/);
    assert.match(text, /b\.ts {2}\+0 -5/);
  });

  it("reports the mesh", async () => {
    const result = await meshStatus(config());
    assert.match(result.content, /redmi-note-4/);
  });

  it("reports health with the host url", async () => {
    const result = await hostHealth(config());
    assert.match(result.content, /Host is up at http:\/\/127\.0\.0\.1:/);
  });

  it("turns a 404 into a version hint", async () => {
    const result = await callHost(config(), "/api/missing");
    assert.equal(result.ok, false);
    assert.equal(result.status, 404);
    assert.match(describeFailure("/api/missing", 404), /older build/);
  });

  it("json survives a cyclic value", () => {
    const cyclic: Record<string, unknown> = {};
    cyclic.self = cyclic;
    assert.equal(typeof json(cyclic), "string");
  });
});
