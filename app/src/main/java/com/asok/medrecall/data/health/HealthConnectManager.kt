package com.asok.medrecall.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.asok.medrecall.ui.vitals.VitalReadingDisplay
import com.asok.medrecall.ui.vitals.VitalType
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Thin READ-ONLY wrapper around Health Connect (Android's closest
 * equivalent to Apple Health/Apple Watch data -- Samsung Health and most
 * watches sync into it). MedRecall never writes to Health Connect; manual
 * entries stay the source of truth in our own Room database
 * (see data/local/VitalReading.kt) -- this just overlays whatever Health
 * Connect can supply on top of that, per screen, per vital.
 *
 * Everything here is best-effort: any failure (permission revoked
 * mid-session, Health Connect briefly unavailable, no data for the range)
 * returns an empty list/false rather than throwing, so a Vitals screen
 * always degrades gracefully to "manual only" instead of crashing.
 */
object HealthConnectManager {

    /** How far back to look for readings each time a Vitals screen loads. */
    private const val LOOKBACK_DAYS = 30L

    fun isAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun getClient(context: Context): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    /** Every read permission MedRecall ever asks Health Connect for, in one shot. */
    val allReadPermissions: Set<String> = VitalType.entries.map { it.healthConnectReadPermission }.toSet()

    private suspend fun grantedPermissions(client: HealthConnectClient): Set<String> = try {
        client.permissionController.getGrantedPermissions()
    } catch (e: Exception) {
        emptySet()
    }

    suspend fun hasPermission(client: HealthConnectClient, vitalType: VitalType): Boolean =
        grantedPermissions(client).contains(vitalType.healthConnectReadPermission)

    suspend fun hasAllPermissions(client: HealthConnectClient): Boolean =
        grantedPermissions(client).containsAll(allReadPermissions)

    suspend fun readReadings(client: HealthConnectClient, vitalType: VitalType): List<VitalReadingDisplay> {
        val range = TimeRangeFilter.between(
            Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS),
            Instant.now()
        )
        return try {
            when (vitalType) {
                VitalType.HEART_RATE -> readHeartRate(client, range)
                VitalType.RESTING_HEART_RATE -> client.readRecords(
                    ReadRecordsRequest(recordType = RestingHeartRateRecord::class, timeRangeFilter = range)
                ).records.map {
                    VitalReadingDisplay(
                        recordedAt = it.time.toEpochMilli(),
                        primaryValue = it.beatsPerMinute.toDouble(),
                        source = "HEALTH_CONNECT"
                    )
                }
                VitalType.HEART_RATE_VARIABILITY -> client.readRecords(
                    ReadRecordsRequest(recordType = HeartRateVariabilityRmssdRecord::class, timeRangeFilter = range)
                ).records.map {
                    VitalReadingDisplay(
                        recordedAt = it.time.toEpochMilli(),
                        primaryValue = it.heartRateVariabilityMillis,
                        source = "HEALTH_CONNECT"
                    )
                }
                VitalType.BLOOD_PRESSURE -> client.readRecords(
                    ReadRecordsRequest(recordType = BloodPressureRecord::class, timeRangeFilter = range)
                ).records.map {
                    VitalReadingDisplay(
                        recordedAt = it.time.toEpochMilli(),
                        primaryValue = it.systolic.inMillimetersOfMercury,
                        secondaryValue = it.diastolic.inMillimetersOfMercury,
                        source = "HEALTH_CONNECT"
                    )
                }
                VitalType.BLOOD_OXYGEN -> client.readRecords(
                    ReadRecordsRequest(recordType = OxygenSaturationRecord::class, timeRangeFilter = range)
                ).records.map {
                    VitalReadingDisplay(
                        recordedAt = it.time.toEpochMilli(),
                        primaryValue = it.percentage.value,
                        source = "HEALTH_CONNECT"
                    )
                }
                VitalType.RESPIRATORY_RATE -> client.readRecords(
                    ReadRecordsRequest(recordType = RespiratoryRateRecord::class, timeRangeFilter = range)
                ).records.map {
                    VitalReadingDisplay(
                        recordedAt = it.time.toEpochMilli(),
                        primaryValue = it.rate,
                        source = "HEALTH_CONNECT"
                    )
                }
                VitalType.BODY_TEMPERATURE -> client.readRecords(
                    ReadRecordsRequest(recordType = BodyTemperatureRecord::class, timeRangeFilter = range)
                ).records.map {
                    VitalReadingDisplay(
                        recordedAt = it.time.toEpochMilli(),
                        primaryValue = it.temperature.inFahrenheit,
                        source = "HEALTH_CONNECT"
                    )
                }
                VitalType.BLOOD_GLUCOSE -> client.readRecords(
                    ReadRecordsRequest(recordType = BloodGlucoseRecord::class, timeRangeFilter = range)
                ).records.map {
                    VitalReadingDisplay(
                        recordedAt = it.time.toEpochMilli(),
                        primaryValue = it.level.inMilligramsPerDeciliter,
                        source = "HEALTH_CONNECT"
                    )
                }
                VitalType.WEIGHT -> client.readRecords(
                    ReadRecordsRequest(recordType = WeightRecord::class, timeRangeFilter = range)
                ).records.map {
                    VitalReadingDisplay(
                        recordedAt = it.time.toEpochMilli(),
                        primaryValue = it.weight.inPounds,
                        source = "HEALTH_CONNECT"
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Heart rate is a time-series (many samples per recording session), so
     * one row per HeartRateRecord -- averaging its samples, with the
     * min/max kept alongside -- keeps the reading list readable instead of
     * showing hundreds of near-identical rows from a single watch session.
     */
    private suspend fun readHeartRate(client: HealthConnectClient, range: TimeRangeFilter): List<VitalReadingDisplay> =
        client.readRecords(ReadRecordsRequest(recordType = HeartRateRecord::class, timeRangeFilter = range))
            .records.map { record ->
                val bpms = record.samples.map { it.beatsPerMinute }
                VitalReadingDisplay(
                    recordedAt = record.startTime.toEpochMilli(),
                    primaryValue = bpms.average(),
                    secondaryValue = bpms.minOrNull()?.toDouble(),
                    tertiaryValue = bpms.maxOrNull()?.toDouble(),
                    source = "HEALTH_CONNECT"
                )
            }
}
