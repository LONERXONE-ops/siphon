package com.lonerxone.siphon.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lonerxone.siphon.data.db.entity.SavedNetwork
import com.lonerxone.siphon.data.db.entity.WaitlistRule
import kotlinx.coroutines.flow.Flow

@Dao
interface WaitlistRuleDao {

    @Query("SELECT * FROM waitlist_rules")
    fun getAllRules(): Flow<List<WaitlistRule>>

    @Query("SELECT * FROM waitlist_rules WHERE id = :id")
    suspend fun getRuleById(id: String): WaitlistRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: WaitlistRule)

    @Update
    suspend fun updateRule(rule: WaitlistRule)

    @Query("DELETE FROM waitlist_rules WHERE id = :id")
    suspend fun deleteRule(id: String)

    // Saved Networks
    @Query("SELECT * FROM saved_networks")
    fun getSavedNetworks(): Flow<List<SavedNetwork>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNetwork(network: SavedNetwork)

    @Query("DELETE FROM saved_networks WHERE id = :id")
    suspend fun deleteNetwork(id: String)
}
