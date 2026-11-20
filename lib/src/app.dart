import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'models.dart';
import 'platform_bridge.dart';
import 'practice_sample.dart';
import 'session_controller.dart';

const _ink = Color(0xFF202B3B);
const _accent = Color(0xFF4263EB);
const _accentSoft = Color(0xFFEEF2FF);
const _paper = Color(0xFFF8F9FD);
const _muted = Color(0xFF667085);

class ScreenLingoApp extends StatelessWidget {
  const ScreenLingoApp({super.key, this.controller});
  final SessionController? controller;

  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    title: 'ScreenLingo',
    themeMode: ThemeMode.light,
    builder: (context, child) => AnnotatedRegion<SystemUiOverlayStyle>(
      value: const SystemUiOverlayStyle(
        statusBarColor: Colors.transparent,
        statusBarIconBrightness: Brightness.dark,
        statusBarBrightness: Brightness.light,
        systemNavigationBarColor: Colors.white,
        systemNavigationBarIconBrightness: Brightness.dark,
      ),
      child: child!,
    ),
    theme: ThemeData(
      useMaterial3: true,
      scaffoldBackgroundColor: _paper,
      colorScheme: ColorScheme.fromSeed(seedColor: _accent).copyWith(
        primary: _accent,
        onPrimary: Colors.white,
        surface: _paper,
        onSurface: _ink,
        primaryContainer: _accentSoft,
        onPrimaryContainer: _ink,
        surfaceTint: Colors.transparent,
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.white,
        foregroundColor: _ink,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
      ),
      textTheme: const TextTheme(
        headlineLarge: TextStyle(
          fontSize: 28,
          height: 1.3,
          fontWeight: FontWeight.w700,
          letterSpacing: -.6,
        ),
        titleLarge: TextStyle(
          fontSize: 22,
          fontWeight: FontWeight.w700,
          letterSpacing: -.7,
        ),
        titleMedium: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
        bodyLarge: TextStyle(fontSize: 16, height: 1.6),
        bodyMedium: TextStyle(fontSize: 14, height: 1.55),
        labelLarge: TextStyle(fontSize: 14, fontWeight: FontWeight.w700),
      ).apply(bodyColor: _ink, displayColor: _ink),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(48, 54),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
      ),
      dividerTheme: const DividerThemeData(
        color: Color(0xFFE7EAF1),
        thickness: 1,
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: _paper,
        indicatorColor: _accentSoft,
        labelTextStyle: WidgetStateProperty.all(
          const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
        ),
      ),
    ),
    home: TranslationHome(controller: controller),
  );
}

class TranslationHome extends StatefulWidget {
  const TranslationHome({super.key, this.controller});
  final SessionController? controller;

  @override
  State<TranslationHome> createState() => _TranslationHomeState();
}

