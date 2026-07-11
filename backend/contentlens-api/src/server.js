import http from "node:http";
import { fileURLToPath } from "node:url";

const PORT = Number.parseInt(process.env.PORT || "8787", 10);
const DOG_API_KEY = process.env.DOES_THE_DOG_DIE_API_KEY || "";
const DOG_BASE_URL = (process.env.DOES_THE_DOG_DIE_BASE_URL || "https://www.doesthedogdie.com/api/v3").replace(/\/$/, "");
const CACHE_TTL_MS = Number.parseInt(process.env.CACHE_TTL_SECONDS || "21600", 10) * 1000;
const RATE_LIMIT_PER_MINUTE = Number.parseInt(process.env.RATE_LIMIT_PER_MINUTE || "60", 10);
const cache = new Map();
const rateLimits = new Map();

export function createServer() {
  return http.createServer(async (req, res) => {
    try {
      setCors(res);
      if (req.method === "OPTIONS") return sendJson(res, 204, {});
      if (!allowRequest(req)) return sendError(res, 429, "rate_limited", "Too many requests.");

      const url = new URL(req.url ?? "/", `http://${req.headers.host ?? "localhost"}`);
      if (req.method === "GET" && url.pathname === "/health") {
        return sendJson(res, 200, { ok: true, service: "contentlens-api" });
      }

      const match = url.pathname.match(/^\/v1\/safety\/tmdb\/(movie|tv)\/(\d+)$/);
      if (req.method === "GET" && match) {
        return handleSafetyRequest(res, match[1], Number.parseInt(match[2], 10), url);
      }

      return sendError(res, 404, "not_found", "Endpoint not found.");
    } catch (error) {
      console.error("Unhandled request failure", scrub(error));
      return sendError(res, 500, "server_error", "ContentLens API could not complete the request.");
    }
  });
}

async function handleSafetyRequest(res, mediaType, tmdbId, url) {
  if (!DOG_API_KEY) {
    return sendError(res, 503, "provider_not_configured", "DoesTheDogDie API key is not configured on the server.");
  }

  const title = (url.searchParams.get("title") || "").trim();
  const year = (url.searchParams.get("year") || "").trim();
  const cacheKey = `${mediaType}:${tmdbId}:${title.toLowerCase()}:${year}`;
  const cached = cache.get(cacheKey);
  if (cached && cached.expiresAt > Date.now()) {
    return sendJson(res, 200, { ...cached.value, cached: true });
  }

  const item = await findDogItem(mediaType, tmdbId, title, year);
  if (!item) {
    return sendError(res, 404, "no_safety_match", "No DoesTheDogDie match was found for this TMDB title.");
  }

  const detail = await dogFetchJson(`/items/${item.id}`);
  const report = normalizeDogReport(detail);
  cache.set(cacheKey, { value: report, expiresAt: Date.now() + CACHE_TTL_MS });
  return sendJson(res, 200, report);
}

async function findDogItem(mediaType, tmdbId, title, year) {
  const byTmdb = await dogFetchJson(`/items?tmdb=${encodeURIComponent(String(tmdbId))}`);
  const tmdbMatch = asArray(byTmdb).find((item) => item.tmdbId === tmdbId && dogTypeMatches(item.itemTypeName, mediaType));
  if (tmdbMatch) return tmdbMatch;

  if (!title) return null;
  const namePath = `/items?name=${encodeURIComponent(title)}${year ? `&releaseYear=${encodeURIComponent(year)}` : ""}`;
  const byName = await dogFetchJson(namePath);
  return asArray(byName).find((item) => dogTypeMatches(item.itemTypeName, mediaType)) ?? null;
}

async function dogFetchJson(path) {
  const response = await fetch(`${DOG_BASE_URL}${path}`, {
    headers: {
      "X-API-KEY": DOG_API_KEY,
      "Accept": "application/json"
    }
  });
  const text = await response.text();
  if (!response.ok) {
    console.warn(`DoesTheDogDie request failed status=${response.status} path=${path.split("?")[0]} body=${text.slice(0, 160)}`);
    const code = response.status === 401 ? "provider_auth_failed"
      : response.status === 403 ? "provider_upgrade_required"
      : response.status === 429 ? "provider_rate_limited"
      : "provider_error";
    throw Object.assign(new Error(code), { status: response.status, code });
  }
  return JSON.parse(text);
}

