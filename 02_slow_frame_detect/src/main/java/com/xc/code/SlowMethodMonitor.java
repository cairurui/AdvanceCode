package com.xc.code;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Printer;

/**
 * 慢函数的检测，实现的原理是基于 Looper 的 Printer
 */
public class SlowMethodMonitor {
    private static final long TIME_BLOCK = 200;
    private static final Printer PRINTER = new Printer() {
        long startTime = 0L;
        @Override
        public void println(String x) {
            if(x.startsWith(">>>>>")){
                startTime = System.currentTimeMillis();
                // QAPM 的方案，开一个线程，每隔 80ms 去获取一次主线的堆栈信息
                // 埋一个炸弹
                LogMonitor.getLogMonitor().startMonitor();
            }else if(x.startsWith("<<<<<")){
                long executeTime = System.currentTimeMillis() - startTime;
                if(executeTime > TIME_BLOCK){
                    Log.e("TAG","有耗时函数");
                    // 关键是怎么获取堆栈？思考想一想？
                    // 直接拿堆栈 ,不行，因为方法执行完了
                    // getStackInfo(Thread.currentThread());
                    // 直接从启动的线程里面去获取堆栈信息，这种应该是可以的
                }
                // 拆炸弹，这种方案也不是最好的
                LogMonitor.getLogMonitor().removeMonitor();
            }
        }
    };

    // 大家平时写代码的时候不要照着我这个写，主要是原理
    static class LogMonitor implements Runnable{
        private static final LogMonitor LOG_MONITOR = new LogMonitor();
        private HandlerThread mHandlerThread = new HandlerThread("log");
        private Handler mHandler ;

        public LogMonitor(){
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper());
        }

        public static LogMonitor getLogMonitor() {
            return LOG_MONITOR;
        }

        void startMonitor(){
            mHandler.postDelayed(this, TIME_BLOCK);
        }

        void removeMonitor(){
            mHandler.removeCallbacks(this);
        }

        @Override
        public void run() {
            getStackInfo(Looper.getMainLooper().getThread());
        }
    }

    private static String getStackInfo(Thread thread) {
        StackTraceElement[] stackTraceElements = thread.getStackTrace();
        for (StackTraceElement stackTraceElement : stackTraceElements) {
            Log.e("TAG", stackTraceElement.toString());
        }
        return "";
    }

    public void start() {
        Looper.getMainLooper().setMessageLogging(PRINTER);
    }
}
