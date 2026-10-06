# Analiza după testarea Fazei 5

Bazată pe `PHONE_TEST_REPORT.md` (S24+, 1 octombrie 2026, commit `dfc087d`, cod `38e55a4`) și pe
citirea codului. Nimic nu a fost modificat; documentul doar organizează pașii următori.

## 1. Rezultatul testării

| | |
|---|---|
| Teste în listă | 92 |
| Executate | 81 |
| PASS | 73 |
| FAIL | 1 (cosmetic, necritic) |
| OBSERVATION | 7 |
| SKIPPED | 11 |
| Crash / ANR / excepții în Logcat | 0 / 0 / 0 |

Confirmate pe telefon:
- ceasul UTC/LOCAL: aceeași secundă în ambele, fără derivă;
- Setările și persistența lor după repornire;
- română și engleză;
- cele trei teme;
- poziția manuală;
- Soarele, identic la minut cu o referință independentă pentru 6 locatori, inclusiv noaptea polară;
- propagarea, identică cu cache-ul N0NBH;
- zi/noapte după Soarele stației;
- fundal, procesul oprit de sistem, stresul de navigare;
- coerența Locație → Soare → Propagare.

**Concluzie:** nicio problemă care să blocheze lucrul mai departe. Două observații sunt totuși
probleme funcționale reale, mici (secțiunea 2), care se pot corecta înainte de redesign.

## 2. Probleme funcționale reale

### 2.1 Locatorul invalid e folosit ca QTH (T7.5, T4.12)

**Problemă:** `KN4` sau `SA00` sunt salvate și apar ca locator valid. Apar în insigna de sus și ca
„QTH: KN4”. Cardul SOARE spune corect „nu este valid”, dar cardul LOCAȚIE spune doar „Locația nu este
disponibilă.”, iar câmpul din Setări nu semnalează nimic.

**Fișiere / clase:**
- `domain/model/Models.kt` → `StationIdentity.normalizeLocator`;
- `domain/location/Maidenhead.kt` → `toPosition`;
- `data/location/PositionRepository.kt` → `resolve()`;
- `ui/settings/SettingsScreen.kt` (câmpul locator).

**Cauza (verificată în cod):**
- `normalizeLocator` doar curăță textul: litere și cifre, maximum 8 caractere, primele 4 cu majuscule.
  Nu verifică structura. E o alegere documentată din faza 1 („no full locator check”).
- Validarea reală există doar în `Maidenhead.toPosition`, care întoarce `null` pentru un locator invalid.
- `PositionRepository.resolve()` pune însă în `StationPosition.locator` textul introdus, chiar și când
  `toPosition` a dat `null`:

  ```kotlin
  locator = station.locator.ifEmpty { null }
  ```

  De acolo ajunge în insignă și la QTH.
- Câmpul se salvează la fiecare literă tastată. Valorile intermediare („K”, „KN4”) sunt deci salvate
  normal cât timp scrii; pentru ele nu e nevoie să blocăm salvarea.

**Regula corectă folosită acum** (`Maidenhead.toPosition`, același tabel ca `fromPosition`):

| perechea | caractere | interval |
|---|---|---|
| 1 | litere | A–R |
| 2 | cifre | 0–9 |
| 3 | litere | A–X |
| 4 | cifre | 0–9 |

- Lungimea e pară, 2–8 caractere; literele mari și mici nu contează.
- **Nepotrivire găsită:** codul acceptă și **2 caractere** (`KN` = un câmp de 20° × 10°, centrul la
  45° N, 30° E). Indicația din Setări spune însă „4, 6 sau 8 caractere”.
  - Cu „KN”, poziția poate fi la ~250 km de stație.
  - Orele Soarelui pot ieși greșite cu ~5–10 minute.
  - Nu e o eroare a calculului, dar contrazice indicația.
- `KN4` (lungime impară) este respins corect: nu există acceptare accidentală a unui locator incomplet
  de 3, 5 sau 7 caractere.

**Soluția propusă (de decis):**
1. O singură funcție de validare, de exemplu `Maidenhead.isValid(locator)`, folosită de repository,
   de ViewModel și de Setări.
2. `resolve()` să pună în `locator` doar un locator valid. Unul invalid merge într-un câmp separat,
   ca ecranul să poată spune „locatorul KN4 nu este valid” și în cardul LOCAȚIE.
