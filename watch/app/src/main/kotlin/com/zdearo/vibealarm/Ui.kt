package com.zdearo.vibealarm

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface

/**
 * Tokens e fontes do Vibe Alarm no relógio. O mockup é um quadro de 400 px; na tela real (Galaxy Watch: 203 dp de
 * largura) as medidas dele ficariam minúsculas, então aqui tudo é em dp, no padrão do Wear OS:
 * horário 48, texto 13–16, alvo de toque 40–44. Larguras são frações da tela para caber no círculo.
 */
object Ui {
    val INK = Color.parseColor("#0B0B0B")
    val SURFACE = Color.parseColor("#1A1A1A")
    val TEXT = Color.parseColor("#E4E4E4")
    val MUTED = Color.parseColor("#9A9A9A")
    val ACCENT = Color.parseColor("#2B3B79")

    /** Pixels por dp. */
    fun scale(c: Context) = c.resources.displayMetrics.density

    /** Bricolage Grotesque 700: horários e títulos. */
    fun display(c: Context) = font(c, "fonts/BricolageGrotesque.ttf", "'wght' 700, 'opsz' 72, 'wdth' 100")

    /** Inter 400 ou 600: texto. */
    fun body(c: Context, weight: Int) = font(c, "fonts/Inter.ttf", "'wght' $weight, 'opsz' 14")

    private val cache = HashMap<String, Typeface>()

    // São fontes variáveis: o peso vem dos eixos, não de arquivos separados.
    private fun font(c: Context, path: String, axes: String) =
        cache.getOrPut(path + axes) {
            Typeface.Builder(c.assets, path).setFontVariationSettings(axes).build() ?: Typeface.DEFAULT
        }
}
