import 'package:flutter/material.dart';

import 'alarm.dart';
import 'check.dart';
import 'repo.dart';
import 'theme.dart';

const intensities = {0: 'Suave', 1: 'Normal', 2: 'Intenso'};
const ramps = {
  15: '15 s',
  30: '30 s',
  40: '40 s',
  60: '1 min',
  90: '1 min 30 s',
  120: '2 min',
};
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

Future<int?> pickOption(
  BuildContext context,
  String title,
  Map<int, String> options,
  int current,
) => showModalBottomSheet<int>(
  context: context,
  backgroundColor: surface,
  shape: const RoundedRectangleBorder(
    borderRadius: BorderRadius.vertical(top: Radius.circular(radius)),
  ),
  builder: (_) => SafeArea(
    child: Padding(
      padding: const EdgeInsets.all(s4),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: display(24)),
          const SizedBox(height: s3),
          for (final e in options.entries)
            InkWell(
              onTap: () => Navigator.pop(context, e.key),
              child: ConstrainedBox(
                constraints: const BoxConstraints(minHeight: s6),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        e.value,
                        style: body(16, bold: e.key == current),
                      ),
                    ),
                    if (e.key == current) const Icon(Icons.check, color: text),
                  ],
                ),
              ),
            ),
        ],
      ),
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
      child: Column(
        children: [
          for (var i = 0; i < children.length; i++) ...[
            if (i > 0) const Divider(height: 1, thickness: 1, color: ink),
            children[i],
          ],
        ],
      ),
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
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: s6),
        child: Row(
          children: [
            Expanded(child: Text(name, style: body(16, bold: true))),
            Text(value, style: body(16, color: muted)),
            if (onTap != null)
              const Icon(Icons.chevron_right, size: 16, color: muted),
          ],
        ),
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
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.repo.resync(); // reenviar faz o relógio responder com o estado de agora (carregando, bateria)
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) widget.repo.resync();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.repo,
    builder: (context, _) => Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(s4, s3, s4, 0),
          child: AlarmsBody(widget.repo),
        ),
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(s4, s2, s4, s3),
          child: PrimaryButton(
            'Novo alarme',
            () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => EditPage(repo: widget.repo)),
            ),
          ),
        ),
      ),
    ),
  );
}

/// A única ação em accent de cada tela.
class PrimaryButton extends StatelessWidget {
  const PrimaryButton(this.label, this.onPressed, {super.key});
  final String label;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) => FilledButton(
    onPressed: onPressed,
    style: FilledButton.styleFrom(
      backgroundColor: accent,
      foregroundColor: text,
      minimumSize: const Size.fromHeight(56),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(s2)),
    ),
    child: Text(label, style: body(16, bold: true)),
  );
}

/// Aviso de boa-noite sob o próximo alarme: uma pílula discreta. Com problema, o ponto pulsa e o texto vai em negrito
/// (urgência sem poluir); o detalhe e as correções ficam em [NightPage].
class NightCheck extends StatefulWidget {
  const NightCheck(this.repo, {super.key});
  final AlarmRepo repo;

  @override
  State<NightCheck> createState() => _NightCheckState();
}

class _NightCheckState extends State<NightCheck>
    with SingleTickerProviderStateMixin {
  late final _pulse = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 900),
  )..repeat(reverse: true);

  @override
  void dispose() {
    _pulse.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final repo = widget.repo;
    final n = repo.checks.where((c) => c.level != Level.ok).length;
    final level = repo.level;
    final summary = n == 0
        ? 'Tudo pronto${level != null ? ' · relógio $level%' : ''}'
        : n == 1
        ? '1 ponto pode fazer o alarme falhar'
        : '$n pontos podem fazer o alarme falhar';
    final dot = Container(
      width: 8,
      height: 8,
      decoration: const BoxDecoration(shape: BoxShape.circle, color: text),
    );
    return Semantics(
      button: true,
      label: '$summary. Toque para ver a checagem completa',
      excludeSemantics: true,
      child: Material(
        color: surface,
        shape: const StadiumBorder(),
        child: InkWell(
          customBorder: const StadiumBorder(),
          onTap: () => Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => NightPage(repo)),
          ),
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 44),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: s3),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (n > 0) ...[
                    MediaQuery.disableAnimationsOf(context)
                        ? dot
                        : FadeTransition(
                            opacity: Tween(
                              begin: 1.0,
                              end: 0.25,
                            ).animate(_pulse),
                            child: dot,
                          ),
                    const SizedBox(width: s2),
                  ],
                  Text(
                    summary,
                    style: body(14, color: n == 0 ? muted : text, bold: n > 0),
                  ),
                  const SizedBox(width: s1),
                  const Icon(Icons.chevron_right, size: 16, color: muted),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Antes de dormir: o que pode fazer o alarme de amanhã falhar, cada problema com o botão que corrige.
