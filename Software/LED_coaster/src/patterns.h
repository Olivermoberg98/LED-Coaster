#ifndef PATTERN_H
#define PATTERN_H

#include <FastLED.h>

enum PatternType {
    PATTERN_FIXED,
    PATTERN_CHASER,
    PATTERN_PULSE
};

void fixed(CRGB* ledsIn,CRGB* ledsOut, int numberOfLeds);
void pulse(CRGB* ledsIn,CRGB* ledsOut, int numberOfLeds, float pulseFrequency, float maxBrigthness, float minBrigthness);
void chaser(CRGB* ledsIn, CRGB* ledsOut, int numberOfLeds, int brightSpots,float roationalFrequency, float maxBrigthness, float minBrigthness);
void clearRing(CRGB* leds, int numLeds);
void runPattern(PatternType pattern, CRGB* ledsIn, CRGB* ledsOut, int numberOfLeds);

#endif