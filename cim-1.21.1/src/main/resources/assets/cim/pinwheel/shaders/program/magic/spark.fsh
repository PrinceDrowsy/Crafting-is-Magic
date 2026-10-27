// The shape of a spark (docs/magic-aura.md), picked by where its UVs sit:
//   u in 0-1: a streak - a teardrop stretched along its flight, head at u = 1, the caster's color with a
//             white core (a release's shards, the wisps round a held spell's horn);
//   u in 2-3: a four-point star - white, the color only at its faint edges (glints, sparkle dust, the
//             release flash);
//   u from 4: a teleport's bubble with its spikes - see the bubble's branch below.
// The vertex color is the caster's color, its alpha how far the spark has faded.

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// A bubble's quad reaches this many of its radii out from its middle - well past its longest spikes, bends
// and all (about 3.8), so none is cut off at the quad's edge.
const float BUBBLE_REACH = 4.5;
// The star bursting out of a bubble at the end of its life, and its spikes - one through each of its
// points: long ones through the four main points, shorter through the four between.
const int SPIKES = 8;
// How far through a bubble's life the star starts to burst (MagicSparks: (SWELL + HOLD) / its whole life).
const float BURST_START = 0.69;
// Points along a swaying spike's line (a crooked one needs only four: base, two corners, tip).
const int BENDS = 6;
const int CURLS = 3;
// A bubble's animation frames before they repeat (MagicSparks' BUBBLE_FRAMES).
const float FRAMES = 16.0;
// How finely a bubble's progress through its life is told (MagicSparks' BUBBLE_LIFE_STEPS).
const float LIFE_STEPS = 63.0;

float hash(float n) {
    return fract(sin(n) * 43758.5453);
}

