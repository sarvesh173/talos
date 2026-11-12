/**
 * The tool shape every CLI adapter shares.
 *
 * A tool is a host endpoint plus the way its payload becomes text. The
 * transport, the timeout and the error mapping are the same for every CLI and
 * live here. Which endpoints a CLI exposes, what it calls them and how it
 * describes them belong to that CLI's adapter.
 */

import { callHost, describeFailure, type HostConfig } from "./host.js";

export interface ToolResult {
  content: string;
  isError?: boolean;
}

/** Turns a host payload into the text a model reads. */
export type Render = (data: unknown, config: HostConfig) => string;

/** Tool output is text; make the shapes readable rather than one long line. */
export function renderJson(data: unknown): string {
  try {
    return JSON.stringify(data, null, 2);
  } catch {
    return String(data);
  }
}

export interface DiffPayload {
  branch?: string;
  files?: { path: string; additions: number; deletions: number }[];
}

/** Pure, so the wording is testable without a server. */
export function renderDiff(data: unknown): string {
  const payload = (data ?? {}) as DiffPayload;
  const files = payload.files ?? [];
  const branch = payload.branch ?? "the current branch";
  if (files.length === 0) {
    // An empty diff means the tree is clean. Saying so beats returning "{}",
    // which reads like a broken response.
    return `No changes on ${branch}.`;
  }
  const summary = files
    .map((file) => `  ${file.path}  +${file.additions} -${file.deletions}`)
    .join("\n");
  return `${payload.branch ?? "branch"}: ${files.length} file(s)\n${summary}`;
}

/** The host answers /health with a status word; the url belongs to the config. */
export function renderHealth(data: unknown, config: HostConfig): string {
  const status = (data as { status?: string } | undefined)?.status ?? "ok";
  return `Host is up at ${config.url} (${status}).`;
}

/** One tool, ready to be registered with a CLI. */
export interface Tool {
  /** Tool name as the CLI exposes it. */
  name: string;
  /** Model-facing description of what the tool answers. */
  description: string;
  /**
   * JSON Schema for the arguments, when the tool takes any. A tool without one
   * is registered as taking nothing, which is what most host reads need.
   */
  input?: Record<string, unknown>;
  /** Reads the host and returns text for the model. */
  run: (config: HostConfig, args?: Record<string, unknown>) => Promise<ToolResult>;
}

/**
 * One entry of a CLI's endpoint list.
 *
 * The adapter picks the path and the renderer; the call itself, the timeout
 * and the error wording are the same everywhere.
 */
export interface ToolSpec {
  /** Tool name as the CLI exposes it. */
  name: string;
  /** Model-facing description of what the tool answers. */
  description: string;
  /** Host path this tool reads. */
  path: string;
  /** Turns the host payload into text. Defaults to JSON. */
  render?: Render;
}

/** Builds a tool from one entry of an adapter's endpoint list. */
export function defineTool(spec: ToolSpec): Tool {
  const render = spec.render ?? renderJson;
  return {
    name: spec.name,
    description: spec.description,
    run: async (config) => {
      const response = await callHost<unknown>(config, spec.path);
      if (!response.ok) {
        // The model reading "401 from /api/agents/roster" can act on it,
        // whereas an exception becomes a stack trace it cannot use.
        return {
          content: describeFailure(spec.path, response.status, response.error),
          isError: true,
        };
      }
      return { content: render(response.data, config) };
    },
  };
}
