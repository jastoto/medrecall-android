package com.asok.medrecall.data.backup

import com.asok.medrecall.data.local.Appointment
import com.asok.medrecall.data.local.Condition
import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.data.local.MedRecallDatabase
import com.asok.medrecall.data.local.Medication
import com.asok.medrecall.data.local.Note
import com.asok.medrecall.data.local.Patient
import com.asok.medrecall.data.local.VitalReading
import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns the sections of the database Asok picked into one JSON file
 * (for upload to Google Drive / OneDrive / Files on This Phone), and
 * back again on restore.
 *
 * Restore is "replace everything" (punch-list decision, not merge):
 * every table covered by the backup's own `sections` list is wiped
 * with `deleteAll()` and reinserted from the file. IDs are preserved
 * on insert (Room lets you insert an explicit non-zero autoGenerate
 * primary key), so cross-references like Medication.doctorId or
 * Condition.doctorId still point at the right row after a restore --
 * this only works because nothing in this table set actually declares
 * a Room @ForeignKey, so there's no ordering constraint on inserts.
 *
 * There is no schema migration story here: a backup is read by
 * [FORMAT_VERSION], and an older or newer backup than this app
 * understands is rejected outright rather than guessed at.
 */
object BackupCodec {
    private const val FORMAT_VERSION = 1

    suspend fun export(db: MedRecallDatabase, sections: Set<BackupSection>): String {
        val root = JSONObject()
        root.put("formatVersion", FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("sections", JSONArray(sections.map { it.name }))

        if (BackupSection.MEDICAL_ID in sections) {
            db.patientDao().getOnce()?.let { root.put("patient", it.toJson()) }
        }
        if (BackupSection.DOCTORS in sections) {
            root.put("doctors", JSONArray(db.doctorDao().getAllOnce().map { it.toJson() }))
        }
        if (BackupSection.CONDITIONS in sections) {
            root.put("conditions", JSONArray(db.conditionDao().getAllOnce().map { it.toJson() }))
        }
        if (BackupSection.MEDICATIONS in sections) {
            root.put("medications", JSONArray(db.medicationDao().getAllOnce().map { it.toJson() }))
        }
        if (BackupSection.APPOINTMENTS in sections) {
            root.put("appointments", JSONArray(db.appointmentDao().getAllOnce().map { it.toJson() }))
        }
        if (BackupSection.VITALS in sections) {
            root.put("vitals", JSONArray(db.vitalReadingDao().getAllOnce().map { it.toJson() }))
        }
        if (BackupSection.NOTES in sections) {
            root.put("notes", JSONArray(db.noteDao().getAllOnce().map { it.toJson() }))
        }
        return root.toString()
    }

    /** @return the sections actually found and restored from [json]. */
    suspend fun import(db: MedRecallDatabase, json: String): Set<BackupSection> {
        val root = JSONObject(json)
        val formatVersion = root.optInt("formatVersion", -1)
        require(formatVersion == FORMAT_VERSION) {
            "This backup was made by an incompatible version of MedRecall+ (format $formatVersion, expected $FORMAT_VERSION)."
        }

        val sections = mutableSetOf<BackupSection>()
        root.optJSONArray("sections")?.let { arr ->
            for (i in 0 until arr.length()) {
                runCatching { BackupSection.valueOf(arr.getString(i)) }.getOrNull()?.let { sections.add(it) }
            }
        }

        if (BackupSection.MEDICAL_ID in sections) {
            root.optJSONObject("patient")?.let { db.patientDao().upsert(patientFromJson(it)) }
        }
        if (BackupSection.DOCTORS in sections) {
            db.doctorDao().deleteAll()
            root.optJSONArray("doctors")?.let { arr ->
                for (i in 0 until arr.length()) db.doctorDao().insert(doctorFromJson(arr.getJSONObject(i)))
            }
        }
        if (BackupSection.CONDITIONS in sections) {
            db.conditionDao().deleteAll()
            root.optJSONArray("conditions")?.let { arr ->
                for (i in 0 until arr.length()) db.conditionDao().insert(conditionFromJson(arr.getJSONObject(i)))
            }
        }
        if (BackupSection.MEDICATIONS in sections) {
            db.medicationDao().deleteAll()
            root.optJSONArray("medications")?.let { arr ->
                for (i in 0 until arr.length()) db.medicationDao().insert(medicationFromJson(arr.getJSONObject(i)))
            }
        }
        if (BackupSection.APPOINTMENTS in sections) {
            db.appointmentDao().deleteAll()
            root.optJSONArray("appointments")?.let { arr ->
                for (i in 0 until arr.length()) db.appointmentDao().insert(appointmentFromJson(arr.getJSONObject(i)))
            }
        }
        if (BackupSection.VITALS in sections) {
            db.vitalReadingDao().deleteAll()
            root.optJSONArray("vitals")?.let { arr ->
                for (i in 0 until arr.length()) db.vitalReadingDao().insert(vitalFromJson(arr.getJSONObject(i)))
            }
        }
        if (BackupSection.NOTES in sections) {
            db.noteDao().deleteAll()
            root.optJSONArray("notes")?.let { arr ->
                for (i in 0 until arr.length()) db.noteDao().insert(noteFromJson(arr.getJSONObject(i)))
            }
        }
        return sections
    }

    // -- JSONObject <-> entity conversions. org.json has no native "put a
    // null" story that round-trips cleanly with optX() on read, so every
    // nullable field is simply omitted from the JSON when null and read
    // back with optX()/-1 sentinels swapped for null. --

    private fun Doctor.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        specialty?.let { put("specialty", it) }
        phone?.let { put("phone", it) }
        address?.let { put("address", it) }
        notes?.let { put("notes", it) }
    }