float smoothUnion(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

// Which way a bubble's star's i-th point (and so its spike) runs, from its seed: roughly an eighth of a
// turn apart, but unevenly, as a burst is.
float pointAngle(float seed, int i) {
    return hash(seed * 0.37 + 0.5) * 6.2831853 + float(i) * 0.78539816 + (hash(seed * 0.53 + float(i) * 2.17) - 0.5) * 0.45;
}

// How far outside a bubble's star (negative inside), at p, the star grown to {@code burst} (0-1) of its
// size: eight thin, straight needles from one point, each tapering to its tip - the four main ones
// reaching the bubble's wall when it's full grown, the four between shorter, each its own length.
float star(vec2 p, float seed, float burst) {
    float needles = 1.0e3;
    for (int i = 0; i < SPIKES; i++) {
        float angle = pointAngle(seed, i);
        float span = (i % 2 == 0 ? 1.0 : 0.45 + hash(seed * 0.71 + float(i) * 3.3) * 0.35) * burst;
        vec2 tip = vec2(cos(angle), sin(angle)) * span;
        float t = clamp(dot(p, tip) / max(dot(tip, tip), 1.0e-6), 0.0, 1.0);
        needles = min(needles, length(p - tip * t) - 0.035 * (1.0 - t));
    }
    return needles;
}

// How far outside the bubble and its spikes (negative inside), in bubble radii, at p (the bubble's radius
// is 1). The spikes are the star's points bursting through the wall ({@code burst}, 0-1): each a tongue
// thick at its base and sharp at its tip, joined onto the bubble's outline by a smooth neck - one shape, so
// they grow out of its edge rather than float over it. They boil, as drawn animation does: each frame
// redraws every spike with its own bends and bulges - some near straight, some kinked - round the same
// direction and length.
float bubbleShape(vec2 p, float seed, float frame, float burst) {
    float r = length(p);
    float spikes = 1.0e3;
    // They break out as the star's points reach the wall.
    float reach = smoothstep(0.45, 1.0, burst);
    for (int i = 0; i < SPIKES; i++) {
        float n = seed * 0.731 + float(i) * 1.37;
        float f = n + frame * 0.917;
        bool major = i % 2 == 0;
        float span = (major ? 1.2 + hash(n + 1.0) * 0.5 : 0.45 + hash(n + 1.0) * 0.3) * reach * (0.85 + hash(f + 5.0) * 0.3);
        if (span < 0.02 || r > 1.6 + span) {
            continue;
        }
        float angle = pointAngle(seed, i);
        vec2 outward = vec2(cos(angle), sin(angle));
        vec2 across = vec2(-outward.y, outward.x);
        float base = major ? 0.11 : 0.09;
        // Its line this frame, none off at its base: for some, sharp crooks - straight runs between two
        // random corners; for the rest, a smooth sway or two. A chain of segments along that line, each
        // tapering - thick at the base, a point at the tip, bulging unevenly between.
        bool crooked = hash(f + 12.0) < 0.5;
        float k1 = 0.2 + hash(f + 6.0) * 0.3;
        float k2 = 0.55 + hash(f + 7.0) * 0.3;
        vec2 lastPoint = vec2(0.0);
        float lastWidth = 0.0;
        for (int j = 0; j <= BENDS; j++) {
            float a = crooked ? (j == 0 ? 0.0 : j == 1 ? k1 : j == 2 ? k2 : 1.0) : float(j) / float(BENDS);
            float side;
            if (crooked) {
                float o = j == 0 ? 0.0 : j == 1 ? hash(f + 8.0) - 0.5 : j == 2 ? hash(f + 9.0) - 0.5 : (hash(f + 10.0) - 0.5) * 0.5;
                side = o * span * 0.35;
            } else {
                side = ((hash(f + 6.0) - 0.5) * sin(a * 3.14159265 * (1.0 + hash(f + 7.0)) + hash(f + 8.0) * 6.28)
                        + (hash(f + 9.0) - 0.5) * 0.5 * sin(a * 9.42477 + hash(f + 10.0) * 6.28)) * span * 0.35 * a;
            }
            // Its base sits a little inside the bubble, so the neck joins it.
            vec2 point = outward * (0.85 + a * (span + 0.15)) + across * side;
            float width = base * pow(1.0 - a, 0.8) * (1.0 + 0.35 * sin(a * 7.0 + hash(f + 11.0) * 6.28));
            if (j > 0) {
                vec2 pa = p - lastPoint;
                vec2 ba = point - lastPoint;
                float t = clamp(dot(pa, ba) / max(dot(ba, ba), 1.0e-6), 0.0, 1.0);
                spikes = min(spikes, length(pa - ba * t) - mix(lastWidth, width, t));
            }
            lastPoint = point;
            lastWidth = width;
        }
    }
    return smoothUnion(r - 1.0, spikes, 0.12);
}

// How much of a curl stroke is at p (0-1): CURLS strokes, each starting at the bubble's edge, running
// along it, then spiralling inward to a tight curl, thinning as it goes - soft across, as the edge is.
// Each its own size, and winding its own amount. As the star bursts ({@code burst}, 0-1) they're pressed
// out against the bubble's wall, as if laid on a sphere seen edge-on: measured along its surface, so near
// the edge, where it turns away, they're squashed flat against it - thinner outward, as long as ever
// along it.
float curls(vec2 p, float seed, float burst) {
    float d = min(length(p), 0.9999);
    p *= mix(1.0, asin(d) / max(d, 1.0e-4) / 1.5707963, smoothstep(0.0, 0.8, burst));
    float lit = 0.0;
    for (int i = 0; i < CURLS; i++) {
        float n = seed * 0.613 + float(i) * 1.91;
        // Spread evenly round the edge, and kept small enough to stay in its own share of the bubble.
        float start = (float(i) + hash(n) * 0.25) / float(CURLS) * 6.2831853;
        vec2 edge = vec2(cos(start), sin(start)) * 0.92;
        // It winds round a middle somewhere in from where it starts, closing in on it.
        vec2 middle = edge * (0.56 + hash(n + 2.0) * 0.2);
        float size = length(edge - middle);
        float turns = 1.1 + hash(n + 3.0) * 0.5;
        float spin = hash(n + 1.0) < 0.5 ? 1.0 : -1.0;
        vec2 v = p - middle;
        float around = mod(spin * (atan(v.y, v.x) - start), 6.2831853);
        for (int k = 0; k < 2; k++) {
            float turn = (around + 6.2831853 * float(k)) / (6.2831853 * turns);
            if (turn > 1.0) {
                break;
            }
            float off = abs(length(v) - size * (1.0 - 0.85 * turn));
            float width = 0.15 * size * (1.0 - 0.6 * turn) * (1.0 - smoothstep(0.85, 1.0, turn));
            lit = max(lit, 1.0 - smoothstep(width * 0.3, width, off));
        }
    }
    return lit;
}

void main() {
    vec3 rgb;
    float alpha;
    float fade = vertexColor.a;
    if (texCoord0.x < 1.5) {
        vec2 p = texCoord0 * 2.0 - 1.0;
        // Thinner toward the tail.
        float taper = mix(0.3, 1.0, smoothstep(-1.0, 0.6, p.x));
        float d = length(vec2(p.x, p.y / taper));
        float body = 1.0 - smoothstep(0.55, 1.0, d);
        float core = 1.0 - smoothstep(0.0, 0.5, d);
        rgb = mix(vertexColor.rgb, vec3(1.0), core);
        alpha = body;
    } else if (texCoord0.x > 3.5) {
        // A bubble: the color throughout, see-through, lighter and more solid round its whole outline
        // (spikes too) and along the strokes curling round inside. At the end of its life a star in the
        // same color swells fast in its middle - the force of the pop - its points bursting out through
        // the wall as the spikes. v's whole part carries its seed and its animation frame (seed * FRAMES +
        // frame); u's, how far through its life it is (in LIFE_STEPS, packed as 4 + 2 * stage).
        float whole = floor(texCoord0.y);
        float seed = floor(whole / FRAMES);
        float frame = whole - seed * FRAMES;
        float stage = floor((texCoord0.x - 4.0) / 2.0);
        float burst = clamp((stage / LIFE_STEPS - BURST_START) / (1.0 - BURST_START), 0.0, 1.0);
        burst = 1.0 - (1.0 - burst) * (1.0 - burst);
        vec2 p = (vec2(texCoord0.x - 4.0 - 2.0 * stage, texCoord0.y - whole) * 2.0 - 1.0) * BUBBLE_REACH;
        float sd = bubbleShape(p, seed, frame, burst);
        // A pixel's size, from p itself: the shape jumps past each spike's tip, and its own fwidth there
        // smeared the tips into blunt blobs.
        float aa = max(fwidth(length(p)), 1.0e-4);
        // Faded out toward the quad's own edge, so nothing ever ends there in a hard cut.
        float inside = (1.0 - smoothstep(-aa, aa, sd)) * (1.0 - smoothstep(BUBBLE_REACH - 0.6, BUBBLE_REACH, max(abs(p.x), abs(p.y))));
        float starSd = star(p, seed, burst);
        float inStar = burst > 0.0 ? 1.0 - smoothstep(-aa, aa, starSd) : 0.0;
        // The star is the lowest layer, inside the orb: plain color, solid but see-through. The edge and
        // the curls - folds of the same skin - lie over it.
        float edge = max(smoothstep(-0.14, -0.02, sd), curls(p, seed, burst));
        float under = max(0.3 + 0.2 * smoothstep(-0.5, -0.1, sd), inStar * 0.5);
        rgb = mix(vertexColor.rgb, vec3(1.0), edge * 0.55);
        alpha = min(under + edge * 0.35, 0.95) * inside;
    } else {
        vec2 p = vec2(texCoord0.x - 2.0, texCoord0.y) * 2.0 - 1.0;
        vec2 a = abs(p);
        float across = 1.0 - smoothstep(0.0, 0.1 * (1.0 - a.x) + 0.02, a.y);
        float down = 1.0 - smoothstep(0.0, 0.1 * (1.0 - a.y) + 0.02, a.x);
        float rays = max(max(1.0 - a.x, 0.0) * across, max(1.0 - a.y, 0.0) * down);
        float glow = exp(-dot(p, p) * 10.0);
        float lit = clamp(max(rays, glow), 0.0, 1.0);
        rgb = mix(vertexColor.rgb, vec3(1.0), clamp(lit * 1.4, 0.0, 1.0));
        alpha = lit;
    }
    alpha *= fade;
    if (alpha < 0.004) {
        discard;
    }
    fragColor = vec4(rgb, alpha);
}
