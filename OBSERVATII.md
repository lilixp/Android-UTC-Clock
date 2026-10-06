# Observații – UTC Radio Clock (versiunea 2.0)

Ce s-a hotărât pe parcurs, ce a rămas de verificat și ce urmează. Starea fazelor e în
[README.md](README.md). Actualizat: 1 octombrie 2026, după faza 4 (propagarea HF, N0NBH).

## Reguli de lucru

- O fază = un singur commit, cu mesajul cerut de Lilian. Fără push: îl face Lilian din Android Studio.
- Înainte de commit, pe PC: build Debug, build Release, toate testele, lint („No issues found”).
- După fiecare fază: raport, apoi oprire; faza următoare începe doar la cererea lui Lilian.
- Pe telefon (S24+, Wi-Fi debugging): instalare peste versiunea existentă (`adb install -r`), ca să
  rămână setările; testele instrumentate **șterg setările** (dezinstalează aplicația), deci se
  rulează doar când e nevoie și setările se salvează înainte și se pun la loc după.

## Decizii luate

| Data | Decizie |
|------|---------|
| 30.09.2026 | Setările rămân în **SharedPreferences** (fără DataStore), prin `SettingsRepository`. |
| 30.09.2026 | Ecran **Setări** separat (iconița din dreapta sus); navigare simplă cu un flag, fără bibliotecă, cât sunt doar două ecrane. |
| 30.09.2026 | Tema (ASPECT) mutată din dashboard în Setări; e o setare a întregii aplicații, citită de MainActivity din `SettingsViewModel`. |
| 30.09.2026 | Butoanele de temă fără bifă: cu textul mare al telefonului, bifa tăia „Întunecat”. |
| 30.09.2026 | Indicativ: litere, cifre și `/`, cel mult 15 caractere, implicit ER1PL. Locator: cel mult 8 litere și cifre, scris ca `KN46dw`, implicit gol. Se salvează automat. |
| 30.09.2026 | Ceasul: o singură sursă de timp, `TimeProvider` (`Clock.systemUTC()` + fusul telefonului, cerut la fiecare secundă); ticker aliniat la secundă, doar cât dashboard-ul e vizibil. |
| 30.09.2026 | Soarele: poziția = centrul locatorului Maidenhead; algoritm NOAA offline; orele rotunjite la minut; recalculare doar la zi nouă, fus nou sau locator nou (verificate o dată pe minut). |
| 30.09.2026 | Referințele pentru testele solare sunt momentele exacte calculate separat pe PC (bisecție pe formulele NOAA); biblioteca `astral` s-a dovedit mai puțin exactă (1¾ min la 65° N). |
| 30.09.2026 | Robolectric imită API 36 (cel mai nou suportat) și are nevoie de `--add-opens` pe JDK-ul nou; e documentat în `app/build.gradle.kts`. |
| 01.10.2026 | Propagarea: sursa este XML-ul N0NBH `https://www.hamqsl.com/solarxml.php` (SFI, K, A, benzile zi/noapte, ora datelor). Autorul îl oferă pentru alte programe, cere actualizare de cel mult o dată pe oră și mențiunea sursei; nu are licență formală și poate dispărea. |
| 01.10.2026 | Benzile: doar cele 3 niveluri N0NBH (Good/Fair/Poor → Bun/Mediu/Slab, verde/galben/roșu); nu 5 niveluri inventate. Pe dashboard: valoarea de zi sau de noapte după Soare la stație; fără locator, 06:00–18:00 ora locală (spus în dialog). |
| 01.10.2026 | SFI/K/A colorate (alegerea lui Lilian): K după scara NOAA (0–3 / 4 / ≥5), A după categoriile NOAA (<16 / 16–29 / ≥30), SFI după pragurile uzuale din v1 (≥120 / 90–119 / <90; NOAA nu are scală pentru SFI). |
| 01.10.2026 | K afișat ca în sursă, întreg („K 0”), fără zecimale inventate. |
| 01.10.2026 | Sub benzi doar „Actualizat … UTC” (câmpul `updated` din XML N0NBH); sursa N0NBH/HamQSL e în Setări → Despre aplicație și în dialogul benzilor. |
| 01.10.2026 | GPS (faza 5): se cer **ACCESS_FINE_LOCATION și ACCESS_COARSE_LOCATION** împreună (locatorul de 6 caractere are nevoie de precizie; utilizatorul poate alege totuși „aproximativă”); fără locație în fundal. |
| 01.10.2026 | Sursa poziției implicită: **Manual**; permisiunea se cere doar la alegerea „Automat (GPS)” sau din butonul cardului LOCAȚIE. În Automat, fără poziție GPS se folosește locatorul manual. |
| 01.10.2026 | Ultima poziție GPS salvată local în `position.xml` (rotunjită la ~100 m), **exclusă din backup**, ștearsă la trecerea pe Manual. Nu e trimisă nicăieri. |
| 01.10.2026 | Locatorul din GPS: 6 caractere, scris `KN46dw`. Coordonatele: 4 zecimale, virgulă în română, punct în engleză, N/S/E/W. |
| 01.10.2026 | Poziția se citește doar cu dashboard-ul pe ecran: la deschidere și revenire, apoi la 30 de minute; o poziție mai nouă de 10 minute nu mai pornește GPS-ul; o citire durează cel mult 30 s. Fără Google Play Services (LocationManager prin `LocationManagerCompat`). |
| 01.10.2026 | **Faza A — locatorul:** sunt valide doar locatorii de **4, 6 sau 8 caractere** (`KN46`, `KN46dw`, `KN46dw12`), verificați de `Maidenhead.toPosition` / `isValid` (singurul loc). `KN`, `KN4`, `KN46d`, `KN46dwx`, caracterele nepermise și textul gol sunt invalide. Setările păstrează textul cât e scris, dar un locator invalid nu devine poziție, QTH, insignă, sursă pentru Soare sau propagare; dashboard-ul spune „Locatorul „KN4” nu este valid.” |
| 01.10.2026 | **Faza A — GPS:** ordinea în `PositionRepository.locate()` este permisiune → locația pornită → abia apoi poziția recentă (regula de 10 minute rămâne). O poziție luată cu altă precizie (aproximativ ↔ exact) nu mai e folosită: se cere una nouă. |
| 01.10.2026 | **Faza A — Soarele:** fără poziție, în modul Automat, cardul SOARE spune motivul din `GpsStatus`: fără permisiune, locația oprită, se caută poziția, poziție indisponibilă. |
| 01.10.2026 | **Estimare offline pe 10 benzi** (lucrul lui Lilian din Android Studio, recuperat din Local History și integrat): regulile pe bandă păstrate; SFI și K vin numai din N0NBH (`PropagationState`, proaspăt sau cache), fără un al doilea depozit și fără valori implicite (SFI 100 / K 2 / A 7 din versiunea recuperată au fost scoase). Date lipsă → „Necunoscut” (`null`, nu `ConditionLevel.UNDEFINED`). Afișată sub datele N0NBH, cu titlul „Estimare offline · 10 benzi”. |
| 01.10.2026 | Zi / crepuscul / noapte la stație: o singură funcție, `SolarDay.phaseAt()`, pentru grupele N0NBH și pentru estimare. |
| 01.10.2026 | Explicații la SFI / K / A și la fiecare dintre cele 10 benzi (dialog la apăsare, starea în card). Textul pentru K urmează `IndexScales`: 4 activ, ≥ 5 furtună. |
| 01.10.2026 | **Redesign, etapa 1:** 4 tab-uri în ordinea **Ceas → Propagare → Soare → Locație** (alegerea lui Lilian, după importanță), Ceasul ecran de start; stația (indicativ, locator) în bara de sus; Înapoi duce la Ceas, apoi iese; Setările revin la tab-ul de plecare; tab-ul păstrat la rotire și la oprirea procesului. Fără Navigation Compose, fără dependențe noi, un singur ViewModel. Testat pe S24+ de Lilian. |
| 01.10.2026 | Etichetele tab-urilor: `autoSize` (se micșorează doar dacă nu încap). Pe S24+ la font 1,3, „Propagation” era tăiat (342,5 px în 338 px); verificarea e în testul instrumentat, pentru că Robolectric are alte fonturi. |
| 01.10.2026 | Rețea fără biblioteci noi: `HttpURLConnection` + `XmlPullParser` din Android; permisiunea `INTERNET`. Cache: ultimul XML valid în SharedPreferences (`propagation_cache`, în afara backup-ului). Descărcare doar cât dashboard-ul e pe ecran: cel mult o dată pe oră, după o eroare din nou peste 15 minute. |

