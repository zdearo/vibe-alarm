import 'package:flutter/material.dart';

import 'alarm.dart';
import 'repo.dart';
import 'theme.dart';

const intensities = {0: 'Suave', 1: 'Normal', 2: 'Intenso'};
const ramps = {15: '15 s', 30: '30 s', 40: '40 s', 60: '1 min', 90: '1 min 30 s', 120: '2 min'};
const walkOptions = {10: '10 s', 20: '20 s', 30: '30 s'};
const timeouts = {5: '5 min', 10: '10 min', 15: '15 min'};

String fmtTime(int h, int m, bool h24) {
  final mm = m.toString().padLeft(2, '0');
  if (h24) return '${h.toString().padLeft(2, '0')}:$mm';
  return '${h % 12 == 0 ? 12 : h % 12}:$mm ${h < 12 ? 'AM' : 'PM'}';
}

String daysLabel(Set<int> d) {
  const names = ['seg', 'ter', 'qua', 'qui', 'sex', 'sáb', 'dom'];
  if (d.isEmpty) return 'só uma vez';
  if (d.length == 7) return 'todos os dias';
  if (d.length == 5 && d.containsAll({1, 2, 3, 4, 5})) return 'seg a sex';
  if (d.length == 2 && d.containsAll({6, 7})) return 'sáb e dom';
  return (d.toList()..sort()).map((i) => names[i - 1]).join(', ');
}

String inLabel(Duration d) {
  final min = d.inMinutes;
  if (min >= 1440) return 'em ${min ~/ 1440} d ${(min % 1440) ~/ 60} h';
  if (min >= 60) return 'em ${min ~/ 60} h ${min % 60} min';
  return 'em $min min';
}

Future<int?> pickOption(BuildContext context, String title, Map<int, String> options, int current) =>
    showModalBottomSheet<int>(
      context: context,
      backgroundColor: surface,
      builder: (_) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(s4),
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(title, style: display(24)),
            const SizedBox(height: s3),
            for (final e in options.entries)
              InkWell(
                onTap: () => Navigator.pop(context, e.key),
                child: SizedBox(
                  height: s6,
                  child: Row(children: [
                    Expanded(child: Text(e.value, style: body(16, bold: e.key == current))),
                    if (e.key == current) const Icon(Icons.check, color: text),
                  ]),
                ),
              ),
          ]),
        ),
      ),
    );

/// Cartão com linhas empilhadas, separadas pela cor do fundo.
class Group extends StatelessWidget {
  const Group({super.key, required this.children});
  final List<Widget> children;

  @override
  Widget build(BuildContext context) => ClipRRect(
        borderRadius: BorderRadius.circular(radius),
        child: Container(
          color: surface,
          child: Column(children: [
            for (var i = 0; i < children.length; i++) ...[
              if (i > 0) const Divider(height: 1, thickness: 1, color: ink),
              children[i],
            ],
          ]),
        ),
      );
}

class ValueRow extends StatelessWidget {
  const ValueRow(this.name, this.value, {super.key, this.onTap});
  final String name, value;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: s3),
          child: SizedBox(
            height: s6,
            child: Row(children: [
              Expanded(child: Text(name, style: body(16, bold: true))),
              Text(value, style: body(16, color: muted)),
              if (onTap != null) const Icon(Icons.chevron_right, size: 16, color: muted),
            ]),
          ),
        ),
      );
}

class Shell extends StatefulWidget {
  const Shell(this.repo, {super.key});
  final AlarmRepo repo;

  @override
  State<Shell> createState() => _ShellState();
}

class _ShellState extends State<Shell> with WidgetsBindingObserver {
  int tab = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.repo.refreshStatus();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) widget.repo.refreshStatus();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
        listenable: widget.repo,
        builder: (context, _) => Scaffold(
          body: SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(s4, s4, s4, 0),
              child: tab == 0 ? AlarmsBody(widget.repo) : SettingsBody(widget.repo),
            ),
          ),
          floatingActionButton: tab == 0
              ? FloatingActionButton.extended(
                  backgroundColor: accent,
                  foregroundColor: text,
                  elevation: 0,
                  shape: const StadiumBorder(),
                  icon: const Icon(Icons.add),
                  label: Text('Novo alarme', style: body(16, bold: true)),
                  onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => EditPage(repo: widget.repo))),
                )
              : null,
          bottomNavigationBar: NavigationBar(
            selectedIndex: tab,
            onDestinationSelected: (i) => setState(() => tab = i),
            destinations: const [
              NavigationDestination(icon: Icon(Icons.alarm), label: 'Alarmes'),
              NavigationDestination(icon: Icon(Icons.tune), label: 'Ajustes'),
            ],
          ),
        ),
      );
}

