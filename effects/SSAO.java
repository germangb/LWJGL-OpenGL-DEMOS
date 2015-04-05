package effects;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
//import java.util.Random;


import main.Main;
import main.MatrixUtils;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import de.matthiasmann.twl.utils.PNGDecoder;
import de.matthiasmann.twl.utils.PNGDecoder.Format;

/**
 * 
 * Screen space ambient occlusion
 * 
 * @author germangb
 *
 */
public class SSAO implements OpenGLEffect {

	// TWEAKING
	private final int SAMPLES = 32;	// <= than 128
	private final float RADIUS = 0.25f;
	
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
			+ "out vec4 frag_normal;\n"
			+ "void main () {\n"
			+ "float diff = clamp(dot(normalize(v_normal), -normalize(v_position)), 0.0, 1.0);\n"
			+ "	diff = mix(0.5, 1.0, diff);"
			+ "	frag_color = vec4(vec3(1.0), 1.0);\n"
			+ "	frag_normal = vec4(v_normal*0.5+0.5, 1.0);"
			+ "}";
	
	// shader used to display the final image to the screen
	
	private final String vertexShaderSSAO = "#version 130\n"
			+ "in vec4 a_position;\n"
			+ "void main () {\n"
			+ "	gl_Position = a_position;\n"
			+ "}";
	