## De verificat (încă nevăzut pe telefon)

- **Schimbarea fusului orar** al telefonului cu aplicația deschisă: acoperită de teste pe PC, dar
  nu încercată pe telefon (e o setare a sistemului; o face Lilian, dacă vrea).
- **Tema după o repornire completă** a aplicației (proces nou): verificată pe PC din două părți
  (reîncărcarea setării + aplicarea la recrearea activității) și o dată pe telefon în faza ASPECT.
- **Trecerea la ora de iarnă** pe 25 octombrie 2026 (04:00 → 03:00 la Chișinău): acoperită de
  teste; de privit ceasul și cardul SOARE în ziua aceea.
- **Miezul nopții local**: data locală sub ora LOCAL și recalcularea Soarelui pentru ziua nouă.
- **Propagarea fără Internet pe telefon** (mod avion): pe PC e testată (date salvate cu „Date
  neactualizate”, fără date „Date indisponibile”); pe telefon o poate încerca Lilian cu modul avion.
- **Trecerea benzilor de la zi la noapte** la apus (culorile se schimbă dacă N0NBH dă valori diferite
  ziua și noaptea): testată pe PC; de privit o dată pe telefon seara.

## Propuneri mici (nefăcute, așteaptă decizia lui Lilian)

- **Semnarea versiunii Release**: acum `app-release-unsigned.apk`; pentru distribuire (Google Play
  sau APK dat altora) trebuie o cheie de semnare, păstrată în afara depozitului.
