import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'alarm.dart';
import 'check.dart';

/// Lista de alarmes (fonte da verdade), padrões, e o envio ao relógio pela Data Layer.
class AlarmRepo extends ChangeNotifier {
  AlarmRepo._(this._p, this.alarms);

  static const _ch = MethodChannel('vibealarm/sync');
  final SharedPreferences _p;
  List<Alarm> alarms;

  List<String> nodes = [];
  Map<String, dynamic>? ack;
  String? sendError;

  static Future<AlarmRepo> load() async {
    final p = await SharedPreferences.getInstance();
    final now = DateTime.now().millisecondsSinceEpoch;
    List<Alarm> list;
    try {
      list = (jsonDecode(p.getString('alarms') ?? '[]') as List)
          .map((e) => Alarm.fromJson(e as Map<String, dynamic>))
          .toList();
    } catch (_) {
      list =
          []; // dados locais corrompidos: começa vazio em vez de travar o app
    }
    // Alarme de uma vez que já tocou aparece desligado.
    list = [
      for (final a in list)
        a.days.isEmpty && a.enabled && a.at <= now
            ? a.copyWith(enabled: false)
            : a,
    ];
    return AlarmRepo._(p, list);
  }

  // Padrões de novo alarme e preferências.
  int get defIntensity => _p.getInt('defIntensity') ?? 1;
  int get defRamp => _p.getInt('defRamp') ?? 40;
  int get defWalk => _p.getInt('defWalk') ?? 20;
  int get defTimeout => _p.getInt('defTimeout') ?? 10;
  bool get hour24 => _p.getBool('hour24') ?? true;

  Future<void> setDefault(String key, int v) async {
    await _p.setInt(key, v);
    notifyListeners();
  }

  Future<void> setHour24(bool v) async {
    await _p.setBool('hour24', v);
    notifyListeners();
  }

  Alarm newAlarm() => Alarm(
    id: DateTime.now().microsecondsSinceEpoch.toString(),
    hour: 7,
    minute: 0,
    days: const {1, 2, 3, 4, 5},
    intensity: defIntensity,
    rampSeconds: defRamp,
    walkSeconds: defWalk,
    timeoutMin: defTimeout,
  );

  Future<void> upsert(Alarm a) {
    final fixed = a.days.isEmpty && a.enabled
        ? a.copyWith(at: Alarm.onceAt(a.hour, a.minute, DateTime.now()))
        : a;
    final i = alarms.indexWhere((x) => x.id == a.id);
    alarms = [...alarms];
    i < 0 ? alarms.add(fixed) : alarms[i] = fixed;
    return _commit();
  }

  Future<void> toggle(String id) {
    final a = alarms.firstWhere((x) => x.id == id);
    return upsert(a.copyWith(enabled: !a.enabled));
  }

  Future<void> remove(String id) {
    alarms = alarms.where((x) => x.id != id).toList();
    return _commit();
  }

  Future<void> _commit() async {
    await _p.setString(
      'alarms',
      jsonEncode(alarms.map((a) => a.toJson()).toList()),
    );
    notifyListeners();
    await sync();
  }

  /// Envia a lista inteira (sempre substitui a anterior no relógio).
  Future<void> sync() async {
    final ts = DateTime.now().millisecondsSinceEpoch;
    try {
      await _ch.invokeMethod('send', {
        'json': jsonEncode(alarms.map((a) => a.toJson()).toList()),
        'ts': ts,
      });
      await _p.setInt('lastSentTs', ts);
      sendError = null;
    } on PlatformException catch (e) {
      sendError = e.message ?? 'Falha ao enviar';
    } on MissingPluginException {
      sendError = 'Sem Data Layer neste aparelho';
    }
    notifyListeners();
    await refreshStatus();
  }

  /// Botão de um problema da checagem: reenvia a lista, abre o Bluetooth do celular ou abre a tela no relógio.
  Future<void> fix(Fix f) async {
    if (f == Fix.resend) return sync();
    try {
      await _ch.invokeMethod(
        f == Fix.bluetooth ? 'openBluetooth' : 'openOnWatch',
        {'kind': f.name},
      );
      sendError = null;
    } on PlatformException catch (e) {
      sendError = e.message ?? 'Não foi possível abrir';
    } on MissingPluginException {
      sendError = 'Sem Data Layer neste aparelho';
    }
    notifyListeners();
  }

  Future<void> refreshStatus() async {
    try {
      final r = Map<String, dynamic>.from(
        await _ch.invokeMethod('status') as Map,
      );
      nodes = List<String>.from(r['nodes'] as List);
      ack = r['ack'] == null
          ? null
          : Map<String, dynamic>.from(r['ack'] as Map);
    } on PlatformException {
      nodes = [];
      ack = null;
    } on MissingPluginException {
      nodes = [];
      ack = null;
    }
    notifyListeners();
  }

  bool get connected => nodes.isNotEmpty;
  String? get watchName => nodes.isEmpty ? null : nodes.first;
  int? get level => ack?['level'] as int?;

  /// O relógio confirmou exatamente a última lista enviada.
  bool get synced {
    final sent = _p.getInt('lastSentTs') ?? 0;
    return sent != 0 && ack?['sentTs'] == sent;
  }

  /// Hora em que o relógio mandou o estado (bateria e permissões são desse momento).
  DateTime? get ackAt => ack?['ts'] == null
      ? null
      : DateTime.fromMillisecondsSinceEpoch(ack!['ts'] as int);

  List<Check> get checks => nightChecks(
    connected: connected,
    synced: synced,
    ack: ack,
    suspendedOk: suspendedOk,
  );

  bool get suspendedOk => _p.getBool('suspendedOk') ?? false;

  Future<void> confirmSuspended() async {
    await _p.setBool('suspendedOk', true);
    notifyListeners();
  }
}
