# Faza A — Corecții funcționale

Corecțiile de logică găsite la testarea fazei 5 pe S24+ (manual și autonom: `PHONE_TEST_REPORT.md`,
`NEXT_STAGE_ANALYSIS.md`). Fără redesign: aspectul aplicației, ecranele și tema sunt neschimbate; doar
câteva texte noi în cardurile SOARE și LOCAȚIE. Fără biblioteci noi; persistența rămâne SharedPreferences.

## Modificări

| Fișier | Ce s-a schimbat |
|---|---|
| `domain/location/Maidenhead.kt` | `toPosition` acceptă doar 4, 6 sau 8 caractere (`VALID_LENGTHS`); `isValid()` nou; `fromPosition` cere tot 4, 6 sau 8 |
| `domain/model/Models.kt` | `StationPosition.locator` = doar un locator valid; câmpul nou `invalidLocator`; documentația din `StationIdentity` aliniată (4/6/8) |
| `data/location/LocationProvider.kt` | `hasPrecisePermission()` (interfață + implementarea Android) |
| `data/location/PositionRepository.kt` | Ordinea în `locate()`: permisiune → locația pornită → poziția recentă; o poziție cu altă precizie nu e folosită (nici la pornire, nici poziția altei aplicații); locatorul invalid nu devine poziție, QTH sau insignă |
| `ui/dashboard/DashboardViewModel.kt` | Cardul SOARE primește poziția sau motivul lipsei ei din același `StationPosition` ca LOCAȚIE (`SunInput`), fără logică GPS separată; cardul LOCAȚIE primește locatorul invalid |
| `ui/dashboard/DashboardUiState.kt` | `SunStatus.NO_POSITION` înlocuit cu `GPS_NO_PERMISSION`, `GPS_LOCATION_OFF`, `GPS_SEARCHING`, `GPS_UNAVAILABLE`; `LocationUiState.invalidLocator` |
| `ui/dashboard/DashboardScreen.kt`, `LocationCard.kt` | Textul potrivit pentru fiecare stare (fără schimbări de aspect) |
| `res/values*/strings.xml` | `sun_location_off`, `sun_gps_unavailable` (RO și EN) |
| Teste | Vezi mai jos |
| `README.md`, `OBSERVATII.md` | Faza A, regulile noi, testele |

## Locator

**Regula finală:** valid doar cu **4, 6 sau 8 caractere**, fiecare în intervalul locului său
(A–R, 0–9, A–X, 0–9), litere mari sau mici. Verificarea e într-un singur loc: `Maidenhead.toPosition`
(și `Maidenhead.isValid`).

| Locator | Rezultat |
|---|---|
| `KN46`, `KN46dw`, `KN46dw12` | valid |
| `KN`, `KN4`, `KN46d`, `KN46dwx`, gol, caractere nepermise, `ZZ99`, `KN46zz` | invalid |

**Un locator invalid:**
- se păstrează în Setări cât e scris (salvarea e automată, la fiecare literă; „K”, „KN4” sunt pași
  firești până la „KN46dw”); câmpul nu și-a schimbat aspectul;
- **nu** devine poziție, QTH, insignă lângă indicativ, sursă pentru Soare sau pentru zi/noapte la benzi;
- dashboard-ul spune „Locatorul „KN4” nu este valid.” în cardul SOARE (ca înainte) și acum și în cardul
  LOCAȚIE (în loc de „Locația nu este disponibilă.”); benzile folosesc regula 06–18;
- în modul Automat, un locator invalid nu e folosit ca rezervă.

## GPS

1. **Ordinea verificărilor** în `PositionRepository.locate()`:
   permisiunea → **locația pornită** → abia apoi poziția recentă (regula de 10 minute, păstrată).
   Locația oprită nu mai e ascunsă de o poziție recentă: starea e `LOCATION_OFF`, iar ultima poziție
   rămâne folosită (ca înainte, cu locația oprită).
2. **Aproximativ ↔ exact:** fiecare poziție știe cu ce precizie a fost luată (`approximate`). Dacă
   precizia permisă acum e alta, poziția veche nu mai e folosită — nici cea salvată, nici cea din
   memorie, nici poziția recentă a altei aplicații — și se cere una nouă. Detectarea are loc la
   următoarea citire: pornirea aplicației, revenirea în ea (`onResume` → `refreshNow`) sau la 30 de
   minute. Cu aceeași precizie, regula de 10 minute rămâne neschimbată.

## Solar

Cardul SOARE ia motivul lipsei poziției din același `StationPosition` (`GpsStatus`) ca LOCAȚIE:

