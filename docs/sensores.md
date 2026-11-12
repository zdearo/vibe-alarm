# Como o alarme sabe que a pessoa levantou

Só a caminhada encerra o alarme (além do limite de segurança de 5, 10 ou 15 min). Não existe botão de parar, nem na tela nem na notificação. A meta é de segundos de caminhada (10, 20 ou 30 s).

## Como funciona
- O acelerômetro é lido só nas **pausas da vibração** (`VibrationRamp.isQuiet`). O motor do relógio balança o sensor, então o que se mede durante os pulsos é do próprio relógio.
- Cada pausa vira uma janela "ativa" se a variação média da aceleração passar de 0,40 m/s² (`MotionDetector`). O resultado é um **crédito em segundos**, em tempo real:
  - janela ativa: soma o tempo decorrido (o 1º segundo ganha um bônus de 1 s, por causa do atraso do sensor);
  - 1ª janela fraca logo depois de uma forte (até 2,5 s): o número **para de subir** e o tempo fica pendente; se a próxima for forte, o pendente é devolvido, se for fraca de novo, é perdido;
  - fraca fora da tolerância: o crédito **desce** no mesmo ritmo (parar baixa o número, não zera de uma vez).
- **Giroscópio:** girar o braço em círculos dá a mesma aceleração que andar, mas faz o pulso girar muito mais rápido. Cada janela vale por inteiro até 1,2 rad/s e nada a partir de 2,6 rad/s, linear no meio.
- A pausa mínima do ciclo máximo é de 1,2 s (`VibrationRamp.MIN_GAP_MS`), para sempre haver uma janela útil.
- O botão lateral faz o Android cancelar a vibração (`CANCELLED_BY_SCREEN_OFF`); o serviço religa em ~0,5 s no ponto da rampa.
- A tela anima o número e o anel de forma contínua, subindo e descendo (~1 s de progresso por segundo).

## Por que não o sensor de passos da Samsung (Galaxy Watch, medido em 11/11/2026)
- Sem vibração: precisa de ~12 s de caminhada para "aquecer" (o contador sobe +10 de uma vez no 1º evento) e depois marca um passo a cada ~0,7 s. Se a pessoa para, aquece de novo.
- **Com o motor vibrando ele não emite nenhum evento**, mesmo com a pessoa andando (confirmado pelo acelerômetro). Só volta ~4 s depois que o motor para.
- Não existe versão wake-up desse sensor no relógio, e privacidade de sensores, permissões e economia de bateria não são a causa.

## Calibração (medida no relógio, 11–12/11/2026, uma pessoa)
- Parado, nas pausas: média 0,03 a 0,39 m/s² (mexidas isoladas); giro < 0,1 rad/s.
- Andando: média 0,25 a 2,6 (braço mais parado dá o valor baixo); giro 0,3 a 1,2 rad/s no regime estável.
- Girando o braço em círculos: média 1,3 a 3,1; giro 1,4 a 3,4 rad/s (quase todas as janelas > 2).
- Não separam: razão horizontal/vertical da aceleração (andar e girar se sobrepõem) e o **barômetro** (LPS28DFW): a altura do pulso oscilou ±0,25 m só com o braço, perto demais do degrau esperado de levantar (~0,4 m). Leitura do barômetro removida do código em 12/11/2026.
- **GPS descartado** (medido em 12/11/2026, dentro de casa): 1º ponto de rede em 1 s com erro de 12–19 m; 1º ponto de GPS só em 14 s, erro de 34 m, depois 9–10 m. Com a pessoa quase parada o "deslocamento" oscilou 4–12 m (ruído), e andando ficou em 5–8 m, com velocidade de 0,2–0,5 m/s. Além disso o Android bloqueia localização em serviço iniciado em segundo plano (`Foreground service started from background can not have location/camera/microphone access`).
- **Magnetômetro (só a intensidade do campo, que não muda com a rotação do relógio):** parado/mexendo pouco ficou estável (39–40 µT); girando rápido (2,7 rad/s) variou 1–2 µT; andando variou 8–18 µT entre janelas. Promissor como indicador de deslocamento, mas a amostra tem ~10 s de caminhada e havia um ímã por perto (1320 µT nos primeiros 25 s). Leitura removida do código em 12/11/2026 (ver "Magnetômetro não separa" abaixo).
- Ainda não foi medido o movimento de rolar na cama de verdade. Se o alarme parar sem a pessoa levantar, subir o limiar (`MotionDetector.THRESHOLD`) ou apertar o peso do giro.
- Quem anda muito rápido (giro alto) conta mais devagar, mas conta: o peso só zera acima de 2,6 rad/s.

