import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { Kanka, ENTITY_TYPES, KankaError } from "./kanka.js";

const typeEnum = z.enum(ENTITY_TYPES).describe("Kanka entity type (API endpoint name), e.g. characters, locations, notes");

export function buildServer(kanka: Kanka, defaultCampaign?: string) {
  const server = new McpServer({ name: "kanka", version: "1.0.0" }, {
    instructions:
      "Tools for reading and building a Kanka worldbuilding wiki. Call kanka_list_campaigns first unless a default campaign is configured. " +
      "Entry text is HTML (<p>, <h2>, <ul>...). Link entities inside entries with Kanka mentions like [character:123] or [location:45|custom text]. " +
      "Kanka rate-limits requests (default 30/min), so prefer search and targeted gets over bulk crawling.",
  });

  const campaignArg = z.number().int().optional().describe(
    defaultCampaign ? `Campaign id (default ${defaultCampaign})` : "Campaign id (required)");
  const cid = (c?: number) => {
    const v = c ?? (defaultCampaign ? Number(defaultCampaign) : undefined);
    if (!v) throw new Error("campaign_id is required (no default campaign configured)");
    return v;
  };
  const ok = (data: unknown) => ({ content: [{ type: "text" as const, text: JSON.stringify(data, null, 2) }] });
  const guard = <A>(fn: (a: A) => Promise<unknown>) => async (a: A) => {
    try { return ok(await fn(a)); }
    catch (e) {
      const msg = e instanceof KankaError || e instanceof Error ? e.message : String(e);
      return { isError: true, content: [{ type: "text" as const, text: msg }] };
    }
  };

  server.tool("kanka_list_campaigns", "List campaigns the token can access.", {}, guard(async () => {
    const r = await kanka.get("campaigns");
    return r.data.map((c: any) => ({ id: c.id, name: c.name, entry: c.entry, visibility: c.visibility, members: c.members }));
  }));

  server.tool("kanka_get_campaign", "Get one campaign's details.", { campaign_id: campaignArg },
    guard(async (a) => (await kanka.get(`campaigns/${cid(a.campaign_id)}`)).data));

  server.tool("kanka_search", "Search entities in a campaign by name. Returns id, entity_id, name, type.",
    { campaign_id: campaignArg, query: z.string().min(1) },
    guard(async (a) => {
      const r = await kanka.get(`campaigns/${cid(a.campaign_id)}/search/${encodeURIComponent(a.query)}`);
      return r.data.map((e: any) => ({ id: e.id, entity_id: e.entity_id, name: e.name, type: e.type, is_private: e.is_private }));
    }));

  server.tool("kanka_recent", "Recently edited entities in a campaign (max 10).",
    { campaign_id: campaignArg, amount: z.number().int().min(1).max(10).default(10) },
    guard(async (a) => (await kanka.get(`campaigns/${cid(a.campaign_id)}/entities/recent`, { amount: a.amount })).data));

  server.tool("kanka_list_entities",
    "List entities of one type (paginated). Filter by name (partial), type (the subtype label), tag ids, or private flag.",
    {
      campaign_id: campaignArg, type: typeEnum,
      name: z.string().optional(), subtype: z.string().optional().describe("Filter on the entity's 'type' field"),
      tags: z.array(z.number().int()).optional(), is_private: z.boolean().optional(),
      page: z.number().int().min(1).default(1),
    },
    guard(async (a) => {
      const r = await kanka.get(`campaigns/${cid(a.campaign_id)}/${a.type}`, {
        name: a.name, type: a.subtype, tags: a.tags, is_private: a.is_private === undefined ? undefined : a.is_private ? 1 : 0, page: a.page,
      });
      return {
        page: r.meta?.current_page, last_page: r.meta?.last_page, total: r.meta?.total,
        items: r.data.map((e: any) => ({ id: e.id, entity_id: e.entity_id, name: e.name, type: e.type, is_private: e.is_private, updated_at: e.updated_at })),
      };
    }));

  server.tool("kanka_get_entity",
    "Get the full record of one entity (type-specific fields plus entry HTML). Set related=true to include posts, relations, attributes, etc.",
    { campaign_id: campaignArg, type: typeEnum, id: z.number().int(), related: z.boolean().default(false) },
    guard(async (a) => (await kanka.get(`campaigns/${cid(a.campaign_id)}/${a.type}/${a.id}`, { related: a.related ? 1 : undefined })).data));

  server.tool("kanka_create_entity",
    "Create an entity. 'name' is required; 'entry' is the HTML description; 'fields' carries type-specific fields (e.g. characters: title, age, sex, pronouns, type, families[], locations[], races[], tags[]; locations: type, parent_location_id; items: location_id, character_id, price, size; notes: type; quests: type, quest_id, character_id, is_completed).",
    {
      campaign_id: campaignArg, type: typeEnum, name: z.string().min(1),
      entry: z.string().optional(), is_private: z.boolean().optional(),
      fields: z.record(z.any()).optional(),
    },
    guard(async (a) => (await kanka.post(`campaigns/${cid(a.campaign_id)}/${a.type}`,
      { ...a.fields, name: a.name, entry: a.entry, is_private: a.is_private })).data));

  server.tool("kanka_update_entity",
    "Partially update an entity. Only provided fields change. Note: 'entry' replaces the whole description.",
    {
      campaign_id: campaignArg, type: typeEnum, id: z.number().int(),
      name: z.string().optional(), entry: z.string().optional(), is_private: z.boolean().optional(),
      fields: z.record(z.any()).optional(),
    },
    guard(async (a) => {
      const body: Record<string, unknown> = { ...a.fields };
      if (a.name !== undefined) body.name = a.name;
      if (a.entry !== undefined) body.entry = a.entry;
      if (a.is_private !== undefined) body.is_private = a.is_private;
      return (await kanka.patch(`campaigns/${cid(a.campaign_id)}/${a.type}/${a.id}`, body)).data;
    }));

  server.tool("kanka_delete_entity", "Delete an entity (recoverable from Kanka's recovery list). Destructive.",
    { campaign_id: campaignArg, type: typeEnum, id: z.number().int() },
    guard(async (a) => { await kanka.del(`campaigns/${cid(a.campaign_id)}/${a.type}/${a.id}`); return { deleted: true }; }));

  // Posts (extra notes under an entity). These use the entity_id, not the type-specific id.
  const eid = z.number().int().describe("entity_id (the global entity id, not the type-specific id)");
  server.tool("kanka_list_posts", "List posts (sub-notes) on an entity.", { campaign_id: campaignArg, entity_id: eid },
    guard(async (a) => (await kanka.get(`campaigns/${cid(a.campaign_id)}/entities/${a.entity_id}/posts`)).data));
  server.tool("kanka_create_post", "Add a post to an entity. visibility_id: 1 all, 2 self, 3 admin, 4 self+admin, 5 members.",
    { campaign_id: campaignArg, entity_id: eid, name: z.string().min(1), entry: z.string().optional(), visibility_id: z.number().int().min(1).max(5).optional() },
    guard(async (a) => (await kanka.post(`campaigns/${cid(a.campaign_id)}/entities/${a.entity_id}/posts`,
      { name: a.name, entry: a.entry, visibility_id: a.visibility_id, entity_id: a.entity_id })).data));
  server.tool("kanka_update_post", "Update a post.",
    { campaign_id: campaignArg, entity_id: eid, post_id: z.number().int(), name: z.string().optional(), entry: z.string().optional(), visibility_id: z.number().int().min(1).max(5).optional() },
    guard(async (a) => {
      const { campaign_id, entity_id, post_id, ...rest } = a;
      return (await kanka.patch(`campaigns/${cid(campaign_id)}/entities/${entity_id}/posts/${post_id}`, rest)).data;
    }));
  server.tool("kanka_delete_post", "Delete a post. Destructive.",
    { campaign_id: campaignArg, entity_id: eid, post_id: z.number().int() },
    guard(async (a) => { await kanka.del(`campaigns/${cid(a.campaign_id)}/entities/${a.entity_id}/posts/${a.post_id}`); return { deleted: true }; }));

  // Relations between entities
  server.tool("kanka_list_relations", "List relations of an entity.", { campaign_id: campaignArg, entity_id: eid },
    guard(async (a) => (await kanka.get(`campaigns/${cid(a.campaign_id)}/entities/${a.entity_id}/relations`)).data));
  server.tool("kanka_create_relation", "Relate two entities (e.g. 'ally of'). attitude is -100..100.",
    { campaign_id: campaignArg, entity_id: eid, target_id: z.number().int().describe("entity_id of the other entity"),
      relation: z.string().min(1), attitude: z.number().int().min(-100).max(100).optional(), is_private: z.boolean().optional() },
    guard(async (a) => (await kanka.post(`campaigns/${cid(a.campaign_id)}/entities/${a.entity_id}/relations`,
      { owner_id: a.entity_id, target_id: a.target_id, relation: a.relation, attitude: a.attitude, is_private: a.is_private })).data));
  server.tool("kanka_delete_relation", "Delete a relation. Destructive.",
    { campaign_id: campaignArg, entity_id: eid, relation_id: z.number().int() },
    guard(async (a) => { await kanka.del(`campaigns/${cid(a.campaign_id)}/entities/${a.entity_id}/relations/${a.relation_id}`); return { deleted: true }; }));

  return server;
}
