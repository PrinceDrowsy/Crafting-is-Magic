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

void main() {
    vec2 centered = texCoord0 - vec2(0.5);
    float dist = length(centered) * 2.0;
    float angle = atan(centered.y, centered.x);
    float angleNorm = angle / (2.0 * PI) + 0.5;

    float coreRadius = 0.12;
    float ringInner = 0.42;
    float ringOuter = 0.62;

    float core = 1.0 - smoothstep(coreRadius * 0.5, coreRadius, dist);

    // Sample vanilla's own fire animation (32 frames, 16x16 each, stacked vertically) as the shape
    // and motion for the ring's flame-licked inner edge, instead of a hand-rolled noise function -
    // the angle wraps around the ring several times for multiple flame tongues, and the band
    // position (inner to outer) maps onto each frame's own tongue axis, tips pointing inward
    // toward the core.
    float bandT = clamp((dist - ringInner) / (ringOuter - ringInner), 0.0, 1.0);
    float frameCount = 32.0;
    float frame = floor(mod(GameTime * 4000.0 / 0.3, frameCount));
    float flameU = fract(angleNorm * 14.0);
    float flameV = (frame + bandT) / frameCount;
    float flameMask = texture(Sampler0, vec2(flameU, flameV)).a;

    // Much wider falloff on the outer edge than the inner one - a soft, blurred glow fading into
    // the sky rather than a crisp cutoff, matching the softer look of the earlier version.
    float band = flameMask * (1.0 - smoothstep(ringOuter - 0.03, ringOuter + 0.4, dist))
            * smoothstep(ringInner - 0.12, ringInner, dist);

    float rays = 0.6 + 0.4 * cos(angle * 12.0);
    core *= mix(0.85, 1.0, rays);

    // Full rainbow arranged around the circumference (a color wheel), not by radius.
    vec3 rainbow = hsv2rgb(vec3(angleNorm, 1.0, 1.0));

    // White flame-lick bleeding into the ring from its inner edge, fading to full color further out.
    float innerGlow = 1.0 - smoothstep(ringInner, ringInner + 0.14, dist);
    vec3 ringColor = mix(rainbow, vec3(1.0), innerGlow * 0.7) * band;

    vec3 starburstColor = vec3(1.0) * core;

    // Slight transparency rather than fully solid/opaque.
    float alpha = max(band, core) * 0.9;
    if (alpha < 0.02) {
        discard;
    }

    // Self-illuminating - a magical effect this bright shouldn't dim with the world's ambient
    // light level the way a normal lit particle would, so lightmapColor is deliberately unused.
    vec3 finalColor = ringColor + starburstColor;
    vec4 color = vec4(finalColor, alpha) * vertexColor * ColorModulator;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
