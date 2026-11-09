# 155번 소리 블록 악곡 목록 (확장판)

숨바꼭질 155번(소리 블록)으로 변장하면 E를 누를 때마다 고른 곡의 다음 음을 한 음씩 연주하고, R로 다음 곡을 고릅니다.
곡을 고르면 `[번호/20] 제목 (부제)` 형식으로 표시됩니다.

- 음 이름은 과학적 음높이 표기(C4 = 가운데 도)이며, 뒤의 숫자는 옥타브입니다.
- `#`는 샤프, `b`는 플랫입니다. 한글 계이름 중복 표기는 삭제했습니다.
- 쉼표와 음 길이는 없고, E를 누를 때마다 다음 음이 연주됩니다. 마지막 음 다음에는 처음으로 돌아갑니다.
- 연주 범위는 F#1 ~ F#7(6옥타브)이며, 모든 음을 이 범위 안에 배치했습니다.
- **확장 방식:** 기존 음 배열을 변경하지 않고, 그 뒤에 주제 변주·연결 프레이즈·주제 재현·짧은 마무리 음형을 추가한 **게임용 단선율 편곡**입니다. 추가 구간은 원곡 악보를 그대로 채보한 것이 아니므로 원곡과 1:1로 일치하지 않을 수 있습니다.
- 코드 위치: `src/main/java/dev/gungang/acoustics/server/PropAbilities.java`의 `SONGS`. **이 문서의 음 배열이 335 버전부터 게임에 그대로 반영되어 있습니다.** 곡을 바꿀 때는 이 문서와 `SONGS`를 함께 수정하세요.

## 목록

| # | 제목 | 부제 | 기존 음 수 | 확장 음 수 |
|---|---|---|---:|---:|
| 1 | Mozart, Piano Sonata No.16 In C Major, K.545 | 소나타 파실레 | 75 | **149** |
| 2 | Beethoven, Bagatelle No.25 In A Minor, WoO 59 | 엘리제를 위하여 | 77 | **152** |
| 3 | Pachelbel, Canon In D Major, P.37 | 파헬벨 카논 | 64 | **135** |
| 4 | Schubert, Die Forelle, D.550 | 송어 | 60 | **131** |
| 5 | Chopin, Etude In G Flat Major, Op.25 No.9 | 나비 | 32 | **112** |
| 6 | Beethoven, Piano Sonata No.14 In C Sharp Minor, Op.27 No.2 | 월광 | 67 | **138** |
| 7 | Chopin, Etude In G Flat Major, Op.10 No.5 | 흑건 | 48 | **125** |
| 8 | Mozart, 12 Variations On "Ah, Vous Dirai-Je, Maman", K.265 | 작은 별 | 42 | **119** |
| 9 | Brahms, Hungarian Dance No.5, WoO 1 | 헝가리 무곡 제5번 | 37 | **114** |
| 10 | Mozart, Piano Sonata No.11 In A Major, K.331 | 터키 행진곡 | 49 | **127** |
| 11 | Necke, Csikos Post, Op.222 | 크시코스의 우편마차 | 32 | **111** |
| 12 | Dvorak, Humoresque No.7, Op.101 | 유모레스크 | 22 | **101** |
| 13 | Russian Folk Song, Korobeiniki | 방물장수들 | 37 | **114** |
| 14 | J. Strauss I, Radetzky March, Op.228 | 라데츠키 행진곡 | 34 | **111** |
| 15 | Offenbach, Orpheus In The Underworld: Infernal Galop | 캉캉 | 35 | **113** |
| 16 | Vivaldi, Violin Concerto In G Minor, Op.8 No.2, RV 315 | 사계 중 여름 3악장 | 47 | **130** |
| 17 | Saint-Saens, The Carnival Of The Animals: XIV. Finale | 동물의 사육제 피날레 | 24 | **102** |
| 18 | Tchaikovsky, The Nutcracker, Op.71: Waltz Of The Flowers | 꽃의 왈츠 | 29 | **107** |
| 19 | Vivaldi, Violin Concerto In F Minor, Op.8 No.4, RV 297 | 사계 중 겨울 1악장 | 64 | **137** |
| 20 | J. Strauss II, On The Beautiful Blue Danube, Op.314 | 아름답고 푸른 도나우 | 37 | **114** |