export function normalizeDogReport(detail) {
  const entries = asArray(detail.topicItemStats)
    .map(normalizeStat)
    .filter(Boolean)
    .sort((a, b) => b.severityScore - a.severityScore || b.yesCount - a.yesCount)
    .slice(0, 12)
    .map(({ severityScore, ...entry }) => entry);

  return {
    source: "DoesTheDogDie community",
    sourceItemId: Number(detail.id) || 0,
    sourceItemName: detail.name || "Matched title",
    reportCount: entries.reduce((sum, item) => sum + item.yesCount + item.noCount, 0),
    entries,
    attribution: "Content warnings are provided by DoesTheDogDie community data.",
    sourceUrl: "https://www.doesthedogdie.com/"
  };
}

function normalizeStat(stat) {
  const topicId = Number(stat.topicId) || 0;
  const topicName = stat.topicName || "";
  const yesCount = Math.max(Number(stat.yesSum) || 0, 0);
  const noCount = Math.max(Number(stat.noSum) || 0, 0);
  const commentCount = Math.max(Number(stat.numComments) || 0, 0);
  if (!topicId || !topicName || (yesCount === 0 && noCount === 0)) return null;
  const severity = inferSeverity(yesCount, noCount, commentCount);
  if (severity === "None") return null;
  return {
    topicId,
    topicName,
    category: mapTopicToCategory(topicName),
    severity,
    severityScore: severityScore(severity),
    yesCount,
    noCount,
    commentCount,
    explanation: explainTopic(topicName, yesCount, noCount, commentCount)
  };
}

export function mapTopicToCategory(topicName) {
  const name = topicName.toLowerCase();
  if (/(dog|cat|animal|pet|horse|rabbit)/.test(name)) return "AnimalHarm";
  if (/(sexual assault|rape|pedophilia)/.test(name)) return "SexualAssault";
  if (/(sex|sexual)/.test(name)) return "SexualContent";
  if (name.includes("nud")) return "Nudity";
  if (name.includes("suicide")) return "SuicideThemes";
  if (/(self harm|self-harm|cutting)/.test(name)) return "SelfHarm";
  if (/(drug|overdose)/.test(name)) return "Drugs";
  if (/(alcohol|drunk)/.test(name)) return "Alcohol";
  if (/(smok|vaping)/.test(name)) return "SmokingVaping";
  if (/(gore|blood|mutilation|amputation)/.test(name)) return "BloodGore";
  if (/(jump scare|sudden loud)/.test(name)) return "JumpScares";
  if (/(flashing|seizure)/.test(name)) return "FlashingLights";
  if (/(violence|gun|torture|stabbing)/.test(name)) return "Violence";
  if (/(child|kid|baby|infant)/.test(name)) return "ChildDanger";
  if (/(language|slur|obscene)/.test(name)) return "Language";
  if (name.includes("bully")) return "Bullying";
  if (name.includes("domestic")) return "DomesticAbuse";
  if (/(disturb|body horror|vomit)/.test(name)) return "DisturbingImagery";
  if (/(scary|fear|horror|demonic)/.test(name)) return "ScaryScenes";
  return "MatureThemes";
}

function inferSeverity(yes, no, comments) {
  if (yes <= 0) return "None";
  const ratio = yes / Math.max(yes + no, 1);
  if (yes >= 50 && ratio >= 0.85) return "GraphicHeavy";
  if (yes >= 15 && ratio >= 0.75) return "Strong";
  if (yes >= 5 && ratio >= 0.55) return "Moderate";
  if (comments >= 3 && yes >= 3) return "Moderate";
  return "Mild";
}

function severityScore(severity) {
  return { None: 0, Mild: 1, Moderate: 2, Strong: 3, GraphicHeavy: 4 }[severity] ?? 0;
}

function explainTopic(topicName, yes, no, comments) {
  const commentText = comments > 0 ? `, with ${comments} context comments` : "";
  return `Community reports indicate "${topicName}" (${yes} yes / ${no} no${commentText}).`;
}

function dogTypeMatches(itemTypeName, mediaType) {
  if (mediaType === "movie") return itemTypeName === "Movie";
  return itemTypeName === "TV" || itemTypeName === "TV Show";
}

function allowRequest(req) {
  const ip = req.socket.remoteAddress || "unknown";
  const now = Date.now();
  const windowStart = now - 60_000;
  const hits = (rateLimits.get(ip) || []).filter((time) => time >= windowStart);
  hits.push(now);
  rateLimits.set(ip, hits);
  return hits.length <= RATE_LIMIT_PER_MINUTE;
}

function setCors(res) {
  res.setHeader("Access-Control-Allow-Origin", process.env.ALLOWED_ORIGIN || "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
}

function sendJson(res, status, payload) {
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(payload));
}

function sendError(res, status, code, message) {
  sendJson(res, status, { error: code, message });
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function scrub(error) {
  return { message: error?.message, code: error?.code, status: error?.status };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  createServer().listen(PORT, () => {
    console.log(`ContentLens API listening on :${PORT}`);
  });
}
