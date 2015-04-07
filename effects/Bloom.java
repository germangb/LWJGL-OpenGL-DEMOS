package effects;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import main.MainGLFW;
import main.MatrixUtils;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

/**
 * 
 * Bloom effect, using the blur technique
 * 
 * @author germangb
 *
 */

public class Bloom implements OpenGLEffect {

	// shader used for the cube
	
	private final String vertexShaderCube = "#version 130\n"
			+ "in vec4 a_position;\n"
			+ "in vec3 a_normal;\n"
			+ "out vec3 v_position;\n"
			+ "out vec3 v_normal;\n"
			+ "uniform mat4 u_mvp;\n"
			+ "uniform mat4 u_mv;\n"
			+ "void main () {\n"
			+ "	gl_Position = u_mvp * a_position;\n"
			+ "	v_position = (u_mv * a_position).xyz;"
			+ "	v_normal = normalize((u_mv * vec4(a_normal, 0.0)).xyz);\n"
			+ "}";
	
	private final String fragmentShaderCube = "#version 130\n"
			+ "in vec3 v_position;\n"
			+ "in vec3 v_normal;\n"
			+ "out vec4 frag_color;\n"
			+ "void main () {\n"
			+ "float diff = clamp(dot(normalize(v_normal), -normalize(v_position)), 0.0, 1.0);\n"
			+ "	frag_color = vec4(vec3(diff), 1.0);\n"
			+ "}";
	
	private final String fragmentShaderLumin = "#version 130\n"
			+ "in vec3 v_position;\n"
			+ "in vec3 v_normal;\n"
			+ "out vec4 frag_color;\n"
			+ "void main () {\n"
			+ "	float diff = clamp(dot(normalize(v_normal), -normalize(v_position)), 0.0, 1.0);\n"
			+ "	diff = pow(diff, 16.0);\n"
			+ "	if (diff > 0.5) diff = 1.0;"
			+ "	frag_color = vec4(vec3(diff), 1.0);\n"
			+ "}";
	
	// shader used for the blur effect
	
	private final String vertexShaderBlur = "#version 130\n"
			+ "in vec4 a_position;\n"
			+ "void main () {\n"
			+ "	gl_Position = a_position;\n"
			+ "}";
	
	private final String fragmentShaderBlur = "#version 130\n"
			+ "out vec4 frag_color;\n"
			+ "uniform vec2 u_resolution;"
			+ "uniform sampler2D u_texture;"
			+ "uniform vec2 u_direction;"
			+ "void main () {\n"
			+ "	vec2 uv = gl_FragCoord.xy / u_resolution;\n"
			+ "	vec2 tex = 1.0 / u_resolution;\n"
			+ "	vec3 color = vec3(0.0);\n"
			+ "	color += texture2D(u_texture, uv+tex*vec2(-4, -4)*u_direction).rgb * 1 / 36;\n"
			+ "	color += texture2D(u_texture, uv+tex*vec2(-3, -3)*u_direction).rgb * 2 / 36;\n"
			+ "	color += texture2D(u_texture, uv+tex*vec2(-2,-2)*u_direction).rgb * 4 / 36;\n"
			+ "	color += texture2D(u_texture, uv+tex*vec2(-1, -1)*u_direction).rgb * 6 / 36;\n"
			+ "	color += texture2D(u_texture, uv+tex*vec2(0, 0)*u_direction).rgb * 10 / 36;\n"
			+ "	color += texture2D(u_texture, uv+tex*vec2(1, 1)*u_direction).rgb * 6 / 36;\n"
			+ "	color += texture2D(u_texture, uv+tex*vec2(2, 2)*u_direction).rgb * 4 / 36;\n"
			+ "	color += texture2D(u_texture, uv+tex*vec2(3, 3)*u_direction).rgb * 2 / 36;\n"
			+ "	color += texture2D(u_texture, uv+tex*vec2(4, 4)*u_direction).rgb * 1 / 36;\n"
			+ "	frag_color = vec4(color, 1.0);\n"
			+ "}";
	
	// shader used to display the final image to the screen
	
	private final String vertexShaderFinal = "#version 130\n"
			+ "in vec4 a_position;\n"
			+ "void main () {\n"
			+ "	gl_Position = a_position;\n"
			+ "}";
	