## 1. 소나타 파실레

**Mozart, Piano Sonata No.16 In C Major, K.545**

음 이름 (149음):

```
C5 E5 G5 B4 C5 D5 C5 A5
G5 C6 G5 F5 E5 F5 E5 A4
B4 C5 D5 E5 F5 G5 A5 G5
F5 E5 D5 C5 B4 A4 G4 A4
B4 C5 D5 E5 F5 G5 F5 E5
D5 C5 B4 A4 G4 F4 G4 A4
B4 C5 D5 E5 F5 E5 D5 C5
B4 A4 G4 F4 E4 F4 G4 A4
B4 C5 D5 E5 D5 C5 B4 A4
G4 F4 E4 C5 E5 G5 C6 B5
A5 G5 F5 E5 D5 C5 B4 A4
G4 F4 E4 F4 G4 A4 B4 C5
D5 E5 F5 G5 A5 G5 F5 E5
D5 C5 G4 A4 B4 C5 E5 D5
C5 B4 A4 C5 E5 G5 B4 C5
D5 C5 A5 G5 C6 G5 F5 E5
F5 E5 A4 B4 C5 D5 E5 F5
G5 A5 G5 E5 D5 C5 B4 C5
D5 E5 C5 G4 C5
```

## 2. 엘리제를 위하여

**Beethoven, Bagatelle No.25 In A Minor, WoO 59**

음 이름 (152음):

```
E5 D#5 E5 D#5 E5 B4 D5 C5
A4 C4 E4 A4 B4 E4 G#4 B4
C5 E4 E5 D#5 E5 D#5 E5 B4
D5 C5 A4 C4 E4 A4 B4 E4
C5 B4 A4 B4 C5 D5 E5 G4
F5 E5 D5 F4 E5 D5 C5 E4
D5 C5 B4 E4 E5 E5 E6 D#5
E5 D#5 E5 D#5 E5 D#5 E5 D#5
E5 B4 D5 C5 A4 C4 E4 A4
B4 E4 C5 B4 A4 A4 E5 A5
B5 C6 B5 A5 G5 F5 E5 D5
C5 B4 A4 G#4 B4 E5 D#5 E5
F5 E5 D5 C5 B4 C5 D5 E5
F5 E5 D5 C5 B4 A4 E4 A4
C5 B4 A4 E5 D#5 E5 E5 D#5
E5 D#5 E5 B4 D5 C5 A4 C4
E4 A4 B4 E4 G#4 B4 C5 E4
E5 D#5 E5 D#5 E5 B4 E5 D#5
E5 B4 D5 C5 A4 C4 E4 A4
```

## 3. 파헬벨 카논

**Pachelbel, Canon In D Major, P.37**

음 이름 (135음):

```
D5 C#5 D5 D4 C#4 A4 E4 F#4
D4 D5 C#5 B4 C#5 F#5 A5 B5
G5 F#5 E5 G5 F#5 E5 D5 C#5
B4 A4 G4 F#4 E4 G4 F#4 E4
D4 E4 F#4 G4 A4 E4 A4 G4
F#4 B4 A4 G4 A4 G4 F#4 E4
D4 B3 B4 C#5 D5 C#5 B4 A4
G4 F#4 E4 B4 A4 B4 A4 G4
D5 C#5 B4 A4 G4 F#4 G4 B4
F#5 A4 D5 F#5 B5 D6 B5 C#6
F#5 E5 D5 C#5 B4 A4 B4 C#5
D5 C#5 B4 A4 G4 F#4 E4 D4
F#4 A4 D5 A4 F#4 E4 D4 D5
C#5 D5 D4 C#4 A4 E4 F#4 D4
D5 C#5 B4 C#5 F#5 A5 B5 G5
F#5 E5 G5 F#5 E5 D5 C#5 A4
F#4 D4 F#4 A4 D5 C#5 D5
```

