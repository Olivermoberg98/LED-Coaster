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


/// @brief Create a chaser pattern with LEDs.
/// This function generates a chaser effect on a given set of LEDs, where bright spots
/// rotate around the LED strip. The brightness of each LED is modulated between a
/// minimum and maximum value, creating a rotating effect.
/// @param ledsIn CRGB array containing the colors of the LEDs to apply the chaser effect.
/// @param ledsOut CRGB array containing the destination array of LEDs with the chaser effect.
/// @param numberOfLeds The number of LEDs in the pattern.
/// @param brightSpots The number of bright spots in the chaser pattern.
/// @param roationalFrequency The rotational frequency of the chaser effect [Hz].
/// @param maxBrigthness The maximum brightness of the bright spots [0-255].
/// @param minBrigthness The minimum brightness for areas outside the bright spots [0-255].
void chaser(CRGB* ledsIn, CRGB* ledsOut, int numberOfLeds, int brightSpots,float roationalFrequency, float maxBrigthness, float minBrigthness) {
    int numBrightSpots = brightSpots;
    float rotation = (numberOfLeds * millis() * roationalFrequency) / 1000.0f;
    float brightness = 100;
    int width = 5;
    float brightIndex = ((int)(rotation) % numberOfLeds) + rotation - (int)rotation;


    for (int i = 0; i < numberOfLeds; i++) {
        ledsOut[i].r = 0;
        ledsOut[i].g = 0;
        ledsOut[i].b = 0;
    }

    for (int j = 0; j < brightSpots; j++) {

        brightIndex = (int)(brightIndex + j*(numberOfLeds/brightSpots)) % numberOfLeds;

        Serial.print(String(brightIndex) + " ");

        for (int i = 0; i < numberOfLeds; i++) {    
            brightness = 0;
            if(i == ceil((int)(brightIndex + width) % numberOfLeds)){
                brightness = 100 * (rotation - (int)rotation);
            }
            else if (i < ceil((int)(brightIndex + width) % numberOfLeds) && i > floor(brightIndex)){
                brightness = 100;
            }
            else if (i == floor(brightIndex)){
                brightness = 100 * (1 - (rotation - (int)rotation));
            }
            //selse {
            //s    brightness = 0;
            //s}
            
            if(ceil((int)(brightIndex + width) % numberOfLeds) < floor(brightIndex)){
                if(i < ceil((int)(brightIndex + width) % numberOfLeds) || i > floor(brightIndex)){
                    brightness = 100;
                }
            }
            if(brightness > 0){
                // Scale the original colors to the new brightness
                ledsOut[i].r = constrain(ledsIn[i].r * brightness / 255.0f, 0, 255);
                ledsOut[i].g = constrain(ledsIn[i].g * brightness / 255.0f, 0, 255);
                ledsOut[i].b = constrain(ledsIn[i].b * brightness / 255.0f, 0, 255);
            }
        }
    }
}
