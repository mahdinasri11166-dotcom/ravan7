package com.therapist.assistant.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.therapist.assistant.util.ProtocolCatalog
import com.therapist.assistant.data.*
import com.therapist.assistant.notification.NotificationHelper
import com.therapist.assistant.util.BackupHelper
import java.io.File
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class AppointmentViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: AppointmentRepository
    private val settingsRepository: SettingsRepository
    private val workshopDao: com.therapist.assistant.data.WorkshopDao
    private val therapistDao: TherapistDao
    private val auditDao: AuditDao
    private val followUpDao: FollowUpDao
    private val homeworkDao: HomeworkDao
    private val appContext = application.applicationContext

    init {
        val db = AppDatabase.getDatabase(application)
        repository = AppointmentRepository(db.appointmentDao(), db.clientDao(), db.questionnaireDao())
        workshopDao = db.workshopDao()
        therapistDao = db.therapistDao()
        auditDao = db.auditDao()
        followUpDao = db.followUpDao()
        homeworkDao = db.homeworkDao()
        settingsRepository = SettingsRepository(application)
        // آزمایشی ۱۴روزه خودکار در اولین اجرا
        viewModelScope.launch {
            ensureAutoTrial()
            ensureDefaultTherapist()
            ensureDefaultClinic()
            ensureDeviceBind()
            checkVersionBump()
        }
    }

    val therapists = therapistDao.getActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val currentTherapistId = settingsRepository.currentTherapistId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val defaultSessionFee = settingsRepository.defaultSessionFee
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    val defaultTherapistName = settingsRepository.defaultTherapistName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val scopeId = settingsRepository.currentTherapistId
    private val therapistsFlow = therapistDao.getActive()

    private fun visibleToUser(therapistIdOnRecord: Long, currentId: Long, role: UserRole?): Boolean {
        if (role == UserRole.SECRETARY) return true
        if (currentId <= 0L) return true
        return therapistIdOnRecord == 0L || therapistIdOnRecord == currentId
    }

    val allAppointments = combine(
        repository.getAllAppointments(),
        settingsRepository.currentTherapistId,
        therapistsFlow
    ) { appts, tid, ths ->
        val role = ths.find { it.id == tid }?.role
        appts.filter { visibleToUser(it.therapistId, tid, role) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upcomingAppointments = allAppointments.map { list ->
        val now = System.currentTimeMillis()
        list.filter {
            it.dateTimeMillis >= now - 60_000 &&
                it.status != AppointmentStatus.CANCELLED &&
                it.status != AppointmentStatus.COMPLETED
        }.sortedBy { it.dateTimeMillis }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val clientCount = repository.getAllClients().map { it.count { c -> !c.isArchived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val clients = combine(
        repository.getAllClients(),
        settingsRepository.currentTherapistId,
        therapistsFlow
    ) { list, tid, ths ->
        val role = ths.find { it.id == tid }?.role
        list.filter { visibleToUser(it.therapistId, tid, role) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unpaidAppointments = allAppointments.map { list ->
        list.filter { !it.isPaid && (it.fee - it.discount) > 0 && it.status != AppointmentStatus.CANCELLED }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val remindersEnabled = settingsRepository.remindersEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val remindDayBefore = settingsRepository.remindDayBefore.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val remindMorning = settingsRepository.remindMorning.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val remindHourBefore = settingsRepository.remindHourBefore.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val onboardingDone = settingsRepository.onboardingDone.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val pinHash = settingsRepository.pinHash.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val clinicName = settingsRepository.clinicName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "مرکز گل سرخ")
    val clinicPhone = settingsRepository.clinicPhone.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val clinicAddress = settingsRepository.clinicAddress.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val isPro = settingsRepository.isPro.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val isOrg = settingsRepository.isOrg.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val tipsDone = settingsRepository.tipsDone.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val autoBackupEnabled = settingsRepository.autoBackupEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val pendingHomework = homeworkDao.pending().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pendingFollowUps = followUpDao.pending().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentAudit = auditDao.recent(50).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val trialStartMillis = settingsRepository.trialStartMillis.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val privacyAccepted = settingsRepository.privacyAccepted.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val biometricEnabled = settingsRepository.biometricEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val msgTemplate = settingsRepository.msgTemplate.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val themeHue = settingsRepository.themeHue.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 350f)
    val themeId = settingsRepository.themeId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "sky")

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val searchResults: StateFlow<List<Appointment>> =
        combine(_searchQuery, repository.getAllAppointments()) { q, list ->
            if (q.isBlank()) emptyList()
            else list.filter {
                it.clientName.contains(q, true) || it.notes.contains(q, true) ||
                        it.sessionNotes.contains(q, true) || it.noteTags.contains(q, true) ||
                        it.soapSubjective.contains(q, true) || it.soapObjective.contains(q, true) ||
                        it.soapAssessment.contains(q, true) || it.soapPlan.contains(q, true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun todayBounds(): Pair<Long, Long> {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        val start = c.timeInMillis
        c.add(Calendar.DAY_OF_YEAR, 1)
        return start to c.timeInMillis
    }

    fun getTodayAppointments() = todayBounds().let { (s, e) -> repository.getAppointmentsForDay(s, e) }

    fun setRemindersEnabled(v: Boolean) = viewModelScope.launch { settingsRepository.setRemindersEnabled(v) }
    fun setRemindDayBefore(v: Boolean) = viewModelScope.launch { settingsRepository.setRemindDayBefore(v) }
    fun setRemindMorning(v: Boolean) = viewModelScope.launch { settingsRepository.setRemindMorning(v) }
    fun setRemindHourBefore(v: Boolean) = viewModelScope.launch { settingsRepository.setRemindHourBefore(v) }
    fun setOnboardingDone(v: Boolean) = viewModelScope.launch { settingsRepository.setOnboardingDone(v) }
    fun setIsPro(v: Boolean) = viewModelScope.launch { settingsRepository.setIsPro(v) }

    fun currentPlan(): LicensePlan =
        LicensePlan.resolve(isPro.value, isOrg.value, isTrialActive())

    fun clientLimitForPlan(): Int = currentPlan().clientLimit

    private suspend fun checkVersionBump() {
        val current = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: ""
        } catch (_: Exception) { "" }
        val last = settingsRepository.lastAppVersion.first()
        if (last.isNotBlank() && last != current) {
            // نسخه عوض شده — پرچم برای UI بکاپ
            settingsRepository.setLastAppVersion(current)
        } else if (last.isBlank()) {
            settingsRepository.setLastAppVersion(current)
        }
    }

    val needsBackupAfterUpdate: StateFlow<Boolean> = settingsRepository.lastAppVersion
        .map { false } // نمایش از طریق پیام در Settings/Today با مقایسه دستی
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun currentTherapistName(): String {
        val id = currentTherapistId.value
        return therapists.value.find { it.id == id }?.name
            ?: defaultTherapistName.value.ifBlank { "درمانگر" }
    }

    fun markTipsTourDone() = viewModelScope.launch { settingsRepository.setTipsTourDone(true) }

    
    private suspend fun ensureAutoTrial() {
        val start = settingsRepository.trialStartMillis.first()
        if (start == 0L) {
            settingsRepository.setTrialStartMillis(System.currentTimeMillis())
        }
    }

    private suspend fun ensureDefaultTherapist() {
        val list = therapistDao.getActive().first()
        if (list.isEmpty()) {
            val id = therapistDao.insert(
                Therapist(name = "درمانگر اصلی", role = UserRole.THERAPIST, isActive = true)
            )
            settingsRepository.setCurrentTherapistId(id)
            settingsRepository.setDefaultTherapistName("درمانگر اصلی")
        } else {
            val cur = settingsRepository.currentTherapistId.first()
            if (cur <= 0L || list.none { it.id == cur }) {
                settingsRepository.setCurrentTherapistId(list.first().id)
                settingsRepository.setDefaultTherapistName(list.first().name)
            }
        }
    }

    private suspend fun ensureDefaultClinic() {
        val name = settingsRepository.clinicName.first()
        if (name.isBlank()) {
            settingsRepository.setClinicName("مرکز گل سرخ")
        }
    }

    fun setCurrentTherapist(id: Long) = viewModelScope.launch {
        settingsRepository.setCurrentTherapistId(id)
        val name = therapistDao.getActive().first().find { it.id == id }?.name
        if (!name.isNullOrBlank()) settingsRepository.setDefaultTherapistName(name)
    }

    fun setCurrentTherapistId(id: Long) = setCurrentTherapist(id)

    fun addTherapist(name: String, role: UserRole = UserRole.THERAPIST) = viewModelScope.launch {
        val n = name.trim().ifBlank { return@launch }
        val id = therapistDao.insert(Therapist(name = n, role = role, isActive = true))
        logAudit("therapist", id.toString(), "create", n)
    }

    fun setDefaultSessionFee(fee: Double) = viewModelScope.launch {
        settingsRepository.setDefaultSessionFee(fee.coerceAtLeast(0.0))
    }

    private suspend fun ensureDeviceBind() {
        val existing = settingsRepository.deviceBindId.first()
        if (existing.isBlank()) {
            val androidId = try {
                android.provider.Settings.Secure.getString(
                    appContext.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: "unknown"
            } catch (_: Exception) {
                "unknown"
            }
            settingsRepository.setDeviceBindId(androidId)
        }
    }

    fun logAudit(entityType: String, entityId: String, action: String, detail: String = "") =
        viewModelScope.launch {
            auditDao.insert(
                AuditLog(
                    entityType = entityType,
                    entityId = entityId,
                    action = action,
                    detail = detail,
                    actorName = currentTherapistName()
                )
            )
        }

    fun addFollowUp(clientId: Long, clientName: String, reason: String, dueMillis: Long) =
        viewModelScope.launch {
            followUpDao.insert(
                FollowUp(clientId = clientId, clientName = clientName, reason = reason, dueMillis = dueMillis)
            )
            logAudit("follow_up", clientId.toString(), "create", reason)
        }

    fun completeFollowUp(id: Long) = viewModelScope.launch { followUpDao.markDone(id) }

    fun setTipsDone(v: Boolean) = viewModelScope.launch { settingsRepository.setTipsDone(v) }
    fun setAutoBackupEnabled(v: Boolean) = viewModelScope.launch { settingsRepository.setAutoBackupEnabled(v) }

    /** کد سازمانی: پیشوند ORG- روی همان مجموعه کدهای Pro */
    fun unlockWithCode(code: String): Boolean {
        val raw = code.trim().uppercase().replace(" ", "")
        val isOrgCode = raw.startsWith("ORG")
        val normalized = raw.replace("-", "").replace("_", "").let {
            if (isOrgCode) it.removePrefix("ORG") else it
        }
        if (normalized !in PRO_UNLOCK_CODES) return false
        viewModelScope.launch {
            val used = settingsRepository.usedProCodes.first().split(',').filter { it.isNotBlank() }.toMutableSet()
            if (normalized in used) return@launch
            used.add(normalized)
            settingsRepository.setUsedProCodes(used.joinToString(","))
            settingsRepository.setIsPro(true)
            if (isOrgCode) settingsRepository.setIsOrg(true)
        }
        return true
    }

    fun startTrial() = viewModelScope.launch {
        if (settingsRepository.trialStartMillis.first() == 0L) {
            settingsRepository.setTrialStartMillis(System.currentTimeMillis())
        }
    }
    fun isTrialActive(): Boolean {
        val start = trialStartMillis.value
        if (start <= 0L) return false
        val window = TRIAL_DAYS * 24L * 60 * 60 * 1000
        return System.currentTimeMillis() - start < window
    }

    fun trialDaysRemaining(): Int {
        val start = trialStartMillis.value
        if (start <= 0L) return 0
        val window = TRIAL_DAYS * 24L * 60 * 60 * 1000
        val left = window - (System.currentTimeMillis() - start)
        if (left <= 0L) return 0
        return ((left + 24L * 60 * 60 * 1000 - 1) / (24L * 60 * 60 * 1000)).toInt().coerceAtLeast(1)
    }
    fun hasProAccess(): Boolean = isPro.value || isOrg.value || isTrialActive()
    fun canUseAssessment(id: String): Boolean = hasProAccess() || id in FREE_ASSESSMENT_IDS
    fun isFreeAssessment(id: String): Boolean = id in FREE_ASSESSMENT_IDS
    fun setPrivacyAccepted(v: Boolean) = viewModelScope.launch { settingsRepository.setPrivacyAccepted(v) }
    fun setBiometricEnabled(v: Boolean) = viewModelScope.launch { settingsRepository.setBiometricEnabled(v) }
    fun setClinicName(v: String) = viewModelScope.launch { settingsRepository.setClinicName(v.trim().ifBlank { "مرکز گل سرخ" }) }
    fun setClinicPhone(v: String) = viewModelScope.launch { settingsRepository.setClinicPhone(v) }
    fun setClinicAddress(v: String) = viewModelScope.launch { settingsRepository.setClinicAddress(v) }
    fun setMsgTemplate(v: String) = viewModelScope.launch { settingsRepository.setMsgTemplate(v) }
    fun setThemeHue(v: Float) = viewModelScope.launch { settingsRepository.setThemeHue(v) }
    fun setThemeId(v: String) = viewModelScope.launch { settingsRepository.setThemeId(v) }

    fun setPin(pin: String?) = viewModelScope.launch {
        settingsRepository.setPinHash(pin?.let { hashPin(it) })
    }

    fun checkPin(pin: String, hash: String?): Boolean =
        hash != null && hashPin(pin) == hash

    private fun hashPin(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    suspend fun getAppointment(id: Long) = repository.getAppointmentById(id)
    suspend fun getClient(id: Long) = repository.getClientById(id)
    fun getAppointmentsForClient(clientId: Long) = repository.getAppointmentsForClient(clientId)
    fun getAppointmentsBetween(s: Long, e: Long) = repository.getAppointmentsBetween(s, e)
    fun getQuestionnaires(clientId: Long) = repository.getQuestionnaires(clientId)

    companion object {
        /** سقف مراجع در نسخه رایگان */
        const val FREE_CLIENT_LIMIT = 50
        /** مدت نسخه آزمایشی حرفه‌ای (روز) */
        const val TRIAL_DAYS = 14
        /**
         * آزمون‌های غربالگری آزاد در نسخه رایگان.
         * بقیه آزمون‌ها فقط با Pro یا دوره آزمایشی.
         */
        val FREE_ASSESSMENT_IDS: Set<String> = setOf(
            "PHQ9", "GAD7", "K6", "PSS10", "WHO5",
            "AUDIT_C", "CAGE", "SCOFF", "ISI", "GDS15"
        )

        /** کدهای فعال‌سازی Pro — فقط در سورس؛ در UI نمایش داده نمی‌شوند. */
        private val PRO_UNLOCK_CODES: Set<String> = setOf(
            "XK7M2Q9PL4RN8WVH",
            "B3T9F6HJ2KD8YQWZ",
            "N5R8C1VX4M7P2LQJ",
            "W6Y2D9KS3HF7T1BN",
            "P4J8M3QG6ZL9R2XC",
            "H7K1V5TN9BW4D6SY",
            "R2X9L4FQ8MC7P3JH",
            "T6N3Y8KD1VS5W9BG",
            "C9F4H2MJ7QX6L1PR",
            "Z5W8B3TN6YK2D9HS",
            "Q1L7P9RX4VF3M8JC",
            "G8D2S6HY5NK9T4BW",
            "M3J9V1FC7PL5R8XQ",
            "Y4K6N2TW9BH7D3SG",
            "F7P1X5QL8JM4C9RV",
            "D9S3H6YB2WK8N5TG",
            "L2C8R4VX7MQ1P6JF",
            "K5T9W3ND8YS2H7BG",
            "V6M1J4FQ9PL3X8RC",
            "S8H2Y7BK5TN9D4WG",
            "J3P9L6XC2RF8M1QV",
            "N7W4D1HS9YK6T3BG",
            "X2Q8C5MJ4PL9R7VF",
            "B9T6K3YN8HS1D5WG",
            "P1F7R4VX6MQ9L2JC",
            "H5Y9S2BW7NK4T8DG",
            "R8L3M6JQ1XC5P9VF",
            "T4N7K1YD9HS6W2BG",
            "C6F2H8PX3QL9M5RJ",
            "Z9W5B1TN7YK4D8HS"
        )
    }

    fun saveClient(
        id: Long?,
        name: String,
        phone: String,
        nationalId: String = "",
        birthDateMillis: Long = 0L,
        address: String = "",
        education: String = "",
        notes: String,
        onSaved: (Long) -> Unit = {},
        onLimitReached: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val pro = settingsRepository.isPro.first()
            val trialStart = settingsRepository.trialStartMillis.first()
            val trialOk = trialStart > 0L && System.currentTimeMillis() - trialStart < TRIAL_DAYS * 24L * 60 * 60 * 1000
            if ((id == null || id == 0L) && !pro && !trialOk && repository.getClientCount() >= clientLimitForPlan()) {
                onLimitReached()
                return@launch
            }
            val tid = settingsRepository.currentTherapistId.first()
            val existing = if (id != null && id > 0) repository.getClientById(id) else null
            val c = Client(
                id = id ?: 0,
                name = name.trim(),
                phone = phone.trim(),
                nationalId = nationalId.trim(),
                birthDateMillis = birthDateMillis,
                address = address.trim(),
                education = education.trim(),
                notes = notes.trim(),
                therapistId = existing?.therapistId?.takeIf { it > 0 } ?: tid
            )
            val newId = if (id == null || id == 0L) repository.insertClient(c) else { repository.updateClient(c); id }
            logAudit("client", newId.toString(), if (id == null || id == 0L) "create" else "update", c.name)
            onSaved(newId)
        }
    }

    fun deleteClient(client: Client) = viewModelScope.launch { repository.deleteClient(client) }

    fun saveAppointment(
        id: Long?,
        clientId: Long,
        clientName: String,
        dateTimeMillis: Long,
        notes: String,
        sessionNotes: String,
        soapS: String = "", soapO: String = "", soapA: String = "", soapP: String = "",
        noteTags: String = "",
        fee: Double,
        discount: Double = 0.0,
        isPaid: Boolean,
        paymentMethod: PaymentMethod,
        status: AppointmentStatus,
        cancelReason: String = "",
        recurrence: RecurrenceType,
        recurrenceEndMillis: Long = 0L,
        generateInvoice: Boolean = false,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            var serial = ""
            if (generateInvoice || (id != null && id > 0)) {
                val existing = id?.let { repository.getAppointmentById(it) }
                serial = existing?.invoiceSerial?.takeIf { it.isNotBlank() }
                    ?: "GS-${SimpleDateFormat("yyyyMM", Locale.US).format(Date())}-${(repository.getInvoiceCount() + 1).toString().padStart(4, '0')}"
            }
            val tid = settingsRepository.currentTherapistId.first()
            val tName = therapistDao.getById(tid)?.name
                ?: settingsRepository.defaultTherapistName.first().ifBlank { "درمانگر" }
            val existingAppt = if (id != null && id > 0) repository.getAppointmentById(id) else null
            val appointment = Appointment(
                id = id ?: 0,
                clientId = clientId,
                clientName = clientName.trim(),
                dateTimeMillis = dateTimeMillis,
                notes = notes.trim(),
                sessionNotes = sessionNotes.trim(),
                soapSubjective = soapS, soapObjective = soapO, soapAssessment = soapA, soapPlan = soapP,
                noteTags = noteTags.trim(),
                fee = fee, discount = discount.coerceAtLeast(0.0), isPaid = isPaid, paymentMethod = paymentMethod,
                status = status, cancelReason = cancelReason,
                recurrence = recurrence, recurrenceEndMillis = recurrenceEndMillis,
                invoiceSerial = serial,
                therapistId = existingAppt?.therapistId?.takeIf { it > 0 } ?: tid,
                therapistName = existingAppt?.therapistName?.takeIf { it.isNotBlank() } ?: tName
            )
            val savedId = if (id == null || id == 0L) {
                val newId = repository.insert(appointment)
                if (recurrence != RecurrenceType.NONE) generateRecurring(appointment.copy(id = newId), recurrence, recurrenceEndMillis)
                newId
            } else {
                NotificationHelper.cancelReminders(appContext, id)
                repository.update(appointment)
                id
            }
            val day = settingsRepository.remindDayBefore.first()
            val hour = settingsRepository.remindHourBefore.first()
            val enabled = settingsRepository.remindersEnabled.first()
            if (enabled && status == AppointmentStatus.SCHEDULED) {
                NotificationHelper.scheduleReminders(appContext, appointment.copy(id = savedId), day, hour)
            }
            onSaved()
        }
    }

    private suspend fun generateRecurring(base: Appointment, type: RecurrenceType, endMillis: Long) {
        val cal = Calendar.getInstance().apply { timeInMillis = base.dateTimeMillis }
        val step = if (type == RecurrenceType.WEEKLY) 7 else 14
        val list = mutableListOf<Appointment>()
        val limit = if (endMillis > 0) endMillis else run {
            cal.add(Calendar.MONTH, 3); cal.timeInMillis
        }
        cal.timeInMillis = base.dateTimeMillis
        repeat(20) {
            cal.add(Calendar.DAY_OF_YEAR, step)
            if (cal.timeInMillis > limit) return@repeat
            list.add(base.copy(id = 0, dateTimeMillis = cal.timeInMillis, parentRecurringId = base.id, recurrence = RecurrenceType.NONE, invoiceSerial = ""))
        }
        if (list.isNotEmpty()) repository.insertAll(list)
    }

    fun deleteAppointment(a: Appointment) = viewModelScope.launch {
        NotificationHelper.cancelReminders(appContext, a.id)
        repository.delete(a)
    }

    fun updateStatus(a: Appointment, status: AppointmentStatus, reason: String = "") {
        viewModelScope.launch {
            repository.update(a.copy(status = status, cancelReason = reason))
            if (status != AppointmentStatus.SCHEDULED) NotificationHelper.cancelReminders(appContext, a.id)
        }
    }

    fun togglePaid(a: Appointment) = viewModelScope.launch {
        repository.update(a.copy(isPaid = !a.isPaid))
    }

    fun saveQuestionnaire(clientId: Long, type: String, score: Int, details: String) {
        viewModelScope.launch {
            val qTid = settingsRepository.currentTherapistId.first()
            repository.insertQuestionnaire(QuestionnaireResult(clientId = clientId, type = type, score = score, detailsJson = details, therapistId = qTid))
        }
    }

    suspend fun getMonthlyIncome(year: Int, month: Int): Double {
        val cal = Calendar.getInstance().apply { set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return repository.getPaidIncomeBetween(start, cal.timeInMillis)
    }

    suspend fun getYearlyIncome(year: Int): Double {
        val cal = Calendar.getInstance().apply { set(year, 0, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
        val start = cal.timeInMillis
        cal.add(Calendar.YEAR, 1)
        return repository.getPaidIncomeBetween(start, cal.timeInMillis)
    }

    suspend fun getTotalUnpaid() = repository.getTotalUnpaid()

    fun seedSampleData() = viewModelScope.launch {
        if (repository.getClientCount() > 0) return@launch
        val c1 = repository.insertClient(Client(name = "سارا محمدی", phone = "09121234567", notes = "مراجع نمونه"))
        val c2 = repository.insertClient(Client(name = "علی رضایی", phone = "09351234567", notes = ""))
        val cal = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        repository.insert(Appointment(
            clientId = c1, clientName = "سارا محمدی",
            dateTimeMillis = cal.timeInMillis, fee = 500000.0,
            notes = "جلسه اول", status = AppointmentStatus.SCHEDULED
        ))
        cal.add(Calendar.DAY_OF_YEAR, 1)
        repository.insert(Appointment(
            clientId = c2, clientName = "علی رضایی",
            dateTimeMillis = cal.timeInMillis, fee = 450000.0, isPaid = true,
            paymentMethod = PaymentMethod.CARD, status = AppointmentStatus.SCHEDULED
        ))
    }

    suspend fun exportBackupJson(): String {
        val clients = repository.getAllClientsOnce()
        val appts = repository.getAllAppointmentsOnce()
        val questionnaires = repository.getAllQuestionnairesOnce()
        val workshopsList = workshopDao.getAllOnce()
        val participants = workshopDao.getAllParticipantsOnce()
        val settings = settingsRepository.getAllAsMap()
        return BackupHelper.buildJson(
            clients, appts, questionnaires, workshopsList, participants, settings
        )
    }

    /** ذخیره فایل پشتیبان روی گوشی و برگرداندن مسیر فایل */
    suspend fun saveBackupToDevice(): File {
        val json = exportBackupJson()
        return BackupHelper.saveToAppStorage(appContext, json)
    }

    suspend fun importBackupJson(json: String): Result<String> {
        return try {
            val parsed = BackupHelper.parse(json)
            // Map old client ids -> new ids
            val idMap = mutableMapOf<Long, Long>()
            // We don't have original ids in Client after parse (set to 0).
            // Rebuild by index order: backup clients keep sequential correspondence via appointments' clientId.
            // So re-parse clients with original ids from JSON for mapping.
            val rootClients = org.json.JSONObject(json).optJSONArray("clients") ?: org.json.JSONArray()
            for (i in 0 until rootClients.length()) {
                val o = rootClients.getJSONObject(i)
                val oldId = o.optLong("id", 0L)
                val client = parsed.clients.getOrNull(i) ?: continue
                val newId = repository.insertClient(client)
                if (oldId > 0) idMap[oldId] = newId
            }

            var apptCount = 0
            for (a in parsed.appointments) {
                val newClientId = idMap[a.clientId] ?: a.clientId
                val name = repository.getClientById(newClientId)?.name ?: a.clientName
                repository.insert(
                    a.copy(id = 0, clientId = newClientId, clientName = name)
                )
                apptCount++
            }

            var qCount = 0
            for (q in parsed.questionnaires) {
                val newClientId = idMap[q.clientId] ?: q.clientId
                repository.insertQuestionnaire(q.copy(id = 0, clientId = newClientId))
                qCount++
            }

            val workshopIdMap = mutableMapOf<Long, Long>()
            for (w in parsed.workshops) {
                val oldWid = w.id
                val newWid = workshopDao.insert(w.copy(id = 0))
                if (oldWid > 0) workshopIdMap[oldWid] = newWid
            }

            var pCount = 0
            for (part in parsed.participants) {
                val newWid = workshopIdMap[part.workshopId] ?: part.workshopId
                val newCid = part.clientId?.let { idMap[it] ?: it }
                workshopDao.insertParticipant(part.copy(id = 0, workshopId = newWid, clientId = newCid))
                pCount++
            }

            if (parsed.settings.isNotEmpty()) {
                settingsRepository.applyFromMap(parsed.settings)
            }

            Result.success(
                "بازیابی شد: ${idMap.size} مراجع، $apptCount نوبت، $qCount پرسشنامه، ${workshopIdMap.size} کارگاه، $pCount شرکت‌کننده"
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    val workshops = workshopDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getWorkshop(id: Long) = workshopDao.getById(id)
    suspend fun getWorkshopParticipants(id: Long) = workshopDao.getParticipants(id)

    fun saveWorkshop(
        id: Long?,
        title: String,
        province: String,
        city: String,
        centerName: String,
        location: String,
        durationHours: Int,
        topics: String,
        trainerName: String,
        dateMillis: Long,
        participants: List<com.therapist.assistant.data.WorkshopParticipant>,
        onSaved: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val w = com.therapist.assistant.data.Workshop(
                id = id ?: 0,
                title = title.trim(),
                province = province.trim(),
                city = city.trim(),
                centerName = centerName.trim(),
                location = location.trim(),
                durationHours = durationHours,
                topics = topics.trim(),
                trainerName = trainerName.trim(),
                dateMillis = dateMillis
            )
            val wid = if (id == null || id == 0L) {
                workshopDao.insert(w)
            } else {
                workshopDao.update(w)
                id
            }
            workshopDao.clearParticipants(wid)
            workshopDao.insertParticipants(participants.map { it.copy(id = 0, workshopId = wid) })
            onSaved(wid)
        }
    }

    
    suspend fun getClientWorkshopLinks(clientId: Long): List<Pair<com.therapist.assistant.data.Workshop, com.therapist.assistant.data.WorkshopParticipant>> {
        val parts = workshopDao.getParticipantsByClient(clientId)
        return parts.mapNotNull { p ->
            val w = workshopDao.getById(p.workshopId) ?: return@mapNotNull null
            w to p
        }
    }

    fun deleteWorkshop(w: com.therapist.assistant.data.Workshop) {
        viewModelScope.launch { workshopDao.delete(w) }
    }

    
    fun quickCreateClient(name: String, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            if (!hasProAccess() && repository.getClientCount() >= clientLimitForPlan()) {
                return@launch
            }
            val id = repository.insertClient(
                Client(name = name.trim(), notes = "پرونده سریع — تکمیل نشده")
            )
            onCreated(id)
        }
    }

    fun setClientArchived(client: Client, archived: Boolean) {
        viewModelScope.launch {
            repository.updateClient(client.copy(isArchived = archived))
        }
    }

    fun quickCompleteSession(appointment: Appointment, fee: Double, isPaid: Boolean, note: String) {
        viewModelScope.launch {
            val updated = appointment.copy(
                status = AppointmentStatus.COMPLETED,
                fee = fee,
                isPaid = isPaid,
                sessionNotes = if (note.isBlank()) appointment.sessionNotes
                else (appointment.sessionNotes + "\n" + note).trim()
            )
            repository.update(updated)
            NotificationHelper.cancelReminders(appContext, appointment.id)
        }
    }

    fun completeSessionWithProtocol(
        appointmentId: Long,
        protocolTitle: String,
        checkedSteps: Set<Int>,
        homeworkTitle: String = ""
    ) = viewModelScope.launch {
        val a = repository.getAppointmentById(appointmentId) ?: return@launch
        val p = ProtocolCatalog.find(protocolTitle)
        val checklist = if (p != null) ProtocolCatalog.formatChecklistForNotes(p, checkedSteps) else protocolTitle
        val planBit = "P — جمع‌بندی پروتکل $protocolTitle\nگام‌های انجام‌شده: ${checkedSteps.sorted().map { it + 1 }.joinToString()}"
        val notes = (a.sessionNotes.trim() + "\n\n" + checklist).trim()
        val soapP = (a.soapPlan.trim() + "\n" + planBit).trim()
        repository.update(
            a.copy(
                status = AppointmentStatus.COMPLETED,
                sessionNotes = notes,
                soapPlan = soapP
            )
        )
        if (homeworkTitle.isNotBlank()) {
            homeworkDao.insert(
                HomeworkTask(
                    clientId = a.clientId,
                    clientName = a.clientName,
                    appointmentId = a.id,
                    title = homeworkTitle,
                    dueMillis = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
                )
            )
        }
        logAudit("appointment", appointmentId.toString(), "complete_protocol", protocolTitle)
        NotificationHelper.cancelReminders(appContext, a.id)
    }

    fun addHomework(clientId: Long, clientName: String, title: String, appointmentId: Long = 0) =
        viewModelScope.launch {
            homeworkDao.insert(
                HomeworkTask(clientId = clientId, clientName = clientName, appointmentId = appointmentId, title = title,
                    dueMillis = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000)
            )
        }

    fun completeHomework(id: Long) = viewModelScope.launch { homeworkDao.markDone(id) }

    fun homeworkForClient(clientId: Long) = homeworkDao.forClient(clientId)

    fun appendSessionNotes(appointmentId: Long, text: String) = viewModelScope.launch {
        val a = repository.getAppointmentById(appointmentId) ?: return@launch
        val merged = (a.sessionNotes.trim() + "\n\n" + text.trim()).trim()
        repository.update(a.copy(sessionNotes = merged))
        logAudit("appointment", appointmentId.toString(), "protocol_notes", text.take(80))
    }

    fun unlockProWithCode(code: String): Boolean {
        val normalized = code.trim()
            .uppercase()
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
        if (normalized !in PRO_UNLOCK_CODES) return false
        // یک‌بارمصرف روی همین دستگاه
        viewModelScope.launch {
            val used = settingsRepository.usedProCodes.first().split(',').filter { it.isNotBlank() }.toMutableSet()
            if (normalized in used) return@launch
            used.add(normalized)
            settingsRepository.setUsedProCodes(used.joinToString(","))
            settingsRepository.setIsPro(true)
        }
        return true
    }

    /** اگر کد قبلاً روی این دستگاه مصرف شده باشد false */
    suspend fun isCodeAlreadyUsed(code: String): Boolean {
        val normalized = code.trim().uppercase().replace(" ", "").replace("-", "").replace("_", "")
        val used = settingsRepository.usedProCodes.first().split(',').filter { it.isNotBlank() }
        return normalized in used
    }

}
