/// Checagem de boa-noite: o que pode fazer o alarme de amanhã falhar, a partir do estado que o relógio reportou.
enum Level { ok, warn, fail }

class Check {
  const Check(this.name, this.value, this.level, [this.fix]);
  final String name, value;
  final Level level;
  final String? fix; // o que fazer, quando não está ok
}

/// [ack] é a última confirmação do relógio (null se nunca chegou). Campos ausentes no ack (relógio com versão
/// antiga) não viram falha: ficam de fora.
List<Check> nightChecks({required bool connected, required bool synced, required Map<String, dynamic>? ack}) {
  bool? flag(String k) => ack?[k] as bool?;
  final level = ack?['level'] as int?;
  return [
    connected
        ? const Check('Relógio', 'conectado', Level.ok)
        : const Check('Relógio', 'não encontrado', Level.fail, 'Ligue o Bluetooth e aproxime o relógio.'),
    synced
        ? const Check('Alarmes no relógio', 'confirmados', Level.ok)
        : const Check('Alarmes no relógio', 'sem confirmação', Level.fail, 'Toque em Verificar de novo com o relógio por perto.'),
    if (level != null)
      level < 30
          ? Check('Bateria do relógio', '$level%', Level.warn, 'Carregue antes de dormir.')
          : Check('Bateria do relógio', '$level%', Level.ok),
    if (flag('activity') == false)
      const Check('Atividade física', 'negada', Level.fail, 'Abra o Pulso no relógio e permita.')
    else if (flag('activity') == true)
      const Check('Atividade física', 'ok', Level.ok),
    if (flag('overlay') == false)
      const Check('Tela do alarme', 'bloqueada', Level.fail, 'O relógio vibra, mas a contagem não abre. Ver docs/setup-relogio.md.')
    else if (flag('overlay') == true)
      const Check('Tela do alarme', 'ok', Level.ok),
    if (flag('battery') == false)
      const Check('Economia de bateria', 'restringe o Pulso', Level.warn, 'O alarme pode falhar de madrugada.')
    else if (flag('battery') == true)
      const Check('Economia de bateria', 'sem restrição', Level.ok),
    if (flag('notifications') == false)
      const Check('Notificações', 'desligadas', Level.warn, 'Com a tela do alarme fechada, nada indica o alarme.')
    else if (flag('notifications') == true)
      const Check('Notificações', 'ok', Level.ok),
    if (flag('scan') != null)
      Check('Trava pelo celular', flag('scan')! ? 'ativa' : 'desligada', Level.ok),
  ];
}
