package com.asok.medrecall.data.backup

import com.asok.medrecall.data.local.MedRecallDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Builds one JSON export per [BackupSection] from Room. Plain org.json
 * (already on Android, no new dependency) with explicit field-by-field
 * mapping rather than reflection/Gson, matching DriveBackupManager's
 * hand-rolled-REST approach elsewhere in this feature.
 *
 * RISK FLAG: unverified against a real build, same as the rest of the
 * backup/account connector work -- if something doesn't compile it's most
 * likely a DAO method name mismatch here (each branch assumes the
 * corresponding *Dao.getAllOnce()/getOnce() from data/local/dao/).
 */
object BackupExporter {

    suspend fun export(database: MedRecallDatabase, section: BackupSection): String {
        val root = JSONObject()
        root.put("section", section.label)
        root.put("exportedAt", LocalDate.now().toString())

        when (section) {
            BackupSection.DOCTORS -> {
                val items = JSONArray()
                database.doctorDao().getAllOnce().forEach { d ->
                    items.put(JSONObject().apply {
                        put("id", d.id)
                        put("name", d.name)
                        putIfNotNull("specialty", d.specialty)
                        putIfNotNull("phone", d.phone)
                        putIfNotNull("address", d.address)
                        putIfNotNull("notes", d.notes)
                    })
                }
                root.put("items", items)
            }

            BackupSection.MEDICATIONS -> {
                val items = JSONArray()
                database.medicationDao().getAllOnce().forEach { m ->
                    items.put(JSONObject().apply {
                        put("id", m.id)
                        put("name", m.name)
                        putIfNotNull("dosage", m.dosage)
                        putIfNotNull("schedule", m.schedule)
                        putIfNotNull("prescribingDoctorId", m.prescribingDoctorId)
                        putIfNotNull("conditionId", m.conditionId)
                        putIfNotNull("startDate", m.startDate)
                        putIfNotNull("endDate", m.endDate)
                        put("active", m.active)
                        putIfNotNull("notes", m.notes)
                    })
                }
                root.put("items", items)
            }

            BackupSection.VITALS -> {
                val items = JSONArray()
                database.vitalReadingDao().getAllOnce().forEach { v ->
                    items.put(JSONObject().apply {
                        put("id", v.id)
                        put("type", v.type)
                        put("recordedAt", v.recordedAt)
                        put("primaryValue", v.primaryValue)
                        putIfNotNull("secondaryValue", v.secondaryValue)
                        putIfNotNull("tertiaryValue", v.tertiaryValue)
                        putIfNotNull("context", v.context)
                        putIfNotNull("notes", v.notes)
                        put("source", v.source)
                    })
                }
                root.put("items", items)
                root.put(
                    "note",
                    "Only manually-entered readings stored in the app are included; live Health Connect data is not exported."
                )
            }

            BackupSection.CONDITIONS -> {
                val items = JSONArray()
                database.conditionDao().getAllOnce().forEach { c ->
                    items.put(JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("status", c.status)
                        put("iconKey", c.iconKey)
                        putIfNotNull("notes", c.notes)
                        put("includedInMedicalId", c.includedInMedicalId)
                        putIfNotNull("doctorId", c.doctorId)
                    })
                }
                root.put("items", items)
            }

            BackupSection.APPOINTMENTS -> {
                val items = JSONArray()
                database.appointmentDao().getAllOnce().forEach { a ->
                    items.put(JSONObject().apply {
                        put("id", a.id)
                        put("dateTime", a.dateTime)
                        put("reason", a.reason)
                        putIfNotNull("doctorId", a.doctorId)
                        putIfNotNull("location", a.location)
                        putIfNotNull("notes", a.notes)
                        put("completed", a.completed)
                    })
                }
                root.put("items", items)
            }

            BackupSection.VISIT_NOTES -> {
                val items = JSONArray()
                database.noteDao().getAllOnce().forEach { n ->
                    items.put(JSONObject().apply {
                        put("id", n.id)
                        put("title", n.title)
                        put("body", n.body)
                        put("createdAt", n.createdAt)
                        putIfNotNull("appointmentId", n.appointmentId)
                        putIfNotNull("doctorId", n.doctorId)
                    })
                }
                root.put("items", items)
            }

            BackupSection.MEDICAL_ID -> {
                val patient = database.patientDao().getOnce()
                if (patient != null) {
                    root.put("patient", JSONObject().apply {
                        put("id", patient.id)
                        put("name", patient.name)
                        putIfNotNull("addressLine", patient.addressLine)
                        putIfNotNull("city", patient.city)
                        putIfNotNull("state", patient.state)
                        putIfNotNull("zip", patient.zip)
                        putIfNotNull("dateOfBirth", patient.dateOfBirth)
                        putIfNotNull("bloodType", patient.bloodType)
                        putIfNotNull("allergies", patient.allergies)
                        putIfNotNull("emergencyContactName", patient.emergencyContactName)
                        putIfNotNull("emergencyContactRelationship", patient.emergencyContactRelationship)
                        putIfNotNull("emergencyContactPhone", patient.emergencyContactPhone)
                        putIfNotNull("notes", patient.notes)
                    })
                } else {
                    root.put("patient", JSONObject.NULL)
                }
            }

            BackupSection.HEALTH -> {
                // No Room table behind this section yet -- Destination.Health
                // is still a StubScreen. Placeholder only, per Asok's request
                // to include it in backups anyway.
                root.put("status", "not_yet_implemented")
                root.put("note", "The Health section of the app hasn't been built yet, so there is no data to back up.")
            }
        }

        return root.toString(2)
    }

    private fun JSONObject.putIfNotNull(key: String, value: Any?): JSONObject {
        if (value != null) put(key, value)
        return this
    }
}
