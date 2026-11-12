import 'package:flutter_test/flutter_test.dart';
import 'package:vibealarm/check.dart';

void main() {
  fixTests();
  suspendedTests();
  List<Level> levels(List<Check> c) => c.map((x) => x.level).toList();

  test('relógio ok em tudo não tem nada fora de ok', () {
    final c = nightChecks(
      suspendedOk: true,
      connected: true,
      synced: true,
      ack: {
        'level': 82,
        'activity': true,
        'overlay': true,
        'powersave': false,
        'notifications': true,
        'scan': true,
      },
    );
    expect(levels(c).every((l) => l == Level.ok), isTrue);
    expect(c.length, 9);
  });

  test('relógio carregando falha: fora do pulso o alarme não toca', () {
    final c = nightChecks(
      suspendedOk: true,
      connected: true,
      synced: true,
      ack: {'level': 82, 'charging': true},
    );
    expect(c.where((x) => x.level == Level.fail).map((x) => x.value), [
      'carregando',
    ]);
  });

  test('desconectado e sem confirmação falham', () {
    final c = nightChecks(
      suspendedOk: true,
      connected: false,
      synced: false,
      ack: null,
    );
    expect(levels(c).where((l) => l != Level.ok), [Level.fail, Level.fail]);
  });

  test('ack de relógio antigo não acusa campos que não mandou', () {
    final c = nightChecks(
      suspendedOk: true,
      connected: true,
      synced: true,
      ack: {'level': 50, 'activity': true},
    );
    expect(c.map((x) => x.name), isNot(contains('Tela do alarme')));
    expect(c.map((x) => x.name), isNot(contains('Trava pelo celular')));
  });

  test('bateria baixa e tela bloqueada', () {
    final c = nightChecks(
      suspendedOk: true,
      connected: true,
      synced: true,
      ack: {'level': 20, 'overlay': false},
    );
    expect(
      c.firstWhere((x) => x.name == 'Bateria do relógio').level,
      Level.warn,
    );
    expect(c.firstWhere((x) => x.name == 'Tela do alarme').level, Level.fail);
  });

  test('trava desligada não é problema', () {
    final c = nightChecks(
      suspendedOk: true,
      connected: true,
      synced: true,
      ack: {'scan': false},
    );
    expect(c.firstWhere((x) => x.name == 'Trava pelo celular').level, Level.ok);
  });
}

void fixTests() {
  test('cada problema vem com o botão que o corrige', () {
    final c = nightChecks(
      suspendedOk: true,
      connected: false,
      synced: false,
      ack: null,
    );
    expect(c.where((x) => x.level != Level.ok).map((x) => x.action), [
      Fix.bluetooth,
      Fix.resend,
    ]);
    final w = nightChecks(
      suspendedOk: true,
      connected: true,
      synced: true,
      ack: {
        'activity': false,
        'overlay': false,
        'powersave': true,
        'notifications': false,
        'level': 20,
      },
    );
    expect(
      w
          .where((x) => x.level != Level.ok && x.action != null)
          .every((x) => x.action!.onWatch),
      isTrue,
    );
    expect(
      w.firstWhere((x) => x.name == 'Bateria do relógio').action,
      isNull,
    ); // carregar não é com o app
  });
}

void suspendedTests() {
  test('lista de suspensos pede confirmação até o usuário dizer que tirou', () {
    final pending = nightChecks(
      suspendedOk: false,
      connected: true,
      synced: true,
      ack: null,
    );
    expect(
      pending.firstWhere((x) => x.name == 'Aplicativos suspensos').action,
      Fix.suspended,
    );
    final done = nightChecks(
      suspendedOk: true,
      connected: true,
      synced: true,
      ack: null,
    );
    expect(
      done.firstWhere((x) => x.name == 'Aplicativos suspensos').level,
      Level.ok,
    );
  });
}
