package com.campbell.xgm.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AlienButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDanger: Boolean = false,
    isEnabled: Boolean = true
) {
    val borderColor = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val contentColor = if (!isEnabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    else if (isDanger) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary

    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = isEnabled,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.5.dp, if (isEnabled) borderColor else borderColor.copy(alpha = 0.3f)),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
        )
    }
}
