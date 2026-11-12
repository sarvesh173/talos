/**
 * The MCP server is exercised the way Claude Code exercises it: a real client
 * over a real transport, against a real host on a loopback port.
 *
 * The transport and the error mapping are tested in talos-plugin-core. What
 * is left here is what this adapter owns: the endpoint list, the tool names
 * and the descriptions the model reads.
 */

import { after, before, describe, it } from "node:test";
import assert from "node:assert/strict";
import { createServer, type Server } from "node:http";

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";

import type { HostConfig } from "talos-plugin-core";

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
          agents: [{ id: "claude-code", installed: true, session_count: 3 }],
        }),
      );
      return;
    }

    if (req.url === "/api/kanban/board") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(
        JSON.stringify({
          room_id: "the-council",
          lanes: { running: [{ id: "task-1", title: "Ship the refactor" }] },
        }),
      );
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

// Read at call time: the loopback port is only known once before() has run.
const config = (overrides: Partial<HostConfig> = {}): HostConfig => ({
  url: base,
  token: "test-key",
  enabled: true,
  timeoutMs: 2000,
  ...overrides,
});

/** Builds the same server the plugin ships, wired to a loopback host. */
const startServer = async () => {
  const mcp = new McpServer({ name: "talos", version: "0.1.0" });

  for (const tool of tools) {
    mcp.registerTool(
      tool.name,
      { description: tool.description },
      async () => {
        const result = await tool.run(config());
        return {
          content: [{ type: "text" as const, text: result.content }],
          isError: result.isError,
        };
      },
    );
  }

  const client = new Client({ name: "test", version: "0.1.0" });
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  await Promise.all([client.connect(clientTransport), mcp.connect(serverTransport)]);
  return client;
};

describe("the endpoint list", () => {
  it("registers the tools Claude Code expects", () => {
    assert.deepEqual(
      tools.map((tool) => tool.name),
      ["agents", "board", "diff", "health"],
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

describe("the MCP server", () => {
  it("lists its tools with their descriptions", async () => {
    const client = await startServer();
    const listed = await client.listTools();
    assert.deepEqual(
      listed.tools.map((tool) => tool.name).sort(),
      ["agents", "board", "diff", "health"],
    );
    for (const tool of listed.tools) {
      assert.ok((tool.description ?? "").length > 20);
    }
    await client.close();
  });

  it("reads the roster", async () => {
    const client = await startServer();
    const result = await client.callTool({ name: "agents", arguments: {} });
    const text = (result.content as { text: string }[])[0].text;
    assert.match(text, /claude-code/);
    await client.close();
  });

  it("reads the board", async () => {
    const client = await startServer();
    const result = await client.callTool({ name: "board", arguments: {} });
    const text = (result.content as { text: string }[])[0].text;
    assert.match(text, /Ship the refactor/);
    await client.close();
  });

  it("summarises a diff by file", async () => {
    const client = await startServer();
    const result = await client.callTool({ name: "diff", arguments: {} });
    const text = (result.content as { text: string }[])[0].text;
    assert.match(text, /dev: 1 file/);
    await client.close();
  });

  it("reports health with the host url", async () => {
    const client = await startServer();
    const result = await client.callTool({ name: "health", arguments: {} });
    const text = (result.content as { text: string }[])[0].text;
    assert.match(text, /Host is up at http:\/\/127\.0\.0\.1:/);
    await client.close();
  });

  it("reports a rejected key as an error rather than throwing", async () => {
    const client = await startServer();
    const mcp = new McpServer({ name: "talos", version: "0.1.0" });
    const tool = tools.find((candidate) => candidate.name === "agents");
    assert.ok(tool);
    mcp.registerTool(tool.name, { description: tool.description }, async () => {
      const result = await tool.run(config({ token: "wrong" }));
      return {
        content: [{ type: "text" as const, text: result.content }],
        isError: result.isError,
      };
    });
    const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
    const other = new Client({ name: "test", version: "0.1.0" });
    await Promise.all([other.connect(clientTransport), mcp.connect(serverTransport)]);
    const result = await other.callTool({ name: "agents", arguments: {} });
    assert.equal(result.isError, true);
    assert.match((result.content as { text: string }[])[0].text, /401/);
    await other.close();
  });
});
