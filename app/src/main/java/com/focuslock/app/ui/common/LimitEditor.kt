package com.focuslock.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.focuslock.app.R
import com.focuslock.app.data.repository.DailyLimit
import kotlin.math.roundToInt

/**
 * The daily limit is edited as *text*, because a half-typed number ("", "1" on the way to "10")
 * is a legitimate state that no `Int` can represent. These helpers turn that text into values.
 */
object LimitInput {
    /** Minutes if [text] is a whole number within the allowed range, otherwise null. */
    fun parse(text: String): Int? =
        text.toIntOrNull()?.takeIf { it in DailyLimit.MIN_MINUTES..DailyLimit.MAX_MINUTES }

    /**
     * Keeps digits only, caps the length at three, and clamps anything above the maximum straight to it.
     * Clamping while typing is safe because a number above the maximum can never become valid by typing
     * more digits. Too-small numbers are left alone: "1" may be on the way to "10".
     */
    fun sanitize(raw: String): String {
        val digits = raw.filter { it in '0'..'9' }.take(3)
        return if ((digits.toIntOrNull() ?: 0) > DailyLimit.MAX_MINUTES) DailyLimit.MAX_MINUTES.toString() else digits
    }

    /** Slider position for [text]; out-of-range or empty text is shown at the nearest end. */
    fun sliderValue(text: String): Float =
        DailyLimit.coerce(text.toIntOrNull() ?: DailyLimit.MIN_MINUTES).toFloat()

    /** Snaps a raw slider value to the nearest [DailyLimit.STEP_MINUTES], so dragging feels deliberate. */
    fun snap(value: Float): Int =
        DailyLimit.coerce((value / DailyLimit.STEP_MINUTES).roundToInt() * DailyLimit.STEP_MINUTES)

    /** Replaces unusable text with the nearest valid value; used when the field loses focus. */
    fun fix(text: String): String = sliderValue(text).roundToInt().toString()
}

/**
 * Slider (5–240 min, snapping to 5) and a number field that always show the same value.
 *
 * Stateless: the caller owns [text]. [onTextChange] reports typing (persist-worthy when
 * [LimitInput.parse] succeeds); [onSliderChange] fires continuously while dragging and
 * [onSliderFinished] once on release, so callers that save immediately can save on release only.
 */
@Composable
fun LimitEditor(
    text: String,
    onTextChange: (String) -> Unit,
    onSliderChange: (Int) -> Unit,
    onSliderFinished: () -> Unit,
    modifier: Modifier = Modifier,
    /** False greys out both controls (the limit can't change while extra time is active today). */
    enabled: Boolean = true,
) {
    val isValid = LimitInput.parse(text) != null
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = formatDuration(LimitInput.sliderValue(text).roundToInt()),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = text,
                onValueChange = { onTextChange(LimitInput.sanitize(it)) },
                modifier = Modifier
                    .width(124.dp)
                    .onFocusChanged { focus ->
                        // Don't leave a half-typed or out-of-range number behind when the user moves on.
                        if (!focus.isFocused && !isValid) onTextChange(LimitInput.fix(text))
                    },
                enabled = enabled,
                singleLine = true,
                isError = !isValid,
                shape = RoundedCornerShape(16.dp),
                suffix = { Text(stringResource(R.string.unit_min)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
        }

        Slider(
            value = LimitInput.sliderValue(text),
            onValueChange = { onSliderChange(LimitInput.snap(it)) },
            onValueChangeFinished = onSliderFinished,
            enabled = enabled,
            valueRange = DailyLimit.MIN_MINUTES.toFloat()..DailyLimit.MAX_MINUTES.toFloat(),
        )

        // One caption whose text never changes height: turns red instead of adding an error line.
        Text(
            text = stringResource(
                R.string.limit_range_hint,
                formatDuration(DailyLimit.MIN_MINUTES),
                formatDuration(DailyLimit.MAX_MINUTES),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (isValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
    }
}
