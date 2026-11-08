#version 330 core

layout(location = 0) in vec3 from;
layout(location = 1) in vec3 to;
layout(location = 2) in int color;

out vec4 vertexColor;

layout(std140) uniform Camera {
    mat4 view;
    mat4 proj;
    vec3 origin;
};

uniform vec2 uScreenSize;
uniform float uLineWidth;

vec4 getColor(int c) {
    float a = float((c >> 24) & 0xFF) / 255.0;
    float r = float((c >> 16) & 0xFF) / 255.0;
    float g = float((c >> 8) & 0xFF) / 255.0;
    float b = float(c & 0xFF) / 255.0;
    return vec4(r, g, b, a);
}

void main() {
    vec3 pos = mix(from, to, gl_VertexID & 1);
    vertexColor = getColor(color);

    vec4 clipFrom = proj * view * vec4(from, 1.0);
    vec4 clipTo = proj * view * vec4(to, 1.0);
    vec4 clip = (gl_VertexID & 1) == 0 ? clipFrom : clipTo;

    vec2 ndcFrom = clipFrom.xy / clipFrom.w;
    vec2 ndcTo = clipTo.xy / clipTo.w;
    vec2 delta = ndcTo - ndcFrom;
    vec2 normal = length(delta) > 1e-5 ? normalize(vec2(-delta.y, delta.x)) : vec2(0.0, 1.0);

    float side = (gl_VertexID & 1) == 0 ? -1.0 : 1.0;
    vec2 ndcWidth = (uLineWidth / uScreenSize) * 2.0;

    gl_Position = clip;
    gl_Position.xy += normal * ndcWidth * 0.5 * side;
}
