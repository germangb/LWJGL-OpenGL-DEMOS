package main;
import org.lwjgl.util.vector.Matrix4f;


public class MatrixUtils {

	public static Matrix4f perspective (float fov, float aspect, float near, float far) {
		Matrix4f proj = new Matrix4f();
		proj.setZero();
		proj.setZero();
		float cot = 1/(float)Math.tan(fov / 2);
		proj.m00 = cot/aspect;
		proj.m11 = cot;
		proj.m22 = (near+far)/(near-far);
		proj.m23 = -1;
		proj.m32 = (2*far*near)/(near-far);
		return proj;
	}
	
}