class SyncChip extends StatelessWidget {
  const SyncChip(this.repo, {super.key});
  final AlarmRepo repo;

  @override
  Widget build(BuildContext context) {
    final (icon, label) = repo.sendError != null
        ? (Icons.error_outline, 'Falha ao enviar')
        : !repo.connected
            ? (Icons.link_off, 'Relógio não encontrado')
            : repo.synced
                ? (Icons.check, 'Relógio sincronizado')
                : (Icons.sync, 'Enviando…');
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: s2, vertical: s1),
      decoration: BoxDecoration(color: surface, borderRadius: BorderRadius.circular(999)),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        Icon(icon, size: 16, color: text),
        const SizedBox(width: s1),
        Text(label, style: body(12, bold: true)),
      ]),
    );
  }
}

class AlarmsBody extends StatelessWidget {
  const AlarmsBody(this.repo, {super.key});
  final AlarmRepo repo;

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    DateTime? nextT;
    Alarm? nextA;
    for (final a in repo.alarms) {
      final t = a.nextAfter(now);
      if (t != null && (nextT == null || t.isBefore(nextT))) {
        nextT = t;
        nextA = a;
      }
    }
    final sorted = [...repo.alarms]..sort((a, b) => (a.hour * 60 + a.minute) - (b.hour * 60 + b.minute));
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
        Text('Alarmes', style: display(24)),
        SyncChip(repo),
      ]),
      const SizedBox(height: s1),
      Text(
        nextA == null ? 'Nenhum alarme ativo' : 'Próximo: ${fmtTime(nextA.hour, nextA.minute, repo.hour24)} · ${inLabel(nextT!.difference(now))}',
        style: body(14, color: muted),
      ),
      const SizedBox(height: s4),
      Expanded(
        child: sorted.isEmpty
            ? Text('Toque em “Novo alarme” para criar o primeiro.', style: body(16, color: muted))
            : ListView.separated(
                padding: const EdgeInsets.only(bottom: 96),
                itemCount: sorted.length,
                separatorBuilder: (_, _) => const SizedBox(height: s2),
                itemBuilder: (_, i) => _AlarmTile(repo, sorted[i]),
              ),
      ),
    ]);
  }
}

class _AlarmTile extends StatelessWidget {
  const _AlarmTile(this.repo, this.a);
  final AlarmRepo repo;
  final Alarm a;

  @override
  Widget build(BuildContext context) {
    final time = fmtTime(a.hour, a.minute, repo.hour24);
    return Material(
      color: surface,
      borderRadius: BorderRadius.circular(radius),
      child: InkWell(
        borderRadius: BorderRadius.circular(radius),
        onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => EditPage(repo: repo, alarm: a))),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(s3, s3, s3, s3),
          child: Row(children: [
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(time, style: display(40, color: a.enabled ? text : muted)),
                const SizedBox(height: s1),
                Text([if (a.label.isNotEmpty) a.label, daysLabel(a.days)].join(' · '), style: body(14, color: muted)),
              ]),
            ),
            Semantics(
              label: 'Ativar alarme das $time',
              child: Switch(value: a.enabled, onChanged: (_) => repo.toggle(a.id)),
            ),
          ]),
        ),
      ),
    );
  }
}

class EditPage extends StatefulWidget {
  const EditPage({super.key, required this.repo, this.alarm});
  final AlarmRepo repo;
  final Alarm? alarm;

  @override
  State<EditPage> createState() => _EditPageState();
}

class _EditPageState extends State<EditPage> {
  late Alarm a = widget.alarm ?? widget.repo.newAlarm();
  late final label = TextEditingController(text: a.label);

  @override
  void dispose() {
    label.dispose();
    super.dispose();
  }

  Future<void> _pickTime() async {
    final t = await showTimePicker(context: context, initialTime: TimeOfDay(hour: a.hour, minute: a.minute));
    if (t != null) setState(() => a = a.copyWith(hour: t.hour, minute: t.minute));
  }

  Future<void> _pick(String title, Map<int, String> options, int current, Alarm Function(int) apply) async {
    final v = await pickOption(context, title, options, current);
    if (v != null) setState(() => a = apply(v));
  }

  Future<void> _save() async {
    await widget.repo.upsert(a.copyWith(label: label.text.trim()));
    if (mounted) Navigator.pop(context);
  }

