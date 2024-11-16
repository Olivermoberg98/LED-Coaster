#include <Arduino.h>
#include <FastLED.h>
#include <patterns.h>
#include <BLEHandler.h>

CRGB led_output_inner[NUM_LEDS_INNER]; 
CRGB led_output_outer[NUM_LEDS_OUTER]; 

PatternType inner_pattern = CHASER;
PatternType outer_pattern = CHASER;

std::string coasterID = "001";
BLEHandler blehandler(coasterID);

void setup() {
  // Setup for the LEDs
  FastLED.addLeds<WS2812, LED_PIN_INNER, GRB>(led_output_inner, NUM_LEDS_INNER);
  FastLED.addLeds<WS2812, LED_PIN_OUTER, GRB>(led_output_outer, NUM_LEDS_OUTER);

 // Setup for the LED colors
  updateLEDColors(0, NUM_LEDS_INNER);
  updateLEDColors(1, NUM_LEDS_OUTER);

  // Setup Bluetooth
  blehandler.begin();
}

void loop() {
  if (blehandler.innerChecked) {
    runPattern(inner_pattern,colors_inner,led_output_inner,NUM_LEDS_INNER);
  } else {
    clearRing(led_output_inner, NUM_LEDS_INNER); 
  }

  if (blehandler.outerChecked) {
    runPattern(outer_pattern,colors_outer,led_output_outer,NUM_LEDS_OUTER);
  } else {
    clearRing(led_output_outer, NUM_LEDS_OUTER);
  }

  if (blehandler.package2Received) {
    inner_pattern = stringToPatternType(blehandler.received_pattern);
    outer_pattern = stringToPatternType(blehandler.received_pattern);

    updateLEDColors(0,NUM_LEDS_INNER,blehandler.received_colors);
    updateLEDColors(1,NUM_LEDS_INNER,blehandler.received_colors);
    
    // Reset the package flag
    blehandler.package2Received = false;
  }

  FastLED.show();
  delay(10);
}
