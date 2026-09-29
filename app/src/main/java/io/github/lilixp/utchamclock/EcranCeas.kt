package io.github.lilixp.utchamclock

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Tot ce afișează ecranul principal la un moment dat. */
data class StareCeas(
    val acum: Instant,
    val solar: DateSolare,
    val benzi: Benzi?,
    val eroareCeas: Double?,
    val meteo: Meteo? = null,
)

private val ORA = DateTimeFormatter.ofPattern("HH:mm:ss")
private val ORA_SCURTA = DateTimeFormatter.ofPattern("HH:mm")
private val PENTRU_LOG = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val TEXT_INFO = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp)
private val TEXT_BENZI = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp) // patru benzi pe un rând
private val TEXT_REZUMAT = TEXT_BENZI.copy(textAlign = TextAlign.Center)
// Segmentele stinse: doar 6% din culoarea cifrelor peste fundalul pe care stau (ecran, card sau panou),
// ca să se vadă ușor, la fel pe orice temă
private const val ESTOMPARE = 0.06f
private val LocalFundal = compositionLocalOf { Color.Unspecified }

@Composable
fun EcranCeas(stare: StareCeas, setari: Setari, tema: Tema, texte: Texte, laSetari: () -> Unit) {
    val context = LocalContext.current
    val utc = stare.acum.atZone(ZoneOffset.UTC)
    val local = stare.acum.atZone(ZoneId.systemDefault())
    val peisaj = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var explicatie by remember { mutableStateOf<String?>(null) }

    // Atingerea orei UTC o copiază, pentru log
    val copiazaUtc = {
        val text = "${utc.format(PENTRU_LOG)} UTC"
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("UTC", text))
        Toast.makeText(context, texte.copiat.format(text), Toast.LENGTH_SHORT).show()
    }

    // La carduri, marginile ecranului sunt mai înguste, ca informațiile să încapă în card pe un rând
    val margine = if (setari.aranjare == Aranjari.CARDURI) 12.dp else 20.dp
    Column(Modifier.fillMaxSize().padding(horizontal = margine, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (setari.indicativ.isNotEmpty()) Indicativ(setari.indicativ, tema)
            }
            IconButton(onClick = laSetari) {
                Icon(painterResource(R.drawable.ic_setari), contentDescription = texte.setari, tint = tema.info)
            }
        }

        // Cu „două puncte care clipesc”, ele se văd doar în prima jumătate a fiecărei secunde
        val afisare = Afisare(setari.secunde, puncteAprinse = !setari.clipire || stare.acum.toEpochMilli() % 1000 < 500)
        val arata: (String) -> Unit = { explicatie = it }
        val ceasUtc = @Composable { m: Modifier -> Ceas("UTC", utc, tema.utc, tema, texte, afisare, m, copiazaUtc) }
        val ceasLocal = @Composable { m: Modifier -> Ceas("LOCAL", local, tema.local, tema, texte, afisare, m) }
        val areRezumat = Calcule.urmatorulGreyline(stare.acum, setari.locator, ZoneId.systemDefault()) != null ||
            (stare.meteo != null && setari.vremea && Calcule.locatorValid(setari.locator))

        when (setari.aranjare) {
            Aranjari.CARDURI -> {
                // Fiecare parte în cardul ei, cu colțuri rotunjite
                Spacer(Modifier.height(6.dp))
                if (peisaj) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(tema, Modifier.weight(1f)) { ceasUtc(Modifier.fillMaxWidth()) }
                        Card(tema, Modifier.weight(1f)) { ceasLocal(Modifier.fillMaxWidth()) }
                    }
                } else {
                    Card(tema) { ceasUtc(Modifier.fillMaxWidth()) }
                    Spacer(Modifier.height(12.dp))
                    Card(tema) { ceasLocal(Modifier.fillMaxWidth()) }
                }
                Spacer(Modifier.weight(1f))
                if (areRezumat) {
                    Card(tema) { Rezumat(stare, setari, tema, texte, peisaj, Modifier.fillMaxWidth(), arata) }
                    Spacer(Modifier.height(12.dp))
                }
                Card(tema) { Informatii(stare, setari, tema, texte, local, arata) }
            }

            Aranjari.PANOU -> {
                // Ca ecranul unui transceiver: ora într-un panou încadrat, ora locală pe un rând dedesubt
                if (!peisaj) Spacer(Modifier.weight(1f))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(tema.suprafata)
                    .border(1.5.dp, tema.info, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    CompositionLocalProvider(LocalFundal provides tema.suprafata) {
                        ceasUtc(Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("LOCAL", color = tema.local.secundar, fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                            Spacer(Modifier.weight(1f))
                            Afisaj(local.format(if (afisare.secunde) ORA else ORA_SCURTA), tema.local, tema, afisare,
                                Modifier.fillMaxWidth(if (peisaj) 0.35f else 0.55f))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Rezumat(stare, setari, tema, texte, peisaj, Modifier.align(Alignment.CenterHorizontally), arata)
                Spacer(Modifier.weight(1f))
                Informatii(stare, setari, tema, texte, local, arata)
            }

            else -> {
                if (peisaj) {
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        ceasUtc(Modifier.weight(1f))
                        ceasLocal(Modifier.weight(1f))
                    }
                } else {
                    // Pe vertical, ceasurile stau la mijloc între indicativ și informațiile de jos
                    Spacer(Modifier.weight(0.7f))
                    ceasUtc(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(24.dp))
                    ceasLocal(Modifier.fillMaxWidth(0.8f).align(Alignment.CenterHorizontally))
                }

                // În spațiul liber de sub ceasuri: cât mai e până la greyline și vremea
                Spacer(Modifier.weight(1f))
                Rezumat(stare, setari, tema, texte, peisaj, Modifier.align(Alignment.CenterHorizontally), arata)
                Spacer(Modifier.weight(1f))
                Informatii(stare, setari, tema, texte, local, arata)
            }
        }
    }

    explicatie?.let { text ->
        AlertDialog(
            onDismissRequest = { explicatie = null },
            confirmButton = { TextButton(onClick = { explicatie = null }) { Text(texte.ok) } },
            text = { Text(text) },
        )
    }
}

