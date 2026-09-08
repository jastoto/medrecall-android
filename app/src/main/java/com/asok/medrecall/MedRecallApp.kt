package com.asok.medrecall

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.asok.medrecall.navigation.Destination
import com.asok.medrecall.ui.MedRecallBottomBar
import com.asok.medrecall.ui.appointments.AppointmentFormScreen
import com.asok.medrecall.ui.appointments.AppointmentsScreen
import com.asok.medrecall.ui.ask.AskMedRecallScreen
import com.asok.medrecall.ui.conditions.ConditionFormScreen
import com.asok.medrecall.ui.conditions.ConditionsScreen
import com.asok.medrecall.ui.doctors.DoctorFormScreen
import com.asok.medrecall.ui.doctors.DoctorsScreen
import com.asok.medrecall.ui.lock.AppLockViewModel
import com.asok.medrecall.ui.lock.LockScreen
import com.asok.medrecall.ui.medications.MedicationFormScreen
import com.asok.medrecall.ui.medications.MedicationsScreen
import com.asok.medrecall.ui.medicalid.MedicalIdFormScreen
import com.asok.medrecall.ui.medicalid.MedicalIdScreen
import com.asok.medrecall.ui.record.RecordVisitScreen
import com.asok.medrecall.ui.reports.ReportDetailScreen
import com.asok.medrecall.ui.reports.ReportsScreen
import com.asok.medrecall.ui.reports.ReportType
import com.asok.medrecall.ui.screens.HomeScreen
import com.asok.medrecall.ui.screens.SplashScreen
import com.asok.medrecall.ui.screens.StubScreen
import com.asok.medrecall.ui.settings.AccountScreen
import com.asok.medrecall.ui.settings.BackupRestoreScreen
import com.asok.medrecall.ui.settings.PinSetupScreen
import com.asok.medrecall.ui.settings.SettingsScreen
import com.asok.medrecall.ui.vitals.VitalDetailScreen
import com.asok.medrecall.ui.vitals.VitalEntryFormScreen
import com.asok.medrecall.ui.vitals.VitalType
import com.asok.medrecall.ui.vitals.VitalsScreen
import kotlinx.coroutines.delay

/**
 * Top-level composable: gates the whole app behind [LockScreen] whenever
 * Settings > Security has Biometric Lock and/or a PIN turned on (see
 * ui/lock/AppLockViewModel.kt), then hosts the real NavHost once unlocked.
 * Re-locks itself every time the app is backgrounded (ON_STOP) so coming
 * back to MedRecall+ always re-challenges.
 */
@Composable
fun MedRecallApp() {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(2500)
        showSplash = false
    }

    if (showSplash) {
        SplashScreen()
        return
    }

    val appLockViewModel: AppLockViewModel = viewModel(factory = AppLockViewModel.factory(LocalContext.current))
    val lockState by appLockViewModel.uiState.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) appLockViewModel.relock()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        lockState.isLoading -> Box(modifier = Modifier.fillMaxSize())
        !lockState.isUnlocked -> LockScreen(
            uiState = lockState,
            onVerifyPin = { pin -> appLockViewModel.verifyPin(pin) },
            onUnlocked = { appLockViewModel.markUnlocked() }
        )
        else -> MedRecallNavHost()
    }
}

