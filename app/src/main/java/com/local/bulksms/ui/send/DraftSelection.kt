package com.local.bulksms.ui.send

internal fun reconcileDraftSelection(
    previousDraftIds: Set<Long>,
    previousSelectedIds: Set<Long>,
    newDraftIds: Set<Long>,
): Set<Long> = (previousSelectedIds intersect newDraftIds) + (newDraftIds - previousDraftIds)
