# 레퍼런스 (docs/refs)

이 폴더에는 **링크와 메모만** 둡니다. 영상, 이미지, 프레임, 랜드마크 원본 등 **미디어는 절대 커밋하지 않습니다.**
미디어는 라이브러리 폴더(`REFKIT_LIBRARY`, 기본 `~/refkit-library/<동작>/<id>/`)에만 둡니다.
이 폴더에는 `.yaml`, `.yml`, `.md` 파일만 허용됩니다(테스트가 검사합니다).

`<동작>.yaml` 형식 예시:

```yaml
motion: bow_draw
refs:
- id: video-01
  kind: video          # video | image | mocap | own
  url: https://www.youtube.com/watch?v=abc
  start: 1:02.5        # m:ss.s
  end: 1:09
  tags: [활, 정면]
  take: 가이 호흡이 느리다
  license: cc-by       # 선택
  marks: {start: 0:00, impact: 0:01.2, settle: 0:02.4}
```

비어 있는 필드는 생략합니다. 추가: `refkit add <url> --motion bow_draw --start 1:02.5 --end 1:09 --tags 활,정면 --take "메모"`

설치: `pip install -e tools/refkit[pose]`