3. În Setări, câmpul arată eroarea (`isError` + text) cât timp valoarea e invalidă. Salvarea rămâne
   automată, ca acum.
4. **Decizia ta:** acceptăm 2 caractere (și schimbăm indicația) sau cerem minimum 4 (și respingem „KN”)?

**Impact:** mic. Afectează repository, ViewModel, Setări, textele și testele. Nu atinge calculul solar
sau N0NBH.

### 2.2 Mesajul din cardul SOARE în modul Automat nu urmează starea GPS (T8.5)

**Problemă:** în modul Automat, cu locația telefonului oprită și fără locator, cardul SOARE spune
„Permite accesul la locație sau introdu locatorul…”. Permisiunea **este** însă acordată; problema e
locația oprită. Cardul LOCAȚIE spune corect „Locația telefonului este oprită.”

**Fișiere / clase:**
- `ui/dashboard/DashboardViewModel.kt` → fluxul `sun` și `sunState()`;
- `ui/dashboard/DashboardUiState.kt` → `SunStatus.NO_POSITION`;
- `ui/dashboard/DashboardScreen.kt` → `SunCard`;
- textul `sun_no_position`.

**Cauza (verificată în cod):**
- Fluxul `sun` combină doar ziua locală, poziția, locatorul și sursa poziției, nu și starea GPS
  (`StationPosition.gps`).
- `sunState()` alege `NO_POSITION` pentru orice caz „Automat fără poziție și fără locator”, cu un
  singur text, scris pentru cazul fără permisiune.

**Stări de diferențiat:** repository-ul le are deja în `GpsStatus`.

| Starea | Ce ar trebui să spună cardul SOARE |
|---|---|
| `NO_PERMISSION` (lipsă sau refuzată) | Permite accesul la locație sau introdu locatorul |
| refuz definitiv („Nu mai întreba”) | Starea e doar în `MainActivity` (`locationBlocked`), nu în repository. Cardul SOARE poate trimite la cardul LOCAȚIE, care are deja butonul spre setările aplicației |
| `LOCATION_OFF` | Pornește locația telefonului sau introdu locatorul |
| `SEARCHING` | Se caută poziția… |
| `UNAVAILABLE` | Poziția nu este disponibilă momentan; poți introduce locatorul |

**Soluția propusă:**
- `sun` folosește `positions.state` în loc de `positions.position`, deci primește și `gps`.
- `SunUiState` primește motivul lipsei poziției: fie un `GpsStatus`, fie câteva valori noi în
  `SunStatus`.
- Câte un text pentru fiecare stare, în română și engleză.
- Calculul solar rămâne neschimbat.

**Impact:** mic. Afectează ViewModel, starea UI, textele și testele. Trebuie păstrată regula ca Soarele
să fie recalculat doar când se schimbă poziția sau ziua, nu și la schimbarea mesajului.

### 2.3 Corecții GPS deja cunoscute (din testarea manuală a fazei 5)

Sunt notate deja în `OBSERVATII.md`, nu sunt bug-uri noi. Le trec aici doar pentru că sunt logică în
repository, nu UI:

- **Locația ON/OFF verificată înainte de reutilizarea unei poziții recente.**
  - În `PositionRepository.locate()`, verificarea „poziție mai nouă de 10 minute → OK” vine înaintea
    verificării `isLocationEnabled()`.
  - Efect: o repornire cu locația oprită nu arată mesajul.
- **La schimbarea aproximativ ↔ exact, poziția veche se ignoră.**
  - `LocationFix.approximate` e deja salvat.
  - Dacă nu se potrivește cu permisiunea actuală, poziția nu se consideră recentă.

**De decis:** le facem în faza A (sunt mici și independente de aspect) sau le lăsăm, cum ai cerut
inițial, pentru redesign? Partea vizibilă (indicatorul „ultima poziție”, „GPS oprit — se folosește
ultima poziție”) rămâne oricum în faza B.

## 3. Probleme UI/UX (pentru redesign)

Nu propun reparații temporare în UI-ul actual.

