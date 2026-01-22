#include "patterns.h"
#include "Arduino.h"
#include <FastLED.h>

// Define the color arrays
CRGB colors_inner[NUM_LEDS_INNER];
CRGB colors_outer[NUM_LEDS_OUTER];

bool inner_needs_update = true;
bool outer_needs_update = true;

PatternType stringToPatternType(const std::string& pattern) {
    if (pattern == "FIXED") {
        return FIXED;
    } else if (pattern == "CHASER") {
        return CHASER;
    } else if (pattern == "PULSE") {
        return PULSE;
    } else if (pattern == "RAINBOW") {
        return RAINBOW;
    } else {
        return FIXED;  // Default to FIXED if the pattern is unrecognized
    }
}

void updateLEDColors(int ring, int NUM_LEDS, const int colors[3]) {
    if (ring == 0) {
        for (int i = 0; i < NUM_LEDS; i++) {
            colors_inner[i] = CRGB(colors[0], colors[1], colors[2]);
        }
    } else if (ring == 1) {
        for (int i = 0; i < NUM_LEDS; i++) {
            colors_outer[i] = CRGB(colors[0], colors[1], colors[2]);
        }
    }
    FastLED.show();
}

void updateLEDColors(int ring, int NUM_LEDS) {
    const int defaultColors[3] = {0, 0, 255}; // Default color: Blue
    updateLEDColors(ring, NUM_LEDS, defaultColors);
}

void runPattern(PatternType pattern, CRGB* ledsIn, CRGB* ledsOut, int numberOfLeds) {
    switch (pattern) {
        case FIXED:
            fixed(ledsIn, ledsOut, numberOfLeds);
            break;
        case CHASER:
            chaser(ledsIn, ledsOut, numberOfLeds, 5, 0.8f, 255, 0); 
            break;
        case PULSE:
            pulse(ledsIn, ledsOut, numberOfLeds, 0.1f, 255, 25); 
            break;
        case RAINBOW:
            rainbow(ledsOut, numberOfLeds);
            break;
        default:
            fixed(ledsIn, ledsOut, numberOfLeds);
            break;
    }
}

void fixed(CRGB* ledsIn, CRGB* ledsOut, int numberOfLeds) {
    for (int i = 0; i < numberOfLeds; i++) {
        // Scale the original colors to the new brightness
        ledsOut[i].r = constrain(ledsIn[i].r, 0, 255);
        ledsOut[i].g = constrain(ledsIn[i].g, 0, 255);
        ledsOut[i].b = constrain(ledsIn[i].b, 0, 255);
    }
    FastLED.show();
}

void pulse(CRGB* ledsIn, CRGB* ledsOut, int numberOfLeds, float pulseFrequency, float maxBrigthness, float minBrigthness) {
    // Calculate the pulse value (between 0 and 1)
    float pulse = (sin((2 * PI * millis() * pulseFrequency) / 1000.0f) + 1.0) / 2.0;

    // Scale brightness between 0 and 1
    float scaledMaxBrightness = maxBrigthness / 255.0f;
    float scaledMinBrightness = minBrigthness / 255.0f;
    float scaledBrightness = pulse * (scaledMaxBrightness - scaledMinBrightness) + scaledMinBrightness;

    for (int i = 0; i < numberOfLeds; i++) {
        // Scale the original colors to the new brightness
        ledsOut[i].r = constrain(ledsIn[i].r * scaledBrightness, 0, 255);
        ledsOut[i].g = constrain(ledsIn[i].g * scaledBrightness, 0, 255);
        ledsOut[i].b = constrain(ledsIn[i].b * scaledBrightness, 0, 255);
    }
    FastLED.show();
}

void chaser(CRGB* ledsIn, CRGB* ledsOut, int numberOfLeds, int brightSpots, float rotationalFrequency, float maxBrightness, float minBrightness) {
    static float rotation = 0; // Persistent rotation position

    // Increment rotation based on frequency
    rotation += (rotationalFrequency * 360.0f * 0.01f); // Assuming ~10ms loop delay
    if (rotation >= 360.0f) rotation -= 360.0f; // Wrap around to avoid overflow

    int spotWidth = numberOfLeds / brightSpots; // Number of LEDs per bright spot

    for (int i = 0; i < numberOfLeds; i++) {
        // Calculate LED's position in the rotation
        int position = (i + (int)(rotation * numberOfLeds / 360.0f)) % numberOfLeds;

        // Determine if this LED is within a bright spot
        bool isBrightSpot = position < spotWidth;

        // Set brightness based on position
        uint8_t brightness = isBrightSpot ? maxBrightness : minBrightness;

        // Scale input colors by brightness and set output
        ledsOut[i].r = (ledsIn[i].r * brightness) / 255;
        ledsOut[i].g = (ledsIn[i].g * brightness) / 255;
        ledsOut[i].b = (ledsIn[i].b * brightness) / 255;
    }
    FastLED.show();
}

void rainbow(CRGB* ledsOut, int numberOfLeds) {
    // Set all LEDs to the same color
    CRGB color = CHSV(sharedHue, 255, 200);
    for (int i = 0; i < numberOfLeds; i++) {
        ledsOut[i] = color;
    }
    FastLED.show();

    sharedCounter++;
    if (sharedCounter % 50 == 0) { 
        sharedHue++;
        if (sharedHue == 255) sharedHue = 0; // Wrap around hue
    }
}

void clearRing(CRGB* leds, int numLeds) {
    for (int i = 0; i < numLeds; i++) {
        leds[i] = CRGB::Black;
    }
    FastLED.show();
}

void onDisconnectPattern(CRGB* ledsInner, int numLedsInner, CRGB* ledsOuter, int numLedsOuter) {
    // Set both rings to red first
    for (int i = 0; i < numLedsInner; i++) {
        ledsInner[i] = CRGB::Red;
    }
    for (int i = 0; i < numLedsOuter; i++) {
        ledsOuter[i] = CRGB::Red;
    }
    FastLED.show();
    
    // Gradual fade-out effect for both rings simultaneously
    for (int brightness = 255; brightness >= 0; brightness -= 5) {
        for (int i = 0; i < numLedsInner; i++) {
            ledsInner[i].fadeToBlackBy(5);
        }
        for (int i = 0; i < numLedsOuter; i++) {
            ledsOuter[i].fadeToBlackBy(5);
        }
        FastLED.show();
        delay(30); // Adjust for fade-out speed
    }
}

void onConnectPattern(CRGB* ledsInner, int numLedsInner, CRGB* ledsOuter, int numLedsOuter) {
    // Use the larger ring size for the ripple effect
    int maxLeds = max(numLedsInner, numLedsOuter);
    
    for (int ripple = 0; ripple < maxLeds; ripple++) {
        // Update inner ring
        for (int i = 0; i < numLedsInner; i++) {
            if (i == ripple % numLedsInner) {
                ledsInner[i] = CRGB::Green;
            } else {
                ledsInner[i].fadeToBlackBy(50);
            }
        }
        
        // Update outer ring
        for (int i = 0; i < numLedsOuter; i++) {
            if (i == ripple % numLedsOuter) {
                ledsOuter[i] = CRGB::Green;
            } else {
                ledsOuter[i].fadeToBlackBy(50);
            }
        }
        
        FastLED.show();
        delay(50); // Adjust for ripple speed
    }
}