/**
 * opencode-talos-control
 *
 * Gives OpenCode read access to the TALOS host it is running against, so the
 * agent can answer "what is on the board", "what changed", "is the mesh up"
 * from the host's own state instead of guessing from the repository.
 *
 * Install:
 *     opencode plugin add ./plugins/opencode-talos-control
 *
 * Configure with environment variables (never committed):
 *     TALOS_URL    default http://127.0.0.1:8000
 *     TALOS_TOKEN  host API key — without this every tool reports it is unset
 *     TALOS_DISABLED=1 to keep the plugin installed but register nothing
 */

import { Plugin } from "@opencode/plugin";
import { readConfig } from "./host.js";
import {
  gitDiff,
  hostHealth,
  json,
  listAgents,
  listProcesses,
  meshStatus,
} from "./tools.js";

const PLUGIN_ID = "talos.control";

export default Plugin.define({
  id: PLUGIN_ID,

  async setup(ctx) {
    const config = readConfig();

    if (!config.enabled) {
      // Register nothing rather than five tools that each fail with the same
      // message. One line in the log tells the user why they are missing.
      console.log(
        `[${PLUGIN_ID}] inactive (set TALOS_TOKEN to enable; TALOS_DISABLED=1 opts out)`,
      );
      return;
    }

    console.log(`[${PLUGIN_ID}] host ${config.url}`);

    await ctx.tool.transform((editor) => {
      editor.namespace({
        name: "talos",
        description: "Read TALOS host state: agents, processes, diff, mesh",
      });

      editor.add({
        name: "agents",
        description:
          "List the agent engines the TALOS host has discovered, with their real session counts. Engines that are not installed are included with the reason.",
        input: { type: "object", properties: {}, additionalProperties: false },
        options: { namespace: "talos" },
        execute: async () => ({ content: (await listAgents(config)).content }),
      });

      editor.add({
        name: "processes",
        description:
          "List the long-running processes the TALOS host is supervising, with their status.",
        input: { type: "object", properties: {}, additionalProperties: false },
        options: { namespace: "talos" },
        execute: async () => ({ content: (await listProcesses(config)).content }),
      });

      editor.add({
        name: "diff",
        description:
          "Show the uncommitted changes in the repository the TALOS host runs in, grouped by file with additions and deletions.",
        input: { type: "object", properties: {}, additionalProperties: false },
        options: { namespace: "talos" },
        execute: async () => {
          const result = await gitDiff(config);
          return { content: result.content, isError: result.isError };
        },
      });

      editor.add({
        name: "mesh",
        description:
          "Show the Tailscale mesh as the TALOS host sees it: peers, their addresses, and whether they are online.",
        input: { type: "object", properties: {}, additionalProperties: false },
        options: { namespace: "talos" },
        execute: async () => ({ content: (await meshStatus(config)).content }),
      });

      editor.add({
        name: "health",
        description:
          "Check that the TALOS host is answering. Use this first when another talos tool fails, to tell a host problem apart from a bad request.",
        input: { type: "object", properties: {}, additionalProperties: false },
        options: { namespace: "talos" },
        execute: async () => {
          const result = await hostHealth(config);
          return { content: result.content, isError: result.isError };
        },
      });
    });

    return () => {
      // Registrations are disposed with the plugin; nothing else is held.
    };
  },
});

export { json };
