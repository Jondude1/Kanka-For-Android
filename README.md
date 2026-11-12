# Kanka for Android

Android app for managing a self-hosted [Kanka](https://kanka.io) world, plus an MCP server so AI agents can worldbuild in the same wiki.

## App
- Connect with your server URL and a Kanka API token
- Pick a campaign, browse 16 entity types, search, view, create, edit and delete
- Per-entity posts, private flag, HTML entry editor with preview
- Updates itself: checks this repo's latest GitHub Release and installs the APK

Install: download the APK from the [latest release](../../releases/latest) once; after that the app updates itself
(allow "install unknown apps" for it when asked).

## MCP server
See [`mcp-server/`](mcp-server/README.md). Run it next to Kanka (Docker image `ghcr.io/jondude1/kanka-mcp`) or locally over stdio.

## Build
GitHub Actions builds and publishes a signed APK on every push to `main`. The signing key in `keystore/` is committed
on purpose (sideloaded app) and must never change, or updates will be rejected.
