package com.example.data

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NotificationAlert(
    val id: Long = System.currentTimeMillis() + (0..1000).random(),
    val type: String, // "BREACH", "WARNING", "ASSIGNMENT", "INGESTION"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

class ServiceFlowViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = ServiceFlowRepository(db.caseDao(), db.agentDao(), db.auditLogDao())

    val allCases: StateFlow<List<CaseEntity>> = repository.allCases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAgents: StateFlow<List<AgentEntity>> = repository.allAgents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAuditLogs: StateFlow<List<AuditLogEntity>> = repository.allAuditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live stateful notifications list
    private val _notifications = MutableStateFlow<List<NotificationAlert>>(emptyList())
    val notifications: StateFlow<List<NotificationAlert>> = _notifications.asStateFlow()

    private val _isAnalyzingCase = MutableStateFlow(false)
    val isAnalyzingCase: StateFlow<Boolean> = _isAnalyzingCase.asStateFlow()

    init {
        // Pre-populate data and start real-time background SLA monitor
        viewModelScope.launch {
            checkAndPrepopulateData()
            startSlaMonitor()
        }
    }

    private suspend fun checkAndPrepopulateData() = withContext(Dispatchers.IO) {
        // If agents are empty, pre-populate
        val agentsList = db.agentDao().getAgentsList()
        if (agentsList.isEmpty()) {
            val defaultAgents = listOf(
                AgentEntity(name = "Sophia Lee", avatar = "Sophia", skills = "Hardware,Network", certifications = "CompTIA A+, ITIL v4", maxWorkload = 4, currentWorkload = 0, tierRating = 4.8f),
                AgentEntity(name = "Marcus Chen", avatar = "Marcus", skills = "Software,Account", certifications = "AWS Certified, ITIL", maxWorkload = 5, currentWorkload = 0, tierRating = 4.9f),
                AgentEntity(name = "Alex Johnson", avatar = "Alex", skills = "Network,Software", certifications = "CCNA, ITIL Specialist", maxWorkload = 4, currentWorkload = 0, tierRating = 4.7f),
                AgentEntity(name = "Elena Rostova", avatar = "Elena", skills = "Account,Software", certifications = "CISSP, ITIL Practitioner", maxWorkload = 5, currentWorkload = 0, tierRating = 4.6f),
                AgentEntity(name = "Hiroshi Sato", avatar = "Hiroshi", skills = "Hardware,Software", certifications = "CompTIA Security+", maxWorkload = 3, currentWorkload = 0, tierRating = 4.5f)
            )
            for (agent in defaultAgents) {
                db.agentDao().insertAgent(agent)
            }

            // Ingest demo cases and run auto-assignment
            val demoCases = listOf(
                CaseEntity(
                    title = "Network drops repeatedly during Teams calls",
                    description = "Enterprise users reporting 10-15 second network dropouts across floor 4.",
                    channel = "Web",
                    urgency = "High",
                    topic = "Network",
                    customerTier = "Enterprise",
                    status = "New",
                    slaDeadline = System.currentTimeMillis() + 5 * 60 * 1000 // 5 minutes
                ),
                CaseEntity(
                    title = "Replace faulty keyboard on development laptop",
                    description = "Several letters on the keyboard (E, R, T) are completely unresponsive.",
                    channel = "Email",
                    urgency = "Medium",
                    topic = "Hardware",
                    customerTier = "Gold",
                    status = "New",
                    slaDeadline = System.currentTimeMillis() + 10 * 60 * 1000 // 10 minutes
                ),
                CaseEntity(
                    title = "Access locked: Invalid MFA credentials error",
                    description = "CEO locked out of Salesforce and Workday admin panels.",
                    channel = "Chat",
                    urgency = "Critical",
                    topic = "Account",
                    customerTier = "Enterprise",
                    status = "New",
                    slaDeadline = System.currentTimeMillis() + 2 * 60 * 1000 // 2 minutes
                )
            )

            for (case in demoCases) {
                val caseId = db.caseDao().insertCase(case).toInt()
                db.auditLogDao().insertAuditLog(AuditLogEntity(
                    caseId = caseId,
                    caseTitle = case.title,
                    action = "Case Ingested",
                    details = "Inflow through ${case.channel}. Initial priority: ${case.urgency}."
                ))
                repository.autoAssignCase(caseId)
            }
        }
    }

    /**
     * Periodically check active unresolved cases, updating countdowns and applying escalation rules
     */
    private suspend fun startSlaMonitor() {
        while (true) {
            delay(2000) // check every 2 seconds
            withContext(Dispatchers.IO) {
                val activeList = db.caseDao().getCasesList().filter { it.status != "Resolved" }
                val now = System.currentTimeMillis()

                for (case in activeList) {
                    val timeLeft = case.slaDeadline - now
                    var updatedCase = case
                    var changed = false

                    if (timeLeft <= 0 && case.slaStatus != "Breached") {
                        // SLA Breached! Auto-escalate to supervisor
                        updatedCase = case.copy(
                            slaStatus = "Breached",
                            status = "Escalated",
                            urgency = if (case.urgency == "Critical") "Critical" else "Critical" // Maximum level
                        )
                        changed = true

                        // Trigger alert notification
                        pushNotification(
                            type = "BREACH",
                            message = "SLA BREACHED: Case #${case.id} '${case.title}' has exceeded its response threshold! Automatically escalated."
                        )

                        // Log breach
                        db.auditLogDao().insertAuditLog(AuditLogEntity(
                            caseId = case.id,
                            caseTitle = case.title,
                            action = "SLA Escalation",
                            details = "Service Level Agreement SLA BREACHED. Urgency upgraded to Critical. Case moved to Supervisor escalation queue."
                        ))

                    } else if (timeLeft in 1..45000 && case.slaStatus == "Met") {
                        // SLA Warning (less than 45s left)
                        updatedCase = case.copy(slaStatus = "Warning")
                        changed = true

                        pushNotification(
                            type = "WARNING",
                            message = "SLA WARNING: Case #${case.id} has less than 45s remaining before breach."
                        )

                        db.auditLogDao().insertAuditLog(AuditLogEntity(
                            caseId = case.id,
                            caseTitle = case.title,
                            action = "SLA Warning",
                            details = "Warning state: Case SLA deadline approaching in < 45 seconds."
                        ))
                    }

                    if (changed) {
                        db.caseDao().updateCase(updatedCase)
                    }
                }
            }
        }
    }

    private fun pushNotification(type: String, message: String) {
        viewModelScope.launch {
            val newList = _notifications.value.toMutableList()
            newList.add(0, NotificationAlert(type = type, message = message))
            // Limit notification center history size
            if (newList.size > 20) {
                newList.removeAt(newList.lastIndex)
            }
            _notifications.value = newList
        }
    }

    /**
     * Ingests a customer support case, automatically using Gemini classification
     * and fallback local heuristics if key is not configured or fails.
     */
    fun ingestSupportCase(
        title: String,
        description: String,
        channel: String,
        manualTopic: String,
        manualUrgency: String,
        manualCustomerTier: String
    ) {
        viewModelScope.launch {
            _isAnalyzingCase.value = true
            
            var finalTitle = title.ifBlank { "Support Request" }
            var finalTopic = manualTopic
            var finalUrgency = manualUrgency
            var finalCustomerTier = manualCustomerTier

            // Try Gemini
            val geminiResult = withContext(Dispatchers.IO) {
                GeminiClient.categorizeSupportCase(finalTitle, description)
            }

            if (geminiResult != null) {
                finalTopic = geminiResult.topic
                finalUrgency = geminiResult.urgency
                finalCustomerTier = geminiResult.customerTier
                finalTitle = geminiResult.title
            } else {
                // Apply simple smart local heuristics on description text if manual selections were empty
                if (description.isNotBlank()) {
                    val descLower = description.lowercase()
                    if (finalTopic == "Software" || finalTopic == "Hardware") {
                        // Keep manual selection, but check text if none
                    } else {
                        finalTopic = when {
                            descLower.contains("password") || descLower.contains("login") || descLower.contains("mfa") || descLower.contains("account") -> "Account"
                            descLower.contains("wifi") || descLower.contains("network") || descLower.contains("router") || descLower.contains("vpn") || descLower.contains("connection") -> "Network"
                            descLower.contains("laptop") || descLower.contains("keyboard") || descLower.contains("printer") || descLower.contains("hardware") || descLower.contains("monitor") -> "Hardware"
                            else -> "Software"
                        }
                    }

                    if (finalUrgency == "Medium") { // default
                        finalUrgency = when {
                            descLower.contains("urgent") || descLower.contains("broken") || descLower.contains("cannot work") || descLower.contains("crash") -> "High"
                            descLower.contains("ceo") || descLower.contains("production down") || descLower.contains("immediately") || descLower.contains("sla") -> "Critical"
                            descLower.contains("question") || descLower.contains("how do i") || descLower.contains("clarification") -> "Low"
                            else -> "Medium"
                        }
                    }
                }
            }

            // Calculate SLA Duration based on urgency
            val slaDurationMs = when (finalUrgency) {
                "Critical" -> 2 * 60 * 1000L // 2 min
                "High" -> 5 * 60 * 1000L // 5 min
                "Medium" -> 10 * 60 * 1000L // 10 min
                else -> 15 * 60 * 1000L // 15 min
            }

            val newCase = CaseEntity(
                title = finalTitle,
                description = description,
                channel = channel,
                topic = finalTopic,
                urgency = finalUrgency,
                customerTier = finalCustomerTier,
                status = "New",
                slaDeadline = System.currentTimeMillis() + slaDurationMs
            )

            withContext(Dispatchers.IO) {
                val caseId = repository.insertCase(newCase).toInt()
                
                // Log Ingestion Event
                val categorizationMethod = if (geminiResult != null) "AI Model (gemini-3.5-flash)" else "Local Classification Engine"
                val auditLog = AuditLogEntity(
                    caseId = caseId,
                    caseTitle = finalTitle,
                    action = "Case Ingested",
                    details = "New support ticket registered via $channel. Classified by $categorizationMethod. Customer Tier: $finalCustomerTier. Topic: $finalTopic. Priority: $finalUrgency (SLA: ${slaDurationMs/60000} min)."
                )
                repository.insertAuditLog(auditLog)

                pushNotification(
                    type = "INGESTION",
                    message = "NEW INTAKE: Case #$caseId '$finalTitle' ($finalUrgency) ingested via $channel."
                )

                // Run automated matching routing
                repository.autoAssignCase(caseId)
            }

            _isAnalyzingCase.value = false
        }
    }

    /**
     * Resolves the case with resolution notes, releases agent workload
     */
    fun resolveCase(case: CaseEntity, resolutionNotes: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedCase = case.copy(
                status = "Resolved",
                notes = resolutionNotes,
                resolvedAt = System.currentTimeMillis(),
                slaStatus = if (case.slaStatus == "Breached") "Breached" else "Met"
            )
            repository.updateCase(updatedCase)

            // Decrement agent's current workload if they had been assigned
            if (case.agentId != null) {
                db.agentDao().getAgentById(case.agentId)?.let { agent ->
                    val updatedAgent = agent.copy(
                        currentWorkload = (agent.currentWorkload - 1).coerceAtLeast(0)
                    )
                    db.agentDao().updateAgent(updatedAgent)
                }
            }

            // Log Resolution
            val detailsText = "Resolved by ${case.agentName ?: "Tier 2 Support"}. Notes: \"$resolutionNotes\""
            db.auditLogDao().insertAuditLog(AuditLogEntity(
                caseId = case.id,
                caseTitle = case.title,
                action = "Case Resolved",
                details = detailsText
            ))

            pushNotification(
                type = "ASSIGNMENT",
                message = "CASE RESOLVED: Ticket #${case.id} was successfully completed. Workload released."
            )
        }
    }

    /**
     * Escalate case to Tier 2 / Supervisor
     */
    fun escalateCase(case: CaseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // Relieve current agent of workload
            if (case.agentId != null) {
                db.agentDao().getAgentById(case.agentId)?.let { agent ->
                    val updatedAgent = agent.copy(
                        currentWorkload = (agent.currentWorkload - 1).coerceAtLeast(0)
                    )
                    db.agentDao().updateAgent(updatedAgent)
                }
            }

            val updatedCase = case.copy(
                status = "Escalated",
                agentId = null,
                agentName = null,
                urgency = "Critical" // Raised to critical
            )
            repository.updateCase(updatedCase)

            // Log Escalation
            db.auditLogDao().insertAuditLog(AuditLogEntity(
                caseId = case.id,
                caseTitle = case.title,
                action = "Manual Escalation",
                details = "Case manually escalated by manager. Priority elevated to Critical. Released previous assignee. Re-routing queue pending..."
            ))

            pushNotification(
                type = "WARNING",
                message = "MANUAL ESCALATION: Case #${case.id} escalated to Supervisor level."
            )

            // Attempt re-routing to another available specialized agent
            repository.autoAssignCase(case.id)
        }
    }

    /**
     * Re-route an unassigned or Escalated case
     */
    fun triggerReRoute(caseId: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.autoAssignCase(caseId)
            }
        }
    }

    /**
     * Adds a new Agent to the available team roster
     */
    fun addAgent(name: String, avatar: String, skills: String, certifications: String, maxWorkload: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val newAgent = AgentEntity(
                name = name,
                avatar = avatar,
                skills = skills,
                certifications = certifications,
                maxWorkload = maxWorkload,
                currentWorkload = 0,
                tierRating = 4.5f,
                isAvailable = true
            )
            val agentId = repository.insertAgent(newAgent)
            
            db.auditLogDao().insertAuditLog(AuditLogEntity(
                caseId = 0,
                caseTitle = "Roster Update",
                action = "Agent Recruited",
                details = "New engineer $name onboarded to support roster. Specializations: $skills."
            ))

            pushNotification(
                type = "ASSIGNMENT",
                message = "AGENT ADDED: $name successfully added to the active support roster."
            )
        }
    }

    /**
     * Toggles agent availability (online/offline).
     * If going offline, their active cases are automatically re-routed to balance workload!
     */
    fun toggleAgentAvailability(agent: AgentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val isNowAvailable = !agent.isAvailable
            val updatedAgent = agent.copy(
                isAvailable = isNowAvailable,
                currentWorkload = if (!isNowAvailable) 0 else agent.currentWorkload // cases will be released if going offline
            )
            repository.updateAgent(updatedAgent)

            db.auditLogDao().insertAuditLog(AuditLogEntity(
                caseId = 0,
                caseTitle = agent.name,
                action = "Agent State Changed",
                details = "Agent ${agent.name} is now ${if (isNowAvailable) "Online & Available" else "Offline (unassigned)."}"
            ))

            if (!isNowAvailable) {
                // Relieve their active cases and re-route them!
                val activeAssignedCases = db.caseDao().getCasesList()
                    .filter { it.agentId == agent.id && it.status != "Resolved" }
                
                for (case in activeAssignedCases) {
                    val releasedCase = case.copy(
                        status = "New",
                        agentId = null,
                        agentName = null
                    )
                    db.caseDao().updateCase(releasedCase)

                    db.auditLogDao().insertAuditLog(AuditLogEntity(
                        caseId = case.id,
                        caseTitle = case.title,
                        action = "Case Re-routed",
                        details = "Agent ${agent.name} went offline. Ticket was automatically unassigned and put back in queue."
                    ))

                    // Re-route immediately
                    repository.autoAssignCase(case.id)
                }
            }

            pushNotification(
                type = "ASSIGNMENT",
                message = "AGENT STATE: ${agent.name} is now ${if (isNowAvailable) "Online" else "Offline"}"
            )
        }
    }

    /**
     * Clear all database records and re-initialize
     */
    fun resetDatabase() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.clearAllData()
            }
            // Populate defaults again
            checkAndPrepopulateData()
            pushNotification("ASSIGNMENT", "SYSTEM RESET: Database cleared and default electronic support center profiles loaded.")
        }
    }
}