  Future<void> _delete() async {
    await widget.repo.remove(a.id);
    if (mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    const letters = ['D', 'S', 'T', 'Q', 'Q', 'S', 'S'];
    const weekdays = [7, 1, 2, 3, 4, 5, 6];
    const dayNames = ['Domingo', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado'];
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(s4),
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            Expanded(
              child: SingleChildScrollView(
                child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                  Align(
                    alignment: Alignment.centerLeft,
                    child: TextButton(
                      onPressed: () => Navigator.pop(context),
                      style: TextButton.styleFrom(foregroundColor: muted, minimumSize: const Size(0, s6), padding: EdgeInsets.zero),
                      child: Text('Cancelar', style: body(16, color: muted)),
                    ),
                  ),
                  Text(widget.alarm == null ? 'Novo alarme' : 'Editar alarme', style: display(24)),
                  const SizedBox(height: s4),
                  Semantics(
                    button: true,
                    label: 'Horário ${fmtTime(a.hour, a.minute, widget.repo.hour24)}. Toque para mudar',
                    child: InkWell(
                      onTap: _pickTime,
                      borderRadius: BorderRadius.circular(radius),
                      child: Container(
                        padding: const EdgeInsets.symmetric(vertical: s4),
                        decoration: BoxDecoration(color: surface, borderRadius: BorderRadius.circular(radius)),
                        child: Column(children: [
                          Text(fmtTime(a.hour, a.minute, widget.repo.hour24), style: display(72)),
                          const SizedBox(height: s2),
                          Text('Toque para mudar', style: body(14, color: muted)),
                        ]),
                      ),
                    ),
                  ),
                  const SizedBox(height: s4),
                  Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                    for (var i = 0; i < 7; i++)
                      Semantics(
                        label: dayNames[i],
                        selected: a.days.contains(weekdays[i]),
                        button: true,
                        child: InkWell(
                          customBorder: const CircleBorder(),
                          onTap: () => setState(() {
                            final d = {...a.days};
                            d.contains(weekdays[i]) ? d.remove(weekdays[i]) : d.add(weekdays[i]);
                            a = a.copyWith(days: d);
                          }),
                          child: Container(
                            width: 44,
                            height: 44,
                            alignment: Alignment.center,
                            decoration: BoxDecoration(shape: BoxShape.circle, color: a.days.contains(weekdays[i]) ? accent : surface),
                            child: Text(letters[i], style: body(14, color: a.days.contains(weekdays[i]) ? text : muted, bold: true)),
                          ),
                        ),
                      ),
                  ]),
                  const SizedBox(height: s4),
                  Container(
                    height: s6,
                    padding: const EdgeInsets.symmetric(horizontal: s3),
                    decoration: BoxDecoration(color: surface, borderRadius: BorderRadius.circular(radius)),
                    child: Row(children: [
                      Text('Nome', style: body(16, color: muted)),
                      Expanded(
                        child: TextField(
                          controller: label,
                          textAlign: TextAlign.end,
                          style: body(16, bold: true),
                          maxLength: 30,
                          decoration: const InputDecoration(border: InputBorder.none, counterText: '', isDense: true),
                        ),
                      ),
                    ]),
                  ),
                  const SizedBox(height: s4),
                  Text('DESPERTAR', style: body(12, color: muted)),
                  const SizedBox(height: s2),
                  Group(children: [
                    ValueRow('Intensidade', intensities[a.intensity]!, onTap: () => _pick('Intensidade', intensities, a.intensity, (v) => a.copyWith(intensity: v))),
                    ValueRow('Subida', ramps[a.rampSeconds] ?? '${a.rampSeconds} s', onTap: () => _pick('Subida até o máximo', ramps, a.rampSeconds, (v) => a.copyWith(rampSeconds: v))),
                    ValueRow('Caminhar por', walkOptions[a.walkSeconds] ?? '${a.walkSeconds} s', onTap: () => _pick('Caminhar por', walkOptions, a.walkSeconds, (v) => a.copyWith(walkSeconds: v))),
                    ValueRow('Limite de segurança', timeouts[a.timeoutMin] ?? '${a.timeoutMin} min', onTap: () => _pick('Limite de segurança', timeouts, a.timeoutMin, (v) => a.copyWith(timeoutMin: v))),
                  ]),
                  if (widget.alarm != null)
                    TextButton(
                      onPressed: _delete,
                      style: TextButton.styleFrom(foregroundColor: muted, minimumSize: const Size(0, s6)),
                      child: Text('Excluir alarme', style: body(16, color: muted, bold: true)),
                    ),
                ]),
              ),
            ),
            const SizedBox(height: s3),
            FilledButton(
              onPressed: _save,
              style: FilledButton.styleFrom(backgroundColor: accent, foregroundColor: text, minimumSize: const Size.fromHeight(56), shape: const StadiumBorder()),
              child: Text('Salvar alarme', style: body(16, bold: true)),
            ),
          ]),
        ),
      ),
    );
  }
}

