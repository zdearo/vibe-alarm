/// Alarme do Vibe Alarm. `days` usa DateTime.weekday (1 = segunda ... 7 = domingo); vazio = toca uma vez, em [at].
class Alarm {
  const Alarm({
    required this.id,
    required this.hour,
    required this.minute,
    this.days = const {},
    this.label = '',
    this.enabled = true,
    this.intensity = 1, // 0 suave, 1 normal, 2 intenso
    this.rampSeconds = 40,
    this.walkSeconds =
        20, // segundos de caminhada para encerrar; não há botão de parar
    this.timeoutMin = 10,
    this.at = 0,
  });

  final String id;
  final int hour, minute;
  final Set<int> days;
  final String label;
  final bool enabled;
  final int intensity, rampSeconds, walkSeconds, timeoutMin;
  final int at;

  Alarm copyWith({
    int? hour,
    int? minute,
    Set<int>? days,
    String? label,
    bool? enabled,
    int? intensity,
    int? rampSeconds,
    int? walkSeconds,
    int? timeoutMin,
    int? at,
  }) => Alarm(
    id: id,
    hour: hour ?? this.hour,
    minute: minute ?? this.minute,
    days: days ?? this.days,
    label: label ?? this.label,
    enabled: enabled ?? this.enabled,
    intensity: intensity ?? this.intensity,
    rampSeconds: rampSeconds ?? this.rampSeconds,
    walkSeconds: walkSeconds ?? this.walkSeconds,
    timeoutMin: timeoutMin ?? this.timeoutMin,
    at: at ?? this.at,
  );

  Map<String, dynamic> toJson() => {
    'id': id,
    'hour': hour,
    'minute': minute,
    'days': (days.toList()..sort()),
    'label': label,
    'enabled': enabled,
    'intensity': intensity,
    'rampSeconds': rampSeconds,
    'walkSeconds': walkSeconds,
    'timeoutMin': timeoutMin,
    'at': at,
  };

  factory Alarm.fromJson(Map<String, dynamic> j) => Alarm(
    id: j['id'] as String,
    hour: j['hour'] as int,
    minute: j['minute'] as int,
    days: Set<int>.from(j['days'] as List),
    label: j['label'] as String? ?? '',
    enabled: j['enabled'] as bool,
    intensity: j['intensity'] as int? ?? 1,
    rampSeconds: j['rampSeconds'] as int? ?? 40,
    // Listas antigas tinham "steps" (~2 s de caminhada por passo); mínimo 5 s.
    walkSeconds: (j['walkSeconds'] as int? ?? (j['steps'] as int? ?? 10) * 2)
        .clamp(5, 120),
    timeoutMin: j['timeoutMin'] as int? ?? 10,
    at: j['at'] as int? ?? 0,
  );

  /// Instante absoluto de um alarme de uma vez: hoje se ainda não passou, senão amanhã.
  static int onceAt(int hour, int minute, DateTime now) {
    var t = DateTime(now.year, now.month, now.day, hour, minute);
    if (!t.isAfter(now)) {
      t = DateTime(now.year, now.month, now.day + 1, hour, minute);
    }
    return t.millisecondsSinceEpoch;
  }

  /// Próximo disparo depois de [now], ou null se desligado ou já passou (alarme de uma vez).
  DateTime? nextAfter(DateTime now) {
    if (!enabled) return null;
    if (days.isEmpty) {
      final t = DateTime.fromMillisecondsSinceEpoch(at);
      return t.isAfter(now) ? t : null;
    }
    for (var i = 0; i <= 7; i++) {
      final t = DateTime(now.year, now.month, now.day + i, hour, minute);
      if (days.contains(t.weekday) && t.isAfter(now)) return t;
    }
    return null;
  }
}
