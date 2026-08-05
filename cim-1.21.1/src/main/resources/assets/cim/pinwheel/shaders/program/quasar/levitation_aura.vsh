#include veil:fog
#veil:buffer veil:camera VeilCamera

layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 Normal;
layout(location = 2) in mat4 ParticleMat;
layout(location = 6) in float Scale;
layout(location = 7) in vec2 UV0Min;
layout(location = 8) in vec2 UV0Max;
layout(location = 9) in vec4 Color;
layout(location = 10) in ivec2 UV2;
layout(location = 11) in vec4 HullAB;
layout(location = 12) in vec4 HullCD;
layout(location = 13) in vec4 HullEF;
layout(location = 14) in vec4 HullGH;
layout(location = 15) in vec4 HullIJ;

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out float vertexDistance;
out vec2 texCoord0;
out vec4 vertexColor;
out vec4 lightmapColor;
flat out vec2 hullPoints[10];

void main() {
    vec4 WorldPosition = ModelViewMat * ParticleMat * vec4(Position * Scale, 1.0);
    gl_Position = ProjMat * WorldPosition;
    vertexDistance = length(WorldPosition.xyz);

    vec2 uvs[4];
    uvs[0] = UV0Min;
    uvs[1] = vec2(UV0Min.x, UV0Max.y);
    uvs[2] = UV0Max;
    uvs[3] = vec2(UV0Max.x, UV0Min.y);
    texCoord0 = uvs[gl_VertexID % 4];

    vertexColor = Color;
    lightmapColor = texelFetch(Sampler2, UV2 / 16, 0);

    // The group's projected screen-space silhouette (its 2D convex hull, normalized by its own
    // bounding radius - see LevitationAuraEffectHandler#updateViewSilhouette), packed 2 points per
    // vec4 attribute since there's no single slot big enough for all of them. Capped at 10 points -
    // see LevitationAuraEffectHandler#MAX_HULL_POINTS's doc on why (going higher blows past this
    // hardware's vertex attribute limit and fails the whole shader link, not just a visual glitch).
    // Passed through as "flat" (no interpolation) since it's the same per-particle data at every
    // vertex of this quad.
    hullPoints[0] = HullAB.xy;
    hullPoints[1] = HullAB.zw;
    hullPoints[2] = HullCD.xy;
    hullPoints[3] = HullCD.zw;
    hullPoints[4] = HullEF.xy;
    hullPoints[5] = HullEF.zw;
    hullPoints[6] = HullGH.xy;
    hullPoints[7] = HullGH.zw;
    hullPoints[8] = HullIJ.xy;
    hullPoints[9] = HullIJ.zw;
}
