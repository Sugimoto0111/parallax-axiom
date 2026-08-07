#version 150

const float PI = 3.14159265358979323846;
const float TAU = 6.28318530717958647692;

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float Time;
uniform float Attack;
uniform float CameraYaw;
uniform float CameraPitch;
uniform float PatternScale;

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPosition;
in vec3 viewNormal;

out vec4 fragColor;

float hash21(vec2 value) {
    return fract(sin(dot(value, vec2(127.1, 311.7))) * 43758.5453123);
}

float valueNoise(vec2 value) {
    vec2 cell = floor(value);
    vec2 local = fract(value);
    local = local * local * (3.0 - 2.0 * local);
    float a = hash21(cell);
    float b = hash21(cell + vec2(1.0, 0.0));
    float c = hash21(cell + vec2(0.0, 1.0));
    float d = hash21(cell + vec2(1.0, 1.0));
    return mix(mix(a, b, local.x), mix(c, d, local.x), local.y);
}

float fbm(vec2 value) {
    float result = 0.0;
    float weight = 0.5;
    mat2 turn = mat2(0.82, -0.57, 0.57, 0.82);
    for (int i = 0; i < 4; i++) {
        result += valueNoise(value) * weight;
        value = turn * value * 2.03 + vec2(5.2, 1.3);
        weight *= 0.5;
    }
    return result;
}

mat3 rotateX(float angle) {
    float sine = sin(angle);
    float cosine = cos(angle);
    return mat3(1.0, 0.0, 0.0,
                0.0, cosine, -sine,
                0.0, sine, cosine);
}

mat3 rotateY(float angle) {
    float sine = sin(angle);
    float cosine = cos(angle);
    return mat3(cosine, 0.0, sine,
                0.0, 1.0, 0.0,
                -sine, 0.0, cosine);
}

float maskAt(vec2 uv) {
    return texture(Sampler0, uv).a;
}

void main() {
    float mask = maskAt(texCoord0);
    if (mask < 0.1) {
        discard;
    }

    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    float neighbour = 1.0;
    neighbour = min(neighbour, maskAt(texCoord0 + vec2(texel.x, 0.0)));
    neighbour = min(neighbour, maskAt(texCoord0 - vec2(texel.x, 0.0)));
    neighbour = min(neighbour, maskAt(texCoord0 + vec2(0.0, texel.y)));
    neighbour = min(neighbour, maskAt(texCoord0 - vec2(0.0, texel.y)));
    neighbour = min(neighbour, maskAt(texCoord0 + texel));
    neighbour = min(neighbour, maskAt(texCoord0 - texel));
    neighbour = min(neighbour, maskAt(texCoord0 + vec2(texel.x, -texel.y)));
    neighbour = min(neighbour, maskAt(texCoord0 + vec2(-texel.x, texel.y)));
    float interior = smoothstep(0.12, 0.92, neighbour);
    float rim = mask * (1.0 - interior);

    float distanceToCamera = max(length(viewPosition), 0.001);
    vec3 ray = -viewPosition / distanceToCamera;
    vec3 normal = normalize(viewNormal);
    if (!gl_FrontFacing) {
        normal = -normal;
    }

    // Anchor a virtual panorama to the camera rotation, then bend the ray just
    // enough for a lens-like parallax response as the item is viewed obliquely.
    ray = rotateX(CameraPitch) * rotateY(-CameraYaw) * ray;
    ray = normalize(ray + normal * 0.16);
    vec2 sphereUv = vec2(0.5 + atan(ray.z, ray.x) / TAU,
                         0.5 + asin(clamp(ray.y, -1.0, 1.0)) / PI);

    float slowTime = Time * 0.055;
    vec2 field = sphereUv * vec2(7.0, 4.2) * PatternScale;
    vec2 viewShift = ray.xy * 1.35;
    float layerA = fbm(field + viewShift + vec2(slowTime, -slowTime * 0.43));
    float layerB = fbm(field * 1.67 - viewShift * 0.72
            + vec2(-slowTime * 0.31, slowTime * 0.24));
    float layerC = fbm(field * 2.43 + viewShift * 0.38 + vec2(9.7, -4.1));

    float contourA = 1.0 - smoothstep(0.025, 0.085,
            abs(fract(layerA * 4.0 + layerB * 0.7) - 0.5));
    float contourB = 1.0 - smoothstep(0.018, 0.065,
            abs(fract(layerB * 6.0 - layerC * 0.55) - 0.5));

    vec2 lensCell = fract(field * 0.47 + vec2(slowTime * 0.08,
            -slowTime * 0.05)) - 0.5;
    float lensRadius = length(lensCell + ray.xy * 0.075);
    float ringTarget = 0.255 + sin(Time * 0.21 + layerC * TAU) * 0.028;
    float focusRing = 1.0 - smoothstep(0.012, 0.045,
            abs(lensRadius - ringTarget));

    float displacedEcho = abs(layerA - layerB);
    float echoLine = 1.0 - smoothstep(0.045, 0.15, displacedEcho);
    float structure = contourA * 0.48 + contourB * 0.31
            + focusRing * 0.62 + echoLine * 0.18;
    float depth = 0.018 + layerC * 0.052 + structure * 0.34;
    depth += Attack * (0.12 + focusRing * 0.42);

    // Nearly neutral values keep the material achromatic; contrast and motion,
    // rather than hue, reveal the virtual layers.
    vec3 colour = vec3(depth);
    colour += vec3(0.82) * rim * (0.13 + Attack * 0.32);
    float alpha = mask * (mix(0.18, 0.84, interior)
            + structure * 0.10 + Attack * 0.08);
    vec4 result = vec4(clamp(colour, 0.0, 1.0), clamp(alpha, 0.0, 0.96));
    fragColor = result * vertexColor * ColorModulator;
}