@Composable
private fun MedRecallNavHost() {
    val navController = rememberNavController()

    Scaffold(bottomBar = { MedRecallBottomBar(navController) }) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Destination.Home.route) {
                HomeScreen(onDestinationClick = { navController.navigate(it.route) })
            }
            composable(Destination.AskMedRecall.route) {
                AskMedRecallScreen(
                    onGoHome = { navController.popBackStack(Destination.Home.route, false) },
                    onNavigateToRoute = { route -> navController.navigate(route) }
                )
            }
            composable(Destination.HelpHowTo.route) {
                StubScreen(Destination.HelpHowTo.label, onGoHome = { navController.popBackStack(Destination.Home.route, false) })
            }
            composable(Destination.RecurringReminders.route) {
                StubScreen(Destination.RecurringReminders.label, onGoHome = { navController.popBackStack(Destination.Home.route, false) })
            }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onGoHome = { navController.popBackStack(Destination.Home.route, false) },
                    onOpenAccount = { navController.navigate("settings_account") },
                    onOpenBackupRestore = { navController.navigate("settings_backup_restore") },
                    onOpenPinSetup = { navController.navigate("settings_pin_setup") }
                )
            }
            composable("settings_account") {
                AccountScreen(onGoHome = { navController.popBackStack(Destination.Home.route, false) })
            }
            composable("settings_backup_restore") {
                BackupRestoreScreen(onGoHome = { navController.popBackStack(Destination.Home.route, false) })
            }
            composable("settings_pin_setup") {
                PinSetupScreen(
                    onDone = { navController.popBackStack() },
                    onGoHome = { navController.popBackStack(Destination.Home.route, false) }
                )
            }
            composable(Destination.RecordVisit.route) {
                RecordVisitScreen(
                    onOpenCalendar = { navController.navigate(Destination.Calendar.route) },
                    onScheduleAppointment = { navController.navigate("appointment_form") },
                    onOpenImport = { navController.navigate("import_stub") },
                    onAddDoctor = { navController.navigate("doctor_form") },
                    onGoHome = { navController.popBackStack(Destination.Home.route, false) }
                )
            }
            composable(Destination.Doctors.route) {
                DoctorsScreen(
                    onAddDoctor = { navController.navigate("doctor_form") },
                    onEditDoctor = { id -> navController.navigate("doctor_form/$id") },
                    onEditAppointment = { id -> navController.navigate("appointment_form/$id") },
                    onGoHome = { navController.popBackStack(Destination.Home.route, false) }
                )
            }
            composable(Destination.Medications.route) {
                MedicationsScreen(
                    onAddMedication = { navController.navigate("medication_form") },
                    onEditMedication = { id -> navController.navigate("medication_form/$id") },
                    onGoHome = { navController.popBackStack(Destination.Home.route, false) }
                )
            }
            composable(Destination.Vitals.route) {
                VitalsScreen(
                    onSelectVital = { vitalType -> navController.navigate("vital_detail/${vitalType.id}") },
                    onGoHome = { navController.popBackStack(Destination.Home.route, false) }
                )
            }
            composable(
                route = "vital_detail/{vitalTypeId}",
                arguments = listOf(navArgument("vitalTypeId") { type = NavType.StringType })
            ) { backStackEntry ->
                val vitalType = VitalType.fromId(backStackEntry.arguments?.getString("vitalTypeId") ?: VitalType.entries.first().id)
                VitalDetailScreen(
                    vitalType = vitalType,
                    onAddReading = { navController.navigate("vital_form/${vitalType.id}") },
                    onEditReading = { id -> navController.navigate("vital_form/${vitalType.id}/$id") },
                    onGoHome = { navController.popBackStack(Destination.Home.route, false) }
                )
            }
            composable(
                route = "vital_form/{vitalTypeId}",
                arguments = listOf(navArgument("vitalTypeId") { type = NavType.StringType })
            ) { backStackEntry ->
                val vitalType = VitalType.fromId(backStackEntry.arguments?.getString("vitalTypeId") ?: VitalType.entries.first().id)
                VitalEntryFormScreen(
                    vitalType = vitalType,
                    readingId = null,
                    onDone = { navController.popBackStack() }
                )
            }
            composable(
                route = "vital_form/{vitalTypeId}/{readingId}",
                arguments = listOf(
                    navArgument("vitalTypeId") { type = NavType.StringType },
                    navArgument("readingId") { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val vitalType = VitalType.fromId(backStackEntry.arguments?.getString("vitalTypeId") ?: VitalType.entries.first().id)
                val readingId = backStackEntry.arguments?.getInt("readingId")
                VitalEntryFormScreen(
                    vitalType = vitalType,
                    readingId = readingId,
                    onDone = { navController.popBackStack() }
                )
            }
            composable(Destination.Health.route) {
                StubScreen(Destination.Health.label, onGoHome = { navController.popBackStack(Destination.Home.route, false) })
            }
            composable(Destination.Conditions.route) {
                ConditionsScreen(
                    onAddCondition = { navController.navigate("condition_form") },
                    onEditCondition = { id -> navController.navigate("condition_form/$id") },
                    onGoHome = { navController.popBackStack(Destination.Home.route, false) }
                )
            }
            composable(Destination.Reports.route) {
                ReportsScreen(
                    onSelectReport = { reportType -> navController.navigate("report_detail/${reportType.id}") },
                    onGoHome = { navController.popBackStack(Destination.Home.route, false) }
                )
            }
            composable(
                route = "report_detail/{reportTypeId}",
                arguments = listOf(navArgument("reportTypeId") { type = NavType.StringType })
            ) { backStackEntry ->
                val reportTypeId = backStackEntry.arguments?.getString("reportTypeId") ?: ReportType.HISTORY.id
                ReportDetailScreen(
                    reportType = ReportType.fromId(reportTypeId),
                    onGoHome = { navController.popBackStack(Destination.Home.route, false) }
                )
            }
            composable(Destination.MedicalId.route) {
                MedicalIdScreen(
                    onEdit = { navController.navigate("medical_id_form") },
                    onGoHome = { navController.popBackStack(Destination.Home.route, false) }
                )
            }
            composable(Destination.Calendar.route) {
                AppointmentsScreen(
                    onAddAppointment = { navController.navigate("appointment_form") },
                    onEditAppointment = { id -> navController.navigate("appointment_form/$id") },
                    onGoHome = { navController.popBackStack(Destination.Home.route, false) }
                )
            }
            composable("appointment_form") {
                AppointmentFormScreen(
                    appointmentId = null,
                    onDone = { navController.popBackStack() }
                )
            }
            composable(
                route = "appointment_form/{appointmentId}",
                arguments = listOf(navArgument("appointmentId") { type = NavType.IntType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getInt("appointmentId")
                AppointmentFormScreen(
                    appointmentId = id,
                    onDone = { navController.popBackStack() }
                )
            }
            composable("medication_form") {
                MedicationFormScreen(
                    medicationId = null,
                    onDone = { navController.popBackStack() }
                )
            }
            composable(
                route = "medication_form/{medicationId}",
                arguments = listOf(navArgument("medicationId") { type = NavType.IntType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getInt("medicationId")
                MedicationFormScreen(
                    medicationId = id,
                    onDone = { navController.popBackStack() }
                )
            }
            composable("doctor_form") {
                DoctorFormScreen(
                    doctorId = null,
                    onDone = { navController.popBackStack() }
                )
            }
            composable(
                route = "doctor_form/{doctorId}",
                arguments = listOf(navArgument("doctorId") { type = NavType.IntType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getInt("doctorId")
                DoctorFormScreen(
                    doctorId = id,
                    onDone = { navController.popBackStack() }
                )
            }
            composable("condition_form") {
                ConditionFormScreen(
                    conditionId = null,
                    onDone = { navController.popBackStack() }
                )
            }
            composable(
                route = "condition_form/{conditionId}",
                arguments = listOf(navArgument("conditionId") { type = NavType.IntType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getInt("conditionId")
                ConditionFormScreen(
                    conditionId = id,
                    onDone = { navController.popBackStack() }
                )
            }
            composable("medical_id_form") {
                MedicalIdFormScreen(onDone = { navController.popBackStack() })
            }
            composable("import_stub") {
                StubScreen("Import", onGoHome = { navController.popBackStack(Destination.Home.route, false) })
            }
        }
    }
}
