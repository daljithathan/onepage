package hathan.daljit.esp32_iot_studentversion_2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hathan.daljit.esp32_iot_studentversion_2.Actuator

@Composable
fun AddActuatorDialog(
    actuators: List<Actuator>,
    onDismiss: () -> Unit,
    onAdd: (name: String, pin: Int, type: String, trigPin: Int?, echoPin: Int?, angle: Int?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf<Int?>(null) }
    var trigPin by remember { mutableStateOf<Int?>(null) }
    var echoPin by remember { mutableStateOf<Int?>(null) }
    var type by remember { mutableStateOf("LED") }
    var pinExpanded by remember { mutableStateOf(false) }
    var trigPinExpanded by remember { mutableStateOf(false) }
    var echoPinExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val validPins = listOf(4, 5, 12, 14, 15, 23, 27, 32, 33, 34, 35, 36, 39)
    val analogPins = listOf(34, 35, 36, 39) // ADC pins for YL-69 and LM393
    val pinDescriptors = validPins.associateWith { pin ->
        if (pin in analogPins) "Analog" else "Digital"
    }
    val usedPins = actuators.flatMap {
        if (it.type == "HCSR04") {
            listOfNotNull(it.trigPin, it.echoPin)
        } else {
            listOf(it.pin)
        }
    }
    val availablePins = validPins.filter { it !in usedPins }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Add Actuator",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null && name.isEmpty()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    OutlinedTextField(
                        value = type,
                        onValueChange = {},
                        label = { Text("Type") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            Icon(
                                if (typeExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.clickable { typeExpanded = !typeExpanded }
                            )
                        }
                    )
                    DropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("LED", "DHT11", "HCSR04", "Servo", "YL69", "TTP223", "PIR", "LM393").forEach { actuatorType ->
                            DropdownMenuItem(
                                text = { Text(actuatorType) },
                                onClick = {
                                    type = actuatorType
                                    if (actuatorType != "HCSR04") {
                                        trigPin = null
                                        echoPin = null
                                    } else {
                                        pin = null
                                    }
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (type != "HCSR04") {
                    Box {
                        OutlinedTextField(
                            value = pin?.let { "$it (${pinDescriptors[it]})" } ?: "",
                            onValueChange = {},
                            label = { Text(if (type in listOf("YL69", "LM393")) "Analog Pin" else "Pin") },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            trailingIcon = {
                                Icon(
                                    if (pinExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.clickable { pinExpanded = !pinExpanded }
                                )
                            },
                            isError = error != null && pin == null
                        )
                        DropdownMenu(
                            expanded = pinExpanded,
                            onDismissRequest = { pinExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            (if (type in listOf("YL69", "LM393")) analogPins.filter { it !in usedPins } else availablePins).forEach { pinNumber ->
                                DropdownMenuItem(
                                    text = { Text("$pinNumber (${pinDescriptors[pinNumber]})") },
                                    onClick = {
                                        pin = pinNumber
                                        pinExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Box {
                        OutlinedTextField(
                            value = trigPin?.let { "$it (${pinDescriptors[it]})" } ?: "",
                            onValueChange = {},
                            label = { Text("Trigger Pin") },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            trailingIcon = {
                                Icon(
                                    if (trigPinExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.clickable { trigPinExpanded = !trigPinExpanded }
                                )
                            },
                            isError = error != null && trigPin == null
                        )
                        DropdownMenu(
                            expanded = trigPinExpanded,
                            onDismissRequest = { trigPinExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            availablePins.filter { it != echoPin }.forEach { pinNumber ->
                                DropdownMenuItem(
                                    text = { Text("$pinNumber (${pinDescriptors[pinNumber]})") },
                                    onClick = {
                                        trigPin = pinNumber
                                        trigPinExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box {
                        OutlinedTextField(
                            value = echoPin?.let { "$it (${pinDescriptors[it]})" } ?: "",
                            onValueChange = {},
                            label = { Text("Echo Pin") },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            trailingIcon = {
                                Icon(
                                    if (echoPinExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.clickable { echoPinExpanded = !echoPinExpanded }
                                )
                            },
                            isError = error != null && echoPin == null
                        )
                        DropdownMenu(
                            expanded = echoPinExpanded,
                            onDismissRequest = { echoPinExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            availablePins.filter { it != trigPin }.forEach { pinNumber ->
                                DropdownMenuItem(
                                    text = { Text("$pinNumber (${pinDescriptors[pinNumber]})") },
                                    onClick = {
                                        echoPin = pinNumber
                                        echoPinExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        name.isEmpty() -> error = "Name cannot be empty"
                        type == "HCSR04" && (trigPin == null || echoPin == null) -> error = "Please select both trigger and echo pins"
                        type == "HCSR04" && trigPin == echoPin -> error = "Trigger and echo pins must be different"
                        type in listOf("LED", "DHT11", "Servo", "YL69", "TTP223", "PIR", "LM393") && pin == null -> error = "Please select a pin"
                        type == "LED" && actuators.any { it.type == "LED" && it.pin == pin } ->
                            error = "Pin $pin is already used by another LED"
                        type == "Servo" && actuators.any { it.type == "Servo" && it.pin == pin } ->
                            error = "Pin $pin is already used by another Servo"
                        type == "YL69" && actuators.any { it.type == "YL69" && it.pin == pin } ->
                            error = "Pin $pin is already used by another YL-69 sensor"
                        type == "TTP223" && actuators.any { it.type == "TTP223" && it.pin == pin } ->
                            error = "Pin $pin is already used by another TTP223 sensor"
                        type == "PIR" && actuators.any { it.type == "PIR" && it.pin == pin } ->
                            error = "Pin $pin is already used by another PIR sensor"
                        type == "LM393" && actuators.any { it.type == "LM393" && it.pin == pin } ->
                            error = "Pin $pin is already used by another LM393 sensor"
                        else -> {
                            onAdd(name, pin ?: 0, type, trigPin, echoPin, if (type == "Servo") 0 else null)
                            onDismiss()
                        }
                    }
                },
                enabled = name.isNotEmpty(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}