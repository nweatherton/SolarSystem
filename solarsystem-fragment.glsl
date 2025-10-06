#version 430

in vec2 fragTexCoord;
in vec3 fragNormal;
in vec3 fragPosition;

out vec4 fragColor;                        // Final output color

// Material properties
uniform sampler2D texSampler;
uniform vec3 lightRGB;                     // Light color (R, G, B)
uniform vec3 lightPosition;  // Light position
uniform float ambient;                     // Ambient
uniform float diffuse;                     // Diffuse
uniform float specular;                    // Specular
uniform float shininess;                   // Shininess
uniform float linearAttenuation;           // Linear Attenuation value
uniform int isSun;                         // 1 if this object is the sun

void main() {
    // Retrieve texture color
    vec4 texColor = texture(texSampler, fragTexCoord);
    // If this is the sun, full brightness and skip shading
    if (isSun == 1) {
        fragColor = texColor;
        return;
    }

    // Calculate normalized vectors
    vec3 norm = normalize(fragNormal);
    vec3 lightDirection = normalize(lightPosition - fragPosition);
    vec3 viewDirection = normalize(-fragPosition); // Assuming the camera is at the origin

    // Ambient lighting
    vec3 ambientLight = ambient * lightRGB;

    // Diffuse lighting
    float diff = max(dot(norm, lightDirection), 0.0);
    vec3 diffuseLight = diffuse * diff * lightRGB;

    // Specular lighting
    vec3 halfwayDirection = normalize(lightDirection + viewDirection);
    float spec = pow(max(dot(norm, halfwayDirection), 0.0), shininess);
    vec3 specularLight = specular * spec * lightRGB;

    // Combine lighting with texture and do attenuation
    float distance = length(lightPosition - fragPosition);
    float attenuation = 1.0 / (1.0 + linearAttenuation * distance);
    vec3 finalColor = attenuation * ((ambientLight + diffuseLight) * vec3(texColor) + specularLight);

    // Clamp final color to [0, 1]
    fragColor = vec4(clamp(finalColor, 0.0, 1.0), texColor.a);
}