class NightPage extends StatefulWidget {
  const NightPage(this.repo, {super.key});
  final AlarmRepo repo;

  @override
  State<NightPage> createState() => _NightPageState();
}

class _NightPageState extends State<NightPage> {
  Fix? _busy;
  final _asked =
      <
        Fix
      >{}; // aberto no relógio; só "Verificar de novo" confirma que resolveu

  Future<void> _run(Fix f) async {
    setState(() => _busy = f);
    await widget.repo.fix(f);
    if (!mounted) return;
    setState(() {
      _busy = null;
      if (f.onWatch && widget.repo.sendError == null) _asked.add(f);
    });
  }

  String _label(Fix f) {
    if (_busy == f) return f == Fix.resend ? 'Reenviando…' : 'Abrindo…';
    return _asked.contains(f) ? 'Aberto no relógio' : f.label;
  }

  // A Column do Group centraliza filhos estreitos: a largura cheia mantém todas as linhas coladas à esquerda.
  Widget _issue(Check c) => SizedBox(
    width: double.infinity,
    child: Padding(
      padding: const EdgeInsets.all(s3),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('${c.name}: ${c.value}', style: body(16, bold: true)),
          if (c.fix != null) ...[
            const SizedBox(height: s1),
            Text(c.fix!, style: body(14, color: muted)),
          ],
          if (c.action != null) ...[
            const SizedBox(height: s3),
            Wrap(
              spacing: s2,
              runSpacing: s2,
              children: [
                FilledButton(
                  onPressed: _busy == null ? () => _run(c.action!) : null,
                  style: FilledButton.styleFrom(
                    backgroundColor: ink,
                    foregroundColor: text,
                    disabledBackgroundColor: ink,
                    disabledForegroundColor: muted,
                    minimumSize: const Size(0, 44),
                    padding: const EdgeInsets.symmetric(horizontal: s3),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(s2),
                    ),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        _label(c.action!),
                        style: body(
                          14,
                          bold: true,
                          color: _busy == c.action ? muted : text,
                        ),
                      ),
                      if (c.action != Fix.resend) ...[
                        const SizedBox(width: s2),
                        const Icon(Icons.north_east, size: 16),
                      ],
                    ],
                  ),
                ),
                // A lista de suspensos não pode ser lida: quem tirou o app dela confirma aqui.
                if (c.action == Fix.suspended)
                  TextButton(
                    onPressed: widget.repo.confirmSuspended,
                    style: TextButton.styleFrom(
                      foregroundColor: text,
                      minimumSize: const Size(0, 44),
                      padding: const EdgeInsets.symmetric(horizontal: s3),
                    ),
                    child: Text(
                      'Já tirei da lista',
                      style: body(14, bold: true),
                    ),
                  ),
              ],
            ),
          ],
        ],
      ),
    ),
  );

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.repo,
    builder: (context, _) {
      final repo = widget.repo;
      final issues = repo.checks.where((c) => c.level != Level.ok).toList();
      final oks = repo.checks.where((c) => c.level == Level.ok).toList();
      final at = repo.ackAt;
      final seen = at == null
          ? 'O relógio ainda não respondeu.'
          : 'Estado enviado pelo relógio às ${fmtTime(at.hour, at.minute, repo.hour24)}.';
      return Scaffold(
        appBar: AppBar(
          backgroundColor: ink,
          title: Text('Antes de dormir', style: display(24)),
        ),
        body: ListView(
          padding: const EdgeInsets.all(s4),
          children: [
            Text(
              issues.isEmpty ? 'Tudo pronto. $seen' : seen,
              style: body(14, color: muted),
            ),
            if (issues.isNotEmpty) ...[
              const SizedBox(height: s4),
              Text(
                'Precisa de atenção',
                style: body(14, color: muted, bold: true),
              ),
              const SizedBox(height: s2),
              Group(children: [for (final c in issues) _issue(c)]),
            ],
            if (oks.isNotEmpty) ...[
              const SizedBox(height: s4),
              Text('Tudo certo', style: body(14, color: muted, bold: true)),
              const SizedBox(height: s2),
              Group(children: [for (final c in oks) ValueRow(c.name, c.value)]),
            ],
            if (repo.sendError != null) ...[
              const SizedBox(height: s3),
              Text(repo.sendError!, style: body(14, color: muted)),
            ],
          ],
        ),
        // Reenviar a lista faz o relógio responder de novo com bateria e permissões atuais.
        bottomNavigationBar: SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(s4, s2, s4, s3),
            child: PrimaryButton('Verificar de novo', repo.resync),
          ),
        ),
      );
    },
  );
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
    final sorted = [...repo.alarms]
      ..sort((a, b) => (a.hour * 60 + a.minute) - (b.hour * 60 + b.minute));
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(child: Text('Alarmes', style: display(24))),
            IconButton(
              tooltip: 'Ajustes',
              alignment: Alignment.centerRight,
              padding: EdgeInsets.zero,
              icon: const Icon(Icons.settings_outlined, color: muted),
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => SettingsPage(repo)),
              ),
            ),
          ],
        ),
        const SizedBox(height: s5),
        // O próximo disparo é o foco da tela, centralizado: maior que qualquer item da lista.
        SizedBox(
          width: double.infinity,
          child: Column(
            children: [
              if (nextA != null) ...[
                Text(
                  'Próximo · ${inLabel(nextT!.difference(now))}',
                  style: body(14, color: muted),
                ),
                const SizedBox(height: s2),
                Text(
                  fmtTime(nextA.hour, nextA.minute, repo.hour24),
                  style: display(72),
                ),
              ] else
                Text('Nenhum alarme ativo', style: body(16, color: muted)),
              const SizedBox(height: s3),
              NightCheck(repo),
            ],
          ),
        ),
        const SizedBox(height: s4),
        const Divider(height: 1, thickness: 1, color: surface),
        Expanded(
          child: sorted.isEmpty
              ? Padding(
                  padding: const EdgeInsets.only(top: s4),
                  child: Text(
                    'Nenhum alarme ainda.',
                    style: body(16, color: muted),
                  ),
                )
              : ListView.separated(
                  padding: const EdgeInsets.only(bottom: s4),
                  itemCount: sorted.length,
                  separatorBuilder: (_, _) =>
                      const Divider(height: 1, thickness: 1, color: surface),
                  itemBuilder: (_, i) => _AlarmTile(repo, sorted[i]),
                ),
        ),
      ],
    );
  }
}

