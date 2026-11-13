# 흰색 UI 확정 참고

2026-11-12 사용자가 선택한 시안은 [흰색 시안3](ui-concepts/2026-11-12/v3-white/03-porcelain-split.png)이다. 시안의 예시 데이터·가상 곡명·이전/다음곡 버튼은 구현 기능으로 간주하지 않는다. 고정 흑백 기본 표지와 기억한 설정으로 즉시 수집을 선택했다. 기존 설계v0.2 원문은 변경하지 않는다.

색: 배경#F4F6F8, 상세흰색, 글자#20262D/#64717D, 기능 강조#63758A. Android Views, 실제 가용 창 기준960dp 미만은 기록 선택창으로 접는다. 보이는 버튼52dp·바깥 터치76dp 이상. 터치/상태/선택/실제 저장·재생의 증거는 구현 기록과 검증 문서에서 구분한다.

표지3종은 built-in image_gen으로 만든 고정 장식용 PNG다. 실제 주행 장소·사용자 사진·음원 파형을 표현하지 않는다. 공통 프롬프트: “Create one square monochrome album-cover asset for a restrained white automotive music app. Original architectural abstraction photographed in black and white. Bright whites, subtle light greys, crisp geometric shadows, premium quiet composition, no text, letters, logos, devices, people, roads, landscapes, colorful accents, vinyl records or musical symbols. This is a fixed decorative default cover, not a real location or a personal journey photo. Edge-to-edge square, no border, no rounded corners.”

- cover_concrete.png: “A sunlit pale concrete wall, one strong diagonal shadow crossing its lower third, precise minimal architecture.”
- cover_curve.png: “A gently curving white architectural wall with a soft grey recessed opening, restrained geometric composition.”
- cover_planes.png: “A minimal abstract facade of thin horizontal white planes, a single vertical dark grey shadow, simple high contrast rhythm.”

자산 위치는 vehicle/app/src/main/res/drawable-nodpi/이며 픽셀 편집 없이 원본을 보존했다. 앱에서 샘플링해 같은 이미지 객체를 재사용한다. 재생목록·선택 행은 ID별로 갱신하고, 재생 시각 갱신은 목록 조회/재구성을 수행하지 않는다.
