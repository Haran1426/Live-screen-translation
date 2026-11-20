import 'models.dart';

/// Authored stress text, not translation output or an accuracy benchmark.
class LongPracticeSample {
  const LongPracticeSample(this.title, this.body);
  final String title;
  final String body;
  static const mixed = 'Sample A17 · library · 図書館 · 도서관 · 图书馆';
  String get copyText => '$body\n$mixed';

  static LongPracticeSample forLanguage(AppLanguage language) {
    final (title, sentence, repeats) = switch (language) {
      AppLanguage.english => (
        'Reading A17',
        'The library opens at 9 in the morning. This authored practice has no personal information. Read each sentence slowly and compare the visible words.',
        6,
      ),
      AppLanguage.korean => (
        '긴 문단 A17',
        '도서관은 오전 9시에 문을 엽니다. 이 연습 문장은 직접 작성했으며 개인 정보가 없습니다. 문장을 천천히 읽고 화면의 단어를 비교해 보세요.',
        9,
      ),
      AppLanguage.japanese => (
        '長い文章 A17',
        '図書館は午前9時に開きます。この練習文には個人情報はありません。ゆっくり読みながら、画面に見える言葉を比べてみましょう。',
        12,
      ),
      AppLanguage.chinese => (
        '长段落 A17',
        '图书馆上午9点开放。这是专门编写的练习文字，不含个人信息。请慢慢阅读每一个句子，并比较屏幕上能看到的文字。',
        14,
      ),
    };
    return LongPracticeSample(title, List.filled(repeats, sentence).join(' '));
  }
}

/// Authored source text only. These are reading exercises, never mock translations.
class PracticeSample {
  const PracticeSample({
    required this.title,
    required this.heading,
    required this.body,
    required this.tipsHeading,
    required this.tips,
    required this.copyText,
  });

  final String title;
  final String heading;
  final String body;
  final String tipsHeading;
  final String tips;
  final String copyText;

  static PracticeSample forLanguage(AppLanguage language) => switch (language) {
    AppLanguage.english => const PracticeSample(
      title: 'Find your\nnext little adventure.',
      heading: 'Take the scenic route',
      body:
          'A quiet walk can change your whole day. Leave your headphones at home, find a new street, and notice the small things around you.',
      tipsHeading: 'Good to know',
      tips:
          'The garden opens at nine in the morning. Admission is free. Bring a bottle of water and a little curiosity.',
      copyText: 'The garden opens at nine in the morning. Admission is free.',
    ),
    AppLanguage.japanese => const PracticeSample(
      title: '図書館は午前9時に開きます。',
      heading: '静かな朝を過ごそう',
      body: '新しい本を探したり、窓のそばでゆっくり読書をしたり。いつもと違う一冊に出会ってみませんか。',
      tipsHeading: 'ご利用案内',
      tips: '入館は無料です。館内では静かに過ごし、本は読み終わったら元の場所に戻しましょう。',
      copyText: '図書館は午前9時に開きます。入館は無料です。',
    ),
    AppLanguage.chinese => const PracticeSample(
      title: '图书馆上午9点开放。',
      heading: '在书页之间慢下来',
      body: '找一本新书，在窗边安静地阅读。放慢脚步，给自己留一点发现新故事的时间。',
      tipsHeading: '实用信息',
      tips: '入馆免费。请保持安静，读完后把书放回原处，让下一位读者也能找到它。',
      copyText: '图书馆上午9点开放。入馆免费。',
    ),
    AppLanguage.korean => const PracticeSample(
      title: '도서관은 오전 9시에 문을 엽니다.',
      heading: '책과 함께 조용한 아침',
      body: '새로운 책 한 권을 골라 창가에서 천천히 읽어 보세요. 익숙한 하루에도 작은 발견이 기다리고 있어요.',
      tipsHeading: '이용 안내',
      tips: '입장은 무료예요. 다른 독자를 위해 조용히 이용하고, 다 읽은 책은 원래 자리에 놓아 주세요.',
      copyText: '도서관은 오전 9시에 문을 엽니다. 입장은 무료예요.',
    ),
  };
}
