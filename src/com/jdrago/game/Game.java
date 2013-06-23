package com.jdrago.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class Game implements GLSurfaceView.Renderer
{
    private static String TAG = "RubikRenderer";

    private Context context_;
    private int width_;
    private int height_;

    private int shaderProgram_;
    private int viewProjMatrixHandle_;
    private int posHandle_;
    private int texHandle_;
    private int vertColorHandle_;

    private float[] viewProjMatrix_ = new float[16];
    private float[] projMatrix_ = new float[16];
    private float[] modelMatrix_ = new float[16];
    private float[] viewMatrix_ = new float[16];
    private float[] tempRotMatrix_ = new float[16];
    private float[] tempMatrix_ = new float[16];

    private final float[] cubieVertData_ = {
            // X, Y, Z, U, V
            -1.5f, -1.5f, -1.5f, 0, 0,
            -0.5f, -1.5f, -1.5f, 1, 0,
            -0.5f, -0.5f, -1.5f, 1, 1,
            -1.5f, -0.5f, -1.5f, 0, 1};
    private FloatBuffer cubieVerts_;

    private final float[] buttonVertData_ = {
            // X, Y, Z, U, V
            0, 0, 0, 0, 0,
            1, 0, 0, 1, 0,
            1, 1, 0, 1, 1,
            0, 1, 0, 0, 1};
    private FloatBuffer buttonVerts_;

    private final int[] quadIndicesData_ = {0, 1, 2, 2, 3, 0};
    private IntBuffer quadIndices_;

    private final String vertShader_ =
            "uniform mat4 uMVPMatrix;\n" +
                    "attribute vec4 aPosition;\n" +
                    "attribute vec2 aTextureCoord;\n" +
                    "uniform vec4 u_color;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "void main() {\n" +
                    "  gl_Position = uMVPMatrix * aPosition;\n" +
                    "  vTextureCoord = aTextureCoord;\n" +
                    "}\n";

    private final String fragShader_ =
            "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform sampler2D sTexture;\n" +
                    "uniform vec4 u_color;\n" +
                    "void main() {\n" +
                    "vec4 t = texture2D(sTexture, vTextureCoord);" +
                    "gl_FragColor.rgba = u_color.rgba * t.rgba;\n" +
                    "}\n";

    private int loadShader(int shaderType, String source)
    {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader != 0)
        {
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0)
            {
                Log.e(TAG, "Could not compile shader " + shaderType + ":");
                Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
        }
        return shader;
    }

    private int createProgram(String vertexSource, String fragmentSource)
    {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0)
        {
            return 0;
        }

        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (pixelShader == 0)
        {
            return 0;
        }

        int program = GLES20.glCreateProgram();
        if (program != 0)
        {
            GLES20.glAttachShader(program, vertexShader);
            checkGlError("glAttachShader");
            GLES20.glAttachShader(program, pixelShader);
            checkGlError("glAttachShader");
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE)
            {
                Log.e(TAG, "Could not link program: ");
                Log.e(TAG, GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }
        return program;
    }

    private void checkGlError(String op)
    {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR)
        {
            Log.e(TAG, op + ": glError " + error);
            throw new RuntimeException(op + ": glError " + error);
        }
    }

    public int loadPNG(int res)
    {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);

        int id = textures[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id);

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);

        InputStream is = context_.getResources().openRawResource(res);
        Bitmap bitmap;
        try
        {
            bitmap = BitmapFactory.decodeStream(is);
        } finally
        {
            try
            {
                is.close();
            } catch (IOException e)
            {
                // Ignore.
            }
        }

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        bitmap.recycle();
        return id;
    }

    public Game(Context context)
    {
        context_ = context;
    }

    public void onSurfaceCreated(GL10 glUnused, EGLConfig config)
    {
        // Ignore the passed-in GL10 interface, and use the GLES20
        // class's static methods instead.
        shaderProgram_ = createProgram(vertShader_, fragShader_);
        if (shaderProgram_ == 0)
        {
            return;
        }
        posHandle_ = GLES20.glGetAttribLocation(shaderProgram_, "aPosition");
        checkGlError("glGetAttribLocation aPosition");
        if (posHandle_ == -1)
        {
            throw new RuntimeException("Could not get attrib location for aPosition");
        }
        texHandle_ = GLES20.glGetAttribLocation(shaderProgram_, "aTextureCoord");
        checkGlError("glGetAttribLocation aTextureCoord");
        if (texHandle_ == -1)
        {
            throw new RuntimeException("Could not get attrib location for aTextureCoord");
        }

        viewProjMatrixHandle_ = GLES20.glGetUniformLocation(shaderProgram_, "uMVPMatrix");
        checkGlError("glGetUniformLocation uMVPMatrix");
        if (viewProjMatrixHandle_ == -1)
        {
            throw new RuntimeException("Could not get attrib location for uMVPMatrix");
        }

        vertColorHandle_ = GLES20.glGetUniformLocation(shaderProgram_, "u_color");
        checkGlError("glGetUniformLocation vertColorHandle");
        if (vertColorHandle_ == -1)
        {
            throw new RuntimeException("Could not get attrib location for vertColorHandle");
        }

//        cubieID_ = loadPNG(R.raw.cubie);
    }

    public void onSurfaceChanged(GL10 glUnused, int width, int height)
    {
        width_ = width;
        height_ = height;

        // Ignore the passed-in GL10 interface, and use the GLES20
        // class's static methods instead.
        GLES20.glViewport(0, 0, width, height);

        float ratio = (float) width / height;
        Matrix.frustumM(projMatrix_, 0, -ratio, ratio, 1, -1, 1, 20);

        //float left = 0.0f;
        //float right = width;
        //float bottom = height;
        //float top = 0.0f;
        //float near = 0.0f;
        //float far = 20.0f;
        //Matrix.orthoM(projMatrix_, 0, left, right, bottom, top, near, far);
    }

    public void onDrawFrame(GL10 glUnused)
    {
//        cube_.update();
//
//        spinX_ = lerp(spinX_, spinReqX_, SPIN_SPEED);
//        spinY_ = lerp(spinY_, spinReqY_, SPIN_SPEED);
//        spinZ_ = lerp(spinZ_, spinReqZ_, SPIN_SPEED);
//
        GLES20.glClearColor(0.1f, 0.1f, 0.9f, 1.0f);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(shaderProgram_);
        checkGlError("glUseProgram");
//
//        GLES20.glEnable(GLES20.GL_BLEND);
//        GLES20.glEnable(GLES20.GL_CULL_FACE);
//        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
//        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
//        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
//        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
//
//        // Setup a perspective for the top square of portrait layout
//        GLES20.glViewport(0, height_ - width_, width_, width_);
//        Matrix.frustumM(projMatrix_, 0, -1, 1, 1, -1, 1, 20);
//        Matrix.setLookAtM(viewMatrix_, 0,
//                0, 0, 1,         // eye
//                0f, 0f, 5f,       // center
//                0f, 1.0f, 0.0f);  // up
//
//        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cubieID_);
//        cubieVerts_.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
//        GLES20.glVertexAttribPointer(posHandle_, 3, GLES20.GL_FLOAT, false, TRIANGLE_VERTICES_DATA_STRIDE_BYTES, cubieVerts_);
//        checkGlError("glVertexAttribPointer maPosition");
//        cubieVerts_.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
//        GLES20.glEnableVertexAttribArray(posHandle_);
//        checkGlError("glEnableVertexAttribArray posHandle");
//        GLES20.glVertexAttribPointer(texHandle_, 2, GLES20.GL_FLOAT, false, TRIANGLE_VERTICES_DATA_STRIDE_BYTES, cubieVerts_);
//        checkGlError("glVertexAttribPointer texHandle");
//        GLES20.glEnableVertexAttribArray(texHandle_);
//        checkGlError("glEnableVertexAttribArray texHandle");
//
//        // Front
//        drawCubieFace( cube_.rots_[RubikCube.ROT_R],  cube_.rots_[RubikCube.ROT_U],  cube_.rots_[RubikCube.ROT_F], RubikCube.FACE_F, 0, 0, cube_.cubies_[RubikCube.FACE_F][2]);
//        drawCubieFace( cube_.rots_[RubikCube.ROT_X],  cube_.rots_[RubikCube.ROT_U],  cube_.rots_[RubikCube.ROT_F], RubikCube.FACE_F, 1, 0, cube_.cubies_[RubikCube.FACE_F][1]);
//        drawCubieFace(-cube_.rots_[RubikCube.ROT_L],  cube_.rots_[RubikCube.ROT_U],  cube_.rots_[RubikCube.ROT_F], RubikCube.FACE_F, 2, 0, cube_.cubies_[RubikCube.FACE_F][0]);
//
//        drawCubieFace( cube_.rots_[RubikCube.ROT_R],  cube_.rots_[RubikCube.ROT_Y],  cube_.rots_[RubikCube.ROT_F], RubikCube.FACE_F, 0, 1, cube_.cubies_[RubikCube.FACE_F][5]);
//        drawCubieFace( cube_.rots_[RubikCube.ROT_X],  cube_.rots_[RubikCube.ROT_Y],  cube_.rots_[RubikCube.ROT_F], RubikCube.FACE_F, 1, 1, cube_.cubies_[RubikCube.FACE_F][4]);
//        drawCubieFace(-cube_.rots_[RubikCube.ROT_L],  cube_.rots_[RubikCube.ROT_Y],  cube_.rots_[RubikCube.ROT_F], RubikCube.FACE_F, 2, 1, cube_.cubies_[RubikCube.FACE_F][3]);
//
//        drawCubieFace( cube_.rots_[RubikCube.ROT_R], -cube_.rots_[RubikCube.ROT_D],  cube_.rots_[RubikCube.ROT_F], RubikCube.FACE_F, 0, 2, cube_.cubies_[RubikCube.FACE_F][8]);
//        drawCubieFace( cube_.rots_[RubikCube.ROT_X], -cube_.rots_[RubikCube.ROT_D],  cube_.rots_[RubikCube.ROT_F], RubikCube.FACE_F, 1, 2, cube_.cubies_[RubikCube.FACE_F][7]);
//        drawCubieFace(-cube_.rots_[RubikCube.ROT_L], -cube_.rots_[RubikCube.ROT_D],  cube_.rots_[RubikCube.ROT_F], RubikCube.FACE_F, 2, 2, cube_.cubies_[RubikCube.FACE_F][6]);
//
//        // Back
//        drawCubieFace( cube_.rots_[RubikCube.ROT_R], -cube_.rots_[RubikCube.ROT_D], -cube_.rots_[RubikCube.ROT_B], RubikCube.FACE_B, 0, 0, cube_.cubies_[RubikCube.FACE_B][6]);
//        drawCubieFace( cube_.rots_[RubikCube.ROT_X], -cube_.rots_[RubikCube.ROT_D], -cube_.rots_[RubikCube.ROT_B], RubikCube.FACE_B, 1, 0, cube_.cubies_[RubikCube.FACE_B][7]);
//        drawCubieFace(-cube_.rots_[RubikCube.ROT_L], -cube_.rots_[RubikCube.ROT_D], -cube_.rots_[RubikCube.ROT_B], RubikCube.FACE_B, 2, 0, cube_.cubies_[RubikCube.FACE_B][8]);
//
//        drawCubieFace( cube_.rots_[RubikCube.ROT_R],  cube_.rots_[RubikCube.ROT_Y], -cube_.rots_[RubikCube.ROT_B], RubikCube.FACE_B, 0, 1, cube_.cubies_[RubikCube.FACE_B][3]);
//        drawCubieFace( cube_.rots_[RubikCube.ROT_X],  cube_.rots_[RubikCube.ROT_Y], -cube_.rots_[RubikCube.ROT_B], RubikCube.FACE_B, 1, 1, cube_.cubies_[RubikCube.FACE_B][4]);
//        drawCubieFace(-cube_.rots_[RubikCube.ROT_L],  cube_.rots_[RubikCube.ROT_Y], -cube_.rots_[RubikCube.ROT_B], RubikCube.FACE_B, 2, 1, cube_.cubies_[RubikCube.FACE_B][5]);
//
//        drawCubieFace( cube_.rots_[RubikCube.ROT_R],  cube_.rots_[RubikCube.ROT_U], -cube_.rots_[RubikCube.ROT_B], RubikCube.FACE_B, 0, 2, cube_.cubies_[RubikCube.FACE_B][0]);
//        drawCubieFace( cube_.rots_[RubikCube.ROT_X],  cube_.rots_[RubikCube.ROT_U], -cube_.rots_[RubikCube.ROT_B], RubikCube.FACE_B, 1, 2, cube_.cubies_[RubikCube.FACE_B][1]);
//        drawCubieFace(-cube_.rots_[RubikCube.ROT_L],  cube_.rots_[RubikCube.ROT_U], -cube_.rots_[RubikCube.ROT_B], RubikCube.FACE_B, 2, 2, cube_.cubies_[RubikCube.FACE_B][2]);
//
//        // Up
//        drawCubieFace( cube_.rots_[RubikCube.ROT_R],  cube_.rots_[RubikCube.ROT_U], -cube_.rots_[RubikCube.ROT_B], RubikCube.FACE_U, 0, 0, cube_.cubies_[RubikCube.FACE_U][2]);
//        drawCubieFace( cube_.rots_[RubikCube.ROT_X],  cube_.rots_[RubikCube.ROT_U], -cube_.rots_[RubikCube.ROT_B], RubikCube.FACE_U, 1, 0, cube_.cubies_[RubikCube.FACE_U][1]);
//        drawCubieFace(-cube_.rots_[RubikCube.ROT_L],  cube_.rots_[RubikCube.ROT_U], -cube_.rots_[RubikCube.ROT_B], RubikCube.FACE_U, 2, 0, cube_.cubies_[RubikCube.FACE_U][0]);
//
//        drawCubieFace( cube_.rots_[RubikCube.ROT_R],  cube_.rots_[RubikCube.ROT_U],  cube_.rots_[RubikCube.ROT_Z], RubikCube.FACE_U, 0, 1, cube_.cubies_[RubikCube.FACE_U][5]);
//        drawCubieFace( cube_.rots_[RubikCube.ROT_X],  cube_.rots_[RubikCube.ROT_U],  cube_.rots_[RubikCube.ROT_Z], RubikCube.FACE_U, 1, 1, cube_.cubies_[RubikCube.FACE_U][4]);
//        drawCubieFace(-cube_.rots_[RubikCube.ROT_L],  cube_.rots_[RubikCube.ROT_U],  cube_.rots_[RubikCube.ROT_Z], RubikCube.FACE_U, 2, 1, cube_.cubies_[RubikCube.FACE_U][3]);
//
//        drawCubieFace( cube_.rots_[RubikCube.ROT_R],  cube_.rots_[RubikCube.ROT_U],  cube_.rots_[RubikCube.ROT_F], RubikCube.FACE_U, 0, 2, cube_.cubies_[RubikCube.FACE_U][8]);
//        drawCubieFace( cube_.rots_[RubikCube.ROT_X],  cube_.rots_[RubikCube.ROT_U],  cube_.rots_[RubikCube.ROT_F], RubikCube.FACE_U, 1, 2, cube_.cubies_[RubikCube.FACE_U][7]);
//        drawCubieFace(-cube_.rots_[RubikCube.ROT_L],  cube_.rots_[RubikCube.ROT_U],  cube_.rots_[RubikCube.ROT_F], RubikCube.FACE_U, 2, 2, cube_.cubies_[RubikCube.FACE_U][6]);
//
//        // Down
//        drawCubieFace( cube_.rots_[RubikCube.ROT_R], -cube_.rots_[RubikCube.ROT_D],  cube_.rots_[RubikCube.ROT_F], RubikCube.FACE_D, 0, 0, cube_.cubies_[RubikCube.FACE_D][2]);
//        drawCubieFace( cube_.rots_[RubikCube.ROT_X], -cube_.rots_[RubikCube.ROT_D],  cube_.rots_[RubikCube.ROT_F], RubikCube.FACE_D, 1, 0, cube_.cubies_[RubikCube.FACE_D][1]);
//        drawCubieFace(-cube_.rots_[RubikCube.ROT_L], -cube_.rots_[RubikCube.ROT_D],  cube_.rots_[RubikCube.ROT_F], RubikCube.FACE_D, 2, 0, cube_.cubies_[RubikCube.FACE_D][0]);
//
//        drawCubieFace( cube_.rots_[RubikCube.ROT_R], -cube_.rots_[RubikCube.ROT_D],  cube_.rots_[RubikCube.ROT_Z], RubikCube.FACE_D, 0, 1, cube_.cubies_[RubikCube.FACE_D][5]);
//        drawCubieFace( cube_.rots_[RubikCube.ROT_X], -cube_.rots_[RubikCube.ROT_D],  cube_.rots_[RubikCube.ROT_Z], RubikCube.FACE_D, 1, 1, cube_.cubies_[RubikCube.FACE_D][4]);
//        drawCubieFace(-cube_.rots_[RubikCube.ROT_L], -cube_.rots_[RubikCube.ROT_D],  cube_.rots_[RubikCube.ROT_Z], RubikCube.FACE_D, 2, 1, cube_.cubies_[RubikCube.FACE_D][3]);
//
//        drawCubieFace( cube_.rots_[RubikCube.ROT_R], -cube_.rots_[RubikCube.ROT_D], -cube_.rots_[RubikCube.ROT_B], RubikCube.FACE_D, 0, 2, cube_.cubies_[RubikCube.FACE_D][8]);
//        drawCubieFace( cube_.rots_[RubikCube.ROT_X], -cube_.rots_[RubikCube.ROT_D], -cube_.rots_[RubikCube.ROT_B], RubikCube.FACE_D, 1, 2, cube_.cubies_[RubikCube.FACE_D][7]);
//        drawCubieFace(-cube_.rots_[RubikCube.ROT_L], -cube_.rots_[RubikCube.ROT_D], -cube_.rots_[RubikCube.ROT_B], RubikCube.FACE_D, 2, 2, cube_.cubies_[RubikCube.FACE_D][6]);
//
//        // Left
//        drawCubieFace(-cube_.rots_[RubikCube.ROT_L],  cube_.rots_[RubikCube.ROT_U],  cube_.rots_[RubikCube.ROT_F], RubikCube.FACE_L, 0, 0, cube_.cubies_[RubikCube.FACE_L][2]);
//        drawCubieFace(-cube_.rots_[RubikCube.ROT_L],  cube_.rots_[RubikCube.ROT_U],  cube_.rots_[RubikCube.ROT_Z], RubikCube.FACE_L, 1, 0, cube_.cubies_[RubikCube.FACE_L][1]);
//        drawCubieFace(-cube_.rots_[RubikCube.ROT_L],  cube_.rots_[RubikCube.ROT_U], -cube_.rots_[RubikCube.ROT_B], RubikCube.FACE_L, 2, 0, cube_.cubies_[RubikCube.FACE_L][0]);
//
//        drawCubieFace(-cube_.rots_[RubikCube.ROT_L],  cube_.rots_[RubikCube.ROT_Y],  cube_.rots_[RubikCube.ROT_F], RubikCube.FACE_L, 0, 1, cube_.cubies_[RubikCube.FACE_L][5]);
//        drawCubieFace(-cube_.rots_[RubikCube.ROT_L],  cube_.rots_[RubikCube.ROT_Y],  cube_.rots_[RubikCube.ROT_Z], RubikCube.FACE_L, 1, 1, cube_.cubies_[RubikCube.FACE_L][4]);
//        drawCubieFace(-cube_.rots_[RubikCube.ROT_L],  cube_.rots_[RubikCube.ROT_Y], -cube_.rots_[RubikCube.ROT_B], RubikCube.FACE_L, 2, 1, cube_.cubies_[RubikCube.FACE_L][3]);
//
//        drawCubieFace(-cube_.rots_[RubikCube.ROT_L], -cube_.rots_[RubikCube.ROT_D],  cube_.rots_[RubikCube.ROT_F], RubikCube.FACE_L, 0, 2, cube_.cubies_[RubikCube.FACE_L][8]);
//        drawCubieFace(-cube_.rots_[RubikCube.ROT_L], -cube_.rots_[RubikCube.ROT_D],  cube_.rots_[RubikCube.ROT_Z], RubikCube.FACE_L, 1, 2, cube_.cubies_[RubikCube.FACE_L][7]);
//        drawCubieFace(-cube_.rots_[RubikCube.ROT_L], -cube_.rots_[RubikCube.ROT_D], -cube_.rots_[RubikCube.ROT_B], RubikCube.FACE_L, 2, 2, cube_.cubies_[RubikCube.FACE_L][6]);
//
//        // Right
//        drawCubieFace( cube_.rots_[RubikCube.ROT_R],  cube_.rots_[RubikCube.ROT_U], -cube_.rots_[RubikCube.ROT_B], RubikCube.FACE_R, 0, 0, cube_.cubies_[RubikCube.FACE_R][2]);
//        drawCubieFace( cube_.rots_[RubikCube.ROT_R],  cube_.rots_[RubikCube.ROT_U],  cube_.rots_[RubikCube.ROT_Z], RubikCube.FACE_R, 1, 0, cube_.cubies_[RubikCube.FACE_R][1]);
//        drawCubieFace( cube_.rots_[RubikCube.ROT_R],  cube_.rots_[RubikCube.ROT_U],  cube_.rots_[RubikCube.ROT_F], RubikCube.FACE_R, 2, 0, cube_.cubies_[RubikCube.FACE_R][0]);
//
//        drawCubieFace( cube_.rots_[RubikCube.ROT_R],  cube_.rots_[RubikCube.ROT_Y], -cube_.rots_[RubikCube.ROT_B], RubikCube.FACE_R, 0, 1, cube_.cubies_[RubikCube.FACE_R][5]);
//        drawCubieFace( cube_.rots_[RubikCube.ROT_R],  cube_.rots_[RubikCube.ROT_Y],  cube_.rots_[RubikCube.ROT_Z], RubikCube.FACE_R, 1, 1, cube_.cubies_[RubikCube.FACE_R][4]);
//        drawCubieFace( cube_.rots_[RubikCube.ROT_R],  cube_.rots_[RubikCube.ROT_Y],  cube_.rots_[RubikCube.ROT_F], RubikCube.FACE_R, 2, 1, cube_.cubies_[RubikCube.FACE_R][3]);
//
//        drawCubieFace( cube_.rots_[RubikCube.ROT_R], -cube_.rots_[RubikCube.ROT_D], -cube_.rots_[RubikCube.ROT_B], RubikCube.FACE_R, 0, 2, cube_.cubies_[RubikCube.FACE_R][8]);
//        drawCubieFace( cube_.rots_[RubikCube.ROT_R], -cube_.rots_[RubikCube.ROT_D],  cube_.rots_[RubikCube.ROT_Z], RubikCube.FACE_R, 1, 2, cube_.cubies_[RubikCube.FACE_R][7]);
//        drawCubieFace( cube_.rots_[RubikCube.ROT_R], -cube_.rots_[RubikCube.ROT_D],  cube_.rots_[RubikCube.ROT_F], RubikCube.FACE_R, 2, 2, cube_.cubies_[RubikCube.FACE_R][6]);
//
//        // Setup ortho for the UI
//        GLES20.glViewport(0, 0, width_, height_);
//        GLES20.glDisable(GLES20.GL_CULL_FACE);
//        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
//
//        float left = 0.0f;
//        float right = width_;
//        float bottom = height_;
//        float top = 0.0f;
//        float near = 0.0f;
//        float far = 20.0f;
//        Matrix.setIdentityM(projMatrix_, 0);
//        Matrix.orthoM(projMatrix_, 0, left, right, bottom, top, near, far);
//        Matrix.setLookAtM(viewMatrix_, 0,
//                0, 0, 10,         // eye
//                0f, 0f, 0f,       // center
//                0f, 1.0f, 0.0f);  // up
//
//        buttonVerts_.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
//        GLES20.glVertexAttribPointer(posHandle_, 3, GLES20.GL_FLOAT, false, TRIANGLE_VERTICES_DATA_STRIDE_BYTES, buttonVerts_);
//        checkGlError("glVertexAttribPointer maPosition");
//        buttonVerts_.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
//        GLES20.glEnableVertexAttribArray(posHandle_);
//        checkGlError("glEnableVertexAttribArray posHandle");
//        GLES20.glVertexAttribPointer(texHandle_, 2, GLES20.GL_FLOAT, false, TRIANGLE_VERTICES_DATA_STRIDE_BYTES, buttonVerts_);
//        checkGlError("glVertexAttribPointer texHandle");
//        GLES20.glEnableVertexAttribArray(texHandle_);
//        checkGlError("glEnableVertexAttribArray texHandle");


//            Matrix.setIdentityM(modelMatrix_, 0);
//            Matrix.translateM(modelMatrix_, 0, b.x_, b.y_, 0);
//            Matrix.scaleM(modelMatrix_, 0, b.width_, b.height_, 0);
//            Matrix.multiplyMM(tempMatrix_, 0, viewMatrix_, 0, modelMatrix_, 0);
//            Matrix.multiplyMM(viewProjMatrix_, 0, projMatrix_, 0, tempMatrix_, 0);
//            GLES20.glUniformMatrix4fv(viewProjMatrixHandle_, 1, false, viewProjMatrix_, 0);
//            GLES20.glUniform4f(vertColorHandle_, animVal, 1.0f, animVal, 1.0f);
//            GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_INT, quadIndices_);
//            checkGlError("glDrawElements");


    }

    public int width()
    {
        return width_;
    }

    public int height()
    {
        return height_;
    }

}
