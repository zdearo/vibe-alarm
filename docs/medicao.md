# Roteiro de medição: rolar na cama e trava com o celular no criado-mudo

Duas perguntas em aberto (ver `docs/sensores.md`):
1. Rolar na cama faz o crédito de caminhada subir até a meta? Se sim, o alarme para sem a pessoa levantar.
2. Com o celular no criado-mudo (sinal forte), mexer só o braço varia o RSSI em 12 dB ou mais? Se sim, a trava abre sem a pessoa sair do lugar.

## Preparação
- Celular no criado-mudo, no lugar de sempre. Bluetooth ligado nos dois. Permissões `BLUETOOTH_SCAN` (relógio) e `BLUETOOTH_ADVERTISE` (celular) dadas (`docs/setup-relogio.md`).
- Alarme de teste no app: **Caminhar por 30 s**, **Limite de segurança 5 min**, intensidade de uso real.
- Antes de cada rodada, limpe o log do relógio: `adb -s <relogio> logcat -c`.
- Não consulte o relógio por `adb` durante a rodada (ele fica acordado e o teste deixa de valer). Só reconecte no fim.

## Rodadas
Cada rodada é um alarme. Anote a hora de início de cada fase (relógio do celular basta).

| Rodada | 0–60 s | 60–120 s | Esperado |
|---|---|---|---|
| A. Rolar | deitado, rolar de um lado para o outro a cada ~5 s | deitado, mexer o braço (procurar o relógio, cobrir o rosto) | alarme **não** para; `caminhando` fica baixo; `trava=fechada` |
| B. Braço perto do celular | deitado, braço estendido perto do celular, mexendo | sentado na cama, braço mexendo | alarme **não** para; `trava=fechada` |
| C. Levantar de verdade | deitado 20 s, depois levantar e andar pelo quarto e para fora | — | alarme para; `trava=aberta` antes de parar |

Repita A e B pelo menos 3 vezes cada, em noites ou posições diferentes. C uma vez por rodada de A/B serve de controle.

## Coleta
Depois de cada rodada:

```bash
adb -s <relogio> logcat -d -s AlarmService > rodada-A1.txt
```

Cada janela (pausa da vibração) gera uma linha:

```
janela média=0.42 caminhando=6 s v=… h=… gyro=0.80 rssi=-58 rstd=2.1 rd=-3.0 dB n=14 trava=fechada
```

- `média`: variação média da aceleração (limiar ativo: `MotionDetector.THRESHOLD`, 0,40 m/s²).
- `caminhando`: crédito acumulado em segundos (meta = 30).
- `gyro`: rad/s; acima de 1,2 o peso cai, acima de 2,6 a janela não conta.
- `rssi` / `rd`: sinal do celular e variação em relação ao ponto X; `n` amostras na janela.
- `trava`: `sem-linha-de-base`, `fechada` ou `aberta`.

## Como decidir
- Se em A o `caminhando` chegou à meta: subir `MotionDetector.THRESHOLD` até o maior valor de `média` visto em A ficar abaixo dele, e conferir em C que andar ainda passa.
- Se em A ou B a trava abriu (`|rd| ≥ 12` por 3 janelas seguidas): subir `ConnectionGate.DB_THRESHOLD` acima do maior `|rd|` de A/B, e conferir em C.
- Se `n=0` em muitas janelas, o farol não chegou: checar se o celular recebeu `/beacon/start` (`adb -s <celular> logcat -d -s Beacon`).
- Registrar os números em `docs/sensores.md` (seção Calibração), com data.
