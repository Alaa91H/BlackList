package com.blacklist.app.domain.capability

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central capability management - evaluates and tracks system capabilities.
 * All components should use this instead of checking capabilities directly.
 */
interface CapabilityManager {
    /** Current state of all tracked capabilities */
    val capabilityStates: StateFlow<Map<String, CapabilityState>>

    /** Descriptors for all tracked capabilities */
    val descriptors: List<CapabilityDescriptor>

    /** Refresh all capability states */
    suspend fun refresh(): Map<String, CapabilityState>

    /** Get state of a specific capability */
    fun getState(id: String): CapabilityState

    /** Check if a capability is available */
    fun isAvailable(id: String): Boolean

    /** Check if a capability is degraded */
    fun isDegraded(id: String): Boolean

    /** Check if a capability is blocked/unavailable */
    fun isBlocked(id: String): Boolean

    /** Get descriptor for a capability */
    fun getDescriptor(id: String): CapabilityDescriptor?

    /** Get all capabilities in a category */
    fun getCapabilitiesByCategory(category: CapabilityCategory): List<CapabilityDescriptor>

    /** Get overall health percentage */
    fun getHealthPercentage(): Int

    /** Get capabilities that are blocked/degraded */
    fun getProblematicCapabilities(): List<CapabilityDescriptor>

    /** Refresh and return all capabilities */
    suspend fun refreshCapabilities(): Map<String, CapabilityState>

    /** Register a listener for capability changes */
    fun addListener(listener: CapabilityListener)

    /** Unregister a listener */
    fun removeListener(listener: CapabilityListener)

    /** Open system settings for a capability */
    fun openSettings(capabilityId: String, context: Context): Boolean
}

/**
 * Listener for capability changes
 */
interface CapabilityListener {
    fun onCapabilityChanged(capabilityId: String, oldState: CapabilityState, newState: CapabilityState)
}