class _TranslationHomeState extends State<TranslationHome>
    with WidgetsBindingObserver {
  late final SessionController controller;
  int page = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    controller =
        widget.controller ?? SessionController(AndroidTranslationBridge());
    unawaited(controller.initialize());
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) unawaited(controller.refresh());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    if (widget.controller == null) controller.dispose();
    super.dispose();
  }

  Future<void> _confirmStart() async {
    final accepted = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('화면 번역을 시작할까요?'),
        content: const Text(
          '시작하면 화면에 보이는 텍스트를 주기적으로 읽어 번역해요. 민감한 정보가 있는 화면은 먼저 닫아 주세요.\n\n'
          '화면 이미지와 번역문은 기기에서 처리하며 파일에 저장하거나 서버로 보내지 않아요. 동그란 버튼으로 카드를 열어 종료할 수 있어요.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('취소'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('화면 공유 계속'),
          ),
        ],
      ),
    );
    if (accepted == true && mounted) await controller.start();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: controller,
    builder: (context, _) => Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 18, 24, 8),
              child: Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(9),
                    decoration: BoxDecoration(
                      color: _accentSoft,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: const Icon(
                      Icons.translate_rounded,
                      color: _accent,
                      size: 22,
                    ),
                  ),
                  const SizedBox(width: 11),
                  const Expanded(
                    child: Text(
                      'ScreenLingo',
                      style: TextStyle(
                        fontWeight: FontWeight.w700,
                        fontSize: 20,
                        letterSpacing: -.7,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            Expanded(
              child: RefreshIndicator(
                onRefresh: controller.refresh,
                child: ListView(
                  key: PageStorageKey<int>(page),
                  padding: const EdgeInsets.fromLTRB(24, 20, 24, 24),
                  children: [
                    if (controller.error != null) ...[
                      _ErrorCard(
                        message: controller.error!,
                        onClose: controller.clearError,
                      ),
                      const SizedBox(height: 18),
                    ],
                    if (page == 0) ..._translationPage(context),
                    if (page == 1) ..._guidePage(context),
                    if (page == 2) ..._settingsPage(context),
                  ],
                ),
              ),
            ),
            if (page == 0) _sessionActions(),
          ],
        ),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: page,
        onDestinationSelected: (value) => setState(() => page = value),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.layers_outlined),
            selectedIcon: Icon(Icons.layers_rounded),
            label: '화면 번역',
          ),
          NavigationDestination(
            icon: Icon(Icons.explore_outlined),
            selectedIcon: Icon(Icons.explore_rounded),
            label: '사용 안내',
          ),
          NavigationDestination(icon: Icon(Icons.tune_rounded), label: '설정'),
        ],
      ),
    ),
  );

  List<Widget> _translationPage(BuildContext context) => [
    Text('화면 번역', style: Theme.of(context).textTheme.headlineLarge),
    if (controller.session.isActive) ...[
      const SizedBox(height: 20),
      _activeCard(),
    ],
    const SizedBox(height: 24),
    _sectionTitle('번역 언어'),
    const SizedBox(height: 10),
    _Surface(
      padding: const EdgeInsets.all(16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(child: _languagePicker(true)),
          Padding(
            padding: const EdgeInsets.only(top: 12),
            child: IconButton(
              onPressed: controller.locked
                  ? null
                  : () => controller.updateSettings(
                      controller.settings.copyWith(
                        source: controller.settings.target,
                        target: controller.settings.source,
                      ),
                    ),
              tooltip: '언어 바꾸기',
              icon: const Icon(Icons.swap_horiz_rounded, color: _accent),
            ),
          ),
          Expanded(child: _languagePicker(false)),
        ],
      ),
    ),
    const SizedBox(height: 24),
    _sectionTitle(
      '시작 준비',
      trailing:
          '${(controller.overlayAllowed ? 1 : 0) + (controller.modelReady ? 1 : 0)}/2 완료',
    ),
    const SizedBox(height: 10),
    _Surface(
      child: Column(
        children: [
          _SetupRow(
            icon: Icons.picture_in_picture_alt_rounded,
            title: '다른 앱 위에 표시',
            detail: controller.overlayAllowed ? '허용됨' : '화면 위에 번역을 표시하려면 필요합니다',
            done: controller.overlayAllowed,
            action: '허용하기',
            onTap: controller.locked ? null : controller.requestOverlay,
          ),
          const Divider(height: 1),
          _SetupRow(
            icon: Icons.download_for_offline_outlined,
            title: '번역 언어 준비',
            detail: controller.modelPreparationHint,
            done: controller.modelReady,
            loading: controller.preparing,
            action: '준비하기',
            onTap: controller.locked ? null : controller.prepare,
          ),
        ],
      ),
    ),
    const SizedBox(height: 16),
    const Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(Icons.verified_user_outlined, size: 16, color: _muted),
        SizedBox(width: 7),
        Expanded(
          child: Text(
            '기기에서 처리 · 캡처·번역문 저장 안 함',
            style: TextStyle(fontSize: 12, color: _muted),
          ),
        ),
      ],
    ),
    if (controller.session.blocks.isNotEmpty) ...[
      const SizedBox(height: 24),
      _sectionTitle(
        '현재 화면의 번역',
        trailing: '${controller.session.elapsedMs} ms',
      ),
      const SizedBox(height: 8),
      Align(
        alignment: Alignment.centerLeft,
        child: Image.asset('assets/branding/google_translate.png', height: 18),
      ),
      const SizedBox(height: 10),
      for (final block in controller.session.blocks)
        Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: _Surface(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  block.original,
                  style: const TextStyle(color: _muted, fontSize: 12),
                ),
                const SizedBox(height: 8),
                SelectableText(
                  block.translated,
                  style: const TextStyle(fontSize: 16, height: 1.5),
                ),
              ],
            ),
          ),
        ),
    ],
  ];

  Widget _languagePicker(bool source) {
    final language = source
        ? controller.settings.source
        : controller.settings.target;
    return PopupMenuButton<AppLanguage>(
      enabled: !controller.locked,
      tooltip: source ? '원문 언어 선택' : '번역 언어 선택',
      onSelected: (value) => controller.updateSettings(
        source
            ? controller.settings.copyWith(source: value)
            : controller.settings.copyWith(target: value),
      ),
      itemBuilder: (_) => AppLanguage.values
          .where(
            (value) =>
                value !=
                (source
                    ? controller.settings.target
                    : controller.settings.source),
          )
          .map(
            (value) => PopupMenuItem(
              value: value,
              child: Text('${value.label} · ${value.nativeLabel}'),
            ),
          )
          .toList(),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              source ? '원문' : '번역',
              style: const TextStyle(fontSize: 11, color: _muted),
            ),
            const SizedBox(height: 7),
            Row(
              children: [
                Flexible(
                  child: Text(
                    language.label,
                    style: const TextStyle(
                      fontWeight: FontWeight.w700,
                      fontSize: 18,
                    ),
                  ),
                ),
                const Icon(Icons.expand_more_rounded, size: 19),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _activeCard() => Container(
    padding: const EdgeInsets.all(22),
    decoration: BoxDecoration(
      color: _accentSoft,
      borderRadius: BorderRadius.circular(14),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Icon(Icons.sensors_rounded, color: _accent),
            const SizedBox(width: 10),
            Text(
              controller.session.phase == SessionPhase.paused
                  ? '일시정지'
                  : '화면 번역 중',
              style: const TextStyle(
                color: _ink,
                fontWeight: FontWeight.w700,
                fontSize: 18,
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Text(
          controller.session.message,
          style: const TextStyle(color: _ink, height: 1.6),
        ),
        const SizedBox(height: 14),
        TextButton.icon(
          style: TextButton.styleFrom(
            foregroundColor: _ink,
            padding: EdgeInsets.zero,
          ),
          onPressed: () => Navigator.of(context).push(
            MaterialPageRoute<void>(
              builder: (_) => PracticeScreen(
                language: controller.settings.source,
                bridge: controller.bridge,
              ),
            ),
          ),
          icon: const Icon(Icons.open_in_new_rounded, size: 16),
          label: const Text('번역 연습 화면 열기'),
        ),
      ],
    ),
  );

  Widget _sessionActions() {
    final active = controller.session.isActive;
    final paused = controller.session.phase == SessionPhase.paused;
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 12, 24, 12),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: FilledButton.icon(
                  onPressed: controller.busy || controller.initializing
                      ? null
                      : active
                      ? controller.togglePause
                      : controller.canStart
                      ? _confirmStart
                      : null,
                  icon: controller.busy
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : Icon(
                          active
                              ? (paused
                                    ? Icons.play_arrow_rounded
                                    : Icons.pause_rounded)
                              : Icons.play_arrow_rounded,
                        ),
                  label: Text(
                    active ? (paused ? '번역 다시 시작' : '잠시 멈추기') : '화면 번역 시작',
                  ),
                ),
              ),
              if (active) ...[
                const SizedBox(width: 10),
                IconButton.filledTonal(
                  onPressed: controller.busy ? null : controller.stop,
                  tooltip: '번역 종료',
                  icon: const Icon(Icons.stop_rounded),
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }

  List<Widget> _guidePage(BuildContext context) => [
    Text('사용 안내', style: Theme.of(context).textTheme.headlineLarge),
    const SizedBox(height: 24),
    const _GuideStep(
      number: '01',
      title: '언어와 권한 준비',
      body: '원문 언어와 번역 언어를 선택하고, 표시 권한과 언어 모델을 준비해 주세요.',
    ),
    const _GuideStep(
      number: '02',
      title: '화면 공유 시작',
      body: '화면 번역 시작을 누른 뒤 Android의 화면 공유 안내를 확인해 주세요. 매번 직접 동의해야 시작돼요.',
    ),
    const _GuideStep(
      number: '03',
      title: '읽고 싶은 앱 열기',
      body: '번역문은 원문 위치에 표시돼요. 동그란 버튼을 누르면 번역 카드가 열려요.',
    ),
    const SizedBox(height: 14),
    FilledButton.tonalIcon(
      onPressed: () => Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => PracticeScreen(
            language: controller.settings.source,
            bridge: controller.bridge,
          ),
        ),
      ),
      icon: const Icon(Icons.auto_stories_outlined),
      label: const Text('번역 연습 화면 열기'),
    ),
    const SizedBox(height: 24),
    const _Surface(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('사용 시 참고', style: TextStyle(fontWeight: FontWeight.w700)),
          SizedBox(height: 10),
          Text(
            '• 원문 언어는 직접 선택해 주세요.\n• 카드에 가려진 부분은 카드를 접으면 읽을 수 있어요.\n• 보안 화면이나 작은 글자는 인식하기 어려울 수 있어요.\n• 화면을 잠그면 번역이 종료돼요.\n• 번역은 참고용이며 문맥에 따라 부정확할 수 있어요.',
            style: TextStyle(color: _muted, height: 1.9),
          ),
        ],
      ),
    ),
  ];

  List<Widget> _settingsPage(BuildContext context) => [
    Text('설정', style: Theme.of(context).textTheme.headlineLarge),
    const SizedBox(height: 24),
    _sectionTitle('다운로드'),
    const SizedBox(height: 10),
    _Surface(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SwitchListTile.adaptive(
            contentPadding: EdgeInsets.zero,
            title: const Text(
              'Wi-Fi에서만 언어 준비',
              style: TextStyle(fontSize: 15, fontWeight: FontWeight.w700),
            ),
            value: controller.settings.wifiOnly,
            onChanged: controller.locked
                ? null
                : (value) => controller.updateSettings(
                    controller.settings.copyWith(wifiOnly: value),
                  ),
          ),
          const SizedBox(height: 12),
          Text(
            controller.networkHint,
            style: const TextStyle(fontSize: 12, color: _muted, height: 1.6),
          ),
          const SizedBox(height: 4),
          const Text(
            '연결 상태 안내입니다. 준비된 언어는 오프라인에서 사용할 수 있습니다.',
            style: TextStyle(fontSize: 11, color: _muted, height: 1.6),
          ),
          TextButton.icon(
            onPressed: controller.checkingNetwork
                ? null
                : controller.refreshNetwork,
            icon: const Icon(Icons.refresh, size: 18),
            label: Text(controller.checkingNetwork ? '연결 확인 중' : '연결 상태 새로고침'),
          ),
        ],
      ),
    ),
    const SizedBox(height: 24),
    _sectionTitle('화면 확인 간격'),
    const SizedBox(height: 10),
    _Surface(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: 8,
            children: [
              for (final seconds in [1, 2, 4])
                ChoiceChip(
                  label: Text('$seconds초'),
                  selected: controller.settings.intervalMs == seconds * 1000,
                  onSelected: controller.locked
                      ? null
                      : (_) => controller.updateSettings(
                          controller.settings.copyWith(
                            intervalMs: seconds * 1000,
                          ),
                        ),
                ),
            ],
          ),
          const SizedBox(height: 8),
          const Text(
            '처리 중에는 다음 화면을 기다려요. 실제 갱신 속도는 기기 성능에 따라 달라요.',
            style: TextStyle(color: _muted, fontSize: 11),
          ),
        ],
      ),
    ),
    const SizedBox(height: 24),
    _sectionTitle('번역 카드 읽기'),
    const SizedBox(height: 10),
    _Surface(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('글자 크기', style: TextStyle(fontWeight: FontWeight.w700)),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            children: [
              for (final choice in const {
                16: '기본 글자',
                20: '큰 글자',
                24: '아주 큰 글자',
              }.entries)
                ChoiceChip(
                  label: Text(choice.value),
                  selected: controller.settings.cardTextSp == choice.key,
                  onSelected: controller.locked
                      ? null
                      : (_) => controller.updateSettings(
                          controller.settings.copyWith(cardTextSp: choice.key),
                        ),
                ),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            '가나다 ABC 123',
            style: TextStyle(
              fontSize: controller.settings.cardTextSp.toDouble(),
              height: 1.5,
            ),
          ),
          const SizedBox(height: 20),
          const Text('카드 높이', style: TextStyle(fontWeight: FontWeight.w700)),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            children: [
              for (final choice in const {300: '기본 높이', 420: '높은 카드'}.entries)
                ChoiceChip(
                  label: Text(choice.value),
                  selected: controller.settings.cardHeightDp == choice.key,
                  onSelected: controller.locked
                      ? null
                      : (_) => controller.updateSettings(
                          controller.settings.copyWith(
                            cardHeightDp: choice.key,
                          ),
                        ),
                ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            controller.session.isActive
                ? '번역 종료 후 변경할 수 있습니다.'
                : '다음 번역부터 적용됩니다. 시스템 글자 크기와 화면 크기를 반영합니다.',
            style: const TextStyle(color: _muted, fontSize: 12, height: 1.6),
          ),
          const SizedBox(height: 4),
          const Text(
            '가려진 원문은 카드를 접어 확인하세요.',
            style: TextStyle(color: _muted, fontSize: 12, height: 1.6),
          ),
        ],
      ),
    ),
    const SizedBox(height: 24),
    _sectionTitle('개인정보'),
    const SizedBox(height: 10),
    const _Surface(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.shield_outlined, color: _accent, size: 28),
          SizedBox(height: 12),
          Text(
            '화면 처리와 Google ML Kit',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
          ),
          SizedBox(height: 10),
          Text(
            '화면 이미지와 번역문은 번역 중 메모리에서만 처리해요. 번역을 끝내면 지우고 파일에 저장하거나 서버로 보내지 않아요.\n\n언어 모델은 Google에서 다운로드해요. 모델을 준비하면 인터넷 연결 없이 화면을 번역할 수 있어요.\n\nGoogle ML Kit는 진단과 서비스 개선을 위해 기기·앱 정보, 설치 식별자, 성능·오류 지표, 선택한 언어를 Google로 전송할 수 있어요.',
            style: TextStyle(color: _muted, fontSize: 13, height: 1.7),
          ),
        ],
      ),
    ),
    const SizedBox(height: 12),
    TextButton.icon(
      onPressed: controller.openTranslationInfo,
      icon: const Icon(Icons.open_in_new_rounded, size: 16),
      label: const Text('Google Translate 기술 안내'),
    ),
    const _Surface(
      child: Text(
        'Google Translate의 자동 번역을 제공합니다. Google은 번역의 정확성, 신뢰성, 상품성, 특정 목적 적합성 및 비침해성을 포함한 번역 관련 명시적·묵시적 보증을 제공하지 않습니다.',
        style: TextStyle(color: _muted, fontSize: 11, height: 1.7),
      ),
    ),
    const SizedBox(height: 22),
    const Text(
      'ScreenLingo 0.1.12 · Android 11 이상',
      textAlign: TextAlign.center,
      style: TextStyle(color: _muted, fontSize: 11),
    ),
  ];

  Widget _sectionTitle(String text, {String? trailing}) => Row(
    children: [
      Text(
        text,
        style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15),
      ),
      const Spacer(),
      if (trailing != null)
        Text(trailing, style: const TextStyle(fontSize: 11, color: _muted)),
    ],
  );
}

