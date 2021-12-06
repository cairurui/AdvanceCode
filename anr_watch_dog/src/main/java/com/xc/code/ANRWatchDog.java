package com.xc.code;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.List;

/**
 * anr 异常信息监控
 */
public class ANRWatchDog implements Runnable {
    /**
     * anr 检测间隔时间
     */
    private static final long CHECK_INTERVAL = 5000L;
    // anr Handler
    private Handler mAnrHandler;
    private HandlerChecker mHandlerChecker;
    private static final String TAG = "ANRWatchDog";
    private Context mContext;

    public ANRWatchDog(Context context) {
        // 获取一些线程的 Looper ，HandlerTread
        mAnrHandler = new Handler(ThreadManager.getAnrWatchDogLooper());
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mHandlerChecker = new HandlerChecker(mainHandler);
        this.mContext = context.getApplicationContext();
    }

    public void start() {
        mAnrHandler.post(this);
    }

    @Override
    public void run() {
        synchronized (this) {
            // 不断的往主线程中扔消息，每隔 5s 扔一个
            mHandlerChecker.scheduleCheckLocked();

            // 确保等待 5s 检测一次
            long start = SystemClock.uptimeMillis();
            long timeout = CHECK_INTERVAL;
            while (timeout > 0) {
                try {
                    wait(timeout);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Log.wtf(TAG, e);
                }
                timeout = CHECK_INTERVAL - (SystemClock.uptimeMillis() - start);
            }

            boolean isBlocked = mHandlerChecker.isBlocked();
            if (isBlocked) {
                Log.e(TAG, "可能存在 ANR -> " + isBlocked);
                // 找是不是有 ANR
                mHandlerChecker.restoreBlocked();
                doubtANR();
            }

            // 进入下一个轮询
            mAnrHandler.post(this);
        }
    }

    /**
     * 怀疑有 ANR
     */
    private void doubtANR() {
        ThreadManager.run(new Runnable() {
            @Override
            public void run() {
                ActivityManager.ProcessErrorStateInfo processErrorStateInfo = getErrorInfo(mContext);
                if (processErrorStateInfo != null) {
                    // 有 ANR 了
                    RuntimeException e = new RuntimeException(processErrorStateInfo.shortMsg);
                    // 先思考一下，这样拿堆栈是不是一定是对的
                    // 有一个弊端会导致误判 95% ，获取线程的一些信息，锁的信息，cpu 的信息.
                    // 因为ANR可能是前面几个方法耗时导致的，最好获取的时候可能已经错过了那个方法了。可以使用ASM插桩的形式，手机每个方法的耗时。
                    e.setStackTrace(mHandlerChecker.getThread().getStackTrace());
                    Log.e(TAG, "run: ANR 信息：", e);
                }
            }
        });
    }

    /**
     * 获取 ANR 的错误信息
     */
    private ActivityManager.ProcessErrorStateInfo getErrorInfo(Context context) {
        // 如果获取 /data/anr/traces.txt ，要 root 权限，低版本可以，高版本不行，不用这个方案。
        // 下面从 AMS 中获取ANR信息
        try {
            final long sleepTime = 500L; // 每次轮训等待的时间
            final long loop = 20; // 轮训次数
            long times = 0;
            do {
                // 多执行几次,确保不漏掉
                ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.ProcessErrorStateInfo> errorList = activityManager.getProcessesInErrorState();
                if (errorList != null) {
                    for (ActivityManager.ProcessErrorStateInfo processErrorStateInfo : errorList) {
                        if (processErrorStateInfo.condition == ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING) {
                            // 有 ANR 信息
                            return processErrorStateInfo;
                        }
                    }
                }
                Thread.sleep(sleepTime);
            } while (times++ < loop);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 监控 Handler
     */
    private static final class HandlerChecker implements Runnable {
        private Handler mHandler;
        private boolean mCompleted = true;

        public HandlerChecker(Handler handler) {
            this.mHandler = handler;
        }

        @Override
        public void run() {
            mCompleted = true;
        }

        public void scheduleCheckLocked() {
            if (!mCompleted) {
                return;
            }
            mCompleted = false;
            mHandler.postAtFrontOfQueue(this);
        }

        public boolean isBlocked() {
            return !mCompleted;
        }

        public void restoreBlocked() {
            mCompleted = true;
        }

        public Thread getThread() {
            return mHandler.getLooper().getThread();
        }
    }
}
