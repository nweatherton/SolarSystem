The lighting is not quite right but I am no longer working on this project.

Instructions for the .sol from Professor Adam Smith:

The first line is the position of the camera. The numbers are respectively the x,
y, and z coordinates. The camera’s focus will remain fixed on the sun.
The second line describes the light. The first 3 numbers are the R, G, and B
components of the light. These will be multiplied by the texture. The next 3 numbers
are the base intensities of the ambient, diffuse, and specular components. The last
number is the linear attenuation constant, kl. (You may assume that kc = 1 and
kq = 0.) A value of 0 indicates no attenuation.

Each subsequent line describes one spherical body in the scene.
– The sun line has only 3 tokens: the texture file to apply, the radius,
and the rotation period (or “day”) in seconds. There will be only one sun,
and it should occupy the center of the screen.
– Planets are tabbed in once, and orbit the sun. The first 3 tokens are the
same as for the sun. After that comes the distance between its center and the
sun’s center, followed by the orbital period (“year”) in seconds. Finally is the
specular shininess, with a 0 indicating no specular component.
– Moons are tabbed in twice, and they orbit the planet most recently
specified. The tokens are the same as for planets. Note that a planet may have

multiple moons.
