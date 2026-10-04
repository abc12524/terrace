package com.terrace.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.terrace.data.model.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    /** 按自增 id 排序，保证同一批写入的消息顺序稳定 */
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY id ASC")
    fun getMessagesBySession(sessionId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getMessagesBySessionSync(sessionId: String): List<Message>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Message): Long

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}
