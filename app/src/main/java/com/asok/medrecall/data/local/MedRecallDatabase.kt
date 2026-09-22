package com.asok.medrecall.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.asok.medrecall.data.local.dao.AppointmentDao
import com.asok.medrecall.data.local.dao.ConditionDao
import com.asok.medrecall.data.local.dao.DoctorDao
import com.asok.medrecall.data.local.dao.MedicationDao
import com.asok.medrecall.data.local.dao.NoteDao
import com.asok.medrecall.data.local.dao.PatientDao
import com.asok.medrecall.data.local.dao.VitalReadingDao

@Database(
    entities = [Patient::class, Doctor::class, Appointment::class, Medication::class, Note::class, Condition::class, VitalReading::class],
    version = 12,
    exportSchema = false
)
abstract class MedRecallDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun doctorDao(): DoctorDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun medicationDao(): MedicationDao
    abstract fun noteDao(): NoteDao
    abstract fun conditionDao(): ConditionDao
    abstract fun vitalReadingDao(): VitalReadingDao

    companion object {
        @Volatile
        private var INSTANCE: MedRecallDatabase? = null

        fun getInstance(context: Context): MedRecallDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MedRecallDatabase::class.java,
                    "medrecall.db"
                )
                    // No migrations written yet — fine while we're still shaping the schema
                    // during early development. Revisit before this ships for real, since this
                    // wipes the local db on every version bump instead of preserving data.
                    // FLAG: beta testers on Firebase App Distribution are now using real
                    // personal data (see project notes) -- a version bump wipes their local
                    // db on next update. Worth writing a real migration before the next beta
                    // push if that data matters to keep.
                    // v8 -> v9: added Condition.doctorId (punch item #5).
                    // v11 -> v12: added VitalReading.customLabel/customUnit for one-off
                    // Custom readings on the new Health page (punch item #13); added
                    // VitalType.A1C/CHOLESTEROL (no schema change, just new `type` strings).
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
