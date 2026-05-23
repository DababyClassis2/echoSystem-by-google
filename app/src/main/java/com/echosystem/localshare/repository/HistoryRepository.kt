package com.echosystem.localshare.repository

import com.echosystem.localshare.database.TransferHistoryDao
import com.echosystem.localshare.database.TransferHistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val transferHistoryDao: TransferHistoryDao
) {
    val allHistory: Flow<List<TransferHistoryEntity>> = transferHistoryDao.getAllHistory()

    suspend fun insertHistory(history: TransferHistoryEntity) = transferHistoryDao.insertHistory(history)

    suspend fun deleteHistory(history: TransferHistoryEntity) = transferHistoryDao.deleteHistory(history)
}
