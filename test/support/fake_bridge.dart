import 'dart:async';

import 'package:livescreentranslation/src/models.dart';
import 'package:livescreentranslation/src/platform_bridge.dart';

class FakeBridge implements TranslationBridge {
  final events = StreamController<SessionSnapshot>.broadcast(sync: true);
  SessionSnapshot current = const SessionSnapshot();
  TranslationSettings settings = const TranslationSettings();
  bool overlay = false;
  bool ready = false;
  bool acceptCapture = true;
  int startCalls = 0;
  TranslationSettings? startedSettings;
  int settingsWrites = 0;
  int prepareCalls = 0;
  int networkChecks = 0;
  DownloadNetwork network = DownloadNetwork.wifi;
  Object? networkFailure;
  Completer<DownloadNetwork>? networkQuery;
  final practiceVisibility = <bool>[];
  Completer<void>? download;
  Object? modelCheckFailure;

  void emit(SessionSnapshot value) {
    current = value;
    events.add(value);
  }

  @override
  Stream<SessionSnapshot> get sessions => events.stream;
  @override
  Future<SessionSnapshot> getSession() async => current;
  @override
  Future<TranslationSettings> getSettings() async => settings;
  @override
  Future<void> saveSettings(TranslationSettings value) async {
    settingsWrites++;
    settings = value;
  }

  @override
  Future<bool> hasOverlayPermission() async => overlay;
  @override
  Future<void> requestOverlayPermission() async {}
  @override
  Future<bool> modelsReady(TranslationSettings settings) async {
    if (modelCheckFailure != null) throw modelCheckFailure!;
    return ready;
  }

  @override
  Future<DownloadNetwork> getDownloadNetwork() async {
    networkChecks++;
    if (networkFailure != null) throw networkFailure!;
    if (networkQuery != null) return networkQuery!.future;
    return network;
  }

  @override
  Future<void> prepareModels(TranslationSettings settings) async {
    prepareCalls++;
    if (download != null) await download!.future;
    ready = true;
  }

  @override
  Future<bool> start(TranslationSettings settings) async {
    startCalls++;
    startedSettings = settings;
    if (acceptCapture) emit(const SessionSnapshot(phase: SessionPhase.running));
    return acceptCapture;
  }

  @override
  Future<void> stop() async => emit(const SessionSnapshot());
  @override
  Future<void> openTranslationInfo() async {}
  @override
  Future<void> setPracticeVisible(bool visible) async =>
      practiceVisibility.add(visible);
  @override
  Future<void> togglePause() async => emit(
    SessionSnapshot(
      phase: current.phase == SessionPhase.paused
          ? SessionPhase.running
          : SessionPhase.paused,
    ),
  );
}
