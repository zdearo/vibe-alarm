/// Checagem de boa-noite: o que pode fazer o alarme de amanhã falhar, a partir do estado que o relógio reportou.
enum Level { ok, warn, fail }

/// O que o app faz quando o usuário toca no problema: corrige sozinho (resend), abre um ajuste do celular
/// (bluetooth) ou abre no relógio a tela que resolve (os demais).
enum Fix {
  resend('Reenviar agora'),
  bluetooth('Abrir Bluetooth'),
  activity('Abrir no relógio'),
  overlay('Abrir no relógio'),
  battery('Abrir no relógio'),
  notifications('Abrir no relógio'),
  suspended('Abrir no relógio');

  const Fix(this.label);
  final String label;
  bool get onWatch => index >= Fix.activity.index;
}

class Check {
  const Check(this.name, this.value, this.level, [this.fix, this.action]);
  final String name, value;
  final Level level;
  final String? fix; // o que fazer, quando não está ok
  final Fix? action; // o botão que faz isso
}

/// [ack] é a última confirmação do relógio (null se nunca chegou). Campos ausentes no ack (relógio com versão
/// antiga) não viram falha: ficam de fora.
///
/// [suspendedOk]: o usuário confirmou que tirou o Vibe Alarm de "Aplicativos suspensos" (lista da Samsung no relógio).
/// Nenhuma API expõe essa lista, então a confirmação é manual.
List<Check> nightChecks({
  required bool connected,
  required bool synced,
  required Map<String, dynamic>? ack,
  required bool suspendedOk,
}) {
  bool? flag(String k) => ack?[k] as bool?;
  final level = ack?['level'] as int?;
  return [
    connected
        ? const Check('Relógio', 'conectado', Level.ok)
        : const Check(
            'Relógio',
            'não encontrado',
            Level.fail,
            'Ligue o Bluetooth e aproxime o relógio.',
            Fix.bluetooth,
          ),
    synced
        ? const Check('Alarmes no relógio', 'confirmados', Level.ok)
        : const Check(
            'Alarmes no relógio',
            'sem confirmação',
            Level.fail,
            'Com o relógio por perto, o app reenvia a lista.',
            Fix.resend,
          ),
    if (level != null)
      level < 30
          ? Check(
              'Bateria do relógio',
              '$level%',
              Level.warn,
              'Carregue antes de dormir.',
            )
          : Check('Bateria do relógio', '$level%', Level.ok),
    if (flag('charging') == true)
      const Check(
        'Relógio',
        'carregando',
        Level.fail,
        'Fora do pulso o alarme não toca. Vista o relógio antes de dormir.',
      ),
    if (flag('activity') == false)
      const Check(
        'Atividade física',
        'negada',
        Level.fail,
        'No relógio, abra Permissões e permita Atividade física.',
        Fix.activity,
      )
    else if (flag('activity') == true)
      const Check('Atividade física', 'ok', Level.ok),
    if (flag('overlay') == false)
      const Check(
        'Tela do alarme',
        'bloqueada',
        Level.fail,
        'O relógio vibra, mas a contagem não abre. No relógio, permita aparecer sobre outros apps.',
        Fix.overlay,
      )
    else if (flag('overlay') == true)
      const Check('Tela do alarme', 'ok', Level.ok),
    // Economia de energia do relógio limita o uso em segundo plano. O antigo "otimização de bateria" do Android
    // não vale aqui: o Wear não tem tela para mudá-la, então ficava "restrito" para sempre.
    if (flag('powersave') == true)
      const Check(
        'Economia de energia',
        'ligada',
        Level.warn,
        'Limita o app em segundo plano. Desligue no relógio, em Bateria.',
        Fix.battery,
      )
    else if (flag('powersave') == false)
      const Check('Economia de energia', 'desligada', Level.ok),
    suspendedOk
        ? const Check('Aplicativos suspensos', 'confirmado por você', Level.ok)
        : const Check(
            'Aplicativos suspensos',
            'confirme',
            Level.warn,
            'Apps suspensos só rodam de vez em quando. No relógio, abra Bateria, role até Aplicativos suspensos e tire o Vibe Alarm da lista.',
            Fix.suspended,
          ),
    if (flag('notifications') == false)
      const Check(
        'Notificações',
        'desligadas',
        Level.warn,
        'Com a tela do alarme fechada, nada indica o alarme. No relógio, ligue as notificações do Vibe Alarm.',
        Fix.notifications,
      )
    else if (flag('notifications') == true)
      const Check('Notificações', 'ok', Level.ok),
    if (flag('scan') != null)
      Check(
        'Trava pelo celular',
        flag('scan')! ? 'ativa' : 'desligada',
        Level.ok,
      ),
  ];
}
