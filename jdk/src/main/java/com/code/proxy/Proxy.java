///*
// * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
// * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// */
//
//package com.code.proxy;
//
//import java.lang.ref.WeakReference;
//import java.lang.reflect.*;
//import java.security.AccessController;
//import java.security.PrivilegedAction;
//import java.util.Arrays;
//import java.util.IdentityHashMap;
//import java.util.Map;
//import java.util.Objects;
//import java.util.concurrent.atomic.AtomicLong;
//import java.util.function.BiFunction;
//
//import sun.misc.ProxyGenerator;
//import sun.misc.VM;
//import sun.reflect.CallerSensitive;
//import sun.reflect.Reflection;
//import sun.reflect.misc.ReflectUtil;
//import sun.security.util.SecurityConstants;
//
///**
// * @author Peter Jones
// * @see InvocationHandler
// * @since 1.3
// */
//public class Proxy implements java.io.Serializable {
//
//    private static final long serialVersionUID = -2222568056686623797L;
//
//    /**
//     * 构造方法参数类型，就是 InvocationHandler
//     */
//    private static final Class<?>[] constructorParams = {InvocationHandler.class};
//
//    /**
//     * 代理类的缓存
//     */
//    private static final WeakCache<ClassLoader, Class<?>[], Class<?>>
//            proxyClassCache = new WeakCache<>(new KeyFactory(), new ProxyClassFactory());
//
//    /**
//     * 代理对象调用的处理器
//     *
//     * @serial
//     */
//    protected InvocationHandler h;
//
//    /**
//     * Prohibits instantiation.
//     */
//    private Proxy() {
//    }
//
//    /**
//     * 供生成的动态代理类调用，也就是 Proxy 的子类。
//     *
//     * @param h 用于代理对象的调用处理器
//     * @throws NullPointerException
//     */
//    protected Proxy(InvocationHandler h) {
//        Objects.requireNonNull(h);
//        this.h = h;
//    }
//
//
//    @CallerSensitive
//    public static Class<?> getProxyClass(ClassLoader loader,
//                                         Class<?>... interfaces)
//            throws IllegalArgumentException {
//        final Class<?>[] intfs = interfaces.clone();
//        final SecurityManager sm = System.getSecurityManager();
//        if (sm != null) {
//            checkProxyAccess(Reflection.getCallerClass(), loader, intfs);
//        }
//
//        return getProxyClass0(loader, intfs);
//    }
//
//    /**
//     * 权限检查
//     * Check permissions required to create a Proxy class.
//     * <p>
//     * To define a com.code.proxy class, it performs the access checks as in
//     * Class.forName (VM will invoke ClassLoader.checkPackageAccess):
//     * 1. "getClassLoader" permission check if loader == null
//     * 2. checkPackageAccess on the interfaces it implements
//     * <p>
//     * To get a constructor and new instance of a com.code.proxy class, it performs
//     * the package access check on the interfaces it implements
//     * as in Class.getConstructor.
//     * <p>
//     * If an interface is non-public, the com.code.proxy class must be defined by
//     * the defining loader of the interface.  If the caller's class loader
//     * is not the same as the defining loader of the interface, the VM
//     * will throw IllegalAccessError when the generated com.code.proxy class is
//     * being defined via the defineClass0 method.
//     */
//    private static void checkProxyAccess(Class<?> caller,
//                                         ClassLoader loader,
//                                         Class<?>... interfaces) {
//        SecurityManager sm = System.getSecurityManager();
//        if (sm != null) {
//            ClassLoader ccl = caller.getClassLoader();
//            if (VM.isSystemDomainLoader(loader) && !VM.isSystemDomainLoader(ccl)) {
//                sm.checkPermission(SecurityConstants.GET_CLASSLOADER_PERMISSION);
//            }
//            ReflectUtil.checkProxyPackageAccess(ccl, interfaces);
//        }
//    }
//
//    /**
//     * 生成代理类。在调用此方法之前，必须调用checkProxyAccess方法来执行权限检查。
//     */
//    private static Class<?> getProxyClass0(ClassLoader loader,
//                                           Class<?>... interfaces) {
//        if (interfaces.length > 65535) {
//            throw new IllegalArgumentException("interface limit exceeded");
//        }
//
//        /**
//         * 获取代理类：
//         * 1. 如果代理类存在则返回缓存的副本。
//         * 2. 不存在，则通过 ProxyClassFactory 创建代理类
//         */
//        return proxyClassCache.get(loader, interfaces);
//    }
//
//
//    /**
//     * 代理类工厂，根据指定的类加载器和接口集合生成代理类
//     */
//    private static final class ProxyClassFactory
//            implements BiFunction<ClassLoader, Class<?>[], Class<?>> {
//
//        // 所有代理类名称前缀，具体名称为 $Proxy + Num
//        private static final String proxyClassNamePrefix = "$Proxy";
//
//        // 生成代理名称的序号，是自增原子类
//        private static final AtomicLong nextUniqueNumber = new AtomicLong();
//
//        /**
//         * 生成代理类的逻辑
//         *
//         * @param loader     类加载器
//         * @param interfaces 接口集合
//         * @return 代理类
//         */
//        @Override
//        public Class<?> apply(ClassLoader loader, Class<?>[] interfaces) {
//            Map<Class<?>, Boolean> interfaceSet = new IdentityHashMap<>(interfaces.length);
//            // 遍历接口集合
//            for (Class<?> intf : interfaces) {
//
//                // 1 验证类加载器是否将当前接口名称解析为相同的类对象
//                Class<?> interfaceClass = null;
//                try {
//                    interfaceClass = Class.forName(intf.getName(), false, loader);
//                } catch (ClassNotFoundException e) {
//                }
//                if (interfaceClass != intf) {
//                    throw new IllegalArgumentException(
//                            intf + " is not visible from class loader");
//                }
//
//                // 2 判断是否是接口
//                if (!interfaceClass.isInterface()) {
//                    throw new IllegalArgumentException(
//                            interfaceClass.getName() + " is not an interface");
//                }
//
//                // 3 验证接口是否重复加载
//                if (interfaceSet.put(interfaceClass, Boolean.TRUE) != null) {
//                    throw new IllegalArgumentException(
//                            "repeated interface: " + interfaceClass.getName());
//                }
//            }
//
//            // 4 代理所在包的名称
//            String proxyPkg = null;
//            int accessFlags = Modifier.PUBLIC | Modifier.FINAL;
//
//
//            // 5 记录非public类型的接口，如果是非public类型的接口，则会将代理类定义在该对应的包中。当且仅当所有非public类型的接口都在一个包中才行，否则不合法
//            for (Class<?> intf : interfaces) {
//                int flags = intf.getModifiers();
//                if (!Modifier.isPublic(flags)) {
//                    accessFlags = Modifier.FINAL;
//                    String name = intf.getName();
//                    int n = name.lastIndexOf('.');
//                    String pkg = ((n == -1) ? "" : name.substring(0, n + 1));
//                    if (proxyPkg == null) {
//                        proxyPkg = pkg;
//                    } else if (!pkg.equals(proxyPkg)) {
//                        throw new IllegalArgumentException(
//                                "non-public interfaces from different packages");
//                    }
//                }
//            }
//
//            // 6 如果接口是public类型的，则使用固定的包名： com.sun.com.code.proxy
//            if (proxyPkg == null) {
//                proxyPkg = ReflectUtil.PROXY_PACKAGE + ".";
//            }
//
//            // 7 组装代理类名称，格式：proxyPkg + $Proxy + Num
//            long num = nextUniqueNumber.getAndIncrement();
//            String proxyName = proxyPkg + proxyClassNamePrefix + num;
//
//            // 8 根据代理类名和接口列表使用 ProxyGenerator 生成指定的代理类，可能返回null （配置了虚拟机参数，将代理类字节信息输出到文件）
//            byte[] proxyClassFile = ProxyGenerator.generateProxyClass(proxyName, interfaces, accessFlags);
//
//            try {
//
//                // 9 调用native方法，返回代理类
//                return defineClass0(loader, proxyName, proxyClassFile, 0, proxyClassFile.length);
//
//            } catch (ClassFormatError e) {
//
//                // 排除代理类生成代码中的bug，提供给代理类创建的参数存在其他问题(例如超出了虚拟机限制)。
//                throw new IllegalArgumentException(e.toString());
//            }
//        }
//    }
//
//    /**
//     * A function that maps an array of interfaces to an optimal key where
//     * Class objects representing interfaces are weakly referenced.
//     */
//    private static final class KeyFactory
//            implements BiFunction<ClassLoader, Class<?>[], Object> {
//
//        /**
//         * 返回接口对应的弱引用。Key1、Key2 以及 KeyX 都持有 WeakReference
//         *
//         * @param classLoader
//         * @param interfaces
//         * @return
//         */
//        @Override
//        public Object apply(ClassLoader classLoader, Class<?>[] interfaces) {
//            switch (interfaces.length) {
//                case 1:
//                    return new Key1(interfaces[0]); // the most frequent
//                case 2:
//                    return new Key2(interfaces[0], interfaces[1]);
//                case 0:
//                    return key0;
//                default:
//                    return new KeyX(interfaces);
//            }
//        }
//    }
//
//
//    /**
//     * 创建代理类对象。
//     *
//     * @param loader     代理类的加载器
//     * @param interfaces 目标类的接口列表
//     * @param h          代理对象方法调用都会分派给该调用处理器
//     * @return 代理对象
//     * @throws IllegalArgumentException
//     */
//    @CallerSensitive
//    public static Object newProxyInstance(ClassLoader loader,
//                                          Class<?>[] interfaces,
//                                          InvocationHandler h)
//            throws IllegalArgumentException {
//
//        // 调用处理器是必传参数
//        Objects.requireNonNull(h);
//
//        // 接口列表
//        final Class<?>[] intfs = interfaces.clone();
//        final SecurityManager sm = System.getSecurityManager();
//        if (sm != null) {
//            checkProxyAccess(Reflection.getCallerClass(), loader, intfs);
//        }
//
//        // 1. 查找或生成指定的代理类。
//        Class<?> cl = getProxyClass0(loader, intfs);
//
//        try {
//            // 对生成的代理类进行安全检查
//            if (sm != null) {
//                checkNewProxyPermission(Reflection.getCallerClass(), cl);
//            }
//
//            // 2. 获取代理类的指定构造方法，即参数类型为 InvocationHandler 的构造方法
//            final Constructor<?> cons = cl.getConstructor(constructorParams);
//            final InvocationHandler ih = h;
//
//            // 3. 保证代理类的构造方法 cons 具有访问权限，便于后续反射创建代理对象
//            if (!Modifier.isPublic(cl.getModifiers())) {
//                AccessController.doPrivileged(new PrivilegedAction<Void>() {
//                    public Void run() {
//                        cons.setAccessible(true);
//                        return null;
//                    }
//                });
//            }
//
//            // 4. 反射创建代理对象，注意参数为 InvocationHandler
//            return cons.newInstance(new Object[]{h});
//
//        } catch (IllegalAccessException | InstantiationException e) {
//            throw new InternalError(e.toString(), e);
//        } catch (InvocationTargetException e) {
//            Throwable t = e.getCause();
//            if (t instanceof RuntimeException) {
//                throw (RuntimeException) t;
//            } else {
//                throw new InternalError(t.toString(), t);
//            }
//        } catch (NoSuchMethodException e) {
//            throw new InternalError(e.toString(), e);
//        }
//    }
//
//
//    private static void checkNewProxyPermission(Class<?> caller, Class<?> proxyClass) {
//        SecurityManager sm = System.getSecurityManager();
//        if (sm != null) {
//            if (ReflectUtil.isNonPublicProxyClass(proxyClass)) {
//                ClassLoader ccl = caller.getClassLoader();
//                ClassLoader pcl = proxyClass.getClassLoader();
//
//                // do permission check if the caller is in a different runtime package
//                // of the com.code.proxy class
//                int n = proxyClass.getName().lastIndexOf('.');
//                String pkg = (n == -1) ? "" : proxyClass.getName().substring(0, n);
//
//                n = caller.getName().lastIndexOf('.');
//                String callerPkg = (n == -1) ? "" : caller.getName().substring(0, n);
//
//                if (pcl != ccl || !pkg.equals(callerPkg)) {
//                    sm.checkPermission(new ReflectPermission("newProxyInPackage." + pkg));
//                }
//            }
//        }
//    }
//
//    /**
//     * Returns true if and only if the specified class was dynamically
//     * generated to be a com.code.proxy class using the {@code getProxyClass}
//     * method or the {@code newProxyInstance} method.
//     *
//     * <p>The reliability of this method is important for the ability
//     * to use it to make security decisions, so its implementation should
//     * not just test if the class in question extends {@code Proxy}.
//     *
//     * @param cl the class to test
//     * @return {@code true} if the class is a com.code.proxy class and
//     * {@code false} otherwise
//     * @throws NullPointerException if {@code cl} is {@code null}
//     */
//    public static boolean isProxyClass(Class<?> cl) {
//        return Proxy.class.isAssignableFrom(cl) && proxyClassCache.containsValue(cl);
//    }
//
//    /**
//     * Returns the invocation handler for the specified com.code.proxy instance.
//     *
//     * @param proxy the com.code.proxy instance to return the invocation handler for
//     * @return the invocation handler for the com.code.proxy instance
//     * @throws IllegalArgumentException if the argument is not a
//     *                                  com.code.proxy instance
//     * @throws SecurityException        if a security manager, <em>s</em>, is present
//     *                                  and the caller's class loader is not the same as or an
//     *                                  ancestor of the class loader for the invocation handler
//     *                                  and invocation of {@link SecurityManager#checkPackageAccess
//     *                                  s.checkPackageAccess()} denies access to the invocation
//     *                                  handler's class.
//     */
//    @CallerSensitive
//    public static InvocationHandler getInvocationHandler(Object proxy)
//            throws IllegalArgumentException {
//        /*
//         * Verify that the object is actually a com.code.proxy instance.
//         */
//        if (!isProxyClass(proxy.getClass())) {
//            throw new IllegalArgumentException("not a com.code.proxy instance");
//        }
//
//        final Proxy p = (Proxy) proxy;
//        final InvocationHandler ih = p.h;
//        if (System.getSecurityManager() != null) {
//            Class<?> ihClass = ih.getClass();
//            Class<?> caller = Reflection.getCallerClass();
//            if (ReflectUtil.needsPackageAccessCheck(caller.getClassLoader(),
//                    ihClass.getClassLoader())) {
//                ReflectUtil.checkPackageAccess(ihClass);
//            }
//        }
//
//        return ih;
//    }
//
//    /*
//     * a key used for com.code.proxy class with 0 implemented interfaces
//     */
//    private static final Object key0 = new Object();
//
//    /*
//     * Key1 and Key2 are optimized for the common use of dynamic proxies
//     * that implement 1 or 2 interfaces.
//     */
//
//    /*
//     * a key used for com.code.proxy class with 1 implemented interface
//     */
//    private static final class Key1 extends WeakReference<Class<?>> {
//        private final int hash;
//
//        Key1(Class<?> intf) {
//            super(intf);
//            this.hash = intf.hashCode();
//        }
//
//        @Override
//        public int hashCode() {
//            return hash;
//        }
//
//        @Override
//        public boolean equals(Object obj) {
//            Class<?> intf;
//            return this == obj ||
//                    obj != null &&
//                            obj.getClass() == Key1.class &&
//                            (intf = get()) != null &&
//                            intf == ((Key1) obj).get();
//        }
//    }
//
//    /*
//     * a key used for com.code.proxy class with 2 implemented interfaces
//     */
//    private static final class Key2 extends WeakReference<Class<?>> {
//        private final int hash;
//        private final WeakReference<Class<?>> ref2;
//
//        Key2(Class<?> intf1, Class<?> intf2) {
//            super(intf1);
//            hash = 31 * intf1.hashCode() + intf2.hashCode();
//            ref2 = new WeakReference<Class<?>>(intf2);
//        }
//
//        @Override
//        public int hashCode() {
//            return hash;
//        }
//
//        @Override
//        public boolean equals(Object obj) {
//            Class<?> intf1, intf2;
//            return this == obj ||
//                    obj != null &&
//                            obj.getClass() == Key2.class &&
//                            (intf1 = get()) != null &&
//                            intf1 == ((Key2) obj).get() &&
//                            (intf2 = ref2.get()) != null &&
//                            intf2 == ((Key2) obj).ref2.get();
//        }
//    }
//
//    /*
//     * a key used for com.code.proxy class with any number of implemented interfaces
//     * (used here for 3 or more only)
//     */
//    private static final class KeyX {
//        private final int hash;
//        private final WeakReference<Class<?>>[] refs;
//
//        @SuppressWarnings("unchecked")
//        KeyX(Class<?>[] interfaces) {
//            hash = Arrays.hashCode(interfaces);
//            refs = (WeakReference<Class<?>>[]) new WeakReference<?>[interfaces.length];
//            for (int i = 0; i < interfaces.length; i++) {
//                refs[i] = new WeakReference<>(interfaces[i]);
//            }
//        }
//
//        @Override
//        public int hashCode() {
//            return hash;
//        }
//
//        @Override
//        public boolean equals(Object obj) {
//            return this == obj ||
//                    obj != null &&
//                            obj.getClass() == KeyX.class &&
//                            equals(refs, ((KeyX) obj).refs);
//        }
//
//        private static boolean equals(WeakReference<Class<?>>[] refs1,
//                                      WeakReference<Class<?>>[] refs2) {
//            if (refs1.length != refs2.length) {
//                return false;
//            }
//            for (int i = 0; i < refs1.length; i++) {
//                Class<?> intf = refs1[i].get();
//                if (intf == null || intf != refs2[i].get()) {
//                    return false;
//                }
//            }
//            return true;
//        }
//    }
//
//
//    private static native Class<?> defineClass0(ClassLoader loader, String name,
//                                                byte[] b, int off, int len);
//}
