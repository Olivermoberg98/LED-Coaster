#include <Arduino.h>
#include <FastLED.h>
#include <patterns.h>
#include <BLEHandler.h>

#define LED_PIN_INNER     0
#define LED_PIN_OUTER     1
#define NUM_LEDS_INNER    10
#define NUM_LEDS_OUTER    20

CRGB colors_inner[NUM_LEDS_INNER];
CRGB colors_outer[NUM_LEDS_OUTER];
CRGB led_output_inner[NUM_LEDS_INNER]; 
CRGB led_output_outer[NUM_LEDS_OUTER]; 

PatternType inner_pattern = PATTERN_CHASER;
PatternType outer_pattern = PATTERN_CHASER;

BLEHandler blehandler;

void setup() {
  // Setup for the inner LEDs
  FastLED.addLeds<WS2812, LED_PIN_INNER, GRB>(led_output_inner, NUM_LEDS_INNER);
  for (int i = 0; i < NUM_LEDS_INNER; i++) {
    colors_inner[i] = CRGB(0, 0, 255);
  }
  // Setup for the outer LEDs
  FastLED.addLeds<WS2812, LED_PIN_INNER, GRB>(led_output_inner, NUM_LEDS_OUTER);
  for (int i = 0; i < NUM_LEDS_OUTER; i++) {
    colors_inner[i] = CRGB(0, 255, 0);
  }

  FastLED.show();

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
    runPattern(outer_pattern,colors_inner,led_output_inner,NUM_LEDS_INNER);
  } else {
    clearRing(led_output_outer, NUM_LEDS_OUTER);
  }

  // Reset the package received flag
  blehandler.package1Received = false;

  FastLED.show();
  delay(10);
}
