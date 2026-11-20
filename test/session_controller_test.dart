import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:livescreentranslation/src/models.dart';
import 'package:livescreentranslation/src/session_controller.dart';

import 'support/fake_bridge.dart';

void main() {
  late FakeBridge bridge;
  late SessionController controller;
  setUp(() {
    bridge = FakeBridge();
    controller = SessionController(bridge);
  });
  tearDown(() async {
    controller.dispose();
    await bridge.events.close();
  });

  test(
    'reading choices survive settings reload, start and an active-session edit',
    () async {
      bridge.overlay = true;
      bridge.ready = true;
      await controller.initialize();
      await controller.updateSettings(
        controller.settings.copyWith(cardTextSp: 24, cardHeightDp: 420),
      );
      controller.dispose();
      controller = SessionController(bridge);
      await controller.initialize();
      expect(controller.settings.cardTextSp, 24);
      expect(controller.settings.cardHeightDp, 420);
      await controller.start();
      expect(bridge.startedSettings!.cardTextSp, 24);
      expect(bridge.startedSettings!.cardHeightDp, 420);
      final writes = bridge.settingsWrites;
      await controller.updateSettings(
        controller.settings.copyWith(cardTextSp: 16, cardHeightDp: 300),
      );
      expect(bridge.settingsWrites, writes);
      expect(controller.settings.cardTextSp, 24);
    },
  );

  test('legacy and unknown reading values use supported defaults', () {
    for (final map in <Map<Object?, Object?>>[
      {},
      {'cardTextSp': 999, 'cardHeightDp': -1},
    ]) {
      final settings = TranslationSettings.fromMap(map);
      expect(settings.cardTextSp, 16);
      expect(settings.cardHeightDp, 300);
    }
    final settings = const TranslationSettings(
      cardTextSp: 24,
      cardHeightDp: 420,
    );
    expect(TranslationSettings.fromMap(settings.toMap()).cardTextSp, 24);
    expect(TranslationSettings.fromMap(settings.toMap()).cardHeightDp, 420);
  });

  test(
    'reading changes keep model readiness without an unrelated SDK query',
    () async {
      bridge.overlay = true;
      bridge.ready = true;
      await controller.initialize();
      bridge.modelCheckFailure = PlatformException(
        code: 'models',
        message: 'unrelated model query',
      );
      await controller.updateSettings(
        controller.settings.copyWith(cardTextSp: 24),
      );
      await controller.updateSettings(
        controller.settings.copyWith(cardHeightDp: 420),
      );
      expect(controller.error, isNull);
      expect(controller.modelReady, isTrue);
      expect(controller.canStart, isTrue);
      expect(bridge.settings.cardTextSp, 24);
      expect(bridge.settings.cardHeightDp, 420);
    },
  );

  test('capture requires both permission and downloaded models', () async {
    await controller.initialize();
    await controller.start();
    expect(bridge.startCalls, 0);
    bridge.overlay = true;
    await controller.refresh();
    await controller.start();
    expect(bridge.startCalls, 0);
    await controller.prepare();
    expect(controller.canStart, isTrue);
    await controller.start();
    expect(bridge.startCalls, 1);
    expect(controller.session.phase, SessionPhase.running);
  });

  test(
    'declining screen sharing keeps the session idle and allows retry',
    () async {
      bridge.overlay = true;
      bridge.ready = true;
      bridge.acceptCapture = false;
      await controller.initialize();
      await controller.start();
      expect(controller.session.isActive, isFalse);
      expect(controller.error, contains('취소'));
      expect(controller.canStart, isTrue);
      bridge.acceptCapture = true;
      await controller.start();
      expect(controller.error, isNull);
      expect(controller.session.phase, SessionPhase.running);
    },
  );

  test(
    'failed model preparation unlocks settings and can be retried',
    () async {
      await controller.initialize();
      bridge.download = Completer<void>();
      final pending = controller.prepare();
      expect(controller.locked, isTrue);
      await controller.updateSettings(
        const TranslationSettings(source: AppLanguage.japanese),
      );
      expect(bridge.settingsWrites, 0);
      bridge.download!.completeError(
        PlatformException(code: 'download', message: '네트워크를 확인해 주세요'),
      );
      await pending;
      expect(controller.preparing, isFalse);
      expect(controller.modelReady, isFalse);
      expect(controller.error, contains('네트워크'));
      bridge.download = null;
      await controller.prepare();
      expect(controller.error, isNull);
      expect(controller.modelReady, isTrue);
    },
  );

  test(
    'pause and stop clear visible translations and active settings stay locked',
    () async {
      bridge.overlay = true;
      bridge.ready = true;
      await controller.initialize();
      bridge.emit(
        const SessionSnapshot(
          phase: SessionPhase.running,
          blocks: [TranslationBlock(original: 'Private', translated: '비공개')],
        ),
      );
      await controller.updateSettings(
        const TranslationSettings(source: AppLanguage.japanese),
      );
      expect(bridge.settingsWrites, 0);
      await controller.togglePause();
      expect(controller.session.phase, SessionPhase.paused);
      expect(controller.session.blocks, isEmpty);
      await controller.stop();
      expect(controller.session.blocks, isEmpty);
      expect(controller.session.isActive, isFalse);
      expect(controller.locked, isFalse);
    },
  );

  test(
    'resume refresh detects an overlay permission revoked in settings',
    () async {
      bridge.overlay = true;
      bridge.ready = true;
      await controller.initialize();
      expect(controller.canStart, isTrue);
      bridge.overlay = false;
      await controller.refresh();
      expect(controller.canStart, isFalse);
    },
  );
  test('saved languages survive a subsequent model status failure', () async {
    await controller.initialize();
    bridge.modelCheckFailure = PlatformException(
      code: 'models',
      message: '모델 조회 실패',
    );
    await controller.updateSettings(
      const TranslationSettings(source: AppLanguage.japanese),
    );
    expect(controller.settings.source, AppLanguage.japanese);
    expect(bridge.settings.source, AppLanguage.japanese);
    expect(controller.modelReady, isFalse);
    expect(controller.busy, isFalse);
  });

  test(
    'native failure is visible and a later session can report it again',
    () async {
      bridge.overlay = true;
      bridge.ready = true;
      await controller.initialize();
      const failure = SessionSnapshot(
        phase: SessionPhase.error,
        message: '화면 처리 응답이 30초 동안 없어 번역을 종료했어요.',
      );
      bridge.emit(const SessionSnapshot(phase: SessionPhase.running));
      bridge.events.addError(
        PlatformException(code: 'bridge', message: '일시적인 연결 오류'),
      );
      expect(controller.error, '일시적인 연결 오류');
      bridge.emit(failure);
      expect(controller.error, failure.message);
      expect(controller.canStart, isTrue);
      expect(controller.session.blocks, isEmpty);
      bridge.emit(const SessionSnapshot(phase: SessionPhase.starting));
      expect(controller.error, isNull);
      bridge.emit(failure);
      expect(controller.error, failure.message);
    },
  );

  test(
    'snapshot failure loads once and dismissal survives duplicate updates',
    () async {
      const failure = SessionSnapshot(
        phase: SessionPhase.error,
        message: '다른 앱 위에 표시 권한이 해제되어 번역을 종료했어요.',
      );
      bridge.current = failure;
      await controller.initialize();
      expect(controller.error, failure.message);
      controller.clearError();
      await controller.refresh();
      bridge.emit(failure);
      expect(controller.error, isNull);
      bridge.emit(
        const SessionSnapshot(
          phase: SessionPhase.error,
          message: '화면 공유를 시작하지 못했어요.',
        ),
      );
      expect(controller.error, '화면 공유를 시작하지 못했어요.');
    },
  );

  test('empty native failure still has a useful notice', () async {
    await controller.initialize();
    bridge.emit(
      const SessionSnapshot(phase: SessionPhase.error, message: '  '),
    );
    expect(controller.error, '화면 번역을 종료했어요. 준비 상태를 확인하고 다시 시작해 주세요.');
  });

  test(
    'cancelled retry is not replaced by the previous native failure',
    () async {
      bridge.overlay = true;
      bridge.ready = true;
      bridge.current = const SessionSnapshot(
        phase: SessionPhase.error,
        message: '화면 공유를 시작하지 못했어요.',
      );
      await controller.initialize();
      bridge.acceptCapture = false;
      await controller.start();
      expect(controller.error, contains('취소'));
      await controller.refresh();
      expect(controller.error, contains('취소'));
    },
  );

  testWidgets(
    'connection hints change during one pending SDK preparation request',
    (tester) async {
      bridge.network = DownloadNetwork.other;
      await controller.initialize();
      bridge.download = Completer<void>();
      final pending = controller.prepare();
      await tester.pump();
      expect(controller.modelPreparationHint, contains('Wi-Fi가 아니에요'));
      expect(controller.modelPreparationHint, contains('요청 중'));
      expect(controller.modelReady, isFalse);
      expect(controller.locked, isTrue);
      bridge.network = DownloadNetwork.wifi;
      await tester.pump(const Duration(seconds: 5));
      expect(controller.modelPreparationHint, contains('인터넷 연결 확인됨'));
      expect(controller.modelReady, isFalse);
      expect(bridge.prepareCalls, 1);
      bridge.download!.complete();
      await pending;
      expect(controller.modelReady, isTrue);
      final checks = bridge.networkChecks;
      await tester.pump(const Duration(seconds: 15));
      expect(bridge.networkChecks, checks);
    },
  );

  test('optional network failure cannot block model preparation', () async {
    bridge.networkFailure = PlatformException(code: 'network');
    await controller.initialize();
    await controller.prepare();
    expect(controller.downloadNetwork, DownloadNetwork.unknown);
    expect(controller.error, isNull);
    expect(controller.modelReady, isTrue);
    expect(bridge.prepareCalls, 1);
  });

  test(
    'cached models can start offline without requesting a download',
    () async {
      bridge.network = DownloadNetwork.offline;
      bridge.overlay = true;
      bridge.ready = true;
      await controller.initialize();
      expect(controller.networkHint, contains('연결이 없어요'));
      expect(controller.modelPreparationHint, contains('오프라인'));
      await controller.start();
      expect(bridge.startCalls, 1);
      expect(bridge.prepareCalls, 0);
    },
  );

  testWidgets(
    'slow and obsolete network queries cannot change completed preparation',
    (tester) async {
      await controller.initialize();
      bridge.networkQuery = Completer<DownloadNetwork>();
      bridge.download = Completer<void>();
      final pending = controller.prepare();
      await tester.pump(const Duration(seconds: 6));
      expect(controller.downloadNetwork, DownloadNetwork.unknown);
      expect(controller.error, isNull);
      bridge.download!.complete();
      await pending;
      final checks = bridge.networkChecks;
      bridge.networkQuery!.complete(DownloadNetwork.offline);
      await tester.pump(const Duration(seconds: 15));
      expect(controller.modelReady, isTrue);
      expect(controller.downloadNetwork, isNot(DownloadNetwork.offline));
      expect(bridge.networkChecks, checks);
    },
  );

  testWidgets(
    'model preparation times out without claiming cancellation or late success',
    (tester) async {
      await controller.initialize();
      bridge.download = Completer<void>();
      final pending = controller.prepare();
      expect(controller.preparing, isTrue);
      await tester.pump(const Duration(minutes: 2));
      await pending;
      expect(controller.preparing, isFalse);
      expect(controller.locked, isFalse);
      expect(controller.modelReady, isFalse);
      expect(controller.error, contains('다운로드는 계속될 수 있어요'));
      final checks = bridge.networkChecks;
      bridge.download!.complete();
      await tester.pump(const Duration(seconds: 15));
      expect(bridge.networkChecks, checks);
      expect(controller.modelReady, isFalse);
      await controller.refresh();
      expect(controller.modelReady, isTrue);
    },
  );
}
