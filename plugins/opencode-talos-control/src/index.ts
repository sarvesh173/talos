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
import { readConfig } from "talos-plugin-core";

import { tools } from "./tools.js";

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

      for (const tool of tools) {
        editor.add({
          name: tool.name,
          description: tool.description,
          input: { type: "object", properties: {}, additionalProperties: false },
          options: { namespace: "talos" },
          execute: async () => {
            const result = await tool.run(config);
            return { content: result.content, isError: result.isError };
          },
        });
      }
    });

    return () => {
      // Registrations are disposed with the plugin; nothing else is held.
    };
  },
});
