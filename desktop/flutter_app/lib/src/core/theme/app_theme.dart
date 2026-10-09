import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// Material 3 Expressive (M3E) theme for the ABK desktop shell.
///
/// Expressiveness is opted into through the framework's own M3E primitives:
/// * [DynamicSchemeVariant.expressive] for the seeded color scheme, and
/// * [FadeForwardsPageTransitionsBuilder] for the expressive page motion.
///
/// Both a [light] and a [dark] variant are produced from the same seed so the
/// app can follow the OS theme.
class AppTheme {
  static const fallbackSeedColor = Color(0xFF126A58);

  static ThemeData light({required Color seedColor}) =>
      _build(brightness: Brightness.light, seedColor: seedColor);

  static ThemeData dark({required Color seedColor}) =>
      _build(brightness: Brightness.dark, seedColor: seedColor);

  static ThemeData _build({
    required Brightness brightness,
    required Color seedColor,
  }) {
    final isDark = brightness == Brightness.dark;
    final scheme = ColorScheme.fromSeed(
      seedColor: seedColor,
      brightness: brightness,
      // M3 Expressive color variant (vivid, higher-contrast palettes).
      dynamicSchemeVariant: DynamicSchemeVariant.expressive,
    );

    // Base per-brightness text theme so glyph colors adapt to the scheme, then
    // layer the expressive display/headline scale on top.
    final base = ThemeData(brightness: brightness, useMaterial3: true);
    final textTheme = GoogleFonts.spaceGroteskTextTheme(base.textTheme).copyWith(
      headlineLarge: GoogleFonts.spaceGrotesk(
        textStyle: base.textTheme.headlineLarge,
        fontSize: 44,
        fontWeight: FontWeight.w700,
        letterSpacing: -1.4,
      ),
      headlineMedium: GoogleFonts.spaceGrotesk(
        textStyle: base.textTheme.headlineMedium,
        fontSize: 28,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.6,
      ),
      titleLarge: GoogleFonts.spaceGrotesk(
        textStyle: base.textTheme.titleLarge,
        fontSize: 20,
        fontWeight: FontWeight.w700,
      ),
      bodyLarge: GoogleFonts.spaceGrotesk(
        textStyle: base.textTheme.bodyLarge,
        fontSize: 16,
        height: 1.4,
        fontWeight: FontWeight.w500,
      ),
      bodyMedium: GoogleFonts.spaceGrotesk(
        textStyle: base.textTheme.bodyMedium,
        fontSize: 14,
        height: 1.45,
      ),
      bodySmall: GoogleFonts.spaceGrotesk(
        textStyle: base.textTheme.bodySmall,
        fontSize: 12,
        height: 1.35,
      ),
      labelLarge: GoogleFonts.spaceGrotesk(
        textStyle: base.textTheme.labelLarge,
        fontSize: 14,
        fontWeight: FontWeight.w700,
      ),
    );

    final pageBackground = Color.lerp(
      scheme.surface,
      scheme.primaryContainer,
      isDark ? 0.08 : 0.14,
    )!;

    // Expressive "fade forwards" motion on every desktop/mobile target.
    const expressiveTransitions = PageTransitionsTheme(
      builders: <TargetPlatform, PageTransitionsBuilder>{
        TargetPlatform.linux: FadeForwardsPageTransitionsBuilder(),
        TargetPlatform.windows: FadeForwardsPageTransitionsBuilder(),
        TargetPlatform.macOS: FadeForwardsPageTransitionsBuilder(),
        TargetPlatform.android: FadeForwardsPageTransitionsBuilder(),
      },
    );

    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: pageBackground,
      textTheme: textTheme,
      splashFactory: InkSparkle.splashFactory,
      pageTransitionsTheme: expressiveTransitions,
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
