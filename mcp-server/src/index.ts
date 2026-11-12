#!/usr/bin/env node
import http from "node:http";
import { timingSafeEqual } from "node:crypto";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { Kanka } from "./kanka.js";
import { buildServer } from "./server.js";

const url = process.env.KANKA_URL;
const token = process.env.KANKA_TOKEN;
if (!url || !token) {
  console.error("Set KANKA_URL (e.g. https://kanka.example.com) and KANKA_TOKEN (Kanka > Profile > API). Optional: KANKA_CAMPAIGN_ID, MCP_TRANSPORT=http, MCP_PORT, MCP_AUTH_TOKEN.");
  process.exit(1);
}
const kanka = new Kanka(url, token);
const campaign = process.env.KANKA_CAMPAIGN_ID;

if ((process.env.MCP_TRANSPORT ?? "stdio") === "http") {
  const port = Number(process.env.MCP_PORT ?? 3333);
  const secret = process.env.MCP_AUTH_TOKEN;
  if (!secret) {
    console.error("MCP_AUTH_TOKEN is required in http mode (agents send it as 'Authorization: Bearer <token>').");
    process.exit(1);
  }
  const authed = (h?: string) => {
    const got = Buffer.from((h ?? "").replace(/^Bearer\s+/i, ""));
    const want = Buffer.from(secret);
    return got.length === want.length && timingSafeEqual(got, want);
  };
  http.createServer(async (req, res) => {
    if (req.url === "/health") { res.writeHead(200).end("ok"); return; }
    if (!req.url?.startsWith("/mcp")) { res.writeHead(404).end(); return; }
    if (!authed(req.headers.authorization)) { res.writeHead(401, { "WWW-Authenticate": "Bearer" }).end("unauthorized"); return; }
    if (req.method !== "POST") { res.writeHead(405, { Allow: "POST" }).end(); return; }
    const chunks: Buffer[] = [];
    for await (const c of req) chunks.push(c as Buffer);
    let body: unknown;
    try { body = JSON.parse(Buffer.concat(chunks).toString() || "{}"); } catch { res.writeHead(400).end("bad json"); return; }
    // Stateless: a fresh server + transport per request.
    const server = buildServer(kanka, campaign);
    const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });
    res.on("close", () => { transport.close(); server.close(); });
    await server.connect(transport);
    await transport.handleRequest(req, res, body);
  }).listen(port, () => console.error(`kanka-mcp listening on :${port}/mcp`));
} else {
  await buildServer(kanka, campaign).connect(new StdioServerTransport());
}
