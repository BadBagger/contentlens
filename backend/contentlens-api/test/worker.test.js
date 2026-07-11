import assert from "node:assert/strict";
import { test } from "node:test";
import worker, { dogTmdbMatches, mapTopicToCategory, normalizeDogReport } from "../src/worker.js";

test("worker exposes health response", async () => {
  const response = await worker.fetch(new Request("https://contentlens.test/health"), {});
  const body = await response.json();

  assert.equal(response.status, 200);
  assert.equal(body.ok, true);
});

test("worker maps topics and report shape", () => {
  assert.equal(mapTopicToCategory("a dog dies"), "AnimalHarm");
  const report = normalizeDogReport({
    id: 10752,
    name: "Old Yeller",
    topicItemStats: [
      { topicId: 153, topicName: "a dog dies", yesSum: 57, noSum: 3, numComments: 7 }
    ]
  });

  assert.equal(report.entries[0].category, "AnimalHarm");
  assert.equal(report.entries[0].severity, "GraphicHeavy");
});

test("worker matches provider TMDB ids when returned as strings", () => {
  assert.equal(dogTmdbMatches({ tmdbId: "603", itemTypeName: "Movie" }, 603, "movie"), true);
  assert.equal(dogTmdbMatches({ tmdbId: "603", itemTypeName: "TV" }, 603, "tv"), true);
});
