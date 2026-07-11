import assert from "node:assert/strict";
import { test } from "node:test";
import { dogTmdbMatches, mapTopicToCategory, normalizeDogReport } from "../src/server.js";

test("maps DoesTheDogDie topics to ContentLens categories", () => {
  assert.equal(mapTopicToCategory("a dog dies"), "AnimalHarm");
  assert.equal(mapTopicToCategory("there are jump scares"), "JumpScares");
  assert.equal(mapTopicToCategory("there is sexual assault"), "SexualAssault");
  assert.equal(mapTopicToCategory("there is alcohol use"), "Alcohol");
});

test("normalizes topic stats into public safety report shape", () => {
  const report = normalizeDogReport({
    id: 10752,
    name: "Old Yeller",
    topicItemStats: [
      { topicId: 153, topicName: "a dog dies", yesSum: 57, noSum: 3, numComments: 7 },
      { topicId: 222, topicName: "there are jump scares", yesSum: 4, noSum: 9, numComments: 1 },
      { topicId: 333, topicName: "there is sexual assault", yesSum: 16, noSum: 1, numComments: 4 }
    ]
  });

  assert.equal(report.source, "DoesTheDogDie community");
  assert.equal(report.sourceItemId, 10752);
  assert.equal(report.reportCount, 90);
  assert.equal(report.entries[0].category, "AnimalHarm");
  assert.equal(report.entries[0].severity, "GraphicHeavy");
  assert.ok(report.entries.some((entry) => entry.category === "SexualAssault" && entry.severity === "Strong"));
});

test("matches provider TMDB ids when returned as strings", () => {
  assert.equal(dogTmdbMatches({ tmdbId: "603", itemTypeName: "Movie" }, 603, "movie"), true);
  assert.equal(dogTmdbMatches({ tmdbId: "603", itemTypeName: "Movie" }, 603, "tv"), false);
});
