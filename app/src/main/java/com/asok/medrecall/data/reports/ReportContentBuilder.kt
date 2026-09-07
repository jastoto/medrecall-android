package com.asok.medrecall.data.reports

import com.asok.medrecall.data.local.MedRecallDatabase
import com.asok.medrecall.ui.reports.ReportType
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReportSection(val heading: String, val lines: List<String>)
data class ReportDocument(val title: String, val subtitle: String, val sections: List<ReportSection>)

private val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.US)
private val dateTimeFmt = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US)

/**
 * Pulls whatever data each report type needs straight from Room (one-shot
 * reads via Flow.first()) and shapes it into a plain ReportDocument that
 * ReportPdfWriter can lay out. Vitals and Medicare Claims have no data
 * model yet, so they fall back to a placeholder section explaining that.
 */
class ReportContentBuilder(private val db: MedRecallDatabase) {

    suspend fun build(reportType: ReportType): ReportDocument {
        val patient = db.patientDao().observe().first()
        val subtitle = "Generated ${dateTimeFmt.format(Date())}" + (patient?.name?.let { " · $it" } ?: "")

        return when (reportType) {
            ReportType.HISTORY -> buildHistory(subtitle)
            ReportType.HEALTH_SUMMARY -> buildHealthSummary(subtitle)
            ReportType.CONDITION_REPORT -> buildConditionReport(subtitle)
            ReportType.DOCTORS_REPORT -> buildDoctorsReport(subtitle)
            ReportType.MEDICATIONS_REPORT -> buildMedicationsReport(subtitle)
            ReportType.VITALS_REPORT -> placeholder(reportType, subtitle)
            ReportType.MEDICARE_CLAIMS -> placeholder(reportType, subtitle)
        }
    }

    private suspend fun buildHistory(subtitle: String): ReportDocument {
        val doctors = db.doctorDao().observeAll().first().associateBy { it.id }
        val appointments = db.appointmentDao().observeAll().first().sortedByDescending { it.dateTime }
        val notes = db.noteDao().observeAll().first()

        val apptLines = if (appointments.isEmpty()) listOf("No appointments on file.") else
            appointments.map { a ->
                val doc = a.doctorId?.let { doctors[it]?.name } ?: "Unspecified doctor"
                buildString {
                    append(dateFmt.format(Date(a.dateTime)))
                    append(" — ${a.reason} with $doc")
                    a.location?.let { append(" ($it)") }
                    if (a.completed) append(" [Completed]")
                }
            }

        val noteLines = if (notes.isEmpty()) listOf("No notes on file.") else
            notes.map { n ->
                val doc = n.doctorId?.let { doctors[it]?.name }
                dateFmt.format(Date(n.createdAt)) + " — " + n.title + (doc?.let { " (Dr. $it)" } ?: "")
            }

        return ReportDocument(
            "History", subtitle,
            listOf(ReportSection("Appointments", apptLines), ReportSection("Notes", noteLines))
        )
    }

    private suspend fun buildHealthSummary(subtitle: String): ReportDocument {
        val patient = db.patientDao().observe().first()
        val conditions = db.conditionDao().observeAll().first().filter { it.status == "Active" }
        val activeMeds = db.medicationDao().observeActive().first()
        val doctors = db.doctorDao().observeAll().first().associateBy { it.id }
        val upcoming = db.appointmentDao().observeUpcoming(System.currentTimeMillis()).first()
            .sortedBy { it.dateTime }.take(5)

        val patientLines = if (patient == null) listOf("No profile on file yet.") else buildList {
            add("Name: ${patient.name}")
            patient.dateOfBirth?.let { add("Date of birth: ${dateFmt.format(Date(it))}") }
            patient.bloodType?.let { add("Blood type: $it") }
            patient.allergies?.let { add("Allergies: $it") }
        }

        val conditionLines = if (conditions.isEmpty()) listOf("No active conditions.") else conditions.map { it.name }

        val medLines = if (activeMeds.isEmpty()) listOf("No active medications.") else
            activeMeds.map { m ->
                val doc = m.prescribingDoctorId?.let { doctors[it]?.name }
                buildString {
                    append(m.name)
                    m.dosage?.let { append(" — $it") }
                    m.schedule?.let { append(" · $it") }
                    doc?.let { append(" (Dr. $it)") }
                }
            }

        val upcomingLines = if (upcoming.isEmpty()) listOf("No upcoming appointments.") else
            upcoming.map { a ->
                val doc = a.doctorId?.let { doctors[it]?.name } ?: "Unspecified doctor"
                "${dateFmt.format(Date(a.dateTime))} — ${a.reason} with $doc"
            }

        return ReportDocument(
            "Health Summary", subtitle,
            listOf(
                ReportSection("Patient Information", patientLines),
                ReportSection("Active Conditions", conditionLines),
                ReportSection("Current Medications", medLines),
                ReportSection("Upcoming Appointments", upcomingLines)
            )
        )
    }

    private suspend fun buildConditionReport(subtitle: String): ReportDocument {
        val conditions = db.conditionDao().observeAll().first()
        if (conditions.isEmpty()) {
            return ReportDocument("Condition Report", subtitle, listOf(ReportSection("Conditions", listOf("No conditions on file."))))
        }
        val grouped = conditions.groupBy { it.status }
        val sections = listOf("Active", "Monitoring", "Resolved").mapNotNull { status ->
            val forStatus = grouped[status]?.sortedBy { it.name } ?: return@mapNotNull null
            if (forStatus.isEmpty()) return@mapNotNull null
            ReportSection(status, forStatus.map { c -> c.name + (c.notes?.let { " — $it" } ?: "") })
        }
        return ReportDocument("Condition Report", subtitle, sections.ifEmpty {
            listOf(ReportSection("Conditions", conditions.map { it.name }))
        })
    }

    private suspend fun buildDoctorsReport(subtitle: String): ReportDocument {
        val doctors = db.doctorDao().observeAll().first()
        val lines = if (doctors.isEmpty()) listOf("No doctors on file.") else
            doctors.map { d ->
                buildString {
                    append(d.name)
                    d.specialty?.let { append(" — $it") }
                    d.phone?.let { append(" · $it") }
                    d.address?.let { append(" · $it") }
                }
            }
        return ReportDocument("Doctors Report", subtitle, listOf(ReportSection("Doctors", lines)))
    }

    private suspend fun buildMedicationsReport(subtitle: String): ReportDocument {
        val meds = db.medicationDao().observeAll().first()
        val doctors = db.doctorDao().observeAll().first().associateBy { it.id }
        if (meds.isEmpty()) {
            return ReportDocument("Medications Report", subtitle, listOf(ReportSection("Medications", listOf("No medications on file."))))
        }
        val grouped = meds.groupBy { it.prescribingDoctorId?.let { id -> doctors[id]?.name } ?: "Unspecified doctor" }
        val sections = grouped.entries.sortedBy { it.key }.map { (doctorName, medsForDoctor) ->
            ReportSection(
                doctorName,
                medsForDoctor.sortedBy { it.name }.map { m ->
                    buildString {
                        append(m.name)
                        m.dosage?.let { append(" — $it") }
                        m.schedule?.let { append(" · $it") }
                        if (!m.active) append(" [Inactive]")
                    }
                }
            )
        }
        return ReportDocument("Medications Report", subtitle, sections)
    }

    private fun placeholder(reportType: ReportType, subtitle: String): ReportDocument =
        ReportDocument(reportType.title, subtitle, listOf(ReportSection(reportType.title, listOf(reportType.description))))
}
