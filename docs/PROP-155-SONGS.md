# 155번 소리 블록 악곡 목록: 원곡 구간 우선 수정본

이 문서는 `PROP-155-SONGS-extended.md`의 **임의 변주/연결 프레이즈** 문제를 수정하기 위해 작성한 자료입니다. 원곡에 실제로 존재하는 연속된 음형을 우선했고, 검증이 어려운 곡은 무리하게 길이를 늘리지 않았습니다.

- 입력: 숨바꼭질 155번 소리 블록, **E**를 누를 때마다 다음 음을 한 개씩 재생, **R**로 곡 변경.
- 표기: `C4`=가온 도, `#`=반음 올림, `b`=반음 내림. **한글 계이름은 삭제**했습니다.
- 음높이 범위: F#1~F#7. 모든 항목에서 범위 검사를 실행했습니다.
- 중요한 한계: 현재 재생기는 **음 길이, 쉼표, 화음, 성부 구분을 무시**하므로, 음을 정확히 나열해도 원곡의 리듬을 정확히 재현하지 못합니다. 특히 월광·쇼팽 에튀드·왈츠·합주곡에서 차이가 큽니다.
- **곡 길이가 무조건 길지는 않습니다.** 잘못된 음을 보태는 것보다 원곡 한 구절을 온전히 연주하는 편을 우선했습니다.
- `원전 대조 필요`가 표시된 항목은 확정 채보가 아닙니다. 기존 입력에서 임의 확장 부분만 제거한 임시판이며 후속 악보 확인이 필요합니다.
- 코드 위치: `src/main/java/dev/gungang/acoustics/server/PropAbilities.java`의 `SONGS`. **이 문서의 음 배열이 336 버전부터 게임에 그대로 반영되어 있습니다.** 곡을 바꿀 때는 이 문서와 `SONGS`를 함께 수정하세요.

## 곡 목록

| # | 곡명 | 음 수 | 검증 구분 |
|---:|---|---:|---|
| 1 | 소나타 파실레 | 75 | 악보 기준 선율 |
| 2 | 엘리제를 위하여 | 72 | 원전 서두 검토 |
| 3 | 파헬벨 카논 | 58 | 악보 대조 |
| 4 | 송어 | 75 | 악보 대조·이조 |
| 5 | 나비 | 64 | 악보 기준 발췌 |
| 6 | 월광 | 72 | 원전 반주 음형 기반 |
| 7 | 흑건 | 48 | 원전 모티프 중심 |
| 8 | 작은 별 | 42 | 전통 선율·완결 |
| 9 | 헝가리 무곡 제5번 | 37 | 원전 대조 추가 필요 |
| 10 | 터키 행진곡 | 71 | 원전 오른손 모티프 |
| 11 | 크시코스의 우편마차 | 32 | 원전 대조 필요 |
| 12 | 유모레스크 | 22 | 간이 편곡 원전 대조 |
| 13 | 방물장수들 | 37 | 기존 원곡 주제 보존 |
| 14 | 라데츠키 행진곡 | 81 | 편곡 선율 대조 |
| 15 | 캉캉 | 47 | 기보된 주제 검토 |
| 16 | 사계 중 여름 3악장 | 95 | 악보의 연속 마디 |
| 17 | 동물의 사육제 피날레 | 24 | 원전 대조 필요 |
| 18 | 꽃의 왈츠 | 29 | 원전 대조 필요 |
| 19 | 사계 중 겨울 1악장 | 100 | 원전 연속 악구 |
| 20 | 아름답고 푸른 도나우 | 74 | 악보 대조·이조 |

## 1. 소나타 파실레

**Mozart, Piano Sonata No.16 In C Major, K.545**

1악장 서두의 주제와 이어지는 실제 음계 악구. 끝 음은 구간 말미의 E이며, 반주가 빠지면 완전 종지 느낌은 약할 수 있음.

```text
C5 E5 G5 B4 C5 D5 C5 A5
G5 C6 G5 F5 E5 F5 E5 A4
B4 C5 D5 E5 F5 G5 A5 G5
F5 E5 D5 C5 B4 A4 G4 A4
B4 C5 D5 E5 F5 G5 F5 E5
D5 C5 B4 A4 G4 F4 G4 A4
B4 C5 D5 E5 F5 E5 D5 C5
B4 A4 G4 F4 E4 F4 G4 A4
B4 C5 D5 E5 D5 C5 B4 A4
G4 F4 E4
```

