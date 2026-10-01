# UTCRadioClock

Aplicație Android pentru radioamatori: ora UTC, ora locală și, în fazele următoare, soarele,
locația (locator Maidenhead), activitatea solară și geomagnetică și condițiile de propagare HF.

Autor: Lilian Putină, ER1PL. Versiunea 2.0, scrisă de la zero (versiunea 1 e pe branch-ul `main`).

## Stare (1 octombrie 2026)

| Faza | Ce | Commit |
|------|----|--------|
| 1 | Proiectul, dashboard-ul, tema Sistem / Luminos / Întunecat | `e41fd47` |
| – | Numele „UTC Radio Clock”, indicativ și locator în Setări | `03ebc26` |
| – | Tema mutată în Setări (secțiunea ASPECT) | `c675c4c` |
| – | Audit și curățenie | `6f0293f` |
| 2 | UTC și ora locală (`TimeProvider`, `java.time.Clock`) | `98c0d8a` |
| 3 | Soarele: răsărit, apus, amiază solară, durata zilei, crepuscul civil | `6a6aa1e` |
| 4 | Propagarea HF și datele geomagnetice de la N0NBH (hamqsl.com) | (acest commit) |

Următoarele faze, observațiile și ce mai e de verificat sunt în [OBSERVATII.md](OBSERVATII.md).
Fiecare fază e un singur commit, verificat pe PC (build Debug și Release, toate testele, lint)
înainte de commit; push-ul pe GitHub îl face Lilian din Android Studio.

## Faza 1

Un dashboard cu:

- data (UTC) și ora **UTC**, cel mai vizibil element;
- ora **locală**, cu fusul orar și decalajul față de UTC (ex. `Europe/Chisinau · UTC+03:00`);
  data locală apare sub ea doar când diferă de cea UTC (după miezul nopții local);
- carduri pentru **Soare**, **Locație** și **Propagare** (Soarele funcționează din faza 3;
  Locație și Propagare au încă `—` sau un mesaj până vin fazele lor);
- alegerea temei: **Sistem / Luminos / Întunecat** (se păstrează după repornire); acum e în
  ecranul Setări, secțiunea **ASPECT**.

## Personalizare (după faza 1)

- Numele afișat: **UTC Radio Clock** (package-ul și proiectul rămân `utcradioclock` / `UTCRadioClock`).
- Ecranul **Setări** (iconița din dreapta sus): **indicativul** (implicit `ER1PL`; litere, cifre și
  `/`, cel mult 15 caractere) și **locatorul Maidenhead** (implicit gol; cel mult 8 litere și cifre,
  scris ca `KN46dw`), apoi secțiunea **ASPECT** cu tema. Totul se salvează automat, la fiecare
  modificare, și rămâne după repornire.
- Pe dashboard, indicativul și locatorul apar discret în dreapta datei; locatorul apare și la
  **QTH** în cardul Locație. Un câmp gol nu se afișează.

## Faza 2: UTC și ora locală

- **UTC** și **LOCAL** în `HH:mm:ss`, actualizate la începutul fiecărei secunde. Ambele (și datele)
  vin din **același moment** citit o singură dată, deci nu pot fi decalate între ele.
- Ora locală folosește **fusul orar al telefonului**, nimic nu e fixat pe Moldova. Dacă utilizatorul
  schimbă fusul (sau călătorește), noua oră locală apare la următoarea secundă. Trecerea la ora de
  vară/iarnă vine din baza de fusuri orare a telefonului (ex. Chișinău: UTC+3 → UTC+2).
- Data locală apare sub ora locală doar când diferă de data UTC (de ex. după miezul nopții local).

## Faza 3: Soarele

Cardul **SOARE** arată, în ora locală a telefonului, rotunjite la minut:
**răsărit, apus, amiaza solară, durata zilei** și **crepusculul civil** (dimineața – seara).

- **Poziția** vine din locatorul Maidenhead din Setări: centrul pătratului lui (KN46dw →
  46,9375° N, 28,2917° E; cu 6 caractere, cel mult ~3 km de stație). Nimic nu e fixat pe KN46dw.
- **Fără locator** sau cu un locator greșit, cardul spune „Locația nu este disponibilă.” și ce e de
  făcut; nu arată nicio oră inventată.
- **Calculul** e offline (fără Internet, fără GPS), cu algoritmul NOAA (Meeus): răsărit/apus când
  marginea de sus a Soarelui e la orizont (−0,833°, cu refracția), crepuscul civil la −6°. Precizie
  de ordinul unui minut între cercurile polare.
