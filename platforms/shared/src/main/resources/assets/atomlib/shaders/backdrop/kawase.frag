// SPDX-License-Identifier: Apache-2.0
#version 330 core
uniform sampler2D sourceImage;
uniform vec2 displacement;
in vec2 coordinate;
out vec4 result;
void main() {
    // Convex average of four diagonal neighbors. Host alpha is never a weight.
    vec3 sum = vec3(0.0);
    for (int ix = -1; ix <= 1; ix += 2)
        for (int iy = -1; iy <= 1; iy += 2)
            sum += texture(sourceImage, coordinate + vec2(ix, iy) * displacement).rgb;
    result = vec4(sum * 0.25, 1.0);
}
