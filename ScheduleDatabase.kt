package hathan.daljit.esp32_iot_studentversion_2

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(
    tableName = "schedules",
    indices = [Index(value = ["actuatorId", "time", "action", "deviceId"], unique = true)]
)
data class ScheduleEntity(
    @PrimaryKey val scheduleId: String,
    val actuatorId: String,
    val time: String,
    val action: String,
    val executed: Boolean,
    val deviceId: String
)

@Dao
interface ScheduleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSchedule(schedule: ScheduleEntity)

    @Query("SELECT * FROM schedules WHERE deviceId = :deviceId")
    suspend fun getSchedulesForDevice(deviceId: String): List<ScheduleEntity>

    @Query("DELETE FROM schedules WHERE scheduleId = :scheduleId")
    suspend fun deleteSchedule(scheduleId: String)

    @Query("UPDATE schedules SET executed = :executed WHERE scheduleId = :scheduleId")
    suspend fun updateExecuted(scheduleId: String, executed: Boolean)

    @Query("SELECT * FROM schedules")
    suspend fun getAllSchedules(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE actuatorId = :actuatorId AND time = :time AND :action = :action AND deviceId = :deviceId LIMIT 1")
    suspend fun getScheduleByDetails(actuatorId: String, time: String, action: String, deviceId: String): ScheduleEntity?

    @Query("SELECT * FROM schedules WHERE scheduleId = :scheduleId LIMIT 1")
    suspend fun getScheduleById(scheduleId: String): ScheduleEntity?


}

@Database(entities = [ScheduleEntity::class], version = 2, exportSchema = false)
abstract class ScheduleDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
}