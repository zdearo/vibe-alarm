# Vibe Alarm

Alarme silencioso por vibração no pulso. O relógio (Wear OS) vibra numa rampa crescente e **só para quando você levanta e anda**: não existe botão de parar, nem na tela nem na notificação. O celular serve para criar e editar os alarmes.

Pensado e calibrado num Galaxy Watch.

## Como funciona

1. Você configura os alarmes no app do celular (Flutter).
2. A lista é enviada ao relógio pela Wearable Data Layer (`/alarms`); o relógio confirma em `/ack`.
3. Na hora, o relógio dispara (`AlarmManager` + wake lock) e vibra numa rampa que cresce em amplitude, duração do pulso e frequência.
4. Nas pausas entre pulsos, o acelerômetro e o giroscópio medem se você está andando. Cada segundo de caminhada soma crédito; parar faz o crédito descer.
5. Ao atingir a meta (10, 20 ou 30 s de caminhada), o alarme para. Um limite de segurança (5, 10 ou 15 min) encerra de qualquer forma.

**Trava pela conexão com o celular (experimental):** no início do alarme, o relógio pede ao celular que anuncie um farol Bluetooth LE. Se houver sinal, o alarme só libera quando o RSSI variar ≥ 12 dB em relação ao ponto inicial por 3 janelas seguidas, ou seja, quando você se afastou do celular (que fica num ponto fixo, como o criado-mudo). Sem sinal, a trava não vale.

Detalhes, medições e calibração: [`docs/sensores.md`](docs/sensores.md).

## Estrutura

```
phone/   App Flutter (Android): lista e edição de alarmes, sincronização com o relógio
  lib/alarm.dart              modelo do alarme
  lib/repo.dart               persistência (shared_preferences) e envio ao relógio
  lib/pages.dart, theme.dart  telas e tema
  android/.../MainActivity.kt ponte Flutter ↔ Data Layer (MethodChannel vibealarm/sync)
  android/.../BeaconService.kt farol Bluetooth LE acionado pelo relógio
watch/   App Wear OS nativo (Kotlin)
  AlarmScheduler, AlarmReceiver, BootReceiver   agendamento e reagendamento após boot
  AlarmService, AlarmActivity                   vibração, leitura dos sensores, tela "Despertando"
  VibrationRamp                                 padrão de vibração crescente
  MotionDetector                                detecção de caminhada (acelerômetro + giroscópio)
  ConnectionGate, Beacon                        trava pelo RSSI do celular
  DataLayerService                              recebe os alarmes do celular
  SleepStateService, HealthDiagActivity         experimentos com Health Services
docs/    notas de setup, sensores e ideias
```

## Opções de cada alarme

| Campo | Valores |
|---|---|
| Horário e dias | dias da semana, ou uma vez só |
| Rótulo | texto livre |
| Intensidade | suave, normal, intenso |
| Subida | tempo até a vibração máxima: 15 s, 30 s, 40 s (padrão), 1 min, 1 min 30 s ou 2 min |
| Caminhada | 10, 20 ou 30 s para encerrar |
| Limite de segurança | 5, 10 ou 15 min |

## Requisitos

- Flutter (Dart SDK ^3.13) e Android SDK
- Celular Android pareado com um relógio Wear OS (relógio com `minSdk = 30`)
- `adb` com os dois aparelhos conectados (USB ou Wi-Fi)

## Build e instalação

Celular:

```bash
cd phone
flutter pub get
flutter run -d <celular>
```

Relógio:

```bash
cd watch
./gradlew installDebug     # com só o relógio selecionado, ou ANDROID_SERIAL=<relogio>
```

Os dois apps usam o mesmo `applicationId` (`com.zdearo.vibealarm`), exigido pela Data Layer, e precisam ser assinados com a mesma chave.

## Setup do relógio

Permissões dadas uma vez após instalar:

```bash
adb -s <relogio> shell pm grant com.zdearo.vibealarm android.permission.ACTIVITY_RECOGNITION
adb -s <relogio> shell pm grant com.zdearo.vibealarm android.permission.POST_NOTIFICATIONS
adb -s <relogio> shell appops set com.zdearo.vibealarm SYSTEM_ALERT_WINDOW allow
```

Para a trava pelo celular (opcional):

```bash
adb -s <relogio> shell pm grant com.zdearo.vibealarm android.permission.BLUETOOTH_SCAN
adb -s <celular> shell pm grant com.zdearo.vibealarm android.permission.BLUETOOTH_ADVERTISE
```

Abra o app do celular uma vez após instalar. Mais detalhes em [`docs/setup-relogio.md`](docs/setup-relogio.md).

## Testes

```bash
cd phone && flutter test
cd watch && ./gradlew test
```

Cobrem o modelo do alarme (celular) e `Schedule`, `VibrationRamp`, `MotionDetector` e `ConnectionGate` (relógio).

## Testando o disparo

Consultar o relógio por `adb` o mantém acordado e esconde atrasos de disparo em sono profundo. Para testar: agende o alarme, desconecte, não toque no relógio e só reconecte depois.

## Limitações conhecidas

- Calibração feita com uma pessoa e um Galaxy Watch; rolar na cama ainda não foi medido. Se o alarme parar sem você levantar, suba `MotionDetector.THRESHOLD`.
- O sensor de passos da Samsung não é usado: não emite eventos com o motor vibrando.
- A trava pelo celular saiu de poucas rodadas de medição e ainda precisa ser revalidada com o celular no criado-mudo.
- Barômetro, magnetômetro e Health Services ainda só registram no log, sem influenciar o alarme.
