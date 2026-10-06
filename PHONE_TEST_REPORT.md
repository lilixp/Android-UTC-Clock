# Raport testare pe telefon – UTC Radio Clock

| | |
|---|---|
| **1. Dispozitiv** | Samsung Galaxy S24+ (SM-S926B, `e2s`), 1440 × 3120, densitate 600, **font 1,3** (setarea telefonului) |
| **2. Android** | 16 (API 36), build `BP4A.251205.006.S926BXXSHDZH3` |
| **3. Aplicația** | `io.github.lilixp.utcradioclock` 2.0.0 (versionCode 1), **Debug**, construită local și instalată cu `adb install -r` |
| **4. Commit testat** | `dfc087d` (cod = `38e55a4`, faza 5; `dfc087d` modifică doar documentația) |
| **5. Data** | 1 octombrie 2026, 11:14–11:47 ora Chișinăului (08:14–08:47 UTC) |
| **Mod de lucru** | Autonom prin ADB (uiautomator pentru texte, capturi de ecran, logcat, `run-as` pentru SharedPreferences) |
| **Starea telefonului** | Locația telefonului **oprită** pe toată durata; permisiunea de locație deja acordată (exactă + aproximativă); limba sistemului română; mod luminos; fus `Europe/Chisinau` (UTC+3) |

**Fără modificări de cod, fără commit-uri.** Setările aplicației au fost salvate înainte și readuse la
final la valorile tale (ER1PL, KN46dw, Manual, Luminos). Limba aplicației a fost readusă la limba sistemului.

## Rezumat

| | Număr |
|---|---|
| **6. Teste în listă** | **92** (81 executate, 11 SKIPPED) |
| **7. PASS** | 73 |
| **8. FAIL** | 1 |
| **9. OBSERVATION** | 7 |
| **10. SKIPPED** | 11 |

**Niciun FAIL critic.** Niciun crash, niciun ANR, nicio excepție a aplicației în Logcat.
Singurul FAIL este cosmetic (un buton care se rupe pe două rânduri în engleză, cu fontul mărit).

De ce SKIPPED: testele marcate astfel cer schimbarea unor setări de sistem ale telefonului (pornirea
locației, mod avion, revocarea permisiunii din setările Android, ora/fusul/data, modul întunecat al
sistemului, rotirea ecranului) sau deblocarea telefonului. Nu le-am schimbat; cele de GPS și permisiuni
le-ai verificat deja manual (raportul fazei 5, testele 1–25). Excepție: T11.8 (actualizarea orară
N0NBH, programată la 08:58:39 UTC); așteptarea a fost oprită la 08:47 UTC, când ai cerut raportul.

---

## FAILURES

### T5.4 – „Automatic (GPS)” se rupe pe două rânduri în engleză (minor, cosmetic)

- **Reproducere:** limba aplicației engleză → Settings → STATION → Station position. Fontul telefonului
  la 1,3 (setarea actuală a S24+).
- **Așteptat:** cele două segmente „Manual” și „Automatic (GPS)” pe un rând, de aceeași înălțime, text centrat.
- **Real:** „Automatic (GPS)” e scris pe două rânduri („Automatic” / „(GPS)”), segmentul e mai înalt
  decât „Manual”, depășește conturul rândului și textul nu mai e centrat. În română („Automat (GPS)”)
  încape pe un rând.
- **Captură:** `pt/shots/z_en_position_selector.png` (detaliu), `pt/shots/t5_en_settings_top.png`.
- **Logcat:** nimic (problemă de afișare).
- **Reproductibil:** da, de fiecare dată în engleză cu font 1,3.

---

## OBSERVATIONS

Nu sunt bug-uri critice. Observațiile deja cunoscute despre GPS (ultima poziție, GPS oprit, regula de
10 minute, aproximativ ↔ exact, pull-to-refresh, redesign) **nu** sunt repetate aici.