## 4. 송어

**Schubert, Die Forelle, D.550**

음 이름 (131음):

```
A4 D5 D5 F#5 F#5 D5 A4 A4
A4 A4 E5 D5 C#5 B4 A4 A4
D5 D5 F#5 F#5 D5 A4 D5 C#5
B4 C#5 D5 G#4 A4 A4 C#5 C#5
D5 C#5 B4 C#5 D5 A4 D5 C#5
C#5 C#5 G5 E5 C#5 D5 D5 B4
B4 B4 D5 D5 A4 A4 A4 A4
E5 C#5 D5 D5 D5 D5 F#5 F#5
D5 A4 A4 A4 A4 E5 D5 C#5
B4 A4 D5 D5 F#5 F#5 D5 A4
D5 C#5 B4 C#5 D5 G#4 A4 A4
B4 B4 B4 D5 D5 A4 A4 A4
A4 E5 C#5 D5 A4 D5 D5 F#5
F#5 D5 A4 A4 A4 A4 E5 D5
C#5 B4 A4 A4 D5 D5 F#5 F#5
D5 A4 D5 C#5 A4 D5 D5 F#5
D5 A4 D5
```

## 5. 나비

**Chopin, Etude In G Flat Major, Op.25 No.9**

음 이름 (112음):

```
Bb4 Bb5 Cb6 C6 Db5 Db6 Eb6 Db6
Gb4 Gb5 Ab5 A5 Bb4 Bb5 Cb6 Bb5
Eb4 Eb5 E5 F5 Gb4 Gb5 F5 Gb5
Ab4 Ab5 Bb5 Ab5 Ab4 Ab5 Bb5 Cb6
Bb4 Gb5 Bb5 Db6 Cb6 Bb5 Ab5 Gb5
F5 Gb5 Ab5 Bb5 Cb6 Db6 Eb6 Db6
Cb6 Bb5 Ab5 Gb5 Eb5 Gb5 Bb5 Db6
F6 Eb6 Db6 Cb6 Bb5 Ab5 Gb5 F5
Eb5 F5 Gb5 Ab5 Bb5 Cb6 Db6 Eb6
Bb4 Bb5 Cb6 C6 Db5 Db6 Eb6 Db6
Gb4 Gb5 Ab5 A5 Bb4 Bb5 Cb6 Bb5
Eb4 Eb5 E5 F5 Gb4 Gb5 F5 Gb5
Ab4 Ab5 Bb5 Ab5 Ab4 Ab5 Bb5 Cb6
Ab5 Gb5 F5 Eb5 Db5 Bb4 Gb4 Bb4
```

## 6. 월광

**Beethoven, Piano Sonata No.14 In C Sharp Minor, Op.27 No.2**

음 이름 (138음):

```
C#3 G#3 C#4 E4 G#3 C#4 E4 G#3
C#4 E4 G#3 C#4 E4 B2 G#3 C#4
E4 G#3 C#4 E4 A2 A3 C#4 E4
F#2 A3 D4 F#4 G#2 G#3 C4 F#4
G#3 C#4 E4 G#3 C#4 D#4 F#3 C4
D#4 C#3 E3 G#3 C#4 G#4 G#4 G#4
G#4 G#4 G#4 A4 G#4 F#4 B4 E4
G#4 G#4 G#4 G#4 G#4 G#4 A4 G#4
F#4 B4 E4 C#3 G#3 C#4 E4 C#3
G#3 C#4 E4 B2 G#3 D#4 F#4 B2
G#3 D#4 F#4 A2 E3 C#4 E4 A2
E3 C#4 E4 G#2 D#3 C4 F#4 G#2
D#3 C4 F#4 C#3 G#3 C#4 E4 G#3
C#4 E4 G#4 C#3 G#3 C#4 E4 G#3
C#4 E4 G#3 C#4 E4 G#3 C#4 E4
B2 G#3 C#4 E4 G#3 C#4 E4 A2
A3 C#4 E4 C#4 G#3 C#4 E4 G#4
E4 C#4
```

