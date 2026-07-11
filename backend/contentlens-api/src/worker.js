const DOG_BASE_URL = "https://www.doesthedogdie.com/api/v3";
const DEFAULT_CACHE_TTL_SECONDS = 21600;
const cache = new Map();

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return json({}, 204);
    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, service: "contentlens-api" });
    }

    const match = url.pathname.match(/^\/v1\/safety\/tmdb\/(movie|tv)\/(\d+)$/);
    if (request.method === "GET" && match) {
      return safetyResponse(match[1], Number.parseInt(match[2], 10), url, env);
    }

    return json({ error: "not_found", message: "Endpoint not found." }, 404);
  }
};

async function safetyResponse(mediaType, tmdbId, url, env) {
  const apiKey = env?.DOES_THE_DOG_DIE_API_KEY || "";
  if (!apiKey) {
    return json({ error: "provider_not_configured", message: "DoesTheDogDie API key is not configured on the server." }, 503);
  }

  const title = (url.searchParams.get("title") || "").trim();
  const year = (url.searchParams.get("year") || "").trim();
  const cacheKey = `${mediaType}:${tmdbId}:${title.toLowerCase()}:${year}`;
  const cached = cache.get(cacheKey);
  if (cached && cached.expiresAt > Date.now()) {
    return json({ ...cached.value, cached: true });
  }

  const item = await findDogItem(apiKey, mediaType, tmdbId, title, year);
  if (!item) {
    return json({ error: "no_safety_match", message: "No DoesTheDogDie match was found for this TMDB title." }, 404);
  }

  const detail = await dogFetchJson(apiKey, `/items/${item.id}`);
  const report = normalizeDogReport(detail);
  const ttl = Number.parseInt(env?.CACHE_TTL_SECONDS || `${DEFAULT_CACHE_TTL_SECONDS}`, 10) * 1000;
  cache.set(cacheKey, { value: report, expiresAt: Date.now() + ttl });
  return json(report);
}

async function findDogItem(apiKey, mediaType, tmdbId, title, year) {
  const byTmdb = await dogFetchJson(apiKey, `/items?tmdb=${encodeURIComponent(String(tmdbId))}`);
  const tmdbMatch = asArray(byTmdb).find((item) => dogTmdbMatches(item, tmdbId, mediaType));
  if (tmdbMatch) return tmdbMatch;

  if (!title) return null;
  const byName = await dogFetchJson(
    apiKey,
    `/items?name=${encodeURIComponent(title)}${year ? `&releaseYear=${encodeURIComponent(year)}` : ""}`
  );
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

export function dogTmdbMatches(item, tmdbId, mediaType) {
  return Number(item?.tmdbId) === Number(tmdbId) && dogTypeMatches(item?.itemTypeName, mediaType);
}

function json(payload, status = 200) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type"
    }
  });
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}
