#include <Arduino.h>
#include <FastLED.h>
#include <patterns.h>

#define LED_PIN_TOP       0
#define NUM_LEDS_TOP      10

#define LED_PIN_BOTTOM    1
#define NUM_LEDS_BOTTOM   20

CRGB colors_top[NUM_LEDS_TOP];    // The colors that are to be shown
CRGB leds_top[NUM_LEDS_TOP];      // The values that are sent to the LEDS after any potetnial brightness scaling is done

CRGB colors_bottom[NUM_LEDS_BOTTOM]; // The colors that are to be shown
CRGB leds_bottom[NUM_LEDS_BOTTOM];   // The values that are sent to the LEDS after any potetnial brightness scaling is done

void setup() {
  Serial.begin(9600);
  FastLED.addLeds<WS2812, LED_PIN_TOP, GRB>(leds_top, NUM_LEDS_TOP);
  FastLED.addLeds<WS2812, LED_PIN_BOTTOM, GRB>(leds_bottom, NUM_LEDS_BOTTOM);

  for (int i = 0; i < NUM_LEDS_TOP; i++) {
    colors_top[i] = CRGB(0, 0, 255);
    leds_top[i] = CRGB(0,0,10);
  }
  for (int i = 0; i < NUM_LEDS_BOTTOM; i++) {
    colors_bottom[i] = CRGB(100, 0, 100);
    leds_bottom[i] = CRGB(125,0,0);
  }
  Serial.println("The serial port works");
  FastLED.show();
}

void loop() {
  
  //pulse(colors, leds, NUM_LEDS_TOP, 0.1f, 255, 25);
  

  chaser(colors_bottom, leds_bottom, NUM_LEDS_BOTTOM, 1, 1, 255, 50);
  //pulse(colors_bottom, leds_bottom, NUM_LEDS_BOTTOM, 1, 255, 50);

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
