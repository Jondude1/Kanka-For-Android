# Kanka MCP server

Lets AI agents (Claude, Hermes, etc.) search, read and edit a self-hosted [Kanka](https://kanka.io) world through MCP.

## Configure

| Variable | Purpose |
|---|---|
| `KANKA_URL` | Site root of your Kanka, e.g. `https://kanka.example.com` (`/api/1.0` is added) |
| `KANKA_TOKEN` | Personal API token (Kanka → Profile → API) |
| `KANKA_CAMPAIGN_ID` | Optional default campaign so agents can skip `campaign_id` |
| `MCP_TRANSPORT` | `stdio` (default) or `http` |
| `MCP_PORT` / `MCP_AUTH_TOKEN` | HTTP mode only. The auth token is required; agents send it as `Authorization: Bearer …` |

## Run

**stdio** (agent launches it itself):
```bash
npm ci && npm run build
```
```json
{ "mcpServers": { "kanka": {
  "command": "node", "args": ["/path/to/mcp-server/dist/index.js"],
  "env": { "KANKA_URL": "https://kanka.example.com", "KANKA_TOKEN": "…", "KANKA_CAMPAIGN_ID": "1" } } } }
```

**HTTP** (always-on on your server, many agents can connect), endpoint `POST /mcp`, health check `GET /health`:
```bash
docker compose -f docker-compose.example.yml up -d
```
Point agents at `http://<host>:3333/mcp` with the bearer token. Keep it on your LAN/VPN or behind a TLS reverse proxy.

## Tools

`kanka_list_campaigns`, `kanka_get_campaign`, `kanka_search`, `kanka_recent`, `kanka_list_entities`, `kanka_get_entity`,
`kanka_create_entity`, `kanka_update_entity`, `kanka_delete_entity`, `kanka_list_posts`, `kanka_create_post`,
`kanka_update_post`, `kanka_delete_post`, `kanka_list_relations`, `kanka_create_relation`, `kanka_delete_relation`.

Entity types: characters, locations, families, organisations, items, notes, events, calendars, timelines, creatures,
races, quests, maps, journals, abilities, tags, conversations, attribute_templates.

Notes: entries are HTML; link entities with mentions like `[character:123]`. Posts and relations use the global
`entity_id`, not the per-type id. Kanka limits each user to 30 requests/minute by default (the server backs off and retries on 429).
