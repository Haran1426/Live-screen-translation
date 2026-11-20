import 'package:flutter/services.dart';

import 'models.dart';

abstract class TranslationBridge {
  Stream<SessionSnapshot> get sessions;
  Future<SessionSnapshot> getSession();
  Future<TranslationSettings> getSettings();
  Future<void> saveSettings(TranslationSettings settings);
  Future<bool> hasOverlayPermission();
  Future<void> requestOverlayPermission();
  Future<bool> modelsReady(TranslationSettings settings);
  Future<DownloadNetwork> getDownloadNetwork();
  Future<void> prepareModels(TranslationSettings settings);
  Future<bool> start(TranslationSettings settings);
  Future<void> stop();
  Future<void> togglePause();
  Future<void> openTranslationInfo();
  Future<void> setPracticeVisible(bool visible);
}

class AndroidTranslationBridge implements TranslationBridge {
  static const _methods = MethodChannel('screenlingo/control');
  static const _events = EventChannel('screenlingo/session');

  @override
  Stream<SessionSnapshot> get sessions => _events.receiveBroadcastStream().map(
    (event) => SessionSnapshot.fromMap(event as Map<Object?, Object?>),
  );

  @override
  Future<SessionSnapshot> getSession() async => SessionSnapshot.fromMap(
    await _methods.invokeMapMethod<Object?, Object?>('getSession') ?? {},
  );
  @override
  Future<TranslationSettings> getSettings() async =>
      TranslationSettings.fromMap(
        await _methods.invokeMapMethod<Object?, Object?>('getSettings') ?? {},
      );
  @override
  Future<void> saveSettings(TranslationSettings settings) =>
      _methods.invokeMethod<void>('saveSettings', settings.toMap());
  @override
  Future<bool> hasOverlayPermission() async =>
      await _methods.invokeMethod<bool>('hasOverlayPermission') ?? false;
  @override
  Future<void> requestOverlayPermission() =>
      _methods.invokeMethod<void>('requestOverlayPermission');
  @override
  Future<bool> modelsReady(TranslationSettings settings) async =>
      await _methods.invokeMethod<bool>('modelsReady', settings.toMap()) ??
      false;
  @override
  Future<DownloadNetwork> getDownloadNetwork() async {
    final code = await _methods.invokeMethod<String>('getDownloadNetwork');
    return DownloadNetwork.values
            .where((value) => value.name == code)
            .firstOrNull ??
        DownloadNetwork.unknown;
  }

  @override
  Future<void> prepareModels(TranslationSettings settings) =>
      _methods.invokeMethod<void>('prepareModels', settings.toMap());
  @override
  Future<bool> start(TranslationSettings settings) async =>
      await _methods.invokeMethod<bool>('start', settings.toMap()) ?? false;
  @override
  Future<void> stop() => _methods.invokeMethod<void>('stop');
  @override
  Future<void> togglePause() => _methods.invokeMethod<void>('togglePause');
  @override
  Future<void> openTranslationInfo() =>
      _methods.invokeMethod<void>('openTranslationInfo');
  @override
  Future<void> setPracticeVisible(bool visible) =>
      _methods.invokeMethod<void>('setPracticeVisible', visible);
}
