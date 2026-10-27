package com.therapist.assistant.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.therapist.assistant.ui.components.GradientTopBar
import com.therapist.assistant.ui.components.SoftCard
import com.therapist.assistant.ui.theme.AppColorTheme
import com.therapist.assistant.viewmodel.AppointmentViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: AppointmentViewModel,
    onBack: () -> Unit,
    onAbout: () -> Unit,
    onPrivacy: () -> Unit = {},
    onUpgrade: () -> Unit = {}
) {
    val remindersEnabled by viewModel.remindersEnabled.collectAsState()
    val dayBefore by viewModel.remindDayBefore.collectAsState()
    val hourBefore by viewModel.remindHourBefore.collectAsState()
    val morning by viewModel.remindMorning.collectAsState()
    val pinHash by viewModel.pinHash.collectAsState()
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val isPro by viewModel.isPro.collectAsState()
    val autoBackup by viewModel.autoBackupEnabled.collectAsState()
    val therapists by viewModel.therapists.collectAsState()
    val currentTherapistId by viewModel.currentTherapistId.collectAsState()
    val defaultFee by viewModel.defaultSessionFee.collectAsState()
    var feeEdit by remember(defaultFee) { mutableStateOf(if (defaultFee > 0) defaultFee.toLong().toString() else "") }
    var newTherapistName by remember { mutableStateOf("") }
    var newTherapistSecretary by remember { mutableStateOf(false) }
    val clinicName by viewModel.clinicName.collectAsState()
    val clinicPhone by viewModel.clinicPhone.collectAsState()
    val clinicAddress by viewModel.clinicAddress.collectAsState()
    val themeId by viewModel.themeId.collectAsState()
    val msgTemplate by viewModel.msgTemplate.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var nameEdit by remember(clinicName) { mutableStateOf(clinicName.ifBlank { "مرکز گل سرخ" }) }
    var phoneEdit by remember(clinicPhone) { mutableStateOf(clinicPhone) }
    var addressEdit by remember(clinicAddress) { mutableStateOf(clinicAddress) }
    var templateEdit by remember(msgTemplate) { mutableStateOf(msgTemplate) }
    var showPinDialog by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var showRestore by remember { mutableStateOf(false) }
    var restoreText by remember { mutableStateOf("") }
    var restoreMsg by remember { mutableStateOf<String?>(null) }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            GradientTopBar(title = "تنظیمات", subtitle = "درمانگرنو", onBack = onBack)

            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SoftCard {
                    Text("ظاهر برنامه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "۸ تم رنگی — انتخاب فعلی بلافاصله اعمال می‌شود",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppColorTheme.entries.forEach { theme ->
                            val selected = themeId == theme.id
                            Surface(
                                onClick = { viewModel.setThemeId(theme.id) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                border = if (selected) androidx.compose.foundation.BorderStroke(
                                    2.dp, MaterialTheme.colorScheme.primary
                                ) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    // حلقه رنگی تم
                                    Box(
                                        Modifier
                                            .size(28.dp)
                                            .background(
                                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                                    listOf(
                                                        theme.swatch,
                                                        theme.swatch.copy(alpha = 0.65f)
                                                    )
                                                ),
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            )
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        theme.titleFa,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (selected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                SoftCard {
                    Text("برندینگ مطب", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nameEdit, onValueChange = { nameEdit = it },
                        label = { Text("نام مرکز / مطب") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phoneEdit, onValueChange = { phoneEdit = it },
                        label = { Text("تلفن مطب") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addressEdit, onValueChange = { addressEdit = it },
                        label = { Text("آدرس مطب / مرکز") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.setClinicName(nameEdit)
                            viewModel.setClinicPhone(phoneEdit)
                            viewModel.setClinicAddress(addressEdit)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("ذخیره برندینگ") }
                }

                SoftCard {
                    Text("تیم درمانگران و منشی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "هر درمانگر فقط پرونده خودش را می‌بیند. منشی به همه دسترسی دارد. هماهنگی چند گوشی با بکاپ مشترک (درایو/ایمیل).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    therapists.forEach { th ->
                        FilterChip(
                            selected = currentTherapistId == th.id,
                            onClick = { viewModel.setCurrentTherapist(th.id) },
                            label = {
                                Text(
                                    th.name + if (th.role == com.therapist.assistant.data.UserRole.SECRETARY) " (منشی)" else ""
                                )
                            },
                            modifier = Modifier.padding(end = 6.dp, bottom = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newTherapistName,
                        onValueChange = { newTherapistName = it },
                        label = { Text("نام درمانگر / منشی جدید") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = newTherapistSecretary, onCheckedChange = { newTherapistSecretary = it })
                        Text("نقش منشی (دسترسی کامل)")
                    }
                    Button(
                        onClick = {
                            if (newTherapistName.isNotBlank()) {
                                viewModel.addTherapist(
                                    newTherapistName,
                                    if (newTherapistSecretary) com.therapist.assistant.data.UserRole.SECRETARY
                                    else com.therapist.assistant.data.UserRole.THERAPIST
                                )
                                newTherapistName = ""
                                newTherapistSecretary = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("افزودن عضو تیم") }
                    Spacer(Modifier.height(12.dp))
                    Text("تعرفه پیش‌فرض جلسه (تومان)", fontWeight = FontWeight.SemiBold)
                    Text("موقع رزرو نوبت خودکار پر می‌شود و قابل تغییر است.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = feeEdit,
                        onValueChange = { if (it.all { c -> c.isDigit() }) feeEdit = it },
                        label = { Text("مبلغ پیش‌فرض") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            viewModel.setDefaultSessionFee(feeEdit.toDoubleOrNull() ?: 0.0)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ذخیره تعرفه") }
                }


                SoftCard {
                    Text("قالب پیام یادآوری", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("از {name} و {date} و {time} استفاده کنید", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = templateEdit, onValueChange = { templateEdit = it },
                        modifier = Modifier.fillMaxWidth().height(100.dp), maxLines = 4, shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.setMsgTemplate(templateEdit) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text("ذخیره قالب پیام")
                    }
                }

                SoftCard {
                    Text("یادآوری‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("فعال‌سازی یادآوری")
                            Text("اعلان سیستم برای نوبت‌ها", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = remindersEnabled, onCheckedChange = { viewModel.setRemindersEnabled(it) })
                    }
                    if (remindersEnabled) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("یک روز قبل", Modifier.weight(1f))
                            Switch(checked = dayBefore, onCheckedChange = { viewModel.setRemindDayBefore(it) })
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("یک ساعت قبل", Modifier.weight(1f))
                            Switch(checked = hourBefore, onCheckedChange = { viewModel.setRemindHourBefore(it) })
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("صبح روز جلسه (۸ صبح)", Modifier.weight(1f))
                            Switch(checked = morning, onCheckedChange = { viewModel.setRemindMorning(it) })
                        }
                    }
                }

                SoftCard {
                    Text("امنیت", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (pinHash != null) "قفل پین فعال است" else "قفل پین غیرفعال")
                            Text("برای حفاظت از اطلاعات مراجعین", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (pinHash != null) {
                            TextButton(onClick = { viewModel.setPin(null) }) { Text("حذف پین") }
                        } else {
                            Button(onClick = { showPinDialog = true }, shape = RoundedCornerShape(12.dp)) { Text("تنظیم پین") }
                        }
                    }
                    if (pinHash != null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("ورود بیومتریک")
                                Text("اثرانگشت یا چهره پس از پین", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = biometricEnabled, onCheckedChange = { viewModel.setBiometricEnabled(it) })
                        }
                    }
                }

                SoftCard {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("پشتیبان خودکار زمان‌بندی‌شده", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Switch(checked = autoBackup, onCheckedChange = { viewModel.setAutoBackupEnabled(it) })
                    }
                    Text(
                        "در پس‌زمینه یادآوری بکاپ محلی؛ اشتراک ابری در پلن حرفه‌ای از منوی بکاپ.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SoftCard {
                    Text("پشتیبان‌گیری کامل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "شامل مراجعین، نوبت‌ها، یادداشت‌ها، پرسشنامه‌ها، کارگاه‌ها و تنظیمات",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val file = viewModel.saveBackupToDevice()
                                    restoreMsg = "ذخیره شد: ${file.name}"
                                } catch (e: Exception) {
                                    restoreMsg = "خطا در ذخیره: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("ذخیره روی گوشی") }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (!viewModel.hasProAccess()) {
                                restoreMsg = "ارسال بکاپ (ایمیل/درایو) در نسخه حرفه‌ای یا آزمایشی ۱۴روزه فعال است."
                                return@Button
                            }
                            scope.launch {
                                try {
                                    val file = viewModel.saveBackupToDevice()
                                    val share = com.therapist.assistant.util.BackupHelper.shareBackupIntent(context, file)
                                    context.startActivity(
                                        Intent.createChooser(share, "ارسال پشتیبان (جیمیل / گوگل درایو / …)")
                                    )
                                    restoreMsg = "فایل آماده اشتراک: ${file.name}"
                                } catch (e: Exception) {
                                    restoreMsg = "خطا: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("ارسال با جیمیل / گوگل درایو (حرفه‌ای)") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showRestore = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text("بازیابی از متن پشتیبان")
                    }
                    restoreMsg?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                }

                SoftCard {
                    TextButton(onClick = onUpgrade, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isPro) "اشتراک حرفه‌ای فعال است" else "ارتقا به نسخه حرفه‌ای")
                    }
                    TextButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth()) { Text("حریم خصوصی") }
                    TextButton(onClick = onAbout, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Info, null)
                        Spacer(Modifier.width(8.dp))
                        Text("درباره ما")
                    }
                    Text("نسخه ۵.۸ — درمانگرنو", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showRestore) {
        AlertDialog(
            onDismissRequest = { showRestore = false },
            title = { Text("بازیابی پشتیبان") },
            text = {
                OutlinedTextField(
                    value = restoreText,
                    onValueChange = { restoreText = it },
                    label = { Text("متن کامل JSON پشتیبان را بچسبانید") },
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val r = viewModel.importBackupJson(restoreText)
                        restoreMsg = r.getOrElse { "خطا: ${it.message}" }
                        showRestore = false
                    }
                }) { Text("بازیابی") }
            },
            dismissButton = { TextButton(onClick = { showRestore = false }) { Text("لغو") } }
        )
    }
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("تنظیم پین") },
            text = {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) newPin = it },
                    label = { Text("پین ۴ تا ۶ رقمی") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPin.length >= 4) {
                        viewModel.setPin(newPin)
                        newPin = ""
                        showPinDialog = false
                    }
                }) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("لغو") }
            }
        )
    }
}
