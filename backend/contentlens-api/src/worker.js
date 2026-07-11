const DOG_BASE_URL = "https://www.doesthedogdie.com/api/v3";
const DEFAULT_CACHE_TTL_SECONDS = 21600;
const FEATURED_FEED_TTL_MS = 60 * 60 * 1000;
const cache = new Map();

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return json({}, 204);
    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, service: "contentlens-api" });
    }
    if (request.method === "GET" && url.pathname === "/v1/featured") {
      return featuredResponse(env);
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

async function featuredResponse(env) {
  const apiKey = env?.DOES_THE_DOG_DIE_API_KEY || "";
  const feedCacheKey = `featured-feed:v1:${apiKey ? "provider" : "empty"}`;
  const cached = cache.get(feedCacheKey);
  if (cached && cached.expiresAt > Date.now()) {
    return json({ ...cached.value, cached: true });
  }

  const generatedAt = new Date().toISOString();
  const safetyByKey = await featuredSafetyByKey(apiKey, env);
  const sections = FEATURED_SECTIONS.map((section) => ({
    ...section,
    items: section.items.map((item) => ({
      ...item,
      safety: safetyByKey.get(featuredItemKey(item)) ?? null
    }))
  }));
  const feed = {
    schemaVersion: 1,
    region: "US",
    generatedAt,
    attribution: "Movie and TV metadata is provided by TMDB. Content warnings are provided by DoesTheDogDie community data when available.",
    sections
  };
  cache.set(feedCacheKey, { value: feed, expiresAt: Date.now() + FEATURED_FEED_TTL_MS });
  return json(feed);
}

async function safetyReportForItem(apiKey, item, env) {
  const cacheKey = `featured:${item.mediaType}:${item.tmdbId}`;
  const cached = cache.get(cacheKey);
  if (cached && cached.expiresAt > Date.now()) return { ...cached.value, cached: true };
  try {
    const dogItem = await findDogItem(apiKey, item.mediaType, item.tmdbId, item.title, item.releaseYear ? String(item.releaseYear) : "");
    if (!dogItem) return null;
    const detail = await dogFetchJson(apiKey, `/items/${dogItem.id}`);
    const report = normalizeDogReport(detail);
    const ttl = Number.parseInt(env?.CACHE_TTL_SECONDS || `${DEFAULT_CACHE_TTL_SECONDS}`, 10) * 1000;
    cache.set(cacheKey, { value: report, expiresAt: Date.now() + ttl });
    return report;
  } catch {
    return null;
  }
}

async function featuredSafetyByKey(apiKey, env) {
  if (!apiKey) return new Map();
  const uniqueItems = [];
  const seen = new Set();
  for (const section of FEATURED_SECTIONS) {
    for (const item of section.items) {
      const key = featuredItemKey(item);
      if (seen.has(key)) continue;
      seen.add(key);
      uniqueItems.push(item);
    }
  }
  const reports = await mapLimit(uniqueItems, 4, async (item) => [
    featuredItemKey(item),
    await safetyReportForItem(apiKey, item, env)
  ]);
  return new Map(reports);
}

function featuredItemKey(item) {
  return `${item.mediaType}:${item.tmdbId}`;
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

const FEATURED_SECTIONS = [
  {
    key: "little-kids",
    title: "Young children",
    subtitle: "Gentle preschool and toddler-friendly starting points.",
    items: [
      featured("tv", 82728, "Bluey", 2018),
      featured("tv", 40050, "Daniel Tiger's Neighborhood", 2012),
      featured("tv", 69926, "Puffin Rock", 2015),
      featured("tv", 2005, "The New Adventures of Winnie the Pooh", 1988)
    ]
  },
  {
    key: "preschool-favorites",
    title: "Preschool favorites",
    subtitle: "Bright, familiar shows for early learners.",
    items: [
      featured("tv", 502, "Sesame Street", 1969),
      featured("tv", 656, "Curious George", 2006),
      featured("tv", 37472, "Octonauts", 2010),
      featured("tv", 93548, "Molly of Denali", 2019)
    ]
  },
  {
    key: "early-elementary",
    title: "Early elementary",
    subtitle: "Friendly adventures for growing attention spans.",
    items: [
      featured("tv", 35094, "Wild Kratts", 2011),
      featured("tv", 7248, "The Magic School Bus", 1994),
      featured("movie", 227973, "The Peanuts Movie", 2015),
      featured("tv", 3902, "Shaun the Sheep", 2007)
    ]
  },
  {
    key: "older-kids",
    title: "Older kids",
    subtitle: "Bigger stories to review for intensity and scares.",
    items: [
      featured("movie", 10191, "How to Train Your Dragon", 2010),
      featured("tv", 82456, "Hilda", 2018),
      featured("tv", 246, "Avatar: The Last Airbender", 2005),
      featured("movie", 501929, "The Mitchells vs. the Machines", 2021)
    ]
  },
  {
    key: "family-night",
    title: "Family night",
    subtitle: "Broad, familiar picks for mixed-age viewing.",
    items: [
      featured("movie", 277834, "Moana", 2016),
      featured("movie", 8587, "The Lion King", 1994),
      featured("movie", 116149, "Paddington", 2014),
      featured("movie", 862, "Toy Story", 1995)
    ]
  },
  {
    key: "low-intensity",
    title: "Low intensity",
    subtitle: "Calmer stories to check first when intensity matters.",
    items: [
      featured("movie", 16859, "Kiki's Delivery Service", 1989),
      featured("movie", 8392, "My Neighbor Totoro", 1988),
      featured("movie", 263109, "Shaun the Sheep Movie", 2015),
      featured("movie", 227973, "The Peanuts Movie", 2015)
    ]
  },
  {
    key: "no-nudity-starting-points",
    title: "Review first: nudity concern",
    subtitle: "Popular picks to review when nudity is a hard concern.",
    items: [
      featured("movie", 12, "Finding Nemo", 2003),
      featured("movie", 150540, "Inside Out", 2015),
      featured("movie", 9806, "The Incredibles", 2004),
      featured("movie", 324857, "Spider-Man: Into the Spider-Verse", 2018)
    ]
  },
  {
    key: "short-watches",
    title: "Short watches",
    subtitle: "Easy options for limited time windows.",
    items: [
      featured("tv", 80616, "Wallace & Gromit's Cracking Contraptions", 2002),
      featured("movie", 13187, "A Charlie Brown Christmas", 1965),
      featured("tv", 3902, "Shaun the Sheep", 2007),
      featured("tv", 114501, "Dug Days", 2021)
    ]
  },
  {
    key: "teen-adventure",
    title: "Tweens and teens",
    subtitle: "Higher-energy titles worth checking against profile limits.",
    items: [
      featured("movie", 671, "Harry Potter and the Philosopher's Stone", 2001),
      featured("movie", 411, "The Chronicles of Narnia", 2005),
      featured("tv", 103540, "Percy Jackson and the Olympians", 2023),
      featured("tv", 40075, "Gravity Falls", 2012)
    ]
  }
];

function featured(mediaType, tmdbId, title, releaseYear) {
  return { mediaType, tmdbId, title, releaseYear };
}

async function mapLimit(items, limit, mapper) {
  const results = new Array(items.length);
  let nextIndex = 0;
  const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (nextIndex < items.length) {
      const index = nextIndex++;
      results[index] = await mapper(items[index], index);
    }
  });
  await Promise.all(workers);
  return results;
}
