package com.mopub.network;

import android.os.Build;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Created by wychi on 2015/8/3.
 */
public class ReflectionUtils {
    private final static String TAG = "ReflectionUtils";
    public static class ReflectionException extends Exception {
        public ReflectionException(Exception e) {
            super(e);
        }
    }

    static void dumpClass(Class clazz) {
        StringBuilder sb = new StringBuilder(">>> ");
        sb.append(clazz.getCanonicalName()).append("\n");
        sb.append("Build.VERSION.SDK_INT=").append(Build.VERSION.SDK_INT).append("\n");
        sb.append("Build.VERSION.RELEASE=").append(Build.VERSION.RELEASE).append("\n");
        for (Method m : clazz.getDeclaredMethods()) {
            sb.append(clazz.getCanonicalName()).append("#").append(m.getName()).append("(");
            for(Class clz : m.getParameterTypes()) {
                sb.append(clz.getCanonicalName()).append(", ");
            }
            sb.append(")\n");
        }
        sb.append("<<<\n");
        android.util.Log.d(TAG, sb.toString());
    }

    static class ClassMetadata {
        static HashMap<String, ClassMetadata> sMap;

        public static ClassMetadata get(String className) throws ClassNotFoundException  {
            ClassMetadata metadata = null;
            if(sMap != null) {
                metadata = sMap.get(className);
            }

            if(metadata == null) {
                metadata = new ClassMetadata();
                metadata.loadClass(className);
                if(sMap == null)
                    sMap = new HashMap<String, ClassMetadata>();
                sMap.put(className, metadata);
            } else {
                Log.d(TAG, "[CacheHit] getClassFromCache: " + className);
            }

            return metadata;
        }

        Class mClazz;
        HashMap<String, Method> mMethods;
        HashMap<String, Field> mFields;

        ClassMetadata() {
        }

        private void loadClass(String className) throws ClassNotFoundException {
            mClazz = Class.forName(className);
        }

        private Method getMethodFromCache(String key) {
            Method method = null;
            if(mMethods != null)
                method = mMethods.get(key);

            return method;
        }

        private void putMethodToCache(String key, Method method) {
            if(mMethods == null) {
                mMethods = new HashMap<String, Method>();
            }
            if(mMethods.containsKey(key)) {
                Log.d(TAG, "Put method to cache. key=" + key);
            }
            mMethods.put(key, method);
        }

        private Field getFieldFromCache(String key) {
            Field field= null;
            if(mFields != null)
                field = mFields.get(key);

            return field;
        }

        private void putFieldToCache(String key, Field method) {
            if(mFields == null) {
                mFields = new HashMap<String, Field>();
            }
            if(mFields.containsKey(key)) {
                Log.d(TAG, "Put Field to cache. key=" + key);
            }
            mFields.put(key, method);
        }

        private Method getNonPublicMethod(String methodName, Class<?>... paramsTypes) throws NoSuchMethodException {
            Class<?> clazz = mClazz;
            Method method = null;

            while (clazz != null && method == null) {
                try {
                    method = clazz.getDeclaredMethod(methodName, paramsTypes);
                    method.setAccessible(true);
                } catch (NoSuchMethodException e) {
                    clazz = clazz.getSuperclass();
                    if(clazz!=null)
                        Log.d(TAG, "NoSuchMethodException " + methodName + " try parent=" + clazz.getCanonicalName());
                }
            }

            if(method == null)
                throw new NoSuchMethodException(methodName + " " + Arrays.toString(paramsTypes));

            return method;
        }

        public Method getMethod(String methodName, Class<?>... paramsTypes) throws NoSuchMethodException {
            String key = methodName+paramsTypes.length; // TODO: figure out better key
            Method method = getMethodFromCache(key);
            if(method !=null)
                return method;

            try {
                method = mClazz.getMethod(methodName, paramsTypes);
            } catch (NoSuchMethodException e) {
                try {
                    method = getNonPublicMethod(methodName, paramsTypes);
                    // to invoke private method
                    method.setAccessible(true);
                } catch (NoSuchMethodException e2) {
                    dumpClass(mClazz);
                    throw e2;
                }
            }

            method.setAccessible(true);

            putMethodToCache(key, method);
            return method;
        }