- **음 수:** 75
- **검증:** 악보 기준 선율
- **참고:** 원곡에는 음마다 다른 길이가 있으며, 8음씩 끊어 쓰는 것은 출력 편의를 위한 것임.
- **악보/참조:** https://github.com/MutopiaProject/MutopiaProject/blob/master/ftp/MozartWA/KV545/K545-1/K545-1.ly

## 2. 엘리제를 위하여

**Beethoven, Bagatelle No.25 In A Minor, WoO 59**

A주제 첫 번째 완결과 두 번째 반복에 이어지는 원곡 음형. A단조 돌아오는 지점에서 끝.

```text
E5 D#5 E5 D#5 E5 B4 D5 C5
A4 C4 E4 A4 B4 E4 G#4 B4
C5 E4 E5 D#5 E5 D#5 E5 B4
D5 C5 A4 C4 E4 A4 B4 E4
C5 B4 A4 B4 C5 D5 E5 G4
F5 E5 D5 F4 E5 D5 C5 E4
D5 C5 B4 E4 E5 E6 D#5 E5
D#5 E5 D#5 E5 B4 D5 C5 A4
C4 E4 A4 B4 E4 C5 B4 A4
```

- **음 수:** 72
- **검증:** 원전 서두 검토
- **참고:** 반주가 멜로디 사이에 삽입되는 부분은 오른손 원본과의 음역 확인이 추가로 필요함.
- **악보/참조:** https://github.com/MutopiaProject/MutopiaProject/blob/master/ftp/BeethovenLv/WoO59/fur_Elise_WoO59/fur_Elise_WoO59.ly

## 3. 파헬벨 카논

**Pachelbel, Canon In D Major, P.37**

정상적인 카논 선율의 느린 진행에서 빠른 선율로 이어지는 한 구간. 원전은 바이올린 단선율.

```text
D5 C#5 B4 A4 G4 F#4 G4 B4
F#4 A3 D4 F#4 B4 D5 B4 C#5
F#5 E5 D5 C#5 B4 A4 B4 C#5
C#5 D5 C#5 D5 D4 C#4 A4 E4
F#4 D4 D5 C#5 B4 C#5 F#4 A4
B4 G4 F#4 E4 G4 F#4 E4 D4
C#5 B4 A4 G4 F#4 E4 G4 F#4
E4 D4
```

- **음 수:** 58
- **검증:** 악보 대조
- **참고:** 원전 판본은 현악 바이올린 단선율로 되어 있어 한 번에 한 음씩 연주하는 장치에 적합함.
- **악보/참조:** https://github.com/MutopiaProject/MutopiaProject/blob/master/ftp/PachelbelJ/CanonInD/CanonInD.ly

## 4. 송어

**Schubert, Die Forelle, D.550**

원가곡의 첫 번째 연에서 이어지는 성악 선율을 D장조로 이조한 연속 구간. D에서 종료.

```text
A4 D5 D5 F#5 F#5 D5 A4 A4
A4 A4 E5 D5 C#5 B4 A4 A4
D5 D5 F#5 F#5 D5 A4 D5 C#5
B4 C#5 D5 G#4 A4 A4 C#5 C#5
D5 C#5 B4 C#5 D5 A4 D5 C#5
C#5 C#5 G5 E5 C#5 D5 D5 B4
B4 B4 D5 D5 A4 A4 A4 A4
E5 C#5 D5 D5 C#5 B4 B4 B4
D5 C#5 E5 D5 A4 A4 A4 A4
E5 C#5 D5
```

- **음 수:** 75
- **검증:** 악보 대조·이조
- **참고:** 원래 가곡 악보는 D♭장조 판본이며, 기존 문서와 맞추어 반음 올린 D장조로 통일.
- **악보/참조:** https://github.com/MutopiaProject/MutopiaProject/blob/master/ftp/SchubertF/D550/forelle/forelle-lys/melody.ly

## 5. 나비

**Chopin, Etude In G Flat Major, Op.25 No.9**

원곡 오른손 16분음표 음형의 첫 8마디를 연결한 버전. 여러 화음에서는 높은 성부 하나만 추림.

