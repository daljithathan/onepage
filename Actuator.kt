package hathan.daljit.esp32_iot_studentversion_2

data class Actuator(
    val id: String,
    val name: String,
    val type: String,
    val pin: Int,
    val state: String? = null,
    val trigPin: Int? = null,
    val echoPin: Int? = null,
    val angle: Int? = null
)