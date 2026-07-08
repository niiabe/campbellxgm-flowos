package com.campbell.xgm.data.local

data class GameTargetEntity(
    val packageName: String,
    val gameName: String,
    val isAllowed: Boolean = true
)
