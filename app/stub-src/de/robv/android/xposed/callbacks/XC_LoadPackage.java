package de.robv.android.xposed.callbacks;

import android.content.pm.ApplicationInfo;

public final class XC_LoadPackage extends XCallback {

    public static final class LoadPackageParam extends Param {
        public String packageName;
        public String processName;
        public ClassLoader classLoader;
        public ApplicationInfo appInfo;
        public boolean isFirstApplication;
    }

    public XC_LoadPackage() { super(); }
    public XC_LoadPackage(int priority) { super(priority); }

    @Override
    protected void call(Param param) throws Throwable {}
}

abstract class XCallback {
    public XCallback() {}
    public XCallback(int priority) {}

    protected abstract void call(Param param) throws Throwable;

    public static class Param {
        public Object[] callbacks;
    }
}