class SettingsBody extends StatelessWidget {
  const SettingsBody(this.repo, {super.key});
  final AlarmRepo repo;

  @override
  Widget build(BuildContext context) {
    final problems = repo.problems;
    return ListView(padding: const EdgeInsets.only(bottom: s4), children: [
      Text('Ajustes', style: display(24)),
      const SizedBox(height: s3),
      // O único bloco em accent: algo que pode fazer o alarme falhar.
      if (problems.isNotEmpty) ...[
        Container(
          padding: const EdgeInsets.all(s3),
          decoration: BoxDecoration(color: accent, borderRadius: BorderRadius.circular(radius)),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              const Icon(Icons.warning_amber, color: text),
              const SizedBox(width: s2),
              Text('Atenção no relógio', style: body(16, bold: true)),
            ]),
            for (final p in problems) ...[
              const SizedBox(height: s1),
              Text(p, style: body(14)),
            ],
          ]),
        ),
        const SizedBox(height: s3),
      ],
      Container(
        padding: const EdgeInsets.all(s3),
        decoration: BoxDecoration(color: surface, borderRadius: BorderRadius.circular(radius)),
        child: Column(children: [
          Row(children: [
            Container(
              width: s6,
              height: s6,
              decoration: const BoxDecoration(shape: BoxShape.circle, color: ink),
              child: const Icon(Icons.watch_outlined, color: text),
            ),
            const SizedBox(width: s3),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(repo.watchName ?? 'Relógio', style: body(16, bold: true)),
                Text(
                  repo.connected ? 'Conectado${repo.level != null ? ' · bateria ${repo.level}%' : ''}' : 'Não encontrado',
                  style: body(14, color: muted),
                ),
              ]),
            ),
          ]),
          const SizedBox(height: s3),
          Row(children: [
            Expanded(
              child: Text(
                repo.sendError ?? (repo.synced ? 'Lista confirmada pelo relógio' : 'Aguardando confirmação'),
                style: body(14, color: muted),
              ),
            ),
            FilledButton(
              onPressed: repo.sync,
              style: FilledButton.styleFrom(backgroundColor: ink, foregroundColor: text, minimumSize: const Size(0, 44), shape: const StadiumBorder()),
              child: Text('Sincronizar', style: body(14, bold: true)),
            ),
          ]),
        ]),
      ),
      const SizedBox(height: s4),
      Text('GERAL', style: body(12, color: muted)),
      const SizedBox(height: s2),
      Group(children: [
        ValueRow('Padrões de novo alarme', '', onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => DefaultsPage(repo)))),
        ValueRow('Formato de hora', repo.hour24 ? '24 h' : '12 h', onTap: () => repo.setHour24(!repo.hour24)),
      ]),
    ]);
  }
}

class DefaultsPage extends StatelessWidget {
  const DefaultsPage(this.repo, {super.key});
  final AlarmRepo repo;

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(backgroundColor: ink, title: Text('Padrões de novo alarme', style: display(24))),
        body: ListenableBuilder(
          listenable: repo,
          builder: (context, _) {
            Future<void> pick(String title, Map<int, String> opts, int cur, String key) async {
              final v = await pickOption(context, title, opts, cur);
              if (v != null) await repo.setDefault(key, v);
            }

            return Padding(
              padding: const EdgeInsets.all(s4),
              child: Group(children: [
                ValueRow('Intensidade', intensities[repo.defIntensity]!, onTap: () => pick('Intensidade', intensities, repo.defIntensity, 'defIntensity')),
                ValueRow('Subida', ramps[repo.defRamp] ?? '${repo.defRamp} s', onTap: () => pick('Subida até o máximo', ramps, repo.defRamp, 'defRamp')),
                ValueRow('Caminhar por', walkOptions[repo.defWalk] ?? '${repo.defWalk} s', onTap: () => pick('Caminhar por', walkOptions, repo.defWalk, 'defWalk')),
                ValueRow('Limite de segurança', timeouts[repo.defTimeout] ?? '${repo.defTimeout} min', onTap: () => pick('Limite de segurança', timeouts, repo.defTimeout, 'defTimeout')),
              ]),
            );
          },
        ),
      );
}
