import test from "node:test";
import assert from "node:assert/strict";
import { redact, redactString, preview, REDACTED } from "../src/redact.ts";

test("strips an OpenAI-style key", () => {
  const out = redactString("key sk-proj-abcdef1234567890ABCDEF here");
  assert.ok(!out.includes("sk-proj-abcdef1234567890ABCDEF"));
  assert.ok(out.includes(REDACTED));
});

test("strips an OpenRouter key", () => {
  const out = redactString("Authorization: Bearer sk-or-v1-9f8e7d6c5b4a39281706aabbcc");
  assert.ok(!out.includes("9f8e7d6c5b4a39281706aabbcc"));
});

test("strips an Anthropic key", () => {
  const out = redactString("sk-ant-api03-ZZZZ1234567890abcdefghij");
  assert.ok(!out.includes("ZZZZ1234567890abcdefghij"));
});

test("strips a GitHub PAT", () => {
  const out = redactString("token github_pat_11ABCDEFG0123456789_abcdefghijklmnop");
  assert.ok(!out.includes("11ABCDEFG0123456789"));
});

test("strips an AWS access key id", () => {
  const out = redactString("AKIAIOSFODNN7EXAMPLE");
  assert.equal(out, REDACTED);
});

test("keeps the label but drops the value on an assignment", () => {
  const out = redactString("api_key=supersecretvalue123456");
  assert.ok(out.toLowerCase().includes("api_key"));
  assert.ok(!out.includes("supersecretvalue123456"));
});

test("leaves ordinary text alone", () => {
  assert.equal(redactString("npm run build"), "npm run build");
  assert.equal(redactString("src/components/Button.tsx"), "src/components/Button.tsx");
});

test("leaves a git SHA alone", () => {
  const sha = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0";
  assert.equal(redactString(sha), sha);
});

test("redacts nested objects", () => {
  const payload = {
    headers: { Authorization: "Bearer sk_live_abcdef1234567890" },
    items: ["plain", "sk-proj-zzzzzzzzzzzzzzzzzzzz"],
  };
  const out = JSON.stringify(redact(payload));
  assert.ok(!out.includes("sk_live_abcdef1234567890"));
  assert.ok(!out.includes("sk-proj-zzzzzzzzzzzzzzzzzzzz"));
  assert.ok(out.includes("plain"));
});

test("redacts credential-named keys regardless of value shape", () => {
  const out = redact({ apiKey: "anything-at-all", token: "x", nested: { password: "hunter2" } });
  const text = JSON.stringify(out);
  assert.ok(!text.includes("anything-at-all"));
  assert.ok(!text.includes("hunter2"));
});

test("survives a cyclic object without hanging", () => {
  const cyclic: Record<string, unknown> = { name: "x" };
  cyclic.self = cyclic;
  assert.doesNotThrow(() => redact(cyclic));
});

test("does not throw on exotic values", () => {
  assert.doesNotThrow(() => redact(undefined));
  assert.doesNotThrow(() => redact(null));
  assert.doesNotThrow(() => redact(42));
  assert.doesNotThrow(() => redact(Symbol("x")));
  assert.doesNotThrow(() => redact(() => 1));
});

test("preview collapses whitespace and truncates", () => {
  assert.equal(preview({ a: 1 }), '{"a":1}');
  assert.equal(preview("line1\nline2"), "line1 line2");
  const long = preview("x".repeat(500), 40);
  assert.ok(long.length <= 40);
  assert.ok(long.endsWith("…"));
});

test("preview of non-serialisable input does not throw", () => {
  const cyclic: Record<string, unknown> = {};
  cyclic.self = cyclic;
  assert.doesNotThrow(() => preview(cyclic));
});

test("repeated calls do not leak regex state", () => {
  // Module-level /g regexes carry lastIndex; two identical calls must agree.
  const a = redactString("sk-proj-abcdef1234567890ABCDEF");
  const b = redactString("sk-proj-abcdef1234567890ABCDEF");
  assert.equal(a, b);
});
