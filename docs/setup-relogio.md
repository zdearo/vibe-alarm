# Setup do relógio (Galaxy Watch, Wear OS)

Depois de instalar o APK do relógio, duas permissões precisam ser dadas uma vez:

```bash
# atividade física (o serviço do alarme usa o tipo "health", que exige essa permissão) e notificações
adb -s <relogio> shell pm grant com.zdearo.vibealarm android.permission.ACTIVITY_RECOGNITION
adb -s <relogio> shell pm grant com.zdearo.vibealarm android.permission.POST_NOTIFICATIONS
# permite abrir a tela "Despertando" quando o alarme dispara com a tela apagada
adb -s <relogio> shell appops set com.zdearo.vibealarm SYSTEM_ALERT_WINDOW allow
```

Sem a última, o alarme vibra e acende a tela normalmente, mas a tela "Despertando"
(contagem de passos) não abre: o Android bloqueia a abertura em segundo plano.

Observações do Galaxy Watch:
- O Modo Hora de dormir da Samsung fica por cima de tudo; com a permissão acima a tela do alarme aparece na frente dele.
- O botão lateral cancela a vibração (`CANCELLED_BY_SCREEN_OFF`); o app religa em ~0,5 s no ponto da rampa.
- Só a caminhada encerra o alarme (ver `docs/sensores.md`), além do limite de segurança (5, 10 ou 15 min).

## Trava pela conexão com o celular (experimental)
Para a trava valer, as duas permissões de Bluetooth precisam estar concedidas (Android 12+):

```bash
adb -s <relogio> shell pm grant com.zdearo.vibealarm android.permission.BLUETOOTH_SCAN
adb -s <celular> shell pm grant com.zdearo.vibealarm android.permission.BLUETOOTH_ADVERTISE
```

Sem elas, ou sem o celular conectado, a trava simplesmente não vale e o alarme para só pela caminhada.
O celular precisa ter o app aberto **uma vez** depois de instalado (para sair do estado "parado"); depois disso não precisa estar aberto.