	private final String fragmentShaderSSAO = "#version 130\n"
			+ "out vec4 frag_color;\n"
			+ "uniform vec3 u_samples[128] = {"
			+ "vec3(0.037177928,0.0044654296,0.029933896),\n"
			+ "vec3(-0.24385965,0.60051495,-0.28419802),\n"
			+ "vec3(0.17040135,0.0470007,0.41519555),\n"
			+ "vec3(-0.28103375,0.40869126,-0.4802908),\n"
			+ "vec3(-0.07622337,0.7991029,0.5932831),\n"
			+ "vec3(0.42802635,0.07755524,-0.06482649),\n"
			+ "vec3(0.3692872,0.6875985,-0.16762063),\n"
			+ "vec3(-0.3168595,0.07392933,0.09266477),\n"
			+ "vec3(-0.23339422,0.100786276,0.26212963),\n"
			+ "vec3(-0.25795275,0.062427703,0.06880221),\n"
			+ "vec3(0.04867975,0.062343445,0.013762419),\n"
			+ "vec3(0.23781206,0.13471504,0.7503907),\n"
			+ "vec3(-0.18128182,0.061228994,-0.054967016),\n"
			+ "vec3(0.7448471,0.012968527,-0.1929226),\n"
			+ "vec3(0.484571,0.041223455,0.21814911),\n"
			+ "vec3(-0.008805875,0.19797431,-0.09444523),\n"
			+ "vec3(0.1352963,0.089606605,0.09638853),\n"
			+ "vec3(0.06966525,0.096466824,-0.06672557),\n"
			+ "vec3(-0.33934718,0.52971905,-0.15866448),\n"
			+ "vec3(-0.11542175,0.9410079,-0.07385515),\n"
			+ "vec3(-0.0027031135,0.023949862,0.028562345),\n"
			+ "vec3(-0.5689861,0.042864494,0.5443621),\n"
			+ "vec3(-0.056812014,0.5447749,-0.31405902),\n"
			+ "vec3(-0.61687815,0.34982237,-0.65074307),\n"
			+ "vec3(0.4576846,0.30120912,-0.016782846),\n"
			+ "vec3(-0.3871637,0.019962564,0.65093577),\n"
			+ "vec3(0.033697896,0.05319917,-0.031137232),\n"
			+ "vec3(0.09196652,0.22050923,-0.16563119),\n"
			+ "vec3(0.19197708,0.03971035,0.15785646),\n"
			+ "vec3(0.05192,0.08577064,-0.056131642),\n"
			+ "vec3(0.1251262,0.046541248,0.25920773),\n"
			+ "vec3(-0.19587876,0.05509093,0.19927722),\n"
			+ "vec3(0.20382945,0.62200195,-0.05831411),\n"
			+ "vec3(-0.28796056,0.73976123,0.05911063),\n"
			+ "vec3(0.023737134,0.06096801,-0.065661356),\n"
			+ "vec3(-0.002908801,0.4302071,-0.51435554),\n"
			+ "vec3(-0.40975353,0.20715582,-0.01428228),\n"
			+ "vec3(0.31912592,0.273365,-0.21453057),\n"
			+ "vec3(0.7819439,0.12099827,-0.20498298),\n"
			+ "vec3(0.17778306,0.5942528,0.32882145),\n"
			+ "vec3(0.24770786,0.12407852,0.0621321),\n"
			+ "vec3(0.24260741,0.1567537,-0.21553543),\n"
			+ "vec3(0.6768648,0.4509167,-0.5441519),\n"
			+ "vec3(0.8579324,0.1943819,-0.2711389),\n"
			+ "vec3(-0.2413977,0.48758224,-0.58925974),\n"
			+ "vec3(-0.05237252,0.1127521,-0.016224092),\n"
			+ "vec3(-0.6981429,0.34299263,0.42411688),\n"
			+ "vec3(-0.14913322,0.20676255,0.1383723),\n"
			+ "vec3(-0.1491481,0.19434258,-0.16854376),\n"
			+ "vec3(-0.20745075,0.14485869,0.021200197),\n"
			+ "vec3(-0.38731983,0.36008602,0.8313532),\n"
			+ "vec3(0.47268507,0.52501744,-0.18236354),\n"
			+ "vec3(-0.014402407,1.3267672E-4,0.009175745),\n"
			+ "vec3(0.032660123,0.23027006,0.5092623),\n"
			+ "vec3(0.2445112,0.62183774,0.55148995),\n"
			+ "vec3(0.038361706,0.16340885,-0.2614387),\n"
			+ "vec3(0.19338778,0.08320792,0.86337006),\n"
			+ "vec3(0.23953909,0.62600243,-0.2223872),\n"
			+ "vec3(0.09857293,0.07211558,0.08149579),\n"
			+ "vec3(-0.29500166,0.0046710796,0.5686271),\n"
			+ "vec3(-0.10284624,0.41611388,0.073809065),\n"
			+ "vec3(-0.3474254,0.6782988,0.46222645),\n"
			+ "vec3(0.53171384,0.21567006,0.3811459),\n"
			+ "vec3(-0.01364459,0.11999134,-0.16014822),\n"
			+ "vec3(0.3513421,0.735199,0.52463484),\n"
			+ "vec3(-0.038025104,0.059919022,0.06122578),\n"
			+ "vec3(0.5682278,0.6393413,0.021520365),\n"
			+ "vec3(0.37009215,0.5180787,-0.16651233),\n"
			+ "vec3(0.34905455,0.061895035,0.18961339),\n"
			+ "vec3(-0.38956428,0.0067299902,0.3439352),\n"
			+ "vec3(0.4013161,0.38520294,0.5248978),\n"
			+ "vec3(0.23815437,0.23671235,0.25807855),\n"
			+ "vec3(0.1771281,0.09759404,-0.13545343),\n"
			+ "vec3(0.13709734,0.024194025,0.051518552),\n"
			+ "vec3(-0.5637207,0.48504257,0.05612399),\n"
			+ "vec3(0.11402065,0.87655944,-0.39898404),\n"
			+ "vec3(0.26888102,0.5524483,-0.13705643),\n"
			+ "vec3(-0.37837738,0.17395186,-0.30996022),\n"
			+ "vec3(-0.32995075,0.16342837,-0.27217296),\n"
			+ "vec3(0.6550955,0.15250628,-0.13367952),\n"
			+ "vec3(0.35986108,0.22873299,-0.16932502),\n"
			+ "vec3(-0.02590451,0.021588624,-0.15097576),\n"
			+ "vec3(0.0650018,0.30528748,-0.21089321),\n"
			+ "vec3(-0.066712424,0.0070538563,0.2731514),\n"
			+ "vec3(0.048997376,0.759485,0.59806406),\n"
			+ "vec3(0.11932138,0.018504363,0.23751183),\n"
			+ "vec3(0.43874004,0.26231804,-0.32420808),\n"
			+ "vec3(-0.010847195,0.010361846,4.441559E-4),\n"
			+ "vec3(-0.13134483,0.089230895,0.289408),\n"
			+ "vec3(0.090402864,0.4112622,-0.474585),\n"
			+ "vec3(-0.05576216,0.20613162,0.041925352),\n"
			+ "vec3(0.042459827,0.22773077,0.1259023),\n"
			+ "vec3(0.025617646,0.06094148,0.084825434),\n"
			+ "vec3(0.49939376,0.61331326,-0.16549307),\n"
			+ "vec3(0.18831815,0.34907436,0.43761006),\n"
			+ "vec3(-0.13780339,0.044090666,0.14128071),\n"
			+ "vec3(0.23666805,0.5672319,0.18010454),\n"
			+ "vec3(-0.009011529,0.013262589,0.013285643),\n"
			+ "vec3(0.08979618,0.3176014,0.28020644),\n"
			+ "vec3(0.02051544,0.35877907,-0.49915448),\n"
			+ "vec3(-0.008712267,0.224793,-0.6675068),\n"
			+ "vec3(-0.003326359,0.40085432,0.23579589),\n"
			+ "vec3(0.14624302,0.42195985,-0.13506351),\n"
			+ "vec3(0.009807803,0.093410626,0.051495787),\n"
			+ "vec3(0.0013325559,0.0059625534,-0.016725827),\n"
			+ "vec3(0.55326384,0.5922909,0.12285417),\n"
			+ "vec3(-0.10582145,0.8604308,-0.43150803),\n"
			+ "vec3(-0.19017927,0.061685637,0.12329064),\n"
			+ "vec3(0.24454695,0.2687681,-0.23417962),\n"
			+ "vec3(0.089084044,0.14681418,0.042045865),\n"
			+ "vec3(0.22108936,0.35643277,0.46918666),\n"
			+ "vec3(-0.14758247,0.19844669,-0.21346219),\n"
			+ "vec3(0.37947792,0.77563417,-0.20355448),\n"
			+ "vec3(-0.1410117,0.19267397,-0.7217085),\n"
			+ "vec3(-0.036907293,0.39728975,-0.17980106),\n"
			+ "vec3(0.0018741547,0.0090094,-0.0071644248),\n"
			+ "vec3(-0.41891372,0.19013938,0.019882875),\n"
			+ "vec3(-0.05402369,0.055250272,0.028340418),\n"
			+ "vec3(-0.19258007,0.472187,0.18708143),\n"
			+ "vec3(0.36300626,0.28441727,0.56222636),\n"
			+ "vec3(0.0015113263,0.05913738,0.07906877),\n"
			+ "vec3(-0.21874425,0.347585,0.13490596),\n"
			+ "vec3(0.26475847,0.15278001,0.053503122),\n"
			+ "vec3(0.36867344,0.2880061,-0.5525578),\n"
			+ "vec3(0.38992235,0.2639807,-0.71612704),\n"
			+ "vec3(0.072205484,0.009784062,-0.83731914),\n"
			+ "vec3(0.008882269,0.011374181,0.0069199027),\n"
			+ "vec3(0.33609566,0.04074963,-0.01924415),\n};\n"
			+ "uniform vec2 u_resolution;"
			+ "uniform float u_radius;"
			+ "uniform int u_maxSamples;"
			+ "uniform sampler2D u_texture;"
			+ "uniform sampler2D u_depth;"
			+ "uniform sampler2D u_normal;"
			+ "uniform sampler2D u_random;"
			+ "uniform mat4 u_invProjection;"
			+ "uniform mat4 u_projection;"
			+ "uniform float u_mouseX;"
			+ "vec4 getModelViewPos (vec2 uv) {"
			+ "	float depth = texture2D(u_depth, uv).r;"
			+ "	vec4 mvPos = vec4(uv*2.0-1.0, depth*2.0-1.0, 1.0);"
			+ "	mvPos = u_invProjection * mvPos;\n"
			+ "	return mvPos;"
			+ "}"
			+ "void main () {\n"
			+ "	vec2 uv = gl_FragCoord.xy / u_resolution;\n"
			+ "	vec3 color = texture2D(u_texture, uv).rgb;\n"
			+ " if (uv.x > u_mouseX) {"
			+ "		vec4 mvPos = getModelViewPos(uv);"
			+ "		vec3 randRead = texture2D(u_random, uv * u_resolution / vec2(64.0)).rgb * 2.0 - 1.0;"
			+ "		vec3 normalRead = texture2D(u_normal, uv).xyz * 2.0 - 1.0;"
			+ "		vec3 tangent = randRead - dot(normalRead, randRead)/dot(normalRead, normalRead) * normalRead;"
			+ "		vec3 bitangent = cross(normalRead, tangent);"
			+ "		mat3 kMat = mat3(normalize(tangent), normalize(normalRead), normalize(bitangent));"
			+ "		float ao = 0.0;"
			+ "		for (int i = 0; i < u_maxSamples; ++i) {"
			+ "			vec4 sample = mvPos + vec4(kMat * u_samples[i] * u_radius, 0.0);"
			+ "			vec4 sampleProject = u_projection * sample;"
			+ "			sampleProject.xyz /= sampleProject.w;"
			+ "			float depthRead = texture2D(u_depth, sampleProject.xy*0.5+0.5).r;"
			+ "			float diff = sampleProject.z*0.5+0.5 - depthRead;"
			+ "			if (diff > 0.0001 && diff < 0.0025) ao+=1;"
			+ "		}"
			+ "		color *=  1.0 - ao / float(u_maxSamples);"
			+ " }"
			+ "	if (abs(uv.x-u_mouseX) < 0.005) color *= 0.0;"
			+ "	frag_color = vec4(color, 1.0);\n"
			+ "}";
	
