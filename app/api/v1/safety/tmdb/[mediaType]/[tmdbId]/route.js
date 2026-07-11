import { normalizeDogReport } from "../../../../../../../backend/contentlens-api/src/server.js";

export const runtime = "nodejs";

const DOG_BASE_URL = (process.env.DOES_THE_DOG_DIE_BASE_URL || "https://www.doesthedogdie.com/api/v3").replace(/\/$/, "");
const CACHE_TTL_MS = Number.parseInt(process.env.CACHE_TTL_SECONDS || "21600", 10) * 1000;
const cache = new Map();

export async function GET(request, context) {
  const apiKey = process.env.DOES_THE_DOG_DIE_API_KEY || "";
  if (!apiKey) {
    return errorJson(503, "provider_not_configured", "DoesTheDogDie API key is not configured on the server.");
  }

  const params = await context.params;
  const mediaType = params.mediaType;
  const tmdbId = Number.parseInt(params.tmdbId, 10);
  if (!["movie", "tv"].includes(mediaType) || !Number.isFinite(tmdbId)) {
    return errorJson(400, "invalid_request", "Expected media type movie or tv and a numeric TMDB id.");
  }

  const url = new URL(request.url);
  const title = (url.searchParams.get("title") || "").trim();
  const year = (url.searchParams.get("year") || "").trim();
  const cacheKey = `${mediaType}:${tmdbId}:${title.toLowerCase()}:${year}`;
  const cached = cache.get(cacheKey);
  if (cached && cached.expiresAt > Date.now()) {
    return Response.json({ ...cached.value, cached: true });
  }

  try {
    const item = await findDogItem(apiKey, mediaType, tmdbId, title, year);
    if (!item) {
      return errorJson(404, "no_safety_match", "No DoesTheDogDie match was found for this TMDB title.");
    }
    const detail = await dogFetchJson(apiKey, `/items/${item.id}`);
    const report = normalizeDogReport(detail);
    cache.set(cacheKey, { value: report, expiresAt: Date.now() + CACHE_TTL_MS });
    return Response.json(report);
  } catch (error) {
    const status = error.status || 500;
    const code = error.code || "provider_error";
    const message = status === 429
      ? "Provider rate limit reached."
      : status === 403
        ? "Provider tier does not allow this request."
        : status === 401
          ? "Provider authentication failed."
          : "Content safety provider request failed.";
    return errorJson(status, code, message);
  }
}

async function findDogItem(apiKey, mediaType, tmdbId, title, year) {
  const byTmdb = await dogFetchJson(apiKey, `/items?tmdb=${encodeURIComponent(String(tmdbId))}`);
  const tmdbMatch = asArray(byTmdb).find((item) => item.tmdbId === tmdbId && dogTypeMatches(item.itemTypeName, mediaType));
  if (tmdbMatch) return tmdbMatch;

  if (!title) return null;
  const namePath = `/items?name=${encodeURIComponent(title)}${year ? `&releaseYear=${encodeURIComponent(year)}` : ""}`;
  const byName = await dogFetchJson(apiKey, namePath);
  return asArray(byName).find((item) => dogTypeMatches(item.itemTypeName, mediaType)) ?? null;
}

async function dogFetchJson(apiKey, path) {
  const response = await fetch(`${DOG_BASE_URL}${path}`, {
    headers: {
      "X-API-KEY": apiKey,
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

function dogTypeMatches(itemTypeName, mediaType) {
  if (mediaType === "movie") return itemTypeName === "Movie";
  return itemTypeName === "TV" || itemTypeName === "TV Show";
}

function errorJson(status, error, message) {
  return Response.json({ error, message }, { status });
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}
