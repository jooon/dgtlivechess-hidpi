package com.novotea.entity;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyEntityAccess extends EntityAccess implements InvocationHandler {
  private static final Map<EntityType, ProxyUtil> utils = new ConcurrentHashMap<>();

  private final ProxyUtil util;

  public ProxyEntityAccess(EntityType type, EntityLoader loader) {
    ProxyUtil cached = utils.get(type);
    if (cached == null) {
      ProxyUtil created = new ProxyUtil(type);
      ProxyUtil existing = utils.putIfAbsent(type, created);
      cached = existing == null ? created : existing;
    }
    this.util = cached;

    Entity entity =
        (Entity)
            Proxy.newProxyInstance(
                type.getClass().getClassLoader(),
                new Class<?>[] {type.getType()},
                this);
    init(type, entity, loader);
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    return util.dispatch(this, proxy, method, args);
  }

  @Override
  public <T> T get(Attribute<T> attribute) {
    util.checkAccess(attribute);
    return super.get(attribute);
  }

  @Override
  public <T> void set(Attribute<T> attribute, T value) {
    util.checkAccess(attribute);
    super.set(null, attribute, value);
  }

  public String defaultToString(Object proxy) throws Throwable {
    return util.defaultToString(this, proxy);
  }

  public String standardToString() {
    return new StringBuilder()
        .append(Integer.toUnsignedString(hashCode()))
        .append("@[")
        .append(util.getEntityType().getName())
        .append(']')
        .toString();
  }
}