| Stare | Mesaj (RO) |
|---|---|
| Fără permisiune | Permite accesul la locație sau introdu locatorul Maidenhead în Setări. |
| Locația oprită | Pornește locația telefonului sau introdu locatorul Maidenhead în Setări. |
| Se caută poziția | Se caută poziția… |
| Poziție indisponibilă | Poziția GPS nu este disponibilă momentan. Poți introduce locatorul Maidenhead în Setări. |
| Poziție disponibilă | Orele Soarelui (neschimbat) |

Starea GPS ajunge la card doar cât nu există poziție, deci o stare GPS nouă cu aceeași poziție nu
recalculează Soarele (test nou). `SolarCalculator` nu s-a schimbat.

## Teste

| | |
|---|---|
| Total | **258** (227 înainte, 31 noi) |
| PASS | **258** |
| FAIL | **0** |

Noi sau actualizate:
- `MaidenheadTest`: valide KN46 / KN46dw / KN46dw12 (și litere mici, spații), invalide KN / KN4 /
  KN46d / KN46dwx / gol / caractere nepermise / în afara intervalelor; `fromPosition` doar 4/6/8.
  Actualizat: „KN” era valid (regula veche, schimbată intenționat).
- `PositionRepositoryTest` (+14): locatori valizi și invalizi, locator invalid în Automat, locația
  oprită cu poziție recentă, locația pornită cu poziție recentă / veche, locația oprită mai târziu,
  exact → aproximativ, aproximativ → exact, schimbarea în timpul rulării, poziția altei aplicații cu
  precizia veche, precizie schimbată cu locația oprită și la pornire, aceeași precizie (regula de 10 min).
- `DashboardViewModelTest` (+10): locator invalid (nici QTH, nici insignă, spus pe LOCAȚIE, fără Soare,
  benzi după ceas), locatori de 4 și 8 caractere, cardul SOARE în Automat pentru fiecare stare GPS și
  cu poziție validă, fără recalculare la o stare GPS nouă cu aceeași poziție. Actualizat: Automat +
  locația oprită dă acum `GPS_LOCATION_OFF`, nu „fără permisiune”.
- `DashboardScreenTest` (+3): câte un mesaj pentru fiecare stare GPS; locatorul invalid nu apare în insignă.
- `LocationCardTest` (+2): locatorul invalid spus pe card, și împreună cu locația oprită.

## Build

| | |
|---|---|
| Debug | ✅ BUILD SUCCESSFUL, fără avertismente de compilare |
| Release | ✅ BUILD SUCCESSFUL (`app-release-unsigned.apk`) |
| Lint | ✅ „No issues found.” |
| Comanda | `gradlew clean assembleDebug assembleRelease test lint assembleDebugAndroidTest` |

**Verificare scurtă pe S24+** (locația telefonului oprită; setările tale readuse la final:
ER1PL, KN46dw, Manual, Luminos):
- `KN4`, `KN`, `KN46d` → „Locatorul „…” nu este valid.” pe SOARE și LOCAȚIE, „QTH: —”, insigna doar ER1PL;
- `KN46dw12` → 46,9271° N, QTH KN46dw12, Soarele calculat;
- Automat, fără locator, locația oprită → „Pornește locația telefonului sau introdu locatorul…”
  (nu mai cere permisiunea);
- Automat + KN46dw + locația oprită → Soarele din KN46dw și „Locația telefonului este oprită.”;
- Logcat fără erori ale aplicației.

## Regresii

Niciuna găsită. Toate testele existente trec. Trei teste vechi au fost actualizate pentru că verificau
exact regula schimbată intenționat: „KN” valid, `fromPosition` cu 2 caractere, `NO_POSITION` pentru
locația oprită. Neschimbate: calculul solar, N0NBH, regula de 10 minute, intervalul de 30 de minute,
modul Manual, permisiunile, aspectul.

## Probleme rămase

Nu țin de Faza A; rămân pentru **Faza B — redesign** (vezi `OBSERVATII.md`):
- „Automatic (GPS)” pe două rânduri în engleză la font 1,3; „Întunecat”, etichetele benzilor și
  indicativul de 15 caractere la limită;
- eroarea afișată chiar la câmpul locator din Setări (acum se vede doar pe dashboard; designul câmpului
  nu a fost schimbat, cum s-a cerut);
- data lângă orele Soarelui care cad în altă zi;
- indicatorul „ultima poziție” / „GPS oprit — se folosește ultima poziție”;
- pull-to-refresh, peisaj, reorganizarea ecranelor.

De verificat pe telefon cu tine (cer locația pornită sau schimbarea permisiunii): locația oprită cu o
poziție GPS recentă și schimbarea aproximativ ↔ exact.