        private Field getNonPublicField(String fieldName) throws NoSuchFieldException {
            Class<?> clazz = mClazz;
            Field field = null;

            while (clazz != null && field == null) {
                try {
                    field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                    if(clazz!=null)
                        Log.d(TAG, "NoSuchFieldException " + fieldName + " try parent=" + clazz.getCanonicalName());
                }
            }

            if(field == null)
                throw new NoSuchFieldException(fieldName);

            return field;
        }

        // TODO: un-test
        public Field getField(String fieldName) throws NoSuchFieldException {
            Field field = getFieldFromCache(fieldName);
            if(field != null)
                return field;

            try {
                field = mClazz.getField(fieldName);
            } catch (NoSuchFieldException e) {
                field = getNonPublicField(fieldName);
            }

            putFieldToCache(fieldName, field);
            return field;
        }
    }

    public static class RefClass {
        String mClassName;
        Object mInstance;

        public RefClass(String className, Object instance) {
            if(className==null)
                throw new RuntimeException("ClassName is not assigned.");

            mClassName = className;
            mInstance = instance;
        }

        public Object call(String methodName, Object... params) throws ReflectionException {
            try {
                // params: value1, value2
                Log.d(TAG, "call method=" + methodName);
                ClassMetadata metadata = ClassMetadata.get(mClassName);

                Class[] paramTypes = null;
                if(params!=null) {
                    paramTypes = new Class[params.length];
                    for (int i = 0; i < params.length; i++) {
                        Class type = params[i].getClass();
                        Log.d(TAG, "param type=" + type);
                        // TODO: find a way to handle primitive class type.
                        if(type == Integer.class) {
                            paramTypes[i] = int.class;
                            Log.d(TAG, "Integer.class is changed to int.class");
                        } else if(type == Long.class) {
                            paramTypes[i] = long.class;
                            Log.d(TAG, "Long.class is changed to long.class");
                        } else {
                            paramTypes[i] = params[i].getClass();
                        }
                    }
                }

                Method method = metadata.getMethod(methodName, paramTypes);
                return method.invoke(mInstance, params);
            } catch (Exception e) {
                throw new ReflectionException(e);
            }
        }

        public Object call2(String methodName, Object... params) throws ReflectionException {
            try {
                // params: type1, value1, type2, value2
                ClassMetadata metadata = ClassMetadata.get(mClassName);

                Class[] paramTypes = null;
                Object[] paramValues = null;
                if(params!=null) {
                    paramTypes = new Class[params.length/2];
                    paramValues = new Object[params.length/2];
                    for (int i = 0; i < params.length; i=i+2) {
                        paramTypes[i/2] = (Class) params[i];
                        paramValues[i/2] = params[i+1];
                    }
                }

                Method method = metadata.getMethod(methodName, paramTypes);
                return method.invoke(mInstance, paramValues);
            } catch (Exception e) {
                throw new ReflectionException(e);
            }
        }

        public Object getValue(String fieldName) throws ReflectionException {
            try {
                ClassMetadata metadata = ClassMetadata.get(mClassName);

                Field field = metadata.getField(fieldName);
                field.setAccessible(true);
                return field.get(mInstance);
            } catch (Exception e) {
                throw new ReflectionException(e);
            }
        }
    }

    public static RefClass Hack(String className) {
        return Hack(className, null);
    }

    /*
     * Example:
     * try {
     *     Object instance = ReflectionUtils.Hack("android.view.WindowManagerGlobal")
	 *					.call("getInstance");
     *     ReflectionUtils.Hack("android.view.WindowManagerGlobal", instance)
	 *					.call("trimMemory", ComponentCallbacks2.TRIM_MEMORY_COMPLETE);
	 * } catch (ReflectionUtils.ReflectionException e) {
	 *     // e.printStackTrace();
	 * }
     */
    public static RefClass Hack(String className, Object instance) {
        return new RefClass(className, instance);
    }
}
