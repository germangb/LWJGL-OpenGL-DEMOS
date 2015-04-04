package effects;
import java.nio.FloatBuffer;

import main.MatrixUtils;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

/**
 * 
 * Render a cube to the screen
 * 
 * @author germangb
 *
 */
public class Cube implements OpenGLEffect {

	private final String vertexShader = "#version 130\n"
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
	
	private final String fragmentShader = "#version 130\n"
			+ "in vec3 v_position;\n"
			+ "in vec3 v_normal;\n"
			+ "out vec4 frag_color;\n"
			+ "void main () {\n"
			+ "float diff = clamp(dot(normalize(v_normal), -normalize(v_position)), 0.0, 1.0);\n"
			+ "	frag_color = vec4(vec3(diff), 1.0);\n"
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
	
	private int vbo;
	
	private Shader shader;
	private int uMvp, uMv;
	
	private Matrix4f model;
	private Matrix4f view;
	private Matrix4f projection;
	
	@Override
	public void setUp() {
		float[] data = new float[] {
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
			-1, +1, +1, 	0, +1, 0,
		};
		FloatBuffer vertexData = BufferUtils.createFloatBuffer(data.length);
		vertexData.put(data);
		vertexData.position(0);
		
		// create vbo
		vbo = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexData, GL15.GL_STATIC_DRAW);
		
		// create shader
		shader = createShader(vertexShader, fragmentShader);
		uMvp = GL20.glGetUniformLocation(shader.id, "u_mvp");
		uMv = GL20.glGetUniformLocation(shader.id, "u_mv");
		
		// create matrices
		model = new Matrix4f();
		view = new Matrix4f();
		projection = MatrixUtils.perspective(3.141592f/3, 4/3f, 0.1f, 100f);
		
		GL11.glEnable(GL11.GL_DEPTH_TEST);
	}
	
	FloatBuffer matBuffer = BufferUtils.createFloatBuffer(16);
	float t = 0;
	
	@Override
	public void update() {
		t += 0.025f;
		
		GL11.glClearColor(1, 1, 1, 1);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
	
		// compute transforms
		model.setIdentity();
		view.setIdentity();
		view.rotate(3.1415f/5, new Vector3f(1,0,0));
		view.rotate(-3.1415f/4, new Vector3f(0,1,0));
		view.translate(new Vector3f(-4, -4, -4));
		model.rotate(t, new Vector3f(0,1,0));
		Matrix4f mv = new Matrix4f();
		Matrix4f mvp = new Matrix4f();
		Matrix4f.mul(view, model, mv);
		Matrix4f.mul(projection, mv, mvp);
		
		// setup shader
		GL20.glUseProgram(shader.id);
		mvp.store(matBuffer); matBuffer.position(0);
		GL20.glUniformMatrix4(uMvp, false, matBuffer);
		mv.store(matBuffer); matBuffer.position(0);
		GL20.glUniformMatrix4(uMv, false, matBuffer);
		
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
		GL20.glEnableVertexAttribArray(0);	// position
		GL20.glEnableVertexAttribArray(1);	// normal
		GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 6<<2, 0<<2);
		GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 6<<2, 3<<2);
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 36);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		
		GL20.glUseProgram(0);
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
		shader.dispose();
		GL15.glDeleteBuffers(vbo);
	}

}