| ID | Problemă | Cauza în cod | De avut în vedere la redesign |
|---|---|---|---|
| T5.4 (FAIL cosmetic) | În engleză, la font 1,3, „Automatic (GPS)” se rupe pe două rânduri, iar segmentul iese mai înalt decât „Manual” | `SettingsScreen.PositionSourceSelector`: `SegmentedButton` cu text fără `maxLines`, două segmente egale pe lățimea ecranului | Etichete scurte sau alt tip de control; testare la font 1,3–2,0 în ambele limbi |
| T2.6 | Etichetele benzilor („80-40m”) ocupă toată lățimea butonului la font 1,3 | `PropagationCard`: 4 butoane egale pe un rând | La un font mai mare: alt aranjament (2 × 2, listă) sau text care se micșorează |
| T2.4 | Indicativul de 15 caractere încape, dar data rămâne cu ~640 px; o lună lungă („30 septembrie 2026”) ar putea trece pe două rânduri | `DashboardScreen`: rândul dată + `StationBadge`, data cu `weight(1f)` | Antetul „data + ER1PL / KN46dw” e deja în planul de redesign |
| Tema / ASPECT | „Întunecat” încape la limită în selectorul de temă | Aceeași cauză ca T5.4 | Același control ca la T5.4 |
| T10.4 | Orele Soarelui pentru un locator departe de fusul telefonului cad în altă zi calendaristică și nu au dată (vezi 4.3) | `TimeFormatter.eventTime` afișează doar `HH:mm` | Data lângă ore care nu sunt în ziua telefonului, în ecranul Solar separat |
| Cunoscute (OBSERVATII) | Indicatorul „ultima poziție GPS” și „GPS oprit — se folosește ultima poziție”; pull-to-refresh | – | Deja în planul de redesign |

## 4. Comportamente intenționate (nu sunt bug-uri)

### 4.1 Reutilizarea poziției GPS mai noi de 10 minute
`PositionRepository.RECENT`, ca să nu pornească GPS-ul inutil.

### 4.2 Actualizarea GPS la 30 de minute și N0NBH la o oră
`REFRESH_INTERVAL`. Testul a confirmat: peste 10 reporniri, nicio descărcare N0NBH înainte de oră.

### 4.3 Sydney: Soarele pentru altă zi calendaristică
`SolarCalculator.calculate()` calculează, intenționat și documentat, **ziua solară a cărei amiază cade
pe data telefonului**:

```kotlin
// Take the UTC day whose solar noon is on the local date
```

- Pentru Sydney, amiaza solară e la 04:45 ora Chișinăului pe 1 octombrie. Răsăritul acelei zile e
  deci în seara de 30 septembrie (22:33), iar apusul pe 1 octombrie (10:58).
- Valorile au fost verificate cu referința independentă: răsărit 30 sept. 22:32:52, apus 1 oct.
  10:57:36. Ambele sunt corecte.
- Același principiu ține împreună răsăritul și apusul aceleiași zile solare. Exemplu: un apus după
  miezul nopții, în nord, vara. Există și teste pentru Sydney și pentru noaptea albă.
- **Nu e o problemă de calcul. Lipsește doar afișarea datei** (secțiunea 3).
- Situația nu apare cu GPS (poziția e în fusul telefonului) și nici cu un locator din aceeași regiune.

### 4.4 Lipsa unei setări de limbă în aplicație
Aplicația urmează limba telefonului sau limba aleasă pentru ea în Android (per-app language).
Testul a confirmat comutarea live și persistența.

### 4.5 `position.xml` gol după trecerea pe Manual
Decizia fazei 5: poziția salvată se șterge.

### 4.6 Un locator de 2 caractere e acceptat
Este intenționat în `Maidenhead`, dar indicația din Setări nu îl menționează (vezi 2.1).

### 4.7 Diferența de 1 s între UTC și LOCAL într-o citire
Artefact al citirii uiautomator, nod cu nod. Capturile de ecran o infirmă: ambele ore vin din aceeași
citire (`ClockReading`).

### 4.8 Lista derulată o dată fără comandă (T2.7)
Nu s-a mai reprodus. În cod nu există nicio derulare programatică: `LazyColumn` folosește starea
implicită și nimic nu apelează `scrollTo`. Cauza cea mai probabilă e o atingere accidentală.
De urmărit, dar nu necesită acțiune.

## 5. Teste SKIPPED

Niciunul nu e bug. Toate cer setări de sistem sau prezența ta.

