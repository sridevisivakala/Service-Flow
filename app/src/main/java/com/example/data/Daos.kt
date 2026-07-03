package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CaseDao {
    @Query("SELECT * FROM cases ORDER BY createdAt DESC")
    fun getAllCases(): Flow<List<CaseEntity>>

    @Query("SELECT * FROM cases WHERE id = :id")
    suspend fun getCaseById(id: Int): CaseEntity?

    @Query("SELECT * FROM cases")
    suspend fun getCasesList(): List<CaseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCase(case: CaseEntity): Long

    @Update
    suspend fun updateCase(case: CaseEntity)

    @Delete
    suspend fun deleteCase(case: CaseEntity)

    @Query("SELECT * FROM cases WHERE status != 'Resolved'")
    fun getActiveCases(): Flow<List<CaseEntity>>

    @Query("DELETE FROM cases")
    suspend fun deleteAll()
}

@Dao
interface AgentDao {
    @Query("SELECT * FROM agents ORDER BY name ASC")
    fun getAllAgents(): Flow<List<AgentEntity>>

    @Query("SELECT * FROM agents WHERE id = :id")
    suspend fun getAgentById(id: Int): AgentEntity?

    @Query("SELECT * FROM agents")
    suspend fun getAgentsList(): List<AgentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgent(agent: AgentEntity): Long

    @Update
    suspend fun updateAgent(agent: AgentEntity)

    @Delete
    suspend fun deleteAgent(agent: AgentEntity)

    @Query("DELETE FROM agents")
    suspend fun deleteAll()
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogs(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity): Long

    @Query("DELETE FROM audit_logs")
    suspend fun deleteAll()
}
