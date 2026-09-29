package io.github.lilixp.utchamclock

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.ZoneId
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Aplicatie() }
    }
}

@Composable
private fun Aplicatie() {
    val context = LocalContext.current
    var setari by remember { mutableStateOf(Setari.citeste(context)) }
    var laSetari by rememberSaveable { mutableStateOf(false) }
    val texte = Texte.pentru(setari.limba)
    val ciclu = LocalLifecycleOwner.current.lifecycle

    // Ceasul se actualizează doar cât aplicația e pe ecran și doar cât e nevoie: la jumătate de secundă
    // când punctele clipesc, la fiecare secundă cu secunde afișate, altfel doar la schimbarea minutului
    // (ecranul se redesenează de 60 de ori mai rar, deci mai puțină baterie)
    var acum by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(setari.clipire, setari.secunde) {
        val pas = if (setari.clipire) 500 else if (setari.secunde) 1000 else 60_000
        ciclu.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                acum = Instant.now()
                delay(pas - acum.toEpochMilli() % pas)
            }
        }
    }

    // Modul de noapte: pornit manual sau, la ceasul de stație, automat între apus și răsărit la QTH
    val noapte = setari.modNoapte ||
        (setari.noapteAutomata && !Calcule.eZiLaQth(acum, setari.locator, ZoneId.systemDefault()))
    val tema = Teme.activa(setari.tema, noapte)

    // Datele din internet se descarcă doar cât aplicația e pe ecran: la revenire, imediat
    // (dacă au trecut cel puțin 5 minute de la ultima descărcare), apoi la fiecare 30 de minute
    var solar by remember { mutableStateOf(DateSolare()) }
    var benzi by remember { mutableStateOf<Benzi?>(null) }
    var eroareCeas by remember { mutableStateOf<Double?>(null) }
    var meteo by remember { mutableStateOf<Meteo?>(null) }
    var ultimaDescarcare by remember { mutableLongStateOf(0L) }
    // Pornește din nou (cu descărcare imediată) și când se schimbă locatorul, pentru vremea noului QTH
    LaunchedEffect(setari.locator) {
        ultimaDescarcare = 0L
        meteo = null // vremea de la vechiul QTH nu mai e valabilă
        ciclu.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                if (System.currentTimeMillis() - ultimaDescarcare >= Retea.PAUZA_MINIMA_MS) {
                    coroutineScope {
                        launch { solar = Retea.descarcaSolar(solar) }
                        launch { benzi = Retea.descarcaBenzi() }
                        launch { eroareCeas = Retea.masoaraEroareCeas() }
                        // Fără conexiune rămâne vremea de la ultima descărcare
                        launch {
                            meteo = if (!Calcule.locatorValid(setari.locator)) null
                            else Retea.descarcaMeteo(setari.locator) ?: meteo
                        }
                    }
                    ultimaDescarcare = System.currentTimeMillis()
                }
                delay(Retea.INTERVAL_MS - (System.currentTimeMillis() - ultimaDescarcare))
            }
        }
    }

    // Ceas de stație: ecranul nu se stinge cât e deschisă aplicația
    val view = LocalView.current
    DisposableEffect(setari.ecranAprins) {
        view.keepScreenOn = setari.ecranAprins
        onDispose { view.keepScreenOn = false }
    }

    // Bara de sus a telefonului: iconițe închise pe temele luminoase, deschise pe cele întunecate
    if (!view.isInEditMode) {
        SideEffect {
            val fereastra = (view.context as Activity).window
            WindowCompat.getInsetsController(fereastra, view).apply {
                isAppearanceLightStatusBars = tema.luminoasa
                isAppearanceLightNavigationBars = tema.luminoasa
            }
        }
    }

    TemaAplicatie(tema) {
        Box(Modifier.safeDrawingPadding()) {
            if (laSetari) {
                EcranSetari(setari, texte,
                    laSchimbare = { setari = it; Setari.salveaza(context, it) },
                    laInapoi = { laSetari = false })
            } else {
                EcranCeas(StareCeas(acum, solar, benzi, eroareCeas, meteo), setari, tema, texte,
                    laSetari = { laSetari = true })
            }
        }
    }
}

/** Fundalul temei, iar componentele standard (câmpuri, butoane) primesc culorile ei. */
@Composable
fun TemaAplicatie(tema: Tema, continut: @Composable () -> Unit) {
    val culori = if (tema.luminoasa) lightColorScheme(
        primary = tema.utc.aprins, onPrimary = tema.fundal, background = tema.fundal, surface = tema.fundal,
        onBackground = tema.utc.aprins, onSurface = tema.utc.aprins, onSurfaceVariant = tema.info,
        outline = tema.info, surfaceContainerHigh = tema.fundal,
    ) else darkColorScheme(
        primary = tema.utc.aprins, onPrimary = tema.fundal, background = tema.fundal, surface = tema.fundal,
        onBackground = tema.utc.aprins, onSurface = tema.utc.aprins, onSurfaceVariant = tema.info,
        outline = tema.info, surfaceContainerHigh = tema.fundal,
    )
    MaterialTheme(colorScheme = culori) {
        Surface(Modifier.fillMaxSize(), color = tema.fundal, content = continut)
    }
}