- **Zile polare:** când Soarele nu apune sau nu răsare deloc, cardul o spune în loc de ore. În
  „nopțile albe” (Soarele nu coboară 6° sub orizont) crepusculul civil lipsește; în nopțile albe din
  nord, apusul poate cădea după miezul nopții local și e arătat ca atare.
- **Recalculare** doar când se schimbă ziua locală, fusul orar sau locatorul (verificate o dată pe
  minut, separat de ceasul de o secundă), nu la fiecare secundă.

## Faza 4: Propagarea HF (N0NBH)

Cardul **PROPAGARE** arată datele reale publicate de Paul Herrman, **N0NBH**, pe hamqsl.com:

```
[ SFI 93 ] [ K 0 ] [ A 3 ]
[ 80-40m ] [ 30-20m ] [ 17-15m ] [ 12-10m ]
N0NBH (hamqsl.com) · actualizat 05:29 UTC
```

- **Sursa:** fișierul XML `https://www.hamqsl.com/solarxml.php`, oferit de N0NBH pentru alte
  programe; cere actualizare de cel mult o dată pe oră și mențiunea sursei. Din el se folosesc
  `solarflux` (SFI), `kindex` (K, număr întreg), `aindex` (A), `updated` (ora datelor, UTC) și
  `calculatedconditions` (cele 4 grupuri de benzi, ziua și noaptea).
- **Benzile:** culoarea vine din clasificarea N0NBH: Good = verde (Bun), Fair = galben (Mediu),
  Poor = roșu (Slab). N0NBH dă valori separate pentru zi și noapte; se arată cea care se aplică
  acum la stație (între răsărit și apus, din faza 3; fără locator, 06:00–18:00 ora locală).
- **Apăsarea unei benzi** deschide explicația: „80-40 m · Ziua: Mediu • Noaptea: Bun · Condiții
  calculate de N0NBH (hamqsl.com).”, plus dacă acum e zi sau noapte la stație.
- **SFI / K / A** au culori din scale publice: K după scara NOAA a furtunilor geomagnetice
  (0–3 verde, 4 galben, ≥5 roșu), A după categoriile zilnice NOAA (<16 verde, 16–29 galben,
  ≥30 roșu). Pentru SFI NOAA nu are scală: pragurile uzuale la radioamatori și din v1
  (≥120 verde, 90–119 galben, <90 roșu). Vezi `IndexScales.kt`.
- **Fără Internet:** se arată ultimele date valide salvate, cu „Date neactualizate · ultima
  actualizare … UTC”; fără date salvate: „Date indisponibile”. Nicio valoare nu e inventată.
- **Actualizare:** doar cât dashboard-ul e pe ecran: datele salvate imediat, o descărcare doar dacă
  sunt mai vechi de o oră, apoi o dată pe oră; după o eroare, din nou după 15 minute.

Orele sunt mereu în format de 24 de ore, cu secunde. Textele sunt în engleză și română
(după limba telefonului).

## Tehnologii

Kotlin, Jetpack Compose, Material 3, Gradle Kotlin DSL (cu version catalog), AndroidX,
Kotlin Coroutines, StateFlow, ViewModel. Teste: JUnit, kotlinx-coroutines-test, Compose UI tests
(pe JVM cu Robolectric și pe telefon/emulator). Fără Java și fără XML pentru interfață.

| Ce | Versiune |
|----|----------|
| Kotlin | 2.4.20 (Kotlin inclus în AGP 9, ridicat de pluginul Compose) |
| Android Gradle Plugin | 9.4.1 |
| Gradle | 9.6.0 |
| Compose BOM | 2026.09.00 |
| compileSdk / targetSdk | 37 |
| minSdk | 26 (Android 8.0) |
| Java (bytecode) | 17 |

**De ce minSdk 26:** `java.time` (fusuri orare, ore, date) există nativ de la Android 8.0,
fără desugaring, iar aproape toate telefoanele în uz au Android 8 sau mai nou.
**De ce targetSdk 37:** cea mai nouă versiune Android; aplicația respectă regulile ei
(afișare edge-to-edge, sub bara de stare și bara de navigare).

Samsung One UI 8.5 este Android 16 cu interfața Samsung; nu e un API separat. Aplicația folosește
doar API-uri Android standard, deci merge la fel pe Samsung și pe alte telefoane.

## Arhitectură

```
UI (Compose)  →  ViewModel (StateFlow)  →  Repository  →  surse de date
```

