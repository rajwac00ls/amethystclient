#version 450

layout(location = 0) in vec3 from;
layout(location = 1) in vec3 to;
layout(location = 2) in int color;

layout(location = 0) out vec4 vColor;

layout(set = 0, binding = 0) uniform Camera {
    mat4 view;
    mat4 proj;
    vec3 origin;
} ubo;

layout(push_constant) uniform PushConstants {
    vec4 data; // x = line width (px), y = screen width, z = screen height
} pc;

vec4 getColor(int c) {
    float a = float((c >> 24) & 0xFF) / 255.0;
    float r = float((c >> 16) & 0xFF) / 255.0;
    float g = float((c >> 8) & 0xFF) / 255.0;
    float b = float(c & 0xFF) / 255.0;
    return vec4(r, g, b, a);
}

void main() {
    vec3 pos = mix(from, to, float(gl_VertexIndex & 1));
    vColor = getColor(color);

    vec4 clipFrom = ubo.proj * ubo.view * vec4(from, 1.0);
    vec4 clipTo = ubo.proj * ubo.view * vec4(to, 1.0);
    vec4 clip = (gl_VertexIndex & 1) == 0 ? clipFrom : clipTo;

    vec2 ndcFrom = clipFrom.xy / clipFrom.w;
    vec2 ndcTo = clipTo.xy / clipTo.w;
    vec2 delta = ndcTo - ndcFrom;
    vec2 normal = length(delta) > 1e-5 ? normalize(vec2(-delta.y, delta.x)) : vec2(0.0, 1.0);

    float side = (gl_VertexIndex & 1) == 0 ? -1.0 : 1.0;
    vec2 ndcWidth = (pc.data.x / pc.data.yz) * 2.0;

    gl_Position = clip;
    gl_Position.xy += normal * ndcWidth * 0.5 * side;
}