## 7. 흑건

**Chopin, Etude In G Flat Major, Op.10 No.5**

음 이름 (125음):

```
Gb6 Bb6 Db6 Gb6 Eb6 Gb6 Db6 Gb6
Bb5 Db6 Gb5 Bb5 Gb5 Bb5 Db5 Gb5
Eb5 Gb5 Db5 Gb5 Bb4 Db5 Gb4 Bb4
Db4 Db5 Ab4 Db5 Ab4 Ab5 Ab4 Ab5
Eb5 Ab5 Eb5 Eb6 Eb5 Eb6 Ab5 Eb6
Ab5 Ab6 Ab5 Ab6 Db6 Ab6 Db6 Db7
Gb5 Bb5 Db6 Eb6 Gb6 Eb6 Db6 Bb5
Ab5 Gb5 Eb5 Db5 Gb5 Bb5 Db6 Gb6
Bb6 Gb6 Eb6 Db6 Bb5 Gb5 Eb5 Db5
Gb5 Ab5 Bb5 Db6 Eb6 Gb6 Ab6 Gb6
F6 Eb6 Db6 Cb6 Bb5 Ab5 Gb5 Eb5
Gb6 Bb6 Db6 Gb6 Eb6 Gb6 Db6 Gb6
Bb5 Db6 Gb5 Bb5 Gb5 Bb5 Db5 Gb5
Eb5 Gb5 Db5 Gb5 Bb4 Db5 Gb4 Bb4
Db4 Db5 Ab4 Db5 Ab4 Ab5 Db6 Bb5
Gb5 Eb5 Db5 Bb4 Gb4
```

## 8. 작은 별

**Mozart, 12 Variations On "Ah, Vous Dirai-Je, Maman", K.265**

음 이름 (119음):

```
C5 C5 G5 G5 A5 A5 G5 F5
F5 E5 E5 D5 D5 C5 G5 G5
F5 F5 E5 E5 D5 G5 G5 F5
F5 E5 E5 D5 C5 C5 G5 G5
A5 A5 G5 F5 F5 E5 E5 D5
D5 C5 C5 D5 E5 F5 G5 G5
G5 A5 G5 F5 E5 D5 C5 G4
C5 E5 G5 A5 G5 F5 E5 D5
C5 C5 E5 G5 C6 B5 A5 G5
F5 E5 D5 C5 D5 E5 F5 G5
E5 D5 C5 C5 C5 G5 G5 A5
A5 G5 F5 F5 E5 E5 D5 D5
C5 G5 G5 F5 F5 E5 E5 D5
G5 G5 F5 F5 E5 E5 D5 C5
C5 G5 F5 E5 D5 C5 C5
```

## 9. 헝가리 무곡 제5번

**Brahms, Hungarian Dance No.5, WoO 1**

음 이름 (114음):

```
E5 D5 E5 D5 F5 E5 B4 C5
A4 E5 D5 E5 F5 A5 G5 E5
F5 D5 F5 E5 F5 E5 E5 D5
B4 C5 B4 F5 E5 F5 E5 E5
B4 B4 D5 C5 A4 A4 C5 E5
A5 G5 F5 E5 D5 C5 B4 A4
B4 C5 D5 E5 F5 E5 D5 C5
B4 A4 E5 F5 E5 D5 E5 F5
A5 G5 F5 E5 D5 C5 B4 A4
C5 D5 E5 F5 E5 E5 D5 E5
D5 F5 E5 B4 C5 A4 E5 D5
E5 F5 A5 G5 E5 F5 D5 F5
E5 F5 E5 E5 D5 B4 C5 B4
F5 E5 F5 E5 D5 C5 B4 A4
E5 A4
```