```text
Bb4 Bb5 Cb6 C6 Db5 Db6 Eb6 Db6
Gb4 Gb5 Ab5 A5 Bb4 Bb5 Cb6 Bb5
Eb4 Eb5 E5 F5 Gb4 Gb5 F5 Gb5
Ab4 Ab5 Bb5 Ab5 Ab4 Ab5 Bb5 Cb6
Gb5 Bb4 Cb6 C6 Db5 Db6 Eb6 Db6
Eb5 Gb4 Ab5 A5 Bb4 Bb5 Cb6 Bb5
Cb5 Eb4 E5 F5 Gb4 Gb5 F5 Gb5
Ab4 Ab5 Bb5 Ab5 Gb4 Gb5 F5 Gb5
```

- **음 수:** 64
- **검증:** 악보 기준 발췌
- **참고:** 옥타브·화음이 많은 원곡이므로 단선율로 들으면 양손 연주와 차이가 큼.
- **악보/참조:** https://github.com/ksnortum/chopin-opus-25/blob/main/includes/etude-op25-no9-parts.ily

## 6. 월광

**Beethoven, Piano Sonata No.14 In C Sharp Minor, Op.27 No.2**

1악장 도입부의 잔잔한 셋잇단음표 반주 패턴. 원곡에서는 위쪽 선율이 동시에 연주되므로 게임 단음색에서는 다르게 들림.

```text
G#3 C#4 E4 G#3 C#4 E4 G#3 C#4
E4 G#3 C#4 E4 G#3 C#4 E4 G#3
C#4 E4 G#3 C#4 E4 G#3 C#4 E4
B3 C#4 E4 B3 C#4 E4 B3 C#4
E4 B3 C#4 E4 A3 C#4 E4 A3
C#4 E4 A3 C#4 E4 A3 C#4 E4
F#3 A3 D4 F#3 A3 D4 F#3 A3
D4 F#3 A3 D4 G#3 C4 F#4 G#3
C4 F#4 G#3 C4 F#4 G#3 C4 F#4
```

- **음 수:** 72
- **검증:** 원전 반주 음형 기반
- **참고:** 원곡의 주선율 자체가 아닌 분산화음 반주만 추출한 형태. 명시적으로 구분.
- **악보/참조:** https://github.com/MutopiaProject/MutopiaProject/blob/master/ftp/BeethovenLv/O27/moonlight/moonlight-lys/moonlight1-a4.ly

## 7. 흑건

**Chopin, Etude In G Flat Major, Op.10 No.5**

첫머리 검은 건반 중심의 빠른 분산 음형. 기존 데이터 뒤의 임의 음계 변주 제거.

```text
Gb6 Bb6 Db6 Gb6 Eb6 Gb6 Db6 Gb6
Bb5 Db6 Gb5 Bb5 Gb5 Bb5 Db5 Gb5
Eb5 Gb5 Db5 Gb5 Bb4 Db5 Gb4 Bb4
Db4 Db5 Ab4 Db5 Ab4 Ab5 Ab4 Ab5
Eb5 Ab5 Eb5 Eb6 Eb5 Eb6 Ab5 Eb6
Ab5 Ab6 Ab5 Ab6 Db6 Ab6 Db6 Db7
```

- **음 수:** 48
- **검증:** 원전 모티프 중심
- **참고:** 옥타브가 높고 텍스처가 복잡해 일부 옥타브 배치를 기존 문서에서 가져옴. 엄밀한 1:1 채보는 아님.
- **악보/참조:** https://github.com/MutopiaProject/MutopiaProject/blob/master/ftp/ChopinFF/O10/chp-10-05/chp-10-05-lys/chp-10-05-a4.ly

## 8. 작은 별

**Mozart, 12 Variations On "Ah, Vous Dirai-Je, Maman", K.265**

주제곡 전체 6개 구절. 변주 없이 주제만 처음부터 끝까지.

```text
C5 C5 G5 G5 A5 A5 G5 F5
F5 E5 E5 D5 D5 C5 G5 G5
F5 F5 E5 E5 D5 G5 G5 F5
F5 E5 E5 D5 C5 C5 G5 G5
A5 A5 G5 F5 F5 E5 E5 D5
D5 C5
```

- **음 수:** 42
- **검증:** 전통 선율·완결
- **참고:** 제목의 12변주는 포함하지 않으며 모차르트가 변주한 기본 주제만 연주.
- **악보/참조:** https://www.mutopiaproject.org/cgibin/piece-info.cgi?id=2236

