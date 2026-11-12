# Vibe Alarm — ideias escolhidas

## Identidade (testar cada uma separada antes de definir)
1. **Assinatura tátil:** a rampa de vibração como "logo sonoro" da marca. Cada interação dá um tique no celular, e o onboarding começa com "sinta o seu despertar".
2. **Rampa como linguagem gráfica:** as barras viram contagem regressiva do tile do relógio, indicador de progresso e logotipo.
3. **Silêncio como estética:** muito espaço vazio, sem animação gratuita, tudo em tom baixo. A marca se define pelo que não faz.

## Diferencial (futuro)
1. **Checagem de boa-noite:** antes de dormir mostra "Tudo pronto: acorda 06:45 · relógio 82% · permissões ok".
2. **Ressonância com o parceiro:** posicionamento "só você acorda"; o parceiro continua dormindo.
3. **Registro do despertar:** "levantou em 38 s, 10 passos", tendência da semana e menor intensidade que ainda acorda.
4. **Acordar na fase leve:** janela de 20 min antes do horário, usando movimento e batimentos. Exige dados de sono e permissões extras no Wear OS.

5. **Plano B no celular (futuro):** se o relógio não for atendido, o celular assume. Referência: PulseWake escala para o iPhone após 30 s. É o único caso em que o app emitiria som.

## Não escolhidas por ora
- Horário em formato próprio (dígitos desenhados com barras ou pontos).
- Voz curta com humor discreto.
- Rampa personalizável (arrastar as barras, curvas nomeadas).

## Referências (pesquisa de 2026-11-11, só trechos de busca; nenhuma página lida inteira)

**Concorrentes diretos**
- PulseWake (iOS/Apple Watch): https://pulsewake.app/ — 3 padrões (Pulse, Heartbeat, Escalator de 10% a 100% em 5 min); se não houver resposta em 30 s, o iPhone assume.
- Gradual Alarm (Wear OS e Apple Watch): https://alarm-clock.app/blog/wear-os-and-apple-watch-smart-waking — 4 fases de háptico e padrão próprio de dispensa.
- Shock Clock, análise de UX: https://medium.com/patternfly/the-human-shock-collar-a-ux-analysis-618322fcb542 — encerra após 10 polichinelos; o bipe que confirma o movimento anula o silêncio. O Sleep Cycle abre direto no alarme.

**Confiança e checagem (diferencial 1)**
- Crit Alarm, tela "Will it wake me?": https://critalarm.app/reliability-checks/ — "All good / Take a look / Fix this", problemas juntos no topo, sem notificação, "Ring a test" após atualização do sistema.
- Beyond Alarm, verificação de confiabilidade: https://beyondalarm.app/features/alarm-reliability-check — nota (At risk / Good / Excellent), botão "Fix 2 critical issues" e passos por marca (Samsung, Xiaomi etc.).

**Assinatura tátil (identidade 1)**
- Visa: https://www.obeliskmusic.com/project-visa.php — padrões criados com um app de teste sensorial.
- Mastercard: https://www.futurebrand.com/our-work/mastercard-haptic — "haptic logo" sincronizado com a melodia da marca, acessível e que reforça confiança.

**Estética**
- Nothing, entrevista de design: https://designwanted.com/nothing-tech-design-smartphones-not-all-the-same/ — a fonte Ndot nasceu de "e se uma máquina desenhasse o logo?"; o Glyph deixa o celular virado para baixo.
- Nothing, tokens do site: https://fudge.design/share/intl.nothing.tech-design — Ndot nos títulos, N Type 82 no corpo, Lettera Mono nos rótulos.

**Lógica de despertar (diferencial 4)**
- Pebble Sykero Alarm: https://github.com/Sykero-Software/PebbleSykeroAlarm — acorda alguns minutos antes, quando você já está se mexendo, e nunca depois do horário.
- somarise (HRV + movimento): https://github.com/lavear/somarise

**Pesquisa que não rendeu:** Reddit fora do ar na hora; Twitter com pouco sinal; YouTube só vídeos de configuração do Nothing.
