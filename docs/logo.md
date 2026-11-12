# Logo e nome

## Nome: Vibe Alarm

- **Vibe** vem de *vibração*, que é o que acorda você, e do tom do app: acordar sem barulho, sem sobressalto.
- **Alarm** diz o que o app é. Sem sufixos, sem trocadilhos.
- Escrita da marca em texto corrido: **Vibe Alarm** (duas palavras, V e A maiúsculos). O rótulo do app nos dois manifests é "Vibe Alarm".
- No logotipo, as duas palavras ficam coladas em minúsculas, em dois tons: `vibe` em `#E4E4E4` e `alarm` em `#9A9A9A`, Bricolage Grotesque 700, espaçamento de letras -0,03 em.

## Símbolo

Três leituras num traço só, sem preenchimento:

1. **Anel** (círculo de raio 30): o relógio no pulso.
2. **Ponteiros em V**: formam o V de *Vibe*. Um é mais curto que o outro, como hora e minuto.
3. **Arcos laterais** (raios 44 e 56, dois de cada lado): a vibração se espalhando. Sem sino e sem onda sonora, porque o alarme é silencioso.

Grade de 120 × 120, centro em (60, 60). Anel e ponteiros com traço 8; arcos com traço 6; pontas e cantos arredondados. Cada arco cobre 70° (raio 44) ou 40° (raio 56), centrado no eixo horizontal.

Tamanho mínimo do símbolo solto: 24. Abaixo disso os arcos externos somem; se um dia for preciso, usar só anel + V.

## Ícone do app

Símbolo `#E4E4E4` sobre `#2B3B79` (accent da identidade). O símbolo ocupa metade da área de 108 e fica dentro do círculo seguro de 66 do ícone adaptativo, então não é cortado no ícone redondo do relógio.

Versões monocromáticas: `#0B0B0B` sobre `#E4E4E4` (fundo claro) e `#E4E4E4` sobre `#0B0B0B` (fundo escuro).

## Arquivos

| Arquivo | Uso |
| --- | --- |
| `docs/logo/simbolo.svg` | Fonte do símbolo (120 × 120), claro, sem fundo |
| `docs/logo/icone.svg` | Fonte do ícone (108 × 108) com fundo accent |
| `phone/android/app/src/main/res/drawable/ic_launcher_foreground.xml` | Símbolo como vector drawable (celular) |
| `watch/app/src/main/res/drawable/ic_launcher_foreground.xml` | Idem (relógio) |
| `*/res/values/ic_launcher_background.xml` | Cor de fundo do ícone, `#2B3B79` |
| `*/res/mipmap-anydpi-v26/ic_launcher.xml` | Ícone adaptativo (Android 8+) |

No celular, os PNGs antigos em `mipmap-*` continuam só como reserva para Android abaixo de 8. O relógio exige API 30, então usa só o adaptativo.

Se mudar o símbolo, atualize os dois SVGs e os dois vector drawables juntos: as coordenadas são as mesmas.