	// vertex buffer object
	private int vboCube;
	private int vboSSAO;
	
	// shader cube
	private Shader shaderCube;
	private int uMvp, uMv;
	
	// shader ssao
	private Shader shaderFinal;
	private int randomTexture, uRandomFinal;
	private int uResolutionFinal;
	private int uTextureFinal;
	private int uDepthFinal;
	private int uNormalFinal;
	private int uProjectionFinal;
	private int uInvProjection;
	private int uRadius;
	private int uMaxSamples;
	private int uMouseX;
	
	// frame buffer object
	private int fbo;
	private int colorTexture, normalTexture;
	private int depthTexture;
	
	// transformations
	private Matrix4f model;
	private Matrix4f view;
	private Matrix4f projection, invProjection;
	
	@Override
	public void setUp() {
		/*Random randd = new Random(42);
		for (int i = 0; i < 32; ++i) {
			float x = randd.nextFloat()*2.0f-1.0f;
			float y = randd.nextFloat();
			float z = randd.nextFloat()*2.0f-1.0f;
			float m = randd.nextFloat();
			float l = (float)Math.sqrt(x*x+y*y+z*z);
			x *= m/l;
			y *= m/l;
			z *= m/l;
			System.out.println("+ \"vec3("+x+","+y+","+z+"),\\n\"");
		}*/
		// create cube vbo
		float[] dataCube = new float[] {
			-64, -1, -64, 	0, 1, 0,
			+64, -1, -64, 	0, 1, 0,
			+64, -1, +64, 	0, 1, 0,
			-64, -1, -64, 	0, 1, 0,
			+64, -1, +64, 	0, 1, 0,
			-64, -1, +64, 	0, 1, 0,
			
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
			
			2.25f + -1, -1, -1, 	0, 0, -1,
			2.25f + +1, -1, -1, 	0, 0, -1,
			2.25f + +1, +1, -1, 	0, 0, -1,
			2.25f + -1, -1, -1, 	0, 0, -1,
			2.25f + +1, +1, -1, 	0, 0, -1,
			2.25f + -1, +1, -1, 	0, 0, -1,
			2.25f + -1, -1, +1, 	0, 0, +1,
			2.25f + +1, -1, +1, 	0, 0, +1,
			2.25f + +1, +1, +1, 	0, 0, +1,
			2.25f + -1, -1, +1, 	0, 0, +1,
			2.25f + +1, +1, +1, 	0, 0, +1,
			2.25f + -1, +1, +1, 	0, 0, +1,
			2.25f + -1, -1, -1, 	-1, 0, 0,
			2.25f + -1, +1, -1, 	-1, 0, 0,
			2.25f + -1, +1, +1, 	-1, 0, 0,
			2.25f + -1, -1, -1, 	-1, 0, 0,
			2.25f + -1, +1, +1, 	-1, 0, 0,
			2.25f + -1, -1, +1, 	-1, 0, 0,
			2.25f + +1, -1, -1, 	+1, 0, 0,
			2.25f + +1, +1, -1, 	+1, 0, 0,
			2.25f + +1, +1, +1, 	+1, 0, 0,
			2.25f + +1, -1, -1, 	+1, 0, 0,
			2.25f + +1, +1, +1, 	+1, 0, 0,
			2.25f + +1, -1, +1, 	+1, 0, 0,
			2.25f + -1, -1, -1, 	0, -1, 0,
			2.25f + +1, -1, -1, 	0, -1, 0,
			2.25f + +1, -1, +1, 	0, -1, 0,
			2.25f + -1, -1, -1, 	0, -1, 0,
			2.25f + +1, -1, +1, 	0, -1, 0,
			2.25f + -1, -1, +1, 	0, -1, 0,
			2.25f + -1, +1, -1, 	0, +1, 0,
			2.25f + +1, +1, -1, 	0, +1, 0,
			2.25f + +1, +1, +1, 	0, +1, 0,
			2.25f + -1, +1, -1, 	0, +1, 0,
			2.25f + +1, +1, +1, 	0, +1, 0,
			2.25f + -1, +1, +1, 	0, +1, 0,
			
			-1, -1, 2.25f+-1, 	0, 0, -1,
			+1, -1, 2.25f+-1, 	0, 0, -1,
			+1, +1, 2.25f+-1, 	0, 0, -1,
			-1, -1, 2.25f+-1, 	0, 0, -1,
			+1, +1, 2.25f+-1, 	0, 0, -1,
			-1, +1, 2.25f+-1, 	0, 0, -1,
			-1, -1, 2.25f+ +1, 	0, 0, +1,
			+1, -1, 2.25f+ +1, 	0, 0, +1,
			+1, +1, 2.25f+ +1, 	0, 0, +1,
			-1, -1, 2.25f+ +1, 	0, 0, +1,
			+1, +1, 2.25f+ +1, 	0, 0, +1,
			-1, +1, 2.25f+ +1, 	0, 0, +1,
			-1, -1, 2.25f+-1, 	-1, 0, 0,
			-1, +1, 2.25f+-1, 	-1, 0, 0,
			-1, +1, 2.25f+ +1, 	-1, 0, 0,
			-1, -1, 2.25f+ -1, 	-1, 0, 0,
			-1, +1, 2.25f+ +1, 	-1, 0, 0,
			-1, -1, 2.25f+ +1, 	-1, 0, 0,
			+1, -1, 2.25f+ -1, 	+1, 0, 0,
			+1, +1, 2.25f+ -1, 	+1, 0, 0,
			+1, +1, 2.25f+ +1, 	+1, 0, 0,
			+1, -1, 2.25f+ -1, 	+1, 0, 0,
			+1, +1, 2.25f+ +1, 	+1, 0, 0,
			+1, -1, 2.25f+ +1, 	+1, 0, 0,
			-1, -1, 2.25f+ -1, 	0, -1, 0,
			+1, -1, 2.25f+ -1, 	0, -1, 0,
			+1, -1, 2.25f+ +1, 	0, -1, 0,
			-1, -1, 2.25f+ -1, 	0, -1, 0,
			+1, -1, 2.25f+ +1, 	0, -1, 0,
			-1, -1, 2.25f+ +1, 	0, -1, 0,
			-1, +1, 2.25f+ -1, 	0, +1, 0,
			+1, +1, 2.25f+ -1, 	0, +1, 0,
			+1, +1, 2.25f+ +1, 	0, +1, 0,
			-1, +1, 2.25f+ -1, 	0, +1, 0,
			+1, +1, 2.25f+ +1, 	0, +1, 0,
			-1, +1,2.25f+  +1, 	0, +1, 0,
			
			-1, 2.25f+ -1, -1, 	0, 0, -1,
			+1, 2.25f+ -1, -1, 	0, 0, -1,
			+1, 2.25f+ +1, -1, 	0, 0, -1,
			-1, 2.25f+ -1, -1, 	0, 0, -1,
			+1, 2.25f+ +1, -1, 	0, 0, -1,
			-1, 2.25f+ +1, -1, 	0, 0, -1,
			-1, 2.25f+ -1, +1, 	0, 0, +1,
			+1, 2.25f+ -1, +1, 	0, 0, +1,
			+1, 2.25f+ +1, +1, 	0, 0, +1,
			-1, 2.25f+ -1, +1, 	0, 0, +1,
			+1,2.25f+ +1, +1, 	0, 0, +1,
			-1, 2.25f+ +1, +1, 	0, 0, +1,
			-1, 2.25f+ -1, -1, 	-1, 0, 0,
			-1, 2.25f+ +1, -1, 	-1, 0, 0,
			-1, 2.25f+ +1, +1, 	-1, 0, 0,
			-1, 2.25f+ -1, -1, 	-1, 0, 0,
			-1, 2.25f+ +1, +1, 	-1, 0, 0,
			-1, 2.25f+ -1, +1, 	-1, 0, 0,
			+1, 2.25f+ -1, -1, 	+1, 0, 0,
			+1, 2.25f+ +1, -1, 	+1, 0, 0,
			+1, 2.25f+ +1, +1, 	+1, 0, 0,
			+1, 2.25f+ -1, -1, 	+1, 0, 0,
			+1, 2.25f+ +1, +1, 	+1, 0, 0,
			+1, 2.25f+ -1, +1, 	+1, 0, 0,
			-1, 2.25f+ -1, -1, 	0, -1, 0,
			+1, 2.25f+ -1, -1, 	0, -1, 0,
			+1, 2.25f+ -1, +1, 	0, -1, 0,
			-1, 2.25f+ -1, -1, 	0, -1, 0,
			+1, 2.25f+ -1, +1, 	0, -1, 0,
			-1, 2.25f+ -1, +1, 	0, -1, 0,
			-1, 2.25f+ +1, -1, 	0, +1, 0,
			+1, 2.25f+ +1, -1, 	0, +1, 0,
			+1, 2.25f+ +1, +1, 	0, +1, 0,
			-1, 2.25f+ +1, -1, 	0, +1, 0,
			+1, 2.25f+ +1, +1, 	0, +1, 0,
			-1, 2.25f+ +1, +1, 	0, +1, 0,
			
			-1, 2.25f+ -1, 2.25f+ -1, 	0, 0, -1,
			+1, 2.25f+ -1, 2.25f+ -1, 	0, 0, -1,
			+1, 2.25f+ +1, 2.25f+ -1, 	0, 0, -1,
			-1, 2.25f+ -1, 2.25f+ -1, 	0, 0, -1,
			+1, 2.25f+ +1, 2.25f+ -1, 	0, 0, -1,
			-1, 2.25f+ +1, 2.25f+ -1, 	0, 0, -1,
			-1, 2.25f+ -1, 2.25f+ +1, 	0, 0, +1,
			+1, 2.25f+ -1, 2.25f+ +1, 	0, 0, +1,
			+1, 2.25f+ +1, 2.25f+ +1, 	0, 0, +1,
			-1, 2.25f+ -1, 2.25f+ +1, 	0, 0, +1,
			+1,2.25f+ +1, 2.25f+ +1, 	0, 0, +1,
			-1, 2.25f+ +1, 2.25f+ +1, 	0, 0, +1,
			-1, 2.25f+ -1, 2.25f+ -1, 	-1, 0, 0,
			-1, 2.25f+ +1, 2.25f+ -1, 	-1, 0, 0,
			-1, 2.25f+ +1, 2.25f+ +1, 	-1, 0, 0,
			-1, 2.25f+ -1, 2.25f+ -1, 	-1, 0, 0,
			-1, 2.25f+ +1, 2.25f+ +1, 	-1, 0, 0,
			-1, 2.25f+ -1, 2.25f+ +1, 	-1, 0, 0,
			+1, 2.25f+ -1, 2.25f+ -1, 	+1, 0, 0,
			+1, 2.25f+ +1, 2.25f+ -1, 	+1, 0, 0,
			+1, 2.25f+ +1, 2.25f+ +1, 	+1, 0, 0,
			+1, 2.25f+ -1, 2.25f+ -1, 	+1, 0, 0,
			+1, 2.25f+ +1, 2.25f+ +1, 	+1, 0, 0,
			+1, 2.25f+ -1, 2.25f+ +1, 	+1, 0, 0,
			-1, 2.25f+ -1, 2.25f+ -1, 	0, -1, 0,
			+1, 2.25f+ -1, 2.25f+ -1, 	0, -1, 0,
			+1, 2.25f+ -1, 2.25f+ +1, 	0, -1, 0,
			-1, 2.25f+ -1, 2.25f+ -1, 	0, -1, 0,
			+1, 2.25f+ -1, 2.25f+ +1, 	0, -1, 0,
			-1, 2.25f+ -1, 2.25f+ +1, 	0, -1, 0,
			-1, 2.25f+ +1, 2.25f+ -1, 	0, +1, 0,
			+1, 2.25f+ +1, 2.25f+ -1, 	0, +1, 0,
			+1, 2.25f+ +1, 2.25f+ +1, 	0, +1, 0,
			-1, 2.25f+ +1, 2.25f+ -1, 	0, +1, 0,
			+1, 2.25f+ +1, 2.25f+ +1, 	0, +1, 0,
			-1, 2.25f+ +1, 2.25f+ +1, 	0, +1, 0,
		};
		FloatBuffer vertexDataCube = BufferUtils.createFloatBuffer(dataCube.length);
		vertexDataCube.put(dataCube);
		vertexDataCube.position(0);
		vboCube = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboCube);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexDataCube, GL15.GL_STATIC_DRAW);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

		// create full quad vbo
		float[] dataSSAO = new float[] {
			-1, -1,
			+1, -1,
			+1, +1,
			-1, -1,
			+1, +1,
			-1, +1
		};
		FloatBuffer vertexDataBlur = BufferUtils.createFloatBuffer(dataSSAO.length);
		vertexDataBlur.put(dataSSAO);
		vertexDataBlur.position(0);
		vboSSAO = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboSSAO);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexDataBlur, GL15.GL_STATIC_DRAW);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

		// create cube shader
		shaderCube = createShader(vertexShaderCube, fragmentShaderCube);
		uMvp = GL20.glGetUniformLocation(shaderCube.id, "u_mvp");
		uMv = GL20.glGetUniformLocation(shaderCube.id, "u_mv");
		
		// create final shader
		shaderFinal = createShader(vertexShaderSSAO, fragmentShaderSSAO);
		uTextureFinal = GL20.glGetUniformLocation(shaderFinal.id, "u_texture");
		uDepthFinal = GL20.glGetUniformLocation(shaderFinal.id, "u_depth");
		uNormalFinal = GL20.glGetUniformLocation(shaderFinal.id, "u_normal");
		uRandomFinal = GL20.glGetUniformLocation(shaderFinal.id, "u_random");
		uResolutionFinal = GL20.glGetUniformLocation(shaderFinal.id, "u_resolution");
		uProjectionFinal = GL20.glGetUniformLocation(shaderFinal.id, "u_projection");
		uInvProjection = GL20.glGetUniformLocation(shaderFinal.id, "u_invProjection");
		uRadius = GL20.glGetUniformLocation(shaderFinal.id, "u_radius");
		uMaxSamples = GL20.glGetUniformLocation(shaderFinal.id, "u_maxSamples");
		uMouseX = GL20.glGetUniformLocation(shaderFinal.id, "u_mouseX");
		try {
			// load random texture
			PNGDecoder rand = new PNGDecoder(getClass().getResourceAsStream("SSAO_random.png"));
			ByteBuffer randData = BufferUtils.createByteBuffer(64*64*4);
			rand.decodeFlipped(randData, 64*4, Format.RGBA);
			randData.position(0);
			randomTexture = createTexture(64, 64, GL11.GL_RGBA, randData);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// create frame buffer
		fbo = GL30.glGenFramebuffers();
		colorTexture = createTexture(Main.WIDTH, Main.HEIGHT, GL11.GL_RGB, null);
		normalTexture = createTexture(Main.WIDTH, Main.HEIGHT, GL11.GL_RGB, null);
		depthTexture = createTexture(Main.WIDTH, Main.HEIGHT, GL11.GL_DEPTH_COMPONENT, null);
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
		GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTexture, 0);
		GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1, GL11.GL_TEXTURE_2D, normalTexture, 0);
		GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTexture, 0);
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		
		// create matrices
		model = new Matrix4f();
		view = new Matrix4f();
		projection = MatrixUtils.perspective(3.141592f/3, 4/3f, 0.1f, 100f);
		invProjection = new Matrix4f(projection);
		invProjection.invert();
	}
	
	// transformation utilities
	private FloatBuffer matBuffer = BufferUtils.createFloatBuffer(16);
	private float t = 0;
	
	@Override
	public void update() {
		cubePass();
		SSAOPass();
	}
	
	public void SSAOPass () {
		GL11.glDisable(GL11.GL_DEPTH_TEST);
				
		/*
		 * render final image to window
		 */
		
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
		GL11.glClearColor(1, 1, 1, 1);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

		GL20.glUseProgram(shaderFinal.id);
		projection.store(matBuffer); matBuffer.position(0);
		GL20.glUniformMatrix4(uProjectionFinal, false, matBuffer);
		invProjection.store(matBuffer); matBuffer.position(0);
		GL20.glUniformMatrix4(uInvProjection, false, matBuffer);
		GL20.glUniform2f(uResolutionFinal, Main.WIDTH, Main.HEIGHT);
		GL20.glUniform1i(uTextureFinal, 0);
		GL20.glUniform1i(uDepthFinal, 1);
		GL20.glUniform1i(uNormalFinal, 2);
		GL20.glUniform1i(uRandomFinal, 3);
		GL20.glUniform1i(uMaxSamples, SAMPLES);
		GL20.glUniform1f(uRadius, RADIUS);
		GL20.glUniform1f(uMouseX, (float)Mouse.getX() / Main.WIDTH);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture);
		GL13.glActiveTexture(GL13.GL_TEXTURE1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
		GL13.glActiveTexture(GL13.GL_TEXTURE2);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, normalTexture);
		GL13.glActiveTexture(GL13.GL_TEXTURE3);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, randomTexture);
		
		// render full screen quad
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboSSAO);
		GL20.glEnableVertexAttribArray(0);	// position
		GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 2<<2, 0<<2);
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
	}
	
	public void cubePass () {
		t += 0.01f;
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
		IntBuffer colors = BufferUtils.createIntBuffer(2);
		colors.put(GL30.GL_COLOR_ATTACHMENT0).put(GL30.GL_COLOR_ATTACHMENT1);
		colors.position(0);
		GL20.glDrawBuffers(colors);
		
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glClearColor(1, 1, 1, 1);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
	
		// compute transforms
		model.setIdentity();
		view.setIdentity();
		view.rotate(3.1415f/5, new Vector3f(1,0,0));
		view.rotate(-3.1415f/4, new Vector3f(0,1,0));
		view.translate(new Vector3f(-5, -5, -5));
		model.rotate(t, new Vector3f(0,1,0));
		Matrix4f mv = new Matrix4f();
		Matrix4f mvp = new Matrix4f();
		Matrix4f.mul(view, model, mv);
		Matrix4f.mul(projection, mv, mvp);
		
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
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 42+36+36+36+36);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		
		GL20.glUseProgram(0);
	}
	
	public int createTexture (int width, int height, int format, ByteBuffer data) {
		int id = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, width, height, 0, format, GL11.GL_UNSIGNED_BYTE, data);
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
		GL15.glDeleteBuffers(vboCube);
		GL15.glDeleteBuffers(vboSSAO);
		GL30.glDeleteFramebuffers(fbo);
		GL11.glDeleteTextures(colorTexture);
		GL11.glDeleteTextures(depthTexture);
	}

}
