package com.local.bulksms.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Color used for phone numbers that pass the carrier validation. */
@Composable
fun availablePhoneColor(): Color = Color(0xFF1B873B)

/** Color used for phone numbers that cannot be sent to. */
@Composable
fun invalidPhoneColor(): Color = MaterialTheme.colorScheme.error

/** Color used for empty phone cells. */
@Composable
fun emptyPhoneColor(): Color = MaterialTheme.colorScheme.outline
