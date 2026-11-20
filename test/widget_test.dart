import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:livescreentranslation/src/app.dart';
import 'package:livescreentranslation/src/models.dart';
import 'package:livescreentranslation/src/session_controller.dart';
import 'package:livescreentranslation/src/practice_sample.dart';

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

  testWidgets(
    'reading choices fit a narrow screen, preview and lock while active',
    (tester) async {
      tester.view.physicalSize = const Size(320, 740);
      tester.view.devicePixelRatio = 1;
      tester.platformDispatcher.textScaleFactorTestValue = 1.4;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
      await tester.pumpWidget(ScreenLingoApp(controller: controller));
      await tester.pumpAndSettle();
      await tester.tap(find.text('설정'));
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(find.text('아주 큰 글자').hitTestable(), 220);
      await tester.tap(find.text('아주 큰 글자'));
      await tester.pumpAndSettle();
      expect(bridge.settings.cardTextSp, 24);
      final preview = tester.widget<Text>(find.text('가나다 ABC 123'));
      expect(preview.style!.fontSize, 24);
      await tester.scrollUntilVisible(find.text('높은 카드').hitTestable(), 180);
      await tester.tap(find.text('높은 카드'));
      await tester.pumpAndSettle();
      expect(bridge.settings.cardHeightDp, 420);
      bridge.emit(const SessionSnapshot(phase: SessionPhase.running));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<ChoiceChip>(find.widgetWithText(ChoiceChip, '높은 카드'))
            .onSelected,
        isNull,
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('home supports a 320dp display with larger system text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 740);
    tester.view.devicePixelRatio = 1;
    tester.platformDispatcher.textScaleFactorTestValue = 1.4;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    await tester.pumpWidget(ScreenLingoApp(controller: controller));
    await tester.pumpAndSettle();
    await tester.drag(find.byType(ListView), const Offset(0, -600));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'network guidance fits large text and refreshes without a download',
    (tester) async {
      tester.view.physicalSize = const Size(320, 740);
      tester.view.devicePixelRatio = 1;
      tester.platformDispatcher.textScaleFactorTestValue = 1.4;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
      bridge.network = DownloadNetwork.other;
      bridge.download = Completer<void>();
      await tester.pumpWidget(ScreenLingoApp(controller: controller));
      await tester.pumpAndSettle();
      final pending = controller.prepare();
      await tester.pump();
      await tester.scrollUntilVisible(find.text('번역 언어 준비'), 300);
      expect(find.textContaining('언어 준비 요청 중.'), findsOneWidget);
      expect(find.textContaining('다운로드 중'), findsNothing);
      expect(tester.takeException(), isNull);
      bridge.download!.complete();
      await pending;
      await tester.pumpAndSettle();
      await tester.tap(find.text('설정'));
      await tester.pumpAndSettle();
      await tester.drag(find.byType(ListView), const Offset(0, 1500));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Wi-Fi에서만 언어 준비'));
      await tester.pumpAndSettle();
      expect(find.textContaining('데이터 요금'), findsOneWidget);
      bridge.network = DownloadNetwork.offline;
      await tester.drag(find.byType(ListView), const Offset(0, -300));
      await tester.pumpAndSettle();
      await tester.tap(find.text('연결 상태 새로고침'));
      await tester.pumpAndSettle();
      expect(find.textContaining('기본 연결이 없어요'), findsOneWidget);
      expect(bridge.prepareCalls, 1);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'home keeps preparation and capture controls without a promotional preview',
    (tester) async {
      await tester.pumpWidget(ScreenLingoApp(controller: controller));
      await tester.pumpAndSettle();
      expect(find.text('번역 언어'), findsOneWidget);
      expect(find.text('시작 준비'), findsOneWidget);
      final start = tester.widget<FilledButton>(
        find.widgetWithText(FilledButton, '화면 번역 시작'),
      );
      expect(start.onPressed, isNull);
      expect(bridge.startCalls, 0);
    },
  );

  testWidgets('capture starts only after the privacy disclosure is accepted', (
    tester,
  ) async {
    bridge.overlay = true;
    bridge.ready = true;
    await tester.pumpWidget(ScreenLingoApp(controller: controller));
    await tester.pumpAndSettle();
    await tester.tap(find.text('화면 번역 시작'));
    await tester.pumpAndSettle();
    expect(find.text('화면 번역을 시작할까요?'), findsOneWidget);
    expect(bridge.startCalls, 0);
    await tester.tap(find.text('취소'));
    await tester.pumpAndSettle();
    expect(bridge.startCalls, 0);
    await tester.tap(find.text('화면 번역 시작'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('화면 공유 계속'));
    await tester.pumpAndSettle();
    expect(bridge.startCalls, 1);
    expect(find.text('잠시 멈추기'), findsOneWidget);
    await tester.tap(find.byTooltip('번역 종료'));
    await tester.pumpAndSettle();
    expect(find.text('화면 번역 시작'), findsOneWidget);
  });

  testWidgets('guide and settings remain usable on a narrow display', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(ScreenLingoApp(controller: controller));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.tap(find.text('사용 안내'));
    await tester.pumpAndSettle();
    expect(find.text('사용 안내'), findsNWidgets(2));
    await tester.tap(find.text('설정'));
    await tester.pumpAndSettle();
    expect(find.text('설정'), findsNWidgets(2));
    await tester.tap(find.text('4초'));
    await tester.pumpAndSettle();
    expect(bridge.settings.intervalMs, 4000);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'native failure is readable, dismissible and retry needs consent',
    (tester) async {
      tester.view.physicalSize = const Size(320, 740);
      tester.view.devicePixelRatio = 1;
      tester.platformDispatcher.textScaleFactorTestValue = 1.4;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
      bridge.overlay = true;
      bridge.ready = true;
      await tester.pumpWidget(ScreenLingoApp(controller: controller));
      await tester.pumpAndSettle();
      bridge.emit(const SessionSnapshot(phase: SessionPhase.running));
      await tester.pumpAndSettle();
      const message = '화면 처리 응답이 30초 동안 없어 번역을 종료했어요. 화면 번역을 다시 시작해 주세요.';
      bridge.emit(
        const SessionSnapshot(phase: SessionPhase.error, message: message),
      );
      await tester.pumpAndSettle();
      expect(find.text(message), findsOneWidget);
      expect(find.text('잠시 멈추기'), findsNothing);
      expect(tester.takeException(), isNull);
      await tester.tap(find.byTooltip('안내 닫기'));
      await controller.refresh();
      await tester.pumpAndSettle();
      expect(find.text(message), findsNothing);
      await tester.tap(find.text('화면 번역 시작'));
      await tester.pumpAndSettle();
      expect(find.text('화면 번역을 시작할까요?'), findsOneWidget);
      expect(bridge.startCalls, 0);
      await tester.tap(find.text('취소'));
      await tester.pumpAndSettle();
    },
  );

  final samples = [
    (
      AppLanguage.english,
      'Find your\nnext little adventure.',
      'The garden opens at nine in the morning. Admission is free.',
    ),
    (AppLanguage.japanese, '図書館は午前9時に開きます。', '図書館は午前9時に開きます。入館は無料です。'),
    (AppLanguage.chinese, '图书馆上午9点开放。', '图书馆上午9点开放。入馆免费。'),
    (
      AppLanguage.korean,
      '도서관은 오전 9시에 문을 엽니다.',
      '도서관은 오전 9시에 문을 엽니다. 입장은 무료예요.',
    ),
  ];
  for (final (language, title, copiedText) in samples) {
    testWidgets(
      '${language.code} practice follows saved source and copies its own sample',
      (tester) async {
        tester.view.physicalSize = const Size(320, 740);
        tester.view.devicePixelRatio = 1;
        tester.platformDispatcher.textScaleFactorTestValue = 1.4;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
        bridge.settings = TranslationSettings(
          source: language,
          target: language == AppLanguage.korean
              ? AppLanguage.english
              : AppLanguage.korean,
        );
        String? clipboard;
        final messenger =
            TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
        messenger.setMockMethodCallHandler(SystemChannels.platform, (
          call,
        ) async {
          if (call.method == 'Clipboard.setData') {
            clipboard = (call.arguments as Map)['text'] as String;
          }
          return null;
        });
        addTearDown(
          () =>
              messenger.setMockMethodCallHandler(SystemChannels.platform, null),
        );
        await tester.pumpWidget(ScreenLingoApp(controller: controller));
        await tester.pumpAndSettle();
        await tester.tap(find.text('사용 안내'));
        await tester.pumpAndSettle();
        await tester.scrollUntilVisible(find.text('번역 연습 화면 열기'), 240);
        await tester.tap(find.text('번역 연습 화면 열기'));
        await tester.pumpAndSettle();
        expect(find.text('${language.label} 원문 연습'), findsOneWidget);
        expect(find.text(title), findsOneWidget);
        expect(bridge.practiceVisibility, [true]);
        expect(bridge.startCalls, 0);
        expect(tester.takeException(), isNull);
        await tester.scrollUntilVisible(find.text('예문 복사'), 240);
        await Scrollable.ensureVisible(
          tester.element(find.text('예문 복사')),
          alignment: .5,
        );
        await tester.pumpAndSettle();
        await tester.tap(find.text('예문 복사'));
        await tester.pump();
        expect(clipboard, copiedText);
        expect(tester.takeException(), isNull);
        Navigator.of(tester.element(find.byType(PracticeScreen))).pop();
        await tester.pumpAndSettle();
        expect(bridge.practiceVisibility, [true, false]);
      },
    );
  }

  for (final language in AppLanguage.values) {
    testWidgets(
      '${language.code} long practice switches, copies and remains scrollable',
      (tester) async {
        tester.view.physicalSize = const Size(320, 740);
        tester.view.devicePixelRatio = 1;
        tester.platformDispatcher.textScaleFactorTestValue = 1.4;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
        String? copied;
        final messenger =
            TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
        messenger.setMockMethodCallHandler(SystemChannels.platform, (
          call,
        ) async {
          if (call.method == 'Clipboard.setData') {
            copied = (call.arguments as Map)['text'] as String;
          }
          return null;
        });
        addTearDown(
          () =>
              messenger.setMockMethodCallHandler(SystemChannels.platform, null),
        );
        await tester.pumpWidget(
          MaterialApp(
            home: PracticeScreen(language: language, bridge: bridge),
          ),
        );
        await tester.pumpAndSettle();
        await tester.tap(find.byTooltip('연습 내용 선택'));
        await tester.pumpAndSettle();
        await tester.tap(
          find.widgetWithText(CheckedPopupMenuItem<bool>, '긴 문단·혼합 언어'),
        );
        await tester.pumpAndSettle();
        final sample = LongPracticeSample.forLanguage(language);
        expect(sample.body.runes.length, greaterThan(600));
        expect(find.text(sample.body), findsOneWidget);
        expect(find.textContaining('혼합 언어는 선택한 원문 언어'), findsOneWidget);
        await tester.scrollUntilVisible(find.text('예문 복사'), 400);
        await Scrollable.ensureVisible(
          tester.element(find.text('예문 복사')),
          alignment: .5,
        );
        await tester.pumpAndSettle();
        await tester.tap(find.text('예문 복사'));
        await tester.pump();
        expect(copied, sample.copyText);
        expect(copied, contains(LongPracticeSample.mixed));
        expect(bridge.startCalls, 0);
        expect(bridge.practiceVisibility, [true]);
        await tester.tap(find.byTooltip('연습 내용 선택'));
        await tester.pumpAndSettle();
        await tester.tap(
          find.widgetWithText(CheckedPopupMenuItem<bool>, '기본 예문'),
        );
        await tester.pumpAndSettle();
        tester
            .state<ScrollableState>(find.byType(Scrollable).first)
            .position
            .jumpTo(0);
        await tester.pumpAndSettle();
        expect(
          find.text(PracticeSample.forLanguage(language).title),
          findsOneWidget,
        );
        expect(find.text(sample.body), findsNothing);
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox());
        await tester.pumpAndSettle();
        expect(bridge.practiceVisibility, [true, false]);
      },
    );
  }

  testWidgets(
    'active session practice uses the chosen source and shared bridge',
    (tester) async {
      bridge.overlay = true;
      bridge.ready = true;
      await tester.pumpWidget(ScreenLingoApp(controller: controller));
      await tester.pumpAndSettle();
      await controller.updateSettings(
        controller.settings.copyWith(source: AppLanguage.japanese),
      );
      await controller.start();
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('번역 연습 화면 열기'));
      await tester.tap(find.text('번역 연습 화면 열기'));
      await tester.pumpAndSettle();
      expect(find.text('図書館は午前9時に開きます。'), findsOneWidget);
      expect(bridge.practiceVisibility, [true]);
      expect(bridge.startCalls, 1);
    },
  );
}
