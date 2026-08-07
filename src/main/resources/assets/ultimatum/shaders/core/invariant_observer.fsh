#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float Time;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

float maskAt(ivec2 cell, ivec2 size) {
    if (cell.x < 0 || cell.y < 0 || cell.x >= size.x || cell.y >= size.y) {
        return 0.0;
    }
    return texelFetch(Sampler0, cell, 0).a;
}

vec3 hsvToRgb(vec3 hsv) {
    vec3 p = abs(fract(hsv.xxx + vec3(0.0, 0.6666667, 0.3333333)) * 6.0 - 3.0);
    return hsv.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), hsv.y);
}

void main() {
    ivec2 size = textureSize(Sampler0, 0);
    vec2 pixel = clamp(texCoord0 * vec2(size), vec2(0.0), vec2(size) - vec2(0.0001));
    ivec2 cell = ivec2(floor(pixel));
    float core = maskAt(cell, size);
    float axial = max(max(maskAt(cell + ivec2(1, 0), size),
                          maskAt(cell + ivec2(-1, 0), size)),
                      max(maskAt(cell + ivec2(0, 1), size),
                          maskAt(cell + ivec2(0, -1), size)));
    float diagonal = max(max(maskAt(cell + ivec2(1, 1), size),
                             maskAt(cell + ivec2(-1, 1), size)),
                         max(maskAt(cell + ivec2(1, -1), size),
                             maskAt(cell + ivec2(-1, -1), size)));
    float halo = (1.0 - core) * max(axial * 0.26, diagonal * 0.11);
    if (core + halo < 0.01) {
        discard;
    }

    vec2 fromCentre = texCoord0 - vec2(0.5);
    float radius = length(fromCentre);
    float angle = fract(atan(fromCentre.y, fromCentre.x) / 6.2831853 + 1.0);
    float hue = fract(Time * 0.036 + angle * 0.72 + radius * 0.24);
    vec3 film = hsvToRgb(vec3(hue, 0.62, 1.0));

    float sweepPhase = fract(angle - Time * 0.115);
    float sweepDistance = min(sweepPhase, 1.0 - sweepPhase);
    float sweep = 1.0 - smoothstep(0.015, 0.105, sweepDistance);
    vec3 colour = mix(film, vec3(1.0), sweep * 0.24);
    float breathing = 0.94 + sin(Time * 1.17) * 0.06;
    float alpha = (core * (0.76 + sweep * 0.18) + halo) * vertexColor.a * breathing;
    fragColor = vec4(colour * vertexColor.rgb, alpha) * ColorModulator;
}
