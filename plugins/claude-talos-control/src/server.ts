/**
 * claude-talos-control
 *
 * Gives Claude Code read access to the TALOS host it is running against, so
 * the session can answer "what is the board", "what changed", "is the host up"
 * from the host's own state instead of guessing from the repository.
 *
 * Claude Code loads this as a plugin and starts the MCP server over stdio.
 * The transport, the timeout and the error mapping come from
 * talos-plugin-core; the endpoint list in tools.ts is Claude Code's own.
 */

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { readConfig } from "talos-plugin-core";

import { tools } from "./tools.js";

const config = readConfig();

const server = new McpServer({ name: "talos", version: "0.1.0" });

if (!config.enabled) {
  // Register nothing rather than four tools that each fail with the same
  // message. One line on stderr tells the user why they are missing.
  process.stderr.write(
    "[talos.control] inactive (set TALOS_TOKEN to enable; TALOS_DISABLED=1 opts out)\n",
  );
} else {
  process.stderr.write(`[talos.control] host ${config.url}\n`);

  for (const tool of tools) {
    server.registerTool(
      tool.name,
      { description: tool.description },
      async () => {
        const result = await tool.run(config);
        return {
          content: [{ type: "text" as const, text: result.content }],
          isError: result.isError,
        };
      },
    );
  }
}

await server.connect(new StdioServerTransport());
