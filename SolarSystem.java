/**
 * SolarSystem is a class that parses a .sol file the graphically represent the solar system in orbit
 *
 * @author Noah Weatherton
 * @version 1.0
 */

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.*;
import javax.swing.*;
import java.lang.Math;
import java.util.Arrays;

import static com.jogamp.opengl.GL4.*;
import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.util.*;
import com.jogamp.common.nio.Buffers;
import org.joml.*;

public class SolarSystem extends JFrame implements GLEventListener {
    // Constants
    private static final int WINDOW_WIDTH = 1200, WINDOW_HEIGHT = 600;
    private static final String WINDOW_TITLE = "Solar System";
    private static final String VERTEX_SHADER_FILE = "solarsystem-vertex.glsl",
            FRAGMENT_SHADER_FILE = "solarsystem-fragment.glsl";
    private static final Vector3f FOCUS_POINT = new Vector3f(0.0f, 0.0f, 0.0f);

    // Window Fields
    private GLCanvas glCanvas;
    private int renderingProgram;
    private int[] vao = new int[1];
    private int[] vbo = new int[4];

    // Allocate variables for display() function
    private FloatBuffer matrixStorage = Buffers.newDirectFloatBuffer(16); //
    private Matrix4f perspectiveMat = new Matrix4f();
    private Matrix4f viewMat = new Matrix4f();
    private Matrix4f modelMat = new Matrix4f();
    private Matrix4f modelViewMat = new Matrix4f();
    private int modelViewLocation, perspectiveLocation;
    private float aspect;
    private double startTime;
    private double elapsedTime;

    // Solar System fields
    private Vector3f cameraPosition;
    private Vector3f lightRGB = new Vector3f();
    private float ambient, diffuse, specular, linearAttenuation;
    private String currentPlanet;
    private int numSphereVertices, numSphereIndices;
    private int texture;
    private float parentPlanetOrbit;
    private float moonPeriod;
    private float parentOrbitalPeriod = -123456;
    private Vector3f lightPosition = new Vector3f(0.0f, 0.0f, 0.0f); // Fixed position
    private int currentIndex = 0;

    // Arrays for drawing
    private int[] textureIDs = new int[16];
    private float[] radiusArray = new float[16];
    private float[] rotationPeriodArray = new float[16];
    private float[] distanceSunArray = new float[16];
    private float[] orbitArray = new float[16];
    private float[] shininessArray = new float[16];
    private float[] additionalOrbitArray = new float[16];
    private String[] textureFilePaths = new String[16];

    /**
     * Constructor for the containing window
     */
    public SolarSystem() {
        // Graphics
        setTitle(WINDOW_TITLE);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);

        // Tells GL about EventListener
        glCanvas = new GLCanvas();
        glCanvas.addGLEventListener(this);
        this.add(glCanvas);
        this.setVisible(true);

        // Starts animating!
        Animator animator = new Animator(glCanvas);
        animator.start();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        GL4 gl = (GL4) GLContext.getCurrentGL();
        gl.glClear(GL_DEPTH_BUFFER_BIT); // Clear Z-Buffer
        gl.glClear(GL_COLOR_BUFFER_BIT);
        gl.glUseProgram(renderingProgram);

        // Calculate elapsed time for animations
        elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0; // Convert to seconds

        // Returns the locations for the uniform variables mv_matrix and p_matrix and send them to the GPU
        modelViewLocation = gl.glGetUniformLocation(renderingProgram, "mv_matrix");
        perspectiveLocation = gl.glGetUniformLocation(renderingProgram, "p_matrix");

        // Install the lighting system
        installLights();

        // Set the camera's view matrix
        viewMat.identity();
        viewMat.lookAt(cameraPosition.x, cameraPosition.y, cameraPosition.z,
                FOCUS_POINT.x, FOCUS_POINT.y, FOCUS_POINT.z,
                0.0f, 1.0f, 0.0f); // Up vector is positive Y

        // Pass perspective matrix to the shader
        gl.glUniformMatrix4fv(perspectiveLocation, 1, false, perspectiveMat.get(matrixStorage));
        Matrix4f[] planetTransforms = new Matrix4f[currentIndex];

