import 'package:flutter/material.dart';

// 5 tokens de cor.
const ink = Color(0xFF0B0B0B); // fundo (Void Eclipse)
const surface = Color(0xFF1A1A1A); // cartões e linhas
const text = Color(0xFFE4E4E4); // texto e ícones ativos (Silver Mist)
const muted = Color(0xFF9A9A9A); // texto secundário
const accent = Color(0xFF2B3B79); // ação e estado ligado (Abyss Blue); sempre com `text` por cima

// Espaçamento: 4 · 8 · 16 · 24 · 32 · 48. Raios: 16 e pílula.
const s1 = 4.0, s2 = 8.0, s3 = 16.0, s4 = 24.0, s5 = 32.0, s6 = 48.0;
const radius = 16.0;

/// Números e títulos: 24, 40 ou 72.
TextStyle display(double size, {Color color = text}) => TextStyle(
      fontFamily: 'Bricolage',
      fontSize: size,
      height: 1,
      fontWeight: FontWeight.w700,
      fontVariations: const [FontVariation('wght', 700), FontVariation('opsz', 72)],
      letterSpacing: size * -0.03,
      color: color,
      fontFeatures: const [FontFeature.tabularFigures()],
    );

/// Texto: 12, 14 ou 16.
TextStyle body(double size, {Color color = text, bool bold = false}) => TextStyle(
      fontFamily: 'Inter',
      fontSize: size,
      height: size == 12 ? 16 / 12 : (size == 14 ? 20 / 14 : 24 / 16),
      fontWeight: bold ? FontWeight.w600 : FontWeight.w400,
      fontVariations: [FontVariation('wght', bold ? 600 : 400), const FontVariation('opsz', 14)],
      color: color,
    );

ThemeData buildTheme() {
  WidgetStateProperty<Color> pick(Color on, Color off) =>
      WidgetStateProperty.resolveWith((s) => s.contains(WidgetState.selected) ? on : off);
  return ThemeData(
    useMaterial3: true,
    brightness: Brightness.dark,
    scaffoldBackgroundColor: ink,
    colorScheme: const ColorScheme.dark(
      primary: accent,
      onPrimary: text,
      secondary: accent,
      onSecondary: text,
      surface: ink,
      onSurface: text,
      error: text,
      onError: ink,
    ),
    switchTheme: SwitchThemeData(
      trackColor: pick(accent, ink),
      thumbColor: pick(text, muted),
      trackOutlineColor: const WidgetStatePropertyAll(Colors.transparent),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: surface,
      indicatorColor: accent,
      height: 72,
      labelTextStyle: WidgetStateProperty.resolveWith(
        (s) => body(12, color: s.contains(WidgetState.selected) ? text : muted, bold: s.contains(WidgetState.selected)),
      ),
      iconTheme: WidgetStateProperty.resolveWith((s) => IconThemeData(color: s.contains(WidgetState.selected) ? text : muted)),
    ),
  );
}
