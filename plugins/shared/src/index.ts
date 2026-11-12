/**
 * talos-plugin-core
 *
 * The half of a TALOS plugin that does not depend on the CLI it is written
 * for: reading the host's configuration from the environment, calling the
 * host with a timeout, mapping failures to text a model can act on, and the
 * shape a tool takes.
 *
 * Each CLI adapter (OpenCode, Claude Code, ...) owns its own endpoint list:
 * which host paths it exposes, what it calls them and how it describes them.
 */

export {
  callHost,
  describeFailure,
  readConfig,
  type HostConfig,
  type HostResponse,
} from "./host.js";

export {
  defineTool,
  renderDiff,
  renderHealth,
  renderJson,
  type DiffPayload,
  type Render,
  type Tool,
  type ToolResult,
  type ToolSpec,
} from "./tool.js";