        // Draw each body
        for (int i = 0; i < currentIndex; i++) {
            // Set up a UV Sphere
            setupUVSphere(new UVSphere(), 48);

            // Reset model matrix
            modelMat.identity();

            // Orbit calculation
            if (orbitArray[i] != 0) {
                float orbitAngle = (float) ((2 * Math.PI * elapsedTime) / orbitArray[i]);
                modelMat.rotate(orbitAngle, 0.0f, 1.0f, 0.0f);
                modelMat.translate(distanceSunArray[i], 0.0f, 0.0f);
            }

            // Store planet transform for moons later
            planetTransforms[i] = new Matrix4f(modelMat);

            // Rotation for moons
            if (additionalOrbitArray[i] != 0) {
                modelMat.identity().mul(planetTransforms[i-1]);
                float moonOrbitAngle = (float) ((2 * Math.PI * elapsedTime) / orbitArray[i]);
                modelMat.rotate(moonOrbitAngle, 0.0f, 1.0f, 0.0f);
                modelMat.translate(distanceSunArray[i], 0.0f, 0.0f);
            }

            // Self-rotation
            if (rotationPeriodArray[i] != 0) {
                float selfRotationAngle = (float) ((2 * Math.PI * elapsedTime) / rotationPeriodArray[i]);
                modelMat.rotate(selfRotationAngle, 0.0f, 1.0f, 0.0f);
            }

            // Scale for all bodies
            modelMat.scale(radiusArray[i]);

            // Combine model and view matrices
            modelViewMat.identity();
            modelViewMat.mul(viewMat);
            modelViewMat.mul(modelMat);

            // Pass the model-view matrix to the shader
            gl.glUniformMatrix4fv(modelViewLocation, 1, false, modelViewMat.get(matrixStorage));

            // Determine if this celestial body is the sun
            int isSunLocation = gl.glGetUniformLocation(renderingProgram, "isSun");
            int isSun = 0;
            if (i == 0) {
                isSun = 1;
            }
            gl.glUniform1i(isSunLocation, isSun);

            int ambientLocation = gl.glGetUniformLocation(renderingProgram, "ambient");
            int diffuseLocation = gl.glGetUniformLocation(renderingProgram, "diffuse");
            int specularLocation = gl.glGetUniformLocation(renderingProgram, "specular");
            int shininessLocation = gl.glGetUniformLocation(renderingProgram, "shininess");
            int lightRGBLocation = gl.glGetUniformLocation(renderingProgram, "lightRGB");
            int lightPositionLocation = gl.glGetUniformLocation(renderingProgram, "lightPosition");

            gl.glUniform1f(ambientLocation, ambient);
            gl.glUniform1f(diffuseLocation, diffuse);
            gl.glUniform1f(specularLocation, specular);
            gl.glUniform1f(shininessLocation, shininessArray[i]); // Per celestial body
            gl.glUniform3f(lightRGBLocation, lightRGB.x, lightRGB.y, lightRGB.z);
            gl.glUniform3f(lightPositionLocation, lightPosition.x, lightPosition.y, lightPosition.z);

            //  Texture jargon
            int texSamplerLocation = gl.glGetUniformLocation(renderingProgram, "texSampler");
            gl.glUniform1i(texSamplerLocation, 0);

            // Send linear attenuation
            int linearAttenuationLocation = gl.glGetUniformLocation(renderingProgram, "linearAttenuation");
            gl.glUniform1f(linearAttenuationLocation, linearAttenuation);

            // Activate and bind the texture
            gl.glActiveTexture(GL_TEXTURE0);
            gl.glBindTexture(GL_TEXTURE_2D, textureIDs[i]);

            // Draw the sphere
            gl.glBindVertexArray(vao[0]); // Ensure VAO is bound
            gl.glDrawElements(GL_TRIANGLES, numSphereIndices, GL_UNSIGNED_INT, 0);
        }

