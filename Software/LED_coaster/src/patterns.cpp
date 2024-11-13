#include "patterns.h"
#include "Arduino.h"
#include <FastLED.h>

/// @brief Pulse a pattern of LEDs.
/// This function takes a pattern of LEDs, and "pulses" it by scaling the brightness
/// of each LED up and down over time. The brightness is scaled by a sine wave
/// with the given frequency, and then further scaled by the minimum and maximum
/// brightness values.
/// @param ledsIn CRGB array containing the colors of the LEDs to pulse.
/// @param ledsOut CRGB array containing the destination array of LEDs which are pulsing.
/// @param numberOfLeds The number of LEDs in the pattern.
/// @param pulseFrequency The frequency of the pulse in [Hz].
/// @param maxBrigthness The maximum brightness of the pulse [0-255].
/// @param minBrigthness The minimum brightness of the pulse [0-255].
/// @brief Pulse a pattern of LEDs.
/// This function takes a pattern of LEDs, and "pulses" it by scaling the brightness
/// of each LED up and down over time. The brightness is scaled by a sine wave
/// with the given frequency, and then further scaled by the minimum and maximum
/// brightness values.
/// @param ledsIn CRGB array containing the colors of the LEDs to pulse.
/// @param ledsOut CRGB array containing the destination array of LEDs which are pulsing.
/// @param numberOfLeds The number of LEDs in the pattern.
/// @param pulseFrequency The frequency of the pulse in [Hz].
/// @param maxBrigthness The maximum brightness of the pulse [0-255].
/// @param minBrigthness The minimum brightness of the pulse [0-255].

void runPattern(PatternType pattern, CRGB* ledsIn, CRGB* ledsOut, int numberOfLeds) {
    switch (pattern) {
        case PATTERN_FIXED:
            fixed(ledsIn, ledsOut, numberOfLeds);
            break;
        case PATTERN_CHASER:
            chaser(ledsIn, ledsOut, numberOfLeds, 1, 1, 255, 50); 
            break;
        case PATTERN_PULSE:
            pulse(ledsIn, ledsOut, numberOfLeds, 0.1f, 255, 25); 
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
}

void chaser(CRGB* ledsIn, CRGB* ledsOut, int numberOfLeds, int brightSpots,float roationalFrequency, float maxBrigthness, float minBrigthness) {
    int numBrightSpots = brightSpots;
    float rotation = (2 * PI * millis() * roationalFrequency) / 1000.0f;
    float brightSpotWidth = 1.0f / numBrightSpots;

    for (int i = 0; i < numberOfLeds; i++) {
        // Calculate the index of the current LED in the cycle
        float index = (i + rotation) / numberOfLeds;

        // Check if the current LED is in one of the bright spots
        bool inBrightSpot = false;
        for (int j = 0; j < numBrightSpots; j++) {
            float brightSpotStart = j * brightSpotWidth;
            float brightSpotEnd = (j + 1) * brightSpotWidth;
            if (index >= brightSpotStart && index <= brightSpotEnd) {
                inBrightSpot = true;
                break;
            }
        }

        // If the current LED is in a bright spot, set its brightness to max
        // Otherwise, set its brightness to min
        float brightness = inBrightSpot ? maxBrigthness : minBrigthness;

        // Scale the original colors to the new brightness
        ledsOut[i].r = constrain(ledsIn[i].r * brightness / 255.0f, 0, 255);
        ledsOut[i].g = constrain(ledsIn[i].g * brightness / 255.0f, 0, 255);
        ledsOut[i].b = constrain(ledsIn[i].b * brightness / 255.0f, 0, 255);
    }
}

void clearRing(CRGB* leds, int numLeds) {
    for (int i = 0; i < numLeds; i++) {
        leds[i] = CRGB::Black;
    }
}