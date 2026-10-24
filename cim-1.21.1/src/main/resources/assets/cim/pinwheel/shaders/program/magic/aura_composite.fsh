// Magic aura, last stage (docs/magic-aura.md, the visual profile): magic is colored light, in three
// layers - the held thing itself washed with a quarter of its caster's color, a saturated rim hugging
// its outline (a touch deeper at its outer edge), and a soft, paler haze fading out beyond. Widths
// are in blocks, so the aura keeps its size - and its wave - however near or far the thing is, and
// the edge waves smoothly, a slow swell rolling along the outline like Celestia's mane.

uniform sampler2D DiffuseSampler0;
uniform sampler2D ValidSampler;
uniform sampler2D NearestSampler;

uniform float AuraTime;
uniform float PixelsPerBlock;
uniform float RimWidth;
uniform float HazeWidth;
uniform float WaveSpeed;
// Each aura's slot (the mask's red, 1-8) picks its caster's color and how far it has faded in.
uniform vec3 AuraColor0;
uniform vec3 AuraColor1;
uniform vec3 AuraColor2;
uniform vec3 AuraColor3;
uniform vec3 AuraColor4;
uniform vec3 AuraColor5;
uniform vec3 AuraColor6;
uniform vec3 AuraColor7;
uniform float AuraFade0;
uniform float AuraFade1;
uniform float AuraFade2;
uniform float AuraFade3;
uniform float AuraFade4;
uniform float AuraFade5;
uniform float AuraFade6;
uniform float AuraFade7;
// How wide each slot's rim and haze are against the usual (a horn gathering in from wide, a grab growing out).
uniform float AuraWidth0;
uniform float AuraWidth1;
uniform float AuraWidth2;
uniform float AuraWidth3;
uniform float AuraWidth4;
uniform float AuraWidth5;
uniform float AuraWidth6;
uniform float AuraWidth7;
// How much each slot's glow swells on its upper side: a horn's flame rising off its own shape (0 for held things).
uniform float AuraRise0;
uniform float AuraRise1;
uniform float AuraRise2;
uniform float AuraRise3;
uniform float AuraRise4;
uniform float AuraRise5;
uniform float AuraRise6;
uniform float AuraRise7;

in vec2 texCoord;

out vec4 fragColor;

const float TINT = 0.25;
/** Waves per block along the edge. */
const float WAVE_SCALE = 2.5;

int slotOf(vec4 visible) {
    return int(round(visible.r * 255.0)) - 1;
}

vec3 colorOf(int slot) {
    if (slot == 0) return AuraColor0;
    if (slot == 1) return AuraColor1;
    if (slot == 2) return AuraColor2;
    if (slot == 3) return AuraColor3;
    if (slot == 4) return AuraColor4;
    if (slot == 5) return AuraColor5;
    if (slot == 6) return AuraColor6;
    return AuraColor7;
}

float fadeOf(int slot) {
    if (slot == 0) return AuraFade0;
    if (slot == 1) return AuraFade1;
    if (slot == 2) return AuraFade2;
    if (slot == 3) return AuraFade3;
    if (slot == 4) return AuraFade4;
    if (slot == 5) return AuraFade5;
    if (slot == 6) return AuraFade6;
    return AuraFade7;
}

float widthOf(int slot) {
    if (slot == 0) return AuraWidth0;
    if (slot == 1) return AuraWidth1;
    if (slot == 2) return AuraWidth2;
    if (slot == 3) return AuraWidth3;
    if (slot == 4) return AuraWidth4;
    if (slot == 5) return AuraWidth5;
    if (slot == 6) return AuraWidth6;
    return AuraWidth7;
}

float riseOf(int slot) {
    if (slot == 0) return AuraRise0;
    if (slot == 1) return AuraRise1;
    if (slot == 2) return AuraRise2;
    if (slot == 3) return AuraRise3;
    if (slot == 4) return AuraRise4;
    if (slot == 5) return AuraRise5;
    if (slot == 6) return AuraRise6;
    return AuraRise7;
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0)), f.x), f.y);
}

void main() {
    vec4 scene = texture(DiffuseSampler0, texCoord);
    ivec2 here = ivec2(gl_FragCoord.xy);

    vec4 inside = texelFetch(ValidSampler, here, 0);
    if (inside.a > 0.0) {
        int slot = slotOf(inside);
        fragColor = vec4(mix(scene.rgb, colorOf(slot), TINT * fadeOf(slot)), scene.a);
        return;
    }

    // The exact nearest visible held-thing pixel, found by the jump flood (as an offset from here).
    vec4 nearest = texelFetch(NearestSampler, here, 0);
    if (nearest.a <= 0.0) {
        fragColor = scene;
        return;
    }
    ivec2 seed = here + ivec2(round(nearest.xy));
    vec4 source = texelFetch(ValidSampler, seed, 0);
    if (source.a <= 0.0) {
        fragColor = scene;
        return;
    }
    float distance = length(nearest.xy);

    int slot = slotOf(source);
    float strength = fadeOf(slot);
    float pixels = PixelsPerBlock / max(source.a, 0.05) * widthOf(slot);
    // Two slow swells drifting different ways, measured in blocks so they keep their size with distance.
    vec2 blocks = gl_FragCoord.xy / pixels * WAVE_SCALE;
    float time = AuraTime * WaveSpeed;
    float wave = (noise(blocks + vec2(time, time * 0.6)) + noise(blocks * 1.7 - vec2(time * 0.8, time * 1.1))) * 0.5 - 0.5;
    // A flame rises: the glow swells on the side above its source (nearest.xy points from here to it),
    // most of all straight up and falling away fast to the sides, so it narrows like a candle's.
    float above = max(-nearest.y / max(distance, 1.0), 0.0);
    float swell = 1.0 + riseOf(slot) * pow(above, 8.0);
    float rim = RimWidth * pixels * swell * (1.0 + wave * 0.7);
    float haze = HazeWidth * pixels * swell * (1.0 + wave * 0.5);
    vec3 color = colorOf(slot);

    // Soft edges, so the outline is smooth at any size.
    float rimAlpha = 0.88 * (1.0 - smoothstep(rim - 1.5, rim + 1.5, distance));
    float hazeFade = clamp(1.0 - (distance - rim) / haze, 0.0, 1.0);
    float hazeAlpha = 0.5 * hazeFade * hazeFade;
    float alpha = max(rimAlpha, hazeAlpha) * strength;
    if (alpha <= 0.001) {
        fragColor = scene;
        return;
    }
    vec3 rimColor = mix(color, color * 0.82, clamp(distance / rim, 0.0, 1.0));
    vec3 hazeColor = mix(color, vec3(1.0), 0.3);
    vec3 glow = mix(hazeColor, rimColor, rimAlpha / max(rimAlpha, hazeAlpha));
    fragColor = vec4(mix(scene.rgb, glow, alpha), scene.a);
}