        // Ensure depth test is enabled
        gl.glEnable(GL_DEPTH_TEST);
        gl.glDepthFunc(GL_LEQUAL);
    }

    /**
     * Do some setting up
     */
    @Override
    public void init(GLAutoDrawable drawable) {
        GL4 gl = (GL4) drawable.getGL();
        startTime = System.currentTimeMillis();
        renderingProgram = Utils.createShaderProgram(VERTEX_SHADER_FILE, FRAGMENT_SHADER_FILE);

        for (int i = 0; i < currentIndex; i++) {
            if (textureFilePaths[i] != null) {
                textureIDs[i] = Utils.loadTextureAWT(textureFilePaths[i]); // Load texture using OpenGL
            }
        }

        // Camera position default values
        if (cameraPosition == null) {
            cameraPosition = new Vector3f(0.0f, 0.0f, 15.0f);
        }

        // Default values
        ambient = 0.1f;
        diffuse = 0.8f;
        specular = 0.5f;
        //shininessArray = new float[] { 32.0f, 16.0f, 8.0f, 10.0f, 10.0f };
    }

    // Method to set up all vertices
    private void setupUVSphere(UVSphere sphere, int precision) {
        GL4 gl = (GL4) GLContext.getCurrentGL();

        // From Shuttle Model
        numSphereVertices = sphere.getNumVertices();
        numSphereIndices = sphere.getNumIndices();
        int[] indices = sphere.getIndices();
        Vector3f[] vertices = sphere.getXYZCoords();
        Vector2f[] texCoords = sphere.getSTCoords();
        Vector3f[] normals = sphere.getXYZCoords(); // Since it is the same for a unit sphere

        float[] pvalues = new float[numSphereVertices*3];
        float[] tvalues = new float[numSphereVertices*2];
        float[] nvalues = new float[numSphereVertices*3];

        for (int i=0; i<numSphereVertices; i++) {
            pvalues[i*3]   = (float) (vertices[i]).x();
            pvalues[i*3+1] = (float) (vertices[i]).y();
            pvalues[i*3+2] = (float) (vertices[i]).z();
            tvalues[i*2]   = (float) (texCoords[i]).x();
            tvalues[i*2+1] = (float) (texCoords[i]).y();
            nvalues[i*3]   = (float) (normals[i]).x();
            nvalues[i*3+1] = (float) (normals[i]).y();
            nvalues[i*3+2] = (float) (normals[i]).z();
        }

        // Transmit data into the first VBO
        gl.glGenVertexArrays(vao.length, vao, 0);
        gl.glBindVertexArray(vao[0]);
        gl.glGenBuffers(vbo.length, vbo, 0);

        gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[0]);
        FloatBuffer vertexBuffer = Buffers.newDirectFloatBuffer(pvalues);
        gl.glBufferData(GL_ARRAY_BUFFER, vertexBuffer.limit()*4, vertexBuffer, GL_STATIC_DRAW);

        // Enable position
        gl.glEnableVertexAttribArray(0);  // Location 0 position
        gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);

        gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[1]);
        FloatBuffer texelBuffer = Buffers.newDirectFloatBuffer(tvalues);
        gl.glBufferData(GL_ARRAY_BUFFER, texelBuffer.limit()*4, texelBuffer, GL_STATIC_DRAW);

        // Enable texture coordinates
        gl.glEnableVertexAttribArray(1);  // Location 1 texture coordinates
        gl.glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, 0);

        gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[2]);
        FloatBuffer normalBuffer = Buffers.newDirectFloatBuffer(nvalues);
        gl.glBufferData(GL_ARRAY_BUFFER, normalBuffer.limit()*4,normalBuffer, GL_STATIC_DRAW);

        gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vbo[3]);
        IntBuffer idxBuf = Buffers.newDirectIntBuffer(indices);
        gl.glBufferData(GL_ELEMENT_ARRAY_BUFFER, idxBuf.limit()*4, idxBuf, GL_STATIC_DRAW);

        // Enable normals
        gl.glEnableVertexAttribArray(2);  // Location 2 normals
        gl.glVertexAttribPointer(2, 3, GL_FLOAT, false, 0, 0);
    }

    /**
     * main method to create the object and check for
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("A file path to the desired .sol file is necessary");
            System.exit(1);
        }
        SolarSystem solarSystem = new SolarSystem();
        solarSystem.parseSolarSystemFile(args[0]);
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        aspect = (float) glCanvas.getWidth() / (float) glCanvas.getHeight();
        perspectiveMat.setPerspective((float) Math.toRadians(60.0f), aspect, 0.1f, 1000.0f);
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
        // Empty
    }

    private void parseSolarSystemFile(String fileName) {
        // Not scanner because it floats my boat. Otherwise, it would not be afloat
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split("\\s+");

                switch (lineNumber) {
                    case 0 -> {
                        // Parse camera position
                        if (data.length != 3) throw new IllegalArgumentException("Invalid camera line format.");
                        cameraPosition = new Vector3f(
                                Float.parseFloat(data[0]), //x
                                Float.parseFloat(data[1]), //y
                                Float.parseFloat(data[2]) //z
                        );
                    }
                    case 1 -> {
                        // Parse light settings
                        if (data.length != 7) throw new IllegalArgumentException("Invalid light line format.");
                        lightRGB = new Vector3f(
                                Float.parseFloat(data[0]), //r
                                Float.parseFloat(data[1]), //g
                                Float.parseFloat(data[2]) //b
                        );
                        ambient = Float.parseFloat(data[3]);
                        diffuse = Float.parseFloat(data[4]);
                        specular = Float.parseFloat(data[5]);
                        linearAttenuation = Float.parseFloat(data[6]); //kl, while kc is 1 and kq is 0
                    }
                    default -> {
                        // Parse bodies (sun, planet, or moon)
                        if (!line.startsWith("\t")) {
                            // Sun
                            parseSun(data);
                        } else if (line.startsWith("\t") && !line.startsWith("\t\t")) {
                            // Planet
                            parsePlanet(data);
                        } else if (line.startsWith("\t\t")) {
                            // Moon
                            parseMoon(data);
                        }
                    }
                }
                lineNumber++;
            }
        } catch (IOException exception) {
            throw new RuntimeException("Trouble reading .sol file");
        }
    }
    private void parseSun (String[] data) {
        String textureFile = data[0];
        float radius = Float.parseFloat(data[1]);
        float rotationPeriod = Float.parseFloat(data[2]);
        float shininess = 0f;

        // Now add to the proper arrays
        textureFilePaths[currentIndex] = textureFile;
        radiusArray[currentIndex] = radius;
        rotationPeriodArray[currentIndex] = rotationPeriod;
        distanceSunArray[currentIndex] = 0f; // It is the sun so...
        orbitArray[currentIndex] = 0f; // No orbit
        shininessArray[currentIndex] = shininess;
        additionalOrbitArray[currentIndex] = 0f; // No additional orbit for the Sun

        // Add 1 to currentIndex
        currentIndex++;
    }

    private void parsePlanet (String[] data) {
        String textureFile = data[1];
        float radius = Float.parseFloat(data[2]);
        float rotationPeriod = Float.parseFloat(data[3]);
        float distanceSunCenter = Float.parseFloat(data[4]);
        float orbitalPeriod = Float.parseFloat(data[5]);
        float shininess = Float.parseFloat(data[6]);

        // Update current planets orbital for moon
        parentOrbitalPeriod = orbitalPeriod;

        // Now add to proper arrays
        textureFilePaths[currentIndex] = textureFile;
        radiusArray[currentIndex] = radius;
        rotationPeriodArray[currentIndex] = rotationPeriod;
        distanceSunArray[currentIndex] = distanceSunCenter;
        orbitArray[currentIndex] = orbitalPeriod;
        shininessArray[currentIndex] = shininess;
        additionalOrbitArray[currentIndex] = 0f; // No additional orbit for planets

        // Add 1 to currentIndex
        currentIndex++;
    }

    private void parseMoon (String[] data) {
        String textureFile = data[1];
        float radius = Float.parseFloat(data[2]);
        float rotationPeriod = Float.parseFloat(data[3]);
        float distanceSunCenter = Float.parseFloat(data[4]);
        float parentOrbitalPeriod = orbitArray[currentIndex - 1];
        float shininess = Float.parseFloat(data[6]);
        float orbitalPeriod = Float.parseFloat(data[5]);

        if (parentOrbitalPeriod == -123456f) {
            throw new IllegalStateException("No planet specified before moon.");
        }

        // Now add to proper arrays
        textureFilePaths[currentIndex] = textureFile;
        radiusArray[currentIndex] = radius;
        rotationPeriodArray[currentIndex] = rotationPeriod;
        distanceSunArray[currentIndex] = distanceSunCenter;
        orbitArray[currentIndex] = orbitalPeriod;
        shininessArray[currentIndex] = shininess;
        additionalOrbitArray[currentIndex] = parentOrbitalPeriod; // Store parent orbital period for moons to rotate by later

        // Increment index
        currentIndex++;
    }

    private void installLights() {
        GL4 gl = (GL4) GLContext.getCurrentGL();

        // Use parsed lighting values
        int lightRGBLoc = gl.glGetUniformLocation(renderingProgram, "lightRGB");
        int ambientLoc = gl.glGetUniformLocation(renderingProgram, "ambient");
        int diffuseLoc = gl.glGetUniformLocation(renderingProgram, "diffuse");
        int specularLoc = gl.glGetUniformLocation(renderingProgram, "specular");
        int linearAttenLoc = gl.glGetUniformLocation(renderingProgram, "linearAttenuation");

        float[] lightRGBArray = new float[]{lightRGB.x, lightRGB.y, lightRGB.z};
        gl.glProgramUniform3fv(renderingProgram, lightRGBLoc, 1, lightRGBArray, 0);
        gl.glProgramUniform1f(renderingProgram, ambientLoc, ambient);
        gl.glProgramUniform1f(renderingProgram, diffuseLoc, diffuse);
        gl.glProgramUniform1f(renderingProgram, specularLoc, specular);
        gl.glProgramUniform1f(renderingProgram, linearAttenLoc, linearAttenuation);
    }
}