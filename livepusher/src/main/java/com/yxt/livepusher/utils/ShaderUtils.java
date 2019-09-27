package com.yxt.livepusher.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;

public class ShaderUtils {
    public static String getRawResource(Context context, int ids) throws IOException {
        InputStream is = context.getResources().openRawResource(ids);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuffer sb = new StringBuffer();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    private static int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader != 0) {
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compile = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compile, 0);
            if (compile[0] != GLES20.GL_TRUE) {
                GLES20.glDeleteShader(shader);
                shader = -1;
            }
            return shader;
        } else
            return -1;
    }

    public static int[] createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);

        if (vertexShader != 0 && fragmentShader != 0) {
            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            GLES20.glLinkProgram(program);
            return new int[]{vertexShader, fragmentShader, program};
        }

        return null;
    }


    public static Bitmap createTextImage(int textSize, String textColor, String bgColor, int padding, String speed, String vehicleLicence, String address, String time) {
        Paint paint = new Paint();
        paint.setColor(Color.parseColor(textColor));
        paint.setTextSize(textSize);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        paint.setStrokeWidth(1);


//        Paint paintBG = new Paint();
//        paintBG.setColor(0x4D000000);
//        paintBG.setAntiAlias(true);

        float width = paint.measureText("地址：" + address) < 310 ? 350 : (paint.measureText("地址：" + address) + 40);

        float top = paint.getFontMetrics().top;
        float bottom = paint.getFontMetrics().bottom;
        Bitmap bm = Bitmap.createBitmap(640, 70, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bm);
        canvas.drawColor(Color.parseColor("#00000000"));
//        canvas.drawRect(10, 80,
//                paint.measureText("地址：" + address) < 310
//                        ? 350 : (paint.measureText("地址：" + address) + 40)
//                , 10, paintBG);
        canvas.drawColor(Color.parseColor(bgColor));
        canvas.drawText("速度：" + speed + " km/h", 20, 30, paint);
        canvas.drawText("车牌号：" + vehicleLicence, 40 + paint.measureText("速度：" + speed + " km/h"), 30, paint);
        canvas.drawText("时间：" + time, 60 + paint.measureText("速度：" + speed + " km/h" + "车牌号：" + vehicleLicence), 30, paint);
        canvas.drawText("地址：" + address, 20, 50, paint);


        return bm;
    }

    public static int loadBitmapTexture(Bitmap bitmap) {
        int[] textureIds = new int[1];
        GLES20.glGenBuffers(1, textureIds, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        ByteBuffer bitmapBuffer = ByteBuffer.allocate(bitmap.getHeight() * bitmap.getWidth() * 4);
        bitmap.copyPixelsToBuffer(bitmapBuffer);
        bitmapBuffer.flip();
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bitmap.getWidth(),
                bitmap.getHeight(), 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bitmapBuffer);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return textureIds[0];


    }
}
