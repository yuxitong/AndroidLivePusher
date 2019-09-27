package com.yxt.xiaozhenkeji.androidlivepusher;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.SurfaceView;
import android.widget.TextView;

import com.yxt.livepusher.test.CameraGLRender;
import com.yxt.livepusher.test.YxtStream;

public class MainActivity extends Activity {
    private static final int PERMISSIONS_REQUEST_CODE = 1;
    YxtStream yxt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //权限申请
        if (!allPermissionsGranted()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(getRequiredPermissions(), PERMISSIONS_REQUEST_CODE);
            }
            return;
        }


        //带预览摄像头
        yxt = new YxtStream((SurfaceView) findViewById(R.id.surfaceView), this);
        //不带预览摄像头
//        yxt = new YxtStream(this);

        //设置前后摄像头
        yxt.setCameraFacing(YxtStream.FACING_BACK);
        //设置帧率
        yxt.setFps(15);


//        yxt.setWidthAndHeight(480, 640);
        //开始执行
        yxt.star();

        //开始录制mp4
//        yxt.startRecord("录制地址+文件名");
        //停止录制
//        yxt.stopRecord();

        //开始推送rtmp
//        yxt.startRtmp(0,"rtmp地址");
        //停止推送rtmp
//        yxt.stopRtmpStream();


        //截图
//        yxt.getCameraRender().requestScreenShot(new CameraGLRender.ScreenShotListener() {
//            @Override
//            public void onBitmapAvailable(Bitmap bitmap) {
//
//            }
//        });


    }

    private String[] getRequiredPermissions() {
        Activity activity = this;
        try {
            PackageInfo info =
                    activity
                            .getPackageManager()
                            .getPackageInfo(activity.getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;
            if (ps != null && ps.length > 0) {
                return ps;
            } else {
                return new String[0];
            }
        } catch (Exception e) {
            return new String[0];
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        yxt.onDestory();
        super.onDestroy();

    }
}