```
app/src/main/java/io/github/lilixp/utcradioclock/
├── MainActivity.kt              ecranele (dashboard / setări), tema, bara de stare
├── AppContainer.kt              obiectele comune (injecție manuală, fără framework)
├── data/
│   ├── time/                    TimeProvider (singura sursă de timp), ClockRepository (un tic pe secundă, ziua locală)
│   ├── settings/                SettingsRepository (tema, indicativul, locatorul, în SharedPreferences)
│   ├── location/                PositionRepository (poziția stației: acum din locator, mai târziu GPS)
│   └── propagation/             HttpClient, PropagationCache, PropagationRepository (N0NBH + cache)
├── domain/
│   ├── model/                   ClockReading, LocalDay, ThemeMode, StationIdentity, GeoPosition,
│   │                            SolarDay, SolarConditions, BandGroup, ConditionLevel
│   ├── location/                Maidenhead (locator → latitudine/longitudine)
│   ├── solar/                   SolarCalculator (NOAA: răsărit, apus, amiază solară, crepuscul civil)
│   └── propagation/             HamQslParser (XML N0NBH), IndexScales (culorile SFI/K/A)
├── ui/
│   ├── dashboard/               DashboardScreen, PropagationCard, DashboardViewModel, DashboardUiState
│   ├── settings/                SettingsScreen, SettingsViewModel
│   └── theme/                   culorile Material 3 (luminos și întunecat)
└── util/                        TimeFormatter (texte pentru ore, date, fus orar)
```

- **TimeProvider** e singura sursă de timp: un `java.time.Clock` (în aplicație `Clock.systemUTC()`)
  pentru moment și fusul orar al telefonului (`ZoneId.systemDefault()`, cerut din nou la fiecare
  secundă). Testele îi dau `Clock.fixed(...)` sau un ceas virtual, deci nu depind de ora PC-ului.
  Nicăieri altundeva nu se citește ora sistemului.
- **ClockRepository** dă o citire (moment + fus) o dată pe secundă, ca `Flow`. Fiecare pas așteaptă
  doar până la începutul secundei următoare, deci nu se acumulează întârzieri (fără drift).
- **DashboardViewModel** combină ora cu indicativul și locatorul într-un singur
  `StateFlow<DashboardUiState>`, care are deja textele gata de afișat. Există **un singur ticker**,
  împărțit de toți cei care ascultă (`stateIn`). ViewModel-ul supraviețuiește rotației, deci
  recrearea ecranului nu pornește alt ceas. Ecranul ascultă cu `collectAsStateWithLifecycle`, deci
  tickerul merge doar cât dashboard-ul e vizibil: se oprește la 5 secunde după ce aplicația trece
  în fundal sau se deschid Setările și repornește imediat, cu ora curentă, la întoarcere.
- **Tema** e o setare a întregii aplicații: MainActivity o ia din **SettingsViewModel**
  (`ThemeMode.isDark` alege luminos sau întunecat), iar dashboard-ul nu se ocupă de ea.
- **SettingsViewModel** doar citește și salvează prin **SettingsRepository**; curățarea valorilor
  (indicativ, locator) e în **StationIdentity**, iar SharedPreferences doar în repository.
- **Soarele:** `Maidenhead` și `SolarCalculator` sunt cod pur, fără Android și fără ceas, testate
  separat. **PositionRepository** transformă locatorul din Setări în poziție; o sursă GPS îl poate
  înlocui mai târziu fără să se schimbe restul. **DashboardViewModel** cere calculul doar când se
  schimbă ziua locală, fusul sau poziția (`ClockRepository.localDays()`, verificat o dată pe minut),
  păstrează ultimul rezultat și trimite ecranului doar texte.
- **Propagarea:** `UrlConnectionHttpClient` (HttpURLConnection din Android, timeout 10 s, fără
  biblioteci noi) descarcă XML-ul, `HamQslParser` (XmlPullParser din Android) îl citește,
  **PropagationRepository** îl păstrează în SharedPreferences (`propagation_cache`, în afara
  backup-ului) și dă un `Flow<PropagationState>` (se încarcă / curent / neactualizat / indisponibil),
  pe care **DashboardViewModel** îl transformă în texte și culori. Nimic din rețea nu e în ecran.
- **Locatorul** ajunge pe ecran ca simplu text din DashboardViewModel; acum vine din Setări,
  iar mai târziu ViewModel-ul îl poate lua din GPS fără ca ecranul să se schimbe.
