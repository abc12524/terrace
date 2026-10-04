package com.terrace.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.terrace.data.model.ChatSession
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: String): ChatSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ChatSession)

    @Query("UPDATE sessions SET updatedAt = :updatedAt, messageCount = :count WHERE id = :id")
    suspend fun updateStats(id: String, updatedAt: Long, count: Int)

    @Query("UPDATE sessions SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: String, title: String)

    @Query("UPDATE sessions SET serverStarted = :started WHERE id = :id")
    suspend fun setServerStarted(id: String, started: Boolean)

    @Query(
        "UPDATE sessions SET totalHit = totalHit + :hit, totalMiss = totalMiss + :miss, " +
            "totalOut = totalOut + :out, totalCost = totalCost + :cost, " +
            "model = :model, balance = :balance, symbol = :symbol WHERE id = :id"
    )
    suspend fun addUsage(
        id: String,
        hit: Int,
        miss: Int,
        out: Int,
        cost: Double,
        model: String,
        balance: String,
        symbol: String
    )

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: String)
}
