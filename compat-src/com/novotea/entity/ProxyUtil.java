package com.novotea.entity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyUtil {
  private static Constructor<MethodHandles.Lookup> lookupConstructor;
  private static Map<Method, InvokeCallback> globalDispatchTable;
  private static Method hashCodeMethod;
  private static Method equalsMethod;
  private static Method toStringMethod;
  private static Method entityUUIDMethod;

  private final EntityType type;
  private final MethodHandle defaultToStringMethod;
  private final Map<Method, InvokeCallback> dispatchTable;

  public ProxyUtil(EntityType type) {
    this.type = type;
    this.dispatchTable = new ConcurrentHashMap<>(globalDispatchTable);

    MethodHandle handle = null;
    try {
      handle =
          MethodHandles.lookup()
              .findVirtual(type.getType(), "defaultToString", java.lang.invoke.MethodType.methodType(String.class));
    } catch (NoSuchMethodException | IllegalAccessException ignored) {
    }
    this.defaultToStringMethod = handle;
  }

  void checkAccess(Attribute<?> attribute) {
    if (type.hasAttribute(attribute)) {
      return;
    }
    throw new ClassCastException(
        String.format(
            "Attribute for type %s is used on entity of type %s",
            attribute.getEntityType(),
            type));
  }

  private static Object equalsMethod(Object proxy, ProxyEntityAccess access, Object other) {
    if (proxy == other) {
      return Boolean.TRUE;
    }
    if (other == null) {
      return Boolean.FALSE;
    }
    if (!(other instanceof Entity)) {
      return Boolean.FALSE;
    }
    return Boolean.valueOf(access == EntityAccess.access((Entity) other));
  }

  public String defaultToString(ProxyEntityAccess access, Object proxy) throws Throwable {
    if (defaultToStringMethod != null) {
      return (String) defaultToStringMethod.invoke(proxy);
    }
    return access.standardToString();
  }

  public EntityType getEntityType() {
    return type;
  }

  public Object dispatch(ProxyEntityAccess access, Object proxy, Method method, Object[] args)
      throws Throwable {
    InvokeCallback callback = dispatchTable.get(method);
    if (callback != null) {
      return callback.invoke(access, proxy, safeArgs(args));
    }

    if (method.isDefault()) {
      MethodHandle handle = defaultMethodHandle(method);
      return register(method, handle).invoke(access, proxy, safeArgs(args));
    }

    throw new IllegalStateException(String.format("Cannot call %s on %s", method, this));
  }

  private InvokeCallback register(Method method, MethodHandle handle) {
    InvokeCallback callback =
        (access, proxy, args) -> handle.bindTo(proxy).invokeWithArguments(Arrays.asList(args));
    dispatchTable.put(method, callback);
    return callback;
  }

  private static MethodHandle defaultMethodHandle(Method method) throws Throwable {
    Class<?> declaringClass = method.getDeclaringClass();

    try {
      Method privateLookupIn =
          MethodHandles.class.getMethod(
              "privateLookupIn", Class.class, MethodHandles.Lookup.class);
      MethodHandles.Lookup lookup =
          (MethodHandles.Lookup)
              privateLookupIn.invoke(null, declaringClass, MethodHandles.lookup());
      return lookup.unreflectSpecial(method, declaringClass);
    } catch (NoSuchMethodException ignored) {
      return legacyLookup(method, declaringClass);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IllegalAccessException) {
        return legacyLookup(method, declaringClass);
      }
      throw cause;
    }
  }

  private static MethodHandle legacyLookup(Method method, Class<?> declaringClass) throws Throwable {
    if (lookupConstructor == null) {
      throw new IllegalStateException("Lookup constructor unavailable");
    }
    try {
      return lookupConstructor
          .newInstance(declaringClass, MethodHandles.Lookup.PRIVATE)
          .unreflectSpecial(method, declaringClass);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  private static Object[] safeArgs(Object[] args) {
    return args == null ? new Object[0] : args;
  }

  private interface InvokeCallback {
    Object invoke(ProxyEntityAccess access, Object proxy, Object... args) throws Throwable;
  }

  static {
    globalDispatchTable = new HashMap<>();
    try {
      hashCodeMethod = Object.class.getDeclaredMethod("hashCode");
      equalsMethod = Object.class.getDeclaredMethod("equals", Object.class);
      toStringMethod = Object.class.getDeclaredMethod("toString");
      entityUUIDMethod = Entity.class.getDeclaredMethod("entityUUID");
    } catch (NoSuchMethodException | SecurityException e) {
      throw new RuntimeException(e);
    }

    try {
      lookupConstructor =
          MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
      lookupConstructor.setAccessible(true);
    } catch (NoSuchMethodException | SecurityException ignored) {
      lookupConstructor = null;
    }

    globalDispatchTable.put(hashCodeMethod, (access, proxy, args) -> Integer.valueOf(access.hashCode()));
    globalDispatchTable.put(equalsMethod, (access, proxy, args) -> equalsMethod(proxy, access, args[0]));
    globalDispatchTable.put(toStringMethod, (access, proxy, args) -> access.defaultToString(proxy));
    globalDispatchTable.put(entityUUIDMethod, (access, proxy, args) -> access.key());
  }
}
