// OpenCode resolves a local plugin directory at the package root and ignores
// package.json "main"/"exports" (opencode#52300), and its loader does not see
// node_modules, so a workspace import like "talos-plugin-core" cannot resolve.
// dist/plugin.js is the bundle with that code inlined: one file, one import.
export { default } from "./dist/plugin.js";