## 10. 터키 행진곡

**Mozart, Piano Sonata No.11 In A Major, K.331**

음 이름 (127음):

```
B4 A4 G#4 A4 C5 D5 C5 B4
C5 E5 F5 E5 D#5 E5 B5 A5
G#5 A5 B5 A5 G#5 A5 C6 A5
C6 B5 A5 G5 A5 B5 A5 G5
A5 B5 A5 G5 F#5 E5 E5 F5
G5 G5 A5 G5 F5 E5 D5 E5
F5 A5 G#5 A5 B5 C6 B5 A5
G#5 A5 E5 A5 C6 B5 A5 G#5
E5 F5 E5 D5 C5 B4 A4 B4
C5 D5 E5 E5 F5 E5 D#5 E5
B4 C5 D5 E5 F5 E5 D5 C5
B4 A4 B4 A4 G#4 A4 C5 D5
C5 B4 C5 E5 F5 E5 D#5 E5
B5 A5 G#5 A5 B5 A5 G#5 A5
C6 A5 C6 B5 A5 G5 A5 B5
C5 B4 A4 G#4 A4 E5 A4
```

## 11. 크시코스의 우편마차

**Necke, Csikos Post, Op.222**

음 이름 (111음):

```
B5 F#5 B5 B5 G5 B5 B5 D#5
F#5 B5 E6 E5 F#5 G5 B5 C6
E6 B5 A5 E5 F#5 G5 E5 G5
B5 F#5 D#5 F#5 B5 G5 F#5 E5
E5 G5 B5 E6 D6 C6 B5 A5
G5 F#5 E5 D#5 E5 F#5 G5 B5
B5 A5 G5 F#5 E5 F#5 G5 A5
B5 C6 B5 A5 G5 F#5 E5 D#5
E5 G5 B5 E6 B5 G5 F#5 E5
B5 F#5 B5 B5 G5 B5 B5 D#5
F#5 B5 E6 E5 F#5 G5 B5 C6
E6 B5 A5 E5 F#5 G5 E5 G5
B5 F#5 D#5 F#5 B5 G5 F#5 E5
B5 A5 G5 F#5 E5 B4 E5
```

## 12. 유모레스크

**Dvorak, Humoresque No.7, Op.101**

음 이름 (101음):

```
C4 D4 C4 D4 E4 G4 A4 G4
A4 B4 D5 C5 B4 D5 C5 A4
G4 G4 C5 D4 G4 C4 C4 D4
C4 D4 E4 G4 A4 G4 F4 E4
D4 C4 E4 G4 C5 B4 A4 G4
F4 E4 D4 C4 G4 G4 C5 E5
D5 C5 B4 A4 G4 F4 E4 D4
C4 E4 G4 A4 G4 E4 C4 D4
C4 D4 E4 G4 A4 G4 A4 B4
D5 C5 B4 D5 C5 A4 G4 G4
C5 D4 G4 C4 C4 D4 C4 D4
E4 G4 A4 G4 A4 B4 G4 F4
E4 D4 C4 G4 C4
```

## 13. 방물장수들

**Russian Folk Song, Korobeiniki**

음 이름 (114음):

```
E5 B4 C5 D5 C5 B4 A4 A4
C5 E5 D5 C5 B4 C5 D5 E5
C5 A4 A4 D5 F5 A5 G5 F5
E5 C5 E5 D5 C5 B4 B4 C5
D5 E5 C5 A4 A4 A4 B4 C5
D5 E5 E5 D5 C5 B4 C5 D5
E5 C5 A4 B4 C5 D5 F5 A5
G5 F5 E5 D5 C5 B4 A4 G#4
A4 B4 C5 D5 E5 C5 A4 A4
C5 E5 A5 G5 F5 E5 B4 C5
D5 C5 B4 A4 A4 C5 E5 D5
C5 B4 C5 D5 E5 C5 A4 A4
D5 F5 A5 G5 F5 E5 C5 E5
D5 C5 B4 E5 D5 C5 B4 A4
E5 A4
```