- **Actualizare la cerere** (de ex. tragere în jos): ar trebui să respecte tot regula N0NBH de o oră.
  Cerută de Lilian după testarea fazei 5 (util la POTA / portabil, într-un loc nou): tragerea în jos
  actualizează tot: poziția GPS (fără regula de 10 minute), Soarele, datele N0NBH (tot cu limita
  de o oră) și tot ce depinde de poziție.
- **Alte date din fluxul N0NBH**, nefolosite acum: pete solare, raze X, vânt solar, câmpul magnetic,
  zgomot (signal noise), condiții VHF (aurora, E-skip), MUF.

## Din testarea fazei 5 pe S24+ (1 octombrie 2026)

Faza 5 a trecut 25 de verificări pe telefon, fără probleme funcționale critice: GPS exact și
aproximativ, locatorul, Manual, rezerva pe locator, poziția salvată, permisiunile, refuzul,
refuzul definitiv, locația oprită, Soarele și propagarea pe poziția GPS.

Observații (cele două corecții de logică sunt **rezolvate în Faza A**; indicatorul rămâne pentru redesign):

- **Ultima poziție vs. poziția actuală:** cu GPS-ul oprit se arată ultima poziție salvată
  („GPS · 10:44 · ±100 m”), fără să fie clar că e cea veche. De arătat, de exemplu, „GPS · ultima
  poziție · 10:44” sau un indicator separat, plus starea „GPS oprit — se folosește ultima poziție”.
- ~~**Cauza observației 10**~~ **Rezolvat în Faza A:** locația oprită e verificată înaintea poziției
  recente, deci „Locația telefonului este oprită.” apare și cu o poziție salvată de sub 10 minute.