/** Cum se așază ceasurile și informațiile pe ecran; se alege separat de culori. */
object Aranjari {
    const val CLASIC = "clasic"
    const val CARDURI = "carduri"
    const val PANOU = "panou"
    val TOATE = listOf(CLASIC, CARDURI, PANOU)
}

/** Un card cu colțuri rotunjite, puțin mai deschis (sau mai închis) decât fundalul. */
@Composable
private fun Card(tema: Tema, modifier: Modifier = Modifier.fillMaxWidth(), continut: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(tema.suprafata)
        .padding(horizontal = 10.dp, vertical = 10.dp)) {
        CompositionLocalProvider(LocalFundal provides tema.suprafata) { continut() }
    }
}

/** Eticheta, ora mare și data, unele sub altele. */
@Composable
private fun Ceas(
    eticheta: String, moment: ZonedDateTime, culori: Tema.Culori, tema: Tema, texte: Texte, afisare: Afisare,
    modifier: Modifier, laAtingere: (() -> Unit)? = null,
) {
    Column(modifier) {
        Text(eticheta, color = culori.secundar, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
        val atingere = if (laAtingere != null) Modifier.clickable(onClick = laAtingere) else Modifier
        Afisaj(moment.format(if (afisare.secunde) ORA else ORA_SCURTA), culori, tema, afisare,
            Modifier.fillMaxWidth().then(atingere))
        Text(texte.data(moment), color = culori.secundar, fontFamily = FontFamily.Monospace, fontSize = 15.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

/** Cum se afișează ora: cu sau fără secunde și dacă cele două puncte sunt aprinse în acest moment. */
data class Afisare(val secunde: Boolean, val puncteAprinse: Boolean)

/**
 * Ora afișată cât de mare încape pe lățime. La fonturile cu segmente, segmentele stinse
 * („88:88:88”) se desenează slab în spate, ca la un afișaj LED adevărat.
 */
@Composable
private fun Afisaj(text: String, culori: Tema.Culori, tema: Tema, afisare: Afisare, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // Lățimea textului „88:88:88” e de ~5,6 ori mărimea fontului DSEG și de ~4,3 ori la fonturile
        // obișnuite; fără secunde („88:88”), de ~3,6 și ~2,5 ori, deci cifrele pot fi mai mari
        val raport = when {
            tema.cuSegmente -> if (afisare.secunde) 5.9f else 3.8f
            else -> if (afisare.secunde) 4.5f else 2.9f
        }
        val marime = with(LocalDensity.current) { (maxWidth.toPx() / raport).toSp() }
        val stil = TextStyle(fontFamily = tema.font, fontWeight = tema.grosime, fontSize = marime,
            textAlign = TextAlign.Center, fontFeatureSettings = "tnum")
        val toateSegmentele = if (afisare.secunde) "88:88:88" else "88:88"
        val fundal = LocalFundal.current.takeOrElse { tema.fundal }
        val stins = culori.stins?.let { lerp(fundal, culori.aprins, ESTOMPARE) }
        stins?.let { Text(toateSegmentele, style = stil, color = it, maxLines = 1, softWrap = false) }
        // Punctele stinse iau culoarea segmentelor stinse (sau devin invizibile la temele fără segmente)
        val oraAfisata = buildAnnotatedString {
            for (caracter in text) {
                if (caracter == ':' && !afisare.puncteAprinse) {
                    withStyle(SpanStyle(color = stins ?: Color.Transparent)) { append(caracter) }
                } else append(caracter)
            }
        }
        Text(oraAfisata, style = stil, color = culori.aprins, maxLines = 1, softWrap = false)
    }
}

/** Indicativul în fontul temei; la fontul cu 14 segmente, cu segmentele stinse în spate. */
@Composable
private fun Indicativ(indicativ: String, tema: Tema) {
    val stil = TextStyle(fontFamily = tema.fontIndicativ, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
    Box {
        if (tema.cuSegmente) Text("~".repeat(indicativ.length), style = stil,
            color = lerp(tema.fundal, tema.utc.aprins, ESTOMPARE), maxLines = 1)
        Text(indicativ, style = stil, color = tema.utc.aprins, maxLines = 1)
    }
}

/**
 * Cât mai e până la următorul greyline (sau cât mai durează cel de acum) și vremea la QTH,
 * unul sub altul; pe orizontală, pe un singur rând.
 */
@Composable
private fun Rezumat(
    stare: StareCeas, setari: Setari, tema: Tema, texte: Texte, peisaj: Boolean, modifier: Modifier,
    arata: (String) -> Unit,
) {
    val greyline = Calcule.urmatorulGreyline(stare.acum, setari.locator, ZoneId.systemDefault())
    val inGreyline = greyline != null && !stare.acum.isBefore(greyline.inceput)
    val textGreyline = greyline?.let {
        when {
            inGreyline -> texte.greylineAcum.format(durata(stare.acum, it.sfarsit))
            it.laRasarit -> texte.greylineRasarit.format(durata(stare.acum, it.inceput))
            else -> texte.greylineApus.format(durata(stare.acum, it.inceput))
        }
    }
    val textVreme = stare.meteo?.takeIf { setari.vremea && Calcule.locatorValid(setari.locator) }?.let { m ->
        listOfNotNull("${Math.round(m.temperatura)}°C", Calcule.grupVreme(m.cod)?.let { texte.vreme[it] },
            "${Math.round(m.vant)} km/⁠h").joinToString(" · ") // vântul nu se rupe pe două rânduri
    }

    @Composable
    fun Randuri() {
        textGreyline?.let {
            Text(it, style = TEXT_REZUMAT, color = if (inGreyline) tema.evidentiat else tema.info,
                modifier = Modifier.clickable { arata(texte.greyline) })
        }
        textVreme?.let {
            Text(it, style = TEXT_REZUMAT, color = tema.info,
                modifier = Modifier.clickable { arata(texte.explicatieVreme.format(setari.locator)) })
        }
    }
    if (peisaj) Row(modifier, horizontalArrangement = Arrangement.spacedBy(28.dp)) { Randuri() }
    else Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) { Randuri() }
}

/** Timpul rămas, rotunjit în sus la minut: „25 min” sau „5h 40m” (scurt, ca să încapă pe un rând). */
private fun durata(de: Instant, pana: Instant): String {
    val minute = (Duration.between(de, pana).seconds + 59) / 60
    return if (minute < 60) "$minute min" else "${minute / 60}h ${minute % 60}m"
}

/** Soarele, ora exactă, indicii solari, QTH-ul și condițiile pe benzi. */
@Composable
private fun Informatii(
    stare: StareCeas, setari: Setari, tema: Tema, texte: Texte, local: ZonedDateTime,
    arata: (String) -> Unit,
) {
    val soare = setari.locator.takeIf { Calcule.locatorValid(it) }?.let {
        val (lat, lon) = Calcule.locatorInCoordonate(it)
        Calcule.rasaritApus(local.toLocalDate(), lat, lon)
    }
    val (rasarit, apus) = soare ?: (null to null)
    val greyline = Calcule.eGreyline(stare.acum, rasarit, apus)
    val culoareSoare = if (greyline) tema.evidentiat else tema.info
    fun ora(moment: Instant?) = moment?.atZone(local.zone)?.format(ORA_SCURTA) ?: "--:--"

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Răsărit, apus, greyline și ora exactă
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconitaSoare(rasare = true, culoareSoare)
            Text(ora(rasarit), style = TEXT_INFO, color = culoareSoare, modifier = Modifier.padding(start = 3.dp, end = 12.dp))
            IconitaSoare(rasare = false, culoareSoare)
            Text(ora(apus), style = TEXT_INFO, color = culoareSoare, modifier = Modifier.padding(start = 3.dp, end = 12.dp))
            if (greyline) Text("GREYLINE", style = TEXT_INFO, color = tema.evidentiat,
                modifier = Modifier.clickable { arata(texte.greyline) })
            Spacer(Modifier.weight(1f))
            val dt = stare.eroareCeas
            val (textDt, culoareDt) = when {
                dt == null -> "Δt --" to tema.info
                kotlin.math.abs(dt) < 0.05 -> texte.sincronizat to tema.niveluri[0] // s-ar afișa 0.0 s
                else -> "Δt %+.1fs".format(Locale.US, dt) to tema.niveluri[Calcule.nivelDt(dt)]
            }
            Text(textDt, style = TEXT_INFO, color = culoareDt, modifier = Modifier.clickable { arata(texte.explicatieCeas(dt)) })
        }

        // Indici solari (estompați când sunt vechi) și QTH
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val s = stare.solar
            Indice("SFI", s.sfi?.toString(), s.sfi?.let { Calcule.nivelSfi(it) }, "sfi", s, tema, texte, arata)
            Indice("K", s.k?.let { "%.1f".format(Locale.US, it) }, s.k?.let { Calcule.nivelK(it) }, "k", s, tema, texte, arata)
            Indice("A", s.a?.toString(), s.a?.let { Calcule.nivelA(it) }, "a", s, tema, texte, arata)
            Spacer(Modifier.weight(1f))
            Text(if (setari.locator.isNotEmpty()) "QTH ${setari.locator}" else texte.qthLipsa,
                style = TEXT_INFO, color = tema.info, maxLines = 1, softWrap = false)
        }

        // Condițiile pe benzi pentru acum (zi sau noapte la QTH)
        val zi = Calcule.eZi(stare.acum, rasarit, apus, local.zone)
        val niveluri = Retea.nivelurileBenzilor(zi, stare.benzi, stare.solar)
        val vechi = stare.benzi == null && stare.solar.eroare
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Calcule.BENZI.forEachIndexed { i, banda ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                    val z = Retea.nivelurileBenzilor(true, stare.benzi, stare.solar)
                    val n = Retea.nivelurileBenzilor(false, stare.benzi, stare.solar)
                    arata(if (z == null || n == null) texte.benziFaraDate
                    else "$banda m\n${texte.ziua}: ${texte.niveluri[z[i]]} · ${texte.noaptea}: ${texte.niveluri[n[i]]}")
                }) {
                    Text("${banda}m", style = TEXT_BENZI, color = tema.info)
                    val nivel = niveluri?.get(i)
                    Bare(nivel, if (nivel == null) tema.stinsa else if (vechi) tema.info else tema.niveluri[nivel], tema.stinsa)
                }
            }
        }
    }
}