| ID | Observație | Captură |
|---|---|---|
| T2.4 | Indicativul de 15 caractere (`ER/YO3ABCDEF/QR`) încape, dar data din stânga rămâne cu ~640 px. „1 octombrie 2026” încape; o lună cu nume mai lung („30 septembrie 2026”) ar putea trece pe două rânduri. Nereprodus (ar cere schimbarea datei). | `t2_long_callsign.png` |
| T2.6 | Etichetele benzilor („80-40m”, „30-20m”…) ocupă toată lățimea butonului la font 1,3. Încap acum, dar la un font mai mare ar fi tăiate sau rupte (aceeași cauză ca T5.4). | `t2_scroll2.png` |
| T2.7 | O singură dată, la început, lista dashboard-ului era derulată cu ~476 px fără nicio comandă de derulare de la mine. Nu s-a mai reprodus (urmărit 24 s și pe tot restul testării). Probabil o atingere accidentală a ecranului. | – |
| T7.5 | Un locator invalid (`KN4`, `SA00`) apare în insigna de sus și la „QTH: KN4” ca un locator obișnuit. Cardul SOARE spune clar „Locatorul „KN4” nu este valid.”, dar cardul LOCAȚIE spune doar „Locația nu este disponibilă.”, fără motiv. | `t7_invalid.png` |
| T4.12 | În Setări, un locator invalid nu e semnalat la câmp (rămâne doar indicația generală „4, 6 sau 8 caractere”); eroarea se vede abia pe dashboard. | – |
| T8.5 | Automat + locația telefonului oprită + fără locator: cardul SOARE spune „Permite accesul la locație sau introdu locatorul…”, deși permisiunea **este** acordată și problema reală e locația oprită (cardul LOCAȚIE spune corect „Locația telefonului este oprită.”). | `t8_auto_nothing.png` |
| T10.4 | Pentru un locator departe de fusul telefonului (Sydney, QF56od) aplicația arată corect ziua solară de la Sydney: răsărit 22:33 (30 sept., ora Chișinăului), apus 10:58 (1 oct.). Orele nu au dată, deci „Răsărit 22:33 / Apus 10:58” poate deruta. Valorile sunt corecte; nu apare cu GPS sau un locator din aceeași zonă. | `t10_sydney.png` |

---

## PASSED – rezumat

- **Build / instalare / pornire:** Debug fără erori sau avertismente; instalare cu păstrarea datelor;
  pornire la rece 598 ms; fără crash; Logcat curat.
- **Ceas:** UTC egal cu ceasul telefonului; LOCAL = UTC + 3 h (Europe/Chisinau, ora de vară);
  12 cadre consecutive de ecran: secunde care avansează una câte una, **aceeași secundă** la UTC și LOCAL;
  trecerea minutului observată. (O citire uiautomator a dat o dată 1 s diferență între UTC și LOCAL:
  efect al citirii nod cu nod, infirmat de capturile de ecran.)
- **Setări:** indicativul (litere mari, caractere nepermise eliminate, maximum 15, gol → ascuns din
  insignă), locatorul (`kn46DW` → `KN46dw`), sursa poziției, tema; toate păstrate la ieșire și după
  repornirea aplicației; secțiunea Despre aplicație completă; starea butoanelor expusă pentru accesibilitate.
- **Română / engleză:** toate ecranele traduse (dashboard, dialogul benzilor, Setări, Despre, mesajele
  GPS), fără texte rămase în cealaltă limbă, fără chei interne; coordonate cu virgulă în română și punct
  în engleză; „1 October 2026”; limba păstrată după repornire; schimbarea limbii cu Setările deschise
  păstrează ecranul.
- **Teme:** Întunecat, Sistem (telefon luminos → luminos), Luminos; contrast bun, iconițele barei de
  stare urmează tema; schimbare imediată; păstrate după repornire.
- **Poziție manuală:** KN46dw, KN46, KN46dw15, IO91wm, QF56od, RR99; coordonatele centrului corecte;
  locator invalid și gol tratate fără crash.
- **Soare:** toate valorile comparate cu o referință independentă (bisecție pe formulele NOAA, la minut):
  KN46dw 07:05 / 18:47 / 12:57 / 11h 42m / 06:35–19:17, KN46 07:02 / 18:44, IO91wm 09:01 / 20:38,
  QF56od (ziua Sydney) 22:33 / 10:58, RR99 noapte polară fără răsărit și fără crepuscul civil. Fără NaN,
  fără valori goale neașteptate.
