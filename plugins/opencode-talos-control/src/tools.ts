/**
 * The tools this plugin adds to OpenCode.
 *
 * Each entry is one host endpoint. The names, the descriptions and the choice
 * of endpoint belong to OpenCode; the call, the timeout and the error mapping
 * come from talos-plugin-core, so a second CLI adapter reuses them instead of
 * copying them.
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
    name: "processes",
    description:
      "List the long-running processes the TALOS host is supervising, with their status.",
    path: "/api/processes",
  }),
  defineTool({
    name: "diff",
    description:
      "Show the uncommitted changes in the repository the TALOS host runs in, grouped by file with additions and deletions.",
    path: "/api/git/diff",
    render: renderDiff,
  }),
  defineTool({
    name: "mesh",
    description:
      "Show the Tailscale mesh as the TALOS host sees it: peers, their addresses, and whether they are online.",
    path: "/api/mesh/status",
  }),
  defineTool({
    name: "health",
    description:
      "Check that the TALOS host is answering. Use this first when another talos tool fails, to tell a host problem apart from a bad request.",
    path: "/health",
    render: renderHealth,
  }),
];
