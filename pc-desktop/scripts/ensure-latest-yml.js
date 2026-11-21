// electron-builder normally writes dist/latest.yml (the file electron-updater reads).
// This script checks it is there and creates it from the installer if it is not,
// so a release can never ship without update metadata.
const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");

const dist = path.resolve(__dirname, "..", "dist");
const ymlPath = path.join(dist, "latest.yml");

if (fs.existsSync(ymlPath)) {
  console.log("latest.yml already exists, nothing to do.");
  process.exit(0);
}

const exe = fs.readdirSync(dist).find((f) => /^Omini-POS-Setup-.*\.exe$/.test(f));
if (!exe) {
  console.error("No installer found in dist/, cannot create latest.yml");
  process.exit(1);
}

const version = exe.replace(/^Omini-POS-Setup-/, "").replace(/\.exe$/, "");
const buf = fs.readFileSync(path.join(dist, exe));
const sha512 = crypto.createHash("sha512").update(buf).digest("base64");

const yml = [
  `version: ${version}`,
  "files:",
  `  - url: ${exe}`,
  `    sha512: ${sha512}`,
  `    size: ${buf.length}`,
  `path: ${exe}`,
  `sha512: ${sha512}`,
  `releaseDate: '${new Date().toISOString()}'`,
  ""
].join("\n");

fs.writeFileSync(ymlPath, yml);
console.log(`Created latest.yml for ${exe}`);