- **GPS (cât s-a putut fără să pornesc locația):** Automat cu permisiunea deja acordată nu mai arată
  dialogul; locația oprită → locatorul manual + „Locația telefonului este oprită.” + „Pornește locația”,
  care deschide pagina Locație din Android (Oprit), iar Înapoi revine la aceeași stare; Automat fără
  locator → liniuțe și mesaje; `position.xml` gol după trecerea pe Manual (ștergerea poziției salvate);
  comutări rapide Manual/Automat fără crash.
- **Propagare:** SFI 93, K 0, A 3 și cele 4 benzi identice cu cache-ul N0NBH de pe telefon (valorile
  de zi); culorile; „Actualizat 07:58 UTC” = `updated` din XML; dialogul benzilor (OK și Înapoi);
  zi/noapte după Soarele stației (zi la KN46dw, noapte la RR99 și QF56od); fără locator, regula 06–18;
  limita de o oră respectată (peste 10 reporniri, nicio descărcare nouă înainte de oră).
- **Fundal / prim-plan:** Home > 5 s și revenire (ceasul arată ora curentă), Recente, procesul oprit de
  sistem în fundal → aplicația revine direct în Setări.
- **Navigare și stres:** Setări ↔ dashboard (săgeata și butonul Înapoi), Înapoi pe dashboard iese în
  launcher; 20 de deschideri/închideri rapide ale Setărilor, 24 de schimbări rapide de temă, 16 comutări
  Manual/Automat, 10 schimbări rapide de limbă: fără crash, același proces, starea corectă.
- **Coerență Locație → Soare → Propagare:** la fiecare locator, coordonatele, orele Soarelui și ziua/noaptea
  benzilor corespund aceleiași poziții.
- **Logcat:** nicio eroare a aplicației; doar mesaje obișnuite ale sistemului (ashmem, proprietăți
  vendor, uiautomator, PackageManager la instalare).

---

## Toate testele

