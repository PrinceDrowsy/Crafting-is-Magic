// Magic aura, first stage (docs/magic-aura.md): keeps only the parts of the mask the camera can
// actually see - a held thing behind a wall gets no aura through it. Out: the aura's slot (red, as
// the mask has it - see MagicAuraRenderer), whether it's drawn solid (green), and the thing's
// distance from the camera in blocks (alpha; 0 = nothing here).

uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;
uniform sampler2D MainDepthSampler;

uniform float NearPlane;
uniform float FarPlane;

in vec2 texCoord;

out vec4 fragColor;

float linearDepth(float depth) {
    float z = depth * 2.0 - 1.0;
    return 2.0 * NearPlane * FarPlane / (FarPlane + NearPlane - z * (FarPlane - NearPlane));
}

void main() {
    vec4 mask = texture(DiffuseSampler0, texCoord);
    if (mask.a <= 0.0) {
        fragColor = vec4(0.0);
        return;
    }
    float held = linearDepth(texture(DiffuseDepthSampler, texCoord).r);
    float world = linearDepth(texture(MainDepthSampler, texCoord).r);
    // A little slack: the held thing is in the world's depth too, at very nearly the same distance.
    if (held > world + 0.05 + world * 0.002) {
        fragColor = vec4(0.0);
        return;
    }
    fragColor = vec4(mask.rgb, max(held, 0.01));
}
