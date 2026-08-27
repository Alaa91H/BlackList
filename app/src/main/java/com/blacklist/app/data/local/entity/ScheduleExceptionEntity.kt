package com.blacklist.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Exact-number exception that is active only while its parent schedule rule
 * matches. This deliberately permits a caller through a schedule; explicit
 * blacklist rules remain higher in the firewall precedence order.
 */
@Entity(
    tableName = "schedule_exceptions",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleRuleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("scheduleRuleId"),
        Index(value = ["scheduleRuleId", "normalizedNumber"], unique = true)
    ]
)
data class ScheduleExceptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleRuleId: Long,
    /** Digits-only local representation; exact matching is enforced by the engine. */
    val normalizedNumber: String,
    val createdAt: Long = System.currentTimeMillis()
)
