package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ExecutiveDarkColorScheme = darkColorScheme(
  primary = MintGreen,
  onPrimary = CharcoalBlack,
  secondary = WheatGold,
  onSecondary = CharcoalBlack,
  background = CharcoalBlack,
  onBackground = PureWhite,
  surface = DarkSurface,
  onSurface = PureWhite,
  error = WarningRed,
  onError = PureWhite
)

@Composable
fun MyApplicationTheme(
  // Force dark theme for the Executive Tech aesthetic
  darkTheme: Boolean = true,
  // Disable dynamic color for strict branding
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = ExecutiveDarkColorScheme

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as Activity).window
      window.statusBarColor = CharcoalBlack.toArgb()
      window.navigationBarColor = CharcoalBlack.toArgb()
      WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
      WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
    }
  }

  MaterialTheme(
    colorScheme = colorScheme, 
    typography = Typography, 
    content = content
  )
}