	private final String fragmentShaderFinal = "#version 130\n"
			+ "out vec4 frag_color;\n"
			+ "uniform vec2 u_resolution;"
			+ "uniform sampler2D u_texture;"
			+ "uniform sampler2D u_lumin;"
			+ "void main () {\n"
			+ "	vec2 uv = gl_FragCoord.xy / u_resolution;\n"
			+ "	vec3 color = texture2D(u_texture, uv).rgb;\n"
			+ "	vec3 colorLumin = texture2D(u_lumin, uv).rgb;\n"
			+ "	frag_color = vec4(color + colorLumin, 1.0);\n"
			+ "}";

	private class Shader {
		public int id;
		public int vert;
		public int frag;
		
		public void dispose () {
			GL20.glDetachShader(id, vert);
			GL20.glDetachShader(id, frag);
			GL20.glDeleteShader(vert);
			GL20.glDeleteShader(frag);
			GL20.glDeleteProgram(id);
		}
	}
	
	// vertex buffer object
	private int vboCube;
	private int vboBlur;
	
	// shader cube
	private Shader shaderCube;
	private int uMvp, uMv;
	
	// shader lumin
	private Shader shaderLumin;
	private int uMvpLumin, uMvLumin;
	
	// shader blur
	private Shader shaderBlur;
	private int uResolutionBlur;
	private int uTextureBlur;
	private int uDirection;
	
	private Shader shaderFinal;
	private int uResolutionFinal;
	private int uTextureFinal;
	private int uLuminFinal;
	
	// frame buffer object
	private int fbo;
	private int fboLumin;
	private int colorTextureA, colorTextureB;
	private int depthTexture;
	private int originalColor, originalDepth;
	
	// transformations
	private Matrix4f model;
	private Matrix4f view;
	private Matrix4f projection;
	