class _Surface extends StatelessWidget {
  const _Surface({
    required this.child,
    this.padding = const EdgeInsets.all(18),
  });
  final Widget child;
  final EdgeInsets padding;
  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(14),
      side: const BorderSide(color: Color(0xFFE7EAF1)),
    ),
    child: Padding(padding: padding, child: child),
  );
}

class _Pill extends StatelessWidget {
  const _Pill({required this.label, required this.color, this.fontSize = 10});
  final String label;
  final Color color;
  final double fontSize;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
    decoration: BoxDecoration(
      color: color,
      borderRadius: BorderRadius.circular(8),
    ),
    child: Text(
      label,
      style: TextStyle(
        color: _ink,
        fontSize: fontSize,
        fontWeight: FontWeight.w800,
        letterSpacing: .4,
      ),
    ),
  );
}

class _SetupRow extends StatelessWidget {
  const _SetupRow({
    required this.icon,
    required this.title,
    required this.detail,
    required this.done,
    required this.action,
    this.loading = false,
    this.onTap,
  });
  final IconData icon;
  final String title;
  final String detail;
  final bool done;
  final bool loading;
  final String action;
  final VoidCallback? onTap;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 11),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Icon(icon, color: _accent, size: 23),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontWeight: FontWeight.w700,
                  fontSize: 13,
                ),
              ),
              const SizedBox(height: 4),
              Text(detail, style: const TextStyle(fontSize: 11, color: _muted)),
            ],
          ),
        ),
        const SizedBox(width: 4),
        if (done)
          const Icon(Icons.check_circle_rounded, color: _accent, size: 22)
        else if (loading)
          const SizedBox(
            width: 22,
            height: 22,
            child: CircularProgressIndicator(strokeWidth: 2),
          )
        else
          TextButton(
            onPressed: onTap,
            style: TextButton.styleFrom(
              padding: const EdgeInsets.symmetric(horizontal: 6),
              textStyle: const TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w700,
              ),
            ),
            child: Text(action),
          ),
      ],
    ),
  );
}

