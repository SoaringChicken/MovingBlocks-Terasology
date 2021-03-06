#version 330 core
// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

layout (location = 0) in vec3 in_vert;
layout (location = 1) in vec3 in_normal;
layout (location = 2) in vec2 in_uv0;
layout (location = 4) in vec4 in_color0;

uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;

out vec4 v_color0;

void main() {
    v_color0 = in_color0;
    gl_Position = (projectionMatrix * modelViewMatrix) * vec4(in_vert, 1.0);
}
