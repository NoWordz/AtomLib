// SPDX-License-Identifier: Apache-2.0
#version 330 core
uniform sampler2D sourceImage;
uniform vec2 origin;
uniform vec2 span;
uniform vec2 outputSize;
uniform float cornerRadius;
uniform float opacity;
in vec2 coordinate;
out vec4 result;
void main() {
    float radius = min(cornerRadius, min(outputSize.x, outputSize.y) * 0.5);
    vec2 q = abs((coordinate - vec2(0.5)) * outputSize) - outputSize * 0.5 + radius;
    float distanceToEdge = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    if (distanceToEdge > 0.0) discard;
    float coverage = 1.0 - smoothstep(-0.75, 0.0, distanceToEdge);
    result = vec4(texture(sourceImage, origin + coordinate * span).rgb, opacity * coverage);
}
