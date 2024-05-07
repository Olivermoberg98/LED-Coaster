#include <Arduino.h>
#include <FastLED.h>

#define LED_PIN 5   // Pin connected to the LED
#define NUM_LEDS 1  // Number of LEDs

CRGB leds[NUM_LEDS]; // LED array

// Define the list of modes
const char* modes[] = {"Static", "Breathing", "Chaser", "Rainbow"};
const int numModes = sizeof(modes) / sizeof(modes[0]);

modes = 

void setup() {
  FastLED.addLeds<WS2812B, LED_PIN, GRB>(leds, NUM_LEDS);
}

void loop() {
  // Example usage:
  // changeLEDMode("Static", {CRGB::Red}); // Set the mode to Static with Red color
  // changeLEDMode("Breathing", {CRGB::Blue}); // Set the mode to Breathing with Blue color
  // changeLEDMode("Chaser", {CRGB::Green}); // Set the mode to Chaser with Green color
  // changeLEDMode("Rainbow", {}); // Set the mode to Rainbow
  
  // Input that could come from a bluetooth or wifi module
  String mode = "Static"; 
  CRGB colorList[] = {CRGB::Red}; 
  
  try {
    changeLEDMode(mode, colorList); 
  } catch (const char* error) {
    Serial.println(error);
  }
}



void changeLEDMode(String mode, std::initializer_list<CRGB> colors) {
  if (mode == "Static") {
    staticMode(colors);
  } else if (mode == "Breathing") {
    breathingMode(colors);
  } else if (mode == "Chaser") {
    chaserMode(colors);
  } else if (mode == "Rainbow") {
    rainbowMode();
  }
}

void staticMode(std::initializer_list<CRGB> colors) {
  // Static mode: Set all LEDs to the same color
  if (colors.size() > 0) {
    fill_solid(leds, NUM_LEDS, *(colors.begin())); 
    FastLED.show();
  }
}

void breathingMode(std::initializer_list<CRGB> colors, int numColors) {
  // Breathing mode implementation
  // Gradually change brightness between two colors
  if (numColors == 2) {
    for (int i = 0; i < 255; i++) {
      leds[0] = colors[0];
      leds[0].fadeLightBy(i);
      FastLED.show();
      delay(10);
    }
    for (int i = 255; i > 0; i--) {
      leds[0] = colors[1];
      leds[0].fadeLightBy(i);
      FastLED.show();
      delay(10);
    }
  }
}
void chaserMode(std::initializer_list<CRGB> colors) {
  // Chaser mode: LEDs chase each other with a specified color
  if (colors.size() > 0) {
    CRGB color = *(colors.begin());
    for (int i = 0; i < NUM_LEDS; i++) {
      leds[i] = color;
      FastLED.show();
      delay(100);
      leds[i] = CRGB::Black;
    }
  }
}

void rainbowMode() {
  // Rainbow mode: Cycle through the colors of the rainbow
  for (int i = 0; i < 255; i++) {
    fill_rainbow(leds, NUM_LEDS, i, 7); // Fill with rainbow colors
    FastLED.show();
    delay(30);
  }
}