class _AlarmTile extends StatelessWidget {
  const _AlarmTile(this.repo, this.a);
  final AlarmRepo repo;
  final Alarm a;

  @override
  Widget build(BuildContext context) {
    final time = fmtTime(a.hour, a.minute, repo.hour24);
    // Linha da lista, sem cartão: a hora carrega a linha, o resto fica em muted.
    return InkWell(
      onTap: () => Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => EditPage(repo: repo, alarm: a),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: s3),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    time,
                    style: display(40, color: a.enabled ? text : muted),
                  ),
                  const SizedBox(height: s1),
                  Text(
                    [
                      if (a.label.isNotEmpty) a.label,
                      daysLabel(a.days),
                    ].join(' · '),
                    style: body(14, color: muted),
                  ),
                ],
              ),
            ),
            Semantics(
              label: 'Ativar alarme das $time',
              child: Switch(
                value: a.enabled,
                onChanged: (_) => repo.toggle(a.id),
              ),
            ),
          ],
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

  Future<void> _pick(
    String title,
    Map<int, String> options,
    int current,
    Alarm Function(int) apply,
  ) async {
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
    const dayNames = [
      'Domingo',
      'Segunda',
      'Terça',
      'Quarta',
      'Quinta',
      'Sexta',
      'Sábado',
    ];
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(s4),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Expanded(
                child: SingleChildScrollView(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Align(
                        alignment: Alignment.centerLeft,
                        child: TextButton(
                          onPressed: () => Navigator.pop(context),
                          style: TextButton.styleFrom(
                            foregroundColor: muted,
                            minimumSize: const Size(0, s6),
                            padding: EdgeInsets.zero,
                          ),
                          child: Text(
                            'Cancelar',
                            style: body(16, color: muted),
                          ),
                        ),
                      ),
                      Text(
                        widget.alarm == null ? 'Novo alarme' : 'Editar alarme',
                        style: display(24),
                      ),
                      const SizedBox(height: s4),
                      _TimeWheels(
                        hour: a.hour,
                        minute: a.minute,
                        onChanged: (h, m) =>
                            setState(() => a = a.copyWith(hour: h, minute: m)),
                      ),
                      const SizedBox(height: s4),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          for (var i = 0; i < 7; i++)
                            Semantics(
                              label: dayNames[i],
                              selected: a.days.contains(weekdays[i]),
                              button: true,
                              child: InkWell(
                                customBorder: const CircleBorder(),
                                onTap: () => setState(() {
                                  final d = {...a.days};
                                  d.contains(weekdays[i])
                                      ? d.remove(weekdays[i])
                                      : d.add(weekdays[i]);
                                  a = a.copyWith(days: d);
                                }),
                                child: Container(
                                  width: 44,
                                  height: 44,
                                  alignment: Alignment.center,
                                  decoration: BoxDecoration(
                                    shape: BoxShape.circle,
                                    color: a.days.contains(weekdays[i])
                                        ? accent
                                        : surface,
                                  ),
                                  child: Text(
                                    letters[i],
                                    style: body(
                                      14,
                                      color: a.days.contains(weekdays[i])
                                          ? text
                                          : muted,
                                      bold: true,
                                    ),
                                  ),
                                ),
                              ),
                            ),
                        ],
                      ),
                      const SizedBox(height: s4),
                      Container(
                        constraints: const BoxConstraints(minHeight: s6),
                        padding: const EdgeInsets.symmetric(horizontal: s3),
                        decoration: BoxDecoration(
                          color: surface,
                          borderRadius: BorderRadius.circular(radius),
                        ),
                        child: Row(
                          children: [
                            Text('Nome', style: body(16, color: muted)),
                            Expanded(
                              child: TextField(
                                controller: label,
                                textAlign: TextAlign.end,
                                style: body(16, bold: true),
                                maxLength: 30,
                                decoration: const InputDecoration(
                                  border: InputBorder.none,
                                  counterText: '',
                                  isDense: true,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: s4),
                      Text(
                        'Despertar',
                        style: body(14, color: muted, bold: true),
                      ),
                      const SizedBox(height: s2),
                      Group(
                        children: [
                          ValueRow(
                            'Intensidade',
                            intensities[a.intensity]!,
                            onTap: () => _pick(
                              'Intensidade',
                              intensities,
                              a.intensity,
                              (v) => a.copyWith(intensity: v),
                            ),
                          ),
                          ValueRow(
                            'Subida',
                            ramps[a.rampSeconds] ?? '${a.rampSeconds} s',
                            onTap: () => _pick(
                              'Subida até o máximo',
                              ramps,
                              a.rampSeconds,
                              (v) => a.copyWith(rampSeconds: v),
                            ),
                          ),
                          ValueRow(
                            'Caminhar por',
                            walkOptions[a.walkSeconds] ?? '${a.walkSeconds} s',
                            onTap: () => _pick(
                              'Caminhar por',
                              walkOptions,
                              a.walkSeconds,
                              (v) => a.copyWith(walkSeconds: v),
                            ),
                          ),
                          ValueRow(
                            'Limite de segurança',
                            timeouts[a.timeoutMin] ?? '${a.timeoutMin} min',
                            onTap: () => _pick(
                              'Limite de segurança',
                              timeouts,
                              a.timeoutMin,
                              (v) => a.copyWith(timeoutMin: v),
                            ),
                          ),
                        ],
                      ),
                      if (widget.alarm != null)
                        TextButton(
                          onPressed: _delete,
                          style: TextButton.styleFrom(
                            foregroundColor: muted,
                            minimumSize: const Size(0, s6),
                          ),
                          child: Text(
                            'Excluir alarme',
                            style: body(16, color: muted, bold: true),
                          ),
                        ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: s3),
              PrimaryButton('Salvar alarme', _save),
            ],
          ),
        ),
      ),
    );
  }
}

