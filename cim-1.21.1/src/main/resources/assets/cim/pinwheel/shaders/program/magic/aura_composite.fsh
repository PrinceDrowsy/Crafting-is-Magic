// Magic aura, last stage (docs/magic-aura.md, the visual profile): magic is colored light, in three
// layers - the held thing itself washed with a quarter of its caster's color, a saturated rim hugging
// its outline (a touch deeper at its outer edge), and a soft, paler haze fading out beyond. Widths
// are in blocks, so the aura keeps its size - and its wave - however near or far the thing is, and
// the edge waves smoothly, a slow swell rolling along the outline like Celestia's mane.

uniform sampler2D DiffuseSampler0;
uniform sampler2D ValidSampler;
uniform sampler2D NearestSampler;
// The screen's own depth (this stage reads the main framebuffer) - for hiding droplets behind the world.
uniform sampler2D DiffuseDepthSampler;

uniform float NearPlane;
uniform float FarPlane;
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
// Which way, and how far (in rim widths, on screen), each slot's glow is drawn out: a horn's flame rising
// off its own shape, plus the trail behind anything moving.
uniform vec2 AuraStretch0;
uniform vec2 AuraStretch1;
uniform vec2 AuraStretch2;
uniform vec2 AuraStretch3;
uniform vec2 AuraStretch4;
uniform vec2 AuraStretch5;
uniform vec2 AuraStretch6;
uniform vec2 AuraStretch7;

in vec2 texCoord;

out vec4 fragColor;

const float TINT = 0.25;
/** Waves per block along the edge. */
const float WAVE_SCALE = 2.5;
/** How many places back along an aura's stretch a pixel looks for it. */
const int FLAME_STEPS = 24;

int slotOf(vec4 visible) {
    return int(round(visible.r * 255.0)) - 1;
}

// Droplets (MagicAuraRenderer): each on screen in pixels (x, y), its radius in pixels (z) and its
// distance from the camera in blocks (w), and its aura's slot (-1 for none).
const int DROPS = 12;
uniform vec4 Drop0;
uniform vec4 Drop1;
uniform vec4 Drop2;
uniform vec4 Drop3;
uniform vec4 Drop4;
uniform vec4 Drop5;
uniform vec4 Drop6;
uniform vec4 Drop7;
uniform vec4 Drop8;
uniform vec4 Drop9;
uniform vec4 Drop10;
uniform vec4 Drop11;
uniform float DropSlot0;
uniform float DropSlot1;
uniform float DropSlot2;
uniform float DropSlot3;
uniform float DropSlot4;
uniform float DropSlot5;
uniform float DropSlot6;
uniform float DropSlot7;
uniform float DropSlot8;
uniform float DropSlot9;
uniform float DropSlot10;
uniform float DropSlot11;

vec4 dropOf(int i) {
    if (i == 0) return Drop0;
    if (i == 1) return Drop1;
    if (i == 2) return Drop2;
    if (i == 3) return Drop3;
    if (i == 4) return Drop4;
    if (i == 5) return Drop5;
    if (i == 6) return Drop6;
    if (i == 7) return Drop7;
    if (i == 8) return Drop8;
    if (i == 9) return Drop9;
    if (i == 10) return Drop10;
    return Drop11;
}