- ~~**Observația 16**~~ **Rezolvat în Faza A:** după schimbarea aproximativ ↔ exact, poziția veche nu
  mai e folosită; la următoarea citire (de ex. revenirea în aplicație) se cere una nouă.
- **Actualizarea la 30 de minute** e conformă planului; tragerea în jos (mai sus) acoperă cazul
  în care se vrea o poziție nouă imediat.

## Redesign UI/UX (stabilit, pentru o etapă ulterioară)

- Etapa 1 (4 tab-uri, stația în bara de sus) e făcută; etapele 2–7 urmează, una câte una.
- **Indicatorul de concurs** (cifrele UTC roșii cât ține un concurs, ora LOCAL neschimbată) e o
  funcție **planificată pentru o etapă ulterioară**, nu pentru etapa 2 (decizia lui Lilian,
  1 octombrie 2026). Modelul (`Contest`, `ContestCalendar`) se decide atunci; sursa listei de
  concursuri se analizează separat, înainte de implementare. Acum nu există nicio listă de concursuri
  în cod și nu trebuie introdusă una.
- **Acasă** minimalist: data, `ER1PL / KN46dw`, UTC și LOCAL.
- **Soare**, **Locație** și **Propagare** în ecrane separate.
- Observațiile de mai sus despre GPS (indicatorul „ultima poziție”, „GPS oprit — se folosește ultima
  poziție”) și tragerea în jos se fac odată cu redesignul.
- Din testarea autonomă pe S24+ (`PHONE_TEST_REPORT.md`, `NEXT_STAGE_ANALYSIS.md`), tot pentru
  redesign: „Automatic (GPS)” pe două rânduri în engleză la font 1,3; indicativul de 15 caractere
  lângă dată; etichetele benzilor și „Întunecat” la limită; data lângă orele Soarelui care cad în altă
  zi (locator departe de fusul telefonului); eroarea afișată chiar la câmpul locator din Setări;
  peisaj; testarea la mai multe dimensiuni de font.

## Ecranul Propagare: panoul de detalii (2 octombrie 2026)

Decizii (cerința lui Lilian pentru redesignul ecranului Propagare):

- **Cardul de sus rămâne neschimbat:** SFI, K, A, cele 4 grupuri, „Actualizat … UTC”,
  „Estimare offline · 10 benzi” și cele 10 benzi, cu aceleași culori și niveluri. Titlul rămâne
  „PROPAGARE”; nu „Propagare HF”. N0NBH nu apare în ecranul principal; sursa e în Setări → Despre.
- **Fără dialoguri:** apăsarea unei căsuțe afișează explicația în panoul de sub card (doar contur,
  pe culoarea paginii). Fără OK, X, popup sau Snackbar. Explicația rămâne până la altă căsuță sau
  până la ieșirea din ecran; a doua apăsare pe aceeași căsuță o păstrează. La intrare:
  „Apasă pe un indice sau pe o bandă pentru detalii.”
- **Selecția:** un inel subțire chiar în afara căsuței, în culoarea textului temei. Căsuța își păstrează
  mărimea și culoarea. O singură selecție, ținută în UI (`rememberSaveable`, păstrată la rotire);
  fără ViewModel nou. Panoul citește aceeași stare ca restul ecranului, deci urmează orice actualizare
  N0NBH fără ticker sau flux nou.
- **SFI, K, A:** valoarea mare, în culoarea nivelului; titlul („Indice K”); nivelul de acum;
  explicația; „Valori orientative” = exact scara `IndexScales` care colorează căsuțele (pragurile au
  devenit constante publice, logica e aceeași). K: 0–3 liniștit, 4 activ, ≥ 5 furtună.
