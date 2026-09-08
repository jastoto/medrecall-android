package com.asok.medrecall.data.search

import com.asok.medrecall.data.local.dao.AppointmentDao
import com.asok.medrecall.data.local.dao.ConditionDao
import com.asok.medrecall.data.local.dao.DoctorDao
import com.asok.medrecall.data.local.dao.MedicationDao
import com.asok.medrecall.data.local.dao.NoteDao
import com.asok.medrecall.data.local.dao.PatientDao
import com.asok.medrecall.data.local.dao.VitalReadingDao
import com.asok.medrecall.ui.vitals.VitalType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Backs the "Ask MedRecall" screen: one word (or phrase) in, matching
 * records back across every part of the app, grouped by [SearchCategory]
 * for display.
 *
 * Matching is done here in Kotlin, over the whole (small, personal-scale)
 * table for each entity, rather than with a SQL LIKE query. Reason: people
 * type "Dr" and expect it to find "Dr. Alvarez"; they type a phone number
 * without dashes and expect it to find one stored with them. [normalize]
 * strips everything that isn't a letter or digit and lowercases what's
 * left, on both the typed query and every record's searchable text, so
 * periods, spaces, hyphens, apostrophes and the like are ignored on both
 * sides of the match. It also means a query that's nothing but punctuation
 * (e.g. a stray ".") normalizes to blank and correctly finds nothing,
 * instead of matching every row that happens to contain a period.
 *
 * Deliberately goes straight to the DAOs rather than through each feature's
 * existing repository -- this is the one place in the app that legitimately
 * needs all of them at once, and every feature repository already just
 * forwards its DAO calls unchanged.
 */
class SearchRepository(
    private val doctorDao: DoctorDao,
    private val medicationDao: MedicationDao,
    private val conditionDao: ConditionDao,
    private val appointmentDao: AppointmentDao,
    private val noteDao: NoteDao,
    private val vitalReadingDao: VitalReadingDao,
    private val patientDao: PatientDao
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy 'at' h:mm a")

    suspend fun search(rawQuery: String): List<SearchResult> {
        val normalizedQuery = normalize(rawQuery)
        // Nothing left after stripping punctuation/spaces -- e.g. the user
        // searched for just "." or "-" -- so there's no real term to match.
        if (normalizedQuery.isBlank()) return emptyList()

        return coroutineScope {
            val doctors = async { doctorDao.getAllOnce() }
            val medications = async { medicationDao.getAllOnce() }
            val conditions = async { conditionDao.getAllOnce() }
            val appointments = async { appointmentDao.getAllOnce() }
            val notes = async { noteDao.getAllOnce() }
            val vitalReadings = async { vitalReadingDao.getAllOnce() }
            val patient = async { patientDao.getOnce() }

            val results = mutableListOf<SearchResult>()

            doctors.await().forEach { doctor ->
                if (matches(normalizedQuery, doctor.name, doctor.specialty, doctor.phone, doctor.address, doctor.notes)) {
                    results += SearchResult(
                        id = "doctor_${doctor.id}",
                        category = SearchCategory.DOCTOR,
                        title = doctor.name,
                        subtitle = listOfNotNull(doctor.specialty, doctor.phone)
                            .joinToString(" · ")
                            .ifBlank { "Doctor" },
                        route = "doctor_form/${doctor.id}"
                    )
                }
            }

            medications.await().forEach { medication ->
                if (matches(normalizedQuery, medication.name, medication.dosage, medication.schedule, medication.notes)) {
                    results += SearchResult(
                        id = "medication_${medication.id}",
                        category = SearchCategory.MEDICATION,
                        title = medication.name,
                        subtitle = listOfNotNull(medication.dosage, medication.schedule)
                            .joinToString(" · ")
                            .ifBlank { if (medication.active) "Active medication" else "Inactive medication" },
                        route = "medication_form/${medication.id}"
                    )
                }
            }

            conditions.await().forEach { condition ->
                if (matches(normalizedQuery, condition.name, condition.status, condition.notes)) {
                    results += SearchResult(
                        id = "condition_${condition.id}",
                        category = SearchCategory.CONDITION,
                        title = condition.name,
                        subtitle = condition.status,
                        route = "condition_form/${condition.id}"
                    )
                }
            }

            appointments.await().forEach { appointment ->
                if (matches(normalizedQuery, appointment.reason, appointment.location, appointment.notes)) {
                    val whenText = formatMillis(appointment.dateTime)
                    results += SearchResult(
                        id = "appointment_${appointment.id}",
                        category = SearchCategory.APPOINTMENT,
                        title = appointment.reason,
                        subtitle = listOfNotNull(whenText, appointment.location).joinToString(" · "),
                        route = "appointment_form/${appointment.id}"
                    )
                }
            }

            notes.await().forEach { note ->
                if (matches(normalizedQuery, note.title, note.body)) {
                    results += SearchResult(
                        id = "note_${note.id}",
                        category = SearchCategory.NOTE,
                        title = note.title.ifBlank { "Visit note" },
                        subtitle = formatMillis(note.createdAt),
                        route = null,
                        detailBody = note.body
                    )
                }
            }

            vitalReadings.await().forEach { reading ->
                val vitalType = VitalType.fromId(reading.type)
                if (matches(normalizedQuery, vitalType.title, reading.context, reading.notes)) {
                    results += SearchResult(
                        id = "vital_${reading.id}",
                        category = SearchCategory.VITAL,
                        title = vitalType.title,
                        subtitle = listOfNotNull(reading.context, formatMillis(reading.recordedAt))
                            .joinToString(" · "),
                        route = "vital_detail/${vitalType.id}"
                    )
                }
            }

            patient.await()?.let { p ->
                if (matches(
                        normalizedQuery,
                        p.allergies, p.notes, p.bloodType,
                        p.emergencyContactName, p.emergencyContactRelationship,
                        p.addressLine, p.city, p.state, p.zip
                    )
                ) {
                    results += SearchResult(
                        id = "medical_id_patient",
                        category = SearchCategory.MEDICAL_ID,
                        title = "Medical ID",
                        subtitle = "Matches your profile details",
                        route = "medical_id"
                    )
                }
            }

            results
        }
    }

    /** Lowercases and strips everything but letters/digits, so "Dr.", "dr", and "DR" are all "dr". */
    private fun normalize(text: String?): String =
        text.orEmpty().lowercase().filter { it.isLetterOrDigit() }

    /** True if [normalizedQuery] appears anywhere in any of [fields], each normalized the same way. */
    private fun matches(normalizedQuery: String, vararg fields: String?): Boolean =
        fields.any { normalize(it).contains(normalizedQuery) }

    private fun formatMillis(millis: Long): String =
        runCatching {
            dateFormatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
        }.getOrDefault("")
}
