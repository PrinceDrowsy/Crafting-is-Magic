#include veil:fog

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float GameTime;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;
in vec4 lightmapColor;

out vec4 fragColor;

#define PI 3.14159265

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

void main() {
    vec2 centered = texCoord0 - vec2(0.5);
    // Charge progress (0 at the start of the charge, 1 right as it completes) is carried through
    // the particle's own alpha channel - its lifetime is set to match the charge duration, so
    // vertexColor.a here is exactly agePercent.
    float chargeT = vertexColor.a;

    // The dome fades in first (chargeT 0 to fadeInEnd) while staying dome-shaped - only once
    // fully faded in does it start stretching into the tapering pointed cone.
    float fadeInEnd = 0.15;
    float fadeIn = smoothstep(0.0, fadeInEnd, chargeT);
    float stretchT = smoothstep(fadeInEnd, 1.0, chargeT);

    float domeRadius = 0.42;
    float coneLength = mix(0.5, 3.2, stretchT);

    float shape;
    if (centered.x < 0.0) {
        shape = length(centered / domeRadius);
    } else {
        float xn = centered.x / coneLength;
        float widthAtX = domeRadius * pow(clamp(1.0 - xn, 0.0, 1.0), 0.65);
        shape = max(length(vec2(0.0, centered.y)) / max(widthAtX, 0.01), xn);
    }

    float shapeAlpha = 1.0 - smoothstep(0.85, 1.05, shape);
    if (shapeAlpha < 0.02) {
        discard;
    }

    // Mostly white/pale fill with a thin rainbow fringe right at the shape's own edge.
    float edgeT = smoothstep(0.55, 1.0, shape);
    float angleNorm = atan(centered.y, centered.x) / (2.0 * PI) + 0.5;
    vec3 rainbowEdge = hsv2rgb(vec3(angleNorm, 1.0, 1.0));
    vec3 baseColor = mix(vec3(1.0), rainbowEdge, edgeT * 0.6);

    // Electric sparks crackling across the shape - fast-flickering random bright points.
    float sparkGrid = 14.0;
    vec2 sparkCell = floor(centered * sparkGrid) + floor(GameTime * 4000.0 * 25.0);
    float sparkChance = hash(sparkCell);
    float spark = step(0.965, sparkChance) * shapeAlpha * (0.4 + 0.6 * chargeT);
    vec3 sparkColor = vec3(1.0) * spark * 4.0;

    vec3 finalColor = baseColor * shapeAlpha + sparkColor;
    float alpha = clamp(shapeAlpha + spark, 0.0, 1.0) * 0.9 * fadeIn;

    vec4 color = vec4(finalColor, alpha) * ColorModulator;
    // Self-illuminating, same as the ring - doesn't dim with ambient light level.
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
