/**
 * The tools this adapter adds to Claude Code.
 *
 * Each entry is one host endpoint. The names, the descriptions and the choice
 * of endpoint belong to Claude Code; the call, the timeout and the error
 * mapping come from talos-plugin-core, so a second CLI adapter reuses them
 * instead of copying them.
 *
 * MCP tool names are namespaced by the server ("talos"), so the names here
 * are the part after `mcp__talos__`.
 */

import { defineTool, renderDiff, renderHealth, type Tool } from "talos-plugin-core";

export const tools: Tool[] = [
  defineTool({
    name: "agents",
    description:
      "List the agent engines the TALOS host has discovered, with their real session counts. Engines that are not installed are included with the reason.",
    path: "/api/agents/roster",
  }),
  defineTool({
    name: "board",
    description:
      "Show the Kanban board the TALOS host is running: the tasks in each lane and the agent activity behind them.",
    path: "/api/kanban/board",
  }),
  defineTool({
    name: "diff",
    description:
      "Show the uncommitted changes in the repository the TALOS host runs in, grouped by file with additions and deletions.",
    path: "/api/git/diff",
    render: renderDiff,
  }),
  defineTool({
    name: "health",
    description:
      "Check that the TALOS host is answering. Use this first when another talos tool fails, to tell a host problem apart from a bad request.",
    path: "/health",
    render: renderHealth,
  }),
];
