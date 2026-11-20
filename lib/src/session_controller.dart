import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'models.dart';
import 'platform_bridge.dart';

class SessionController extends ChangeNotifier {
  SessionController(this.bridge);
  final TranslationBridge bridge;
  StreamSubscription<SessionSnapshot>? _subscription;
  bool _disposed = false;
  int _settingsRevision = 0;
  Timer? _networkTimer;
  int _networkRevision = 0;
  bool checkingNetwork = false;
  DownloadNetwork downloadNetwork = DownloadNetwork.unknown;
  TranslationSettings settings = const TranslationSettings();
  SessionSnapshot session = const SessionSnapshot();
  bool overlayAllowed = false;
  bool modelReady = false;
  bool initializing = true;
  bool busy = false;
  bool preparing = false;
  String? _operationError;
  String? _sessionError;

  String? get error => _operationError ?? _sessionError;

  bool get locked => busy || preparing || initializing || session.isActive;
  bool get canStart => !locked && overlayAllowed && modelReady;

  String get networkHint => switch (downloadNetwork) {
    DownloadNetwork.wifi => '현재 기본 연결: Wi-Fi · 인터넷 연결 확인됨',
    DownloadNetwork.other =>
      settings.wifiOnly
          ? '현재 기본 연결은 Wi-Fi가 아니에요. Wi-Fi 전용 준비는 연결 상태를 확인해 주세요.'
          : '현재 기본 연결은 Wi-Fi가 아니에요. 언어 준비에 데이터 요금이 발생할 수 있어요.',
    DownloadNetwork.offline => '사용 가능한 기본 연결이 없어요. 언어 준비에는 인터넷 연결이 필요해요.',
    DownloadNetwork.unverified =>
      '인터넷 연결이 확인되지 않았어요. Wi-Fi 로그인이나 연결 상태를 확인해 주세요.',
    DownloadNetwork.unknown => '현재 연결을 확인하지 못했어요. Wi-Fi 전용 설정과 연결 상태를 확인해 주세요.',
  };

  String get modelPreparationHint {
    if (modelReady) return '준비 완료 · 오프라인으로 번역할 수 있어요';
    final prefix = preparing ? '언어 준비 요청 중. ' : '처음 한 번 언어 모델을 내려받아요. ';
    return '$prefix$networkHint';
  }

  Future<void> refreshNetwork() async {
    if (_disposed || checkingNetwork) return;
    final revision = ++_networkRevision;
    checkingNetwork = true;
    _notify();
    var value = DownloadNetwork.unknown;
    try {
      value = await bridge.getDownloadNetwork().timeout(
        const Duration(seconds: 5),
      );
    } catch (_) {
      // Optional guidance must not block cached models or the SDK's own policy.
    }
    if (_disposed || revision != _networkRevision) return;
    downloadNetwork = value;
    checkingNetwork = false;
    _notify();
  }

  Future<void> initialize() async {
    try {
      _subscription = bridge.sessions.listen(
        (value) {
          _applySession(value);
          _notify();
        },
        onError: (Object failure) {
          _operationError = _message(failure);
          _notify();
        },
      );
      settings = await bridge.getSettings();
      await refresh();
    } catch (failure) {
      _operationError = _message(failure);
    } finally {
      initializing = false;
      _notify();
    }
  }

  Future<void> refresh() async {
    unawaited(refreshNetwork());
    try {
      final revision = _settingsRevision;
      final permission = await bridge.hasOverlayPermission();
      final ready = await bridge.modelsReady(settings);
      final current = await bridge.getSession();
      if (_disposed) return;
      overlayAllowed = permission;
      if (revision == _settingsRevision) modelReady = ready;
      _applySession(current);
    } catch (failure) {
      _operationError = _message(failure);
    }
    _notify();
  }

  Future<void> updateSettings(TranslationSettings value) async {
    if (locked || value.source == value.target) return;
    final languageChanged =
        value.source != settings.source || value.target != settings.target;
    busy = true;
    _clearErrors();
    _settingsRevision++;
    final previous = settings;
    settings = value;
    if (languageChanged) modelReady = false;
    var saved = false;
    _notify();
    try {
      await bridge.saveSettings(value);
      saved = true;
      if (languageChanged) modelReady = await bridge.modelsReady(value);
    } catch (failure) {
      if (!saved) settings = previous;
      _operationError = _message(failure);
    } finally {
      busy = false;
      _notify();
    }
  }

  Future<void> requestOverlay() => _perform(bridge.requestOverlayPermission);

  Future<void> prepare() async {
    if (locked) return;
    preparing = true;
    _clearErrors();
    unawaited(refreshNetwork());
    _networkTimer = Timer.periodic(const Duration(seconds: 5), (_) {
      unawaited(refreshNetwork());
    });
    _notify();
    try {
      await bridge.prepareModels(settings).timeout(const Duration(minutes: 2));
      modelReady = await bridge.modelsReady(settings);
    } catch (failure) {
      _operationError = _message(failure);
    } finally {
      _networkTimer?.cancel();
      _networkTimer = null;
      _networkRevision++;
      checkingNetwork = false;
      preparing = false;
      _notify();
    }
  }

  Future<void> start() async {
    if (!canStart) return;
    await _perform(() async {
      final accepted = await bridge.start(settings);
      if (!accepted) _operationError = '화면 공유를 취소했어요. 준비되면 다시 시작해 주세요.';
      await refresh();
    });
  }

  Future<void> stop() => _perform(bridge.stop);
  Future<void> togglePause() => _perform(bridge.togglePause);
  Future<void> openTranslationInfo() => _perform(bridge.openTranslationInfo);

  void clearError() {
    _clearErrors();
    _notify();
  }

  void _clearErrors() {
    _operationError = null;
    _sessionError = null;
  }

  void _applySession(SessionSnapshot value) {
    if (value.phase != SessionPhase.error) {
      _sessionError = null;
    } else if (session.phase != SessionPhase.error ||
        session.message != value.message) {
      // Refresh and EventChannel can report the same failure. Keep a dismissed
      // notice closed until the message changes or a new session begins.
      _operationError = null;
      _sessionError = value.message.trim().isEmpty
          ? '화면 번역을 종료했어요. 준비 상태를 확인하고 다시 시작해 주세요.'
          : value.message;
    }
    session = value;
  }

  Future<void> _perform(Future<void> Function() action) async {
    if (busy) return;
    busy = true;
    _clearErrors();
    _notify();
    try {
      await action();
    } catch (failure) {
      _operationError = _message(failure);
    } finally {
      busy = false;
      _notify();
    }
  }

  String _message(Object failure) {
    if (failure is TimeoutException) {
      return '언어 준비가 오래 걸려 대기를 끝냈어요. 다운로드는 계속될 수 있어요. 잠시 후 화면을 새로고침해 주세요.';
    }
    if (failure is MissingPluginException) {
      return '화면 번역은 Android 앱에서 사용할 수 있어요.';
    }
    if (failure is PlatformException) {
      return failure.message ?? '작업을 완료하지 못했어요. 다시 시도해 주세요.';
    }
    return '연결 중 문제가 발생했어요. 앱을 다시 열어 주세요.';
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _networkTimer?.cancel();
    _networkRevision++;
    unawaited(_subscription?.cancel());
    super.dispose();
  }
}
