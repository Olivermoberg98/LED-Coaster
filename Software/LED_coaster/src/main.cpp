#include <Arduino.h>
#include <FastLED.h>
#include <patterns.h>
#include <BLEHandler.h>

CRGB led_output_inner[NUM_LEDS_INNER]; 
CRGB led_output_outer[NUM_LEDS_OUTER]; 

PatternType inner_pattern = FIXED;
PatternType outer_pattern = FIXED;

std::string coasterID = "001";
BLEHandler blehandler(coasterID);

void setup() {
  // Setup for the LEDs
  FastLED.addLeds<WS2812, LED_PIN_INNER, GRB>(led_output_inner, NUM_LEDS_INNER);
  FastLED.addLeds<WS2812, LED_PIN_OUTER, GRB>(led_output_outer, NUM_LEDS_OUTER);

  // Setup for the LED colors
  updateLEDColors(0, NUM_LEDS_INNER);
  updateLEDColors(1, NUM_LEDS_OUTER);

  Serial.begin(9600);

  // Setup Bluetooth
  blehandler.begin();
}

void loop() {
  if (blehandler.innerChecked && blehandler.deviceConnected) {
    runPattern(inner_pattern,colors_inner,led_output_inner,NUM_LEDS_INNER);
  } else if (blehandler.innerChecked) {
     //FastLED.show();
  } else {
    clearRing(led_output_inner, NUM_LEDS_INNER);
  }

  if (blehandler.outerChecked && blehandler.deviceConnected) {
    runPattern(outer_pattern,colors_outer,led_output_outer,NUM_LEDS_OUTER);
  } else if (blehandler.outerChecked) {
     //FastLED.show();
  } else {
    clearRing(led_output_outer, NUM_LEDS_OUTER);
  }

  if (blehandler.package2Received) {
    inner_pattern = stringToPatternType(blehandler.received_pattern);
    outer_pattern = stringToPatternType(blehandler.received_pattern);

    if (inner_pattern!=3 || outer_pattern != 3) {
      updateLEDColors(0,NUM_LEDS_INNER,blehandler.received_colors);
      updateLEDColors(1,NUM_LEDS_OUTER,blehandler.received_colors);
    }
    
    // Reset the package flag
    blehandler.package2Received = false;
    Serial.println(inner_pattern);
  }

  delay(10);
}