## 14. 라데츠키 행진곡

**J. Strauss I, Radetzky March, Op.228**

음 이름 (111음):

```
D5 D5 D5 D5 D5 D5 F#5 E5
D5 C#5 B4 A#4 B4 C#5 D5 E5
A4 F#5 F5 F#5 F#5 F5 F#5 F#5
F5 F#5 E5 D5 F#5 F5 F#5 F#5
F5 F#5 D5 F#5 A5 A5 F#5 E5
D5 C#5 D5 E5 F#5 A5 F#5 E5
D5 A4 D5 D5 D5 F#5 E5 D5
C#5 B4 A#4 B4 C#5 D5 E5 F#5
G5 A5 F#5 E5 D5 C#5 D5 A4
D5 D5 D5 D5 D5 D5 D5 F#5
E5 D5 C#5 B4 A#4 B4 C#5 D5
E5 A4 F#5 F5 F#5 F#5 F5 F#5
F#5 F5 F#5 E5 D5 F#5 F5 F#5
F#5 A5 F#5 E5 D5 C#5 D5
```

## 15. 캉캉

**Offenbach, Orpheus In The Underworld: Infernal Galop**

음 이름 (113음):

```
G4 D4 D4 A4 C5 B4 A4 D5
D5 D5 E5 B4 C5 A4 A4 A4
C5 B4 A4 G4 G5 F#5 E5 D5
C5 B4 A4 G4 D4 D4 A4 C5
B4 A4 D5 G4 B4 D5 G5 F#5
E5 D5 C5 B4 A4 G4 A4 B4
C5 D5 E5 F#5 G5 A5 G5 F#5
E5 D5 C5 B4 A4 G4 D4 G4
B4 D5 G5 B5 A5 G5 F#5 E5
D5 C5 B4 G4 D4 D4 A4 C5
B4 A4 D5 D5 D5 E5 B4 C5
A4 A4 A4 C5 B4 A4 G4 G5
F#5 E5 D5 C5 B4 A4 G4 D4
D4 G5 F#5 E5 D5 C5 B4 A4
G4
```

## 16. 사계 중 여름 3악장

**Vivaldi, Violin Concerto In G Minor, Op.8 No.2, RV 315**

음 이름 (130음):

```
D5 C5 Bb4 A4 G4 F#4 E4 D4
C4 Bb3 A3 G3 G3 G3 G3 G3
G3 G3 G3 G3 G3 G3 G3 G3
G5 F5 Eb5 D5 C5 Bb4 A4 G4
F4 Eb4 D4 C4 C4 C4 C4 C4
C4 C4 C4 C4 C4 C4 C4 G4
G4 G4 G4 G4 G4 G4 G4 G4
G5 F5 Eb5 D5 C5 Bb4 A4 G4
G4 G4 G4 C5 Bb5 A5 G5 F5
Eb5 D5 C5 Bb4 A4 G4 F4 Eb4
D4 C4 C4 C4 C4 G4 F#4 G4
A4 Bb4 C5 D5 D5 C5 Bb4 A4
G4 F#4 E4 D4 C4 Bb3 A3 G3
G3 G3 G3 G3 G3 G3 G3 G3
G3 G3 G3 G3 G5 F5 Eb5 D5
C5 Bb4 G5 F5 Eb5 D5 C5 Bb4
A4 G4
```

## 17. 동물의 사육제 피날레

**Saint-Saens, The Carnival Of The Animals: XIV. Finale**

음 이름 (102음):

