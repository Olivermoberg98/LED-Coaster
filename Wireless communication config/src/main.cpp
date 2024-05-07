#include <Arduino.h>

const int HM10_RX = 16;  // Define RX pin for HM-10 module (connect to ESP32 TX pin)
const int HM10_TX = 17;  // Define TX pin for HM-10 module (connect to ESP32 RX pin)


void setup() {
  Serial.begin(9600); // Initialize serial communication with Arduino IDE
  Serial2.begin(9600, SERIAL_8N1, HM10_RX, HM10_TX); // Initialize hardware serial communication with HM-10 module

  delay(1000);  // Wait for module to initialize

  Serial.println("Sending AT commands to HM-10 module...");
  Serial2.println("AT"); // Send AT command to check if module is responsive
  delay(500); // Wait for response

  if (Serial2.available()) {
    Serial.println("HM-10 module responded!");
    while (Serial2.available()) {
      Serial.write(Serial2.read()); // Print response from HM-10 module
    }
  } else {
    Serial.println("No response from HM-10 module. Check wiring and try again.");
  }

  // Configure HM-10 module
  configureHM10();
}

void configureHM10() {
  // Send AT commands to configure HM-10 module
  Serial2.println("AT+NAMEMyDevice"); // Set device name
  delay(500);
  Serial2.println("AT+BAUD9600");    // Set baud rate to 9600
  delay(500);
  Serial2.println("AT+ROLE0");       // Set module as peripheral
  delay(500);
  Serial2.println("AT+RESET");        // Reset module to apply changes
  delay(1000);
}