- Nu există bibliotecă de navigare, Hilt sau mai multe module: două ecrane nu le cer încă.
  Se adaugă când apar mai multe ecrane (de ex. widget).

## Rulare

### Din Android Studio

1. **File → Open** și alege folderul `UTCRadioClock`. Așteaptă sincronizarea Gradle.
2. Alege dispozitivul din lista de sus (emulator sau telefon) și apasă **Run ▶**.

### Pe emulator

**Tools → Device Manager → Create Virtual Device**, alege un telefon (ex. Pixel 9) și o imagine
de sistem cu API 36 sau 37, apoi pornește-l și apasă **Run ▶**.

### Pe telefon (ex. Samsung Galaxy)

**1. Pornește Developer Options (Opțiuni dezvoltator)**
Setări → Despre telefon → Informații software → atinge de 7 ori **Număr versiune**
(Build number). Confirmă cu PIN-ul. În Setări apare **Opțiuni dezvoltator**, la sfârșitul listei.

**2a. USB debugging**
Setări → Opțiuni dezvoltator → pornește **Remedierea erorilor prin USB** (USB debugging).
Conectează telefonul prin cablu și acceptă pe telefon „Permiteți remedierea erorilor prin USB?”.
Pe Samsung, **Auto Blocker** (Setări → Securitate și confidențialitate) trebuie să fie oprit,
altfel blochează comenzile prin USB.

**2b. Wi-Fi debugging (fără cablu, Android 11+)**
Telefonul și PC-ul trebuie să fie în aceeași rețea.
1. Setări → Opțiuni dezvoltator → pornește **Remedierea erorilor wireless** (Wireless debugging).
2. Prima dată: atinge **Asociați dispozitivul cu un cod de asociere**. Pe PC:
   ```
   adb pair <IP>:<port-de-asociere>
   ```
   și scrie codul de 6 cifre afișat pe telefon.
3. Apoi, de fiecare dată (portul se schimbă când repornești opțiunea): în ecranul
   Remedierea erorilor wireless se vede **Adresă IP și port**. Pe PC:
   ```
   adb connect <IP>:<port>
   ```
   Android Studio poate face asocierea și prin cod QR: lista de dispozitive →
   **Pair Devices Using Wi-Fi**.

**3. Verifică legătura**
```
adb devices
```
Telefonul trebuie să apară cu starea `device` (nu `unauthorized` sau `offline`).
`adb` e în `%LOCALAPPDATA%\Android\Sdk\platform-tools`.

**4. Rulează** din Android Studio (Run ▶), sau din linia de comandă:
```
gradlew installDebug
```

## Build și teste

În linia de comandă, cu JDK-ul din Android Studio
(`set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`):

| Comandă | Ce face |
|---------|---------|
| `gradlew assembleDebug` | APK-ul de test: `app/build/outputs/apk/debug/` |
| `gradlew testDebugUnitTest` | teste JUnit + teste Compose pe JVM (Robolectric), fără telefon |
| `gradlew connectedDebugAndroidTest` | teste Compose pe telefon sau emulator conectat |
| `gradlew lint` | Android lint: `app/build/reports/lint-results-debug.html` |
| `gradlew build` | tot ce e mai sus, fără testele de pe telefon |

Teste pe PC (`gradlew testDebugUnitTest`, fără telefon). Niciunul nu folosește ora reală a PC-ului:

- `TimeProviderTest`: momentul vine din `Clock.fixed`, fusul e cerut din nou la fiecare citire,
  UTC și ora locală din aceeași citire;
- `MaidenheadTest`: locatori de 2, 4, 6 și 8 caractere în mai multe părți ale lumii, colțurile
  lumii, litere mari/mici, locator gol și invalid;
- `SolarCalculatorTest`: răsărit, apus, amiază solară, crepuscul civil și durata zilei pentru
  Chișinău (ora de vară și de iarnă), Londra, New York, Tokio și Sydney (unde răsăritul e în ziua
  UTC anterioară), noaptea albă la 65° N (apus după miezul nopții, fără crepuscul civil), zi și
  noapte polară la 78,5° N, latitudini și longitudini extreme fără erori. Referințele sunt
  momentele exacte calculate separat pe PC (vezi comentariul din test), toleranță 30 s;
