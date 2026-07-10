package com.smithware.contentlens.domain

enum class ContentCategory(val label: String) {
    Language("Language"),
    Violence("Violence"),
    BloodGore("Blood/gore"),
    SexualContent("Sexual content"),
    Nudity("Nudity"),
    SexualAssault("Sexual assault"),
    Drugs("Drugs"),
    Alcohol("Alcohol"),
    SmokingVaping("Smoking/vaping"),
    SelfHarm("Self-harm"),
    SuicideThemes("Suicide themes"),
    ChildDanger("Child danger"),
    AnimalHarm("Animal harm"),
    HateSpeechSlurs("Hate speech/slurs"),
    Bullying("Bullying"),
    DomesticAbuse("Domestic abuse"),
    ScaryScenes("Scary scenes"),
    JumpScares("Jump scares"),
    FlashingLights("Flashing lights"),
    DisturbingImagery("Disturbing imagery"),
    MatureThemes("Mature themes")
}

enum class Severity(val label: String, val score: Int) {
    None("None", 0),
    Mild("Mild", 1),
    Moderate("Moderate", 2),
    Strong("Strong", 3),
    GraphicHeavy("Graphic/Heavy", 4)
}

enum class MediaType(val label: String) {
    Movie("Movie"),
    TvShow("TV Show")
}

enum class LensRating(val label: String) {
    Everyone("Everyone"),
    SevenPlus("7+"),
    TenPlus("10+"),
    ThirteenPlus("13+"),
    SixteenPlus("16+"),
    EighteenPlus("18+"),
    AdultGraphic("Adult/Graphic"),
    InsufficientData("Unrated/Insufficient Data")
}

enum class Sensitivity(val label: String) {
    DontCare("Don't care"),
    MildConcern("Mild concern"),
    AvoidModeratePlus("Avoid moderate+"),
    AvoidStrongPlus("Avoid strong+"),
    AvoidAny("Avoid any")
}

enum class FitLabel(val label: String) {
    GoodFit("Good fit"),
    MildCaution("Mild caution"),
    Caution("Caution"),
    NotRecommended("Not recommended for this profile"),
    InsufficientData("Insufficient data")
}

data class RatingSummary(
    val rating: LensRating,
    val topWarnings: List<String>,
    val entryCount: Int
)

interface MediaMetadataRepository {
    suspend fun searchTitles(query: String): List<com.smithware.contentlens.data.MediaTitleEntity>
}

interface ContentReportRepository {
    suspend fun submitReport(report: com.smithware.contentlens.data.ContentReportEntity)
}

interface RatingEngine {
    fun summarize(entries: List<com.smithware.contentlens.data.ContentRatingEntryEntity>): RatingSummary
}

interface PersonalFitEngine {
    fun evaluate(
        entries: List<com.smithware.contentlens.data.ContentRatingEntryEntity>,
        sensitivities: List<com.smithware.contentlens.data.ProfileSensitivityEntity>
    ): FitLabel
}
