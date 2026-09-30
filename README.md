# UTCRadioClock

Aplicație Android pentru radioamatori: ora UTC, ora locală și, în fazele următoare, soarele,
locația (locator Maidenhead), activitatea solară și geomagnetică și condițiile de propagare HF.

Autor: Lilian Putină, ER1PL. Versiunea 2.0, scrisă de la zero (versiunea 1 e pe branch-ul `main`).

## Faza 1 (acum)

Un dashboard cu:

- data (UTC) și ora **UTC**, cel mai vizibil element;
- ora **locală**, cu fusul orar și decalajul față de UTC (ex. `Europe/Chisinau · UTC+03:00`);
  data locală apare sub ea doar când diferă de cea UTC (după miezul nopții local);
- carduri pentru **Soare**, **Locație** și **Propagare**, cu `—` până vin fazele următoare;
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
│   ├── time/                    TimeSource (ceasul telefonului), ClockRepository (un tic pe secundă)
│   └── settings/                SettingsRepository (tema, indicativul, locatorul, în SharedPreferences)
├── domain/model/                ClockReading, ThemeMode, StationIdentity, SunInfo, StationLocation
├── ui/
│   ├── dashboard/               DashboardScreen, DashboardViewModel, DashboardUiState
│   ├── settings/                SettingsScreen, SettingsViewModel
│   └── theme/                   culorile Material 3 (luminos și întunecat)
└── util/                        TimeFormatter (texte pentru ore, date, fus orar)
```

- **ClockRepository** dă ora o dată pe secundă, aliniat la începutul secundei.
- **DashboardViewModel** combină ora cu setările într-un singur `StateFlow<DashboardUiState>`,
  care are deja textele gata de afișat. Se oprește la 5 secunde după ce aplicația trece în fundal.
- **SunInfo** și **StationLocation** sunt goale în faza 1; fazele următoare le vor completa
  din propriile lor repository-uri, fără să schimbe ecranul.
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

Teste:

- `DashboardViewModelTest`: starea inițială, ticul la fiecare secundă, data locală după miezul
  nopții, tema aleasă în Setări, limba textelor, indicativul și locatorul din setări (cu ceas virtual);
- `TimeFormatterTest`: formatul orelor, fusul orar cu ora de vară și de iarnă, valorile necunoscute;
- `StationIdentityTest`: curățarea indicativului și a locatorului, lungimile maxime;
- `SettingsRepositoryTest` (Robolectric): salvarea și reîncărcarea după repornire (indicativ,
  locator, fiecare dintre cele trei teme) și valorile implicite;
- `DashboardScreenTest` (Robolectric): titlul, data, ceasurile, indicativul și locatorul,
  iconița Setări, liniuțele, propagarea, lipsa cardului ASPECT, fundalul luminos și întunecat;
- `SettingsScreenTest` (Robolectric): câmpurile, salvarea valorilor scrise, secțiunea ASPECT după
  STAȚIE, opțiunile Sistem / Luminos / Întunecat, butonul Înapoi, tema luminoasă și întunecată;
- `DashboardInstrumentedTest` (pe telefon): dashboard-ul fără ASPECT, aplicația reală care pornește
  și schimbă ora de la o secundă la alta, ecranul Setări cu ASPECT, deschis și închis.

Robolectric imită Android 16 (API 36), cel mai nou pe care îl suportă complet
(`app/src/test/resources/robolectric.properties`).

Mesajul `Unable to strip the following libraries … libandroidx.graphics.path.so` din build e doar
informativ: apare când NDK-ul nu e instalat; biblioteca e inclusă ca atare.
