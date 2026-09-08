package com.asok.medrecall.data.search

/**
 * One category of thing Ask MedRecall can find. `label` is the section
 * header shown above that category's results on the Ask MedRecall screen.
 */
enum class SearchCategory(val label: String) {
    DOCTOR("Doctors"),
    MEDICATION("Medications"),
    CONDITION("Conditions"),
    VITAL("Vitals"),
    APPOINTMENT("Appointments"),
    NOTE("Visit Notes"),
    MEDICAL_ID("Medical ID")
}

/**
 * One matched record, already flattened into whatever a result row needs to
 * display and, on tap, navigate to. [route] is a NavHost route string (same
 * routes the rest of the app already navigates with) -- null means there is
 * no dedicated screen to open (Visit Notes today), in which case the screen
 * shows [detailBody] inline instead of navigating.
 */
data class SearchResult(
    val id: String,
    val category: SearchCategory,
    val title: String,
    val subtitle: String,
    val route: String?,
    val detailBody: String? = null
)
