#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float Time;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

vec3 hsvToRgb(vec3 hsv) {
    vec3 p = abs(fract(hsv.xxx + vec3(0.0, 0.6666667, 0.3333333)) * 6.0 - 3.0);
    return hsv.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), hsv.y);
}

bool belongsToBand(int band, float radius) {
    if (band == 0) {
        return radius >= 0.395;
    }
    if (band == 1) {
        return radius >= 0.285 && radius < 0.395;
    }
    if (band == 2) {
        return radius >= 0.165 && radius < 0.285;
    }
    return radius < 0.165;
}

void main() {
    float mask = texture(Sampler0, texCoord0).a;
    if (mask < 0.1) {
        discard;
    }

    vec2 fromCentre = texCoord0 - vec2(0.5);
    float radius = length(fromCentre);
    int band = int(floor(vertexColor.r * 3.0 + 0.5));
    if (!belongsToBand(band, radius)) {
        discard;
    }

    float angle = fract(atan(fromCentre.y, fromCentre.x) / 6.2831853 + 1.0);
    float hue = fract(Time * 0.036 + float(band) * 0.17
            + angle * 0.11 + radius * 0.19);
    vec3 film = hsvToRgb(vec3(hue, 0.42, 0.96));

    float sweepPhase = fract(angle - Time * 0.115 + float(band) * 0.21);
    float sweepDistance = min(sweepPhase, 1.0 - sweepPhase);
    float sweep = 1.0 - smoothstep(0.015, 0.115, sweepDistance);
    float breathing = 0.90 + sin(Time * 1.17 + float(band) * 1.31) * 0.10;
    vec3 colour = mix(film, vec3(1.0), sweep * 0.38);
    float alpha = mask * vertexColor.a * breathing * (0.82 + sweep * 0.18);
    fragColor = vec4(colour, alpha) * ColorModulator;
}
