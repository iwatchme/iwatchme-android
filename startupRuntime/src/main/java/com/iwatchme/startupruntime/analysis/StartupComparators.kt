package com.iwatchme.startupruntime.analysis

import com.iwatchme.startupruntime.model.StartupTaskReport

internal fun startupTaskReportComparator(first: StartupTaskReport, second: StartupTaskReport): Int {
    val firstOffset = first.startOffsetMs ?: Long.MAX_VALUE
    val secondOffset = second.startOffsetMs ?: Long.MAX_VALUE
    return compareValuesBy(first, second, { firstOffset }, { secondOffset }, { it.id })
}
