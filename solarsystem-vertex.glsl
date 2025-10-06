#version 430

layout(location = 0) in vec3 position;
layout(location = 1) in vec2 texCoord;
layout(location = 2) in vec3 normal;

out vec2 fragTexCoord;
out vec3 fragNormal;
out vec3 fragPosition;

uniform mat4 mv_matrix;
uniform mat4 p_matrix;

void main() {
    gl_Position = p_matrix * mv_matrix * vec4(position, 1.0);

    fragPosition = vec3(mv_matrix * vec4(position, 1.0));
    fragNormal = normalize(mat3(mv_matrix) * normal);
    fragTexCoord = texCoord;
}
