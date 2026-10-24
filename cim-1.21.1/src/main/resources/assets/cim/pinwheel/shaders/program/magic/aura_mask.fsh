// The aura's slot (in red) wherever the thing's own texture is solid, so its see-through pixels stay
// holes.

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    if (texture(Sampler0, texCoord0).a < 0.1) {
        discard;
    }
    fragColor = vertexColor;
}