| ID | Test | Motiv | De testat manual? |
|---|---|---|---|
| T8.8 | Poziție GPS exactă | Cere pornirea locației | Deja făcut manual. **Da**, după corecțiile din 2.3 |
| T8.9 | Locație aproximativă | Cere schimbarea permisiunii | Deja făcut manual. **Da**, după corecția aproximativ ↔ exact |
| T8.10 | Ultima poziție după repornire | Cere o poziție GPS | **Da**, împreună cu T8.8 (verifică și 2.3, ordinea ON/OFF) |
| T9.2 | Refuz / revocare / refuz definitiv / revenire din setări | Cere setările Android | Deja făcut manual (testele 17–20). Doar dacă se schimbă fluxul permisiunii |
| T11.8 | Actualizarea orară N0NBH | Oprit înainte de ora programată | **Da**, ușor: aplicația deschisă peste ora următoare. Pe PC e testat cu ceas virtual |
| T11.9 | Fără Internet (cache) | Cere modul avion | **Da**: un test scurt cu modul avion („Date neactualizate…”) |
| T13.4 | Ecran stins / aprins | Cere deblocarea | **Da**, util: aplicația e un ceas care stă pe masă |
| T2.9 | Orientare peisaj | Cere rotirea ecranului | **Da**, înainte de redesign: arată ce trebuie gândit pentru peisaj |
| T6.5 | Tema Sistem urmează modul întunecat al telefonului | Cere modul întunecat al sistemului | Opțional (testat pe PC). Merită la redesign |
| T2.8 | Schimbarea datei UTC | Cere schimbarea orei | Nu e nevoie de schimbat ora: se vede natural la 03:00 ora locală (00:00 UTC). Testat pe PC |
| T3.5 | UTC independent de fus | Cere schimbarea fusului | Opțional, testat pe PC. Trecerea la ora de iarnă (25 octombrie 2026) e un test natural |

## 6. Propunere de ordine a lucrului

### FAZA A — corecții funcționale mici (înainte de redesign)

1. Validarea locatorului (2.1). Decizia ta înainte: 2 caractere da sau nu.
2. Mesajul cardului SOARE după starea GPS (2.2).
3. Opțional, după decizia ta, corecțiile GPS cunoscute (2.3): ordinea ON/OFF și schimbarea
   aproximativ ↔ exact.

Fiecare cu teste pe PC, apoi o verificare scurtă pe telefon, cu tine (T8.8–T8.10, T11.9, T13.4).
Un singur commit.

### FAZA B — redesign UI/UX

**Planul deja stabilit:**
- Acasă minimalist: data, `ER1PL / KN46dw`, UTC și LOCAL.
- Soare, Locație și Propagare HF în ecrane separate.

**De inclus:**
- controalele care nu se rup la font mare: T5.4, T2.6, tema;
- antetul cu indicativ lung (T2.4);
- data la orele Soarelui din altă zi (T10.4);
- indicatorul „ultima poziție” / „GPS oprit”;
- pull-to-refresh;
- orientarea peisaj (T2.9);
- testarea la mai multe dimensiuni de font, în ambele limbi și în ambele teme.

### FAZA C — funcții noi (fără ordine decisă)

Din `OBSERVATII.md` și din cerințele inițiale:
- widget;
- notificări (de exemplu K ≥ 5);
- explicații la SFI / K / A;
- alte date N0NBH (pete solare, raze X, vânt solar, MUF, condiții VHF);
- semnarea versiunii Release (pentru distribuire).

## 7. Riscuri / lucruri de verificat

- **Validarea locatorului schimbă un comportament salvat.** Dacă se decide minimum 4 caractere, un
  locator de 2 caractere deja salvat devine invalid. Probabilitatea e mică, dar mesajul trebuie să fie
  clar.
- **Fluxul `sun` și recalcularea.** La 2.2, legarea cardului SOARE de starea GPS nu trebuie să
  declanșeze recalculări la fiecare schimbare de stare. Testul existent
  `sunIsNotRecalculatedEverySecond` și unul nou pentru schimbarea stării GPS fără schimbarea poziției.
- **Documentație nealiniată:**
  - indicația din Setări („4, 6 sau 8”) față de cod (2–8);
  - KDoc-ul `StationIdentity` („2, 4, 6 or 8 characters”).

  De aliniat împreună cu 2.1.
- **Lățimea controalelor:** problemele de lățime apar deja la font 1,3, deci și alți utilizatori le pot
  avea. Redesignul ar trebui verificat sistematic la mai multe dimensiuni de font (Robolectric acceptă
  `fontScale` în `@Config`).