## 9. 헝가리 무곡 제5번

**Brahms, Hungarian Dance No.5, WoO 1**

기존 문서의 A단조 단선율 주제만 남기고 임의 연결부 삭제.

```text
E5 D5 E5 D5 F5 E5 B4 C5
A4 E5 D5 E5 F5 A5 G5 E5
F5 D5 F5 E5 F5 E5 E5 D5
B4 C5 B4 F5 E5 F5 E5 E5
B4 B4 D5 C5 A4
```

- **음 수:** 37
- **검증:** 원전 대조 추가 필요
- **참고:** 브람스의 피아노 연탄 원곡은 F#단조이며, 이 버전은 조성도 바뀐 기존 단선율 편곡. 정확한 원조 버전으로 대체 전임.
- **악보/참조:** https://clefsight.com/pieces/brahms-hungarian-dance-no-5-in-f-minor-3c0671

## 10. 터키 행진곡

**Mozart, Piano Sonata No.11 In A Major, K.331**

3악장 Alla Turca의 유명한 A단조 첫 주제 및 원전 재현. 같은 주제의 반복은 실제 곡에 포함된 반복임.

```text
B4 A4 G#4 A4 C5 D5 C5 B4
C5 E5 F5 E5 D#5 E5 B5 A5
G#5 A5 B5 A5 G#5 A5 C6 A5
C6 B5 A5 G#5 A5 B5 A5 G#5
A5 C6 A5 C6 B5 A5 G#5 F#5
E5 B4 A4 G#4 A4 C5 D5 C5
B4 C5 E5 F5 E5 D#5 E5 B5
A5 G#5 A5 B5 A5 G#5 A5 C6
A5 C6 B5 A5 G#5 A5 E5
```

- **음 수:** 71
- **검증:** 원전 오른손 모티프
- **참고:** 원전은 중간 장식음·화음도 있어 모두 표현되지는 않음.
- **악보/참조:** https://github.com/MutopiaProject/MutopiaProject/blob/master/ftp/MozartWA/KV331/KV331_3_RondoAllaTurca/KV331_3_RondoAllaTurca-lys/KV331_3_RondoAllaTurcaNotes.ly

## 11. 크시코스의 우편마차

**Necke, Csikos Post, Op.222**

기존 입력의 짧은 주제만 보존. 검증되지 않은 덧붙임 구간은 제거.

```text
B5 F#5 B5 B5 G5 B5 B5 D#5
F#5 B5 E6 E5 F#5 G5 B5 C6
E6 B5 A5 E5 F#5 G5 E5 G5
B5 F#5 D#5 F#5 B5 G5 F#5 E5
```

- **음 수:** 32
- **검증:** 원전 대조 필요
- **참고:** 악보 출처가 검증되지 않은 상태라 더 연장하면 오히려 잘못된 음이 추가될 위험이 큼.

## 12. 유모레스크

**Dvorak, Humoresque No.7, Op.101**

대표적인 완결 선율 22음. 원곡 G♭장조를 C장조로 옮긴 쉬운 버전. 임의로 늘리지 않음.

```text
C4 D4 C4 D4 E4 G4 A4 G4
A4 B4 D5 C5 B4 D5 C5 A4
G4 G4 C5 D4 G4 C4
```

- **음 수:** 22
- **검증:** 간이 편곡 원전 대조
- **참고:** 원곡 길이를 더 늘리려면 악보에서 다음 프레이즈를 새로 추출해야 함.
- **악보/참조:** https://pianumero.com/song/humoresque-dvorak

## 13. 방물장수들

**Russian Folk Song, Korobeiniki**

테트리스 테마로 유명한 민요의 주요 주제. 마지막 A4에서 종지.

```text
E5 B4 C5 D5 C5 B4 A4 A4
C5 E5 D5 C5 B4 C5 D5 E5
C5 A4 A4 D5 F5 A5 G5 F5
E5 C5 E5 D5 C5 B4 B4 C5
D5 E5 C5 A4 A4
```

- **음 수:** 37
- **검증:** 기존 원곡 주제 보존
- **참고:** 원곡 리듬과 장식음은 생략한 전통 민요 주선율.

## 14. 라데츠키 행진곡