	@Override
	public void setUp() {
		// create cube vbo
		float[] dataCube = new float[] {
			-1, -1, -1, 	0, 0, -1,
			+1, -1, -1, 	0, 0, -1,
			+1, +1, -1, 	0, 0, -1,
			-1, -1, -1, 	0, 0, -1,
			+1, +1, -1, 	0, 0, -1,
			-1, +1, -1, 	0, 0, -1,
			-1, -1, +1, 	0, 0, +1,
			+1, -1, +1, 	0, 0, +1,
			+1, +1, +1, 	0, 0, +1,
			-1, -1, +1, 	0, 0, +1,
			+1, +1, +1, 	0, 0, +1,
			-1, +1, +1, 	0, 0, +1,
			-1, -1, -1, 	-1, 0, 0,
			-1, +1, -1, 	-1, 0, 0,
			-1, +1, +1, 	-1, 0, 0,
			-1, -1, -1, 	-1, 0, 0,
			-1, +1, +1, 	-1, 0, 0,
			-1, -1, +1, 	-1, 0, 0,
			+1, -1, -1, 	+1, 0, 0,
			+1, +1, -1, 	+1, 0, 0,
			+1, +1, +1, 	+1, 0, 0,
			+1, -1, -1, 	+1, 0, 0,
			+1, +1, +1, 	+1, 0, 0,
			+1, -1, +1, 	+1, 0, 0,
			-1, -1, -1, 	0, -1, 0,
			+1, -1, -1, 	0, -1, 0,
			+1, -1, +1, 	0, -1, 0,
			-1, -1, -1, 	0, -1, 0,
			+1, -1, +1, 	0, -1, 0,
			-1, -1, +1, 	0, -1, 0,
			-1, +1, -1, 	0, +1, 0,
			+1, +1, -1, 	0, +1, 0,
			+1, +1, +1, 	0, +1, 0,
			-1, +1, -1, 	0, +1, 0,
			+1, +1, +1, 	0, +1, 0,
			-1, +1, +1, 	0, +1, 0
		};
		FloatBuffer vertexDataCube = BufferUtils.createFloatBuffer(dataCube.length);
		vertexDataCube.put(dataCube);
		vertexDataCube.position(0);
		vboCube = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboCube);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexDataCube, GL15.GL_STATIC_DRAW);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

		// create full quad vbo
		float[] dataBlur = new float[] {
			-1, -1,
			+1, -1,
			+1, +1,
			-1, -1,
			+1, +1,
			-1, +1
		};
		FloatBuffer vertexDataBlur = BufferUtils.createFloatBuffer(dataBlur.length);
		vertexDataBlur.put(dataBlur);
		vertexDataBlur.position(0);
		vboBlur = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboBlur);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexDataBlur, GL15.GL_STATIC_DRAW);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

		// create lumin shader
		shaderLumin = createShader(vertexShaderCube, fragmentShaderLumin);
		uMvpLumin = GL20.glGetUniformLocation(shaderLumin.id, "u_mvp");
		uMvLumin = GL20.glGetUniformLocation(shaderLumin.id, "u_mv");
		
		// create cube shader
		shaderCube = createShader(vertexShaderCube, fragmentShaderCube);
		uMvp = GL20.glGetUniformLocation(shaderCube.id, "u_mvp");
		uMv = GL20.glGetUniformLocation(shaderCube.id, "u_mv");
		
		// create blur shader
		shaderBlur = createShader(vertexShaderBlur, fragmentShaderBlur);
		uTextureBlur = GL20.glGetUniformLocation(shaderBlur.id, "u_texture");
		uResolutionBlur = GL20.glGetUniformLocation(shaderBlur.id, "u_resolution");
		uDirection = GL20.glGetUniformLocation(shaderBlur.id, "u_direction");
		
		// create final shader
		shaderFinal = createShader(vertexShaderFinal, fragmentShaderFinal);
		uTextureFinal = GL20.glGetUniformLocation(shaderFinal.id, "u_texture");
		uLuminFinal = GL20.glGetUniformLocation(shaderFinal.id, "u_lumin");
		uResolutionFinal = GL20.glGetUniformLocation(shaderFinal.id, "u_resolution");
		
		// create frame buffer
		fbo = GL30.glGenFramebuffers();
		fboLumin = GL30.glGenFramebuffers();
		colorTextureA = createTexture(MainGLFW.WIDTH, MainGLFW.HEIGHT, GL11.GL_RGB);
		colorTextureB = createTexture(MainGLFW.WIDTH, MainGLFW.HEIGHT, GL11.GL_RGB);
		depthTexture = createTexture(MainGLFW.WIDTH, MainGLFW.HEIGHT, GL11.GL_DEPTH_COMPONENT);
		originalColor = createTexture(MainGLFW.WIDTH, MainGLFW.HEIGHT, GL11.GL_RGB);
		originalDepth = createTexture(MainGLFW.WIDTH, MainGLFW.HEIGHT, GL11.GL_DEPTH_COMPONENT);
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboLumin);
		GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTextureA, 0);
		GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTexture, 0);
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
		GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, originalColor, 0);
		GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, originalDepth, 0);
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		
		// create matrices
		model = new Matrix4f();
		view = new Matrix4f();
		projection = MatrixUtils.perspective(3.141592f/3, 4/3f, 0.1f, 100f);
	}
	
	// transformation utilities
	private FloatBuffer matBuffer = BufferUtils.createFloatBuffer(16);
	private float t = 0;
	
	// blur texture target
	private final int blurIterarions = 16;	// must be even
	
	@Override
	public void update(long window) {
		t += 0.025f;
		
		// compute transforms
		model.setIdentity();
		view.setIdentity();
		view.rotate(3.1415f/5, new Vector3f(1,0,0));
		view.rotate(-3.1415f/4, new Vector3f(0,1,0));
		view.translate(new Vector3f(-4, -4, -4));
		model.rotate(t, new Vector3f(0,1,0));
		model.rotate(t, new Vector3f(1,0,0));
		Matrix4f.mul(view, model, mv);
		Matrix4f.mul(projection, mv, mvp);
		
		cubePass();
		luminPass();
		blurPass();
	}
	
	public void blurPass () {
		/*
		 * blur image
		 */
		
		GL11.glDisable(GL11.GL_DEPTH_TEST);

		// begin the blur
		GL20.glUseProgram(shaderBlur.id);
		GL20.glUniform2f(uResolutionBlur, MainGLFW.WIDTH, MainGLFW.HEIGHT);
		GL20.glUniform1i(uTextureBlur, 0);
		
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboBlur);
		GL20.glEnableVertexAttribArray(0);	// position
		GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 2<<2, 0<<2);
		
		// horizontal blur
		GL20.glUniform2f(uDirection, 1, 0);
		for (int i = 0; i < blurIterarions; ++i) {
			int write = i % 2 == 0 ? colorTextureB:colorTextureA;
			int blur = i % 2 == 0 ? colorTextureA:colorTextureB;
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboLumin);
			GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, write, 0);
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, blur);
			GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
		}
		
		// vertical blur
		GL20.glUniform2f(uDirection, 0, 1);
		for (int i = 0; i < blurIterarions; ++i) {
			int write = i % 2 == 0 ? colorTextureB:colorTextureA;
			int blur = i % 2 == 0 ? colorTextureA:colorTextureB;
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboLumin);
			GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, write, 0);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, blur);
			GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
		}
		
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		
		/*
		 * render final image to window
		 */
		
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		GL11.glClearColor(1, 1, 1, 1);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
		
		
		// bind shader
		GL20.glUseProgram(shaderFinal.id);
		GL20.glUniform2f(uResolutionFinal, MainGLFW.WIDTH, MainGLFW.HEIGHT);
		GL20.glUniform1i(uTextureFinal, 0);
		GL20.glUniform1i(uLuminFinal, 1);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, originalColor);
		GL13.glActiveTexture(GL13.GL_TEXTURE1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTextureA);

		// render full screen quad
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboBlur);
		GL20.glEnableVertexAttribArray(0);	// position
		GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 2<<2, 0<<2);
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
	}
	
	public void luminPass () {
		//t += 0.025f;
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboLumin);
		
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glClearColor(0, 0, 0, 1);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

		// setup shader
		GL20.glUseProgram(shaderLumin.id);
		mvp.store(matBuffer); matBuffer.position(0);
		GL20.glUniformMatrix4(uMvpLumin, false, matBuffer);
		mv.store(matBuffer); matBuffer.position(0);
		GL20.glUniformMatrix4(uMvLumin, false, matBuffer);
		
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboCube);
		GL20.glEnableVertexAttribArray(0);	// position
		GL20.glEnableVertexAttribArray(1);	// normal
		GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 6<<2, 0<<2);
		GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 6<<2, 3<<2);
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 36);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		
		GL20.glUseProgram(0);
	}
	
	Matrix4f mv = new Matrix4f();
	Matrix4f mvp = new Matrix4f();
	
	public void cubePass () {
		//t += 0.025f;
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
		
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glClearColor(0, 0, 0, 1);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		
		// setup shader
		GL20.glUseProgram(shaderCube.id);
		mvp.store(matBuffer); matBuffer.position(0);
		GL20.glUniformMatrix4(uMvp, false, matBuffer);
		mv.store(matBuffer); matBuffer.position(0);
		GL20.glUniformMatrix4(uMv, false, matBuffer);
		
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboCube);
		GL20.glEnableVertexAttribArray(0);	// position
		GL20.glEnableVertexAttribArray(1);	// normal
		GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 6<<2, 0<<2);
		GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 6<<2, 3<<2);
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 36);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		
		GL20.glUseProgram(0);
	}
	
	public int createTexture (int width, int height, int format) {
		int id = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, width, height, 0, format, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		return id;
	}
	
	public Shader createShader (String vert, String frag) {
		Shader shader = new Shader();
		shader.vert = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
		shader.frag = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
		GL20.glShaderSource(shader.vert, vert);
		GL20.glShaderSource(shader.frag, frag);
		GL20.glCompileShader(shader.vert);
		GL20.glCompileShader(shader.frag);
		String vertLog = GL20.glGetShaderInfoLog(shader.vert, 1024);
		String fragLog = GL20.glGetShaderInfoLog(shader.frag, 1024);	
		System.err.println("[vertex-shader] "+vertLog);
		System.err.println("[fragment-shader] "+fragLog);
		shader.id = GL20.glCreateProgram();
		GL20.glAttachShader(shader.id, shader.vert);
		GL20.glAttachShader(shader.id, shader.frag);
		GL20.glLinkProgram(shader.id);
		return shader;
	}

	@Override
	public void cleanUp() {
		shaderCube.dispose();
		shaderFinal.dispose();
		shaderBlur.dispose();
		GL15.glDeleteBuffers(vboCube);
		GL15.glDeleteBuffers(vboBlur);
		GL30.glDeleteFramebuffers(fboLumin);
		GL11.glDeleteTextures(colorTextureA);
		GL11.glDeleteTextures(colorTextureB);
		GL11.glDeleteTextures(depthTexture);
	}

}