- **Grupuri:** nivelul de acum, zi/noapte la stație, ziua și noaptea (N0NBH), „fără locator, după ceas”.
- **Benzi:** estimarea din `OfflinePropagationCalculator`, faza zilei (`SolarDay.phaseAt`), motivul
  pentru „Necunoscut” (fără poziție / lipsesc SFI sau K), explicația benzii.
- **Texte schimbate:** explicația lui K nu mai repetă scara, care e acum în „Valori orientative”;
  „Estimare offline…” și „Lipsesc SFI sau indicele K…” nu mai pomenesc N0NBH.
- **Font 1,3 pe S24+:** SFI, K și A au nevoie de o derulare scurtă (~50 dp) ca să se vadă și
  „Valori orientative”. Nimic nu e tăiat; grupurile și benzile încap fără derulare.

- **Fără „Acum e zi/noapte la stație.”** (decizia lui Lilian, 2 octombrie 2026): nici la benzi, nici
  la grupuri. La grupuri, tabelul Ziua/Noaptea rămâne, iar rândul momentului (zi sau noapte la
  stație) e îngroșat. La benzi rămân doar motivele pentru „Necunoscut” (fără poziție / lipsesc SFI
  sau K).
- **Idee pentru o etapă viitoare, neimplementată: banda de 6 m (50 MHz).** Tratată separat de
  cele 10 benzi HF și afișată contextual doar când există condiții relevante de propagare pe 6 m.
  Sursa și regula se decid atunci; acum nu există nimic pentru 6 m în cod.

## Modificări făcute de Claude Code (6 octombrie 2026)

Două ajustări de design cerute de Lilian, făcute de **Claude Code** (nu de ChatGPT Codex), peste
lucrul existent:

- **Propagare, tabelul unui grup (Bandă | Zi | Noapte):** celulele sunt din nou **doar culoare**, fără
  „Bun/Mediu/Slab” și fără „—” (revenire la decizia din 2 octombrie). Celula neutră înseamnă
  „Necunoscut”. TalkBack citește fiecare celulă („30 m, Zi: Bun”). Celula are minimum 24 dp
  (era 28 dp cu text). Fișier: `PropagationCard.kt` (`LevelCell`).
- **Soare, bara „Ziua la stație”:** ziua, momentul curent e un **soare mic stilizat** (disc și 8 raze,
  contur închis), desenat în interiorul barei. În crepuscul și noaptea rămâne liniuța albă. Fără lună.
  Faza momentului curent vine din segmentele barei (`SunPresentation.markerPhase`). Fișiere:
  `SunScreen.kt` (`DayBand`, `drawSun`), `SunPresentation.kt`.
- Teste: `PropagationCardTest` (celule fără text, cu descriere) și `SunLayoutTest`
  (`marker_sunByDay_whiteLineAtTwilightAndAtNight`).
- Testul instrumentat `dashboardShowsClocksAndPlaceholders` caută acum „Locația nu este disponibilă.”
  în conținutul ecranului Soare (noul ecran Soare îl pune într-un card, nu lângă titlu).

Decizii ale lui Lilian în aceeași zi (notate de Claude Code):

- **Grayline: renunțăm.** Bara „Ziua la stație” arată deja crepusculul (grayline).
- **Calendarul de concursuri: renunțăm**, ca să nu încărcăm ecranul (și indicatorul de concurs).
- **Tragerea în jos (Etapa 6): renunțăm.** Datele N0NBH rămân actualizate o dată pe oră (limita
  cerută de N0NBH; SFI se schimbă orar, restul la 3 ore). Fără Internet se văd ultimele date salvate,
  ca acum. Propunerea „Actualizare la cerere” de mai sus nu se mai face.
- **Etapa 4 (Locație)** este următoarea.

## Etapa 4: ecranul Locație (6 octombrie 2026, făcută de Claude Code)

Varianta A aleasă de Lilian, implementată de **Claude Code**:

