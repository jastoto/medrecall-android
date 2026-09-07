package com.asok.medrecall

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.asok.medrecall.navigation.Destination
import com.asok.medrecall.ui.MedRecallBottomBar
import com.asok.medrecall.ui.appointments.AppointmentFormScreen
import com.asok.medrecall.ui.appointments.AppointmentsScreen
import com.asok.medrecall.ui.medications.MedicationFormScreen
import com.asok.medrecall.ui.medications.MedicationsScreen
import com.asok.medrecall.ui.screens.HomeScreen
import com.asok.medrecall.ui.screens.StubScreen

@Composable
fun MedRecallApp() {
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
            composable(Destination.AskMedRecall.route) { StubScreen(Destination.AskMedRecall.label) }
            composable(Destination.HelpHowTo.route) { StubScreen(Destination.HelpHowTo.label) }
            composable(Destination.RecurringReminders.route) { StubScreen(Destination.RecurringReminders.label) }
            composable(Destination.Settings.route) { StubScreen(Destination.Settings.label) }
            composable(Destination.RecordVisit.route) { StubScreen(Destination.RecordVisit.label) }
            composable(Destination.Doctors.route) { StubScreen(Destination.Doctors.label) }
            composable(Destination.Medications.route) {
                MedicationsScreen(
                    onAddMedication = { navController.navigate("medication_form") },
                    onEditMedication = { id -> navController.navigate("medication_form/$id") }
                )
            }
            composable(Destination.Vitals.route) { StubScreen(Destination.Vitals.label) }
            composable(Destination.Health.route) { StubScreen(Destination.Health.label) }
            composable(Destination.Conditions.route) { StubScreen(Destination.Conditions.label) }
            composable(Destination.Reports.route) { StubScreen(Destination.Reports.label) }
            composable(Destination.MedicalId.route) { StubScreen(Destination.MedicalId.label) }
            composable(Destination.Calendar.route) {
                AppointmentsScreen(
                    onAddAppointment = { navController.navigate("appointment_form") },
                    onEditAppointment = { id -> navController.navigate("appointment_form/$id") }
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
        }
    }
}
