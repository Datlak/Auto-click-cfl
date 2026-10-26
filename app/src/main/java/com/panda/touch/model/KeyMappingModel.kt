package com.panda.touch.model

import java.util.UUID

enum class ActionType {
    SINGLE_TAP,     // Nhấn theo ngón tay (Down -> Up)
    RAPID_FIRE,     // Nhấp liên thanh (Auto click tốc độ cao khi giữ nút)
    SWIPE,          // Vuốt theo quỹ đạo định sẵn
    MACRO           // Chuỗi thao tác tuần tự
}

data class PointF(
    var x: Float,
    var y: Float
)

data class KeyMapping(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Button",
    var actionType: ActionType = ActionType.SINGLE_TAP,
    var triggerPos: PointF = PointF(200f, 500f),
    var targetPos: PointF = PointF(1800f, 500f),
    var sizeDp: Int = 54,
    var opacity: Float = 0.8f,
    var rapidFireClicksPerSec: Int = 10,
    var swipeDurationMs: Int = 200,
    var targetEndPoint: PointF? = null,
    var assignedSlot: Int = 5 // Slot 5-9
)

data class GameProfile(
    val id: String = UUID.randomUUID().toString(),
    var gameName: String = "Default Profile",
    var packageName: String = "com.default.game",
    val mappings: MutableList<KeyMapping> = mutableListOf()
)