    private fun doctorFromJson(o: JSONObject) = Doctor(
        id = o.getInt("id"),
        name = o.getString("name"),
        specialty = o.optStringOrNull("specialty"),
        phone = o.optStringOrNull("phone"),
        address = o.optStringOrNull("address"),
        notes = o.optStringOrNull("notes")
    )

    private fun Condition.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("status", status)
        put("iconKey", iconKey)
        notes?.let { put("notes", it) }
        put("includedInMedicalId", includedInMedicalId)
        doctorId?.let { put("doctorId", it) }
    }

    private fun conditionFromJson(o: JSONObject) = Condition(
        id = o.getInt("id"),
        name = o.getString("name"),
        status = o.optString("status", "Active"),
        iconKey = o.optString("iconKey", "assignment"),
        notes = o.optStringOrNull("notes"),
        includedInMedicalId = o.optBoolean("includedInMedicalId", true),
        doctorId = o.optIntOrNull("doctorId")
    )

    private fun Medication.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        dosage?.let { put("dosage", it) }
        schedule?.let { put("schedule", it) }
        prescribingDoctorId?.let { put("prescribingDoctorId", it) }
        startDate?.let { put("startDate", it) }
        endDate?.let { put("endDate", it) }
        put("active", active)
        notes?.let { put("notes", it) }
        conditionId?.let { put("conditionId", it) }
    }

    private fun medicationFromJson(o: JSONObject) = Medication(
        id = o.getInt("id"),
        name = o.getString("name"),
        dosage = o.optStringOrNull("dosage"),
        schedule = o.optStringOrNull("schedule"),
        prescribingDoctorId = o.optIntOrNull("prescribingDoctorId"),
        startDate = o.optLongOrNull("startDate"),
        endDate = o.optLongOrNull("endDate"),
        active = o.optBoolean("active", true),
        notes = o.optStringOrNull("notes"),
        conditionId = o.optIntOrNull("conditionId")
    )

    private fun Appointment.toJson() = JSONObject().apply {
        put("id", id)
        put("dateTime", dateTime)
        put("reason", reason)
        doctorId?.let { put("doctorId", it) }
        location?.let { put("location", it) }
        notes?.let { put("notes", it) }
        put("completed", completed)
    }

    private fun appointmentFromJson(o: JSONObject) = Appointment(
        id = o.getInt("id"),
        dateTime = o.getLong("dateTime"),
        reason = o.getString("reason"),
        doctorId = o.optIntOrNull("doctorId"),
        location = o.optStringOrNull("location"),
        notes = o.optStringOrNull("notes"),
        completed = o.optBoolean("completed", false)
    )

    private fun VitalReading.toJson() = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("recordedAt", recordedAt)
        put("primaryValue", primaryValue)
        secondaryValue?.let { put("secondaryValue", it) }
        tertiaryValue?.let { put("tertiaryValue", it) }
        context?.let { put("context", it) }
        notes?.let { put("notes", it) }
        put("source", source)
    }

    private fun vitalFromJson(o: JSONObject) = VitalReading(
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

    private fun Patient.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        addressLine?.let { put("addressLine", it) }
        city?.let { put("city", it) }
        state?.let { put("state", it) }
        zip?.let { put("zip", it) }
        dateOfBirth?.let { put("dateOfBirth", it) }
        bloodType?.let { put("bloodType", it) }
        allergies?.let { put("allergies", it) }
        emergencyContactName?.let { put("emergencyContactName", it) }
        emergencyContactRelationship?.let { put("emergencyContactRelationship", it) }
        emergencyContactPhone?.let { put("emergencyContactPhone", it) }
        notes?.let { put("notes", it) }
    }

    private fun patientFromJson(o: JSONObject) = Patient(
        id = 1,
        name = o.getString("name"),
        addressLine = o.optStringOrNull("addressLine"),
        city = o.optStringOrNull("city"),
        state = o.optStringOrNull("state"),
        zip = o.optStringOrNull("zip"),
        dateOfBirth = o.optLongOrNull("dateOfBirth"),
        bloodType = o.optStringOrNull("bloodType"),
        allergies = o.optStringOrNull("allergies"),
        emergencyContactName = o.optStringOrNull("emergencyContactName"),
        emergencyContactRelationship = o.optStringOrNull("emergencyContactRelationship"),
        emergencyContactPhone = o.optStringOrNull("emergencyContactPhone"),
        notes = o.optStringOrNull("notes")
    )

    private fun Note.toJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("body", body)
        put("createdAt", createdAt)
        appointmentId?.let { put("appointmentId", it) }
        doctorId?.let { put("doctorId", it) }
    }

    private fun noteFromJson(o: JSONObject) = Note(
        id = o.getInt("id"),
        title = o.getString("title"),
        body = o.getString("body"),
        createdAt = o.getLong("createdAt"),
        appointmentId = o.optIntOrNull("appointmentId"),
        doctorId = o.optIntOrNull("doctorId")
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) getLong(key) else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) getDouble(key) else null
}
