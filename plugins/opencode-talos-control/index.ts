// Local plugin directories resolve their entrypoint at the package root and
// ignore package.json "main"/"exports" (opencode#52300). This file exists so
// the directory loads; the implementation lives in src/.
export { default } from "./src/index.js";
