package com.asok.medrecall.data.backup

import com.asok.medrecall.data.local.Appointment
import com.asok.medrecall.data.local.Condition
import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.data.local.MedRecallDatabase
import com.asok.medrecall.data.local.Medication
import com.asok.medrecall.data.local.Note
import com.asok.medrecall.data.local.Patient
import com.asok.medrecall.data.local.VitalReading
import org.json.JSONObject

/**
 * Reverse of BackupExporter: reads one section's JSON (in the exact shape
 * BackupExporter writes) and loads it into Room. REPLACE semantics per
 * Asok's spec -- deleteAll() on that section's table before reinserting,
 * with insertAllForRestore() (OnConflictStrategy.REPLACE) preserving each
 * row's original id so cross-references (Medication.prescribingDoctorId,
 * Appointment.doctorId, Note.appointmentId/doctorId) still line up as long
 * as Doctors/Appointments are restored too -- there's no enforced foreign
 * key in these entities, so a partial restore just leaves a dangling id
 * rather than failing.
 *
 * RISK FLAG: same unverified-against-a-real-build category as the rest of
 * this feature -- if something doesn't compile it's most likely a field
 * name/type mismatch against BackupExporter's JSON shape below.
 */
object BackupImporter {

    suspend fun import(database: MedRecallDatabase, section: BackupSection, json: String) {
        val root = JSONObject(json)

        when (section) {
            BackupSection.DOCTORS -> {
                val items = root.optJSONArray("items")
                val list = buildList {
                    if (items != null) for (i in 0 until items.length()) {
                        val o = items.getJSONObject(i)
                        add(
                            Doctor(
                                id = o.getInt("id"),
                                name = o.getString("name"),
                                specialty = o.optStringOrNull("specialty"),
                                phone = o.optStringOrNull("phone"),
                                address = o.optStringOrNull("address"),
                                notes = o.optStringOrNull("notes")
                            )
                        )
                    }
                }
                database.doctorDao().deleteAll()
                database.doctorDao().insertAllForRestore(list)
            }

            BackupSection.MEDICATIONS -> {
                val items = root.optJSONArray("items")
                val list = buildList {
                    if (items != null) for (i in 0 until items.length()) {
                        val o = items.getJSONObject(i)
                        add(
                            Medication(
                                id = o.getInt("id"),
                                name = o.getString("name"),
                                dosage = o.optStringOrNull("dosage"),
                                schedule = o.optStringOrNull("schedule"),
                                prescribingDoctorId = o.optIntOrNull("prescribingDoctorId"),
                                conditionId = o.optIntOrNull("conditionId"),
                                startDate = o.optLongOrNull("startDate"),
                                endDate = o.optLongOrNull("endDate"),
                                active = o.optBoolean("active", true),
                                notes = o.optStringOrNull("notes")
                            )
                        )
                    }
                }
                database.medicationDao().deleteAll()
                database.medicationDao().insertAllForRestore(list)
            }

            BackupSection.VITALS -> {
                val items = root.optJSONArray("items")
                val list = buildList {
                    if (items != null) for (i in 0 until items.length()) {
                        val o = items.getJSONObject(i)
                        add(
                            VitalReading(
                                id = o.getInt("id"),
                                type = o.getString("type"),
                                recordedAt = o.getLong("recordedAt"),
                                primaryValue = o.getDouble("primaryValue"),
                                secondaryValue = o.optDoubleOrNull("secondaryValue"),
                                tertiaryValue = o.optDoubleOrNull("tertiaryValue"),
                                context = o.optStringOrNull("context"),
                                notes = o.optStringOrNull("notes"),
                                source = o.optString("source", "MANUAL")
                            )
                        )
                    }
                }
                database.vitalReadingDao().deleteAll()
                database.vitalReadingDao().insertAllForRestore(list)
            }

            BackupSection.CONDITIONS -> {
                val items = root.optJSONArray("items")
                val list = buildList {
                    if (items != null) for (i in 0 until items.length()) {
                        val o = items.getJSONObject(i)
                        add(
                            Condition(
                                id = o.getInt("id"),
                                name = o.getString("name"),
                                status = o.optString("status", "Active"),
                                iconKey = o.optString("iconKey", "assignment"),
                                notes = o.optStringOrNull("notes"),
                                includedInMedicalId = o.optBoolean("includedInMedicalId", true),
                                doctorId = o.optIntOrNull("doctorId")
                            )
                        )
                    }
                }
                database.conditionDao().deleteAll()
                database.conditionDao().insertAllForRestore(list)
            }

            BackupSection.APPOINTMENTS -> {
                val items = root.optJSONArray("items")
                val list = buildList {
                    if (items != null) for (i in 0 until items.length()) {
                        val o = items.getJSONObject(i)
                        add(
                            Appointment(
                                id = o.getInt("id"),
                                dateTime = o.getLong("dateTime"),
                                reason = o.getString("reason"),
                                doctorId = o.optIntOrNull("doctorId"),
                                location = o.optStringOrNull("location"),
                                notes = o.optStringOrNull("notes"),
                                completed = o.optBoolean("completed", false),
                                deviceCalendarEventId = null
                            )
                        )
                    }
                }
                database.appointmentDao().deleteAll()
                database.appointmentDao().insertAllForRestore(list)
            }

            BackupSection.VISIT_NOTES -> {
                val items = root.optJSONArray("items")
                val list = buildList {
                    if (items != null) for (i in 0 until items.length()) {
                        val o = items.getJSONObject(i)
                        add(
                            Note(
                                id = o.getInt("id"),
                                title = o.getString("title"),
                                body = o.getString("body"),
                                createdAt = o.getLong("createdAt"),
                                appointmentId = o.optIntOrNull("appointmentId"),
                                doctorId = o.optIntOrNull("doctorId")
                            )
                        )
                    }
                }
                database.noteDao().deleteAll()
                database.noteDao().insertAllForRestore(list)
            }

            BackupSection.MEDICAL_ID -> {
                val patientJson = root.opt("patient")
                if (patientJson is JSONObject) {
                    database.patientDao().upsert(
                        Patient(
                            id = 1,
                            name = patientJson.getString("name"),
                            addressLine = patientJson.optStringOrNull("addressLine"),
                            city = patientJson.optStringOrNull("city"),
                            state = patientJson.optStringOrNull("state"),
                            zip = patientJson.optStringOrNull("zip"),
                            dateOfBirth = patientJson.optLongOrNull("dateOfBirth"),
                            bloodType = patientJson.optStringOrNull("bloodType"),
                            allergies = patientJson.optStringOrNull("allergies"),
                            emergencyContactName = patientJson.optStringOrNull("emergencyContactName"),
                            emergencyContactRelationship = patientJson.optStringOrNull("emergencyContactRelationship"),
                            emergencyContactPhone = patientJson.optStringOrNull("emergencyContactPhone"),
                            notes = patientJson.optStringOrNull("notes")
                        )
                    )
                }
                // patientJson missing/null means the backup had no Medical ID set -- leave whatever is in the app alone.
            }

            BackupSection.HEALTH -> {
                // No Room table behind this section yet (see BackupExporter) -- nothing to restore.
            }
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) getLong(key) else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) getDouble(key) else null
}
