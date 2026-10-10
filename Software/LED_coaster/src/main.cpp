#include <Arduino.h>
#include <FastLED.h>
#include <patterns.h>
#include <BLEHandler.h>

CRGB led_output_inner[NUM_LEDS_INNER]; 
CRGB led_output_outer[NUM_LEDS_OUTER]; 

PatternType inner_pattern = FIXED;
PatternType outer_pattern = FIXED;

std::string coasterID = "05";
BLEHandler blehandler(coasterID);

// Battery sense: +BATT through a 470k/470k divider, so the pin sees half the cell.
const int BAT_SENSE_PIN = 4;
// MCP73871 status outputs, all open-drain (High-Z reads HIGH with the pull-ups).
const int STAT1_PIN = 5;
const int STAT2_PIN = 6;
const int PG_PIN = 10;
const unsigned long BATTERY_REPORT_INTERVAL_MS = 2000;
unsigned long lastBatteryReport = 0;
const unsigned long STATUS_PUSH_INTERVAL_MS = 30000;
unsigned long lastStatusPush = 0;
// Below this the low-battery flag is set even before the charger raises LBO (3.1 V).
const uint32_t LOW_BATTERY_WARN_MV = 3500;

// Charger state as carried in Package 3; values are part of the BLE contract.
enum ChargerState : uint8_t {
  CHARGER_UNKNOWN = 0,
  CHARGER_ON_BATTERY = 1,
  CHARGER_CHARGING = 2,
  CHARGER_COMPLETE = 3,
  CHARGER_LOW_BATTERY = 4,
  CHARGER_TEMP_FAULT = 5,
  CHARGER_NO_BATTERY = 6
};
// Reported state only changes once a new reading has held for two reads in a row.
ChargerState reportedChargerState = CHARGER_UNKNOWN;
ChargerState pendingChargerState = CHARGER_UNKNOWN;

// Averages several calibrated readings; the LED rail is noisy while patterns run.
uint32_t readBatteryMillivolts() {
  const int samples = 16;
  uint32_t sum = 0;
  for (int i = 0; i < samples; i++) {
    sum += analogReadMilliVolts(BAT_SENSE_PIN);
  }
  return (sum / samples) * 2;
}

// Li-Po resting-voltage curve, linearly interpolated between points.
int batteryPercent(uint32_t mv) {
  static const uint16_t curve[][2] = {
    {4200, 100}, {4150, 95}, {4110, 90}, {4080, 85}, {4020, 80}, {3980, 75},
    {3950, 70},  {3910, 65}, {3870, 60}, {3850, 55}, {3840, 50}, {3820, 45},
    {3800, 40},  {3790, 35}, {3770, 30}, {3750, 25}, {3730, 20}, {3710, 15},
    {3690, 10},  {3610, 5},  {3270, 0}
  };
  const int points = sizeof(curve) / sizeof(curve[0]);
  if (mv >= curve[0][0]) return 100;
  if (mv <= curve[points - 1][0]) return 0;
  for (int i = 1; i < points; i++) {
    if (mv >= curve[i][0]) {
      uint32_t vHigh = curve[i - 1][0], vLow = curve[i][0];
      uint32_t pHigh = curve[i - 1][1], pLow = curve[i][1];
      return pLow + (mv - vLow) * (pHigh - pLow) / (vHigh - vLow);
    }
  }
  return 0;
}

// Decodes the status outputs per MCP73871 datasheet Table 5-1.
ChargerState chargerState() {
  bool pg = digitalRead(PG_PIN);
  bool stat1 = digitalRead(STAT1_PIN);
  bool stat2 = digitalRead(STAT2_PIN);
  if (!pg && !stat1 && stat2)  return CHARGER_CHARGING;
  if (!pg && stat1 && !stat2)  return CHARGER_COMPLETE;
  if (!pg && !stat1 && !stat2) return CHARGER_TEMP_FAULT;
  if (!pg && stat1 && stat2)   return CHARGER_NO_BATTERY;
  if (pg && !stat1 && stat2)   return CHARGER_LOW_BATTERY;
  if (pg && stat1 && stat2)    return CHARGER_ON_BATTERY;
  return CHARGER_UNKNOWN;
}

const char* chargerStateName(ChargerState state) {
  switch (state) {
    case CHARGER_ON_BATTERY:  return "on battery";
    case CHARGER_CHARGING:    return "charging";
    case CHARGER_COMPLETE:    return "charge complete";
    case CHARGER_LOW_BATTERY: return "low battery";
    case CHARGER_TEMP_FAULT:  return "temperature fault";
    case CHARGER_NO_BATTERY:  return "no battery present";
    default:                  return "unknown";
  }
}

