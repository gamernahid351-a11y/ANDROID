package com.primex.gateway.utils

import android.content.Context
import androidx.room.*
import com.primex.gateway.model.PaymentMethod
import com.primex.gateway.model.Transaction
import com.primex.gateway.model.TransactionStatus
import kotlinx.coroutines.flow.Flow

class PaymentMethodConverter {
    @TypeConverter fun fromMethod(value: PaymentMethod): String = value.name
    @TypeConverter fun toMethod(value: String): PaymentMethod = PaymentMethod.valueOf(value)
}

class StatusConverter {
    @TypeConverter fun fromStatus(value: TransactionStatus): String = value.name
    @TypeConverter fun toStatus(value: String): TransactionStatus = TransactionStatus.valueOf(value)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE username = :username ORDER BY createdAt DESC")
    fun getAll(username: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE username = :username AND (status = 'QUEUED' OR status = 'FAILED') ORDER BY createdAt ASC")
    suspend fun getPending(username: String): List<Transaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tx: Transaction)

    @Update
    suspend fun update(tx: Transaction)

    @Query("DELETE FROM transactions WHERE username = :username")
    suspend fun clearAll(username: String)

    @Query("SELECT COUNT(*) FROM transactions WHERE username = :username")
    suspend fun count(username: String): Int
}

@Database(entities = [Transaction::class], version = 1, exportSchema = false)
@TypeConverters(PaymentMethodConverter::class, StatusConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "primex_db")
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
