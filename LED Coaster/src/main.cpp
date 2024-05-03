#include <Arduino.h>
#include <FastLED.h>

#define LED_PIN 5  // Pin connected to the LED
#define NUM_LEDS 1 // Number of LEDs

CRGB leds[NUM_LEDS]; // LED array

int red = 0;
int green = 0;
int blue = 255;

void setup() {
  FastLED.addLeds<WS2812B, LED_PIN, GRB>(leds, NUM_LEDS);
}

void loop() {
  // Define the rgb values

  red += 10 % 255;
  green += 0 % 255;
  blue += 5 % 255;
  leds[0] = CRGB(red, green, blue);
  

 FastLED.show();

 delay(1000);
}

