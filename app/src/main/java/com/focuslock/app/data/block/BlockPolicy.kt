package com.focuslock.app.data.block

import com.focuslock.app.data.repository.AppLimitRepository
import com.focuslock.app.data.repository.UsageRepository
import com.focuslock.app.data.usage.LockRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** An app that is locked right now, with what the lock screen and notification need to describe it. */
data class LockedApp(
    val packageName: String,
    val label: String,
    val usedSeconds: Long,
    /** The limit including any bonus granted today. */
    val limitSeconds: Long,
)

/**
 * Which apps are locked, as a live map keyed by package name. It follows the rules and today's usage
 * rows in Room, so pausing a rule, unlocking, raising a limit or the date changing all show up at once.
 * The accessibility service keeps the latest map in memory and checks it on every window change.
 */
@Singleton
class BlockPolicy @Inject constructor(
    private val appLimits: AppLimitRepository,
    private val usage: UsageRepository,
) {
    fun lockedApps(): Flow<Map<String, LockedApp>> =
        combine(appLimits.observeBlockedApps(), usage.observeToday()) { apps, rows ->
            val byPackage = rows.associateBy { it.packageName }
            buildMap {
                for (app in apps) {
                    val today = byPackage[app.packageName]
                    if (LockRules.isLocked(app, today)) {
                        put(
                            app.packageName,
                            LockedApp(
                                packageName = app.packageName,
                                label = app.appLabel,
                                usedSeconds = today?.usedSeconds ?: 0L,
                                limitSeconds = LockRules.effectiveLimitSeconds(app, today),
                            ),
                        )
                    }
                }
            }
        }
}