| ID | Descriere | Acțiuni | Rezultat așteptat | Rezultat real | Status |
|---|---|---|---|---|---|
| T1.1 | Build Debug | `gradlew assembleDebug` | Fără erori/avertismente | BUILD SUCCESSFUL, fără avertismente | PASS |
| T1.2 | Instalare | `adb install -r` (păstrează datele) | Success | Success; setările tale păstrate | PASS |
| T1.3 | Pornire la rece | `am start -W` | Pornește | COLD, 598 ms | PASS |
| T1.4 | Fără crash la pornire | proces + ecran | Proces viu | Proces viu, dashboard afișat | PASS |
| T1.5 | Logcat la pornire | logcat --pid | Fără excepții ale aplicației | Doar ashmem / libc vendor (sistem) | PASS |
| T1.6 | Ecran principal complet | uiautomator + captură | Data, insignă, UTC, LOCAL, carduri | Toate prezente | PASS |
| T2.1 | Antet: dată, indicativ, locator | dashboard | 1 octombrie 2026, ER1PL, KN46dw | Corect | PASS |
| T2.2 | Cardurile UTC și LOCAL | dashboard | Ore + fus | 08:15:27 / 11:15:27, Europe/Chisinau · UTC+03:00 | PASS |
| T2.3 | Toate cardurile și derularea | derulare până jos | SOARE, LOCAȚIE, PROPAGARE, fără suprapuneri | Corect, fără texte tăiate | PASS |
| T2.4 | Indicativ de 15 caractere în antet | ER/YO3ABCDEF/QR | Încape lângă dată | Încape; data rămâne cu ~640 px (risc la luni lungi) | OBSERVATION |
| T2.5 | Indicativ gol | câmp gol | Insigna fără indicativ | Doar KN46dw în insignă | PASS |
| T2.6 | Etichetele benzilor la font 1,3 | captură | Text complet | Complet, dar ocupă toată lățimea butonului | OBSERVATION |
| T2.7 | Poziția listei stabilă | observare | Nu se derulează singură | O dată derulată ~476 px fără comandă; nereprodus | OBSERVATION |
| T2.8 | Schimbarea datei UTC | – | Data nouă la 00:00 UTC | Cere schimbarea orei telefonului | SKIPPED |
| T2.9 | Orientare peisaj | – | Layout corect | Cere rotirea ecranului (setare de sistem) | SKIPPED |
| T3.1 | UTC = ceasul telefonului | 5 comparații cu `date -u` | Aceeași oră | Corect (în latența citirii) | PASS |
| T3.2 | LOCAL = UTC + 3 h | comparație | Diferență 3 h | Corect | PASS |
| T3.3 | Secundele, UTC și LOCAL din același moment | 12 capturi consecutive | Secunde succesive, aceeași secundă | 08:18:09…14, identice în toate cadrele | PASS |
| T3.4 | Trecerea minutului | observare | Minutul avansează | 08:15:59 → 08:16:05 | PASS |
| T3.5 | UTC independent de fus | – | UTC neschimbat la alt fus | Cere schimbarea fusului (acoperit de testele pe PC) | SKIPPED |
| T4.1 | Ecranul Setări (RO) | deschidere | STAȚIE, ASPECT, DESPRE | Complet, aranjat corect | PASS |
| T4.2 | Normalizarea indicativului | yo3abc/p; er1pl xx@#-_.ab; 20 caractere | Litere mari, fără caractere nepermise, max 15 | YO3ABC/P; ER1PLXXAB; ER/YO3ABCDEFGH/ | PASS |
| T4.3 | Persistența indicativului | ieșire + repornire | Păstrat | YO3ABC/P păstrat | PASS |
| T4.4 | Normalizarea locatorului | kn46DW | KN46dw | KN46dw | PASS |
| T4.5 | Persistența locatorului | repornire | Păstrat | KN46dw păstrat | PASS |
| T4.6 | Sursa poziției | Manual → Automat (GPS) | Salvat, explicația se schimbă | AUTOMATIC, explicația GPS | PASS |
| T4.7 | Persistența sursei poziției | repornire | Păstrat | AUTOMATIC păstrat | PASS |
| T4.8 | Despre aplicație | derulare | Versiune, autor, sursa N0NBH | 2.0.0, Lilian Putină ER1PL, textul N0NBH | PASS |
| T4.9 | Starea butoanelor pentru accesibilitate | uiautomator | Selecția expusă | checked=true pe Manual / Luminos | PASS |
| T4.10 | Navigarea Setări ↔ dashboard | săgeata / Înapoi | Revine la dashboard | Corect | PASS |
| T4.11 | Setare de limbă în aplicație | – | – | Nu există (aplicația urmează limba telefonului); testat prin T5 | PASS |
| T4.12 | Locator invalid semnalat în Setări | KN4 | Indicație la câmp | Nicio indicație la câmp | OBSERVATION |
| T5.1 | Română → engleză | limba aplicației = en-US | Totul în engleză | Dashboard, dialog, Setări, Despre, mesaje GPS în engleză | PASS |
| T5.2 | Formate în engleză | dashboard | Punct zecimal, dată în engleză | 46.9375° N, „1 October 2026” | PASS |
| T5.3 | Dialogul benzilor în engleză | 30-20m | Text în engleză | Day: Good • Night: Good… | PASS |
| T5.4 | Butoanele Setărilor în engleză | Station position | Pe un rând, aceeași înălțime | „Automatic (GPS)” pe două rânduri, segment mai înalt | FAIL |
| T5.5 | Engleza după repornire | repornire | Păstrată | 1 October 2026 | PASS |
| T5.6 | Engleză → română | limba sistemului | Totul în română | Corect, imediat | PASS |
| T5.7 | Schimbarea limbii cu Setările deschise | recreare activitate | Rămâne în Setări | Rămâne în Setări | PASS |
| T5.8 | Schimbări rapide de limbă | 10 schimbări | Fără crash | Fără crash | PASS |
| T6.1 | Tema Întunecat | dashboard, dialog, Setări | Contrast bun, bara de stare deschisă | Corect | PASS |
| T6.2 | Tema Sistem (telefon luminos) | Sistem | Luminos | Luminos | PASS |
| T6.3 | Tema Luminos | Luminos | Luminos | Luminos | PASS |
| T6.4 | Persistența temei | repornire după Sistem și Luminos | Păstrată | SYSTEM, apoi LIGHT păstrate | PASS |
| T6.5 | Sistem urmează modul întunecat al telefonului | – | Întunecat | Cere schimbarea modului întunecat al sistemului | SKIPPED |
| T7.1 | Manual KN46dw | locator | Centru, QTH, sursa | 46,9375° N, 28,2917° E, QTH KN46dw, „Locator din Setări” | PASS |
| T7.2 | Locator de 4 caractere | KN46 | Centru 46,5 / 29,0 | 46,5000° N, 29,0000° E; Soare 07:02 / 18:44 (= referință) | PASS |
| T7.3 | Locator de 8 caractere | KN46dw15 | 46,9396 / 28,2625 | Corect | PASS |
| T7.4 | Locator invalid | KN4, SA00 | Fără Soare, mesaj clar | „Locatorul „KN4” nu este valid.”, liniuțe | PASS |
| T7.5 | Locatorul invalid în insignă / QTH | KN4 | – | Afișat ca valid; cardul LOCAȚIE nu spune motivul | OBSERVATION |
| T7.6 | Locator gol | câmp gol | Mesaje, liniuțe, regula 06–18 | Corect | PASS |
| T7.7 | Schimbarea locatorului actualizează tot | KN46dw → IO91wm → … → KN46dw | Soare, Locație, benzi actualizate | Imediat la revenirea pe dashboard | PASS |
| T8.1 | Automat cu permisiunea deja acordată | Manual → Automat | Fără dialog | Fără dialog | PASS |
| T8.2 | Automat + locația oprită | dashboard | Locatorul + mesaj + buton | „Locația telefonului este oprită.” + „Pornește locația” | PASS |
| T8.3 | Butonul „Pornește locația” | atingere, apoi Înapoi | Pagina Locație din Android | LocationSettingsActivity (Oprit); revenire corectă | PASS |
| T8.4 | Automat + locația oprită + fără locator | câmp gol | Liniuțe, mesaje | Corect, benzile după 06–18 | PASS |
| T8.5 | Mesajul cardului SOARE în T8.4 | – | Despre locația oprită | Cere permisiunea, deși e acordată | OBSERVATION |
| T8.6 | Poziția salvată ștearsă la Manual | position.xml | Gol după Manual | <map /> (poziția de dimineață ștearsă) | PASS |
| T8.7 | Comutări rapide Manual / Automat | 16 atingeri | Fără crash, ultima alegere salvată | Corect | PASS |
| T8.8 | Locația pornită: poziție GPS exactă | – | Coordonate GPS, KN46dx… | Cere pornirea locației (verificat de tine manual) | SKIPPED |
| T8.9 | Locație aproximativă | – | „GPS (aproximativ)” | Cere schimbarea permisiunii (verificat de tine manual) | SKIPPED |
| T8.10 | Ultima poziție după repornire | – | Afișată imediat | Cere o poziție GPS (locația oprită) | SKIPPED |
| T9.1 | Permisiunea acordată | dumpsys package | FINE + COARSE | Ambele acordate (USER_SET) | PASS |
| T9.2 | Refuz / revocare / refuz definitiv / revenire din setări | – | Mesajele și butoanele cardului | Cere schimbarea permisiunii din setările Android (verificat de tine manual, testele 17–20) | SKIPPED |
| T10.1 | Soare KN46dw | comparație cu referința | 07:05 / 18:47 / 12:57 / 11h 42m / 06:35–19:17 | Identic | PASS |
| T10.2 | Soare IO91wm (Londra) | locator | 09:01 / 20:38, 08:28–21:12 | Identic | PASS |
| T10.3 | Soare QF56od (Sydney) | locator | 22:33 / 10:58, 22:08–11:23 (ziua Sydney) | Identic | PASS |
| T10.4 | Ore din altă zi calendaristică | QF56od | – | Răsăritul e în 30 sept., fără dată | OBSERVATION |
| T10.5 | Noapte polară RR99 | locator | Fără răsărit, fără crepuscul | „Soarele nu răsare în această zi.”, 0h 00m, amiaza 02:54 | PASS |
| T10.6 | Fără NaN / valori imposibile | toate cazurile | – | Niciuna | PASS |
| T10.7 | Soarele după repornire | repornire | Aceleași valori | Aceleași valori | PASS |
| T11.1 | SFI / K / A și benzile = cache-ul N0NBH | comparație cu XML-ul din telefon | 93 / 0 / 3; Good, Good, Fair, Poor | Identic | PASS |
| T11.2 | Culorile | captură | Verde / galben / roșu după nivel | Corect | PASS |
| T11.3 | Ora actualizării | comparație | „Actualizat 07:58 UTC” (`updated` 0758 GMT) | Identic | PASS |
| T11.4 | Dialogul benzilor | atingere, OK, Înapoi | Zi/noapte, sursa, se închide | Corect | PASS |
| T11.5 | Zi/noapte după Soarele stației | KN46dw, RR99, QF56od | Zi, noapte, noapte | Zi, noapte, noapte | PASS |
| T11.6 | Fără locator: regula 06–18 | dialog | Mesajul regulii | „Fără locator, ziua este între 06:00 și 18:00…” | PASS |
| T11.7 | Limita de o oră | peste 10 reporniri | Nicio descărcare înainte de oră | fetched_at neschimbat (07:58:39 UTC) | PASS |
| T11.8 | Actualizarea orară automată | dashboard vizibil până la 08:58:39 UTC | Descărcare nouă, ora nouă sub benzi | Testare oprită la 08:47 UTC, la cererea ta, înainte de oră | SKIPPED |
| T11.9 | Fără Internet (cache) | – | „Date neactualizate…” | Cere modul avion (setare de sistem; acoperit de testele pe PC) | SKIPPED |
| T12.1 | Persistență: limbă, temă, indicativ, locator, sursă | repornire după fiecare | Păstrate | Toate păstrate (T4.3, T4.5, T4.7, T5.5, T6.4) | PASS |
| T12.2 | Persistență: cache-ul N0NBH | reporniri | Datele apar imediat | Afișate imediat, fără descărcare | PASS |
| T13.1 | Home și revenire | Home 12 s, apoi aplicația | Ceasul la ora curentă | 08:34:32 (corect) | PASS |
| T13.2 | Recente | comutare | Revine corect | Corect | PASS |
| T13.3 | Procesul oprit de sistem în fundal | `am kill` în fundal | Revine în ecranul în care era | Revine în Setări | PASS |
| T13.4 | Ecran stins / aprins | – | Ceasul continuă | Cere deblocarea telefonului | SKIPPED |
| T14.1 | Înapoi din Setări (săgeată și buton) | navigare | Dashboard | Corect | PASS |
| T14.2 | Înapoi pe dashboard | buton Înapoi | Iese în launcher | Launcher; relansare corectă | PASS |
| T15.1 | Deschideri/închideri rapide ale Setărilor | 20 de cicluri | Fără crash | Fără crash, același proces | PASS |
| T15.2 | Schimbări rapide de temă cu navigare | 24 + 12 atingeri | Fără crash, ultima temă salvată | Corect | PASS |
| T16.1 | Logcat după toate testele | logcat --pid + FATAL/ANR | Nicio eroare a aplicației | Niciuna | PASS |
| T18.1 | Coerență Locație → Soare → Propagare | toate locatorii | Aceeași poziție peste tot | Corect | PASS |

