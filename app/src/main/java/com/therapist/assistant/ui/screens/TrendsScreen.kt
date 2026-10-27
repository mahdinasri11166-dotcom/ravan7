package com.therapist.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.therapist.assistant.assessment.AssessmentCatalog
import com.therapist.assistant.ui.components.GradientTopBar
import com.therapist.assistant.ui.components.SoftCard
import com.therapist.assistant.util.JalaliDate
import com.therapist.assistant.viewmodel.AppointmentViewModel

/**
 * داشبورد روند درمان: آزمون‌ها، پیگیری‌ها، بدهی‌ها.
 */
@Composable
fun TrendsScreen(
    viewModel: AppointmentViewModel,
    onBack: () -> Unit,
    onClient: (Long) -> Unit
) {
    val clients by viewModel.clients.collectAsState()
    val appts by viewModel.allAppointments.collectAsState()
    val followUps by viewModel.pendingFollowUps.collectAsState()
    val unpaid by viewModel.unpaidAppointments.collectAsState()
    val now = System.currentTimeMillis()
    val fourteenDays = 14L * 24 * 60 * 60 * 1000

    val inactiveClients = remember(clients, appts) {
        clients.filter { !it.isArchived }.mapNotNull { c ->
            val last = appts.filter { it.clientId == c.id }.maxByOrNull { it.dateTimeMillis }
            val lastMs = last?.dateTimeMillis ?: c.createdAt
            if (now - lastMs > fourteenDays) c to lastMs else null
        }.sortedBy { it.second }
    }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            GradientTopBar(title = "روند درمان و پیگیری", subtitle = "داشبورد تجاری", onBack = onBack)
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    SoftCard {
                        Text("خلاصه", fontWeight = FontWeight.Bold)
                        Text("مراجع فعال: ${clients.count { !it.isArchived }}")
                        Text("پیگیری باز: ${followUps.size}")
                        Text("نوبت بدهکار: ${unpaid.size}")
                        Text("بدون جلسه بیش از ۱۴ روز: ${inactiveClients.size}")
                    }
                }
                item {
                    SoftCard {
                        Text("پیگیری‌های موعددار", fontWeight = FontWeight.Bold)
                        if (followUps.isEmpty()) {
                            Text("موردی نیست", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                items(followUps, key = { it.id }) { f ->
                    SoftCard(onClick = { onClient(f.clientId) }) {
                        Text(f.clientName, fontWeight = FontWeight.SemiBold)
                        Text(f.reason, style = MaterialTheme.typography.bodySmall)
                        Text("موعد: ${JalaliDate.formatDate(f.dueMillis)}", style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = { viewModel.completeFollowUp(f.id) }) {
                            Text("انجام شد")
                        }
                    }
                }
                item {
                    SoftCard {
                        Row {
                            Icon(Icons.Default.TrendingUp, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("مراجع بدون جلسه ≥ ۱۴ روز", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                items(inactiveClients.take(20), key = { it.first.id }) { (c, last) ->
                    SoftCard(onClick = { onClient(c.id) }) {
                        Text(c.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "آخرین فعالیت: ${JalaliDate.formatDate(last)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(
                            onClick = {
                                viewModel.addFollowUp(
                                    c.id,
                                    c.name,
                                    "پیگیری قطع ارتباط بیش از ۱۴ روز",
                                    now + 3L * 24 * 60 * 60 * 1000
                                )
                            }
                        ) { Text("ثبت پیگیری ۳ روزه") }
                    }
                }
            }
        }
    }
}
