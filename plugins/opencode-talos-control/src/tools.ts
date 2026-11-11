/**
 * The tools this plugin adds to OpenCode.
 *
 * Each one maps to a host endpoint that already exists. Where the host answers
 * with an error, the tool returns that error as text: the model reading "401
 * from /api/agents/roster" can act on it, whereas an exception becomes a stack
 * trace the model cannot use.
 */

import { callHost, type HostConfig } from "./host.js";

export interface ToolResult {
  content: string;
  isError?: boolean;
}

const ok = (content: string): ToolResult => ({ content });
const fail = (content: string): ToolResult => ({ content, isError: true });

/** Tool output is text; make the shapes readable rather than one long line. */
export function json(value: unknown, indent = 2): string {
  try {
    return JSON.stringify(value, null, indent);
  } catch {
    return String(value);
  }
}

export function describeFailure(path: string, status: number, error?: string): string {
  if (status === 401) {
    return `TALOS rejected the API key (401 from ${path}). The key in TALOS_TOKEN is wrong or revoked.`;
  }
  if (status === 404) {
    return `The host has no ${path} endpoint (404). It is running an older build than this plugin expects.`;
  }
  return `${path} failed: ${error ?? "unknown error"}${status ? ` (HTTP ${status})` : ""}`;
}

export async function listAgents(config: HostConfig): Promise<ToolResult> {
  const path = "/api/agents/roster";
  const response = await callHost<unknown>(config, path);
  if (!response.ok) return fail(describeFailure(path, response.status, response.error));
  return ok(json(response.data));
}

export async function listProcesses(config: HostConfig): Promise<ToolResult> {
  const path = "/api/processes";
  const response = await callHost<unknown>(config, path);
  if (!response.ok) return fail(describeFailure(path, response.status, response.error));
  return ok(json(response.data));
}

export interface DiffPayload {
  branch?: string;
  files?: { path: string; additions: number; deletions: number }[];
}

/** Pure, so the wording is testable without a server. */
export function formatDiff(data: DiffPayload | undefined): string {
  const files = data?.files ?? [];
  const branch = data?.branch ?? "the current branch";
  if (files.length === 0) {
    // An empty diff means the tree is clean. Saying so beats returning "{}",
    // which reads like a broken response.
    return `No changes on ${branch}.`;
  }
  const summary = files
    .map((file) => `  ${file.path}  +${file.additions} -${file.deletions}`)
    .join("\n");
  return `${data?.branch ?? "branch"}: ${files.length} file(s)\n${summary}`;
}

export async function gitDiff(config: HostConfig): Promise<ToolResult> {
  // The host diffs the repository its own core runs in; there is no repo
  // parameter, so offering one here would be a lie.
  const path = "/api/git/diff";
  const response = await callHost<DiffPayload>(config, path);
  if (!response.ok) return fail(describeFailure(path, response.status, response.error));
  return ok(formatDiff(response.data));
}

export async function meshStatus(config: HostConfig): Promise<ToolResult> {
  const path = "/api/mesh/status";
  const response = await callHost<unknown>(config, path);
  if (!response.ok) return fail(describeFailure(path, response.status, response.error));
  return ok(json(response.data));
}

export async function hostHealth(config: HostConfig): Promise<ToolResult> {
  const path = "/health";
  const response = await callHost<{ status?: string }>(config, path);
  if (!response.ok) return fail(describeFailure(path, response.status, response.error));
  return ok(`Host is up at ${config.url} (${response.data?.status ?? "ok"}).`);
}
