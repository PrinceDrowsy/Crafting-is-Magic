#include veil:fog

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float GameTime;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;
in vec4 lightmapColor;
flat in vec2 hullPoints[10];

out vec4 fragColor;

void main() {
    // p spans roughly [-1, 1] across the billboard, matching the convention the hull points below
    // are normalized in (see LevitationAuraEffectHandler#updateViewSilhouette) - a hull vertex at
    // its own farthest point from centroid sits at magnitude 1/SILHOUETTE_MARGIN here, not 1.0,
    // since the quad's own world-space size was inflated by that same margin to leave the rim room
    // to extend outward without clipping at the quad's physical edge.
    vec2 p = (texCoord0 - vec2(0.5)) * 2.0;

    // vertexColor.r carries shapeFill through from GroupAuraCodeModule (there's no real color
    // animation on this particle, so the channel is otherwise unused - see that class's doc): how
    // much of the group's bounding box its blocks actually fill. vertexColor.a carries the number
    // of meaningful entries in hullPoints (the rest repeat the last valid point).
    float shapeFill = clamp(vertexColor.r, 0.35, 1.0);
    int hullCount = int(clamp(round(vertexColor.a), 3.0, 10.0));

    // Signed area (shoelace formula) of the hull tells us whether its vertices wind clockwise or
    // counter-clockwise in this coordinate space - needed to know which way each edge's outward
    // normal points, without relying on (and hoping to have gotten right) a fixed assumption about
    // which winding direction the Java-side convex hull algorithm happens to produce.
    float signedArea = 0.0;
    for (int i = 0; i < 10; i++) {
        if (i >= hullCount) {
            break;
        }
        vec2 a = hullPoints[i];
        vec2 b = hullPoints[(i + 1) % hullCount];
        signedArea += a.x * b.y - b.x * a.y;
    }
    float windingSign = signedArea < 0.0 ? -1.0 : 1.0;

    // Distance from p to the hull's own boundary: negative inside, 0 exactly on an edge, positive
    // outside. For a CONVEX polygon, the max of each edge's own outward half-plane distance is
    // exact whenever the closest boundary point lies within that edge's segment, and a close
    // approximation elsewhere - more than adequate for a soft, wavy glow rather than a crisp
    // geometric outline. A SMOOTH max (rather than a hard max) rounds adjacent edges into a curve
    // at each hull vertex instead of a sharp angular kink, matching the wave's organic look - but
    // it has to be the log-sum-exp form (simultaneous over ALL edges), not a left-to-right pairwise
    // fold: a sequential fold's result depends on iteration order and visibly misbehaved (a hard,
    // pixelated notch) on hulls with very unequal edge lengths, e.g. a long selection's long sides
    // vs. its short end-cap edge.
    float planeDists[10];
    float hardMax = -1.0e6;
    for (int i = 0; i < 10; i++) {
        if (i >= hullCount) {
            planeDists[i] = -1.0e6;
            continue;
        }
        vec2 a = hullPoints[i];
        vec2 b = hullPoints[(i + 1) % hullCount];
        vec2 edge = b - a;
        float edgeLen = length(edge);
        float planeDist = edgeLen < 1.0e-4 ? -1.0e6 : dot(p - a, windingSign * vec2(edge.y, -edge.x) / edgeLen);
        planeDists[i] = planeDist;
        hardMax = max(hardMax, planeDist);
    }
    // The rounding radius trades off against how much it eats into sharp/acute hull vertices (e.g.
    // the pointed tip of a long, thin selection viewed near end-on) - log-sum-exp's smooth max is
    // always >= the true hard max, so a large radius here visibly rounds sharp tips OFF rather
    // than just smoothing the transition between two nearly-parallel edges, leaving part of the
    // actual block silhouette poking out past where the fill/rim think the edge is.
    float cornerSharpness = 1.0 / 0.02;
    float sumExp = 0.0;
    for (int i = 0; i < 10; i++) {
        if (i >= hullCount) {
            break;
        }
        sumExp += exp(cornerSharpness * (planeDists[i] - hardMax));
    }
    float hullDist = hardMax + log(sumExp) / cornerSharpness;

    float angle = atan(p.y, p.x);

    // Organic, undulating rim - several sine harmonics at different angular frequencies and
    // animation speeds/directions combine so the wobble doesn't read as one simple pulsing
    // circle, closer to the show's lumpy, faintly flame/cloud-like aura boundary. Sparser groups
    // get a stronger wobble. Amplitudes are kept well under rimOffset itself (see below) so this
    // reads as a gentle ripple around the true edge rather than the rim swinging out into long
    // loops and back - the available headroom out to the quad's own physical border is fixed
    // (see LevitationAuraEffectHandler#SILHOUETTE_MARGIN), so an overly large wave both looks
    // wrong and risks clipping.
    float waveAmp = mix(1.5, 1.0, shapeFill);
    float t = GameTime * 4000.0;
    float wave = sin(angle * 5.0 + t / 2.5) * 0.022 * waveAmp
            + sin(angle * 8.0 - t / 1.7) * 0.014 * waveAmp
            + sin(angle * 3.0 + t / 3.5) * 0.016 * waveAmp;

    // The rim sits a little OUTSIDE the true silhouette edge (hullDist == 0) - loosely conforming
    // to the actual shape rather than tracing it exactly - and "wave" wobbles that offset over
    // time and around the shape for the organic look.
    float rimOffset = 0.16 + wave;
    float rimThickness = 0.07;

    float rim = 1.0 - smoothstep(0.0, rimThickness, abs(hullDist - rimOffset));

    // Solid fill - ramps up to (near-)full opacity a short, fixed distance BEFORE the rim's own
    // position (rimOffset), not before the true silhouette edge (hullDist == 0). Stopping the fill
    // at hullDist == 0 left a visible gap of near-zero alpha between the filled interior and the
    // rim sitting further out at rimOffset - the loose halo margin between the true edge and the
    // rim was neither "interior" nor "rim" and so stayed essentially transparent, showing empty
    // background (or, for thin slivers of the silhouette, the raw block texture) through the hole.
    // Ramping against rimOffset instead means the fill and the rim always meet with no gap between
    // them, regardless of how far out the rim currently sits.
    float fillRamp = 0.08;
    float interiorGlow = clamp((rimOffset - hullDist) / fillRamp, 0.0, 1.0) * 0.85;

    float alpha = clamp(interiorGlow + rim, 0.0, 1.0);
    alpha *= 1.0 - smoothstep(rimOffset, rimOffset + rimThickness * 1.5, hullDist);
    if (alpha < 0.02) {
        discard;
    }

    vec3 interiorColor = vec3(0.85, 0.25, 0.75);
    vec3 rimColor = vec3(1.0, 0.85, 0.95);
    vec3 finalColor = mix(interiorColor, rimColor, clamp(rim * 1.4, 0.0, 1.0));

    // Self-illuminating - a magical glow shouldn't dim with the world's ambient light level, so
    // lightmapColor is deliberately unused (matches the rainbow ring's own approach).
    vec4 color = vec4(finalColor, alpha) * ColorModulator;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
