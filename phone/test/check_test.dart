import 'package:flutter_test/flutter_test.dart';
import 'package:vibealarm/check.dart';

void main() {
  List<Level> levels(List<Check> c) => c.map((x) => x.level).toList();

  test('relógio ok em tudo não tem nada fora de ok', () {
    final c = nightChecks(connected: true, synced: true, ack: {
      'level': 82, 'activity': true, 'overlay': true, 'battery': true, 'notifications': true, 'scan': true,
    });
    expect(levels(c).every((l) => l == Level.ok), isTrue);
    expect(c.length, 8);
  });

  test('desconectado e sem confirmação falham', () {
    final c = nightChecks(connected: false, synced: false, ack: null);
    expect(levels(c), [Level.fail, Level.fail]);
  });

  test('ack de relógio antigo não acusa campos que não mandou', () {
    final c = nightChecks(connected: true, synced: true, ack: {'level': 50, 'activity': true, 'battery': true});
    expect(c.map((x) => x.name), isNot(contains('Tela do alarme')));
    expect(c.map((x) => x.name), isNot(contains('Trava pelo celular')));
  });

  test('bateria baixa e tela bloqueada', () {
    final c = nightChecks(connected: true, synced: true, ack: {'level': 20, 'overlay': false});
    expect(c.firstWhere((x) => x.name == 'Bateria do relógio').level, Level.warn);
    expect(c.firstWhere((x) => x.name == 'Tela do alarme').level, Level.fail);
  });

  test('trava desligada não é problema', () {
    final c = nightChecks(connected: true, synced: true, ack: {'scan': false});
    expect(c.firstWhere((x) => x.name == 'Trava pelo celular').level, Level.ok);
  });
}
