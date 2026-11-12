/**
 * Tests run against a real HTTP server on a loopback port, so the timeout,
 * status mapping and JSON handling all exercise real fetch behaviour rather
 * than a mock that cannot disagree with it.
 *
 * The transport itself is tested in talos-plugin-core. What is left here is
 * what this adapter owns: the endpoint list, the tool names and the
 * descriptions the model reads.
 */

import { after, before, describe, it } from "node:test";
import assert from "node:assert/strict";
import { createServer, type Server } from "node:http";

import { readConfig, type HostConfig } from "talos-plugin-core";

import { tools } from "../dist/tools.js";

let server: Server;
let base: string;

before(async () => {
  server = createServer((req, res) => {
    const token = req.headers.authorization;

    if (req.url === "/health") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ status: "healthy" }));
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
          agents: [{ id: "opencode", installed: true, session_count: 20 }],
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

    if (req.url === "/api/mesh/status") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ peers: [{ name: "redmi-note-4", online: true }] }));
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

const tool = (name: string) => {
  const found = tools.find((candidate) => candidate.name === name);
  assert.ok(found, `no tool named ${name}`);
  return found;
};

describe("readConfig", () => {
  // The token is also read from a file, so an empty env still finds the real
  // one on a machine that has it. Point HOME at nothing to test the absence.
  const emptyEnv = { HOME: "/nonexistent-talos-test", TALOS_TOKEN_FILE: "/nonexistent-talos-test/token" };

  it("stays disabled without a token", () => {
    assert.equal(readConfig(emptyEnv).enabled, false);
  });

  it("enables when a token is present and strips trailing slashes", () => {
    const cfg = readConfig({ ...emptyEnv, TALOS_TOKEN: "abc", TALOS_URL: "http://x:1///" });
    assert.equal(cfg.enabled, true);
    assert.equal(cfg.url, "http://x:1");
  });

  it("TALOS_DISABLED wins over a token", () => {
    assert.equal(readConfig({ ...emptyEnv, TALOS_TOKEN: "abc", TALOS_DISABLED: "1" }).enabled, false);
  });
});

describe("the endpoint list", () => {
  it("registers the five tools OpenCode expects", () => {
    assert.deepEqual(
      tools.map((tool) => tool.name),
      ["agents", "processes", "diff", "mesh", "health"],
    );
  });

  it("gives every tool a description the model can act on", () => {
    for (const tool of tools) {
      assert.ok(tool.description.length > 20, `${tool.name} has no usable description`);
    }
  });

  it("gives every tool a unique name", () => {
    const names = new Set(tools.map((tool) => tool.name));
    assert.equal(names.size, tools.length);
  });
});

describe("the tools", () => {
  it("lists agents", async () => {
    const result = await tool("agents").run(config());
    assert.equal(result.isError, undefined);
    assert.match(result.content, /opencode/);
  });

  it("lists processes", async () => {
    const result = await tool("processes").run(config());
    assert.equal(result.isError, undefined);
    assert.match(result.content, /processes/);
  });

  it("summarises a diff by file", async () => {
    const result = await tool("diff").run(config());
    assert.match(result.content, /dev: 1 file/);
    assert.match(result.content, /src\/a\.ts {2}\+3 -1/);
  });

  it("reports the mesh", async () => {
    const result = await tool("mesh").run(config());
    assert.match(result.content, /redmi-note-4/);
  });

  it("reports health with the host url", async () => {
    const result = await tool("health").run(config());
    assert.match(result.content, /Host is up at http:\/\/127\.0\.0\.1:/);
  });

  it("marks a failed call as an error instead of throwing", async () => {
    const result = await tool("agents").run(config({ token: "wrong" }));
    assert.equal(result.isError, true);
    assert.match(result.content, /401/);
  });
});
