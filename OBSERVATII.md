# Observații – UTC Ham Clock pentru Android

Lilian vrea ca aplicația Android și cea nouă de Windows (D:\Claude\UTCHamClockWin) să aibă
aproximativ aceleași funcții și același aspect. Aici se notează ce s-a hotărât la Windows și
trebuie adus și pe Android. Se fac doar când cere Lilian.

## Deja aduse pe Android

- 29.09.2026 – **Culorile nivelurilor** mereu verde / galben / roșu, în nuanța fiecărei teme
  (FT-891, LCD reflexiv, VFD albastru cu verde mentă, Zi luminoasă). Modul noapte rămâne doar roșu.
- 29.09.2026 – **„Sincronizat” / „Nesincronizat”**: sub 0,5 s diferență scrie „Sincronizat” (verde),
  de la 0,5 s „Nesincronizat” (roșu), fără valoarea Δt; cât greșește exact se vede la atingere.

## De adus de la Windows (hotărâte acolo, 29.09.2026)

| # | Ce | Cum e la Windows |
|---|----|------------------|
| 1 | Fără greyline și fără vreme pe ecran | Scoase de tot: rândul „Greyline la apus în…”, vremea Open-Meteo, opțiunea „Vremea la QTH”, eticheta GREYLINE. |
| 2 | QTH sus în dreapta | „QTH KN46dw” pe rândul indicativului, în dreapta, în culoarea indicativului. |
| 3 | Sincronizarea lângă UTC | „Sincronizat”/„Nesincronizat” pe rândul etichetei UTC, în dreapta. |
| 4 | Răsărit și apus mari, la mijloc | Sub ceasuri, răsăritul în stânga și apusul în dreapta, cu iconițele, mai mari decât textul obișnuit, în culoarea ceasului local. |
| 5 | Data | Doar sub UTC; sub LOCAL apare doar în prima oră după ce ziua locală devine alta decât cea UTC (00:00–01:00 local). Datele și etichetele au culoarea cifrelor. |
| 6 | Casete colorate | SFI, K, A (3 casete) și benzile (4 casete: „80-40m”, „30-20m”, „17-15m”, „12-10m”), fiecare colorată după nivel, cu textul închis pe galben și alb pe verde închis/roșu. Înlocuiesc barele. |
| 7 | Explicații la atingere | Pe „Sincronizat”, răsărit/apus, SFI, K, A și pe fiecare bandă (condițiile ziua și noaptea + sursa: N0NBH sau estimare). |
| 8 | Etichetele UTC/LOCAL | Mai mari și în culoarea cifrelor. |
| 9 | Minimal modern | Cifrele subțiri pe toată lățimea, ca la celelalte teme. |

Pe Windows, în plus (fără echivalent pe Android): sincronizarea ceasului Windows la clic pe
„Nesincronizat” (pe telefon, ora se potrivește automat din rețea).

## Hotărâte doar pentru Android

| # | Ce | Cum |
|---|----|-----|
| 10 | Fără verificarea orei (29.09.2026) | Se scoate de tot: „Sincronizat”/„Nesincronizat”, măsurarea Δt și cererea la pool.ntp.org. Android potrivește singur ora din rețea, iar o aplicație nu are voie să o schimbe. Pe Windows rămâne. |
