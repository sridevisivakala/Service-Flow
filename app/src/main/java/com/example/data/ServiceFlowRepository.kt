package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ServiceFlowRepository(
    private val caseDao: CaseDao,
    private val agentDao: AgentDao,
    private val auditLogDao: AuditLogDao
) {
    val allCases: Flow<List<CaseEntity>> = caseDao.getAllCases()
    val activeCases: Flow<List<CaseEntity>> = caseDao.getActiveCases()
    val allAgents: Flow<List<AgentEntity>> = agentDao.getAllAgents()
    val allAuditLogs: Flow<List<AuditLogEntity>> = auditLogDao.getAllAuditLogs()

    suspend fun insertCase(case: CaseEntity): Long = withContext(Dispatchers.IO) {
        caseDao.insertCase(case)
    }

    suspend fun updateCase(case: CaseEntity) = withContext(Dispatchers.IO) {
        caseDao.updateCase(case)
    }

    suspend fun deleteCase(case: CaseEntity) = withContext(Dispatchers.IO) {
        // If the case had an assigned agent, release their workload
        if (case.agentId != null && case.status != "Resolved") {
            agentDao.getAgentById(case.agentId)?.let { agent ->
                val updatedAgent = agent.copy(
                    currentWorkload = (agent.currentWorkload - 1).coerceAtLeast(0)
                )
                agentDao.updateAgent(updatedAgent)
            }
        }
        caseDao.deleteCase(case)
    }

    suspend fun insertAgent(agent: AgentEntity): Long = withContext(Dispatchers.IO) {
        agentDao.insertAgent(agent)
    }

    suspend fun updateAgent(agent: AgentEntity) = withContext(Dispatchers.IO) {
        agentDao.updateAgent(agent)
    }

    suspend fun deleteAgent(agent: AgentEntity) = withContext(Dispatchers.IO) {
        agentDao.deleteAgent(agent)
    }

    suspend fun insertAuditLog(log: AuditLogEntity): Long = withContext(Dispatchers.IO) {
        auditLogDao.insertAuditLog(log)
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        caseDao.deleteAll()
        agentDao.deleteAll()
        auditLogDao.deleteAll()
    }

    /**
     * Automated Assignment Workflow Algorithm
     * Matches the case to the most suitable agent based on:
     * 1. Skills match (e.g. topic "Network" -> agent with "Network" skill)
     * 2. Availability (isAvailable == true)
     * 3. Workload balancing (assigns to the qualified agent with the lowest workload, below maxWorkload)
     */
    suspend fun autoAssignCase(caseId: Int): AgentEntity? = withContext(Dispatchers.IO) {
        val case = caseDao.getCaseById(caseId) ?: return@withContext null
        
        // Only assign cases that are New, or Escalated with no assigned agent
        if (case.status != "New" && case.status != "Escalated" && case.agentId != null) {
            return@withContext null
        }

        val allAgentsList = agentDao.getAgentsList()
        val availableAgents = allAgentsList.filter { it.isAvailable && it.currentWorkload < it.maxWorkload }

        if (availableAgents.isEmpty()) {
            // Log that assignment is pending due to lack of available agents
            val log = AuditLogEntity(
                caseId = case.id,
                caseTitle = case.title,
                action = "Assignment Failed",
                details = "No available agents found. All agents are offline or at maximum workload capacity (${case.urgency} SLA resolution pending)."
            )
            auditLogDao.insertAuditLog(log)
            return@withContext null
        }

        // 1. Try to find agents whose skills match the case topic
        val skillMatchedAgents = availableAgents.filter { agent ->
            agent.skills.split(",").any { skill ->
                skill.trim().equals(case.topic, ignoreCase = true)
            }
        }

        // 2. Fallback to any available agent if no exact skill match is available
        val candidates = if (skillMatchedAgents.isNotEmpty()) skillMatchedAgents else availableAgents
        val isSkillMatched = skillMatchedAgents.isNotEmpty()

        // 3. Select the candidate with the lowest current workload
        val assignedAgent = candidates.minByOrNull { it.currentWorkload }

        if (assignedAgent != null) {
            // Update agent workload
            val updatedAgent = assignedAgent.copy(currentWorkload = assignedAgent.currentWorkload + 1)
            agentDao.updateAgent(updatedAgent)

            // Update case status and assignment
            val updatedCase = case.copy(
                agentId = assignedAgent.id,
                agentName = assignedAgent.name,
                status = "Assigned"
            )
            caseDao.updateCase(updatedCase)

            // Log assignment success
            val matchCriteria = if (isSkillMatched) "matching skill '${case.topic}'" else "general availability (fallback)"
            val log = AuditLogEntity(
                caseId = case.id,
                caseTitle = case.title,
                action = "Agent Assigned",
                details = "Automated routing: Assigned to ${assignedAgent.name} based on $matchCriteria and lowest workload (${updatedAgent.currentWorkload}/${updatedAgent.maxWorkload} active)."
            )
            auditLogDao.insertAuditLog(log)
            return@withContext updatedAgent
        } else {
            return@withContext null
        }
    }
}
