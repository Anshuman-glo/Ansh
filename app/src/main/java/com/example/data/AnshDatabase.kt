package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- Room Entities ---

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val category: String = "General",
    val isLocked: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val priority: String = "Medium", // High, Medium, Low
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "automation_rules")
data class AutomationRule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val triggerEvent: String, // e.g., "Battery level < 25%", "Time is 22:00", "Headset Connected"
    val actionTask: String,   // e.g., "Enable Power Saving", "Mute audio", "Read Agenda"
    val isActive: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_history")
data class ChatHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val query: String,
    val response: String,
    val isThinking: Boolean = false,
    val thinkingLog: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

// --- DAOs ---

@Dao
interface AnshDao {
    // Notes
    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note)

    @Update
    suspend fun updateNote(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Int)

    // Tasks
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, timestamp DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @Update
    suspend fun updateTask(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)

    // Automation Rules
    @Query("SELECT * FROM automation_rules ORDER BY timestamp DESC")
    fun getAllRules(): Flow<List<AutomationRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AutomationRule)

    @Update
    suspend fun updateRule(rule: AutomationRule)

    @Query("DELETE FROM automation_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Int)

    // Chat History
    @Query("SELECT * FROM chat_history ORDER BY timestamp ASC")
    fun getChatHistory(): Flow<List<ChatHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatHistory)

    @Query("DELETE FROM chat_history")
    suspend fun clearChatHistory()
}

// --- App Database ---

@Database(
    entities = [Note::class, Task::class, AutomationRule::class, ChatHistory::class],
    version = 2,
    exportSchema = false
)
abstract class AnshDatabase : RoomDatabase() {
    abstract fun dao(): AnshDao
}
