// The mood grade (README: The mood system - each feeling's look): one set of dials every
// scene-setting moment uses. Neutral values leave the picture untouched:
// Saturation 1, Tint (1,1,1), Contrast 1, Brightness 0, Glow 0.

uniform sampler2D DiffuseSampler0;

uniform float Saturation;
uniform vec3 Tint;
uniform float Contrast;
uniform float Brightness;
uniform float Glow;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSampler0, texCoord);
    vec3 rgb = color.rgb;
    float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));

    rgb = mix(vec3(luma), rgb, Saturation);
    rgb = (rgb - 0.5) * Contrast + 0.5;
    rgb *= Tint;
    rgb += Brightness;
    // Highlight glow: bright areas lift softly, like diffusion on a lens.
    rgb += Glow * max(luma - 0.6, 0.0) * 1.5;

    fragColor = vec4(clamp(rgb, 0.0, 1.0), color.a);
}
