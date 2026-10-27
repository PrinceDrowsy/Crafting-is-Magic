// The shape of a spark (docs/magic-aura.md), picked by where its UVs sit:
//   u in 0-1: a streak - a teardrop stretched along its flight, head at u = 1, the caster's color with a
//             white core (a release's shards, the wisps round a held spell's horn);
//   u in 2-3: a four-point star - white, the color only at its faint edges (glints, sparkle dust, the
//             release flash).
// The vertex color is the caster's color, its alpha how far the spark has faded.

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec3 rgb;
    float alpha;
    if (texCoord0.x < 1.5) {
        vec2 p = texCoord0 * 2.0 - 1.0;
        // Thinner toward the tail.
        float taper = mix(0.3, 1.0, smoothstep(-1.0, 0.6, p.x));
        float d = length(vec2(p.x, p.y / taper));
        float body = 1.0 - smoothstep(0.55, 1.0, d);
        float core = 1.0 - smoothstep(0.0, 0.5, d);
        rgb = mix(vertexColor.rgb, vec3(1.0), core);
        alpha = body;
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
    alpha *= vertexColor.a;
    if (alpha < 0.004) {
        discard;
    }
    fragColor = vec4(rgb, alpha);
}
