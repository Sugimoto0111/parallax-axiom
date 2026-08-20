#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float Time;
uniform float Attack;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

float cellMask(ivec2 cell, ivec2 size) {
    if (cell.x < 0 || cell.y < 0 || cell.x >= size.x || cell.y >= size.y) {
        return 0.0;
    }
    return texelFetch(Sampler0, cell, 0).a;
}

void main() {
    ivec2 textureSizePixels = textureSize(Sampler0, 0);
    vec2 pixel = clamp(texCoord0 * vec2(textureSizePixels), vec2(0.0),
            vec2(textureSizePixels) - vec2(0.0001));
    ivec2 cell = ivec2(floor(pixel));
    vec2 local = fract(pixel);
    float mask = cellMask(cell, textureSizePixels);
    if (mask < 0.1) {
        discard;
    }

    float edgeDistance = 2.0;
    if (cellMask(cell + ivec2(-1, 0), textureSizePixels) < 0.1) {
        edgeDistance = min(edgeDistance, local.x);
    }
    if (cellMask(cell + ivec2(1, 0), textureSizePixels) < 0.1) {
        edgeDistance = min(edgeDistance, 1.0 - local.x);
    }
    if (cellMask(cell + ivec2(0, -1), textureSizePixels) < 0.1) {
        edgeDistance = min(edgeDistance, local.y);
    }
    if (cellMask(cell + ivec2(0, 1), textureSizePixels) < 0.1) {
        edgeDistance = min(edgeDistance, 1.0 - local.y);
    }

    float antialias = max(fwidth(edgeDistance), 0.025);
    float line = 1.0 - smoothstep(0.14, 0.14 + antialias, edgeDistance);
    float halo = 1.0 - smoothstep(0.18, 0.62 + antialias, edgeDistance);
    if (line + halo < 0.002) {
        discard;
    }

    vec2 normalizedPosition = pixel / vec2(textureSizePixels);
    float pathCoordinate = fract(normalizedPosition.x * 0.37
            + normalizedPosition.y * 0.63);
    float movingPhase = fract(pathCoordinate - Time * 0.19);
    float distanceToHead = min(movingPhase, 1.0 - movingPhase);
    float traceHead = 1.0 - smoothstep(0.015, 0.115, distanceToHead);
    float breathing = 0.82 + sin(Time * 1.45
            + float(cell.x + cell.y) * 0.74) * 0.18;

    float intensity = line * (0.48 + traceHead * 1.05 + Attack * 0.92)
            + halo * (0.075 + traceHead * 0.19 + Attack * 0.13);
    intensity *= breathing;
    vec3 colour = vec3(0.88 + traceHead * 0.12);
    float alpha = clamp(intensity, 0.0, 1.0) * vertexColor.a;
    fragColor = vec4(colour * vertexColor.rgb, alpha) * ColorModulator;
}
