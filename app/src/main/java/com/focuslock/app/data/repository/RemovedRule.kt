package com.focuslock.app.data.repository

import com.focuslock.app.data.db.BlockedApp
import com.focuslock.app.data.db.DailyUsage

/**
 * Everything deleted along with a rule. Handing it back to [AppLimitRepository.restore] undoes the
 * delete exactly, including today's usage, so deleting and undoing can't be used to reset a counter.
 */
data class RemovedRule(
    val app: BlockedApp,
    val usage: List<DailyUsage>,
)
