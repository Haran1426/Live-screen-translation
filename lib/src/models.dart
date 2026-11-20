enum SessionPhase { idle, starting, running, paused, error }

// Advisory OS snapshot, never a model-download progress or success signal.
enum DownloadNetwork { wifi, other, offline, unverified, unknown }

enum AppLanguage {
  english('en', '영어', 'English', 'EN'),
  korean('ko', '한국어', '한국어', 'KO'),
  japanese('ja', '일본어', '日本語', 'JA'),
  chinese('zh', '중국어', '中文', 'ZH');

  const AppLanguage(this.code, this.label, this.nativeLabel, this.shortLabel);
  final String code;
  final String label;
  final String nativeLabel;
  final String shortLabel;

  static AppLanguage fromCode(Object? code, AppLanguage fallback) =>
      values.where((value) => value.code == code).firstOrNull ?? fallback;
}

class TranslationBlock {
  const TranslationBlock({required this.original, required this.translated});
  factory TranslationBlock.fromMap(Map<Object?, Object?> map) =>
      TranslationBlock(
        original: map['original'] as String? ?? '',
        translated: map['translated'] as String? ?? '',
      );
  final String original;
  final String translated;
}

class SessionSnapshot {
  const SessionSnapshot({
    this.phase = SessionPhase.idle,
    this.message = '번역할 화면을 준비해 주세요',
    this.blocks = const [],
    this.elapsedMs = 0,
  });

  factory SessionSnapshot.fromMap(Map<Object?, Object?> map) => SessionSnapshot(
    phase:
        SessionPhase.values.where((p) => p.name == map['phase']).firstOrNull ??
        SessionPhase.idle,
    message: map['message'] as String? ?? '',
    blocks: (map['blocks'] as List<Object?>? ?? [])
        .whereType<Map<Object?, Object?>>()
        .map(TranslationBlock.fromMap)
        .toList(growable: false),
    elapsedMs: (map['elapsedMs'] as num?)?.toInt() ?? 0,
  );

  final SessionPhase phase;
  final String message;
  final List<TranslationBlock> blocks;
  final int elapsedMs;
  bool get isActive =>
      phase == SessionPhase.starting ||
      phase == SessionPhase.running ||
      phase == SessionPhase.paused;
}

class TranslationSettings {
  const TranslationSettings({
    this.source = AppLanguage.english,
    this.target = AppLanguage.korean,
    this.wifiOnly = true,
    this.intervalMs = 2000,
    this.cardTextSp = 16,
    this.cardHeightDp = 300,
  });

  factory TranslationSettings.fromMap(Map<Object?, Object?> map) =>
      TranslationSettings(
        source: AppLanguage.fromCode(map['source'], AppLanguage.english),
        target: AppLanguage.fromCode(map['target'], AppLanguage.korean),
        wifiOnly: map['wifiOnly'] as bool? ?? true,
        intervalMs: (map['intervalMs'] as num?)?.toInt() ?? 2000,
        cardTextSp: _choice(map['cardTextSp'], const [16, 20, 24], 16),
        cardHeightDp: _choice(map['cardHeightDp'], const [300, 420], 300),
      );

  final AppLanguage source;
  final AppLanguage target;
  final bool wifiOnly;
  final int intervalMs;
  final int cardTextSp;
  final int cardHeightDp;

  static int _choice(Object? value, List<int> choices, int fallback) =>
      choices.contains(value) ? (value as num).toInt() : fallback;

  TranslationSettings copyWith({
    AppLanguage? source,
    AppLanguage? target,
    bool? wifiOnly,
    int? intervalMs,
    int? cardTextSp,
    int? cardHeightDp,
  }) => TranslationSettings(
    source: source ?? this.source,
    target: target ?? this.target,
    wifiOnly: wifiOnly ?? this.wifiOnly,
    intervalMs: intervalMs ?? this.intervalMs,
    cardTextSp: cardTextSp ?? this.cardTextSp,
    cardHeightDp: cardHeightDp ?? this.cardHeightDp,
  );

  Map<String, Object> toMap() => {
    'source': source.code,
    'target': target.code,
    'wifiOnly': wifiOnly,
    'intervalMs': intervalMs,
    'cardTextSp': cardTextSp,
    'cardHeightDp': cardHeightDp,
  };
}
