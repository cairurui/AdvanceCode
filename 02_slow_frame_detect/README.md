## 慢函数检测

慢函数的检测，实现的原理是基于 Looper 的 Printer，在开始分发事件时埋炸弹，处理完事件时拆炸弹。
如果在指定时间内拆炸弹则会取主线程打印堆栈。设置的时间短可以检测出那些是耗时操作。

获取主线程堆栈方式：
StackTraceElement[] st = Looper.getMainLooper().getThread().getStackTrace();

```java
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
        private Handler mHandler = new Handler();

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
```

## 帧统计
在 ChoreographerMonitor 注册帧监听，计算每秒多少次即可。

```java 
public class ChoreographerMonitor {
    private long nowTime = 1;
    private int sm = 1;
    private int smResult = 60;

    public void start() {
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                Choreographer.getInstance().postFrameCallback(this);
                // 当前的帧率,如果有掉帧堆栈信息怎么拿？怎么解决掉帧问题
                // 这次课先不讲
                plusSM();
            }
        });
    }

    /**
     * 怎么计算当前的帧率
     */
    private void plusSM() {
        // 查考源码
        // 没超过一秒是不断 ++
        long t = System.currentTimeMillis();
        if (nowTime == 1) {
            nowTime = t;
        }

        if (nowTime / 1000 == t / 1000) {
            sm++;
        } else if (t / 1000 - nowTime / 1000 >= 1) {
            smResult = sm;
            Log.e("TAG","smResult -> "+smResult);
            sm = 1;
            nowTime = t;
        }
    }
}
```