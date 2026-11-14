package com.ilink.vehicle.domain

/** 흔한 한국 성씨. 첫 글자가 이 중 하나인 세 · 네 글자 한글 이름만 성을 뗀다 */
private const val SURNAMES = "김이박최정강조윤장임한오서신권황안송류전홍고문양손배백허유남심노하곽성차주우구민진나지엄채원천방공현함변염여추도소석선설마길연위표명기반왕금옥육인맹제모탁국어은편용예봉경"
private val HANGUL_NAME = Regex("^[가-힣]{3,4}$")

/** "김민지" → "민지", "테스트" → "테스트" (성씨가 아니면 그대로) */
fun givenName(full: String): String {
    val name = full.trim()
    return if (HANGUL_NAME.matches(name) && name[0] in SURNAMES) name.substring(1) else name
}

/** 사진 자리에 쓰는 짧은 이름: 성을 뗀 이름, 세 글자를 넘으면 뒤 두 글자 */
fun shortName(full: String): String = givenName(full).let { if (it.length > 3) it.takeLast(2) else it }
