#include <Arduino.h>
#include <FastLED.h>
#include <patterns.h>

#define LED_PIN     0
#define NUM_LEDS    10

CRGB colors[NUM_LEDS];
CRGB leds[NUM_LEDS]; // change to better name later specify that these are the values that are to be shown

void setup() {

  FastLED.addLeds<WS2812, LED_PIN, GRB>(leds, NUM_LEDS);
  for (int i = 0; i < NUM_LEDS; i++) {
    colors[i] = CRGB(0, 0, 255);
  }
  FastLED.show();
}

void loop() {
  
  //pulse(colors, leds, NUM_LEDS, 0.1f, 255, 25);
  chaser(colors, leds, NUM_LEDS, 1, 1, 255, 50);

  FastLED.show();
  delay(10);

  // # Chaser
  // This function should be independent of the colors used preferably
  // input should be pointer to some color array
  // Inputs should contain speed, time?, maxBrigthness, minBrightness, ...
  // Bool for static or revolving colors?
  // input:  CRGB*, float speed, float currentTime, int maxBrigthness, int minBrightness
  // output: void


  // # SpinColor
  // Function that can spin the color array by fractional indecies thorugh linear interpolation
  // input: CRGB*, float indexStep
  // output: void
  
  //for   (int i = 0; i < NUM_LEDS; i++) {
  //  leds[i] = CRGB(foo(), 0, 0);
  //}
  //FastLED.show();
  //delay(50);


}
