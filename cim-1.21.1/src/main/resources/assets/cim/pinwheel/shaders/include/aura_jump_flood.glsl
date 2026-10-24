// One jump-flooding pass for the magic aura (MagicAuraRenderer): every pixel looks at itself and its
// eight neighbours STEP pixels away, and keeps whichever held-thing pixel ("seed") any of them knows
// of that's nearest. Passes run from a big step down to 1, so after them every pixel knows the exact
// nearest visible held-thing pixel on screen, however far away - at full resolution, for any width
// of rim. In and out: the offset from this pixel to its seed (rg - small whole numbers, exact in any
// float format), alpha 1 if there is one. Define STEP first.

uniform sampler2D DiffuseSampler0;

out vec4 fragColor;

void main() {
    ivec2 size = textureSize(DiffuseSampler0, 0);
    ivec2 here = ivec2(gl_FragCoord.xy);
    vec2 best = vec2(0.0);
    float bestDistance = 1.0e9;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            ivec2 at = here + ivec2(x, y) * STEP;
            if (at.x < 0 || at.y < 0 || at.x >= size.x || at.y >= size.y) {
                continue;
            }
            vec4 known = texelFetch(DiffuseSampler0, at, 0);
            if (known.a <= 0.0) {
                continue;
            }
            vec2 offset = vec2(at - here) + known.xy;
            float distance = length(offset);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = offset;
            }
        }
    }
    fragColor = bestDistance < 1.0e8 ? vec4(best, 0.0, 1.0) : vec4(0.0);
}
