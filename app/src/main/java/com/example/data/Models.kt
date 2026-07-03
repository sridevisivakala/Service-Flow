package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cases")
data class CaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val channel: String, // "Web", "Email", "Chat", "Phone"
    val urgency: String, // "Low", "Medium", "High", "Critical"
    val topic: String, // "Hardware", "Software", "Network", "Account"
    val customerTier: String, // "Bronze", "Silver", "Gold", "Enterprise"
    val status: String, // "New", "Assigned", "In Progress", "Resolved", "Escalated"
    val agentId: Int? = null,
    val agentName: String? = null,
    val slaDeadline: Long, // Epoch timestamp in ms
    val slaStatus: String = "Met", // "Met", "Warning", "Breached"
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
    val notes: String? = null
)

@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val avatar: String, // Identifier for icon/color
    val skills: String, // Comma-separated, e.g., "Hardware,Network"
    val certifications: String, // E.g., "ITIL, CCNA, CompTIA"
    val maxWorkload: Int = 5,
    val currentWorkload: Int = 0,
    val tierRating: Float = 4.5f,
    val isAvailable: Boolean = true
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val caseId: Int,
    val caseTitle: String,
    val action: String, // "Case Ingested", "Agent Assigned", "Case Escalated", "SLA Warning", "Case Resolved"
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)