class _ErrorCard extends StatelessWidget {
  const _ErrorCard({required this.message, required this.onClose});
  final String message;
  final VoidCallback onClose;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.fromLTRB(14, 8, 4, 8),
    decoration: BoxDecoration(
      color: const Color(0xFFFFEBDD),
      borderRadius: BorderRadius.circular(16),
    ),
    child: Row(
      children: [
        const Icon(
          Icons.info_outline_rounded,
          size: 20,
          color: Color(0xFF9A4A25),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Text(
            message,
            style: const TextStyle(fontSize: 12, color: Color(0xFF7A3F25)),
          ),
        ),
        IconButton(
          onPressed: onClose,
          tooltip: '안내 닫기',
          icon: const Icon(Icons.close_rounded, size: 18),
        ),
      ],
    ),
  );
}

class _GuideStep extends StatelessWidget {
  const _GuideStep({
    required this.number,
    required this.title,
    required this.body,
  });
  final String number;
  final String title;
  final String body;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 24),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _Pill(label: number, color: _accentSoft, fontSize: 13),
        const SizedBox(width: 16),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 7),
              Text(body, style: const TextStyle(color: _muted)),
            ],
          ),
        ),
      ],
    ),
  );
}

class PracticeScreen extends StatefulWidget {
  const PracticeScreen({
    super.key,
    required this.language,
    required this.bridge,
  });