---

## RECOMMENDATIONS

Doar pe baza problemelor găsite:

1. **T5.4 / T2.6 – texte în butoane cu fontul mărit:** etichetele butoanelor segmentate și ale benzilor
   să nu se rupă pe două rânduri (o variantă: `maxLines = 1` cu text mai scurt în engleză, de ex.
   „Auto (GPS)”, sau micșorare automată a textului). De încercat și cu fontul la maximum.
2. **T8.5 – mesajul cardului SOARE în modul Automat:** să urmeze starea reală a GPS-ului (locație oprită
   / fără permisiune), ca în cardul LOCAȚIE.
3. **T7.5 / T4.12 – locator invalid:** de semnalat la câmp în Setări (eroare sub câmp) și de nefolosit
   ca QTH / în insignă cât timp nu e valid.
4. **T10.4:** opțional, data lângă orele Soarelui care cad în altă zi decât cea a telefonului.

Testele SKIPPED (locația pornită, permisiuni din setările Android, mod avion, fus/dată, mod întunecat al
sistemului, rotire, ecran stins) se pot face împreună, cu tine la telefon.

Capturile sunt în
`C:\Users\Lilian\AppData\Local\Temp\claude\D--Claude\b3567dde-db78-48f8-8c7e-3598921ad6ac\scratchpad\pt\shots\`.
