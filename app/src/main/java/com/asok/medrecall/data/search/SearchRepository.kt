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
 * records back across every part of the app, oldest-first ordering left to
 * each source query -- this repository only flattens results into a single
 * list grouped by [SearchCategory] for display.
 *
 * Deliberately goes straight to the DAOs rather than through each feature's
 * existing repository -- this is the one place in the app that legitimately
 * needs all of them at once, and every feature repository already just
 * forwards its DAO calls unchanged.
 *
 * Vitals are matched in memory against [VitalType.title] (e.g. "Blood
 * Pressure") rather than in SQL, since a reading's stored `type` is a
 * machine id ("blood_pressure") that a typed search term wouldn't LIKE-match.
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
        val query = rawQuery.trim()
        if (query.isEmpty()) return emptyList()
        val lowerQuery = query.lowercase()

        return coroutineScope {
            val doctors = async { doctorDao.search(query) }
            val medications = async { medicationDao.search(query) }
            val conditions = async { conditionDao.search(query) }
            val appointments = async { appointmentDao.search(query) }
            val notes = async { noteDao.search(query) }
            val vitalReadings = async { vitalReadingDao.getAllOnce() }
            val patient = async { patientDao.getOnce() }

            val results = mutableListOf<SearchResult>()

            doctors.await().forEach { doctor ->
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

            medications.await().forEach { medication ->
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

            conditions.await().forEach { condition ->
                results += SearchResult(
                    id = "condition_${condition.id}",
                    category = SearchCategory.CONDITION,
                    title = condition.name,
                    subtitle = condition.status,
                    route = "condition_form/${condition.id}"
                )
            }

            appointments.await().forEach { appointment ->
                val whenText = formatMillis(appointment.dateTime)
                results += SearchResult(
                    id = "appointment_${appointment.id}",
                    category = SearchCategory.APPOINTMENT,
                    title = appointment.reason,
                    subtitle = listOfNotNull(whenText, appointment.location).joinToString(" · "),
                    route = "appointment_form/${appointment.id}"
                )
            }

            notes.await().forEach { note ->
                results += SearchResult(
                    id = "note_${note.id}",
                    category = SearchCategory.NOTE,
                    title = note.title.ifBlank { "Visit note" },
                    subtitle = formatMillis(note.createdAt),
                    route = null,
                    detailBody = note.body
                )
            }

            vitalReadings.await().forEach { reading ->
                val vitalType = VitalType.fromId(reading.type)
                val haystack = listOfNotNull(vitalType.title, reading.context, reading.notes)
                    .joinToString(" ")
                    .lowercase()
                if (haystack.contains(lowerQuery)) {
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
                val haystack = listOfNotNull(
                    p.allergies, p.notes, p.bloodType,
                    p.emergencyContactName, p.emergencyContactRelationship,
                    p.addressLine, p.city, p.state, p.zip
                ).joinToString(" ").lowercase()
                if (haystack.contains(lowerQuery)) {
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

    private fun formatMillis(millis: Long): String =
        runCatching {
            dateFormatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
        }.getOrDefault("")
}