class SettingsPage extends StatelessWidget {
  const SettingsPage(this.repo, {super.key});
  final AlarmRepo repo;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      backgroundColor: ink,
      title: Text('Ajustes', style: display(24)),
    ),
    body: ListenableBuilder(
      listenable: repo,
      builder: (context, _) => ListView(
        padding: const EdgeInsets.all(s4),
        children: [
          Group(
            children: [
              ValueRow(
                repo.watchName ?? 'Relógio',
                repo.connected ? 'conectado' : 'não encontrado',
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => NightPage(repo)),
                ),
              ),
            ],
          ),
          const SizedBox(height: s4),
          Group(
            children: [
              ValueRow(
                'Padrões de novo alarme',
                '',
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => DefaultsPage(repo)),
                ),
              ),
              ValueRow(
                'Formato de hora',
                repo.hour24 ? '24 h' : '12 h',
                onTap: () => repo.setHour24(!repo.hour24),
              ),
            ],
          ),
        ],
      ),
    ),
  );
}

class DefaultsPage extends StatelessWidget {
  const DefaultsPage(this.repo, {super.key});
  final AlarmRepo repo;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      backgroundColor: ink,
      title: Text('Padrões de novo alarme', style: display(24)),
    ),
    body: ListenableBuilder(
      listenable: repo,
      builder: (context, _) {
        Future<void> pick(
          String title,
          Map<int, String> opts,
          int cur,
          String key,
        ) async {
          final v = await pickOption(context, title, opts, cur);
          if (v != null) await repo.setDefault(key, v);
        }

        return Padding(
          padding: const EdgeInsets.all(s4),
          child: Group(
            children: [
              ValueRow(
                'Intensidade',
                intensities[repo.defIntensity]!,
                onTap: () => pick(
                  'Intensidade',
                  intensities,
                  repo.defIntensity,
                  'defIntensity',
                ),
              ),
              ValueRow(
                'Subida',
                ramps[repo.defRamp] ?? '${repo.defRamp} s',
                onTap: () =>
                    pick('Subida até o máximo', ramps, repo.defRamp, 'defRamp'),
              ),
              ValueRow(
                'Caminhar por',
                walkOptions[repo.defWalk] ?? '${repo.defWalk} s',
                onTap: () =>
                    pick('Caminhar por', walkOptions, repo.defWalk, 'defWalk'),
              ),
              ValueRow(
                'Limite de segurança',
                timeouts[repo.defTimeout] ?? '${repo.defTimeout} min',
                onTap: () => pick(
                  'Limite de segurança',
                  timeouts,
                  repo.defTimeout,
                  'defTimeout',
                ),
              ),
            ],
          ),
        );
      },
    ),
  );
}

