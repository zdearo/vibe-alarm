import 'package:flutter/material.dart';

import 'pages.dart';
import 'repo.dart';
import 'theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final repo = await AlarmRepo.load();
  runApp(MaterialApp(
    title: 'Pulso',
    debugShowCheckedModeBanner: false,
    theme: buildTheme(),
    home: Shell(repo),
  ));
}
