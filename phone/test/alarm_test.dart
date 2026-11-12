import 'package:flutter_test/flutter_test.dart';
import 'package:vibealarm/alarm.dart';

void main() {
  // 2026-11-14 é sábado.
  final saturday10 = DateTime(2026, 11, 14, 10);
  const weekdays = {1, 2, 3, 4, 5};

  test('alarme de dias úteis num sábado toca na segunda', () {
    const a = Alarm(id: 'a', hour: 6, minute: 45, days: weekdays);
    expect(a.nextAfter(saturday10), DateTime(2026, 11, 16, 6, 45));
  });

  test('desligado nunca toca', () {
    const a = Alarm(id: 'a', hour: 6, minute: 45, days: weekdays, enabled: false);
    expect(a.nextAfter(saturday10), isNull);
  });

  test('alarme de uma vez usa o instante absoluto e expira', () {
    final at = Alarm.onceAt(7, 15, saturday10);
    expect(DateTime.fromMillisecondsSinceEpoch(at), DateTime(2026, 11, 15, 7, 15));
    final a = Alarm(id: 'o', hour: 7, minute: 15, at: at);
    expect(a.nextAfter(saturday10), DateTime(2026, 11, 15, 7, 15));
    expect(a.nextAfter(DateTime(2026, 11, 15, 8)), isNull);
  });

  test('JSON de ida e volta preserva tudo', () {
    const a = Alarm(id: 'x', hour: 5, minute: 50, days: {2, 4}, label: 'Academia', intensity: 2, rampSeconds: 60, walkSeconds: 30, timeoutMin: 15);
    final b = Alarm.fromJson(a.toJson());
    expect(b.toJson(), a.toJson());
  });

  test('lista antiga com steps vira segundos de caminhada', () {
    Alarm old(int steps) => Alarm.fromJson({'id': 'x', 'hour': 6, 'minute': 0, 'days': [], 'enabled': true, 'steps': steps});
    expect(old(10).walkSeconds, 20);
    expect(old(0).walkSeconds, 5);
  });
}