// Reads the battery and charger, logs them, and loads Package 3 into the status
// characteristics. Returns true when the reported charger state changed.
bool updateBatteryStatus() {
  uint8_t pins = digitalRead(PG_PIN) | (digitalRead(STAT1_PIN) << 1) | (digitalRead(STAT2_PIN) << 2);
#ifdef BATTERY_STATUS_FAKE
  // Steps through every charger state and a falling voltage, one step per 5 s.
  uint32_t step = millis() / 5000;
  ChargerState state = (ChargerState)(step % 7);
  uint32_t mv = 4200 - (step % 20) * 50;
  bool usbPower = state == CHARGER_CHARGING || state == CHARGER_COMPLETE ||
                  state == CHARGER_TEMP_FAULT || state == CHARGER_NO_BATTERY;
#else
  ChargerState state = chargerState();
  uint32_t mv = readBatteryMillivolts();
  bool usbPower = !(pins & 0x01);
#endif

  bool changed = false;
  if (state != reportedChargerState && state == pendingChargerState) {
    reportedChargerState = state;
    changed = true;
  }
  pendingChargerState = state;

  uint8_t percent = batteryPercent(mv);
  uint8_t flags = 0;
  if (reportedChargerState == CHARGER_LOW_BATTERY || mv < LOW_BATTERY_WARN_MV) flags |= 0x01;
  if (usbPower) flags |= 0x02;
#ifdef BATTERY_STATUS_FAKE
  flags |= 0x04;
#endif
  uint32_t uptime = millis() / 1000;

  // Package 3: see "The BLE contract" in CLAUDE.md
  uint8_t package3[13] = {
    0x03, 0x01,
    (uint8_t)(mv & 0xFF), (uint8_t)(mv >> 8),
    percent, reportedChargerState, flags, pins,
    (uint8_t)(uptime & 0xFF), (uint8_t)(uptime >> 8), (uint8_t)(uptime >> 16), (uint8_t)(uptime >> 24),
    0
  };
  for (int i = 0; i < 12; i++) package3[12] += package3[i];
  blehandler.setStatus(package3, sizeof(package3), percent);

  Serial.printf("Battery: %u mV, %d%%, charger: %s (PG=%d STAT1=%d STAT2=%d)\n",
                mv, percent, chargerStateName(state),
                pins & 0x01, (pins >> 1) & 0x01, (pins >> 2) & 0x01);
  return changed;
}

void setup() {
  // Setup for the LEDs
  FastLED.addLeds<WS2812, LED_PIN_INNER, GRB>(led_output_inner, NUM_LEDS_INNER);
  FastLED.addLeds<WS2812, LED_PIN_OUTER, GRB>(led_output_outer, NUM_LEDS_OUTER);

  // All 30 TZ-5050S2RGB at full white draw 1.08 A (12 mA per channel), and the
  // ~265 mOhm from the cell through the charger's BAT->SYS path and Q1 would
  // then sag +SYS below the LDO's dropout on a half-empty battery, resetting the
  // ESP32. 900 mA holds +SYS at 3.40 V down to a 3.6 V cell.
  FastLED.setMaxPowerInVoltsAndMilliamps(5, 900);

  // Setup for the LED colors
  updateLEDColors(0, NUM_LEDS_INNER);
  updateLEDColors(1, NUM_LEDS_OUTER);

  Serial.begin(9600);

  analogSetPinAttenuation(BAT_SENSE_PIN, ADC_11db);
  pinMode(STAT1_PIN, INPUT_PULLUP);
  pinMode(STAT2_PIN, INPUT_PULLUP);
  pinMode(PG_PIN, INPUT_PULLUP);

  // Setup Bluetooth
  blehandler.begin();

  // Starts the charger state settled, so the first push is not "unknown"
  reportedChargerState = pendingChargerState = chargerState();
  updateBatteryStatus();
  lastBatteryReport = millis();
}

void loop() {
  // Update connection state machine
  blehandler.updateConnectionState();
  blehandler.updateAdvertising();

  bool pushStatus = false;
  if (millis() - lastBatteryReport >= BATTERY_REPORT_INTERVAL_MS) {
    lastBatteryReport = millis();
    pushStatus = updateBatteryStatus();
  }
  if (blehandler.statusSubscribePending) {
    blehandler.statusSubscribePending = false;
    pushStatus = true;
  }
  if (blehandler.isConnected() && millis() - lastStatusPush >= STATUS_PUSH_INTERVAL_MS) {
    pushStatus = true;
  }
  if (pushStatus) {
    blehandler.notifyStatus();
    lastStatusPush = millis();
  }

  // Only process patterns when fully connected
  if (blehandler.shouldProcessPatterns()) {
    // Inner ring
    if (blehandler.innerChecked && blehandler.deviceConnected) {
      if (inner_pattern == FIXED && !inner_needs_update) {
        delay(50);
      } else {
        runPattern(inner_pattern, colors_inner, led_output_inner, NUM_LEDS_INNER);
        if (inner_pattern == FIXED) inner_needs_update = false;
      }
    } else {
      clearRing(led_output_inner, NUM_LEDS_INNER);
      inner_needs_update = true;
    }

    // Outer ring
    if (blehandler.outerChecked && blehandler.deviceConnected) {
      if (outer_pattern == FIXED && !outer_needs_update) {
        delay(50);
      } else {
        runPattern(outer_pattern, colors_outer, led_output_outer, NUM_LEDS_OUTER);
        if (outer_pattern == FIXED) outer_needs_update = false;
      }
    } else {
      clearRing(led_output_outer, NUM_LEDS_OUTER);
      outer_needs_update = true;
    }

    // Handle incoming pattern and color data
    if (blehandler.package2Received) {
      inner_pattern = stringToPatternType(blehandler.received_pattern);
      outer_pattern = stringToPatternType(blehandler.received_pattern);

      if (inner_pattern != 3 || outer_pattern != 3) {
        updateLEDColors(0, NUM_LEDS_INNER, blehandler.received_colors);
        updateLEDColors(1, NUM_LEDS_OUTER, blehandler.received_colors);
      }
      
      inner_needs_update = true;
      outer_needs_update = true;
      
      blehandler.package2Received = false;
      Serial.println(inner_pattern);
    }
  }

  delay(10);
}