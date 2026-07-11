package de.robv.android.xposed;

import java.lang.reflect.Member;

public abstract class XC_MethodHook {

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        private Object result = null;
        private Throwable throwable = null;
        private boolean returnEarly = false;

        public Object getResult() { return result; }
        public void setResult(Object result) {
            this.result = result;
            this.returnEarly = true;
        }
        public Throwable getThrowable() { return throwable; }
        public boolean hasThrowable() { return throwable != null; }
        public void setThrowable(Throwable throwable) { this.throwable = throwable; }
        public Object getResultOrThrowable() throws Throwable {
            if (throwable != null) throw throwable;
            return result;
        }
    }

    public class Unhook {
        public XC_MethodHook getCallback() { return XC_MethodHook.this; }
        public void unhook() {}
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
}