**J. Strauss I, Radetzky March, Op.228**

요한 슈트라우스 1세 원곡 선율의 한 개 행진 주제를 사용. F장조 멜로디 자료를 원곡 D장조로 내려서 표기.

```text
F#5 E#5 F#5 F#5 E#5 F#5 F#5 E#5
F#5 E5 D5 F#5 E#5 F#5 F#5 E#5
F#5 E#5 F#5 F#5 E#5 F#5 B5 A5
A5 F#5 G5 B5 A5 A5 E5 F#5
B5 A5 A5 F#5 E5 C#6 D6 B5
C#6 A5 G#5 A5 B5 A5 G5 F#5
E#5 F#5 F#5 E#5 F#5 E5 D5 F#5
E#5 F#5 F#5 E#5 F#5 E#5 F#5 B5
A5 A5 F#5 G#5 F#5 E5 E5 D5
C#6 B5 A5 G5 F#5 E5 D5 D5
D5
```

- **음 수:** 81
- **검증:** 편곡 선율 대조
- **참고:** 읽기 쉬운 단선율로 재현해 짧은 장식음과 쉼은 생략함. E#는 피아노의 F음과 같은 건반.
- **악보/참조:** https://playocarina.com/songs/radetzky-march/

## 15. 캉캉

**Offenbach, Orpheus In The Underworld: Infernal Galop**

지옥의 갤럽에서 유명한 캉캉 주제. 실제 악구가 다시 반복되는 버전.

```text
C5 D5 F5 E5 D5 G5 G5 G5
A5 E5 F5 D5 D5 D5 F5 E5
D5 C5 C6 B5 A5 G5 F5 E5
D5 C5 D5 F5 E5 D5 G5 G5
G5 A5 E5 F5 D5 D5 D5 F5
E5 D5 C5 G5 D5 E5 C5
```

- **음 수:** 47
- **검증:** 기보된 주제 검토
- **참고:** 이 소스는 C장조 단선율 편곡으로, 원곡의 모든 관현악 음을 반영한 것은 아님.
- **악보/참조:** https://github.com/hra1129/Y8960_Cartridge/blob/main/y8960_fm_sample/mkcancan.py

## 16. 사계 중 여름 3악장

**Vivaldi, Violin Concerto In G Minor, Op.8 No.2, RV 315**

3악장 10~17마디(독주 바이올린)에서 폭풍우 하강 선율이 반복 저음을 교차하는 연속 구간.

```text
D5 C5 Bb4 A4 G4 F#4 E4 D4
C4 Bb3 A3 G3 G3 G3 G3 G3
G3 G3 G3 G3 G3 G3 G3 G3
G5 F5 Eb5 D5 C5 Bb4 A4 G4
F4 Eb4 D4 C4 C4 C4 C4 C4
C4 C4 C4 C4 C4 C4 C4 C4
Bb5 A5 G5 F5 Eb5 D5 C5 Bb4
A4 G4 F4 Eb4 Eb4 Eb4 Eb4 Eb4
Eb4 Eb4 Eb4 Eb4 Eb4 Eb4 Eb4 Eb4
D6 C6 Bb5 A5 G5 F#5 E5 D5
C5 Bb4 A4 G4 G4 G4 G4 G4
G4 G4 G4 G4 G4 G4 G4
```

- **음 수:** 95
- **검증:** 악보의 연속 마디
- **참고:** 동일음 12회 등의 반복도 원곡에 실제 존재함. 사용자가 빠르게 연타해야 하는 부분.
- **악보/참조:** https://github.com/MutopiaProject/MutopiaProject/blob/master/ftp/VivaldiA/O8/summer/summer-lys/summer3.ly

## 17. 동물의 사육제 피날레

**Saint-Saens, The Carnival Of The Animals: XIV. Finale**

기존 단선율 24음만 보존하고 임의로 이어 붙인 다른 음계 삭제.

```text
D5 D5 D5 D5 D5 E5 D#5 E5
A5 G5 F5 E5 D5 D5 D5 D5
D5 E5 D#5 E5 A5 G5 F5 E5
```

- **음 수:** 24
- **검증:** 원전 대조 필요
- **참고:** 원곡 피날레에는 합주 선율과 도입부 인용이 혼재하므로 단선율 편곡 검증 필요.
- **악보/참조:** https://www.kodaly.org.au/wp-content/uploads/2016/01/Carnival-of-the-Animals-Student-Workbook-Part-2.pdf

