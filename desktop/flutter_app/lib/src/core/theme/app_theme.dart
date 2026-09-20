import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class AppTheme {
  static const fallbackSeedColor = Color(0xFF126A58);

  static ThemeData light({required Color seedColor}) {
    final scheme = ColorScheme.fromSeed(
      seedColor: seedColor,
      brightness: Brightness.light,
    );
    final textTheme = GoogleFonts.spaceGroteskTextTheme().copyWith(
      headlineLarge: GoogleFonts.spaceGrotesk(
        fontSize: 44,
        fontWeight: FontWeight.w700,
        letterSpacing: -1.4,
      ),
      headlineMedium: GoogleFonts.spaceGrotesk(
        fontSize: 28,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.6,
      ),
      titleLarge: GoogleFonts.spaceGrotesk(
        fontSize: 20,
        fontWeight: FontWeight.w700,
      ),
      bodyLarge: GoogleFonts.spaceGrotesk(
        fontSize: 16,
        height: 1.4,
        fontWeight: FontWeight.w500,
      ),
      bodyMedium: GoogleFonts.spaceGrotesk(fontSize: 14, height: 1.45),
      bodySmall: GoogleFonts.spaceGrotesk(fontSize: 12, height: 1.35),
      labelLarge: GoogleFonts.spaceGrotesk(
        fontSize: 14,
        fontWeight: FontWeight.w700,
      ),
    );

    final pageBackground = Color.lerp(
      scheme.surface,
      scheme.primaryContainer,
      0.14,
    )!;

    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: pageBackground,
      textTheme: textTheme,
      splashFactory: InkSparkle.splashFactory,
      cardTheme: CardThemeData(
        color: scheme.surface,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(32)),
      ),
      navigationRailTheme: NavigationRailThemeData(
        backgroundColor: Colors.transparent,
        selectedIconTheme: IconThemeData(color: scheme.primary),
        unselectedIconTheme: IconThemeData(color: scheme.onSurfaceVariant),
        selectedLabelTextStyle: textTheme.labelLarge?.copyWith(
          color: scheme.primary,
        ),
        unselectedLabelTextStyle: textTheme.labelLarge?.copyWith(
          color: scheme.onSurfaceVariant,
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: scheme.surface.withValues(alpha: 0.94),
        indicatorColor: scheme.primaryContainer,
        labelTextStyle: WidgetStatePropertyAll(textTheme.labelLarge),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(20),
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(20),
          ),
        ),
      ),
      chipTheme: ChipThemeData(
        side: BorderSide.none,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
      ),
      dividerColor: scheme.outlineVariant.withValues(alpha: 0.48),
    );
  }
}