@Composable
private fun Indice(
    nume: String, valoare: String?, nivel: Int?, cheie: String, solar: DateSolare, tema: Tema, texte: Texte,
    arata: (String) -> Unit,
) {
    val culoare = if (nivel == null || solar.eroare) tema.info else tema.niveluri[nivel]
    Text("$nume ${valoare ?: "--"}", style = TEXT_INFO, color = culoare, modifier = Modifier.clickable {
        val (titlu, descrieri) = texte.explicatii.getValue(cheie)
        arata(if (valoare == null || nivel == null) texte.faraDate
        else "$titlu: $valoare\n${descrieri[nivel]}" + if (solar.eroare) "\n${texte.faraConexiune}" else "")
    })
}

/** Trei bare, ca semnalul telefonului: 3 = bun, 2 = mediu, 1 = slab. */
@Composable
private fun Bare(nivel: Int?, aprinsa: Color, stinsa: Color) {
    val aprinse = if (nivel == null) 0 else 3 - nivel
    Canvas(Modifier.padding(start = 4.dp).size(15.dp, 13.dp)) {
        val latime = size.width / 5
        for (j in 0 until 3) {
            val inaltime = size.height * (j + 1) / 3
            drawRect(if (j < aprinse) aprinsa else stinsa,
                topLeft = Offset(j * latime * 2, size.height - inaltime), size = Size(latime, inaltime))
        }
    }
}

