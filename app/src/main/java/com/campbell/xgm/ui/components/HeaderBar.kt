package com.campbell.xgm.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CutCornerShape
import com.campbell.xgm.R

@Composable
fun HeaderBar() {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        val alienShape = CutCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(alienShape)
                .background(Color(0xFF0F0F0F)) // Deep dark metallic background
                .border(
                    width = 2.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary, // Neon Red
                            MaterialTheme.colorScheme.secondary, // Electric Blue
                            MaterialTheme.colorScheme.primary
                        )
                    ),
                    shape = alienShape
                )
                .padding(vertical = 20.dp, horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val appName = stringResource(id = R.string.app_name).uppercase()
                val splitIndex = appName.indexOf("XGM")
                val prefix = if (splitIndex >= 0) appName.substring(0, splitIndex) else appName
                val suffix = if (splitIndex >= 0) appName.substring(splitIndex) else ""

                val shadow = androidx.compose.ui.graphics.Shadow(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    blurRadius = 8f
                )

                Text(
                    text = prefix,
                    style = MaterialTheme.typography.headlineMedium.copy(shadow = shadow),
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 6.sp
                )
                Text(
                    text = suffix,
                    style = MaterialTheme.typography.headlineMedium.copy(shadow = shadow),
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 6.sp
                )
            }
        }
    }
}
