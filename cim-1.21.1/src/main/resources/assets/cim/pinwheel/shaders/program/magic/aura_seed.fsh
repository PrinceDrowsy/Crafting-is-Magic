// Magic aura: seeds the jump flood - every visible held-thing pixel is its own nearest (offset 0).

uniform sampler2D DiffuseSampler0;

out vec4 fragColor;

void main() {
    fragColor = texelFetch(DiffuseSampler0, ivec2(gl_FragCoord.xy), 0).a > 0.0 ? vec4(0.0, 0.0, 0.0, 1.0) : vec4(0.0);
}
