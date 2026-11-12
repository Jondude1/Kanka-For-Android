// Thin client for the Kanka REST API (self-hosted or kanka.io).

export const ENTITY_TYPES = [
  "characters", "locations", "families", "organisations", "items", "notes",
  "events", "calendars", "timelines", "creatures", "races", "quests", "maps",
  "journals", "abilities", "tags", "conversations", "attribute_templates",
] as const;
export type EntityType = (typeof ENTITY_TYPES)[number];

export class KankaError extends Error {
  constructor(public status: number, message: string, public body?: unknown) {
    super(message);
  }
}

export class Kanka {
  private base: string;
  constructor(url: string, private token: string) {
    let u = url.trim().replace(/\/+$/, "");
    if (!/\/api\/\d/.test(u)) u += "/api/1.0";
    this.base = u;
  }

  async request(method: string, path: string, opts: { query?: Record<string, unknown>; body?: unknown } = {}): Promise<any> {
    const url = new URL(this.base + "/" + path.replace(/^\/+/, ""));
    for (const [k, v] of Object.entries(opts.query ?? {})) {
      if (v === undefined || v === null || v === "") continue;
      if (Array.isArray(v)) v.forEach((x) => url.searchParams.append(`${k}[]`, String(x)));
      else url.searchParams.set(k, String(v));
    }
    for (let attempt = 0; attempt < 4; attempt++) {
      const res = await fetch(url, {
        method,
        headers: {
          Authorization: `Bearer ${this.token}`,
          Accept: "application/json",
          ...(opts.body !== undefined ? { "Content-Type": "application/json" } : {}),
        },
        body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
      });
      if (res.status === 429) {
        const wait = Number(res.headers.get("retry-after") ?? "") || 2 * (attempt + 1);
        await new Promise((r) => setTimeout(r, Math.min(wait, 30) * 1000));
        continue;
      }
      const text = await res.text();
      let json: any = undefined;
      try { json = text ? JSON.parse(text) : undefined; } catch { /* non-JSON */ }
      if (!res.ok) {
        const msg = json?.message ?? json?.error ?? text.slice(0, 300) ?? res.statusText;
        throw new KankaError(res.status, `Kanka ${res.status} on ${method} ${path}: ${msg}`, json ?? text);
      }
      return json;
    }
    throw new KankaError(429, "Kanka rate limit: still throttled after retries");
  }

  get = (p: string, query?: Record<string, unknown>) => this.request("GET", p, { query });
  post = (p: string, body: unknown) => this.request("POST", p, { body });
  patch = (p: string, body: unknown) => this.request("PATCH", p, { body });
  del = (p: string) => this.request("DELETE", p);
}