/// Horário em duas rodas verticais (horas e minutos). O número mais perto do centro cresce e clareia
/// conforme a roda gira; os vizinhos ficam menores, em muted.
class _TimeWheels extends StatefulWidget {
  const _TimeWheels({
    required this.hour,
    required this.minute,
    required this.onChanged,
  });
  final int hour, minute;
  final void Function(int hour, int minute) onChanged;

  @override
  State<_TimeWheels> createState() => _TimeWheelsState();
}

class _TimeWheelsState extends State<_TimeWheels> {
  static const row = 56.0;
  late final _h = FixedExtentScrollController(initialItem: widget.hour);
  late final _m = FixedExtentScrollController(initialItem: widget.minute);
  late int hour = widget.hour, minute = widget.minute;

  @override
  void dispose() {
    _h.dispose();
    _m.dispose();
    super.dispose();
  }

  Widget _wheel(
    String label,
    FixedExtentScrollController c,
    int n,
    ValueChanged<int> onPick,
  ) => SizedBox(
    width: 96,
    child: Semantics(
      label: label,
      child: AnimatedBuilder(
        animation: c,
        builder: (_, _) {
          final pos = c.hasClients ? c.offset / row : c.initialItem.toDouble();
          return ListWheelScrollView(
            controller: c,
            itemExtent: row,
            diameterRatio:
                100, // roda quase plana: só o tamanho muda, não a curvatura
            physics: const FixedExtentScrollPhysics(),
            onSelectedItemChanged: onPick,
            children: [
              for (var i = 0; i < n; i++)
                Builder(
                  builder: (_) {
                    final t = (1 - (i - pos).abs()).clamp(0.0, 1.0);
                    return Center(
                      child: Text(
                        i.toString().padLeft(2, '0'),
                        style: display(
                          22 + 26 * t,
                          color: Color.lerp(muted, text, t)!,
                        ),
                      ),
                    );
                  },
                ),
            ],
          );
        },
      ),
    ),
  );

  @override
  Widget build(BuildContext context) => Container(
    height: row * 3,
    decoration: BoxDecoration(
      color: surface,
      borderRadius: BorderRadius.circular(radius),
    ),
    child: Stack(
      alignment: Alignment.center,
      children: [
        Positioned(
          left: s2,
          right: s2,
          top: row,
          height: row,
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: ink,
              borderRadius: BorderRadius.circular(s2),
            ),
          ),
        ),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            _wheel('Horas', _h, 24, (i) {
              hour = i;
              widget.onChanged(hour, minute);
            }),
            const SizedBox(width: s2),
            Text(':', style: display(48)),
            const SizedBox(width: s2),
            _wheel('Minutos', _m, 60, (i) {
              minute = i;
              widget.onChanged(hour, minute);
            }),
          ],
        ),
      ],
    ),
  );
}
