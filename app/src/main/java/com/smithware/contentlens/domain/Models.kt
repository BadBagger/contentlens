package com.smithware.contentlens.domain

enum class ContentCategory(val label: String) {
    Language("Language"),
    Profanity("Profanity"),
    Violence("Violence"),
    GraphicViolence("Graphic violence"),
    BloodGore("Blood/gore"),
    Gore("Gore"),
    SexualContent("Sexual content"),
    Nudity("Nudity"),
    SexualAssault("Sexual assault"),
    Drugs("Drugs"),
    DrugUse("Drug use"),
    Alcohol("Alcohol"),
    AlcoholUse("Alcohol use"),
    SmokingVaping("Smoking/vaping"),
    Smoking("Smoking"),
    ReligiousThemes("Religious themes"),
    Horror("Horror"),
    SelfHarm("Self-harm"),
    SuicideThemes("Suicide themes"),
    Suicide("Suicide"),
    ChildDanger("Child danger"),
    ChildHarm("Child harm"),
    AnimalHarm("Animal harm"),
    HateSpeechSlurs("Hate speech/slurs"),
    Bullying("Bullying"),
    DomesticAbuse("Domestic abuse"),
    ScaryScenes("Scary scenes"),
    JumpScares("Jump scares"),
    FlashingLights("Flashing lights"),
    DisturbingImagery("Disturbing imagery"),
    DisturbingMedicalImagery("Disturbing medical imagery"),
    LoudSuddenSounds("Loud or sudden sounds"),
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
    Allow("Allow"),
    WarnMe("Warn me"),
    AvoidWhenPossible("Avoid when possible"),
    NeverShow("Never show"),
    UnknownRequiresApproval("Unknown content requires approval"),
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

enum class BoundaryStatus(val label: String) {
    Safe("Safe for your selected profiles"),
    Warning("Contains one warning"),
    Conflict("Conflicts with your preferences"),
    InsufficientInformation("Insufficient content information"),
    Blocked("Blocked by a profile rule")
}

data class BoundaryEvaluation(
    val eligible: Boolean,
    val status: BoundaryStatus,
    val compatibilityScore: Int,
    val blockedReasons: List<String> = emptyList(),
    val warningReasons: List<String> = emptyList(),
    val matchedPreferences: List<String> = emptyList(),
    val explanation: String = status.label
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

    fun evaluateDetailed(
        entries: List<com.smithware.contentlens.data.ContentRatingEntryEntity>,
        sensitivities: List<com.smithware.contentlens.data.ProfileSensitivityEntity>,
        profiles: List<com.smithware.contentlens.data.UserProfileEntity> = emptyList()
    ): BoundaryEvaluation
}