float dropSlotOf(int i) {
    if (i == 0) return DropSlot0;
    if (i == 1) return DropSlot1;
    if (i == 2) return DropSlot2;
    if (i == 3) return DropSlot3;
    if (i == 4) return DropSlot4;
    if (i == 5) return DropSlot5;
    if (i == 6) return DropSlot6;
    if (i == 7) return DropSlot7;
    if (i == 8) return DropSlot8;
    if (i == 9) return DropSlot9;
    if (i == 10) return DropSlot10;
    return DropSlot11;
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

vec2 stretchOf(int slot) {
    if (slot == 0) return AuraStretch0;
    if (slot == 1) return AuraStretch1;
    if (slot == 2) return AuraStretch2;
    if (slot == 3) return AuraStretch3;
    if (slot == 4) return AuraStretch4;
    if (slot == 5) return AuraStretch5;
    if (slot == 6) return AuraStretch6;
    return AuraStretch7;
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

float linearDepth(float depth) {
    float z = depth * 2.0 - 1.0;
    return 2.0 * NearPlane * FarPlane / (FarPlane + NearPlane - z * (FarPlane - NearPlane));
}

/** A smooth union of two distances (negative inside) - where they meet, a neck joins them, as a blob of a lava lamp's does. */
float smoothUnion(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

void main() {
    vec4 scene = texture(DiffuseSampler0, texCoord);
    ivec2 here = ivec2(gl_FragCoord.xy);
    float world = linearDepth(texture(DiffuseDepthSampler, texCoord).r);

    // Droplets: round blobs of an aura's color, each joined smoothly onto any glow of its own aura it
    // touches - blown out of it like a bubble, then pinching off. Distances in pixels, negative inside.
    float drop = 1.0e9;
    float dropRadius = 0.0;
    float dropDepth = 0.0;
    int dropSlot = -1;
    for (int i = 0; i < DROPS; i++) {
        float slotHere = dropSlotOf(i);
        vec4 d = dropOf(i);
        // Unused, or behind the world here.
        if (slotHere < 0.0 || d.z <= 0.0 || d.w > world + 0.05) {
            continue;
        }
        float sd = length(gl_FragCoord.xy - d.xy) - d.z;
        if (dropSlot == int(slotHere)) {
            drop = smoothUnion(drop, sd, d.z * 0.8);
        } else if (sd < drop) {
            drop = sd;
            dropSlot = int(slotHere);
        } else {
            continue;
        }
        dropRadius = max(dropRadius, d.z);
        dropDepth = d.w;
    }

    vec4 inside = texelFetch(ValidSampler, here, 0);
    if (inside.a > 0.0) {
        int slot = slotOf(inside);
        // A droplet in front of the held thing covers it.
        if (dropSlot >= 0 && drop < 0.0 && dropDepth < inside.a) {
            fragColor = vec4(mix(scene.rgb, colorOf(dropSlot), 0.88 * fadeOf(dropSlot) * (1.0 - smoothstep(-1.5, 0.0, drop))), scene.a);
            return;
        }
        // Marked in green: the aura's own body (a horn swing's crescent), solid like a droplet.
        float wash = inside.g > 0.5 ? 0.88 : TINT;
        fragColor = vec4(mix(scene.rgb, colorOf(slot), wash * fadeOf(slot)), scene.a);
        return;
    }

    // The exact nearest visible held-thing pixel, found by the jump flood (as an offset from here).
    vec4 nearest = texelFetch(NearestSampler, here, 0);
    vec4 source = nearest.a > 0.0 ? texelFetch(ValidSampler, here + ivec2(round(nearest.xy)), 0) : vec4(0.0);
    if (source.a <= 0.0) {
        if (dropSlot < 0) {
            fragColor = scene;
            return;
        }
        // Only a droplet here, drifted clear of its aura: a solid blob with a soft haze.
        float strength = fadeOf(dropSlot);
        float haze = min(HazeWidth * PixelsPerBlock / max(dropDepth, 0.05) * widthOf(dropSlot), dropRadius);
        float bodyAlpha = 0.88 * (1.0 - smoothstep(-1.5, 1.5, drop));
        float hazeFade = clamp(1.0 - drop / max(haze, 0.5), 0.0, 1.0);
        float alpha = max(bodyAlpha, 0.5 * hazeFade * hazeFade) * strength;
        vec3 color = colorOf(dropSlot);
        vec3 glow = mix(mix(color, vec3(1.0), 0.3), color, bodyAlpha / max(max(bodyAlpha, 0.5 * hazeFade * hazeFade), 0.001));
        fragColor = vec4(mix(scene.rgb, glow, alpha), scene.a);
        return;
    }
    float distance = length(nearest.xy);

    int slot = slotOf(source);
    float strength = fadeOf(slot);
    float pixels = max(PixelsPerBlock / max(source.a, 0.05) * widthOf(slot), 0.5);
    // Two slow swells drifting different ways, measured in blocks so they keep their size with distance.
    vec2 blocks = gl_FragCoord.xy / pixels * WAVE_SCALE;
    float time = AuraTime * WaveSpeed;
    float wave = (noise(blocks + vec2(time, time * 0.6)) + noise(blocks * 1.7 - vec2(time * 0.8, time * 1.1))) * 0.5 - 0.5;
    // The glow is drawn out along its slot's stretch (on screen, in rim widths): a horn's flame rising off
    // it, narrowing like a candle's, and the trail behind anything moving. Each pixel looks back along the
    // stretch for the aura, counting what it looked past at a discount - so the shape smears that way by
    // the stretch's length and not at all to the sides. Judged by distance alone, never by direction to
    // the nearest pixel (which flips between a tilted tip's corners and tore the flame's top into spikes).
    vec2 stretch = stretchOf(slot);
    float stretchLength = length(stretch);
    if (stretchLength > 0.01) {
        vec2 back = -stretch / stretchLength;
        float discount = 1.0 / (1.0 + stretchLength);
        float reach = (RimWidth + HazeWidth) * pixels * 1.7 * (1.0 + stretchLength);
        ivec2 size = textureSize(ValidSampler, 0);
        for (int i = 1; i <= FLAME_STEPS; i++) {
            float s = reach * float(i) / float(FLAME_STEPS);
            ivec2 below = here + ivec2(round(back * s));
            if (below.x < 0 || below.y < 0 || below.x >= size.x || below.y >= size.y) {
                break;
            }
            vec4 under = texelFetch(ValidSampler, below, 0);
            float d = 0.0;
            if (under.a <= 0.0) {
                vec4 near = texelFetch(NearestSampler, below, 0);
                if (near.a <= 0.0) {
                    continue;
                }
                under = texelFetch(ValidSampler, below + ivec2(round(near.xy)), 0);
                d = length(near.xy);
            }
            if (under.a > 0.0 && slotOf(under) == slot) {
                distance = min(distance, d + s * discount);
            }
        }
    }
    float rim = RimWidth * pixels * (1.0 + wave * 0.7);
    float haze = HazeWidth * pixels * (1.0 + wave * 0.5);
    // How far outside the rim's outer edge this is (negative within it).
    float edge = distance - rim;
    if (dropSlot == slot) {
        // A droplet of this aura swells out of its rim, the two outlines flowing into one.
        edge = smoothUnion(edge, drop, dropRadius * 0.8);
    } else if (dropSlot >= 0 && drop < edge) {
        // Another aura's droplet, nearer: drawn as its own.
        slot = dropSlot;
        strength = fadeOf(slot);
        edge = drop;
        haze = min(haze, dropRadius);
    }
    vec3 color = colorOf(slot);

    // Soft edges, so the outline is smooth at any size.
    float rimAlpha = 0.88 * (1.0 - smoothstep(-1.5, 1.5, edge));
    float hazeFade = clamp(1.0 - edge / haze, 0.0, 1.0);
    float hazeAlpha = 0.5 * hazeFade * hazeFade;
    float alpha = max(rimAlpha, hazeAlpha) * strength;
    if (alpha <= 0.001) {
        fragColor = scene;
        return;
    }
    vec3 rimColor = mix(color, color * 0.82, clamp((edge + rim) / rim, 0.0, 1.0));
    vec3 hazeColor = mix(color, vec3(1.0), 0.3);
    vec3 glow = mix(hazeColor, rimColor, rimAlpha / max(rimAlpha, hazeAlpha));
    fragColor = vec4(mix(scene.rgb, glow, alpha), scene.a);
}