- **Sus:** locatorul mare; pe GPS și locatorul de 8 caractere (VHF și peste). Sursa poziției cu
  aceleași cuvinte ca pe ecranul Soare: „Locator manual / Locator de rezervă · centrul pătratului”,
  „Poziție din GPS · 11:14 UTC”, „Ultima poziție GPS disponibilă · … UTC” (marcată, ca să nu fie
  luată drept una nouă: observația din testarea fazei 5). Ora poziției e în UTC, lângă sursă, nu
  într-o placă separată (Lilian nu era sigură că merită o placă).
- **Comutatorul Manual | Automat (GPS)** pe ecran: aceeași setare ca în Setări, cu aceeași cerere de
  permisiune la trecerea pe Automat.
- **Portabil:** „La 2,6 km de KN46dw · azimut spre casă 248°”, doar pe GPS, de la 1 km de locatorul
  din Setări (azimutul e cel de întors antena spre casă).
- **Plăci:** Latitudine, Longitudine; pe GPS Altitudine (deasupra nivelului mării când telefonul o dă,
  altfel înălțimea GPS) și Precizie (GPS exact / aproximativ); Declinația magnetică (modelul magnetic
  mondial din Android, fără Internet), pentru orientarea antenei cu busola.
- Altitudinea se păstrează și cu ultima poziție salvată. Fără surse noi online și fără biblioteci noi.
- Lăsate pentru mai târziu (variantele B și C): coordonate în grade-minute-secunde, calculatorul de
  azimut către un locator, zonele CQ/ITU, regiunea IARU, DXCC (acestea din urmă cer date externe).

## Faza A — corecții funcționale (1 octombrie 2026)

Rezolvate (detalii în `PHASE_A_REPORT.md`):

1. **Locatorul:** doar 4, 6 sau 8 caractere; `KN`, `KN4`, `KN46d` etc. nu mai sunt poziție, QTH sau
   insignă; dashboard-ul spune că locatorul nu e valid (T7.5 din testarea autonomă).
2. **GPS, locația oprită:** verificată înaintea poziției recente (observația 10 din testarea manuală).
3. **GPS, aproximativ ↔ exact:** poziția cu cealaltă precizie e ignorată și se cere una nouă
   (observația 16).
4. **Cardul SOARE în modul Automat:** motivul real (fără permisiune / locația oprită / se caută /
   indisponibilă), nu mereu „Permite accesul la locație” (T8.5).

De verificat pe telefon, cu Lilian: locația oprită cu o poziție recentă, schimbarea aproximativ ↔
exact, `KN` / `KN4` în Setări, mesajul SOARE cu locația oprită.

## Estimarea offline pe 10 benzi (1 octombrie 2026)

Recuperată din Local History (lucrul din Android Studio, pierdut la un rollback accidental la 13:10)
și integrată în arhitectura existentă. Ce nu s-a integrat rămâne în
`C:\Users\Lilian\AndroidStudioProjects\UTCRadioClock-recuperare-1310\` (vezi `RECUPERARE.md` acolo):
`SolarIndicesRepository` (dubla N0NBH), `ConditionLevel.UNDEFINED` (dubla `null`), starea dialogurilor
în ViewModel și tot începutul de redesign (tab-uri, `ClockScreen` / `SunScreen` / `LocationScreen` /
`PropagationScreen`, iconițe, pull-to-refresh, „ultima poziție salvată”) — material pentru Faza B.

Tot acum: testul instrumentat `dashboardShowsClocksAndPlaceholders` caută „Locația nu este
disponibilă.” doar în cardul SOARE (din faza 5 textul apare și în cardul LOCAȚIE).

## Ce urmează (fazele următoare, în ordinea din cerințele inițiale)

Făcute: SFI, K, A și propagarea HF pe benzi (faza 4, de la N0NBH); GPS și locația (faza 5).

1. **Widget** pe ecranul principal.
2. **Notificări** (de ex. furtună geomagnetică, K ≥ 5).

Rețeaua există deja (faza 4): `HttpClient` + cache + teste fără Internet; o sursă nouă de date
online se poate adăuga la fel.