- `DashboardViewModelTest` (ceas virtual): starea inițială, ticul la fiecare secundă, alinierea la
  secundă fără drift, data locală după miezul nopții local, miezul nopții UTC, schimbarea fusului
  telefonului, trecerea la ora de iarnă, un singur ticker pentru mai mulți ascultători, oprirea
  tickerului când dashboard-ul nu mai e vizibil, limba textelor, indicativul și locatorul din setări;
  Soarele din locator, alt locator, locator gol sau invalid, recalculare doar la zi nouă, fus nou
  sau locator nou (nu la fiecare secundă), ora de iarnă în orele Soarelui;
- `ThemeTest`: Sistem urmează telefonul, Luminos și Întunecat nu;
- `MainActivityTest` (Robolectric, aplicația reală, ceas oprit la o oră cunoscută): UTC, ora locală
  și data pe ecran, același ViewModel (deci același ceas) după recreare, pornirea cu valorile
  implicite, Dashboard → Setări → Dashboard cu săgeata și cu butonul Înapoi al telefonului, tema
  întunecată și luminoasă aplicată întregii aplicații și păstrată când activitatea e recreată;
  cardul SOARE fără locator, apoi cu orele calculate după ce locatorul e scris în Setări;
- `TimeFormatterTest`: `HH:mm:ss` pe 24 de ore, același moment în mai multe fusuri orare, fusul
  orar cu ora de vară și de iarnă, orele Soarelui rotunjite la minut, durata zilei „11h 45m”;
- `StationIdentityTest`: curățarea indicativului și a locatorului, lungimile maxime;
- `SettingsRepositoryTest` (Robolectric): salvarea și reîncărcarea după repornire (indicativ,
  locator, fiecare dintre cele trei teme) și valorile implicite;
- `DashboardScreenTest` (Robolectric): titlul, data, ceasurile, indicativul și locatorul,
  iconița Setări, liniuțele, propagarea, lipsa cardului ASPECT, fundalul luminos și întunecat;
  cardul SOARE cu date, fără locator, cu locator invalid, în zi polară, în noapte albă, în română
  și engleză, în tema întunecată;
- `SettingsScreenTest` (Robolectric): câmpurile, salvarea valorilor scrise, secțiunea ASPECT după
  STAȚIE, opțiunile Sistem / Luminos / Întunecat, butonul Înapoi, tema luminoasă și întunecată;
- `HamQslParserTest` (Robolectric): răspunsul real N0NBH din 1 octombrie 2026
  (`app/src/test/resources/hamqsl`), zi și noapte, zecimale, „No Report” și valori goale (rămân
  lipsă, nu zero), benzi incomplete, răspunsuri invalide (HTML, XML tăiat, JSON, gol);
- `IndexScalesTest`: pragurile K, A și SFI;
- `UrlConnectionHttpClientTest`: clientul HTTP real față de un mic server pe PC (nu Internetul):
  răspuns bun cu User-Agent-ul aplicației, erori HTTP, timeout, niciun server (ca fără Internet),
  răspuns prea mare;
- `SharedPreferencesPropagationCacheTest` (Robolectric): cache-ul gol, salvat și reîncărcat;
- `PropagationRepositoryTest` (ceas virtual): prima pornire, cache recent fără descărcare, cache vechi
  apoi date noi, fără Internet cu și fără cache, timeout, răspuns invalid (cache-ul rămâne),
  reîncercare la 15 minute, fără pâlpâire între stări, actualizare orară;
- `PropagationCardTest` (Robolectric, desen real): SFI/K/A, cele 4 benzi, culorile (verificate pe
  pixeli), valori lipsă, dialogul unei benzi, zi/noapte după ceas fără locator, date neactualizate,
  indisponibil, se încarcă, tema întunecată, engleza;
- `DashboardViewModelTest` și `MainActivityTest` acoperă și propagarea: stări, valori, zi/noapte după
  Soare la stație, aplicația reală cu răspunsul N0NBH și fără Internet.

Teste care au nevoie de telefon sau emulator (`gradlew connectedDebugAndroidTest`), în
`DashboardInstrumentedTest.kt`:

- `DashboardInstrumentedTest`: dashboard-ul fără ASPECT, cardul SOARE fără locator, cardul
  PROPAGARE care se încarcă;
- `AppInstrumentedTest`: aplicația reală care pornește și schimbă ora de la o secundă la alta,
  ecranul Setări cu ASPECT, deschis și închis.

Robolectric imită Android 16 (API 36), cel mai nou pe care îl suportă complet
(`app/src/test/resources/robolectric.properties`).

Mesajul `Unable to strip the following libraries … libandroidx.graphics.path.so` din build e doar
informativ: apare când NDK-ul nu e instalat; biblioteca e inclusă ca atare.