## 18. 꽃의 왈츠

**Tchaikovsky, The Nutcracker, Op.71: Waltz Of The Flowers**

기존 문서에서 가장 짧은 주제만 유지하고 임의 변주 삭제.

```text
G4 B4 D5 E5 D5 D5 G4 B4
D5 G5 F5 F5 B4 D5 F5 A5
E5 G5 F5 F5 C#5 E5 D5 D5
A4 C5 B4 A4 G4
```

- **음 수:** 29
- **검증:** 원전 대조 필요
- **참고:** 원곡 왈츠는 복수의 주제와 긴 서주가 있어, 이 단선율을 엄밀한 오리지널 전사로 보증하지 않음.
- **악보/참조:** https://github.com/musetrainer/library/blob/master/index.html

## 19. 사계 중 겨울 1악장

**Vivaldi, Violin Concerto In F Minor, Op.8 No.4, RV 297**

1악장 B부분(차가운 바람)에서 시작하여 원곡에 인접한 다음 빠른 악구까지 이어지는 독주 바이올린 선율.

```text
C5 C6 C6 G5 G5 Eb5 Eb5 C5
C5 C6 C6 G5 G5 Eb5 Eb5 C5
C5 C6 Bb5 Ab5 G5 F5 Eb5 D5
C5 C6 Bb5 Ab5 G5 F5 Eb5 D5
C5 G4 C5 Eb5 C5 G4 C5 Eb5
C5 G4 C5 Eb5 C5 G4 C5 Eb5
C5 C5 Eb5 Eb6 Eb6 C6 C6 G5
G5 Eb5 Eb5 Eb6 Eb6 C6 C6 G5
G5 Eb5 Eb5 Eb6 D6 C6 B5 A5
G5 F5 Eb5 Eb6 D6 C6 B5 A5
G5 F5 Eb5 C5 Eb5 G5 Eb5 C5
Eb5 G5 Eb5 C5 Eb5 G5 Eb5 C5
Eb5 G5 Eb5 Eb5
```

- **음 수:** 100
- **검증:** 원전 연속 악구
- **참고:** 32분음표 무리와 쉼표를 삭제해 음만 남긴 버전. 이전 확장판의 12~13마디 뒤 26~27마디 건너뛰기를 제거.
- **악보/참조:** https://github.com/MutopiaProject/MutopiaProject/blob/master/ftp/VivaldiA/O8/winter/winter-lys/winter1.ly

## 20. 아름답고 푸른 도나우

**J. Strauss II, On The Beautiful Blue Danube, Op.314**

대표 왈츠 첫 주제 전체를 C장조 원전 피아노 편곡에서 D장조로 통일해 옮김. D에서 마감.

```text
D4 D4 F#4 A4 A4 A5 A5 F#5
F#5 D4 D4 F#4 A4 A4 A5 A5
G5 G5 C#4 C#4 E4 B4 B4 B5
B5 G5 G5 C#4 C#4 E4 B4 B4
B5 B5 F#5 F#5 D4 D4 F#4 A4
D5 D6 D6 A5 A5 D4 D4 F#4
A4 D5 D6 D6 B5 B5 E4 E4
G4 B4 B4 B4 G#4 A4 F#5 F#5
D5 F#4 F#4 E4 B4 A4 D4 D5
D5 D5
```

- **음 수:** 74
- **검증:** 악보 대조·이조
- **참고:** 피아노 화음은 위 성부만 추렸으며, 3/4박자의 음 길이를 표현하려면 코드 측 수정 필요.
- **악보/참조:** https://github.com/MutopiaProject/MutopiaProject/blob/master/ftp/StraussJJ/O314/blue_danube/blue_danube.ly

---

## 이 파일을 코드에 적용할 때

마지막 음 다음에는 즉시 첫 음으로 돌아가므로, **곡의 끝과 시작이 자연스럽게 맞닿는지** 확인하세요. 음높이 문자열만으로 완전한 원곡을 재현할 수 없다는 점은 위 한계 설명과 같습니다. 각 음의 길이(예: `durationTicks`)를 지원하도록 코드 구조를 확장하면 중도 끊김처럼 느껴지는 문제를 더 근본적으로 개선할 수 있습니다.