```
D5 D5 D5 D5 D5 E5 D#5 E5
A5 G5 F5 E5 D5 D5 D5 D5
D5 E5 D#5 E5 A5 G5 F5 E5
D5 D5 D5 D5 E5 D#5 E5 F5
G5 A5 G5 F5 E5 D5 C5 D5
E5 F5 G5 A5 B5 C6 B5 A5
G5 F5 E5 D5 E5 D#5 E5 A5
G5 F5 E5 D5 D5 D5 D5 D5
D5 D5 D5 D5 E5 D#5 E5 A5
G5 F5 E5 D5 D5 D5 D5 D5
E5 D#5 E5 A5 G5 F5 E5 D5
D5 D5 D5 D5 E5 D#5 E5 A5
G5 F5 E5 D5 C5 D5
```

## 18. 꽃의 왈츠

**Tchaikovsky, The Nutcracker, Op.71: Waltz Of The Flowers**

음 이름 (107음):

```
G4 B4 D5 E5 D5 D5 G4 B4
D5 G5 F5 F5 B4 D5 F5 A5
E5 G5 F5 F5 C#5 E5 D5 D5
A4 C5 B4 A4 G4 G4 B4 D5
G5 A5 G5 F#5 E5 D5 B4 D5
G5 F#5 E5 D5 C5 B4 A4 G4
B4 D5 G5 A5 B5 A5 G5 F#5
E5 D5 C5 B4 A4 B4 D5 F#5
G5 A5 G5 F#5 E5 G4 B4 D5
E5 D5 D5 G4 B4 D5 G5 F5
F5 B4 D5 F5 A5 E5 G5 F5
F5 C#5 E5 D5 D5 A4 C5 B4
A4 G4 G4 B4 D5 B4 A4 G4
D5 B4 G4
```

## 19. 사계 중 겨울 1악장

**Vivaldi, Violin Concerto In F Minor, Op.8 No.4, RV 297**

음 이름 (137음):

```
C5 C6 C6 G5 G5 Eb5 Eb5 C5
C5 C6 C6 G5 G5 Eb5 Eb5 C5
C5 C6 Bb5 Ab5 G5 F5 Eb5 D5
C5 C6 Bb5 Ab5 G5 F5 Eb5 D5
F5 E5 F5 G5 F5 G5 E5 F5
G5 F5 G5 Ab5 G5 Ab5 F5 G5
Ab5 G5 Ab5 Bb5 Ab5 Bb5 G5 Ab5
Bb5 Ab5 Bb5 C6 Bb5 C6 Ab5 Bb5
C5 C6 Bb5 Ab5 G5 F5 Eb5 D5
C5 Bb4 Ab4 G4 F4 G4 Ab4 Bb4
C5 Eb5 G5 C6 Bb5 Ab5 G5 F5
E5 F5 G5 Ab5 G5 F5 E5 F5
G5 Ab5 Bb5 C6 Bb5 Ab5 G5 F5
E5 F5 C5 C6 C6 G5 G5 Eb5
Eb5 C5 C5 C6 C6 G5 G5 Eb5
Eb5 C5 C5 C6 Bb5 Ab5 G5 F5
Eb5 D5 F5 E5 F5 G5 Ab5 G5
F5
```

## 20. 아름답고 푸른 도나우

**J. Strauss II, On The Beautiful Blue Danube, Op.314**

음 이름 (114음):

```
D4 D4 F#4 A4 A4 A5 A5 F#5
F#5 D4 D4 F#4 A4 A4 A5 A5
G5 G5 C#4 C#4 E4 B4 B4 B5
B5 G5 G5 C#4 C#4 E4 B4 B4
B5 B5 F#5 F#5 D4 D4 F#4 A4
D5 D6 D6 B5 B5 D4 F#4 A4
D5 D6 D6 C#6 C#6 C#4 E4 B4
B4 B5 B5 G5 G5 C#4 E4 B4
B4 B5 B5 F#5 F#5 D4 F#4 A4
D5 D6 B5 A5 F#5 D4 D4 F#4
A4 A4 A5 A5 F#5 F#5 D4 D4
F#4 A4 A4 A5 A5 G5 G5 C#4
C#4 E4 B4 B4 B5 B5 G5 G5
C#4 C#4 E4 A5 F#5 E5 D5 A4
F#4 D4
```