  final AppLanguage language;
  final TranslationBridge bridge;

  @override
  State<PracticeScreen> createState() => _PracticeScreenState();
}

class _PracticeScreenState extends State<PracticeScreen> {
  bool _longMixed = false;
  @override
  void initState() {
    super.initState();
    unawaited(_setVisible(true));
  }

  Future<void> _setVisible(bool visible) async {
    try {
      await widget.bridge.setPracticeVisible(visible);
    } on MissingPluginException {
      // The reading sample remains available in Flutter-only previews.
    } on PlatformException {
      // A detached activity cannot capture its practice screen.
    }
  }

  @override
  void dispose() {
    unawaited(_setVisible(false));
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final sample = PracticeSample.forLanguage(widget.language);
    final title = Text(
      sample.title,
      style: const TextStyle(
        fontSize: 35,
        height: 1.25,
        fontWeight: FontWeight.w800,
        letterSpacing: -1,
      ),
    );
    final illustration = Container(
      height: 140,
      decoration: BoxDecoration(
        color: _accentSoft,
        borderRadius: BorderRadius.circular(14),
      ),
      child: const Icon(Icons.landscape_outlined, size: 80, color: _accent),
    );
    return Scaffold(
      appBar: AppBar(
        title: Text('${widget.language.label} 원문 연습'),
        actions: [
          PopupMenuButton<bool>(
            tooltip: '연습 내용 선택',
            initialValue: _longMixed,
            onSelected: (value) => setState(() => _longMixed = value),
            itemBuilder: (_) => [
              CheckedPopupMenuItem(
                value: false,
                checked: !_longMixed,
                child: const Text('기본 예문'),
              ),
              CheckedPopupMenuItem(
                value: true,
                checked: _longMixed,
                child: const Text('긴 문단·혼합 언어'),
              ),
            ],
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(28),
        children: [
          if (_longMixed)
            ..._longPractice()
          else ...[
            _Pill(
              label: '${widget.language.nativeLabel} · PRACTICE',
              color: _accentSoft,
            ),
            const SizedBox(height: 32),
            // The default floating control occupies this area; keep key CJK text below it.
            if (widget.language == AppLanguage.english) ...[
              title,
              const SizedBox(height: 24),
              illustration,
            ] else ...[
              illustration,
              const SizedBox(height: 24),
              title,
            ],
            const SizedBox(height: 28),
            Text(
              sample.heading,
              style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 12),
            Text(
              sample.body,
              style: const TextStyle(fontSize: 19, height: 1.7),
            ),
            const SizedBox(height: 22),
            Text(
              sample.tipsHeading,
              style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 12),
            Text(
              sample.tips,
              style: const TextStyle(fontSize: 19, height: 1.7),
            ),
            const SizedBox(height: 24),
            TextButton.icon(
              onPressed: () =>
                  Clipboard.setData(ClipboardData(text: sample.copyText)),
              icon: const Icon(Icons.copy_outlined),
              label: const Text('예문 복사'),
            ),
            const SizedBox(height: 180),
          ],
        ],
      ),
    );
  }

  List<Widget> _longPractice() {
    final sample = LongPracticeSample.forLanguage(widget.language);
    return [
      _Pill(
        label: '${widget.language.nativeLabel} · PRACTICE',
        color: _accentSoft,
      ),
      const SizedBox(height: 12),
      const Text(
        '직접 작성한 긴 문단이에요. 혼합 언어는 선택한 원문 언어에 따라 일부 글자를 읽지 못할 수 있어요.',
        style: TextStyle(fontSize: 12, color: _muted),
      ),
      // Keep the paragraph below the default floating control.
      const SizedBox(height: 80),
      Text(
        sample.title,
        style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
      ),
      const SizedBox(height: 12),
      Text(sample.body, style: const TextStyle(fontSize: 13, height: 1.2)),
      const SizedBox(height: 12),
      const Text(
        LongPracticeSample.mixed,
        style: TextStyle(fontSize: 13, height: 1.2),
      ),
      const SizedBox(height: 20),
      TextButton.icon(
        onPressed: () =>
            Clipboard.setData(ClipboardData(text: sample.copyText)),
        icon: const Icon(Icons.copy_outlined),
        label: const Text('예문 복사'),
      ),
      const SizedBox(height: 180),
    ];
  }
}