## Disparo com o relógio em sono profundo (corrigido em 12/11/2026)
- O `startForegroundService` chamado no `AlarmReceiver` é assíncrono: quando `onReceive` termina, o wake lock do AlarmManager cai e o relógio volta a dormir **antes** de o serviço subir. Medido em sono profundo (log do sistema: `SystemSuspend: write mem+` 47 ms depois do disparo): a vibração começou **45 s e depois 2 min 12 s** atrasada, e só quando algo acordou o aparelho.
- Correção: o `AlarmReceiver` pega um wake lock parcial de 20 s antes de iniciar o serviço; o serviço pega o dele em seguida.
- Verificado em sono profundo de verdade (suspend/resume a cada ~1 s até o disparo): serviço às 01:34:34.057 e vibração às 01:34:34.172 para um alarme das 01:34:34, **0,17 s de atraso**, e o aparelho não voltou a dormir durante o alarme.
- Cuidado ao testar: consultar o relógio por `adb` o mantém acordado e **esconde** esse defeito. Para testar disparo, agendar, não tocar no relógio e só reconectar depois.

## RSSI do farol no log
- RSSI do farol Bluetooth LE do celular (`rssi=`, `rd=`), usado pela trava. O celular anuncia o farol enquanto o app está aberto (`phone/.../MainActivity.kt`, `BLUETOOTH_ADVERTISE`) e o relógio escuta (`BLUETOOTH_SCAN`, `Beacon.kt`).
- Rodada de 01:28 (celular parado num ponto): RSSI −62 dBm em repouso; ao andar e se afastar caiu até −92 (Δ −27 dB) e voltou a −75 ao retornar; o campo magnético variou −8 a −20 µT. O RSSI tem ruído de ±5 dB por janela. Ainda falta a fase "braço mexendo no mesmo lugar" para saber se ambos ficam quietos.

## Farol Bluetooth do celular acionado pelo relógio (12/11/2026)
- **Disparo remoto funciona sem app aberto e sem serviço em primeiro plano.** No início do alarme o relógio manda `/beacon/start` pela Data Layer (`MessageClient`); um `WearableListenerService` do celular (`BeaconService`) começa a anunciar um farol Bluetooth LE e o relógio escuta. Com o processo do celular morto e a tela apagada: mensagem entregue em 0,67 s, anúncio 52 ms depois, 1º sinal no relógio 1,35 s após o início do alarme. `/beacon/stop` encerra; o anúncio também para sozinho em 180 s. Não testado: celular em Doze profundo por horas, ou app forçado a parar pelo usuário.
- **Discriminação (uma rodada de cada, celular parado num ponto, linha de base fraca: −76 dBm):**
  - só mexendo o braço: RSSI −77 a −64 dBm (Δ −1 a **+11 dB**), campo magnético Δ **−24 a −8 µT**, |Δ RSSI| entre janelas 2,1 dB;
  - andando e se afastando: RSSI −92 a −62 (Δ **−28 a 0**), campo Δ −20 a 0 µT, |Δ RSSI| entre janelas 4,7 dB.
- **Magnetômetro não separa:** só o braço já variou até 24 µT, o mesmo que andar. Descartado.
- **RSSI separa só de um lado:** Δ ≤ −8 dB aconteceu em 16 de 28 janelas andando e em 0 de 26 só com o braço; mas o braço chegou a +11 dB. Com a linha de base fraca o braço só podia melhorar o sinal; com o celular perto (sinal forte) ele pode piorar. Falta testar na geometria real (celular no criado-mudo).

## Trava de liberação pela conexão com o celular (ativa, 12/11/2026)
- O alarme só para quando a caminhada completa a meta **e** o RSSI do celular (num ponto fixo) variou ≥ 12 dB em relação ao ponto X, para mais ou para menos, por 3 janelas seguidas (`ConnectionGate`). O ponto X é a média das 3 primeiras janelas com sinal. Depois de aberta, fica aberta.
- **Sem sinal do celular, a trava não vale** (alarme para só pela caminhada). Se o sinal some por 3 janelas seguidas depois do ponto X, conta como ter se afastado.
- Meta atingida com a trava fechada: o anel fica cheio e a tela mostra "Continue andando".
- Ainda sem teto de tempo para a trava (só o limite de segurança de 5/10/15 min), a ser ajustado com o uso. Limiar e janelas saíram de uma rodada de cada, com o celular longe; revalidar com o celular no criado-mudo. O log mostra `trava=` por janela.