/** Soare pe jumătate deasupra orizontului, cu săgeată în sus (răsărit) sau în jos (apus). */
@Composable
private fun IconitaSoare(rasare: Boolean, culoare: Color) {
    Canvas(Modifier.width(22.dp).height(15.dp)) {
        val u = size.width / 22 // aceleași coordonate ca iconița din versiunea de Windows
        val linie = 1.5f * u
        drawLine(culoare, Offset(0f, 13 * u), Offset(14 * u, 13 * u), linie)
        drawArc(culoare, 180f, 180f, useCenter = true, topLeft = Offset(2 * u, 8 * u), size = Size(10 * u, 10 * u))
        for ((x1, y1, x2, y2) in listOf(listOf(7, 5, 7, 3), listOf(2, 7, 1, 6), listOf(12, 7, 13, 6))) {
            drawLine(culoare, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), linie, StrokeCap.Round)
        }
        val (sus, jos) = if (rasare) 13f to 3f else 2f to 12f
        drawLine(culoare, Offset(19 * u, sus * u), Offset(19 * u, jos * u), linie)
        val varf = if (rasare) 1f else 14f
        val baza = if (rasare) 5f else 10f
        drawPath(Path().apply {
            moveTo(19 * u, varf * u); lineTo(16.5f * u, baza * u); lineTo(21.5f * u, baza * u); close()
        }, culoare)
    }
}
